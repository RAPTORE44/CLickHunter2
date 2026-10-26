# Suivi des évolutions ClickHunter

La section 17 du cahier d’audit reçu ne comporte **aucune nouvelle idée renseignée**.
Les lignes vides ne sont pas traitées comme des fonctionnalités à inventer.

| Évolution | Statut | Vérification |
| --- | --- | --- |
| Bestiaire et maîtrises | Terminée | Tests et client Paper ; les espèces non découvertes restent masquées |
| Contrats quotidiens | Terminée | Acceptation testée, paiements et concurrence vérifiés automatiquement |
| PNJ des contrats | À vérifier | Adaptateur disponible ; skin et interaction réels à valider avec Citizens |
| Raid coopératif | Terminée | Combat et paiement testés ; réapparition contrôlée sur Paper |
| Avant-postes | Terminée | Terrain, extension, protections et persistance vérifiés |
| Correction buyzone | Terminée | Achats et permissions persistantes vérifiés, diagnostic disponible |
| Revue complémentaire sécurité, données et UI/UX | Terminée dans le périmètre du rapport | Voir `AUDIT-COMPLET.md` pour les limites et validations restantes |
| Rechargement global `/reload` | À vérifier | Conservation du sac vérifiée, mais exception Paper/Brigadier observée ; utiliser arrêt/redémarrage |
| Charge à la fréquentation cible | À vérifier | Aucun nombre de joueurs simultanés garanti sans mesure sur une copie représentative |

Les prochaines idées reçues seront ajoutées ici avec un identifiant, leurs dépendances et un statut :
**À développer**, **En cours**, **Terminée**, **À vérifier** ou **Bloquée**.
Une fonctionnalité terminée ne doit pas être réimplémentée pour chaque nouvelle demande.

## Propositions à valider avant développement

| Proposition | Intérêt pour le joueur | Impact sur le gameplay | Complexité | Priorité |
| --- | --- | --- | --- | --- |
| Journal des transferts d’objets avec reprise après coupure brutale | Réduire la fenêtre résiduelle de perte/duplication entre inventaire et SQLite | Aucun changement volontaire de gain ; nouveau format de reprise à migrer et tester | Élevée | P1 |
| Sauvegardes cohérentes et restauration répétée sur une copie | Protéger toute la progression, y compris les constructions | Aucun ; nécessite une politique couvrant base et mondes/playerdata | Moyenne | P1 |
| Campagne de charge à la fréquentation cible, avec spark | Vérifier la fluidité réelle des combats | Aucun ; les optimisations suivantes seront guidées par les mesures | Moyenne | P1 |
| Exiger le set précédent pour les nouveaux achats de zones | Rendre la progression par équipement plus explicite | Modification importante des conditions de progression ; conserver les accès déjà acquis | Moyenne | P2, validation requise |
| Équipement secondaire dédié par zone | Objectif supplémentaire de collection et de puissance | Nouvelle mécanique et nouvel équilibrage ; l’amulette actuelle reste passive dans l’inventaire | Moyenne à élevée | P2, validation requise |

Ces propositions ne sont pas imposées par l’audit. Le code actuel reste la référence : les prix,
les ratios de compression et les gains configurés existants sont préservés.

### Autocompresseur consommable

L'objet acheté chez le marchand s'utilise par clic droit et disparaît à l'activation. L'activation est conservée dans les données persistantes du joueur, par type de mob. Un objet déjà activé n'est pas consommé une seconde fois. La compression est exécutée après les gains de ressources des mobs : 64 ressources d'un palier deviennent une ressource du palier suivant, avec cascade. Les objets déjà achetés restent utilisables. Les prix restent identiques.

### BuyZone et apparitions vanilla

Le retour reste au nord du portail mais le joueur regarde vers le sud (y compris lors d'un retour de secours). Le mode créatif contourne les portails sans achat ni menu ; repasser en survie rétablit les contrôles habituels. Les nouvelles apparitions de créatures vanilla sont bloquées dans tous les mondes par défaut. Les apparitions CUSTOM des plugins, dont les mobs ClickHunter et les PNJ, sont conservées. Réglage : `mobs.disable-vanilla-spawns` dans `config.yml` (défaut : `true`, même avec une ancienne configuration). Les créatures déjà présentes ne sont pas supprimées.

### Audit complémentaire après les réglages de confort

- Terminée : protection des callbacks de prestige/améliorations lors d’une reconnexion sous le même UUID.
- Terminée : préservation des objets/essences si le service de livraison est absent, et des activations si la sauvegarde du compresseur échoue.
- Terminée : correction de l’activation dans l’air, sans contourner une interdiction explicite d’utiliser l’objet.
- Terminée : limitation des boutons économiques aux clics prévus et suppression de traitements redondants.
- Section 17 du nouveau cahier : aucune nouvelle idée renseignée ; aucun nouveau système majeur lancé.

Voir `AUDIT-SUIVI-2026-10-25.md` et `validation/audit-suivi.json` pour les preuves et limites. Les autres lignes jaunes et les dépendances en attente conservent leur état.

### Fournisseur de PNJ

Terminée : migration vers ZNPCsPlus 2.0.0 pour Daily, Discord, Contrats et marchands. Les commandes et positions existantes sont conservées. Vérifications natives sur Paper : interactions des quatre types, refus de doublon, restauration après redémarrage et suppression ciblée. Voir `PNJ-ZNPCSPLUS.md` et `validation/znpcsplus.json`.


### Audit du 26 octobre 2026

- Terminée : publication AH avec journal SQL, reçu playerdata, validation conjointe des frais et reprise idempotente.
- Terminée : contrôles de connexion dans `/claim`, valeurs de prestige bornées et positions YAML invalides préservées.
- Terminée : cache des boutons inchangés, invalidation ItemsAdder et messages de publication/arrêt de jeu cohérents.
- Terminée : ancrage des mobs déplacés dans un autre monde, test reproduit avant correction puis réussi.
- À vérifier sur le serveur réel : rendu du fournisseur Holographic Displays et du pack ItemsAdder, profil de charge représentatif.
- À développer : généralisation du journal de sortie aux dépôts de stations et aux lots d’essences ; elle n’est pas déclarée livrée par la protection AH.
- Section 17 : aucune nouvelle idée renseignée dans le document joint. Les autres demandes et lignes jaunes conservent leur suivi existant.

Détails et preuves : [audit du 26 octobre](AUDIT-2026-10-26.md).


### Piñata et enchères événementielles — 26 octobre 2026

- Terminée : piñata en arpenteur, point unique sauvegardé dans la zone Vache, apparition aléatoire entre 20 et 30 minutes, durée de trois minutes, annonce cliquable et téléportation.
- Terminée : un seul type de récompense par apparition, commun à tous les participants. Le propriétaire a confirmé que « souls » désigne les Gemmes Sombres. Gains journalisés, limites individuelles et récapitulatifs privés conservés hors ligne.
- Terminée : enchères toutes les trois heures, inscriptions pendant trois minutes avec rappels, trois lots légendaires successifs, prix en serpent paginé et compteur de 30 secondes relancé par offre.
- Terminée : offres fermes, contrôle de solvabilité au dépôt et au débit final, attribution atomique avec journal de livraison, reprise après redémarrage et inventaire plein via `/claim`.
- Catalogue par défaut : trois équipements déjà légendaires tirés dans les zones Blaze, Wither Squelette et Ghast ; la demande d’objets exacts reste une personnalisation possible si le propriétaire fournit une autre sélection.
- Validation : 280 tests automatisés réussis et 21 contrôles natifs répartis entre les deux modules. Pas de campagne de charge ni de validation humaine des skins/du pack ItemsAdder public.

Documentation : [piñata](PINATA.md), [enchères](ENCHERES.md), [preuves](validation/enchere-pinata.json). Les 60 commandes principales et leurs alias sont répertoriés par catégorie dans le README.
