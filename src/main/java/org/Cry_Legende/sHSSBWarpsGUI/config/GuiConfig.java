package org.Cry_Legende.sHSSBWarpsGUI.config;

import java.util.List;
// import java.util.Map; // Map n'est plus utilisé pour staticItems

public class GuiConfig {
    public final String title;
    public final int size;
    public final SoundConfig defaultClickSound;
    public final List<ConfiguredItem> staticItems; // Remplacé Map<Integer, ItemConfig> par List<ConfiguredItem>

    public final ItemConfig dynamicItemTemplate; // Pour les listes paginées (têtes, warps)
    public final ItemConfig featuredDynamicItemTemplate; // Pour les items dynamiques mis en avant
    public final List<Integer> dynamicItemSlots; // Slots pour les items dynamiques

    public GuiConfig(String title, int size, SoundConfig defaultClickSound,
                     List<ConfiguredItem> staticItems, ItemConfig dynamicItemTemplate,
                     ItemConfig featuredDynamicItemTemplate, List<Integer> dynamicItemSlots) {
        this.title = title;
        this.size = size;
        this.defaultClickSound = defaultClickSound;
        this.staticItems = staticItems;
        this.dynamicItemTemplate = dynamicItemTemplate;
        this.featuredDynamicItemTemplate = featuredDynamicItemTemplate;
        this.dynamicItemSlots = dynamicItemSlots;
    }
}