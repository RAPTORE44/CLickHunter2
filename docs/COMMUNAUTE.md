# Mariage et popularité

Modules inclus dans ClikerV2 pour Paper 1.21.4 / Java 21, sans nouvelle dépendance. Les commandes complètes, alias de sous-commandes et permissions figurent dans le [README](../README.md).

## Installation et configuration

Arrêter le serveur, sauvegarder `plugins/ClikerV2` et les playerdata/mondes, remplacer `ClikerV2.jar`, puis redémarrer. Le fichier `plugins/ClikerV2/communaute/social.yml` est créé sans remplacer un fichier existant. Le modifier puis redémarrer. Une configuration numérique, un matériau, fuseau ou tableau d’échelons invalide désactive les commandes communautaires avec une erreur explicite dans les logs ; les autres modules restent disponibles.

Les nouvelles tables SQLite sont additives : `social_marriages`, `social_deaths`, `social_popularity`, `social_recommenders`. Mariages et recommandations utilisent les UUID ; changer de pseudo ne remet pas la progression à zéro. Une sauvegarde cohérente doit conserver base SQLite, playerdata, sacs et mondes ensemble.

## Mariage

- Le propriétaire a confirmé : **seules les nouvelles demandes sont limitées à février**. Les couples gardent leurs commandes toute l’année. `mariage.commandes-saisonnieres` est désactivé par défaut.
- Calendrier réel Europe/Paris, configurable ; il ne s’agit pas d’un délai de 365 jours ni d’une permission OP qui contourne la date.
- Hors février, l’initiateur doit tenir en main principale l’objet authentique donné par `/marry objet <joueur>`, pendant la demande et à l’acceptation. Le marqueur PDC ne peut pas être reproduit en renommant une améthyste. **L’anneau n’est pas consommé** : le besoin indiqué est de le tenir en main. Son matériau, nom et description sont configurables ; ne pas changer le matériau au milieu d’une distribution sans remplacer les anciens objets.
- Demande privée cliquable, expiration après 60 secondes, refus possible. Une seule demande impliquant un joueur à la fois ; déconnexion annule une demande en attente. Les demandes ne survivent pas au redémarrage ; les mariages oui.
- Deux lignes réciproques sont créées dans une transaction, avec unicité sur les deux UUID. Une acceptation concurrente ne peut pas créer plusieurs couples.
- Le dernier tarif fourni, **100 000 000 Écus**, remplace la mention initiale de 10 000. `/marry divorce` affiche le coût ; `/marry divorce confirmer` valide sous 30 secondes. Débit conditionnel et suppression des deux liens sont atomiques. Ni fonds insuffisants ni double confirmation ne doivent prélever deux fois. Un ancien formulaire ne peut pas dissoudre un nouveau mariage.

### Commandes entre partenaires

Les téléportations exigent un partenaire disponible, un même monde, un chunk existant et une arrivée sans vide, liquide, obstacle ou bloc dangereux. La commande `s` fait venir le partenaire ; se marier active cette fonction réciproque. Permission de téléportation requise pour le joueur déplacé. Les protections Bukkit peuvent annuler le déplacement.

Les téléportations sociales vérifient aussi les portails BuyZone enregistrés : destination dans un portail verrouillé, segment traversant celui-ci ou arrivée au sud de son couloir refusés. Cette règle suit la carte orientée au sud demandée précédemment ; configurer correctement les portails reste nécessaire, car ils ne décrivent pas toutes les limites d’une zone de terrain arbitraire. Les avant-postes et mondes d’arène sont exclus. Le chunk d’arrivée, s’il est déchargé, est chargé de façon asynchrone, sans génération de terrain et avec une seule demande en cours par joueur. Le mariage et les permissions sont revérifiés après ce chargement ; aucun monde n’est créé.

`dback` utilise le dernier lieu de mort du partenaire sauvegardé **depuis l’installation du module**, y compris après redémarrage et lorsque le partenaire est hors ligne. Les destinations inaccessibles ou dangereuses sont refusées. Une nouvelle mort remplace la précédente.

`gift` transfère **toute la pile** tenue en main principale. Le partenaire doit être connecté et disposer initialement de la place nécessaire. Les objets protégés, transferts déjà en cours et inventaires isolés sont refusés. Le journal de sortie existant sauvegarde le retrait et son reçu dans le playerdata avant le commit SQL ; ce commit valide ensemble le lien matrimonial, le reçu et la livraison. Si le lien a changé, le cadeau est rendu une seule fois via le mécanisme de reprise. Si le destinataire devient plein ou hors ligne entre-temps, l’objet reste dans `/claim`. Aucun objet n’est jeté au sol.

`inv` et `ec` montrent une **copie en lecture seule**, non une ouverture modifiable de l’inventaire réel. Les clics, touches numériques, échanges main secondaire, doubles clics et glissements sont annulés. Le menu se ferme si le partenaire se déconnecte ou si le divorce aboutit. Le contenu ne s’actualise pas en continu ; le rouvrir pour un nouvel instantané. Les objets conservent leur véritable apparence, sans substitution par les icônes de navigation.

`kiss` produit un message privé et des cœurs visibles par les deux partenaires. Un délai configurable limite les bisous, téléportations et cadeaux répétés.

## Popularité

`/rc <joueur>` vise un autre joueur connecté et visible. L’émetteur doit avoir cinq heures de temps de jeu selon les statistiques du serveur. Une simple ancienneté de compte ne suffit pas. Un seul destinataire peut être recommandé par heure, quelle que soit la personne choisie ; le délai persiste après reconnexion et redémarrage. Un recul de l’horloge serveur n’ouvre pas un nouveau créneau.

Le **destinataire** reçoit +1 recommandation et 500 XP réels du système existant, pas 500 niveaux. Le point, l’horodatage et le crédit XP sont validés dans la même transaction : une erreur annule tout. Les XP restent dus si le destinataire se déconnecte pendant le traitement, et leur application est différée si son inventaire est isolé. Aucun bonus de monnaie non demandé n’est ajouté.

Les 16 seuils demandés sont repris : 0, 10, 25, 50, 100, 250, 500, 750, 1000, 1500, 2000, 3000, 5000, 7500, 10000 et 20000. Le menu `/echelons` présente les seuils, acquis et points manquants. Au survol du pseudo dans le chat : nombre de recommandations et échelon. Un son privé accompagne la recommandation ; une annonce serveur n’est envoyée que lors du franchissement d’un seuil.

Le rendu du chat lit un cache concurrent des joueurs connectés, sans SQL ni accès à l’inventaire sur le thread asynchrone du chat. Aucun balayage périodique de toute la base n’est effectué. Les noms et messages sont du texte, pas des commandes console interpolées.

Les paramètres et messages sont en français dans `communaute/social.yml`. Les émojis des échelons dépendent des caractères pris en charge par le client et son pack de polices.

## Vérification

Tests automatisés : calendrier Europe/Paris aux changements de mois et année bissextile, validation de configuration, unicité et persistance des unions, demandes concurrentes/expirées/refusées, anneau authentique obligatoire hors saison, confirmation et débit unique du divorce, cadeaux journalisés avec reprise, inventaires en lecture seule, recommandations concurrentes, délai global persistant, seuil de cinq heures, récompense réservée au destinataire et rollback en cas de dépassement XP. Le test BuyZone vérifie le refus de sauter un portail verrouillé par téléportation sociale.

Les résultats d’intégration sur Paper sont conservés séparément dans `docs/validation/communaute.json`. Ils ne constituent pas une validation du pack ItemsAdder ou des polices du serveur public.

## Tags et couleur de parole

`/tag` ouvre les catégories Bugs, Chasse, Temps de jeu, Boss, Popularité et Prestige. Chaque catégorie contient **15 grades**, soit 90 tags. Les objectifs et couleurs sont modifiables dans `communaute/tags.yml`. Pour équiper un tag, il faut **à la fois** atteindre son objectif et posséder `cliker.tag.<categorie>.<grade>` : `bugs`, `mobs`, `jeu`, `boss`, `popularite` ou `prestige`, grade 1 à 15. `cliker.tag.*` donne les permissions de tous les grades, sans supprimer leurs objectifs.

Les kills, boss et prestiges proviennent des compteurs existants ; le temps de jeu vient des statistiques Paper. `/tag bugs <joueur> <nombre>` définit le total de bugs **validés par le staff**, avec `cliker.tag.admin`. Ce compteur n’est jamais augmenté par les joueurs eux-mêmes. Le tag Bugs montre le nombre validé. Le tag choisi et la couleur sont sauvegardés par UUID, affichés après le pseudo, prestige et niveau dans le chat. Le menu permet de retirer son tag.

`/chatcouleur` (alias `/chatcolor`) présente seize couleurs, en français. `cliker.chat.couleur.<identifiant>` autorise une couleur ; `cliker.chat.couleur.*` les autorise toutes. Identifiants : `white`, `black`, `gray`, `dark_gray`, `blue`, `dark_blue`, `aqua`, `dark_aqua`, `green`, `dark_green`, `red`, `dark_red`, `gold`, `yellow`, `light_purple`, `dark_purple`. Le blanc est accessible par défaut. Les menus empêchent la sélection sans permission ; un droit révoqué retire l’effet visuel au prochain rafraîchissement.

Les profils sont rafraîchis en lot toutes les 30 secondes (configurable), les permissions et niveaux toutes les cinq secondes. Le rendu asynchrone du chat ne fait aucune requête SQL et ne lit pas les inventaires. Ouvrir un menu recharge la progression pour éviter d’attendre le rafraîchissement périodique.

## Accueil des nouveaux joueurs

À la **première connexion**, un message global cliquable invite à `/bvn`. La commande et son alias `/bienvenue` accueillent le dernier nouveau joueur pendant cinq minutes maximum. Par défaut, l’émetteur reçoit **250 Écus et 50 XP**, configurables dans `communaute/bienvenue.yml`.

Une seule récompense par couple émetteur/nouvel arrivant. Les reconnexions ne créent pas une nouvelle occasion ; l’auto-bienvenue, un délai expiré ou un arrivant remplacé par le suivant ne paient pas. La validation du message de bienvenue et des gains repose sur un reçu SQL unique ; même deux commandes concurrentes ne doivent pas payer deux fois. L’occasion active et ses reçus survivent au redémarrage, sans prolonger les cinq minutes. Permission joueur : `cliker.bienvenue.use`, accordée par défaut.
