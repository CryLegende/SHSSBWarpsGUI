package org.Cry_Legende.sHSSBWarpsGUI.utils;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Règles partagées par la saisie dans le chat ({@link PlayerChatInput}) et la saisie par dialogue : un seul endroit
 * pour les validations, afin que les deux chemins acceptent et refusent exactement les mêmes choses.
 */
public final class WarpRules {

    private WarpRules() {
    }

    public enum RenameCheck {OK, INVALID, EXISTS}

    /**
     * Longueur maximale (en caractères visibles) d'un nom de warp : celle des noms d'île de SuperiorSkyblock2.
     */
    public static int maxNameLength(SHSSBWarpsGUI plugin) {
        return plugin.getSuperiorSkyblock().getSettings().getIslandNames().getMaxLength();
    }

    /**
     * Nom vide ou trop long, ou déjà pris par un autre warp de l'île.
     */
    public static RenameCheck checkRename(SHSSBWarpsGUI plugin, Island island, IslandWarp warp, String input) {
        String strippedNewName = ChatColor.stripColor(ChatUtils.color(input));
        if (input.isEmpty() || strippedNewName.length() > maxNameLength(plugin)) {
            return RenameCheck.INVALID;
        }
        if (island.getWarp(input) != null && !input.equalsIgnoreCase(warp.getName())) {
            return RenameCheck.EXISTS;
        }
        return RenameCheck.OK;
    }

    public static int maxLoreLines(SHSSBWarpsGUI plugin) {
        return plugin.getConfig().getInt("manage-warp-gui.max-lore-lines", 5);
    }

    /**
     * Conversion d'une saisie en lignes de description, la même que dans le chat : « // » sépare deux lignes (un retour
     * à la ligne aussi, dans un champ multiligne) et une ligne qui ne commence pas par un code couleur est grise (&amp;7).
     * Les retours à la ligne en fin de texte sont ignorés ; un texte vide donne une description vide.
     */
    public static List<String> toLoreLines(String input) {
        List<String> lines = new ArrayList<>();
        String text = input.replace("\r\n", "\n").replace('\r', '\n');
        if (text.isBlank()) {
            return lines;
        }
        while (text.endsWith("\n")) {
            text = text.substring(0, text.length() - 1);
        }
        for (String segment : text.split("\n", -1)) {
            for (String line : segment.split("//")) {
                lines.add(line.startsWith("&") ? line : "&7" + line);
            }
        }
        return lines;
    }

    /**
     * Enregistre la description (lore de l'icône) du warp, en gardant l'icône actuelle, ou un papier si elle n'en a pas.
     * Une liste vide efface la description. Les lignes utilisent les codes &amp;.
     */
    public static void applyDescription(SHSSBWarpsGUI plugin, Player player, IslandWarp warp, List<String> lines) {
        ItemStack currentIcon = warp.getRawIcon();
        if (currentIcon == null || currentIcon.getType() == Material.AIR) {
            currentIcon = new ItemStack(Material.PAPER);
            ItemMeta tempMeta = currentIcon.getItemMeta();
            if (tempMeta != null) {
                tempMeta.setDisplayName(ChatUtils.color("&fWarp: &b" + warp.getName()));
                currentIcon.setItemMeta(tempMeta);
            }
        }
        ItemMeta meta = currentIcon.getItemMeta();
        if (meta != null) {
            if (lines.isEmpty()) {
                meta.setLore(null);
            } else {
                meta.setLore(ChatUtils.color(lines));
            }
            currentIcon.setItemMeta(meta);
            warp.setIcon(currentIcon);

            String messageToSend = plugin.getConfigManager().msgWarpDescSet.replace("%warp_name%", warp.getName());
            ChatUtils.sendMessage(player, messageToSend);
        }
    }

    /**
     * Le joueur peut-il modifier les warps de cette île ? Comme à l'ouverture du menu de gestion : il est de l'île, ou
     * il a la permission du staff.
     */
    public static boolean canManage(Player player, Island island) {
        if (player.hasPermission("shssbwarpsgui.admin.editothers")) {
            return true;
        }
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
        Island own = superiorPlayer == null ? null : superiorPlayer.getIsland();
        return own != null && own.getUniqueId().equals(island.getUniqueId());
    }

    /**
     * Revérifie, au moment d'agir, que le warp existe toujours sur son île et que le joueur peut le modifier. Dit
     * pourquoi au joueur et renvoie null sinon ; renvoie le warp à jour sinon.
     */
    public static IslandWarp resolveForEdit(SHSSBWarpsGUI plugin, Player player, IslandWarp warp) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        Island island = warp.getIsland();
        if (island == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgIslandNotFound);
            return null;
        }
        IslandWarp current = island.getWarp(warp.getName());
        if (current == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgWarpNotFound.replace("%warp_name%", warp.getName()));
            return null;
        }
        if (!canManage(player, island)) {
            ChatUtils.sendMessage(player, cfgMgr.msgNotYourWarp.replace("%warp_name%", warp.getName()));
            return null;
        }
        return current;
    }
}
