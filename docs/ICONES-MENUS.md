# Icônes des menus ClickHunter

Les images de `icons.zip` sont intégrées sous forme de **474 modèles d’item natifs Minecraft 1.21.4** (couleurs et variantes grises). Aucun plugin supplémentaire n’est requis pour leur rendu. Les fichiers d’origine ne sont pas des instructions d’administration : seules les textures et les définitions de ressources ont été utilisées.

## Installation indispensable côté client

1. Télécharger `ClickHunter-icones.zip`, le pack généré depuis le dossier `resource-pack/` du dépôt.
2. Héberger le ZIP à une URL HTTPS directement téléchargeable et le proposer aux joueurs avec le système de pack de ressources du serveur. Pour `server.properties`, renseigner `resource-pack=<URL>` et le SHA-1 du ZIP dans `resource-pack-sha1`. La distribution n’est pas configurée automatiquement : aucune URL d’hébergement n’a été fournie.
3. Arrêter le serveur, installer le JAR, puis redémarrer. Accepter le pack en jeu.
4. Les icônes personnalisées sont appliquées quand Paper confirme le chargement du pack. Sans pack chargé, les boutons restent en objets vanilla.

Si le serveur utilise déjà un pack ItemsAdder ou un autre pack, **fusionner les assets**, plutôt que remplacer ce pack. L’archive préparée contient `assets/clickhunter/items/`, `assets/clickhunter/models/item/` et `assets/icons/textures/`. Le pack final doit rester compatible Minecraft 1.21.4. Chaque nouvelle génération du pack existant doit reprendre cette fusion.

Commande de génération, sans dépendance Python supplémentaire :

```sh
python3 scripts/package-menu-icons.py --output dist/ClickHunter-icones.zip
python3 scripts/package-menu-icons.py --base pack-existant.zip --output dist/pack-fusionne.zip
```

L’option `--base` conserve le `pack.mcmeta` du pack existant ; vérifier qu’il cible bien Minecraft 1.21.4. Le pack autonome utilise le format 46. Publier ensuite le pack final fusionné, avec son nouveau SHA-1.

## Menus et sécurité

La couche de présentation s’applique aux boutons des menus internes : menu principal, calendrier, améliorations, prestige, paramètres, marchands, sélection des sacs, BuyZone, navigation des enchères et de `/claim`, bestiaire, contrats et avant-postes.

Les noms, lore, quantités, matériaux, enchantements et données PDC des boutons sont conservés. Seul le composant `item_model` est remplacé. Les vitrages de fond restent inchangés. Les inventaires des joueurs, les 54 cases des sacs, les dépôts d’équipement aux stations et les objets réels proposés/récupérés aux enchères ou via `/claim` ne sont pas transformés en icônes.

Les boutons actualisés sont redécorés pendant que le menu est ouvert. Le traitement s’arrête quand il n’y a plus de menu suivi. Il n’y a aucun calcul SQL ni accès disque pendant ces actualisations.

## Personnalisation

`plugins/ClikerV2/gui/icons.yml` contient les correspondances de noms et les substitutions par menu/case. Les identifiants disponibles sont listés dans `src/main/resources/gui/icons-catalog.txt`.

```yaml
active: true
exiger-pack-charge: true
menus:
  UpgradeMenu:
    '13': combat_epee
  DailyMenu:
    '4': interface_info
```

`exiger-pack-charge: false` permet un pack installé manuellement côté client, mais le serveur ne peut alors vérifier sa présence : un joueur sans les modèles verra des textures manquantes. Conserver `true` pour le repli automatique vanilla. Une configuration d’icônes invalide doit désactiver cette couche visuelle sans désactiver les autres modules du plugin.

## Validation

Les tests MockBukkit vérifient l’exclusion des inventaires externes et des coffres personnels. Deux tests de conservation des métadonnées et de chargement du pack sont ignorés par MockBukkit 4.36, qui ne fournit pas getItemModel. Les essais sur Paper confirment le composant item_model natif, le matériau conservé du bouton retour, les boutons dynamiques et l’exclusion du coffre personnel. Les modèles et références de textures sont vérifiés à la génération. Le rendu visuel dans le client nécessite l’installation du pack ; il n’est pas certifié par un test de serveur sans client graphique chargé avec le pack.
