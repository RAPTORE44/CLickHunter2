# Mise en conformité du cahier maître — état au 24 octobre 2026

Cette révision est une **mise en conformité partielle**, pas la livraison finale de
l'ensemble du cahier maître. Elle conserve l'identité et les données ClikerV2 pour
éviter d'invalider les clés PDC et le dossier historique sans migration complète.

## Modifications effectives

- Daily sur 28 jours, avec Epic/Legendary/Mythic/Divine x1→x7 et nouveaux visuels
  classiques (coffre brillant, coffre normal, wagonnet). Le pack de test historique
  est sauvegardé et migré. Les personnalisations existantes sont conservées.
- Les anciens jours 29–31 conservent leur date de claim et deviennent une fin de
  cycle : pas de seconde récupération lors de la migration.
- Scoreboard et TAB natifs désactivés par défaut. Activation volontaire dans
  `config.yml`, `displays.scoreboard.enabled` et `displays.tab.enabled`.
- Protection des essences sous forme d'œufs contre l'usage vanilla, les interactions
  avec les entités et le distributeur, en reconnaissant le PDC et non le lore.
- Enchant : coût configurable par défaut 100 000 Écus, probabilités configurables
  dans `enchants.yml` ; le niveau V occupe 0,1 % des tirages par défaut.
- Boss : multiplicateur PV configurable à x100 et échéance persistante dans
  `boss.yml`, intervalle 30 minutes par défaut. Cela n'ajoute pas encore la
  répartition complète par contribution ni toutes les commandes boss du cahier.
- Prestige : conversion romaine vérifiée de 1 à 100 et suppression des débordements
  de la formule historique des kills. Les très grandes conditions saturent à
  Long.MAX_VALUE ; ce n'est pas un équilibrage final permettant de jouer jusqu'à 100.
- Gains de lootboxes modifiables dans `lootboxes.yml`. Les valeurs des nouveaux
  tiers Legendary/Mythic sont proposées explicitement ; le pack historique complet
  est toujours nécessaire pour les reproduire à l'identique.
- Wrapper Maven 3.9.9 fourni, archive de distribution et wrapper épinglés par checksum.

## Installation de cette révision

Arrêter le serveur, sauvegarder le dossier `plugins/ClikerV2/`, remplacer le JAR et
redémarrer. Les fichiers manquants sont créés automatiquement. Le plugin conserve
son dossier et ses clés PDC historiques. Ne pas utiliser `/reload`.

Les fichiers `enchants.yml`, `boss.yml` et `lootboxes.yml` sont nouveaux. Leur
chargement est effectué au démarrage, pas via un moteur de reload partiel.
Le compteur boss peut faire apparaître une vague rapidement si l'échéance
persistée est déjà dépassée lors d'un redémarrage.

## Restant à implémenter — non certifié

- Noyau NorthBlueClicker complet, abstraction stockage multi-backends, ItemRegistry,
  CurrencyService avec audit et API publique/événements.
- PlaceholderAPI et Vault API ; la récupération de l'artefact PAPI au dépôt officiel
  a renvoyé HTTP 403 depuis l'environnement, sans ajout de dépendance non vérifiée.
- 10 zones totalement data-driven et shops de zones configurables 27/54 cases.
- Courbe XP contractuelle, LevelService, prestige configurable et transactionnel.
- Matrice finale complète des enchants, supports/conflits et /eguide.
- HDV avec commission vendeur à la publication, escrow et journal transactionnel,
  récupération des items et historique. Cette révision ne prétend pas corriger son
  ancienne transaction composite ni sa facturation.
- Vente, lootboxes, prestige, upgrades et GUI à dépôt : transactions durables,
  protections contre fermeture/crash et livraison différée des objets.
- Mailbox et livraison atomique des récompenses daily, commandes admin daily.
- Black Market, Dark Auction distincte, Spawn Shop, hub /menu, fishing, mining, events.
- /vault complet, administration /nb, validation globale, backups administrables,
  massedit, profils, overrides, reloads sûrs et audit généralisé.
- Validation réelle Paper/Citizens/ItemsAdder et scénarios de crash/charge.

Les prix d'accès et l'ancien pack d'équilibrage ne sont toujours pas fournis.
Le fichier Buyzone conserve donc ses tarifs non configurés à -1.

Les tests existants et nouveaux valident uniquement les capacités qu'ils exercent.
La réussite du build ne constitue pas une certification production globale.
