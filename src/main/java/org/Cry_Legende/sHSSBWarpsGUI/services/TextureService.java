package org.Cry_Legende.sHSSBWarpsGUI.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TextureService {

    private final SHSSBWarpsGUI plugin;
    private final File texturesFile; // Cache: PremiumUUID -> TextureString/NO_SKIN_MARKER
    private final ConcurrentHashMap<UUID, String> premiumTextureCache = new ConcurrentHashMap<>();

    private final File uuidMappingsFile; // Cache: OfflineUUID -> PremiumUUID/NO_PREMIUM_MARKER
    private final ConcurrentHashMap<UUID, Optional<UUID>> offlineToPremiumCache = new ConcurrentHashMap<>(); // Optional.empty() pour NO_PREMIUM_MARKER

    private final String NO_SKIN_MARKER = "NO_SKIN_DATA_SERVICE_V2"; // Changer pour forcer un refresh du cache si structure change
    private final boolean serverOfflineMode;
    private final int connectTimeout;
    private final int readTimeout;

    // Les requêtes HTTP ne partent jamais du thread principal ni du pool commun de la JVM : deux fils dédiés suffisent
    // (Mojang limite le débit, plus de parallélisme n'apporterait que des erreurs 429).
    private final ExecutorService httpExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "SHSSBWarpsGUI-Textures");
        thread.setDaemon(true);
        return thread;
    });

    // Requêtes en cours : plusieurs ouvertures du menu pendant qu'une réponse est attendue ne multiplient pas les appels
    private final ConcurrentHashMap<UUID, CompletableFuture<UuidLookup>> pendingUuidLookups = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<TextureFetchResult>> pendingTextureFetches = new ConcurrentHashMap<>();

    // Après une réponse 429, plus aucun appel pendant ce délai (les têtes retombent sur le skin par défaut sans rien mémoriser)
    private static final long RATE_LIMIT_BACKOFF_MILLIS = 60_000L;
    private volatile long rateLimitedUntil = 0L;

    // Sauvegarde du cache : regroupée et faite hors du thread principal
    private static final long SAVE_DELAY_TICKS = 100L;
    private final Object fileLock = new Object();
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);

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
        synchronized (fileLock) {
            // Ce qui n'est pas encore sur le disque doit y être avant de recharger, sinon le rechargement l'effacerait
            if (dirty.get()) {
                writeCaches();
            }

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
            YamlConfiguration texturesConfig = YamlConfiguration.loadConfiguration(texturesFile);
            texturesConfig.getKeys(false).forEach(premiumUuidStr -> {
                try {
                    String texture = texturesConfig.getString(premiumUuidStr);
                    if (texture != null) {
                        premiumTextureCache.put(UUID.fromString(premiumUuidStr), texture);
                    }
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
                YamlConfiguration uuidMappingsConfig = YamlConfiguration.loadConfiguration(uuidMappingsFile);
                uuidMappingsConfig.getKeys(false).forEach(offlineUuidStr -> {
                    try {
                        UUID offlineUUID = UUID.fromString(offlineUuidStr);
                        String premiumUuidOrMarker = uuidMappingsConfig.getString(offlineUuidStr);
                        if (premiumUuidOrMarker == null) {
                            return;
                        }
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
    }

    /**
     * Écrit les caches sur le disque, tout de suite (arrêt du plugin).
     */
    public void saveCaches() {
        synchronized (fileLock) {
            writeCaches();
        }
    }

    /**
     * Arrêt du plugin : sauvegarde finale et fin des fils HTTP.
     */
    public void shutdown() {
        saveCaches();
        httpExecutor.shutdownNow();
    }

    /**
     * Doit être appelé avec fileLock. Les caches sont copiés dans une configuration neuve (ce qui n'existe plus en mémoire
     * disparaît du fichier) puis écrits dans un fichier temporaire, remplacé d'un coup : un arrêt brutal pendant l'écriture
     * ne tronque pas le cache.
     */
    private void writeCaches() {
        dirty.set(false);

        YamlConfiguration texturesConfig = new YamlConfiguration();
        premiumTextureCache.forEach((uuid, texture) -> texturesConfig.set(uuid.toString(), texture));
        if (writeAtomically(texturesFile, texturesConfig.saveToString(), "premium_textures.yml")) {
            plugin.getLogger().fine("Premium textures cache saved to premium_textures.yml (" + premiumTextureCache.size() + " entries).");
        }

        if (serverOfflineMode) {
            YamlConfiguration uuidMappingsConfig = new YamlConfiguration();
            offlineToPremiumCache.forEach((offlineUUID, premiumUUIDOptional) ->
                uuidMappingsConfig.set(offlineUUID.toString(), premiumUUIDOptional.map(UUID::toString).orElse(NO_SKIN_MARKER)));
            if (writeAtomically(uuidMappingsFile, uuidMappingsConfig.saveToString(), "offline_uuid_to_premium.yml")) {
                plugin.getLogger().fine("Offline UUID mappings cache saved to offline_uuid_to_premium.yml (" + offlineToPremiumCache.size() + " entries).");
            }
        }
    }

    private boolean writeAtomically(File target, String content, String label) {
        try {
            File parent = target.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            File tmp = new File(parent, target.getName() + ".tmp");
            Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save " + label + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Demande une sauvegarde : plusieurs changements rapprochés (une page entière de têtes) donnent une seule écriture,
     * faite hors du thread principal.
     */
    private void markDirty() {
        dirty.set(true);
        if (!plugin.isEnabled() || !saveScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                saveScheduled.set(false);
                synchronized (fileLock) {
                    if (dirty.get()) {
                        writeCaches();
                    }
                }
            }, SAVE_DELAY_TICKS);
        } catch (RuntimeException e) {
            saveScheduled.set(false); // plugin en cours d'arrêt : onDisable sauvegarde
        }
    }

    /**
     * Exécute sur le thread principal, ou ne fait rien si le plugin s'arrête (le callback n'a alors plus de menu à remplir).
     */
    private void runOnMainThread(Runnable runnable) {
        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else if (plugin.isEnabled()) {
            Bukkit.getScheduler().runTask(plugin, runnable);
        }
    }

    public void getPlayerHead(OfflinePlayer targetPlayer, Consumer<ItemStack> callback) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        if (meta == null) { // Should not happen, but good to check
            runOnMainThread(() -> callback.accept(head));
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
            Optional<UUID> cachedPremiumUUIDOptional = offlineToPremiumCache.get(primaryUUID);
            if (cachedPremiumUUIDOptional != null) {
                if (cachedPremiumUUIDOptional.isPresent()) {
                    UUID premiumUUID = cachedPremiumUUIDOptional.get();
                    plugin.getLogger().fine("[TextureService] OfflineMode: Found cached premium UUID " + premiumUUID + " for " + playerName);
                    processTextureFetch(premiumUUID, meta, head, targetPlayer, callback);
                } else { // Optional.empty() signifie qu'on a déjà cherché et qu'il n'y a pas de premium UUID
                    plugin.getLogger().fine("[TextureService] OfflineMode: Player " + playerName + " (Offline UUID: " + primaryUUID + ") previously marked as having no premium UUID. Applying fallback.");
                    applyFallbackSkin(meta, targetPlayer);
                    head.setItemMeta(meta);
                    runOnMainThread(() -> callback.accept(head));
                }
            } else {
                // Pas dans offlineToPremiumCache, tenter de récupérer l'UUID premium par le nom
                if (playerName == null) { // Si le nom n'est pas disponible (rare pour OfflinePlayer valide)
                    plugin.getLogger().warning("[TextureService] OfflineMode: Player name is null for Offline UUID " + primaryUUID + ". Applying fallback.");
                    applyFallbackSkin(meta, targetPlayer);
                    head.setItemMeta(meta);
                    runOnMainThread(() -> callback.accept(head));
                    return;
                }
                plugin.getLogger().fine("[TextureService] OfflineMode: No cached premium UUID for " + playerName + ". Fetching premium UUID by name...");
                getPremiumUUIDFromName(primaryUUID, playerName).thenAcceptAsync(lookup -> {
                    if (lookup.uuid() != null) {
                        UUID premiumUUID = lookup.uuid();
                        plugin.getLogger().info("[TextureService] OfflineMode: Fetched premium UUID " + premiumUUID + " for " + playerName + ". Caching mapping.");
                        offlineToPremiumCache.put(primaryUUID, Optional.of(premiumUUID)); // Cache le mapping offline -> premium
                        markDirty();
                        processTextureFetch(premiumUUID, meta, head, targetPlayer, callback);
                    } else {
                        if (lookup.definitive()) {
                            plugin.getLogger().info("[TextureService] OfflineMode: No premium UUID found for " + playerName + ". Marking and applying fallback.");
                            offlineToPremiumCache.put(primaryUUID, Optional.empty()); // Cache qu'il n'y a pas de premium UUID
                            markDirty();
                        } else {
                            // Erreur passagère (limite de débit, réseau) : rien n'est mémorisé, la prochaine ouverture du menu réessaiera
                            plugin.getLogger().fine("[TextureService] OfflineMode: Lookup failed for " + playerName + " (temporary). Applying fallback without caching.");
                        }
                        applyFallbackSkin(meta, targetPlayer);
                        head.setItemMeta(meta);
                        callback.accept(head);
                    }
                }, this::runOnMainThread); // Exécuter le callback sur le thread principal
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
            runOnMainThread(() -> callback.accept(head)); // Assurer que le callback est sur le main thread
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
                markDirty();
            } else if (fetchResult.isTemporary()) {
                // Limite de débit, erreur réseau ou HTTP : skin par défaut cette fois, sans le mémoriser comme « pas de skin »
                plugin.getLogger().warning("[TextureService] processTextureFetch: Temporary failure fetching texture for Premium UUID " + premiumUUIDToFetch +
                    " (" + fetchResult.getErrorMessage() + "). Applying fallback for " + originalTargetPlayer.getName() + " without caching.");
                applyFallbackSkin(meta, originalTargetPlayer);
            } else {
                plugin.getLogger().warning("[TextureService] processTextureFetch: Failed to fetch texture for Premium UUID " + premiumUUIDToFetch +
                    (fetchResult.isNoContent() ? " (No skin data 204)" : " (Error: " + fetchResult.getErrorMessage() + ")") +
                    ". Applying fallback for " + originalTargetPlayer.getName() + " and caching NO_SKIN_MARKER.");
                applyFallbackSkin(meta, originalTargetPlayer);
                premiumTextureCache.put(premiumUUIDToFetch, NO_SKIN_MARKER);
                markDirty();
            }
            head.setItemMeta(meta);
            callback.accept(head);
        }, this::runOnMainThread); // Assurer que le callback final est sur le main thread
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
            return URI.create(textures.getAsJsonObject("SKIN").get("url").getAsString()).toURL();
        } catch (RuntimeException | IOException e) {
            return null;
        }
    }

    /**
     * Résultat de la recherche d'UUID premium : uuid null = introuvable. definitive = false pour une erreur passagère
     * (limite de débit, réseau), qu'il ne faut pas mémoriser comme « pas de compte premium ».
     */
    private record UuidLookup(UUID uuid, boolean definitive) {
    }

    private static class TextureFetchResult {
        private final String texture;
        private final boolean success;
        private final boolean noContent;
        private final boolean temporary;
        private final String errorMessage;

        public TextureFetchResult(String texture, boolean success, boolean noContent, boolean temporary, String errorMessage) {
            this.texture = texture;
            this.success = success;
            this.noContent = noContent;
            this.temporary = temporary;
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

        public boolean isTemporary() {
            return temporary;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    private boolean isRateLimited() {
        return System.currentTimeMillis() < rateLimitedUntil;
    }

    private void enterRateLimitBackoff() {
        rateLimitedUntil = System.currentTimeMillis() + RATE_LIMIT_BACKOFF_MILLIS;
    }

    private String userAgent() {
        return plugin.getPluginMeta().getName() + "/" + plugin.getPluginMeta().getVersion();
    }

    private CompletableFuture<UuidLookup> getPremiumUUIDFromName(UUID offlineUUID, String playerName) {
        // Une seule recherche à la fois par joueur
        return deduplicated(pendingUuidLookups, offlineUUID, () -> lookupPremiumUuid(playerName), new UuidLookup(null, false));
    }

    /**
     * Lance la tâche sur les fils HTTP, sauf si la même clé est déjà en cours : l'appelant reçoit alors le futur existant.
     * Le futur retourné est retiré de la table dès qu'il est terminé.
     */
    private <T> CompletableFuture<T> deduplicated(ConcurrentHashMap<UUID, CompletableFuture<T>> pending, UUID key,
                                                  Supplier<T> task, T onRejected) {
        CompletableFuture<T> created = new CompletableFuture<>();
        CompletableFuture<T> existing = pending.putIfAbsent(key, created);
        if (existing != null) {
            return existing;
        }
        try {
            CompletableFuture.supplyAsync(task, httpExecutor).whenComplete((result, error) -> {
                pending.remove(key, created);
                if (error != null) {
                    created.completeExceptionally(error);
                } else {
                    created.complete(result);
                }
            });
        } catch (RuntimeException e) { // fils arrêtés (le plugin se désactive)
            pending.remove(key, created);
            created.complete(onRejected);
        }
        return created;
    }

    private UuidLookup lookupPremiumUuid(String playerName) {
        if (isRateLimited()) {
            return new UuidLookup(null, false);
        }
        String urlStr = "https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(playerName, StandardCharsets.UTF_8);
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlStr).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("User-Agent", userAgent());

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    if (json.has("id")) {
                        String uuidWithNoDashes = json.get("id").getAsString();
                        String formattedUUID = String.format("%s-%s-%s-%s-%s",
                            uuidWithNoDashes.substring(0, 8), uuidWithNoDashes.substring(8, 12),
                            uuidWithNoDashes.substring(12, 16), uuidWithNoDashes.substring(16, 20),
                            uuidWithNoDashes.substring(20, 32));
                        return new UuidLookup(UUID.fromString(formattedUUID), true);
                    }
                    return new UuidLookup(null, true);
                }
            }
            if (responseCode == 429) {
                enterRateLimitBackoff();
                return new UuidLookup(null, false);
            }
            plugin.getLogger().fine("[TextureService] GetPremiumUUID: Impossible de trouver l'UUID premium pour le nom " + playerName + ". Code: " + responseCode);
            // 204 / 404 : ce nom n'est pas un compte premium (définitif) ; les autres codes sont des erreurs du service (passagères)
            boolean definitive = responseCode == HttpURLConnection.HTTP_NO_CONTENT
                || responseCode == HttpURLConnection.HTTP_NOT_FOUND
                || responseCode == HttpURLConnection.HTTP_BAD_REQUEST;
            return new UuidLookup(null, definitive);
        } catch (Exception e) {
            plugin.getLogger().warning("[TextureService] GetPremiumUUID: Erreur lors de la récupération de l'UUID premium pour " + playerName + ": " + e.getMessage());
            return new UuidLookup(null, false);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private CompletableFuture<TextureFetchResult> fetchTextureFromMojang(UUID premiumUUID) {
        // Une seule requête à la fois par compte premium
        return deduplicated(pendingTextureFetches, premiumUUID, () -> requestTexture(premiumUUID),
            new TextureFetchResult(null, false, false, true, "Service arrêté."));
    }

    private TextureFetchResult requestTexture(UUID premiumUUID) {
        if (isRateLimited()) {
            return new TextureFetchResult(null, false, false, true, "Limite de débit en cours (429).");
        }
        String urlStr = "https://sessionserver.mojang.com/session/minecraft/profile/" + premiumUUID.toString().replace("-", "") + "?unsigned=false";
        HttpURLConnection connection = null;
        try {
            URL url = URI.create(urlStr).toURL();
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setRequestProperty("User-Agent", userAgent());

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder responseContent = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) responseContent.append(line);
                    JsonObject json = JsonParser.parseString(responseContent.toString()).getAsJsonObject();
                    if (json.has("properties")) {
                        for (com.google.gson.JsonElement element : json.getAsJsonArray("properties")) {
                            JsonObject property = element.getAsJsonObject();
                            if ("textures".equals(property.get("name").getAsString())) {
                                return new TextureFetchResult(property.get("value").getAsString(), true, false, false, null);
                            }
                        }
                    }
                    return new TextureFetchResult(null, false, false, false, "Propriété 'textures' non trouvée.");
                }
            } else if (responseCode == HttpURLConnection.HTTP_NO_CONTENT || responseCode == HttpURLConnection.HTTP_NOT_FOUND) { // 204 / 404
                return new TextureFetchResult(null, false, true, false, "Aucun profil de skin (" + responseCode + ").");
            } else if (responseCode == 429) {
                enterRateLimitBackoff();
                return new TextureFetchResult(null, false, false, true, "Trop de requêtes (429).");
            } else {
                return new TextureFetchResult(null, false, false, true, "Code HTTP: " + responseCode);
            }
        } catch (IOException e) {
            return new TextureFetchResult(null, false, false, true, "Erreur IO: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().severe("[TextureService] FetchTextureFromMojang: Erreur inattendue pour " + premiumUUID + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return new TextureFetchResult(null, false, false, true, "Erreur inattendue: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
