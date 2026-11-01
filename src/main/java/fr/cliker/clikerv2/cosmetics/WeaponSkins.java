package fr.cliker.clikerv2.cosmetics;

import fr.cliker.clikerv2.features.*;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class WeaponSkins implements Listener, CommandExecutor {
    private static WeaponSkins current;

    public static boolean shouldDrop() {
        return current != null
                && java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                        < Math.max(
                                0,
                                Math.min(1, current.settings.yaml.getDouble("chance-drop", 0.01)));
    }

    public static ItemStack drop() {
        return current == null ? null : current.parchment("normal");
    }

    private final JavaPlugin plugin;
    private final Settings settings;
    private final Menus menus;
    private final NamespacedKey scroll, collection;
    private List<List<Material>> pools =
            List.of(
                    List.of(
                            Material.STONE,
                            Material.DIRT,
                            Material.OAK_PLANKS,
                            Material.COBBLESTONE,
                            Material.SAND),
                    List.of(
                            Material.COPPER_BLOCK,
                            Material.AMETHYST_BLOCK,
                            Material.PRISMARINE,
                            Material.MOSS_BLOCK),
                    List.of(
                            Material.LAPIS_BLOCK,
                            Material.DIAMOND_BLOCK,
                            Material.EMERALD_BLOCK,
                            Material.CRYING_OBSIDIAN),
                    List.of(Material.GOLD_BLOCK, Material.PURPUR_BLOCK, Material.QUARTZ_BLOCK),
                    List.of(Material.NETHERITE_BLOCK, Material.BEACON, Material.DRAGON_EGG),
                    List.of(Material.SEA_LANTERN, Material.END_STONE),
                    List.of(Material.PUMPKIN, Material.SNOW_BLOCK));
    private final List<String> tiers =
            List.of("Commun", "Peu commun", "Rare", "Épique", "Légendaire", "Spécial", "Événement");

    public WeaponSkins(JavaPlugin p) {
        plugin = p;
        settings = new Settings(p, "cosmetiques/skins.yml");
        var configured = new ArrayList<List<Material>>();
        for (int n = 0; n < pools.size(); n++) {
            String key = "tiers." + n + ".blocs";
            if (!settings.yaml.contains(key))
                settings.yaml.set(key, pools.get(n).stream().map(Material::name).toList());
            var values =
                    settings.yaml.getStringList(key).stream()
                            .map(Material::matchMaterial)
                            .filter(java.util.Objects::nonNull)
                            .filter(m -> m.isBlock() && m.isItem())
                            .distinct()
                            .toList();
            configured.add(values.isEmpty() ? pools.get(n) : values);
        }
        pools = List.copyOf(configured);
        settings.save();
        current = this;
        menus = new Menus(p);
        scroll = new NamespacedKey(p, "skin_scroll");
        collection = new NamespacedKey(p, "weapon_skins");
        p.getCommand("skin").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, p);
    }

    private Set<String> unlocked(Player p) {
        String s =
                p.getPersistentDataContainer()
                        .getOrDefault(collection, PersistentDataType.STRING, "");
        return new HashSet<>(Arrays.asList(s.split(",")));
    }

    private ItemStack parchment(String type) {
        var i =
                Menus.item(
                        Material.PAPER,
                        "&dParchemin de skin — " + type,
                        "&7Pose ce parchemin sur une épée dans ton inventaire.",
                        "&7Résultat aléatoire ; les doublons sont possibles.",
                        "&7Le skin ne modifie jamais les dégâts.");
        var m = i.getItemMeta();
        m.getPersistentDataContainer().set(scroll, PersistentDataType.STRING, type);
        i.setItemMeta(m);
        return i;
    }

    private void apply(ItemStack sword, Material model) {
        var meta = sword.getItemMeta();
        meta.setItemModel(model.getKey());
        sword.setItemMeta(meta);
    }

    private void menu(Player p, int tier) {
        var v =
                menus.create(
                        p, 54, tier < 0 ? "Collection des skins" : "Skins — " + tiers.get(tier));
        if (tier < 0) {
            for (int i = 0; i < 7; i++) {
                int selected = i;
                v.put(
                        10 + i,
                        Menus.item(pools.get(i).getFirst(), "&b" + tiers.get(i)),
                        (who, right) -> menu(who, selected));
            }
        } else {
            Set<String> owned = unlocked(p);
            for (int i = 0; i < pools.get(tier).size(); i++) {
                Material model = pools.get(tier).get(i);
                String id = tier + ":" + model.name();
                boolean known = owned.contains(id);
                v.put(
                        10 + i,
                        Menus.item(
                                known ? model : Material.BARRIER,
                                known
                                        ? "&a" + model.name().replace('_', ' ')
                                        : "&7Skin non découvert",
                                "&7" + tiers.get(tier),
                                known
                                        ? "&eClique pour équiper sur l’épée tenue."
                                        : "&7À découvrir avec un parchemin."),
                        (who, right) -> {
                            if (known
                                    && fr.cliker.clikerv2.combat.CombatReach.sword(
                                            who.getInventory().getItemInMainHand())) {
                                apply(who.getInventory().getItemInMainHand(), model);
                                who.saveData();
                                who.sendMessage("§aSkin équipé ; dégâts conservés.");
                            }
                        });
            }
            v.put(49, Menus.item(Material.ARROW, "&eRetour"), (who, right) -> menu(who, -1));
        }
        v.show(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)
                || e.getClickedInventory() != p.getInventory()
                || fr.cliker.clikerv2.outpost.WorldInventories.isolated(p)) return;
        var cursor = e.getCursor();
        if (cursor == null
                || !cursor.hasItemMeta()
                || !cursor.getItemMeta()
                        .getPersistentDataContainer()
                        .has(scroll, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        var weapon = e.getCurrentItem();
        if (!fr.cliker.clikerv2.combat.CombatReach.sword(weapon)) return;
        var delivery =
                Bukkit.getServicesManager().load(fr.cliker.clikerv2.rewards.ClaimService.class);
        if (delivery != null && delivery.isDelivering(p.getUniqueId())) return;
        String kind =
                cursor.getItemMeta()
                        .getPersistentDataContainer()
                        .get(scroll, PersistentDataType.STRING);
        int tier = kind.equals("special") ? 5 : kind.equals("evenement") ? 6 : rollTier();
        var pool = pools.get(tier);
        Material model =
                pool.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(pool.size()));
        var replacement = weapon.clone();
        apply(replacement, model);
        var owned = unlocked(p);
        boolean fresh = owned.add(tier + ":" + model.name());
        p.getPersistentDataContainer()
                .set(collection, PersistentDataType.STRING, String.join(",", owned));
        e.setCurrentItem(replacement);
        cursor.setAmount(cursor.getAmount() - 1);
        e.setCursor(cursor);
        p.saveData();
        p.sendMessage(
                "§bSkin "
                        + tiers.get(tier)
                        + " : "
                        + model.name().replace('_', ' ')
                        + (fresh ? " §a— découvert !" : " §7— déjà découvert."));
    }

    private int rollTier() {
        int n = java.util.concurrent.ThreadLocalRandom.current().nextInt(100);
        return n < 55 ? 0 : n < 80 ? 1 : n < 93 ? 2 : n < 99 ? 3 : 4;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("donner")) {
            if (!s.hasPermission("cliker.skin.admin")) return true;
            Player p = a.length > 1 ? Bukkit.getPlayerExact(a[1]) : null;
            if (p == null) {
                s.sendMessage("/skin donner <joueur> [quantité] [normal|special|evenement]");
                return true;
            }
            int amount = 1;
            try {
                if (a.length > 2) amount = Math.max(1, Math.min(64, Integer.parseInt(a[2])));
            } catch (NumberFormatException e) {
                return true;
            }
            String type = a.length > 3 ? a[3].toLowerCase(Locale.ROOT) : "normal";
            if (!Set.of("normal", "special", "evenement").contains(type)) return true;
            var i = parchment(type);
            i.setAmount(amount);
            fr.cliker.clikerv2.rewards.ItemDelivery.give(p, i);
            return true;
        }
        if (s instanceof Player p && !fr.cliker.clikerv2.outpost.WorldInventories.isolated(p))
            menu(p, -1);
        return true;
    }

    public void shutdown() {
        if (current == this) current = null;
        HandlerList.unregisterAll(this);
    }
}
