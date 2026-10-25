package fr.cliker.clikerv2.config;

import org.bukkit.*;

import java.sql.*;

/** Valeurs primitives transportées entre SQLite et le thread serveur. */
public record StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
    public StoredLocation {
        if (world == null
                || world.isBlank()
                || !Double.isFinite(x)
                || !Double.isFinite(y)
                || !Double.isFinite(z)
                || !Float.isFinite(yaw)
                || !Float.isFinite(pitch))
            throw new IllegalArgumentException("Coordonnées invalides");
    }

    private static double number(ResultSet row, String column) throws SQLException {
        Object value = row.getObject(column);
        if (!(value instanceof Number number))
            throw new IllegalArgumentException("Coordonnée non numérique : " + column);
        return number.doubleValue();
    }

    public static StoredLocation capture(Location location) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Capture de position hors du thread serveur");
        if (location.getWorld() == null) throw new IllegalArgumentException("Monde absent");
        return new StoredLocation(
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch());
    }

    public static StoredLocation read(ResultSet row, boolean yaw, boolean pitch)
            throws SQLException {
        return new StoredLocation(
                row.getString("world"),
                number(row, "x"),
                number(row, "y"),
                number(row, "z"),
                yaw ? (float) number(row, "yaw") : 0,
                pitch ? (float) number(row, "pitch") : 0);
    }

    public Location resolve() {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Résolution de monde hors du thread serveur");
        World loaded = Bukkit.getWorld(world);
        return loaded == null ? null : new Location(loaded, x, y, z, yaw, pitch);
    }
}
