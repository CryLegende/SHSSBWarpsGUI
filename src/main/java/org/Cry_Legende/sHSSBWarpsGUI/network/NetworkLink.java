package org.Cry_Legende.sHSSBWarpsGUI.network;

import com.google.gson.JsonObject;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Ce dont le mode réseau a besoin de SHNetwork. Cette interface ne mentionne aucune classe de SHNetwork :
 * le plugin la manipule sans jamais charger {@link ShNetworkLink}, donc sans SHNetwork il ne se passe rien.
 */
public interface NetworkLink {

    /** Nom de ce serveur sur le réseau. */
    String serverName();

    /** Attend que le serveur de départ ait fini de sauvegarder le joueur ; se termine hors du fil principal. */
    CompletableFuture<Boolean> awaitSaved(UUID uuid, Duration timeout);

    /** Inscrit la tâche exécutée au départ de chaque joueur (fil principal, pendant PlayerQuitEvent). */
    void registerSaveTask(Function<Player, CompletableFuture<Void>> task);

    /** Vrai si le joueur est connecté sur un serveur du réseau, celui-ci compris. Ne bloque jamais. */
    boolean isOnline(UUID uuid);

    /** Pseudos des joueurs connectés sur tout le réseau. Ne bloque jamais. */
    Collection<String> onlineNames();

    /** Publie sur un canal, à destination des autres serveurs. Ne bloque jamais. */
    void publish(String channel, JsonObject message);

    /** Écoute un canal sur le fil du bus : le gestionnaire ne doit ni attendre ni toucher au disque ou à la base. */
    void listen(String channel, Consumer<JsonObject> handler);

    /** Envoie le joueur sur un autre serveur et le place à l'arrivée sur le point donné. */
    void sendTo(Player player, String server, Location arrival);
}
