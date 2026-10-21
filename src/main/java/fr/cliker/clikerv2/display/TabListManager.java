package fr.cliker.clikerv2.display;

import fr.cliker.clikerv2.prestige.PrestigeManager;
import fr.cliker.clikerv2.prestige.PrestigeType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class TabListManager implements Listener {
    private static final Component HEADER = Component.newline()
            .append(Component.text("Northblue Cliker", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
            .append(Component.newline());
    private static final Component FOOTER = Component.newline()
            .append(Component.text("play.northblue.fr", NamedTextColor.AQUA))
            .append(Component.newline());

    private final PrestigeManager prestige;

    public TabListManager(PrestigeManager prestige) {
        this.prestige = prestige;
    }

    public void apply(Player player) {
        player.sendPlayerListHeaderAndFooter(HEADER, FOOTER);
        player.playerListName(buildTabName(player));
    }

    public Component buildTabName(Player player) {
        Component prefix = RankPrefix.of(player);
        int level = prestige.getLevel(player.getUniqueId());
        Component prestigeTag = level > 0
                ? Component.text("[", NamedTextColor.DARK_GRAY)
                        .append(Component.text(PrestigeType.toRoman(level), NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                : Component.empty();
        return prefix.append(prestigeTag).append(Component.text(player.getName(), NamedTextColor.WHITE));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }
}
