package fr.cliker.clikerv2.liveauction;

import fr.cliker.clikerv2.config.YamlFiles;
import fr.cliker.clikerv2.features.Settings;
import fr.cliker.clikerv2.rewards.ClaimService;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.Consumer;

/** Présentoir visuel sans objet ramassable et liste des présences distincte des offres fermes. */
final class AuctionRoom implements Listener {
    private final JavaPlugin plugin;
    private final Settings settings;
    private final Consumer<Player> open, leave;
    private final NamespacedKey marker;
    final Set<UUID> present = new HashSet<>();
    private ItemDisplay item;
    private Interaction interaction;
    private String shown = "";
    private Chunk chunk;

    AuctionRoom(
            JavaPlugin plugin, Settings settings, Consumer<Player> open, Consumer<Player> leave) {
        this.plugin = plugin;
        this.settings = settings;
        this.open = open;
        this.leave = leave;
        marker = new NamespacedKey(plugin, "auction_presentoir");
    }

    void start() {
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) cleanup(e);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void cleanup(Entity e) {
        if (e.getPersistentDataContainer().has(marker)) e.remove();
    }

    boolean configured() {
        return YamlFiles.location(settings.yaml, "salle") != null;
    }

    boolean enter(Player p) {
        Location at = YamlFiles.location(settings.yaml, "salle");
        if (at == null) {
            p.sendMessage("§cLe staff doit définir la salle avec /enchere setsalle.");
            return false;
        }
        if (!(p.getWorld().equals(at.getWorld()) && p.getLocation().distanceSquared(at) < 1)
                && !fr.cliker.clikerv2.features.TravelAndTools.safeTeleport(p, at)) return false;
        present.add(p.getUniqueId());
        return true;
    }

    boolean inside(Player p) {
        Location at = YamlFiles.location(settings.yaml, "salle");
        double radius = Math.max(3, Math.min(100, settings.yaml.getDouble("salle-rayon", 16)));
        return at != null
                && at.getWorld().equals(p.getWorld())
                && Math.abs(at.getY() - p.getY()) < 12
                && Math.abs(at.getX() - p.getX()) <= radius
                && Math.abs(at.getZ() - p.getZ()) <= radius;
    }

    void remove(Player p, boolean spawn) {
        present.remove(p.getUniqueId());
        if (spawn) {
            p.closeInventory();
            p.performCommand("spawn");
        }
    }

    void update(LiveAuctionStore.State state, long seconds) {
        for (UUID owner : new ArrayList<>(present)) {
            Player p = Bukkit.getPlayer(owner);
            if (p == null) {
                present.remove(owner);
                continue;
            }
            if (!inside(p)) {
                present.remove(owner);
                leave.accept(p);
                continue;
            }
            if (state == null || state.phase().equals("DONE")) {
                leave.accept(p);
                continue;
            }
            p.sendActionBar(
                    fr.cliker.clikerv2.daily.DailySettings.color(
                            state.phase().equals("REGISTERING")
                                    ? "&6Enchères &8• &fDébut dans &e" + seconds + "s"
                                    : "&6Lot "
                                            + (state.lot() + 1)
                                            + "/3 &8• &fFin dans &e"
                                            + seconds
                                            + "s &8• &e"
                                            + fr.cliker.clikerv2.Numbers.compact(state.price())
                                            + " Écus"));
        }
        if (state == null || state.phase().equals("DONE")) {
            clear();
            return;
        }
        String key = state.id() + ":" + state.phase() + ":" + state.lot();
        if (key.equals(shown)
                && item != null
                && item.isValid()
                && interaction != null
                && interaction.isValid()) return;
        clear();
        Location at = YamlFiles.location(settings.yaml, "presentoir");
        if (at == null) {
            at = YamlFiles.location(settings.yaml, "salle");
            if (at != null) at.add(0, 1.5, 3);
        }
        if (at == null) return;
        chunk = at.getChunk();
        chunk.addPluginChunkTicket(plugin);
        shown = key;
        int lot = state.phase().equals("REGISTERING") ? 0 : state.lot();
        item =
                at.getWorld()
                        .spawn(
                                at,
                                ItemDisplay.class,
                                e -> {
                                    e.setItemStack(
                                            ClaimService.deserialize(state.items().get(lot)));
                                    e.setPersistent(false);
                                    e.setInvulnerable(true);
                                    e.setBillboard(Display.Billboard.CENTER);
                                    e.getPersistentDataContainer()
                                            .set(marker, PersistentDataType.BYTE, (byte) 1);
                                });
        interaction =
                at.getWorld()
                        .spawn(
                                at.clone().add(0, -.5, 0),
                                Interaction.class,
                                e -> {
                                    e.setInteractionWidth(1.4f);
                                    e.setInteractionHeight(1.5f);
                                    e.setPersistent(false);
                                    e.getPersistentDataContainer()
                                            .set(marker, PersistentDataType.BYTE, (byte) 1);
                                });
        var holograms = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (holograms != null && holograms.available())
            holograms.create(
                    "auction-presentoir",
                    at.clone().add(0, 1.4, 0),
                    List.of("&6&lLot " + (lot + 1) + "/3", "&fClic droit : enchérir"),
                    null,
                    false);
    }

    @EventHandler
    public void interact(PlayerInteractEntityEvent e) {
        if (interaction == null
                || !interaction.getUniqueId().equals(e.getRightClicked().getUniqueId())) return;
        e.setCancelled(true);
        if (e.getHand() == org.bukkit.inventory.EquipmentSlot.HAND
                && present.contains(e.getPlayer().getUniqueId())) open.accept(e.getPlayer());
    }

    @EventHandler
    public void damage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(marker)
                || e.getEntity() instanceof Player p
                        && present.contains(p.getUniqueId())
                        && inside(p)) e.setCancelled(true);
    }

    @EventHandler
    public void loaded(EntitiesLoadEvent e) {
        for (Entity entity : e.getEntities())
            if (entity != item && entity != interaction) cleanup(entity);
    }

    void clear() {
        if (item != null) item.remove();
        if (interaction != null) interaction.remove();
        item = null;
        interaction = null;
        shown = "";
        if (chunk != null) chunk.removePluginChunkTicket(plugin);
        chunk = null;
        var holograms = fr.cliker.clikerv2.hologram.SharedHolograms.service();
        if (holograms != null) holograms.remove("auction-presentoir");
    }

    void shutdown() {
        clear();
        present.clear();
        HandlerList.unregisterAll(this);
    }
}
