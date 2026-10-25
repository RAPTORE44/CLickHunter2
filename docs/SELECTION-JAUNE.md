# Sélection jaune — suivi réel des 141 idées

Source : `ClickHunter_classement_1500_idees.xlsx`, feuille « Classement 1-1500 ». Sélection extraite des cellules à remplissage jaune `FFFFFF00`, sans utiliser les priorités du classeur pour remplacer les choix du propriétaire. Les numéros ci-dessous sont les identifiants d’origine.

Ce document distingue les livraisons des adaptations et du travail restant. **Les 141 idées ne sont pas toutes intégrées : ce suivi décrit un premier lot et le reste du chantier.** Les lignes non jaunes restent des propositions à valider. Les dépendances mentionnées dans le classeur (pets, Ordres, zones 50, instances de boss, réseau Redis, bot Discord) ne sont pas considérées comme déjà existantes.

Les demandes antérieures restent appliquées dans l’attente de l’arbitrage sur les contradictions : dégâts qui montent d’un bloc et compression 64 → 1.

| ID | Ligne Excel | État | Idée et résultat |
| --- | --- | --- | --- |
| 819 | 12 | À développer | **Sauvegarde Automatique Asynchrone ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 826 | 13 | Partiel / adapté | **Prévention du "Softlock" ** — Jet/ramassage bloqués par défaut et autorisations par session ; vente AH des équipements protégée. Ne couvre pas les commandes des plugins tiers. |
| 823 | 16 | Partiel / adapté | **Bouton "Que faire maintenant ?" (`/guide`) ** — `/guide` conseille le prochain palier le moins cher avec le solde réel ; aucune estimation fictive de rentabilité. |
| 35 | 18 | Partiel / adapté | **Numérotation abrégée lisible ** — Suffixes K/M/B/T/Qa/Qi et arrondi corrigé dans le formateur commun ; les confirmations financières gardent les montants exacts. |
| 24 | 19 | Partiel / adapté | **Aperçu des statistiques comparatives ** — Comparaison des dégâts de base de l’épée proposée avec celle tenue ; enchantements et procs exclus et signalés. |
| 36 | 20 | Partiel / adapté | **Bouton "Acheter le Max" (Buy Max) ** — Clic droit dans `/upgrade` : achat de tous les niveaux abordables en une transaction. Les marchands vendent des pièces et non des niveaux : pas de répétition d’achat inutile. |
| 960 | 21 | À développer | **Hitbox Élargie Confortable (Interaction Entities) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 997 | 24 | Existant / à compléter | **Collecte Automatique des Ressources Oubliées (`/claim) ** — Déjà présent : les drops passent par ItemDelivery et /claim si l’inventaire est plein. |
| 961 | 27 | Intégré | **Commande Rapide `/zone <numéro>` ou `/z <numéro>` ** — `/zone <numéro>` et `/z` ; permissions BuyZone vérifiées avant la téléportation. |
| 981 | 28 | À développer | **Objet "Menu ClickHunter" Fixé au Slot 9 (Étoile du Nether / Boussole) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 51 | 31 | À développer | **Mob Doré (Shiny) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 820 | 32 | Intégré | **Journal de Progression Visuel (`/progression`) ** — `/progression` : barre des zones configurées, durée de dix secondes, nettoyage à la déconnexion. |
| 995 | 33 | À développer | **Synchronisation Visuelle du Temps de Recharge (Item Cooldown Packet) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 971 | 34 | Dépendance / arbitrage | **Bouton "Retour" Universel sur le Slot 49 (Bas-Milieu) ** — Le slot 49 n’existe pas dans les menus de 9/27/36 cases et occupe du stockage dans les sacs ; garder les 54 cases utiles demande une adaptation du cahier. |
| 829 | 37 | À développer | **Logs de Progression Anti-Duplication ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 815 | 38 | À développer | **Indicateur de Probabilité de Victoire contre le Boss ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 974 | 42 | Intégré | **Désactivation Totale des Collisions Joueur-Joueur (`collisionRule never`) ** — Collisions désactivées avec restauration à la déconnexion et à l’arrêt. |
| 975 | 43 | Intégré | **Désactivation des Dégâts de Chute et de la Faim ** — Faim maintenue à 20 ; seuls les dégâts de chute sont annulés, combat conservé. |
| 847 | 47 | Intégré | **Statistiques Détaillées par Minute (`/stats`) ** — `/stats` affiche les gains réellement crédités des 60 dernières secondes, séparés par créature ; pas de projection trompeuse pour les premières secondes. |
| 297 | 50 | À développer | **Coffre de Butin de Boss dans la Zone ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 348 | 51 | Existant / à compléter | **Immunité de Transition Courte ** — Aucune transition invulnérable longue dans le boss actuel ; les nouvelles phases de boss restent à développer. |
| 22 | 52 | À développer | **Indicateur de "Coups restants" ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 2 | 53 | Partiel / adapté | **Text Display de dégâts flottants ** — TextDisplay ajouté, mais animation ascendante d’un bloc préservée selon la demande antérieure, en attente de l’arbitrage demandé. |
| 30 | 56 | À développer | **Jauge de Surchauffe dans le viseur ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 846 | 57 | Intégré | **Célébration du Premier Boss Battu ** — Première victoire suivie par type de boss : message et particules personnelles sans dégâts ; effets supprimés en mode doux. Le suivi commence à cette version. |
| 275 | 58 | À développer | **Enclume de Fusion sans XP Vanilla ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 828 | 59 | À développer | **Testeur d'Équilibrage Admin (`/sim <zone> <prestige>`) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 833 | 61 | Partiel / adapté | **Réduction de la Fatigue Visuelle ** — Option de dispersion réduite des dégâts ; les effets de caméra et explosions supplémentaires ne sont pas ajoutés. |
| 325 | 62 | À développer | **Système de "Pitié" (RNG Meter sur le Boss) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 330 | 63 | À développer | **Annonce de Première Victoire (First Clear) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 338 | 64 | À développer | **Parchemin d'Invocation Instantanée ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 455 | 67 | À développer | **Récompenses Automatiques de Fin de Semaine ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 457 | 68 | À développer | **Lien `[Item]` et `[Stats]` dans le Chat ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 460 | 69 | À développer | **Événement Horaire "Rush de Récolte" (10 min) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 461 | 70 | À développer | **Événement Horaire "La Piñata Géante du Spawn" ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 489 | 72 | Dépendance / arbitrage | **Alertes Discord des Événements Majeurs ** — Nécessite la configuration d’une intégration Discord et les consentements de notification ; aucun message externe envoyé. |
| 956 | 73 | Intégré | **Composant `enchantment_glint_override` (1.20.5+) ** — Les essences compressées utilisent enchantment_glint_override sans faux enchantement ; le ratio 64 → 1 reste inchangé. |
| 957 | 74 | À développer | **Composant `custom_name` et `lore` en JSON Riche avec Dégradés (MiniMessage) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 993 | 75 | À développer | **Protection Contre le Clic Droit Involontaire sur les Blocs ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 890 | 76 | Existant / à compléter | **Transparence Totale des Probabilités ** — Les coffres affichent déjà 100 % pour leurs gains déterministes et leur chance de base de drop. Le bonus Loot Finder peut augmenter la chance effective ; affichage contextuel par joueur et boutique externe à compléter. |
| 336 | 77 | À développer | **Classement par Boss à l'Entrée de l'Arène ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 825 | 79 | À développer | **Formule de Défense Lisible ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 843 | 80 | À développer | **Sauvegarde de Combo à la Déconnexion Involontaire ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 274 | 81 | À développer | **Échange Sécurisé (`/trade`) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 967 | 82 | À développer | **Commande `/dps` ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 836 | 83 | À développer | **Clarté des Multiplicateurs (Additif vs Multiplicatif) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 384 | 84 | À développer | **Bonus de Retour Rapide (Rubberbanding) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 253 | 87 | À développer | **Réduction de Fidélité du PNJ ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 347 | 88 | À développer | **Phase de Frénésie Finale (0 à 15% PV) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 822 | 92 | Dépendance / arbitrage | **Aide Contextuelle Automatique ** — Le compresseur est consommé à l’activation : aucun équipement en main gauche à conseiller. |
| 21 | 96 | Partiel / adapté | **Mode Performance** — Les textes de dégâts sont désormais visibles seulement par leur auteur ; `/settings` permet de les désactiver. Le filtrage des autres particules reste à développer. |
| 989 | 98 | À développer | **Son de Notification Personnalisable à 100% de Progression ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 979 | 99 | Dépendance / arbitrage | **Compatibilité Cross-Version (ViaVersion 1.20 à 1.21.4+) ** — Nécessite ViaVersion et une matrice de tests de versions clientes ; non certifié sans ces essais. |
| 315 | 102 | Partiel / adapté | **Compteur de Temps au Millimètre (Speedrun de Boss) ** — Chronomètre mesuré entre premier coup et mort avant la file SQL ; record personnel par boss conservé. Classement serveur des temps restant à développer. |
| 834 | 105 | À développer | **Sondage de Rétention au Niveau 5 ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 830 | 107 | À développer | **Verrouillage de Sécurité des Échanges ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 969 | 111 | À développer | **Système de Polices d'Icônes Légères (Unicode / Mini-Pack) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 816 | 112 | À développer | **Système de Rattrapage pour Nouveaux Joueurs (Catch-up Mechanic) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 988 | 114 | À développer | **Mode Nuit d'Interface (Dark Mode GUI) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 978 | 115 | Dépendance / arbitrage | **Sauvegarde Redis / Base de Données Instantanée ** — Nécessite une architecture multi-serveurs, un service Redis et des règles de cohérence des inventaires ; aucun service Redis fourni. |
| 821 | 118 | À développer | **Récompense de Retour (Welcome Back Bonus) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 990 | 119 | Dépendance / arbitrage | **Exportation de Profil en Image sur Discord (`/profil` Bot Discord) ** — Nécessite un bot Discord et son hébergement/configuration ; aucun bot fourni. |
| 850 | 121 | À développer | **Rapport de Bug Récompensé (`/bug`) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 296 | 124 | Partiel / adapté | **Verrouillage d'Objet (`/lock`) ** — `/lock` protège les épées, armures et compresseurs contre le jet et la vente AH. Les systèmes de recyclage/pets mentionnés n’existent pas encore. |
| 328 | 126 | À développer | **Contrainte de Défi (No-Hit Bonus) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 952 | 127 | À développer | **Attribut natif de taille** — Le boss actuel du raid utilise une entité Giant ; l’attribut natif SCALE et les mini-boss restent à développer. |
| 279 | 128 | À développer | **PNJ Évaluateur de Puissance (Gear Score) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 295 | 131 | À développer | **Historique des Achats ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 324 | 136 | À développer | **Coffre de Fin de Combat Physique ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 314 | 138 | À développer | **Coopération de Boss (Mode Raid 2 à 4 Joueurs) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 339 | 145 | À développer | **Boss Mondial (World Boss Horaire) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 46 | 149 | À développer | **Confirmation rapide par double-clic ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 266 | 158 | À développer | **Pack "Tout Améliorer" (One-Click Gear Up) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 15 | 162 | À développer | **Scoreboard contextuel ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 44 | 165 | À développer | **Hologramme de rentabilité ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 398 | 168 | À développer | **Bénédiction Quotidienne de Prestige ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 34 | 183 | À développer | **Notification Toast (Advancement) personnalisée ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 199 | 186 | À développer | **Personnalisation du Message de Kill de Boss ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 849 | 187 | À développer | **Pause Café Officielle (Mode Pause de 5 min) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 302 | 192 | À développer | **Bouclier Directionnel ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 341 | 193 | À développer | **Réduction du Coût d'Invocation ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 305 | 195 | À développer | **Onde de Choc Sautée ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 318 | 196 | À développer | **Pilier d'Abri ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 331 | 197 | À développer | **Rayon Tournant (Laser Sweep) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 308 | 198 | À développer | **Cristaux de Faiblesse ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 321 | 199 | À développer | **Poussée du Boss (Knockback Pulse) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 334 | 200 | À développer | **Boss "Miroir de Couleur" ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 311 | 201 | À développer | **Difficultés Multiples par Boss ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 350 | 202 | À développer | **Collection de Cœurs de Boss ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 977 | 204 | À développer | **Mise à Jour Dynamique du Lore des Items (Packet Lore) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 304 | 205 | À développer | **Télégraphes d'Attaque au Sol (Zones Rouges) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 343 | 206 | À développer | **Spectateur d'Arène ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 307 | 207 | À développer | **Mécanique de "Duel de Clics" (Clash Phase) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 333 | 208 | À développer | **Siphon de Vie du Boss ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 346 | 209 | À développer | **Statue du Vainqueur ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 323 | 212 | À développer | **Aura de Gel de Clic ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 326 | 213 | À développer | **Trophée de Boss Équipable ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 976 | 214 | À développer | **Maintien de l'Armure Équipée au Clic Droit ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 303 | 215 | À développer | **Phase d'Invocation de Sbires ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 953 | 216 | À développer | **Attribut natif `player.entity_interaction_range` (1.20.5+) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 332 | 218 | À développer | **Plancher piégé en Damier ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 309 | 219 | À développer | **Alternance d'Arme / Main ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 335 | 220 | À développer | **Exécution Cinématique ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 985 | 221 | À développer | **Animation de Coffre par `Block Display` ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 312 | 222 | À développer | **Invocation par Offrande ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 962 | 223 | Intégré | **Commande `/boss` ** — `/boss` utilise la zone accessible la plus avancée et refuse une arrivée dangereuse. |
| 29 | 225 | Intégré | **Récapitulatif de session à la déconnexion/reconnexion ** — Récapitulatif des gains et des coups de la session précédente affiché à la reconnexion, conservé dans le PDC vanilla. |
| 42 | 226 | À développer | **Indicateur de record personnel ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 344 | 229 | À développer | **Pari sur le Combat ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 48 | 230 | Partiel / adapté | **Compteur de clics totaux sur le profil ** — Compteur persistant des coups validés ajouté à `/stats`. Affichage au survol du pseudo dans le chat restant à développer. |
| 272 | 232 | À développer | **Puits à Souhaits du Spawn ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 14 | 236 | À développer | **Transition de zone cinématique ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 998 | 237 | À développer | **Mode Cinématique pour Trailers (`/cinema`) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 13 | 238 | À développer | **Mannequin de mesure CPS/DPS ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 45 | 246 | À développer | **Cadre d'item brillant dans les menus ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 370 | 249 | À développer | **Effet Global du Prestige d'un Joueur ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 9 | 251 | À développer | **Alerte sonore d'amélioration (Upgrade Ping) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 12 | 256 | À développer | **GUI Shop en frise chronologique ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 25 | 257 | À développer | **Particules de traînée d'épée (Slash Trail) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 38 | 258 | À développer | **Effet de tremblement (Camera Shake) au spawn du Boss ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 28 | 262 | À développer | **Animation d'ouverture de nouvelle zone ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 18 | 268 | Intégré | **Son de "Caisse enregistreuse" ou d'émeraude ** — Son personnel joué seulement lorsqu’au moins une compression automatique a eu lieu. |
| 11 | 274 | À développer | **Présentoir d'armure de zone ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 453 | 279 | À développer | **Classement par Zone ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 482 | 286 | À développer | **Protection Anti-Vol de Mob Optionnelle ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 452 | 309 | À développer | **Classements Multi-Périodes ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 955 | 314 | Dépendance / arbitrage | **Composants d'Items `max_stack_size` (1.20.5+) ** — Conflit avec la demande précédente de piles de 64 ; question d’arbitrage posée. |
| 968 | 315 | À développer | **Commande `/drops <zone>` ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 951 | 319 | Partiel / adapté | **Utilisation des `Text Display` (1.19.4+) au lieu des ArmorStands pour tous les hologrammes de stacks et de dégâts ** — Textes de dégâts convertis en TextDisplay, interpolation native, limites et nettoyage conservés ; pas de promesse chiffrée de gain CPU. |
| 999 | 322 | À développer | **Compteur Global en Temps Réel au Spawn ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 86 | 331 | À développer | **Mobs Dorés Fugitifs ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 842 | 334 | Existant / à compléter | **Recyclage Automatique des Entités Fantômes ** — Les textes temporaires existants sont bornés et supprimés à expiration, déconnexion et arrêt ; récupération après crash non ajoutée. |
| 5 | 339 | À développer | **Saut d'impact ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 47 | 342 | À développer | **Musique d'ambiance en NoteBlocks ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 365 | 351 | À développer | **Icône et Couleur de Chat Évolutives ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 4 | 352 | À développer | **Micro-rotation au clic ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 17 | 353 | À développer | **Compteur de Combo à l'écran ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 20 | 356 | Partiel / adapté | **Filtre anti-spam de chat ** — `/settings` règle les textes de dégâts et le son de compression ; filtre des messages de drops encore à développer. |
| 33 | 357 | À développer | **Effet de zoom léger (Speed FOV) ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 39 | 361 | À développer | **Onglet TabList illustré par zone ** — Non livré ; conserver la demande dans le périmètre de développement. |
| 1344 | 1488 | Dépendance / arbitrage | **Salut d'Ordre ** — Dépend d’un système d’Ordres absent du plugin. |

## Validation et limites

Les tests couvrent aussi l’expiration exacte de la fenêtre de gains à 60 secondes et la conservation du compteur de coups. Ils couvrent les protections de jet/ramassage et leur remise à zéro à la déconnexion, les dégâts de chute, la faim, les achats maximaux avec concurrence et les textes de dégâts ascendants. Les nouvelles téléportations doivent aussi être essayées sur la carte réelle : elles refusent les arrivées sans sol sûr et respectent l’annulation par les autres protections. Aucun terrain de boss n’est construit automatiquement.

Les préférences visuelles sont conservées dans les données persistantes vanilla du joueur. Les autorisations de jet/ramassage restent uniquement en mémoire et sont effacées à la déconnexion. Ne pas déplacer des opérations Bukkit de sauvegarde du joueur sur un thread SQL : cela ne constituerait pas une sauvegarde asynchrone sûre.

## Autres idées proposées, non activées

- **996 — Alerte d’inventaire à 90 %** : prévient avant de passer les récompenses dans `/claim`, avec limitation des rappels.
- **832 — Mode daltonien** : ajoute des symboles aux états colorés des menus.
- **809 — Tutoriel interactif court** : guide un nouvel arrivant vers son premier mob, le marchand et l’activation réelle du compresseur.
- **282 + 283 — Réserve de ressources liée aux marchands** : libère l’inventaire ; nécessite une migration transactionnelle et des limites configurables.
- **987 — Temps estimé avant achat** : fondé sur les gains réellement mesurés, après une période d’observation ; ne pas afficher de fausse précision.

Ces propositions ne sont pas activées sans validation du propriétaire.

Les 46 commandes actuelles restent recensées dans le README. Les nouveaux compteurs personnels et les premières victoires commencent avec cette version ; les anciennes sessions ne peuvent pas être reconstituées. La télémétrie de farm ne constitue pas un journal financier ni un classement hebdomadaire.
