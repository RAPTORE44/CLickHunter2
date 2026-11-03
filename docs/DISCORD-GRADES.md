# Grades LuckPerms ↔ Discord — ClikerV2 1.0.12

La synchronisation est intégrée à ClikerV2. **LuckPerms reste nécessaire**, mais DiscordSRV, JDA et un deuxième plugin Minecraft ne le sont pas. Les notifications webhook existantes continuent à fonctionner indépendamment.

## Installation

1. Installer le JAR 1.0.12 sous Paper 1.21.4 / Java 21, serveur arrêté, puis redémarrer. Conserver une sauvegarde du dossier `plugins/ClikerV2` et des données LuckPerms.
2. Créer une application et son bot dans le [portail développeur Discord](https://discord.com/developers/applications). Inviter ce bot sur le serveur avec la portée OAuth2 **bot**. Il lui faut **Gérer les rôles**, **Voir le salon** et **Voir les anciens messages** dans le salon de liaison. Aucun droit Administrateur n’est requis.
3. Placer le rôle du bot **au-dessus de tous les rôles à synchroniser**. Les rôles d’intégration gérés automatiquement et `@everyone` ne sont pas des cibles valides.
4. Activer le mode développeur Discord pour copier les identifiants du serveur, d’un salon textuel dédié à la liaison et des six rôles.
5. Modifier `plugins/ClikerV2/integrations/discord-grades.yml`. Conserver le jeton du bot sur le serveur ; ne pas l’envoyer dans le chat ni publier ce fichier avec son secret. Le jeton peut aussi venir de la variable d’environnement `CLICKHUNTER_DISCORD_BOT_TOKEN`, prioritaire sur le YAML.
6. Mettre `actif: true`, puis exécuter `/discordsync reload` et `/discordsync statut`. Le plugin vérifie la présence des groupes LuckPerms, l’accès au serveur/salon, les permissions du bot et la hiérarchie des rôles avant de synchroniser. Un jeton invalide ou un bot insuffisamment autorisé met les opérations en attente ; aucun grade n’est retiré à cause d’une erreur HTTP.

Extrait à compléter avec les **vrais IDs**, entre guillemets :

```yaml
actif: true
bot-token: 'JETON_DU_BOT_A_RENSEIGNER_SUR_LE_SERVEUR'
serveur-id: 'ID_DU_SERVEUR'
salon-liaison-id: 'ID_DU_SALON_TEXTUEL'
premiere-liaison: minecraft
intervalle-secondes: 30
comptes-par-passage: 20
code-minutes: 10
groupes:
  minivip: 'ID_ROLE_MINI_VIP'
  vip: 'ID_ROLE_VIP'
  vipplus: 'ID_ROLE_VIP_PLUS'
  mvp: 'ID_ROLE_MVP'
  mvpplus: 'ID_ROLE_MVP_PLUS'
  legend: 'ID_ROLE_LEGEND'
```

Ces valeurs `ID_...` sont des emplacements à remplacer, pas des identifiants utilisables. Adapter les clés aux groupes réellement présents dans `/lp listgroups` : si le groupe s’appelle `vip+`, utiliser `vip+` au lieu de `vipplus`. Un rôle vide désactive seulement cette association. Un rôle ne peut correspondre qu’à un groupe.

Le fichier est installé **désactivé**, sans jeton ni identifiant inventé. Les droits Unix du fichier sont limités à son propriétaire lorsque le système le permet. Le plugin ne publie pas le jeton dans ses diagnostics ou erreurs.

## Liaison pour le joueur

1. Rejoindre le Discord du serveur.
2. Faire `/discord lier` en jeu.
3. Cliquer **Copier le message Discord**, puis **Ouvrir le salon de liaison**.
4. Coller le message contenant la mention du bot et `lier CODE`. Une simple saisie du nom du bot, sans véritable mention, ne fonctionne pas.
5. Une confirmation privée apparaît en jeu ; la liaison est sauvegardée par UUID et identifiant Discord.

Le code est personnel, valable dix minutes par défaut, à usage unique. Une nouvelle demande invalide le précédent. Seule son empreinte SHA-256 est stockée ; il n’est pas écrit dans les logs. Le message doit être récent, provenir d’un humain membre du serveur Discord configuré, et se trouver dans le salon configuré. Une modification d’un ancien message antérieur à l’émission du code ne valide pas la liaison.

Un compte Minecraft a au plus un compte Discord lié ; un compte Discord ne peut pas être associé simultanément à deux UUID Minecraft sur ce serveur Discord. `/discord statut` montre seulement sa propre liaison.

## Règles de synchronisation

- Seuls les **parents directs, globaux et persistants** de LuckPerms sont synchronisés. Un parent temporaire enregistré dans LuckPerms compte jusqu’à son expiration. Les parents propres à un monde/serveur, les nodes transitoires en mémoire, les groupes hérités indirectement, le statut OP, le groupe primaire seul et les wildcards ne donnent pas automatiquement un rôle Discord.
- Ajouter ou retirer un de ces groupes en jeu met à jour son rôle Discord. Ajouter ou retirer le rôle correspondant sur Discord met à jour ce groupe LuckPerms, même pour un joueur hors ligne. Aucun pseudo Discord n’est utilisé comme preuve d’identité.
- Une attribution provenant de Discord crée un parent LuckPerms **permanent** : un rôle Discord ne porte pas de date d’expiration.
- Plusieurs groupes directs associés peuvent donner plusieurs rôles. Le système ne choisit pas arbitrairement un grade supérieur et ne supprime pas les autres rôles Discord ou groupes LuckPerms.
- À la **première liaison**, `premiere-liaison: minecraft` prend les groupes Minecraft comme référence. Les rôles associés sont alignés dessus. Utiliser `discord` pour prendre les rôles existants comme référence à cette première liaison. Les liaisons déjà établies utilisent ensuite les changements observés dans les deux sens.
- Un départ du Discord ou un membre absent n’est pas traité comme un retrait de grade Minecraft. Les achats Minecraft restent conservés ; au retour du membre, les associations sont rétablies à partir de Minecraft avant de reprendre la synchronisation normale.
- Supprimer un rôle du catalogue Discord, perdre les droits du bot ou subir une erreur réseau n’est pas interprété comme un retrait de grade. La configuration doit être corrigée.
- Les associations modifiées dans le fichier utilisent un nouveau suivi. Un ancien rôle qui n’est plus associé n’est plus touché automatiquement. Après un changement complet de serveur Discord configuré, délier puis refaire la liaison ; les rôles de l’ancien serveur nécessitent une vérification manuelle.

### Délai

Ce bot utilise l’API REST Discord, sans connexion Gateway permanente. Il peut donc apparaître **hors ligne dans la liste Discord**, tout en lisant les mentions et en gérant les rôles normalement. Il ne fournit pas de commande slash Discord ni de lecture des messages privés.

Les événements LuckPerms demandent une vérification prioritaire. Les modifications faites sur Discord sont détectées par passages : **20 comptes toutes les 30 secondes par défaut**, y compris les joueurs Minecraft hors ligne. Avec plus de 20 comptes liés, le délai d’un passage complet augmente : environ `arrondi supérieur(comptes / comptes-par-passage) × intervalle`, auquel s’ajoutent la durée des requêtes et les éventuelles limites de débit. Il ne s’agit pas d’une promesse de synchronisation instantanée.

Les messages de liaison sont vérifiés environ toutes les cinq secondes lorsqu’un code est encore valide. Sans code en attente, cette lecture est arrêtée. Les requêtes passent dans un seul worker, avec une file de 128 demandes et un ensemble de priorités borné ; les comptes liés sont parcourus par pages SQL, sans charger toute la communauté en mémoire.

Les corps HTTP sont limités à 2 Mio, la durée complète de réception est bornée à neuf secondes et une expiration annule la requête. Les erreurs et réponses Discord 429 déclenchent une attente et une nouvelle tentative. Les opérations en cours sont sauvegardées avant modification puis acquittées après succès. Une sauvegarde LuckPerms échouée est réessayée, même si le parent paraît déjà présent dans son cache. Une reprise ne doit pas produire une boucle d’ajout/retrait.

## Commandes et permissions

| Commande | Permission | Fonction |
| --- | --- | --- |
| `/discord` | `cliker.discord.use` | Lien d’invitation existant. |
| `/discord lier` | `cliker.discord.use` et `cliker.discord.link` | Code personnel, copie et salon de validation. |
| `/discord statut` | mêmes permissions | Sa liaison et l’état du bot, même pendant une panne Discord. |
| `/discord delier confirmer` | mêmes permissions | Enregistre la déliaison, retire les rôles Discord associés, puis supprime la liaison. Conserve les grades Minecraft. |
| `/discordsync statut` | `cliker.discord.sync.admin` | État global et nombre d’associations. |
| `/discordsync reload` | même permission | Recharge le fichier ; une syntaxe invalide conserve le fonctionnement précédent. |
| `/discordsync sync [joueur connecté\|UUID]` | même permission | Demande une vérification prioritaire ; sans argument, soi-même. |

`cliker.discord.link` est accordée par défaut ; `cliker.discord.sync.admin` est réservée aux OP par défaut. Les commandes PNJ `/discord pnj spawn|remove` et les commandes webhook `/discordnotif ...` restent séparées et conservent leurs permissions. Aucun nouvel alias n’est ajouté.

Une déliaison interrompue reste en attente et reprend automatiquement ; le lien n’est supprimé qu’après le nettoyage des rôles du serveur configuré. Le joueur peut ensuite effectuer une nouvelle liaison.

## Validation et limites

**510 tests réussis**, sans échec, erreur ni test ignoré. Les 21 nouveaux tests comprennent le moteur de synchronisation, le worker et la sélection des parents LuckPerms.

Les tests automatisés couvrent les ajouts/retraits dans les deux sens, les parents directs globaux, l’expiration, l’exclusion des groupes contextuels/transitoires, les codes expirés ou déjà utilisés, les associations uniques, les reprises après erreur API/sauvegarde, la fermeture du worker, le retrait puis retour d’un membre et la préservation des groupes/rôles non associés. Un test du worker complet utilise un Discord simulé et vérifie que ses appels sont hors du thread principal.

Essais locaux sous Paper 1.21.4 / Java 21 avec ZNPCsPlus 2.0.0 : démarrage 1.0.12 avec l’intégration désactivée et sans LuckPerms, lien Discord historique inchangé, suggestions des commandes pour un non OP, gestion réservée au staff, rechargement et anciens bilans de boss conservés. Le worker actif est testé avec une API Discord et un accès LuckPerms simulés ; la sélection des nodes persistants est aussi vérifiée via les interfaces de l’API LuckPerms.

Aucun jeton de production n’a été fourni : **aucun rôle réel n’a été attribué ou retiré sur ton Discord pour ces tests**. L’activation réelle nécessite la configuration du bot et un essai avec un compte de test lié. La synchronisation suppose des attributions de grades réalisées dans LuckPerms ; elle ne convertit pas les anciennes permissions Bukkit isolées en groupes.

Références officielles consultées : [messages et lecture du salon](https://docs.discord.com/developers/resources/message#get-channel-messages), [exception de contenu pour les mentions du bot](https://docs.discord.com/developers/events/gateway#message-content-intent), [ajout/retrait d’un rôle de membre](https://docs.discord.com/developers/resources/guild#add-guild-member-role).
