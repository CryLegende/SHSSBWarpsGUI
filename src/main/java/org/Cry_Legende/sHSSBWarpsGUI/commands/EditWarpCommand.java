package org.Cry_Legende.sHSSBWarpsGUI.commands;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class EditWarpCommand implements CommandExecutor, TabCompleter {

    private final SHSSBWarpsGUI plugin;

    public EditWarpCommand(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            ChatUtils.sendMessage(sender, plugin.getConfigManager().msgPlayerOnlyCommand);
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("shssbwarpsgui.warp.edit")) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgNoPermissionCommand);
            return true;
        }

        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
        ConfigManager cfgMgr = plugin.getConfigManager();

        if (sPlayer == null || sPlayer.getIsland() == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgIslandNotFound);
            return true;
        }

        if (args.length < 2) {
            ChatUtils.sendMessage(player, "&cUsage: /" + label + " <warp> <option> [valeurs...]");
            return true;
        }

        String warpName = args[0];
        String option = args[1].toLowerCase();
        Island island = sPlayer.getIsland();
        IslandWarp warp = island.getWarp(warpName);

        if (warp == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgWarpNotFound.replace("%warp_name%", warpName));
            return true;
        }

        switch (option) {
            case "confirmicon":
                handleConfirmIcon(player, warp);
                break;
            case "rename":
                if (args.length < 3) {
                    ChatUtils.sendMessage(player, "&cUsage: /" + label + " " + warpName + " rename <nouveauNom>");
                    return true;
                }
                String newWarpName = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                handleRenameWarp(player, warp, newWarpName);
                break;
            default:
                ChatUtils.sendMessage(player, "&cOption '" + option + "' inconnue. Options valides: confirmicon, rename.");
                break;
        }
        return true;
    }

    private void handleConfirmIcon(Player player, IslandWarp warp) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        if (itemInHand.getType() == Material.AIR) {
            ChatUtils.sendMessage(player, "&cVous devez tenir un item dans votre main pour définir l'icône.");
            return;
        }

        ItemStack newIcon = itemInHand.clone();
        ItemMeta newMeta = newIcon.getItemMeta();

        // 1. SUPPRIMER LE LORE DE L'ITEM
        if (newMeta != null) {
            newMeta.setLore(null);
            newIcon.setItemMeta(newMeta);
        }

        // 2. Récupérer l'ancienne icône pour préserver la DESCRIPTION DU WARP
        ItemStack oldIcon = warp.getRawIcon();

        if (oldIcon != null && oldIcon.hasItemMeta() && oldIcon.getItemMeta().hasLore()) {
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;
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
}
