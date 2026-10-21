# Récompenses journalières NorthBlue — module de ClikerV2

Le calendrier est **intégré à ClikerV2** : aucun deuxième plugin NorthBlueDaily
n'est à installer. Cible : **Paper 1.21.4 et Java 21**.

## Installation

1. Arrêter le serveur. Sauvegarder l'ancien JAR et le dossier `plugins/ClikerV2/`.
2. Remplacer l'ancien plugin par le nouveau `ClikerV2.jar` dans `plugins/`.
   Ne pas garder deux versions du JAR et ne pas utiliser `/reload`.
3. Pour le PNJ joueur avec skin, installer une version de **Citizens** explicitement
   compatible avec **Paper 1.21.4**. Citizens est optionnel pour `/daily`, mais
   obligatoire pour les commandes de PNJ. Télécharger Citizens auprès de sa source
   officielle : <https://www.spigotmc.org/resources/citizens.13811/> et suivre ses
   instructions de compatibilité. Le module ne nécessite pas Sentinel ou Denizen.
4. Démarrer le serveur. La configuration française est créée automatiquement dans
   `plugins/ClikerV2/daily.yml`. Les autres modules de ClikerV2 sont conservés.
5. Ouvrir `/daily`, ou créer le PNJ avec `/daily pnj spawn` depuis sa position
   souhaitée. La direction du regard de l'administrateur est conservée.

Modifier `daily.yml` puis **redémarrer le serveur** pour appliquer les changements.
Ne pas recopier la configuration d'exemple sur celle du serveur après création du
PNJ : les clés `pnj.actif`, `pnj.id` et `pnj.position` y conservent sa position.

## Commandes et permissions

| Commande | Permission | Accès par défaut |
| --- | --- | --- |
| `/daily` | `cliker.daily.use` | Tous les joueurs |
| `/daily pnj spawn` | `cliker.daily.admin` | Opérateurs |
| `/daily pnj remove` | `cliker.daily.admin` | Opérateurs |

Le clic droit sur le PNJ ouvre directement le même calendrier et vérifie aussi
`cliker.daily.use`. Le PNJ porte le skin `Nati144378`, reste immobile et protégé,
et affiche « Récompenses journalières » en vert puis « Clic droit pour ouvrir ».
La création est refusée si un PNJ est déjà enregistré. Au démarrage, les doublons
portant la balise de ce module sont supprimés, sans toucher aux autres PNJ Citizens.
La suppression est persistante ; le PNJ est restauré automatiquement au redémarrage
s'il est encore activé. Citizens assure le téléchargement et le cache du skin ;
l'accès aux services Mojang et aux textures doit être autorisé sur le serveur.

## Règle des journées calendaires

- Fuseau par défaut : `Europe/Paris`, modifiable avec `fuseau-horaire`.
- Le changement de journée se produit à **minuit local**, sans délai de 24 heures.
- Une récupération à 23 h 59 autorise la suivante dès 00 h 00, y compris à 00 h 01.
- Il faut **réclamer** une récompense chaque jour : se connecter ne valide rien.
- Une journée entière manquée remet la prochaine récompense au jour 1.
- Après la récupération du jour 31, un nouveau cycle commence le lendemain.
- Les changements d'heure de printemps et d'automne sont pris en compte.

Exemple : jour 5 récupéré lundi, aucune récupération mardi, retour mercredi :
le jour 1 est disponible. Si mardi a été récupéré, mercredi propose le jour 7.

La progression est enregistrée par UUID dans `plugins/ClikerV2/data/daily.db`.
Sauvegarder le dossier complet à l'arrêt (SQLite peut aussi créer des fichiers WAL).
Les transactions SQLite et une garde en mémoire empêchent les doubles récupérations.
Tous les déplacements d'objets du calendrier sont bloqués : clics, shift-clics,
double-clics, touches de barre rapide et glisser-déposer.

## Interface et visuels

Inventaire de **54 cases**, titre « Calendrier de Récompenses », fond bleu foncé,
bandeau cyan, bouton Informations en case centrale de la première ligne.
Les jours 1 à 28 occupent les sept colonnes centrales des quatre lignes suivantes ;
les jours 29 à 31 sont centrés sur la dernière ligne.

La configuration définit séparément les icônes de fond, bandeau, informations,
récompense disponible, verrouillée et déjà récupérée. Chaque icône peut définir :

```yaml
materiau: CHEST
itemsadder: ''  # Exemple avec vos propres assets : northblue:cadeau
custom-model-data: 0
nom: '&a&lJour {jour}'
description:
  - '&7État : {etat}'
  - '&f{recompense}'
  - '&aCliquez pour réclamer !'
```

**ItemsAdder est optionnel.** Les icônes personnalisées requièrent ItemsAdder,
les objets correspondants et son pack de ressources accepté par les joueurs.
Le module ne fournit pas ces assets. Sans objet ItemsAdder utilisable, il affiche
le matériau Minecraft indiqué : coffre pour le cadeau, crochet pour le cadenas
et teinture verte pour une récompense récupérée. Aucun pack n'est requis pour ces
remplacements classiques. Les icônes réelles de cadeau et cadenas dépendent donc
d'un pack personnalisé ; les titres, séparateurs, états et descriptions sont
fournis en texte coloré standard.

Aucune capture d'écran n'était présente dans la demande reçue : le menu suit la
disposition et les couleurs décrites, sans prétendre reproduire des textures absentes.

## Récompenses configurables

Les **31 récompenses initiales sont uniquement du test**. Aucune monnaie ni objet
n'est attribué. Pour chaque jour, le fichier contient :

```yaml
recompenses:
  1:
    description: 'Récompense test — Jour 1'
    commandes:
      - 'minecraft:tellraw {joueur} {"text":"[NorthBlue] Récompense test — Jour 1 récupérée !","color":"green"}'
```

Les commandes s'exécutent en console, sans slash initial. Variables acceptées :
`{joueur}`, `{uuid}` et `{jour}`. Une liste vide bloque la récupération sans
consommer le jour. Les messages, infobulles et noms des icônes sont modifiables
en français dans `daily.yml`. Les délais affichés sont conditionnels à la
récupération quotidienne des récompenses précédentes.

La récupération est enregistrée **avant** d'exécuter les commandes afin d'empêcher
leur double exécution. Les commandes externes ne font pas partie de la transaction
SQLite : un arrêt brutal entre ces deux étapes, une déconnexion pendant le traitement
ou une commande défaillante peut nécessiter une intervention administrative. Les
erreurs sont journalisées avec UUID et jour ; le module ne rejoue pas automatiquement
les commandes, ce qui risquerait de doubler un paiement. Les administrateurs doivent
valider leurs futures commandes avant utilisation en production.

## Compilation et vérifications

```sh
# JDK 21 et Maven 3.9.9, depuis la racine du projet
mvn -B clean package
```

Résultat : `target/clikerv2-1.0.0.jar`. Le fichier à installer est le JAR complet
incluant SQLite, et non `original-clikerv2-1.0.0.jar`.
Le package `org.sqlite` reste inchangé, car les noms JNI de sa bibliothèque native
ne supportent pas la relocalisation. Les descripteurs de services sont fusionnés
lors du packaging.

Tests automatisés : calendrier, minuit, jours manqués, fin du cycle, changements
d'heure, persistance UUID, concurrence, menu complet, commandes console et
protection des objets. La bibliothèque SQLite incluse dans le JAR final a aussi
été exécutée directement. L'apparence en jeu, le skin et le cycle de vie réel du
PNJ Citizens doivent être vérifiés sur un serveur Paper avec Citizens installé ;
ils ne sont pas validés par le serveur simulé utilisé pour les tests.

## Vérification en jeu recommandée

1. `/daily` : vérifier les 31 jours, les infobulles et le bouton Informations.
2. Réclamer le jour 1 : recevoir le message de test, puis voir l'icône récupérée.
3. Cliquer de nouveau et tenter les déplacements d'objets : aucune seconde récompense.
4. `/daily pnj spawn` : vérifier le skin, le texte, l'immobilité et le clic droit.
5. Refaire `spawn` : création refusée. Redémarrer : un seul PNJ revient.
6. `/daily pnj remove`, puis redémarrer : le PNJ doit rester supprimé.
