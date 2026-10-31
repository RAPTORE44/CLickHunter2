# Warp sur bloc ou Donateurs absents

La version actuelle est **1.0.1**. Elle conserve les mêmes données, permissions et commandes. Le JAR 1.0.0 publié avant cette mise à jour contenait déjà Warp et Donateurs ; ces fonctions n’ont pas été retirées des sources.

## Identifier ce que le serveur charge

1. Arrêter le serveur et sauvegarder son dossier `plugins/ClikerV2`.
2. Remplacer le JAR du plugin. Garder **un seul JAR déclarant le plugin ClikerV2** dans `plugins/` ; déplacer les anciens JAR hors de ce dossier.
3. Conserver `plugins/ClikerV2/` : ne pas remplacer ce dossier par une configuration vierge.
4. Redémarrer complètement puis exécuter `/version ClikerV2`. Le résultat attendu est `1.0.1`.
5. Exécuter `/clikerv2:diagnostic modules` et conserver les lignes retournées. Permission : `cliker.diagnostic.admin`, OP par défaut. La commande fonctionne aussi depuis la console sans `/`.

Le diagnostic indique quelle extension possède `/warp` et `/donateurs`. Si un autre plugin prend le nom court, `/clikerv2:warp set all` et `/clikerv2:donateurs` ciblent directement ClickHunter.

## Lire le résultat

- `raccourcis-warp : 1 actifs / 1 sauvegardés` : le bloc est chargé ; sa position est dans `navigation/warps.yml`, section `raccourcis-warp`.
- Un nombre de sauvegardes supérieur au nombre actif peut indiquer un monde non chargé. Le diagnostic donne son nom. Depuis 1.0.1, les liens sont rétablis quand ce monde charge, ainsi qu’après un déchargement/rechargement.
- `0 actifs / 0 sauvegardés` : le fichier chargé ne contient aucune position. Retrouver la configuration précédente dans la sauvegarde du serveur ; ne pas écraser les autres données pour recréer un bloc.
- `Donateurs : module actif` et `Podium : X/3 positions PNJ sauvegardées` : le module est chargé. Les fichiers sont `pnj/donateurs-1.yml`, `pnj/donateurs-2.yml`, `pnj/donateurs-3.yml`. Les points sont dans la base SQLite existante, distincts des positions des PNJ.
- Un module « non chargé » demande les lignes d’erreur du démarrage de ClikerV2. Ne pas supprimer ses fichiers pour masquer l’erreur.

ZNPCsPlus 2.0.0 est nécessaire aux corps des PNJ. Les titres utilisent le fournisseur d’hologrammes configuré. Une erreur de téléchargement de skin ne signifie pas que les points donateurs ont été effacés. Le diagnostic général `/diagnostic` détaille les fournisseurs.

Le défaut de chargement tardif des blocs est corrigé. L’agrandissement des PNJ donateurs utilise également l’API publique de PacketEvents afin d’éviter une erreur d’accès à sa classe interne. La cause d’une installation précise — ancien JAR, conflit de commande, monde absent, erreur de configuration ou données manquantes — doit être déterminée avec sa version et ses journaux, pas présumée à partir de l’apparence du serveur.

## Validation de la 1.0.1

418 tests automatisés réussis. Les tests couvrent le chargement tardif et le déchargement/rechargement des mondes, la conservation des positions, le menu Donateurs et les conflits du nom `/warp`. Sur Paper 1.21.4 avec ZNPCsPlus 2.0.0, création puis conservation après redémarrage du bloc Warp et de son hologramme, menu Donateurs avec 12,5 points sauvegardés, PNJ posé conservé et réception de l’attribut d’échelle 2 par le client. [Résultats structurés](validation/restauration-modules-1.0.1.json).
