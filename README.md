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
- Holographic Displays **API 3**, avec une version du fournisseur compatible Paper 1.21.4, pour les hologrammes demandés ; secours natif configurable si indisponible.

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
| `/sac gestion` | Aperçu puis confirmation du tri ou du dépôt des ressources dans le premier sac ; clic droit sur un autre sac pour le gérer. |
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
| `/pinata` / `/pinata tp` | Voir la piñata active et la rejoindre ; alias `/pignata`. |
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
| `/enchere` / `/enchere rejoindre` | Consulter l’événement et s’inscrire pendant les trois minutes ; alias `/encheres`. Les inscrits peuvent rouvrir la vente ensuite. |
| `/enchere quitter` | Quitter le menu ; pendant les inscriptions, se désinscrire. Une offre déjà déposée reste ferme. |
| `/ah` | Ouvrir l'hôtel des ventes. |
| `/ah vendre <prix>` / `/ah sell <prix>` | Confirmer la vente de l’objet tenu ; frais non remboursables de 1 % à la publication, annonce chat optionnelle pour 5 % supplémentaires. |
| `/ah rechercher <nom>` | Rechercher sans accents, avec filtres de catégorie et tri conservé pendant la pagination. |
| `/ah favoris` | Consulter ses dix recherches favorites au maximum ; l’étoile ajoute/retire la recherche courante. |
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
| `/jeutchat` | Voir le jeu de chat en cours et son délai ; répondre dans le chat pour gagner une lootbox divine. |
| `/session` | Consulter la durée, les éliminations et les gains de chasse confirmés, avec rendement par minute. |
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
| `/avantposte visites` | Parcourir les avant-postes publics ; gauche visite, droite ajoute ou retire un favori. |
| `/avantposte favoris` | Retrouver les terrains favoris encore publics. |
| `/avantposte public` / `/avantposte prive` | Ouvrir les visites à tous ou les réserver au propriétaire et aux joueurs de confiance. |
| `/avantposte visite <pseudo>` | Visiter un terrain existant. |
| `/avantposte ajouter <pseudo>` | Autoriser un joueur connu à construire et à utiliser les coffres. |
| `/avantposte retirer <pseudo>` | Retirer cette autorisation. |
| `/avantposte top` | Consulter les propriétaires les plus riches. |
| `/avantposte blocs` | Ouvrir la boutique de blocs depuis le monde des avant-postes. |
| `/avantposte pnj deplacer` | Déplacer son marchand à sa position et orientation, dans son propre terrain. |
| `/avantposte sortir` | Quitter l'avant-poste. |

### Administration : monde, zones et PNJ

Ces commandes nécessitent les permissions administrateur correspondantes. ZNPCsPlus 2.0.0 est nécessaire pour les PNJ. Les hologrammes utilisent Holographic Displays API 3 ou le secours natif configuré.

| Commande | Utilité | Permission |
| --- | --- | --- |
| `/enchere forcer` | Ouvrir immédiatement les trois minutes d’inscription à une nouvelle enchère. | `cliker.enchere.admin` |
| `/enchere demarrer` | Commencer la vente avec les inscrits actuels sans attendre la fin du délai. | `cliker.enchere.admin` |
| `/pinata setspawn` | Définir le point unique de l’arpenteur à sa position, dans la zone Vache. | `cliker.pinata.admin` |
| `/pinata spawn` | Forcer une apparition au point sauvegardé, sans doublon. | `cliker.pinata.admin` |
| `/respawnmobs` | Faire réapparaître tous les mobs placés, sans les boss ni les invocations temporaires ; alias `/mobsrespawn`. | `cliker.mob.admin` |
| `/spawnboss` | Lancer une vague de tous les boss de zone, avec annonce globale ; alias `/bossrespawn`. | `cliker.mob.admin` |
| `/jeutchat lancer [calcul\|devinette\|pays]` / `/jeutchat stop` | Lancer ou interrompre une manche de jeu de chat. | `cliker.jeutchat.admin` |
| `/diagnostic` | Vérifier SQLite, livraisons, mobs manquants, PNJ, hologrammes et destinations absentes ; lecture seule, résultats paginés. | `cliker.diagnostic.admin` |
| `/avantposte vedette <pseudo>` / `/avantposte vedette retirer` | Mettre un terrain public à l’honneur pendant sept jours ou retirer sa mise en avant. | `cliker.services.admin` |
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
| `/upgradebloc set` / `/upgradebloc remove` | Lier ou délier le bloc visé au menu upgrade, avec hologramme. | `cliker.services.admin` |
| `/sacbloc set` / `/sacbloc remove` | Lier ou délier le bloc visé au sac personnel de chaque joueur, avec hologramme. | `cliker.services.admin` |
| `/warp set <zone> <entree\|boss>` | Sauvegarder sa position et son orientation comme destination. | `cliker.services.admin` |
| `/batonspawn` | Ouvrir le choix des créatures : gauche reçoit un bâton de mob, droite un bâton de boss. | `cliker.services.admin` |
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

Le contenu est sauvegardé avec l’inventaire dans un snapshot binaire du playerdata, puis copié dans SQLite à la fermeture du coffre, à la déconnexion
et à l’arrêt du plugin. Sauvegarder et restaurer ces données ensemble. Les objets de l'ancien sac sont automatiquement repris
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

Icônes des boutons : [utilisation des items `icons:*` déjà installés dans ItemsAdder](docs/ICONES-MENUS.md). Aucun pack supplémentaire à installer. Les commandes et leurs permissions sont répertoriées par catégorie ci-dessus.

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

Les créatures placées sont persistantes lors du déchargement des chunks. Une vérification toutes les deux secondes rétablit les créatures manquantes dans les chunks chargés, sans avancer la prochaine vague des boss tués.

Les marchands affichent les vrais objets proposés (épées, armures, compresseurs et ressources). Les boutons de compression montrent la ressource obtenue. Les icônes ItemsAdder de navigation restent actives.

Le marchand de blocs propose des catégories avec pagination. `/tallisement` affiche les découvertes ; le bouton « Non débloqués » ouvre les catégories créatures, boss et cinq familles générales, en préservant le mystère des créatures.

Dans `/ah`, le bouton en bas à gauche ouvre les tris par prix, date, nom et rareté, dans les deux sens ; la page précédente est désormais sur la deuxième case. Le PNJ shop propose Épées, Armures, Potions, Lait et Tous. Shop et marché noir conservent leurs visuels vanilla sur tous les boutons. Le marché noir place ses six offres aux cases 12, 14, 16, 30, 32 et 34 (comptage à partir de 1, avec une ligne vide entre les rangées).
Les PNJ suivent par défaut le joueur le plus proche ; `pnj.regard: FIXED` conserve une orientation fixe si souhaité. `/settings` permet de masquer les autres joueurs, préférence conservée après reconnexion.

### TAB ClickHunter

Le TAB affiche ClickHunter en vert, le nombre de connectés, `play.clickhunter.fr`, le Discord et une invitation à voter. Boutique et vote utilisent provisoirement `https://clickhunter.fr`, modifiable dans `affichage/tab.yml`. Les anciens liens vides sont complétés une fois, sans remplacer les liens personnalisés. Les staff sont en premier, puis Mini-VIP, VIP, VIP+, MVP, MVP+, Legend, Joueur. Ordre et noms de groupes LuckPerms sont configurables (`group.<nom>` en remplacement). La permission `cliker.tab.staff` force le classement staff. Le pseudo précède `[Prestige romain - niveau]` ; prestige nul affiché 0. Couleurs par niveau de 1 à 10, par dizaine jusqu’à 100, par centaine jusqu’à 1000 ; niveaux élevés abrégés en `1.2k`, `5.5k`, `99k`. Les colonnes dépendent du nombre de joueurs et de la taille du client Minecraft, sans fausses entrées. `/settings` conserve le masquage des joueurs. Aucune commande supplémentaire ni dépendance supplémentaire.

Les vagues de boss de zone annoncent à tout le serveur « Tous les boss de zone ont réapparu ! ». À chaque mort d’un boss de zone, le chat indique son nom et le joueur ayant porté le coup fatal. Sans joueur responsable, l’environnement est indiqué. Messages et activation sont configurables dans `activites/boss-zones.yml`, section `annonces` ; les anciennes configurations utilisent automatiquement ces textes par défaut.

### Améliorations UX et fournisseur d’hologrammes

Potions colorées par effet (`boutiques/potions.yml`), achats et drops remis directement dans l’inventaire avec le surplus dans `/claim`, gestion des sacs avec aperçu, favoris AH, visites publiques, détail des bonus actifs des talismans et aperçu des zones débloquées. Les nouveaux boutons Session, Warp et Talismans sont ajoutés au menu principal sans écraser les boutons personnalisés.

Les hologrammes de ClikerV2 passent par l’API 3 d’Holographic Displays, avec secours natif configurable dans `affichage/hologrammes.yml`. Un JAR du fournisseur compatible Paper 1.21.4 reste nécessaire pour valider son rendu réel ; la version Minecraft seule n’identifie pas ce JAR. Les hologrammes créés par les autres plugins ne sont pas modifiés.

Instructions, sauvegardes et limites : [améliorations UX et hologrammes](docs/AMELIORATIONS-UX.md).

Le pied du TAB alterne Site, Boutique, Discord et Vote toutes les dix secondes, avec un indicateur coloré animé (`affichage/tab.yml`, section `animation`). Les aides et jeux de chat sont réglables dans `activites/tchat.yml` : calculs, devinettes Minecraft et langues parlées dans différents pays. Le premier gagnant reçoit une seule lootbox divine via la livraison sécurisée ; inventaire plein ou avant-poste, elle attend dans `/claim`.

Les magma cubes utilisent la taille moyenne 2 et les ghasts une échelle 0,5 par défaut, configurables dans `mobs/creatures.yml`. `/spawnboss` concerne les boss de zone ; le boss public du serveur conserve son seuil de kills. Une vague manuelle recale la prochaine vague automatique selon l’intervalle configuré.


### Audit de fiabilité du 26 octobre 2026

La publication AH utilise un journal de sortie et un reçu playerdata : une interruption avant validation est reprise à la connexion, sans publier ni rembourser deux fois le même objet. Le remboursement rejoint directement l’inventaire si possible, sinon `/claim`. Les annonces existantes restent compatibles. Les callbacks `/claim` vérifient la bonne connexion du joueur, les calculs de prestige sont bornés et les positions YAML invalides sont signalées sans réécriture du fichier. Les icônes de menus inchangées ne sont plus entièrement retraitées à chaque rafraîchissement. Un mob déplacé dans un autre monde est ramené à son point d’origine sans interrompre sa tâche d’immobilisation.

Lors de cet audit, la liste des **58 commandes** et de leurs alias a été comparée à `plugin.yml` ; aucune commande n’est ajoutée par cet audit. [Rapport, tests, installation et limites](docs/AUDIT-2026-10-26.md). Pour installer le JAR : arrêter le serveur, sauvegarder ensemble le dossier du plugin et les mondes/playerdata, remplacer le JAR de ClikerV2, puis redémarrer. Ne pas supprimer les configurations ni la base. Le journal AH ajoute automatiquement une table et un index, sans conversion des anciennes données.


### Piñata de la zone Vache

Un arpenteur immobile et immortel apparaît au point défini par `/pinata setspawn`, toutes les 20 à 30 minutes (délai aléatoire entre apparitions). Il reste trois minutes. Son annonce est cliquable et lance `/pinata tp`. Chaque apparition choisit **un seul type** : Écus, Gemmes Sombres ou XP, commun à tous les joueurs pendant tout l’événement. Les quantités par coup sont tirées dans les bornes configurées.

Plusieurs joueurs peuvent frapper simultanément ; chaque joueur a son propre délai entre coups. Seuls les coups directs de joueurs en survie ou aventure, à portée et en vue de la piñata, rapportent une récompense. Les dégâts des armes ne changent pas le montant gagné. Les crédits sont directs et journalisés ; les récapitulatifs privés restent disponibles à la reconnexion si nécessaire.

Configuration française : `plugins/ClikerV2/activites/pinata.yml` (délais, durée, récompenses, textes, hologramme et point). Les Gemmes Sombres y sont exprimées en gemmes entières. Permission joueur `cliker.pinata.use` accordée par défaut ; administration `cliker.pinata.admin`, opérateurs par défaut. Aucune nouvelle dépendance. [Détails et vérifications](docs/PINATA.md).


### Enchères légendaires toutes les trois heures

`/enchere rejoindre` inscrit le joueur pendant la fenêtre de trois minutes, annoncée plusieurs fois dans le chat avec un lien cliquable. Trois équipements légendaires existants sont ensuite vendus successivement, avec 30 secondes par lot et remise à 30 secondes à chaque offre acceptée. Le double coffre parcourt les montants en serpent, sur plusieurs pages : de 1K jusqu’au plafond configurable de 1 000 000B (1Qa). La tête du meneur occupe son montant et les prix précédents sont verrouillés.

Le solde doit suffire pour déposer une offre. Les offres sont fermes, même après fermeture du menu, `/enchere quitter` ou déconnexion ; seul le gagnant est débité à l’attribution. Les offres devenues insolvables sont écartées au profit de la plus haute offre encore solvable. L’objet est livré directement si possible, sinon conservé dans `/claim`. Les annonces AH et leurs taxes restent un système séparé.

Configuration française : `plugins/ClikerV2/activites/encheres.yml`. Permission joueur `cliker.enchere.use` par défaut ; gestion `cliker.enchere.admin` pour les opérateurs. [Fonctionnement, catalogue et tests](docs/ENCHERES.md). La liste ci-dessus couvre maintenant les **60 commandes principales** déclarées par le plugin, avec leurs sous-commandes et alias.
