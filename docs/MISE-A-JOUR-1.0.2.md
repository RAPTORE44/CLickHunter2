# Livraison 1.0.2 — 31 octobre 2026

Paper 1.21.4 et Java 21. Remplacer uniquement le JAR puis redémarrer ; conserver le dossier de données. Vérifier `/version ClikerV2` : **1.0.2**. Les nouveaux réglages sont ajoutés et les migrations du Daily et des boss créent une sauvegarde.

## Règles appliquées

- Boss de zone : 100 × les PV de la créature, aucun dégât ; un œuf rare (75 %) ou épique (25 %) livré avec les autres drops.
- Boss de raid : 1 000 × les PV du mob du raid ; mobs et boss se déplacent et attaquent. Boss public : 1 milliard de PV par défaut, sans facteur de zone sur les PV ; impacts à distance annoncés pendant une seconde par une zone rouge, réglables dans `activites/raid.yml`.
- Double respawn I–V : 2/4/6/8/10 % de chance de faire apparaître un mob temporaire supplémentaire après une élimination de mob persistant. Les mobs temporaires et boss sont exclus ; plafond partagé de 30 invocations par monde. Disponible à la table et dans son livre d’information ; compatible avec l’enchantement Universel.
- Caisses Ender Chest : Écus et XP uniquement. Anciennes caisses encore ouvrables sans Gemmes ni Cendres. Jeux de chat et Daily utilisent les paliers de `recompenses/caisses-prestige.yml`, Commune au prestige 0. Une caisse par jour, deux les jours 7, 14, 21, 28 et 31 ; commandes Daily personnalisées conservées.
- Poubelle : transfert gauche 1/droite pile dans les deux sens. Fermeture par le joueur : destruction. Déconnexion, mort, arrêt ou fermeture imposée : conservation dans le playerdata. Reprise différée pendant une livraison ou l’isolation de l’avant-poste ; aucun transfert entre profils d’inventaire.
- Plus de messages « Récompense récupérée ! » pour les livraisons automatiques ou le Daily. Les erreurs et les états des menus restent affichés.

## Commandes et interfaces

Le README contient toutes les commandes, alias et permissions par catégorie. Nouveautés : `/trash`, `/poubelle`, `/pouvelle`, `/tutoriel`, `/flyspeed`, `/god`, `/heal`, `/feed`. Les quatre commandes staff ont des permissions `cliker.staff.<commande>`, OP par défaut ; cibler autrui exige aussi `cliker.staff.others`. Le mode god reste actif jusqu’à désactivation.

Le clic droit sur un joueur ouvre ses statistiques et son équipement en lecture seule. Le menu principal retire les stations enchantement/reforge/runes sans retirer leurs commandes ni leurs blocs. Classements : six catégories sur l’accueil, puis top 10 et filtres de période. Le scoreboard retire CLICKER, utilise des valeurs compactes françaises et un dégradé RGB pour le nom et l’adresse. L’étoile de menu passe en gras. Le TAB ajoute un bandeau défilant tout en conservant l’alternance des liens toutes les dix secondes.

La ponte vanilla des poules est bloquée. Les nouveaux critères de réussites vanilla hors recettes sont bloqués ; les progrès déjà enregistrés ne sont pas effacés. Les notifications ClickHunter restent actives. Les marchands villageois reçoivent un biome et un métier variés, stables après redémarrage ; les skins joueurs configurés sont conservés.

## Validation

- **436 tests automatisés**, aucun échec, erreur ou test ignoré.
- Paper 1.21.4 / Java 21 / ZNPCsPlus 2.0.0 : menu de poubelle 54 cases, clics gauche/droit, reprise avant fermeture et destruction du seul contenu restant. Déconnexion avec 32 objets en attente : les 32 sont rendus à la reconnexion, sans duplication à la réouverture.
- Deux clients : classements, ouverture des statistiques du bon joueur par interaction, absence des stations dans `/menu`, vitesse de vol vérifiée dans le paquet réseau, mode god réellement activé/désactivé, soin et nourriture restaurés.
- Deux clients : l’attaquant reçoit le son de coup et le spectateur n’en reçoit aucun. TAB changeant et couleurs RGB reçus. Boss public en déplacement et particules rouges d’attaque à distance reçues.
- Livraison réelle d’un Ender Chest directement dans l’inventaire, sans message de récupération. Apparition d’un marchand villageois ZNPCsPlus reçue par le client.

## Dépendances et limites

Le filtre des attaques vanilla utilise l’API PacketEvents intégrée à **ZNPCsPlus 2.0.0**, sans nouveau plugin. Les corps des PNJ utilisent aussi ZNPCsPlus. Le rendu des icônes ItemsAdder et Holographic Displays n’a pas été testé avec les JAR de production ; le secours vanilla/natif reste présent. Les services Mojang ont retourné des erreurs réseau dans l’environnement de test : les tests ne certifient pas un téléchargement récent de skins joueurs. Les variantes de villageois ne nécessitent pas ces téléchargements.

Tests effectués sur une instance Paper isolée, pas sur le serveur de production. Les changements de PV s’appliquent à la prochaine apparition/au prochain combat.
