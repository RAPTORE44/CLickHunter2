# Buyzone — module intégré à ClikerV2

Paper 1.21.4, Java 21. Aucune dépendance supplémentaire : le module utilise les
monnaies et la base SQLite de ClikerV2, sans Vault ni WorldGuard.

## Configuration nécessaire avant de créer les portails

Les tarifs existants sont conservés. Les accès qui n'avaient pas encore de prix
reçoivent un **équilibrage provisoire autorisé** : Vache gratuite, puis le prix
d'accès correspond à dix fois le prix actuel d'une pièce d'armure de la zone.
Exemples : Cochon 1 000 Écus, Poulet 5 000, Mouton 10 000, Piglin Brute 10 millions.
Cela ne modifie pas le prix des équipements. Tous les montants restent éditables.

Au premier démarrage, le fichier est créé dans `plugins/ClikerV2/buyzone.yml`.
Ajuster les tarifs et, si nécessaire, remplacer les permissions proposées,
puis redémarrer le serveur avant de créer les portails. Exemple de structure,
avec le tarif provisoire :

```yaml
zones:
  cochon:
    nom: 'Cochon'
    prix: 1000
    devise: ecus
    permission: cliker.zone.cochon
```

- `prix: -1` interdit la création du portail et l'achat tant que le tarif manque.
- `prix: 0` est un déblocage gratuit explicite ; utiliser un entier positif pour
  une zone payante.
- Devises disponibles : `ecus`, `gemmes-sombres`, `cendres`.
- Les prix sont exprimés en unités internes. Pour les Gemmes Sombres uniquement,
  **10 unités internes = 1 gemme affichée**, comme dans l'économie existante.
- Les permissions proposées sont `cliker.zone.<identifiant>`. Remplacer ces noms
  par ceux réellement utilisés sur le serveur si une autre convention existe.
- Les noms de zones peuvent être complétés dans la configuration, avec un
  identifiant composé de lettres minuscules, chiffres, tirets ou underscores.

Identifiants repris : `vache`, `cochon`, `poulet`, `mouton`, `lapin`, `ourse`,
`ane`, `pigman`, `piglin`, `piglin_brute`, `hoglin`, `magma_slime`, `blaze`,
`wither_skeleton`, `ghast`, `vindicator`, `ravager`, `wither`, `warden`.

## Création et suppression

- `/buyzone spawn <zone>` : crée un portail invisible à la position des pieds de
  l'administrateur. Exemple : `/buyzone spawn cochon`.
- `/buyzone remove <zone>` : supprime le portail sans supprimer les accès achetés.
- Un seul portail par zone. Le supprimer avant de le déplacer.
- Permission de gestion : `cliker.buyzone.admin`, accordée aux opérateurs par défaut.
- Permission de contournement : `cliker.buyzone.bypass`, accordée aux opérateurs
  par défaut. L'enlever au joueur de test pour vérifier le fonctionnement.

Les positions sont enregistrées dans `buyzone.yml`, sous `portails`, et restaurées
au redémarrage. Ne pas remplacer ce fichier par celui livré dans le code source
une fois les portails créés, afin de conserver les positions enregistrées.

## Géométrie confirmée

Le choix retenu est un **portail invisible**, et non un volume de 40 × 40 blocs :

- **40 blocs de large**, soit 20 blocs à l'est et 20 à l'ouest du centre.
- **10 blocs de haut**, depuis les pieds de l'administrateur jusqu'à 10 blocs au-dessus.
- **1 bloc d'épaisseur**, centré sur le point de création.
- Retour à **X du centre, Z du centre moins 5** : cinq blocs au **nord**.

Le portail est aligné est-ouest, indépendamment de la direction du regard à sa
création. Le nord Minecraft correspond à **-Z**. Prévoir un sol sûr au point de
retour : la création est refusée si aucune position sûre n'y est disponible.
Une petite recherche verticale protège les retours sur terrain légèrement irrégulier.

Le portail protège son passage : ce n'est pas une région de terrain entière.
Il faut fermer physiquement les contournements latéraux et par-dessus si le portail
est censé être l'unique entrée d'une zone. Il ne remplace pas les régions complètes
d'un plugin de protection : une téléportation directement loin derrière le portail
ne peut pas être attribuée à une zone de terrain sans en définir les limites.

## Fonctionnement de l'achat

1. Un joueur sans la permission associée tente de traverser : le mouvement est
   bloqué et un inventaire de type **distributeur (3 × 3)** s'ouvre.
2. Les huit cases extérieures contiennent du verre noir sans nom. L'étoile du
   Nether centrale affiche le nom de la zone, sa devise et son prix.
3. Un clic sur l'étoile confirme l'achat. Le débit et l'enregistrement du droit
   d'accès sont faits dans **une même transaction SQLite**.
4. Si le solde est insuffisant : le menu se ferme, le joueur est averti dans le
   chat et aucun déblocage ni débit n'est effectué.
5. Si l'achat réussit : le menu se ferme, la permission est appliquée et le joueur
   est placé au centre du portail. Il peut désormais traverser librement.
6. Une fermeture sans achat renvoie le joueur cinq blocs au nord du centre.

Les clics multiples ne déclenchent pas plusieurs débits. Tous les déplacements
et glisser-déposer dans le menu sont annulés. Le contrôle couvre les mouvements
rapides traversant entièrement l'épaisseur en un seul événement, les véhicules,
les destinations de téléportation à l'intérieur du portail et les joueurs déjà
présents dans celui-ci.

Une confirmation déjà cliquée reste traitée même si le joueur ferme le menu ou
se déconnecte pendant l'opération. En cas de fermeture pendant le paiement, il
est d'abord renvoyé au nord ; un achat validé reste acquis et n'impose pas une
nouvelle téléportation s'il a déjà fermé le menu.

## Sauvegarde et permissions

La table `buyzone_unlocks` dans la **base d'économie existante** conserve les
achats par UUID et identifiant de zone. Elle est créée automatiquement, sans
modifier les soldes ou les autres données existantes.

Au chargement du joueur, le module restaure les accès et applique les permissions
avec une attachment Bukkit. **LuckPerms n'est pas obligatoire** : les droits
persistent dans SQLite et sont appliqués pendant que ClikerV2 fonctionne. Une
permission déjà fournie par LuckPerms ou un autre gestionnaire donne aussi accès,
sans demander un achat. Le module ne modifie pas les groupes du joueur ni la base
LuckPerms. Les noeuds des zones sont déclarés verrouillés par défaut.

Après un achat, changer le nom de la permission dans `buyzone.yml` puis redémarrer
applique le nouveau noeud aux acheteurs de cette même zone. Supprimer puis recréer
un portail avec le même identifiant conserve les achats.

Le module refuse les achats tant que le chargement des droits du joueur n'est
pas terminé. Un échec de sauvegarde annule aussi le débit. Une déconnexion ou un
redémarrage après validation ne perd pas l'accès acheté.

## Installation et validation

1. Arrêter le serveur, sauvegarder le dossier `plugins/ClikerV2/` et remplacer le JAR.
2. Démarrer pour générer `buyzone.yml`.
3. Renseigner les tarifs et permissions réels, puis redémarrer.
4. Avec les droits administrateur, créer le portail à la bonne entrée, avec un
   retour sûr au nord.
5. Tester avec un joueur sans `cliker.buyzone.bypass`, avec et sans argent.

Les tests couvrent la géométrie, le menu, la fermeture et le retour, le solde
insuffisant, l'achat, les doubles clics, la concurrence des paiements, l'annulation
atomique d'un débit si l'enregistrement du droit échoue et le rechargement des
achats après redémarrage. Ils utilisent un serveur simulé. Le placement réel du
portail, les collisions et le terrain du serveur restent à vérifier en jeu.

## Migration automatique des anciens tarifs

Une ancienne configuration sans `version-prix: 2` est copiée dans
`backups/buyzone-<timestamp>.yml`. Seuls les prix absents ou égaux à -1 des zones
connues sont remplacés. Les montants positifs, la gratuité déjà configurée, les
permissions, les positions et les achats sont conservés. Les zones personnalisées
ne sont pas affectées. Après migration, `-1` reste utilisable pour désactiver
volontairement une zone : la migration ne repasse pas à chaque démarrage.
