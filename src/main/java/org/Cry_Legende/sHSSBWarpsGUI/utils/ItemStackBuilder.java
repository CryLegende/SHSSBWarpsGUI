package org.Cry_Legende.sHSSBWarpsGUI.utils;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.ArrayList; // Pour lore vide
import java.util.Collections; // Pour lore vide
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class ItemStackBuilder {
    public ItemStack itemStack;
    private ItemMeta itemMeta;

    public ItemStackBuilder(Material material) {
        this.itemStack = new ItemStack(material != null ? material : Material.STONE); // Fallback si material est null
        this.itemMeta = this.itemStack.getItemMeta();
        if (this.itemMeta == null && material != Material.AIR) { // Air n'a pas de meta, mais les autres devraient
            // Cela peut arriver si le serveur a des soucis ou si le material est très inhabituel
            SHSSBWarpsGUI.getInstance().getLogger().warning("ItemMeta est null pour le material: " + material.name() + ". L'item pourrait ne pas être correctement construit.");
            // Créer une meta par défaut pour éviter les NPEs plus tard, bien que limitée
            this.itemMeta = SHSSBWarpsGUI.getInstance().getServer().getItemFactory().getItemMeta(material);
        }
    }

    public ItemStackBuilder(ItemStack itemStack) {
        if (itemStack == null) {
            SHSSBWarpsGUI.getInstance().getLogger().warning("ItemStackBuilder a reçu un ItemStack null. Utilisation de STONE par défaut.");
            this.itemStack = new ItemStack(Material.STONE);
        } else {
            this.itemStack = itemStack.clone();
        }
        this.itemMeta = this.itemStack.getItemMeta();
        if (this.itemMeta == null && this.itemStack.getType() != Material.AIR) {
            SHSSBWarpsGUI.getInstance().getLogger().warning("ItemMeta est null pour l'ItemStack cloné: " + this.itemStack.getType().name());
            this.itemMeta = SHSSBWarpsGUI.getInstance().getServer().getItemFactory().getItemMeta(this.itemStack.getType());
        }
    }

    public static ItemStackBuilder fromMaterial(String materialString) {
        Material material = Material.getMaterial(materialString.toUpperCase());
        if (material == null) {
            SHSSBWarpsGUI.getInstance().getLogger().warning("Material invalide dans ItemStackBuilder: '" + materialString + "'. Utilisation de STONE par défaut.");
            material = Material.STONE;
        }
        return new ItemStackBuilder(material);
    }

    public ItemStackBuilder setName(String name) {
        if (itemMeta != null && name != null) { // Vérifier si itemMeta n'est pas null
            itemMeta.setDisplayName(ChatUtils.color(name));
        }
        return this;
    }

    public ItemStackBuilder setLore(List<String> lore) {
        if (itemMeta != null && lore != null) { // Vérifier si itemMeta n'est pas null
            itemMeta.setLore(ChatUtils.color(lore)); // ChatUtils.color gère déjà la liste
        } else if (itemMeta != null && lore == null) {
            itemMeta.setLore(Collections.emptyList()); // Mettre un lore vide si null est passé
        }
        return this;
    }

    // Pas besoin de setLore avec OfflinePlayer ici, car les placeholders sont appliqués avant d'appeler setLore

    public ItemStackBuilder setAmount(int amount) {
        itemStack.setAmount(Math.max(1, amount)); // S'assurer que le montant est au moins 1
        return this;
    }

    public ItemStackBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        if (itemMeta != null && enchantment != null) {
            itemMeta.addEnchant(enchantment, level, ignoreLevelRestriction);
        }
        return this;
    }

    public ItemStackBuilder setGlow(boolean glow) {
        if (itemMeta == null) return this;
        if (glow) {
            // Utiliser un enchantement moins commun pour éviter les conflits, mais Durability est standard.
            Enchantment glowEnchant = Enchantment.LUCK; // Ou un autre qui n'a pas d'effet visible par défaut
            try { // Essayer LUCK, fallback sur DURABILITY si LUCK n'est pas disponible (versions plus anciennes)
                glowEnchant = Enchantment.LUCK; // Spigot utilise ce nom pour Luck
            } catch (IllegalArgumentException e) {
                glowEnchant = Enchantment.DURABILITY;
            }
            itemMeta.addEnchant(glowEnchant, 1, true);
            itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            // Retirer tous les enchants si le seul but était le glow et qu'il n'y en a qu'un.
            // C'est délicat car on ne sait pas si d'autres enchants étaient intentionnels.
            // Une approche plus sûre est de ne retirer que l'enchantement de glow spécifique si on le connaît.
            // Pour la simplicité, si on désactive le glow, on retire tous les enchants cachés s'il n'y a qu'un enchant.
            boolean hadGlowEnchant = false;
            for (Enchantment ench : itemMeta.getEnchants().keySet()) { // Vérifier tous les enchants
                if (itemMeta.hasItemFlag(ItemFlag.HIDE_ENCHANTS)) { // S'ils étaient cachés, on les enlève
                    itemMeta.removeEnchant(ench);
                    hadGlowEnchant = true;
                }
            }
            if (hadGlowEnchant) itemMeta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public ItemStackBuilder setSkullOwner(OfflinePlayer owner) {
        if (itemMeta instanceof SkullMeta && owner != null) {
            SkullMeta skullMeta = (SkullMeta) itemMeta;
            skullMeta.setOwningPlayer(owner);
        }
        return this;
    }

    // Cette méthode pour appliquer une texture Base64 est surtout utile pour TextureService.
    // L'ItemStackBuilder standard devrait plutôt utiliser setSkullOwner ou setPlayerProfile.
    public ItemStackBuilder setSkullTexture(String base64Texture) {
        if (itemMeta instanceof SkullMeta && base64Texture != null && !base64Texture.isEmpty()) {
            SkullMeta skullMeta = (SkullMeta) itemMeta;
            GameProfile profile = new GameProfile(UUID.randomUUID(), "SHSSBWarpsGUIProfile"); // Nom non-null
            profile.getProperties().put("textures", new Property("textures", base64Texture));
            try {
                Field profileField = skullMeta.getClass().getDeclaredField("profile");
                profileField.setAccessible(true);
                profileField.set(skullMeta, profile);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                SHSSBWarpsGUI.getInstance().getLogger().severe("Erreur lors de l'application de la texture de tête (reflexion) dans ItemStackBuilder: " + e.getMessage());
            }
        }
        return this;
    }

    public ItemStackBuilder addItemFlags(ItemFlag... flags) {
        if (itemMeta != null && flags != null) {
            itemMeta.addItemFlags(flags);
        }
        return this;
    }

    public ItemStack build() {
        if (itemMeta != null) { // Appliquer la meta seulement si elle existe
            itemStack.setItemMeta(itemMeta);
        }
        return itemStack;
    }
}
