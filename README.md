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
