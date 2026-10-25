# Consignes du dépôt

## README et commandes — demande permanente du propriétaire

À chaque modification du plugin, vérifier et maintenir dans `README.md` la liste complète des commandes classées par catégorie. Mettre à jour les syntaxes, sous-commandes, alias, permissions administrateur et descriptions dès qu'un comportement change. Comparer cette liste à `src/main/resources/plugin.yml` et aux gestionnaires des commandes ; ne pas documenter de commandes inexistantes. Si la modification ne change aucune commande, vérifier que la liste reste exacte sans ajouter de faux changement documentaire.

Conserver cette consigne pour les prochaines interventions. Documenter aussi les interactions sans commande lorsqu'elles remplacent une commande ou expliquent l'utilisation d'une fonctionnalité.

## Périmètre confirmé du classeur d’idées

Le propriétaire a confirmé de conserver les demandes antérieures en cas de contradiction : dégâts qui montent d’un bloc puis disparaissent, piles de 64 et compression 64 → 1, autocompresseur consommé à l’activation. Appliquer les autres lignes jaunes uniquement aux modules existants. Laisser en attente les fondations absentes (pets, Ordres, instances de boss, réseau multi-serveurs). Les lignes non jaunes restent des propositions à valider explicitement. Suivre les livraisons réelles dans `docs/SELECTION-JAUNE.md` sans présenter les 141 idées comme terminées.

## Icônes des menus

Les textures et items `icons:*` fournis sont déjà installés dans ItemsAdder sur le serveur. Utiliser directement ces items via l’API ItemsAdder, sans demander un nouveau pack ni générer des modèles `clickhunter:*`. La référence est la liste des items réellement déclarés dans les YAML du ZIP, et non toutes les textures présentes.

## PNJ

Utiliser ZNPCsPlus 2.0.0 pour les PNJ Daily, Discord, Contrats et marchands. Citizens n’est plus le fournisseur ; sa présence optionnelle sert uniquement à nettoyer les anciens PNJ balisés par ClikerV2 lors de la migration, sans toucher aux autres. Conserver les commandes, positions et orientations sauvegardées.

## Nouveau périmètre autorisé explicitement

Le propriétaire demande désormais les raids privés avec invitations et parchemins, un boss public après un milliard de kills, les boutiques PNJ et la collection de talismans généraux. Ces systèmes font partie du périmètre autorisé ; l’ancienne attente sur les instances ne bloque pas ces demandes. Les 19 zones actuelles sont conservées ; le propriétaire a choisi de débloquer le tier 5 général dans la dernière zone actuelle (zone 19), plutôt que de le réserver à la future zone 40.
