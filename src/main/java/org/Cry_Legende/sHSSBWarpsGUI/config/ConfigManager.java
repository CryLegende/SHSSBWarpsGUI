package org.Cry_Legende.sHSSBWarpsGUI.config;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ConfigManager {

    private final SHSSBWarpsGUI plugin;
    public FileConfiguration config;

    public GuiConfig mainGuiConfig;
    public GuiConfig playerWarpsGuiConfig;
    public GuiConfig manageWarpGuiConfig;

    public boolean featuredEnabled;
    public double featuredCost;
    public long featuredDurationMillis;
    public String featuredDurationFormat;
    public String featuredDateTimeFormat;

    // Variables pour le compteur de visites
    public boolean visitCounterEnabled;
    public int visitCounterCooldownSeconds;

    // Variables pour les Favoris
    public int favoritesMaxCount;
    public ItemConfig favoriteSeparatorConfig;
    public String msgFavoriteAdded;
    public String msgFavoriteRemoved;
    public String msgFavoriteFull;
    public List<String> loreLineFavorite;

    // NOUVEAU : Messages distincts pour Ajouter et Retirer
    public String msgLoreShiftClickFavorite;    // "Shift-Clic pour mettre"
    public String msgLoreShiftClickUnfavorite;  // "Shift-Clic pour retirer"

    public ItemConfig featureWarpButtonConfig;
    public ItemConfig unfeatureWarpButtonConfig;

    public String msgNoPermissionCommand;
    public String msgNoPermissionFeature;
    public String msgPlayerOnlyCommand;
    public String msgWarpNotFound;
    public String msgIslandNotFound;
    public String msgNotYourWarp;
    public String msgGenericError;
    public String msgTeleporting;
    public String msgTeleportWarmup;
    public String msgActionCancelled;
    public String msgPendingInputActionCancelled;
    public String msgMaxLoreLinesReached;
    public String msgDeleteConfirmInstruction;

    public String msgFeaturedSuccess, msgFeaturedRemoved, msgNotEnoughMoney, msgAlreadyFeatured, msgNotFeatured, msgNoWarpsToFeature, msgFeatureExpired;

    public String msgWarpRenamed, msgWarpIconSet, msgWarpDescSet, msgWarpLocSet, msgWarpPrivacySet, msgWarpDeleted;
    public String instructionRename, instructionSetIcon, instructionSetDesc;

    public String titleFeatureConfirmGui;
    public String titleUnfeatureConfirmGui;
    public String itemCancelNameFeatureConfirmGui;
    public List<String> itemCancelLoreFeatureConfirmGui;


    public ConfigManager(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();

        // Chargement de la configuration pour le compteur de visites
        ConfigurationSection visitCounterSection = config.getConfigurationSection("visit-counter");
        if (visitCounterSection != null) {
            visitCounterEnabled = visitCounterSection.getBoolean("enabled", true);
            visitCounterCooldownSeconds = visitCounterSection.getInt("cooldown-seconds", 60);
        } else {
            visitCounterEnabled = true;
            visitCounterCooldownSeconds = 60;
        }

        // Chargement de la configuration pour les Favoris
        ConfigurationSection favSection = config.getConfigurationSection("favorites");
        if (favSection != null) {
            favoritesMaxCount = favSection.getInt("max-count", 5);
            favoriteSeparatorConfig = loadItemConfig(favSection.getConfigurationSection("separator-item"), "GRAY_STAINED_GLASS_PANE", "NONE");

            ConfigurationSection favMsgSection = favSection.getConfigurationSection("messages");
            if (favMsgSection != null) {
                msgFavoriteAdded = favMsgSection.getString("added", "&aJoueur ajouté aux favoris !");
                msgFavoriteRemoved = favMsgSection.getString("removed", "&cJoueur retiré des favoris.");
                msgFavoriteFull = favMsgSection.getString("full", "&cVous avez atteint la limite de favoris.");
            } else {
                msgFavoriteAdded = "&aJoueur ajouté aux favoris !";
                msgFavoriteRemoved = "&cJoueur retiré des favoris.";
                msgFavoriteFull = "&cVous avez atteint la limite de favoris.";
            }

            loreLineFavorite = favSection.getStringList("lore-indicator");
            if (loreLineFavorite.isEmpty()) {
                loreLineFavorite = Collections.singletonList("&e⭐ &6Dans vos favoris");
            }

            // Chargement des messages d'action
            msgLoreShiftClickFavorite = favSection.getString("lore-action", "&8&o(( &6&oShift-Clic pour mettre en favori &8&o))");
            msgLoreShiftClickUnfavorite = favSection.getString("lore-action-remove", "&8&o(( &c&oShift-Clic pour retirer des favoris &8&o))");

        } else {
            // Valeurs par défaut si la section 'favorites' manque
            favoritesMaxCount = 5;
            favoriteSeparatorConfig = new ItemConfig("GRAY_STAINED_GLASS_PANE", " ", Collections.emptyList(), "NONE", null, 0, false, null, null);
            msgFavoriteAdded = "&aJoueur ajouté aux favoris !";
            msgFavoriteRemoved = "&cJoueur retiré des favoris.";
            msgFavoriteFull = "&cVous avez atteint la limite de favoris.";
            loreLineFavorite = Collections.singletonList("&e⭐ &6Dans vos favoris");
            msgLoreShiftClickFavorite = "&8&o(( &6&oShift-Clic pour mettre en favori &8&o))";
            msgLoreShiftClickUnfavorite = "&8&o(( &c&oShift-Clic pour retirer des favoris &8&o))";
        }

        ConfigurationSection globalMsgSection = config.getConfigurationSection("messages");
        if (globalMsgSection != null) {
            msgNoPermissionCommand = globalMsgSection.getString("no-permission-command", "&cPermission refusée pour cette commande.");
            msgNoPermissionFeature = globalMsgSection.getString("no-permission-feature", "&cPermission refusée pour cette action.");
            msgPlayerOnlyCommand = globalMsgSection.getString("player-only-command", "&cCette commande est réservée aux joueurs.");
            msgWarpNotFound = globalMsgSection.getString("warp-not-found", "&cWarp '&e%warp_name%&c' introuvable.");
            msgIslandNotFound = globalMsgSection.getString("island-not-found", "&cÎle introuvable.");
            msgNotYourWarp = globalMsgSection.getString("not-your-warp", "&cLe warp '%warp_name%' ne vous appartient pas.");
            msgGenericError = globalMsgSection.getString("generic-error", "&cUne erreur est survenue.");
            instructionRename = globalMsgSection.getString("instruction-rename", "&eTapez le nouveau nom pour le warp '&b%warp_name%&e' dans le chat, ou 'annuler'.");
            instructionSetIcon = globalMsgSection.getString("instruction-seticon", "&ePlacez l'item pour la nouvelle icône du warp '&b%warp_name%&e' dans votre main et tapez &a/swg confirmicon %warp_name%&e, ou 'annuler'.");
            instructionSetDesc = globalMsgSection.getString("instruction-setdesc", "&eTapez la nouvelle description (lore). Utilisez '//' pour une nouvelle ligne. Tapez 'fin' pour terminer, ou 'annuler'.");
            msgTeleporting = globalMsgSection.getString("teleporting", "&aTéléportation vers &e%warp_name%&a...");
            msgTeleportWarmup = globalMsgSection.getString("teleport-warmup", "&eTéléportation dans %time% seconde(s)...");
            msgActionCancelled = globalMsgSection.getString("action-cancelled", "&eAction annulée.");
            msgPendingInputActionCancelled = globalMsgSection.getString("pending-input-action-cancelled", "&eSaisie en attente annulée.");
            msgMaxLoreLinesReached = globalMsgSection.getString("max-lore-lines-reached", "&cVous avez atteint le nombre maximum de %max_lines% lignes pour la description.");

        } else {
            plugin.getLogger().warning("Section 'messages' manquante dans config.yml! Utilisation de messages par défaut.");
            msgNoPermissionCommand = "&cPermission refusée.";
            msgNoPermissionFeature = "&cPermission refusée.";
            msgPlayerOnlyCommand = "&cJoueurs seulement.";
            msgWarpNotFound = "&cWarp introuvable.";
            msgIslandNotFound = "&cÎle introuvable.";
            msgNotYourWarp = "&cPas votre warp.";
            msgGenericError = "&cErreur.";
            instructionRename = "&eEntrez nom...";
            instructionSetIcon = "&eEntrez icone...";
            instructionSetDesc = "&eEntrez desc...";
            msgTeleporting = "&aTéléportation...";
            msgTeleportWarmup = "&eWarmup...";
            msgActionCancelled = "&eAction annulée.";
            msgPendingInputActionCancelled = "&eSaisie annulée.";
            msgMaxLoreLinesReached = "&cMax lignes.";
        }

        mainGuiConfig = loadGuiConfig("main-gui",
            "dynamic-item-template", "PLAYER_HEAD", "OPEN_PLAYER_WARPS",
            "featured-dynamic-item-template", "PLAYER_HEAD", "OPEN_PLAYER_WARPS",
            "dynamic-slots");

        playerWarpsGuiConfig = loadGuiConfig("player-warps-gui",
            "dynamic-item-template", "ENDER_PEARL", "TELEPORT_TO_WARP",
            "featured-dynamic-item-template", "NETHER_STAR", "TELEPORT_TO_WARP",
            "dynamic-slots");

        manageWarpGuiConfig = loadGuiConfig("manage-warp-gui",
            null, null, null,
            null, null, null,
            null);
        if (manageWarpGuiConfig == null) {
            plugin.getLogger().severe("ÉCHEC CRITIQUE du chargement de la configuration pour 'manage-warp-gui'.");
            manageWarpGuiConfig = new GuiConfig("Erreur ManageWarp", 27, new SoundConfig(Sound.UI_BUTTON_CLICK, 1, 1), new ArrayList<>(), null, null, new ArrayList<>());
        }


        ConfigurationSection featuredSection = config.getConfigurationSection("featured-warps");
        if (featuredSection != null) {
            featuredEnabled = featuredSection.getBoolean("enabled", true);
            featuredCost = featuredSection.getDouble("cost", 1000.0);
            featuredDurationMillis = parseDuration(featuredSection.getString("duration", "7d"));
            featuredDurationFormat = featuredSection.getString("duration-format", "%d%j, %h%h, %m%min");
            featuredDateTimeFormat = featuredSection.getString("date-format", "dd/MM/yyyy 'à' HH:mm");

            ConfigurationSection featureMsgSection = featuredSection.getConfigurationSection("messages");
            if (featureMsgSection != null) {
                msgFeaturedSuccess = featureMsgSection.getString("success", "&aWarp '%warp_name%' mis en avant pour %duration%!");
                msgFeaturedRemoved = featureMsgSection.getString("removed", "&eWarp '%warp_name%' n'est plus en avant.");
                msgNotEnoughMoney = featureMsgSection.getString("not-enough-money", "&cPas assez d'argent. Requis: %cost% %vault_currency_name_plural%.");
                msgAlreadyFeatured = featureMsgSection.getString("already-featured", "&cWarp '%warp_name%' déjà en avant. Expire: %feature_expiry_date%.");
                msgNotFeatured = featureMsgSection.getString("not-featured", "&cWarp '%warp_name%' pas en avant.");
                msgNoWarpsToFeature = featureMsgSection.getString("no-warps-to-feature", "&cVous n'avez pas de warp à mettre en avant.");
                msgFeatureExpired = featureMsgSection.getString("expired", "&6La mise en avant du warp '%warp_name%' de %owner_name% a expiré.");
            } else {
                plugin.getLogger().warning("Section 'featured-warps.messages' manquante!");
                msgFeaturedSuccess = "&aWarp mis en avant!";
                msgFeaturedRemoved = "&eWarp retiré.";
                msgNotEnoughMoney = "&cPas assez d'argent.";
                msgAlreadyFeatured = "&cDéjà en avant.";
                msgNotFeatured = "&cPas en avant.";
                msgNoWarpsToFeature = "&cAucun warp.";
                msgFeatureExpired = "&6Expiré.";
            }
        } else {
            plugin.getLogger().warning("Section 'featured-warps' manquante!");
            featuredEnabled = false;
        }

        featureWarpButtonConfig = loadItemConfig(config.getConfigurationSection("player-warps-gui.feature-this-warp-button"),
            "EMERALD_BLOCK", "CONFIRM_FEATURE_THIS_WARP");
        unfeatureWarpButtonConfig = loadItemConfig(config.getConfigurationSection("player-warps-gui.unfeature-this-warp-button"),
            "REDSTONE_BLOCK", "CONFIRM_UNFEATURE_THIS_WARP");

        ConfigurationSection manageWarpMsgSection = config.getConfigurationSection("manage-warp-messages");
        if (manageWarpMsgSection != null) {
            msgWarpRenamed = manageWarpMsgSection.getString("renamed", "&aWarp renommé en '&e%new_warp_name%&a'.");
            msgWarpIconSet = manageWarpMsgSection.getString("icon-set", "&aIcône du warp '&e%warp_name%&a' mise à jour.");
            msgWarpDescSet = manageWarpMsgSection.getString("desc-set", "&aDescription du warp '&e%warp_name%&a' mise à jour.");
            msgWarpLocSet = manageWarpMsgSection.getString("location-set", "&aPosition du warp '&e%warp_name%&a' mise à jour.");
            msgWarpPrivacySet = manageWarpMsgSection.getString("privacy-set", "&aVisibilité du warp '&e%warp_name%&a' changée en %status%.");
            msgWarpDeleted = manageWarpMsgSection.getString("deleted", "&cWarp '&e%warp_name%&c' supprimé.");
            msgDeleteConfirmInstruction = manageWarpMsgSection.getString("delete-confirm-instruction", "&cÊtes-vous sûr de vouloir supprimer le warp '&e%warp_name%&c'?\n&cTapez &b/%label% deletewarp %warp_name% confirm &cpour confirmer.");
        } else {
            plugin.getLogger().warning("Section 'manage-warp-messages' manquante. Utilisation de messages par défaut.");
            msgWarpRenamed = "&aWarp renommé.";
            msgWarpIconSet = "&aIcône mise à jour.";
            msgWarpDescSet = "&aDescription mise à jour.";
            msgWarpLocSet = "&aPosition mise à jour.";
            msgWarpPrivacySet = "&aVisibilité changée.";
            msgWarpDeleted = "&cWarp supprimé.";
            msgDeleteConfirmInstruction = "&cConfirmer suppression /cmd deletewarp %warp_name% confirm";
        }

        ConfigurationSection featureConfirmGuiSection = config.getConfigurationSection("feature-confirmation-gui-texts");
        if (featureConfirmGuiSection != null) {
            titleFeatureConfirmGui = featureConfirmGuiSection.getString("title-feature", "&2&lMettre en Avant ce Warp ?");
            titleUnfeatureConfirmGui = featureConfirmGuiSection.getString("title-unfeature", "&4&lRetirer la Mise en Avant ?");
            itemCancelNameFeatureConfirmGui = featureConfirmGuiSection.getString("cancel-button.name", "&cCanceller");
            itemCancelLoreFeatureConfirmGui = featureConfirmGuiSection.getStringList("cancel-button.lore");
        } else {
            plugin.getLogger().warning("Section 'feature-confirmation-gui-texts' manquante. Utilisation de textes par défaut.");
            titleFeatureConfirmGui = "&2&lMettre en Avant ce Warp ?";
            titleUnfeatureConfirmGui = "&4&lRetirer la Mise en Avant ?";
            itemCancelNameFeatureConfirmGui = "&cCanceller";
            itemCancelLoreFeatureConfirmGui = List.of("&7Revenir au menu précédent.");
        }
    }

    public GuiConfig getManageWarpGuiConfig() {
        if (this.manageWarpGuiConfig == null) {
            plugin.getLogger().warning("manageWarpGuiConfig était null, tentative de rechargement ad-hoc.");
            this.manageWarpGuiConfig = loadGuiConfig("manage-warp-gui", null, null, null, null, null, null, null);
        }
        return this.manageWarpGuiConfig;
    }

    private GuiConfig loadGuiConfig(String basePath,
                                    String dynamicItemConfigPath, String defaultDynamicMaterial, String defaultDynamicAction,
                                    String featuredDynamicItemConfigPath, String defaultFeaturedMaterial, String defaultFeaturedAction,
                                    String dynamicSlotsConfigPath) {
        ConfigurationSection section = config.getConfigurationSection(basePath);

        String effectiveDefaultDynMat = (dynamicItemConfigPath != null) ? defaultDynamicMaterial : "STONE";
        String effectiveDefaultDynAct = (dynamicItemConfigPath != null) ? defaultDynamicAction : "NONE";
        String effectiveDefaultFeatMat = (featuredDynamicItemConfigPath != null) ? defaultFeaturedMaterial : "STONE";
        String effectiveDefaultFeatAct = (featuredDynamicItemConfigPath != null) ? defaultFeaturedAction : "NONE";

        if (section == null) {
            plugin.getLogger().warning("Section GUI '" + basePath + "' manquante! Une config par défaut minimale sera utilisée.");
            SoundConfig errorSound = new SoundConfig(Sound.UI_BUTTON_CLICK, 1f, 1f);
            ItemConfig errorDynamic = (dynamicItemConfigPath != null) ? new ItemConfig(effectiveDefaultDynMat, "Erreur Template Dyn", Collections.emptyList(), effectiveDefaultDynAct, null, 0, false, null, null) : null;
            ItemConfig errorFeatured = (featuredDynamicItemConfigPath != null) ? new ItemConfig(effectiveDefaultFeatMat, "Erreur Template Feat", Collections.emptyList(), effectiveDefaultFeatAct, null, 0, true, null, null) : null;
            List<Integer> defaultDynSlots = (dynamicSlotsConfigPath != null) ? getDefaultSlotsForGui(basePath, 27) : Collections.emptyList();
            return new GuiConfig("Erreur Config: " + basePath, 27, errorSound, new ArrayList<>(), errorDynamic, errorFeatured, defaultDynSlots);
        }

        String title = section.getString("title", "Inventaire " + basePath);
        int size = section.getInt("size", 27);
        SoundConfig defaultSound = loadSoundConfig(section.getConfigurationSection("default-click-sound"), new SoundConfig(Sound.UI_BUTTON_CLICK, 1f, 1f));

        List<ConfiguredItem> staticItemsList = new ArrayList<>();
        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String itemKey : itemsSection.getKeys(false)) {
                ConfigurationSection itemEntrySection = itemsSection.getConfigurationSection(itemKey);
                if (itemEntrySection == null) {
                    continue;
                }
                ItemConfig itemCfg = loadItemConfig(itemEntrySection, "STONE", "NONE");

                List<Integer> parsedSlots;
                if (itemEntrySection.isList("slots")) {
                    parsedSlots = parseSlots(itemEntrySection.getStringList("slots"));
                } else {
                    parsedSlots = parseSlots(Collections.singletonList(itemEntrySection.getString("slots", "")));
                }

                if (itemCfg != null && !parsedSlots.isEmpty()) {
                    staticItemsList.add(new ConfiguredItem(itemCfg, parsedSlots));
                }
            }
        }

        ItemConfig dynamicItemTpl = (dynamicItemConfigPath != null) ? loadItemConfig(section.getConfigurationSection(dynamicItemConfigPath), effectiveDefaultDynMat, effectiveDefaultDynAct) : null;
        ItemConfig featuredDynamicItemTpl = (featuredDynamicItemConfigPath != null) ? loadItemConfig(section.getConfigurationSection(featuredDynamicItemConfigPath), effectiveDefaultFeatMat, effectiveDefaultFeatAct) : null;

        if (featuredDynamicItemTpl != null && dynamicItemConfigPath != null && section.isSet(featuredDynamicItemConfigPath) && !section.isSet(featuredDynamicItemConfigPath + ".glow")) {
            if (!section.getBoolean(featuredDynamicItemConfigPath + ".glow-explicitly-false", false)) {
                featuredDynamicItemTpl = new ItemConfig(featuredDynamicItemTpl.materialString, featuredDynamicItemTpl.nameTemplate, featuredDynamicItemTpl.loreTemplate, featuredDynamicItemTpl.action, featuredDynamicItemTpl.command, featuredDynamicItemTpl.customModelData, true, featuredDynamicItemTpl.nbt, featuredDynamicItemTpl.clickSound);
            }
        }

        List<Integer> dynamicSlots = (dynamicSlotsConfigPath != null) ? parseSlots(section.getStringList(dynamicSlotsConfigPath)) : Collections.emptyList();
        if (dynamicSlotsConfigPath != null && dynamicSlots.isEmpty() && !"manage-warp-gui".equals(basePath)) {
            dynamicSlots = getDefaultSlotsForGui(basePath, size);
        }

        return new GuiConfig(title, size, defaultSound, staticItemsList, dynamicItemTpl, featuredDynamicItemTpl, dynamicSlots);
    }

    private ItemConfig loadItemConfig(ConfigurationSection section, String defaultMaterialOnError, String defaultActionOnError) {
        if (section == null) {
            return new ItemConfig(defaultMaterialOnError, "Item Défaut (Section Null)", Collections.emptyList(), defaultActionOnError, null, 0, false, null, null);
        }

        String material = section.getString("material", defaultMaterialOnError);
        String name = section.getString("name-template", section.getString("name", ""));
        List<String> lore = section.getStringList("lore-template");
        if (lore.isEmpty() && section.isList("lore")) lore = section.getStringList("lore");
        String action = section.getString("action", defaultActionOnError).toUpperCase();
        String command = section.getString("command");
        int modelData = section.getInt("custom-model-data", 0);
        boolean glow = section.getBoolean("glow", false);
        SoundConfig sound = loadSoundConfig(section.getConfigurationSection("click-sound"), null);

        Map<String, String> nbtMap = null;
        ConfigurationSection nbtSection = section.getConfigurationSection("nbt");
        if (nbtSection != null) {
            nbtMap = new HashMap<>();
            for (String key : nbtSection.getKeys(false)) {
                nbtMap.put(key, nbtSection.getString(key));
            }
        }
        return new ItemConfig(material, name, lore, action, command, modelData, glow, nbtMap, sound);
    }

    private SoundConfig loadSoundConfig(ConfigurationSection section, SoundConfig defaultConfig) {
        if (section == null) return defaultConfig;
        String raw = section.getString("sound");
        Sound sound = raw == null
            ? (defaultConfig != null ? defaultConfig.sound : Sound.UI_BUTTON_CLICK)
            : sound(raw);
        if (sound == null) {
            plugin.getLogger().warning("Son invalide dans la config: '" + raw + "' (section: " + section.getCurrentPath() + ").");
            return defaultConfig != null ? defaultConfig : new SoundConfig(Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
        float volume = (float) section.getDouble("volume", defaultConfig != null ? defaultConfig.volume : 1.0);
        float pitch = (float) section.getDouble("pitch", defaultConfig != null ? defaultConfig.pitch : 1.0);
        return new SoundConfig(sound, volume, pitch);
    }

    /**
     * Son lu en config : ancien nom (ENTITY_PLAYER_LEVELUP) ou clé (minecraft:entity.player.levelup).
     */
    private static Sound sound(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim();
        if (s.indexOf(':') >= 0 || s.indexOf('.') >= 0) {
            NamespacedKey key = NamespacedKey.fromString(s.toLowerCase(Locale.ROOT));
            return key == null ? null : Registry.SOUND_EVENT.get(key);
        }
        try {
            return (Sound) Sound.class.getField(s.toUpperCase(Locale.ROOT)).get(null);
        } catch (ReflectiveOperationException | ClassCastException e) {
            return null;
        }
    }

    private List<Integer> getDefaultSlotsForGui(String guiBasePath, int guiSize) {
        List<Integer> defaultSlots = new ArrayList<>();
        if (guiSize == 54) {
            for (int i = 10; i <= 16; i++) defaultSlots.add(i);
            for (int i = 19; i <= 25; i++) defaultSlots.add(i);
            for (int i = 28; i <= 34; i++) defaultSlots.add(i);
            for (int i = 37; i <= 43; i++) defaultSlots.add(i);
        } else if (guiSize == 45) {
            for (int i = 10; i <= 16; i++) defaultSlots.add(i);
            for (int i = 19; i <= 25; i++) defaultSlots.add(i);
            for (int i = 28; i <= 34; i++) defaultSlots.add(i);
        } else if (guiSize == 36) {
            for (int i = 10; i <= 16; i++) defaultSlots.add(i);
            for (int i = 19; i <= 25; i++) defaultSlots.add(i);
        } else if (guiSize == 27) {
            for (int i = 10; i <= 16; i++) defaultSlots.add(i);
        } else {
            int startSlot = (guiSize >= 18) ? 10 : 0;
            int endSlot = guiSize - (guiSize >= 18 ? 10 : 1);
            for (int i = startSlot; i <= endSlot && defaultSlots.size() < (guiSize > 9 ? guiSize - 9 : guiSize); i++) {
                if (i % 9 != 0 && (i + 1) % 9 != 0) defaultSlots.add(i);
            }
        }
        return defaultSlots;
    }

    private List<Integer> parseSlots(List<String> slotStrings) {
        List<Integer> slots = new ArrayList<>();
        if (slotStrings == null) return slots;
        for (String s : slotStrings) {
            if (s == null || s.trim().isEmpty()) continue;
            s = s.trim();
            if (s.contains("-")) {
                String[] parts = s.split("-");
                if (parts.length == 2) {
                    try {
                        int start = Integer.parseInt(parts[0].trim());
                        int end = Integer.parseInt(parts[1].trim());
                        for (int i = start; i <= end; i++) slots.add(i);
                    } catch (NumberFormatException e) {
                        plugin.getLogger().warning("Format de slot (range) invalide: '" + s + "'");
                    }
                }
            } else {
                try {
                    slots.add(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Format de slot (single) invalide: '" + s + "'");
                }
            }
        }
        return slots.stream().distinct().collect(Collectors.toList());
    }

    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.isEmpty() || "permanent".equalsIgnoreCase(durationStr) || "perm".equalsIgnoreCase(durationStr)) {
            return Long.MAX_VALUE / 2;
        }
        long totalMillis = 0;
        Pattern pattern = Pattern.compile("(\\d+)([wdhms])");
        Matcher matcher = pattern.matcher(durationStr.toLowerCase());
        boolean foundAny = false;
        while (matcher.find()) {
            foundAny = true;
            long val = Long.parseLong(matcher.group(1));
            switch (matcher.group(2)) {
                case "w":
                    totalMillis += TimeUnit.DAYS.toMillis(val * 7);
                    break;
                case "d":
                    totalMillis += TimeUnit.DAYS.toMillis(val);
                    break;
                case "h":
                    totalMillis += TimeUnit.HOURS.toMillis(val);
                    break;
                case "m":
                    totalMillis += TimeUnit.MINUTES.toMillis(val);
                    break;
                case "s":
                    totalMillis += TimeUnit.SECONDS.toMillis(val);
                    break;
            }
        }
        if (!foundAny) {
            SHSSBWarpsGUI.getInstance().getLogger().warning("Impossible de parser la durée: '" + durationStr + "'.");
        }
        return totalMillis;
    }

    public static String formatDuration(long millis, String formatTemplate) {
        if (millis >= Long.MAX_VALUE / 2) return "Permanent";
        if (millis <= 0) return "Expiré";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        String result = formatTemplate;
        result = result.replace("%d%", String.valueOf(days))
            .replace("%h%", String.valueOf(hours))
            .replace("%m%", String.valueOf(minutes))
            .replace("%s%", String.valueOf(seconds));

        if (formatTemplate.contains("%j")) result = result.replace("%j", days == 1 ? "jour" : "jours");

        return result.trim();
    }
}
