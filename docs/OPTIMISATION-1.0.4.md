# Version 1.0.4 — zones en veille, combat et commandes

## Installation

Paper 1.21.4 / Java 21. Remplacer le JAR puis redémarrer en conservant les données. `/version ClikerV2` doit afficher **1.0.4**. `/diagnostic modules` vérifie la version, les fournisseurs de commandes, les chunks et entités chargés et l’activation du TAB/scoreboard. `/diagnostic` ajoute une interface avec les compteurs de mobs actifs et de zones en veille.

## Travail évité lorsqu’une zone est vide

Un index spatial des joueurs est renouvelé une fois par seconde. Les spawns et hologrammes publics à plus de **64 blocs** de tout joueur passent en veille. Les positions sauvegardées restent en mémoire et en SQLite ; les PV virtuels d’un mob endormi sont conservés lors de son réveil. Un boss mort attend toujours sa vague : la veille ne le ressuscite pas.

Le démarrage et les resets ne forcent plus le chargement de tous les chunks des points de mobs. Le réveil retrouve d’abord les entités existantes du même point et supprime les doublons. Le chargement naturel d’un chunk nettoie aussi les anciens corps appartenant à des points supprimés, sans charger les régions éloignées pour les rechercher.

Les mondes des raids privés sont créés au lancement d’un groupe. Les neuf tickets de chunks de l’arène publique sont relâchés lorsqu’elle est vide. Ses tâches rapides de déplacement et d’hologrammes sont suspendues ; le contrôle léger de présence reste actif pour reprendre le combat. Les hologrammes et les piñatas libèrent leur représentation physique et leurs tickets en l’absence de joueurs ; le délai réel des événements continue. Un joueur peut toujours rejoindre une piñata annoncée, puis sa représentation se réveille.

Les classements holographiques invisibles n’effectuent plus de lecture SQL périodique. Les positions des mobs normaux sont contrôlées toutes les deux ticks, au lieu de chaque tick ; la cadence de déplacement des boss reste conservée. Les messages de dégâts et l’animation montante restent limités et nettoyés à la fin de leur durée.

### Réglages SFTP

`performance/zones.yml` :

```yaml
mise-en-veille: true
distance-activation: 64
actualisation-ticks: 20
liberer-chunks-spawn: true
```

La distance peut être augmentée pour une grande zone. Les chunks de spawn vanilla ne sont plus maintenus par leur gamerule quand `liberer-chunks-spawn` est actif. Les tickets d’autres plugins sont respectés. Un chunk peut rester chargé pendant le délai normal de déchargement de Paper ; les mondes et données ne sont pas supprimés. Désactiver cette option empêche son application aux prochains chargements ; une gamerule déjà enregistrée à zéro reste une donnée du monde.

Les API de monde restent sur le thread serveur ; SQLite et les calculs déjà adaptés restent asynchrones. Référence consultée : [documentation officielle Paper sur les tâches](https://github.com/PaperMC/docs/blob/main/src/content/docs/paper/dev/api/scheduler.md), qui précise que les accès/modifications du monde ne sont généralement pas sûrs depuis une tâche asynchrone. Répartir directement les entités sur plusieurs threads ne serait pas une optimisation sûre sous Paper classique.

## Effets de combat et dégâts

L’attaquant reçoit son noteblock, ses particules, ses textes et sa blessure visuelle. Les blessures et événements de dégâts natifs sont filtrés pour les autres spectateurs via l’API PacketEvents intégrée à ZNPCsPlus 2.0.0. Le vrai décès reste commun à tous. Les zones d’impact dangereuses des boss restent visibles aux participants pour permettre l’esquive. Les PV communs restent cohérents entre les joueurs.

L’Épée du Dresseur a **5 dégâts de base**, avec correction de l’ancien texte +25 dans l’inventaire et de la comparaison marchand. Les bonus d’équipement, prestige, niveaux et potions continuent de s’ajouter. Tous les ensembles ont été testés en OP et non OP avec le même équipement et les mêmes bonus. Les identifiants accentués de l’Âne sont acceptés ; le coup final repose sur les PV virtuels, sans réduction supplémentaire par l’armure vanilla du mob, et conserve le bon tueur.

Les boss de zone gardent **100 ×** les PV de leur mob et une attaque native nulle ; leur listener annule aussi les dégâts directs et projectiles vers les joueurs. Les boss de raid ont **1 000 ×** les PV du mob du raid ; le boss public conserve ses attaques et ses PV configurables.

## Compression

Le marchand et les compresseurs utilisent le même barème d’essences. Commun jusqu’au Lapin, Peu commun de l’Ourse au Hoglin, puis Rare à partir du Magma Slime. Vache ne compresse donc pas ses Communes. Le marchand masque les conversions inutiles et bloque leur action même avec un ancien clic. Il n’encaisse pas de compresseur lorsqu’aucune compression n’est nécessaire. Les anciens objets/activations restent conservés ; la décompression permet de récupérer la valeur des ressources déjà compressées.

## Commandes et présentation

- `/drop` remplace `/antidrop` et `/ramassage` : jet et ramassage basculent ensemble, jusqu’à déconnexion. Les objets verrouillés restent protégés. Permission `cliker.comfort.use`.
- `/inv <joueur>` ouvre l’inventaire réel d’un joueur en ligne pour le staff, avec dépôts et retraits. Permission `cliker.staff.inv`, OP par défaut. Aucun miroir différé de l’inventaire ; changements de monde, déconnexion, livraison en cours et isolation des avant-postes sont contrôlés.
- `/trash`, `/poubelle` et `/pouvelle` conservent le menu de 54 cases, gauche 1 et droite pile. Les objets restent récupérables avant fermeture ; récupération après déconnexion ou arrêt et protection des profils d’avant-poste conservées.
- L’ancien hologramme global « Prochains boss » est retiré, y compris son ancien point sauvegardé. Les compteurs individuels des zones restent disponibles. L’option de création `bosstimer` est retirée de `/holotop`.
- Les secondes restantes sont rétablies dans le titre des trois types de piñata, même pour un ancien titre configuré sans compteur. Couleurs Écus/XP/Gemmes conservées.
- Une migration réactive une fois le TAB et le scoreboard, en conservant le style RGB, le bandeau animé, les compteurs courts et l’étoile de menu en gras.

## Résultats vérifiés

**449 tests automatisés réussis**, sans échec, erreur ni test ignoré.

Sur une copie Paper 1.21.4 avec Java 21 et ZNPCsPlus 2.0.0, sans joueur et avec les mêmes 26 points sauvegardés, le diagnostic réalisé trois secondes après démarrage donne :

| Mesure | Ancien JAR 1.0.3 | Version 1.0.4 |
| --- | --- | --- |
| Points sauvegardés | 26 | 26 |
| Mobs chargés à vide | 26 | 0 |
| Points en veille | pas de veille explicite | 26 |
| Mondes privés de raid chargés d’avance | 4 | 0 |

Ces mesures concernent les entités de ce scénario, pas un pourcentage global de CPU ou de RAM. La mémoire JVM dépend aussi du garbage collector, des mondes et des autres plugins ; aucun pourcentage de gain global n’est annoncé sans profilage de production.

Les parcours réels à deux clients ont vérifié :

- 5 PV retirés par le Dresseur non OP ; animation de blessure reçue par l’attaquant et pas par le spectateur.
- Épée Âne non OP : décès malgré un attribut d’armure vanilla de 30, animation de mort reçue par les deux clients.
- Mise en veille puis retour d’un mob unique, avec ses **95/100 PV** conservés.
- Dépôt de trois diamants via `/inv` réellement reçu par le joueur ciblé ; `/drop` bascule les deux actions pour le non OP.
- Poubelle accessible : dépôt de pile, reprise d’un objet, destruction du reste à la fermeture.
- Piñata avec secondes visibles dans les paquets d’hologramme ; diagnostic en interface graphique.
- Boss de zone : attaque simulée de 12 dégâts annulée pour le non OP et attribut d’attaque égal à zéro.
- Redémarrage : ancien point « Prochains boss » supprimé de SQLite, TAB et scoreboard actifs, spawns sauvegardés conservés en veille.

Tests effectués sur un serveur isolé. La production n’est pas accessible depuis cet environnement ; l’installation du nouveau JAR et le redémarrage restent nécessaires. Le rendu Holographic Displays réel et ItemsAdder nécessitent les JAR compatibles du serveur ; les essais utilisent le secours natif et les tests d’API. Les téléchargements Mojang ont rencontré des erreurs réseau dans la copie de test.
