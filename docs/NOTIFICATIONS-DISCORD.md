# Notifications Discord — version 1.0.8

Paper 1.21.4 / Java 21. Aucun nouveau plugin ni bot requis. Ce système est indépendant du PNJ Discord et du lien d'invitation existant.

## Activer les messages

1. Discord : paramètres du salon → Intégrations → Webhooks → créer et copier l'URL.
2. Via SFTP, ouvrir `plugins/ClikerV2/integrations/discord-notifications.yml`.
3. Renseigner `webhook-url` puis `actif: true`. Cette URL contient un secret : ne pas la transmettre dans le chat ou une capture publique. Si exposée, supprimer et recréer le webhook.
4. Exécuter `/discordnotif reload`, puis `/discordnotif test` (permission `cliker.discord.admin`). La commande confirme la mise en file, pas la réception distante. Vérifier le salon choisi.

La configuration par défaut dans le dépôt ne contient aucun secret. Le lien `https://discord.gg/WSwBwpnQTa` ne permet pas de publier des messages : seule une URL de webhook convient.

## Événements

| Clé | Moment de déclenchement | Défaut |
| --- | --- | --- |
| boss-zones-spawn | Vague logique de réapparition naturelle ou forcée, avec au moins un point de boss. Un seul message pour toutes les zones. Les corps des zones vides restent en veille. | Oui |
| boss-zone-mort | Élimination validée en base, avec nom du boss et pseudo du joueur. | Non |
| boss-serveur-spawn | Activation du boss public, naturelle ou forcée ; également lors de sa restauration active après redémarrage. | Oui |
| boss-serveur-mort | Victoire publique confirmée par son état persistant. | Oui |
| pinata-spawn | Apparition d'une nouvelle piñata ; Écus, XP ou Gemmes Sombres. | Oui |
| enchere-inscriptions | Ouverture des inscriptions ; les rappels du même événement sont dédupliqués. | Oui |
| raid-public | Un chef ouvre son groupe aux renforts. | Non |

Chaque clé possède `actif`, `titre`, `texte`, `couleur` (couleur RGB en entier décimal). Variables communes : `{serveur}`, `{adresse}`. Variables spécifiques : `{nombre}`, `{boss}`, `{joueur}`, `{recompense}`, `{secondes}` selon le tableau. L'adresse est `play.clickhunter.fr` par défaut. Pas de mention de rôle ni de `@everyone`, même si un pseudo ou texte en contient une.

## Performances et sécurité

Aucune requête HTTP sur le thread serveur. Worker unique, une requête en vol, timeout de connexion 5 secondes et de requête 10 secondes. File plafonnée à 64 par défaut, configurable entre 1 et 256. Historique de déduplication limité à 512 identifiants et dix minutes ; nettoyage à chaque ajout, suppression à l'arrêt.

Seules les URL HTTPS officielles `discord.com` et `discordapp.com`, avec chemin de webhook et port standard, sont acceptées. Les redirections ne sont pas suivies. Le statut administrateur et les journaux n'affichent ni URL ni jeton. Titres et descriptions sont limités aux tailles acceptées par Discord.

Réponse 429 : deux nouvelles tentatives maximum, avec respect de Retry-After (minimum une seconde). Si Discord demande plus de 60 secondes, la notification est abandonnée plutôt que renvoyée trop tôt. Autres erreurs ou timeout : abandon et avertissement limité en fréquence. Un timeout peut survenir après réception distante ; ne pas réessayer aveuglément évite de multiplier les doublons. Ce n'est pas une garantie de livraison exactement une fois.

La file n'est pas persistante : un arrêt, un rechargement, une panne ou une saturation peut faire perdre une annonce. Aucun objet ou gain de joueur ne dépend de Discord. La déduplication est locale au processus ; elle ne garantit pas l'absence de repost après redémarrage. Les fonctions de test d'enchère locale ne publient pas de fausse enchère réelle.

## Validation

Tests automatisés : filtrage des URL, échappement JSON, interdiction des mentions, exécution dans le worker, déduplication, capacité de file, reprise 429, arrêt et masquage du secret lors d'une erreur. Transport simulé : aucun message envoyé à un vrai salon pendant les tests, car aucun webhook n'a été fourni. Une réception réelle devra être vérifiée après configuration par l'administrateur.

Validation automatisée : **476 tests fonctionnels réussis**, aucun échec, erreur ou test ignoré. Le chargement et les commandes statut/test/reload ont aussi été vérifiés sur la copie Paper 1.21.4, notifications désactivées.
