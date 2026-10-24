# Textes de dégâts animés

Sur un coup infligeant des dégâts aux mobs gérés par ClikerV2, le texte existant
est conservé (`-25`, par exemple), mais monte maintenant d'**un bloc** avant de
disparaître. Le texte reste orienté vers la caméra, comme un nom flottant natif.
Aucun plugin d'hologrammes supplémentaire n'est nécessaire.

L'animation remplace l'ancien texte fixe : elle ne s'ajoute pas en doublon.
Elle affiche les dégâts calculés pour le coup ; elle ne crée aucun gain de blé,
monnaie ou objet. Le calcul des dégâts et des récompenses n'est pas changé.
Les événements déjà annulés sont ignorés par le handler de dégâts.

Fichier généré : `plugins/ClikerV2/damage-popups.yml`.

```yaml
active: true
montee-blocs: 1.0
duree-ticks: 20
texte: '&c&l-{degats}'
decalage-vertical: 0.5
dispersion: 0.35
maximum-actifs: 200
maximum-par-joueur: 20
```

`{degats}` affiche le montant réel du coup, au format compact du plugin.
Les couleurs et le texte sont personnalisables avec les codes `&`.
La dispersion décale seulement le point de départ horizontalement ; la trajectoire
reste verticale. Après 20 ticks, la montée atteint exactement un bloc ; le texte
est supprimé au tick suivant. Cela correspond à environ une seconde à 20 TPS.

Une seule tâche anime tous les textes actifs ; elle s'arrête quand il n'y en a
plus. Les limites évitent une accumulation lors de frappes rapides et retirent
les anciens textes si nécessaire. Ces limites concernent uniquement l'affichage,
pas les dégâts. Les entités sont non persistantes et nettoyées à la déconnexion,
au déchargement du monde et à l'arrêt du plugin.

Tests : hauteur finale exacte, position horizontale stable, disparition, absence
de texte pour des dégâts nuls, limites en cas de spam et nettoyage du cycle de vie.
Le rendu client et les performances en charge restent à vérifier sur Paper réel.

Installation : arrêter le serveur, remplacer le JAR, puis redémarrer.
Redémarrer après modification de la configuration.
