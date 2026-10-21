# ClikerV2

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
