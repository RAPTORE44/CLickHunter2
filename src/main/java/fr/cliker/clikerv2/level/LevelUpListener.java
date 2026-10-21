package fr.cliker.clikerv2.level;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;

import java.util.Locale;

/** Affiche un titre à chaque montée de niveau XP avec le bonus de dégâts cumulé (0.1%/niveau), et tient à jour le classement des niveaux. */
public final class LevelUpListener implements Listener {
    private final PlayerLevelStore levelStore;

    public LevelUpListener(PlayerLevelStore levelStore) {
        this.levelStore = levelStore;
    }

    @EventHandler
    public void onLevelChange(PlayerLevelChangeEvent event) {
        levelStore.setLevel(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getNewLevel());
        if (event.getNewLevel() <= event.getOldLevel()) return;

        String percent = String.format(Locale.FRANCE, "%.1f", LevelDamageBonus.percent(event.getNewLevel()));
        Title title = Title.title(
                Component.text("Nouveau LvL", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD),
                Component.text("Dégât +" + percent + "%", NamedTextColor.RED, TextDecoration.BOLD)
        );
        event.getPlayer().showTitle(title);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        levelStore.setLevel(event.getPlayer().getUniqueId(), event.getPlayer().getName(), event.getPlayer().getLevel());
    }
}
