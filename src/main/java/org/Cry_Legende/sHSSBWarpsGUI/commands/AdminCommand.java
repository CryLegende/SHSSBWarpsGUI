package org.Cry_Legende.sHSSBWarpsGUI.commands;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.managers.FeaturedWarpsManager.FeaturedWarpEntry;
import org.Cry_Legende.sHSSBWarpsGUI.network.NetworkWarps;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.Cry_Legende.sHSSBWarpsGUI.utils.PlayerNames;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** /shssbwarpsgui (anciens noms : /swgadmin, /shssbwarps) : reload, import et featured. */
public class AdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBS = List.of("reload", "import", "featured");

    private final SHSSBWarpsGUI plugin;

    public AdminCommand(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    private static boolean can(CommandSender sender, String sub) {
        return sender.hasPermission("shssbwarpsgui.admin." + sub);
    }

    /** Sous-commandes que l'expéditeur peut utiliser ; import n'est listée qu'avec le mode réseau. */
    private List<String> allowed(CommandSender sender) {
        List<String> allowed = new ArrayList<>();
        for (String sub : SUBS) {
            if (sub.equals("import") && plugin.getNetwork() == null) continue;
            if (can(sender, sub)) allowed.add(sub);
        }
        return allowed;
    }

    private String text(String key) {
        String value = plugin.getTexts().getString("messages." + key);
        return value == null ? "" : value;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> allowed = allowed(sender);
        // import reste accepté sans le mode réseau pour afficher son message d'inactivité
        boolean importInactive = sub.equals("import") && can(sender, "import") && plugin.getNetwork() == null;
        if (!allowed.contains(sub) && !importInactive) {
            if (allowed.isEmpty()) {
                ChatUtils.sendMessage(sender, plugin.getConfigManager().msgNoPermissionCommand);
            } else {
                ChatUtils.sendMessage(sender, text("usage").replace("{commands}", String.join("|", allowed)));
            }
            return true;
        }
        switch (sub) {
            case "reload" -> reload(sender);
            case "import" -> importFiles(sender);
            default -> featured(sender);
        }
        return true;
    }

    private void reload(CommandSender sender) {
        try {
            plugin.reload();
            ChatUtils.sendMessage(sender, text("reloaded"));
        } catch (SHSSBWarpsGUI.ReloadException e) {
            plugin.getLogger().warning(e.getMessage() + " (l'ancienne configuration reste active)");
            ChatUtils.sendMessage(sender, text("reload-failed").replace("{file}", e.file()).replace("{error}", e.detail()));
        }
    }

    private void importFiles(CommandSender sender) {
        NetworkWarps network = plugin.getNetwork();
        if (network == null) {
            ChatUtils.sendMessage(sender, plugin.getConfig().getString("reseau.messages.import-inactif", ""));
        } else {
            network.importFiles(sender);
        }
    }

    /** Warps actuellement mis en avant : propriétaire, nom du warp et échéance. */
    private void featured(CommandSender sender) {
        List<FeaturedWarpEntry> entries = new ArrayList<>(plugin.getFeaturedWarpsManager().getFeaturedWarps());
        if (entries.isEmpty()) {
            ChatUtils.sendMessage(sender, text("featured-empty"));
            return;
        }
        entries.sort(Comparator.comparingLong((FeaturedWarpEntry entry) -> entry.expiryTimestamp));
        ChatUtils.sendMessage(sender, text("featured-header").replace("{count}", String.valueOf(entries.size())));
        SimpleDateFormat format = dateFormat();
        for (FeaturedWarpEntry entry : entries) {
            String owner = PlayerNames.of(Bukkit.getOfflinePlayer(entry.ownerUUID), entry.ownerUUID.toString());
            String expiry = entry.expiryTimestamp >= Long.MAX_VALUE / 2
                ? text("featured-permanent")
                : format.format(new Date(entry.expiryTimestamp));
            ChatUtils.sendMessage(sender, text("featured-line")
                .replace("{warp}", entry.warpName)
                .replace("{owner}", owner)
                .replace("{expiry}", expiry));
        }
    }

    private SimpleDateFormat dateFormat() {
        try {
            return new SimpleDateFormat(plugin.getConfigManager().featuredDateTimeFormat);
        } catch (IllegalArgumentException | NullPointerException e) {
            return new SimpleDateFormat("dd/MM/yyyy HH:mm");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], allowed(sender), completions);
        }
        return completions;
    }
}
