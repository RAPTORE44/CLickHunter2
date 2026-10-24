package fr.cliker.clikerv2.mobs;

import fr.cliker.clikerv2.EconomyService;
import fr.cliker.clikerv2.MobTier;
import fr.cliker.clikerv2.Numbers;
import fr.cliker.clikerv2.bag.BagManager;
import fr.cliker.clikerv2.bag.TalismanItem;
import fr.cliker.clikerv2.combat.SwordCommand;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.economy.PermissionBonus;
import fr.cliker.clikerv2.essence.CompressorManager;
import fr.cliker.clikerv2.essence.EssenceItem;
import fr.cliker.clikerv2.essence.EssenceRarity;
import fr.cliker.clikerv2.gear.CharmItem;
import fr.cliker.clikerv2.gear.EnchantType;
import fr.cliker.clikerv2.gear.ItemMods;
import fr.cliker.clikerv2.gear.ReforgeType;
import fr.cliker.clikerv2.gear.RuneType;
import fr.cliker.clikerv2.gear.TierGear;
import fr.cliker.clikerv2.level.LevelDamageBonus;
import fr.cliker.clikerv2.lootbox.LootboxItem;
import fr.cliker.clikerv2.lootbox.LootboxTier;
import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.upgrade.UpgradeManager;
import fr.cliker.clikerv2.upgrade.UpgradeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomMobManager implements Listener {
    public static NamespacedKey MOB_ID_KEY;

    private final JavaPlugin plugin;
    private final double bossHpMultiplier;
    private final MobSpawnStore store;
    private final EconomyService economy;
    private final UpgradeManager upgrades;
    private final MobKillStore killStore;
    private final PrestigeManager prestige;
    private final CompressorManager compressorManager;
    private final BagManager bagManager;
    private final NamespacedKey mobIdKey;
    private final NamespacedKey spawnIdKey;

    private final Map<String, String> spawnMobId = new HashMap<>();
    private final Map<String, Location> spawnLocation = new HashMap<>();
    private final Map<String, List<String>> spawnIdsByMob = new HashMap<>();
    private final Map<String, UUID> aliveBySpawnId = new HashMap<>();
    private final Map<String, BukkitTask> pendingRespawns = new HashMap<>();
    /** PV réels suivis par le plugin (illimités) : l'attribut vanille max_health est plafonné à 1024 par Minecraft, inutilisable au-delà. */
    private final Map<String, Long> currentHealth = new HashMap<>();
    /** Zones marquées "boss" (Golem) : PV x10 et récompenses spéciales à la mort. */
    private final Set<String> bossSpawnIds = new HashSet<>();

    public CustomMobManager(JavaPlugin plugin, MobSpawnStore store, EconomyService economy, UpgradeManager upgrades, MobKillStore killStore, PrestigeManager prestige, CompressorManager compressorManager, BagManager bagManager) {
        this.plugin = plugin;
        java.io.File bossFile = new java.io.File(plugin.getDataFolder(), "boss.yml");
        if (!bossFile.exists()) plugin.saveResource("boss.yml", false);
        bossHpMultiplier = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(bossFile).getDouble("multiplicateur-pv", 100);
        if (!Double.isFinite(bossHpMultiplier) || bossHpMultiplier <= 0) throw new IllegalArgumentException("Multiplicateur boss invalide");
        this.store = store;
        this.economy = economy;
        this.upgrades = upgrades;
        this.killStore = killStore;
        this.prestige = prestige;
        this.compressorManager = compressorManager;
        this.bagManager = bagManager;
        this.mobIdKey = new NamespacedKey(plugin, "cliker_mob_id");
        MOB_ID_KEY = mobIdKey;
        this.spawnIdKey = new NamespacedKey(plugin, "cliker_spawn_id");
    }

    public void startAnchor() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::anchorAll, 1L, 1L);
    }

    private void anchorAll() {
        for (Map.Entry<String, UUID> entry : aliveBySpawnId.entrySet()) {
            Location spawn = spawnLocation.get(entry.getKey());
            Entity entity = plugin.getServer().getEntity(entry.getValue());
            if (spawn == null || !(entity instanceof LivingEntity living) || !living.isValid()) continue;
            living.setVelocity(new Vector(0, 0, 0));
            if (living.getLocation().distanceSquared(spawn) > 0.01) living.teleport(spawn);
        }
    }

    /**
     * Force le chargement des chunks des zones de spawn AVANT de scanner/supprimer les anciens mobs.
     * Sans ça, un reload à chaud (PlugMan) sur un chunk non chargé (zone sans joueur à proximité) ne voit pas
     * les mobs existants via world.getEntities(), donc ne les supprime pas -> nouveaux mobs spawnés en double.
     */
    public void load() {
        store.loadAll().whenComplete((points, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (error != null) { plugin.getLogger().severe("Impossible de charger les zones de spawn : " + error.getMessage()); return; }
            for (MobSpawnStore.SpawnPoint point : points) {
                point.location().getWorld().getChunkAt(point.location()).load();
            }
            // 1 tick de délai après le chargement des chunks : sur Paper les entités d'un chunk qui vient d'être
            // chargé ne sont pas toujours visibles via world.getEntities() dans le même tick, ce qui laissait
            // passer des mobs existants au scan et en spawnait des doublons par-dessus lors d'un reload à chaud.
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (var world : plugin.getServer().getWorlds()) {
                    for (Entity entity : world.getEntities()) {
                        if (entity.getPersistentDataContainer().has(mobIdKey, PersistentDataType.STRING)) entity.remove();
                    }
                }
                for (MobSpawnStore.SpawnPoint point : points) {
                    spawnMobId.put(point.id(), point.mobId());
                    spawnLocation.put(point.id(), point.location());
                    spawnIdsByMob.computeIfAbsent(point.mobId(), key -> new ArrayList<>()).add(point.id());
                    if (point.boss()) bossSpawnIds.add(point.id());
                    spawn(point.id());
                }
            });
        }));
    }

    public void startPeriodicReset() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::resetAll, 18000L, 18000L);
    }

    /**
     * Ignore les zones boss (gérées par BossEventManager). Force le chargement des chunks AVANT le scan de
     * suppression, sinon une zone sans joueur à proximité n'est pas vue par world.getEntities() : l'ancien mob
     * n'est pas supprimé mais spawn() en recrée un quand même par-dessus -> doublons qui s'accumulent à chaque
     * cycle de 15 min (observé : ~96 zones -> plusieurs milliers de mobs après quelques heures).
     */
    private void resetAll() {
        for (Location location : spawnLocation.values()) location.getWorld().getChunkAt(location).load();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (String spawnId : new ArrayList<>(aliveBySpawnId.keySet())) {
                if (!bossSpawnIds.contains(spawnId)) despawn(spawnId);
            }
            for (Map.Entry<String, BukkitTask> entry : new HashMap<>(pendingRespawns).entrySet()) {
                if (bossSpawnIds.contains(entry.getKey())) continue;
                entry.getValue().cancel();
                pendingRespawns.remove(entry.getKey());
            }
            for (var world : plugin.getServer().getWorlds()) {
                for (Entity entity : world.getEntities()) {
                    String spawnId = entity.getPersistentDataContainer().get(spawnIdKey, PersistentDataType.STRING);
                    if (spawnId != null && bossSpawnIds.contains(spawnId)) continue;
                    if (entity.getPersistentDataContainer().has(mobIdKey, PersistentDataType.STRING)) entity.remove();
                }
            }
            int resetCount = 0;
            for (String spawnId : spawnLocation.keySet()) {
                if (bossSpawnIds.contains(spawnId)) continue;
                spawn(spawnId);
                resetCount++;
            }
            plugin.getLogger().info("Reset périodique des mobs custom effectué (" + resetCount + " zones, boss ignorés).");
        });
    }

    public void shutdown() {
        for (String spawnId : new ArrayList<>(aliveBySpawnId.keySet())) despawn(spawnId);
        for (BukkitTask task : pendingRespawns.values()) task.cancel();
        pendingRespawns.clear();
    }

    public void addSpawn(String mobId, Location location) { addSpawn(mobId, location, false); }

    public void addSpawn(String mobId, Location location, boolean boss) {
        String id = UUID.randomUUID().toString();
        spawnMobId.put(id, mobId);
        spawnLocation.put(id, location.clone());
        spawnIdsByMob.computeIfAbsent(mobId, key -> new ArrayList<>()).add(id);
        if (boss) bossSpawnIds.add(id);
        spawn(id);
        store.save(id, mobId, location, boss);
    }

    public boolean removeNearestSpawn(String mobId, Location near) {
        List<String> ids = spawnIdsByMob.get(mobId);
        if (ids == null || ids.isEmpty()) return false;
        String nearest = null;
        double bestDistance = Double.MAX_VALUE;
        for (String id : ids) {
            Location location = spawnLocation.get(id);
            if (location == null || !location.getWorld().equals(near.getWorld())) continue;
            double distance = location.distanceSquared(near);
            if (distance < bestDistance) { bestDistance = distance; nearest = id; }
        }
        if (nearest == null) return false;
        removeSpawnById(mobId, nearest);
        return true;
    }

    public int removeAllSpawns(String mobId) {
        List<String> ids = spawnIdsByMob.remove(mobId);
        if (ids == null || ids.isEmpty()) return 0;
        for (String id : new ArrayList<>(ids)) removeSpawnById(mobId, id);
        return ids.size();
    }

    public int countSpawns(String mobId) { return spawnIdsByMob.getOrDefault(mobId, List.of()).size(); }

    public Set<String> bossSpawnIds() { return bossSpawnIds; }

    /** Refait apparaître (ou fait apparaître pour la première fois) tous les Golems, utilisé par BossEventManager toutes les 30 min. */
    public void triggerBossWave() {
        for (String spawnId : new HashSet<>(bossSpawnIds)) {
            despawn(spawnId);
            spawn(spawnId);
        }
    }

    private void removeSpawnById(String mobId, String id) {
        despawn(id);
        List<String> ids = spawnIdsByMob.get(mobId);
        if (ids != null) { ids.remove(id); if (ids.isEmpty()) spawnIdsByMob.remove(mobId); }
        spawnMobId.remove(id);
        spawnLocation.remove(id);
        bossSpawnIds.remove(id);
        BukkitTask pending = pendingRespawns.remove(id);
        if (pending != null) pending.cancel();
        store.removeById(id);
    }

    private void despawn(String spawnId) {
        UUID uuid = aliveBySpawnId.remove(spawnId);
        currentHealth.remove(spawnId);
        if (uuid == null) return;
        Entity entity = plugin.getServer().getEntity(uuid);
        if (entity != null) entity.remove();
    }

    private void spawn(String spawnId) {
        Location location = spawnLocation.get(spawnId);
        String mobId = spawnMobId.get(spawnId);
        MobTier tier = mobId == null ? null : MobTier.parse(mobId);
        if (location == null || tier == null) return;
        boolean boss = bossSpawnIds.contains(spawnId);
        org.bukkit.entity.EntityType entityType = boss ? org.bukkit.entity.EntityType.IRON_GOLEM : tier.entityType();
        LivingEntity entity = (LivingEntity) location.getWorld().spawnEntity(location, entityType);
        entity.setPersistent(false);
        entity.setRemoveWhenFarAway(false);
        entity.setGravity(false);
        entity.setCollidable(false);
        entity.setAI(false);
        if (entity instanceof Mob mob) mob.setAware(false);
        entity.getPersistentDataContainer().set(mobIdKey, PersistentDataType.STRING, mobId);
        entity.getPersistentDataContainer().set(spawnIdKey, PersistentDataType.STRING, spawnId);
        long maxHp = boss ? bossHealth(tier) : Math.round(tier.maxHealth());
        var maxHealthAttribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHp);
            entity.setHealth(maxHealthAttribute.getValue());
        }
        entity.setCustomNameVisible(true);
        currentHealth.put(spawnId, maxHp);
        updateNameplate(entity, tier, maxHp, maxHp, boss);
        aliveBySpawnId.put(spawnId, entity.getUniqueId());
    }

    /**
     * PV du Golem : x10 le mob normal jusqu'au Wither Squelette, puis paliers explicites bien plus élevés au-delà
     * (le stuff seul, sans upgrades/prestige/enchants, doit prendre largement plus de 10 coups pour le tuer).
     */
    private long bossHealth(MobTier tier) {
        return Math.round(tier.maxHealth() * bossHpMultiplier);
    }

    private void updateNameplate(LivingEntity entity, MobTier tier, long hp, long maxHp, boolean boss) {
        Component label = boss
                ? Component.text("⚔ Golem " + tier.label() + " ⚔ ", NamedTextColor.DARK_RED, TextDecoration.BOLD)
                : Component.text(tier.label() + " ", NamedTextColor.GOLD, TextDecoration.BOLD);
        entity.customName(label.append(Component.text("[" + Numbers.compact(Math.max(0, hp)) + "/" + Numbers.compact(maxHp) + "]", NamedTextColor.RED)));
    }

    /**
     * PV suivis manuellement (l'attribut vanille max_health est plafonné à 1024 par Minecraft, donc inutilisable
     * au-delà de ce seuil). Les dégâts réels appliqués à l'entité vanille restent nuls tant que le coup n'est pas
     * fatal côté PV virtuels ; le coup fatal met la vraie vie à 0 pour déclencher la mort naturellement (drops,
     * récompenses, respawn gérés par onDeath sans duplication de logique).
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        String mobId = victim.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        String spawnId = victim.getPersistentDataContainer().get(spawnIdKey, PersistentDataType.STRING);
        if (mobId == null || spawnId == null) return;
        MobTier tier = MobTier.parse(mobId);
        if (tier == null) return;

        if (!(event.getDamager() instanceof Player player)) { event.setDamage(0.0); return; }

        Long applied = stuffDamage(player);
        if (applied == null) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon.hasItemMeta() && weapon.getItemMeta().getPersistentDataContainer().has(SwordCommand.SWORD_KEY, PersistentDataType.BYTE)) {
                applied = 25L + armorDamage(player);
            }
        }
        if (applied == null) { event.setDamage(0.0); return; }
        applied += (long) upgrades.getBonus(player.getUniqueId(), UpgradeType.FORCE);
        applied += prestige.forceBonus(player.getUniqueId());
        applied += Math.round(charmBonus(player));
        applied = applyWeaponProcs(player, applied);
        double bagBonus = bagManager.mobDamageBonusPercent(player.getUniqueId(), tier);
        if (bagBonus > 0) applied = Math.round(applied * (1 + bagBonus / 100.0));
        applied = LevelDamageBonus.apply(applied, player.getLevel());
        compressorManager.onHit(player);

        boolean boss = bossSpawnIds.contains(spawnId);
        long maxHp = boss ? bossHealth(tier) : Math.round(tier.maxHealth());
        long hpBefore = currentHealth.getOrDefault(spawnId, maxHp);
        long hpAfter = Math.max(0, hpBefore - applied);
        currentHealth.put(spawnId, hpAfter);

        if (hpAfter <= 0) {
            event.setDamage(victim.getHealth());
        } else {
            event.setDamage(0.0);
        }
        updateNameplate(victim, tier, hpAfter, maxHp, boss);
        spawnDamagePopup(victim, applied);
    }

    /**
     * Dégâts du stuff : montant fixe, identique au chiffre affiché en lore, quelle que soit la cible.
     * L'épée porte le tag du tier d'origine (ex. ourse) ; chaque pièce d'armure DU MÊME tier ajoute la même valeur.
     * Épée + 4 pièces assorties = 5x ce montant, appliqué tel quel sur n'importe quel mob custom visé.
     */
    private Long stuffDamage(Player player) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!weapon.hasItemMeta()) return null;
        String gearMobId = weapon.getItemMeta().getPersistentDataContainer().get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        if (gearMobId == null) return null;
        MobTier gearTier = MobTier.parse(gearMobId);
        if (gearTier == null) return null;

        long perPiece = TierGear.pieceDamage(gearTier);
        long damage = perPiece;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (!matchesGear(piece, gearMobId)) continue;
            damage += perPiece;
            damage += Math.round(EnchantType.STRENGTH.magnitudeAt(gearTier, ItemMods.getEnchantLevel(piece, EnchantType.STRENGTH)));
        }
        damage += weaponBonusDamage(weapon, gearTier);
        return damage;
    }

    /** Dégâts de chaque pièce d'armure de tier portée (épée du dresseur = pas de tier à assortir, chaque pièce compte pour elle-même). */
    private long armorDamage(Player player) {
        long damage = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || !piece.hasItemMeta()) continue;
            String tierId = piece.getItemMeta().getPersistentDataContainer().get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
            if (tierId == null) continue;
            MobTier pieceTier = MobTier.parse(tierId);
            if (pieceTier == null) continue;
            damage += TierGear.pieceDamage(pieceTier);
            damage += Math.round(EnchantType.STRENGTH.magnitudeAt(pieceTier, ItemMods.getEnchantLevel(piece, EnchantType.STRENGTH)));
        }
        return damage;
    }

    /** Bonus de dégâts fixes de l'épée : enchants Tranchant/Giant Killer/Force + reforge Féroce + rune Tranchant. */
    private long weaponBonusDamage(ItemStack weapon, MobTier tier) {
        long bonus = Math.round(EnchantType.TRANCHANT.magnitudeAt(tier, ItemMods.getEnchantLevel(weapon, EnchantType.TRANCHANT)));
        bonus += Math.round(EnchantType.GIANT_KILLER.magnitudeAt(tier, ItemMods.getEnchantLevel(weapon, EnchantType.GIANT_KILLER)));
        bonus += Math.round(EnchantType.STRENGTH.magnitudeAt(tier, ItemMods.getEnchantLevel(weapon, EnchantType.STRENGTH)));
        bonus += Math.round(RuneType.TRANCHANT.magnitudeAt(tier, ItemMods.getRuneLevel(weapon, RuneType.TRANCHANT)));
        ReforgeType reforge = ItemMods.getReforge(weapon);
        if (reforge != null) bonus += reforge.swordDamageBonus(tier);
        return bonus;
    }

    /** Procs : vol de vie épée (reforge) + coup double armure (enchant Critique). Retourne les dégâts finaux après proc. */
    private long applyWeaponProcs(Player player, long baseDamage) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!weapon.hasItemMeta()) return baseDamage;
        String gearMobId = weapon.getItemMeta().getPersistentDataContainer().get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        if (gearMobId == null) return baseDamage;
        MobTier tier = MobTier.parse(gearMobId);
        if (tier == null) return baseDamage;

        long finalDamage = baseDamage;

        double critical = armorEnchantBonus(player, EnchantType.CRITICAL);
        if (critical > 0 && ThreadLocalRandom.current().nextDouble(100.0) < critical) finalDamage *= 2;

        double lifesteal = ItemMods.getReforge(weapon) != null ? ItemMods.getReforge(weapon).swordLifestealPercent() : 0;
        if (lifesteal > 0) {
            var maxHealthAttribute = player.getAttribute(Attribute.MAX_HEALTH);
            double max = maxHealthAttribute != null ? maxHealthAttribute.getValue() : player.getHealth();
            player.setHealth(Math.min(max, player.getHealth() + finalDamage * (lifesteal / 100.0)));
        }
        return finalDamage;
    }

    /** Somme les bonus des amulettes portées (n'importe où dans l'inventaire, cumulable entre mobs différents). */
    private double charmBonus(Player player) {
        double bonus = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            MobTier tier = CharmItem.tierOf(stack);
            if (tier != null) bonus += tier.charmDamageBonus();
        }
        return bonus;
    }

    private boolean matchesGear(ItemStack item, String gearMobId) {
        if (item == null || !item.hasItemMeta()) return false;
        String tag = item.getItemMeta().getPersistentDataContainer().get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
        return gearMobId.equals(tag);
    }

    private void spawnDamagePopup(LivingEntity victim, double damage) {
        Location popupLocation = victim.getLocation().add(
                ThreadLocalRandom.current().nextDouble(-0.5, 0.5),
                victim.getHeight() + 0.5,
                ThreadLocalRandom.current().nextDouble(-0.5, 0.5));
        ArmorStand stand = victim.getWorld().spawn(popupLocation, ArmorStand.class, armorStand -> {
            armorStand.setVisible(false);
            armorStand.setGravity(false);
            armorStand.setMarker(true);
            armorStand.setCustomNameVisible(true);
            armorStand.customName(Component.text("-" + Numbers.compact(damage), NamedTextColor.RED, TextDecoration.BOLD));
            armorStand.setPersistent(false);
        });
        plugin.getServer().getScheduler().runTaskLater(plugin, stand::remove, 15L);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        String mobId = victim.getPersistentDataContainer().get(mobIdKey, PersistentDataType.STRING);
        String spawnId = victim.getPersistentDataContainer().get(spawnIdKey, PersistentDataType.STRING);
        if (mobId == null || spawnId == null) return;
        MobTier tier = MobTier.parse(mobId);
        if (tier == null) return;

        event.getDrops().clear();
        event.setDroppedExp(0);
        aliveBySpawnId.remove(spawnId, victim.getUniqueId());
        currentHealth.remove(spawnId);

        boolean boss = bossSpawnIds.remove(spawnId);

        Player killer = victim.getKiller();
        if (killer != null) {
            int amount = ThreadLocalRandom.current().nextInt(1, 4);
            ItemStack essence = EssenceItem.create(tier, amount);
            var leftover = killer.getInventory().addItem(essence);
            leftover.values().forEach(item -> killer.getWorld().dropItemNaturally(killer.getLocation(), item));
            grantRewards(killer, tier);
            rollLootboxDrops(killer);
            rollTalismanDrop(killer, tier);
            killStore.increment(killer.getUniqueId());
            if (boss) grantBossRewards(killer, tier);
        }
        if (boss) {
            if (spawnLocation.containsKey(spawnId)) bossSpawnIds.add(spawnId);
            // Pas de respawn immédiat : les Golems ne réapparaissent qu'à la prochaine vague (BossEventManager, toutes les 30 min).
        } else if (spawnLocation.containsKey(spawnId)) {
            scheduleRespawn(spawnId);
        }
    }

    /** 1 chance sur 500 000 par kill d'obtenir le talisman du mob tué (bonus % de dégâts contre ce mob tant qu'il est dans le /sac). */
    private void rollTalismanDrop(Player killer, MobTier tier) {
        if (ThreadLocalRandom.current().nextInt(500_000) != 0) return;
        ItemStack talisman = TalismanItem.createMobTalisman(tier);
        var leftover = killer.getInventory().addItem(talisman);
        leftover.values().forEach(item -> killer.getWorld().dropItemNaturally(killer.getLocation(), item));
        killer.sendMessage(Component.text("✦✦✦ TALISMAN OBTENU : ", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD)
                .append(Component.text(tier.label() + " Talisman !", NamedTextColor.AQUA)));
    }

    /** Chance de coffre identique pour les 19 mobs custom, roll indépendant par rareté (peut cumuler plusieurs coffres sur un kill chanceux). */
    private void rollLootboxDrops(Player killer) {
        double lootFinderBonus = armorEnchantBonus(killer, EnchantType.LOOT_FINDER);
        for (LootboxTier tier : LootboxTier.values()) {
            if (ThreadLocalRandom.current().nextDouble(100.0) < tier.dropChancePercent() + lootFinderBonus) {
                ItemStack box = LootboxItem.create(tier);
                var leftover = killer.getInventory().addItem(box);
                leftover.values().forEach(item -> killer.getWorld().dropItemNaturally(killer.getLocation(), item));
                killer.sendMessage(Component.text("✦ Coffre obtenu : ", NamedTextColor.YELLOW)
                        .append(Component.text(tier.boxName(), tier.color(), TextDecoration.BOLD)));
            }
        }
    }

    private void grantRewards(Player killer, MobTier tier) {
        int tierIndex = tier.ordinal() + 1;
        UUID uuid = killer.getUniqueId();
        boolean treasureTalisman = bagManager.hasTreasureTalisman(uuid);
        long ecusReward = (10L * tierIndex + (long) upgrades.getBonus(uuid, UpgradeType.RICHESSE) + prestige.ecusBonus(uuid) + weaponRuneBonus(killer, RuneType.ECUS))
                * PermissionBonus.multiplier(killer, "ecus") + (treasureTalisman ? 100L : 0L);
        long gemmesReward = (tierIndex + (long) (upgrades.getBonus(uuid, UpgradeType.FORTUNE) * 10) + prestige.gemmesBonusTenths(uuid) + weaponRuneBonus(killer, RuneType.GEMMES))
                * PermissionBonus.multiplier(killer, "gemmes") + (treasureTalisman ? 10L : 0L);
        int xpReward = (int) ((tierIndex + (int) upgrades.getBonus(uuid, UpgradeType.SAGESSE) + (int) prestige.xpBonus(uuid)
                + (int) weaponRuneBonus(killer, RuneType.XP) + (int) armorRuneBonus(killer, RuneType.XP)
                + (int) weaponEnchantBonus(killer, EnchantType.FIRST_STRIKE))
                * PermissionBonus.multiplier(killer, "xp")) + (treasureTalisman ? 25 : 0);
        long cendresReward = weaponRuneBonus(killer, RuneType.CENDRES);

        economy.change(killer.getUniqueId(), killer.getName(), CurrencyType.ECUS, ecusReward);
        economy.change(killer.getUniqueId(), killer.getName(), CurrencyType.GEMMES_SOMBRES, gemmesReward);
        if (cendresReward > 0) economy.change(killer.getUniqueId(), killer.getName(), CurrencyType.CENDRES, cendresReward);
        killer.giveExp(xpReward);

        Component actionBar = Component.text("+ ", NamedTextColor.GREEN)
                .append(Component.text(ecusReward + " Écus", NamedTextColor.GOLD))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(CurrencyType.GEMMES_SOMBRES.format(gemmesReward) + " Gemmes Sombres", NamedTextColor.DARK_PURPLE))
                .append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                .append(Component.text(xpReward + " XP", NamedTextColor.AQUA));
        if (cendresReward > 0) {
            actionBar = actionBar.append(Component.text(" · ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(cendresReward + " Cendres", NamedTextColor.GRAY));
        }
        killer.sendActionBar(actionBar);
    }

    /**
     * Récompenses en plus des récompenses normales quand la victime est un Golem (boss) : gros lot d'essence Commune
     * garanti + essence de rareté supérieure (Peu Commune tiers 1-7, Rare tiers 8-19), et une grosse fourchette d'Écus/Gemmes.
     */
    private void grantBossRewards(Player killer, MobTier tier) {
        int index = tier.ordinal() + 1;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        /** Les 7 premiers mobs ont des gains de boss réduits de moitié. */
        int gainDivisor = index <= 7 ? 2 : 1;

        int communeAmount = Math.max(1, (50 * index) / gainDivisor);
        EssenceRarity altRarity = index <= 7 ? EssenceRarity.PEU_COMMUNE : EssenceRarity.RARE;
        int altAmount = Math.max(1, (random.nextInt(1, 11) * index) / gainDivisor);
        giveItem(killer, EssenceItem.create(tier, EssenceRarity.COMMUNE, communeAmount));
        giveItem(killer, EssenceItem.create(tier, altRarity, altAmount));

        long ecusReward = ((long) random.nextInt(1000, 5001) * index) / gainDivisor;
        long gemmesReward = ((long) random.nextInt(10, 21) * index) / gainDivisor;
        economy.change(killer.getUniqueId(), killer.getName(), CurrencyType.ECUS, ecusReward);
        economy.change(killer.getUniqueId(), killer.getName(), CurrencyType.GEMMES_SOMBRES, gemmesReward * 10);

        killer.showTitle(net.kyori.adventure.title.Title.title(
                Component.text("⚔ Golem vaincu ! ⚔", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("+" + Numbers.compact(ecusReward) + " Écus · +" + Numbers.compact(gemmesReward) + " Gemmes", NamedTextColor.GOLD)));
    }

    private void giveItem(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        leftover.values().forEach(leftoverItem -> player.getWorld().dropItemNaturally(player.getLocation(), leftoverItem));
    }

    /** Somme les bonus d'une rune d'armure (XP, catégorie BOTH) sur toutes les pièces équipées portant cette rune. */
    private long armorRuneBonus(Player player, RuneType type) {
        long total = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || !piece.hasItemMeta()) continue;
            String tierId = piece.getItemMeta().getPersistentDataContainer().get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
            if (tierId == null) continue;
            MobTier pieceTier = MobTier.parse(tierId);
            if (pieceTier == null) continue;
            total += Math.round(type.magnitudeAt(pieceTier, ItemMods.getRuneLevel(piece, type)));
        }
        return total;
    }

    /** Bonus de la rune d'épée équipée (Tranchant/XP/Écus/Gemmes/Cendres au kill). */
    private long weaponRuneBonus(Player player, RuneType type) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!weapon.hasItemMeta()) return 0;
        String tierId = weapon.getItemMeta().getPersistentDataContainer().get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        if (tierId == null) return 0;
        MobTier tier = MobTier.parse(tierId);
        if (tier == null) return 0;
        return Math.round(type.magnitudeAt(tier, ItemMods.getRuneLevel(weapon, type)));
    }

    /** Bonus de l'enchant d'épée équipée (First Strike au kill, dégâts déjà gérés dans weaponBonusDamage). */
    private long weaponEnchantBonus(Player player, EnchantType type) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (!weapon.hasItemMeta()) return 0;
        String tierId = weapon.getItemMeta().getPersistentDataContainer().get(TierGear.SWORD_KEY, PersistentDataType.STRING);
        if (tierId == null) return 0;
        MobTier tier = MobTier.parse(tierId);
        if (tier == null) return 0;
        return Math.round(type.magnitudeAt(tier, ItemMods.getEnchantLevel(weapon, type)));
    }

    /** Somme le bonus d'un enchant d'armure (Défense/Chercheur de Butin/Critique) sur toutes les pièces équipées. */
    private long armorEnchantBonus(Player player, EnchantType type) {
        long total = 0;
        for (ItemStack piece : player.getInventory().getArmorContents()) {
            if (piece == null || !piece.hasItemMeta()) continue;
            String tierId = piece.getItemMeta().getPersistentDataContainer().get(TierGear.ARMOR_KEY, PersistentDataType.STRING);
            if (tierId == null) continue;
            MobTier pieceTier = MobTier.parse(tierId);
            if (pieceTier == null) continue;
            total += Math.round(type.magnitudeAt(pieceTier, ItemMods.getEnchantLevel(piece, type)));
        }
        return total;
    }

    private void scheduleRespawn(String spawnId) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            pendingRespawns.remove(spawnId);
            spawn(spawnId);
        }, 20L);
        pendingRespawns.put(spawnId, task);
    }
}
