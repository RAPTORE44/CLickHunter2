# Audit et optimisation 1.0.7 — 1er novembre 2026

## Périmètre et méthode

Référence : version 1.0.6, commit `0dec03e`. Paper 1.21.4, Java 21, Maven 3.9.9, SQLite, ZNPCsPlus 2.0.0 ; dépendances et paramètres JVM de production inchangés. L'inventaire automatique couvre les **218 fichiers Java**, environ **45 331 lignes**, **300 handlers** et **35 déclarations de tâches périodiques**. Ces nombres sont un inventaire de sources, pas le nombre de tâches simultanément actives. [Inventaire par fichier](performance/inventory-1.0.7.json).

Cet inventaire est exhaustif sur les fichiers accessibles ; l'analyse sémantique approfondie porte sur les chemins fréquents, la persistance, les transferts d'objets, les interfaces et le cycle de vie. Il ne constitue pas une preuve formelle de correction de chaque ligne ni de l'absence de toute fuite dans les plugins tiers.

## Architecture et examen des familles

| Famille examinée | Composants et contraintes |
| --- | --- |
| Stockage | DatabaseManager, EconomyService, RewardLedger, stores de claim/auction/bags/outpost/referral/social/rankings ; opérations SQLite sérialisées hors thread serveur. Connexion Daily séparée, activité quotidienne faible. |
| Monde et entités | mobs, raid, pinata, world, performance, npc, hologram ; veille des zones, spawns persistants, chunks et rendu. Garder les mutations Bukkit synchrones. Aucun scan global ajouté. |
| Combat et progression | combat, gear, upgrade, level, prestige, essence, bag, hunting, lootbox ; calculs, probabilités et fréquence inchangés. Préserver les reçus et snapshots des inventaires. |
| Interfaces et réseau | ui, display, daily, auction, liveauction, merchant, outpost, cosmetics, rankings, station, wiki ; constructions d'items, pagination, caches et mises à jour. Les menus dynamiques restent réactifs. |
| Joueurs et événements | buyzone, comfort, community, social, referral, staff, security, runtime, spawn, cooldown, tuto/tutorial, discord et intégrations ; sessions, téléportations, permissions et déconnexion. |
| Configuration | config, YAML français et plugin.yml ; aucune migration de format, commande, permission ou dépendance nouvelle dans cette livraison. |

Les marqueurs `.join()` des contrats de chasse interviennent après `CompletableFuture.allOf` réussi ; ils ne prouvent pas une attente SQL sur le thread principal. Les `String.join`, `Optional.get` et `Supplier.get` trouvés par l'index ne sont pas assimilés à des attentes de futures. Les attentes d'arrêt restent contrôlées et signalent les sauvegardes non terminées.

## Diagnostic priorisé et corrections

| Priorité | Fichier / méthode | Cause et fréquence | Conséquence | Correction et justification | Risque / validation | Gain |
| --- | --- | --- | --- | --- | --- | --- |
| P1 | DatabaseManager.submit/open | Chaque opération ouvre/configure/ferme une connexion ; plusieurs opérations par élimination et par interface. | Allocations JDBC, coût du driver et des PRAGMA répétés. | Une connexion appartient exclusivement au worker SQL unique. Le jour Paris est réenregistré à chaque opération. Transaction laissée ouverte : rollback et retour à autocommit, comme avec la fermeture précédente. Fermeture en fin de file à l'arrêt. Une connexion explicitement fermée est recréée. | Élevé : tests de rollback, commit oublié, identité de connexion et arrêt ; suite de transactions/claim complète. | Mesuré : médiane du microbenchmark de lectures réduite de 70,4 à 13,5 ms par lot de 400. |
| P2 | TabListManager.refresh/priority | Le comparateur lit plusieurs fois le groupe lors du tri, O(N log N). | Appels de permissions/rang répétés. | Calcul du rang une fois par joueur et par rafraîchissement ; aucune conservation prolongée d'un rang potentiellement périmé. | Faible : comportement du tri et héritage des grades testés. | Réduction structurelle des résolutions de priorité, pas de pourcentage CPU attribué. |
| P2 | TabListManager.apply/refresh | Bandeau et ordre renvoyés même si identiques. | Paquets évitables et travail client. | Cache de l'état affiché par UUID, comparaison avant émission ; nom déjà comparé auparavant. Purge à déconnexion et désactivation. Fréquences et animation conservées. | Moyen : essais Paper des menus et charge de clients. | Non mesuré avant/après en production. |
| P1 | MailModule.open | Spam de commande : plusieurs SELECT de boîte en vol par joueur. | Gonflement de la file SQL et réponses obsolètes. | Une requête d'ouverture en vol, jeton lié à la session ; ancien résultat ignoré après déconnexion. Aucun changement des transactions d'envoi. | Faible à moyen : tests du stockage et parcours des interfaces. | Borne structurelle : une ouverture SQL simultanée par joueur pour ce menu. |
| P1 | AfkModule.shop | Même problème pour la lecture du solde AFK. | Requêtes et reconstructions inutiles. | Même garde et nettoyage ; achats restent indépendants et transactionnels. | Faible à moyen : parcours AFK existants et suite de persistance. | Même borne locale. |

## Allocations repérées par JFR et deuxième correction

Le JFR de 60 secondes est conservé en artefact `clickhunter107.jfr`, avec [résumé extrait](performance/jfr-after107-summary.json). Il couvre la fin de la charge puis le retour au repos. Les moyennes de tick périodiques vont de 0,154 à 101,194 ms (médiane 9,056 ms). Ce ne sont ni les percentiles de chaque tick ni une mesure avant/après. 25 événements GC ont été enregistrés. Les événements CPU load renvoient zéro dans cet environnement : cette valeur n'est pas interprétée comme une absence de consommation.

Les poids d'allocation échantillonnés portant une pile du plugin ont orienté deux corrections supplémentaires :

- `DailySettings.color` : cache LRU de **256 libellés de 96 caractères au maximum**, composants Adventure immuables, accès synchronisés, longs textes exclus. Invalidation par clé texte et éviction LRU. Le cache ajoute de la mémoire bornée ; aucune réduction de heap retenu n'est revendiquée.
- `MenuIcons.normalize` : motif de suppression des marques Unicode précompilé au lieu d'une compilation à chaque appel ; normalisation et résultats identiques, tests d'icônes conservés.

[Microbenchmark apparié](performance/color-paired107.json) : 8 libellés répétés, 20 échauffements, cinq paires alternées de 100 000 conversions dans la même JVM. Médiane : **53,551 ms → 2,229 ms**, soit −95,8 % pour ce cas de réutilisation idéal. Le rendu est comparé au sérialiseur d'origine ; le test vérifie aussi la borne du cache et l'exclusion des longs textes. Cela n'annonce pas un tel gain pour les chaînes dynamiques ou pour le serveur complet.

## Mesures avant / après

[Avant](performance/sql-before107.json) et [après](performance/sql-after107.json). Même environnement, JDK, Maven et code de scénario. Un échauffement de 400 lectures puis trois lots de 400 `SELECT 1` en file, attente de toutes les réponses ; nouveau fichier SQLite par exécution. Pas de réseau, de gameplay ni de JFR pendant ce microbenchmark. Les mesures incluent le passage par le worker et le driver, pas seulement l'exécution SQL.

| Indicateur | Avant | Après | Évolution |
| --- | --- | --- | --- |
| Temps médian d'un lot de 400 lectures | 70,405 ms | 13,534 ms | −80,8 % sur ce scénario uniquement |
| Plage des trois lots | 64,435–85,517 ms | 12,040–19,486 ms | Plages sans recouvrement dans ces répétitions |
| CPU global serveur | Non mesuré | Non mesuré comparativement | Non calculé |
| RAM / heap retenu | Non mesuré | Non mesuré comparativement | Non calculé |
| MSPT moyen / P95 / P99 | Non mesuré | Non mesuré comparativement | Non calculé |
| Allocations et GC comparatifs | Non mesuré | Profil JFR exploratoire après seulement | Non calculé |
| Trafic réseau comparatif en octets | Non mesuré | Non mesuré | Non calculé |

Ce petit nombre de répétitions ne remplace pas une étude statistique multi-JVM. Le gain du driver est net ici ; aucun gain de 80,8 % sur l'ensemble du serveur, la RAM ou les FPS n'est revendiqué.

## Charge réelle locale

[Résultats](performance/load-after107.json), outil [load-local.cjs](../scripts/performance/load-local.cjs). Mineflayer, clients offline, copie Paper locale, port 25582. La copie était initialement limitée à trois joueurs et throttlée à quatre secondes : le premier essai a été interrompu, puis max-players=120 et throttle=0 ont été appliqués à la copie uniquement. Ces deux fichiers sont restaurés après essais. Java `-Xms256M -Xmx1400M`, ZNPCsPlus 2.0.0, pas de JAR ItemsAdder/HD réels. Les erreurs HTTP Mojang observées proviennent du réseau de test et des téléchargements de profils.

| Clients | Connexions terminées | Observation | Statistiques ouvertes | Paquets serveur décodés |
| --- | --- | --- | --- | --- |
| 1 | 2,943 s | 5,003 s | 1/1 | 480 |
| 10 | 2,979 s | 5,003 s | 10/10 | 4 456 |
| 25 | 4,531 s | 5,008 s | 25/25 | 10 859 |
| 50 | 9,462 s | 5,014 s | 50/50 | 31 520 |
| 100 | 32,186 s | 5,017 s | 100/100 | 103 947 |

Le JFR a été activé pendant le dernier palier seulement : ces paliers ne sont donc pas un comparatif strict toutes choses égales pour mesurer une courbe de CPU.

Les compteurs couvrent tous les paquets décodés pendant la fenêtre (entités, inventaires, autres plugins et TAB), pas uniquement ClikerV2 ni les octets compressés sur le réseau. Les clients sont immobiles, pas en combat, et la fenêtre est courte. Ce résultat ne certifie pas une tenue prolongée à 100 combattants. Les temps de connexion ne sont pas la latence d'ouverture des menus. La hausse non linéaire des paquets inclut la visibilité des autres joueurs ; elle mérite un profil en arène dense avant toute nouvelle optimisation de gameplay.

## Vérifications et garanties conservées

**470 tests fonctionnels réussis, zéro échec/erreur/ignoré**, ainsi que deux scénarios de microbenchmark exécutés séparément (472 cas présents dans les rapports Surefire).

[Parcours Paper finaux](performance/functional-after107.json). Compilation Java 21 ; suite fonctionnelle complète et tests de reprise SQL. Les parcours Paper des statistiques de cible, skins, enchère de test, mannequin, calendrier et menu ont été rejoués. L'outil de charge ouvre réellement `/stats` pour chaque client. Aucun changement de dégâts, de probabilités, de récompenses, de cadence de combat, de rayon d'activation ou de format persistant. La connexion SQL partagée ne sort pas du worker ; les opérations de monde restent sur le thread serveur. Pas de nouvelle dépendance ni de réglage de GC.

## Risques résiduels et recommandations

- **P1 : file globale SQL non bornée.** Les lectures de ces deux menus sont coalescées mais ce n'est pas une borne de toutes les écritures. Rejeter aveuglément les écritures ferait perdre des éliminations ou des récompenses : pas de remplacement par une file bornée sans journal durable et politique de reprise de tous les appelants. Instrumenter sa profondeur en production puis concevoir ce contrôle séparément.
- Les sauvegardes playerdata synchrones aux transferts sensibles sont conservées : les différer sans protocole durable ferait risquer duplication/perte. Les profiler en production avant de regrouper davantage.
- Les commandes console Daily restent des effets externes distincts du reçu SQL ; un arrêt entre les deux nécessite vérification manuelle. L'optimisation ne rend pas atomiques des commandes arbitraires d'autres plugins.
- Pas de preuve d'absence de fuite prolongée : pas de heap dump comparatif de plusieurs heures ni de test de reconnexions sur plusieurs jours.
- Pas de mesure côté client des FPS ; aucun gain annoncé. Pas de validation Folia ni d'autres versions Minecraft.
- Tester en production avec Spark pendant une vraie charge de combats, capturer MSPT P95/P99, profondeur SQL, allocations et GC, puis reproduire sur copie avec les mêmes mondes et données. Conserver SQLite et playerdata dans la même sauvegarde.
- Les APIs Holographic Displays et ItemsAdder réelles restent à tester avec les JAR de production. Le rendu natif et ZNPCsPlus sont ceux de la copie.

## Reproduction

Benchmark : `mvn -Dtest=SqlBenchmark -Dbenchmark.output=/tmp/sql.json test` avec le JDK 21 et les paramètres Maven du dépôt. Scénarios clients : installer Mineflayer dans un dossier de test, exposer son dossier node_modules via `NODE_PATH`, puis lancer `node scripts/performance/load-local.cjs`. L'outil cible uniquement 127.0.0.1:25582 et une copie offline. Ne pas l'utiliser sur un serveur public. Un échec interrompt les paliers et conserve les résultats déjà obtenus.

Profil : `jcmd <pid-copie> JFR.start name=ClickHunter107 settings=profile duration=60s filename=/tmp/clickhunter.jfr`. Examiner avec JDK Mission Control ; ne pas comparer un profil sous charge à une référence idle comme s'il s'agissait d'un gain.
