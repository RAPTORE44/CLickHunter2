# Audit complet ClickHunter — 26 octobre 2026

L’audit a repris le cahier des charges joint, les demandes déjà confirmées et les composants réellement présents dans le dépôt. Les cinq idées nouvelles de sa dernière section étaient laissées vides : aucune mécanique ni modification d’équilibrage n’a été inventée. Les réglages précédents sont conservés, notamment les 31 jours du calendrier, les tarifs et gains, les 19 zones, les drops, les piles de 64 et la conversion de compression 64 → 1.

L’inventaire [des sources et configurations](INVENTAIRE-AUDIT.md) indexe 179 fichiers Java et 34 YAML. L’inventaire est un outil de couverture, pas une preuve d’absence de défaut.

## Résultat de la revue

| Priorité | Risque observé | Correction appliquée |
| --- | --- | --- |
| Élevée | Le kill du mob validait monnaie et statistiques SQL, puis livrait essences et coffres séparément. Une interruption ou un échec SQL pouvait désynchroniser ces récompenses. | Les objets générés par un kill sont enregistrés avec le reçu du kill et livrés après validation. |
| Élevée | L’ouverture d’une lootbox consommait l’objet en mémoire avant le crédit. | Le retrait passe par le journal de sortie existant ; retrait, reçu et crédit sont réunis transactionnellement et la reprise ne crédite pas deux fois. |
| Élevée | Des nombres SQL anormaux pour les niveaux d’amélioration pouvaient être tronqués par `getInt`. | Les valeurs sont lues et vérifiées dans 0–100 ; les données invalides sont refusées sans réécriture ni débit. |
| Élevée | Une inscription payante pouvait rester débitée si le téléporteur de salle refusait l’entrée. | Remboursement idempotent lié à un reçu unique de cette tentative ; il est refusé si le joueur a ensuite enchéri. Les frais d’une sortie volontaire gardent leur règle existante. |
| Moyenne | Les réponses de chat mises en file pouvaient atteindre une manche démarrée après leur réception. | Le callback est lié à l’identifiant de la manche observée ; une ancienne réponse ne peut gagner le jeu suivant. |
| Moyenne | Un visiteur d’avant-poste pouvait placer certaines entités sans passer par la protection des blocs. | Protection ajoutée à l’événement de placement d’entité, avec tests propriétaire, visiteur et limite de parcelle. |
| Moyenne | Le contrôle de dégâts des raids dépendait du client pour la portée. | Vérification serveur commune : monde, état vivant, portée de six blocs et ligne de vue. |
| Moyenne | Téléportations avec coordonnées/rotation non finies ou hors hauteur. | Validation préalable des coordonnées, rotation, hauteur et limite du monde. |
| Moyenne | Certains modules optionnels mal configurés pouvaient arrêter l’initialisation de commandes non liées. | Piñata, enchères, tags/couleurs/bienvenue enregistrent une erreur exploitable et désactivent proprement leurs commandes concernées. |
| Moyenne | L’ancienne progression Daily plafonnait au jour 28 et écartait 29–31 lors d’une migration. | Calendrier 31 jours restauré ; sauvegarde YAML préalable et ajout des entrées manquantes sans remplacer les valeurs sauvegardées. |
| Faible | Le lait effaçait les bonus visibles mais oubliait les monnaies Gemmes Sombres et Cendres. | Tous les effets persistants du plugin sont nettoyés ensemble. |
| Faible | Des boutons identiques étaient retraités à chaque rafraîchissement ; des réponses UI différées pouvaient rouvrir une ancienne vue. | Mises à jour de vue vérifiées contre la session et le menu courant ; assertions de non-réouverture ajoutées. |

Les tests de sécurité prestige et d’amélioration refusent également les nombres négatifs, hors plage, fractionnaires ou impossibles à représenter sans tronquer. Les modifications conservent les formats de données et les comportements d’économie, à l’exception de la remise en cohérence du calendrier explicite à 31 jours.

## Couverture et vérification

La suite complète contient **336 tests : zéro échec, zéro erreur, zéro test ignoré**. Elle couvre SQLite réel pour les transactions et redémarrages simulés, MockBukkit pour menus, protections et événements, ainsi que des erreurs injectées pour tester les chemins de reprise. La compilation propre avec Java 21 et Paper API 1.21.4 a réussi avec `bash /workspace/tools/setup-cliker.sh`. `git diff --check` est propre.

Les premières exécutions ont fait ressortir et permis de corriger une faute de syntaxe dans la migration SQLite du reçu d’enchère et des assertions qui validaient encore l’ancien calendrier de 28 jours. La dernière exécution complète est verte.

Les optimisations restent ciblées : aucune boucle SQL synchrone nouvelle, aucune recherche de joueur réseau, aucune réécriture de configuration invalide, aucune décoration répétée de case inchangée. Aucun gain chiffré en TPS ou capacité joueurs n’est annoncé sans profilage représentatif. Aucun test de charge de la population du serveur n’a été exécuté dans cette passe.

## Limites restantes

- Les journaux transactionnels couvrent les sorties AH, l’ouverture des lootboxes, les gains d’un kill et le remboursement des entrées d’enchères traitées ici. Ils ne rendent pas automatiquement crash-safe chaque dépôt de station, opération de sac ou parcours d’essence déjà existant.
- Les tests de pannes reproduisent des erreurs logicielles contrôlées. Ils ne certifient pas une coupure électrique à chaque instruction, la durabilité du matériel, ni la cohérence d’une sauvegarde prise pendant que Paper écrit les mondes.
- Le JAR de test réel d’Holographic Displays pour 1.21.4 et le pack ItemsAdder de production ne sont pas présents dans l’environnement. Les interfaces API/secours peuvent être testées, mais leur rendu réel en jeu n’est pas validé.
- Le build inclut ZNPCsPlus 2.0.0 disponible localement. Aucune validation avec LuckPerms, ItemsAdder ou Holographic Displays de production n’est revendiquée.
- La suite unitaire ne remplace pas l’observation visuelle d’un joueur, un redémarrage complet sur copie de la base de production ni un test de charge avec une population réaliste.

## Propositions de suite

| Priorité | Proposition | Effet joueur | Impact sur l’équilibrage | Effort |
| --- | --- | --- | --- | --- |
| P1 | Étendre le journal d’objets aux stations et dépôts d’essences, après scénarios de lots propres à chaque atelier. | Réduit les pertes/doublons lors d’une interruption. | Aucun changement de prix ou rendement. | Élevé |
| P1 | Tester une sauvegarde cohérente puis une restauration complète de base, playerdata et mondes sur copie. | Reprise fiable après incident. | Aucun. | Moyen |
| P1 | Profilage Spark pendant une session représentative de combats et d’événements. | Identifie les ralentissements réels. | Aucun avant mesure. | Moyen |
| P2 | Objectif de progression suggéré dans `/menu` à partir des données réellement débloquées. | Rend les prochaines étapes plus lisibles. | Affichage uniquement. | Moyen |
| P2 | Comparaison des objets avant achat ou amélioration. | Aide à choisir avec des valeurs compréhensibles. | Affichage seulement, valeurs existantes. | Moyen |
| P3 | Politique de rétention des journaux après restauration testée. | Simplifie la maintenance d’une base qui grandit. | Aucun ; pas de purge automatique. | Moyen |

