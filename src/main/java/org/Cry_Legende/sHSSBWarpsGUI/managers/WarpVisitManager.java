package org.Cry_Legende.sHSSBWarpsGUI.managers;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class WarpVisitManager {

    private final SHSSBWarpsGUI plugin;
    private final File visitsFile;

    private final Map<UUID, Map<String, Integer>> visitCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> visitCooldowns = new ConcurrentHashMap<>();

    private final long cooldownMillis;
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    public WarpVisitManager(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
        this.visitsFile = new File(plugin.getDataFolder(), "warp_visits.yml");
        // Correction ici pour utiliser la variable qui est maintenant correctement chargée
        this.cooldownMillis = plugin.getConfigManager().visitCounterCooldownSeconds * 1000L;
        loadVisits();
        startMaintenanceTask();
    }

    public void loadVisits() {
        if (!visitsFile.exists()) {
            try {
                visitsFile.getParentFile().mkdirs();
                visitsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier warp_visits.yml: " + e.getMessage());
                return;
            }
        }
        FileConfiguration visitsConfig = YamlConfiguration.loadConfiguration(visitsFile);
        visitCounts.clear();
        ConfigurationSection visitsSection = visitsConfig.getConfigurationSection("visits");
        if (visitsSection != null) {
            for (String ownerUuidStr : visitsSection.getKeys(false)) {
                try {
                    UUID ownerUuid = UUID.fromString(ownerUuidStr);
                    ConfigurationSection warpsSection = visitsSection.getConfigurationSection(ownerUuidStr);
                    if (warpsSection != null) {
                        Map<String, Integer> warpVisits = new ConcurrentHashMap<>();
                        for (String warpName : warpsSection.getKeys(false)) {
                            warpVisits.put(warpName.toLowerCase(), warpsSection.getInt(warpName));
                        }
                        visitCounts.put(ownerUuid, warpVisits);
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("UUID invalide dans warp_visits.yml: " + ownerUuidStr);
                }
            }
        }
        plugin.getLogger().info(visitCounts.size() + " enregistrements de visites de warps chargés.");
    }

    /**
     * Écrit les compteurs sur le disque (copie des compteurs en mémoire, fichier temporaire puis remplacement : un arrêt
     * brutal ne tronque pas le fichier). Peut être appelée hors du thread principal.
     */
    public synchronized void saveVisits() {
        dirty.set(false);
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection visitsSection = config.createSection("visits");

        visitCounts.forEach((ownerUuid, warpMap) -> {
            ConfigurationSection ownerSection = visitsSection.createSection(ownerUuid.toString());
            warpMap.forEach(ownerSection::set);
        });

        try {
            File tmp = new File(visitsFile.getParentFile(), visitsFile.getName() + ".tmp");
            Files.writeString(tmp.toPath(), config.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), visitsFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), visitsFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            dirty.set(true);
            plugin.getLogger().severe("Impossible de sauvegarder le fichier warp_visits.yml: " + e.getMessage());
        }
    }

    /**
     * Toutes les cinq minutes : sauvegarde si des visites ont été comptées (un arrêt brutal du serveur ne perd plus toute
     * la session) et oubli des cooldowns expirés (sans cela la table grossirait avec chaque couple visiteur/warp).
     */
    private void startMaintenanceTask() {
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            long now = System.currentTimeMillis();
            visitCooldowns.forEach((visitor, warps) -> {
                warps.values().removeIf(expiry -> now >= expiry);
                if (warps.isEmpty()) {
                    visitCooldowns.remove(visitor, warps);
                }
            });
            if (dirty.get()) {
                saveVisits();
            }
        }, 20L * 300, 20L * 300);
    }

    public int getVisitCount(UUID ownerUuid, String warpName) {
        Map<String, Integer> ownerWarps = visitCounts.get(ownerUuid);
        return ownerWarps == null ? 0 : ownerWarps.getOrDefault(warpName.toLowerCase(), 0);
    }

    public void incrementVisitCount(Player visitor, IslandWarp warp) {
        // Correction ici pour utiliser la variable qui est maintenant correctement chargée
        if (!plugin.getConfigManager().visitCounterEnabled) {
            return;
        }

        Island island = warp.getIsland();
        if (island == null || island.getOwner() == null) {
            return;
        }

        // --- CORRECTION DE L'ERREUR DE TYPE ---
        SuperiorPlayer sVisitor = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(visitor);
        if (sVisitor == null) {
            return; // Ne devrait pas arriver si le joueur est en ligne, mais sécurité
        }
        // Utiliser sVisitor (SuperiorPlayer) au lieu de visitor (Player)
        if (island.isMember(sVisitor)) {
            return;
        }
        // --- FIN DE LA CORRECTION ---

        UUID visitorUuid = visitor.getUniqueId();
        UUID ownerUuid = island.getOwner().getUniqueId();
        String warpName = warp.getName().toLowerCase();
        String uniqueWarpId = ownerUuid + ":" + warpName;

        long now = System.currentTimeMillis();
        long expiry = visitCooldowns.computeIfAbsent(visitorUuid, k -> new ConcurrentHashMap<>()).getOrDefault(uniqueWarpId, 0L);

        if (now < expiry) {
            return; // En cooldown
        }

        Map<String, Integer> ownerWarps = visitCounts.computeIfAbsent(ownerUuid, k -> new ConcurrentHashMap<>());
        int newCount = ownerWarps.getOrDefault(warpName, 0) + 1;
        ownerWarps.put(warpName, newCount);
        dirty.set(true);

        visitCooldowns.computeIfAbsent(visitorUuid, k -> new ConcurrentHashMap<>()).put(uniqueWarpId, now + this.cooldownMillis);
    }
}