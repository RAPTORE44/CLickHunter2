# Piñata partagée — zone Vache

Intégrée à ClikerV2, Paper 1.21.4 / Java 21. « Souls » correspond aux **Gemmes Sombres**, conformément au choix confirmé. Aucun PNJ tiers nécessaire pour l’arpenteur : c’est une entité d’événement, distincte des mobs de progression. Son hologramme utilise le fournisseur commun du plugin et son secours natif.

## Commandes

- `/pinata setspawn` : administrateur debout à l’emplacement voulu dans la zone Vache ; sauvegarde ce point et son orientation. Il s’agit du point unique d’apparition, pas d’un portail ni d’une nouvelle zone payante. Modification refusée pendant un événement.
- `/pinata settp` : définir une arrivée distincte pour les joueurs.
- `/pinata spawn` : apparition forcée, au point sauvegardé. Refuse les doublons. Console autorisée.
- `/pinata` : état actuel et raccourci cliquable.
- `/pinata tp` : rejoindre l’événement encore actif. Depuis l’avant-poste, revenir d’abord au spawn pour conserver l’isolation des inventaires.
- Alias `/pignata` ; gestion réservée à `cliker.pinata.admin` (op), consultation/téléportation à `cliker.pinata.use` (tous par défaut).

## Règles

Délai tiré aléatoirement entre **1 200 et 1 800 secondes entre deux apparitions** ; une apparition forcée recale la prochaine échéance. Durée maximale **180 secondes**, ou jusqu’à épuisement des PV. La prochaine échéance et le point sont sauvegardés. Un redémarrage clôt l’ancienne piñata, conserve les gains déjà validés et les récapitulatifs, nettoie les anciennes entités balisées et reprend la planification sans recréer de doublon.

Une seule catégorie est choisie à l’apparition, à chances égales : Écus, Gemmes Sombres ou XP. Elle reste identique pour chaque joueur pendant les trois minutes, et le journal SQL refuse un gain d’un autre type. Les quantités varient selon la configuration (par défaut 25–75 Écus, 1–2 Gemmes Sombres, ou 10–30 XP par coup valide).

L’arpenteur perd des PV virtuels et joue une animation de blessure. Plusieurs participants peuvent frapper en même temps ; aucun temps d’invulnérabilité partagé ne les bloque. Limite individuelle par défaut : un coup toutes les 400 ms, plus une opération SQL en cours maximum par joueur. Ni les dégâts ni les multiplicateurs d’équipement ne changent les quantités. Créatif, spectateur, projectiles, familiers et dégâts environnementaux ne gagnent rien. Le clic droit est bloqué pour empêcher selle, monte et alimentation. Les coups exigent une distance de six blocs au plus et une ligne de vue. L’autoclic existant reste consacré aux mobs de progression ; frapper directement la piñata.

Chaque gain, reçu idempotent et total de participant sont inscrits dans la même transaction. Les Écus et Gemmes Sombres sont crédités au solde, l’XP utilise la livraison persistante déjà présente. Aucun objet n’est envoyé dans `/claim`. À la fin, chacun reçoit son récapitulatif en privé ; hors ligne, il l’obtient à la prochaine connexion. Une répétition de notification après une panne n’attribue jamais de gain supplémentaire.

## Configuration et installation

`plugins/ClikerV2/activites/pinata.yml` contient les messages français, le titre d’hologramme, les délais, le point et les récompenses. Les bornes de Gemmes sont des quantités entières affichées, converties automatiquement en unités internes. Les bornes invalides sont refusées. Les valeurs d’origine sont conservées dans les ressources du JAR ; les modifications de fichiers sont prises en compte au redémarrage.

Arrêter le serveur, sauvegarder le dossier du plugin et les mondes/playerdata, remplacer `ClikerV2.jar`, redémarrer puis définir le point dans la zone Vache. Les deux nouvelles tables SQL sont ajoutées sans modifier les soldes ou tables existants. Aucun point n’est inventé automatiquement dans un monde inconnu.

## Validation

Les tests SQLite vérifient le type unique par apparition, deux joueurs indépendants, les doubles coups, le refus après fin, les totaux, le dépassement de solde et la conservation des récapitulatifs à la reprise. Les contrôles natifs et leur périmètre sont consignés dans [le fichier de validation commun](validation/enchere-pinata.json). Le rendu réel Holographic Displays reste soumis à la présence d’un fournisseur compatible ; le serveur local utilise le secours natif.


Validation livrée : 280 tests automatisés passent sur le plugin complet. Le scénario piñata accéléré vérifie cinq parcours natifs et le scénario de durée réelle en vérifie trois, dont une durée mesurée de 180736 ms. Les réglages accélérés appartiennent uniquement à la copie de test ; le JAR conserve 180 secondes et 1 200–1 800 secondes.


## Évolution : dégâts, interface unique et arrivée séparée

La piñata possède désormais des PV virtuels configurables (`points-vie`, 10 000 par défaut). Les coups valides retirent entre 1 et 1 000 PV selon leurs dégâts Bukkit et déclenchent l’animation de blessure ; les gains ne sont pas multipliés par ces dégâts. L’événement se termine lorsque les PV sont épuisés ou à l’échéance de trois minutes. Les récaps restent sauvegardés et privés, avec un seul type de récompense par apparition.

Le titre partagé ne contient plus de compte à rebours : `messages.titre-sans-chrono` utilise `{pv}`, `{pv-max}` et `{type}`. Aucun nom vanilla n’est conservé lorsque l’hologramme commun est actif, pour éviter le second affichage au survol. Les dégâts flottants des mobs restent distincts de leur titre.

La hotbar affiche chaque gain et le total personnel de cette apparition (`messages.gain-total`, `{montant}`, `{type}`, `{total}`). `/pinata settp` définit une arrivée séparée ; sans point explicite, le joueur arrive trois blocs au nord de la piñata, après contrôle de sécurité. `/pinata setspawn` conserve son rôle de point d’apparition unique.
