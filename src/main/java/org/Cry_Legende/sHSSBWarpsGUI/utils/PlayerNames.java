package org.Cry_Legende.sHSSBWarpsGUI.utils;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Noms de joueurs sans accès disque.
 * OfflinePlayer.getName() d'un joueur hors ligne relit son fichier playerdata à chaque appel (5 à 40 ms) :
 * le nom vient du joueur en ligne, sinon de SuperiorSkyblock2 (en mémoire), et n'est lu sur disque qu'en dernier recours.
 * En mode hors ligne l'UUID dérive du pseudo : un nom trouvé ne change plus, il est gardé pour toute la session.
 */
public final class PlayerNames {

    private static final Map<UUID, String> NAMES = new ConcurrentHashMap<>();

    private PlayerNames() {
    }

    public static String of(OfflinePlayer player) {
        if (player == null) {
            return null;
        }
        if (player instanceof Player online) {
            return online.getName();
        }
        UUID id = player.getUniqueId();
        String cached = NAMES.get(id);
        if (cached != null) {
            return cached;
        }
        Player online = player.getPlayer();
        String name = online != null ? online.getName() : null;
        if (name == null) {
            SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayers().getPlayersContainer().getSuperiorPlayer(id);
            name = superiorPlayer != null ? superiorPlayer.getName() : null;
        }
        if (name == null || name.isEmpty() || name.equalsIgnoreCase("null")) {
            name = player.getName();
        }
        if (name != null) {
            NAMES.put(id, name);
        }
        return name;
    }

    public static String of(OfflinePlayer player, String fallback) {
        String name = of(player);
        return name != null ? name : fallback;
    }
}
