# Configuration des raids et du boss public

Les commandes d’administration sont réservées aux OP par défaut : `cliker.raid.admin` pour les raids, `cliker.boss.admin` pour le boss public. Les suggestions et `/raid admin info`, `/boss admin info` donnent les syntaxes.

## Cartes et points

Quatre cartes de raid indépendantes au maximum ; une carte distincte pour le boss public. Le fichier `activites/arenes.yml` contient les affectations, points et PNJ. Le plugin refuse l’affectation du monde principal, de l’avant-poste, du monde AFK, d’un monde occupé ou d’un monde déjà réservé à une autre instance. Utiliser des mondes dédiés, jamais une carte de survie : les règles et protections de combat concernent toute la carte affectée.

Exemple : depuis le spawn, après avoir chargé une carte vide dédiée :

```text
/raid admin map 1 ma_carte_raid
/boss admin map ma_carte_boss
```

**Redémarrer après une affectation de carte.** Les combats actifs conservent leur carte jusqu’au redémarrage. Les cartes existantes ne sont ni effacées ni recopiées. Chaque emplacement de raid utilise sa propre carte : pour quatre groupes simultanés, préparer quatre mondes distincts.

Se placer sur chaque destination puis saisir :

```text
/raid admin set 1 entree
/raid admin set 1 centre
/raid admin set 1 observation
/raid admin rayon 1 31
/boss admin set entree
/boss admin set centre
/boss admin set observation
/boss admin rayon 31
```

Le centre définit l’apparition du boss et la zone de combat. Entrée, centre et observation doivent être dans la carte affectée. Position et orientation exactes sont sauvegardées. Le rayon horizontal est compris entre 8 et 256 blocs ; il ne construit pas de murs. Préparer un sol et un point d’observation sûrs.

Depuis le spawn ou un autre monde de sortie :

```text
/raid admin set 1 sortie
/boss admin set sortie
```

La sortie doit être hors de l’arène. Sans sortie configurée, le joueur revient à sa position d’entrée, ou au spawn d’un autre monde si cette position est indisponible. Les points et le rayon prennent effet immédiatement.

## PNJ et observation

```text
/raid admin pnj entree spawn
/raid admin pnj sortie spawn
/raid admin pnj observation spawn
/boss admin pnj entree spawn
/boss admin pnj sortie spawn
/boss admin pnj observation spawn
```

Remplacer `spawn` par `remove` pour déplacer un PNJ. Un seul PNJ de chaque rôle par activité, positions persistantes, doublons refusés. ZNPCsPlus 2.0.0 requis. Les apparences sont dans les sections `raid.pnj` et `boss.pnj` de `activites/arenes.yml` ; chaque entrée contient une section `pnj` avec type, skin, titre et sous-titre.

Le PNJ d’entrée Raid ouvre le groupe et celui du boss rejoint l’arène publique. Les PNJ de sortie exécutent la commande quitter. Le PNJ d’observation est réservé au staff et choisit un raid actif ; `/raid observer <pseudo>` permet de choisir un groupe précis. `/boss observer` rejoint l’observation du boss public.

Les observateurs ne peuvent pas infliger ou recevoir de dégâts et ne sont pas ciblés. Ils conservent leur mode de jeu ; un point sûr est donc nécessaire. `/raid quitter` et `/boss quitter` permettent de repartir. La permission `cliker.raid.admin` autorise également une téléportation ordinaire (`/tp` fourni par Paper ou un autre plugin) vers un joueur dans une instance privée ; elle ne donne pas accès au groupe ni à son menu de lancement.

## Combat, difficulté et récompenses

```text
/raid admin param
/raid admin param mobs.maximum 12
/raid admin param mobs.pv 500
/raid admin param mobs.degats 4
/raid admin param boss.tous-les-mobs 100
/raid admin param boss.pv 10000000000
/raid admin param boss.degats 12
/raid admin param boss.portee 6
/raid admin param boss.echelle 4
/raid admin param boss.vitesse 1
/raid admin param boss.recompenses.ecus 100000
/boss admin param boss.pv 10000000000
/boss admin zone vache
/boss admin seuil 1000000000
```

`/raid admin param` règle la base dans `activites/raid.yml`. `/boss admin param` enregistre des valeurs propres au boss public dans `activites/arenes.yml`, sans modifier les raids privés. Les paramètres non précisés pour le boss public héritent de la base. Les valeurs sont bornées ; les quantités doivent être entières. Une commande sans clé affiche toutes les clés disponibles.

Les clés de récompense sont `mobs.recompenses.<devise>` et `boss.recompenses.<devise>`, pour `ecus`, `gemmes-sombres`, `cendres` et `xp`. Les gemmes sont en dixièmes internes : 10 = 1 gemme affichée. Les valeurs sont les bases avant difficulté et bonus existants. La cagnotte du boss est répartie selon les dégâts, avec le journal persistant de récompenses existant.

Les nouveaux raids prennent leurs nouveaux paramètres au lancement. Le boss public charge sa difficulté, son cycle et ses paramètres au redémarrage. Sa zone définit son espèce et sa difficulté ; les PV sont multipliés par le carré du numéro de zone, dégâts et récompenses par le numéro de zone (dégâts plafonnés). Le parchemin définit la zone d’un raid privé : vaches et boss Vache géant pour Vache, cochons et boss Cochon géant pour Cochon, etc. Les animaux passifs se déplacent et infligent des dégâts de contact grâce au contrôleur du raid. PvP désactivé.

`/boss spawn` force le boss public, sans doublon ni consommation du cycle naturel. `/spawnboss` concerne les boss des zones de farm.

Les boss de farm conservent leur modèle existant, possèdent par défaut 1 000 fois les PV du mob de leur zone, poursuivent l’attaquant dans un rayon de 20 blocs et reviennent à leur point. Leur minuteur apparaît automatiquement 6 blocs à l’est et 3 blocs au-dessus du point de boss, indépendamment de leur déplacement. L’horloge commune reste dans `activites/boss-zones.yml`. La musique Minecraft s’arrête après la sortie du combat ; le volume dépend aussi des réglages Musique du client.

## Vérification

Les tests automatisés couvrent les espèces par difficulté, les valeurs refusées, la conservation des points/orientations, les mondes distincts, les permissions, le passage staff et la persistance des réglages. Le rendu des grandes créatures, leur physique, les animations, la musique et le regard individuel des PNJ doivent être contrôlés sur Paper 1.21.4 avec les plugins du serveur.
