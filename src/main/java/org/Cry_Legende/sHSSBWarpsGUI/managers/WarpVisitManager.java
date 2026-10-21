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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WarpVisitManager {

    private final SHSSBWarpsGUI plugin;
    private final File visitsFile;
    private FileConfiguration visitsConfig;

    private final Map<UUID, Map<String, Integer>> visitCounts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> visitCooldowns = new ConcurrentHashMap<>();

    private final long cooldownMillis;

    public WarpVisitManager(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
        this.visitsFile = new File(plugin.getDataFolder(), "warp_visits.yml");
        // Correction ici pour utiliser la variable qui est maintenant correctement chargée
        this.cooldownMillis = plugin.getConfigManager().visitCounterCooldownSeconds * 1000L;
        loadVisits();
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
        visitsConfig = YamlConfiguration.loadConfiguration(visitsFile);
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

    public void saveVisits() {
        if (visitsConfig == null) return;
        visitsConfig.set("visits", null);
        ConfigurationSection visitsSection = visitsConfig.createSection("visits");

        visitCounts.forEach((ownerUuid, warpMap) -> {
            ConfigurationSection ownerSection = visitsSection.createSection(ownerUuid.toString());
            warpMap.forEach(ownerSection::set);
        });

        try {
            visitsConfig.save(visitsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder le fichier warp_visits.yml: " + e.getMessage());
        }
    }

    public int getVisitCount(UUID ownerUuid, String warpName) {
        return visitCounts.getOrDefault(ownerUuid, new ConcurrentHashMap<>()).getOrDefault(warpName.toLowerCase(), 0);
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

        visitCooldowns.get(visitorUuid).put(uniqueWarpId, now + this.cooldownMillis);
    }
}