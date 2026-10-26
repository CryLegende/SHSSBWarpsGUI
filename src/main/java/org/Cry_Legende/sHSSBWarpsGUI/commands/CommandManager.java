package org.Cry_Legende.sHSSBWarpsGUI.commands;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.TabCompleteEvent;

import java.util.Arrays;
import java.util.List;

public class CommandManager implements Listener {

    private final IslandCommand islandCommand;

    public CommandManager(SHSSBWarpsGUI plugin) {
        this.islandCommand = new IslandCommand(plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // Sortie rapide : cet événement passe pour chaque commande de chaque joueur
        String message = event.getMessage();
        if (!message.regionMatches(true, 1, "is ", 0, 3) && !message.regionMatches(true, 1, "island ", 0, 7)) {
            return;
        }

        Player player = event.getPlayer();
        String command = message.substring(1);
        String[] parts = command.split(" ");
        String label = parts[0].toLowerCase();

        if (!label.equals("is") && !label.equals("island")) {
            return;
        }

        if (parts.length < 2) return;

        String subCommand = parts[1].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 2, parts.length);

        switch (subCommand) {
            case "warps":
                event.setCancelled(true);
                islandCommand.handleWarpsCommand(player, args);
                break;
            case "warp":
                event.setCancelled(true);
                islandCommand.handleWarpCommand(player, args);
                break;
            case "editwarp":
                event.setCancelled(true);
                islandCommand.handleEditWarpCommand(player, args);
                break;
        }
    }

    @EventHandler
    public void onTabComplete(TabCompleteEvent event) {
        if (!(event.getSender() instanceof Player) || event.isCancelled()) return;

        String buffer = event.getBuffer();
        if (!buffer.startsWith("/")) return;

        String[] parts = buffer.substring(1).split(" ", -1);
        String label = parts[0].toLowerCase();

        if (!label.equals("is") && !label.equals("island")) {
            return;
        }

        // On n'intervient que si une sous-commande a été tapée, pour ne pas casser la complétion des sous-commandes de SSB2.
        if (parts.length > 2) {
            Player player = (Player) event.getSender();
            String subCommand = parts[1].toLowerCase();
            String[] subArgs = Arrays.copyOfRange(parts, 2, parts.length);
            List<String> completions = null;

            // On fournit la complétion SEULEMENT pour nos sous-commandes
            switch (subCommand) {
                case "warps":
                    completions = islandCommand.getWarpsTabComplete(player, subArgs);
                    break;
                case "warp":
                    completions = islandCommand.getWarpTabComplete(player, subArgs);
                    break;
                case "editwarp":
                    completions = islandCommand.getEditWarpTabComplete(player, subArgs);
                    break;
            }

            // Si on a trouvé des complétions pour nos commandes, on les applique.
            // Sinon, on ne touche à rien pour laisser SSB2 faire.
            if (completions != null) {
                event.setCompletions(completions);
            }
        }
    }
}