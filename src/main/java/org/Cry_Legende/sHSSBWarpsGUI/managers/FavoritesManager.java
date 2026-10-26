package org.Cry_Legende.sHSSBWarpsGUI.managers;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class FavoritesManager {

    private final SHSSBWarpsGUI plugin;
    private final File file;
    private FileConfiguration config;
    private final Map<UUID, List<UUID>> favoritesCache = new HashMap<>();

    public FavoritesManager(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "favorites.yml");
        loadFavorites();
    }

    public void loadFavorites() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer favorites.yml: " + e.getMessage());
            }
        }
        config = YamlConfiguration.loadConfiguration(file);
        favoritesCache.clear();

        for (String key : config.getKeys(false)) {
            try {
                UUID viewerUUID = UUID.fromString(key);
                List<String> rawList = config.getStringList(key);
                List<UUID> targets = new ArrayList<>();
                for (String s : rawList) targets.add(UUID.fromString(s));
                favoritesCache.put(viewerUUID, targets);
            } catch (Exception e) {
                plugin.getLogger().warning("Erreur chargement favoris pour: " + key);
            }
        }
    }

    public void saveFavorites() {
        for (String key : config.getKeys(false)) config.set(key, null); // Clear old
        favoritesCache.forEach((viewer, targets) -> {
            List<String> sList = new ArrayList<>();
            targets.forEach(t -> sList.add(t.toString()));
            config.set(viewer.toString(), sList);
        });
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder favorites.yml: " + e.getMessage());
        }
    }

    public boolean isFavorite(UUID viewer, UUID target) {
        return favoritesCache.containsKey(viewer) && favoritesCache.get(viewer).contains(target);
    }

    public void toggleFavorite(UUID viewer, UUID target) {
        List<UUID> list = favoritesCache.computeIfAbsent(viewer, k -> new ArrayList<>());
        if (list.contains(target)) {
            list.remove(target);
        } else {
            // Vérification limite (5 par défaut)
            int max = plugin.getConfig().getInt("favorites.max-count", 5);
            if (list.size() >= max) {
                return; // Géré côté GUIListener pour le message d'erreur
            }
            list.add(target);
        }
        saveFavorites();
    }

    public boolean canAddFavorite(UUID viewer) {
        int max = plugin.getConfig().getInt("favorites.max-count", 5);
        return favoritesCache.getOrDefault(viewer, List.of()).size() < max;
    }

    public List<UUID> getFavorites(UUID viewer) {
        return favoritesCache.getOrDefault(viewer, new ArrayList<>());
    }
}
