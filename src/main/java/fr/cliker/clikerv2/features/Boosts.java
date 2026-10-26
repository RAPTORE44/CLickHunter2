package fr.cliker.clikerv2.features;

import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public final class Boosts implements Listener {
    private static final List<String> TYPES =
            List.of("chance", "coins", "xp", "vitesse", "degats", "gemmes", "cendres");
    private final JavaPlugin plugin;
    private final Settings palette;
    private final NamespacedKey kind, factor, seconds, speed;
    private BukkitTask task;

    public Boosts(JavaPlugin plugin) {
        this.plugin = plugin;
        palette = new Settings(plugin, "boutiques/potions.yml");
        Bukkit.getServicesManager()
                .register(Boosts.class, this, plugin, org.bukkit.plugin.ServicePriority.Normal);
        kind = new NamespacedKey(plugin, "boost_kind");
        factor = new NamespacedKey(plugin, "boost_factor");
        seconds = new NamespacedKey(plugin, "boost_seconds");
        speed = new NamespacedKey(plugin, "boost_speed");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                plugin,
                                () -> Bukkit.getOnlinePlayers().forEach(this::speed),
                                20,
                                20);
    }

    public static double multiplier(Player p, String type) {
        var service = Bukkit.getServicesManager().load(Boosts.class);
        var key =
                service == null
                        ? NamespacedKey.fromString("clikerv2:boost_" + type + "_expiry")
                        : new NamespacedKey(service.plugin, "boost_" + type + "_expiry");
        var amount =
                service == null
                        ? NamespacedKey.fromString("clikerv2:boost_" + type + "_value")
                        : new NamespacedKey(service.plugin, "boost_" + type + "_value");
        int value =
                p.getPersistentDataContainer().getOrDefault(amount, PersistentDataType.INTEGER, 1);
        return p.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L)
                                > System.currentTimeMillis()
                        && value >= 2
                        && value <= 4
                ? value
                : 1;
    }

    private static String label(String type) {
        return switch (type) {
            case "coins" -> "ÉCUS";
            case "gemmes" -> "GEMMES";
            case "cendres" -> "CENDRES";
            case "degats" -> "DÉGÂTS";
            default -> type.toUpperCase(Locale.ROOT);
        };
    }

    public static fr.cliker.clikerv2.hunting.Gains rewards(
            Player player, fr.cliker.clikerv2.hunting.Gains base) {
        if (player == null) return base;
        return fr.cliker.clikerv2.hunting.Gains.of(
                Math.round(
                        base.amount(fr.cliker.clikerv2.economy.CurrencyType.ECUS)
                                * multiplier(player, "coins")
                                * (1 + Talismans.globalBonus(player, "richesse") / 100)),
                Math.round(
                        base.amount(fr.cliker.clikerv2.economy.CurrencyType.GEMMES_SOMBRES)
                                * multiplier(player, "gemmes")),
                Math.round(
                        base.amount(fr.cliker.clikerv2.economy.CurrencyType.CENDRES)
                                * multiplier(player, "cendres")),
                Math.round(
                        base.xp()
                                * multiplier(player, "xp")
                                * (1 + Talismans.globalBonus(player, "sagesse") / 100)));
    }

    public ItemStack potion(String type, int value, int duration) {
        var i =
                Menus.item(
                        Material.POTION,
                        label(type) + " X" + value + " — " + duration / 60 + " min",
                        "Clic droit pour activer.",
                        "Même effet : durée cumulée ; seul le multiplicateur le plus fort est"
                                + " actif.");
        var m = i.getItemMeta();
        ((org.bukkit.inventory.meta.PotionMeta) m).setColor(color(type));
        m.setMaxStackSize(64);
        m.getPersistentDataContainer().set(kind, PersistentDataType.STRING, type);
        m.getPersistentDataContainer().set(factor, PersistentDataType.INTEGER, value);
        m.getPersistentDataContainer().set(seconds, PersistentDataType.INTEGER, duration);
        i.setItemMeta(m);
        return i;
    }

    private Color color(String type) {
        int fallback =
                switch (type) {
                    case "xp" -> 0x3498DB;
                    case "gemmes" -> 0x8E44AD;
                    case "coins" -> 0xF1C40F;
                    case "degats" -> 0xE74C3C;
                    case "vitesse" -> 0x1ABC9C;
                    case "chance" -> 0x2ECC71;
                    case "cendres" -> 0xE67E22;
                    default -> 0xFFFFFF;
                };
        String value =
                palette.yaml
                        .getString("couleurs." + type, Integer.toHexString(fallback))
                        .replace("#", "");
        try {
            if (!value.matches("[0-9a-fA-F]{6}")) return Color.fromRGB(fallback);
            return Color.fromRGB(Integer.parseInt(value, 16));
        } catch (IllegalArgumentException invalid) {
            return Color.fromRGB(fallback);
        }
    }

    public static ItemStack restyle(ItemStack item) {
        var service = Bukkit.getServicesManager().load(Boosts.class);
        if (service == null
                || item == null
                || item.getType() != Material.POTION
                || !item.hasItemMeta()) return item;
        var meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        String type =
                meta.getPersistentDataContainer().get(service.kind, PersistentDataType.STRING);
        if (type != null) {
            Color color = service.color(type);
            if (!color.equals(meta.getColor())) {
                meta.setColor(color);
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void open(org.bukkit.event.inventory.InventoryOpenEvent event) {
        for (int i = 0; i < event.getInventory().getSize(); i++) {
            var item = event.getInventory().getItem(i);
            if (item != null && item.getType() == Material.POTION)
                event.getInventory().setItem(i, restyle(item));
        }
    }

    @EventHandler
    public void join(org.bukkit.event.player.PlayerJoinEvent event) {
        var p = event.getPlayer();
        for (int i = 0; i < p.getInventory().getSize(); i++) {
            var item = p.getInventory().getItem(i);
            if (item != null && item.getType() == Material.POTION)
                p.getInventory().setItem(i, restyle(item));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND
                || !e.getAction().isRightClick()
                || e.getItem() == null) return;
        var i = e.getItem();
        var m = i.getItemMeta();
        if (m == null) return;
        String type = m.getPersistentDataContainer().get(kind, PersistentDataType.STRING);
        if (type == null) return;
        e.setCancelled(true);
        var p = e.getPlayer();
        if (fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) return;
        int
                value =
                        m.getPersistentDataContainer()
                                .getOrDefault(factor, PersistentDataType.INTEGER, 0),
                duration =
                        m.getPersistentDataContainer()
                                .getOrDefault(seconds, PersistentDataType.INTEGER, 0);
        if (!TYPES.contains(type) || value < 2 || value > 4 || duration < 1 || duration > 1200)
            return;
        double current = multiplier(p, type);
        if (current > value) {
            p.sendMessage("Un bonus plus puissant est déjà actif. Potion conservée.");
            return;
        }
        var expiry = new NamespacedKey(plugin, "boost_" + type + "_expiry");
        long from =
                current == value
                        ? Math.max(
                                System.currentTimeMillis(),
                                p.getPersistentDataContainer()
                                        .getOrDefault(expiry, PersistentDataType.LONG, 0L))
                        : System.currentTimeMillis();
        p.getPersistentDataContainer()
                .set(expiry, PersistentDataType.LONG, Math.addExact(from, duration * 1000L));
        p.getPersistentDataContainer()
                .set(
                        new NamespacedKey(plugin, "boost_" + type + "_value"),
                        PersistentDataType.INTEGER,
                        value);
        i.setAmount(i.getAmount() - 1);
        p.getInventory().setItemInMainHand(i.getAmount() == 0 ? null : i);
        speed(p);
        p.sendMessage(
                "Bonus " + label(type) + " X" + value + " activé pour " + duration / 60 + " min.");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void milk(PlayerItemConsumeEvent e) {
        if (e.getItem().getType() != Material.MILK_BUCKET) return;
        for (String type : TYPES) {
            e.getPlayer()
                    .getPersistentDataContainer()
                    .remove(new NamespacedKey(plugin, "boost_" + type + "_expiry"));
            e.getPlayer()
                    .getPersistentDataContainer()
                    .remove(new NamespacedKey(plugin, "boost_" + type + "_value"));
        }
        speed(e.getPlayer());
    }

    private void speed(Player p) {
        var a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        double mult =
                fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)
                        ? 1
                        : multiplier(p, "vitesse");
        var old =
                a.getModifiers().stream()
                        .filter(m -> m.getKey().equals(speed))
                        .findFirst()
                        .orElse(null);
        if (old != null && old.getAmount() == mult - 1) return;
        if (old != null) a.removeModifier(old);
        if (mult > 1)
            a.addModifier(
                    new AttributeModifier(
                            speed, mult - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    public void shutdown() {
        task.cancel();
        Bukkit.getServicesManager().unregister(Boosts.class, this);
        for (var p : Bukkit.getOnlinePlayers()) {
            var a = p.getAttribute(Attribute.MOVEMENT_SPEED);
            if (a != null
                    && a.getModifiers().stream()
                                    .filter(m -> m.getKey().equals(speed))
                                    .findFirst()
                                    .orElse(null)
                            != null) a.removeModifier(speed);
        }
        HandlerList.unregisterAll(this);
    }
}
