# Inventaire statique de l’audit ClickHunter

Produit par `python3 scripts/audit-inventory.py`. Chaque fichier de production est indexé ; cette liste n’est pas une certification de son comportement.

| Composant | Fichiers Java | Lignes |
| --- | ---: | ---: |
| noyau | 5 | 1739 |
| auction | 5 | 1389 |
| bag | 7 | 1016 |
| buyzone | 3 | 881 |
| combat | 6 | 547 |
| comfort | 5 | 1299 |
| config | 3 | 219 |
| cooldown | 1 | 62 |
| daily | 6 | 814 |
| discord | 1 | 143 |
| display | 5 | 824 |
| economy | 4 | 280 |
| essence | 8 | 689 |
| features | 14 | 3618 |
| gear | 19 | 2145 |
| hologram | 4 | 823 |
| hunting | 6 | 1188 |
| level | 3 | 140 |
| liveauction | 4 | 1819 |
| lootbox | 5 | 554 |
| merchant | 4 | 1001 |
| mobs | 7 | 1916 |
| npc | 1 | 323 |
| outpost | 4 | 2019 |
| pinata | 2 | 811 |
| prestige | 6 | 939 |
| raid | 2 | 680 |
| referral | 2 | 576 |
| rewards | 5 | 1488 |
| runtime | 2 | 37 |
| security | 1 | 32 |
| social | 5 | 2491 |
| spawn | 5 | 279 |
| station | 4 | 410 |
| tuto | 3 | 292 |
| ui | 5 | 738 |
| upgrade | 6 | 995 |
| world | 1 | 98 |

Total : **179 fichiers Java**, **34 configurations YAML**.

## Fichiers indexés

| Fichier | SHA-256 au moment de l’inventaire |
| --- | --- |
| [src/main/java/fr/cliker/clikerv2/ClikerPlugin.java](../src/main/java/fr/cliker/clikerv2/ClikerPlugin.java) | `638ec4dfde3850bd3d95c72a2abc774f313a267d3f459cf075bf9a182c64876d` |
| [src/main/java/fr/cliker/clikerv2/DatabaseManager.java](../src/main/java/fr/cliker/clikerv2/DatabaseManager.java) | `1876886b8fc739e0b443517414a3670b1b79a2e803ab25b81701d285f695e568` |
| [src/main/java/fr/cliker/clikerv2/EconomyService.java](../src/main/java/fr/cliker/clikerv2/EconomyService.java) | `98912dd947c1c61a7e103091fc5ae036f4a8e5dcbbc2fb77dea50237b5573754` |
| [src/main/java/fr/cliker/clikerv2/MobTier.java](../src/main/java/fr/cliker/clikerv2/MobTier.java) | `f7489c7fede9215108dfa529cf50d449b5b37ce739b042db58edb7835352f7aa` |
| [src/main/java/fr/cliker/clikerv2/Numbers.java](../src/main/java/fr/cliker/clikerv2/Numbers.java) | `7fcf8011789d9aa6949c9e29e80bba5bdb0afc182da9403106a79cf9fa32437a` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionCommand.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionCommand.java) | `1c17aac3f33838eb70e394e9305dbd335d2d70b7f760398f9c60528fc86118bc` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionListing.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionListing.java) | `2a9c309e2b156fd7ed7dfa38146b4bcfc657805dec3fb08f6b8ace83c094aaf3` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionManager.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionManager.java) | `527e9f499e220ca69983c92f8205cb48e7dc807fc459ae88c04aae1aa01e25ac` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionMenu.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionMenu.java) | `654d8032e3bcca3a2747ee2c7704040752f55441538b7976be26a502ea6e8599` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionStore.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionStore.java) | `5cbbd3f48a725777672bbc8dab25bfc6e3d0e39ba840fc53eb6bb9d9a3976ba9` |
| [src/main/java/fr/cliker/clikerv2/bag/BagCommand.java](../src/main/java/fr/cliker/clikerv2/bag/BagCommand.java) | `ca3d7f21c2f09ce132c66f81b1f2cfc8bef544a3b5c623ae7398960226b82ad9` |
| [src/main/java/fr/cliker/clikerv2/bag/BagManager.java](../src/main/java/fr/cliker/clikerv2/bag/BagManager.java) | `27ec68e5a84e25834613f6da62d7e9915ab3d36084431879080c5d4a45e0fd21` |
| [src/main/java/fr/cliker/clikerv2/bag/BagMenu.java](../src/main/java/fr/cliker/clikerv2/bag/BagMenu.java) | `c23db05b12e0111a61efba82372e46ffed2153c20927751899004f96489b6bdd` |
| [src/main/java/fr/cliker/clikerv2/bag/BagOrganizer.java](../src/main/java/fr/cliker/clikerv2/bag/BagOrganizer.java) | `b9d16970b0c9b11d8e41df3e156377c6f70533e43ae39d75cd0ff1a2bde098cb` |
| [src/main/java/fr/cliker/clikerv2/bag/BagStore.java](../src/main/java/fr/cliker/clikerv2/bag/BagStore.java) | `ccacb253e2aa052215ccb7c3bc647b0e5b7995917ac82480feebb56b03211976` |
| [src/main/java/fr/cliker/clikerv2/bag/TalismanCommand.java](../src/main/java/fr/cliker/clikerv2/bag/TalismanCommand.java) | `e28ee6f76f384d735ff24ed4e126e8291c4116e5a1491d137dbc80365d7e4ca2` |
| [src/main/java/fr/cliker/clikerv2/bag/TalismanItem.java](../src/main/java/fr/cliker/clikerv2/bag/TalismanItem.java) | `05f90eb73fba5dcf3f5fa1509673c6876b73859e5728baff70c17ca8b796b40a` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZone.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZone.java) | `22550ae14d4733cfb089ff1d7d5f8fff35bbbcc2a0b02f1bbd85d3324fd72d50` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneModule.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneModule.java) | `9b52ec29b2bfd4bfabd5f5d3a74110394d80f713de09033b4321db7ff9c84183` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneStore.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneStore.java) | `83ab3b1e10e8970ad1f24234ee0abc62b825e4638594d7d00da2100443f97c01` |
| [src/main/java/fr/cliker/clikerv2/combat/AutoClickManager.java](../src/main/java/fr/cliker/clikerv2/combat/AutoClickManager.java) | `f273bbdfb6a800523f08857b2f60e73b529b2ef3f5aa89ee2abd2b8d10e627c3` |
| [src/main/java/fr/cliker/clikerv2/combat/CombatReach.java](../src/main/java/fr/cliker/clikerv2/combat/CombatReach.java) | `421067510c05151911ad1a4b3b8e1c943e07df71b3bb52d7ee34029e243a91f2` |
| [src/main/java/fr/cliker/clikerv2/combat/DamagePopups.java](../src/main/java/fr/cliker/clikerv2/combat/DamagePopups.java) | `b3e604911202d31c025b56a715f4f97fd3abe5eabce416b599f7aaa7dc995e3e` |
| [src/main/java/fr/cliker/clikerv2/combat/DeathLogFilter.java](../src/main/java/fr/cliker/clikerv2/combat/DeathLogFilter.java) | `225281aa212c41d0ef98f79509d3a5df5a6d48b0bd1238d3a0b06307cd32edd8` |
| [src/main/java/fr/cliker/clikerv2/combat/StarterSwordListener.java](../src/main/java/fr/cliker/clikerv2/combat/StarterSwordListener.java) | `eebd5c9bf7eedf5c8b3b7f2dcd32f6699c260b98f7f15cc2ccefde6aefa1a78e` |
| [src/main/java/fr/cliker/clikerv2/combat/SwordCommand.java](../src/main/java/fr/cliker/clikerv2/combat/SwordCommand.java) | `d63002ae28f6f5a634cccd07ca8361cb0dd4129195420ac4fc60338c388a7632` |
| [src/main/java/fr/cliker/clikerv2/comfort/FarmTelemetry.java](../src/main/java/fr/cliker/clikerv2/comfort/FarmTelemetry.java) | `b1ff6dd419205044e143ee65e6b94ad24c8343afe0dfeae298229da46215674f` |
| [src/main/java/fr/cliker/clikerv2/comfort/PlayerComfort.java](../src/main/java/fr/cliker/clikerv2/comfort/PlayerComfort.java) | `c2e639e44d381042e8997418adb9a5cc9ce0444592a5b96b6ed2c7ad13d9a9ad` |
| [src/main/java/fr/cliker/clikerv2/comfort/ProgressNavigation.java](../src/main/java/fr/cliker/clikerv2/comfort/ProgressNavigation.java) | `f886e7e4288b0c4c30c6ad1323a62619a2413bf73026282d9d0c30f82857afa4` |
| [src/main/java/fr/cliker/clikerv2/comfort/SessionConvenience.java](../src/main/java/fr/cliker/clikerv2/comfort/SessionConvenience.java) | `e87c87b3f2df1e6937c8e4aa41fe00a9251a6bd4dd5bf50058b30e0e41ce0858` |
| [src/main/java/fr/cliker/clikerv2/comfort/UpgradeGuide.java](../src/main/java/fr/cliker/clikerv2/comfort/UpgradeGuide.java) | `d18e29334d1d4aaa0fd531ed3efe81ce3e8aee0abac8381a5b679b4dfa471f0b` |
| [src/main/java/fr/cliker/clikerv2/config/ConfigFiles.java](../src/main/java/fr/cliker/clikerv2/config/ConfigFiles.java) | `5b3b50311b74006dca9dcc019bfd84592f2f0f74de3315d4e3ce823806d63020` |
| [src/main/java/fr/cliker/clikerv2/config/StoredLocation.java](../src/main/java/fr/cliker/clikerv2/config/StoredLocation.java) | `49336cc9cf11bbca0c01d34b81c1b725bd047510edfe59e15eb70cc62e1bbea0` |
| [src/main/java/fr/cliker/clikerv2/config/YamlFiles.java](../src/main/java/fr/cliker/clikerv2/config/YamlFiles.java) | `d337184d373c48f9d9470041e8f538a93d0d220aa0fc9984ffe24a97dd13ad20` |
| [src/main/java/fr/cliker/clikerv2/cooldown/CooldownCommand.java](../src/main/java/fr/cliker/clikerv2/cooldown/CooldownCommand.java) | `6dcf2122a5537f4854cf0a8963e4f6c8ebea7d24e7ee5d367f6d78262c898b09` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyMenu.java](../src/main/java/fr/cliker/clikerv2/daily/DailyMenu.java) | `34cadda89905301f86c80b654c84be3934df5a2af588b5de17b2b32c4ff60082` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyModule.java](../src/main/java/fr/cliker/clikerv2/daily/DailyModule.java) | `7a4247ae4d141e90d8dbe10b0ab2974b47957c979788ea9f3e657884f63427c0` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyNpc.java](../src/main/java/fr/cliker/clikerv2/daily/DailyNpc.java) | `84905fda36e6b4cce8f0a8d801617ede984f7a1684661007ab16c53a443270ec` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyProgress.java](../src/main/java/fr/cliker/clikerv2/daily/DailyProgress.java) | `b72d18c08c9f42b53551e7bb0be67ac6a68ccfdb61ebb25c9ccf01e61fba5014` |
| [src/main/java/fr/cliker/clikerv2/daily/DailySettings.java](../src/main/java/fr/cliker/clikerv2/daily/DailySettings.java) | `083df67a96f6012299872ca8868711f6f2206e81b2afaa74a0631413dfd79e08` |
| [src/main/java/fr/cliker/clikerv2/daily/RewardStore.java](../src/main/java/fr/cliker/clikerv2/daily/RewardStore.java) | `5f74f798fcb937f95846ce6b0237378755b2369d698abdd205833a06d1826c48` |
| [src/main/java/fr/cliker/clikerv2/discord/DiscordModule.java](../src/main/java/fr/cliker/clikerv2/discord/DiscordModule.java) | `ef929784325c563d44fbb4b6847671bd1d65585a93033ef43eaaa88103add923` |
| [src/main/java/fr/cliker/clikerv2/display/MoneyScoreboard.java](../src/main/java/fr/cliker/clikerv2/display/MoneyScoreboard.java) | `4cb12ece245bbc8cfe5dc8a40f925174d11dce8aab650b0e8a0153f4b360ee01` |
| [src/main/java/fr/cliker/clikerv2/display/RankPrefix.java](../src/main/java/fr/cliker/clikerv2/display/RankPrefix.java) | `81d982ee2950f26b05460700f05c2684d70fb3f518916848cf5dfe5009e27def` |
| [src/main/java/fr/cliker/clikerv2/display/StatsCommand.java](../src/main/java/fr/cliker/clikerv2/display/StatsCommand.java) | `09eeabb3b6856989f599df4dddf6dcb11228a48cc0e1c562ddea73745b3b79e3` |
| [src/main/java/fr/cliker/clikerv2/display/StatsMenu.java](../src/main/java/fr/cliker/clikerv2/display/StatsMenu.java) | `0a0261a688058fdaaee1d73aab6812cb8aa981fa4055e783769c3337cba98668` |
| [src/main/java/fr/cliker/clikerv2/display/TabListManager.java](../src/main/java/fr/cliker/clikerv2/display/TabListManager.java) | `8a2eec35e1c74bfbe880868ff0baa12b99293ce5cbbfc6dbe12d4bee6e065b83` |
| [src/main/java/fr/cliker/clikerv2/economy/CurrencyCommand.java](../src/main/java/fr/cliker/clikerv2/economy/CurrencyCommand.java) | `466e4882862063729a3e2b6d6685414aca80f6411987fc39af9c77f7bdc40813` |
| [src/main/java/fr/cliker/clikerv2/economy/CurrencyType.java](../src/main/java/fr/cliker/clikerv2/economy/CurrencyType.java) | `4ae5325c32f2178a98590e0faeef6d681bc11652ca5031ea2cd78548da3933fb` |
| [src/main/java/fr/cliker/clikerv2/economy/ExpCommand.java](../src/main/java/fr/cliker/clikerv2/economy/ExpCommand.java) | `fe3eca451e383fa9c09fe5de7c576f0fa7b45b0fb9fe2bc31a4e343dc91acf03` |
| [src/main/java/fr/cliker/clikerv2/economy/PermissionBonus.java](../src/main/java/fr/cliker/clikerv2/economy/PermissionBonus.java) | `40a8403076e394342946f7c530ccee5fcec5212d9c0c19a1fdcd6e61f914e759` |
| [src/main/java/fr/cliker/clikerv2/essence/CompressorCommand.java](../src/main/java/fr/cliker/clikerv2/essence/CompressorCommand.java) | `2902d41383652ec54c63c2693f9e10a1ffd626a8a31ed48244874207d94dfeb6` |
| [src/main/java/fr/cliker/clikerv2/essence/CompressorItem.java](../src/main/java/fr/cliker/clikerv2/essence/CompressorItem.java) | `cf573a5afe46c8211bbeb372de268b493fbe461445f0d5a2f1e47d97f4aad6b9` |
| [src/main/java/fr/cliker/clikerv2/essence/CompressorManager.java](../src/main/java/fr/cliker/clikerv2/essence/CompressorManager.java) | `fae33857a40a21181c77466706497a0bc6640073e1f3436c38b202e0cb8900c4` |
| [src/main/java/fr/cliker/clikerv2/essence/EssenceCompression.java](../src/main/java/fr/cliker/clikerv2/essence/EssenceCompression.java) | `4ff525c586ae125f4ac1a967d78563a9ba409d7368f6a736265c2909d7e8acb1` |
| [src/main/java/fr/cliker/clikerv2/essence/EssenceItem.java](../src/main/java/fr/cliker/clikerv2/essence/EssenceItem.java) | `193a4e826dd87d8cc0da1c826c578ef3347a67352c2618acc85f780eef766593` |
| [src/main/java/fr/cliker/clikerv2/essence/EssenceRarity.java](../src/main/java/fr/cliker/clikerv2/essence/EssenceRarity.java) | `0c9b5d7d402b5b7c7c4eba2c2f75e2fa9135d343078b6f3393b6444f45873989` |
| [src/main/java/fr/cliker/clikerv2/essence/SellCommand.java](../src/main/java/fr/cliker/clikerv2/essence/SellCommand.java) | `ec5690f15eb461e3f65da5d464fd3c5d812d990cef74a4c11feed1bb4dc1c26b` |
| [src/main/java/fr/cliker/clikerv2/essence/SellMenu.java](../src/main/java/fr/cliker/clikerv2/essence/SellMenu.java) | `b421dfaa733d49c40e5a26adc514fe8cc52693c7dd48a15b3dc1b2e8e7930a23` |
| [src/main/java/fr/cliker/clikerv2/features/AdminDiagnostic.java](../src/main/java/fr/cliker/clikerv2/features/AdminDiagnostic.java) | `d4cd3da96abee18606726412c35974144e76d1d6dfbbc89744e560770502cbd1` |
| [src/main/java/fr/cliker/clikerv2/features/Boosts.java](../src/main/java/fr/cliker/clikerv2/features/Boosts.java) | `19929624ea83ccedda53202873cb3c219c05d0e8fa1bf5fdfd37f11ed4ac9db7` |
| [src/main/java/fr/cliker/clikerv2/features/ChatActivities.java](../src/main/java/fr/cliker/clikerv2/features/ChatActivities.java) | `f52e219e770971a23ecc19f2e13372daa735e97b527eff9db1dd98de267cfd95` |
| [src/main/java/fr/cliker/clikerv2/features/CreatureSettings.java](../src/main/java/fr/cliker/clikerv2/features/CreatureSettings.java) | `07e59c7af0854ba724b081fc0bd3d64b093da4008581da99bbdb92192f9dce15` |
| [src/main/java/fr/cliker/clikerv2/features/ExpansionModule.java](../src/main/java/fr/cliker/clikerv2/features/ExpansionModule.java) | `2370ace995680e3b2313ecf8f19877e87b0b52ae779d1d9f6b59fe8bcb46105f` |
| [src/main/java/fr/cliker/clikerv2/features/HuntKillEvent.java](../src/main/java/fr/cliker/clikerv2/features/HuntKillEvent.java) | `600a2a06738091ec967d8954fbfd11c4ba6ce238311977a3e32290e493d11456` |
| [src/main/java/fr/cliker/clikerv2/features/Menus.java](../src/main/java/fr/cliker/clikerv2/features/Menus.java) | `249927273e348f0196f43a768514765b51aeffe62b5fad607a3f56765b0aea56` |
| [src/main/java/fr/cliker/clikerv2/features/PartyRaids.java](../src/main/java/fr/cliker/clikerv2/features/PartyRaids.java) | `bd2753002f1de92b55984c17eee206a29ea7cca63256be47b5690ce5d4e5b3fb` |
| [src/main/java/fr/cliker/clikerv2/features/PlainGear.java](../src/main/java/fr/cliker/clikerv2/features/PlainGear.java) | `a5814544697550f6b7c309d6a9100c6d898f42a92a449545cd88bda6b897fbf0` |
| [src/main/java/fr/cliker/clikerv2/features/ServiceNpcs.java](../src/main/java/fr/cliker/clikerv2/features/ServiceNpcs.java) | `0b9bd3102b32f9f04721b83b9faa625ee2fa6327bba5aaa0c48968094dd0c5f0` |
| [src/main/java/fr/cliker/clikerv2/features/Settings.java](../src/main/java/fr/cliker/clikerv2/features/Settings.java) | `a8754ba8aff79e12a8a15f3084ed1668c25474fc49b6bdc6495fc6db10dd0181` |
| [src/main/java/fr/cliker/clikerv2/features/ShopStore.java](../src/main/java/fr/cliker/clikerv2/features/ShopStore.java) | `11c0dcd98121a2f70c6b91ea4dc75da3e7a7f7bf1d70ba08ae9886c22be5a962` |
| [src/main/java/fr/cliker/clikerv2/features/Talismans.java](../src/main/java/fr/cliker/clikerv2/features/Talismans.java) | `a1ff6c4cb81eff28b64969f3a917694480eef1a85ca16046aa0682df89fed37d` |
| [src/main/java/fr/cliker/clikerv2/features/TravelAndTools.java](../src/main/java/fr/cliker/clikerv2/features/TravelAndTools.java) | `2f3978c738c0b8fb3ea89f788988ffdd92c22b7ac9d71c4b677d8375d67f7064` |
| [src/main/java/fr/cliker/clikerv2/gear/CharmCommand.java](../src/main/java/fr/cliker/clikerv2/gear/CharmCommand.java) | `2ce298d78c256ed4670284c82b27b8c3d6c0ab4561b9f0c36b5a4016a23272e7` |
| [src/main/java/fr/cliker/clikerv2/gear/CharmItem.java](../src/main/java/fr/cliker/clikerv2/gear/CharmItem.java) | `7285abcf7012a3366067624cb00bc78f7fca84c41e0395f7560ffb4c77a72b65` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantCommand.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantCommand.java) | `1c16c8380f92cba067648fb87d2ba2d7d8253f07d67d18036a45bd7884f8b531` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantMenu.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantMenu.java) | `e463ac02b409cd87dd7f9def488f83bd213e3235055216a90b9f489d09495025` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantRoll.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantRoll.java) | `3c7b9992e336888ebe0e3126336b0f17c70dc909ab0e25ae7980d4e5fb6eacee` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantType.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantType.java) | `fc4ea1efb1f55ca7a17527d60cc807736b297f3f14ba2771dd14abc10ffa251f` |
| [src/main/java/fr/cliker/clikerv2/gear/GearAttributeManager.java](../src/main/java/fr/cliker/clikerv2/gear/GearAttributeManager.java) | `4b520765cd91dd891bbb6a3955f972e08d35d4e928f668d8c04157d7653c9798` |
| [src/main/java/fr/cliker/clikerv2/gear/GearGuide.java](../src/main/java/fr/cliker/clikerv2/gear/GearGuide.java) | `c412c1c76d9b73255ed011154582cc23f15f564bdbfc6554ceddb65f427388a0` |
| [src/main/java/fr/cliker/clikerv2/gear/GearLore.java](../src/main/java/fr/cliker/clikerv2/gear/GearLore.java) | `9d70b4521673e4925e99e477882eaa55619637192b232f84dbb88fcb4ac0bfe7` |
| [src/main/java/fr/cliker/clikerv2/gear/GearTransactions.java](../src/main/java/fr/cliker/clikerv2/gear/GearTransactions.java) | `21776dc5a012d9ef3f62e66db07f4860a93807c1720ca2ade37598d2113d3b92` |
| [src/main/java/fr/cliker/clikerv2/gear/ItemMods.java](../src/main/java/fr/cliker/clikerv2/gear/ItemMods.java) | `0d61a1d00779e56120b6af9358bdb2acd9d069abf4fd831adff75765e28c4d64` |
| [src/main/java/fr/cliker/clikerv2/gear/ReforgeCommand.java](../src/main/java/fr/cliker/clikerv2/gear/ReforgeCommand.java) | `e1fc8bfc977c57f76776894678341b38a353dcc6eb272b61ce376d14da4fe45a` |
| [src/main/java/fr/cliker/clikerv2/gear/ReforgeMenu.java](../src/main/java/fr/cliker/clikerv2/gear/ReforgeMenu.java) | `2b7b095f471d8c14b4d4d2a9b48bdaa612a9e25434583a71f71a62fa25684474` |
| [src/main/java/fr/cliker/clikerv2/gear/ReforgeType.java](../src/main/java/fr/cliker/clikerv2/gear/ReforgeType.java) | `c102bae7dff50dc6e207b919e9156b4e21263245915d668121dad0a675c89063` |
| [src/main/java/fr/cliker/clikerv2/gear/RuneCommand.java](../src/main/java/fr/cliker/clikerv2/gear/RuneCommand.java) | `a15bfa858844f00c1c3f9bdf27586b3a8e1c74befee6121271a39120aec6b605` |
| [src/main/java/fr/cliker/clikerv2/gear/RuneMenu.java](../src/main/java/fr/cliker/clikerv2/gear/RuneMenu.java) | `87754d30a9a5c6e7f8a05d280dab713d98f9f2f9fce4a332a4e7e8c7c3c9f76a` |
| [src/main/java/fr/cliker/clikerv2/gear/RuneType.java](../src/main/java/fr/cliker/clikerv2/gear/RuneType.java) | `06262786984152088e48cc07c9f80454f3efa195139124b93599907da71a80ae` |
| [src/main/java/fr/cliker/clikerv2/gear/TierGear.java](../src/main/java/fr/cliker/clikerv2/gear/TierGear.java) | `0dce34e7baa11ee2047a9df1c6e8afde1014c67e2c644e684fe24b66eb1f4e50` |
| [src/main/java/fr/cliker/clikerv2/gear/TierGearCommand.java](../src/main/java/fr/cliker/clikerv2/gear/TierGearCommand.java) | `a42f067d3958d7488c04db69e9db22bacfd19fcb8818b5b58c8fc7d4c5444634` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramCommand.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramCommand.java) | `7213961934693ae3f76c67b86a8c00ca4ca39d66c3ca5e32f0c6f98fd545b503` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramManager.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramManager.java) | `bc5c13ee5ad705b37070fadeb285c148ca07ab1aacb495acba9436b88304ecb7` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramStore.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramStore.java) | `6fdf300503be19b637e982589656c2b1151268cef34d5470029800f68deceb3c` |
| [src/main/java/fr/cliker/clikerv2/hologram/SharedHolograms.java](../src/main/java/fr/cliker/clikerv2/hologram/SharedHolograms.java) | `212210faaf9b54851c71f8828af50882c359ec0eb4c784bf03e7b6f3572a2604` |
| [src/main/java/fr/cliker/clikerv2/hunting/ContractStore.java](../src/main/java/fr/cliker/clikerv2/hunting/ContractStore.java) | `1b6b5643cfb5a1014444180215a211ceab137326e1e7ebc7aa1104d8b092d273` |
| [src/main/java/fr/cliker/clikerv2/hunting/Gains.java](../src/main/java/fr/cliker/clikerv2/hunting/Gains.java) | `71c26a760810e6d32f85ae41d51e848c7d9389f910ce79e03ee9e80c1dc51869` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntUI.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntUI.java) | `0c773605d9cf80f58d8f22eaf2ecf7afb6142d2fa92538eaf1ca02fc31f0cde2` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntingModule.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntingModule.java) | `d83898747f2ffe903a09aa3e46d60ccc4221374da0de457be29448b25ad77074` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntingStore.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntingStore.java) | `a18a20a1baaee4285b8bc8cfbec550bfa3c89652647182b9054eda4cccb45254` |
| [src/main/java/fr/cliker/clikerv2/hunting/RewardLedger.java](../src/main/java/fr/cliker/clikerv2/hunting/RewardLedger.java) | `c870722463ab79ca31c7aec5e87c9055e5987d267c8b475f42bae3a2d0684fc0` |
| [src/main/java/fr/cliker/clikerv2/level/LevelDamageBonus.java](../src/main/java/fr/cliker/clikerv2/level/LevelDamageBonus.java) | `d69a167933b5853ce570100ccdca5b13e90e0d8e20744a0e59714f68f1a0f081` |
| [src/main/java/fr/cliker/clikerv2/level/LevelUpListener.java](../src/main/java/fr/cliker/clikerv2/level/LevelUpListener.java) | `feb8cbe2d8ec1a7be446a23c775e4ee4cacea14ac84f82cca68b8ceafcd61b5c` |
| [src/main/java/fr/cliker/clikerv2/level/PlayerLevelStore.java](../src/main/java/fr/cliker/clikerv2/level/PlayerLevelStore.java) | `004bfbe457c56db3c79af03cc190fe1e628cde7671e2456888f9c7a73315b48e` |
| [src/main/java/fr/cliker/clikerv2/liveauction/AuctionRoom.java](../src/main/java/fr/cliker/clikerv2/liveauction/AuctionRoom.java) | `109a17446b77a1df92a8234e95cb304bf2e903602d3559503f9ff798d7991a02` |
| [src/main/java/fr/cliker/clikerv2/liveauction/BidPrices.java](../src/main/java/fr/cliker/clikerv2/liveauction/BidPrices.java) | `cd0fc33897a00c7feac863485fe86e277841e2448e5930aebc5fc732e3caf3f3` |
| [src/main/java/fr/cliker/clikerv2/liveauction/LiveAuctionStore.java](../src/main/java/fr/cliker/clikerv2/liveauction/LiveAuctionStore.java) | `2beaf89d0f4aec78f3a9edda9d665447d7c3b4fec84d67cfa0310e030582e848` |
| [src/main/java/fr/cliker/clikerv2/liveauction/LiveAuctions.java](../src/main/java/fr/cliker/clikerv2/liveauction/LiveAuctions.java) | `61ddc6793fd0914326c7f3d5396b98caedc9b38087b0851b929b4df54b5a8ac4` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxCommand.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxCommand.java) | `e33e19a283b3ea5e4537849466fe3dbb5f0a27b78055b28210808851656ff8a3` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxItem.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxItem.java) | `b1c28f85524d0da279acc92845c477f464793f56b87671822c82e9811b7ee64e` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxManager.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxManager.java) | `4e2c00cc0f02106b946dece8340dccfbec8b0719e00084675bb4741f0fb25bbd` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxStore.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxStore.java) | `208f50db22e5a5e54309e27818820d680d13c7780ef1fe535d639da8330e0041` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxTier.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxTier.java) | `a638051a76ab234d16d6ffc1577daf8203c61954b09f8c413cca229794fb7e40` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantCommand.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantCommand.java) | `165056084e520643917ec2247677b0655099893c63678523de376b37f199be87` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantManager.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantManager.java) | `0ee42938223618ebdea4cff4a26847a88a654bf222b2e339bb071a111f6817a8` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantMenu.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantMenu.java) | `7d111afb13a33b52e9f1357ee560857736a37bbac102395256e7a7c1d6a77a9a` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantStore.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantStore.java) | `5962c2ba365494b984c3202e98f8d8143eba814e649458a6c1b624abd1f55ba0` |
| [src/main/java/fr/cliker/clikerv2/mobs/BossEventManager.java](../src/main/java/fr/cliker/clikerv2/mobs/BossEventManager.java) | `d314ace3e9c69c7c501002a9701b035f66b83748df4a23e8ce4d36415fbe3cd3` |
| [src/main/java/fr/cliker/clikerv2/mobs/CustomMobManager.java](../src/main/java/fr/cliker/clikerv2/mobs/CustomMobManager.java) | `aee1753792a8df5daca00ed8371ed20536eec69a24ec95b3f9b0d4f0e4eb89e8` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobKillStore.java](../src/main/java/fr/cliker/clikerv2/mobs/MobKillStore.java) | `5427af864d7ec23efb7d921db4414fc20cb9708048198d4627fcd56f894a923d` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobKillsCommand.java](../src/main/java/fr/cliker/clikerv2/mobs/MobKillsCommand.java) | `f9ad013bfafd3e103ab1ab91b58d1eb1555ec4563fb02d51e7f56ddc8160c6d0` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobSpawnCommand.java](../src/main/java/fr/cliker/clikerv2/mobs/MobSpawnCommand.java) | `3f251a566166e05eacaa553d3d7d3f913f7777f7f1c963dcc79677f62d0964a4` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobSpawnStore.java](../src/main/java/fr/cliker/clikerv2/mobs/MobSpawnStore.java) | `dd52dea6b5d26441f35c17f0b782c06e9177ece897f91b87962b9374d7fc08ca` |
| [src/main/java/fr/cliker/clikerv2/mobs/VanillaSpawnListener.java](../src/main/java/fr/cliker/clikerv2/mobs/VanillaSpawnListener.java) | `618899ace54336ecb13d5def2f4d43309cf4c2070918f6e698bae1f1aa7d33ac` |
| [src/main/java/fr/cliker/clikerv2/npc/ZnpcsNpc.java](../src/main/java/fr/cliker/clikerv2/npc/ZnpcsNpc.java) | `5ba6c122c75daeb68e61ec892fbf569e78075286e6becec10af86b1eebdbbde2` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostBlocks.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostBlocks.java) | `f2a6d80f4b96d579d87132cd6c7ca2376cfcc13a1c2f6fe01e866f6a7342ec95` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostModule.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostModule.java) | `933364141a47f24648670835976c2db64b3e561ccaf18b3abda348dbb6182617` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostStore.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostStore.java) | `8eb61b5674857a60756fc1679e0f33b4af46b23195907330ec30b291148a5bb8` |
| [src/main/java/fr/cliker/clikerv2/outpost/WorldInventories.java](../src/main/java/fr/cliker/clikerv2/outpost/WorldInventories.java) | `f685fbc45b25fd6a9d48b36a9bf5544c3b2e2bcb61008a7ff486a9321833b8d4` |
| [src/main/java/fr/cliker/clikerv2/pinata/PinataModule.java](../src/main/java/fr/cliker/clikerv2/pinata/PinataModule.java) | `9317dd33a14ebccf2f8f846503e75a2e3829eb7170fc714ee4235595cf66af1e` |
| [src/main/java/fr/cliker/clikerv2/pinata/PinataStore.java](../src/main/java/fr/cliker/clikerv2/pinata/PinataStore.java) | `8abb949ea5aa36b9287a81f8fe4ae38fa46369be41dd51400f297a66936d4ebf` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeChatFormatter.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeChatFormatter.java) | `7a5a915937cf6ac19ded0c4e886aabed1dd2dd2985bdc7dee810954104dd00c3` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeCommand.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeCommand.java) | `0c3859ef9f620fc2bf32f8758fe06faf0acd11fe13a5389ef6bfd0dad400d33d` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeManager.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeManager.java) | `dc6b99ee2367e653c3ecc038363545ebcac83093aef28f53b7c023e04a9a2b10` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeMenu.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeMenu.java) | `1abe9bab813485ee7712b89edaba7208b4214bc2dd7284c10f68b4c794d931fa` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeStore.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeStore.java) | `3553d1557315f65e31bd4d074fab8b028c76763a478b8a5cdd345f66dccd4546` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeType.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeType.java) | `40dc56f11f2203bee8e0eb0afb39e2ad3ab51026c4bfdb4b2cb2704ae1651609` |
| [src/main/java/fr/cliker/clikerv2/raid/ProportionalRewards.java](../src/main/java/fr/cliker/clikerv2/raid/ProportionalRewards.java) | `bc7e8702ed54d925827c14c2e61b89e0057a0de885656c80d2d73ffa33fd819c` |
| [src/main/java/fr/cliker/clikerv2/raid/RaidModule.java](../src/main/java/fr/cliker/clikerv2/raid/RaidModule.java) | `3825847e90ab3d37da2a2bca5f15c4a24900d462db4f3e3d1420e6238cd76b74` |
| [src/main/java/fr/cliker/clikerv2/referral/ReferralModule.java](../src/main/java/fr/cliker/clikerv2/referral/ReferralModule.java) | `088a78f8cffa45ad7c43a8a3af32bda634abaaf55f380f6204808c21df598680` |
| [src/main/java/fr/cliker/clikerv2/referral/ReferralStore.java](../src/main/java/fr/cliker/clikerv2/referral/ReferralStore.java) | `d0a4a6330c10d0dde7b97d25a1a5e7db918a7a79d35c692ec0e4d5ac531d1ba6` |
| [src/main/java/fr/cliker/clikerv2/rewards/ClaimService.java](../src/main/java/fr/cliker/clikerv2/rewards/ClaimService.java) | `ba6f61d531f7ad03df0f263169f09d1614130d07811b0ec4aca8cbfb997733b7` |
| [src/main/java/fr/cliker/clikerv2/rewards/ItemDelivery.java](../src/main/java/fr/cliker/clikerv2/rewards/ItemDelivery.java) | `83996cb7a55d25f6b0cfbadf0031a1f835f86275c526260d83bb245c8de8f1bc` |
| [src/main/java/fr/cliker/clikerv2/rewards/MailStore.java](../src/main/java/fr/cliker/clikerv2/rewards/MailStore.java) | `4e1b8f4fdd6b53fa203b8ad60bbd3ea0b3dfa028abce909d2c7b3b6cb89402b9` |
| [src/main/java/fr/cliker/clikerv2/rewards/OutgoingItems.java](../src/main/java/fr/cliker/clikerv2/rewards/OutgoingItems.java) | `7ac58b27e5dbcefe444985c46d6fc87d32de4eca7b63aad60fa98912a1dd3e93` |
| [src/main/java/fr/cliker/clikerv2/rewards/OutgoingStore.java](../src/main/java/fr/cliker/clikerv2/rewards/OutgoingStore.java) | `0b5db1a98fcfad7b3e60ac2955fe992fca8e6308f2c1b31cde81b4335c6af398` |
| [src/main/java/fr/cliker/clikerv2/runtime/PlayerSessions.java](../src/main/java/fr/cliker/clikerv2/runtime/PlayerSessions.java) | `f721d9ed994c54140f39c2220d68fd7fcc3c75632981ea6be6fb36898f5c1f2a` |
| [src/main/java/fr/cliker/clikerv2/runtime/ServerTasks.java](../src/main/java/fr/cliker/clikerv2/runtime/ServerTasks.java) | `d77bd0f9ea6fcee886e7475b4825edba129676dcaa54171040dac6ebae0e475b` |
| [src/main/java/fr/cliker/clikerv2/security/SystemEggProtection.java](../src/main/java/fr/cliker/clikerv2/security/SystemEggProtection.java) | `5b79b638f0e1363fbd95daef4dee0b11e17edde4dd523dc5c0de881fade4296d` |
| [src/main/java/fr/cliker/clikerv2/social/CommunityExtras.java](../src/main/java/fr/cliker/clikerv2/social/CommunityExtras.java) | `1a50a494d2d524b1d0a395128df0662f8d3c8f0d88106580228e755fbaa0c49a` |
| [src/main/java/fr/cliker/clikerv2/social/CommunityStore.java](../src/main/java/fr/cliker/clikerv2/social/CommunityStore.java) | `1b3599e2eb1bd4f4b4e9ade8bc4c91427a62b092708aa165d3588c9d2457318c` |
| [src/main/java/fr/cliker/clikerv2/social/SocialModule.java](../src/main/java/fr/cliker/clikerv2/social/SocialModule.java) | `78622c249a20e050df8d9a518dbf83c7cf610f1575a6e3c4ce07d7c9ddc21e4e` |
| [src/main/java/fr/cliker/clikerv2/social/SocialSettings.java](../src/main/java/fr/cliker/clikerv2/social/SocialSettings.java) | `88fcdf11cadbb86f3652480c819f06a71473c808fe0a1c40a67e9c9ea70402cf` |
| [src/main/java/fr/cliker/clikerv2/social/SocialStore.java](../src/main/java/fr/cliker/clikerv2/social/SocialStore.java) | `e4b1eeb1637f593594f083e2687e70819b853e95dedb4c27ecb153df31d31d39` |
| [src/main/java/fr/cliker/clikerv2/spawn/RespawnListener.java](../src/main/java/fr/cliker/clikerv2/spawn/RespawnListener.java) | `cc72e0c5ba1f4d03c1df8022178aa08d0b4cda60358111b2e40be7e19f0a9212` |
| [src/main/java/fr/cliker/clikerv2/spawn/SetSpawnCommand.java](../src/main/java/fr/cliker/clikerv2/spawn/SetSpawnCommand.java) | `f498ee0108be97529a6366f9661018a30df09d104d05c03ca8d601ddbb2d4a3c` |
| [src/main/java/fr/cliker/clikerv2/spawn/SpawnCommand.java](../src/main/java/fr/cliker/clikerv2/spawn/SpawnCommand.java) | `699a59a6731928bfc2806a00d90d877dc091707bca6f86cab1d4baef1b861451` |
| [src/main/java/fr/cliker/clikerv2/spawn/SpawnManager.java](../src/main/java/fr/cliker/clikerv2/spawn/SpawnManager.java) | `f27a61e80bb968eea4a3664971a4c829dcb04ca86c17b09c7c5e6f5c073c1d48` |
| [src/main/java/fr/cliker/clikerv2/spawn/SpawnStore.java](../src/main/java/fr/cliker/clikerv2/spawn/SpawnStore.java) | `eac66bee6d1246cedd600d616517178ffaa695f8b8240f317b93c143884495cf` |
| [src/main/java/fr/cliker/clikerv2/station/StationCommand.java](../src/main/java/fr/cliker/clikerv2/station/StationCommand.java) | `8b0641cb8ef82d9d2ce8da24e8b425352192bad372f3cc91f5d2776a7bd7cab7` |
| [src/main/java/fr/cliker/clikerv2/station/StationManager.java](../src/main/java/fr/cliker/clikerv2/station/StationManager.java) | `9e2327451ab492eaf767a80f2c48c7390105b6134dbb9cf2f401e2e75a388082` |
| [src/main/java/fr/cliker/clikerv2/station/StationStore.java](../src/main/java/fr/cliker/clikerv2/station/StationStore.java) | `6d7984d66742431fe092b79f451a68d1b1d15f4b767e4d1ff0f59aefe65e858b` |
| [src/main/java/fr/cliker/clikerv2/station/StationType.java](../src/main/java/fr/cliker/clikerv2/station/StationType.java) | `238dca1c1a39675b86d4c4a16f2234f135a90fe1df10e958642203a096721c2b` |
| [src/main/java/fr/cliker/clikerv2/tuto/TutoBook.java](../src/main/java/fr/cliker/clikerv2/tuto/TutoBook.java) | `061cc57d74a3ac3cf71a012263a9f420633be67ed610e76fd26abd22052e2ac2` |
| [src/main/java/fr/cliker/clikerv2/tuto/TutoCommand.java](../src/main/java/fr/cliker/clikerv2/tuto/TutoCommand.java) | `0af182925a279ee0cb7885b125e250ec6b29e5d9f340188616714b930688ef85` |
| [src/main/java/fr/cliker/clikerv2/tuto/TutoJoinListener.java](../src/main/java/fr/cliker/clikerv2/tuto/TutoJoinListener.java) | `fcaf63e4fb76f0e8b0f1a90b29a212ed660afbfb58942e8f9e8dc6d56eec6e10` |
| [src/main/java/fr/cliker/clikerv2/ui/GainBar.java](../src/main/java/fr/cliker/clikerv2/ui/GainBar.java) | `d35fa63373256c5761a844774f439e3db34b18bb9645e6a4b06a12a9020e11d1` |
| [src/main/java/fr/cliker/clikerv2/ui/MainMenu.java](../src/main/java/fr/cliker/clikerv2/ui/MainMenu.java) | `ada7a7f289c1ba63d9f33e38fb4181aa41d747385144b4ed53c1a7517a30745a` |
| [src/main/java/fr/cliker/clikerv2/ui/MenuIcons.java](../src/main/java/fr/cliker/clikerv2/ui/MenuIcons.java) | `a882ae358debdbe7d1daf7a2bcbe0cc548cd3a0d910dcd35e15fd23995a95f1a` |
| [src/main/java/fr/cliker/clikerv2/ui/MenuNavigation.java](../src/main/java/fr/cliker/clikerv2/ui/MenuNavigation.java) | `5e95103a79347efeda8a6e06829b9312682135cb574707d8355a7765a7f999df` |
| [src/main/java/fr/cliker/clikerv2/ui/ViewRequests.java](../src/main/java/fr/cliker/clikerv2/ui/ViewRequests.java) | `0e4eff064c662350d17b8347362f63db442cf9f219a081a4ab0ff926b87bdd28` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeCommand.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeCommand.java) | `4c89c6bf39cc2fd688deb507878891e668b53b1d8d2faecc093e5039387fcd28` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeManager.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeManager.java) | `89410b7bcabe87a4cf467dec763c89ee892dd0f1b846337d5869fd00817a36c7` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeMenu.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeMenu.java) | `4114f5f450b5cf67e30fc0c7978e7a6a7bcb79b052630b1fb281d05312018b16` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeQuantityMenu.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeQuantityMenu.java) | `1947cf56e83357e4404927e431fd373b0d4b8f0915968c3573ca9b26ed50dd18` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeStore.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeStore.java) | `473ae6ffd2c23c1cf699fd30be04711eddc8527c8bf1b2653978b28ad4f1d236` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeType.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeType.java) | `25576171b9d224f98804d504d12dca69b49b1fef7c4e77c8a1e69e7190f3f7a5` |
| [src/main/java/fr/cliker/clikerv2/world/ActivityWorlds.java](../src/main/java/fr/cliker/clikerv2/world/ActivityWorlds.java) | `dc5fc09bc4a93b4fd3f2e787dbf08fd5709739da2356439b8ed1e6407fd961b1` |
| [src/main/resources/activites/encheres.yml](../src/main/resources/activites/encheres.yml) | `c1bec1511f534d9ac79188207d67d335a84560587170dd99753b566781919fd4` |
| [src/main/resources/activites/pinata.yml](../src/main/resources/activites/pinata.yml) | `2cf4afc65e34001b0b97fd7ef20ef3edf3c8f8c31eb4c06d55650e7dc79cc3a1` |
| [src/main/resources/activites/raids-groupes.yml](../src/main/resources/activites/raids-groupes.yml) | `6c6ea9c658b62d08ffb8d88ac3851f7a94cf401b06e8d55228502715850193ae` |
| [src/main/resources/activites/tchat.yml](../src/main/resources/activites/tchat.yml) | `618a93149366f6bebc3a69bbd8ad94ed30b937354695fcbfaae85e528c83bf86` |
| [src/main/resources/affichage/classements.yml](../src/main/resources/affichage/classements.yml) | `7ad350ced24b0c34f47b0ecf8575df66a79e01d7b93f2f4e8929788d77daf245` |
| [src/main/resources/affichage/hologrammes.yml](../src/main/resources/affichage/hologrammes.yml) | `13b02a0588e33c5c608a4124ca46e395835f465edd178b26c7b600418046ae02` |
| [src/main/resources/affichage/tab.yml](../src/main/resources/affichage/tab.yml) | `e02e849042062b0f1ec59dbb908b3281d44d0186f41a5ca336c0d334720140d8` |
| [src/main/resources/avantpostes.yml](../src/main/resources/avantpostes.yml) | `912522c1f4a4f0593c2cc60c3af538129e033dd89899c600905f84dcdd1d3794` |
| [src/main/resources/boss.yml](../src/main/resources/boss.yml) | `fcf1028e5f4778f546999af72d8555a80c6fc43272d93fe5b6f03f5904215827` |
| [src/main/resources/boutiques/catalogues.yml](../src/main/resources/boutiques/catalogues.yml) | `99154c2bdb526d8a76c6d5d4834713a9af8a692fe5509518c98ac06fed5dff07` |
| [src/main/resources/boutiques/potions.yml](../src/main/resources/boutiques/potions.yml) | `63cde508dc330c27f993b5fd0b7710005f181fcc1df18803ac15031442bf2077` |
| [src/main/resources/buyzone.yml](../src/main/resources/buyzone.yml) | `2f62e85a731a89701a03ae0d9ef5d09176a94fa38d0d8b37096e14ea8f63c919` |
| [src/main/resources/chasse.yml](../src/main/resources/chasse.yml) | `96bc80fb7ecaf4209c6b6f9532fead647dc1018a6e12621170bdd7779d6cc014` |
| [src/main/resources/claim.yml](../src/main/resources/claim.yml) | `be8c68578edc76bd869a613db94b6eea917add5935953c9a66a5a5486a289d35` |
| [src/main/resources/communaute/bienvenue.yml](../src/main/resources/communaute/bienvenue.yml) | `16d8d7f18950cf53a402c3ba544c4423186b3eccf41a9e1fdee55d6242a1473d` |
| [src/main/resources/communaute/social.yml](../src/main/resources/communaute/social.yml) | `bd4d5ed4d69115caf159788993962372610253f87246f52ff1943cde039efa7f` |
| [src/main/resources/communaute/tags.yml](../src/main/resources/communaute/tags.yml) | `27316acb20b918b6320ef6aae07482215c91d2f12ac1e314ba97559178e949dd` |
| [src/main/resources/communaute/tchat.yml](../src/main/resources/communaute/tchat.yml) | `5072969dbb82749fb74b8998d59d28579e0b6641a22ec1fd19e5789609ff9d7f` |
| [src/main/resources/config.yml](../src/main/resources/config.yml) | `17763380ae594546629e5a91b884d64c49a2ca3ae278efea0c6cfa6559a72fe0` |
| [src/main/resources/daily.yml](../src/main/resources/daily.yml) | `57bdc4adc73e02ec9e11ef64a70cda2b775d035a6aa41f4b3aed5b33d234df28` |
| [src/main/resources/damage-popups.yml](../src/main/resources/damage-popups.yml) | `8a176eb44795df3fe7705720581d817073c7886cf4960a6ab9a6742a45c23256` |
| [src/main/resources/discord.yml](../src/main/resources/discord.yml) | `7742894dc5e91ba3973e8e55cee44f7f8243e51ea11be5af8e750383a0db1777` |
| [src/main/resources/enchants.yml](../src/main/resources/enchants.yml) | `37ae5a5ecac5c7e8a571407fd5c1d4ee8264dc15cbc12c7173864b99b95feef0` |
| [src/main/resources/gui/icons.yml](../src/main/resources/gui/icons.yml) | `f59c12d4f079b636506049ffe20f79b0560f2b55f1732da70bc1320faae92714` |
| [src/main/resources/gui/menu.yml](../src/main/resources/gui/menu.yml) | `0c829745c168f988503015e6ef38ef3a6b3b4873588749a0982fbf0efb788069` |
| [src/main/resources/lootboxes.yml](../src/main/resources/lootboxes.yml) | `5b2d966ee0111ba4df62a70eb4a8c7ca0cc60fd5e54117624ab15e4cc2b5e2e3` |
| [src/main/resources/mobs/creatures.yml](../src/main/resources/mobs/creatures.yml) | `9b0ed43cf15495d50cce1ca93f7055f20f93740d7acfab572ade62b3212e1147` |
| [src/main/resources/navigation/warps.yml](../src/main/resources/navigation/warps.yml) | `b9053ea5b07047bcc2f98ac20125bf9075a68faa5c13b9e162d177933c99924a` |
| [src/main/resources/parrainage.yml](../src/main/resources/parrainage.yml) | `7d0a849eb624db2866de7592ce77405b46939495acb9917c425d3e5d4958ca24` |
| [src/main/resources/plugin.yml](../src/main/resources/plugin.yml) | `4617753b666add92edac8880bcd9749cb976087d2eb899efd89b4d6032e0096e` |
| [src/main/resources/pnj/marchands.yml](../src/main/resources/pnj/marchands.yml) | `4612239b5da619690f46c168065705b0ea3567df69f39679f284c29c3d8e977e` |
| [src/main/resources/progression/talismans.yml](../src/main/resources/progression/talismans.yml) | `217b2c8fa04111239fec852abc16667343c3c29e50d7b6d8df01384a5ce51db0` |
| [src/main/resources/raid.yml](../src/main/resources/raid.yml) | `565d3165a53e29189ae99c1416404bc23774cdfb8c188df0385554c42366be0d` |
| [src/main/resources/scoreboard.yml](../src/main/resources/scoreboard.yml) | `7df39b58c9c84b0faa72f592a841e6d19f35f326a2fbf6c9333a7385c06c0d90` |
