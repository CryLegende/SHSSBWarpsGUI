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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WarpManager {

    private final SHSSBWarpsGUI plugin;

    public WarpManager(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    public static class PlayerWarpData {
        private final OfflinePlayer owner;
        private final List<IslandWarp> warps;
        private final boolean isOnline;
        private final boolean hasFeaturedWarp;

        public PlayerWarpData(OfflinePlayer owner, List<IslandWarp> warps, boolean isOnline, boolean hasFeaturedWarp) {
            this.owner = owner;
            this.warps = warps;
            this.isOnline = isOnline;
            this.hasFeaturedWarp = hasFeaturedWarp;
        }

        public OfflinePlayer getOwner() {
            return owner;
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

    public CompletableFuture<List<PlayerWarpData>> getAllWarpsSorted() {
        return CompletableFuture.supplyAsync(() -> {
            List<Island> islands = SuperiorSkyblockAPI.getGrid().getIslands();
            List<PlayerWarpData> playerDataList = new ArrayList<>();
            FeaturedWarpsManager fwm = plugin.getFeaturedWarpsManager();

            for (Island island : islands) {
                if (island.getOwner() == null || island.getOwner().getUniqueId() == null) {
                    plugin.getLogger().fine("getAllWarpsSorted: Île sans propriétaire ou UUID de propriétaire null, ignorée: " + (island.getUniqueId() != null ? island.getUniqueId() : "UUID île inconnu"));
                    continue;
                }

                OfflinePlayer owner = Bukkit.getOfflinePlayer(island.getOwner().getUniqueId());
                if (owner == null) {
                    plugin.getLogger().warning("getAllWarpsSorted: Impossible d'obtenir OfflinePlayer pour UUID " + island.getOwner().getUniqueId() + ", île ignorée.");
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
                    boolean isAnyWarpFeatured = accessibleWarps.stream()
                        .anyMatch(w -> fwm.isFeatured(owner.getUniqueId(), w.getName()));
                    playerDataList.add(new PlayerWarpData(owner, accessibleWarps, owner.isOnline(), isAnyWarpFeatured));
                }
            }

            playerDataList.sort(new Comparator<PlayerWarpData>() {
                @Override
                public int compare(PlayerWarpData o1, PlayerWarpData o2) {
                    if (o1.hasFeaturedWarp() && !o2.hasFeaturedWarp()) return -1;
                    if (!o1.hasFeaturedWarp() && o2.hasFeaturedWarp()) return 1;

                    if (o1.isOnline() && !o2.isOnline()) return -1;
                    if (!o1.isOnline() && o2.isOnline()) return 1;

                    String name1 = o1.getOwner().getName();
                    String name2 = o2.getOwner().getName();

                    if (name1 == null && name2 == null) return 0;
                    if (name1 == null) return 1;
                    if (name2 == null) return -1;

                    return name1.compareToIgnoreCase(name2);
                }
            });
            return playerDataList;
        });
    }
}
