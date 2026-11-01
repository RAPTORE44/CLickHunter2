# Version 1.0.5 — anciennes demandes et coups critiques

Paper 1.21.4 / Java 21. Remplacer le JAR et redémarrer en conservant SQLite et les playerdata ensemble. Vérifier `/version ClikerV2` : 1.0.5.

Les commandes et réglages sont détaillés au début du README. Aucun nouveau plugin requis au-delà de ZNPCsPlus 2.0.0 déjà utilisé pour les PNJ et le filtre des paquets.

## Règles de sécurité

Le courrier consomme la pièce jointe avec le journal de sortie existant ; publication et consommation du reçu sont dans la même transaction. Un refus (blocage, quota ou panne) rend l'objet par le mécanisme de reprise existant. La récupération vérifie le propriétaire et transfère une seule fois vers le journal de livraison. Impossible de supprimer une lettre contenant encore une pièce jointe. Les brouillons non envoyés sont temporaires et disparaissent à la déconnexion ; les lettres envoyées sont persistantes.

La rédaction commence dans le menu et utilise le chat privé par paragraphes ; elle ne force pas l'éditeur natif d'un livre depuis un coffre. Les livres Minecraft déjà rédigés peuvent aussi être envoyés en main. Liens refusés par défaut, liste de mots interdits configurable, délai d'envoi et quota par destinataire.

Le profil AFK conserve inventaire, armure, main secondaire, XP et mode de jeu dans le playerdata, avec le marqueur de restauration. Les commandes hors AFK et les transferts de ressources sont bloqués ; les livraisons attendent le retour au clicker. Les points sont enregistrés par durée cumulée de session pour éviter un nouveau reçu SQL à chaque contrôle ; reprises et sauvegardes désordonnées ne doublent pas le crédit. Jusqu'à vingt secondes non encore sauvegardées peuvent être perdues lors d'un arrêt brutal.

Le filtre de chat repose sur l'API PacketEvents embarquée dans ZNPCsPlus 2.0.0. Si cette intégration n'est pas disponible, l'entrée AFK est refusée. Les informations AFK passent par la barre d'action et les menus.

Le raid public accueille le chef et trois renforts ; ceux sans contribution au boss ne reçoivent pas le lot final. Les montants au tiers sont arrondis à l'entier inférieur dans l'unité interne de chaque monnaie. Le parchemin de victoire et les gains sont enregistrés dans une transaction ; le boss public serveur ne donne pas ces parchemins.

## Validation

**462 tests réussis**, zéro échec, erreur ou test ignoré.

Les tests automatisés couvrent : propriété et récupération unique des pièces jointes, refus d'expéditeur et quota, annulation transactionnelle d'envoi, points AFK idempotents, achats sans double débit et sans perte sur panne, trois renforts maximum et répartition au tiers, JSON des succès et dégâts critiques du Dresseur en chasse et contre un boss.

Premiers essais sur la copie Paper 1.21.4 : pièce jointe rendue directement, rédaction privée, succès reçu par le client et envoi bloqué avec restitution intégrale. Le premier chargement AFK a révélé une attente de génération sur le thread serveur ; la préparation des chunks a ensuite été déplacée vers les futures Paper avant tout changement d'inventaire. Les derniers résultats de validation complètent ce compte rendu.

Le serveur de production n'est pas accessible ici. Les fondations différées et le reste des lignes jaunes ne sont pas déclarés terminés par cette livraison.

Résultat final Paper : entrée AFK avec inventaire vide, isolation du chat système et joueur, achat différé puis caisse livrée et trois diamants restaurés à la sortie. Plus de blocage de génération constaté dans ce parcours. Le raccourci étoile est suspendu pendant l’AFK. Voir [le relevé de validation](validation/ancienne-demandes-1.0.5.json).
