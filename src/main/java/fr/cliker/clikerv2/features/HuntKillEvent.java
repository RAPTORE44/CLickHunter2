package fr.cliker.clikerv2.features;

import fr.cliker.clikerv2.MobTier;

import org.bukkit.event.*;

import java.util.UUID;

/** Émis sur le thread serveur après validation SQL du kill, hors rejouements. */
public final class HuntKillEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    public final UUID owner;
    public final String name;
    public final MobTier tier;
    public final boolean boss;

    public HuntKillEvent(UUID owner, String name, MobTier tier, boolean boss) {
        this.owner = owner;
        this.name = name;
        this.tier = tier;
        this.boss = boss;
    }

    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
