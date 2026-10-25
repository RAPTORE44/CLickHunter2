# ClikerV2

Le module **BuyZone** crée des portails invisibles d'achat d'accès avec l'économie
intégrée : [configuration des tarifs, commandes et installation](docs/BUYZONE.md).
Les prix d'accès manquants sont proposés à partir des prix actuels des armures, sans modifier les tarifs existants.

Le module **NorthBlue de récompenses journalières** est intégré à ce même plugin :
`/daily`, calendrier de 28 jours et PNJ ZNPCsPlus 2.0.0 optionnel.
Voir [l'installation, la configuration et les règles du calendrier](docs/RECOMPENSES-JOURNALIERES.md).

Plugin unique pour Paper 1.21.4 : économie SQLite et commandes de gestion des monnaies.

## Prérequis

- Paper 1.21.4
- Java 21 ou plus récent
- ZNPCsPlus **2.0.0** pour les PNJ et leurs boutiques
- ItemsAdder optionnel pour les icônes `icons:*` déjà installées
- DecentHolograms optionnel pour les classements holographiques

## Commandes par catégorie

Cette liste doit être vérifiée et mise à jour à chaque modification du plugin, en même temps que les commandes et leurs comportements. `<argument>` désigne un argument obligatoire et `[argument]` un argument facultatif.

### Accueil et communauté

| Commande | Utilité |
| --- | --- |
| `/menu` | Ouvrir le menu principal. |
| `/tuto` | Consulter le livre-guide. |
| `/spawn` | Rejoindre le point d'accueil. |
| `/discord` | Recevoir le lien du Discord. |
| `/parrain <pseudo>` | Déclarer son parrain pendant le délai autorisé ; alias `/parrainage`. |

### Récompenses et stockage

| Commande | Utilité |
| --- | --- |
| `/daily` | Ouvrir le calendrier et réclamer la récompense disponible. |
| `/claim` | Récupérer les récompenses en objets mises en attente. |
| `/sac` | Ouvrir les sacs personnels accessibles selon le rang du joueur. |

### Combat et progression

| Commande | Utilité |
| --- | --- |
| `/autoclick` | Activer ou désactiver l'attaque automatique. |
| `/upgrade` | Acheter des améliorations en Gemmes Sombres. |
| `/stats` | Consulter ses statistiques. |
| `/prestige` | Consulter les conditions et effectuer un prestige si les conditions sont remplies. |
| `/bestiaire` | Consulter les créatures découvertes et les maîtrises. |
| `/contrats` | Accepter ou réclamer les contrats de chasse. |
| `/raid` | Ouvrir son groupe de raid (1 à 8 joueurs). |
| `/raid inviter <pseudo>` | Inviter un joueur en ligne pour 60 secondes. |
| `/raid accepter <pseudo>` | Accepter l’invitation d’un chef. |
| `/raid lancer` | Le chef consomme le parchemin tenu et lance un raid privé de sa difficulté. |
| `/raid rejoindre` | Rejoindre son raid déjà lancé. |
| `/raid quitter` | Quitter le groupe et son arène ; `sortir` est accepté. |
| `/tallisement` | Consulter la collection de talismans ; alias `/talismans`, `/talissement`. |

### Économie et équipement

| Commande | Utilité |
| --- | --- |
| `/ecus balance [joueur]` | Consulter un solde d'Écus. |
| `/gemmes balance [joueur]` | Consulter un solde de Gemmes Sombres ; alias `/gemmessombres`. |
| `/cendres balance [joueur]` | Consulter un solde de Cendres. |
| `/sell` | Sur la pile cliquée : gauche vend 1 essence, droite vend cette pile uniquement. |
| `/ah` | Ouvrir l'hôtel des ventes. |
| `/ah vendre <prix>` / `/ah sell <prix>` | Confirmer la vente de l’objet tenu ; frais non remboursables de 1 % à la publication, annonce chat optionnelle pour 5 % supplémentaires. |
| `/ah mesannonces` | Consulter ses annonces ; `mylistings` reste accepté. |
| `/enchant` | Ouvrir la table d'enchantement, payée en Écus. |
| `/reforge` | Ouvrir la reforge, payée en Gemmes Sombres. |
| `/runes` | Ouvrir la table de runes, payée en Cendres. |

L'autocompresseur s'achète chez le marchand puis s'active par clic droit : l'objet est consommé et 64 ressources d'un palier sont automatiquement converties en une du palier suivant, pour le mob associé.

`balance` peut être abrégé en `bal`. Sans argument, `/ecus`, `/gemmes` et `/cendres` affichent le solde du joueur qui les exécute.

### Confort, protection et navigation

| Commande | Utilité |
| --- | --- |
| `/guide` | Conseiller le prochain palier d’amélioration le moins cher, selon les niveaux et le solde réel. |
| `/zone <numéro>` | Rejoindre une zone déjà débloquée et configurée ; alias `/z`. |
| `/warp` | Zones débloquées : gauche vers l’entrée, droite devant leur boss ; raccourcis spawn, raids et boss public. |
| `/boss` | Rejoindre l’arène publique du boss serveur, accessible à tous. |
| `/boss quitter` | Revenir de l’arène publique. |
| `/progression` | Afficher pendant dix secondes la progression dans les zones configurées. |
| `/settings` | Régler les textes de dégâts, les effets doux, le son de compression et les messages de farm ; alias `/parametres`. |
| `/antidrop` | Basculer directement entre autoriser et bloquer le jet d’objets et la vente AH des équipements protégés, jusqu’à la déconnexion. |
| `/ramassage <autoriser\|bloquer>` | Autoriser ou bloquer le ramassage au sol jusqu’à la déconnexion. Les récompenses directes du plugin restent livrées. |
| `/lock` | Verrouiller ou déverrouiller une épée, une armure ClickHunter ou un compresseur tenu, contre le jet et la vente AH. |

Les protections de jet et de ramassage sont actives à chaque connexion. Les réglages visuels et les filtres de messages persistent. Les téléportations vérifient l’accès BuyZone et refusent une arrivée dangereuse. Permissions : `cliker.navigation.use` pour la navigation, `cliker.comfort.use` pour les protections et paramètres, `cliker.upgrade.use` pour le guide.

Dans `/prestige`, un premier clic gauche prépare le prestige ; un second clic gauche dans les dix secondes le confirme. Fermer le menu annule la préparation.

Les trappes, portillons et pots décoratifs sont protégés des clics involontaires (`comfort.protect-decoration`). L’épée affiche la recharge native pendant l’autoclick.

Dans `/stats`, la réduction bonus de dégâts est affichée séparément de l’armure vanilla, et les gains confirmés des 60 dernières secondes sont séparés par créature. Le compteur des coups validés est conservé, et la session précédente est résumée à la reconnexion. Les boss disposent d’un chronomètre et d’un record personnel, avec une célébration de première victoire.

Dans `/upgrade`, clic gauche : acheter un niveau ; clic droit : choisir **X1, X5, X10, X50 ou le maximum abordable**. Le coût cumule le tarif de chaque niveau ; les quantités impossibles sont bloquées et aucun achat partiel n’est effectué pour X1/X5/X10/X50. Le menu des marchands compare les dégâts **de base** des épées, hors bonus.

### Avant-postes

Toutes ces commandes acceptent aussi `/is` et `/avantpostes` à la place de `/avantposte`.

| Commande | Utilité |
| --- | --- |
| `/avantposte` | Ouvrir le menu du terrain personnel. |
| `/avantposte aller` | Créer ou rejoindre son terrain. |
| `/avantposte agrandir` | Acheter l'extension suivante. |
| `/avantposte visite <pseudo>` | Visiter un terrain existant. |
| `/avantposte ajouter <pseudo>` | Autoriser un joueur connu à construire et à utiliser les coffres. |
| `/avantposte retirer <pseudo>` | Retirer cette autorisation. |
| `/avantposte top` | Consulter les propriétaires les plus riches. |
| `/avantposte blocs` | Ouvrir la boutique de blocs depuis le monde des avant-postes. |
| `/avantposte pnj deplacer` | Déplacer son marchand à sa position et orientation, dans son propre terrain. |
| `/avantposte sortir` | Quitter l'avant-poste. |

### Administration : monde, zones et PNJ

Ces commandes nécessitent les permissions administrateur correspondantes. ZNPCsPlus 2.0.0 est nécessaire pour les PNJ ; DecentHolograms est nécessaire pour `/holotop`.

| Commande | Utilité | Permission |
| --- | --- | --- |
| `/gm [0\|1\|2\|3] [joueur]` | Changer de mode ; sans argument, basculer entre survie et créatif. Modes : 0 survie, 1 créatif, 2 aventure, 3 spectateur. | `cliker.gamemode.admin` |
| `/setspawn` | Définir le point d'accueil à la position actuelle. | `cliker.spawn.admin` |
| `/buyzone spawn <zone>` | Placer un portail d'achat pour une zone configurée. | `cliker.buyzone.admin` |
| `/buyzone remove <zone>` | Supprimer ce portail. | `cliker.buyzone.admin` |
| `/daily pnj spawn` / `/daily pnj remove` | Placer ou supprimer le PNJ des récompenses. | `cliker.daily.admin` |
| `/discord pnj spawn` / `/discord pnj remove` | Placer ou supprimer le PNJ Discord. | `cliker.discord.admin` |
| `/contrats pnj spawn` / `/contrats pnj remove` | Placer ou supprimer le PNJ des contrats. | `cliker.contrats.admin` |
| `/mobspawn setspawn <mob>` | Définir un point d'apparition de mob ; alias `/mob`. | `cliker.mob.admin` |
| `/mobspawn bossspawn <mob>` | Définir un point d'apparition de boss. | `cliker.mob.admin` |
| `/mobspawn remove <mob>` | Supprimer le point d'apparition le plus proche pour ce mob. | `cliker.mob.admin` |
| `/mobspawn removeall <mob>` | Supprimer tous les points d'apparition de ce mob. | `cliker.mob.admin` |
| `/mobspawn list` | Lister les points d'apparition. | `cliker.mob.admin` |
| `/pnj spawn <mob>` / `/pnj remove` / `/pnj list` | Gérer les marchands de progression. | `cliker.mob.admin` |
| `/pnj skin <mob> <pseudo\|villager>` | Modifier l’apparence des marchands d’une créature. | `cliker.mob.admin` |
| `/pnjservice spawn <ah\|shop\|blackmarket>` | Placer un PNJ de service, orienté ouest. | `cliker.services.admin` |
| `/pnjservice remove <ah\|shop\|blackmarket>` | Retirer un PNJ de service. | `cliker.services.admin` |
| `/pnjservice skin <ah\|shop\|blackmarket> <pseudo\|villager>` | Choisir un skin de joueur ou revenir au villageois. | `cliker.services.admin` |
| `/sacbloc set` / `/sacbloc remove` | Lier ou délier le bloc visé au sac personnel de chaque joueur. | `cliker.services.admin` |
| `/warp set <zone> <entree\|boss>` | Sauvegarder sa position et son orientation comme destination. | `cliker.services.admin` |
| `/batonspawn <mobs\|boss> <nom>` | Recevoir un bâton ; clic droit sur un bloc pour créer un spawn persistant au-dessus. | `cliker.services.admin` |
| `/raid parchemin <zone> [joueur]` | Attribuer un parchemin de raid. | `cliker.services.admin` |
| `/station spawn <enchant\|reforge\|rune>` / `/station remove` | Placer ou supprimer une station. | `cliker.mob.admin` |
| `/holotop spawn <level\|kills\|ecus\|gemmes\|cendres\|bosstimer>` / `/holotop remove` | Gérer les hologrammes de classement et le minuteur des boss. | `cliker.mob.admin` |

### Administration : monnaies et distribution

| Commande | Utilité | Permission |
| --- | --- | --- |
| `/ecus <give\|take\|set> <joueur> <montant>` | Ajouter, retirer ou définir des Écus. | `cliker.money.admin` |
| `/gemmes <give\|take\|set> <joueur> <montant>` | Ajouter, retirer ou définir des Gemmes Sombres. | `cliker.money.admin` |
| `/cendres <give\|take\|set> <joueur> <montant>` | Ajouter, retirer ou définir des Cendres. | `cliker.money.admin` |
| `/xp give <joueur> <montant>` | Donner de l'expérience Minecraft. | `cliker.xp.admin` |
| `/mobkills give <joueur> <montant>` | Ajouter des éliminations pour les tests. | `cliker.mob.admin` |
| `/epee` | Recevoir l'épée du dresseur. | `cliker.mob.admin` |
| `/stuff <mob>` | Recevoir l'épée et l'armure du mob. | `cliker.mob.admin` |
| `/coffre <joueur> <rareté> [quantité]` | Donner des coffres de récompenses. | `cliker.mob.admin` |
| `/compresseur <mob>` | Recevoir l'objet autocompresseur à activer. | `cliker.mob.admin` |
| `/charm <mob>` | Recevoir une amulette de dégâts. | `cliker.mob.admin` |
| `/talisman treasure <joueur>` | Donner un talisman de trésor. | `cliker.mob.admin` |

Les commandes du plugin sont déclarées dans [plugin.yml](src/main/resources/plugin.yml). Les commandes des autres plugins ne sont pas incluses. Il n'existe pas de commande `/money` propre à ClikerV2 : utiliser `/ecus`, `/gemmes` ou `/cendres`.

La base est créée dans `plugins/ClikerV2/data/economy.db`. Les opérations SQLite passent par une file dédiée ; les interactions Bukkit reviennent sur le thread principal. Les commandes monétaires recherchent les joueurs en ligne ou déjà connus, sans recherche réseau bloquante.

## Compilation

```text
mvn clean package
```

Le fichier JAR compilé est dans `target/clikerv2-1.0.0.jar`.

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

Sélection du classeur : [suivi des 141 lignes jaunes, livraisons et travail restant](docs/SELECTION-JAUNE.md).

Audit de suivi : [corrections de reconnexion, sauvegarde, compresseur et achats](docs/AUDIT-SUIVI-2026-10-25.md). Les boutons des marchands et de transformation répondent aux clics gauche/droit ; les touches de déplacement d’objets ne déclenchent pas de dépense. Le compresseur s’active dans l’air ou sur un bloc, selon les protections du serveur.

Icônes des boutons : [utilisation des items `icons:*` déjà installés dans ItemsAdder](docs/ICONES-MENUS.md). Aucun pack supplémentaire à installer. Aucune commande supplémentaire : les 46 commandes et leurs permissions restent inchangées.

PNJ : [intégration ZNPCsPlus 2.0.0, installation et migration](docs/PNJ-ZNPCSPLUS.md).

Confort de session : mode de jeu conservé à la déconnexion, étoile de menu en neuvième case de la barre rapide (clic droit), gains colorés et icônes Daily réappliquées immédiatement à chaque actualisation. L’objet déjà présent en neuvième case est déplacé vers une case libre ; si l’inventaire est plein, libérer une case puis fermer l’inventaire pour installer l’étoile sans perte. L’étoile ne se déplace pas, ne se jette pas et ne se vend pas.

Détails et validations : [session, barre rapide et calendrier](docs/SESSION-ET-INTERFACE.md).

### Inventaires des avant-postes et interactions

Les avant-postes ont un inventaire et une XP distincts (armure et seconde main comprises). Le retour au `/spawn`, `/avantposte sortir` ou tout autre monde restaure l’équipement clicker ; les blocs de construction restent dans l’avant-poste et sont retrouvés à la prochaine visite. Les profils sont conservés après reconnexion et redémarrage. Les ventes, échanges, sacs partagés, coffres de l’End et commandes externes non autorisées y sont bloqués, ainsi que la sortie des objets et véhicules par téléportation ou portail. Cette séparation s’applique aussi en créatif.

Chaque terrain possède un marchand **ZNPCsPlus 2.0.0**, déplaçable par son propriétaire. Il vend tous les blocs Minecraft possédant une forme d’objet, en pages : clic gauche pour un bloc, clic droit pour une pile vanilla. Prix par défaut : **10 Écus par bloc**, modifiable dans `avantpostes.yml`, section `boutique-blocs` (prix individuels par nom de matériau). Les états techniques sans objet (portails, eau posée…) ne figurent pas dans la boutique. Les achats payés attendent une place libre dans l’inventaire d’avant-poste ; ils ne sont jamais livrés dans le monde clicker.

Les portails BuyZone renvoient désormais **15 blocs au nord**, avec le regard vers le sud. En créatif, les portes, trappes et autres interactions sont autorisées. Les mobs clicker et raid sont silencieux ; un coup réussi déclenche un son de bloc musical, avec l’animation de dégâts qui monte d’un bloc.

Détails et vérifications : [inventaires et boutique de construction](docs/AVANTPOSTES-INVENTAIRES.md).

## Boutiques, ateliers et nouvelles activités

Les PNJ AH, shop et blackmarket utilisent ZNPCsPlus 2.0.0. Les marchands apparaissent en villageois par défaut ; les skins de joueur restent configurables. Les boutons utilisent les icônes ItemsAdder déjà installées, avec remplacement vanilla. Les boutiques shop et blackmarket sont accessibles uniquement par leurs PNJ : aucune commande joueur ne les ouvre.

Dans chaque atelier, le livre en haut à droite explique les effets, catégories, niveaux et probabilités réellement utilisés. Retour ramène à l’atelier et l’objet précédemment déposé est restitué normalement.

Le boss public apparaît à chaque nouveau milliard de créatures effectivement éliminées ; compteur, cycle et état actif persistent après redémarrage. Les boss propres aux zones conservent leur fonctionnement et leurs destinations dans `/warp`. Les raids privés nécessitent un parchemin lié à une zone débloquée par chaque participant. Les deux activités utilisent des mondes distincts et partagent les récompenses de boss selon les dégâts effectifs, sans PvP.

Les talismans de créature et de boss s’ajoutent à cinq familles générales : lame, richesse, sagesse, fortune et vitalité, chacune sur cinq tiers. Les bonus sont actifs dans les sacs, avec le meilleur tier par famille générale. Le tier 5 se débloque dans la **dernière zone actuelle (zone 19)**, conformément au choix du propriétaire.

Configuration SFTP organisée, migration des anciens fichiers avec copies de secours et détails de chaque module : [guide des nouveaux systèmes](docs/SERVICES-RAIDS-TALISMANS.md).
