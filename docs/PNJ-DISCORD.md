# PNJ Discord ClickHunter

Le module est intégré au même `ClikerV2.jar`.
Lien livré : **https://discord.gg/WSwBwpnQTa**.

## Utilisation

- `/discord` : affiche le lien cliquable dans le chat.
- `/discord pnj spawn` : crée le PNJ à la position et avec l'orientation de l'administrateur.
- `/discord pnj remove` : supprime le PNJ Discord.
- Un clic droit sur ce PNJ envoie directement le lien au joueur.

Permission joueur : `cliker.discord.use`, accordée par défaut.
Gestion du PNJ : `cliker.discord.admin`, réservée aux opérateurs par défaut.

**Citizens compatible Paper 1.21.4 est nécessaire pour le PNJ.** La commande
`/discord` fonctionne aussi sans Citizens. Aucun ajout de dépendance n'est requis
si Citizens est déjà installé pour le PNJ des récompenses.

Le PNJ reste immobile et protégé, affiche « DISCORD » puis « Clic droit pour
rejoindre ». Son skin par défaut est Nati144378, comme le PNJ Daily existant ;
il est modifiable dans la configuration. La position et l'état du PNJ sont
sauvegardés pour être restaurés après redémarrage. La création de doublons est refusée.

La balise du PNJ Discord est distincte de celle du Daily. Le déplacement ou la
suppression de l'un n'affecte pas l'autre. L'adaptateur Citizens est désormais
partagé ; le Daily conserve sa balise et ses données historiques.

## Configuration

Fichier généré : `plugins/ClikerV2/discord.yml`.
Le lien, les messages, le skin et les deux lignes au-dessus du PNJ sont modifiables.
Redémarrer après modification. Ne pas écraser ce fichier avec celui des sources
après placement du PNJ : il contient aussi sa position et son identifiant.
Le lien est envoyé au joueur ; le plugin ne force pas l'ouverture du navigateur.

## Installation

Arrêter le serveur, sauvegarder le dossier ClikerV2, remplacer le JAR et redémarrer.
Se placer au bon endroit puis saisir `/discord pnj spawn` avec les droits admin.

Les tests vérifient le lien exact et son événement de clic, les permissions,
l'absence de Citizens, le refus d'un doublon déjà enregistré et la séparation
Daily/Discord lors de la suppression. Les tests de frontière Citizens utilisent
des doubles de registre, pas un Citizens réel. L'apparition, le skin et les clics
réels doivent être vérifiés sur Paper avec Citizens installé.
