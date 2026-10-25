# PNJ Discord

Le PNJ utilise désormais **ZNPCsPlus 2.0.0**. Voir [installation et migration](PNJ-ZNPCSPLUS.md).

- `/discord` : lien cliquable `https://discord.gg/WSwBwpnQTa` (permission `cliker.discord.use`).
- `/discord pnj spawn` : PNJ à la position et à l’orientation actuelles (permission `cliker.discord.admin`).
- `/discord pnj remove` : suppression persistante de ce PNJ seulement.

Le clic droit donne le lien au joueur, sans message global. Configuration : `discord.yml`, avec skin, titre, sous-titre et messages. La commande `/discord` fonctionne même sans ZNPCsPlus. Les positions existantes sont conservées et les autres PNJ ne sont pas supprimés.
