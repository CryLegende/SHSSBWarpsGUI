package org.Cry_Legende.sHSSBWarpsGUI.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/**
 * Textes de messages.yml. Chaque section garde le chemin qu'elle avait dans config.yml ; une clé absente du fichier
 * prend le texte du jar.
 */
public final class Messages {

    /** Sections de textes de l'ancien config.yml, reprises au même chemin dans messages.yml. */
    private static final List<String> LEGACY_PATHS = List.of(
        "messages",
        "manage-warp-messages",
        "favorites.messages",
        "featured-warps.messages",
        "feature-confirmation-gui-texts",
        "dialogues.boutons",
        "dialogues.renommer.titre", "dialogues.renommer.corps", "dialogues.renommer.champ",
        "dialogues.description.titre", "dialogues.description.corps", "dialogues.description.champ",
        "dialogues.suppression",
        "dialogues.erreurs",
        "dialogues.messages");

    /** Texte de l'ancien config.yml qui n'était lu nulle part : il n'est pas repris. */
    private static final Set<String> NOT_MIGRATED = Set.of("manage-warp-messages.delete-confirm-instruction");

    private Messages() {
    }

    /**
     * Crée messages.yml s'il n'existe pas : le fichier du jar, dont les textes sont remplacés par ceux des anciennes
     * sections de config.yml quand elles existent.
     */
    public static void prepare(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (file.exists()) {
            return;
        }
        plugin.saveResource("messages.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (migrate(plugin.getConfig(), yaml)) {
            try {
                yaml.save(file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Écriture de messages.yml impossible : " + e.getMessage());
            }
        }
    }

    /** Copie dans {@code target} les textes de l'ancien config.yml ; vrai si quelque chose a été repris. */
    static boolean migrate(ConfigurationSection legacy, YamlConfiguration target) {
        boolean changed = false;
        for (String path : LEGACY_PATHS) {
            if (legacy.isConfigurationSection(path)) {
                ConfigurationSection section = legacy.getConfigurationSection(path);
                for (String key : section.getKeys(true)) {
                    String full = path + "." + key;
                    if (!section.isConfigurationSection(key) && !NOT_MIGRATED.contains(full)) {
                        target.set(full, section.get(key));
                        changed = true;
                    }
                }
            } else if (legacy.contains(path, true)) {
                target.set(path, legacy.get(path));
                changed = true;
            }
        }
        return changed;
    }

    /** Lit messages.yml ; lève l'erreur YAML telle quelle pour que l'appelant garde l'ancien état. */
    public static YamlConfiguration load(JavaPlugin plugin) throws IOException, InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.load(new File(plugin.getDataFolder(), "messages.yml"));
        yaml.setDefaults(jarDefaults(plugin));
        return yaml;
    }

    /** Textes du jar seuls, quand messages.yml est illisible au démarrage. */
    public static YamlConfiguration defaults(JavaPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.setDefaults(jarDefaults(plugin));
        return yaml;
    }

    private static YamlConfiguration jarDefaults(JavaPlugin plugin) {
        try (InputStream in = plugin.getResource("messages.yml")) {
            return in == null ? new YamlConfiguration()
                    : YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return new YamlConfiguration();
        }
    }
}
