# Avant-postes : inventaires et boutique

Paper 1.21.4, Java 21. Le marchand nécessite **ZNPCsPlus 2.0.0**. Les boutons utilisent les `icons:*` déjà installés dans ItemsAdder, avec remplacement vanilla si ItemsAdder est absent.

## Utilisation

- `/is aller` : créer ou rejoindre son terrain.
- Clic droit sur le marchand : ouvrir les pages de blocs. Gauche achète un bloc ; droite achète une pile vanilla (une unité pour les blocs non empilables).
- `/is blocs` : ouvrir la même boutique depuis le monde des avant-postes.
- `/is pnj deplacer` : positionner son propre marchand à sa position et orientation, dans les limites de son terrain. La position est sauvegardée.
- `/spawn` ou `/is sortir` : retrouver son équipement clicker. Le délai habituel de `/spawn` reste actif.

Les alias `/avantposte` et `/avantpostes` fonctionnent aussi. Permission joueur : `cliker.avantposte`.
La liste complète classée par catégorie reste dans le [README](../README.md).

## Inventaires

Deux profils contiennent chacun les 41 emplacements (stockage, armure, seconde main) et l’XP Minecraft. Les monnaies restent communes : la boutique prélève des Écus. Le changement ne repose pas sur la destination supposée d’une commande : il suit le changement de monde réellement effectué, y compris les téléportations externes et réapparitions.

Le fichier vanilla `playerdata` conserve ensemble le profil actif, son inventaire et l’archive de l’autre profil. Une sauvegarde est demandée après chaque permutation. Une archive invalide ou une exception de sauvegarde bloque la session, conserve l’inventaire initial et produit une erreur explicite dans les journaux. Les pannes matérielles et modifications directes du playerdata par d’autres plugins nécessitent toujours des sauvegardes du serveur.

Lors de la première entrée après cette mise à jour, l’équipement existant est conservé comme équipement clicker ; le profil de construction commence vide. Au retour suivant, les deux profils sont retrouvés, y compris après reconnexion et redémarrage. L’étoile de menu est assurée dans chaque profil lorsque la place est disponible.

Les commandes d’échange et de vente, les sacs partagés, le coffre de l’End et les menus virtuels externes sont bloqués dans les avant-postes. Les téléportations et portails des objets et véhicules ne permettent pas de les exporter. Les commandes autorisées sont une liste explicite dans `WorldInventories` ; une commande externe nouvellement installée n’est donc pas autorisée par défaut. Le créatif autorise les interactions de construction mais conserve cette séparation d’inventaires. Les récompenses d’objets clicker et les régularisations d’XP/prestige attendent le retour au monde clicker.

## Prix et achats

Ajouter ou modifier dans `plugins/ClikerV2/avantpostes.yml`, puis redémarrer normalement :

```yaml
boutique-blocs:
  skin: Nati144378
  prix-defaut: 10
  prix:
    STONE: 10
    DIAMOND_BLOCK: 5000
```

Les montants sont des Écus par bloc. Une valeur non positive ou dépassant 1 000 milliards revient au prix de sécurité de 10 Écus. Tous les matériaux Paper de type bloc possédant une forme d’objet sont listés, triés par identifiant : les états techniques sans objet ne sont pas achetables.

Le débit et la livraison en attente sont créés dans une seule transaction SQLite. Fonds insuffisants ou échec d’insertion : aucun débit validé. Une livraison attend une capacité suffisante dans l’inventaire de construction ; elle reste en attente si le joueur se déconnecte ou quitte ce monde. Elle est distincte du `/claim` clicker. Un reçu persistant enregistré avec l’inventaire évite de redonner les mêmes blocs si l’acquittement SQL doit être repris. Les PNJ des terrains non visités sont déchargés puis recréés à la prochaine visite, sans doublon.

## Autres changements de cette livraison

- BuyZone : retour à 15 blocs au nord du centre du portail, regard vers le sud. Prévoir un sol sûr ; les vérifications de sécurité du retour restent actives.
- Créatif : portes, trappes et autres interactions de construction autorisées.
- Mobs clicker et raid silencieux ; un coup réussi joue `BLOCK_NOTE_BLOCK_HAT`. Les dégâts continuent de monter d’un bloc puis disparaissent.
- `/upgrade` : gauche achète un niveau ; droite ouvre X1, X5, X10, X50 et maximum abordable. Chaque coût cumule les niveaux réellement achetés. Les boutons impossibles sont bloqués ; les quantités fixes ne sont jamais achetées partiellement. La transaction vérifie à nouveau le solde et le plafond de 100 niveaux.

## Validation de la livraison

201 tests automatisés passent, sans échec ni test ignoré. Ils couvrent notamment les profils d’inventaire et d’XP, l’archive corrompue sans écrasement, l’absence d’export par téléportation, le report des récompenses clicker, l’achat atomique de blocs (fonds insuffisants, concurrence et échec d’insertion), les quantités exactes et le plafond des améliorations, le retour BuyZone et les interactions en créatif.

Un serveur isolé Paper 1.21.4 avec Java 21 et ZNPCsPlus 2.0.0 a servi à vérifier les clics des menus, l’achat X5, les boutons bloqués, le marchand et son déplacement, la livraison d’un bloc, la restauration réelle d’une épée, d’un casque, d’un bouclier et de l’XP, puis la conservation des profils et du marchand après redémarrage. Le rendu ItemsAdder utilise le même adaptateur déjà présent ; ItemsAdder n’était pas installé dans ce serveur de test, donc le parcours natif utilise les icônes vanilla de remplacement.
