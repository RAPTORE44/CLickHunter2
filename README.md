# ClikerV2

## Dernière mise à jour — 29 octobre 2026

- Jeux de chat automatiques toutes les quinze minutes ; les lancements manuels administrateur restent possibles.
- `/upgrade set block` et `/bestiaire set block` : raccourcis sur le bloc visé, avec hologramme ; la variante `bloc` est aussi acceptée.
- `/menu` passe à 54 cases, avec les voyages, la progression, l’équipement, le commerce puis les activités. L’ancienne disposition est sauvegardée avant migration et les raccourcis personnalisés sont conservés.
- Niveau/XP en bleu, Écus en jaune, Gemmes en violet dans le scoreboard et la piñata. Retrait du marqueur `{secondes}s` des textes de piñata.
- PNJ donateurs à taille ×2 via l’attribut Minecraft `scale`, transmis avec PacketEvents intégré à ZNPCsPlus 2.0.0. Les hologrammes sont relevés au-dessus des têtes ; seul le rendu est agrandi, les zones d’interaction propres à ZNPCsPlus restent gérées par ce fournisseur.

### Mise à jour précédente — 27 octobre 2026

- Podium manuel de trois PNJ donateurs : rang et pseudo au-dessus du skin, RAPTORE44 par défaut, sans montant affiché. Positions, pseudos et apparences conservés après redémarrage (ZNPCsPlus 2.0.0).

- `/warp set all` lie le bloc visé au menu Warp, avec son hologramme ; alias de `/warpbloc set`.
- PNJ Wiki avec lien vers `https://wiki.clickhunter.fr` et accès au livre des commandes accessibles.
- ATM : total retiré et montant disponible affichés, historique conservé après les prochains changements de tarif.
- Classements et minuteur des boss abaissés de trois blocs ; orientation fixe vers l’est. Ces panneaux utilisent TextDisplay natif car Holographic Displays API 3 ne permet pas de verrouiller cet axe ; les autres hologrammes conservent le fournisseur configuré.
- Piñata : arrivée face au sud, nom jaune pour les Écus, bleu pour l’XP, violet pour les gemmes, sans PV affichés ; son de note block et particules d’émeraude à chaque coup récompensé. Les particules ne créent aucun objet ramassable.

- Jeux de chat : réponses en un mot (ou un nombre pour les calculs), accents et trémas facultatifs. Les anciennes devinettes par défaut à plusieurs mots sont adaptées automatiquement ; une question personnalisée sans réponse en un mot est écartée.
- ATM Marketus : conversion du temps de jeu en Écus, menu `/atm`, PNJ sauvegardé et retrait protégé contre les doubles paiements. Tarif provisoire : 1 000 Écus par tranche de dix minutes, configurable dans `boutiques/atm.yml`.
- `/guide` propose des accès cliquables aux activités et à la progression.
- Le secours natif des hologrammes et les textes de dégâts utilisent un fond transparent. Les noms vanilla des créatures de raid sont masqués dès leur apparition lorsque le fournisseur d’hologrammes est actif.
- [Audit des demandes et écarts encore ouverts](docs/AUDIT-DEMANDES.md).

Le module **BuyZone** crée des portails invisibles d'achat d'accès avec l'économie
intégrée : [configuration des tarifs, commandes et installation](docs/BUYZONE.md).
Les prix d'accès manquants sont proposés à partir des prix actuels des armures, sans modifier les tarifs existants.

Le module **NorthBlue de récompenses journalières** est intégré à ce même plugin :
`/daily`, calendrier de 31 jours et PNJ ZNPCsPlus 2.0.0 optionnel.
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
| `/aide [commande]` | Liste les commandes accessibles par catégorie ; `/aide warp` affiche le détail. |
| `/menu` | Ouvrir le menu principal. |
| `/tuto` | Consulter le livre-guide. |
| `/spawn` | Rejoindre le point d'accueil. |
| `/discord` | Recevoir le lien du Discord. |
| `/wiki` / `/wiki livre` | Recevoir le lien du Wiki et un bouton ouvrant le livre des commandes accessibles, ou ouvrir ce livre directement. |
| `/tag` | Collection des tags : Bugs, Chasse, Temps de jeu, Boss, Popularité et Prestige ; 15 grades par catégorie. Permission **et** progression requises. |
| `/chatcouleur` | Choisir une couleur de parole autorisée ; alias `/chatcolor`. |
| `/bvn` | Souhaiter la bienvenue au dernier **nouveau** joueur sous cinq minutes : 250 Écus et 50 XP, une seule fois par arrivant ; alias `/bienvenue`. |
| `/parrain <pseudo>` | Déclarer son parrain pendant le délai autorisé ; alias `/parrainage`. |

### Mariage et popularité

| Commande | Utilité |
| --- | --- |
| `/marry` / `/marry info` | Voir son partenaire. |
| `/marry help` | Aide cliquable ; `aide` est aussi accepté. |
| `/marry demand <pseudo>` | Demander en mariage : joueur en ligne, célibataire, demande valable 60 secondes ; `demande` est aussi accepté. En février, ou avec un anneau spécial tenu en main hors saison. |
| `/marry accept <pseudo>` | Accepter la demande active de ce joueur. |
| `/marry deny <pseudo>` | Refuser la demande ; `refuser` est aussi accepté. |
| `/marry divorce` / `/marry divorce confirmer` | Aperçu du prix puis confirmation sous 30 secondes : **100 000 000 Écus**, prélevés uniquement si le divorce aboutit. |
| `/marry tp` / `/marry s` | Rejoindre son partenaire / le faire venir, dans le même monde de jeu et hors zones verrouillées. |
| `/marry gift` | Offrir toute la pile tenue en main principale ; partenaire connecté et place suffisante. |
| `/marry dback` | Rejoindre son dernier lieu de mort sauvegardé, si la destination est sûre et accessible. |
| `/marry kiss` | Envoyer un bisou privé et des particules de cœur. |
| `/marry inv` / `/marry ec` | Consulter un instantané de l’inventaire / Enderchest du partenaire connecté, en **lecture seule**. |
| `/rc <joueur>` | Recommander un autre joueur connecté : +1 point et **500 XP pour lui**, après 5 heures de jeu, une fois par heure pour l’émetteur. |
| `/echelons` | Menu des 16 échelons, seuils et progression personnelle. La popularité est aussi affichée au survol du pseudo dans le chat. |

Les couples conservent leurs commandes toute l’année. L’anneau est identifié par le plugin, non consommé, et doit rester en main principale jusqu’à l’acceptation hors février. Le fuseau par défaut est Europe/Paris. Les commandes de transfert et de consultation ne traversent pas l’isolation des avant-postes ou des arènes. Les cadeaux sont livrés par le journal existant : inventaire directement, `/claim` si le destinataire se déconnecte ou n’a plus de place entre-temps. [Configuration et règles](docs/COMMUNAUTE.md).

### Récompenses et stockage

| Commande | Utilité |
| --- | --- |
| `/daily` | Ouvrir le calendrier et réclamer la récompense disponible. |
| `/claim` | Récupérer les récompenses en objets mises en attente. |
| `/atm` | Ouvrir l’ATM et retirer les tranches de temps de jeu disponibles. Les minutes restantes sont conservées ; le temps déjà payé ne peut pas être payé à nouveau. Le compteur utilise le temps Minecraft total, y compris le temps passé immobile. |
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
| `/contrats` | Clic gauche pour accepter/réclamer ; clic droit sur une créature découverte pour épingler sa progression dans la barre d'action. |
| `/pinata` / `/pinata tp` | Voir la piñata active et la rejoindre ; alias `/pignata`. |
| `/raid` | Ouvrir son groupe de raid (1 à 8 joueurs). Le boss affiche sa jauge de vie ; à la victoire, les participants voient le top 5 des dégâts et leur récapitulatif individuel de gains. |
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
| `/enchere` / `/enchere rejoindre` | Consulter l’événement et rejoindre la salle pendant les trois minutes d’inscription ; **entrée : 10 000 000 Écus**, une seule fois par événement, non remboursable ; alias `/encheres`. |
| `/enchere quitter` / `/enchere quit` | Se désinscrire, fermer le menu et retourner au spawn ; une offre déjà déposée reste ferme. |
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
| `/guide` | Conseiller le prochain palier d’amélioration selon les niveaux et le solde réel, puis proposer des accès cliquables au bestiaire, aux contrats, aux zones, aux raids et au résumé de session. Aussi accessible dans `/menu`. |
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
| `/avantposte agrandir` | Voir la taille et le prix suivants, puis confirmer ou annuler l'achat dans le menu. |
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
| `/marry objet <joueur>` | Donner l’anneau spécial (également depuis la console) ; livraison inventaire, sinon `/claim`. | `cliker.marry.admin` |
| `/enchere setsalle` | Définir le point d’arrivée commun et le centre de la salle, avec son orientation. | `cliker.enchere.admin` |
| `/enchere setpresentoir` | Définir l’objet flottant ; repli à trois blocs du centre si non défini. | `cliker.enchere.admin` |
| `/tag bugs <joueur> <nombre>` | Définir le total de bugs validés d’un joueur connu. | `cliker.tag.admin` |
| `/warpbloc set` / `/warpbloc remove` | Définir/retirer un raccourci Warp avec hologramme en visant un bloc à moins de cinq blocs. | `cliker.services.admin` |
| `/enchere forcer` | Ouvrir immédiatement les trois minutes d’inscription à une nouvelle enchère. | `cliker.enchere.admin` |
| `/enchere demarrer` | Commencer la vente avec les inscrits actuels sans attendre la fin du délai. | `cliker.enchere.admin` |
| `/pinata settp` | Définir séparément la destination du lien cliquable de la piñata. | `cliker.pinata.admin` |
| `/pinata setspawn` | Définir le point unique de l’arpenteur à sa position, dans la zone Vache. | `cliker.pinata.admin` |
| `/pinata spawn` | Forcer une apparition au point sauvegardé, sans doublon. | `cliker.pinata.admin` |
| `/respawnmobs` | Faire réapparaître tous les mobs placés, sans les boss ni les invocations temporaires ; alias `/mobsrespawn`. | `cliker.mob.admin` |
| `/spawnboss` | Lancer une vague de tous les boss de zone, avec annonce globale ; alias `/bossrespawn`. | `cliker.mob.admin` |
| `/jeutchat lancer [calcul\|devinette\|pays]` / `/jeutchat stop` | Lancer ou interrompre une manche de jeu de chat. | `cliker.jeutchat.admin` |
| `/diagnostic` | Vérifier SQLite (durée de la requête), TPS, tâches planifiées, livraisons, mobs manquants, PNJ, hologrammes et destinations absentes ; indique la commande de correction et la commande Spark si Spark manque. Lecture seule, résultats paginés. | `cliker.diagnostic.admin` |
| `/avantposte vedette <pseudo>` / `/avantposte vedette retirer` | Mettre un terrain public à l’honneur pendant sept jours ou retirer sa mise en avant. | `cliker.services.admin` |
| `/gm [0\|1\|2\|3] [joueur]` | Changer de mode ; sans argument, basculer entre survie et créatif. Modes : 0 survie, 1 créatif, 2 aventure, 3 spectateur. | `cliker.gamemode.admin` |
| `/setspawn` | Définir le point d'accueil à la position actuelle. | `cliker.spawn.admin` |
| `/buyzone spawn <zone>` | Placer un portail d'achat pour une zone configurée. | `cliker.buyzone.admin` |
| `/buyzone remove <zone>` | Supprimer ce portail. | `cliker.buyzone.admin` |
| `/daily pnj spawn` / `/daily pnj remove` | Placer ou supprimer le PNJ des récompenses. | `cliker.daily.admin` |
| `/atm pnj spawn` / `/atm pnj remove` | Placer ou supprimer Marketus à sa position. | `cliker.atm.admin` |
| `/donateurs set <1\|2\|3> <pseudo>` | Associer un joueur au rang ; met à jour son nom et son skin, sans déplacer le PNJ. Console et joueurs ; alias `/topdonateurs`. | `cliker.donateurs.admin` |
| `/donateurs spawn <1\|2\|3>` | Placer le PNJ de ce rang à sa position ; refuse les doublons. | `cliker.donateurs.admin` |
| `/donateurs remove <1\|2\|3>` | Retirer ce PNJ pour le déplacer ; conserve le joueur associé. | `cliker.donateurs.admin` |
| `/donateurs list` | Consulter les trois joueurs associés et les PNJ placés ; également affiché par `/donateurs`. | `cliker.donateurs.admin` |
| `/wiki pnj spawn` / `/wiki pnj remove` | Placer ou supprimer le PNJ Wiki. Lien et textes dans `wiki.yml`. | `cliker.wiki.admin` |
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
| `/upgrade set block` / `/upgrade set bloc` | Lier le bloc visé au menu upgrade ; alias de `/upgradebloc set`. | `cliker.services.admin` |
| `/bestiaire set block` / `/bestiaire set bloc` | Lier le bloc visé au bestiaire, avec hologramme. | `cliker.services.admin` |
| `/bestiairebloc set` / `/bestiairebloc remove` | Placer ou retirer le raccourci du bestiaire sur le bloc visé. | `cliker.services.admin` |
| `/upgradebloc set` / `/upgradebloc remove` | Lier ou délier le bloc visé au menu upgrade, avec hologramme. | `cliker.services.admin` |
| `/sacbloc set` / `/sacbloc remove` | Lier ou délier le bloc visé au sac personnel de chaque joueur, avec hologramme. | `cliker.services.admin` |
| `/warp set all` | Lier le bloc visé à moins de cinq blocs au menu Warp avec hologramme. | `cliker.services.admin` |
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

Le pied du TAB alterne Site, Boutique, Discord et Vote toutes les dix secondes, avec un indicateur coloré animé (`affichage/tab.yml`, section `animation`). Les aides et jeux de chat sont réglables dans `activites/tchat.yml` : calculs, devinettes Minecraft et langues parlées dans différents pays. Chaque réponse configurée doit être un seul mot (ou nombre), sans espace ni tiret ; plusieurs synonymes d'un mot sont possibles. Les majuscules, accents et trémas sont ignorés : `francais` accepte `français`, `noel` accepte `Noël`. Les questions sans réponse valide sont écartées, avec un calcul en remplacement si la catégorie devient vide. La consigne est personnalisable avec `messages.consigne`. Le premier gagnant reçoit une seule lootbox divine via la livraison sécurisée ; inventaire plein ou avant-poste, elle attend dans `/claim`.

Les magma cubes utilisent la taille moyenne 2 et les ghasts une échelle 0,5 par défaut, configurables dans `mobs/creatures.yml`. `/spawnboss` concerne les boss de zone ; le boss public du serveur conserve son seuil de kills. Une vague manuelle recale la prochaine vague automatique selon l’intervalle configuré.


### Audit de fiabilité du 26 octobre 2026

L’audit du 26 octobre couvre aussi les gains de morts de mobs, les coffres, le calendrier à 31 jours, les enchères, les niveaux d’amélioration, les sorties d’inventaire et les protections d’avant-poste. L’inscription d’enchère annulée par un refus de téléportation rembourse uniquement ses propres frais. Les détails, le nombre de tests et les limites vérifiées sont dans [le rapport complet](docs/AUDIT-COMPLET-2026-10-26.md).

La liste des **68 commandes principales** et de leurs alias est classée par catégorie ci-dessus. Pour installer le JAR : arrêter le serveur, sauvegarder ensemble le dossier du plugin et les mondes/playerdata, remplacer le JAR de ClikerV2, puis redémarrer. Ne pas supprimer les configurations ni la base. Les migrations conservent les données existantes et ajoutent seulement les colonnes ou tables nécessaires.

### Aide, suivi et confirmations

`/aide` ne liste que les commandes permises par LuckPerms et les permissions Bukkit effectives ; `/aide <commande>` affiche son usage et propose de saisir la commande. Dans `/contrats`, le clic gauche conserve l'acceptation/la réclamation et le clic droit épingle ou retire le suivi. La barre d'action indique l'objectif, reprend à la reconnexion et ne compte que les éliminations confirmées en base. Une extension d'avant-poste affiche sa nouvelle surface et son prix avant le bouton de confirmation ; annuler ne touche pas au solde. À la victoire du boss de raid, les participants voient le top 5 des dégâts puis le message individuel de récompense, alloué comme avant.


### Piñata de la zone Vache

Un arpenteur immobile doté de PV configurables apparaît au point défini par `/pinata setspawn`, toutes les 20 à 30 minutes (délai aléatoire entre apparitions). Il reste au maximum trois minutes, ou jusqu’à épuisement de ses PV. Son annonce est cliquable et lance `/pinata tp`. Chaque apparition choisit **un seul type** : Écus, Gemmes Sombres ou XP, commun à tous les joueurs pendant tout l’événement. Les quantités par coup sont tirées dans les bornes configurées.

Plusieurs joueurs peuvent frapper simultanément ; chaque joueur a son propre délai entre coups. Seuls les coups directs de joueurs en survie ou aventure, à portée et en vue de la piñata, rapportent une récompense. Les dégâts des armes ne changent pas le montant gagné. Les crédits sont directs et journalisés ; la hotbar affiche les gains personnels cumulés et les récapitulatifs privés restent disponibles à la reconnexion si nécessaire. `/pinata settp` définit une arrivée séparée. Le titre partagé affiche les PV sans chrono et remplace le nom vanilla pour éviter le doublon.

Configuration française : `plugins/ClikerV2/activites/pinata.yml` (délais, durée, récompenses, textes, hologramme et point). Les Gemmes Sombres y sont exprimées en gemmes entières. Permission joueur `cliker.pinata.use` accordée par défaut ; administration `cliker.pinata.admin`, opérateurs par défaut. Aucune nouvelle dépendance. [Détails et vérifications](docs/PINATA.md).


### Enchères légendaires toutes les trois heures

Le staff définit `/enchere setsalle` et, idéalement, `/enchere setpresentoir`. Les inscriptions durent trois minutes, avec plusieurs annonces cliquables. `/enchere rejoindre` prélève **10 000K = 10 000 000 Écus** une seule fois par événement et téléporte dans la salle commune. Les frais sont annoncés avant inscription et ne sont pas remboursés. Le compte à rebours apparaît dans la hotbar ; un seul objet légendaire flotte sur le présentoir. Son clic droit rouvre les mises du lot courant après le début.

Au début de chaque lot, le double coffre s’ouvre automatiquement aux joueurs présents. Le menu contient les montants et la page suivante : parcours descendant à gauche, deux cases à droite, remontée, et ainsi de suite (**29 montants par page**). Les prix dépassés disparaissent, la tête du meneur occupe sa mise et atteindre le dernier montant d’une page entraîne le passage automatique à la suivante. Le chronomètre reste dans la hotbar, avec 30 secondes relancées par offre.

Trois équipements légendaires existants sont vendus successivement. Aucune obligation de miser sur chaque objet. Les offres restent fermes après fermeture, déconnexion ou `/enchere quit` ; seul le gagnant solvable est débité pour le lot. Quitter supprime le droit de déposer de nouvelles offres et renvoie au spawn. Les lots sont livrés dans l’inventaire si possible, sinon `/claim`. En fin de vente, les participants encore présents retournent au spawn. Configuration : `activites/encheres.yml`. [Règles détaillées](docs/ENCHERES.md).

La liste ci-dessus couvre les **68 commandes principales** déclarées par le plugin, leurs alias et sous-commandes.


### Configuration communautaire

`plugins/ClikerV2/communaute/social.yml` regroupe les tarifs, messages français, calendrier, objet spécial, XP et les 16 échelons. Permissions joueur par défaut : `cliker.marry.use`, `cliker.marry.teleport`, `cliker.marry.consulter`, `cliker.popularite.use`. Administration : `cliker.marry.admin`. Aucune dépendance supplémentaire. Modifier les fichiers puis redémarrer ; conserver SQLite et les playerdata ensemble dans les sauvegardes.


### Warps, marché noir, tags et accueil

Le menu Warp masque complètement les destinations non accessibles. Chaque zone possède `cliker.zone.<identifiant>` ; seule `cliker.zone.vache` est accordée à tous par défaut. Un refus explicite LuckPerms reste prioritaire pour l’affichage. Les achats de zone existants continuent d’appliquer leurs permissions internes ; les droits de voyage sont revérifiés au clic. `/warpbloc set` ajoute un raccourci physique avec hologramme. Le marché noir permet désormais l’achat des lots quelle que soit leur zone d’origine, avec les mêmes prix et stocks. Les éventuelles conditions d’utilisation des œufs restent distinctes de l’achat.

Les noms du shop suivent les couleurs des matériaux/potions, sans italique, avec description et prix différenciés. Les tags et couleurs sont sauvegardés dans SQLite. Permissions : `cliker.tag.<categorie>.<grade>` (grade 1 à 15), `cliker.chat.couleur.<couleur>` ; groupes `cliker.tag.*` et `cliker.chat.couleur.*` pour tout accorder. Les tags réclament toujours la progression, même avec la permission. Les compteurs de bugs sont validés par le staff, pas par les joueurs. Configuration et seuils : `communaute/tags.yml`, `communaute/bienvenue.yml` et `communaute/tchat.yml`.

Un nouveau joueur reçoit un message global de bienvenue. `/bvn` ou `/bienvenue` accueille le dernier arrivant dans les cinq minutes ; les reconnexions, répétitions et auto-bienvenues ne versent aucune récompense. [Détails et permissions](docs/COMMUNAUTE.md).

Les positions enregistrées des classements restent les points d’ancrage. `affichage/classements.yml:decalage-vertical` vaut `-3` par défaut ; ce décalage de rendu ne se cumule pas aux redémarrages. Les anciens totaux ATM, dont seul le temps retiré était stocké, sont reconstruits au tarif actuel ; les futurs retraits conservent le montant réellement versé dans SQLite. Le montant disponible est actualisé à l’ouverture du menu et après un retrait.

Les PNJ donateurs utilisent `pnj/donateurs-1.yml`, `pnj/donateurs-2.yml` et `pnj/donateurs-3.yml`. `joueur` détermine simultanément le pseudo et le skin Minecraft ; `titre-rang` et `format-pseudo` personnalisent les deux lignes (`{joueur}` dans le format). Le classement est mis à jour manuellement, sans montant ni calcul automatique. Les modifications SFTP sont relues au redémarrage ; `/donateurs set` actualise immédiatement un PNJ déjà placé. Le chargement du skin dépend de ZNPCsPlus et de l’existence du profil Minecraft.

Les Cendres servent à appliquer ou remplacer une rune sur l’équipement dans `/rune` : coût actuel **500 Cendres** par tentative. Les runes donnent des bonus selon l’équipement ; les détails sont accessibles depuis le livre d’information de cette table. Les réglages des raccourcis bestiaire sont sauvegardés dans `navigation/warps.yml` (`bestiaires` et `hologrammes.bestiaires`). Les commandes de placement nécessitent de viser un bloc à moins de cinq blocs. La taille des PNJ donateurs se règle avec `pnj.echelle` dans chacun des fichiers `pnj/donateurs-N.yml` (2 par défaut).
