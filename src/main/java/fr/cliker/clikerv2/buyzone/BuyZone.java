package fr.cliker.clikerv2.buyzone;

import fr.cliker.clikerv2.economy.CurrencyType;

import org.bukkit.Location;

/** Portail ou volume invisible, aligné sur les axes du monde (nord = -Z). */
public record BuyZone(
        String id,
        String name,
        String permission,
        CurrencyType currency,
        long price,
        Location center,
        double halfWidth,
        double height,
        double depth) {
    public BuyZone {
        if (id == null
                || !id.matches("[a-z0-9_-]+")
                || permission == null
                || permission.isBlank()
                || currency == null
                || center == null
                || center.getWorld() == null
                || !Double.isFinite(halfWidth)
                || !Double.isFinite(height)
                || !Double.isFinite(depth)
                || halfWidth <= 0
                || height <= 0
                || depth <= 0) throw new IllegalArgumentException("Zone invalide");
        center = center.clone();
    }

    @Override
    public Location center() {
        return center.clone();
    }

    public boolean contains(Location point) {
        return point != null
                && center.getWorld().equals(point.getWorld())
                && Math.abs(point.getX() - center.getX()) <= halfWidth
                && Math.abs(point.getZ() - center.getZ()) <= depth / 2
                && point.getY() >= center.getY()
                && point.getY() < center.getY() + height;
    }

    public boolean crosses(Location from, Location to) {
        if (to == null
                || from == null
                || !center.getWorld().equals(to.getWorld())
                || !center.getWorld().equals(from.getWorld())) return false;
        // Intersection segment/boîte : empêche de traverser un portail étroit en un seul mouvement.
        double[] start = {from.getX(), from.getY(), from.getZ()};
        double[] end = {to.getX(), to.getY(), to.getZ()};
        double[] min = {center.getX() - halfWidth, center.getY(), center.getZ() - depth / 2};
        double[] max = {
            center.getX() + halfWidth, center.getY() + height, center.getZ() + depth / 2
        };
        double enter = 0, exit = 1;
        for (int i = 0; i < 3; i++) {
            double delta = end[i] - start[i];
            if (Math.abs(delta) < 1e-10) {
                if (start[i] < min[i] || start[i] > max[i] || (i == 1 && start[i] >= max[i]))
                    return false;
            } else {
                double a = (min[i] - start[i]) / delta, b = (max[i] - start[i]) / delta;
                enter = Math.max(enter, Math.min(a, b));
                exit = Math.min(exit, Math.max(a, b));
                if (enter > exit) return false;
            }
        }
        return true;
    }

    public Location northReturn() {
        Location point = center.clone();
        point.setZ(center.getZ() - (depth <= 1 ? 15 : depth / 2 + 15));
        point.setYaw(0);
        point.setPitch(0);
        return point;
    }
}
