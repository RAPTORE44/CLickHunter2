# ClickHunter — audit complémentaire du 25 octobre 2026

## Résultat et périmètre

Référence de départ : commit `bea94b6`. Cible : **Paper 1.21.4, Java 21, SQLite en mode WAL**.
Les monnaies, les prix, les ratios de compression et les gains configurés sont conservés.
Aucune table existante n’est supprimée ni migrée dans cette passe. Aucune dépendance de production n’est ajoutée.

L’[inventaire reproductible](INVENTAIRE-AUDIT.md) indexe les **134 fichiers Java** et les **16 configurations YAML**.
La revue concerne les parcours économiques, les événements, les données, les menus, les tâches, le combat
et leurs interactions. L’inventaire est exhaustif ; les tests et la revue ciblée ne prouvent pas l’absence
de tout défaut dans chaque combinaison de plugins tiers ou de configurations.

La section 17 transmise ne contient aucune idée renseignée. Aucun nouveau mode de jeu n’a été inventé.
Le [suivi des évolutions](SUIVI-EVOLUTIONS.md) sépare les fonctionnalités terminées, les validations restantes
et les propositions nécessitant une décision de gameplay ou une migration plus importante.

## 1. Problèmes réellement identifiés et corrections

Un **risque P0 conditionnel** a été identifié dans la reprise de `/claim` après une erreur de sauvegarde et une reconnexion : utilisation possible d’une ancienne instance du joueur. Le test injecte cet échec et vérifie l’absence d’écriture de l’ancienne session ; aucune corruption disque réelle n’a été provoquée.
Les risques résiduels liés à une coupure pendant un échange inventaire/SQLite restent décrits plus bas.

| Priorité | Défaut et origine | Correction et vérification |
| --- | --- | --- |
| P0 conditionnel | Une reprise de sauvegarde `/claim` conservait l’ancienne instance du joueur après reconnexion | Reprise par UUID avec réservation conservée et réconciliation de la session actuelle ; test d’échec puis reconnexion sans sauvegarde de l’ancienne session |
| P1 | Un échec du nettoyage final d’un reçu `/claim` pouvait laisser le joueur bloqué | Nettoyage repris sur la session actuelle, sans seconde attribution de l’objet ; test dédié |
| P1 | Une annonce disparue décalait les indices de la liste : un clic pouvait viser un autre objet que celui affiché, notamment lors d’un retrait personnel | Identifiants immuables conservés dans chaque vue ; tests de disparition d’une annonce avant l’achat ou le retrait |
| P1 | Les annonces au-delà des 45 premières étaient inaccessibles dans l’interface | Pagination des listes publiques et personnelles, test de la 46e annonce |
| P1 | L’ouverture d’un coffre envoyait trois crédits séparés et accordait l’XP immédiatement, sans traiter leurs échecs | Crédit atomique des monnaies et de l’XP ; original conservé dans `/claim` si un crédit est refusé ; test de dépassement avec annulation de tous les gains |
| P1 | Une pile de coffres pouvait produire une longue rafale de messages et d’opérations SQL | Une opération et un message pour la pile, calcul des totaux contrôlé contre les dépassements |
| P1 | La station physique ne vérifiait pas la permission de sa commande et traitait aussi les événements annulés et de main secondaire | Vérification `cliker.gear.use`, main principale uniquement, respect des interactions annulées ; trois tests dédiés |
| P1 | Des consultations/modifications de positions Bukkit se faisaient dans les tâches SQLite, avec des objets `Location` ou `Block` mutables | Capture de valeurs primitives avant écriture ; résolution des mondes sur le thread serveur ; tests des cinq stockages de positions et d’une mutation après mise en file |
| P1 | Un pseudo passé aux commandes de monnaie pouvait déclencher une recherche de profil réseau sur le thread serveur | Résolution locale par joueur connecté, cache ou base ; cible inconnue refusée sans compte fantôme ; tests sur joueur hors ligne connu et permissions |
| P1 | Le gestionnaire de réapparition global, enregistré après le raid, pouvait remplacer sa destination spécifique | Priorité `LOWEST` pour le spawn générique ; test de l’ordre d’enregistrement et mort réelle dans l’arène |
| P1 | Le point d’accueil était confirmé et mis en cache avant la réussite de sa sauvegarde ; une téléportation refusée était annoncée comme réussie | Publication du cache après validation SQL, conservation de l’ancien point sur erreur, résultat réel de `Player.teleport` ; trois tests ciblés |
| P1 | Le chat asynchrone lisait le cache de prestige pendant ses mises à jour sur le thread serveur | Cache concurrent pour cette donnée, initialisation de la disponibilité LuckPerms sur le thread serveur |
| P1, non résolu hors parcours normal | Lors du `/reload` contrôlé, une `ConcurrentModificationException` apparaît dans `Commands.sendAsync` / `CommandNode.hashCode` (Paper/Brigadier) | Le sac est conservé, mais le rechargement global n’est pas certifié ; cause racine non déterminée et aucun masquage ni patch interne Paper. Déploiement par arrêt/redémarrage |
| P2 | Certains retours SQL tentaient de programmer une action alors que le plugin était déjà arrêté | Dispatch commun `ServerTasks`, qui ignore uniquement l’arrêt attendu et ne masque pas une erreur quand le plugin reste actif |
| P2 | Les menus statistiques/prestige/améliorations pouvaient s’ouvrir après que le joueur avait changé de contexte | Requêtes de vue associées au joueur, à son monde et à l’inventaire précédent ; tests de navigation concurrente |
| P2 | Une amélioration n’affichait pas le solde, le manque de ressources ni un état visuel cohérent | Solde chargé avant affichage, état disponible/verrouillé/maximal, manque exact, tous les boutons actualisés après achat ; tests de rendu et révocation de permission |
| P2 | Les bonus des talismans reparcouraient jusqu’à sept coffres à chaque attaque | Agrégat recalculé au chargement et aux modifications, lecture directe en combat ; test d’invalidation sans changement des règles de cumul |
| P2 | La compression parcourait plusieurs fois les mêmes piles pour chaque groupe de 64 essences | Détection groupée des possibilités puis conversions par lots ; tests de conservation de valeur sur les deux paliers et du compresseur en main secondaire |
| P2 | Les modificateurs d’armure identiques étaient retirés puis réinstallés chaque seconde | Conservation des modificateurs déjà corrects ; nettoyage des modificateurs et tâches appartenant au gestionnaire à l’arrêt |
| P2 | Le scoreboard réalisait deux lectures séparées par joueur | Lecture jointe d’un instantané monnaie/éliminations ; benchmark local et tests existants du scoreboard |
| P2 | Une position stockée avec une coordonnée texte pouvait être interprétée comme zéro ; une ligne invalide pouvait bloquer les autres | Validation numérique et des valeurs finies, exclusion de la seule ligne invalide, journalisation et conservation en base ; test de corruption sans suppression |
| P2 | Des valeurs PDC aberrantes pouvaient produire des bonus d’équipement hors limites | Lecture bornée, ancien niveau 20 préservé, données invalides ignorées avec avertissement limité et sans réécriture de l’objet ; test dédié |
| P2 | Des classements ex æquo n’avaient pas d’ordre secondaire explicite | Tri déterministe par nom puis UUID ; pas de changement des valeurs classées |
| P2 | Les barres de boss et certaines tâches n’avaient pas de nettoyage explicite propre au gestionnaire | Arrêt des tâches, retrait des joueurs des barres, purge des états d’autoclick, masquage si aucun emplacement de boss n’existe |
| P3 | Navigation et vocabulaire incohérents, taxe affichée en calcul flottant aux grandes valeurs | Boutons de retour communs, commandes françaises de l’AH avec anciens alias conservés, prix exacts sur les confirmations et calcul entier de taxe partagé |

### Fichiers principaux concernés

- Économie et lecture groupée : `EconomyService`, `CurrencyCommand`, `RewardLedger`, `LootboxManager`, `MoneyScoreboard`.
- Annonces : `AuctionMenu`, `AuctionManager`, `AuctionListing`, `AuctionCommand`.
- Données : `DatabaseManager`, `StoredLocation`, `MobSpawnStore`, `MerchantStore`, `StationStore`, `HologramStore`, `SpawnStore`, `SpawnManager`.
- Combat et tâches : `BagManager`, `CompressorManager`, `EssenceCompression`, `GearAttributeManager`, `AutoClickManager`, `BossEventManager`, `PrestigeManager`, `RankPrefix`, `ServerTasks`.
- Interfaces : `MenuNavigation`, `ViewRequests`, `StatsMenu`, `UpgradeMenu`, `PrestigeMenu`, les stations et menus d’activités ; descriptifs `plugin.yml` et titre du menu.
- Les autres remplacements de dispatch asynchrone utilisent le même garde de cycle de vie, sans modifier leur logique de récompense.

## 2. Optimisations et conservation des données

Les écritures SQLite restent sur leur exécuteur dédié. Les retours nécessitant Bukkit reviennent sur le
thread serveur. Les futures de chargement ne bloquent pas ce thread et ne conservent pas une connexion
ouverte en attendant la résolution d’un monde.

Le scoreboard passe de **deux requêtes à une** par lecture de joueur, avec des valeurs cohérentes dans
le même instantané. La mesure locale compare 300 lectures complètes avec les mêmes résultats : voir
les résultats chiffrés ci-dessous. Aucun gain global de TPS n’en est déduit.

Les talismans utilisent un agrégat en mémoire par joueur et espèce ; les modifications des sacs invalident
cet agrégat. Les copies d’objets renvoyées aux interfaces restent indépendantes du cache. Une fermeture
sans modification ne déclenche plus la réécriture identique de tous les coffres.

La compression conserve le ratio **64 → 1** et les mêmes quantités finales. Elle traite les groupes par
lots et vérifie d’abord si les essences présentes sont compressibles par un compresseur possédé.
La main secondaire peut toujours contenir un compresseur ; les essences qui s’y trouvent ne sont pas consommées.

Les prix de zones, les conditions actuellement codées, les récompenses configurées, les UUID, les objets
stockés, les permissions et les identifiants de commandes historiques sont conservés.
Les libellés français des nouveaux objets ne réécrivent pas les objets déjà possédés.
Une coordonnée corrompue ou un monde manquant ne provoque pas de suppression automatique de la ligne SQL.

## 3. Sécurité et limites restantes

Les confirmations d’achats restent contrôlées côté serveur. Les vues d’annonces ne servent jamais
à calculer le prix payé ni à choisir la cible à partir d’un indice devenu obsolète. L’ouverture simultanée
ne permet pas à deux acheteurs d’obtenir le même objet.

Les retours de coffres refusés sont inscrits dans le stockage de `/claim`, et non dépendants de
l’affichage d’un message au joueur. Une déconnexion ou un arrêt après cette inscription ne les supprime pas.
Les données de transaction et les reçus existants restent utilisés.

**Limite résiduelle :** Minecraft et SQLite ne fournissent pas de transaction commune couvrant le fichier
joueur et le retrait initial d’un objet avant son inscription en base. Une coupure exactement dans cette
fenêtre exige une sauvegarde cohérente et peut nécessiter une restauration. Une panne de disque empêchant
toute écriture ne peut pas être corrigée par un reçu supplémentaire dans la même base.
Une refonte du journal des transferts, avec migration et tests d’incidents, est proposée séparément.
Aucun test effectué ne permet d’affirmer que tous les scénarios de crash ou tous les conflits de plugins sont couverts.

Le comportement des commandes de monnaie change pour une cible qui n’a jamais été connue du serveur :
elle est refusée, au lieu de lancer une recherche réseau ou de créer un compte potentiellement erroné.
Les commandes et droits sur les joueurs connus sont préservés.

## 4. UI/UX

- Titres principaux et actions positives verts ; prix dorés, manque de ressources rouge et informations secondaires grises.
- Ancien titre par défaut NorthBlue du menu principal remplacé par ClickHunter ; les titres personnalisés sont conservés.
- Flèche de retour commune dans les menus consultatifs, stations et activités. Les 54 cases des coffres de stockage et les deux vitres du sélecteur `/sac` restent disponibles selon leur fonction initiale.
- `/ah vendre <prix>` et `/ah mesannonces`, avec maintien de `/ah sell` et `/ah mylistings`.
- Prix exact et taxe entière sur les confirmations. La liste peut utiliser une valeur abrégée, puis la confirmation présente le montant précis.
- Meilleure indication des niveaux, bonus suivants, solde, coût et ressources manquantes dans les améliorations.
- Statistiques renommées correctement : « Première connexion » et « XP actuelle », qui n’est pas présentée comme un cumul historique.
- Coffres légendaire/mythique et talisman du trésor libellés en français lors des nouvelles créations ; anciens identifiants PDC inchangés.
- Ouverture groupée de coffres accompagnée d’un seul récapitulatif.

## 5. Tests et validation

### Automatique

**145 tests exécutés : 0 échec, 0 erreur, 0 test ignoré** (113 tests hérités, 32 nouveaux).
Les tests hérités sont conservés. Les nouveaux cas ciblent les comportements corrigés : transactions
refusées, restitution durable sans retour UI, concurrence, affichage obsolète, pagination, états des
améliorations, permissions, valeurs extrêmes, corruption localisée et accès aux positions sur le thread serveur.
Les échecs SQL injectés pendant les tests produisent volontairement des journaux d’erreur : ils vérifient
la conservation des données et ne sont pas des erreurs constatées en exploitation normale.

La mesure `SnapshotBenchmarkTest` n’utilise aucun seuil de vitesse dépendant de la machine et ne simule
pas un serveur chargé. Dernière mesure locale : **300 lectures séparées en 342,6 ms**, contre **176,2 ms** pour les lectures combinées. Le nombre de requêtes passe de deux à une par lecture. Ces durées ne constituent pas un engagement sur les TPS ni un test de population cible.

### Sur Paper avec clients connectés

Environnement isolé : **Paper 1.21.4 build 232 / Java 21**, lié à `127.0.0.1` uniquement, données synthétiques.
Les services d’authentification Mojang étaient inaccessibles dans l’environnement ; les clients de test
utilisent donc le mode hors ligne local, qui ne doit pas être recopié sur un serveur public.

Vérifications déjà effectuées pendant cette passe :

- Achat d’amélioration avec mise à jour du solde et retour au menu.
- Ouverture d’une pile de trois coffres et récapitulatif unique.
- Autoclick bloqué par un mur puis reprise après retrait du mur.
- Sauvegarde du spawn, mort dans le raid et réapparition dans l’arène.
- Station physique, préchargement et restitution de l’équipement via le retour au menu.
- Publication d’annonce via l’alias français et accès à la liste personnelle.
- Deux acheteurs simultanés sur la même annonce : pour un prix de **12 345 Écus**, un seul perd ce montant, l’autre reste à **50 000**, un seul objet est attribué et le vendeur hors ligne reçoit **12 222 Écus** après taxe.
- Récupération effective de l’objet en attente via `/claim`.

Le JAR final a également été démarré puis rechargé de façon contrôlée avec un sac ouvert :
l’objet placé en case 53, sa quantité unique et ses métadonnées ont été retrouvés après rechargement.
**Le journal Paper signale cependant une `ConcurrentModificationException` dans son constructeur
asynchrone de commandes pendant ce `/reload`. Le test de conservation du sac réussit, mais le
rechargement global n’est donc pas déclaré réussi sans réserve.** La cause racine n’a pas été isolée ;
le journal et les erreurs ne sont pas masqués.
Un dépassement de solde a été exercé sur Paper : aucun crédit partiel et restitution effective du coffre.
Le rechargement à chaud n’est pas recommandé en production : Paper, les générateurs de mondes et les
plugins tiers peuvent conserver des états qui dépassent le périmètre du plugin.

### À vérifier sur une copie représentative du serveur

- Charge à la fréquentation visée, avec les vrais équipements, points de mobs, worlds et statistiques spark.
- Citizens installé : rendu et skin des PNJ, interactions et restauration réelle.
- Versions réelles de LuckPerms, DecentHolograms, ItemsAdder et leurs interactions.
- Inspection visuelle humaine des menus dans le client et le pack de ressources réel.
- Coupures brutales pendant chaque phase des transferts et procédure complète de restauration base + mondes/playerdata.

## 6. Configuration et déploiement

Aucun secret ni identifiant de connexion n’est modifié. SQLite et les formats existants sont conservés.
Les positions sont transportées en mémoire sous forme immuable ; cela ne change pas les tables.
Les valeurs importantes déjà configurées restent celles du serveur, sans rééquilibrage arbitraire.

Pour déployer : arrêter proprement, sauvegarder `plugins/ClikerV2/` et les mondes/playerdata, remplacer
`ClikerV2.jar`, puis redémarrer avec Java 21. Le remplacement d’un JAR pendant qu’il est chargé n’est pas
la procédure recommandée. Ne pas remplacer une configuration personnalisée par les valeurs de test.

## 7. Prochaines évolutions

Voir le [suivi des évolutions et propositions classées](SUIVI-EVOLUTIONS.md), qui précise pour chaque
proposition son intérêt joueur, son impact, sa complexité et sa priorité.

Les prérequis de set complet avant achat d’une zone, un équipement secondaire dédié et une refonte
du journal des objets ne sont pas imposés : ces changements dépasseraient une correction ciblée et
nécessitent une décision sur la progression ou une migration plus importante.
