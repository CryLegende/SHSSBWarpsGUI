package org.Cry_Legende.sHSSBWarpsGUI.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class TextureService {

    private final SHSSBWarpsGUI plugin;
    private final File texturesFile; // Cache: PremiumUUID -> TextureString/NO_SKIN_MARKER
    private FileConfiguration texturesConfig;
    private final ConcurrentHashMap<UUID, String> premiumTextureCache = new ConcurrentHashMap<>();

    private final File uuidMappingsFile; // Cache: OfflineUUID -> PremiumUUID/NO_PREMIUM_MARKER
    private FileConfiguration uuidMappingsConfig;
    private final ConcurrentHashMap<UUID, Optional<UUID>> offlineToPremiumCache = new ConcurrentHashMap<>(); // Optional.empty() pour NO_PREMIUM_MARKER

    private final String NO_SKIN_MARKER = "NO_SKIN_DATA_SERVICE_V2"; // Changer pour forcer un refresh du cache si structure change
    private final boolean serverOfflineMode;
    private final int connectTimeout;
    private final int readTimeout;

    public TextureService(SHSSBWarpsGUI plugin, File cacheDir) {
        this.plugin = plugin;
        this.texturesFile = new File(cacheDir, "premium_textures.yml"); // Nom de fichier plus clair
        this.uuidMappingsFile = new File(cacheDir, "offline_uuid_to_premium.yml");
        this.serverOfflineMode = !Bukkit.getServer().getOnlineMode();

        // Lire les timeouts depuis la config principale du plugin
        this.connectTimeout = plugin.getConfig().getInt("texture-service.fetch-timeout.connect", 5000);
        this.readTimeout = plugin.getConfig().getInt("texture-service.fetch-timeout.read", 7000);

        loadCaches();
    }

    public void loadCaches() {
        // Load Premium Textures Cache
        premiumTextureCache.clear();
        if (!texturesFile.exists()) {
            try {
                texturesFile.getParentFile().mkdirs();
                texturesFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create premium_textures.yml: " + e.getMessage());
            }
        }
        texturesConfig = YamlConfiguration.loadConfiguration(texturesFile);
        texturesConfig.getKeys(false).forEach(premiumUuidStr -> {
            try {
                premiumTextureCache.put(UUID.fromString(premiumUuidStr), texturesConfig.getString(premiumUuidStr));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Invalid Premium UUID in premium_textures.yml: " + premiumUuidStr);
            }
        });
        plugin.getLogger().info(premiumTextureCache.size() + " premium textures/markers loaded from premium_textures.yml.");

        if (serverOfflineMode) {
            offlineToPremiumCache.clear();
            if (!uuidMappingsFile.exists()) {
                try {
                    uuidMappingsFile.getParentFile().mkdirs();
                    uuidMappingsFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().severe("Could not create offline_uuid_to_premium.yml: " + e.getMessage());
                }
            }
            uuidMappingsConfig = YamlConfiguration.loadConfiguration(uuidMappingsFile);
            uuidMappingsConfig.getKeys(false).forEach(offlineUuidStr -> {
                try {
                    UUID offlineUUID = UUID.fromString(offlineUuidStr);
                    String premiumUuidOrMarker = uuidMappingsConfig.getString(offlineUuidStr);
                    if (NO_SKIN_MARKER.equals(premiumUuidOrMarker)) { // Utiliser NO_SKIN_MARKER pour indiquer "pas de premium UUID trouvé"
                        offlineToPremiumCache.put(offlineUUID, Optional.empty());
                    } else {
                        offlineToPremiumCache.put(offlineUUID, Optional.of(UUID.fromString(premiumUuidOrMarker)));
                    }
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in offline_uuid_to_premium.yml: " + offlineUuidStr);
                }
            });
            plugin.getLogger().info(offlineToPremiumCache.size() + " offline UUID mappings loaded from offline_uuid_to_premium.yml.");
        }
    }

    public void saveCaches() {
        if (texturesConfig != null) {
            for (String key : texturesConfig.getKeys(false)) texturesConfig.set(key, null);
            premiumTextureCache.forEach((uuid, texture) -> texturesConfig.set(uuid.toString(), texture));
            try {
                texturesConfig.save(texturesFile);
                plugin.getLogger().info("Premium textures cache saved to premium_textures.yml (" + premiumTextureCache.size() + " entries).");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save premium_textures.yml: " + e.getMessage());
            }
        }

        if (serverOfflineMode && uuidMappingsConfig != null) {
            for (String key : uuidMappingsConfig.getKeys(false)) uuidMappingsConfig.set(key, null);
            offlineToPremiumCache.forEach((offlineUUID, premiumUUIDOptional) -> {
                uuidMappingsConfig.set(offlineUUID.toString(), premiumUUIDOptional.map(UUID::toString).orElse(NO_SKIN_MARKER));
            });
            try {
                uuidMappingsConfig.save(uuidMappingsFile);
                plugin.getLogger().info("Offline UUID mappings cache saved to offline_uuid_to_premium.yml (" + offlineToPremiumCache.size() + " entries).");
            } catch (IOException e) {
                plugin.getLogger().severe("Could not save offline_uuid_to_premium.yml: " + e.getMessage());
            }
        }
    }

    public void getPlayerHead(OfflinePlayer targetPlayer, Consumer<ItemStack> callback) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta == null) { // Should not happen, but good to check
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(head));
            return;
        }

        UUID primaryUUID = targetPlayer.getUniqueId(); // UUID que le serveur connaît (offline si en offline mode)
        String playerName = targetPlayer.getName();

        if (!serverOfflineMode) {
            // ONLINE MODE: primaryUUID est l'UUID premium.
            plugin.getLogger().fine("[TextureService] Online mode: Processing head for " + playerName + " (UUID: " + primaryUUID + ")");
            processTextureFetch(primaryUUID, meta, head, targetPlayer, callback);
        } else {
            // OFFLINE MODE:
            plugin.getLogger().fine("[TextureService] Offline mode: Processing head for " + playerName + " (Offline UUID: " + primaryUUID + ")");
            if (offlineToPremiumCache.containsKey(primaryUUID)) {
                Optional<UUID> cachedPremiumUUIDOptional = offlineToPremiumCache.get(primaryUUID);
                if (cachedPremiumUUIDOptional.isPresent()) {
                    UUID premiumUUID = cachedPremiumUUIDOptional.get();
                    plugin.getLogger().fine("[TextureService] OfflineMode: Found cached premium UUID " + premiumUUID + " for " + playerName);
                    processTextureFetch(premiumUUID, meta, head, targetPlayer, callback);
                } else { // Optional.empty() signifie qu'on a déjà cherché et qu'il n'y a pas de premium UUID
                    plugin.getLogger().fine("[TextureService] OfflineMode: Player " + playerName + " (Offline UUID: " + primaryUUID + ") previously marked as having no premium UUID. Applying fallback.");
                    applyFallbackSkin(meta, targetPlayer);
                    head.setItemMeta(meta);
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(head));
                }
            } else {
                // Pas dans offlineToPremiumCache, tenter de récupérer l'UUID premium par le nom
                if (playerName == null) { // Si le nom n'est pas disponible (rare pour OfflinePlayer valide)
                    plugin.getLogger().warning("[TextureService] OfflineMode: Player name is null for Offline UUID " + primaryUUID + ". Applying fallback.");
                    applyFallbackSkin(meta, targetPlayer);
                    head.setItemMeta(meta);
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(head));
                    return;
                }
                plugin.getLogger().fine("[TextureService] OfflineMode: No cached premium UUID for " + playerName + ". Fetching premium UUID by name...");
                getPremiumUUIDFromName(playerName).thenAcceptAsync(premiumUUIDOptional -> {
                    if (premiumUUIDOptional.isPresent()) {
                        UUID premiumUUID = premiumUUIDOptional.get();
                        plugin.getLogger().info("[TextureService] OfflineMode: Fetched premium UUID " + premiumUUID + " for " + playerName + ". Caching mapping.");
                        offlineToPremiumCache.put(primaryUUID, Optional.of(premiumUUID)); // Cache le mapping offline -> premium
                        saveSingleMappingToFile(primaryUUID, premiumUUID.toString());
                        processTextureFetch(premiumUUID, meta, head, targetPlayer, callback);
                    } else {
                        plugin.getLogger().info("[TextureService] OfflineMode: No premium UUID found for " + playerName + ". Marking and applying fallback.");
                        offlineToPremiumCache.put(primaryUUID, Optional.empty()); // Cache qu'il n'y a pas de premium UUID
                        saveSingleMappingToFile(primaryUUID, NO_SKIN_MARKER);
                        applyFallbackSkin(meta, targetPlayer);
                        head.setItemMeta(meta);
                        callback.accept(head);
                    }
                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Exécuter le callback sur le thread principal
            }
        }
    }

    private void saveSingleMappingToFile(UUID offlineUUID, String valueToStore) {
        if (serverOfflineMode && uuidMappingsConfig != null) {
            synchronized (uuidMappingsConfig) {
                uuidMappingsConfig.set(offlineUUID.toString(), valueToStore);
                try {
                    uuidMappingsConfig.save(uuidMappingsFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to save mapping for " + offlineUUID + " to offline_uuid_to_premium.yml: " + e.getMessage());
                }
            }
        }
    }

    private void saveSingleTextureToFile(UUID premiumUUID, String textureValue) {
        if (texturesConfig != null) {
            synchronized (texturesConfig) {
                texturesConfig.set(premiumUUID.toString(), textureValue);
                try {
                    texturesConfig.save(texturesFile);
                } catch (IOException e) {
                    plugin.getLogger().warning("Failed to save texture for " + premiumUUID + " to premium_textures.yml: " + e.getMessage());
                }
            }
        }
    }

    private void processTextureFetch(UUID premiumUUIDToFetch, SkullMeta meta, ItemStack head, OfflinePlayer originalTargetPlayer, Consumer<ItemStack> callback) {
        plugin.getLogger().fine("[TextureService] processTextureFetch: Attempting to use/fetch texture for Premium UUID: " + premiumUUIDToFetch + " (for player " + originalTargetPlayer.getName() + ")");
        String cachedTexture = premiumTextureCache.get(premiumUUIDToFetch);

        if (cachedTexture != null) {
            if (NO_SKIN_MARKER.equals(cachedTexture)) {
                plugin.getLogger().fine("[TextureService] processTextureFetch: Premium UUID " + premiumUUIDToFetch + " marked as NO_SKIN. Applying fallback for " + originalTargetPlayer.getName());
                applyFallbackSkin(meta, originalTargetPlayer);
            } else {
                plugin.getLogger().fine("[TextureService] processTextureFetch: Found cached texture for Premium UUID " + premiumUUIDToFetch + ". Applying for " + originalTargetPlayer.getName());
                applyTextureToMeta(meta, premiumUUIDToFetch, cachedTexture);
            }
            head.setItemMeta(meta);
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(head)); // Assurer que le callback est sur le main thread
            return;
        }

        // Texture non trouvée dans premiumTextureCache, il faut la récupérer de Mojang
        plugin.getLogger().fine("[TextureService] processTextureFetch: No cached texture for Premium UUID " + premiumUUIDToFetch + ". Fetching from Mojang for " + originalTargetPlayer.getName() + "...");
        fetchTextureFromMojang(premiumUUIDToFetch).thenAcceptAsync(fetchResult -> {
            if (fetchResult.isSuccess() && fetchResult.getTexture() != null && !fetchResult.getTexture().isEmpty()) {
                String texture = fetchResult.getTexture();
                plugin.getLogger().info("[TextureService] processTextureFetch: Successfully fetched texture for Premium UUID " + premiumUUIDToFetch + ". Caching and applying for " + originalTargetPlayer.getName());
                premiumTextureCache.put(premiumUUIDToFetch, texture);
                applyTextureToMeta(meta, premiumUUIDToFetch, texture);
                saveSingleTextureToFile(premiumUUIDToFetch, texture);
            } else {
                plugin.getLogger().warning("[TextureService] processTextureFetch: Failed to fetch texture for Premium UUID " + premiumUUIDToFetch +
                    (fetchResult.isNoContent() ? " (No skin data 204)" : " (Error: " + fetchResult.getErrorMessage() + ")") +
                    ". Applying fallback for " + originalTargetPlayer.getName() + " and caching NO_SKIN_MARKER.");
                applyFallbackSkin(meta, originalTargetPlayer);
                premiumTextureCache.put(premiumUUIDToFetch, NO_SKIN_MARKER);
                saveSingleTextureToFile(premiumUUIDToFetch, NO_SKIN_MARKER);
            }
            head.setItemMeta(meta);
            callback.accept(head);
        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Assurer que le callback final est sur le main thread
    }

    private void applyFallbackSkin(SkullMeta meta, OfflinePlayer target) {
        plugin.getLogger().fine("[TextureService] Applying fallback skin (setOwningPlayer) for " + target.getName());
        meta.setOwningPlayer(target);
    }

    /**
     * Applique la texture via l'API de profils de Bukkit.
     *
     * <p>L'ancienne version écrivait le champ {@code profile} de {@code CraftMetaSkull} par
     * réflexion. Ce champ est doublé d'une forme sérialisée que la réflexion ne mettait pas à
     * jour : à la première conversion de l'item en NBT — c'est-à-dire quand la tête entre dans
     * un inventaire — le serveur constatait l'écart, journalisait
     * <i>Found inconsistent skull meta</i> et reconstruisait la sérialisation lui-même. Une
     * ligne d'avertissement par tête, à chaque ouverture de menu.</p>
     *
     * <p>{@code setOwnerProfile} renseigne les deux représentations : l'item est cohérent avant
     * d'entrer dans l'inventaire. Le rendu est identique, seule la façon de poser la texture
     * change.</p>
     */
    private void applyTextureToMeta(SkullMeta skullMeta, UUID premiumUUID, String base64Texture) {
        if (base64Texture == null || base64Texture.isEmpty() || base64Texture.equals(NO_SKIN_MARKER)) {
            return;
        }
        URL skinUrl = skinUrlOf(base64Texture);
        if (skinUrl == null) {
            plugin.getLogger().warning("Texture illisible pour " + premiumUUID + " : skin par défaut.");
            return;
        }
        PlayerProfile profile = Bukkit.createPlayerProfile(premiumUUID);
        profile.getTextures().setSkin(skinUrl);
        skullMeta.setOwnerProfile(profile);
    }

    /**
     * Décode la valeur base64 renvoyée par Mojang et en extrait l'URL du skin.
     */
    private URL skinUrlOf(String base64Texture) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Texture), StandardCharsets.UTF_8);
            JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
            if (textures == null || !textures.has("SKIN")) return null;
            return new URL(textures.getAsJsonObject("SKIN").get("url").getAsString());
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    private static class TextureFetchResult {
        private final String texture;
        private final boolean success;
        private final boolean noContent;
        private final String errorMessage;

        public TextureFetchResult(String texture, boolean success, boolean noContent, String errorMessage) {
            this.texture = texture;
            this.success = success;
            this.noContent = noContent;
            this.errorMessage = errorMessage;
        }

        public String getTexture() {
            return texture;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isNoContent() {
            return noContent;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private CompletableFuture<Optional<UUID>> getPremiumUUIDFromName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String urlStr = "https://api.mojang.com/users/profiles/minecraft/" + playerName;
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setRequestProperty("User-Agent", plugin.getDescription().getName() + "/" + plugin.getDescription().getVersion());

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("id")) {
                            String uuidWithNoDashes = json.get("id").getAsString();
                            String formattedUUID = String.format("%s-%s-%s-%s-%s",
                                uuidWithNoDashes.substring(0, 8), uuidWithNoDashes.substring(8, 12),
                                uuidWithNoDashes.substring(12, 16), uuidWithNoDashes.substring(16, 20),
                                uuidWithNoDashes.substring(20, 32));
                            return Optional.of(UUID.fromString(formattedUUID));
                        }
                    }
                } else {
                    plugin.getLogger().fine("[TextureService] GetPremiumUUID: Impossible de trouver l'UUID premium pour le nom " + playerName + ". Code: " + connection.getResponseCode());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[TextureService] GetPremiumUUID: Erreur lors de la récupération de l'UUID premium pour " + playerName + ": " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
            return Optional.empty();
        });
    }

    private CompletableFuture<TextureFetchResult> fetchTextureFromMojang(UUID premiumUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String urlStr = "https://sessionserver.mojang.com/session/minecraft/profile/" + premiumUUID.toString().replace("-", "") + "?unsigned=false";
            HttpURLConnection connection = null;
            try {
                URL url = new URL(urlStr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(connectTimeout);
                connection.setReadTimeout(readTimeout);
                connection.setRequestProperty("User-Agent", plugin.getDescription().getName() + "/" + plugin.getDescription().getVersion());

                int responseCode = connection.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        StringBuilder responseContent = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) responseContent.append(line);
                        JsonObject json = JsonParser.parseString(responseContent.toString()).getAsJsonObject();
                        if (json.has("properties")) {
                            for (com.google.gson.JsonElement element : json.getAsJsonArray("properties")) {
                                JsonObject property = element.getAsJsonObject();
                                if ("textures".equals(property.get("name").getAsString())) {
                                    return new TextureFetchResult(property.get("value").getAsString(), true, false, null);
                                }
                            }
                        }
                        return new TextureFetchResult(null, false, false, "Propriété 'textures' non trouvée.");
                    }
                } else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) { // 204
                    return new TextureFetchResult(null, false, true, "Aucun profil de skin (204).");
                } else if (responseCode == 429) {
                    return new TextureFetchResult(null, false, false, "Trop de requêtes (429).");
                } else {
                    return new TextureFetchResult(null, false, false, "Code HTTP: " + responseCode);
                }
            } catch (IOException e) {
                return new TextureFetchResult(null, false, false, "Erreur IO: " + e.getMessage());
            } catch (Exception e) {
                plugin.getLogger().severe("[TextureService] FetchTextureFromMojang: Erreur inattendue pour " + premiumUUID + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
                return new TextureFetchResult(null, false, false, "Erreur inattendue: " + e.getMessage());
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
