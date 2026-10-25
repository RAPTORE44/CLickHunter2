# ClikerV2

Le module **Buyzone** crée des portails invisibles d'achat d'accès avec l'économie
intégrée : [configuration des tarifs, commandes et installation](docs/BUYZONE.md).
Les prix d'accès manquants sont proposés à partir des prix actuels des armures, sans modifier les tarifs existants.

Le module **NorthBlue de récompenses journalières** est intégré à ce même plugin :
`/daily`, calendrier de 28 jours et PNJ Citizens optionnel.
Voir [l'installation, la configuration et les règles du calendrier](docs/RECOMPENSES-JOURNALIERES.md).

Plugin unique pour Paper 1.21.4 : economie SQLite et commandes de gestion des monnaies.

## Prerequis

- Paper 1.21.4
- Java 21 ou plus recent
- Aucun autre plugin requis

## Commandes

- `/money balance [joueur]` ou `/money bal [joueur]`
- `/money give <joueur> <monnaie> <montant>`
- `/money take <joueur> <monnaie> <montant>`
- `/money set <joueur> <monnaie> <montant>`

Les monnaies acceptent `ecus`, `gemmes-sombres` et `cendres`. Les operations `give`, `take` et `set` demandent `cliker.money.admin`.

Les joueurs hors ligne sont geres par leur UUID via `Bukkit.getOfflinePlayer`. La base est creee dans `plugins/ClikerV2/data/economy.db`. Les operations SQLite sont executees par une file dediee unique, puis les messages reviennent sur le thread principal Paper.

## Build

```text
mvn clean package
```

Le jar final est dans `target/clikerv2-1.0.0.jar`.

## Sacs personnels

`/sac` affiche neuf cases : deux vitres noires sans nom aux extrémités et
sept sacs au centre. Un sac accessible est une étoile du Nether ; un sac
verrouillé reste une barrière. Chaque sac ouvre un coffre personnel de 54 cases.

| Sac | Rang affiché | Accès |
| --- | --- | --- |
| 1 | Joueur | Par défaut |
| 2 | Mini-VIP | `cliker.sac.2` |
| 3 | VIP | `cliker.sac.3` |
| 4 | VIP+ | `cliker.sac.4` |
| 5 | MVP | `cliker.sac.5` |
| 6 | MVP+ | `cliker.sac.6` |
| 7 | Legend | `cliker.sac.7` |

Les permissions sont cumulatives : la permission d'un sac débloque aussi les
précédents. Les anciennes permissions `.8` et `.9` donnent accès aux sept sacs.
Les noms des permissions ne sont pas affichés dans le menu. Affecter ces
permissions aux groupes correspondants dans le gestionnaire de permissions.

Le contenu est sauvegardé dans SQLite à la fermeture du coffre, à la déconnexion
et à l'arrêt du plugin. Les objets de l'ancien sac sont automatiquement repris
dans le premier coffre ; les talismans stockés conservent leurs bonus.

Vérifications automatisées : `mvn test`.

État de la mise en conformité du cahier maître : [avancement et limites](docs/MISE-A-JOUR-MAITRE.md).

Compilation reproductible (JDK 21) : `./mvnw clean package`.

Navigation et performances : `/menu` — [configuration et optimisations](docs/UX-UI-OPTIMISATIONS.md).

Scoreboard ClickHunter : [présentation, monnaies et configuration](docs/SCOREBOARD.md).

Combat : [textes de dégâts qui montent puis disparaissent](docs/TEXTES-DE-DEGATS.md).

Communauté : [commande Discord et placement du PNJ](docs/PNJ-DISCORD.md).

Récompenses différées et communauté : [/claim et /parrain](docs/CLAIM-PARRAINAGE.md).

Activités intégrées : [bestiaire, contrats, raid et avant-postes](docs/ACTIVITES.md).

Revue précédente : [correctifs, vérifications et limites du 25 octobre 2026](docs/AUDIT-2026-10-25.md).

Audit complémentaire : [constats classés, corrections et validations](docs/AUDIT-COMPLET.md), [inventaire des composants](docs/INVENTAIRE-AUDIT.md) et [suivi des évolutions](docs/SUIVI-EVOLUTIONS.md).
