# Icônes ItemsAdder des menus

Le plugin utilise directement les **288 items déclarés dans les configurations de `icons.zip`**, sous le namespace **`icons`** de ton installation ItemsAdder existante. Aucun nouveau pack n’est à installer, générer ou héberger. Le mécanisme autonome `clickhunter:*` de la version précédente est remplacé.

## Installation

Arrêter le serveur, remplacer `ClikerV2.jar`, puis redémarrer avec ItemsAdder et tes contenus `icons` déjà installés. Le plugin demande par exemple `icons:navigation_retour`, `icons:interface_info` ou `icons:boutique_sac_shopping` à `CustomStack.getInstance`.

Le matériau et le modèle visuel proviennent de l’item ItemsAdder réel, sans inventer de numéros CustomModelData. Les noms, lore, quantités et données d’action des boutons restent ceux de ClickHunter. Les vitrages de fond, inventaires des joueurs, coffres personnels, dépôts aux stations et objets réels des enchères et de `/claim` sont exclus.

La configuration `gui/icons.yml` associe les boutons aux identifiants existants. Les anciens réglages de pack séparé sont retirés automatiquement ; les références `boutique_enchere` (texture présente mais item non déclaré dans le ZIP) sont remplacées par l’item déclaré `boutique_vitrine`.

Si un item est absent d’ItemsAdder, le bouton reste en vanilla et un avertissement unique indique son identifiant dans la console. Une nouvelle tentative est effectuée après dix secondes. Les items résolus sont mis en cache ; le cache est invalidé lors du chargement des données ItemsAdder, si l’événement API est disponible. Un redémarrage recharge toujours les correspondances.

## Personnalisation

```yaml
active: true
menus:
  UpgradeMenu:
    '13': combat_epee
```

Les valeurs sont les identifiants sans le préfixe `icons:` ; la liste exacte figure dans `src/main/resources/gui/icons-catalog.txt`. Les configurations des autres fonctionnalités et les 46 commandes ne changent pas.

## Validation

Les tests utilisent un résolveur simulant des items ItemsAdder pour vérifier les identifiants demandés, le modèle appliqué, les noms/lore/PDC conservés, le cache, l’invalidation et l’exclusion du stockage réel. La connexion à l’installation ItemsAdder et le rendu de ton pack devront être vérifiés sur ton serveur : ItemsAdder n’est pas installé dans le serveur de test local.
