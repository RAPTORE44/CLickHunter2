# Menu et talismans permanents — version 1.0.15

## Utiliser un talisman

1. Tenir le talisman dans la **main principale**.
2. Faire un **clic droit**, dans l’air ou sur un bloc.
3. Un seul exemplaire est consommé. Après validation de l’ajout, son bonus reste enregistré pour l’UUID du joueur.
4. Ouvrir `/tallisement`, `/talismans` ou `/talissement` pour consulter la collection et les bonus.

Il n’existe **aucune action joueur pour retirer ou déséquiper un talisman enregistré**. Les objets affichés dans le menu sont des icônes de consultation : ils ne peuvent pas être pris. L’objet consommé n’est plus dans l’inventaire ou dans un sac ; son enregistrement persiste après déconnexion, mort ou redémarrage.

Un exemplaire d’un identifiant déjà enregistré n’est pas consommé et ne double pas son bonus. Les tiers différents peuvent tous être collectionnés, mais seul le meilleur tier d’une même famille générale ou AFK s’applique. Ajouter un tier inférieur ne supprime jamais le tier supérieur.

Les talismans de créature, de boss, les cinq familles générales sur cinq tiers, les quatre tiers AFK et le talisman du Trésor sont pris en charge. Les valeurs de leurs bonus, les chances de découverte et les monnaies ne sont pas modifiées. Les bonus restent suspendus dans l’avant-poste, comme auparavant ; l’enregistrement nécessite de revenir à l’inventaire clicker.

## Collection et découvertes

La page principale garde les talismans déjà découverts et place les enregistrements permanents en premier. Les icônes indiquent s’ils sont actifs, si un tier supérieur est prioritaire, ou si l’objet doit encore être enregistré par clic droit. Une découverte historique ne donne pas gratuitement le bonus : l’objet physique doit être réellement consommé.

La grille possède 28 cases centrales espacées, avec pagination. Le bouton des bonus affiche les effets effectivement utilisés par le personnage. Les talismans non découverts sont regroupés par catégorie ; les créatures et boss inconnus gardent leur mystère. Les menus restent en lecture seule.

## Anciens objets et sacs

Les anciens talismans physiques restent reconnus par leurs tags ; aucun objet de sac n’est supprimé automatiquement. Leurs effets hérités dans les sacs sont conservés pour ne pas faire disparaître des bonus à la mise à jour.

Pour rendre un ancien exemplaire permanent : le reprendre dans sa main, fermer le sac, puis faire un clic droit. Une fois cet exemplaire enregistré, vider ou déplacer le contenu des sacs ne peut pas retirer son enregistrement. Le même talisman enregistré et encore présent sous forme d’un autre exemplaire dans un sac ne s’ajoutent pas deux fois au cache permanent ; le calcul conserve la compatibilité avec les effets hérités existants.

Les objets non encore enregistrés et les doublons physiques restent des objets ordinaires transportables. Cette compatibilité ne crée pas une option permettant de retirer un talisman de la collection permanente. L’ancien texte d’un objet déjà stocké peut encore mentionner le sac ; l’aide du nouveau menu explique son enregistrement par clic droit.

## Nouveau menu principal

`/menu` reste un double coffre de 54 cases. Les raccourcis sont regroupés par lignes :

- Navigation et destinations, avant-poste et résumé de session.
- Progression : guide, statistiques, bestiaire, améliorations, prestige, talismans, skins.
- Commerce et rangement : vente, AH, ATM, sacs, claim, courrier et classements.
- Activités : daily, contrats, raid et AFK.
- Pied du menu : guide, fermeture et wiki.

Des repères colorés identifient les lignes. Les noms et descriptions restent droits, sans italique. Les icônes ItemsAdder déjà installées restent utilisées par le système existant, avec remplacement vanilla.

La disposition passe de la version 2 à la version 3 dans `gui/menu.yml`. Une copie de l’ancienne configuration est conservée avant migration. Les raccourcis standards sont repositionnés ; les commandes, permissions et textes personnalisés sont conservés. Un bouton supplémentaire en conflit avec un emplacement standard est déplacé vers une case libre. Les anciennes lignes standard de description sur les talismans/sacs sont actualisées.

## Commandes et installation

Aucune commande ni permission joueur supplémentaire. `/tallisement` et ses alias gardent leur syntaxe, le raccourci du menu continue à fonctionner. `/talisman treasure <joueur>` reste une attribution staff avec `cliker.mob.admin` ; le joueur doit ensuite enregistrer l’objet reçu.

Arrêter le serveur, sauvegarder le dossier `plugins/ClikerV2` et les playerdata, remplacer le JAR, puis redémarrer sous Paper 1.21.4 / Java 21. Les nouvelles tables sont créées sans effacer les anciennes découvertes ou les sacs.

## Persistance et validation

Le retrait d’un exemplaire utilise le journal `OutgoingItems` déjà employé pour les transactions d’objets. L’enregistrement permanent, la découverte et la validation du reçu sont dans la même transaction SQL. Si la sauvegarde joueur ou le commit échoue, aucun bonus permanent n’est accordé ; le mécanisme existant conserve ou restitue l’objet, éventuellement via `/claim` lors de la reprise. Aucun appel SQL n’est ajouté aux coups portés : les bonus sont recalculés en cache au chargement et aux changements de collection/sacs.

**532 tests fonctionnels réussis**, sans échec, erreur ni test ignoré. Six nouveaux tests vérifient le clic droit non OP, la double interaction et les doublons, les familles générales/boss/Trésor/AFK, la reconnexion et l’indépendance du contenu des sacs, les tags invalides et la main secondaire, l’échec de sauvegarde et les icônes non récupérables. Les tests du menu principal vérifient encore l’isolation de l’avant-poste, les permissions et le clic sans répétition de commande, aux nouveaux emplacements.

Le rendu des textures ItemsAdder du serveur de production n’est pas revendiqué sans son installation locale ; les essais locaux utilisent le remplacement vanilla.

Essai local Paper 1.21.4 : clic droit non OP, consommation unique, doublon préservé, collection non récupérable, disposition du menu et bonus conservé après reconnexion validés. [Compte rendu](validation/talismans-1.0.15.json).
