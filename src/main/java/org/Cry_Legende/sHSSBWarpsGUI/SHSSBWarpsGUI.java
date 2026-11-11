package org.Cry_Legende.sHSSBWarpsGUI;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblock;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.commands.CommandManager;
import org.Cry_Legende.sHSSBWarpsGUI.commands.AdminCommand;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.config.Messages;
import org.Cry_Legende.sHSSBWarpsGUI.dialog.WarpDialogs;
import org.Cry_Legende.sHSSBWarpsGUI.gui.GuiListener;
import org.Cry_Legende.sHSSBWarpsGUI.hooks.PAPIHook;
import org.Cry_Legende.sHSSBWarpsGUI.hooks.VaultHook;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FavoritesManager; // Import ajouté
import org.Cry_Legende.sHSSBWarpsGUI.managers.FeaturedWarpsManager;
import org.Cry_Legende.sHSSBWarpsGUI.managers.WarpManager;
import org.Cry_Legende.sHSSBWarpsGUI.managers.WarpVisitManager;
import org.Cry_Legende.sHSSBWarpsGUI.network.NetworkLink;
import org.Cry_Legende.sHSSBWarpsGUI.network.NetworkWarps;
import org.Cry_Legende.sHSSBWarpsGUI.network.ShNetworkLink;
import org.Cry_Legende.sHSSBWarpsGUI.network.WarpDatabase;
import org.Cry_Legende.sHSSBWarpsGUI.utils.PlayerChatInput;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public class SHSSBWarpsGUI extends JavaPlugin {

    private static SHSSBWarpsGUI instance;
    private ConfigManager configManager;
    // Textes de messages.yml, avec les défauts du jar
    private volatile YamlConfiguration texts;
    private WarpManager warpManager;
    private WarpVisitManager warpVisitManager;
    private FeaturedWarpsManager featuredWarpsManager;
    private FavoritesManager favoritesManager; // Variable ajoutée
    private VaultHook vaultHook;
    private PAPIHook papiHook;
    private GuiListener guiListenerInstance;
    private PlayerChatInput playerChatInputListener;
    // null sur un serveur sans dialogues (avant 1.21.6) : tout passe alors par le chat
    private WarpDialogs warpDialogs;
    // Non nul seulement avec SHNetwork : favoris, visites et mises en avant passent par la base commune
    private NetworkWarps network;

    @Override
    public void onEnable() {
        instance = this;

        // Dépendance obligatoire
        if (Bukkit.getPluginManager().getPlugin("SuperiorSkyblock2") == null) {
            getLogger().severe("SuperiorSkyblock2 n'est pas trouvé ! Le plugin va se désactiver.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Initialisation des managers et services
        this.configManager = new ConfigManager(this);
        saveDefaultConfig();
        // Crée messages.yml au premier démarrage, avec les textes des anciennes sections de config.yml
        Messages.prepare(this);
        try {
            texts = Messages.load(this);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().warning("messages.yml illisible, textes du jar utilisés : " + e.getMessage());
            texts = Messages.defaults(this);
        }
        configManager.loadConfigs();

        this.featuredWarpsManager = new FeaturedWarpsManager(this, new File(getDataFolder(), "featured_warps.yml"));

        // Initialisation du gestionnaire de favoris
        this.favoritesManager = new FavoritesManager(this);

        this.warpManager = new WarpManager(this);
        this.warpVisitManager = new WarpVisitManager(this);

        // Configuration des hooks et enregistrement des composants
        setupHooks();
        registerCommands();
        registerListeners();
        enableNetwork();

        getLogger().info("SHSSBWarpsGUI a été activé avec succès !");
    }

    /**
     * Passe en mode réseau si SHNetwork est installé et 'reseau.actif' est vrai. Les classes qui touchent à SHNetwork
     * ne sont résolues qu'ici : sans lui, rien n'est chargé et le plugin garde son fonctionnement d'origine.
     */
    private void enableNetwork() {
        if (!getConfig().getBoolean("reseau.actif", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("SHNetwork")) return;
        WarpDatabase db = null;
        try {
            NetworkLink link = ShNetworkLink.open();
            db = new WarpDatabase(
                getConfig().getString("reseau.base-de-donnees.url"),
                getConfig().getString("reseau.base-de-donnees.utilisateur"),
                getConfig().getString("reseau.base-de-donnees.mot-de-passe"),
                Math.max(250, getConfig().getInt("reseau.base-de-donnees.delai-ms", 3000)));
            NetworkWarps warps = new NetworkWarps(this, db, link);
            warps.start();
            network = warps;
            getLogger().info("SHNetwork détecté : favoris, visites et warps mis en avant partagés entre les serveurs, en base."
                + (warps.followsIslandHosts() ? " Warps des îles hébergées ailleurs : joueur envoyé sur leur serveur." : ""));
        } catch (RuntimeException | LinkageError e) {
            network = null;
            if (db != null) db.close();
            getLogger().log(Level.SEVERE, "SHNetwork présent mais inutilisable, favoris, visites et mises en avant restent dans les fichiers de ce serveur : " + e);
        }
    }

    @Override
    public void onDisable() {
        // Écritures en base terminées d'abord : les sauvegardes de fichiers ci-dessous ne font rien en mode réseau
        if (network != null) {
            network.stop();
        }
        // Sauvegarde des données et arrêt des tâches
        if (featuredWarpsManager != null) {
            featuredWarpsManager.saveFeaturedWarps();
            featuredWarpsManager.stopExpiryCheckTask();
        }
        if (favoritesManager != null) { // Sauvegarde des favoris
            favoritesManager.saveFavorites();
        }
        if (warpVisitManager != null) {
            warpVisitManager.saveVisits();
        }
        if (playerChatInputListener != null) {
            PlayerChatInput.clearAllPendingActions();
        }
        if (warpDialogs != null) {
            warpDialogs.clear();
        }
        getLogger().info("SHSSBWarpsGUI a été désactivé.");
    }

    /**
     * Configure les hooks pour Vault (économie) et PlaceholderAPI.
     */
    private void setupHooks() {
        this.vaultHook = new VaultHook(this);
        if (!vaultHook.setupEconomy()) {
            getLogger().warning("Vault n'a pas pu être hook ou aucune économie n'a été trouvée.");
        } else {
            getLogger().info("Vault détecté et hooké avec succès.");
        }

        this.papiHook = new PAPIHook();
        if (papiHook.isEnabled()) {
            getLogger().info("PlaceholderAPI détecté et hooké.");
        } else {
            getLogger().info("PlaceholderAPI non détecté.");
        }
    }

    /**
     * Enregistre les commandes définies dans le plugin.yml.
     */
    private void registerCommands() {
        // Commande d'administration /shssbwarpsgui (anciens noms : /swgadmin, /shssbwarps)
        AdminCommand adminCommand = new AdminCommand(this);
        Objects.requireNonNull(getCommand("shssbwarpsgui")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("shssbwarpsgui")).setTabCompleter(adminCommand);
    }

    /**
     * Relit config.yml et messages.yml. Les deux sont lus à part et ne remplacent l'état en cours que s'ils sont
     * valides ; sinon l'ancienne configuration reste active. Les fichiers de données (favoris, visites, warps mis en
     * avant) et la base ne sont pas touchés ; la section reseau ne se recharge pas à chaud.
     */
    public void reload() throws ReloadException {
        String file = "config.yml";
        YamlConfiguration loaded;
        try {
            new YamlConfiguration().load(new File(getDataFolder(), file));
            file = "messages.yml";
            loaded = Messages.load(this);
        } catch (IOException | InvalidConfigurationException e) {
            throw new ReloadException(file, e);
        }
        texts = loaded;
        configManager.loadConfigs();
    }

    /** Fichier de configuration illisible pendant un reload. */
    public static final class ReloadException extends Exception {

        private final String file;
        private final String detail;

        ReloadException(String file, Throwable cause) {
            super(file + " : " + firstLine(cause), cause);
            this.file = file;
            this.detail = firstLine(cause);
        }

        public String file() {
            return file;
        }

        public String detail() {
            return detail;
        }

        private static String firstLine(Throwable cause) {
            String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
            return message.lines().findFirst().orElse("");
        }
    }

    /** Textes de messages.yml (les défauts du jar complètent les clés absentes). */
    public YamlConfiguration getTexts() {
        return texts;
    }

    /**
     * Enregistre tous les listeners nécessaires au plugin.
     */
    private void registerListeners() {
        // Enregistre le listener qui intercepte les commandes /is pour nous
        Bukkit.getPluginManager().registerEvents(new CommandManager(this), this);

        // Enregistre les listeners pour les GUIs et la saisie de chat
        this.guiListenerInstance = new GuiListener(this);
        Bukkit.getPluginManager().registerEvents(this.guiListenerInstance, this);

        this.playerChatInputListener = new PlayerChatInput(this);

        // Saisies par dialogue natif (clients 1.21.6+) ; indisponibles avant 1.21.6, le chat reste alors la seule saisie
        try {
            Class.forName("io.papermc.paper.event.player.PlayerCustomClickEvent");
            this.warpDialogs = new WarpDialogs(this);
        } catch (ClassNotFoundException | LinkageError e) {
            this.warpDialogs = null;
            getLogger().info("Dialogues indisponibles sur ce serveur : saisie dans le chat.");
        }
    }

    // --- Getters pour accéder aux composants du plugin ---

    public static SHSSBWarpsGUI getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public WarpManager getWarpManager() {
        return warpManager;
    }

    public WarpVisitManager getWarpVisitManager() {
        return warpVisitManager;
    }

    public FeaturedWarpsManager getFeaturedWarpsManager() {
        return featuredWarpsManager;
    }

    public FavoritesManager getFavoritesManager() { // Getter ajouté
        return favoritesManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public PAPIHook getPapiHook() {
        return papiHook;
    }

    /** Mode réseau, ou null sans SHNetwork. */
    public NetworkWarps getNetwork() {
        return network;
    }

    /** Vrai si le joueur est connecté sur un autre serveur du réseau (toujours faux sans SHNetwork). */
    public boolean isRemoteOnline(UUID id) {
        return network != null && network.isOnline(id);
    }

    /**
     * Vrai si le joueur est en ligne, ici ou sur un autre serveur du réseau, et visible pour {@code viewer} (un joueur
     * invisible sur ce serveur ne l'est pas ; l'invisibilité d'un joueur d'un autre serveur n'est pas connue).
     */
    public boolean isVisibleOnline(Player viewer, UUID id) {
        Player local = Bukkit.getPlayer(id);
        if (local != null) {
            return viewer.canSee(local);
        }
        return isRemoteOnline(id);
    }

    /** Pseudos des joueurs des autres serveurs (vide sans SHNetwork). */
    public Collection<String> remoteOnlineNames() {
        return network == null ? List.of() : network.onlineNames();
    }

    /**
     * Téléporte le joueur au warp : téléportation de SuperiorSkyblock2, ou, avec SHNetwork, envoi vers le serveur qui
     * héberge l'île du warp.
     */
    public void warpTo(SuperiorPlayer sPlayer, Island island, IslandWarp warp) {
        NetworkWarps warps = network;
        if (warps == null) {
            island.warpPlayer(sPlayer, warp.getName());
        } else {
            warps.warp(sPlayer, island, warp);
        }
    }

    public SuperiorSkyblock getSuperiorSkyblock() {
        return SuperiorSkyblockAPI.getSuperiorSkyblock();
    }

    public GuiListener getGuiListener() {
        return guiListenerInstance;
    }

    /**
     * Saisies par dialogue natif, ou null si le serveur ne les gère pas.
     */
    public WarpDialogs getWarpDialogs() {
        return warpDialogs;
    }
}