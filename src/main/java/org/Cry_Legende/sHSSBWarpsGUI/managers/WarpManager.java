package org.Cry_Legende.sHSSBWarpsGUI.managers;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WarpManager {

    private final SHSSBWarpsGUI plugin;

    public WarpManager(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    public static class PlayerWarpData {
        private final OfflinePlayer owner;
        private final String ownerName;
        private final List<IslandWarp> warps;
        private final boolean isOnline;
        private final boolean hasFeaturedWarp;

        public PlayerWarpData(OfflinePlayer owner, String ownerName, List<IslandWarp> warps, boolean isOnline, boolean hasFeaturedWarp) {
            this.owner = owner;
            this.ownerName = ownerName;
            this.warps = warps;
            this.isOnline = isOnline;
            this.hasFeaturedWarp = hasFeaturedWarp;
        }

        public OfflinePlayer getOwner() {
            return owner;
        }

        /**
         * Nom du propriétaire lu dans SuperiorSkyblock2 (en mémoire). OfflinePlayer#getName relit le fichier du
         * joueur sur le disque pour un joueur hors ligne : à ne jamais appeler dans un tri.
         */
        public String getOwnerName() {
            return ownerName;
        }

        public List<IslandWarp> getWarps() {
            return warps;
        }

        public boolean isOnline() {
            return isOnline;
        }

        public boolean hasFeaturedWarp() {
            return hasFeaturedWarp;
        }
    }

    /**
     * Warps publics par propriétaire d'île. Le parcours de toutes les îles (SuperiorSkyblock2) se fait hors du thread
     * principal ; le reste (joueurs hors ligne, état en ligne, mises en avant) utilise l'API Bukkit et se fait sur le
     * thread principal : le futur retourné se termine donc sur le thread principal.
     */
    public CompletableFuture<List<PlayerWarpData>> getAllWarpsSorted() {
        CompletableFuture<List<OwnerWarps>> collected = new CompletableFuture<>();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                collected.complete(collectPublicWarps());
            } catch (Throwable t) {
                collected.completeExceptionally(t);
            }
        });
        return collected.thenApplyAsync(this::buildSortedData, runnable -> {
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
            } else if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, runnable);
            }
        });
    }

    // Propriétaire d'île et ses warps publics, nom compris : le nom vient de SuperiorSkyblock2, sans accès disque
    private record OwnerWarps(UUID ownerId, String ownerName, List<IslandWarp> warps) {
    }

    private List<OwnerWarps> collectPublicWarps() {
        List<Island> islands = SuperiorSkyblockAPI.getGrid().getIslands();
        List<OwnerWarps> result = new ArrayList<>();

        for (Island island : islands) {
            if (island.getOwner() == null || island.getOwner().getUniqueId() == null) {
                plugin.getLogger().fine("getAllWarpsSorted: Île sans propriétaire ou UUID de propriétaire null, ignorée: " + (island.getUniqueId() != null ? island.getUniqueId() : "UUID île inconnu"));
                continue;
            }

            Map<String, IslandWarp> islandWarpsMap = island.getIslandWarps();
            if (islandWarpsMap.isEmpty()) {
                continue;
            }

            List<IslandWarp> accessibleWarps = islandWarpsMap.values().stream()
                .filter(warp -> !warp.hasPrivateFlag())
                .collect(Collectors.toList());

            if (!accessibleWarps.isEmpty()) {
                result.add(new OwnerWarps(island.getOwner().getUniqueId(), island.getOwner().getName(), accessibleWarps));
            }
        }
        return result;
    }

    private List<PlayerWarpData> buildSortedData(List<OwnerWarps> collected) {
        List<PlayerWarpData> playerDataList = new ArrayList<>();
        FeaturedWarpsManager fwm = plugin.getFeaturedWarpsManager();

        for (OwnerWarps entry : collected) {
            OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.ownerId());
            List<IslandWarp> accessibleWarps = entry.warps();
            boolean isAnyWarpFeatured = accessibleWarps.stream()
                .anyMatch(w -> fwm.isFeatured(entry.ownerId(), w.getName()));
            // en ligne ici ou, avec SHNetwork, sur un autre serveur du réseau (présence tenue en mémoire)
            boolean online = owner.isOnline() || plugin.isRemoteOnline(entry.ownerId());
            playerDataList.add(new PlayerWarpData(owner, entry.ownerName(), accessibleWarps, online, isAnyWarpFeatured));
        }

        // Mis en avant d'abord, puis en ligne, puis par nom ; les noms inconnus en dernier
        playerDataList.sort(Comparator
            .comparing((PlayerWarpData d) -> !d.hasFeaturedWarp())
            .thenComparing(d -> !d.isOnline())
            .thenComparing(PlayerWarpData::getOwnerName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return playerDataList;
    }
}
