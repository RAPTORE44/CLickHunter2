# Améliorations de confort, livraison et hologrammes

## Potions et gains

Couleurs vanilla par effet : XP bleu, Gemmes violet, Écus jaune, Force/Dégâts rouge, Vitesse turquoise, Chance vert, Cendres orange. Palette RGB dans `boutiques/potions.yml`. Les potions d’un même effet gardent leur couleur, quelle que soit leur puissance ou leur durée. Les objets anciens sont recolorés à la connexion, à l’ouverture des coffres et lors de leur livraison ; leurs données et quantités sont conservées.

Le shop ajoute les potions Gemmes ×2 et Cendres ×2 en 1, 5 et 20 minutes. Elles multiplient les gains de combat correspondants, y compris la répartition de récompenses de raid ; elles ne multiplient pas les ventes et les échanges. Tarifs dans `boutiques/catalogues.yml`, nouvelles valeurs ajoutées sans remplacer les tarifs personnalisés.

Les achats et drops arrivent dans l’inventaire quand il y a de la place. Le surplus seulement reste dans `/claim`. Une place dans une pile existante compte comme de la place disponible. Les achats utilisent toujours un journal SQL durable avant livraison, même si la remise automatique est immédiate : ce journal évite de perdre un objet pendant une coupure. Il ne faut pas le confondre avec un passage obligatoire du joueur par `/claim`. L’ouverture d’un autre menu ne bloque plus cette livraison. Le shop reste ouvert après un achat.

Les récompenses d’un joueur hors ligne restent en attente durable puis sont livrées automatiquement à sa connexion. Dans l’avant-poste, les récompenses clicker attendent le retour au spawn pour préserver les inventaires séparés. Les monnaies restent créditées directement. Une récompense non empilable qui n’a pas la place requise ne remplace aucun objet existant. Une récompense empilable est fractionnée atomiquement si seule une partie peut entrer : la quantité qui rentre est livrée, le surplus reste en attente.

## Gestion des sacs

`/sac` conserve ses neuf cases et ses sept coffres de 54 cases. Gauche ouvre le sac ; droite ouvre sa gestion. `/sac gestion` ouvre directement la gestion du premier sac. Le tri regroupe les piles réellement identiques, en conservant leurs métadonnées. Le dépôt rapide ne prend que les essences de mobs, y compris leurs versions compressées, et laisse le surplus dans l’inventaire.

Chaque opération affiche un aperçu et exige une confirmation. Si le contenu a changé ou qu’une livraison est en cours, l’aperçu est refusé et doit être recalculé. Les sacs verrouillés et les inventaires isolés restent protégés.

Pour les transferts en bloc, inventaire et contenu des sacs sont enregistrés ensemble dans le playerdata avant la copie SQLite. Le snapshot binaire `bags_snapshot_v1` devient prioritaire au chargement ; les changements manuels des sacs le mettent aussi à jour. Si la copie SQL est ancienne après une coupure, le snapshot joueur reprend le contenu confirmé. Un échec de sauvegarde signalé lors du transfert en bloc annule les modifications des deux côtés. **Sauvegarder/restaurer les playerdata et les données SQLite ensemble** ; ne pas restaurer uniquement l’ancienne table `bags` en espérant écraser un snapshot joueur plus récent.

## AH et favoris

- `/ah rechercher <nom>` recherche sans tenir compte de la casse ni des accents.
- Le bouton Recherche prépare la commande dans le chat ; Catégories filtre les épées, armures, potions, essences, talismans et autres objets.
- L’étoile enregistre ou retire la recherche courante. `/ah favoris` affiche jusqu’à dix recherches enregistrées dans les données du joueur, conservées après reconnexion.
- Filtres et tri s’appliquent avant la pagination et restent actifs quand on change de page ou qu’on actualise.

## Talismans et zones

La collection conserve ses pages débloquées/non débloquées. Chaque talisman débloqué indique s’il est actif dans les sacs, absent des sacs, remplacé par un tier supérieur ou suspendu dans l’avant-poste. Le bouton Mes bonus actifs montre les familles générales, le tier retenu, les effets contre les créatures/boss et le talisman du trésor. Un talisman découvert n’accorde pas automatiquement son bonus : il doit être dans un sac. Les cinq familles générales ne cumulent que le meilleur tier ; les règles existantes des talismans de créature sont conservées.

`/warp` ajoute aux zones débloquées leurs PV et récompenses de base, les ressources, le nombre de talismans de créature/boss encore manquants et des indications pour consulter le bestiaire/la collection. Les valeurs affichées sont les valeurs de base configurées, pas une promesse de gains après tous les bonus. Les zones verrouillées ne dévoilent pas la créature.

## Avant-postes publics

`/avantposte visites` ouvre les terrains publics, avec pagination. Gauche visite ; droite ajoute ou retire un favori. `/avantposte favoris` n’affiche que les favoris encore publics. Le menu principal de l’avant-poste propose aussi ce navigateur.

`/avantposte prive` réserve les visites au propriétaire et aux joueurs de confiance ; les autres visiteurs présents sont ramenés hors du terrain. `/avantposte public` réouvre les visites. Les menus et raccourcis ne permettent pas de contourner l’isolation pour ouvrir les sacs clicker ou exporter des objets par l’AH. Les contrôles de propriété restent en place ; être visiteur n’autorise pas à construire ou à ouvrir les coffres privés. Les favoris sont conservés par joueur et la confidentialité par terrain.

Un administrateur ayant `cliker.services.admin` peut mettre un terrain public à l’honneur pendant sept jours avec `/avantposte vedette <pseudo>`, ou retirer la mise en avant avec `/avantposte vedette retirer`. Il s’affiche en tête, avec un badge. Aucun bonus de combat n’est accordé par cette mise en avant.

## Résumé et diagnostic

`/session` affiche la durée depuis la connexion, les coups, créatures tuées, boss de zone vaincus et gains de chasse confirmés en Écus, Gemmes, Cendres et XP, avec un rendement par minute. Il ne compte pas les ventes, dons ou achats comme du revenu de chasse. La dernière session reste résumée au retour du joueur. Le menu principal reçoit un bouton Session, ainsi que Warp et Talismans, sans écraser les cases personnalisées existantes.

`/diagnostic` demande `cliker.diagnostic.admin` (op par défaut). Il consulte SQLite avec `quick_check`, les livraisons en attente, les mobs actifs/manquants/en attente de chunks, les PNJ gérés par le plugin, le fournisseur d’hologrammes et les destinations de warp absentes. Les résultats sont paginés et actualisables. La console reçoit le texte. Le diagnostic ne modifie ni ne supprime de données ; les vérifications SQL restent hors du thread serveur.

## Holographic Displays

Les hologrammes gérés par ClikerV2 passent par un fournisseur commun : classements, minuteur, stations, titres des PNJ, sacs/upgrade, noms/PV des mobs et des raids, et dégâts flottants. Le corps et les interactions des PNJ restent dans ZNPCsPlus 2.0.0. Les anciennes lignes ZNPCs sont retirées pour éviter de doubler les textes. Les anciens classements et stations DecentHolograms connus de ClikerV2 sont retirés lors de la migration ; les créations des autres plugins ne sont pas modifiées.

L’intégration utilise **l’API 3 d’Holographic Displays**. Il faut une distribution du fournisseur qui fonctionne réellement sous Paper 1.21.4 ; préciser seulement « 1.21.4 » ne permet pas d’identifier son JAR. Le [dépôt officiel consulté](https://github.com/filoghost/HolographicDisplays) contient des adaptateurs jusqu’à Minecraft 1.20 et n’apporte pas de preuve de compatibilité avec 1.21.4. Aucun JAR Holographic Displays compatible n’était disponible dans le serveur d’essai. L’adaptateur est testé sur le contrat de l’API ; le rendu réel du fournisseur doit être vérifié avec le JAR installé sur le serveur utilisateur.

Si le fournisseur est absent ou incompatible, `affichage/hologrammes.yml` active par défaut un secours natif TextDisplay avec un avertissement au démarrage. `secours-natif: false` désactive ce secours. `/diagnostic` indique le fournisseur effectivement utilisé. Les dégâts restent privés au frappeur, montent d’un bloc puis disparaissent, avec les plafonds existants par joueur et au total.

Holographic Displays rend des textes qui font face au client : son API ne propose pas une orientation fixe vers l’est. Les positions des classements restent sauvegardées ; le secours natif peut conserver l’orientation fixe. Les autres réglages de textes restent dans leurs dossiers habituels (`affichage/classements.yml`, `affichage/damage-popups.yml`, `pnj/`, `navigation/warps.yml`, `mobs/creatures.yml`). Arrêt et redémarrage normaux requis pour installer la mise à jour.

## Animation du TAB et animation du chat

Le pied du TAB alterne Site, Boutique, Discord et Vote toutes les dix secondes, avec un indicateur de couleur changeante. Régler `animation.secondes-par-lien` et `animation.messages` dans `affichage/tab.yml`. Les trois liens précédemment affichés en permanence sont remplacés une fois par `{annonce}` lors de la migration, sans supprimer les autres lignes personnalisées du pied. Site/boutique/vote utilisent pour le moment `https://clickhunter.fr`.

`activites/tchat.yml` règle les messages d’aide (toutes les 180 secondes après un premier message au bout de 30 secondes), les questions/réponses et les jeux automatiques (toutes les 300 secondes, manche de 45 secondes par défaut). Les jeux alternent calculs, devinettes Minecraft et langues parlées dans des pays. Les pays multilingues acceptent plusieurs réponses. Casse, accents, espaces et articles initiaux sont normalisés.

`/jeutchat` affiche la manche en cours. Un administrateur ayant `cliker.jeutchat.admin` peut faire `/jeutchat lancer [calcul|devinette|pays]` ou `/jeutchat stop`. Les réponses se donnent dans le chat normal, en respectant les éventuelles modérations du serveur. Le premier gagnant reçoit **une seule lootbox DIVINE** existante ; son enregistrement est idempotent par manche et l’annonce de victoire suit l’enregistrement durable. Réponses simultanées ou répétées ne créent pas de seconde récompense. La livraison suit les règles inventaire, surplus et isolation décrites plus haut. Une manche en cours n’est pas reprise après redémarrage.

## Taille et réapparition des créatures

Les magma cubes sont de taille moyenne `2` (NBT vanilla `Size: 1`), et les ghasts ont une échelle `0.5`, soit environ deux blocs de large. Ces valeurs sont dans `mobs/creatures.yml`, champs `taille` de `magma_slime` et `echelle` de `ghast`. Elles s’appliquent aux créatures normales et aux invocations correspondantes, pas aux boss Golems.

`/respawnmobs` (alias `/mobsrespawn`) rétablit tous les points de créatures placées, en conservant les positions sauvegardées et sans recréer les invocations temporaires. `/spawnboss` (alias `/bossrespawn`) lance une vague de tous les **boss de zone**, avec annonce globale, et recale le prochain déclenchement automatique. Les deux commandes demandent `cliker.mob.admin` et fonctionnent depuis la console. Elles ne lancent pas le boss public à seuil ni les boss des raids privés. Les chunks des points concernés sont chargés pour éviter les doublons avant réapparition ; un grand nombre de points peut rendre cette opération administrateur coûteuse.

## Validation de la livraison

240 tests automatisés réussis, sans échec ni test ignoré. 23 vérifications/scénarios natifs consignés sur Paper 1.21.4 build 232 avec ZNPCsPlus 2.0.0 : session, diagnostic et permissions, aperçu des zones, talismans, recherches/favoris, sacs, visites et confidentialité, livraisons complètes/partielles, rotation du TAB, gagnant unique, tailles et réapparitions. Le JAR final a également passé un démarrage et un contrôle de ses menus. SQLite `quick_check` : `ok`.

Le [compte rendu](validation/ux-hologrammes-tchat.json) précise les contrôles, l’empreinte du JAR et les limites. Le fournisseur Holographic Displays réel n’était pas installé : le secours natif a servi aux essais, et le contrat API a été testé séparément.
