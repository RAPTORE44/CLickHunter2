# Version 1.0.11 — Contrats, préparation des raids et bilans personnels

## Contrats progressifs

`/contrats` et le PNJ ouvrent trois offres de chasse : Facile, Normal et Difficile. Elles ciblent la dernière zone à la fois accessible via BuyZone et déjà découverte par une élimination normale. Une zone simplement visible par un administrateur, sans découverte de sa créature, ne révèle pas son nom dans ces offres. Un joueur qui ne connaît encore aucune créature doit commencer par tuer une Vache.

Réglages dans `plugins/ClikerV2/progression/chasse.yml`, section `adaptatifs` :

| Difficulté | Objectif initial | Ajout par zone au-delà de Vache | Multiplicateur des gains |
| --- | ---: | ---: | ---: |
| Facile | 30 | 5 | 1 |
| Normal | 100 | 15 | 3 |
| Difficile | 300 | 40 | 9 |

Les gains utilisent la définition existante `contrats.<mob>-mobs` de cette zone. Les trois monnaies actuelles et l’XP restent configurables. Les éliminations antérieures à l’acceptation ne comptent pas. Clic gauche pour accepter ou réclamer ; clic droit pour épingler le suivi.

Les offres non acceptées sont recalculées à l’ouverture. Après acceptation, cible, objectif et récompenses sont conservés en SQL jusqu’à minuit dans le fuseau configuré. Une offre déjà réclamée ne peut pas être remplacée en débloquant une nouvelle zone : trois emplacements par journée, sans seconde récompense pour le même emplacement. Un ancien écran dont l’offre a changé est refusé puis actualisé. L’accès à la zone est de nouveau vérifié au clic.

Les contrats de l’ancienne version déjà acceptés, y compris les objectifs de boss, restent affichés jusqu’à la fin de leur journée. Les définitions historiques restent dans la configuration ; elles servent aussi de base aux gains des nouveaux contrats. Les nouveaux objectifs adaptatifs portent sur les créatures normales.

## Préparation du raid

Dans `/raid`, chaque tête affiche : état prêt/non prêt, accès à la zone, parchemin valide en main pour le chef, conseil d’épée et nombre de pièces d’armure du niveau conseillé ou supérieur. Les autres membres utilisent le parchemin du chef ; aucun parchemin supplémentaire n’est demandé.

L’état est calculé à partir des conditions de lancement : présence, joueur vivant, inventaire clicker, permission `cliker.raid`, zone débloquée et absence de transfert d’objets en cours pour chaque membre. Le bouton de départ liste les joueurs bloqués et signale aussi les arènes occupées. Les conseils de matériel ne bloquent pas l’entrée. Un transfert en cours bloque le départ avant consommation du parchemin, pour éviter une téléportation partielle du groupe.

L’affichage est actualisé lors des changements de groupe, d’un changement de case de la barre rapide et via le bouton **Actualiser la préparation**. Il ne repose pas sur une nouvelle tâche périodique. Le lancement garde ses vérifications serveur ; l’icône verte ne réserve pas une arène et les droits peuvent avoir changé entre l’affichage et le clic.

## Bilans des boss

Après une victoire, les participants reçoivent un lien privé vers `/bilanboss`. Le menu conserve les **28 derniers bilans** consultables et les records par catégorie : boss de zone, boss de raid et boss serveur, avec distinction de la créature concernée.

Chaque bilan indique les dégâts effectifs, la contribution en pourcentage des dégâts crédités aux participants, les monnaies et l’XP attribués, les objets enregistrés pour livraison, ainsi que les records personnels de dégâts et de contribution. Le nombre de victoires avec participation est conservé séparément. Les records ne sont pas partagés entre joueurs. Les bilans restent consultables après une déconnexion ou un redémarrage ; les combats antérieurs à la version ne sont pas reconstruits.

Les gains ne changent pas : boss de zone au coup final ; boss de raid/public selon leur répartition existante. Un participant à un boss de zone qui n’a pas donné le dernier coup voit ses dégâts, mais aucun gain fictif. Les objets suivent toujours les règles de livraison existantes, dont la suppression du surplus d’œufs quand l’inventaire est plein.

L’ancien podium de dégâts envoyé aux participants et les lignes individuelles de gains des raids sont remplacés par ce lien privé. Les annonces globales d’apparition et de mort déjà configurées restent en place. Aucun nouveau message global n’est ajouté.

## Commande et installation

- `/bilanboss` : ses propres bilans uniquement ; permission `cliker.bilanboss`, accordée par défaut ; aucun alias et aucun argument permettant de consulter autrui.
- `/contrats`, `/raid` et leurs permissions restent inchangés. La liste complète par catégorie est maintenue dans le README.
- Arrêter le serveur, sauvegarder `plugins/ClikerV2` et les playerdata, remplacer `ClikerV2.jar`, puis redémarrer sous Paper 1.21.4 / Java 21.
- Les nouvelles tables sont créées automatiquement dans la base existante. Les messages et réglages manquants des contrats sont ajoutés sans remplacer les valeurs déjà présentes.
- Aucun fournisseur supplémentaire nécessaire. ZNPCsPlus 2.0.0 reste le fournisseur du PNJ ; les icônes disposent de leur remplacement vanilla.

## Vérification

- 489 tests fonctionnels réussis, aucun échec, erreur ou test ignoré.
- Paper 1.21.4 / Java 21 local, avec ZNPCsPlus 2.0.0 et secours vanilla pour les icônes : ouverture du bilan vide par un non OP ; trois offres et acceptation après découverte ; préparation bloquée sans parchemin puis groupe prêt sans parchemin supplémentaire ; boss de zone frappé par deux joueurs, avec lien et bilan privés ; lancement réel d’un raid à deux et bilan avec parchemin de victoire ; conservation des bilans de zone/raid et du contrat accepté après redémarrage. Les réglages simplifiés du raid de test ont été restaurés ensuite. Le combat contre le boss serveur utilise le même chemin de paiement, mais n’a pas été rejoué intégralement sur ce serveur local.
- Cas ajoutés : sélection de la dernière zone accessible et découverte ; trois difficultés croissantes ; changement d’offre avant acceptation ; refus d’un écran périmé ; contrat accepté figé malgré rechargement/changement de configuration ; impossibilité de réclamer deux fois en changeant de zone ; état de préparation du groupe et accès manquant ; parchemin unique du chef ; transfert d’un membre bloquant le départ sans consommer le parchemin ; bilans privés de deux contributeurs ; gains du coup final ; records distincts dégâts/pourcentage ; doublon de décès et rollback SQL.
- Les écritures des bilans et des records utilisent la transaction de paiement existante. Le couple source du boss / UUID empêche le comptage en double. Les dégâts excédant les PV restants ne sont pas crédités. Les contributions des boss de zone restent conservées pendant la mise en veille de leur point de spawn.
- Aucune nouvelle boucle de calcul de contrats ou de bilans : lectures à l’ouverture, écritures au paiement, SQL exécuté hors du thread serveur et historique affiché borné à 28 entrées. Aucun pourcentage de gain CPU/RAM n’est revendiqué.

Les vérifications locales ne remplacent pas une validation sur la production, notamment du rendu des icônes ItemsAdder et de la version installée d’Holographic Displays.
