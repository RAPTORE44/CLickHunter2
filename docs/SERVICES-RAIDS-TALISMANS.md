# Services, navigation, raids et talismans

Paper 1.21.4, Java 21. **ZNPCsPlus 2.0.0** fournit les PNJ ; ItemsAdder reste optionnel pour les icônes, via les items `icons:*` déjà installés. Les objets vendus sont de vrais objets Minecraft et conservent leurs données dans les livraisons.

## Installation et fichiers SFTP

Arrêter le serveur normalement, sauvegarder ses mondes et le dossier `plugins/ClikerV2`, remplacer le JAR puis redémarrer. Aucun effacement de base de données ni de progression n’est nécessaire. Les tables et colonnes ajoutées sont compatibles avec les données existantes.

Les configurations historiques sont déplacées automatiquement dans leurs dossiers canoniques. Une copie est conservée dans `migration-configs/`. Dès qu’un nouveau chemin existe, c’est ce fichier qui fait autorité. Ne pas réintroduire un ancien fichier à la racine pour le modifier. Redémarrer normalement après modification par SFTP.

| Dossier / fichier | Paramètres |
| --- | --- |
| `config.yml` | Paramètres généraux et économie initiale. |
| `mobs/creatures.yml` | Noms, PV, dégâts des pièces et amulettes, valeur de vente, Écus, gemmes en dixièmes et XP de base de chaque créature ; modèles de noms/hologrammes `{nom}`, `{pv}`, `{pv-max}`. |
| `navigation/buyzone.yml` | Accès, permissions, prix et positions des portails. |
| `navigation/warps.yml` | Destinations exactes des zones et de leurs boss, blocs de raccourci des sacs, protection de la carte. |
| `activites/raid.yml` | PV, dégâts, seuil avant le boss, maximum de créatures et récompenses de base des arènes. |
| `activites/raids-groupes.yml` | Limite de raids simultanés, taux des parchemins, seuil et difficulté du boss public. |
| `activites/boss-zones.yml` | Horloge des boss de zone et multiplicateur de PV ; distinct du boss serveur. |
| `activites/avantpostes.yml` | Monde de construction, extensions et marchand de blocs. |
| `boutiques/catalogues.yml` | Prix des épées, armures, potions et lait ; dégâts des épées simples ; zones et tarifs des six offres du marché noir, durée de rotation. |
| `pnj/ah.yml`, `pnj/shop.yml`, `pnj/blackmarket.yml` | Activation, position, type, skin, titre et sous-titre des PNJ de service. |
| `pnj/marchands.yml` | Apparence des marchands de progression et positions indexées par identifiant. |
| `pnj/discord.yml` | Lien, messages et apparence du PNJ Discord. |
| `recompenses/daily.yml`, `recompenses/claim.yml`, `recompenses/lootboxes.yml` | Calendrier, récupération différée et coffres de récompenses. |
| `progression/chasse.yml`, `progression/parrainage.yml`, `progression/talismans.yml` | Bestiaire/contrats, parrainage et collection de talismans. |
| `equipement/enchants.yml` | Coût et probabilités des cinq niveaux d’enchantement. |
| `affichage/scoreboard.yml`, `affichage/damage-popups.yml`, `affichage/classements.yml` | Tableau latéral, animation des dégâts et titres/formats des hologrammes top 10 (fournisseur commun Holographic Displays API 3 ou secours natif). |
| `gui/menu.yml`, `gui/icons.yml` | Menu principal et correspondances des icônes ItemsAdder. |

`data/` contient les sauvegardes SQLite, pas des fichiers de réglage. Les inventaires de construction utilisent aussi le playerdata des mondes. Conserver ces données et les mondes ensemble dans les sauvegardes. Les anciens documents qui citent `daily.yml`, `raid.yml` ou d’autres noms à la racine désignent désormais leurs chemins ci-dessus.

## Vente des essences et AH

Dans `/sell`, un clic **gauche** vend une unité de la pile cliquée ; un clic **droit** vend cette pile seulement, même si d’autres piles identiques existent dans l’inventaire. Les autres types de clic ne déclenchent pas de vente. En cas d’échec de crédit, seule la quantité effectivement retirée est restituée.

`/ah vendre <prix>` et `/ah sell <prix>` sont équivalents et proposés par Tab. La confirmation affiche les frais de publication de **1 %**, débités immédiatement et non remboursés au retrait ou à l’expiration. À l’achat, le vendeur reçoit le prix entier : cette taxe n’est pas prélevée une seconde fois. Les annonces préexistantes conservent leur ancien mode de taxation à la vente, grâce à un indicateur de migration en base.

Le bouton d’annonce permet d’ajouter **5 % du prix** aux frais initiaux. Après publication réussie seulement, le chat annonce le nom du vendeur, l’objet, sa quantité, le prix et un lien cliquable vers `/ah`. Débit et enregistrement de la nouvelle annonce sont une seule transaction. En cas de fonds insuffisants ou d’échec SQL, aucune publication n’est confirmée et l’objet est rendu ou placé dans `/claim`.

## Guides des ateliers et équipement simple

Le livre en haut à droite des trois ateliers ouvre un guide avec un objet par effet : description, équipement compatible, taux réels et niveaux possibles. Les probabilités des enchantements utilisent la configuration du moteur ; les types compatibles sont équiprobables. Les reforges n’ont pas de niveau ; les runes tirent chaque niveau de 1 à 15 avec la même probabilité. Le bouton Retour ouvre l’atelier correspondant. L’objet précédemment déposé est restitué par le mécanisme normal de fermeture ; il n’est pas transféré dans le guide.

Le PNJ shop vend les épées du bois à la netherite et les armures du cuir au diamant, contre des Écus uniquement. Ce matériel simple n’est pas accepté par les améliorations ClickHunter et ne peut pas être amélioré par enchantement vanilla, enclume ou forge. Les réparations par assemblage sont également bloquées pour préserver cette distinction. Les dégâts des épées simples sont configurables indépendamment de leur matériau.

## PNJ et boutiques

Toutes les commandes sont répertoriées par catégorie dans le [README](../README.md). Les commandes de gestion ci-dessous demandent `cliker.services.admin` (op par défaut).

- `/pnjservice spawn ah`, `/pnjservice spawn shop`, `/pnjservice spawn blackmarket` : un PNJ par service, à sa position ; création d’un doublon refusée.
- `/pnjservice remove <service>` : retirer le PNJ.
- `/pnjservice skin <service> <pseudo|villager>` : choisir un skin de joueur ou un villageois.
- Pour les marchands de progression : `/pnj skin <mob> <pseudo|villager>`, permission `cliker.mob.admin`.

Les marchands utilisent par défaut un véritable modèle de villageois, pas un skin de joueur choisi arbitrairement. Les PNJ restent tournés vers l’ouest. Il n’existe **aucune commande joueur `/shop` ou `/blackmarket`**.

Le marché noir occupe un double coffre et propose exactement six offres : quatre œufs de créatures et deux potions. Chaque offre dispose d’un stock global de cinq unités par rotation, trois heures par défaut. Les zones associées sont configurables ; une offre inaccessible masque l’objet. L’achat vérifie à nouveau le stock et le solde en transaction, pour empêcher la vente d’une sixième unité lors de clics simultanés. Aucun équipement rare, armure, épée ou talisman n’y figure.

Les œufs du marché noir invoquent une créature unique, sans créer de spawn permanent, près des créatures de leur zone débloquée (32 blocs). Le plafond d’invocations actives est de 30 par monde. Un œuf refusé reste dans l’inventaire.

## Potions de bonus

Le shop propose Chance ×2/×3, Écus ×2, XP ×2, Vitesse ×2/×3/×4 et Dégâts ×2/×3, en durées de **1, 5 ou 20 minutes**, avec tarifs élevés configurables. Les potions identiques s’empilent par 64. Clic droit en main principale pour activer ; une seule unité est consommée.

Les effets différents coexistent. Pour un même effet, seul le multiplicateur le plus fort est actif ; réutiliser le même multiplicateur prolonge sa durée. Une potion plus faible est refusée et conservée lorsqu’un effet plus fort est actif. L’expiration suit l’heure réelle, y compris hors ligne. Les bonus sont suspendus dans l’avant-poste, sans suspendre leur chronomètre. Le lait, peu coûteux, supprime les effets vanilla et tous ces bonus temporaires.

Écus et XP améliorent les gains de combat, pas les ventes et échanges. Les récompenses de boss de raid sont d’abord réparties selon les dégâts, puis les bonus personnels actifs des joueurs en ligne sont appliqués. Les participants hors ligne conservent leur part de base. La chance affecte les coffres de chasse et les talismans ; les multiplicateurs ne transforment pas les prix de vente en source de monnaie supplémentaire.

## Warp, sacs et protection

`/warp` affiche les zones : une zone verrouillée reste anonyme et ne téléporte jamais. Pour une zone débloquée, gauche rejoint son entrée et droite son boss. Le menu propose aussi le spawn, les raids et le boss public.

Un administrateur définit les destinations en se plaçant au bon endroit : `/warp set vache entree` ou `/warp set vache boss`. Sans destination explicite, un point d’apparition existant du type demandé peut servir de référence, avec recul de cinq blocs. Si aucun point n’existe ou que l’arrivée n’est pas sûre, la téléportation est refusée.

`/sacbloc set` en visant un bloc existant transforme son clic droit en raccourci vers les **propres sacs du joueur**. `/sacbloc remove` retire le lien, sans supprimer le bloc ni un inventaire. Ce raccourci conserve les permissions et l’interdiction des sacs partagés dans les avant-postes.

`/batonspawn mobs vache` ou `/batonspawn boss vache` donne un bâton administrateur. Un clic droit sur un bloc crée un point d’apparition persistant au-dessus. La permission est contrôlée à chaque utilisation : donner le bâton à un joueur ne lui donne pas le droit de modifier la carte. Les créatures stationnaires regardent vers le sud et leurs boss vers l’est.

Les joueurs non op ne peuvent pas casser, poser des blocs, utiliser des seaux pour modifier la carte, détruire ses décorations ou piétiner ses cultures en dehors des avant-postes. Les protections de propriété existantes continuent de s’appliquer dans les avant-postes. Les interactions autorisées en créatif avec les portes et trappes sont conservées.

## Raids privés et boss public

`/raid` ouvre le groupe. Le chef invite par le menu ou `/raid inviter <pseudo>` ; l’invité accepte via le lien ou `/raid accepter <chef>`, pendant 60 secondes. Le groupe accueille jusqu’à huit joueurs. Le chef tient un parchemin lié à une zone en main puis lance depuis le menu ou `/raid lancer`. Chaque membre doit être en ligne, hors avant-poste et avoir débloqué cette zone.

Le parchemin est consommé une fois par lancement. Les parchemins se trouvent en chassant (taux configurable), ou sont attribués par `/raid parchemin <zone> [joueur]` pour l’administration. Le numéro de la zone détermine la difficulté : PV multipliés par son carré, dégâts et récompenses de base par son numéro, avec plafond de dégâts reçus. Les réglages de base sont dans `activites/raid.yml`.

Les mondes du pool sont préparés au démarrage pour éviter une création de monde au clic de lancement. Le premier démarrage peut donc prendre davantage de temps. Chaque groupe utilise sa propre arène ; les téléportations de joueurs non membres sont refusées, même pour un op utilisant une téléportation ordinaire. Jusqu’à quatre groupes simultanés par défaut. `/raid rejoindre` permet de revenir dans son raid actif ; `/raid quitter` ou `/raid sortir` quitte le groupe. Une arène vide pendant une minute est abandonnée. La victoire ramène les joueurs et libère l’arène. Les sessions privées sont temporaires : elles ne reprennent pas après redémarrage, et un parchemin déjà utilisé n’est pas remboursé pour une partie abandonnée.

`/boss` rejoint une autre arène, publique, sans parchemin ni groupe. Un boss apparaît à chaque nouveau **milliard de créatures réellement éliminées** dans les zones ou les raids. Le compteur est durable et n’est incrémenté qu’après validation des gains ; les événements rejoués ne le gonflent pas. Le cycle actif reprend après redémarrage. Une annonce à tout le serveur propose `/boss` au déclenchement ; `/boss quitter` revient au point de départ. Les chunks de l’arène publique restent chargés pour que le boss ne disparaisse pas en l’absence de joueurs.

Les deux activités désactivent le PvP et répartissent les gains de boss selon les dégâts réellement infligés, plafonnés aux PV restants. Les récompenses en monnaies/XP sont enregistrées par le journal existant, y compris pour les contributeurs déconnectés.

## Collection de talismans

`/tallisement` (alias `/talismans`, `/talissement`) conserve les découvertes par UUID. Un objet est prévu pour chaque créature et son boss. Cinq familles générales — lame, richesse, sagesse, fortune, vitalité — possèdent chacune cinq tiers. Déblocage par défaut en zones 1, 5, 10, 15 et **19**. Le propriétaire a choisi la dernière zone actuelle pour le tier 5. L’ancien réglage provisoire de zone 40 est migré une fois vers 19 ; les autres valeurs personnalisées sont conservées.

Les talismans découverts sont livrés via `/claim`. Les talismans existants achetés ou échangés sont reconnus lors de la consultation de la collection. Une découverte nouvelle et sa livraison sont enregistrées ensemble ; la collection n’accorde pas un second exemplaire à chaque ouverture du menu.

Les bonus passifs exigent de placer les talismans dans ses sacs. Le meilleur tier d’une famille générale s’applique, sans cumul des exemplaires identiques. Le talisman de boss augmente les dégâts contre le boss associé ; les autres bonus concernent dégâts, Écus, XP, chance et PV. Les taux et valeurs restent dans `progression/talismans.yml`.

## Vérifications de cette livraison

218 tests automatisés réussis, sans échec ni test ignoré. Les essais sur Paper 1.21.4 avec ZNPCsPlus 2.0.0 couvrent les guides, les PNJ, les achats et stocks, les taxes AH, la vente à l’unité ou par pile, les potions et le lait, les raids privés et publics, les protections, les redémarrages et les derniers réglages `/antidrop` et tier 5 en zone 19. Le contrôle SQLite `quick_check` est valide.

Le seuil du boss public a été franchi depuis un compteur de test à 999 999 999 avec une élimination réelle. Le rendu ItemsAdder et DecentHolograms doit être vérifié sur le serveur qui possède ces plugins ; le serveur d’essai utilisait les icônes vanilla. Ces scénarios ne constituent pas un test de charge à grande échelle. Le [compte rendu](validation/services-raids-talismans.json) détaille les contrôles et leurs limites.

## Blocs des sacs et des améliorations

En visant un bloc à moins de cinq blocs : `/sacbloc set` ouvre les sacs personnels au clic droit ; `/upgradebloc set` ouvre `/upgrade`. Utiliser respectivement `/sacbloc remove` ou `/upgradebloc remove` pour retirer le raccourci et son hologramme. Permission : `cliker.services.admin`. Les permissions habituelles des menus restent appliquées.

Les hologrammes utilisent désormais le fournisseur commun Holographic Displays API 3, avec secours natif configurable ; voir [le guide actuel](AMELIORATIONS-UX.md). Les positions persistent après redémarrage ; les hologrammes sont recréés dans les chunks chargés, sans forcer le chargement des autres chunks. Textes et couleurs sont configurables sous `hologrammes.sacs` et `hologrammes.upgrades` dans `navigation/warps.yml` (couleurs avec `&`, saut de ligne avec `\n` dans une chaîne YAML entre guillemets doubles).

## Navigation par catégories

Le marchand de construction de l’avant-poste ouvre les catégories Tous les blocs, Bois, Pierre et minerais, Couleurs et verre, Nature, Décoration et mécanismes. Chaque liste possède sa pagination et un bouton Catégories ; gauche achète un bloc, droite une pile.

`/batonspawn` sans argument ouvre la sélection des créatures. Gauche donne le bâton de la créature, droite celui de son boss ; un clic sur un bloc définit ensuite le point d’apparition. La permission administrateur reste obligatoire.

`/tallisement` présente uniquement les talismans débloqués. Le bouton Non débloqués ouvre les catégories Créatures, Boss et les cinq familles générales. Chaque catégorie masque les talismans déjà découverts et permet de revenir aux débloqués. Les noms des créatures non découvertes restent cachés.

## Tri AH, catégories shop et visibilité

La case 46 de l’AH accueille la page précédente ; la case 45 (indices à partir de zéro) ouvre huit tris : prix, ancienneté, nom et rareté dans les deux sens. Le tri s’applique à toutes les annonces avant pagination et reste actif au changement de page ou à l’actualisation. La rareté utilise les données des équipements/essences ClickHunter, sinon la rareté Minecraft ; renommer un objet ne change pas son rang.

Le shop propose Épées, Armures, Potions, Lait et Tous. Tous les visuels de ses menus et du marché noir restent vanilla. Les offres du marché noir occupent les cases 12, 14, 16, 30, 32 et 34 (numérotation à partir de 1).

Les PNJ utilisent par défaut `pnj.regard: CLOSEST_PLAYER` de ZNPCsPlus ; `FIXED` reste configurable. `/settings` propose Masquer les autres joueurs. Ce choix personnel persiste dans les données du joueur, s’applique aux nouveaux arrivants et ne masque ni les PNJ ni les mobs. Il agit sur l’affichage, sans désactiver les collisions de combat ou protéger des dégâts.

Les fonctionnalités plus récentes (gestion des sacs, favoris AH, visites, session, diagnostic, potions et migration des hologrammes) sont décrites dans [le guide UX actuel](AMELIORATIONS-UX.md). Les vérifications datées ci-dessus correspondent à leur livraison historique.
