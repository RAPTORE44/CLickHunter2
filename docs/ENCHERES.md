# Enchères légendaires et salle commune

Module intégré à ClikerV2, indépendant des annonces AH et de leurs taxes. Paper 1.21.4, Java 21 ; aucune nouvelle dépendance. Le présentoir utilise ItemDisplay + Interaction natifs ; son titre utilise le service commun d’hologrammes.

## Préparation

Construire une pièce avec sol sûr. À son point d’arrivée : `/enchere setsalle`. Au-dessus du socle de présentation : `/enchere setpresentoir`. La position exacte et l’orientation sont sauvegardées dans `activites/encheres.yml`. Sans salle définie, aucun événement n’est lancé et aucune entrée n’est prélevée. Sans présentoir défini, celui-ci apparaît trois blocs au sud du centre, à 1,5 bloc de hauteur.

`salle-rayon` définit la demi-largeur du périmètre de présence autour du centre (16 blocs par défaut, hauteur ±12). Prévoir une salle cohérente avec cette valeur. Un joueur sorti du périmètre est désinscrit au contrôle suivant. Le présentoir ne produit aucun objet ramassable, est protégé et nettoyé au redémarrage. Les participants présents sont protégés des dégâts dans la salle.

## Joueurs

Tous les trois heures, trois minutes d’inscriptions sont annoncées plusieurs fois. Le lien exécute `/enchere rejoindre`. L’entrée coûte **10 000K = 10 000 000 Écus**, débitée atomiquement une seule fois par UUID et événement. Les frais sont annoncés avant inscription et sont non remboursables. Réouvrir une inscription déjà payée pendant la fenêtre ne refacture pas l’entrée. Sans solde suffisant, aucune inscription ni téléportation n’est validée.

Le joueur rejoint physiquement les autres participants. Pendant l’attente, il voit le premier objet flotter, sans menu présentant les trois lots futurs. Le temps avant début s’affiche dans la hotbar. Le clic droit sur le présentoir ouvre uniquement le lot courant lorsque sa vente est active.

Au début du lot, le menu s’ouvre automatiquement aux participants dans la salle. Le double coffre reste épuré : montants et flèche suivante. Le parcours descend dans la colonne gauche, avance de deux cases, remonte, avance de deux cases, etc. Les raccords font partie du chemin : **29 montants par page**. Les prix strictement croissants commencent à 1K et atteignent le plafond configurable de 1 000 000B (1Qa).

Un clic mise le montant exact affiché, après contrôle du solde SQL. Les montants dépassés deviennent du fond inactif, et la tête du meneur occupe sa mise. Atteindre le dernier montant d’une page fait passer les menus concernés à la suivante. Chaque changement de page ou de lot ouvre un nouveau conteneur : un ancien paquet de clic ne peut pas acheter un autre montant. Les touches numériques, doubles clics et glissements ne déplacent aucun objet.

Chaque offre acceptée remet le chronomètre à **30 secondes**, affiché uniquement en hotbar. Trois lots sont vendus successivement. Il est permis de regarder un lot, fermer le menu ou ne rien miser ; le présentoir permet de rouvrir. Un lot sans offre solvable reste invendu, sans prélèvement du prix d’un lot.

`/enchere quit` ou `/enchere quitter` désinscrit, ferme le menu et renvoie au spawn. Aucune nouvelle offre n’est ensuite autorisée. **Les offres déjà déposées restent fermes**, même après départ ou déconnexion ; elles ne sont pas annulées pour contourner une surenchère. Les frais d’entrée ne sont pas remboursés. En fin de vente, les joueurs encore présents retournent au spawn.

## Paiement, objets et reprise

Les fonds des mises ne sont pas réservés. Le solde est contrôlé à l’offre et à l’attribution. Si le meilleur enchérisseur est devenu insolvable, sa dernière offre est écartée au profit de la dernière offre la plus haute encore solvable d’un autre joueur ; un même joueur ne revient pas automatiquement à son ancien prix plus bas.

Le débit du lot, l’enregistrement du gagnant, le passage au lot suivant et l’objet livré sont validés dans une transaction SQL. L’objet est remis directement si possible, sinon `/claim` pour inventaire plein ou gagnant hors ligne. Une répétition de règlement ne doit ni payer ni donner deux fois.

Les événements, lots, offres, inscriptions et reçus d’entrée sont conservés dans SQLite. Après un arrêt normal, un lot expiré est réglé une seule fois puis la vente poursuit son cycle. Les joueurs reconnectés dans la salle récupèrent leur participation s’ils sont encore inscrits, sans repayer ; sinon `/enchere rejoindre` rouvre leur participation existante pendant l’événement. Les demandes d’interface tardives d’une ancienne connexion sont ignorées.

Si une protection du serveur refuse une téléportation après la validation SQL de l’inscription, l’inscription payée reste conservée et peut être rejointe sans nouveaux frais. Préparer et tester le point d’arrivée avant l’ouverture publique.

## Administration et configuration

- `/enchere setsalle` : arrivée et centre de présence.
- `/enchere setpresentoir` : position flottante du lot.
- `/enchere forcer` : ouvrir immédiatement les inscriptions.
- `/enchere demarrer` : terminer les inscriptions et commencer avec les participants actuels.
- Permission : `cliker.enchere.admin` ; joueurs : `cliker.enchere.use` par défaut.
- Alias racine : `/encheres`.

`activites/encheres.yml` contient les délais, frais, messages, prix, positions et catalogue. Par défaut, les trois lots sont des équipements légendaires distincts tirés parmi les armes/armures existantes des zones Blaze, Wither Squelette et Ghast. Le choix exact d’objets personnalisés n’a pas été fourni. Les équipements conservent leurs attributs et métadonnées ; aucune récompense de test ni pièce générique ne remplace leur contenu.

## Vérification

Les tests automatisés couvrent frais atomiques uniques, fonds insuffisants, offres concurrentes, délai relancé, insolvabilité, déconnexion/fermeture, anciens clics de page/lot, paiement et livraison uniques, reprise SQL et parcours vertical contigu de 29 montants. Les preuves locales distinguent l’ancienne interface validée auparavant de cette évolution avec salle ; les essais accélérés ne prétendent pas avoir attendu trois heures réelles.
