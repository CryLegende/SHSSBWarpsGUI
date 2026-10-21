package org.Cry_Legende.sHSSBWarpsGUI.managers;

import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.Cry_Legende.sHSSBWarpsGUI.config.ConfigManager;
import org.Cry_Legende.sHSSBWarpsGUI.utils.ChatUtils;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask; // Ajout de l'import BukkitTask

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class FeaturedWarpsManager {
    private final SHSSBWarpsGUI plugin;
    private final File featuredWarpsFile;
    private FileConfiguration featuredWarpsConfig;
    private final Set<FeaturedWarpEntry> featuredWarps = new HashSet<>();
    private BukkitTask expiryCheckTask; // Utiliser BukkitTask pour pouvoir l'annuler

    public static class FeaturedWarpEntry {
        public final UUID ownerUUID;
        public final String warpName;
        public long expiryTimestamp;

        public FeaturedWarpEntry(UUID ownerUUID, String warpName, long expiryTimestamp) {
            this.ownerUUID = ownerUUID;
            this.warpName = warpName;
            this.expiryTimestamp = expiryTimestamp;
        }

        public boolean isExpired() {
            // Si expiryTimestamp est Long.MAX_VALUE / 2 (permanent), ne jamais expirer
            if (this.expiryTimestamp == Long.MAX_VALUE / 2) return false;
            return System.currentTimeMillis() > expiryTimestamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FeaturedWarpEntry that = (FeaturedWarpEntry) o;
            return ownerUUID.equals(that.ownerUUID) && warpName.equalsIgnoreCase(that.warpName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerUUID, warpName.toLowerCase());
        }

        public String toSaveString() {
            return ownerUUID.toString() + ":" + warpName + ":" + expiryTimestamp;
        }

        public static FeaturedWarpEntry fromSaveString(String s) {
            String[] parts = s.split(":", 3);
            if (parts.length == 3) {
                try {
                    return new FeaturedWarpEntry(UUID.fromString(parts[0]), parts[1], Long.parseLong(parts[2]));
                } catch (IllegalArgumentException e) {
                    SHSSBWarpsGUI.getInstance().getLogger().warning("Erreur de format pour l'entrée de warp mis en avant: " + s + " - " + e.getMessage());
                }
            }
            return null;
        }
    }

    public FeaturedWarpsManager(SHSSBWarpsGUI plugin, File file) {
        this.plugin = plugin;
        this.featuredWarpsFile = file;
        loadFeaturedWarps();
        startExpiryCheckTask();
    }

    public void loadFeaturedWarps() {
        if (!featuredWarpsFile.exists()) {
            try {
                featuredWarpsFile.getParentFile().mkdirs();
                featuredWarpsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossible de créer le fichier des warps mis en avant: " + e.getMessage());
                return;
            }
        }
        featuredWarpsConfig = YamlConfiguration.loadConfiguration(featuredWarpsFile);
        featuredWarps.clear();
        featuredWarpsConfig.getStringList("featured-list").stream()
            .map(FeaturedWarpEntry::fromSaveString)
            .filter(Objects::nonNull)
            .filter(entry -> !entry.isExpired())
            .forEach(featuredWarps::add);
        plugin.getLogger().info(featuredWarps.size() + " warps mis en avant (non expirés) chargés.");
    }

    public void saveFeaturedWarps() {
        if (featuredWarpsConfig == null) {
            plugin.getLogger().warning("featuredWarpsConfig est null. Impossible de sauvegarder les warps mis en avant.");
            return; // Eviter NPE si le fichier n'a pas pu être chargé/créé
        }
        List<String> toSave = featuredWarps.stream()
            // .filter(entry -> !entry.isExpired()) // On sauvegarde tout, le filtre se fait au chargement ou par la tâche
            .map(FeaturedWarpEntry::toSaveString)
            .collect(Collectors.toList());
        featuredWarpsConfig.set("featured-list", toSave);
        try {
            featuredWarpsConfig.save(featuredWarpsFile);
            plugin.getLogger().info("Warps mis en avant sauvegardés.");
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible de sauvegarder les warps mis en avant: " + e.getMessage());
        }
    }

    public boolean isFeatured(UUID ownerUUID, String warpName) {
        FeaturedWarpEntry entry = findEntry(ownerUUID, warpName);
        return entry != null && !entry.isExpired();
    }

    public FeaturedWarpEntry getFeaturedEntry(UUID ownerUUID, String warpName) {
        return findEntry(ownerUUID, warpName);
    }

    private FeaturedWarpEntry findEntry(UUID ownerUUID, String warpName) {
        for (FeaturedWarpEntry entry : featuredWarps) {
            if (entry.ownerUUID.equals(ownerUUID) && entry.warpName.equalsIgnoreCase(warpName)) {
                return entry;
            }
        }
        return null;
    }

    public void addFeaturedWarp(UUID ownerUUID, String warpName) {
        long durationMillis = plugin.getConfigManager().featuredDurationMillis;
        long expiryTimestamp;
        if (durationMillis >= Long.MAX_VALUE / 2 || durationMillis <= 0) { // Vérification pour "permanent"
            expiryTimestamp = Long.MAX_VALUE / 2; // Valeur spéciale pour permanent
        } else {
            expiryTimestamp = System.currentTimeMillis() + durationMillis;
        }

        FeaturedWarpEntry existingEntry = findEntry(ownerUUID, warpName);
        if (existingEntry != null) {
            existingEntry.expiryTimestamp = expiryTimestamp; // Mettre à jour l'expiration (prolongation)
            plugin.getLogger().info("Mise à jour de l'expiration pour le warp mis en avant: " + warpName + " de " + ownerUUID);
        } else {
            featuredWarps.add(new FeaturedWarpEntry(ownerUUID, warpName, expiryTimestamp));
            plugin.getLogger().info("Nouveau warp mis en avant ajouté: " + warpName + " de " + ownerUUID);
        }
        saveFeaturedWarps(); // Sauvegarder après modification
    }

    public void removeFeaturedWarp(UUID ownerUUID, String warpName) {
        boolean removed = featuredWarps.removeIf(entry -> entry.ownerUUID.equals(ownerUUID) && entry.warpName.equalsIgnoreCase(warpName));
        if (removed) {
            plugin.getLogger().info("Warp mis en avant retiré: " + warpName + " de " + ownerUUID);
            saveFeaturedWarps();
        }
    }

    public Set<FeaturedWarpEntry> getFeaturedWarps() { // Retourne les non-expirés
        return featuredWarps.stream().filter(e -> !e.isExpired()).collect(Collectors.toSet());
    }

    private void startExpiryCheckTask() {
        if (expiryCheckTask != null && !expiryCheckTask.isCancelled()) {
            expiryCheckTask.cancel();
        }
        expiryCheckTask = new ExpiryCheckTask().runTaskTimerAsynchronously(plugin, 20L * 60, 20L * 60); // Toutes les minutes
        plugin.getLogger().info("Tâche de vérification d'expiration des warps mis en avant démarrée.");
    }

    public void stopExpiryCheckTask() {
        if (expiryCheckTask != null && !expiryCheckTask.isCancelled()) {
            expiryCheckTask.cancel();
            expiryCheckTask = null;
            plugin.getLogger().info("Tâche de vérification d'expiration des warps mis en avant arrêtée.");
        }
    }

    private class ExpiryCheckTask extends BukkitRunnable {
        @Override
        public void run() {
            plugin.getLogger().fine("Vérification des warps mis en avant expirés...");
            Set<FeaturedWarpEntry> toRemove = new HashSet<>();
            boolean changed = false;
            // Itérer sur une copie pour permettre la modification pendant l'itération
            for (FeaturedWarpEntry entry : new HashSet<>(featuredWarps)) {
                if (entry.isExpired()) {
                    toRemove.add(entry);
                    changed = true;
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(entry.ownerUUID);
                    String ownerName = owner.getName() != null ? owner.getName() : entry.ownerUUID.toString().substring(0, 6);

                    // Préparer les placeholders pour le message
                    Map<String, String> placeholders = new HashMap<>();
                    placeholders.put("%warp_name%", entry.warpName);
                    placeholders.put("%owner_name%", ownerName);
                    // Pas besoin de %duration% ou %cost% ici car c'est un message d'expiration

                    String message = plugin.getConfigManager().msgFeatureExpired; // Obtenir le template du message
                    message = ChatUtils.setPlaceholders(owner, message); // Appliquer PAPI au template
                    // Appliquer nos placeholders custom après PAPI
                    for (Map.Entry<String, String> pEntry : placeholders.entrySet()) {
                        message = message.replace(pEntry.getKey(), pEntry.getValue());
                    }

                    plugin.getLogger().info(ChatUtils.color(message)); // Log pour admin
                    if (owner.isOnline() && owner.getPlayer() != null) {
                        ChatUtils.sendMessage(owner.getPlayer(), message);
                    }
                }
            }
            if (changed) {
                plugin.getLogger().info(toRemove.size() + " warp(s) mis en avant ont expiré et ont été retirés.");
                featuredWarps.removeAll(toRemove);
                saveFeaturedWarps();
                // Un refresh du GUI global pourrait être utile ici si des joueurs ont le menu ouvert
                // Mais cela peut être lourd. Pour l'instant, le GUI se met à jour à la prochaine ouverture/changement de page.
            } else {
                plugin.getLogger().fine("Aucun warp mis en avant n'a expiré.");
            }
        }
    }
}
