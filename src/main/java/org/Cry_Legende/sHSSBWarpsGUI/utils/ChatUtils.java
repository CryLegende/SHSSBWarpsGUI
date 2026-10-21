package org.Cry_Legende.sHSSBWarpsGUI.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Collectors;

public class ChatUtils {

    public static String color(String message) {
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public static List<String> color(List<String> messages) {
        return messages.stream().map(ChatUtils::color).collect(Collectors.toList());
    }

    public static String setPlaceholders(OfflinePlayer player, String text) {
        if (text == null) return null;
        if (SHSSBWarpsGUI.getInstance().getPapiHook() != null && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }

    public static List<String> setPlaceholders(OfflinePlayer player, List<String> texts) {
        if (texts == null) return null;
        return texts.stream().map(line -> setPlaceholders(player, line)).collect(Collectors.toList());
    }

    public static void sendMessage(CommandSender sender, String message) {
        if (message == null || message.isEmpty()) return;
        sender.sendMessage(color(setPlaceholders(sender instanceof Player ? (Player) sender : null, message)));
    }
}