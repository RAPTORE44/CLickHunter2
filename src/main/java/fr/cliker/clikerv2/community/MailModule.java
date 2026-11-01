package fr.cliker.clikerv2.community;

import fr.cliker.clikerv2.features.*;
import fr.cliker.clikerv2.rewards.*;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Rédaction privée depuis le menu, lecture en livre et envoi durable de pièces jointes. */
public final class MailModule implements Listener, CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final CommunityStore store;
    private final ClaimService claims;
    private final OutgoingItems outgoing;
    private final Menus menus;
    private final Settings settings;
    private final Map<UUID, Draft> drafts = new HashMap<>();
    private final Set<UUID> writing = ConcurrentHashMap.newKeySet();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<UUID, Long> sent = new HashMap<>();

    private static final class Draft {
        String target;
        String body = "";

        Draft(String name) {
            target = name;
        }
    }

    public MailModule(
            JavaPlugin p, CommunityStore store, ClaimService claims, OutgoingItems outgoing) {
        plugin = p;
        this.store = store;
        this.claims = claims;
        this.outgoing = outgoing;
        menus = new Menus(p);
        settings = new Settings(p, "communaute/courrier.yml");
        p.getCommand("courrier").setExecutor(this);
        p.getCommand("courrier").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, p);
    }

    private void sync(Runnable r) {
        if (plugin.isEnabled()) fr.cliker.clikerv2.runtime.ServerTasks.run(plugin, r);
    }

    private boolean current(Player p) {
        return fr.cliker.clikerv2.runtime.PlayerSessions.isCurrent(p);
    }

    private boolean ready(Player p) {
        return current(p) && !fr.cliker.clikerv2.outpost.WorldInventories.isolated(p);
    }

    private void error(Player p, Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) cause = cause.getCause();
        if (cause instanceof java.sql.SQLException
                && Set.of(
                                "Ce joueur refuse ton courrier.",
                                "Boîte aux lettres pleine.",
                                "Courrier vide ou trop long.")
                        .contains(cause.getMessage())) {
            p.sendMessage("§c" + cause.getMessage());
            return;
        }
        plugin.getLogger().log(java.util.logging.Level.WARNING, "Opération courrier refusée", e);
        p.sendMessage(
                "§cCourrier refusé : destinataire bloqué, boîte pleine ou service indisponible."
                        + " Aucun objet n’est perdu.");
    }

    private boolean clean(String text) {
        String plain =
                java.text.Normalizer.normalize(
                                text.toLowerCase(Locale.ROOT), java.text.Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "");
        return settings.yaml.getStringList("moderation.mots-interdits").stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .noneMatch(plain::contains)
                && (settings.yaml.getBoolean("moderation.autoriser-liens", false)
                        || !plain.matches("(?s).*(https?://|www\\.).*"));
    }

    private void open(Player p, int page) {
        store.inbox(p.getUniqueId(), page)
                .whenComplete(
                        (letters, e) ->
                                sync(
                                        () -> {
                                            if (!ready(p)) return;
                                            if (e != null) {
                                                error(p, e);
                                                return;
                                            }
                                            var v =
                                                    menus.create(
                                                            p,
                                                            54,
                                                            "Boîte aux lettres • " + (page + 1));
                                            for (int n = 0; n < letters.size(); n++) {
                                                var letter = letters.get(n);
                                                v.put(
                                                        n,
                                                        Menus.item(
                                                                letter.payload() == null
                                                                        ? Material.WRITTEN_BOOK
                                                                        : Material.CHEST,
                                                                (letter.read() ? "&7" : "&a")
                                                                        + "Lettre de "
                                                                        + letter.name(),
                                                                "&7"
                                                                        + (letter.read()
                                                                                ? "Déjà lue"
                                                                                : "Nouvelle"
                                                                                      + " lettre"),
                                                                "&eClique pour lire et gérer ce"
                                                                        + " courrier.",
                                                                letter.payload() == null
                                                                        ? "&7Sans pièce jointe."
                                                                        : "&6Une pièce jointe est"
                                                                                + " disponible."),
                                                        (who, right) -> detail(who, letter, page));
                                            }
                                            v.put(
                                                    45,
                                                    Menus.item(Material.ARROW, "&ePage précédente"),
                                                    (who, right) ->
                                                            open(who, Math.max(0, page - 1)));
                                            v.put(
                                                    48,
                                                    Menus.item(
                                                            Material.WRITABLE_BOOK,
                                                            "&bRédiger une lettre",
                                                            "&7Clique puis écris le pseudo dans le"
                                                                    + " chat privé.",
                                                            "&7Ou /courrier ecrire <pseudo>."),
                                                    (who, right) -> {
                                                        drafts.put(
                                                                who.getUniqueId(), new Draft(null));
                                                        writing.add(who.getUniqueId());
                                                        who.closeInventory();
                                                        who.sendMessage(
                                                                "§bDestinataire ? Écris son pseudo"
                                                                    + " dans le chat, ou annuler.");
                                                    });
                                            v.put(
                                                    50,
                                                    Menus.item(
                                                            Material.PAPER,
                                                            "&fEnvoyer un objet",
                                                            "&7Tiens l’objet en main, puis :",
                                                            "&e/courrier envoyer <pseudo>",
                                                            "&7Un livre signé peut aussi servir de"
                                                                    + " lettre."),
                                                    null);
                                            if (letters.size() == 45)
                                                v.put(
                                                        53,
                                                        Menus.item(
                                                                Material.ARROW, "&ePage suivante"),
                                                        (who, right) -> open(who, page + 1));
                                            v.show(p);
                                        }));
    }

    private ItemStack book(String sender, String body) {
        var i = new ItemStack(Material.WRITTEN_BOOK);
        var m = (BookMeta) i.getItemMeta();
        m.setTitle("Courrier ClickHunter");
        m.setAuthor(sender);
        List<Component> pages = new ArrayList<>();
        if (body.isBlank()) body = "Ce courrier contient une pièce jointe.";
        for (int at = 0; at < body.length() && pages.size() < 50; at += 300)
            pages.add(Component.text(body.substring(at, Math.min(body.length(), at + 300))));
        m.pages(pages);
        i.setItemMeta(m);
        return i;
    }

    private void detail(Player p, CommunityStore.Letter letter, int page) {
        var v = menus.create(p, 27, "Courrier de " + letter.name());
        v.put(
                11,
                Menus.item(Material.WRITTEN_BOOK, "&bLire la lettre"),
                (who, right) -> {
                    store.read(who.getUniqueId(), letter.id());
                    who.openBook(book(letter.name(), letter.body()));
                });
        v.put(
                13,
                Menus.item(
                        letter.payload() == null ? Material.GRAY_DYE : Material.CHEST,
                        "&6Récupérer la pièce jointe",
                        letter.payload() == null
                                ? "&7Aucun objet à récupérer."
                                : "&7Inventaire plein : surplus dans /claim."),
                (who, right) -> {
                    if (!ready(who) || !busy.add(who.getUniqueId())) return;
                    store.take(who.getUniqueId(), letter.id())
                            .whenComplete(
                                    (taken, e) ->
                                            sync(
                                                    () -> {
                                                        busy.remove(who.getUniqueId());
                                                        if (!current(who)) return;
                                                        if (e != null) error(who, e);
                                                        else {
                                                            claims.resume(who.getUniqueId());
                                                            open(who, page);
                                                        }
                                                    }));
                });
        v.put(
                15,
                Menus.item(
                        Material.BARRIER,
                        "&cSupprimer la lettre",
                        "&7Impossible tant qu’une pièce jointe reste présente."),
                (who, right) ->
                        store.delete(who.getUniqueId(), letter.id())
                                .whenComplete(
                                        (ok, e) ->
                                                sync(
                                                        () -> {
                                                            if (!ready(who)) return;
                                                            if (e != null) error(who, e);
                                                            else if (!ok)
                                                                who.sendMessage(
                                                                        "§cRécupère d’abord la"
                                                                                + " pièce jointe.");
                                                            else open(who, page);
                                                        })));
        v.put(22, Menus.item(Material.ARROW, "&eRetour"), (who, right) -> open(who, page));
        v.put(
                26,
                Menus.item(
                        Material.RED_DYE,
                        "&cBloquer cet expéditeur",
                        "&7Les lettres déjà reçues sont conservées.",
                        "&7/courrier debloquer " + letter.name()),
                (who, right) ->
                        store.block(who.getUniqueId(), letter.sender(), true)
                                .whenComplete(
                                        (ok, e) ->
                                                sync(
                                                        () -> {
                                                            if (!current(who)) return;
                                                            if (e != null) error(who, e);
                                                            else
                                                                who.sendMessage(
                                                                        "§aExpéditeur bloqué.");
                                                        })));
        v.show(p);
    }

    private void compose(Player p) {
        var d = drafts.get(p.getUniqueId());
        if (d == null) return;
        var v = menus.create(p, 27, "Lettre pour " + d.target);
        v.put(
                10,
                Menus.item(
                        Material.WRITABLE_BOOK,
                        "&bÉcrire le texte",
                        "&7Chaque message privé ajoute un paragraphe.",
                        "&7Écris terminer pour revenir au menu.",
                        "&7Le texte reste privé, jamais publié dans le chat."),
                (who, right) -> {
                    writing.add(who.getUniqueId());
                    who.closeInventory();
                    who.sendMessage(
                            "§bÉcris ta lettre. terminer : aperçu ; annuler : quitter la"
                                    + " rédaction.");
                });
        v.put(
                12,
                Menus.item(Material.WRITTEN_BOOK, "&fAperçu du livre"),
                (who, right) -> who.openBook(book(who.getName(), d.body)));
        v.put(
                14,
                Menus.item(Material.LIME_DYE, "&aEnvoyer le texte", "&7Sans pièce jointe."),
                (who, right) -> send(who, d, false));
        v.put(
                16,
                Menus.item(
                        Material.CHEST,
                        "&6Envoyer avec l’objet tenu",
                        "&7Toute la pile en main est jointe.",
                        "&7Les objets verrouillés sont refusés."),
                (who, right) -> send(who, d, true));
        v.show(p);
    }

    private void send(Player p, Draft d, boolean attachment) {
        if (!ready(p) || busy.contains(p.getUniqueId())) return;
        if (System.currentTimeMillis() - sent.getOrDefault(p.getUniqueId(), 0L)
                < Math.max(1000, settings.yaml.getInt("delai-envoi-secondes", 10) * 1000L)) {
            p.sendMessage("§eAttends quelques secondes avant un nouvel envoi.");
            return;
        }
        var item = attachment ? p.getInventory().getItemInMainHand().clone() : null;
        if (attachment
                && (item == null
                        || item.getType().isAir()
                        || fr.cliker.clikerv2.comfort.PlayerComfort.protectedForSale(p, item))) {
            p.sendMessage("§cTiens un objet non verrouillé en main.");
            return;
        }
        if (!clean(d.body) || d.body.length() > 15000 || (d.body.isBlank() && !attachment)) {
            p.sendMessage("§cTexte vide, trop long ou refusé par la modération.");
            return;
        }
        busy.add(p.getUniqueId());
        String body = d.body, target = d.target;
        String payload = item == null ? null : ClaimService.serialize(item);
        String id = UUID.randomUUID().toString();
        claims.resolve(target)
                .whenComplete(
                        (owner, e) ->
                                sync(
                                        () -> {
                                            if (!ready(p)) {
                                                busy.remove(p.getUniqueId());
                                                return;
                                            }
                                            if (e != null
                                                    || owner == null
                                                    || owner.equals(p.getUniqueId())) {
                                                busy.remove(p.getUniqueId());
                                                p.sendMessage(
                                                        "§cChoisis un autre joueur déjà connu du"
                                                                + " serveur.");
                                                return;
                                            }
                                            java.util.function.Consumer<Throwable> done =
                                                    failure -> {
                                                        busy.remove(p.getUniqueId());
                                                        if (failure != null) {
                                                            if (current(p)) error(p, failure);
                                                            return;
                                                        }
                                                        sent.put(
                                                                p.getUniqueId(),
                                                                System.currentTimeMillis());
                                                        drafts.remove(p.getUniqueId());
                                                        if (current(p))
                                                            p.sendMessage(
                                                                    "§aCourrier envoyé à "
                                                                            + target
                                                                            + ".");
                                                        var recipient = Bukkit.getPlayer(owner);
                                                        if (recipient != null
                                                                && !AfkModule.isolated(recipient)) {
                                                            recipient.sendMessage(
                                                                    "§b✉ Nouveau courrier de "
                                                                            + p.getName()
                                                                            + ". /courrier");
                                                            ToastNotifications.show(
                                                                    recipient,
                                                                    Material.WRITTEN_BOOK,
                                                                    "Nouveau courrier",
                                                                    p.getName() + " t’a écrit.");
                                                        }
                                                    };
                                            int maximum =
                                                    Math.max(
                                                            10,
                                                            Math.min(
                                                                    1000,
                                                                    settings.yaml.getInt(
                                                                            "maximum-courriers",
                                                                            100)));
                                            if (item != null) {
                                                if (!outgoing.consumeHeld(
                                                        p,
                                                        item,
                                                        receipt ->
                                                                store.send(
                                                                        id,
                                                                        owner,
                                                                        p.getUniqueId(),
                                                                        p.getName(),
                                                                        body,
                                                                        payload,
                                                                        receipt,
                                                                        maximum),
                                                        done)) {
                                                    busy.remove(p.getUniqueId());
                                                    p.sendMessage(
                                                            "§eAttends la fin des transferts"
                                                                    + " d’objets.");
                                                }
                                            } else
                                                store.send(
                                                                id,
                                                                owner,
                                                                p.getUniqueId(),
                                                                p.getName(),
                                                                body,
                                                                null,
                                                                null,
                                                                maximum)
                                                        .whenComplete(
                                                                (ok, failure) ->
                                                                        sync(
                                                                                () ->
                                                                                        done.accept(
                                                                                                failure)));
                                        }));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void chat(io.papermc.paper.event.player.AsyncChatEvent e) {
        if (!writing.contains(e.getPlayer().getUniqueId())) return;
        e.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(e.message());
        sync(
                () -> {
                    Player p = e.getPlayer();
                    if (!ready(p) || !writing.contains(p.getUniqueId())) return;
                    Draft d = drafts.get(p.getUniqueId());
                    if (d == null) {
                        writing.remove(p.getUniqueId());
                        return;
                    }
                    if (text.equalsIgnoreCase("annuler")) {
                        writing.remove(p.getUniqueId());
                        p.sendMessage("§7Rédaction suspendue. /courrier ecrire pour reprendre.");
                        return;
                    }
                    if (d.target == null) {
                        if (!text.matches("[A-Za-z0-9_]{1,16}")) {
                            p.sendMessage("§cPseudo invalide.");
                            return;
                        }
                        d.target = text;
                        writing.remove(p.getUniqueId());
                        compose(p);
                        return;
                    }
                    if (text.equalsIgnoreCase("terminer")) {
                        writing.remove(p.getUniqueId());
                        compose(p);
                        return;
                    }
                    if (text.length() + d.body.length() + 1 > 15000 || !clean(text)) {
                        p.sendMessage("§cParagraphe refusé ou lettre trop longue.");
                        return;
                    }
                    d.body += (d.body.isBlank() ? "" : "\n\n") + text;
                    p.sendMessage("§7Paragraphe ajouté. terminer pour l’aperçu.");
                });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command c, String l, String[] a) {
        if (!(sender instanceof Player p)) return true;
        if (!ready(p)) {
            p.sendMessage("§cReviens au clicker pour utiliser ton courrier.");
            return true;
        }
        if (a.length == 0) {
            open(p, 0);
            return true;
        }
        if (a[0].equalsIgnoreCase("ecrire")) {
            if (a.length > 1) drafts.put(p.getUniqueId(), new Draft(a[1]));
            if (drafts.containsKey(p.getUniqueId())) compose(p);
            else p.sendMessage("§e/courrier ecrire <pseudo>");
        } else if (a.length == 2 && a[0].equalsIgnoreCase("envoyer")) {
            var d = new Draft(a[1]);
            var held = p.getInventory().getItemInMainHand();
            if (held.hasItemMeta() && held.getItemMeta() instanceof BookMeta m) {
                d.body =
                        m.pages().stream()
                                .map(PlainTextComponentSerializer.plainText()::serialize)
                                .collect(java.util.stream.Collectors.joining("\n\n"));
            }
            send(p, d, true);
        } else if (a.length == 2
                && Set.of("bloquer", "debloquer").contains(a[0].toLowerCase(Locale.ROOT)))
            claims.resolve(a[1])
                    .whenComplete(
                            (id, e) -> {
                                if (e != null || id == null) {
                                    sync(() -> p.sendMessage("§cJoueur inconnu."));
                                    return;
                                }
                                store.block(p.getUniqueId(), id, a[0].equalsIgnoreCase("bloquer"))
                                        .whenComplete(
                                                (ok, error) ->
                                                        sync(
                                                                () -> {
                                                                    if (current(p))
                                                                        p.sendMessage(
                                                                                error == null
                                                                                        ? "§aPréférence"
                                                                                              + " de courrier"
                                                                                              + " enregistrée."
                                                                                        : "§cEnregistrement"
                                                                                              + " impossible.");
                                                                }));
                            });
        else p.sendMessage("§e/courrier [ecrire|envoyer|bloquer|debloquer] <pseudo>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return a.length == 1
                ? List.of("ecrire", "envoyer", "bloquer", "debloquer").stream()
                        .filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT)))
                        .toList()
                : a.length == 2
                        ? Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(
                                        x ->
                                                x.toLowerCase(Locale.ROOT)
                                                        .startsWith(a[1].toLowerCase(Locale.ROOT)))
                                .toList()
                        : List.of();
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        store.unread(p.getUniqueId())
                .whenComplete(
                        (n, error) ->
                                sync(
                                        () -> {
                                            if (error == null && n > 0 && ready(p)) {
                                                p.sendMessage(
                                                        "§b✉ "
                                                                + n
                                                                + " courrier(s) non lu(s)."
                                                                + " /courrier");
                                                ToastNotifications.show(
                                                        p,
                                                        Material.WRITTEN_BOOK,
                                                        "Boîte aux lettres",
                                                        n + " courrier(s) à lire.");
                                            }
                                        }));
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        writing.remove(e.getPlayer().getUniqueId());
        drafts.remove(e.getPlayer().getUniqueId());
        busy.remove(e.getPlayer().getUniqueId());
        sent.remove(e.getPlayer().getUniqueId());
    }

    public void shutdown() {
        writing.clear();
        drafts.clear();
        busy.clear();
        HandlerList.unregisterAll(this);
    }
}
