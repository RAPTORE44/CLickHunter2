package fr.cliker.clikerv2.display;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.cacheddata.CachedMetaData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Récupère le préfixe de grade LuckPerms (icônes ItemsAdder incluses) pour le tab et le chat. */
public final class RankPrefix {
    private static final boolean LUCKPERMS_PRESENT = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;

    private RankPrefix() {}

    public static Component of(Player player) {
        if (!LUCKPERMS_PRESENT) return Component.empty();
        try {
            LuckPerms api = LuckPermsProvider.get();
            CachedMetaData metaData = api.getPlayerAdapter(Player.class).getMetaData(player);
            String prefix = metaData.getPrefix();
            return parse(prefix);
        } catch (IllegalStateException notLoaded) {
            return Component.empty();
        }
    }

    private static Component parse(String raw) {
        if (raw == null || raw.isEmpty()) return Component.empty();
        if (raw.indexOf('<') >= 0) {
            try {
                return MiniMessage.miniMessage().deserialize(raw);
            } catch (RuntimeException invalidMiniMessage) {
                // certains préfixes ItemsAdder mélangent MiniMessage et légacy, on retente en légacy plus bas
            }
        }
        return LegacyComponentSerializer.legacyAmpersand().deserialize(raw);
    }
}
