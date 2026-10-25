# TAB ClickHunter

Configuration : `plugins/ClikerV2/affichage/tab.yml`. Redémarrer après modification. Le TAB est activé lors de cette mise à jour ; il peut ensuite être désactivé avec `displays.tab.enabled: false` dans `config.yml`.

L’entête affiche ClickHunter en vert, le compteur réel du serveur et `play.clickhunter.fr`. Le pied affiche le Discord connu et invite à voter. Renseigner les véritables adresses de boutique et vote dans `liens.boutique` et `liens.vote` ; aucun domaine de boutique supposé n’est utilisé.

La liste affiche le préfixe du grade, puis `Pseudo [Prestige romain - Niveau]`. Le prestige zéro est affiché `0`. Les préfixes LuckPerms existants sont conservés, avec les noms/couleurs de secours dans `grades`. Le groupe primaire LuckPerms ou les permissions `group.<nom>` déterminent le grade ; adapter `ordre-grades` aux noms réellement utilisés. Un op ou un joueur ayant `cliker.tab.staff` précède tous les autres. Par défaut : staff, admin, modérateur, modo, helper, Mini-VIP, VIP, VIP+, MVP, MVP+, Legend, Joueur. Les joueurs d’un même groupe sont classés par pseudo.

La disposition en colonnes suit le client vanilla, de haut en bas puis vers la droite, sans joueurs fictifs. On ne peut pas imposer trois colonnes fixes avec cette interface native et peu de joueurs. Le masquage dans `/settings` est conservé.

Les niveaux 1 à 10 ont chacun une couleur, puis les couleurs progressent par dizaines jusqu’à 100 et par centaines jusqu’à 1000. Au-delà, la dernière couleur est utilisée. Les grands niveaux s’affichent en milliers, tronqués à une décimale : `1250 → 1.2k`, `1500 → 1.5k`, `5500 → 5.5k`, `99000 → 99k`. Palette modifiable dans `couleurs-niveau`.

Rafraîchissement toutes les deux secondes par défaut (`actualisation-ticks`), avec niveaux et prestiges en mémoire, sans requête SQL ni modification des équipes du scoreboard. Aucun plugin TAB supplémentaire n’est nécessaire. Un autre plugin qui pilote également le TAB peut entrer en conflit ; choisir un seul gestionnaire.
