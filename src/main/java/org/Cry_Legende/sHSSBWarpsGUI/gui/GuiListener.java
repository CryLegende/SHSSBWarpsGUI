package org.Cry_Legende.sHSSBWarpsGUI.gui;

import org.Cry_Legende.sHSSBWarpsGUI.commands.IslandCommand;
import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.*;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FeaturedWarpsManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ItemStackBuilder;
import org.Cry_Legende.sHSSBWarpsGUI.utils.PlayerChatInput;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class GuiListener implements Listener {

    private final SHSSBWarpsGUI plugin;

    public GuiListener(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    /**
     * Empêche le glisser-déposer (Drag) dans les menus personnalisés.
     * Cela évite que des items soient déposés dans les slots vides.
     */
    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        // getHolder(false) : pas d'instantané du bloc pour les coffres, cet événement passe pour tous les inventaires du serveur
        InventoryHolder holder = event.getInventory().getHolder(false);
        if (holder instanceof BaseGUI || holder instanceof ConfirmationGUICallbackHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // Vérification de sécurité : Est-ce que l'inventaire ouvert (Top) est un de nos menus ?
        Inventory topInventory = event.getView().getTopInventory();
        // getHolder(false) : pas d'instantané du bloc pour les coffres, cet événement passe pour tous les clics du serveur
        InventoryHolder topHolder = topInventory.getHolder(false);
        boolean isOurGui = topHolder instanceof BaseGUI || topHolder instanceof ConfirmationGUICallbackHolder;

        if (isOurGui) {
            // On annule TOUT clic par défaut dès qu'un de nos menus est ouvert.
            // Cela protège contre le Shift-Clic depuis l'inventaire du bas vers le haut.
            event.setCancelled(true);
        } else {
            // Ce n'est pas notre menu, on laisse faire.
            return;
        }

        Inventory clickedInventory = event.getClickedInventory();
        // Si le joueur clique en dehors ou dans son propre inventaire (en bas), on s'arrête là (l'event est déjà annulé).
        if (clickedInventory == null || clickedInventory != topInventory) {
            return;
        }

        // --- Logique du clic dans le menu ---

        InventoryHolder holder = topHolder; // clickedInventory == topInventory à ce stade
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        BaseGUI baseGuiInstance = null;
        ItemConfig itemFullConfig = null;
        GuiConfig currentGuiConfigFromBase = null;

        if (holder instanceof BaseGUI) {
            baseGuiInstance = (BaseGUI) holder;
            currentGuiConfigFromBase = baseGuiInstance.guiConfig;

            // --- GESTION DES FAVORIS (SHIFT-CLIC) ---
            if (baseGuiInstance instanceof MainWarpsGUI && event.isShiftClick()) {
                String targetUUIDStr = baseGuiInstance.getItemPersistentData(clickedItem, BaseGUI.TARGET_PLAYER_UUID_KEY);
                if (targetUUIDStr != null) {
                    try {
                        UUID targetUUID = UUID.fromString(targetUUIDStr);
                        handleFavoriteToggle(player, targetUUID);
                    } catch (IllegalArgumentException ignored) {
                    }
                    return; // Arrêt du traitement
                }
            }
            // ------------------------------------------

            if (currentGuiConfigFromBase != null && currentGuiConfigFromBase.staticItems != null) {
                for (ConfiguredItem ci : currentGuiConfigFromBase.staticItems) {
                    if (ci.slots.contains(event.getRawSlot())) {
                        itemFullConfig = ci.itemConfig;
                        break;
                    }
                }
            }
            if (itemFullConfig == null && currentGuiConfigFromBase != null && currentGuiConfigFromBase.dynamicItemSlots != null && currentGuiConfigFromBase.dynamicItemSlots.contains(event.getRawSlot())) {
                Object itemData = findDataForDynamicSlot(baseGuiInstance, event.getRawSlot());
                if (itemData != null) {
                    boolean isFeatured = baseGuiInstance.isDynamicItemFeatured(itemData);
                    itemFullConfig = isFeatured && currentGuiConfigFromBase.featuredDynamicItemTemplate != null ?
                        currentGuiConfigFromBase.featuredDynamicItemTemplate :
                        currentGuiConfigFromBase.dynamicItemTemplate;
                } else if (currentGuiConfigFromBase.dynamicItemTemplate != null) {
                    itemFullConfig = currentGuiConfigFromBase.dynamicItemTemplate;
                }
            }
        } else if (holder instanceof ConfirmationGUICallbackHolder) {
            baseGuiInstance = ((ConfirmationGUICallbackHolder) holder).previousGuiToList;
            if (baseGuiInstance != null) currentGuiConfigFromBase = baseGuiInstance.guiConfig;

            ConfigManager cfgMgr = plugin.getConfigManager();
            String actionPDC = baseGuiInstance != null ? baseGuiInstance.getItemPersistentData(clickedItem, BaseGUI.ITEM_ACTION_KEY) : null;

            if (actionPDC != null) {
                switch (actionPDC) {
                    case "CONFIRM_FEATURE_THIS_WARP":
                        itemFullConfig = cfgMgr.featureWarpButtonConfig;
                        break;
                    case "CONFIRM_UNFEATURE_THIS_WARP":
                        itemFullConfig = cfgMgr.unfeatureWarpButtonConfig;
                        break;
                    case "DO_DELETE_WARP":
                        itemFullConfig = new ItemConfig("RED_WOOL", "Suppr", Collections.emptyList(), "DO_DELETE_WARP", null, 0, false, null, new SoundConfig(Sound.ENTITY_VILLAGER_NO, 1f, 0.8f));
                        break;
                    case "BACK_FROM_CONFIRM_TO_PREVIOUS":
                    case "BACK":
                        List<String> cancelLore = cfgMgr.itemCancelLoreFeatureConfirmGui;
                        String cancelName = cfgMgr.itemCancelNameFeatureConfirmGui;
                        itemFullConfig = new ItemConfig("BARRIER", cancelName, cancelLore, actionPDC, null, 0, false, null, new SoundConfig(Sound.UI_BUTTON_CLICK, 1f, 1.2f));
                        break;
                }
            }
        }

        if (baseGuiInstance == null && !(holder instanceof ConfirmationGUICallbackHolder)) {
            return;
        }

        SoundConfig soundToPlay = null;
        if (itemFullConfig != null && itemFullConfig.clickSound != null) {
            soundToPlay = itemFullConfig.clickSound;
        } else if (currentGuiConfigFromBase != null && currentGuiConfigFromBase.defaultClickSound != null) {
            soundToPlay = currentGuiConfigFromBase.defaultClickSound;
        } else if (holder instanceof ConfirmationGUICallbackHolder) {
            soundToPlay = new SoundConfig(Sound.UI_BUTTON_CLICK, 1f, 1f);
        }

        if (soundToPlay != null && soundToPlay.sound != null) {
            player.playSound(player.getLocation(), soundToPlay.sound, soundToPlay.volume, soundToPlay.pitch);
        }

        String action = null;
        BaseGUI pdcContext = baseGuiInstance;
        if (holder instanceof ConfirmationGUICallbackHolder) {
            pdcContext = ((ConfirmationGUICallbackHolder) holder).previousGuiToList;
        }

        if (pdcContext != null) {
            action = pdcContext.getItemPersistentData(clickedItem, BaseGUI.ITEM_ACTION_KEY);
        }

        if (action == null || action.equalsIgnoreCase("NONE")) {
            return;
        }

        BaseGUI guiContextForActionHandler = (holder instanceof BaseGUI) ? (BaseGUI) holder :
            ((holder instanceof ConfirmationGUICallbackHolder) ? ((ConfirmationGUICallbackHolder) holder).previousGuiToList : null);


        if (guiContextForActionHandler == null &&
            !action.toUpperCase().startsWith("CONFIRM_") &&
            !action.equalsIgnoreCase("BACK_FROM_CONFIRM_TO_PREVIOUS") &&
            !action.equalsIgnoreCase("CLOSE_GUI") &&
            !action.equalsIgnoreCase("DO_DELETE_WARP")
        ) {
            plugin.getLogger().severe("Impossible d'exécuter l'action '" + action + "' car le contexte du GUI (guiContextForActionHandler) est perdu.");
            return;
        }

        if (!action.toUpperCase().startsWith("CONFIRM_") && !action.equalsIgnoreCase("DO_DELETE_WARP")) {
            if (PlayerChatInput.hasPendingAction(player)) {
                PlayerChatInput.clearPendingAction(player);
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgPendingInputActionCancelled);
            }
            if (plugin.getWarpDialogs() != null) {
                plugin.getWarpDialogs().cancel(player);
            }
        }

        switch (action.toUpperCase()) {
            case "PREVIOUS_PAGE":
                if (guiContextForActionHandler != null) guiContextForActionHandler.previousPage();
                break;
            case "NEXT_PAGE":
                if (guiContextForActionHandler != null) guiContextForActionHandler.nextPage();
                break;
            case "BACK":
            case "BACK_TO_MAIN_WARPS_GUI":
                if (guiContextForActionHandler != null) guiContextForActionHandler.goBack();
                break;
            case "CLOSE_GUI":
                player.closeInventory();
                break;
            case "OPEN_PLAYER_WARPS":
                if (guiContextForActionHandler != null)
                    handleOpenPlayerWarps(player, clickedItem, guiContextForActionHandler);
                break;
            case "PLAYER_INFO_HEAD":
                break;
            case "TELEPORT_TO_WARP":
                if (guiContextForActionHandler != null) {
                    if (event.getClick() == ClickType.LEFT) {
                        handleTeleportWarp(player, clickedItem, guiContextForActionHandler);
                    } else if (event.getClick() == ClickType.RIGHT && guiContextForActionHandler instanceof PlayerSpecificWarpsGUI) {
                        handleOpenManageWarpGUI(player, clickedItem, (PlayerSpecificWarpsGUI) guiContextForActionHandler);
                    }
                }
                break;
            case "COMMAND":
                String command = guiContextForActionHandler != null ? guiContextForActionHandler.getItemPersistentData(clickedItem, BaseGUI.ITEM_COMMAND_KEY) : null;
                if (command == null && itemFullConfig != null) command = itemFullConfig.command;

                if (command != null && !command.isEmpty()) {
                    final String finalCommand = plugin.getPapiHook().setPlaceholders(player, command);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (finalCommand.startsWith("console:"))
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand.substring(8).trim());
                        else player.performCommand(finalCommand.trim());
                    });
                }
                break;
            case "EDIT_WARP_ICON":
            case "EDIT_WARP_NAME":
            case "EDIT_WARP_DESCRIPTION":
            case "EDIT_WARP_LOCATION":
            case "TOGGLE_WARP_PRIVACY":
            case "DELETE_WARP_CONFIRM":
                if (guiContextForActionHandler instanceof ManageWarpGUI) {
                    ((ManageWarpGUI) guiContextForActionHandler).handleManageAction(player, action, clickedItem);
                }
                break;
            case "MANAGE_WARP_FEATURE":
                if (guiContextForActionHandler instanceof ManageWarpGUI) {
                    handleManageFeatureClick(player, clickedItem, guiContextForActionHandler);
                }
                break;
            case "CONFIRM_FEATURE_THIS_WARP":
            case "CONFIRM_UNFEATURE_THIS_WARP":
                if (holder instanceof ConfirmationGUICallbackHolder && guiContextForActionHandler != null) {
                    handleFeatureWarpAction(player, clickedItem, guiContextForActionHandler, action.equalsIgnoreCase("CONFIRM_FEATURE_THIS_WARP"));
                }
                break;
            case "DO_DELETE_WARP":
                if (holder instanceof ConfirmationGUICallbackHolder && guiContextForActionHandler instanceof ManageWarpGUI) {
                    ((ManageWarpGUI) guiContextForActionHandler).handleManageAction(player, action, clickedItem);
                }
                break;
            case "BACK_FROM_CONFIRM_TO_PREVIOUS":
                if (holder instanceof ConfirmationGUICallbackHolder && guiContextForActionHandler != null) {
                    guiContextForActionHandler.open();
                } else {
                    player.closeInventory();
                }
                break;
            default:
                break;
        }
    }

    private void handleFavoriteToggle(Player player, UUID targetUUID) {
        if (player.getUniqueId().equals(targetUUID)) {
            ChatUtils.sendMessage(player, "&cVous ne pouvez pas vous ajouter vous-même en favori.");
            return;
        }

        boolean isFav = plugin.getFavoritesManager().isFavorite(player.getUniqueId(), targetUUID);

        if (isFav) {
            plugin.getFavoritesManager().toggleFavorite(player.getUniqueId(), targetUUID);
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgFavoriteRemoved);
        } else {
            if (plugin.getFavoritesManager().canAddFavorite(player.getUniqueId())) {
                plugin.getFavoritesManager().toggleFavorite(player.getUniqueId(), targetUUID);
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgFavoriteAdded);
            } else {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgFavoriteFull);
                return;
            }
        }
        new IslandCommand(plugin).openMainGui(player);
    }

    private Object findDataForDynamicSlot(BaseGUI gui, int rawSlot) {
        if (gui == null || gui.itemsToDisplay == null || gui.guiConfig == null || gui.guiConfig.dynamicItemSlots == null)
            return null;
        int itemsPerPage = gui.guiConfig.dynamicItemSlots.size();
        if (itemsPerPage == 0) return null;

        int dynamicSlotListIndex = -1;
        for (int i = 0; i < gui.guiConfig.dynamicItemSlots.size(); i++) {
            if (gui.guiConfig.dynamicItemSlots.get(i) == rawSlot) {
                dynamicSlotListIndex = i;
                break;
            }
        }
        if (dynamicSlotListIndex != -1) {
            int dataIndex = gui.currentPage * itemsPerPage + dynamicSlotListIndex;
            if (dataIndex >= 0 && dataIndex < gui.itemsToDisplay.size()) {
                return gui.itemsToDisplay.get(dataIndex);
            }
        }
        return null;
    }

    private void handleOpenManageWarpGUI(Player player, ItemStack clickedWarpItem, PlayerSpecificWarpsGUI previousWarpListGui) {
        String ownerUUIDStr = previousWarpListGui.getItemPersistentData(clickedWarpItem, BaseGUI.WARP_OWNER_KEY);
        String warpNameStr = previousWarpListGui.getItemPersistentData(clickedWarpItem, BaseGUI.WARP_NAME_KEY);

        if (ownerUUIDStr != null && warpNameStr != null) {
            UUID ownerUUID = UUID.fromString(ownerUUIDStr);
            if (!player.getUniqueId().equals(ownerUUID) && !player.hasPermission("shssbwarpsgui.admin.editothers")) {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgNotYourWarp.replace("%warp_name%", warpNameStr));
                return;
            }
            SuperiorPlayer ssbOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(ownerUUID);
            if (ssbOwner != null && ssbOwner.getIsland() != null) {
                IslandWarp warpToManage = ssbOwner.getIsland().getWarp(warpNameStr);
                if (warpToManage != null) {
                    new ManageWarpGUI(plugin, player, warpToManage, previousWarpListGui).open();
                } else {
                    ChatUtils.sendMessage(player, plugin.getConfigManager().msgWarpNotFound.replace("%warp_name%", warpNameStr));
                }
            } else {
                ChatUtils.sendMessage(player, plugin.getConfigManager().msgIslandNotFound);
            }
        } else {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgGenericError);
        }
    }

    private void handleOpenPlayerWarps(Player player, ItemStack clickedItem, BaseGUI currentGui) {
        String ownerUUIDString = currentGui.getItemPersistentData(clickedItem, BaseGUI.TARGET_PLAYER_UUID_KEY);
        if (ownerUUIDString == null) return;

        UUID ownerUUID = UUID.fromString(ownerUUIDString);
        OfflinePlayer warpOwner = Bukkit.getOfflinePlayer(ownerUUID);
        SuperiorPlayer ssbOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(ownerUUID);

        if (ssbOwner == null || ssbOwner.getIsland() == null) {
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgIslandNotFound);
            return;
        }

        Island island = ssbOwner.getIsland();
        List<IslandWarp> warps = new ArrayList<>(island.getIslandWarps().values());

        if (!player.getUniqueId().equals(ownerUUID)) {
            warps = warps.stream().filter(w -> !w.hasPrivateFlag()).collect(Collectors.toList());
        }

        if (warps.isEmpty()) {
            ChatUtils.sendMessage(player, "&cCe joueur n'a pas de warps accessibles.");
            return;
        }

        new PlayerSpecificWarpsGUI(plugin, player, warpOwner, warps, currentGui).open();
    }

    private void handleTeleportWarp(Player player, ItemStack clickedItem, BaseGUI guiContext) {
        String ownerUUIDString = guiContext.getItemPersistentData(clickedItem, BaseGUI.WARP_OWNER_KEY);
        String warpName = guiContext.getItemPersistentData(clickedItem, BaseGUI.WARP_NAME_KEY);
        handleTeleportWarpLogic(player, ownerUUIDString != null ? UUID.fromString(ownerUUIDString) : null, warpName, guiContext);
    }

    private void handleTeleportWarpLogic(Player player, UUID ownerUUID, String warpName, BaseGUI guiContext) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        if (ownerUUID == null || warpName == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgGenericError);
            return;
        }
        SuperiorPlayer ssbOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(ownerUUID);
        if (ssbOwner == null || ssbOwner.getIsland() == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgIslandNotFound);
            return;
        }
        Island island = ssbOwner.getIsland();
        IslandWarp targetWarp = island.getWarp(warpName);
        if (targetWarp == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgWarpNotFound.replace("%warp_name%", warpName));
            return;
        }

        if (targetWarp.hasPrivateFlag() && !player.getUniqueId().equals(ownerUUID) && !player.hasPermission("superior.admin.bypass")) {
            ChatUtils.sendMessage(player, "&cCe warp est privé.");
            return;
        }

        SuperiorPlayer sPlayer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(player);
        if (sPlayer != null) {
            if (island.isLocked() && !island.isMember(sPlayer) && !player.hasPermission("superior.admin.bypass")) {
                ChatUtils.sendMessage(player, "&cCette île est verrouillée.");
                return;
            }
            if (island.isBanned(sPlayer) && !player.hasPermission("superior.admin.bypass")) {
                ChatUtils.sendMessage(player, "&cVous êtes banni de cette île.");
                return;
            }
        }

        plugin.getWarpVisitManager().incrementVisitCount(player, targetWarp);

        if (sPlayer != null) {
            long warmupMillis = SuperiorSkyblockAPI.getSuperiorSkyblock().getSettings().getWarpsWarmup();
            long warmupSeconds = warmupMillis / 1000;

            if (warmupMillis > 0 && !player.hasPermission("superior.admin.bypass.warmup")) {
                ChatUtils.sendMessage(player, cfgMgr.msgTeleportWarmup.replace("%time%", String.valueOf(warmupSeconds)));
                player.closeInventory();
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline() && sPlayer.asPlayer().isOnline()) {
                            island.warpPlayer(sPlayer, targetWarp.getName());
                        }
                    }
                }.runTaskLater(plugin, warmupMillis / 50);
            } else {
                island.warpPlayer(sPlayer, targetWarp.getName());
                player.closeInventory();
            }
        }
    }

    public void handleManageFeatureClick(Player player, ItemStack clickedWarpItem, BaseGUI currentWarpListOrManageGui) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        if (!cfgMgr.featuredEnabled) {
            ChatUtils.sendMessage(player, "&cLa mise en avant est désactivée.");
            return;
        }

        String ownerUUIDString = currentWarpListOrManageGui.getItemPersistentData(clickedWarpItem, BaseGUI.WARP_OWNER_KEY);
        String warpName = currentWarpListOrManageGui.getItemPersistentData(clickedWarpItem, BaseGUI.WARP_NAME_KEY);

        if (ownerUUIDString == null || warpName == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgGenericError);
            return;
        }
        UUID ownerUUID = UUID.fromString(ownerUUIDString);

        if (!player.getUniqueId().equals(ownerUUID) && !player.hasPermission("shssbwarpsgui.admin.featureothers")) {
            ChatUtils.sendMessage(player, cfgMgr.msgNoPermissionFeature);
            return;
        }

        OfflinePlayer warpOwner = Bukkit.getOfflinePlayer(ownerUUID);
        FeaturedWarpsManager fwm = plugin.getFeaturedWarpsManager();
        FeaturedWarpsManager.FeaturedWarpEntry currentFeatureEntry = fwm.getFeaturedEntry(ownerUUID, warpName);
        boolean isCurrentlyFeatured = currentFeatureEntry != null && !currentFeatureEntry.isExpired();

        String confirmGuiTitle = isCurrentlyFeatured ? cfgMgr.titleUnfeatureConfirmGui : cfgMgr.titleFeatureConfirmGui;
        Inventory confirmInv = Bukkit.createInventory(new ConfirmationGUICallbackHolder(currentWarpListOrManageGui), 27, ChatUtils.color(confirmGuiTitle));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%warp_name%", warpName);
        placeholders.put("%owner_name%", warpOwner.getName() != null ? warpOwner.getName() : "Inconnu");
        placeholders.put("%cost%", String.format("%,.2f", cfgMgr.featuredCost));
        placeholders.put("%feature_duration_formatted%", ConfigManager.formatDuration(cfgMgr.featuredDurationMillis, cfgMgr.featuredDurationFormat));
        String currency = plugin.getVaultHook().getEconomy() != null ? plugin.getVaultHook().getEconomy().currencyNamePlural() : "";
        placeholders.put("%vault_currency_name_plural%", currency != null ? currency : "");

        if (isCurrentlyFeatured && currentFeatureEntry != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(cfgMgr.featuredDateTimeFormat);
            placeholders.put("%feature_expiry_date%", sdf.format(new Date(currentFeatureEntry.expiryTimestamp)));
        } else {
            placeholders.put("%feature_expiry_date%", "Jamais");
        }

        ItemConfig buttonConfigSrc = isCurrentlyFeatured ? cfgMgr.unfeatureWarpButtonConfig : cfgMgr.featureWarpButtonConfig;
        if (buttonConfigSrc == null) {
            ChatUtils.sendMessage(player, "&cErreur de configuration pour le bouton de mise en avant.");
            return;
        }

        ItemConfig confirmButtonDisplayConfig = buttonConfigSrc.generateDisplayInstance(
            currentWarpListOrManageGui.parsePlaceholders(buttonConfigSrc.nameTemplate, player, placeholders),
            currentWarpListOrManageGui.parseLorePlaceholders(buttonConfigSrc.loreTemplate, player, placeholders)
        );
        ItemStack confirmButton = currentWarpListOrManageGui.buildItemFromConfig(confirmButtonDisplayConfig, player, null);

        if (confirmButton != null) {
            currentWarpListOrManageGui.setItemPersistentData(confirmButton, BaseGUI.WARP_OWNER_KEY, ownerUUIDString);
            currentWarpListOrManageGui.setItemPersistentData(confirmButton, BaseGUI.WARP_NAME_KEY, warpName);
        }

        ItemStack cancelButton = new ItemStackBuilder(Material.BARRIER)
            .setName(currentWarpListOrManageGui.parsePlaceholders(cfgMgr.itemCancelNameFeatureConfirmGui, player, placeholders))
            .setLore(currentWarpListOrManageGui.parseLorePlaceholders(cfgMgr.itemCancelLoreFeatureConfirmGui, player, placeholders))
            .build();
        if (cancelButton != null) {
            currentWarpListOrManageGui.setItemPersistentData(cancelButton, BaseGUI.ITEM_ACTION_KEY, "BACK_FROM_CONFIRM_TO_PREVIOUS");
        }

        confirmInv.setItem(11, confirmButton);
        confirmInv.setItem(15, cancelButton);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < confirmInv.getSize(); i++) {
            if (confirmInv.getItem(i) == null) confirmInv.setItem(i, filler);
        }
        player.openInventory(confirmInv);
    }

    static class ConfirmationGUICallbackHolder implements InventoryHolder {
        public final BaseGUI previousGuiToList;

        public ConfirmationGUICallbackHolder(BaseGUI previousGuiToList) {
            this.previousGuiToList = previousGuiToList;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }

    private void handleFeatureWarpAction(Player player, ItemStack clickedItem, BaseGUI previousGuiToList, boolean isFeaturing) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        FeaturedWarpsManager fwm = plugin.getFeaturedWarpsManager();

        String ownerUUIDString = previousGuiToList.getItemPersistentData(clickedItem, BaseGUI.WARP_OWNER_KEY);
        String warpName = previousGuiToList.getItemPersistentData(clickedItem, BaseGUI.WARP_NAME_KEY);

        if (ownerUUIDString == null || warpName == null) {
            ChatUtils.sendMessage(player, cfgMgr.msgGenericError);
            if (previousGuiToList != null) previousGuiToList.open();
            else player.closeInventory();
            return;
        }
        UUID ownerUUID = UUID.fromString(ownerUUIDString);
        OfflinePlayer warpOwner = Bukkit.getOfflinePlayer(ownerUUID);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%warp_name%", warpName);
        placeholders.put("%owner_name%", warpOwner.getName() != null ? warpOwner.getName() : "Inconnu");
        placeholders.put("%cost%", String.format("%,.2f", cfgMgr.featuredCost));
        placeholders.put("%duration%", ConfigManager.formatDuration(cfgMgr.featuredDurationMillis, cfgMgr.featuredDurationFormat));
        String currency = plugin.getVaultHook().getEconomy() != null ? plugin.getVaultHook().getEconomy().currencyNamePlural() : "";
        placeholders.put("%vault_currency_name_plural%", currency != null ? currency : "");
        FeaturedWarpsManager.FeaturedWarpEntry currentEntry = fwm.getFeaturedEntry(ownerUUID, warpName);
        if (currentEntry != null && !currentEntry.isExpired()) {
            SimpleDateFormat sdf = new SimpleDateFormat(cfgMgr.featuredDateTimeFormat);
            placeholders.put("%feature_expiry_date%", sdf.format(new Date(currentEntry.expiryTimestamp)));
        } else {
            placeholders.put("%feature_expiry_date%", "N/A");
        }

        if (isFeaturing) {
            if (currentEntry != null && !currentEntry.isExpired()) {
                ChatUtils.sendMessage(player, parsePlaceholdersForPlayer(cfgMgr.msgAlreadyFeatured, player, placeholders));
                if (previousGuiToList != null) previousGuiToList.open();
                else player.closeInventory();
                return;
            }
            if (cfgMgr.featuredCost > 0) {
                if (!plugin.getVaultHook().hasEnough(player, cfgMgr.featuredCost)) {
                    ChatUtils.sendMessage(player, parsePlaceholdersForPlayer(cfgMgr.msgNotEnoughMoney, player, placeholders));
                    if (previousGuiToList != null) previousGuiToList.open();
                    else player.closeInventory();
                    return;
                }
                if (!plugin.getVaultHook().withdraw(player, cfgMgr.featuredCost)) {
                    ChatUtils.sendMessage(player, "&cUne erreur est survenue lors du prélèvement.");
                    if (previousGuiToList != null) previousGuiToList.open();
                    else player.closeInventory();
                    return;
                }
            }
            fwm.addFeaturedWarp(ownerUUID, warpName);
            ChatUtils.sendMessage(player, parsePlaceholdersForPlayer(cfgMgr.msgFeaturedSuccess, player, placeholders));
        } else { // Unfeaturing
            if (currentEntry == null || currentEntry.isExpired()) {
                ChatUtils.sendMessage(player, parsePlaceholdersForPlayer(cfgMgr.msgNotFeatured, player, placeholders));
                if (previousGuiToList != null) previousGuiToList.open();
                else player.closeInventory();
                return;
            }
            fwm.removeFeaturedWarp(ownerUUID, warpName);
            ChatUtils.sendMessage(player, parsePlaceholdersForPlayer(cfgMgr.msgFeaturedRemoved, player, placeholders));
        }

        if (previousGuiToList != null) {
            previousGuiToList.open();
        } else {
            player.closeInventory();
        }
    }

    private String parsePlaceholdersForPlayer(String text, Player p, Map<String, String> customPlaceholders) {
        if (text == null) return "";
        String result = text;
        if (customPlaceholders != null) {
            for (Map.Entry<String, String> entry : customPlaceholders.entrySet()) {
                result = result.replace(entry.getKey(), entry.getValue() != null ? entry.getValue() : "");
            }
        }
        return plugin.getPapiHook() != null ? plugin.getPapiHook().setPlaceholders(p, result) : result;
    }
}
