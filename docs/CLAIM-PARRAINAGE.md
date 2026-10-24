# Récompenses en attente et parrainage

Modules intégrés à ClikerV2 : Paper 1.21.4, Java 21. Aucune nouvelle dépendance externe.

## /claim — boîte de récupération

`/claim` ouvre un double coffre de 54 cases. Chaque objet se récupère par un clic ;
les flèches permettent de parcourir les pages. Le coffre est personnel et ne
permet ni dépôts ni déplacement des icônes. Si l'objet entier ne tient pas dans
l'inventaire, il reste en attente : libérer une place puis réessayer.

- Les objets attribués à un joueur hors ligne sont sauvegardés par UUID.
- En ligne, les objets sont remis directement lorsque c'est possible. Les surplus
  passent dans la boîte au lieu d'être jetés au sol.
- À la connexion, les objets en attente sont signalés et leur livraison est tentée
  si le joueur a de la place et n'est pas dans une autre interface.
- Les **Écus, Gemmes Sombres et Cendres restent crédités directement**, même hors
  ligne : ils ne deviennent pas des icônes à récupérer.
- Le contenu persiste au redémarrage dans la base d'économie existante.

Les distributions d'objets de ClikerV2 passent désormais par ce service : drops
custom, cadeaux, coffres, talismans, équipements, marchands, compressions, retours
d'objets des menus et objets du HDV. `/coffre <joueur> <rareté> [quantité]` et
`/talisman treasure <joueur>` acceptent aussi un joueur hors ligne déjà connu.
Le compte doit avoir déjà rejoint le serveur : aucun UUID n'est inventé à partir
d'un pseudo inconnu. Les commandes vanilla et les plugins externes ne sont pas
interceptés automatiquement ; ils peuvent appeler le service Java s'ils souhaitent
utiliser cette boîte.

Configuration visible : `plugins/ClikerV2/claim.yml`.
Permission : `cliker.claim`, accordée à tous par défaut.

### Sécurité et limites

La récupération d'une entrée utilise une réservation SQLite, un verrou par joueur
et un reçu PDC temporaire. L'inventaire est sauvegardé avant validation définitive.
À la reconnexion, un reçu d'une livraison interrompue permet de valider l'entrée
sans attribuer l'objet une seconde fois. Les interactions d'inventaire, commandes,
drops et combats sont momentanément bloqués pendant cette validation. Un problème
de sauvegarde déclenche une nouvelle tentative ; consulter les logs en cas de
blocage anormal. Le reçu est retiré après validation.

Cette protection concerne la récupération dans `/claim` et la transaction de
parrainage. Elle ne transforme pas toutes les anciennes transactions du HDV,
shops ou daily en transactions atomiques de bout en bout. Ne pas utiliser de
reload à chaud. Les modifications forcées d'inventaire par un plugin externe et
les défaillances matérielles de stockage ne sont pas couvertes par les tests.

## /parrain <pseudo>

Alias : `/parrainage <pseudo>`.
Permission : `cliker.parrein`, accordée à tous par défaut.

Le délai commence à la **toute première connexion réelle du compte** au serveur.
Il dure **30 minutes par défaut**, en temps réel, y compris les périodes hors ligne.
Reconnexion et redémarrage ne remettent pas le compteur à zéro. Un joueur déjà
ancien ne reçoit donc pas une nouvelle fenêtre à l'installation du module.
Modifier le délai configure les nouvelles fenêtres ; celles déjà enregistrées
restent inchangées.

Pendant ce délai :

- Un message privé de bienvenue explique `/parrain <pseudo>` et propose
  `/parrain RAPTORE44` si personne n'a invité le joueur.
- Une barre de boss violette, de style Wither, rappelle la commande et le temps restant.
- Une validation réussie retire la barre. À expiration, elle disparaît également.
- Le parrain doit être un compte connu ayant déjà rejoint le serveur ; il peut être hors ligne.
- Le choix est définitif : une seule validation par UUID. Auto-parrainage et cycles
  de parrainage réciproque sont refusés.

Les récompenses des deux comptes, les objets en attente et les notifications
sont enregistrés dans **une transaction SQLite unique**. Un échec annule
l'ensemble. Répéter la commande ne donne pas plusieurs récompenses.

Le parrain reçoit une **notification privée**, immédiatement s'il est connecté,
ou à sa prochaine connexion sinon. Rien n'est annoncé globalement dans le chat.

### Configuration visible

Fichier généré : `plugins/ClikerV2/parrainage.yml`.

```yaml
delai-minutes: 30
parrain-par-defaut: RAPTORE44
barre:
  couleur: PURPLE
  titre: '&dFais /parrain <pseudo> &f• &e{temps}'
recompenses:
  filleul:
    monnaies:
      ecus: 1000
      gemmes-sombres: 0
      cendres: 0
    objets:
      - type: lootbox
        id: commun
        quantite: 1
  parrain:
    monnaies:
      ecus: 2500
      gemmes-sombres: 0
      cendres: 0
    objets:
      - type: lootbox
        id: commun
        quantite: 1
```

Les monnaies sont dans les unités internes de l'économie : **10 dixièmes de Gemme
Sombre = 1 gemme affichée**. Les valeurs négatives sont refusées.
Un objet Minecraft classique peut être défini par :

```yaml
- type: minecraft
  materiau: DIAMOND
  quantite: 1
```

Les messages, titre et couleur de barre sont également modifiables. Redémarrer
le serveur après modification des fichiers. Les réglages ne donnent pas de
récompenses rétroactives à un parrainage déjà validé.

## Installation et validation

1. Arrêter le serveur et sauvegarder le dossier `plugins/ClikerV2/`.
2. Remplacer le JAR puis démarrer : les deux YAML et tables sont créés automatiquement.
3. Ajuster les récompenses si nécessaire puis redémarrer.
4. Tester `/claim` avec un inventaire plein et un compte neuf pour `/parrain`.

Les tests couvrent notamment les propriétaires, doubles clics, pagination,
inventaire plein, conservation hors ligne/redémarrage, fusion de piles, reprise
par reçu PDC, échec de sauvegarde, délai non réinitialisé, expiration, refus des
anciens comptes, auto-parrainage, rollback financier, récompenses uniques,
barre, messages privés et notification du parrain après reconnexion.
Les frontières de sauvegarde de fichiers joueur et d'écriture d'inventaire non
implémentées dans MockBukkit sont simulées dans les tests ; un vrai arrêt brutal
du serveur et le rendu client de la barre restent à vérifier sur Paper.

La permission historique `cliker.parrein` est conservée pour ne pas casser les groupes existants.
Les anciennes mentions de `/parrein` dans la barre et le message d’accueil sont corrigées automatiquement au redémarrage.
