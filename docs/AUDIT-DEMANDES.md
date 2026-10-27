# Audit des demandes ClickHunter — 27 octobre 2026

Cet audit compare les demandes de la conversation au code du dépôt. « Présent » signifie que le parcours existe dans le code ; aucun test sur le serveur Paper 1.21.4 de production n'a été effectué. « Partiel » signale un écart fonctionnel. Les prix qui dépendaient de l'ancien pack introuvable restent les prix actuels ou provisoires, comme autorisé.

| Demande | État vérifié | Écart ou preuve |
| --- | --- | --- |
| Paper 1.21.4, Java 21, plugin intégré | Présent | `pom.xml`, `plugin.yml`, `ClikerPlugin`. |
| `/daily`, 31 jours, minuit Europe/Paris, progression par UUID, PNJ | Présent dans le code | `daily/`; le JAR ZNPCsPlus 2.0.0 reste nécessaire en jeu. |
| `/claim` uniquement pour objets hors ligne, inventaire plein ou isolé | Présent dans le code | `rewards/ClaimService`, journal SQLite. |
| Sacs, raccourcis sur bloc, upgrades en lots, menus configurables | Présent dans le code | `bag/`, `features/ServiceNpcs`, `upgrade/`, `gui/menu.yml`. |
| BuyZone, retour au nord de 15 blocs, mode créatif libre | Présent dans le code | `buyzone/`; géométrie et téléportation à valider sur la carte réelle. |
| Bestiaire mystérieux, maîtrises, contrats, talismans | Présent dans le code | `hunting/`, `features/Talismans`; carnet hebdomadaire et défis facultatifs séparés : **absents**. |
| Guide de progression, résumé de session | Présent | `/guide` suggère une amélioration selon le solde et donne maintenant des accès cliquables ; `/session` résume la session. Journal persistant des événements majeurs : **absent**. |
| Avant-postes, inventaire isolé, favoris et vedette | Présent dans le code | `outpost/`; filtres avancés des visites : **absents**. |
| Warp masquant les zones verrouillées, AH, vente, marchands, marché noir, NPC | Présent dans le code | `features/TravelAndTools`, `auction/`, `merchant/`, `features/ServiceNpcs`. Vérification en jeu encore nécessaire. |
| Boss de zones, vagues et annonces, mobs persistants, piñata | Présent dans le code | `mobs/`, `pinata/`; comportement après plusieurs redémarrages à vérifier sur serveur réel. |
| Boss serveur public, seuil d'un milliard de mobs, apparition automatique et annonce `/boss` | Présent dans le code | `features/PartyRaids` suit `server_counters`; `raid/RaidModule` accueille les joueurs. |
| Raid privé avec parchemin et invitations | Présent, **partiel** | Les parchemins tombent actuellement sur les mobs à 0,1 % par défaut ; la demande récente veut aussi les obtenir en gagnant un raid. Le groupe va jusqu'à huit joueurs. Ouverture publique limitée à trois participants, annonce et récompense à un tiers : **absentes**. |
| Enchères légendaires toutes les trois heures, salle, inscription, trois lots et mise de trente secondes | Présent dans le code | `liveauction/`; il faut un essai à plusieurs joueurs sur Paper pour confirmer l'interface et les transitions des pages. |
| ATM de Marketus convertissant le temps de jeu en dollars/Écus | Présent dans le code | `/atm`, `/atm pnj spawn|remove`, tarif configurable. Retrait et compteur sont enregistrés dans une seule transaction SQL. Le compteur Minecraft inclut le temps immobile ; aucune exclusion AFK n’est encore en place. |
| Succès Minecraft pour les notifications serveur | **Absent** | Les notifications sont actuellement du chat, titre ou barre d'action. Les toasts de succès nécessitent une intégration dédiée côté client/datapack. |
| Courrier entre joueurs : livres rédigés depuis le menu, objets joints, blocage d'expéditeurs, modération et notification | **Absent** | `rewards/MailStore` est un journal interne de livraison d'objets, pas une messagerie entre joueurs. |
| Zone AFK isolée, dix minutes par point, `/afkshop`, prix des lootboxes et quatre talismans | **Absent** | Aucun monde ni compteur AFK. La coupure des commandes/chat, l'inventaire isolé et les récompenses restent à concevoir et tester ensemble. |
| Succès sans double crédit, enchères et progression économique sûres | Partiel | Les récompenses existantes utilisent SQLite et des clés idempotentes ; les systèmes ATM, courrier et AFK n'ont pas encore de transaction. |
| Hologrammes Holographic Displays et secours natif sans fond, pas de double nom | Partiel | Le fond transparent et le masquage immédiat des noms de raid sont appliqués au secours natif. Le rendu Holographic Displays réel dépend d'un JAR compatible Paper 1.21.4 indisponible ici. |
| Dégâts qui montent d'un bloc, compression 64 → 1, autocompresseur consommé | Présent dans le code | `combat/DamagePopups`, `essence/CompressorManager`. |
| Scoreboard, TAB, chat, tags, mariage, popularité, jeux de chat, enchères | Présent dans le code | `display/`, `social/`, `features/ChatActivities`, `liveauction/`; personnalisation des alertes par joueur : **absente**. |

Priorité de développement après cet audit : ATM avec crédit SQL unique par tranche, messagerie avec livraison atomique des pièces jointes et blocage des expéditeurs, raid public à trois places avec parts plafonnées, puis monde AFK et boutique. Chaque ajout devra comprendre une migration non destructive des données, les permissions, la configuration française, le README et des tests de transaction. Les messages de succès peuvent ensuite remplacer les annonces adaptées sans cacher les informations essentielles.
