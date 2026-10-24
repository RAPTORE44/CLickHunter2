package fr.cliker.clikerv2.daily;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class DailyMenu implements Listener {
    private final DailyModule module;
    private final Map<UUID, Holder> openMenus = new HashMap<>();
    public DailyMenu(DailyModule module) { this.module = module; }

    public static int slot(int day) {
        if (day < 1 || day > DailyProgress.DAYS) throw new IllegalArgumentException("Jour invalide");
        return (1 + (day - 1) / 7) * 9 + 1 + (day - 1) % 7;
    }
    public static int day(int slot) {
        for (int day = 1; day <= DailyProgress.DAYS; day++) if (slot(day) == slot) return day;
        return -1;
    }
    public void open(Player player, DailyProgress progress) {
        Holder holder = new Holder(player.getUniqueId(), progress);
        holder.inventory = Bukkit.createInventory(holder, 54, DailySettings.color(module.settings().config().getString("interface.titre", "Calendrier de Récompenses")));
        var header = module.settings().icon("bandeau", Map.of());
        var background = module.settings().icon("fond", Map.of());
        for (int slot = 0; slot < 54; slot++) holder.inventory.setItem(slot, slot < 9 ? header : background);
        org.bukkit.inventory.ItemStack close = new org.bukkit.inventory.ItemStack(org.bukkit.Material.BARRIER);
        var meta = close.getItemMeta();
        meta.displayName(DailySettings.color("&cFermer").decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        close.setItemMeta(meta);
        holder.inventory.setItem(49, close);
        render(holder);
        player.openInventory(holder.inventory);
        if (holder(player) == holder) openMenus.put(player.getUniqueId(), holder);
    }
    public void update(Player player, DailyProgress progress) {
        if (holder(player) instanceof Holder holder && holder.owner.equals(player.getUniqueId())) {
            holder.progress = progress;
            render(holder);
        }
    }
    public void refreshOpenMenus() {
        var iterator = openMenus.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline() || holder(player) != entry.getValue()) iterator.remove();
            else render(entry.getValue());
        }
    }
    private void render(Holder holder) {
        ZonedDateTime now = module.now();
        DailySettings settings = module.settings();
        Map<String, String> info = new HashMap<>();
        info.put("jour", Integer.toString(holder.progress.currentDay(now.toLocalDate())));
        info.put("etat", settings.message(holder.progress.claimedToday(now.toLocalDate()) ? "etat-recupere" : "etat-disponible"));
        info.put("temps", duration(Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.getZone()))));
        setIcon(holder, 4, "informations", info);
        for (int day = 1; day <= DailyProgress.DAYS; day++) {
            DailyProgress.Status status = holder.progress.status(day, now.toLocalDate());
            String icon = switch (status) { case AVAILABLE -> "disponible"; case LOCKED -> "verrouille"; case CLAIMED -> "recupere"; };
            Map<String, String> values = Map.of("jour", Integer.toString(day), "recompense", settings.reward(day),
                    "temps", duration(holder.progress.untilUnlock(day, now)), "etat", settings.message("etat-" + icon));
            setIcon(holder, slot(day), icon, values);
        }
    }
    private void setIcon(Holder holder, int slot, String icon, Map<String, String> values) {
        Map<String, String> key = new HashMap<>(values);
        key.put("$icon", icon);
        if (key.equals(holder.rendered.get(slot))) return;
        holder.inventory.setItem(slot, module.settings().icon(icon, values));
        holder.rendered.put(slot, key);
    }
    public static String duration(Duration duration) {
        long seconds = Math.max(0, duration.getSeconds());
        return (seconds / 86400 > 0 ? seconds / 86400 + "j " : "") + twoDigits((seconds % 86400) / 3600) + "h " + twoDigits((seconds % 3600) / 60) + "m " + twoDigits(seconds % 60) + "s";
    }
    private static String twoDigits(long value) { return value < 10 ? "0" + value : Long.toString(value); }
    @EventHandler public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); // Y compris shift-click, raccourcis clavier, double-clic et inventaire du bas.
        if (!(event.getWhoClicked() instanceof Player player) || !holder.owner.equals(player.getUniqueId())) return;
        if (!event.isLeftClick() && !event.isRightClick()) return;
        if (event.getRawSlot() == 49) {
            Bukkit.getScheduler().runTask(module.plugin(), () -> { if (holder(player) == holder) player.closeInventory(); });
            return;
        }
        int day = day(event.getRawSlot());
        if (day < 0) return;
        DailyProgress.Status status = holder.progress.status(day, module.now().toLocalDate());
        if (status == DailyProgress.Status.AVAILABLE) module.claim(player, day);
        else player.sendMessage(module.settings().message(status == DailyProgress.Status.CLAIMED ? "deja-recupere" : "verrouille", Map.of("temps", duration(holder.progress.untilUnlock(day, module.now())))));
    }
    @EventHandler public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
    @EventHandler public void close(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof Holder holder) {
            openMenus.remove(holder.owner, holder);
            module.cancelOpen(event.getPlayer().getUniqueId());
        }
    }
    public void shutdown() {
        for (Player player : module.plugin().getServer().getOnlinePlayers()) {
            if (holder(player) instanceof Holder) player.closeInventory();
        }
        openMenus.clear();
    }
    private static InventoryHolder holder(Player player) {
        Inventory top = player.getOpenInventory().getTopInventory();
        return top == null ? null : top.getHolder();
    }
    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private DailyProgress progress;
        private Inventory inventory;
        private final Map<Integer, Map<String, String>> rendered = new HashMap<>();
        private Holder(UUID owner, DailyProgress progress) { this.owner = owner; this.progress = progress; }
        @Override public Inventory getInventory() { return inventory; }
    }
}
