package org.Cry_Legende.sHSSBWarpsGUI.dialog;

import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.island.warps.IslandWarp;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.Cry_Legende.sHSSBWarpsGUI.utils.WarpRules;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Les trois saisies de la gestion d'un warp par dialogue natif (clients 1.21.6 et plus) : nom, description et
 * confirmation de suppression. Chaque méthode renvoie false quand le dialogue n'est pas utilisable : l'appelant garde
 * alors le chemin par le chat (ou le menu de confirmation), qui reste exactement celui d'avant.
 * <p>
 * Au clic, tout est revérifié comme dans le chat : l'île et le warp existent toujours, le joueur peut les modifier,
 * puis le nom ou la description passent par les mêmes règles ({@link WarpRules}).
 */
public final class WarpDialogs {

    private final SHSSBWarpsGUI plugin;
    private final Dialogs engine;

    public WarpDialogs(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
        this.engine = new Dialogs(plugin);
        Bukkit.getPluginManager().registerEvents(engine, plugin);
    }

    public boolean supported(Player player) {
        return engine.supported(player);
    }

    /**
     * Abandonne, sans rien dire, le dialogue attendu de ce joueur (il a fait autre chose entre-temps).
     */
    public void cancel(Player player) {
        engine.cancel(player);
    }

    public void clear() {
        engine.clear();
    }

    // ------------------------------------------------------------------ textes

    private String text(String path, String... replacements) {
        return replace(plugin.getConfig().getString("dialogues." + path, ""), replacements);
    }

    private List<String> lines(String path, String... replacements) {
        List<String> out = new ArrayList<>();
        for (String line : plugin.getConfig().getStringList("dialogues." + path)) {
            out.add(replace(line, replacements));
        }
        return out;
    }

    private static String replace(String text, String... replacements) {
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            text = text.replace(replacements[i], replacements[i + 1]);
        }
        return text;
    }

    // ------------------------------------------------------------------ renommer

    /**
     * @param fallback saisie dans le chat, si l'affichage échoue au dernier moment
     */
    public boolean askRename(Player player, IslandWarp warp, Runnable fallback) {
        return rename(player, warp, null, null, fallback);
    }

    private boolean rename(Player player, IslandWarp warp, String typed, String error, Runnable fallback) {
        String[] vars = {"%warp_name%", warp.getName(), "%max_length%", String.valueOf(WarpRules.maxNameLength(plugin))};
        int rawMax = Math.max(1, plugin.getConfig().getInt("dialogues.renommer.longueur-max", 256));
        Dialogs.Screen screen = new Dialogs.Screen(text("renommer.titre", vars), lines("renommer.corps", vars), error,
            new Dialogs.Field(text("renommer.champ", vars), typed != null ? typed : warp.getName(), rawMax, 0, 0),
            text("boutons.valider"), text("boutons.annuler"));
        return engine.show(player, "renommer", screen, (p, confirmed, value) -> onRename(p, warp, confirmed, value, fallback), fallback);
    }

    private void onRename(Player player, IslandWarp warp, boolean confirmed, String value, Runnable fallback) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        if (!confirmed) {
            ChatUtils.sendMessage(player, cfgMgr.msgActionCancelled);
            return;
        }
        IslandWarp current = WarpRules.resolveForEdit(plugin, player, warp);
        if (current == null) {
            return;
        }
        Island island = current.getIsland();
        String input = value == null ? "" : value.strip();
        int rawMax = Math.max(1, plugin.getConfig().getInt("dialogues.renommer.longueur-max", 256));
        String maxLength = String.valueOf(WarpRules.maxNameLength(plugin));
        // un client modifié peut dépasser la limite du champ ou glisser des caractères de contrôle : refusés comme un nom invalide
        if (input.length() > rawMax || input.chars().anyMatch(c -> c < 32 || c == 127)) {
            rename(player, current, value, text("erreurs.nom-invalide", "%max_length%", maxLength), fallback);
            return;
        }
        WarpRules.RenameCheck check = WarpRules.checkRename(plugin, island, current, input);
        if (check == WarpRules.RenameCheck.INVALID) {
            rename(player, current, value, text("erreurs.nom-invalide", "%max_length%", maxLength), fallback);
            return;
        }
        if (check == WarpRules.RenameCheck.EXISTS) {
            rename(player, current, value, text("erreurs.nom-existe", "%new_name%", input), fallback);
            return;
        }
        String oldName = current.getName();
        island.renameWarp(current, input);
        ChatUtils.sendMessage(player, cfgMgr.msgWarpRenamed
            .replace("%old_warp_name%", oldName)
            .replace("%new_warp_name%", input));
    }

    // ------------------------------------------------------------------ description

    /**
     * @param fallback saisie dans le chat, si l'affichage échoue au dernier moment
     */
    public boolean askDescription(Player player, IslandWarp warp, Runnable fallback) {
        return description(player, warp, null, null, fallback);
    }

    private int lineMax() {
        return Math.max(1, plugin.getConfig().getInt("dialogues.description.longueur-ligne-max", 256));
    }

    private boolean description(Player player, IslandWarp warp, String typed, String error, Runnable fallback) {
        int maxLines = Math.max(1, WarpRules.maxLoreLines(plugin));
        String[] vars = {"%warp_name%", warp.getName(), "%max_lines%", String.valueOf(maxLines)};
        String initial = typed != null ? typed
            : plugin.getConfig().getBoolean("dialogues.description.pre-remplir", true) ? currentDescription(warp, maxLines) : "";
        Dialogs.Screen screen = new Dialogs.Screen(text("description.titre", vars), lines("description.corps", vars), error,
            new Dialogs.Field(text("description.champ", vars), initial, lineMax() * maxLines + maxLines - 1, maxLines,
                plugin.getConfig().getInt("dialogues.description.hauteur", 100)),
            text("boutons.valider"), text("boutons.annuler"));
        return engine.show(player, "description", screen, (p, confirmed, value) -> onDescription(p, warp, confirmed, value, fallback), fallback);
    }

    /**
     * Description actuelle (lore de l'icône) en codes &amp;, une ligne par ligne, pour la modifier plutôt que la retaper.
     */
    private String currentDescription(IslandWarp warp, int maxLines) {
        ItemStack icon = warp.getRawIcon();
        ItemMeta meta = icon == null ? null : icon.getItemMeta();
        List<String> lore = meta == null ? null : meta.getLore();
        if (lore == null || lore.isEmpty()) {
            return "";
        }
        return String.join("\n", lore.subList(0, Math.min(lore.size(), maxLines))).replace('§', '&');
    }

    private void onDescription(Player player, IslandWarp warp, boolean confirmed, String value, Runnable fallback) {
        ConfigManager cfgMgr = plugin.getConfigManager();
        if (!confirmed) {
            ChatUtils.sendMessage(player, cfgMgr.msgActionCancelled);
            return;
        }
        IslandWarp current = WarpRules.resolveForEdit(plugin, player, warp);
        if (current == null) {
            return;
        }
        String input = value == null ? "" : value;
        int maxLines = Math.max(1, WarpRules.maxLoreLines(plugin));
        // même plafond que le chat : au plus une ligne de chat par ligne de description
        if (input.length() > lineMax() * maxLines + maxLines - 1) {
            description(player, current, value, text("erreurs.description-trop-longue"), fallback);
            return;
        }
        List<String> descriptionLines = WarpRules.toLoreLines(input);
        if (descriptionLines.size() > maxLines) {
            description(player, current, value, text("erreurs.description-lignes", "%max_lines%", String.valueOf(maxLines)), fallback);
            return;
        }
        WarpRules.applyDescription(plugin, player, current, descriptionLines);
    }

    // ------------------------------------------------------------------ suppression

    /**
     * @param onCancel  Non, Annuler ou Échap : retour au menu de gestion
     * @param onConfirm Oui : suppression (qui revérifie le warp et les droits)
     * @param fallback  menu de confirmation classique, si l'affichage échoue au dernier moment
     */
    public boolean askDelete(Player player, IslandWarp warp, Runnable onCancel, Consumer<Player> onConfirm, Runnable fallback) {
        String[] vars = {"%warp_name%", warp.getName()};
        Dialogs.Screen screen = new Dialogs.Screen(text("suppression.titre", vars), lines("suppression.corps", vars), null, null,
            text("suppression.oui"), text("suppression.non"));
        return engine.show(player, "suppression", screen, (p, confirmed, value) -> {
            if (confirmed) {
                onConfirm.accept(p);
            } else {
                onCancel.run();
            }
        }, fallback);
    }
}
