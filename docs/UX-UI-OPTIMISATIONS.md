# UX, interfaces et optimisations

## Navigation

`/menu` ouvre le menu principal de NorthBlue. Il rassemble les fonctionnalités
actuellement disponibles : calendrier, sacs, statistiques, améliorations, prestige,
HDV, spawn, enchantements, reforge, vente d'essences et guide.

Permission : `cliker.menu`, accordée à tous par défaut. Chaque bouton respecte
également la permission de la fonctionnalité appelée. Une fonction inaccessible
apparaît verrouillée ; les noms techniques des permissions ne sont pas affichés.
La permission est revérifiée au clic et juste avant l'exécution de la commande.

Configuration créée automatiquement : `plugins/ClikerV2/gui/menu.yml`.
Taille (9 à 54, multiple de 9), titre, fond, emplacements, matériaux, textes et
commandes sont modifiables. Les actions sont exécutées **en tant que joueur**,
jamais en console. Une configuration invalide est signalée sans désactiver le
reste du plugin. Redémarrer après modification ; pas de reload à chaud ajouté.

Les déplacements d'objets sont annulés. Les doubles clics ne lancent pas plusieurs
commandes et la navigation est reportée au tick suivant, hors de l'événement de clic.

## Interfaces existantes

- Calendrier : bouton Fermer dans la dernière ligne, contenu mis à jour sans
  reconstruire le fond à chaque seconde. Le passage à minuit actualise bien le
  jour disponible même si le menu reste ouvert.
- Sacs : mention « 54 emplacements personnels », action d'ouverture et explication
  du verrouillage par rang. Les sept coffres et leur contenu restent inchangés.
- Buyzone : une horloge « Achat en cours » remplace l'étoile pendant le paiement.
  Aucun changement de prix ou de règle d'accès.
- Chargement du calendrier : indication discrète dans l'action bar ; plusieurs
  demandes avant la réponse ne lancent plus plusieurs chargements SQL concurrents.

## Optimisations effectives

- Les menus de calendrier ouverts sont suivis directement : la tâche de
  rafraîchissement ne parcourt plus tous les joueurs connectés pour les retrouver.
  Les entrées fermées, remplacées ou déconnectées sont retirées.
- Les deux éléments de décor du calendrier sont créés une fois par ouverture,
  plutôt que de recréer et réécrire 54 cases à chaque seconde.
- Une icône dynamique n'est reconstruite et renvoyée que lorsque ses valeurs
  changent. Les comptes à rebours continuent à se mettre à jour chaque seconde.
- Les durées évitent la création répétée d'un Formatter.
- Les classements partagent une requête SQL par type lors d'une actualisation :
  plusieurs hologrammes Écus ne relisent plus chacun le même top 10. Une requête
  du même type déjà en cours n'est pas relancée.
- Les lignes d'hologramme identiques aux dernières lignes appliquées ne sont pas
  renvoyées à DecentHolograms. Les classements restent sur leur moteur d'origine.

Il s'agit de réductions observables dans les chemins de code, pas d'une promesse
chiffrée de TPS ou d'un benchmark en production. Aucun test de charge sur un
serveur Paper réel n'a été réalisé pour cette révision.

## Installation

Arrêter le serveur, sauvegarder `plugins/ClikerV2/`, remplacer le JAR puis
redémarrer. Les données, objets, permissions existantes et prix sont conservés.
`gui/menu.yml` est ajouté s'il n'existe pas. Tester `/menu` avec un compte joueur.

Ces améliorations ne terminent pas les autres modules du cahier maître ; leur
état reste décrit dans `MISE-A-JOUR-MAITRE.md`.
