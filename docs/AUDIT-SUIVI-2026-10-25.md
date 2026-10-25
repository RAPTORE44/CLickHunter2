# Audit de suivi ClickHunter — 25 octobre 2026

Référence avant intervention : `29c6505`. Ce suivi complète l’audit précédent et examine en priorité les changements récents et les frontières de persistance. L’inventaire statique de l’ensemble du dépôt est actualisé : 140 classes Java de production et 16 configurations YAML. Un inventaire exhaustif ne constitue pas une preuve d’absence de bugs ; les scénarios effectivement vérifiés sont précisés ci-dessous.

Le nouveau cahier reprend les exigences générales et laisse les cinq lignes d’idées de sa section 17 vides. Aucune idée nouvelle n’est inventée. Les lignes jaunes restent suivies dans `SELECTION-JAUNE.md`, selon les arbitrages déjà confirmés : piles de 64, compression 64 → 1, textes ascendants d’un bloc, compresseur consommé à l’activation, et développement dans les modules existants seulement.

## 1. Problèmes identifiés

| Priorité | Constat réel | État |
| --- | --- | --- |
| P1 | Des résultats SQL de prestige/amélioration et les reprises de réinitialisation XP conservaient un ancien objet `Player` après une reconnexion. Le seul contrôle de présence en ligne ne garantit pas son identité. | Corrigé : identité Bukkit et génération de chargement vérifiées, reprise sur le joueur courant. |
| P1 | L’activation du compresseur modifiait le PDC et consommait l’objet sans restauration en cas d’exception de sauvegarde. Le retrait devait aussi viser explicitement la main réelle, indépendamment de la copie portée par l’événement. | Corrigé et testé par injection d’une panne de sauvegarde, événement copié, main secondaire et événement obsolète. |
| P1 | Sur Paper, le clic droit dans l’air sur le compresseur n’activait pas l’objet, tandis que le clic sur un bloc fonctionnait. | Traitement corrigé : distinguer le résultat d’interaction du bloc et l’autorisation d’utiliser l’objet ; une interdiction explicite de l’objet reste respectée. Validation native consignée dans le fichier de preuves. |
| P1 conditionnel | Les stations et le marchand retiraient l’objet ou les essences avant de constater l’absence du service de livraison. Les stations pouvaient aussi rester marquées occupées. | Vérification de disponibilité avant tout retrait ; préparation et sérialisation avant réservation ; restauration en cas d’échec immédiat de démarrage. |
| P2 | Touches numériques, échanges avec la main secondaire ou touches de jet pouvaient déclencher un achat ou une transformation en survolant un bouton. | Seuls les clics gauche/droit prévus déclenchent ces actions. Les manipulations normales du dépôt des stations sont conservées. |
| P2 | La compression créait des clés et lisait l’inventaire même sans activation ; les attributs inchangés étaient retirés puis réinstallés ; le marchand sollicitait deux fois la livraison sur un achat réussi. | Clés réutilisées, contrôle d’activation préalable, attributs inchangés conservés et notification de livraison unique. |
| P2 | Une erreur de lecture de réinitialisation XP pouvait rester silencieuse. | Erreur journalisée et reprise bornée à une tâche par UUID, annulée à la déconnexion et à l’arrêt. |

Aucune nouvelle faille P0 n’a été démontrée pendant ce passage. Cela ne certifie pas l’absence de vulnérabilité dans les combinaisons non testées.

## 2. Corrections et fichiers

- `runtime/PlayerSessions.java` : contrôle de l’identité de la connexion actuellement enregistrée dans Bukkit.
- `prestige/PrestigeManager.java` : générations de chargement, abandon des callbacks d’ancienne interface, réinitialisation XP sur la bonne connexion, reprises par UUID et arrêt explicite.
- `upgrade/UpgradeManager.java` : protection des chargements et achats tardifs, relecture de la session courante après achat déjà validé, permission vérifiée avant achat, conservation des attributs identiques.
- `ClikerPlugin.java` : arrêt explicite du gestionnaire de prestige.
- `essence/CompressorItem.java` et `CompressorManager.java` : clés d’activation réutilisées, consommation de la main réelle, contrôle du tier et de la copie d’événement, restauration en cas d’exception, traitement correct du clic dans l’air.
- `gear/GearTransactions.java` : contrôles de livraison et préparation avant retrait, libération de la réservation et restitution si le démarrage échoue immédiatement.
- `merchant/MerchantMenu.java` : disponibilité de livraison vérifiée avant consommation des essences, clics contrôlés et livraison demandée une seule fois.
- `gear/EnchantMenu.java`, `ReforgeMenu.java`, `RuneMenu.java` : boutons de transformation limités aux clics prévus.
- `comfort/FarmTelemetry.java` : réutilisation des clés PDC.
- README, inventaire et fichiers de preuves actualisés ; nouvelles régressions dans les packages de tests `runtime`, `essence`, `gear` et `merchant`.

Aucun changement de schéma SQLite, de permission existante, de commande, de tarif ou de bonus d’équilibrage. Les formats et clés des activations restent compatibles avec les joueurs déjà enregistrés.

## 3. Optimisations

La vérification d’activation précède désormais la lecture de l’inventaire. Un test instrumenté effectue cent appels sans activation et constate zéro lecture d’inventaire. Les clés d’activation, auparavant reconstruites à chaque passage, sont préparées à l’initialisation. La télémétrie conserve ses clés dans une petite table privée limitée aux noms internes.

Une amélioration sans changement d’attribut ne retire plus puis ne réinstalle un modificateur identique. Un achat marchand n’appelle plus deux fois la filière de livraison. Ces suppressions d’opérations sont vérifiables dans le code ; aucun gain chiffré de TPS ou de CPU global n’est annoncé sans test de charge représentatif.

Les appels Bukkit et la mutation des inventaires restent sur le thread principal ; les transactions SQL restent dans leurs files dédiées. Les générations de chargement ne sont pas persistées et sont effacées à la déconnexion.

## 4. Sécurité et données

Les achats restent autoritaires en base. Une opération déjà validée avant déconnexion n’est pas annulée artificiellement : sa progression est relue pour la connexion courante. L’ancienne interface ne reçoit pas de callback tardif. Les réinitialisations XP gardent leur marqueur persistant et ne sont acquittées qu’après sauvegarde du joueur.

La protection du compresseur respecte une interdiction explicite d’utilisation d’objet, notamment pendant une livraison `/claim`. Une simple interdiction de l’interaction du bloc dans l’air ne bloque plus à tort l’activation. Un événement dont le tier diffère de celui de la main réelle est refusé.

La restauration après exception de sauvegarde protège l’état en mémoire et conserve l’objet pour réessayer. Ce n’est pas une garantie d’atomicité entre le fichier vanilla du joueur et SQLite en cas de panne matérielle ou d’arrêt brutal pendant une écriture. Le journal d’escrow et la procédure de sauvegarde cohérente restent des évolutions séparées, déjà identifiées.

## 5. UI/UX et français

Les indisponibilités des stations, marchands et activations affichent une explication courte et confirment la conservation de l’objet ou des ressources. Les détails d’exception sont réservés aux journaux. Une touche de déplacement d’item ne déclenche plus une dépense inattendue sur un bouton.

Les 46 commandes restent documentées par catégorie dans le README. Le clic droit dans l’air et sur un bloc active le compresseur selon le même objectif, avec protection de la main secondaire et de la double activation. Les demandes précédentes concernant l’animation des dégâts et les piles restent appliquées.

## 6. Tests et limites

Résultat : **177 tests automatisés, zéro échec, zéro erreur, zéro test ignoré**. Le client natif confirme l’activation dans l’air, la non-consommation d’un second objet déjà activé et la conservation du bonus ARMOR à 0,2 après reconnexion puis arrêt/redémarrage. `PRAGMA quick_check` renvoie `ok` pour les bases locales `economy.db` et `daily.db`. Les preuves sont enregistrées dans `validation/audit-suivi.json`, avec le SHA-256 du JAR.

Régressions ajoutées : résultats SQL différés avec reconnexion sous le même UUID, relecture d’un achat déjà payé sans ancien callback GUI, réinitialisation XP de la bonne instance, consommation effective de la main, erreur de sauvegarde, activation en main secondaire, événement obsolète, interaction d’air refusée seulement côté bloc, interdiction explicite de l’objet, indisponibilité de livraison et touches numériques/main secondaire.

MockBukkit ne fournit pas tous les attributs et méthodes Paper. Les tests de reconnexion adaptent uniquement leur double de test pour `getModifier` ; l’attribut ARMOR et son maintien après reconnexion sont contrôlés sur Paper réel. Les tests de panne injectent des exceptions et ne simulent pas une coupure physique de disque.

L’environnement natif est isolé : Paper 1.21.4 build 232, Java 21.0.9, client automatisé local et comptes synthétiques. Citizens, ItemsAdder, DecentHolograms et la charge à grande population restent à vérifier avec les versions de production. Les anciennes validations ne sont pas présentées comme de nouveaux essais.

Le `/reload` global reste non certifié : l’exception Paper/Brigadier observée au passage précédent n’est pas corrigée par ces modifications. Déployer par arrêt propre et redémarrage, après sauvegarde de la base et des mondes/playerdata.

## 7. Évolutions futures à valider

| Évolution | Intérêt joueur | Impact gameplay / données | Complexité | Priorité |
| --- | --- | --- | --- | --- |
| Journal d’escrow pour les transferts inventaire/base | Mieux récupérer après arrêt brutal | Migration et reprise à concevoir, aucun changement de récompenses prévu | Élevée | P1 |
| Sauvegarde cohérente base + mondes/playerdata et essai de restauration | Réduit la perte après incident | Exploitation serveur, procédure à valider sur la vraie installation | Moyenne | P1 |
| Charge représentative avec spark et plusieurs clients | Mesure la fluidité réelle | Aucun changement d’équilibrage | Moyenne | P1 |
| Poursuite des lignes jaunes sur les modules existants | Confort et progression selon les choix confirmés | Respect des arbitrages et vérification par lot | Variable | P2 |
| Localisation centralisée des messages récents | Personnalisation et cohérence du français | Compatibilité des configurations à conserver | Moyenne | P2 |

Les pets, Ordres, instances et services multi-serveurs restent hors du périmètre confirmé. Les idées non jaunes ne sont pas activées sans validation.
