package org.Cry_Legende.sHSSBWarpsGUI.gui;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfiguredItem;
import org.Cry_Legende.sHSSBWarpsGUI.config.ItemConfig;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FeaturedWarpsManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class PlayerSpecificWarpsGUI extends BaseGUI {

    private final OfflinePlayer warpOwnerOffline;

    public PlayerSpecificWarpsGUI(SHSSBWarpsGUI plugin, Player viewer, OfflinePlayer warpOwner, List<IslandWarp> warps, BaseGUI previousGui) {
        super(plugin, viewer, plugin.getConfigManager().playerWarpsGuiConfig, warps, previousGui);
        this.warpOwnerOffline = warpOwner;
    }

    public OfflinePlayer getWarpOwnerOffline() {
        return warpOwnerOffline;
    }

    @Override
    protected void createAndFillInventory() {
        if (guiConfig == null) {
            plugin.getLogger().severe("GuiConfig est null pour " + this.getClass().getSimpleName() + ". GUI non ouvert.");
            ChatUtils.sendMessage(viewer, "&cErreur: Configuration du menu introuvable.");
            return;
        }

        SuperiorPlayer ssbOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(warpOwnerOffline.getUniqueId());
        Island island = (ssbOwner != null) ? ssbOwner.getIsland() : null;

        Map<String, String> customPlaceholders = new HashMap<>();
        customPlaceholders.put("%target_player_name%", warpOwnerOffline.getName() != null ? warpOwnerOffline.getName() : "Inconnu");

        if (island != null) {
            customPlaceholders.put("%island_rating_formatted%", String.format("%.1f ★", island.getTotalRating()));
            customPlaceholders.put("%island_level_formatted%", String.format("%,d", island.getIslandLevel().longValue()));

            List<SuperiorPlayer> members = island.getIslandMembers(true);
            long onlineMembers = members.stream().filter(member -> {
                Player memberPlayer = member.asPlayer();
                return memberPlayer != null && viewer.canSee(memberPlayer);
            }).count();

            customPlaceholders.put("%island_members_online%", String.valueOf(onlineMembers));
            customPlaceholders.put("%island_members_max%", String.valueOf(members.size()));

        } else {
            customPlaceholders.put("%island_rating_formatted%", "&cN/A");
            customPlaceholders.put("%island_level_formatted%", "&cN/A");
            customPlaceholders.put("%island_members_online%", "0");
            customPlaceholders.put("%island_members_max%", "0");
        }

        String title = parsePlaceholders(guiConfig.title, warpOwnerOffline, customPlaceholders);
        inventory = Bukkit.createInventory(this, guiConfig.size, ChatUtils.color(title));

        if (guiConfig.staticItems != null) {
            for (ConfiguredItem configuredItem : guiConfig.staticItems) {
                if (configuredItem.itemConfig == null) continue;

                ItemStack itemStack = buildItemFromConfig(configuredItem.itemConfig, warpOwnerOffline, customPlaceholders);
                if (itemStack == null || itemStack.getType() == Material.AIR) continue;

                for (int slot : configuredItem.slots) {
                    if (slot >= 0 && slot < guiConfig.size) {
                        inventory.setItem(slot, itemStack.clone());
                    }
                }

                if ("PLAYER_INFO_HEAD".equalsIgnoreCase(configuredItem.itemConfig.action) && itemStack.getType() == Material.PLAYER_HEAD) {
                    plugin.getTextureService().getPlayerHead(warpOwnerOffline, texturedHead -> {
                        if (inventory.getViewers().isEmpty() || texturedHead == null || !(texturedHead.getItemMeta() instanceof SkullMeta))
                            return;

                        SkullMeta texturedMeta = (SkullMeta) texturedHead.getItemMeta();
                        if (texturedMeta.getPlayerProfile() == null) return;

                        for (int slot : configuredItem.slots) {
                            ItemStack currentItem = inventory.getItem(slot);
                            if (currentItem != null && currentItem.getItemMeta() instanceof SkullMeta) {
                                SkullMeta finalMeta = (SkullMeta) currentItem.getItemMeta();
                                finalMeta.setPlayerProfile(texturedMeta.getPlayerProfile());
                                currentItem.setItemMeta(finalMeta);
                            }
                        }
                    });
                }
            }
        }

        fillDynamicItems();
        updateNavigationButtonsState();
    }

    @Override
    protected ItemStack createDynamicItem(Object itemData, OfflinePlayer placeholderContext, ItemConfig baseItemConfig) {
        IslandWarp warp = (IslandWarp) itemData;
        ConfigManager cfgMgr = plugin.getConfigManager();
        boolean isThisWarpFeatured = plugin.getFeaturedWarpsManager().isFeatured(warpOwnerOffline.getUniqueId(), warp.getName());

        ItemConfig itemConfigToUse = isThisWarpFeatured && guiConfig.featuredDynamicItemTemplate != null ?
            guiConfig.featuredDynamicItemTemplate : baseItemConfig;

        Map<String, String> customPlaceholders = new HashMap<>();
        customPlaceholders.put("%warp_name%", warp.getName());
        customPlaceholders.put("%owner_name%", warpOwnerOffline.getName() != null ? warpOwnerOffline.getName() : "Inconnu");
        Location loc = warp.getLocation();
        customPlaceholders.put("%warp_x%", String.valueOf(loc.getBlockX()));
        customPlaceholders.put("%warp_y%", String.valueOf(loc.getBlockY()));
        customPlaceholders.put("%warp_z%", String.valueOf(loc.getBlockZ()));
        customPlaceholders.put("%world%", loc.getWorld() != null ? loc.getWorld().getName() : "N/A");

        int visitCount = plugin.getWarpVisitManager().getVisitCount(warpOwnerOffline.getUniqueId(), warp.getName());
        customPlaceholders.put("%warp_visit_count%", String.valueOf(visitCount));

        List<String> warpDescriptionLines = new ArrayList<>();
        ItemStack ssbIcon = warp.getRawIcon();
        if (ssbIcon != null && ssbIcon.hasItemMeta()) {
            ItemMeta iconMeta = ssbIcon.getItemMeta();
            if (iconMeta != null && iconMeta.hasLore()) {
                warpDescriptionLines.addAll(iconMeta.getLore());
            }
        }
        for (int i = 0; i < 5; i++) {
            // Note: warpDescriptionLines contient déjà les couleurs car extrait de l'itemMeta existant ou coloré à la saisie
            customPlaceholders.put("%warp_description_line_" + (i + 1) + "%",
                i < warpDescriptionLines.size() ? ChatUtils.color(warpDescriptionLines.get(i)) : "");
        }
        if (warpDescriptionLines.isEmpty()) {
            customPlaceholders.put("%warp_description_line_1%", ChatUtils.color("&7Aucune description."));
        }

        if (isThisWarpFeatured) {
            FeaturedWarpsManager.FeaturedWarpEntry featuredEntry = plugin.getFeaturedWarpsManager().getFeaturedEntry(warpOwnerOffline.getUniqueId(), warp.getName());
            if (featuredEntry != null) {
                SimpleDateFormat sdf = new SimpleDateFormat(cfgMgr.featuredDateTimeFormat);
                customPlaceholders.put("%feature_expiry_date%", sdf.format(new Date(featuredEntry.expiryTimestamp)));
                customPlaceholders.put("%feature_duration_formatted%", ConfigManager.formatDuration(featuredEntry.expiryTimestamp - System.currentTimeMillis(), cfgMgr.featuredDurationFormat));
            }
        }

        SuperiorPlayer ssbViewer = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(viewer);
        ItemStack iconFromWarpSSB = ssbViewer != null ? warp.getIcon(ssbViewer) : warp.getRawIcon();

        // --- CORRECTION: Préservation des métadonnées des items (Potions, Bannières, etc.) ---
        ItemStack displayItem;

        if (iconFromWarpSSB != null && iconFromWarpSSB.getType() != Material.AIR) {
            // On clone l'icône réelle pour garder toutes les propriétés (PotionMeta, BannerMeta, etc.)
            displayItem = iconFromWarpSSB.clone();

            // On applique UNIQUEMENT le nom et le lore définis dans la config par-dessus
            String displayName = parsePlaceholders(itemConfigToUse.nameTemplate, placeholderContext, customPlaceholders);
            List<String> displayLore = parseLorePlaceholders(itemConfigToUse.loreTemplate, placeholderContext, customPlaceholders);

            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatUtils.color(displayName));

                // CORRECTION ICI : Appliquer ChatUtils.color() sur l'ensemble du Lore
                // (Le displayLore vient de la config et contient des placeholders, mais les couleurs & n'ont pas encore été traitées)
                meta.setLore(ChatUtils.color(displayLore));

                // Gestion spéciale du Glow si la config le force
                if (itemConfigToUse.glow) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                displayItem.setItemMeta(meta);
            }
        } else {
            // Fallback : Si le warp n'a pas d'icône valide, on construit depuis la config
            ItemConfig finalConfigForBuild = new ItemConfig(
                itemConfigToUse.materialString,
                parsePlaceholders(itemConfigToUse.nameTemplate, placeholderContext, customPlaceholders),
                parseLorePlaceholders(itemConfigToUse.loreTemplate, placeholderContext, customPlaceholders),
                itemConfigToUse.action,
                itemConfigToUse.command,
                itemConfigToUse.customModelData,
                itemConfigToUse.glow,
                itemConfigToUse.nbt,
                itemConfigToUse.clickSound
            );
            // buildItemFromConfig gère déjà les couleurs via ItemStackBuilder
            displayItem = buildItemFromConfig(finalConfigForBuild, placeholderContext, null);
        }

        if (displayItem != null) {
            setItemPersistentData(displayItem, WARP_OWNER_KEY, warpOwnerOffline.getUniqueId().toString());
            setItemPersistentData(displayItem, WARP_NAME_KEY, warp.getName());

            if (itemConfigToUse.action != null && !itemConfigToUse.action.equals("NONE")) {
                setItemPersistentData(displayItem, ITEM_ACTION_KEY, itemConfigToUse.action);
            }
        } else {
            return new ItemStack(Material.BARRIER);
        }
        return displayItem;
    }

    public void refreshWarpsAndReopen() {
        // le menu principal (bouton « Retour ») garderait sinon le nombre de warps d'avant la suppression
        if (previousGui instanceof MainWarpsGUI mainGui) {
            mainGui.refreshOwner(warpOwnerOffline.getUniqueId());
        }
        SuperiorPlayer ssbOwner = SuperiorSkyblockAPI.getPlayers().getSuperiorPlayer(warpOwnerOffline.getUniqueId());
        if (ssbOwner != null && ssbOwner.getIsland() != null) {
            Island island = ssbOwner.getIsland();
            List<IslandWarp> updatedWarps = new ArrayList<>(island.getIslandWarps().values());

            if (!viewer.getUniqueId().equals(warpOwnerOffline.getUniqueId())) {
                this.itemsToDisplay = updatedWarps.stream().filter(w -> !w.hasPrivateFlag()).collect(Collectors.toList());
            } else {
                this.itemsToDisplay = updatedWarps;
            }

            if (this.itemsToDisplay.isEmpty()) {
                ChatUtils.sendMessage(viewer, "&eCe joueur n'a plus de warps accessibles.");
                if (previousGui != null) {
                    previousGui.open();
                } else {
                    viewer.closeInventory();
                }
            } else {
                this.currentPage = 0;
                this.open();
            }
        } else {
            ChatUtils.sendMessage(viewer, "&cL'île de ce joueur n'est plus accessible.");
            if (previousGui != null) {
                previousGui.open();
            } else {
                viewer.closeInventory();
            }
        }
    }
}
