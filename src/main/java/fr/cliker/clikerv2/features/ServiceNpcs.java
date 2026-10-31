package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.buyzone.*;
import fr.cliker.clikerv2.npc.*;
import fr.cliker.clikerv2.rewards.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class ServiceNpcs implements CommandExecutor, TabCompleter {
    private final JavaPlugin plugin;
    private final Menus menus;
    private final ShopStore store;
    private final Boosts boosts;
    private final BuyZoneModule zones;
    private final Settings shop;
    private final PlainGear plain;
    private final Map<String, Settings> configs = new HashMap<>();
    private final Map<String, ZnpcsNpc> npcs = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Set<UUID> browsing = new HashSet<>();
    private boolean stopped;

    public ServiceNpcs(
            JavaPlugin p, Menus menus, DatabaseManager db, Boosts boosts, BuyZoneModule zones) {
        plugin = p;
        this.menus = menus;
        this.plain = new PlainGear(p);
        store = new ShopStore(db);
        this.boosts = boosts;
        this.zones = zones;
        shop = new Settings(p, "boutiques/catalogues.yml");
        boolean added = false;
        for (String type : List.of("gemmes", "cendres"))
            for (int minutes : new int[] {1, 5, 20}) {
                String path = "shop.potions." + type + ".x2." + minutes;
                if (!shop.yaml.contains(path)) {
                    shop.yaml.set(path, 4_000_000L * minutes);
                    added = true;
                }
            }
        if (added) shop.save();
        for (String id : List.of("ah", "blackmarket", "shop")) {
            var settings = new Settings(p, "pnj/" + id + ".yml");
            configs.put(id, settings);
            if (!settings.yaml.contains("pnj.type")) {
                settings.yaml.set("pnj.type", "villager");
                settings.yaml.set("pnj.skin", "");
                settings.yaml.set(
                        "pnj.titre",
                        "&a"
                                + (id.equals("ah")
                                        ? "Hôtel des ventes"
                                        : id.equals("shop") ? "Boutique" : "Marché noir"));
                settings.yaml.set("pnj.sous-titre", "&7Clic droit pour ouvrir");
                settings.save();
            }
            var npc =
                    new ZnpcsNpc(
                            p,
                            settings.yaml,
                            settings::save,
                            player -> {
                                if (id.equals("ah")) player.performCommand("ah");
                                else open(player, id, 0);
                            },
                            "service-" + id,
                            id);
            npcs.put(id, npc);
            npc.start();
        }
        p.getCommand("pnjservice").setExecutor(this);
        p.getCommand("pnjservice").setTabCompleter(this);
    }

    private record Offer(ItemStack item, long price, String key, int cap, MobTier zone) {}

    private List<Offer> offers(String id) {
        var list = new ArrayList<Offer>();
        if (id.equals("blackmarket")) {
            long rotation =
                    System.currentTimeMillis()
                            / Math.max(
                                    60, shop.yaml.getLong("blackmarket.rotation-secondes", 10800))
                            / 1000;
            for (int i = 0; i < 6; i++) {
                String base = "blackmarket.offres." + (i + 1);
                MobTier zone =
                        MobTier.parse(
                                shop.yaml.getString(
                                        base + ".zone",
                                        MobTier.values()[Math.min(i * 3, 18)].id()));
                ItemStack item;
                if (i < 4) {
                    Material egg =
                            Material.matchMaterial(
                                    (zone == null ? MobTier.VACHE : zone).entityType().name()
                                            + "_SPAWN_EGG");
                    if (egg == null) egg = Material.PIG_SPAWN_EGG;
                    item =
                            Menus.item(
                                    egg,
                                    "Œuf — " + (zone == null ? "Zone inconnue" : zone.label()),
                                    "Invoque une créature dans sa zone débloquée.");
                    var meta = item.getItemMeta();
                    meta.getPersistentDataContainer()
                            .set(
                                    new NamespacedKey(plugin, "market_egg"),
                                    PersistentDataType.STRING,
                                    zone == null ? "vache" : zone.id());
                    item.setItemMeta(meta);
                } else item = boosts.potion(i == 4 ? "chance" : "xp", 2, 60);
                list.add(
                        new Offer(
                                item,
                                positive(base + ".prix", 100000L * (i + 1)),
                                "black:" + rotation + ":" + i,
                                5,
                                zone));
            }
            return list;
        }
        int index = 0;
        for (String type : List.of("WOODEN", "STONE", "IRON", "GOLDEN", "DIAMOND", "NETHERITE")) {
            var item =
                    Menus.item(
                            Material.valueOf(type + "_SWORD"),
                            "Épée en " + materialName(type),
                            "Équipement simple : non améliorable.",
                            "+"
                                    + positive(
                                            "shop.degats-epees." + type,
                                            new long[] {25, 50, 100, 150, 250, 500}[index])
                                    + " dégâts");
            var meta = item.getItemMeta();
            meta.getPersistentDataContainer()
                    .set(
                            new NamespacedKey(plugin, "plain_shop_damage"),
                            PersistentDataType.LONG,
                            positive(
                                    "shop.degats-epees." + type,
                                    new long[] {25, 50, 100, 150, 250, 500}[index]));
            item.setItemMeta(meta);
            plain.mark(item);
            list.add(
                    new Offer(
                            item,
                            positive("shop.epees." + type, 1000L * (long) Math.pow(5, index++)),
                            "",
                            0,
                            null));
        }
        index = 0;
        for (String type : List.of("LEATHER", "CHAINMAIL", "IRON", "GOLDEN", "DIAMOND")) {
            for (String part : List.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS"))
                list.add(
                        new Offer(
                                Menus.item(
                                        Material.valueOf(type + "_" + part),
                                        "Armure en " + materialName(type) + " — " + partName(part),
                                        "Équipement simple : non améliorable."),
                                positive("shop.armures." + type, 500L * (long) Math.pow(5, index)),
                                "",
                                0,
                                null));
            index++;
        }
        for (var offer : list) plain.mark(offer.item);
        for (String type :
                List.of("chance", "coins", "xp", "vitesse", "degats", "gemmes", "cendres"))
            for (int factor :
                    type.equals("vitesse")
                            ? new int[] {2, 3, 4}
                            : type.equals("chance") || type.equals("degats")
                                    ? new int[] {2, 3}
                                    : new int[] {2})
                for (int duration : new int[] {60, 300, 1200})
                    list.add(
                            new Offer(
                                    boosts.potion(type, factor, duration),
                                    positive(
                                            "shop.potions."
                                                    + type
                                                    + ".x"
                                                    + factor
                                                    + "."
                                                    + (duration / 60),
                                            1_000_000L * factor * factor * (duration / 60)),
                                    "",
                                    0,
                                    null));
        list.add(
                new Offer(
                        new ItemStack(Material.MILK_BUCKET),
                        positive("shop.lait", 100),
                        "",
                        0,
                        null));
        return list;
    }

    private static String materialName(String name) {
        return switch (name) {
            case "WOODEN" -> "bois";
            case "STONE" -> "pierre";
            case "IRON" -> "fer";
            case "GOLDEN" -> "or";
            case "DIAMOND" -> "diamant";
            case "NETHERITE" -> "netherite";
            case "LEATHER" -> "cuir";
            case "CHAINMAIL" -> "mailles";
            default -> name;
        };
    }

    private static String partName(String name) {
        return switch (name) {
            case "HELMET" -> "casque";
            case "CHESTPLATE" -> "plastron";
            case "LEGGINGS" -> "jambières";
            case "BOOTS" -> "bottes";
            default -> name;
        };
    }

    private long positive(String path, long fallback) {
        long value = shop.yaml.getLong(path, fallback);
        return value > 0 && value <= 1_000_000_000_000_000L ? value : fallback;
    }

    public void open(Player p, String id, int page) {
        if (id.equals("shop")) {
            categories(p);
            return;
        }
        open(p, id, page, "Tous");
    }

    private void categories(Player p) {
        if (stopped || fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) return;
        var view = menus.create(p, 27, "Shop — Catégories");
        view.vanilla = true;
        String[] names = {"Épées", "Armures", "Potions", "Lait", "Tous"};
        Material[] icons = {
            Material.DIAMOND_SWORD,
            Material.DIAMOND_CHESTPLATE,
            Material.POTION,
            Material.MILK_BUCKET,
            Material.CHEST
        };
        for (int i = 0; i < names.length; i++) {
            String category = names[i];
            view.put(
                    11 + i,
                    Menus.item(icons[i], category),
                    (who, r) -> open(who, "shop", 0, category));
        }
        view.show(p);
    }

    private void open(Player p, String id, int page, String category) {
        if (stopped
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                || !browsing.add(p.getUniqueId())) return;
        var original = p.getOpenInventory().getTopInventory();
        store.stocks(
                        id.equals("blackmarket")
                                ? "black:"
                                        + (System.currentTimeMillis()
                                                / Math.max(
                                                        60,
                                                        shop.yaml.getLong(
                                                                "blackmarket.rotation-secondes",
                                                                10800))
                                                / 1000)
                                        + ":"
                                : "unused:")
                .whenComplete(
                        (stocks, error) ->
                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                        plugin,
                                        () -> {
                                            browsing.remove(p.getUniqueId());
                                            if (stopped
                                                    || !fr.cliker.clikerv2.runtime.PlayerSessions
                                                            .isCurrent(p)
                                                    || p.getOpenInventory().getTopInventory()
                                                            != original
                                                    || fr.cliker.clikerv2.outpost.WorldInventories
                                                            .isolated(p)) return;
                                            if (error != null) {
                                                p.sendMessage(
                                                        "Boutique temporairement indisponible.");
                                                return;
                                            }
                                            render(p, id, page, stocks, category);
                                        }));
    }

    private void render(
            Player p, String id, int requested, Map<String, Integer> stocks, String category) {
        var offers =
                offers(id).stream()
                        .filter(
                                o ->
                                        switch (category) {
                                            case "Épées" ->
                                                    o.item.getType().name().endsWith("_SWORD");
                                            case "Armures" ->
                                                    o.item.getType()
                                                            .name()
                                                            .matches(
                                                                    ".*_(HELMET|CHESTPLATE|LEGGINGS|BOOTS)");
                                            case "Potions" -> o.item.getType() == Material.POTION;
                                            case "Lait" -> o.item.getType() == Material.MILK_BUCKET;
                                            default -> true;
                                        })
                        .toList();
        int page = Math.max(0, Math.min(requested, (offers.size() - 1) / 45));
        var view =
                menus.create(
                        p,
                        54,
                        id.equals("blackmarket")
                                ? "Marché noir — stock limité"
                                : "Boutique — " + (page + 1));
        view.merchandise = true;
        view.vanilla = true;
        for (int j = 0; j < 45 && page * 45 + j < offers.size(); j++) {
            Offer o = offers.get(page * 45 + j);
            int slot = id.equals("blackmarket") ? new int[] {11, 13, 15, 29, 31, 33}[j] : j;
            boolean unlocked =
                    id.equals("blackmarket")
                            || o.zone == null
                            || zones != null && zones.canTravel(p, o.zone.id());
            int left = o.cap == 0 ? -1 : Math.max(0, o.cap - stocks.getOrDefault(o.key, 0));
            var item =
                    unlocked && left != 0
                            ? o.item.clone()
                            : Menus.item(
                                    Material.BARRIER, unlocked ? "Épuisé" : "Zone verrouillée");
            var meta = item.getItemMeta();
            if (id.equals("shop") && meta.hasDisplayName()) {
                String material = item.getType().name();
                net.kyori.adventure.text.format.TextColor color =
                        material.startsWith("DIAMOND")
                                ? net.kyori.adventure.text.format.NamedTextColor.AQUA
                                : material.startsWith("GOLDEN")
                                        ? net.kyori.adventure.text.format.NamedTextColor.GOLD
                                        : material.startsWith("NETHERITE")
                                                ? net.kyori.adventure.text.format.TextColor.color(
                                                        0x49434b)
                                                : material.startsWith("LEATHER")
                                                                || material.startsWith("WOODEN")
                                                        ? net.kyori.adventure.text.format.TextColor
                                                                .color(0xa87932)
                                                        : material.startsWith("IRON")
                                                                ? net.kyori.adventure.text.format
                                                                        .NamedTextColor.WHITE
                                                                : material.equals("POTION")
                                                                        ? net.kyori.adventure.text
                                                                                .format
                                                                                .NamedTextColor
                                                                                .LIGHT_PURPLE
                                                                        : net.kyori.adventure.text
                                                                                .format
                                                                                .NamedTextColor
                                                                                .GRAY;
                if (meta instanceof org.bukkit.inventory.meta.PotionMeta potion
                        && potion.hasColor())
                    color =
                            net.kyori.adventure.text.format.TextColor.color(
                                    potion.getColor().asRGB());
                meta.displayName(
                        meta.displayName()
                                .color(color)
                                .decoration(
                                        net.kyori.adventure.text.format.TextDecoration.ITALIC,
                                        false));
            }
            var lore =
                    meta.lore() == null
                            ? new ArrayList<net.kyori.adventure.text.Component>()
                            : new ArrayList<>(meta.lore());
            lore.add(
                    fr.cliker.clikerv2.daily.DailySettings.color(
                            "&7Prix : &e" + fr.cliker.clikerv2.Numbers.compact(o.price) + " Écus"));
            lore.add(
                    fr.cliker.clikerv2.daily.DailySettings.color(
                            "&aClic gauche : acheter cet objet."));
            if (left >= 0)
                lore.add(
                        net.kyori.adventure.text.Component.text(
                                "Stock restant : " + left + " / 5"));
            meta.lore(lore);
            item.setItemMeta(meta);
            view.put(
                    slot,
                    item,
                    (player, right) -> {
                        if (right || !unlocked || left == 0 || !pending.add(player.getUniqueId()))
                            return;
                        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(player)
                                || !id.equals("blackmarket")
                                        && o.zone != null
                                        && (zones == null
                                                || !zones.canTravel(player, o.zone.id()))) {
                            pending.remove(player.getUniqueId());
                            return;
                        }
                        view.pending = true;
                        String source = "shop:" + java.util.UUID.randomUUID();
                        store.buy(
                                        player.getUniqueId(),
                                        player.getName(),
                                        o.key,
                                        o.cap,
                                        o.price,
                                        ClaimService.serialize(o.item),
                                        source)
                                .whenComplete(
                                        (ok, e) ->
                                                fr.cliker.clikerv2.runtime.ServerTasks.run(
                                                        plugin,
                                                        () -> {
                                                            pending.remove(player.getUniqueId());
                                                            view.pending = false;
                                                            if (stopped) return;
                                                            if (e != null)
                                                                plugin.getLogger()
                                                                        .log(
                                                                                java.util.logging
                                                                                        .Level
                                                                                        .SEVERE,
                                                                                "Achat boutique"
                                                                                    + " impossible",
                                                                                e);
                                                            if (fr.cliker.clikerv2.runtime
                                                                    .PlayerSessions.isCurrent(
                                                                    player)) {
                                                                player.sendMessage(
                                                                        e != null
                                                                                ? "Achat"
                                                                                      + " indisponible,"
                                                                                      + " aucun"
                                                                                      + " achat"
                                                                                      + " confirmé."
                                                                                : Boolean.TRUE
                                                                                                .equals(
                                                                                                        ok)
                                                                                        ? "Achat"
                                                                                              + " validé."
                                                                                        : "Fonds"
                                                                                              + " insuffisants"
                                                                                              + " ou stock"
                                                                                              + " épuisé.");
                                                                if (Boolean.TRUE.equals(ok))
                                                                    Bukkit.getServicesManager()
                                                                            .load(
                                                                                    ClaimService
                                                                                            .class)
                                                                            .deliverPurchase(
                                                                                    player
                                                                                            .getUniqueId(),
                                                                                    source);
                                                                if (player.getOpenInventory()
                                                                                .getTopInventory()
                                                                        == view.getInventory())
                                                                    open(
                                                                            player, id, page,
                                                                            category);
                                                            }
                                                        }));
                    });
        }
        if (!id.equals("blackmarket")) {
            view.put(
                    45,
                    Menus.item(Material.ARROW, "Page précédente"),
                    (player, r) -> open(player, id, page - 1, category));
            view.put(
                    53,
                    Menus.item(Material.ARROW, "Page suivante"),
                    (player, r) -> open(player, id, page + 1, category));
        }
        view.put(
                49, Menus.item(Material.BARRIER, "Fermer"), (player, r) -> player.closeInventory());
        if (id.equals("shop"))
            view.put(48, Menus.item(Material.BOOK, "Catégories"), (who, r) -> categories(who));
        view.show(p);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p) || !p.hasPermission("cliker.services.admin")) {
            sender.sendMessage("Permission administrateur requise.");
            return true;
        }
        if (args.length < 2 || !npcs.containsKey(args[1].toLowerCase(Locale.ROOT))) {
            p.sendMessage(
                    "/pnjservice spawn|remove <ah|shop|blackmarket> ou /pnjservice skin <type>"
                            + " <pseudo|villager>");
            return true;
        }
        String id = args[1].toLowerCase(Locale.ROOT);
        var npc = npcs.get(id);
        try {
            if (args[0].equalsIgnoreCase("spawn")) {
                var at = p.getLocation().clone();
                at.setYaw(90);
                at.setPitch(0);
                p.sendMessage(npc.spawn(at) ? "PNJ créé vers l’ouest." : "Ce PNJ existe déjà.");
            } else if (args[0].equalsIgnoreCase("remove")) {
                npc.remove();
                p.sendMessage("PNJ supprimé.");
            } else if (args[0].equalsIgnoreCase("skin") && args.length == 3) {
                if (!args[2].equalsIgnoreCase("villager")
                        && !args[2].matches("[A-Za-z0-9_]{1,16}")) {
                    p.sendMessage("Indique un pseudo Minecraft valide ou villager.");
                    return true;
                }
                var config = configs.get(id);
                var at = fr.cliker.clikerv2.config.YamlFiles.location(config.yaml, "pnj.position");
                npc.remove();
                config.yaml.set(
                        "pnj.type", args[2].equalsIgnoreCase("villager") ? "villager" : "player");
                config.yaml.set("pnj.skin", args[2].equalsIgnoreCase("villager") ? "" : args[2]);
                config.save();
                if (at != null) npc.spawn(at);
                p.sendMessage("Apparence sauvegardée.");
            }
        } catch (Exception e) {
            plugin.getLogger()
                    .log(java.util.logging.Level.SEVERE, "Modification PNJ impossible", e);
            p.sendMessage("Modification impossible, consulte les journaux.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        if (!s.hasPermission("cliker.services.admin")) return List.of();
        return (args.length == 1
                        ? List.of("spawn", "remove", "skin")
                        : args.length == 2
                                ? List.of("ah", "shop", "blackmarket")
                                : List.<String>of())
                .stream()
                        .filter(v -> v.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)))
                        .toList();
    }

    public void shutdown() {
        stopped = true;
        npcs.values().forEach(ZnpcsNpc::shutdown);
    }
}
