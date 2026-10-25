package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.*;
import fr.cliker.clikerv2.buyzone.*;
import fr.cliker.clikerv2.mobs.*;

import org.bukkit.plugin.java.JavaPlugin;

public final class ExpansionModule {
    private final JavaPlugin plugin;
    private final DatabaseManager db;
    private final EconomyService economy;
    private final BuyZoneModule zones;
    private final CustomMobManager mobs;
    private final fr.cliker.clikerv2.bag.BagManager bags;
    private Talismans talismans;
    private PartyRaids raids;
    private Boosts boosts;
    private ServiceNpcs npcs;
    private TravelAndTools travel;
    private ChatActivities chat;

    public ExpansionModule(
            JavaPlugin p,
            DatabaseManager db,
            EconomyService economy,
            BuyZoneModule zones,
            CustomMobManager mobs,
            fr.cliker.clikerv2.bag.BagManager bags) {
        this.bags = bags;
        plugin = p;
        this.db = db;
        this.economy = economy;
        this.zones = zones;
        this.mobs = mobs;
    }

    public void start() {
        var menus = new Menus(plugin);
        new AdminDiagnostic(plugin, db, mobs, menus);
        chat = new ChatActivities(plugin);
        boosts = new Boosts(plugin);
        travel = new TravelAndTools(plugin, menus, zones, mobs);
        npcs = new ServiceNpcs(plugin, menus, db, boosts, zones);
        talismans = new Talismans(plugin, db, bags, menus);
        raids = new PartyRaids(plugin, db, mobs, zones, menus);
    }

    public void shutdown() {
        if (chat != null) chat.shutdown();
        if (raids != null) raids.shutdown();
        if (talismans != null) talismans.shutdown();
        if (npcs != null) npcs.shutdown();
        if (travel != null) travel.shutdown();
        if (boosts != null) boosts.shutdown();
    }
}
