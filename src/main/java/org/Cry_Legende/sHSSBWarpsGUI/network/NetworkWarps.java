package org.Cry_Legende.sHSSBWarpsGUI.network;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.google.gson.JsonObject;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FavoritesManager;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FeaturedWarpsManager;
import org.Cry_Legende.sHSSBWarpsGUI.managers.WarpVisitManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.IllegalPluginAccessException;

import java.io.File;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Mode réseau : favoris, compteurs de visites et warps mis en avant passent par la base commune au lieu des
 * fichiers de ce serveur, et les warps vers une île hébergée par un autre serveur y envoient le joueur.
 *
 * <ul>
 *   <li>favoris : écrits un par un à chaque changement, relus à l'arrivée du joueur une fois que le serveur de départ
 *       a fini ses écritures (barrière de SHNetwork) ;</li>
 *   <li>visites : incrément atomique en base ({@code visits = visits + 1}), total relu pour tous les serveurs ;</li>
 *   <li>mises en avant : écrites en base, une mise en avant échue est retirée par un seul serveur, qui prévient le
 *       propriétaire où qu'il soit ;</li>
 *   <li>visites et mises en avant sont relues toutes les {@code reseau.rafraichissement-secondes} secondes, et les
 *       mises en avant dès qu'un autre serveur annonce un changement : le fil principal ne lit jamais que la mémoire ;</li>
 *   <li>warp d'une île hébergée ailleurs : le joueur est envoyé sur le serveur de l'île à l'emplacement du warp, sans
 *       passer par le contrôle de sécurité de SuperiorSkyblock2, qui lirait ici une copie périmée du monde et pourrait
 *       supprimer le warp.</li>
 * </ul>
 */
public final class NetworkWarps implements Listener {

    private static final String CHANNEL_FEATURED = "shssbwarpsgui.featured";
    private static final String CHANNEL_EXPIRED = "shssbwarpsgui.expire";
    private static final int WRITE_ATTEMPTS = 3;
    /** Durée pendant laquelle une île sans ligne dans island_host est tenue pour hébergée par l'hôte par défaut. */
    private static final long UNLISTED_MILLIS = 30_000L;
    /** Un deuxième clic sur le même warp pendant que le transfert part est ignoré (le joueur ne paie pas deux fois). */
    private static final long SENDING_MILLIS = 3_000L;

    @FunctionalInterface
    private interface SqlTask {

        void run() throws SQLException;
    }

    private final SHSSBWarpsGUI plugin;
    private final WarpDatabase db;
    private final NetworkLink link;
    private final Duration waitMax;
    private final int refreshSeconds;
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "SHSSBWarpsGUI-reseau");
        thread.setDaemon(true);
        return thread;
    });

    // hôtes des îles (table island_host de SHSkyBridge)
    private final boolean bridge;
    private final String me;
    private final String defaultHost;
    private final Map<UUID, String> hosts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> unlisted = new ConcurrentHashMap<>();
    private final Map<UUID, Long> sending = new ConcurrentHashMap<>();
    private volatile boolean hostsWarned;

    public NetworkWarps(SHSSBWarpsGUI plugin, WarpDatabase db, NetworkLink link) {
        this.plugin = plugin;
        this.db = db;
        this.link = link;
        this.waitMax = Duration.ofSeconds(Math.max(1, plugin.getConfig().getInt("reseau.attente-max-secondes", 10)));
        this.refreshSeconds = Math.max(5, plugin.getConfig().getInt("reseau.rafraichissement-secondes", 30));
        this.me = link.serverName().toLowerCase(Locale.ROOT);

        String configured = plugin.getConfig().getString("reseau.hote-par-defaut", "").trim();
        boolean skyBridge = Bukkit.getPluginManager().isPluginEnabled("SHSkyBridge");
        if (configured.isEmpty() && skyBridge) {
            // même valeur que le pont : une seule réponse pour « où est cette île »
            configured = Bukkit.getPluginManager().getPlugin("SHSkyBridge").getConfig().getString("hote-par-defaut", "").trim();
        }
        this.defaultHost = configured.toLowerCase(Locale.ROOT);
        this.bridge = skyBridge && !defaultHost.isEmpty();
    }

    /** Vrai si les îles hébergées par un autre serveur sont reconnues (SHSkyBridge présent). */
    public boolean followsIslandHosts() {
        return bridge;
    }

    // ------------------------------------------------------------------ cycle de vie

    /** Branche le mode réseau : les trois gestionnaires écrivent en base, les joueurs déjà là sont chargés. */
    public void start() {
        FavoritesManager favorites = plugin.getFavoritesManager();
        WarpVisitManager visits = plugin.getWarpVisitManager();
        FeaturedWarpsManager featured = plugin.getFeaturedWarpsManager();

        link.registerSaveTask(player -> flush());
        link.listen(CHANNEL_FEATURED, message -> submit(this::refreshFeatured));
        link.listen(CHANNEL_EXPIRED, this::onExpiredMessage);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        favorites.setSink(new FavoritesManager.Sink() {
            @Override
            public void added(UUID viewer, UUID target) {
                submit(() -> write(() -> db.addFavorite(viewer, target), "Favori de " + viewer));
            }

            @Override
            public void removed(UUID viewer, UUID target) {
                submit(() -> write(() -> db.removeFavorite(viewer, target), "Favori de " + viewer));
            }
        });
        visits.setSink((owner, warpKey) -> submit(() -> countVisit(owner, warpKey)));
        featured.setSink(new FeaturedWarpsManager.Sink() {
            @Override
            public void saved(UUID ownerUUID, String warpName, long expiryTimestamp) {
                submit(() -> {
                    write(() -> db.upsertFeatured(ownerUUID, warpName, expiryTimestamp), "Mise en avant de " + warpName);
                    changedFeatured();
                });
            }

            @Override
            public void removed(UUID ownerUUID, String warpName) {
                submit(() -> {
                    write(() -> db.removeFeatured(ownerUUID, warpName), "Mise en avant de " + warpName);
                    changedFeatured();
                });
            }

            @Override
            public void expired(FeaturedWarpsManager.FeaturedWarpEntry entry) {
                submit(() -> claimExpired(entry));
            }

            @Override
            public void reload() {
                submit(NetworkWarps.this::refreshFeatured);
            }
        });

        submit(this::bootstrap);
        io.scheduleWithFixedDelay(() -> {
            try {
                refreshAll();
            } catch (RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Relecture du mode réseau interrompue : " + e);
            }
        }, refreshSeconds, refreshSeconds, TimeUnit.SECONDS);
        // les joueurs déjà là (rechargement à chaud) sont chargés comme à une arrivée
        for (Player online : plugin.getServer().getOnlinePlayers()) {
            beginLoad(online.getUniqueId());
        }
    }

    /** Termine les écritures en cours puis ferme la base. */
    public void stop() {
        io.shutdown();
        try {
            if (!io.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Des écritures en base n'ont pas pu être terminées à l'arrêt.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        db.close();
    }

    private void bootstrap() {
        try {
            db.createTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Tables du mode réseau inaccessibles : " + e.getMessage());
        }
        refreshAll();
        try {
            boolean files = new File(plugin.getDataFolder(), "warp_visits.yml").isFile()
                || new File(plugin.getDataFolder(), "featured_warps.yml").isFile()
                || new File(plugin.getDataFolder(), "favorites.yml").isFile();
            if (files && db.loadVisits().isEmpty() && db.loadFeatured().isEmpty()) {
                plugin.getLogger().info("Tables du réseau vides : /swgadmin import copie favorites.yml, warp_visits.yml et featured_warps.yml dans la base.");
            }
        } catch (SQLException e) {
            // base inaccessible : déjà signalé par la relecture
        }
    }

    private void refreshAll() {
        refreshVisits();
        refreshFeatured();
        refreshHosts();
    }

    // ------------------------------------------------------------------ présence

    /** Vrai si le joueur est connecté sur n'importe quel serveur du réseau. Ne lit que la mémoire. */
    public boolean isOnline(UUID uuid) {
        return link.isOnline(uuid);
    }

    /** Pseudos des joueurs de tout le réseau. Ne lit que la mémoire. */
    public Collection<String> onlineNames() {
        return link.onlineNames();
    }

    // ------------------------------------------------------------------ favoris

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        beginLoad(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        plugin.getFavoritesManager().forget(event.getPlayer().getUniqueId());
    }

    /** Relit les favoris du joueur en base, une fois que le serveur de départ a fini d'y écrire. */
    private void beginLoad(UUID uuid) {
        link.awaitSaved(uuid, waitMax).whenComplete((saved, error) -> {
            if (error != null || !Boolean.TRUE.equals(saved)) {
                plugin.getLogger().warning("Départ de " + uuid + " non confirmé par l'autre serveur"
                    + (error != null ? " (" + error + ")" : "") + " : favoris relus quand même.");
            }
            submit(() -> {
                List<UUID> targets;
                try {
                    targets = db.loadFavorites(uuid);
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Favoris de " + uuid + " non relus en base : " + e.getMessage());
                    return;
                }
                runMain(() -> {
                    if (Bukkit.getPlayer(uuid) != null) {
                        plugin.getFavoritesManager().replace(uuid, targets);
                    }
                });
            });
        });
    }

    // ------------------------------------------------------------------ visites

    private void countVisit(UUID owner, String warpKey) {
        try {
            int total = db.incrementVisit(owner, warpKey);
            plugin.getWarpVisitManager().setVisitCount(owner, warpKey, total);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Visite du warp " + warpKey + " de " + owner + " non écrite en base : " + e.getMessage());
        }
    }

    private void refreshVisits() {
        try {
            plugin.getWarpVisitManager().replaceAll(db.loadVisits());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Compteurs de visites non relus en base : " + e.getMessage());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Compteurs de visites illisibles : " + e);
        }
    }

    // ------------------------------------------------------------------ mises en avant

    private void refreshFeatured() {
        try {
            List<FeaturedWarpsManager.FeaturedWarpEntry> fresh = new ArrayList<>();
            for (WarpDatabase.FeaturedRow row : db.loadFeatured()) {
                fresh.add(new FeaturedWarpsManager.FeaturedWarpEntry(row.owner(), row.warpName(), row.expiry()));
            }
            plugin.getFeaturedWarpsManager().replaceAll(fresh);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Warps mis en avant non relus en base : " + e.getMessage());
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Warps mis en avant illisibles : " + e);
        }
    }

    /** Après une écriture : ce serveur relit, les autres sont prévenus et relisent. */
    private void changedFeatured() {
        refreshFeatured();
        link.publish(CHANNEL_FEATURED, new JsonObject());
    }

    /**
     * Mise en avant échue : la suppression de la ligne ne réussit que pour un serveur, qui prévient le propriétaire
     * (ici s'il y est, sinon par un message aux autres serveurs). Les autres ont déjà retiré l'entrée de leur mémoire.
     */
    private void claimExpired(FeaturedWarpsManager.FeaturedWarpEntry entry) {
        boolean claimed;
        try {
            claimed = db.claimExpired(entry.ownerUUID, entry.warpName, entry.expiryTimestamp);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Mise en avant échue de " + entry.warpName + " non retirée en base : " + e.getMessage());
            return;
        }
        if (!claimed) {
            return;
        }
        JsonObject message = new JsonObject();
        message.addProperty("owner", entry.ownerUUID.toString());
        message.addProperty("warp", entry.warpName);
        link.publish(CHANNEL_EXPIRED, message);
        runMain(() -> plugin.getFeaturedWarpsManager().notifyExpired(entry.ownerUUID, entry.warpName, true));
    }

    private void onExpiredMessage(JsonObject message) {
        UUID owner;
        String warp;
        try {
            owner = UUID.fromString(message.get("owner").getAsString());
            warp = message.get("warp").getAsString();
        } catch (RuntimeException e) {
            return;
        }
        runMain(() -> plugin.getFeaturedWarpsManager().notifyExpired(owner, warp, false));
    }

    // ------------------------------------------------------------------ warps vers une île hébergée ailleurs

    /**
     * Téléporte le joueur au warp. Une île hébergée par un autre serveur y envoie le joueur à l'emplacement du warp ;
     * sinon (île de ce serveur, ou pas de pont) c'est la téléportation de SuperiorSkyblock2, comme sans réseau.
     */
    public void warp(SuperiorPlayer sPlayer, Island island, IslandWarp warp) {
        if (!bridge) {
            island.warpPlayer(sPlayer, warp.getName());
            return;
        }
        UUID islandId = island.getUniqueId();
        String known = knownHost(islandId);
        if (known != null) {
            go(sPlayer, island, warp, known);
            return;
        }
        // île inconnue de la copie des hôtes (créée il y a moins de quelques secondes, ou d'avant le pont) : une lecture
        submit(() -> {
            String host;
            try {
                String row = db.islandHost(islandId);
                if (row == null) {
                    unlisted.put(islandId, System.currentTimeMillis());
                    host = defaultHost;
                } else {
                    hosts.put(islandId, row);
                    host = row;
                }
            } catch (SQLException e) {
                warnHosts(e);
                host = null;
            }
            String resolved = host;
            runMain(() -> go(sPlayer, island, warp, resolved));
        });
    }

    private String knownHost(UUID islandId) {
        String host = hosts.get(islandId);
        if (host != null) {
            return host;
        }
        Long checked = unlisted.get(islandId);
        if (checked != null && System.currentTimeMillis() - checked < UNLISTED_MILLIS) {
            return defaultHost;
        }
        return null;
    }

    /** Sur le fil principal. {@code host} nul : hôte inconnu, téléportation normale. */
    private void go(SuperiorPlayer sPlayer, Island island, IslandWarp warp, String host) {
        Player player = sPlayer.asPlayer();
        if (player == null || !player.isOnline()) {
            return;
        }
        if (host == null || host.equals(me)) {
            island.warpPlayer(sPlayer, warp.getName());
            return;
        }
        ConfigManager cfg = plugin.getConfigManager();
        if (island.getWarp(warp.getName()) == null) {
            ChatUtils.sendMessage(player, cfg.msgWarpNotFound.replace("%warp_name%", warp.getName()));
            return;
        }
        Location destination = warp.getLocation();
        if (destination == null || destination.getWorld() == null) {
            ChatUtils.sendMessage(player, cfg.msgGenericError);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = sending.get(player.getUniqueId());
        if (last != null && now - last < SENDING_MILLIS) {
            return;
        }
        sending.put(player.getUniqueId(), now);
        sending.values().removeIf(at -> now - at >= SENDING_MILLIS);
        double charge = SuperiorSkyblockAPI.getSuperiorSkyblock().getSettings().getChargeOnWarp();
        if (charge > 0 && !sPlayer.hasBypassModeEnabled()) {
            if (!plugin.getVaultHook().hasEnough(player, charge)) {
                ChatUtils.sendMessage(player, plugin.getConfig().getString("reseau.messages.warp-trop-cher", "")
                    .replace("%cost%", String.format("%,.2f", charge)));
                return;
            }
            if (!plugin.getVaultHook().withdraw(player, charge)) {
                ChatUtils.sendMessage(player, cfg.msgGenericError);
                return;
            }
        }
        ChatUtils.sendMessage(player, cfg.msgTeleporting.replace("%warp_name%", warp.getName()));
        link.sendTo(player, host, destination.clone());
    }

    private void refreshHosts() {
        if (!bridge) {
            return;
        }
        try {
            Map<UUID, String> fresh = db.loadIslandHosts();
            hosts.putAll(fresh);
            hosts.keySet().retainAll(fresh.keySet());
            unlisted.clear();
        } catch (SQLException e) {
            warnHosts(e);
        }
    }

    private void warnHosts(SQLException e) {
        if (!hostsWarned) {
            hostsWarned = true;
            plugin.getLogger().log(Level.WARNING, "Hôtes des îles (table island_host) illisibles, les warps restent des téléportations normales : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ reprise des fichiers

    /**
     * Copie favorites.yml, warp_visits.yml et featured_warps.yml dans la base commune, sans rien écraser : une ligne
     * déjà en base reste, un compteur garde le plus grand total, une mise en avant l'échéance la plus tardive.
     * Peut être relancée sans risque. Le résultat est donné à {@code sender}.
     */
    public void importFiles(CommandSender sender) {
        submit(() -> {
            String text;
            try {
                int favorites = db.importFavorites(readFavorites());
                int visits = db.importVisits(readVisits());
                int featured = db.importFeatured(readFeatured());
                refreshVisits();
                changedFeatured();
                text = plugin.getConfig().getString("reseau.messages.import-termine", "")
                    .replace("%favoris%", String.valueOf(favorites))
                    .replace("%visites%", String.valueOf(visits))
                    .replace("%mises_en_avant%", String.valueOf(featured));
            } catch (SQLException e) {
                text = plugin.getConfig().getString("reseau.messages.import-echec", "").replace("%erreur%", String.valueOf(e.getMessage()));
            }
            String shown = text;
            runMain(() -> ChatUtils.sendMessage(sender, shown));
        });
    }

    private Map<UUID, List<UUID>> readFavorites() {
        Map<UUID, List<UUID>> favorites = new HashMap<>();
        File file = new File(plugin.getDataFolder(), "favorites.yml");
        if (!file.isFile()) {
            return favorites;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                UUID viewer = UUID.fromString(key);
                List<UUID> targets = new ArrayList<>();
                for (String raw : yaml.getStringList(key)) {
                    targets.add(UUID.fromString(raw));
                }
                favorites.put(viewer, targets);
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("Favoris ignorés dans favorites.yml : " + key);
            }
        }
        return favorites;
    }

    private Map<UUID, Map<String, Integer>> readVisits() {
        Map<UUID, Map<String, Integer>> visits = new HashMap<>();
        File file = new File(plugin.getDataFolder(), "warp_visits.yml");
        if (!file.isFile()) {
            return visits;
        }
        ConfigurationSection section = YamlConfiguration.loadConfiguration(file).getConfigurationSection("visits");
        if (section == null) {
            return visits;
        }
        for (String ownerKey : section.getKeys(false)) {
            ConfigurationSection warps = section.getConfigurationSection(ownerKey);
            if (warps == null) {
                continue;
            }
            try {
                UUID owner = UUID.fromString(ownerKey);
                Map<String, Integer> counts = new HashMap<>();
                for (String warp : warps.getKeys(false)) {
                    counts.put(warp.toLowerCase(Locale.ROOT), warps.getInt(warp));
                }
                visits.put(owner, counts);
            } catch (IllegalArgumentException invalid) {
                plugin.getLogger().warning("Visites ignorées dans warp_visits.yml : " + ownerKey);
            }
        }
        return visits;
    }

    private List<WarpDatabase.FeaturedRow> readFeatured() {
        List<WarpDatabase.FeaturedRow> rows = new ArrayList<>();
        File file = new File(plugin.getDataFolder(), "featured_warps.yml");
        if (!file.isFile()) {
            return rows;
        }
        for (String raw : YamlConfiguration.loadConfiguration(file).getStringList("featured-list")) {
            FeaturedWarpsManager.FeaturedWarpEntry entry = FeaturedWarpsManager.FeaturedWarpEntry.fromSaveString(raw);
            if (entry != null && !entry.isExpired()) {
                rows.add(new WarpDatabase.FeaturedRow(entry.ownerUUID, entry.warpName, entry.expiryTimestamp));
            }
        }
        return rows;
    }

    // ------------------------------------------------------------------ fil d'écriture

    /** Écrit avec quelques essais : seules des écritures qui peuvent être rejouées sans effet (idempotentes) passent ici. */
    private void write(SqlTask task, String what) {
        for (int attempt = 1; attempt <= WRITE_ATTEMPTS; attempt++) {
            try {
                task.run();
                return;
            } catch (SQLException e) {
                if (attempt == WRITE_ATTEMPTS) {
                    plugin.getLogger().log(Level.WARNING, what + " non écrit en base : " + e.getMessage());
                    return;
                }
                try {
                    Thread.sleep(200L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /** Futur terminé quand toutes les écritures déjà demandées sont faites (le fil d'écriture les traite dans l'ordre). */
    private CompletableFuture<Void> flush() {
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            io.execute(() -> done.complete(null));
        } catch (RejectedExecutionException stopped) {
            done.complete(null);
        }
        return done;
    }

    private void submit(Runnable task) {
        try {
            io.execute(() -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    plugin.getLogger().log(Level.WARNING, "Erreur du mode réseau : " + e);
                }
            });
        } catch (RejectedExecutionException stopped) {
            // plugin arrêté : plus rien à écrire
        }
    }

    private void runMain(Runnable task) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (IllegalPluginAccessException disabled) {
            // plugin arrêté
        }
    }
}
