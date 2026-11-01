# Optimisation 1.0.9

Paper 1.21.4, Java 21. Remplacer le JAR puis redémarrer. `/version ClikerV2` doit afficher 1.0.9. Aucune commande ni permission ajoutée ; le README conserve la liste complète classée par catégorie.

## Corrections

- `ui/MenuIcons.render/open/eligibleSlots` : masquage des cases calculé une fois pour chaque rendu, avec les mêmes règles de protection des objets réels. Aucun cache supplémentaire de politiques : les changements de drapeaux des menus sont relus à chaque image. Test instrumenté : deux lectures de holder au lieu des 108 appels déduits de l'ancien chemin de 54 cases. Ce n'est pas un pourcentage global de CPU.
- `display/MoneyScoreboard.update` : un état immuable et un tableau de composants par session en ligne. Égalité des données avant formatage ; vérification des préfixes conservée, y compris sans changement de données, pour restaurer une ligne modifiée par un autre composant. L'état est remplacé au changement et supprimé avec la session. Pas de cache de solde susceptible de masquer un achat.
- `combat/AutoClickManager.start/stopIfIdle` : tâche unique à la demande, arrêt dès que la liste est vide, redémarrage à l'activation. Mort et spectateur restent des pauses sans perte du réglage. Recherche toutes les dix ticks au plus, et cadence effective inchangée. Le premier contrôle après activation intervient dans les dix ticks.

## Validation

478 tests fonctionnels, zéro échec/erreur/ignoré. Les deux microbenchmarks historiques restent séparés des tests fonctionnels. Régressions : icônes Daily changeantes, menus de marchand, inventaires protégés, cadence et portée de l'autoclic, arrêt et redémarrage de sa tâche, économies/claim et mutations du scoreboard.

Le profil JFR de la passe 1.0.7 avait identifié le chemin des icônes parmi les allocations du plugin. La présente correction retire une répétition structurelle ; aucun gain global RAM, CPU ou FPS n'est annoncé sans comparaison en conditions identiques de production. Les lectures SQL périodiques du scoreboard sont conservées pour ne pas introduire de stale cache ; seule la reconstruction graphique inutile est supprimée.

Les gains ne suppriment pas les risques déjà documentés de la file SQL globale non bornée ou les garanties limitées des commandes console externes du Daily. Le rendu Holographic Displays et ItemsAdder avec les JAR de production n'a pas été certifié ici.

Essais réels de la version : [résultats Paper](validation/optimisation-1.0.9.json). Un profil JFR de 25 secondes a été capturé pendant la répétition de ces menus, puis le retour au repos : [résumé](performance/jfr109-summary.txt). Profil exploratoire après uniquement, pas une comparaison avant/après ni un test prolongé sous forte charge.
