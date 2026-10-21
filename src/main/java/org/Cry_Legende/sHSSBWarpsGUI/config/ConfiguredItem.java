package org.Cry_Legende.sHSSBWarpsGUI.config;

import java.util.List;

// Représente un item configuré avec ses slots assignés
public class ConfiguredItem {
    public final ItemConfig itemConfig; // La configuration de l'item (material, name, action, etc.)
    public final List<Integer> slots;   // Les slots où cet item doit être placé

    public ConfiguredItem(ItemConfig itemConfig, List<Integer> slots) {
        this.itemConfig = itemConfig;
        this.slots = slots;
    }
}