# Livraison 1.0.6

Paper 1.21.4 / Java 21. Remplacer le JAR puis redémarrer en conservant SQLite et les playerdata. Vérifier `/version ClikerV2` : 1.0.6. Nouveaux réglages des skins : `cosmetiques/skins.yml`. Le README décrit toutes les commandes par catégorie.

## Vérifications

**465 tests réussis**, aucun échec, erreur ou test ignoré. [Résultats des parcours Paper](validation/menus-1.0.6.json). Le parcours du parchemin a confirmé un modèle de bloc appliqué et une découverte enregistrée, avec matériau épée conservé.

Les essais sur une copie Paper avec deux clients couvrent les statistiques de la cible, les catégories de skins, la simulation d'enchère sans déplacement, la création du mannequin, l'accès au calendrier et le raccourci skins du menu. Les tests automatisés couvrent les 19 équipements en OP/non OP, le refus de dégâts sans épée après les comparaisons d’équipement, le refus de dégâts sans épée, l'arrêt des recherches de cible sans arme, la portée d'un bloc et les récompenses Daily fixes. Un test SQL vérifie le solde en fin de journée malgré les dépenses ultérieures.

## Limites à connaître

Le test d'enchère simule l'interface et les mises : aucun argent, lot ou autre enchérisseur n'est engagé. Les anciens niveaux perdus (prestige ou réduction) n'ont pas été enregistrés dans l'historique, et ne peuvent pas être reconstruits fidèlement : la vue des niveaux reste celle des niveaux gagnés. Les autres classements datés utilisent les variations nettes pour reconstruire la situation en fin de période.

Le surplus d'essence ordinaire est au sol et réservé au propriétaire, soumis à la disparition normale des objets de Minecraft ; ce choix respecte l'absence de /claim pour ces ressources. Les récompenses rares et celles des boss gardent le journal durable. Les icônes ItemsAdder et Holographic Displays nécessitent les JAR compatibles de production ; les essais utilisent les remplacements natifs.

Aucun profil de charge du serveur de production n'est accessible ici. Le catalogue de construction couvre déjà tous les blocs disponibles dans Paper 1.21.4 : aucun faux ajout de matériaux provenant d'une autre version n'est annoncé.
