# 500 succès et lama piñata — version 1.0.16

## Collection personnelle

`/succes` ouvre les catégories, `/succes tout`, `/succes debloques` et `/succes a-faire` ouvrent la liste filtrée. Alias : `/achievements`. Permission `cliker.succes`, accordée à tous par défaut. Les OP ne disposent pas d’une commande de validation artificielle. Raccourci dans `/menu`, emplacement 46 (comptage à partir de zéro), et aide dans `/aide`.

La liste possède 28 cases de contenu par page, les pages précédente/suivante, le filtre et le retour aux catégories. Chaque objectif montre sa condition, sa progression, son état et sa date de déblocage. Les icônes sont en lecture seule. Une créature jamais éliminée reste anonyme dans ses objectifs ; les paliers déjà obtenus restent visibles.

| Domaine | Nombre | Contenu |
| --- | ---: | --- |
| Créatures | 190 | 19 créatures × 10 paliers, de 1 à 1 000 000 éliminations |
| Boss de zone | 190 | 19 boss × 10 paliers, de 1 à 2 500 victoires |
| Objectifs transversaux | 120 | 20 séries de 6 paliers |
| Total | **500** | IDs stables et déblocages sauvegardés par UUID |

Les vingt séries transversales couvrent les éliminations globales, boss de zone, coups portés, temps de jeu, niveaux, prestige, zones accessibles, talismans permanents, contrats réclamés, victoires de raid avec contribution, victoires du boss serveur avec contribution, Écus et Gemmes gagnés en chasse, XP gagnée, courriers envoyés, temps AFK, coups de piñata, Écus retirés à l’ATM, paliers d’avant-poste et accueils `/bvn`. La création d’un avant-poste compte pour son premier palier, puis ses cinq extensions.

Il s’agit de **succès honorifiques**, sans gain économique automatique ni objet à réclamer. Ils n’ajoutent pas 500 advancements Minecraft permanents au serveur. La notification utilise le système existant de toast de succès Minecraft privé ; un lot de plusieurs objectifs ne crée qu’une notification récapitulative, soumise aux limites de la file existante. Tout reste consultable même si une notification est désactivée ou non affichée.

## Statistiques et sauvegarde

Le système reprend les données déjà présentes : bestiaire, compteurs de victoires, prestige, XP, talismans enregistrés, contrats et activités. Les meilleurs paliers observés restent acquis lorsque le niveau ou les accès changent. Le niveau le plus élevé observé dans la session est retenu même si le joueur passe un prestige avant la prochaine vérification.

Le compteur des courriers envoyés est validé dans la même transaction que l’envoi. Les lettres encore présentes sont importées une seule fois lors de la migration ; effacer une lettre ensuite ne retire pas ce crédit. Les anciens courriers déjà supprimés et les anciens niveaux maximaux qui n’ont jamais été sauvegardés ne peuvent pas être reconstruits. Les autres statistiques historiques sont limitées aux données réellement conservées, sans inventer de progression.

Les déblocages et les nouvelles valeurs maximales sont enregistrés dans une transaction SQLite avec des clés uniques : ouvrir plusieurs fois le menu ne valide pas deux fois un succès. Aucune mutation d’inventaire, aucun envoi réseau et aucun appel Bukkit ne sont faits depuis la file SQL.

Les passages automatiques sont espacés de 60 secondes par défaut (minimum 30), et répartis à raison d’au maximum un joueur soumis toutes les 5 ticks. Les données de menu restent en cache pendant la connexion. Aucun monde ou chunk n’est chargé pour suivre un succès, aucune requête n’est ajoutée aux coups de mêlée et seuls les compteurs modifiés sont réécrits. Les gros serveurs peuvent observer un délai supérieur à l’intervalle si beaucoup de joueurs sont dus simultanément. Les menus ne se réouvrent pas à chaque passage automatique.

Les noms et seuils des 500 objectifs sont dans `plugins/ClikerV2/progression/succes.yml`. Garder leurs IDs pour conserver les déblocages. Les seuils d’une même série doivent rester strictement croissants et positifs ; les réglages invalides sont signalés. Modifier un seuil ne retire jamais un déblocage déjà enregistré. Redémarrer après modification du fichier. `notifications: false` coupe les notifications de nouveaux succès, pas leur sauvegarde.

## Lama piñata

La piñata est désormais un lama adulte immobile, silencieux et lumineux. Son pelage tourne entre les quatre variantes vanilla, avec des tapis colorés. Chaque coup réellement validé déclenche une rotation de l’apparence et du contour si au moins **500 ms réelles** se sont écoulées depuis la dernière rotation, tous attaquants confondus. Les coups pendant ce délai gardent leurs gains et effets habituels ; ils ne déclenchent pas de tâche différée ni une rafale de changements ensuite.

Le contour coloré utilise une petite équipe dédiée sur le scoreboard de chaque joueur présent à proximité (128 blocs). La liste des tableaux est rafraîchie au maximum une fois par seconde ; un joueur qui arrive ou change de scoreboard reçoit donc le contour au passage suivant. Les équipes dédiées sont supprimées quand elles ne servent plus, lors de la mise en veille, à la fin et à l’arrêt du plugin. Aucun changement de scoreboard joueur ni de son équipe de grade n’est nécessaire.

Le nom conserve sa couleur selon l’événement : jaune pour les Écus, bleu pour l’XP, violet pour les Gemmes Sombres ; le contour peut changer indépendamment. Les secondes restantes restent visibles. Les gains restent d’un seul type par apparition, avec le récapitulatif privé, les sons de note block et les particules d’émeraude.

Commandes conservées : `/pinata`, `/pinata tp` ; staff `/pinata setspawn`, `/pinata settp`, `/pinata spawn`, permission `cliker.pinata.admin`. Alias `/pignata`. Les points existants restent sauvegardés. Seule l’ancienne phrase standard « Un arpenteur piñata est apparu » devient « Un lama piñata est apparu » dans la configuration existante ; les autres textes personnalisés sont conservés.

## Installation

Arrêter le serveur, sauvegarder `plugins/ClikerV2` et les données des joueurs, remplacer `ClikerV2.jar`, puis redémarrer sous Paper 1.21.4 / Java 21. Le nouveau fichier de succès et le raccourci sont installés automatiquement, sans effacer les anciennes données. Les nouvelles icônes passent par le système ItemsAdder `icons:*` déjà utilisé, avec remplacement vanilla ; le lama utilise les textures Minecraft classiques.

## Validation

542 tests fonctionnels réussis, sans échec, erreur ni test ignoré. Les nouveaux tests couvrent le catalogue et ses 500 IDs, la cohérence du YAML, la reprise historique, les déblocages concurrents sans doublon, la conservation après prestige/rechargement, les unités de monnaie et victoires, les courriers supprimés, les filtres/pagination/permissions/menus non récupérables, la migration du raccourci et la limite globale de 500 ms du lama avec nettoyage des scoreboards.

Essais clients sur Paper 1.21.4 : accès non OP, pagination, raccourci ajouté à un menu existant, lama avec contour lumineux sur scoreboard individuel, changements de couleur limités, secondes conservées et filtre après reconnexion. [Résultats](validation/succes-pinata-1.0.16.json). Les textures ItemsAdder du serveur de production ne sont pas installées localement ; leur rendu n’est pas revendiqué comme testé.
