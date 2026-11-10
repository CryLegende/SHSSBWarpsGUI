package org.Cry_Legende.sHSSBWarpsGUI.network;

import com.google.gson.JsonObject;
import org.Cry_Legende.sHNetwork.api.NetPlayer;
import org.Cry_Legende.sHNetwork.api.SHNetworkAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

/** Pont vers SHNetwork. Cette classe n'est chargée que si SHNetwork est installé. */
public final class ShNetworkLink implements NetworkLink {

    private final SHNetworkAPI api;

    private ShNetworkLink() {
        this.api = SHNetworkAPI.get();
    }

    /** Point d'entrée : le plugin ne résout cette classe qu'à cet appel, donc jamais sans SHNetwork. */
    public static NetworkLink open() {
        return new ShNetworkLink();
    }

    @Override
    public String serverName() {
        return api.serverName();
    }

    @Override
    public CompletableFuture<Boolean> awaitSaved(UUID uuid, Duration timeout) {
        return api.awaitSaved(uuid, timeout);
    }

    @Override
    public void registerSaveTask(Function<Player, CompletableFuture<Void>> task) {
        api.registerSaveTask("SHSSBWarpsGUI", task);
    }

    @Override
    public boolean isOnline(UUID uuid) {
        return api.find(uuid.toString()).isPresent();
    }

    @Override
    public Collection<String> onlineNames() {
        Collection<NetPlayer> all = api.online();
        Collection<String> names = new ArrayList<>(all.size());
        for (NetPlayer player : all) {
            names.add(player.name());
        }
        return names;
    }

    @Override
    public void publish(String channel, JsonObject message) {
        api.publish(channel, message.toString());
    }

    @Override
    public void listen(String channel, Consumer<JsonObject> handler) {
        api.subscribe(channel, message -> {
            JsonObject json;
            try {
                json = message.object();
            } catch (RuntimeException e) {
                return; // message illisible : le bus ne s'arrête jamais pour lui
            }
            handler.accept(json);
        }, false);
    }

    @Override
    public void sendTo(Player player, String server, Location arrival) {
        api.sendTo(player, server, arrival);
    }
}
