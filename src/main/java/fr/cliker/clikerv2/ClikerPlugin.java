package fr.cliker.clikerv2;

import fr.cliker.clikerv2.auction.AuctionCommand;
import fr.cliker.clikerv2.auction.AuctionManager;
import fr.cliker.clikerv2.auction.AuctionMenu;
import fr.cliker.clikerv2.auction.AuctionStore;
import fr.cliker.clikerv2.bag.BagCommand;
import fr.cliker.clikerv2.bag.BagManager;
import fr.cliker.clikerv2.bag.BagMenu;
import fr.cliker.clikerv2.bag.BagStore;
import fr.cliker.clikerv2.bag.TalismanCommand;
import fr.cliker.clikerv2.bag.TalismanItem;
import fr.cliker.clikerv2.combat.AutoClickManager;
import fr.cliker.clikerv2.combat.DeathLogFilter;
import fr.cliker.clikerv2.combat.StarterSwordListener;
import fr.cliker.clikerv2.combat.SwordCommand;
import fr.cliker.clikerv2.cooldown.CooldownCommand;
import fr.cliker.clikerv2.display.MoneyScoreboard;
import fr.cliker.clikerv2.display.StatsCommand;
import fr.cliker.clikerv2.display.StatsMenu;
import fr.cliker.clikerv2.display.TabListManager;
import fr.cliker.clikerv2.economy.CurrencyCommand;
import fr.cliker.clikerv2.economy.CurrencyType;
import fr.cliker.clikerv2.economy.ExpCommand;
import fr.cliker.clikerv2.essence.CompressorCommand;
import fr.cliker.clikerv2.essence.CompressorItem;
import fr.cliker.clikerv2.essence.CompressorManager;
import fr.cliker.clikerv2.essence.EssenceItem;
import fr.cliker.clikerv2.essence.SellCommand;
import fr.cliker.clikerv2.essence.SellMenu;
import fr.cliker.clikerv2.gear.CharmCommand;
import fr.cliker.clikerv2.gear.CharmItem;
import fr.cliker.clikerv2.gear.EnchantCommand;
import fr.cliker.clikerv2.gear.EnchantMenu;
import fr.cliker.clikerv2.gear.GearAttributeManager;
import fr.cliker.clikerv2.gear.ItemMods;
import fr.cliker.clikerv2.gear.ReforgeCommand;
import fr.cliker.clikerv2.gear.ReforgeMenu;
import fr.cliker.clikerv2.gear.RuneCommand;
import fr.cliker.clikerv2.gear.RuneMenu;
import fr.cliker.clikerv2.gear.TierGear;
import fr.cliker.clikerv2.gear.TierGearCommand;
import fr.cliker.clikerv2.hologram.HologramCommand;
import fr.cliker.clikerv2.hologram.HologramManager;
import fr.cliker.clikerv2.hologram.HologramStore;
import fr.cliker.clikerv2.level.LevelUpListener;
import fr.cliker.clikerv2.level.PlayerLevelStore;
import fr.cliker.clikerv2.lootbox.LootboxCommand;
import fr.cliker.clikerv2.lootbox.LootboxItem;
import fr.cliker.clikerv2.lootbox.LootboxManager;
import fr.cliker.clikerv2.merchant.MerchantCommand;
import fr.cliker.clikerv2.merchant.MerchantManager;
import fr.cliker.clikerv2.merchant.MerchantMenu;
import fr.cliker.clikerv2.merchant.MerchantStore;
import fr.cliker.clikerv2.mobs.BossEventManager;
import fr.cliker.clikerv2.mobs.CustomMobManager;
import fr.cliker.clikerv2.mobs.MobKillStore;
import fr.cliker.clikerv2.mobs.MobKillsCommand;
import fr.cliker.clikerv2.mobs.MobSpawnCommand;
import fr.cliker.clikerv2.mobs.MobSpawnStore;
import fr.cliker.clikerv2.prestige.PrestigeChatFormatter;
import fr.cliker.clikerv2.prestige.PrestigeCommand;
import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.prestige.PrestigeMenu;
import fr.cliker.clikerv2.prestige.PrestigeStore;
import fr.cliker.clikerv2.spawn.RespawnListener;
import fr.cliker.clikerv2.spawn.SetSpawnCommand;
import fr.cliker.clikerv2.spawn.SpawnCommand;
import fr.cliker.clikerv2.spawn.SpawnManager;
import fr.cliker.clikerv2.spawn.SpawnStore;
import fr.cliker.clikerv2.station.StationCommand;
import fr.cliker.clikerv2.station.StationManager;
import fr.cliker.clikerv2.station.StationStore;
import fr.cliker.clikerv2.tuto.TutoCommand;
import fr.cliker.clikerv2.tuto.TutoJoinListener;
import fr.cliker.clikerv2.upgrade.UpgradeCommand;
import fr.cliker.clikerv2.upgrade.UpgradeManager;
import fr.cliker.clikerv2.upgrade.UpgradeMenu;
import fr.cliker.clikerv2.upgrade.UpgradeStore;

import org.bukkit.command.CommandExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.Map;

public final class ClikerPlugin extends JavaPlugin implements org.bukkit.event.Listener {
    private fr.cliker.clikerv2.hunting.RewardLedger rewardLedger;
    private fr.cliker.clikerv2.hunting.HuntingModule huntingModule;
    private fr.cliker.clikerv2.raid.RaidModule raidModule;
    private fr.cliker.clikerv2.outpost.OutpostModule outpostModule;
    private fr.cliker.clikerv2.hunting.HuntUI activityUI;
    private AutoClickManager autoClickManager;
    private GearAttributeManager gearAttributeManager;
    private BossEventManager bossEventManager;
    private DatabaseManager database;
    private EconomyService economy;
    private CustomMobManager mobManager;
    private BagMenu bagMenu;
    private fr.cliker.clikerv2.rewards.ClaimService claims;
    private fr.cliker.clikerv2.referral.ReferralModule referrals;
    private fr.cliker.clikerv2.discord.DiscordModule discordModule;
    private MoneyScoreboard scoreboard;
    private fr.cliker.clikerv2.ui.MainMenu mainMenu;
    private fr.cliker.clikerv2.daily.DailyModule dailyModule;
    private fr.cliker.clikerv2.buyzone.BuyZoneModule buyZoneModule;
    private PrestigeManager prestigeManager;
    private MerchantManager merchantManager;
    private fr.cliker.clikerv2.ui.MenuIcons menuIcons;
    private fr.cliker.clikerv2.comfort.PlayerComfort playerComfort;
    private fr.cliker.clikerv2.comfort.FarmTelemetry farmTelemetry;
    private fr.cliker.clikerv2.comfort.ProgressNavigation progressNavigation;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        fr.cliker.clikerv2.display.RankPrefix.initialize();
        DeathLogFilter.install();
        try {
            database =
                    new DatabaseManager(
                            this, getConfig().getString("database-file", "data/economy.db"));
            database.initialize();
        } catch (SQLException | RuntimeException exception) {
            getLogger().severe("Impossible d'initialiser SQLite : " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Map<CurrencyType, Long> startingBalances = new EnumMap<>(CurrencyType.class);
        for (CurrencyType currency : CurrencyType.values()) {
            startingBalances.put(
                    currency,
                    Math.max(0L, getConfig().getLong("starting-balances." + currency.key(), 0L)));
        }
        economy = new EconomyService(database, startingBalances);
        getServer().getPluginManager().registerEvents(this, this);
        getServer()
                .getPluginManager()
                .registerEvents(new fr.cliker.clikerv2.mobs.VanillaSpawnListener(this), this);
        for (var player : getServer().getOnlinePlayers()) initializeAccount(player);
        claims = new fr.cliker.clikerv2.rewards.ClaimService(this, database);
        claims.start();
        rewardLedger = new fr.cliker.clikerv2.hunting.RewardLedger(this, database);
        activityUI = new fr.cliker.clikerv2.hunting.HuntUI(this);
        new fr.cliker.clikerv2.ui.MenuNavigation(this);
        registerCurrencyCommand("ecus", CurrencyType.ECUS);
        registerCurrencyCommand("gemmes", CurrencyType.GEMMES_SOMBRES);
        registerCurrencyCommand("cendres", CurrencyType.CENDRES);
        if (getCommand("xp") != null) {
            ExpCommand expCommand = new ExpCommand();
            setExecutor("xp", expCommand);
            getCommand("xp").setTabCompleter(expCommand);
        }

        MobKillStore killStore = new MobKillStore(database);
        if (getCommand("mobkills") != null) {
            MobKillsCommand mobKillsCommand = new MobKillsCommand(this, killStore);
            setExecutor("mobkills", mobKillsCommand);
            getCommand("mobkills").setTabCompleter(mobKillsCommand);
        }

        PrestigeStore prestigeStore = new PrestigeStore(database);
        prestigeManager = new PrestigeManager(this, prestigeStore, killStore);
        getServer().getPluginManager().registerEvents(prestigeManager, this);
        getServer()
                .getPluginManager()
                .registerEvents(new PrestigeChatFormatter(prestigeManager), this);

        TabListManager tabListManager = new TabListManager(prestigeManager);
        if (getConfig().getBoolean("displays.tab.enabled", false))
            getServer().getPluginManager().registerEvents(tabListManager, this);
        if (getConfig().getBoolean("displays.tab.enabled", false))
            prestigeManager.setTabListManager(tabListManager);
        prestigeManager.loadOnlinePlayers();

        PrestigeMenu prestigeMenu =
                new PrestigeMenu(this, prestigeManager, killStore, tabListManager);
        getServer().getPluginManager().registerEvents(prestigeMenu, this);
        setExecutor("prestige", new PrestigeCommand(prestigeMenu));

        try {
            MoneyScoreboard.installStyle(this);
            scoreboard = new MoneyScoreboard(this, economy, killStore, prestigeManager);
            if (getConfig().getBoolean("displays.scoreboard.enabled", false)) {
                getServer().getPluginManager().registerEvents(scoreboard, this);
                scoreboard.start();
            }
        } catch (IllegalArgumentException | IllegalStateException error) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Scoreboard indisponible : vérifier scoreboard.yml",
                            error);
        }

        UpgradeStore upgradeStore = new UpgradeStore(database);
        UpgradeManager upgradeManager = new UpgradeManager(this, upgradeStore, economy);
        getServer().getPluginManager().registerEvents(upgradeManager, this);
        UpgradeMenu upgradeMenu = new UpgradeMenu(this, upgradeManager);
        getServer().getPluginManager().registerEvents(upgradeMenu, this);
        setExecutor("upgrade", new UpgradeCommand(upgradeMenu));
        upgradeManager.loadOnlinePlayers();
        setExecutor("guide", new fr.cliker.clikerv2.comfort.UpgradeGuide(this, upgradeManager));

        CompressorManager compressorManager = new CompressorManager();
        getServer().getPluginManager().registerEvents(compressorManager, this);

        TalismanItem.init(this);
        BagStore bagStore = new BagStore(database);
        BagManager bagManager = new BagManager(this, bagStore);
        getServer().getPluginManager().registerEvents(bagManager, this);
        bagMenu = new BagMenu(this, bagManager);
        getServer().getPluginManager().registerEvents(bagMenu, this);
        setExecutor("sac", new BagCommand(bagMenu));
        bagManager.loadOnlinePlayers();
        TalismanCommand talismanCommand = new TalismanCommand(this);
        setExecutor("talisman", talismanCommand);
        if (getCommand("talisman") != null) getCommand("talisman").setTabCompleter(talismanCommand);

        MobSpawnStore mobSpawnStore = new MobSpawnStore(database);
        mobManager =
                new CustomMobManager(
                        this,
                        mobSpawnStore,
                        economy,
                        upgradeManager,
                        killStore,
                        prestigeManager,
                        compressorManager,
                        bagManager);
        var huntingStore = new fr.cliker.clikerv2.hunting.HuntingStore(rewardLedger);
        mobManager.setHunting(huntingStore, rewardLedger);
        try {
            huntingModule =
                    new fr.cliker.clikerv2.hunting.HuntingModule(
                            this, huntingStore, rewardLedger, mobManager);
            huntingModule.start();
        } catch (Exception error) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Module de chasse indisponible : vérifier chasse.yml",
                            error);
            if (huntingModule != null) huntingModule.shutdown();
            unavailable("bestiaire");
            unavailable("contrats");
        }
        try {
            raidModule = new fr.cliker.clikerv2.raid.RaidModule(this, rewardLedger, mobManager);
            raidModule.start();
        } catch (Exception error) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Raid indisponible : vérifier raid.yml",
                            error);
            if (raidModule != null) raidModule.shutdown();
            unavailable("raid");
        }
        try {
            outpostModule = new fr.cliker.clikerv2.outpost.OutpostModule(this, database, claims);
            outpostModule.start();
        } catch (Exception error) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Avant-postes indisponibles : vérifier avantpostes.yml",
                            error);
            if (activityUI != null) activityUI.shutdown();
            if (outpostModule != null) outpostModule.shutdown();
            unavailable("avantposte");
        }
        getServer().getPluginManager().registerEvents(mobManager, this);
        getServer().getScheduler().runTaskLater(this, mobManager::load, 20L);
        mobManager.startAnchor();
        mobManager.startPeriodicReset();
        if (getCommand("mobspawn") != null) {
            MobSpawnCommand mobSpawnCommand = new MobSpawnCommand(mobManager);
            setExecutor("mobspawn", mobSpawnCommand);
            getCommand("mobspawn").setTabCompleter(mobSpawnCommand);
        }
        bossEventManager = new BossEventManager(this, mobManager);
        getServer().getPluginManager().registerEvents(bossEventManager, this);
        bossEventManager.start();

        setExecutor("epee", new SwordCommand(this));
        getServer().getPluginManager().registerEvents(new StarterSwordListener(), this);
        TierGear.init(this);
        if (getCommand("stuff") != null) {
            TierGearCommand stuffCommand = new TierGearCommand();
            setExecutor("stuff", stuffCommand);
            getCommand("stuff").setTabCompleter(stuffCommand);
        }

        ItemMods.init(this);
        gearAttributeManager = new GearAttributeManager(this);
        getServer().getPluginManager().registerEvents(gearAttributeManager, this);
        gearAttributeManager.start();

        EnchantMenu enchantMenu = new EnchantMenu(this, economy);
        getServer().getPluginManager().registerEvents(enchantMenu, this);
        setExecutor("enchant", new EnchantCommand(enchantMenu));

        ReforgeMenu reforgeMenu = new ReforgeMenu(this, economy);
        getServer().getPluginManager().registerEvents(reforgeMenu, this);
        setExecutor("reforge", new ReforgeCommand(reforgeMenu));

        RuneMenu runeMenu = new RuneMenu(this, economy);
        getServer().getPluginManager().registerEvents(runeMenu, this);
        setExecutor("runes", new RuneCommand(runeMenu));

        StationStore stationStore = new StationStore(database);
        StationManager stationManager =
                new StationManager(this, stationStore, enchantMenu, reforgeMenu, runeMenu);
        getServer().getPluginManager().registerEvents(stationManager, this);
        getServer().getScheduler().runTaskLater(this, stationManager::load, 20L);
        StationCommand stationCommand = new StationCommand(stationManager);
        setExecutor("station", stationCommand);
        if (getCommand("station") != null) getCommand("station").setTabCompleter(stationCommand);

        EssenceItem.init(this);
        SellMenu sellMenu = new SellMenu(this, economy);
        getServer().getPluginManager().registerEvents(sellMenu, this);
        setExecutor("sell", new SellCommand(sellMenu));

        PlayerLevelStore levelStore = new PlayerLevelStore(database);
        getServer().getPluginManager().registerEvents(new LevelUpListener(levelStore), this);

        CharmItem.init(this);
        if (getCommand("charm") != null) {
            CharmCommand charmCommand = new CharmCommand();
            setExecutor("charm", charmCommand);
            getCommand("charm").setTabCompleter(charmCommand);
        }

        CompressorItem.init(this);
        if (getCommand("compresseur") != null) {
            CompressorCommand compressorCommand = new CompressorCommand();
            setExecutor("compresseur", compressorCommand);
            getCommand("compresseur").setTabCompleter(compressorCommand);
        }

        MerchantMenu merchantMenu = new MerchantMenu(this, economy);
        getServer().getPluginManager().registerEvents(merchantMenu, this);
        MerchantStore merchantStore = new MerchantStore(database);
        merchantManager = new MerchantManager(this, merchantStore, merchantMenu);
        getServer().getPluginManager().registerEvents(merchantManager, this);
        getServer().getScheduler().runTaskLater(this, merchantManager::load, 20L);
        if (getCommand("pnj") != null) {
            MerchantCommand merchantCommand = new MerchantCommand(merchantManager);
            setExecutor("pnj", merchantCommand);
            getCommand("pnj").setTabCompleter(merchantCommand);
        }

        fr.cliker.clikerv2.lootbox.LootboxTier.load(this);
        LootboxItem.init(this);
        getServer()
                .getPluginManager()
                .registerEvents(new LootboxManager(this, rewardLedger, claims), this);
        if (getCommand("coffre") != null) {
            LootboxCommand lootboxCommand = new LootboxCommand(this);
            setExecutor("coffre", lootboxCommand);
            getCommand("coffre").setTabCompleter(lootboxCommand);
        }

        autoClickManager = new AutoClickManager(this, upgradeManager);
        getServer().getPluginManager().registerEvents(autoClickManager, this);
        autoClickManager.start();
        setExecutor("autoclick", autoClickManager);

        HologramStore hologramStore = new HologramStore(database);
        HologramManager hologramManager =
                new HologramManager(this, hologramStore, levelStore, killStore, economy);
        getServer().getScheduler().runTaskLater(this, hologramManager::load, 20L);
        hologramManager.start();
        if (getCommand("holotop") != null) {
            HologramCommand hologramCommand = new HologramCommand(hologramManager);
            setExecutor("holotop", hologramCommand);
            getCommand("holotop").setTabCompleter(hologramCommand);
        }
        if (!hologramManager.isAvailable())
            getLogger().warning("DecentHolograms introuvable : /holotop desactive.");
        bossEventManager.setHologramManager(hologramManager);

        AuctionStore auctionStore = new AuctionStore(database);
        AuctionManager auctionManager = new AuctionManager(this, auctionStore, economy);
        getServer().getPluginManager().registerEvents(auctionManager, this);
        auctionManager.load();
        auctionManager.start();
        AuctionMenu auctionMenu = new AuctionMenu(this, auctionManager);
        getServer().getPluginManager().registerEvents(auctionMenu, this);
        setExecutor("ah", new AuctionCommand(auctionMenu));

        SpawnStore spawnStore = new SpawnStore(database);
        SpawnManager spawnManager = new SpawnManager(this, spawnStore);
        getServer().getScheduler().runTaskLater(this, spawnManager::load, 20L);
        setExecutor("spawn", new SpawnCommand(this, spawnManager));
        setExecutor("setspawn", new SetSpawnCommand(this, spawnManager));
        getServer().getPluginManager().registerEvents(new RespawnListener(spawnManager), this);

        getServer().getPluginManager().registerEvents(new TutoJoinListener(this, levelStore), this);
        setExecutor("tuto", new TutoCommand());

        StatsMenu statsMenu =
                new StatsMenu(this, economy, upgradeManager, killStore, prestigeManager);
        getServer().getPluginManager().registerEvents(statsMenu, this);
        setExecutor("stats", new StatsCommand(statsMenu));

        try {
            dailyModule = new fr.cliker.clikerv2.daily.DailyModule(this);
            dailyModule.start();
            getLogger().info("Module de récompenses journalières NorthBlue actif.");
        } catch (Exception exception) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Impossible de démarrer le module daily",
                            exception);
            if (dailyModule != null) dailyModule.shutdown();
            dailyModule = null;
            if (getCommand("daily") != null)
                getCommand("daily")
                        .setExecutor(
                                (sender, command, label, args) -> {
                                    sender.sendMessage(
                                            net.kyori.adventure.text.Component.text(
                                                    "Le calendrier est indisponible. Contacte un"
                                                            + " administrateur.",
                                                    net.kyori.adventure.text.format.NamedTextColor
                                                            .RED));
                                    return true;
                                });
        }
        try {
            buyZoneModule = new fr.cliker.clikerv2.buyzone.BuyZoneModule(this, economy, database);
            buyZoneModule.start();
            getLogger().info("Module buyzone actif.");
        } catch (Exception exception) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Impossible de démarrer le module buyzone",
                            exception);
            if (buyZoneModule != null) buyZoneModule.shutdown();
            buyZoneModule = null;
        }
        getServer()
                .getPluginManager()
                .registerEvents(new fr.cliker.clikerv2.security.SystemEggProtection(), this);
        try {
            mainMenu = new fr.cliker.clikerv2.ui.MainMenu(this);
            getServer().getPluginManager().registerEvents(mainMenu, this);
            getCommand("menu").setExecutor(mainMenu);
        } catch (IllegalArgumentException error) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Configuration du menu principal invalide",
                            error);
            getCommand("menu")
                    .setExecutor(
                            (sender, command, label, args) -> {
                                sender.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                "Le menu est indisponible. Contacte un"
                                                        + " administrateur.",
                                                net.kyori.adventure.text.format.NamedTextColor
                                                        .RED));
                                return true;
                            });
        }
        try {
            discordModule = new fr.cliker.clikerv2.discord.DiscordModule(this);
            discordModule.start();
        } catch (Exception error) {
            getLogger().log(java.util.logging.Level.SEVERE, "Module Discord indisponible", error);
            if (discordModule != null) discordModule.shutdown();
        }
        try {
            referrals = new fr.cliker.clikerv2.referral.ReferralModule(this, database, claims);
            referrals.start();
        } catch (IllegalArgumentException | IllegalStateException error) {
            getLogger()
                    .log(
                            java.util.logging.Level.SEVERE,
                            "Configuration du parrainage invalide : vérifier parrainage.yml",
                            error);
            if (referrals != null) referrals.shutdown();
            referrals = null;
            getCommand("parrain")
                    .setExecutor(
                            (sender, command, label, args) -> {
                                sender.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                "Le parrainage est temporairement indisponible."
                                                        + " Contacte un administrateur.",
                                                net.kyori.adventure.text.format.NamedTextColor
                                                        .RED));
                                return true;
                            });
        }
        playerComfort = new fr.cliker.clikerv2.comfort.PlayerComfort(this);
        playerComfort.start();
        farmTelemetry = new fr.cliker.clikerv2.comfort.FarmTelemetry(this);
        farmTelemetry.start();
        progressNavigation =
                new fr.cliker.clikerv2.comfort.ProgressNavigation(this, buyZoneModule, mobManager);
        progressNavigation.start();
        try {
            menuIcons = new fr.cliker.clikerv2.ui.MenuIcons(this);
            menuIcons.start();
        } catch (RuntimeException error) {
            menuIcons = null;
            getLogger()
                    .log(
                            java.util.logging.Level.WARNING,
                            "Icônes personnalisées désactivées ; les menus vanilla restent"
                                    + " disponibles.",
                            error);
        }
        getLogger().info("Economie active avec SQLite.");
    }

    @Override
    public void onDisable() {
        if (merchantManager != null) merchantManager.shutdown();
        if (menuIcons != null) menuIcons.shutdown();
        if (prestigeManager != null) prestigeManager.shutdown();
        if (playerComfort != null) playerComfort.shutdown();
        if (farmTelemetry != null) farmTelemetry.shutdown();
        if (progressNavigation != null) progressNavigation.shutdown();
        // Restituer les objets déposés dans les interfaces avant d'arrêter /claim et SQLite.
        for (var player : getServer().getOnlinePlayers()) {
            var holder = player.getOpenInventory().getTopInventory().getHolder();
            if (holder != null && holder.getClass().getName().startsWith("fr.cliker.clikerv2."))
                player.closeInventory();
        }
        if (activityUI != null) activityUI.shutdown();
        if (outpostModule != null) outpostModule.shutdown();
        if (raidModule != null) raidModule.shutdown();
        if (huntingModule != null) huntingModule.shutdown();
        if (rewardLedger != null) rewardLedger.shutdown();
        if (referrals != null) referrals.shutdown();
        if (discordModule != null) discordModule.shutdown();
        if (scoreboard != null) scoreboard.shutdown();
        if (mainMenu != null) mainMenu.shutdown();
        if (buyZoneModule != null) buyZoneModule.shutdown();
        if (dailyModule != null) dailyModule.shutdown();
        if (bagMenu != null) bagMenu.shutdown();
        if (autoClickManager != null) autoClickManager.shutdown();
        if (bossEventManager != null) bossEventManager.shutdown();
        if (gearAttributeManager != null) gearAttributeManager.shutdown();
        if (mobManager != null) mobManager.shutdown();
        if (claims != null) claims.shutdown();
        if (database != null) database.close();
    }

    @org.bukkit.event.EventHandler
    public void accountJoined(org.bukkit.event.player.PlayerJoinEvent event) {
        initializeAccount(event.getPlayer());
    }

    private void initializeAccount(org.bukkit.entity.Player player) {
        var owner = player.getUniqueId();
        economy.getBalance(owner, player.getName(), CurrencyType.ECUS)
                .exceptionally(
                        error -> {
                            getLogger()
                                    .log(
                                            java.util.logging.Level.SEVERE,
                                            "Chargement du compte économique impossible : " + owner,
                                            error);
                            return null;
                        });
    }

    private void unavailable(String command) {
        if (getCommand(command) != null)
            getCommand(command)
                    .setExecutor(
                            (sender, cmd, label, args) -> {
                                sender.sendMessage(
                                        net.kyori.adventure.text.Component.text(
                                                "Cette activité est temporairement indisponible."
                                                        + " Contacte un administrateur.",
                                                net.kyori.adventure.text.format.NamedTextColor
                                                        .RED));
                                return true;
                            });
    }

    private void registerCurrencyCommand(String name, CurrencyType currency) {
        if (getCommand(name) == null) return;
        CurrencyCommand executor = new CurrencyCommand(this, economy, currency);
        setExecutor(name, executor);
        getCommand(name).setTabCompleter(executor);
    }

    /**
     * Enregistre l'exécuteur d'une commande avec un délai de 3s entre deux usages par le même
     * joueur.
     */
    private void setExecutor(String name, CommandExecutor executor) {
        if (getCommand(name) != null) getCommand(name).setExecutor(new CooldownCommand(executor));
    }
}
