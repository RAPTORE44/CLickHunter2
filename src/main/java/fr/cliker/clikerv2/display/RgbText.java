package fr.cliker.clikerv2.display;

/** Dégradé hexadécimal lisible par le sérialiseur Adventure historique. */
public final class RgbText {
    private RgbText() {}

    public static String gradient(String text, int from, int to, boolean bold) {
        var result = new StringBuilder();
        int[] chars = text.codePoints().toArray();
        for (int i = 0; i < chars.length; i++) {
            double n = chars.length < 2 ? 0 : (double) i / (chars.length - 1);
            int rgb = 0;
            for (int shift : new int[] {16, 8, 0}) {
                int a = (from >> shift) & 255, b = (to >> shift) & 255;
                rgb |= ((int) Math.round(a + (b - a) * n)) << shift;
            }
            result.append(String.format(java.util.Locale.ROOT, "&#%06x", rgb));
            if (bold) result.append("&l");
            result.appendCodePoint(chars[i]);
        }
        return result.toString();
    }

    public static String marquee(String text, long step, int width) {
        String ring = text + "        ";
        StringBuilder out = new StringBuilder();
        int offset = (int) Math.floorMod(step, ring.length());
        for (int i = 0; i < width; i++) out.append(ring.charAt((offset + i) % ring.length()));
        return out.toString();
    }
}
