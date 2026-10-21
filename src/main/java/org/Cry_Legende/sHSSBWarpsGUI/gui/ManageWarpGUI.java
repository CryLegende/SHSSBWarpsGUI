package org.Cry_Legende.sHSSBWarpsGUI.gui;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI; // Non utilisé directement, mais bon à garder
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
// import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer; // Pas utilisé directement ici
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfiguredItem;
import org.Cry_Legende.sHSSBWarpsGUI.config.ItemConfig;
import org.Cry_Legende.sHSSBWarpsGUI.config.SoundConfig;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FeaturedWarpsManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ItemStackBuilder;
import org.Cry_Legende.sHSSBWarpsGUI.utils.PlayerChatInput;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ManageWarpGUI extends BaseGUI {

    private final IslandWarp warpToManage;
    private final OfflinePlayer warpOwner;
    private final Island island;

    public ManageWarpGUI(SHSSBWarpsGUI plugin, Player viewer, IslandWarp warpToManage, PlayerSpecificWarpsGUI previousGui) {
        super(plugin, viewer, plugin.getConfigManager().getManageWarpGuiConfig(), null, previousGui); // itemsToDisplay est null car pas de liste paginée ici
        this.warpToManage = warpToManage;
        this.island = warpToManage.getIsland();
        this.warpOwner = (this.island != null && this.island.getOwner() != null) ?
            Bukkit.getOfflinePlayer(this.island.getOwner().getUniqueId()) : viewer; // Fallback sur viewer si owner est null, bien que peu probable ici
    }

    @Override
    protected void createAndFillInventory() {
        if (guiConfig == null) {
            plugin.getLogger().severe("GuiConfig est null pour ManageWarpGUI. Impossible de créer l'inventaire.");
            ChatUtils.sendMessage(viewer, "&cErreur critique lors de l'ouverture du menu de gestion de warp.");
            inventory = Bukkit.createInventory(this, 27, ChatUtils.color("&cErreur Config")); // Fallback inventory
            return;
        }
        Map<String, String> titlePlaceholders = new HashMap<>();
        titlePlaceholders.put("%warp_name%", warpToManage.getName());
        titlePlaceholders.put("%owner_name%", warpOwner.getName() != null ? warpOwner.getName() : "Inconnu");

        String title = parsePlaceholders(guiConfig.title, warpOwner, titlePlaceholders);
        inventory = Bukkit.createInventory(this, guiConfig.size, ChatUtils.color(title));

        if (guiConfig.staticItems != null) {
            for (ConfiguredItem configuredItemEntry : guiConfig.staticItems) {
                ItemConfig itemCfg = configuredItemEntry.itemConfig;
                if (itemCfg == null) continue;

                Map<String, String> itemPlaceholders = new HashMap<>(titlePlaceholders); // Copier les placeholders du titre
                String materialString = itemCfg.materialString; // Matériau par défaut

                // Logique spécifique pour les items dynamiques dans ManageWarpGUI
                if ("TOGGLE_WARP_PRIVACY".equals(itemCfg.action)) {
                    itemPlaceholders.put("%warp_status_text%", warpToManage.hasPrivateFlag() ? "&aRendre Public" : "&cRendre Privé");
                    itemPlaceholders.put("%warp_current_status%", warpToManage.hasPrivateFlag() ? "&cPrivé" : "&aPublic");
                    if ("%warp_status_material%".equalsIgnoreCase(itemCfg.materialString)) { // Si le matériau est un placeholder
                        materialString = warpToManage.hasPrivateFlag() ? "REDSTONE_BLOCK" : "EMERALD_BLOCK"; // Déterminer le matériau réel
                    }
                }
                if ("MANAGE_WARP_FEATURE".equals(itemCfg.action)) {
                    FeaturedWarpsManager fwm = plugin.getFeaturedWarpsManager();
                    FeaturedWarpsManager.FeaturedWarpEntry featureEntry = fwm.getFeaturedEntry(warpOwner.getUniqueId(), warpToManage.getName());
                    boolean isFeatured = featureEntry != null && !featureEntry.isExpired();

                    itemPlaceholders.put("%feature_status_text%", isFeatured ? "&cRetirer Mise en Avant" : "&6Mettre en Avant");
                    if (isFeatured && featureEntry != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat(plugin.getConfigManager().featuredDateTimeFormat);
                        itemPlaceholders.put("%feature_expiry_date%", sdf.format(new Date(featureEntry.expiryTimestamp)));
                    } else {
                        itemPlaceholders.put("%feature_expiry_date%", "Jamais");
                    }
                    itemPlaceholders.put("%feature_cost%", String.format("%,.2f", plugin.getConfigManager().featuredCost));
                    String currency = plugin.getVaultHook().getEconomy() != null ? plugin.getVaultHook().getEconomy().currencyNamePlural() : "";
                    itemPlaceholders.put("%vault_currency_name_plural%", currency != null ? currency : "");
                    itemPlaceholders.put("%feature_duration_formatted%", ConfigManager.formatDuration(plugin.getConfigManager().featuredDurationMillis, plugin.getConfigManager().featuredDurationFormat));

                    if ("%feature_status_material%".equalsIgnoreCase(itemCfg.materialString)) {
                        materialString = isFeatured ? plugin.getConfig().getString("manage-warp-gui.items.bouton_manage_feature_manage.material-featured", "GOLD_BLOCK")
                            : plugin.getConfig().getString("manage-warp-gui.items.bouton_manage_feature_manage.material-not-featured", "IRON_BLOCK");
                    }
                }
                // Ajouter d'autres placeholders si nécessaire pour d'autres boutons

                ItemConfig processedItemCfg = itemCfg;
                // Si le materialString a été changé dynamiquement, créer une instance temporaire d'ItemConfig
                if (!materialString.equals(itemCfg.materialString)) {
                    processedItemCfg = new ItemConfig(
                        materialString, itemCfg.nameTemplate, itemCfg.loreTemplate, itemCfg.action, itemCfg.command,
                        itemCfg.customModelData, itemCfg.glow, itemCfg.nbt, itemCfg.clickSound
                    );
                }

                ItemStack itemStack = buildItemFromConfig(processedItemCfg, warpOwner, itemPlaceholders);
                if (itemStack != null) {
                    // S'assurer que les données du warp sont sur l'item pour les actions
                    setItemPersistentData(itemStack, WARP_OWNER_KEY, warpOwner.getUniqueId().toString());
                    setItemPersistentData(itemStack, WARP_NAME_KEY, warpToManage.getName());

                    for (int slot : configuredItemEntry.slots) {
                        if (slot >= 0 && slot < guiConfig.size) {
                            inventory.setItem(slot, itemStack.clone()); // Cloner pour éviter les modifications partagées
                        }
                    }
                }
            }
        }
        // Pas d'items dynamiques paginés dans ce GUI, donc pas d'appel à fillDynamicItems ou updateNavigationButtonsState
    }


    @Override
    protected ItemStack createDynamicItem(Object data, OfflinePlayer placeholderContext, ItemConfig baseItemConfig) {
        // Ce GUI n'a pas d'items dynamiques paginés comme MainWarpsGUI ou PlayerSpecificWarpsGUI
        return null;
    }

    public void handleManageAction(Player player, String action, ItemStack clickedItem) {
        String warpNameFromPDC = getItemPersistentData(clickedItem, WARP_NAME_KEY); // Vérifier le warp concerné par l'item
        if (warpNameFromPDC == null || !warpNameFromPDC.equals(this.warpToManage.getName())) {
            ChatUtils.sendMessage(player, "&cErreur : Incohérence de warp pour l'action de gestion.");
            player.closeInventory();
            return;
        }

        if (this.island == null) { // Devrait être vérifié à la création du GUI, mais double sécurité
            ChatUtils.sendMessage(player, plugin.getConfigManager().msgIslandNotFound);
            player.closeInventory();
            return;
        }

        ConfigManager cfgMgr = plugin.getConfigManager();
        String messageToSend;

        switch (action.toUpperCase()) {
            case "EDIT_WARP_ICON":
                messageToSend = cfgMgr.instructionSetIcon
                    .replace("%warp_name%", warpNameFromPDC)
                    .replace("/swg confirmicon %warp_name%", "/" + plugin.getCommand("shssbwarps").getLabel() + " confirmicon " + warpNameFromPDC);
                ChatUtils.sendMessage(player, messageToSend);
                player.closeInventory();
                break;
            case "EDIT_WARP_NAME":
                messageToSend = cfgMgr.instructionRename
                    .replace("%warp_name%", warpNameFromPDC)
                    .replace("%max_length%", String.valueOf(plugin.getSuperiorSkyblock().getSettings().getIslandNames().getMaxLength()));
                ChatUtils.sendMessage(player, messageToSend);
                player.closeInventory();
                PlayerChatInput.requestPlayerInput(player, PlayerChatInput.InputType.RENAME_WARP, this.warpToManage);
                break;
            case "EDIT_WARP_DESCRIPTION":
                messageToSend = cfgMgr.instructionSetDesc
                    .replace("%warp_name%", warpNameFromPDC)
                    .replace("%max_lines%", String.valueOf(plugin.getConfig().getInt("manage-warp-gui.max-lore-lines", 5)));
                ChatUtils.sendMessage(player, messageToSend);
                player.closeInventory();
                PlayerChatInput.requestPlayerInput(player, PlayerChatInput.InputType.SET_WARP_DESCRIPTION_LINE, this.warpToManage);
                break;
            case "EDIT_WARP_LOCATION":
                this.warpToManage.setLocation(player.getLocation());
                // SuperiorSkyblockAPI.getSuperiorSkyblock().getGrid().saveIsland(this.island); // Si SSB2 ne sauvegarde pas automatiquement
                messageToSend = cfgMgr.msgWarpLocSet.replace("%warp_name%", warpNameFromPDC);
                ChatUtils.sendMessage(player, messageToSend);
                open(); // Re-open GUI to reflect changes if any (though location is not visible in this GUI)
                break;
            case "TOGGLE_WARP_PRIVACY":
                boolean currentPrivateStatus = this.warpToManage.hasPrivateFlag();
                this.warpToManage.setPrivateFlag(!currentPrivateStatus);
                // SuperiorSkyblockAPI.getSuperiorSkyblock().getGrid().saveIsland(this.island);
                String statusText = !currentPrivateStatus ? ChatUtils.color("&cPrivé") : ChatUtils.color("&aPublic");
                messageToSend = cfgMgr.msgWarpPrivacySet
                    .replace("%warp_name%", warpNameFromPDC)
                    .replace("%status%", statusText);
                ChatUtils.sendMessage(player, messageToSend);
                open(); // Re-open to update button text/material
                break;
            case "MANAGE_WARP_FEATURE": // Cliqué depuis ManageWarpGUI, ouvre le confirm GUI de feature
                plugin.getGuiListener().handleManageFeatureClick(player, clickedItem, this);
                break;
            case "DELETE_WARP_CONFIRM": // Ouvre le GUI de confirmation de suppression
                openDeleteConfirmationGUI(player);
                break;
            case "DO_DELETE_WARP": // Action réelle de suppression, appelée depuis le GUI de confirmation
                String warpNameToDelete = getItemPersistentData(clickedItem, WARP_NAME_KEY); // Devrait être le même que this.warpToManage.getName()
                if (warpNameToDelete == null || !warpNameToDelete.equals(this.warpToManage.getName())) {
                    ChatUtils.sendMessage(player, "&cErreur lors de la confirmation de suppression (incohérence de nom).");
                    return;
                }
                island.deleteWarp(warpNameToDelete);
                // SuperiorSkyblockAPI.getSuperiorSkyblock().getGrid().saveIsland(this.island);
                messageToSend = cfgMgr.msgWarpDeleted.replace("%warp_name%", warpNameToDelete);
                ChatUtils.sendMessage(player, messageToSend);

                // Revenir au GUI précédent (PlayerSpecificWarpsGUI) et le rafraîchir
                if (previousGui instanceof PlayerSpecificWarpsGUI) {
                    ((PlayerSpecificWarpsGUI) previousGui).refreshWarpsAndReopen();
                } else if (previousGui != null) {
                    previousGui.open(); // Fallback si ce n'est pas PlayerSpecificWarpsGUI
                } else {
                    player.closeInventory(); // Si pas de GUI précédent, fermer
                }
                break;
            default:
                ChatUtils.sendMessage(player, "&cAction de gestion inconnue : " + action);
                break;
        }
    }

    private void openDeleteConfirmationGUI(Player player) {
        // Le GUI de confirmation est simple et peut rester codé ici pour l'instant
        // ou être rendu configurable si besoin (plus complexe)
        Inventory confirmDeleteInv = Bukkit.createInventory(
            new GuiListener.ConfirmationGUICallbackHolder(this), // Le GUI actuel est le "previous" pour le confirm GUI
            27,
            ChatUtils.color("&4&lSupprimer '&c" + warpToManage.getName() + "&4&l' ?")
        );

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%warp_name%", warpToManage.getName());

        // Bouton Oui (Confirmer suppression)
        ItemConfig yesDeleteCfg = new ItemConfig("RED_WOOL", "&c&lOui, Supprimer",
            List.of("&4Cette action est irréversible!", "&7Le warp &c%warp_name%&7 sera effacé."),
            "DO_DELETE_WARP", null, 0, true, null,
            new SoundConfig(Sound.ENTITY_VILLAGER_NO, 1f, 0.8f));
        ItemStack yesDeleteItem = buildItemFromConfig(yesDeleteCfg, player, placeholders);
        setItemPersistentData(yesDeleteItem, WARP_NAME_KEY, warpToManage.getName()); // S'assurer que le nom du warp est sur le bouton

        // Bouton Non (Annuler)
        ItemConfig noCancelCfg = new ItemConfig("GREEN_WOOL", "&a&lNon, Annuler",
            List.of("&7Retourner au menu de gestion."),
            "BACK_FROM_CONFIRM_TO_PREVIOUS", null, 0, false, null,
            new SoundConfig(Sound.UI_BUTTON_CLICK, 1f, 1.2f));
        ItemStack noCancelItem = buildItemFromConfig(noCancelCfg, player, placeholders);
        // L'action BACK_FROM_CONFIRM_TO_PREVIOUS est gérée par GuiListener pour retourner à `this` (ManageWarpGUI)

        confirmDeleteInv.setItem(11, yesDeleteItem);
        confirmDeleteInv.setItem(15, noCancelItem);

        // Remplissage
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < confirmDeleteInv.getSize(); i++) {
            if (confirmDeleteInv.getItem(i) == null) confirmDeleteInv.setItem(i, filler.clone());
        }
        player.openInventory(confirmDeleteInv);
    }
}
