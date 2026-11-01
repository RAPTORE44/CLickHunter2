# Vérification des dernières demandes — 31 octobre 2026

> Rapport historique de la version 1.0.4. Les ajouts suivants sont décrits dans [la livraison 1.0.5](MISE-A-JOUR-1.0.5.md) : AFK, courrier, raids publics, parchemins de victoire, notifications et critiques.

## Version et portée

Audit du code au commit `788b22c9eb011fbd9448d571a7db2585516e83e4`, version **1.0.4**. Une lecture directe de GitHub (`git ls-remote origin refs/heads/main`) confirme ce même commit sur `main`. Le JAR racine et `/workspace/artifacts/ClikerV2.jar` portent la version 1.0.4 et le même SHA-256 :

`c6cf41b3ba52ebb31c821a338926cc36433c28257ffaf9be0458d1c221836633`

Les tests Maven ont été relancés pendant cette vérification : **449 tests, 109 suites, zéro échec, erreur ou test ignoré**. Les essais Paper ci-dessous sont des résultats conservés de la livraison précédente, relus pendant cet audit ; ils n'ont pas été rejoués sur le serveur de production.

Il faut distinguer la présence du code, les tests automatisés et l'installation sur le serveur. Cet audit ne certifie pas le JAR actuellement installé en production, à laquelle cet environnement n'a pas accès.

## Dernières demandes reprises et présentes

| Demande retenue, après les dernières précisions | Vérification |
| --- | --- |
| Toujours jour, y compris après chargement d'un monde | `world/PermanentDay`, tests `PermanentDayTest`. |
| Épée du Dresseur : 5 dégâts de base | Constante partagée dans `combat/SwordCommand`. Essai Paper non OP : 100 → 95 PV. Les bonus restent additionnels. |
| Armes et armures cohérentes entre OP et non OP, notamment Âne | `GearParityTest`, identifiants accentués et coup final corrigés dans `CustomMobManager`. Essai Paper : un non OP tue aussi le mob avec 30 d'armure vanilla. |
| Compression 64 → 1 plafonnée à la rareté des achats d'équipement | Barème commun `gear/EquipmentEssences`, compresseur automatique et conversions marchandes. Vache reste en Commun ; décompression des anciennes ressources conservée. |
| Boss de zone : PV ×100 et aucun dégât | `CustomMobManager`, attaque native à zéro et annulation des dégâts. Essai Paper : attaque de 12 dégâts annulée sur non OP. |
| Boss de raid : PV ×1000, déplacement et attaques | `raid/RaidModule`. Ne pas confondre ces règles avec celles des boss de zone. |
| Boss serveur moins résistant, attaques à distance et apparition forcée | PV publics configurables, attaques télégraphiées et `/boss spawn`. Présents ; déplacement et particules observés dans les essais Paper précédents. |
| Double respawn : chance de faire apparaître un mob supplémentaire | `gear/DoubleRespawn`, branche de mort des mobs normaux et tests dédiés. Boss et invocations temporaires exclus. |
| Suppression de l'hologramme global « Prochains boss » | Nettoyage de l'ancien point, y compris SQLite. Les compteurs individuels par zone restent présents. |
| Secondes rétablies sur les trois types de piñata | `pinata/`, tests d'affichage et paquets Paper précédemment contrôlés. Cette demande remplace les anciennes demandes de masquer le temps. |
| `/drop` à la place d'antidrop et ramassage | Commande unique basculant les deux actions ; anciens noms retirés de `plugin.yml`. Essai Paper non OP réalisé. |
| `/inv` pour déposer et retirer des objets chez un joueur | Inventaire réel du joueur en ligne, permission staff et contrôles de changement de profil. Essai Paper : trois diamants effectivement déposés. |
| `/trash`, `/poubelle`, alias `/pouvelle` | Menu de 54 cases ; gauche un objet, droite la pile ; reprise avant fermeture. Tests et parcours Paper conservés, dont récupération après déconnexion sans duplication. |
| `/flyspeed`, `/god`, `/heal`, `/feed` | Commandes déclarées avec permissions staff ; comportements vérifiés dans les essais Paper 1.0.2. |
| Clic droit sur un joueur pour ses statistiques | Lecture seule de la bonne cible ; vérifié dans les essais Paper précédents. |
| Classements simplifiés et menu sans stations enchantement/reforge/runes | Menus présents, tests et essais Paper 1.0.2. Les commandes et stations elles-mêmes restent disponibles. |
| TAB et scoreboard conservés et corrigés | RGB, animation, suppression de la ligne CLICKER, compteurs courts et étoile de menu en gras. Migration de réactivation, tests et essais Paper conservés. |
| Lootboxes Ender Chest sans Gemmes Sombres ni Cendres | Nouvelles caisses en Ender Chest ; ouverture des anciennes sans ces monnaies. Les anciens objets déjà stockés peuvent conserver leur apparence de coffre. |
| Daily et jeux de chat adaptés au prestige | Paliers dans `recompenses/caisses-prestige.yml` ; Commune au prestige 0. Daily : une caisse, deux aux jours 7/14/21/28/31 ; configurations personnalisées conservées. |
| Plus de message « Récompense récupérée » | Notifications de réussite retirées des livraisons automatiques et du Daily, erreurs conservées. Essai Paper précédent. |
| Pas de ponte vanilla ni de nouveaux succès vanilla hors recettes | `mobs/VanillaRewardsListener`, tests dédiés ; les progrès déjà enregistrés ne sont pas effacés. |
| Tutoriel en bossbar | `tutorial/OnboardingGuide`, cinq étapes fondées sur les actions réellement effectuées ; progression persistante. |
| Zones vides en veille, effets de coups personnels | `performance/ZoneActivity`, mobs, hologrammes, raids et piñatas. Mesure conservée : 26 points, 26 mobs actifs avant, zéro après sans joueurs. Essai de retour : un mob unique à 95/100 PV ; blessure privée, mort partagée. Aucun gain global CPU/RAM n'est certifié par cette seule mesure. |
| BuyZone : retour de 15 blocs au nord, regard au sud, créatif libre | `buyzone/BuyZone.northReturn`, contrôles créatif et `BuyZoneTest`. Géométrie de la carte de production non inspectée. |
| Raccourcis sur blocs, donateurs et réouvertures rapides des menus | Modules et tests présents (`DonorPodiumTest`, `MenuOpenGuardTest`, etc.). Résultats de restauration 1.0.1 conservés dans `docs/validation`. Les points réellement placés sur le serveur ne sont pas accessibles ici. |
| Invitations de raid réellement prises en compte | Gestion des membres et tests `PartyRaidsTest`, dont acceptation, lancement, téléportation refusée et reprise. Ce parcours ne réalise pas l'ouverture publique demandée plus anciennement. |
| Jeux de chat toutes les quinze minutes, accents facultatifs | Intervalle de 900 secondes, normalisation Unicode et réponses simples dans `features/ChatActivities`. |

Les détails et les limites des essais de livraison se trouvent dans [la livraison 1.0.2](MISE-A-JOUR-1.0.2.md) et [la livraison 1.0.4](OPTIMISATION-1.0.4.md).

## Demandes anciennes toujours incomplètes

Ces points ne doivent pas être présentés comme réalisés, même si les tests du plugin existant passent :

1. **Monde AFK et `/afkshop` : absents.** Pas de système complet d'isolation, points toutes les dix minutes, boutique de caisses et quatre talismans AFK.
2. **Messagerie entre joueurs : absente.** `rewards/MailStore` alimente `/claim` ; ce n'est pas une interface pour écrire des livres, envoyer des pièces jointes, bloquer des expéditeurs et recevoir leurs notifications.
3. **Ouverture publique d'un raid à trois participants et parts au tiers : absente.** Le groupe privé existant accepte jusqu'à huit joueurs ; son existence ne satisfait pas cette demande distincte.
4. **Parchemin gagné à la victoire d'un raid : absent.** L'obtention actuelle est un drop configurable sur les mobs de chasse, ou une attribution administrateur.
5. **Notifications serveur sous forme de succès Minecraft : absentes.** Bloquer les succès vanilla ne crée pas les notifications personnalisées demandées.
6. **Toutes les lignes jaunes du classeur : non terminées.** Le suivi [SELECTION-JAUNE.md](SELECTION-JAUNE.md) conserve des éléments partiels ou à développer ; certaines mentions doivent être recoupées avec les livraisons ultérieures. Les fondations explicitement mises en attente ne sont pas des livraisons promises pour cette passe.

L'ancien [audit des demandes](AUDIT-DEMANDES.md) signalait déjà les principaux manques AFK, courrier, raids publics et notifications. Une interruption ou une reprise ultérieure ne permet donc pas de considérer tout l'historique comme terminé.

## Ce qui reste à confirmer sur le serveur

- `/version ClikerV2` doit afficher **1.0.4** après remplacement du JAR et redémarrage. La présence de cette version sur GitHub ne met pas automatiquement à jour le serveur Minecraft.
- Les essais disponibles utilisent Paper 1.21.4, Java 21 et ZNPCsPlus 2.0.0. Le rendu réel d'ItemsAdder et d'Holographic Displays reste à valider avec les JAR compatibles installés sur le serveur ; le secours natif a été testé.
- Les performances de production et les interactions avec les autres plugins nécessitent les données de ce serveur. Paper classique conserve les opérations de monde sur son thread serveur ; le plugin n'est pas arbitrairement divisé sur cinq cœurs.

Cette intervention ajoute ce compte rendu ; elle ne change ni les fonctionnalités ni le JAR 1.0.4.
