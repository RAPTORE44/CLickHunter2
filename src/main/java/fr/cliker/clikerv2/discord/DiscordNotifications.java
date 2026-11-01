package fr.cliker.clikerv2.discord;

import fr.cliker.clikerv2.features.Settings;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.event.*;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Notifications métier distinctes du PNJ et du lien d'invitation Discord. */
public final class DiscordNotifications implements Listener, CommandExecutor, TabCompleter {
    private static DiscordNotifications current;
    private final JavaPlugin plugin;
    private Settings settings;
    private WebhookRelay relay;

    public DiscordNotifications(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
        current = this;
        plugin.getCommand("discordnotif").setExecutor(this);
        plugin.getCommand("discordnotif").setTabCompleter(this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void reload() {
        Settings next = new Settings(plugin, "integrations/discord-notifications.yml");
        WebhookRelay replacement = null;
        if (next.yaml.getBoolean("actif", false))
            replacement =
                    new WebhookRelay(
                            WebhookRelay.endpoint(next.yaml.getString("webhook-url", "")),
                            WebhookRelay.http(),
                            message -> plugin.getLogger().warning(message),
                            next.yaml.getInt("file-maximum", 64));
        WebhookRelay old = relay;
        settings = next;
        relay = replacement;
        if (old != null) old.close();
    }

    public static void emit(String event, String id, Map<String, String> values) {
        DiscordNotifications service = current;
        if (service == null) return;
        if (!Bukkit.isPrimaryThread()) {
            fr.cliker.clikerv2.runtime.ServerTasks.run(
                    service.plugin, () -> emit(event, id, values));
            return;
        }
        service.publish(event, id, values);
    }

    private boolean publish(String event, String id, Map<String, String> values) {
        if (relay == null || !settings.yaml.getBoolean("evenements." + event + ".actif", false))
            return false;
        String path = "evenements." + event;
        String title = settings.yaml.getString(path + ".titre", event),
                body = settings.yaml.getString(path + ".texte", "");
        Map<String, String> placeholders = new HashMap<>(values);
        placeholders.put("serveur", settings.yaml.getString("serveur", "ClickHunter"));
        placeholders.put("adresse", settings.yaml.getString("adresse", "play.clickhunter.fr"));
        for (var entry : placeholders.entrySet()) {
            title = title.replace("{" + entry.getKey() + "}", entry.getValue());
            body = body.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return relay.offer(
                event + ":" + id,
                WebhookRelay.payload(
                        settings.yaml.getString("nom-webhook", "ClickHunter • Événements"),
                        title,
                        body,
                        settings.yaml.getInt(path + ".couleur", 0x55ff55)));
    }

    @EventHandler
    public void bossDeath(fr.cliker.clikerv2.features.HuntKillEvent event) {
        if (event.boss)
            emit(
                    "boss-zone-mort",
                    UUID.randomUUID().toString(),
                    Map.of("boss", event.tier.label(), "joueur", event.name));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cliker.discord.admin")) {
            sender.sendMessage("§cPermission insuffisante.");
            return true;
        }
        String sub = args.length == 0 ? "statut" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("reload")) {
            try {
                reload();
                sender.sendMessage("§aConfiguration des notifications Discord rechargée.");
            } catch (RuntimeException error) {
                sender.sendMessage(
                        "§cConfiguration refusée. Vérifie le fichier ; l’ancien état est conservé."
                                + " L’URL du webhook doit rester secrète.");
            }
        } else if (sub.equals("test")) {
            boolean queued = publish("test", UUID.randomUUID().toString(), Map.of());
            sender.sendMessage(
                    queued
                            ? "§aNotification de test mise en file ; cela ne confirme pas sa"
                                    + " réception par Discord."
                            : "§eNotifications désactivées ou file pleine. Configure"
                                    + " integrations/discord-notifications.yml.");
        } else
            sender.sendMessage(
                    "§bNotifications Discord : "
                            + (relay == null
                                    ? "désactivées"
                                    : "actives • "
                                            + relay.pending()
                                            + " notification(s) en attente")
                            + ". /discordnotif test | reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return s.hasPermission("cliker.discord.admin") && a.length == 1
                ? List.of("statut", "test", "reload").stream()
                        .filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT)))
                        .toList()
                : List.of();
    }

    public void shutdown() {
        if (current == this) current = null;
        if (relay != null) relay.close();
        HandlerList.unregisterAll(this);
    }
}
