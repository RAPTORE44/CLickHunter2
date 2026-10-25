# Bestiaire, contrats, raids et avant-postes

Les objets transformés aux stations reviennent dans l’inventaire ou `/claim`, même si le menu est fermé.
Survoler le message de réussite permet de voir le résultat.

Ces quatre modules sont intégrés à **ClikerV2**, sous Paper 1.21.4 et Java 21.
Ils utilisent les monnaies actuelles : Écus, Gemmes Sombres, Cendres, et l’XP Minecraft.
Les Gemmes Sombres sont exprimées **en dixièmes** dans les fichiers : `10 = 1 gemme`.

## Installation et mise à jour

1. Arrêter le serveur proprement. Sauvegarder `plugins/ClikerV2/` et les mondes, avec leurs dossiers `playerdata`.
2. Remplacer l’ancien JAR par `ClikerV2.jar` dans `plugins/`. Conserver les configurations et la base existantes.
3. Démarrer avec Java 21. Les nouvelles tables et configurations sont créées automatiquement.
4. Configurer `chasse.yml`, `raid.yml`, `avantpostes.yml`, puis redémarrer pour appliquer les changements.
5. Pour les PNJ humains uniquement, installer une version de **Citizens compatible Paper 1.21.4**.

Les menus et activités fonctionnent sans Citizens, sans ItemsAdder et sans pack de ressources.
Les mondes `clickhunter_raid` et `clickhunter_avantpostes` sont générés automatiquement.
Ne pas leur attribuer le nom d’un monde de jeu préexistant. Conserver leurs noms après ouverture aux joueurs.
Les quatre boutons sont ajoutés à `/menu` en préservant les emplacements personnalisés ; un menu complet nécessite un placement manuel.

## Commandes et permissions

| Commande | Fonction | Permission |
| --- | --- | --- |
| `/bestiaire` | Découvertes, maîtrises, éliminations et gains | `cliker.bestiaire` |
| `/contrats` | Accepter et réclamer des contrats quotidiens | `cliker.contrats` |
| `/contrats pnj spawn` | Placer le PNJ à sa position et orientation | `cliker.contrats.admin` |
| `/contrats pnj remove` | Supprimer le PNJ des contrats | `cliker.contrats.admin` |
| `/raid` | Rejoindre l’arène coopérative | `cliker.raid` |
| `/raid quitter` | Revenir à sa position précédente, ou au spawn après reconnexion | `cliker.raid` |
| `/avantposte` ou `/is` | Menu du terrain personnel | `cliker.avantposte` |
| `/avantposte aller` | Créer gratuitement son terrain ou le rejoindre | `cliker.avantposte` |
| `/avantposte agrandir` | Acheter le palier suivant en Écus | `cliker.avantposte` |
| `/avantposte visite <pseudo>` | Visiter un terrain existant | `cliker.avantposte` |
| `/avantposte ajouter <pseudo>` | Autoriser un joueur connu à construire et ouvrir les coffres | `cliker.avantposte` |
| `/avantposte retirer <pseudo>` | Retirer cette autorisation | `cliker.avantposte` |
| `/avantposte top` | Dix propriétaires les plus riches en Écus, avec leurs trois soldes | `cliker.avantposte` |
| `/avantposte sortir` | Revenir à son monde précédent | `cliker.avantposte` |

Les permissions de jeu sont accordées par défaut. La gestion des PNJ est réservée aux opérateurs.
`cliker.avantposte.admin` permet aux administrateurs de modifier tous les terrains : ne pas l’accorder aux visiteurs.
Les visites restent publiques ; un joueur ajouté dispose de vrais droits sur les constructions et les coffres.

## Bestiaire et maîtrises

Les 19 espèces et leur boss associé sont suivis séparément par UUID. Tant qu’un joueur n’a pas tué
**au moins un mob normal de cette espèce**, son nom, son icône et ses récompenses restent masqués.
Tuer uniquement son boss ne révèle pas l’espèce normale. Aucun prérequis ne révèle le mob suivant.

Chaque entrée découverte présente les éliminations normales, les boss vaincus, les maîtrises de chaque
catégorie, le gain normal par unité avec l’équipement actuel, la fourchette du bonus de boss et les
monnaies/XP cumulées effectivement créditées sur cette espèce et son boss.
Les maîtrises sont des paliers de collection configurables ; elles n’ajoutent pas de bonus de combat.
Les statistiques détaillées commencent à cette mise à jour : l’ancien compteur global ne permet pas de reconstituer les espèces ni les gains passés.
Les pillards et le Gardien du raid constituent une activité séparée, et ne révèlent aucune espèce du bestiaire.

## Contrats

`chasse.yml` contient les paliers de maîtrise, messages, PNJ et **38 contrats configurables**
(19 espèces × normal/boss). Les définitions sont ajoutées au premier démarrage.
Un premier clic accepte le contrat ; seules les éliminations suivantes comptent.
Un second clic après l’objectif verse directement les monnaies et inscrit l’XP à délivrer.
Les clics répétés ne peuvent pas payer deux fois le même contrat.

Réinitialisation à minuit dans `Europe/Paris`, configurable. Réclamer les contrats terminés avant minuit :
ils ne sont pas reportés au lendemain. Les contrats d’une créature inconnue restent anonymes.
Le PNJ des contrats dispose de sa propre identité Citizens, de sa position sauvegardée et de ses textes.
Son retrait ne supprime pas les PNJ du calendrier ou de Discord.

## Raid

`raid.yml` configure les PV, dégâts, plafond d’ennemis, seuil d’apparition du boss et récompenses.
L’arène contient des pillards hostiles. À chaque seuil d’éliminations collectif, les pillards disparaissent
et le **Gardien colossal** apparaît. Il avance vers les joueurs et inflige une attaque de proximité toutes les deux secondes.
Le boss possède 10 milliards de PV par défaut : équipement avancé et coopération conseillés.
Utiliser l’épée du clicker ; les calculs de dégâts et l’animation sont partagés avec le combat existant.

Les dégâts dépassant les PV restants du boss sont exclus de la contribution. La cagnotte entière de chaque
monnaie et d’XP est partagée selon les dégâts effectifs, avec répartition des arrondis sans créer de monnaie.
Les joueurs qui se déconnectent avant le coup final conservent leur contribution ; l’argent est crédité
hors ligne et l’XP est délivrée à leur retour. Le dernier coup ne donne aucun avantage particulier.

Le PvP, la construction et les dégâts des explosions sur le terrain sont désactivés. L’inventaire et les
niveaux sont conservés à la mort dans cette arène. La progression du combat en cours est temporaire :
un redémarrage annule le combat non terminé. Les récompenses déjà validées restent sauvegardées.
Ce n’est pas une garantie d’équilibrage : ajuster les PV et la cagnotte selon l’équipement réel du serveur.

## Avant-postes

Chaque joueur possède un terrain unique dans un monde vide, avec un îlot initial de **21 × 21** blocs.
La première limite constructible mesure **33 × 33** blocs. Les paliers par défaut vont jusqu’à **193 × 193**.
Une bordure individuelle indique la limite ; l’achat d’un palier agrandit le droit de construire et la bordure,
sans remplir le vide automatiquement. Les joueurs apportent leurs matériaux.

Les terrains sont espacés de 256 blocs. Les visiteurs peuvent regarder, mais ne peuvent pas casser,
construire, ouvrir les coffres, manipuler les décorations ou ramasser les objets du propriétaire.
Les fluides et pistons ne traversent pas les limites ; les explosions ne détruisent pas les constructions.
Les autorisations des invités, le propriétaire et les extensions sont conservés dans SQLite.
Les blocs sont sauvegardés normalement par Paper dans le monde dédié.
Ne pas réduire les rayons déjà vendus ni changer l’ordre des paliers sur un serveur en production.

## Vérifier un portail buyzone

Depuis le monde de jeu, prévoir un sol sûr **5 blocs au nord** du centre (`Z - 5`), puis utiliser
`/buyzone spawn cochon`, en remplaçant `cochon` par l’identifiant voulu.
Le portail mesure 40 blocs sur l’axe est-ouest et 10 blocs de haut. Il ne tourne pas selon l’orientation du joueur.

`/buyzone liste` affiche le catalogue et les portails actifs. `/buyzone info cochon` indique la position,
le monde, la permission de la zone et l’état du contournement pour l’administrateur.
Si le contournement reste actif avec LuckPerms, une permission explicite ou un joker de groupe peut
l’accorder. Pour tester avec son propre compte :
`/lp user <pseudo> permission set cliker.buyzone.bypass false`.
Un joueur qui possède déjà `cliker.zone.cochon`, ou qui l’a achetée, ne reçoit plus l’offre.
Les portails en attente d’un monde sont restaurés lorsque celui-ci est chargé, sans effacer leurs positions.
