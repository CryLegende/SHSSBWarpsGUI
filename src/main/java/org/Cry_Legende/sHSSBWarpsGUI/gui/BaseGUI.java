package org.Cry_Legende.sHSSBWarpsGUI.gui;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfiguredItem;
import org.Cry_Legende.sHSSBWarpsGUI.config.GuiConfig;
import org.Cry_Legende.sHSSBWarpsGUI.config.ItemConfig;
import org.Cry_Legende.sHSSBWarpsGUI.config.SoundConfig;
import org.Cry_Legende.sHSSBWarpsGUI.managers.WarpManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ItemStackBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class BaseGUI implements InventoryHolder {
    protected final SHSSBWarpsGUI plugin;
    protected final Player viewer;
    protected Inventory inventory;
    protected int currentPage = 0;
    protected List<?> itemsToDisplay;
    protected GuiConfig guiConfig;
    protected BaseGUI previousGui;

    public static final NamespacedKey ITEM_ACTION_KEY = new NamespacedKey(SHSSBWarpsGUI.getInstance(), "gui_item_action");
    public static final NamespacedKey ITEM_COMMAND_KEY = new NamespacedKey(SHSSBWarpsGUI.getInstance(), "gui_item_command");
    public static final NamespacedKey WARP_OWNER_KEY = new NamespacedKey(SHSSBWarpsGUI.getInstance(), "gui_warp_owner_uuid");
    public static final NamespacedKey WARP_NAME_KEY = new NamespacedKey(SHSSBWarpsGUI.getInstance(), "gui_warp_name");
    public static final NamespacedKey TARGET_PLAYER_UUID_KEY = new NamespacedKey(SHSSBWarpsGUI.getInstance(), "gui_target_player_uuid");

    public BaseGUI(SHSSBWarpsGUI plugin, Player viewer, GuiConfig guiConfig, List<?> itemsToDisplay, BaseGUI previousGui) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.guiConfig = guiConfig;
        this.itemsToDisplay = itemsToDisplay;
        this.previousGui = previousGui;
    }

    protected abstract ItemStack createDynamicItem(Object data, OfflinePlayer placeholderContext, ItemConfig baseItemConfig);

    public void open() {
        if (guiConfig == null) {
            plugin.getLogger().severe("GuiConfig est null pour " + this.getClass().getSimpleName() + " (viewer: " + viewer.getName() + "). Impossible d'ouvrir le GUI.");
            ChatUtils.sendMessage(viewer, "&cErreur critique: Configuration du menu introuvable. Contactez un admin.");
            return;
        }
        createAndFillInventory();
        if (inventory != null) {
            viewer.openInventory(inventory);
        } else {
            plugin.getLogger().severe("L'inventaire n'a pas pu être créé pour " + this.getClass().getSimpleName() + " (viewer: " + viewer.getName() + ").");
            ChatUtils.sendMessage(viewer, "&cErreur: Impossible d'ouvrir le menu.");
        }
    }

    protected void createAndFillInventory() {
        Map<String, String> titlePlaceholders = new HashMap<>();
        if (this instanceof PlayerSpecificWarpsGUI && ((PlayerSpecificWarpsGUI) this).getWarpOwnerOffline() != null) {
            OfflinePlayer warpOwner = ((PlayerSpecificWarpsGUI) this).getWarpOwnerOffline();
            titlePlaceholders.put("%target_player_name%", warpOwner.getName() != null ? warpOwner.getName() : "Inconnu");
        }
        String title = parsePlaceholders(guiConfig.title, viewer, titlePlaceholders);
        inventory = Bukkit.createInventory(this, guiConfig.size, ChatUtils.color(title));

        if (guiConfig.staticItems != null) {
            for (ConfiguredItem configuredItem : guiConfig.staticItems) {
                if (configuredItem.itemConfig == null) continue;
                ItemStack itemStack = buildItemFromConfig(configuredItem.itemConfig, viewer, null);
                if (itemStack != null) {
                    for (int slot : configuredItem.slots) {
                        if (slot >= 0 && slot < guiConfig.size) {
                            inventory.setItem(slot, itemStack.clone());
                        } else {
                            plugin.getLogger().warning("Slot " + slot + " hors limites pour l'item '" + configuredItem.itemConfig.nameTemplate + "' dans " + guiConfig.title);
                        }
                    }
                }
            }
        }
        fillDynamicItems();
        updateNavigationButtonsState();
    }

    protected void fillDynamicItems() {
        if (inventory == null) return;
        if (itemsToDisplay == null || guiConfig.dynamicItemSlots == null || guiConfig.dynamicItemSlots.isEmpty() || guiConfig.dynamicItemTemplate == null) {
            if (guiConfig.dynamicItemSlots != null && !guiConfig.dynamicItemSlots.isEmpty()) {
                for (int slot : guiConfig.dynamicItemSlots) {
                    if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, null);
                }
            }
            return;
        }
        for (int slot : guiConfig.dynamicItemSlots) {
            if (slot >= 0 && slot < inventory.getSize()) inventory.setItem(slot, null);
        }

        int itemsPerPage = guiConfig.dynamicItemSlots.size();
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, itemsToDisplay.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndexInDynamicList = i - startIndex;
            if (slotIndexInDynamicList < guiConfig.dynamicItemSlots.size()) {
                int actualSlotInGui = guiConfig.dynamicItemSlots.get(slotIndexInDynamicList);
                if (actualSlotInGui < 0 || actualSlotInGui >= inventory.getSize()) continue;

                Object itemData = itemsToDisplay.get(i);
                OfflinePlayer placeholderContext = determineContextForDynamicItem(itemData);
                boolean isFeatured = isDynamicItemFeatured(itemData);

                ItemConfig baseItemCfg = isFeatured && guiConfig.featuredDynamicItemTemplate != null ?
                    guiConfig.featuredDynamicItemTemplate : guiConfig.dynamicItemTemplate;

                if (baseItemCfg == null) {
                    plugin.getLogger().severe("ERREUR: Template d'item dynamique (baseItemCfg) est NULL pour " + guiConfig.title +
                        ". Type: " + (isFeatured ? "featured" : "normal") + ". Vérifiez config.");
                    continue;
                }
                plugin.getLogger().fine("[BaseGUI Debug] FillDynamicItems - GUI: " + guiConfig.title +
                    " | Context: " + (placeholderContext != null ? placeholderContext.getName() : "null") +
                    " | Template Material: " + baseItemCfg.materialString +
                    " | Action: " + baseItemCfg.action + " | Featured: " + isFeatured);

                ItemStack displayItem = createDynamicItem(itemData, placeholderContext, baseItemCfg);
                inventory.setItem(actualSlotInGui, displayItem);
            }
        }
    }

    protected OfflinePlayer determineContextForDynamicItem(Object itemData) {
        if (itemData instanceof WarpManager.PlayerWarpData) {
            return ((WarpManager.PlayerWarpData) itemData).getOwner();
        } else if (itemData instanceof com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp) {
            com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp iw = (com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp) itemData;
            if (iw.getIsland() != null && iw.getIsland().getOwner() != null) {
                return Bukkit.getOfflinePlayer(iw.getIsland().getOwner().getUniqueId());
            }
        }
        return viewer;
    }

    protected boolean isDynamicItemFeatured(Object itemData) {
        if (itemData instanceof WarpManager.PlayerWarpData) {
            WarpManager.PlayerWarpData pwd = (WarpManager.PlayerWarpData) itemData;
            return pwd.getWarps().stream().anyMatch(w -> plugin.getFeaturedWarpsManager().isFeatured(pwd.getOwner().getUniqueId(), w.getName()));
        } else if (itemData instanceof com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp) {
            com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp iw = (com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp) itemData;
            if (iw.getIsland() != null && iw.getIsland().getOwner() != null) {
                return plugin.getFeaturedWarpsManager().isFeatured(iw.getIsland().getOwner().getUniqueId(), iw.getName());
            }
        }
        return false;
    }

    protected ItemStack buildItemFromConfig(ItemConfig itemCfg, OfflinePlayer placeholderContext, Map<String, String> additionalPlaceholders) {
        if (itemCfg == null) {
            plugin.getLogger().warning("buildItemFromConfig a reçu ItemConfig null. Context: " + (placeholderContext != null ? placeholderContext.getName() : "null"));
            return new ItemStack(Material.BARRIER);
        }
        if ("AIR".equalsIgnoreCase(itemCfg.materialString)) {
            plugin.getLogger().fine("buildItemFromConfig: ItemConfig avec material AIR pour template: " + itemCfg.nameTemplate);
            return null;
        }

        Material mat = Material.getMaterial(itemCfg.materialString.toUpperCase());
        if (mat == null) {
            plugin.getLogger().warning("Matériau invalide '" + itemCfg.materialString + "' pour item (nom template: '" + itemCfg.nameTemplate + "'). Utilisation de BARRIER.");
            mat = Material.BARRIER;
        }
        ItemStackBuilder builder = new ItemStackBuilder(mat);

        String name = parsePlaceholders(itemCfg.nameTemplate, placeholderContext, additionalPlaceholders);
        List<String> lore = parseLorePlaceholders(itemCfg.loreTemplate, placeholderContext, additionalPlaceholders);

        builder.setName(name);
        builder.setLore(lore);

        ItemMeta currentMeta = builder.build().getItemMeta();
        if (currentMeta != null) {
            if (itemCfg.customModelData > 0) {
                currentMeta.setCustomModelData(itemCfg.customModelData);
            }
            builder.itemStack.setItemMeta(currentMeta);
        }

        if (itemCfg.glow) {
            builder.setGlow(true);
        }

        ItemStack item = builder.build();

        if (itemCfg.action != null && !itemCfg.action.equalsIgnoreCase("NONE")) {
            setItemPersistentData(item, ITEM_ACTION_KEY, itemCfg.action);
        }
        if ("COMMAND".equalsIgnoreCase(itemCfg.action) && itemCfg.command != null) {
            setItemPersistentData(item, ITEM_COMMAND_KEY, itemCfg.command);
        }

        if (itemCfg.nbt != null) {
            ItemMeta metaForNbt = item.getItemMeta();
            if (metaForNbt != null) {
                PersistentDataContainer pdc = metaForNbt.getPersistentDataContainer();
                for (Map.Entry<String, String> nbtEntry : itemCfg.nbt.entrySet()) {
                    pdc.set(new NamespacedKey(plugin, nbtEntry.getKey().toLowerCase().replace(" ", "_")), PersistentDataType.STRING, nbtEntry.getValue());
                }
                item.setItemMeta(metaForNbt);
            }
        }
        return item;
    }

    protected SoundConfig getSoundConfigForItem(ItemConfig itemConfigFromLayoutOrTemplate) {
        if (itemConfigFromLayoutOrTemplate != null && itemConfigFromLayoutOrTemplate.clickSound != null) {
            return itemConfigFromLayoutOrTemplate.clickSound;
        }
        if (guiConfig != null && guiConfig.defaultClickSound != null) {
            return guiConfig.defaultClickSound;
        }
        return null;
    }

    // --- DÉBUT DE LA CORRECTION ---
    protected String parsePlaceholders(String text, OfflinePlayer p, Map<String, String> customPlaceholders) {
        if (text == null) return "";

        // NOUVELLE LOGIQUE :
        // 1. Appliquer PlaceholderAPI en premier sur le texte brut.
        String result = plugin.getPapiHook() != null ? plugin.getPapiHook().setPlaceholders(p, text) : text;

        // 2. Appliquer ensuite les placeholders personnalisés (comme %target_player_name%) sur le résultat.
        if (customPlaceholders != null) {
            for (Map.Entry<String, String> entry : customPlaceholders.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    result = result.replace(entry.getKey(), entry.getValue());
                }
            }
        }

        return result; // Retourner le résultat final, entièrement parsé.
    }
    // --- FIN DE LA CORRECTION ---

    protected List<String> parseLorePlaceholders(List<String> lore, OfflinePlayer p, Map<String, String> customPlaceholders) {
        if (lore == null) return Collections.emptyList();
        return lore.stream()
            .map(line -> parsePlaceholders(line, p, customPlaceholders))
            .collect(Collectors.toList());
    }

    protected void setItemPersistentData(ItemStack item, NamespacedKey key, String value) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    protected String getItemPersistentData(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(key, PersistentDataType.STRING, null);
    }

    public void nextPage() {
        if (itemsToDisplay == null || guiConfig.dynamicItemSlots == null || guiConfig.dynamicItemSlots.isEmpty())
            return;
        if ((currentPage + 1) * guiConfig.dynamicItemSlots.size() < itemsToDisplay.size()) {
            currentPage++;
            fillDynamicItems();
            updateNavigationButtonsState();
        }
    }

    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            fillDynamicItems();
            updateNavigationButtonsState();
        }
    }

    protected void updateNavigationButtonsState() {
        if (inventory == null || guiConfig.staticItems == null) return;

        for (ConfiguredItem configuredItem : guiConfig.staticItems) {
            ItemConfig itemCfg = configuredItem.itemConfig;
            if (itemCfg == null) continue;

            if ("PREVIOUS_PAGE".equals(itemCfg.action)) {
                for (int slot : configuredItem.slots) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        if (currentPage > 0) {
                            inventory.setItem(slot, buildItemFromConfig(itemCfg, viewer, null));
                        } else {
                            inventory.setItem(slot, null);
                        }
                    }
                }
            } else if ("NEXT_PAGE".equals(itemCfg.action)) {
                boolean hasNextPage = (itemsToDisplay != null && guiConfig.dynamicItemSlots != null && !guiConfig.dynamicItemSlots.isEmpty() &&
                    (currentPage + 1) * guiConfig.dynamicItemSlots.size() < itemsToDisplay.size());
                for (int slot : configuredItem.slots) {
                    if (slot >= 0 && slot < inventory.getSize()) {
                        if (hasNextPage) {
                            inventory.setItem(slot, buildItemFromConfig(itemCfg, viewer, null));
                        } else {
                            inventory.setItem(slot, null);
                        }
                    }
                }
            }
        }
    }

    public void goBack() {
        if (previousGui != null) {
            previousGui.open();
        } else {
            if (this instanceof MainWarpsGUI) {
                viewer.closeInventory();
            } else {
                plugin.getLogger().warning("Tentative de retour en arrière sans GUI précédent pour " + this.getClass().getSimpleName() + " (viewer: " + viewer.getName() + "), fermeture.");
                viewer.closeInventory();
            }
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
