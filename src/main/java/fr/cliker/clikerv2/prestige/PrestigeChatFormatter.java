package fr.cliker.clikerv2.prestige;

import fr.cliker.clikerv2.display.RankPrefix;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public final class PrestigeChatFormatter implements Listener {
    private final PrestigeManager prestige;

    public PrestigeChatFormatter(PrestigeManager prestige) {
        this.prestige = prestige;
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        int level = prestige.getLevel(player.getUniqueId());

        Component rank = RankPrefix.of(player);
        event.renderer((source, sourceDisplayName, message, viewer) -> {
            Component prestigeTag = level > 0
                    ? Component.text("[", NamedTextColor.DARK_GRAY)
                            .append(Component.text(PrestigeType.toRoman(level), NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD))
                            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    : Component.empty();
            return rank.append(prestigeTag).append(sourceDisplayName).append(Component.text(" » ", NamedTextColor.DARK_GRAY)).append(message);
        });
    }
}
