# Consignes du dépôt

## README et commandes — demande permanente du propriétaire

À chaque modification du plugin, vérifier et maintenir dans `README.md` la liste complète des commandes classées par catégorie. Mettre à jour les syntaxes, sous-commandes, alias, permissions administrateur et descriptions dès qu'un comportement change. Comparer cette liste à `src/main/resources/plugin.yml` et aux gestionnaires des commandes ; ne pas documenter de commandes inexistantes. Si la modification ne change aucune commande, vérifier que la liste reste exacte sans ajouter de faux changement documentaire.

Conserver cette consigne pour les prochaines interventions. Documenter aussi les interactions sans commande lorsqu'elles remplacent une commande ou expliquent l'utilisation d'une fonctionnalité.

## Périmètre confirmé du classeur d’idées

Le propriétaire a confirmé de conserver les demandes antérieures en cas de contradiction : dégâts qui montent d’un bloc puis disparaissent, piles de 64 et compression 64 → 1, autocompresseur consommé à l’activation. Appliquer les autres lignes jaunes uniquement aux modules existants. Laisser en attente les fondations absentes (pets, Ordres, instances de boss, réseau multi-serveurs). Les lignes non jaunes restent des propositions à valider explicitement. Suivre les livraisons réelles dans `docs/SELECTION-JAUNE.md` sans présenter les 141 idées comme terminées.

## Icônes des menus

Les textures et items `icons:*` fournis sont déjà installés dans ItemsAdder sur le serveur. Utiliser directement ces items via l’API ItemsAdder, sans demander un nouveau pack ni générer des modèles `clickhunter:*`. La référence est la liste des items réellement déclarés dans les YAML du ZIP, et non toutes les textures présentes.

## PNJ

Utiliser ZNPCsPlus 2.0.0 pour les PNJ Daily, Discord, Contrats et marchands. Citizens n’est plus le fournisseur ; sa présence optionnelle sert uniquement à nettoyer les anciens PNJ balisés par ClikerV2 lors de la migration, sans toucher aux autres. Conserver les commandes, positions et orientations sauvegardées.

## Nouveau périmètre autorisé explicitement

Le propriétaire demande désormais les raids privés avec invitations et parchemins, un boss public après un milliard de kills, les boutiques PNJ et la collection de talismans généraux. Ces systèmes font partie du périmètre autorisé ; l’ancienne attente sur les instances ne bloque pas ces demandes. Les 19 zones actuelles sont conservées ; le propriétaire a choisi de débloquer le tier 5 général dans la dernière zone actuelle (zone 19), plutôt que de le réserver à la future zone 40.

## Préférences récentes de présentation

Les PNJ regardent chaque joueur indépendamment (ZNPCsPlus `PER_PLAYER`), selon la nouvelle demande qui remplace `CLOSEST_PLAYER` et l’orientation fixe. Distance de regard par défaut : 48 blocs ; visibilité : 96 blocs, réglables via `pnj.distance-regard` et `pnj.distance-affichage`. Le shop et le marché noir conservent tous leurs visuels vanilla, y compris les boutons ; les autres menus gardent les icônes ItemsAdder. Le marché noir utilise les cases 12, 14, 16 et 30, 32, 34, comptées à partir de 1.

## Livraison et hologrammes — demande actuelle

Les achats et drops doivent être livrés directement dans l’inventaire si possible ; `/claim` conserve le surplus, les récompenses hors ligne et celles en attente pendant l’isolation de l’avant-poste. Garder le journal SQL idempotent de livraison. Le fournisseur demandé pour tous les hologrammes gérés par ClikerV2 est Holographic Displays API 3 ; ZNPCsPlus conserve les corps et interactions des PNJ. Ne pas réintroduire DecentHolograms comme fournisseur actif ; sa détection ne sert qu’au nettoyage des anciennes créations connues. La version exacte compatible Paper 1.21.4 du fournisseur n’a pas été fournie (le propriétaire a seulement confirmé « 1.21.4 »). Documenter le secours natif et ne pas affirmer que le rendu réel Holographic Displays a été testé sans ce JAR.

Les sacs possèdent désormais un snapshot binaire dans le playerdata, prioritaire au chargement, ainsi qu’une copie SQL. Conserver l’écriture conjointe inventaire+sacs avant la copie SQL pour les transferts en bloc, et sauvegarder/restaurer les deux sources ensemble.

## Animation et jeux de chat

Le pied du TAB doit faire alterner Site, Boutique, Discord et Vote toutes les dix secondes. Les jeux de chat (calcul, devinette, langues parlées par pays) attribuent une seule caisse au premier gagnant, selon son prestige (Commune au prestige 0), via `recompenses/caisses-prestige.yml`, par le journal de livraison existant. Aides et questions restent configurables en français dans `activites/tchat.yml`. Les magma cubes ont une taille moyenne 2, les ghasts une échelle 0,5. Conserver les commandes administrateur `/respawnmobs` et `/spawnboss`, les exclusions des invocations temporaires et la prévention des doublons lors des vagues.

## Règles confirmées après la version 1.0.3

L’Épée du Dresseur inflige 5 dégâts de base. Compression automatique et manuelle plafonnées à la rareté nécessaire pour acheter l’épée et les armures du mob ; Vache reste en Commun. Conserver la décompression des anciennes ressources. Les boss de zone sont sans dégâts et ont 100 fois les PV du mob ; les boss de raid ont 1 000 fois les PV du mob de raid. Le grand hologramme global « Prochains boss » est retiré, les compteurs par zone restent présents. Les piñatas affichent de nouveau leurs secondes restantes. TAB et scoreboard restent actifs, avec le style RGB et animé demandé. `/drop` remplace `/antidrop` et `/ramassage` et bascule les deux actions ensemble. Conserver la mise en veille des zones et la conservation des PV ; ne pas réintroduire de chargement global des chunks de spawn au démarrage ou au reset.

## Demandes après 1.0.5

Autoclic : un bloc de distance entre hitboxes. Épée en main obligatoire, y compris avec skin de bloc (le matériau reste une épée). Les essences des mobs ordinaires ne vont pas dans /claim : surplus réservé au sol ; les boss et récompenses rares gardent le journal. Daily fixe progressif sans prestige. Étoile : MENU CLICKHUNTER, gras et sans italique.

## Demandes après 1.0.9

Œufs/essences de mobs : supprimer le surplus quand l’inventaire est plein ; aucun nouveau drop au sol. `/claim` propose Tout récupérer. Entrée aux enchères gratuite par défaut ; protection passive à 100 blocs du point de salle, toute hauteur, bossbar pendant les événements.

## Grades Discord — demande après 1.0.11

Le propriétaire veut la synchronisation LuckPerms ↔ Discord directement dans ClikerV2, sans DiscordSRV. Conserver le bot natif et la liaison vérifiée par code ; seuls les parents LuckPerms directs, globaux et persistants et les rôles explicitement associés sont synchronisés. Six associations configurables : Mini-VIP, VIP, VIP+, MVP, MVP+, Legend. Ne pas inventer de jeton ou d'ID Discord, ni activer la connexion sans configuration. Conserver la journalisation des opérations, les limites de débit et la reprise des déliaisons ; une absence Discord ou une erreur réseau ne doit pas retirer un achat Minecraft.

## Publications de jeu vers Discord — demande après 1.0.13

Conserver les Top 10 Discord issus du RankingStore du jeu, avec dix lignes et édition horaire des mêmes messages dont les IDs sont sauvegardés. Les salons sont configurables par tableau et par événement. Le bot natif peut réutiliser ses identifiants de discord-grades.yml sans nécessiter LuckPerms pour publier. Les alertes du boss serveur incluent ses PV à l'apparition et le joueur du coup final ; les enchères distinguent inscriptions et début réel des mises. Une route native active évite un second envoi par le webhook historique. Aucun envoi sur le vrai Discord sans configuration du bot et des salons.
