package org.Cry_Legende.sHSSBWarpsGUI.utils;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PlayerChatInput implements Listener {

    private static final Map<UUID, PendingWarpActionInfo> pendingActions = new HashMap<>();
    private final SHSSBWarpsGUI plugin;

    public enum InputType {
        RENAME_WARP,
        SET_WARP_DESCRIPTION_LINE
    }

    private static class PendingWarpActionInfo {
        InputType type;
        IslandWarp warp;
        List<String> currentLoreInput;

        PendingWarpActionInfo(InputType type, IslandWarp warp) {
            this.type = type;
            this.warp = warp;
            if (type == InputType.SET_WARP_DESCRIPTION_LINE) {
                this.currentLoreInput = new ArrayList<>();
            }
        }
    }

    public PlayerChatInput(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public static void requestPlayerInput(Player player, InputType type, IslandWarp warp) {
        pendingActions.put(player.getUniqueId(), new PendingWarpActionInfo(type, warp));
    }

    public static boolean hasPendingAction(Player player) {
        return pendingActions.containsKey(player.getUniqueId());
    }

    public static void clearPendingAction(Player player) {
        pendingActions.remove(player.getUniqueId());
    }

    public static void clearAllPendingActions() {
        pendingActions.clear();
    }


    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingActions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (pendingActions.containsKey(playerId)) {
            event.setCancelled(true);
            PendingWarpActionInfo actionInfo = pendingActions.get(playerId);
            String input = event.getMessage();

            Bukkit.getScheduler().runTask(plugin, () -> {
                ConfigManager cfgMgr = plugin.getConfigManager();
                Island island = actionInfo.warp.getIsland();

                if (island == null) {
                    ChatUtils.sendMessage(player, cfgMgr.msgIslandNotFound);
                    pendingActions.remove(playerId);
                    return;
                }
                IslandWarp currentWarp = island.getWarp(actionInfo.warp.getName());
                if (currentWarp == null) {
                    String msg = cfgMgr.msgWarpNotFound.replace("%warp_name%", actionInfo.warp.getName());
                    ChatUtils.sendMessage(player, msg);
                    pendingActions.remove(playerId);
                    return;
                }
                actionInfo.warp = currentWarp;

                String messageToSend;

                if (input.equalsIgnoreCase("annuler") || input.equalsIgnoreCase("cancel")) {
                    pendingActions.remove(playerId);
                    ChatUtils.sendMessage(player, cfgMgr.msgActionCancelled);
                    return;
                }

                switch (actionInfo.type) {
                    case RENAME_WARP:
                        int maxLength = plugin.getSuperiorSkyblock().getSettings().getIslandNames().getMaxLength();
                        String strippedNewName = ChatColor.stripColor(ChatUtils.color(input));

                        if (input.isEmpty() || strippedNewName.length() > maxLength) {
                            ChatUtils.sendMessage(player, "&cLe nom du warp est invalide ou trop long (max " + maxLength + " caractères visibles). Réessayez ou tapez 'annuler'.");
                            return;
                        }
                        if (island.getWarp(input) != null && !input.equalsIgnoreCase(actionInfo.warp.getName())) {
                            ChatUtils.sendMessage(player, "&cUn warp nommé '&e" + input + "&c' existe déjà sur votre île. Réessayez ou tapez 'annuler'.");
                            return;
                        }

                        String oldName = actionInfo.warp.getName();
                        island.renameWarp(actionInfo.warp, input);

                        messageToSend = cfgMgr.msgWarpRenamed
                            .replace("%old_warp_name%", oldName)
                            .replace("%new_warp_name%", input);
                        ChatUtils.sendMessage(player, messageToSend);
                        pendingActions.remove(playerId);
                        break;

                    case SET_WARP_DESCRIPTION_LINE:
                        int maxLoreLines = plugin.getConfig().getInt("manage-warp-gui.max-lore-lines", 5);

                        if (input.equalsIgnoreCase("fin")) {
                            ItemStack currentIcon = actionInfo.warp.getRawIcon();
                            if (currentIcon == null || currentIcon.getType() == Material.AIR) {
                                currentIcon = new ItemStack(Material.PAPER);
                                ItemMeta tempMeta = currentIcon.getItemMeta();
                                if (tempMeta != null) {
                                    tempMeta.setDisplayName(ChatUtils.color("&fWarp: &b" + actionInfo.warp.getName()));
                                    currentIcon.setItemMeta(tempMeta);
                                }
                            }
                            ItemMeta meta = currentIcon.getItemMeta();
                            if (meta != null) {
                                if (actionInfo.currentLoreInput.isEmpty()) {
                                    meta.setLore(null);
                                } else {
                                    meta.setLore(ChatUtils.color(actionInfo.currentLoreInput));
                                }
                                currentIcon.setItemMeta(meta);
                                actionInfo.warp.setIcon(currentIcon);

                                messageToSend = cfgMgr.msgWarpDescSet.replace("%warp_name%", actionInfo.warp.getName());
                                ChatUtils.sendMessage(player, messageToSend);
                            }
                            pendingActions.remove(playerId);
                        } else if (input.equalsIgnoreCase("clear")) {
                            actionInfo.currentLoreInput.clear();
                            ChatUtils.sendMessage(player, "&eDescription (lore) effacée. Tapez une nouvelle ligne, 'fin' pour sauvegarder, ou 'annuler'.");
                        } else {
                            if (actionInfo.currentLoreInput.size() >= maxLoreLines) {
                                messageToSend = cfgMgr.msgMaxLoreLinesReached.replace("%max_lines%", String.valueOf(maxLoreLines));
                                ChatUtils.sendMessage(player, messageToSend);
                                return;
                            }
                            String[] lines = input.split("//");
                            for (String line : lines) {
                                // CORRECTION ICI : Ajouter &7 par défaut si pas de couleur
                                if (!line.startsWith("&")) {
                                    line = "&7" + line;
                                }

                                if (actionInfo.currentLoreInput.size() < maxLoreLines) {
                                    actionInfo.currentLoreInput.add(line);
                                } else {
                                    messageToSend = cfgMgr.msgMaxLoreLinesReached
                                        .replace("%max_lines%", String.valueOf(maxLoreLines))
                                        + " Ligne ignorée: '&f" + line + "&c'.";
                                    ChatUtils.sendMessage(player, messageToSend);
                                    break;
                                }
                            }
                            ChatUtils.sendMessage(player, "&aLigne(s) ajoutée(s). Total: " + actionInfo.currentLoreInput.size() + "/" + maxLoreLines + ". Tapez la ligne suivante, '//' pour un saut de ligne, 'clear', 'fin', ou 'annuler'.");
                        }
                        break;
                }
            });
        }
    }
}
