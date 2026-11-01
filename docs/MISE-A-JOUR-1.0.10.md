# Version 1.0.10 — récompenses et salle des enchères

Paper 1.21.4 / Java 21. Remplacer le JAR et redémarrer ; conserver SQLite et les playerdata ensemble. Vérifier `/version ClikerV2` : 1.0.10.

## Récompenses

Les essences ordinaires ne tombent plus au sol quand l’inventaire est plein. Les nouveaux œufs gagnés sur les mobs possèdent un indicateur de politique dans l’enveloppe YAML de livraison, pas dans les métadonnées de l’objet : empilement et statistiques inchangés. Les anciennes enveloppes sont toujours lisibles et ne sont pas purgées. En cas de livraison fractionnée, la politique suit les deux fractions. La suppression n’affecte que les entrées non réservées, avec vérification du propriétaire ; un objet déjà attribué avec reçu ne peut pas être supprimé par ce mécanisme. Les autres objets gardent leur restitution habituelle. Les achats déjà journalisés ne sont pas réécrits.

Le bouton Tout récupérer ferme le menu pour éviter sa reconstruction à chaque objet, puis réutilise le journal existant. La recherche passe aux pages suivantes si les premiers objets ne tiennent pas. Le processus s’arrête quand tout est récupéré ou quand rien d’autre ne tient. Déconnexion, isolation d’inventaire et arrêt nettoient l’état de recherche ; les objets restent en base avec leurs protections.

## Raid

Clic droit en main principale avec un parchemin reconnu : ouverture du menu via la garde anti-réouvertures. Aucun parchemin n’est consommé à l’ouverture. Une livraison en cours bloque le lancement afin de protéger l’inventaire.

## Enchères

`/enchere setspawn` et `/enchere setsalle` définissent le même point persistant. La protection est un carré horizontal de 200 blocs de côté, centré sur ce point, sur toute la hauteur du monde. Les limites à ±100 sont incluses. Le rayon est configurable via `protection-rayon`. La protection est passive : elle ne charge pas de chunks et ne lance pas de tâche périodique. Elle couvre joueurs inscrits ou non et staff, sans supprimer les anciens objets ou mobs déjà présents.

Les dégâts sont annulés avant le calcul des PV virtuels des mobs. Mobs nouveaux, dégâts, faim, casse/pose, jets d’objets, apparition d’items, destructions par explosion, feu, déplacements de pistons et arrivées de fluides sont contrôlés. Les commandes administrateur ou outils qui modifient directement le monde sans événement Bukkit ne sont pas une frontière de sécurité contre l’administrateur du serveur.

La bossbar utilise le tick d’enchère existant, sans nouveau scheduler ; liste de spectateurs synchronisée et purgée à déconnexion/arrêt. Elle est visible dans le rayon de protection pendant un événement, même sans inscription. La salle de participation conserve son rayon propre `salle-rayon` ; cela ne l’élargit pas implicitement à toute la zone protégée.

Le prix par défaut devient zéro ; l’ancien standard de 10 000 000 est migré une fois. Un prix personnalisé reste inchangé et les frais déjà payés ne sont pas remboursés rétroactivement. Paramètre : `entree-ecus`.

## Validation

**483 tests réussis**, zéro échec/erreur/ignoré. [Résultats Paper](validation/enchere-claim-1.0.10.json).

Tests unitaires et SQL : œufs en inventaire plein, préservation des anciennes récompenses, récupération partielle, recherche au-delà de 45 entrées bloquées, parchemin non consommé et protection à toutes les hauteurs, y compris pour OP.

Essais Paper 1.21.4 avec deux clients : bouton Tout récupérer après libération d’une case ; ouverture du raid au clic droit dans l’air ; entrée à zéro et bossbar ; dégâts annulés pour un non OP, apparition d’un mob refusée, jet de pile annulé avec objets conservés. Les remplacements natifs d’icônes sont utilisés dans cette copie ; le rendu des JAR ItemsAdder/HD de production reste à confirmer sur le serveur réel.
