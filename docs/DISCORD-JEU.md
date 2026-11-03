# Top 10 et événements Discord — ClikerV2 1.0.14

Le même bot natif que celui des grades peut publier les classements et événements. Aucun DiscordSRV, JDA ou service externe supplémentaire n’est nécessaire. Les publications seules fonctionnent sans LuckPerms ; la synchronisation des grades conserve sa dépendance à LuckPerms.

## Activer

Après installation du JAR et redémarrage, modifier :

`plugins/ClikerV2/integrations/discord-jeu.yml`

```yaml
actif: true
bot-token: ''
serveur-id: ''
fuseau: Europe/Paris
adresse: play.clickhunter.fr
classements:
  actif: true
  salon-id: 'ID_DU_SALON_CLASSEMENTS'
  intervalle-minutes: 60
  tableaux:
    ecus: {type: ecus, periode: all}
    gemmes: {type: gemmes, periode: all}
    cendres: {type: cendres, periode: all}
    kills: {type: kills, periode: all}
    niveaux: {type: niveaux, periode: all}
    donateurs: {type: donateurs, periode: all}
evenements:
  actif: true
  salon-id: 'ID_DU_SALON_EVENEMENTS'
  # Conserver ici la section types installée avec le plugin.
```

Remplacer les `ID_...` par les vrais identifiants numériques Discord, entre guillemets. `bot-token` et `serveur-id` vides réutilisent les valeurs de `discord-grades.yml`. Il est aussi possible de les renseigner ici si les grades ne sont pas configurés. La variable `CLICKHUNTER_DISCORD_BOT_TOKEN` reste prioritaire pour le jeton. Le fichier contenant le jeton doit rester sur le serveur ; ses droits Unix sont limités au propriétaire lorsque le système le permet.

Le bot doit être membre du serveur et avoir, dans chaque salon : **Voir le salon**, **Envoyer des messages**, **Intégrer des liens** et **Voir les anciens messages**. Les dérogations du salon sont prises en compte. Ni Administrateur ni Gérer les rôles ne sont nécessaires pour cette fonctionnalité seule. Comme le bot utilise l’API REST, il peut apparaître hors ligne dans Discord tout en publiant normalement.

Faire `/discordjeu reload`, puis `/discordjeu statut`. `/discordjeu actualiser` demande une mise à jour des tableaux sans attendre l’heure suivante. Rien n’est publié avec le fichier vierge : l’intégration est désactivée, sans ID ni jeton inventés.

## Les classements

Les tableaux utilisent **RankingStore**, exactement comme les menus de classement du jeu, avec les joueurs hors ligne présents dans ces données. Ils ne sont pas calculés uniquement à partir des joueurs connectés.

| Type | Source et affichage |
| --- | --- |
| `ecus` | Solde actuel d’Écus, jaune. |
| `gemmes` | Solde actuel de Gemmes Sombres, violet ; conversion correcte des dixièmes. |
| `cendres` | Solde actuel de Cendres, gris. |
| `kills` | Éliminations suivies par le plugin, rouge. |
| `niveaux` | Derniers niveaux connus, bleu. |
| `donateurs` | Points de soutien du classement existant, orange ; aucun montant en euros. |

Chaque message contient dix positions. Les positions sans joueur restent affichées avec `—` et zéro. Les égalités suivent le même tri que le jeu. Les noms sont échappés et les mentions Discord sont désactivées.

Le premier passage crée les messages manquants. Les suivants les **modifient**, au lieu d’ajouter six nouveaux messages chaque heure. Les identifiants et l’heure de la dernière actualisation sont sauvegardés en SQL. Un redémarrage ne recrée pas les tableaux déjà connus et ne remet pas leur horloge à zéro. Si un message a été supprimé, il est recréé et son nouvel ID est enregistré.

L’intervalle est de 60 minutes par défaut. Les échéances sont vérifiées environ une fois par minute ; la durée des requêtes et les éventuelles attentes Discord s’ajoutent à ce délai. Il ne s’agit pas d’un alignement à la minute zéro de l’heure civile. Les valeurs sont rafraîchies à la demande du worker, sans lecture permanente des soldes sur le thread Minecraft.

### Salons séparés et périodes

Chaque tableau peut définir son propre salon :

```yaml
classements:
  salon-id: 'ID_SALON_COMMUN'
  tableaux:
    ecus:
      type: ecus
      periode: all
      salon-id: 'ID_SALON_ECUS'
    kills_semaine:
      type: kills
      periode: semaine
      salon-id: 'ID_SALON_SEMAINE'
```

Les périodes acceptées sont `all` / `toujours`, `jour` / `day`, `semaine` / `week`, `mois` / `month`, `annee` / `year`. Elles concernent la période courante dans le fuseau configuré, Europe/Paris par défaut. La semaine commence le lundi. Mettre `actif: false` dans un tableau pour ne plus l’actualiser ; les anciens messages ne sont pas supprimés automatiquement.

Pour les monnaies, une période autre que `all` classe la **variation nette du solde**, comme le jeu, et non toutes les entrées brutes. Les périodes dépendent de l’historique réellement suivi par le plugin. Le texte du message précise le sens des données ; les niveaux sur une période correspondent aux niveaux gagnés, et les donateurs aux points ajoutés moins ceux retirés.

## Notifications

Les types et leurs textes sont dans `evenements.types`. Un salon commun peut être défini dans `evenements.salon-id` ; un type peut le remplacer avec son propre `salon-id`.

| Type | Déclenchement et informations |
| --- | --- |
| `boss-serveur-spawn` | Boss public disponible, son nom, ses PV compacts et exacts, commande `/boss`. |
| `boss-serveur-mort` | Après le paiement confirmé du boss : nom, joueur du coup final, nombre de participants. |
| `enchere-inscriptions` | Ouverture des inscriptions ; les rappels en jeu ne créent pas plusieurs notifications Discord identiques. |
| `enchere-debut` | Début réel des mises sur le premier lot, avec nombre d’inscrits, nom du lot et numéro. |
| `enchere-fin` | Session terminée, ou annulée faute d’inscrits. |
| `enchere-lot` | Lot suivant ; désactivé par défaut. |
| `enchere-vendu` | Adjudication confirmée, gagnant et prix en Écus ; désactivé par défaut. |
| `pinata-spawn` | Piñata et type de récompense ; désactivé par défaut dans le bot. |
| `boss-zones-spawn` | Vague de réinitialisation des boss de zone ; désactivé par défaut dans le bot. |

Les PV de la notification sont ceux de l’apparition du boss ; aucune requête Discord n’est envoyée à chaque coup. Les textes, couleurs et activations sont modifiables dans le fichier. Les autres types déjà émis par le plugin peuvent être ajoutés sous `evenements.types` si souhaité, par exemple `raid-public` ou `boss-zone-mort`, avec leurs champs existants.

Les placeholders disponibles selon l’événement comprennent `{boss}`, `{pv}`, `{pv_bruts}`, `{pv_max}`, `{joueur}`, `{participants}`, `{secondes}`, `{lot}`, `{total}`, `{objet}`, `{prix}`, `{etat}`, `{nombre}`, `{recompense}`, ainsi que `{adresse}` et `{serveur}`. Utiliser les champs qui existent pour le type concerné.

### Webhooks déjà configurés

Les événements explicitement pris en charge par le bot ne sont pas également envoyés par le webhook historique. Les événements sans route bot active continuent à utiliser leur configuration dans `integrations/discord-notifications.yml`. Pour désactiver un événement partout si les deux systèmes sont configurés, désactiver ses entrées dans les deux fichiers.

Les alertes webhook existantes du boss public reçoivent aussi les PV et le nom du joueur du coup final si leurs textes étaient encore les textes standard ; les textes personnalisés sont conservés. Les sections manquantes des nouvelles alertes d’enchères sont ajoutées sans remplacer le webhook secret ni les choix existants.

## Administration

| Commande | Permission |
| --- | --- |
| `/discordjeu statut` | `cliker.discord.game.admin` |
| `/discordjeu reload` | même permission |
| `/discordjeu actualiser` | même permission |

Permission OP par défaut ; aucun alias ajouté. Le statut ne révèle pas le jeton. Une configuration syntaxiquement invalide conserve le worker précédent. Une connexion refusée apparaît dans le statut et fait l’objet de nouvelles tentatives, sans bloquer le serveur de jeu.

## Reprises, performances et limites

- Un worker réseau dédié, une file de 64 événements, un seul appel de publication en cours ; aucun appel Discord sur le thread principal.
- Requêtes bornées en durée et en taille, mentions désactivées, réponses 429 respectées. Une actualisation forcée partiellement réussie reprend les tableaux restants, sans republier ceux déjà acquittés. Les tableaux reçoivent une nouvelle tentative après erreur ; une réponse 403 ne crée pas un nouveau tableau pour contourner le refus.
- IDs des tableaux persistants ; recherche des **100 derniers messages** du salon pour réadopter un message du bot dont l’acquittement SQL aurait été perdu. Un salon de classements dédié facilite cette récupération. La recherche ignore les messages des joueurs et des autres bots. Des nonces Discord stables limitent aussi les doublons d’une création réessayée.
- Les alertes envoyées par le bot ont un reçu SQL conservé 30 jours. L’apparition et la mort du boss public utilisent un numéro de session distinct du compteur de progression : une apparition forcée ne modifie pas l’objectif du prochain boss naturel.
- Les alertes en attente de plus de cinq minutes sont écartées pour ne pas annoncer trop tard une ancienne apparition. Une file pleine peut également perdre une alerte, avec avertissement limité dans les logs. Il ne s’agit pas d’un journal garanti de tous les événements pendant une longue panne Discord.
- Discord et SQLite ne partagent pas de transaction. Un redémarrage normal conserve les messages ; une perte de la base couplée à un message sorti des 100 derniers ne permet pas de garantir l’absence absolue de doublon.
- Aucun chunk de zone supplémentaire n’est chargé pour ces classements. Les requêtes de classement restent exécutées sur le worker SQL du plugin, avec dix résultats par tableau. Aucune amélioration CPU/RAM chiffrée n’est revendiquée.

## Validation

**526 tests fonctionnels réussis**, sans échec, erreur ni test ignoré, dont douze nouveaux tests de publication et de routage. Essais locaux Paper 1.21.4 / Java 21 : démarrage, permission staff, rechargement et migration des configurations, menu de classement et boss déjà actif sans doublon.

Les tests utilisent une API Discord simulée : valeurs de classement issues de la base réelle de test, dix lignes, Gemmes en dixièmes, mentions neutralisées, édition du même message, reprise après redémarrage, récupération d’un acquittement perdu, recréation après suppression, déduplication d’événements, refus des permissions/salons incorrects et routage natif hors du thread principal. Les tests de groupes de raid existants vérifient encore les apparitions forcées et la conservation de la progression naturelle.

Aucun jeton ni salon de production n’a été fourni ; aucun classement ou message réel n’a été publié sur ton Discord pour ces tests. Il reste à renseigner les salons, activer le fichier et vérifier la réception réelle avec le bot configuré.
