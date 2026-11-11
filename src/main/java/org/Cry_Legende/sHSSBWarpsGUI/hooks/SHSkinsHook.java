package org.Cry_Legende.sHSSBWarpsGUI.hooks;

import org.Cry_Legende.sHSkins.api.SHSkinsAPI;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

/**
 * Seule classe qui touche à l'API de SHSkins : sans lui elle n'est jamais chargée.
 */
final class SHSkinsHook {

    private SHSkinsHook() {
    }

    /**
     * Pose sur la tête le skin que SHSkins connaît pour ce joueur ; sans skin connu, la tête garde le skin par défaut.
     */
    static void applyHead(SkullMeta meta, UUID uuid, String name) {
        SHSkinsAPI.applyHead(meta, uuid, name);
    }
}
