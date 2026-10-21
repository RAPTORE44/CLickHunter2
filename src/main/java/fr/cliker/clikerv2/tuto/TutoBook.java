package fr.cliker.clikerv2.tuto;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

/** Livre-guide expliquant tout le serveur, ouvert via /tuto ou à la connexion. */
public final class TutoBook {
    private TutoBook() {}

    public static ItemStack create() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(Component.text("Guide Northblue Cliker"));
        meta.author(Component.text("Northblue"));

        meta.addPages(
                page("★ Bienvenue ★",
                        NamedTextColor.LIGHT_PURPLE,
                        "Bienvenue sur Northblue Cliker !",
                        "",
                        "Ce livre explique toutes les mécaniques du serveur.",
                        "",
                        "Tourne les pages pour tout découvrir.",
                        "Retape /tuto à tout moment pour le relire."),
                page("✦ Économie",
                        NamedTextColor.GOLD,
                        "3 monnaies :",
                        "✦ Écus - monnaie principale",
                        "◆ Gemmes Sombres - upgrades/gear",
                        "❀ Cendres - runes",
                        "",
                        "/ecus /gemmes /cendres",
                        "pour voir ton solde."),
                page("☠ Mobs Custom",
                        NamedTextColor.RED,
                        "19 mobs custom, Vache -> Warden,",
                        "PV croissants (100 -> 100M).",
                        "",
                        "Ils sont figés, tape-les !",
                        "",
                        "/autoclick pour attaquer",
                        "automatiquement la cible visée."),
                page("⚔ Stuff",
                        NamedTextColor.RED,
                        "Chaque mob a son épée + armure,",
                        "disponibles au PNJ du mob.",
                        "",
                        "Chaque pièce fait des dégâts fixes",
                        "= 8% des PV du mob d'origine.",
                        "",
                        "Set complet (épée+4 armure) = 40%."),
                page("◆ Essences",
                        NamedTextColor.YELLOW,
                        "Tuer un mob donne son essence",
                        "(Commune au départ).",
                        "",
                        "La compression (64 -> 1 palier",
                        "supérieur) se fait uniquement",
                        "au PNJ du mob.",
                        "",
                        "/sell pour vendre tes essences."),
                page("⚒ Compresseur & Amulette",
                        NamedTextColor.AQUA,
                        "Compresseur : dans l'inventaire,",
                        "compresse tes essences à chaque",
                        "coup porté sur ce mob.",
                        "",
                        "Amulette : bonus de dégâts fixe",
                        "passif tant qu'elle est portée.",
                        "",
                        "Les deux s'achètent au PNJ."),
                page("🏪 PNJ Marchand",
                        NamedTextColor.LIGHT_PURPLE,
                        "Un PNJ par mob : achète son",
                        "épée, son armure, son",
                        "compresseur et son amulette",
                        "contre Écus + essences.",
                        "",
                        "Compresse aussi tes essences",
                        "gratuitement sur place."),
                page("✦ Enchant / Reforge / Runes",
                        NamedTextColor.GREEN,
                        "/enchant, /reforge, /runes :",
                        "dépose ton épée/armure,",
                        "résultat 100% aléatoire.",
                        "",
                        "Max 1 actif à la fois par item,",
                        "chaque tentative remplace",
                        "le précédent (niveau I à XX)."),
                page("⚔ Golems (Boss)",
                        NamedTextColor.DARK_RED,
                        "Un boss par mob (PV x10+),",
                        "posé par un admin.",
                        "",
                        "Toute la vague apparaît",
                        "ensemble toutes les 30 min",
                        "(annonce + barre de boss).",
                        "",
                        "Loot bien meilleur qu'un mob normal."),
                page("★ Upgrades & Prestige",
                        NamedTextColor.DARK_PURPLE,
                        "/upgrade : bonus permanents",
                        "payés en Gemmes Sombres.",
                        "",
                        "/prestige : reset ton niveau XP",
                        "contre un bonus cumulatif par",
                        "kill (Écus/Gemmes/XP/Dégâts).",
                        "",
                        "Niveau XP donne aussi",
                        "+0.1% dégâts/niveau."),
                page("💰 Hôtel des Ventes",
                        NamedTextColor.GOLD,
                        "/ah : vends et achète des items",
                        "entre joueurs.",
                        "",
                        "Confirmation avant chaque achat",
                        "et vente. Taxe de 1% à la vente.",
                        "",
                        "Une annonce expire au bout",
                        "de 72h (item rendu)."),
                page("✦ Et le reste",
                        NamedTextColor.WHITE,
                        "Coffres du Nether : tu as une",
                        "chance d'en gagner en tuant",
                        "des mobs (rareté variable).",
                        "",
                        "/stats : récap complet de",
                        "ton profil.",
                        "",
                        "Hologrammes en jeu : classements",
                        "niveaux/kills/monnaies.",
                        "",
                        "Bon jeu sur Northblue !")
        );

        book.setItemMeta(meta);
        return book;
    }

    private static Component page(String title, NamedTextColor color, String... lines) {
        Component page = Component.text(title, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false)
                .append(Component.newline());
        for (String line : lines) {
            page = page.append(Component.newline()).append(Component.text(line, NamedTextColor.BLACK));
        }
        return page;
    }
}
