package org.Cry_Legende.sHSSBWarpsGUI.hooks;

import org.Cry_Legende.sHSSBWarpsGUI.utils.PlayerNames;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Skin des têtes de joueurs. Le serveur est en mode hors ligne : ni l'UUID ni le pseudo ne permettent de retrouver le
 * skin chez Mojang, il vient donc de SHSkins, qui le garde en mémoire sur chaque serveur (réponse immédiate, aucune
 * requête réseau). Sans SHSkins, la tête reçoit un profil sans textures et garde le skin par défaut.
 */
public final class SkinHeads {

    private SkinHeads() {
    }

    /**
     * Pose le skin du joueur sur une tête existante ; le nom, la description et le reste de l'objet ne changent pas.
     */
    public static void apply(ItemStack head, OfflinePlayer player) {
        head.editMeta(SkullMeta.class, meta -> apply(meta, player));
    }

    public static void apply(SkullMeta meta, OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        String name = PlayerNames.of(player);
        if (Bukkit.getPluginManager().isPluginEnabled("SHSkins")) {
            SHSkinsHook.applyHead(meta, uuid, name);
        } else {
            meta.setPlayerProfile(Bukkit.createProfile(uuid, name));
        }
    }
}
