package fr.cliker.clikerv2.prestige;

import fr.cliker.clikerv2.display.RankPrefix;

import io.papermc.paper.event.player.AsyncChatEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PrestigeChatFormatter implements Listener {
    private final java.util.function.Function<
                    java.util.UUID, fr.cliker.clikerv2.social.CommunityExtras.Style>
            style;
    private final PrestigeManager prestige;
    private final java.util.function.Function<java.util.UUID, Component> hover;

    public PrestigeChatFormatter(PrestigeManager prestige) {
        this(prestige, owner -> Component.empty());
    }

    public PrestigeChatFormatter(
            PrestigeManager prestige,
            java.util.function.Function<java.util.UUID, Component> hover) {
        this(prestige, hover, owner -> fr.cliker.clikerv2.social.CommunityExtras.DEFAULT);
    }

    public PrestigeChatFormatter(
            PrestigeManager prestige,
            java.util.function.Function<java.util.UUID, Component> hover,
            java.util.function.Function<
                            java.util.UUID, fr.cliker.clikerv2.social.CommunityExtras.Style>
                    style) {
        this.style = style;
        this.prestige = prestige;
        this.hover = hover;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        int level = prestige.getLevel(player.getUniqueId());

        Component rank = RankPrefix.of(player);
        Component popularity = hover.apply(player.getUniqueId());
        var appearance = style.apply(player.getUniqueId());
        Component progression =
                Component.text(" [", NamedTextColor.DARK_GRAY)
                        .append(
                                Component.text(
                                        level > 0 ? PrestigeType.toRoman(level) : "0",
                                        NamedTextColor.LIGHT_PURPLE))
                        .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                        .append(
                                Component.text(
                                        fr.cliker.clikerv2.display.TabListManager.compactLevel(
                                                appearance.level()),
                                        NamedTextColor.AQUA))
                        .append(Component.text("]", NamedTextColor.DARK_GRAY));
        event.renderer(
                (source, sourceDisplayName, message, viewer) ->
                        rank.append(
                                        popularity.equals(Component.empty())
                                                ? sourceDisplayName
                                                : sourceDisplayName.hoverEvent(popularity))
                                .append(progression)
                                .append(appearance.tag())
                                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                                .append(message.color(appearance.color())));
    }
}
