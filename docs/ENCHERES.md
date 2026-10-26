# Enchères légendaires événementielles

Module de ClikerV2, Paper 1.21.4 et Java 21. Il est distinct de `/ah` : le serveur propose trois lots, sans frais de publication ni taxe AH. Le débit du prix gagnant retire les Écus de l’économie, sans vendeur joueur à créditer.

## Commandes et permissions

| Commande | Fonction | Permission |
| --- | --- | --- |
| `/enchere` | Consulter l’état et le délai actuel. | `cliker.enchere.use`, tous par défaut |
| `/enchere rejoindre` | S’inscrire pendant les trois minutes puis ouvrir le coffre. Un inscrit peut le rouvrir une fois la vente commencée. | `cliker.enchere.use` |
| `/enchere quitter` | Fermer le menu ; se désinscrire si la vente n’a pas commencé. Les offres déjà déposées restent fermes. | `cliker.enchere.use` |
| `/enchere forcer` | Ouvrir immédiatement une session d’inscription de trois minutes, sans doubler un événement existant. | `cliker.enchere.admin`, op par défaut |
| `/enchere demarrer` | Terminer les inscriptions et démarrer avec les inscrits actuels. S’il n’y en a aucun, la session est annulée sans débit. | `cliker.enchere.admin` |

Alias `/encheres`. Les sous-commandes proposées tiennent compte de la permission de gestion. Depuis un avant-poste, revenir au `/spawn` avant de participer pour conserver l’isolation des inventaires.

## Déroulement

1. Toutes les trois heures, trois minutes d’inscription s’ouvrent. L’annonce et les rappels à 120, 60, 30 et 10 secondes indiquent `/enchere rejoindre` et sont cliquables.
2. Les trois lots sont visibles dans un double coffre pendant l’inscription. Les nouveaux inscrits sont refusés après sa fermeture ; les inscriptions sont sauvegardées par UUID.
3. Chaque lot commence avec 30 secondes et aucune offre. Cliquer sur une somme dépose une offre ferme si le solde suffit, relance les 30 secondes et place la tête du meneur sur le montant. Tous les montants inférieurs ou égaux sont verrouillés. Un joueur peut dépasser sa propre offre.
4. À zéro, la plus haute offre solvable est débitée et son lot est placé dans le journal de livraison **dans la même transaction**. Puis le lot suivant commence, jusqu’au troisième. Sans offre solvable, le lot reste invendu et personne n’est débité.

Le plafond par défaut est **1 000 000B = 1 000 000 000 000 000 Écus = 1Qa**, selon la notation existante du plugin. Les prix commencent à 1 000 et augmentent de 20 %, arrondis vers le haut, jusqu’au plafond exact. Cinq lignes de neuf cases forment un serpent continu, de gauche à droite puis de droite à gauche. Les flèches parcourent les pages suivantes ; les prix antérieurs restent verrouillés sur toutes les pages. Le dernier montant s’arrête au plafond, sans dépassement numérique.

Le bas du coffre affiche le temps restant, le lot courant, les règles, le nombre d’inscrits, le meilleur enchérisseur, le solde consulté et les commandes de navigation. Le solde peut être actualisé par clic sur son bouton. Le serveur vérifie toujours le solde réel à l’offre et à la clôture. Les lots et têtes gardent leur apparence réelle ; les boutons de navigation utilisent les icônes habituelles du plugin.

## Engagement financier et départ

**Aucun débit ni réservation n’est effectué lors d’un clic d’enchère.** L’offre reste ferme jusqu’à son dépassement ou à l’attribution, même si le joueur ferme le menu, quitte avec la commande ou se déconnecte. Cette règle est affichée à l’inscription, dans les prix et dans le bouton de sortie.

Si le meilleur enchérisseur a dépensé son argent ailleurs, sa dernière offre est écartée : on examine la dernière offre de chaque autre enchérisseur, dans l’ordre décroissant, jusqu’à trouver un solde suffisant. Un joueur insolvable ne peut pas revenir automatiquement à sa propre ancienne offre moins chère. Il n’y a ni solde négatif, ni lot gratuit, ni débit d’un perdant. Le gagnant peut être hors ligne.

L’objet rejoint directement l’inventaire si de la place existe ; le surplus, l’inventaire plein et les gains hors ligne passent par `/claim`. Les mêmes sources idempotentes empêchent une double livraison après répétition d’une clôture. Les offres, délais, inscriptions, lots et résultats sont sauvegardés en SQLite. Après redémarrage, un lot expiré est traité une seule fois et la session continue. Un rechargement global Paper n’est pas le mode d’installation recommandé : utiliser un arrêt/redémarrage normal.

## Catalogue et configuration

Fichier SFTP : `plugins/ClikerV2/activites/encheres.yml`.

- `intervalle-secondes: 10800`, `inscriptions-secondes: 180`, `offre-secondes: 30` et `rappels-secondes` règlent la cadence.
- `prix.depart`, `prix.augmentation-pourcent` et `prix.maximum` définissent l’échelle commune des prix ; une configuration invalide est refusée.
- `lots.zones` et `lots.types` définissent les équipements candidats. Par défaut : épées et armures des zones Blaze, Wither Squelette et Ghast, réellement légendaires selon `TierGear`. Trois équipements sont tirés à chaque événement. Les statistiques, matériaux et couleurs d’origine sont conservés ; aucun équipement mythique n’est réétiqueté légendaire.
- `messages` contient les annonces françaises. Les rappels et démarrages sont globaux ; les messages de surenchère sont limités aux joueurs ayant le menu ouvert.
- `prochaine-inscription` est conservée automatiquement pour ne pas repousser le délai à chaque redémarrage. Une ouverture forcée recale cette échéance. Une session encore en cours ne peut jamais être doublée.

Les prix, achats et objets des autres modules sont inchangés. Quatre nouvelles tables et un index sont ajoutés sans conversion des anciennes données. Sauvegarder ensemble le dossier du plugin et les mondes/playerdata avant installation.

## Validation

Tests SQLite : inscription, désinscription, offre hors délai, solde insuffisant, concurrence sur le même prix, absence de débit avant clôture, relance du délai, gagnant hors ligne, insolvabilité, trois lots successifs, reprise d’état, transaction annulée sur erreur, prix extrêmes et attribution unique. Tests de menu : départ pendant inscription asynchrone et protection des déplacements d’objets.

Les résultats et les durées réellement utilisées sur Paper sont consignés dans `validation/enchere-pinata.json`. Les essais accélérés ne sont pas présentés comme une attente réelle de trois heures. La durée réelle de trois minutes de la piñata a fait l’objet d’un contrôle séparé. Aucun test de charge représentatif ni rendu ItemsAdder de production n’est revendiqué.


Validation livrée : **280 tests automatisés, zéro échec, zéro erreur, zéro test ignoré**. Sept contrôles natifs accélérés ont été rejoués sur le JAR final pour couvrir la vente complète ; six autres contrôles vérifient la reprise, avec un compteur réel mesuré à 30932 ms. La reprise d’une offre après arrêt normal a été exercée, y compris le paiement hors ligne et les reconnexions sans duplication.

Les skins déjà disponibles sont copiés et mis en cache. Pour un joueur réel absent, une seule résolution asynchrone est tentée par événement ; une texture indisponible conserve une tête de secours avec le nom du meneur. Les UUID synthétiques hors ligne ne sont pas recherchés dans l’annuaire Mojang : aucune recherche répétée pour ces profils n’a été émise pendant le contrôle final. Le rendu de vrais skins en ligne reste à vérifier sur le serveur public.


Les clics différés sont liés à l’événement, au lot, à la phase et à la page affichés au moment du clic. Un changement de page ou de lot utilise un nouveau conteneur : un ancien paquet client ne peut pas être réinterprété comme une offre sur un montant ou un objet différent. Les trois scénarios déterministes (aperçu d’inscription, page suivante et lot suivant) échouaient avant la correction et passent dans la suite finale ; un quatrième test vérifie les clics d’un ancien conteneur. Les rafraîchissements du compteur et des skins conservent le conteneur tant que le contexte financier reste identique.
