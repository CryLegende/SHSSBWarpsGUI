package org.Cry_Legende.sHSSBWarpsGUI.hooks;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class PAPIHook {

    private final boolean papiEnabled;

    public PAPIHook() {
        this.papiEnabled = SHSSBWarpsGUI.getInstance().getServer().getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    public String setPlaceholders(OfflinePlayer player, String text) {
        if (papiEnabled && text != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public List<String> setPlaceholders(OfflinePlayer player, List<String> texts) {
        if (papiEnabled && texts != null) {
            return texts.stream().map(line -> setPlaceholders(player, line)).collect(Collectors.toList());
        }
        return texts;
    }

    public boolean isEnabled() {
        return papiEnabled;
    }
}