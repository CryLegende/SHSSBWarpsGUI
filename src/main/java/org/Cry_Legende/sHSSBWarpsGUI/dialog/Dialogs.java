package org.Cry_Legende.sHSSBWarpsGUI.dialog;

import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.TextDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.Cry_Legende.sHSSBWarpsGUI.SHSSBWarpsGUI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Saisies par dialogue natif (clients 1.21.6 et plus) : un champ de texte (une ligne ou plusieurs) ou une simple
 * confirmation, avec les boutons Valider et Annuler. Échap vaut Annuler (c'est le bouton « non » du dialogue).
 * <p>
 * Un client peut envoyer n'importe quel clic personnalisé quand il veut : chaque dialogue affiché reçoit donc un
 * <b>jeton</b> aléatoire, glissé dans la clé des boutons (<code>shssbwarpsgui:&lt;saisie&gt;/&lt;jeton&gt;/&lt;action&gt;</code>), et
 * un seul dialogue est attendu par joueur. Un clic n'est pris en compte que si la saisie, le jeton et l'état du joueur
 * correspondent, une seule fois et avant l'expiration ; tout le reste est ignoré. L'action elle-même revérifie
 * ensuite la cible (warp toujours là, droits du joueur) comme le fait le chemin par le chat.
 */
final class Dialogs implements Listener {

    static final String NAMESPACE = "shssbwarpsgui";
    /**
     * Nom du champ de texte dans la réponse du client.
     */
    static final String FIELD = "valeur";
    static final String VALIDATE = "valider";
    static final String CANCEL = "annuler";

    private static final int WIDTH = 300;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder()
        .character('&').hexColors().build();

    /**
     * Ce que le plugin fait quand le joueur répond. Appelé sur le fil principal.
     *
     * @param confirmed false pour Annuler ou Échap
     * @param text      contenu du champ (null sans champ ou si Annuler)
     */
    interface Reaction {
        void accept(Player player, boolean confirmed, String text);
    }

    /**
     * Champ de texte : maxLines 0 = une seule ligne, sinon champ multiligne de cette hauteur (en pixels).
     */
    record Field(String label, String initial, int maxLength, int maxLines, int height) {
    }

    /**
     * Contenu d'un dialogue. Les textes utilisent les codes &amp; ; error est affiché en tête (null : aucune).
     */
    record Screen(String title, List<String> body, String error, Field field, String yes, String no) {
    }

    /**
     * Dialogue attendu pour un joueur.
     */
    record Pending(String kind, String token, long expiresAt, Reaction reaction) {
    }

    enum Outcome {NONE, STALE, EXPIRED, OK}

    record Taken(Outcome outcome, Pending pending) {
    }

    private final SHSSBWarpsGUI plugin;
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    Dialogs(SHSSBWarpsGUI plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ disponibilité

    /**
     * Dialogues activés dans la config et client assez récent. Sans ViaVersion tous les clients ont la version du
     * serveur (1.21.11), donc le dialogue est toujours possible.
     */
    boolean supported(Player player) {
        if (!plugin.getConfig().getBoolean("dialogues.actif", true)) {
            return false;
        }
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) {
                return true;
            }
            return ViaSupport.protocol(player) >= plugin.getConfig().getInt("dialogues.min-protocol", 771);
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ affichage

    /**
     * Affiche le dialogue au prochain tick (le temps que le menu éventuellement ouvert se ferme).
     *
     * @param fallback appelé si l'affichage échoue au dernier moment, pour passer par le chat
     * @return false si le dialogue n'a pas pu être préparé : l'appelant garde alors le chemin par le chat
     */
    boolean show(Player player, String kind, Screen screen, Reaction reaction, Runnable fallback) {
        UUID id = player.getUniqueId();
        Pending ticket;
        Dialog dialog;
        try {
            ticket = issue(id, kind, expirationNanos(), reaction, System.nanoTime());
            dialog = build(kind, ticket.token(), screen);
        } catch (RuntimeException e) {
            pending.remove(id);
            plugin.getLogger().log(Level.WARNING, "Dialogue « " + kind + " » impossible pour " + player.getName() + " : " + e, e);
            return false;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            // remplacé entre-temps, annulé, ou joueur parti : rien à afficher
            if (pending.get(id) != ticket || !player.isOnline()) {
                return;
            }
            try {
                player.showDialog(dialog);
            } catch (RuntimeException e) {
                pending.remove(id, ticket);
                plugin.getLogger().log(Level.WARNING, "Affichage du dialogue « " + kind + " » impossible pour " + player.getName() + " : " + e, e);
                if (fallback != null) {
                    fallback.run();
                }
            }
        });
        return true;
    }

    private long expirationNanos() {
        return Math.max(10L, plugin.getConfig().getLong("dialogues.expiration", 600L)) * 1_000_000_000L;
    }

    private Dialog build(String kind, String token, Screen screen) {
        List<DialogBody> body = new ArrayList<>();
        if (screen.error() != null && !screen.error().isEmpty()) {
            body.add(DialogBody.plainMessage(LEGACY.deserialize(screen.error()), WIDTH));
        }
        for (String line : screen.body()) {
            body.add(DialogBody.plainMessage(LEGACY.deserialize(line), WIDTH));
        }
        List<DialogInput> inputs = new ArrayList<>();
        Field field = screen.field();
        if (field != null) {
            int max = Math.max(1, field.maxLength());
            TextDialogInput.Builder input = DialogInput.text(FIELD, LEGACY.deserialize(field.label())).width(WIDTH).maxLength(max);
            String initial = field.initial();
            if (initial != null && !initial.isEmpty()) {
                if (field.maxLines() > 0) {
                    String[] rows = initial.split("\n", -1);
                    if (rows.length > field.maxLines()) {
                        initial = String.join("\n", java.util.Arrays.copyOf(rows, field.maxLines()));
                    }
                }
                // le client refuse (et déconnecte) un texte initial plus long que la limite
                input.initial(initial.length() > max ? initial.substring(0, max) : initial);
            }
            if (field.maxLines() > 0) {
                input.multiline(TextDialogInput.MultilineOptions.create(field.maxLines(), Math.max(40, Math.min(300, field.height()))));
            }
            inputs.add(input.build());
        }
        DialogBase base = DialogBase.builder(LEGACY.deserialize(screen.title()))
            .canCloseWithEscape(true)
            // le client ferme l'écran dès le clic ; en cas d'erreur de saisie le plugin en rouvre un nouveau
            .afterAction(DialogBase.DialogAfterAction.CLOSE)
            .body(body)
            .inputs(inputs)
            .build();
        ActionButton yes = ActionButton.builder(LEGACY.deserialize(screen.yes()))
            .action(DialogAction.customClick(key(kind, token, VALIDATE), null)).build();
        ActionButton no = ActionButton.builder(LEGACY.deserialize(screen.no()))
            .action(DialogAction.customClick(key(kind, token, CANCEL), null)).build();
        return Dialog.create(factory -> factory.empty().base(base).type(DialogType.confirmation(yes, no)));
    }

    // ------------------------------------------------------------------ jetons

    static Key key(String kind, String token, String action) {
        return Key.key(NAMESPACE, kind + "/" + token + "/" + action);
    }

    /**
     * {saisie, jeton, action} d'une clé du plugin, ou null si ce n'est pas une clé valable.
     */
    static String[] parse(Key key) {
        if (!NAMESPACE.equals(key.namespace())) {
            return null;
        }
        String[] parts = key.value().split("/", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].length() != 32) {
            return null;
        }
        return VALIDATE.equals(parts[2]) || CANCEL.equals(parts[2]) ? parts : null;
    }

    /**
     * Attend un dialogue de ce joueur ; remplace celui qui était attendu.
     */
    Pending issue(UUID player, String kind, long ttlNanos, Reaction reaction, long now) {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        Pending ticket = new Pending(kind, HexFormat.of().formatHex(bytes), now + ttlNanos, reaction);
        pending.put(player, ticket);
        return ticket;
    }

    /**
     * Vérifie un clic : bon joueur, bonne saisie, bon jeton, pas expiré. Le dialogue n'est utilisable qu'une fois.
     */
    Taken take(UUID player, String kind, String token, long now) {
        Pending current = pending.get(player);
        if (current == null) {
            return new Taken(Outcome.NONE, null);
        }
        boolean same = current.kind().equals(kind)
            && MessageDigest.isEqual(current.token().getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8));
        if (!same) {
            return new Taken(Outcome.STALE, null);
        }
        if (!pending.remove(player, current)) {
            return new Taken(Outcome.NONE, null);
        }
        return now - current.expiresAt() >= 0 ? new Taken(Outcome.EXPIRED, null) : new Taken(Outcome.OK, current);
    }

    /**
     * Abandonne le dialogue attendu de ce joueur (sans rien lui dire).
     */
    void cancel(Player player) {
        pending.remove(player.getUniqueId());
    }

    void clear() {
        pending.clear();
    }

    // ------------------------------------------------------------------ événements

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        Key id = event.getIdentifier();
        if (!NAMESPACE.equals(id.namespace()) || !(event.getCommonConnection() instanceof PlayerGameConnection game)) {
            return;
        }
        Player player = game.getPlayer();
        String text = null;
        try {
            DialogResponseView view = event.getDialogResponseView();
            text = view == null ? null : view.getText(FIELD);
        } catch (RuntimeException ignored) {
            // réponse illisible : traitée comme un champ vide
        }
        String value = text;
        if (Bukkit.isPrimaryThread()) {
            handle(player, id, value);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> handle(player, id, value));
        }
    }

    private void handle(Player player, Key id, String text) {
        String[] parts = parse(id);
        if (parts == null || !player.isOnline()) {
            return;
        }
        Taken taken = take(player.getUniqueId(), parts[0], parts[1], System.nanoTime());
        if (taken.outcome() == Outcome.EXPIRED) {
            player.sendMessage(LEGACY.deserialize(plugin.getConfig().getString("dialogues.messages.expire", "")));
            return;
        }
        if (taken.outcome() != Outcome.OK) {
            // pas de dialogue attendu, ou ancien : clic ignoré
            return;
        }
        boolean confirmed = VALIDATE.equals(parts[2]);
        try {
            taken.pending().reaction().accept(player, confirmed, confirmed ? (text == null ? "" : text) : null);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Dialogue « " + parts[0] + " » de " + player.getName() + " : " + e, e);
            player.sendMessage(LEGACY.deserialize(plugin.getConfig().getString("dialogues.messages.erreur", "")));
        }
    }
}
