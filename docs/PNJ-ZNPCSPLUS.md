# PNJ ZNPCsPlus 2.0.0

Les PNJ Daily, Discord, Contrats et les marchands utilisent l’API **ZNPCsPlus 2.0.0**, vérifiée sur le JAR officiel de la publication <https://github.com/Pyrbu/ZNPCsPlus/releases/tag/2.0.0>.

## Installation

1. Arrêter proprement le serveur. Sauvegarder `plugins/ClikerV2/` et les données des plugins de PNJ.
2. Installer `ZNPCsPlus-2.0.0.jar` et remplacer le JAR ClikerV2.
3. Redémarrer sous Paper 1.21.4 / Java 21. Aucun `/reload` nécessaire.

Aucun changement de commande ou de permission. `/daily pnj spawn|remove`, `/discord pnj spawn|remove`, `/contrats pnj spawn|remove` et `/pnj spawn <mob>|remove|list` restent les interfaces de gestion. Les commandes fonctionnelles des modules restent disponibles sans fournisseur de PNJ ; la création d’un marchand nécessite ZNPCsPlus.

## Fonctionnement

Les PNJ sont des joueurs virtuels par paquets : pas d’IA ni d’entité villageois à déplacer. Chaque joueur voit les PNJ le regarder indépendamment (`look=PER_PLAYER`). La portée de regard est de 48 blocs et la visibilité de 96 blocs par défaut, réglables par `pnj.distance-regard` et `pnj.distance-affichage`. Les limites d’affichage du client et des chunks restent applicables. Les hologrammes sont gérés par le service partagé Holographic Displays API 3, avec secours natif configurable. Les clics droits reviennent sur le thread principal avant d’ouvrir les menus ou d’envoyer le lien Discord. Le skin `Nati144378` est conservé par défaut ; les skins déjà configurés dans les modules restent prioritaires. Le téléchargement du skin dépend des services utilisés par ZNPCsPlus.

Les positions Daily/Discord/Contrats restent dans leurs YAML et les marchands dans SQLite. Les identifiants internes `clickhunter-*` sont stables et réservés au plugin. Ces PNJ ne sont pas enregistrés une deuxième fois dans le stockage ZNPCsPlus : ils sont recréés à partir des données ClikerV2 au démarrage et retirés à l’arrêt. Ils ne doivent pas être édités avec les commandes natives ZNPCsPlus ; utiliser les commandes ClikerV2.

## Migration

Les anciennes positions restent utilisables. Si Citizens est encore présent, seuls les anciens PNJ portant la balise de ce module sont retirés lors de la restauration correspondante. Les autres PNJ Citizens et ZNPCsPlus restent inchangés. Citizens figure encore en dépendance optionnelle uniquement pour ordonner ce nettoyage, pas pour créer les nouveaux PNJ. Les anciens villageois marchands marqués par ClikerV2 sont nettoyés au chargement des positions.

Les anciennes clés de messages contenant `citizens` sont conservées pour compatibilité ; les textes du fournisseur sont adaptés à ZNPCsPlus. Conserver les fichiers existants plutôt que recopier des configurations neuves sur les positions enregistrées.

## Vérifications

Tests automatisés : gestion administrative, fonctionnement sans fournisseur, doublon refusé, suppression limitée au bon identifiant et sauvegarde de l’intention de suppression. Essais avec le JAR officiel sur Paper : PNJ Daily et Contrats visibles et cliquables, Discord donnant le lien exact, marchand ouvrant sa boutique. Le rendu graphique exact du skin n’est pas certifié par le client de test sans affichage graphique.
