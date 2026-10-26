package org.Cry_Legende.sHSSBWarpsGUI.commands;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.gui.BaseGUI;
import org.Cry_Legende.sHSSBWarpsGUI.gui.MainWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.gui.PlayerSpecificWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.managers.WarpVisitManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class IslandCommand {

    private final SHSSBWarpsGUI plugin;

    public IslandCommand(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    // --- LOGIQUE D'EXÉCUTION DES COMMANDES ---

    public void handleWarpsCommand(Player player, String[] args) {
        if (args.length == 0) {
            if (!player.hasPermission("shssbwarpsgui.use")) {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgNoPermissionCommand);
                return;
            }
            openMainGui(player);
        } else {
            if (!player.hasPermission("shssbwarpsgui.warps.list.other")) {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgNoPermissionCommand);
                return;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (target == null || !target.hasPlayedBefore()) {
                ChatUtils.sendMessage(player, "&cLe joueur '" + args[0] + "' n'a jamais joué sur le serveur.");
                return;
            }
            openPlayerWarpsGui(player, target, new MainWarpsGUI(plugin, player, new ArrayList<>()));
        }
    }

    public void handleWarpCommand(Player player, String[] args) {
        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
        if (sPlayer == null || sPlayer.getIsland() == null) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgIslandNotFound);
            return;
        }
        Island island = sPlayer.getIsland();

        if (args.length == 0) {
            if (!player.hasPermission("shssbwarpsgui.warp.self")) {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgNoPermissionCommand);
                return;
            }
            List<IslandWarp> warps = new ArrayList<>(island.getIslandWarps().values());
            if (warps.isEmpty()) {
                ChatUtils.sendMessage(player, "&cVous n'avez aucun warp sur votre île.");
                return;
            }
            openPlayerWarpsGui(player, player, new MainWarpsGUI(plugin, player, new ArrayList<>()));
        } else {
            if (!player.hasPermission("shssbwarpsgui.warp.teleport")) {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgNoPermissionCommand);
                return;
            }
            String warpName = args[0];
            IslandWarp targetWarp = island.getWarp(warpName);
            if (targetWarp == null) {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgWarpNotFound.replace("%warp_name%", warpName));
                return;
            }
            teleportToWarp(sPlayer, targetWarp);
        }
    }

    public void handleEditWarpCommand(Player player, String[] args) {
        if (!player.hasPermission("shssbwarpsgui.warp.edit")) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgNoPermissionCommand);
            return;
        }
        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
        if (sPlayer == null || sPlayer.getIsland() == null) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgIslandNotFound);
            return;
        }
        if (args.length < 2) {
            ChatUtils.sendMessage(player, "&cUsage: /is editwarp <warp> <option> [valeurs...]");
            return;
        }
        String warpName = args[0];
        String option = args[1].toLowerCase();
        Island island = sPlayer.getIsland();
        IslandWarp warp = island.getWarp(warpName);
        if (warp == null) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgWarpNotFound.replace("%warp_name%", warpName));
            return;
        }
        switch (option) {
            case "confirmicon":
                handleConfirmIcon(player, warp);
                break;
            case "rename":
                if (args.length < 3) {
                    ChatUtils.sendMessage(player, "&cUsage: /is editwarp " + warpName + " rename <nouveauNom>");
                    return;
                }
                String newWarpName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleRenameWarp(player, warp, newWarpName);
                break;
            default:
                ChatUtils.sendMessage(player, "&cOption '" + option + "' inconnue. Options valides: confirmicon, rename.");
                break;
        }
    }

    // --- LOGIQUE DE TAB-COMPLETION ---

    public List<String> getWarpsTabComplete(Player player, String[] args) {
        if (args.length == 1 && player.hasPermission("shssbwarpsgui.warps.list.other")) {
            String input = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                .filter(p -> player.canSee(p))
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    public List<String> getWarpTabComplete(Player player, String[] args) {
        if (args.length == 1 && player.hasPermission("shssbwarpsgui.warp.teleport")) {
            SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
            if (sPlayer != null && sPlayer.getIsland() != null) {
                String input = args[0].toLowerCase();
                return sPlayer.getIsland().getIslandWarps().keySet().stream()
                    .filter(warpName -> warpName.toLowerCase().startsWith(input))
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    public List<String> getEditWarpTabComplete(Player player, String[] args) {
        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
        if (sPlayer == null || sPlayer.getIsland() == null) return Collections.emptyList();

        if (args.length == 1) {
            String input = args[0].toLowerCase();
            return sPlayer.getIsland().getIslandWarps().keySet().stream()
                .filter(warpName -> warpName.toLowerCase().startsWith(input))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            String input = args[1].toLowerCase();
            List<String> options = Arrays.asList("rename", "confirmicon");
            return options.stream()
                .filter(opt -> opt.startsWith(input))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }

    // --- METHODES UTILITAIRES ---

    private void handleConfirmIcon(Player player, IslandWarp warp) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            ChatUtils.sendMessage(player, "&cVous devez tenir un item dans votre main pour définir l'icône.");
            return;
        }

        ItemStack newIcon = itemInHand.clone();
        ItemMeta newMeta = newIcon.getItemMeta();

        // 1. SUPPRIMER LE LORE DE L'ITEM (stats, etc.) pour ne garder que l'aspect visuel
        if (newMeta != null) {
            newMeta.setLore(null);
            newIcon.setItemMeta(newMeta);
        }

        // 2. Récupérer l'ancienne icône pour préserver la DESCRIPTION DU WARP
        ItemStack oldIcon = warp.getRawIcon();

        if (oldIcon != null && oldIcon.hasItemMeta() && oldIcon.getItemMeta().hasLore()) {
            // On récupère le meta à nouveau car on vient de le modifier
            newMeta = newIcon.getItemMeta();
            if (newMeta != null) {
                newMeta.setLore(oldIcon.getItemMeta().getLore());
                newIcon.setItemMeta(newMeta);
            }
        }

        warp.setIcon(newIcon);
        ChatUtils.sendMessage(player, cfgMgr.msgWarpIconSet.replace("%warp_name%", warp.getName()));
    }

    private void handleRenameWarp(Player player, IslandWarp warp, String newName) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        Island island = warp.getIsland();
        int maxLength = plugin.getSuperiorSkyblock().getSettings().getIslandNames().getMaxLength();
        if (newName.isEmpty() || ChatColor.stripColor(ChatUtils.color(newName)).length() > maxLength) {
            ChatUtils.sendMessage(player, "&cLe nouveau nom du warp est invalide ou trop long (max " + maxLength + " caractères visibles).");
            return;
        }
        if (island.getWarp(newName) != null && !newName.equalsIgnoreCase(warp.getName())) {
            ChatUtils.sendMessage(player, "&cUn warp nommé '&e" + newName + "&c' existe déjà sur votre île.");
            return;
        }
        String oldName = warp.getName();
        island.renameWarp(warp, newName);
        String message = cfgMgr.msgWarpRenamed.replace("%old_warp_name%", oldName).replace("%new_warp_name%", newName);
        ChatUtils.sendMessage(player, message);
    }

    public void openMainGui(Player player) {
        plugin.getWarpManager().getAllWarpsSorted().thenAcceptAsync(allWarps -> {
            if (!player.isOnline()) {
                return; // parti pendant le chargement de la liste
            }
            if (allWarps.isEmpty()) {
                ChatUtils.sendMessage(player, "&cAucun warp n'est disponible pour le moment.");
                return;
            }

            WarpVisitManager warpVisitManager = plugin.getWarpVisitManager();

            allWarps.sort((pwd1, pwd2) -> {
                if (pwd1.hasFeaturedWarp() != pwd2.hasFeaturedWarp()) {
                    return pwd1.hasFeaturedWarp() ? -1 : 1;
                }
                Player owner1 = Bukkit.getPlayer(pwd1.getOwner().getUniqueId());
                Player owner2 = Bukkit.getPlayer(pwd2.getOwner().getUniqueId());
                boolean p1IsVisibleOnline = pwd1.isOnline() && owner1 != null && player.canSee(owner1);
                boolean p2IsVisibleOnline = pwd2.isOnline() && owner2 != null && player.canSee(owner2);
                if (p1IsVisibleOnline != p2IsVisibleOnline) {
                    return p1IsVisibleOnline ? -1 : 1;
                }
                int totalVisits1 = pwd1.getWarps().stream()
                    .mapToInt(w -> warpVisitManager.getVisitCount(pwd1.getOwner().getUniqueId(), w.getName()))
                    .sum();
                int totalVisits2 = pwd2.getWarps().stream()
                    .mapToInt(w -> warpVisitManager.getVisitCount(pwd2.getOwner().getUniqueId(), w.getName()))
                    .sum();

                if (totalVisits1 != totalVisits2) {
                    return Integer.compare(totalVisits2, totalVisits1);
                }
                String name1 = pwd1.getOwner().getName();
                String name2 = pwd2.getOwner().getName();
                if (name1 == null && name2 == null) return 0;
                if (name1 == null) return 1;
                if (name2 == null) return -1;
                return name1.compareToIgnoreCase(name2);
            });

            new MainWarpsGUI(plugin, player, allWarps).open();
        }, runnable -> {
            if (Bukkit.isPrimaryThread()) {
                runnable.run();
            } else if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, runnable);
            }
        }).exceptionally(error -> {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Impossible de construire la liste des warps pour " + player.getName(), error);
            if (plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> ChatUtils.sendMessage(player, plugin.getConfigManager().msgGenericError));
            }
            return null;
        });
    }

    private void openPlayerWarpsGui(Player viewer, OfflinePlayer warpOwner, BaseGUI previousGui) {
        SuperiorPlayer ssbOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(warpOwner.getUniqueId());
        if (ssbOwner == null || ssbOwner.getIsland() == null) {
            ChatUtils.sendMessage(viewer, "&cCe joueur n'a pas d'île.");
            return;
        }
        Island island = ssbOwner.getIsland();
        List<IslandWarp> warps = new ArrayList<>(island.getIslandWarps().values());
        if (!viewer.getUniqueId().equals(warpOwner.getUniqueId())) {
            warps = warps.stream().filter(w -> !w.hasPrivateFlag()).collect(Collectors.toList());
        }
        if (warps.isEmpty()) {
            ChatUtils.sendMessage(viewer, "&cCe joueur n'a pas de warps accessibles.");
            return;
        }
        new PlayerSpecificWarpsGUI(plugin, viewer, warpOwner, warps, previousGui).open();
    }

    private void teleportToWarp(SuperiorPlayer sPlayer, IslandWarp warp) {
        Player player = sPlayer.asPlayer();
        long warmupMillis = SuperiorSkyblockAPI.getSuperiorSkyblock().getSettings().getWarpsWarmup();
        long warmupSeconds = warmupMillis / 1000;
        if (warmupMillis > 0 && !player.hasPermission("superior.admin.bypass.warmup")) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgTeleportWarmup.replace("%time%", String.valueOf(warmupSeconds)));
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) warp.getIsland().warpPlayer(sPlayer, warp.getName());
                }
            }.runTaskLater(plugin, warmupMillis / 50);
        } else {
            warp.getIsland().warpPlayer(sPlayer, warp.getName());
        }
    }
}
