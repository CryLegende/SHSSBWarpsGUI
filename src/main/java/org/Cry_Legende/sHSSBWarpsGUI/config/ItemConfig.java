package org.Cry_Legende.sHSSBWarpsGUI.config;

import java.util.List;
import java.util.Map;

public class ItemConfig {
    public final String materialString;
    public String nameTemplate;
    public List<String> loreTemplate;
    public final String action;
    public final String command;
    public final int customModelData;
    public final boolean glow;
    public final Map<String, String> nbt;
    public final SoundConfig clickSound;

    public ItemConfig(String materialString, String nameTemplate, List<String> loreTemplate, String action, String command,
                      int customModelData, boolean glow, Map<String, String> nbt, SoundConfig clickSound) {
        this.materialString = (materialString == null || materialString.isEmpty()) ? "STONE" : materialString;
        this.nameTemplate = nameTemplate;
        this.loreTemplate = loreTemplate;
        this.action = (action == null || action.isEmpty()) ? "NONE" : action.toUpperCase();
        this.command = command;
        this.customModelData = customModelData;
        this.glow = glow;
        this.nbt = nbt;
        this.clickSound = clickSound;
    }

    public ItemConfig generateDisplayInstance(String dynamicName, List<String> dynamicLore) {
        return new ItemConfig(
            this.materialString,
            dynamicName,
            dynamicLore,
            this.action,
            this.command,
            this.customModelData,
            this.glow,
            this.nbt,
            this.clickSound
        );
    }
}
