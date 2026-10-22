package org.Cry_Legende.sHSSBWarpsGUI.gui;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import com.destroystokyo.paper.profile.PlayerProfile;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ItemConfig;
import org.Cry_Legende.sHSSBWarpsGUI.managers.WarpManager;
// ItemStackBuilder n'est pas importé car buildItemFromConfig est dans BaseGUI
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.stream.Collectors;

public class MainWarpsGUI extends BaseGUI {

    public MainWarpsGUI(SHSSBWarpsGUI plugin, Player viewer, List<WarpManager.PlayerWarpData> warpsData) {
        super(plugin, viewer, plugin.getConfigManager().mainGuiConfig, warpsData, null);
    }

    /**
     * Met à jour la ligne d'un joueur après un changement de ses warps (suppression) : sans cela, le menu rouvert par
     * « Retour » garderait le nombre de warps d'avant. Le joueur disparaît de la liste s'il n'a plus de warp public.
     */
    @SuppressWarnings("unchecked")
    public void refreshOwner(UUID ownerId) {
        if (itemsToDisplay == null) return;
        List<WarpManager.PlayerWarpData> data = new ArrayList<>((List<WarpManager.PlayerWarpData>) itemsToDisplay);
        int index = -1;
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).getOwner().getUniqueId().equals(ownerId)) {
                index = i;
                break;
            }
        }
        if (index < 0) return;

        SuperiorPlayer superiorOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(ownerId);
        Island island = superiorOwner == null ? null : superiorOwner.getIsland();
        List<IslandWarp> publicWarps = island == null ? new ArrayList<>() : island.getIslandWarps().values().stream()
            .filter(warp -> !warp.hasPrivateFlag())
            .collect(Collectors.toList());

        if (publicWarps.isEmpty()) {
            data.remove(index);
        } else {
            WarpManager.PlayerWarpData old = data.get(index);
            boolean featured = publicWarps.stream().anyMatch(warp -> plugin.getFeaturedWarpsManager().isFeatured(ownerId, warp.getName()));
            data.set(index, new WarpManager.PlayerWarpData(old.getOwner(), publicWarps, old.isOnline(), featured));
        }
        itemsToDisplay = data;

        // la dernière page peut ne plus exister
        int perPage = guiConfig.dynamicItemSlots == null ? 0 : guiConfig.dynamicItemSlots.size();
        if (perPage > 0 && currentPage > 0 && currentPage * perPage >= data.size()) {
            currentPage = Math.max(0, (data.size() - 1) / perPage);
        }
    }

    @Override
    protected void createAndFillInventory() {
        // 1. Crée l'inventaire standard
        super.createAndFillInventory();

        // 2. Gestion de la ligne de Favoris (Ligne 0: Slots 0-8)
        List<UUID> favorites = plugin.getFavoritesManager().getFavorites(viewer.getUniqueId());

        if (favorites.isEmpty()) return;

        ItemConfig separatorCfg = plugin.getConfigManager().favoriteSeparatorConfig;
        ItemStack separator = buildItemFromConfig(separatorCfg, viewer, null);

        int[] glassSlots = {0, 1, 7, 8};
        for (int slot : glassSlots) {
            inventory.setItem(slot, separator);
        }

        List<WarpManager.PlayerWarpData> allData = (List<WarpManager.PlayerWarpData>) itemsToDisplay;
        List<WarpManager.PlayerWarpData> favoriteWarpsData = allData.stream()
            .filter(d -> favorites.contains(d.getOwner().getUniqueId()))
            .limit(5)
            .collect(Collectors.toList());

        int[] favSlots = {2, 3, 4, 5, 6};

        for (int i = 0; i < favoriteWarpsData.size(); i++) {
            if (i >= favSlots.length) break;

            WarpManager.PlayerWarpData data = favoriteWarpsData.get(i);
            OfflinePlayer owner = data.getOwner();

            boolean isFeatured = data.hasFeaturedWarp();
            ItemConfig templateToUse = isFeatured ? guiConfig.featuredDynamicItemTemplate : guiConfig.dynamicItemTemplate;

            ItemStack headItem = createDynamicItem(data, owner, templateToUse);
            inventory.setItem(favSlots[i], headItem);
        }
    }

    @Override
    protected ItemStack createDynamicItem(Object itemData, OfflinePlayer placeholderContext, ItemConfig baseItemConfig) {
        if (baseItemConfig == null) return new ItemStack(Material.BARRIER);

        WarpManager.PlayerWarpData playerData = (WarpManager.PlayerWarpData) itemData;

        Map<String, String> customPlaceholders = new HashMap<>();
        customPlaceholders.put("%player_name%", placeholderContext.getName() != null ? placeholderContext.getName() : placeholderContext.getUniqueId().toString().substring(0, 8));
        customPlaceholders.put("%warp_count%", String.valueOf(playerData.getWarps().size()));

        String onlineStatus = "&cHors Ligne";
        if (playerData.isOnline()) {
            Player ownerPlayer = Bukkit.getPlayer(playerData.getOwner().getUniqueId());
            if (ownerPlayer != null && viewer.canSee(ownerPlayer)) {
                onlineStatus = "&aEn Ligne";
            }
        }
        customPlaceholders.put("%online_status%", onlineStatus);

        // --- Gestion du Lore ---
        List<String> loreToUse = new ArrayList<>(baseItemConfig.loreTemplate);

        boolean isFavorite = plugin.getFavoritesManager().isFavorite(viewer.getUniqueId(), placeholderContext.getUniqueId());

        // 1. Indicateur "Est Favori" (Toujours avant l'instruction)
        if (isFavorite) {
            List<String> favIndicator = plugin.getConfigManager().loreLineFavorite;
            if (favIndicator != null && !favIndicator.isEmpty()) {
                loreToUse.addAll(favIndicator);
            }
        }

        // 2. Instruction Shift-Clic (Dynamique : Ajouter OU Retirer)
        if (isFavorite) {
            // Message pour retirer
            String msgRemove = plugin.getConfigManager().msgLoreShiftClickUnfavorite;
            if (msgRemove != null && !msgRemove.isEmpty()) {
                loreToUse.add(msgRemove);
            }
        } else {
            // Message pour ajouter
            String msgAdd = plugin.getConfigManager().msgLoreShiftClickFavorite;
            if (msgAdd != null && !msgAdd.isEmpty()) {
                loreToUse.add(msgAdd);
            }
        }

        ItemConfig displayConfig = new ItemConfig(
            baseItemConfig.materialString,
            baseItemConfig.nameTemplate,
            loreToUse,
            baseItemConfig.action,
            baseItemConfig.command,
            baseItemConfig.customModelData,
            baseItemConfig.glow,
            baseItemConfig.nbt,
            baseItemConfig.clickSound
        ).generateDisplayInstance(
            parsePlaceholders(baseItemConfig.nameTemplate, placeholderContext, customPlaceholders),
            parseLorePlaceholders(loreToUse, placeholderContext, customPlaceholders)
        );

        ItemStack displayItem = buildItemFromConfig(displayConfig, placeholderContext, null);

        if (displayItem == null) return new ItemStack(Material.BARRIER);

        setItemPersistentData(displayItem, ITEM_ACTION_KEY, displayConfig.action);
        setItemPersistentData(displayItem, TARGET_PLAYER_UUID_KEY, placeholderContext.getUniqueId().toString());

        if (displayItem.getType() == Material.PLAYER_HEAD) {
            return applyTextureAsync(displayItem, placeholderContext);
        }

        return displayItem;
    }

    private ItemStack applyTextureAsync(ItemStack itemToTexture, OfflinePlayer owner) {
        ItemStack finalDisplayItem = itemToTexture.clone();

        plugin.getTextureService().getPlayerHead(owner, texturedHead -> {
            ItemMeta currentMeta = finalDisplayItem.getItemMeta();

            if (currentMeta instanceof SkullMeta && texturedHead.getItemMeta() instanceof SkullMeta) {
                SkullMeta displaySkullMeta = (SkullMeta) currentMeta;
                SkullMeta texturedSkullMeta = (SkullMeta) texturedHead.getItemMeta();

                PlayerProfile profileFromTexturedHead = texturedSkullMeta.getPlayerProfile();

                if (profileFromTexturedHead != null) {
                    displaySkullMeta.setPlayerProfile(profileFromTexturedHead);
                } else if (texturedSkullMeta.hasOwner()) {
                    displaySkullMeta.setOwningPlayer(texturedSkullMeta.getOwningPlayer());
                }
                finalDisplayItem.setItemMeta(displaySkullMeta);
            }

            if (inventory != null && inventory.getHolder() == this) {
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack itemInSlot = inventory.getItem(slot);
                    if (itemInSlot != null &&
                        Objects.equals(getItemPersistentData(itemInSlot, TARGET_PLAYER_UUID_KEY), owner.getUniqueId().toString()) &&
                        Objects.equals(getItemPersistentData(itemInSlot, ITEM_ACTION_KEY), getItemPersistentData(itemToTexture, ITEM_ACTION_KEY))) {
                        inventory.setItem(slot, finalDisplayItem);
                    }
                }
            }
        });
        return itemToTexture;
    }
}
