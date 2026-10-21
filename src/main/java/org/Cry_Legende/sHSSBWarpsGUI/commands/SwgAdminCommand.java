package org.Cry_Legende.sHSSBWarpsGUI.commands;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;

public class SwgAdminCommand implements CommandExecutor, TabCompleter {

    private final SHSSBWarpsGUI plugin;

    public SwgAdminCommand(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().loadConfigs();
            plugin.getTextureService().loadCaches();
            plugin.getFeaturedWarpsManager().loadFeaturedWarps();
            ChatUtils.sendMessage(sender, "&a[SHSSBWarpsGUI] Configuration rechargée avec succès !");
            return true;
        }

        ChatUtils.sendMessage(sender, "&cUsage: /" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            if ("reload".startsWith(args[0].toLowerCase())) {
                return Collections.singletonList("reload");
            }
        }
        return Collections.emptyList();
    }
}