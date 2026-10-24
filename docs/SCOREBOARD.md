# Scoreboard ClickHunter

Présentation native Paper inspirée de la capture fournie : **ClickHunter** en vert,
nom du joueur, données de progression, compteurs du serveur et adresse
**play.clickhunter.fr** en vert. Les anciennes lignes Income, Power et Vote Party
ne sont pas reprises car ces données ne sont pas disponibles dans le plugin.

Données affichées : niveau vanilla utilisé par le plugin, Écus, Gemmes Sombres,
Cendres, prestige romain, mobs tués et joueurs connectés. Les Gemmes Sombres sont
converties depuis leur stockage en dixièmes : 185 devient 18.5, pas 185.

Configuration générée : `plugins/ClikerV2/scoreboard.yml`.

- `titre` : texte et couleurs du titre.
- `actualisation-ticks` : fréquence, 40 ticks par défaut (2 secondes), minimum 20.
- `lignes` : entre 1 et 15 lignes, avec codes couleur `&`.
- Variables : `{joueur}`, `{niveau}`, `{prestige}`, `{ecus}`, `{gemmes}`, `{cendres}`,
  `{kills}`, `{connectes}`. Les lignes vides sont supportées.

Cette nouvelle présentation **active une seule fois** le scoreboard, conformément
à la demande de l'utilisateur. L'ancienne configuration est sauvegardée dans
`backups/config-scoreboard-<timestamp>.yml`. La version de présentation est
mémorisée dans `displays.scoreboard.style-version`.

Pour utiliser un scoreboard externe, définir ensuite dans `config.yml` :

```yaml
displays:
  scoreboard:
    enabled: false
    style-version: 1
```

La désactivation ultérieure est respectée. Le TAB natif reste indépendant et ne
s'active pas avec cette modification. Redémarrer pour appliquer la configuration.
Aucun plugin ou pack de ressources supplémentaire n'est nécessaire.

Le tableau n'est pas recréé à chaque mise à jour : seules les lignes modifiées
sont appliquées. Un chargement SQL déjà en cours pour un joueur n'est pas relancé.
Pendant le chargement, les monnaies affichent `…` plutôt qu'un faux solde nul.
Si un autre plugin remplace le tableau d'un joueur, celui-ci n'est pas réimposé
en boucle. Le tableau précédent est restauré à l'arrêt lorsque celui du module
est encore actif.

Tests : formatage des vraies monnaies, activation unique, respect de la désactivation,
actualisation du tableau, branding et restauration du tableau précédent.
Le simulateur ne gère pas la méthode Paper de masquage des numéros : cet appel
est capturé à cette frontière dans le test. Le code livré utilise bien
`NumberFormat.blank()` sur Paper 1.21.4. Le rendu visuel client reste à vérifier en jeu.

Installation : arrêter le serveur, sauvegarder le dossier ClikerV2, remplacer le
JAR et redémarrer. Les prix, soldes, objets et règles de progression ne changent pas.
