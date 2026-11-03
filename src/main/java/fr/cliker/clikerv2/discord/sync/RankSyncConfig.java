package fr.cliker.clikerv2.discord.sync;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

/** Valeurs immuables : aucun accès YAML/Bukkit dans les traitements de synchronisation. */
public record RankSyncConfig(
        boolean enabled,
        String token,
        String guild,
        String channel,
        Map<String, String> groups,
        boolean initialMinecraft,
        int interval,
        int batch,
        int codeMinutes) {
    public RankSyncConfig {
        groups = Map.copyOf(groups);
    }

    public static boolean snowflake(String id) {
        return id != null && id.matches("[0-9]{17,20}");
    }

    public static RankSyncConfig read(YamlConfiguration yaml, String environmentToken) {
        boolean active = yaml.getBoolean("actif", false);
        String token =
                environmentToken == null || environmentToken.isBlank()
                        ? yaml.getString("bot-token", "").trim()
                        : environmentToken.trim();
        String guild = yaml.getString("serveur-id", "").trim(),
                channel = yaml.getString("salon-liaison-id", "").trim();
        Map<String, String> groups = new LinkedHashMap<>();
        var section = yaml.getConfigurationSection("groupes");
        if (section != null)
            for (String group : section.getKeys(false)) {
                String role = section.getString(group, "").trim();
                if (role.isEmpty()) continue;
                if (!group.matches("[a-z0-9_+.-]{1,64}") || !snowflake(role))
                    throw new IllegalArgumentException("Association groupe/rôle invalide.");
                if (role.equals(guild) || groups.containsValue(role))
                    throw new IllegalArgumentException(
                            "Rôle @everyone ou rôle associé plusieurs fois.");
                groups.put(group, role);
            }
        String initial = yaml.getString("premiere-liaison", "minecraft").toLowerCase(Locale.ROOT);
        if (!Set.of("minecraft", "discord").contains(initial))
            throw new IllegalArgumentException("premiere-liaison : minecraft ou discord.");
        if (active
                && (token.isBlank()
                        || token.chars().anyMatch(Character::isWhitespace)
                        || !snowflake(guild)
                        || !snowflake(channel)
                        || groups.isEmpty()))
            throw new IllegalArgumentException(
                    "Renseigne le jeton du bot, le serveur, le salon et au moins un rôle.");
        return new RankSyncConfig(
                active,
                token,
                guild,
                channel,
                groups,
                initial.equals("minecraft"),
                Math.max(15, Math.min(3600, yaml.getInt("intervalle-secondes", 30))),
                Math.max(1, Math.min(50, yaml.getInt("comptes-par-passage", 20))),
                Math.max(1, Math.min(30, yaml.getInt("code-minutes", 10))));
    }

    @Override
    public String toString() {
        return "RankSyncConfig[enabled="
                + enabled
                + ", token=<masqué>, guild="
                + guild
                + ", groups="
                + groups.size()
                + "]";
    }
}
