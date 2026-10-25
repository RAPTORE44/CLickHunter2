# Inventaire statique de l’audit ClickHunter

Produit par `python3 scripts/audit-inventory.py`. Chaque fichier de production est indexé ; cette liste n’est pas une certification de son comportement.

| Composant | Fichiers Java | Lignes |
| --- | ---: | ---: |
| noyau | 5 | 1490 |
| auction | 5 | 935 |
| bag | 6 | 672 |
| buyzone | 3 | 865 |
| combat | 5 | 478 |
| comfort | 4 | 891 |
| config | 2 | 112 |
| cooldown | 1 | 55 |
| daily | 6 | 689 |
| discord | 1 | 141 |
| display | 5 | 648 |
| economy | 4 | 280 |
| essence | 8 | 681 |
| gear | 18 | 1860 |
| hologram | 3 | 506 |
| hunting | 6 | 1169 |
| level | 3 | 140 |
| lootbox | 4 | 515 |
| merchant | 4 | 908 |
| mobs | 7 | 1599 |
| npc | 1 | 332 |
| outpost | 2 | 825 |
| prestige | 6 | 843 |
| raid | 2 | 531 |
| referral | 2 | 576 |
| rewards | 3 | 872 |
| runtime | 2 | 37 |
| security | 1 | 32 |
| spawn | 5 | 279 |
| station | 4 | 414 |
| tuto | 3 | 278 |
| ui | 3 | 325 |
| upgrade | 5 | 754 |
| world | 1 | 98 |

Total : **140 fichiers Java**, **16 configurations YAML**.

## Fichiers indexés

| Fichier | SHA-256 au moment de l’inventaire |
| --- | --- |
| [src/main/java/fr/cliker/clikerv2/ClikerPlugin.java](../src/main/java/fr/cliker/clikerv2/ClikerPlugin.java) | `3c859097c04815449633e60e2514b87c1c43b38b74fbc5aa0e14baf3eeeb5cde` |
| [src/main/java/fr/cliker/clikerv2/DatabaseManager.java](../src/main/java/fr/cliker/clikerv2/DatabaseManager.java) | `097310e8cf6a3feb4fde0d95a4a6da6636180b188a036a95a588d68d256f3950` |
| [src/main/java/fr/cliker/clikerv2/EconomyService.java](../src/main/java/fr/cliker/clikerv2/EconomyService.java) | `2f282f8feefcbb2e7e7b9b2de1f811a7195967ff2b0e2c21fd143ccd964877e7` |
| [src/main/java/fr/cliker/clikerv2/MobTier.java](../src/main/java/fr/cliker/clikerv2/MobTier.java) | `def3c9e6a6f867a1d36b8e1ab035adeee948b38a90ffadfec245883b3d482ae6` |
| [src/main/java/fr/cliker/clikerv2/Numbers.java](../src/main/java/fr/cliker/clikerv2/Numbers.java) | `7fcf8011789d9aa6949c9e29e80bba5bdb0afc182da9403106a79cf9fa32437a` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionCommand.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionCommand.java) | `ba9c821e40b0a920202e0bda1ef098baaad2d79dbfe625cbd78aa6d22e417aac` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionListing.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionListing.java) | `2a9c309e2b156fd7ed7dfa38146b4bcfc657805dec3fb08f6b8ace83c094aaf3` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionManager.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionManager.java) | `81fba80eaeb8919b48847da56148679d7ceaabc4e64b1bcc6eb1339fa42c3b92` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionMenu.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionMenu.java) | `3dde18ef8288cc3a24a50a54f8a87e0f38013caf57a6d76a254c76daa3656e74` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionStore.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionStore.java) | `39ef2c7f2435eecf6da48bf147a3f3333ba9cdfa526ddb34a6600db97b2da4ac` |
| [src/main/java/fr/cliker/clikerv2/bag/BagCommand.java](../src/main/java/fr/cliker/clikerv2/bag/BagCommand.java) | `25b3eb454c2755e53020fc58cf43ee079357ea74a05ee9a21225be207a97bedd` |
| [src/main/java/fr/cliker/clikerv2/bag/BagManager.java](../src/main/java/fr/cliker/clikerv2/bag/BagManager.java) | `db766e24db392d9bd2b495f68f0b280f73ef3a81318b6ac20cbae8f2a59157b5` |
| [src/main/java/fr/cliker/clikerv2/bag/BagMenu.java](../src/main/java/fr/cliker/clikerv2/bag/BagMenu.java) | `ac86976fa7f984302ebafedcfe3a7afa0ddcfcc1bf353c1f1f59fe2b735114c0` |
| [src/main/java/fr/cliker/clikerv2/bag/BagStore.java](../src/main/java/fr/cliker/clikerv2/bag/BagStore.java) | `ccacb253e2aa052215ccb7c3bc647b0e5b7995917ac82480feebb56b03211976` |
| [src/main/java/fr/cliker/clikerv2/bag/TalismanCommand.java](../src/main/java/fr/cliker/clikerv2/bag/TalismanCommand.java) | `e28ee6f76f384d735ff24ed4e126e8291c4116e5a1491d137dbc80365d7e4ca2` |
| [src/main/java/fr/cliker/clikerv2/bag/TalismanItem.java](../src/main/java/fr/cliker/clikerv2/bag/TalismanItem.java) | `05f90eb73fba5dcf3f5fa1509673c6876b73859e5728baff70c17ca8b796b40a` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZone.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZone.java) | `e0b83b41d26e61ce2a92af4480e5a3b6e7d0ea264d4f35a4d69b0eabf1d1ae02` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneModule.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneModule.java) | `3a41977c972c56ba6d8a179bee1d0f3d9a83d662fc78575b81d8730e0583c5b3` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneStore.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneStore.java) | `83ab3b1e10e8970ad1f24234ee0abc62b825e4638594d7d00da2100443f97c01` |
| [src/main/java/fr/cliker/clikerv2/combat/AutoClickManager.java](../src/main/java/fr/cliker/clikerv2/combat/AutoClickManager.java) | `0e4a6983a7d23fb732163d1bb19c001a69cd26bd7b804c78a618846956beaf0c` |
| [src/main/java/fr/cliker/clikerv2/combat/DamagePopups.java](../src/main/java/fr/cliker/clikerv2/combat/DamagePopups.java) | `ee466a2890d020644ad0a1822c2107b0a029b62aaa2492dcd2efafd4c03a793f` |
| [src/main/java/fr/cliker/clikerv2/combat/DeathLogFilter.java](../src/main/java/fr/cliker/clikerv2/combat/DeathLogFilter.java) | `225281aa212c41d0ef98f79509d3a5df5a6d48b0bd1238d3a0b06307cd32edd8` |
| [src/main/java/fr/cliker/clikerv2/combat/StarterSwordListener.java](../src/main/java/fr/cliker/clikerv2/combat/StarterSwordListener.java) | `400e5841960670e4281059bf30495266ad01fd4efa6417e1bf148b7f1ec59689` |
| [src/main/java/fr/cliker/clikerv2/combat/SwordCommand.java](../src/main/java/fr/cliker/clikerv2/combat/SwordCommand.java) | `d63002ae28f6f5a634cccd07ca8361cb0dd4129195420ac4fc60338c388a7632` |
| [src/main/java/fr/cliker/clikerv2/comfort/FarmTelemetry.java](../src/main/java/fr/cliker/clikerv2/comfort/FarmTelemetry.java) | `19d8fa7167fca4773ed24ce2cbd0eb88aefbdb2a16142afdc998a98a1b7e7f8f` |
| [src/main/java/fr/cliker/clikerv2/comfort/PlayerComfort.java](../src/main/java/fr/cliker/clikerv2/comfort/PlayerComfort.java) | `d95964393125b0c8747b91c6b32861a39f56d830f5527cbb367c253f66348052` |
| [src/main/java/fr/cliker/clikerv2/comfort/ProgressNavigation.java](../src/main/java/fr/cliker/clikerv2/comfort/ProgressNavigation.java) | `f886e7e4288b0c4c30c6ad1323a62619a2413bf73026282d9d0c30f82857afa4` |
| [src/main/java/fr/cliker/clikerv2/comfort/UpgradeGuide.java](../src/main/java/fr/cliker/clikerv2/comfort/UpgradeGuide.java) | `d18e29334d1d4aaa0fd531ed3efe81ce3e8aee0abac8381a5b679b4dfa471f0b` |
| [src/main/java/fr/cliker/clikerv2/config/StoredLocation.java](../src/main/java/fr/cliker/clikerv2/config/StoredLocation.java) | `49336cc9cf11bbca0c01d34b81c1b725bd047510edfe59e15eb70cc62e1bbea0` |
| [src/main/java/fr/cliker/clikerv2/config/YamlFiles.java](../src/main/java/fr/cliker/clikerv2/config/YamlFiles.java) | `f7d02fd04fdef8e2f6c383823212dfefd31af38fb58417976f6a3aa9924fa072` |
| [src/main/java/fr/cliker/clikerv2/cooldown/CooldownCommand.java](../src/main/java/fr/cliker/clikerv2/cooldown/CooldownCommand.java) | `74271daa768c17f715d66849f9ee203df0e482be3d2b573c757fd50d2392f84e` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyMenu.java](../src/main/java/fr/cliker/clikerv2/daily/DailyMenu.java) | `1e7af627f2ce04e4457c11ec789a72f3618adf296e644af71ca55bae41baca00` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyModule.java](../src/main/java/fr/cliker/clikerv2/daily/DailyModule.java) | `7a4247ae4d141e90d8dbe10b0ab2974b47957c979788ea9f3e657884f63427c0` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyNpc.java](../src/main/java/fr/cliker/clikerv2/daily/DailyNpc.java) | `e45bf5cd8001d154f0565f77ecf8b005c3b3ae69405d64e803e5211caca516d4` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyProgress.java](../src/main/java/fr/cliker/clikerv2/daily/DailyProgress.java) | `ee1d65ca541999b328c21857902abc1779cc6b9f1e509514adc31400d83984e4` |
| [src/main/java/fr/cliker/clikerv2/daily/DailySettings.java](../src/main/java/fr/cliker/clikerv2/daily/DailySettings.java) | `be4c8d13690270639b733df2f1095db0a14b531975e8b019ffbb2022bf423935` |
| [src/main/java/fr/cliker/clikerv2/daily/RewardStore.java](../src/main/java/fr/cliker/clikerv2/daily/RewardStore.java) | `5f74f798fcb937f95846ce6b0237378755b2369d698abdd205833a06d1826c48` |
| [src/main/java/fr/cliker/clikerv2/discord/DiscordModule.java](../src/main/java/fr/cliker/clikerv2/discord/DiscordModule.java) | `9aa6c9d336eaa5247822c50300b6ebf642ba24ec41215998456ff19f711faf1f` |
| [src/main/java/fr/cliker/clikerv2/display/MoneyScoreboard.java](../src/main/java/fr/cliker/clikerv2/display/MoneyScoreboard.java) | `645ac4384d2f8a853847c59d7ae5cc2e24f9f33ca52affc59fd327c1b6ab359d` |
| [src/main/java/fr/cliker/clikerv2/display/RankPrefix.java](../src/main/java/fr/cliker/clikerv2/display/RankPrefix.java) | `81d982ee2950f26b05460700f05c2684d70fb3f518916848cf5dfe5009e27def` |
| [src/main/java/fr/cliker/clikerv2/display/StatsCommand.java](../src/main/java/fr/cliker/clikerv2/display/StatsCommand.java) | `09eeabb3b6856989f599df4dddf6dcb11228a48cc0e1c562ddea73745b3b79e3` |
| [src/main/java/fr/cliker/clikerv2/display/StatsMenu.java](../src/main/java/fr/cliker/clikerv2/display/StatsMenu.java) | `0a0261a688058fdaaee1d73aab6812cb8aa981fa4055e783769c3337cba98668` |
| [src/main/java/fr/cliker/clikerv2/display/TabListManager.java](../src/main/java/fr/cliker/clikerv2/display/TabListManager.java) | `d599c6297e80cb75585a9cbecc155637d1438564c7ee0cc0dcf101ec511fd206` |
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
| [src/main/java/fr/cliker/clikerv2/essence/SellMenu.java](../src/main/java/fr/cliker/clikerv2/essence/SellMenu.java) | `a5ab15c121707824dcb96258a02633957e6d7f7d5021fe55b802b085dd3ff5dc` |
| [src/main/java/fr/cliker/clikerv2/gear/CharmCommand.java](../src/main/java/fr/cliker/clikerv2/gear/CharmCommand.java) | `2ce298d78c256ed4670284c82b27b8c3d6c0ab4561b9f0c36b5a4016a23272e7` |
| [src/main/java/fr/cliker/clikerv2/gear/CharmItem.java](../src/main/java/fr/cliker/clikerv2/gear/CharmItem.java) | `7285abcf7012a3366067624cb00bc78f7fca84c41e0395f7560ffb4c77a72b65` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantCommand.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantCommand.java) | `1c16c8380f92cba067648fb87d2ba2d7d8253f07d67d18036a45bd7884f8b531` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantMenu.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantMenu.java) | `fae49567c44d29020e8d461acd89fc78042eeb586a645a16ee7887f451e8d11f` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantRoll.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantRoll.java) | `3c7b9992e336888ebe0e3126336b0f17c70dc909ab0e25ae7980d4e5fb6eacee` |
| [src/main/java/fr/cliker/clikerv2/gear/EnchantType.java](../src/main/java/fr/cliker/clikerv2/gear/EnchantType.java) | `fc4ea1efb1f55ca7a17527d60cc807736b297f3f14ba2771dd14abc10ffa251f` |
| [src/main/java/fr/cliker/clikerv2/gear/GearAttributeManager.java](../src/main/java/fr/cliker/clikerv2/gear/GearAttributeManager.java) | `4b520765cd91dd891bbb6a3955f972e08d35d4e928f668d8c04157d7653c9798` |
| [src/main/java/fr/cliker/clikerv2/gear/GearLore.java](../src/main/java/fr/cliker/clikerv2/gear/GearLore.java) | `9d70b4521673e4925e99e477882eaa55619637192b232f84dbb88fcb4ac0bfe7` |
| [src/main/java/fr/cliker/clikerv2/gear/GearTransactions.java](../src/main/java/fr/cliker/clikerv2/gear/GearTransactions.java) | `21776dc5a012d9ef3f62e66db07f4860a93807c1720ca2ade37598d2113d3b92` |
| [src/main/java/fr/cliker/clikerv2/gear/ItemMods.java](../src/main/java/fr/cliker/clikerv2/gear/ItemMods.java) | `0d61a1d00779e56120b6af9358bdb2acd9d069abf4fd831adff75765e28c4d64` |
| [src/main/java/fr/cliker/clikerv2/gear/ReforgeCommand.java](../src/main/java/fr/cliker/clikerv2/gear/ReforgeCommand.java) | `e1fc8bfc977c57f76776894678341b38a353dcc6eb272b61ce376d14da4fe45a` |
| [src/main/java/fr/cliker/clikerv2/gear/ReforgeMenu.java](../src/main/java/fr/cliker/clikerv2/gear/ReforgeMenu.java) | `7bf218fbd35408d1ec29f004261b16d4d22d1c18fcdf0d1622f31ea60db04990` |
| [src/main/java/fr/cliker/clikerv2/gear/ReforgeType.java](../src/main/java/fr/cliker/clikerv2/gear/ReforgeType.java) | `c102bae7dff50dc6e207b919e9156b4e21263245915d668121dad0a675c89063` |
| [src/main/java/fr/cliker/clikerv2/gear/RuneCommand.java](../src/main/java/fr/cliker/clikerv2/gear/RuneCommand.java) | `a15bfa858844f00c1c3f9bdf27586b3a8e1c74befee6121271a39120aec6b605` |
| [src/main/java/fr/cliker/clikerv2/gear/RuneMenu.java](../src/main/java/fr/cliker/clikerv2/gear/RuneMenu.java) | `3e1b83496dfd2147ee9e9cba8a143c8de950a84cf5b59921204d4f135198091b` |
| [src/main/java/fr/cliker/clikerv2/gear/RuneType.java](../src/main/java/fr/cliker/clikerv2/gear/RuneType.java) | `06262786984152088e48cc07c9f80454f3efa195139124b93599907da71a80ae` |
| [src/main/java/fr/cliker/clikerv2/gear/TierGear.java](../src/main/java/fr/cliker/clikerv2/gear/TierGear.java) | `792026920c8b61a4cfeb4c5bcbf147c27c00310537349e3c4a08c1b0bf7efb0c` |
| [src/main/java/fr/cliker/clikerv2/gear/TierGearCommand.java](../src/main/java/fr/cliker/clikerv2/gear/TierGearCommand.java) | `a42f067d3958d7488c04db69e9db22bacfd19fcb8818b5b58c8fc7d4c5444634` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramCommand.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramCommand.java) | `5d4bf7ede24059215758406ad4a14691baf81e05a151e039eb895a4220128fc8` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramManager.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramManager.java) | `7a652ba8e70ec1ac05f3c0eb1c3c3190181776d9631127d17c9046ad6b4f2b7a` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramStore.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramStore.java) | `6fdf300503be19b637e982589656c2b1151268cef34d5470029800f68deceb3c` |
| [src/main/java/fr/cliker/clikerv2/hunting/ContractStore.java](../src/main/java/fr/cliker/clikerv2/hunting/ContractStore.java) | `1b6b5643cfb5a1014444180215a211ceab137326e1e7ebc7aa1104d8b092d273` |
| [src/main/java/fr/cliker/clikerv2/hunting/Gains.java](../src/main/java/fr/cliker/clikerv2/hunting/Gains.java) | `71c26a760810e6d32f85ae41d51e848c7d9389f910ce79e03ee9e80c1dc51869` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntUI.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntUI.java) | `0c773605d9cf80f58d8f22eaf2ecf7afb6142d2fa92538eaf1ca02fc31f0cde2` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntingModule.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntingModule.java) | `a7b7667275ed12351483c44ebb14f4bb78bc94127d3b26a9f30b47372c863371` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntingStore.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntingStore.java) | `5399c83eb9cd0411b0741f30d52655e983c82e022788cd228fac6b9f12608cbd` |
| [src/main/java/fr/cliker/clikerv2/hunting/RewardLedger.java](../src/main/java/fr/cliker/clikerv2/hunting/RewardLedger.java) | `89b70c2e6f3d30e2abcf2999bb9b5bcb6500eec59e09620d0334c207926b0380` |
| [src/main/java/fr/cliker/clikerv2/level/LevelDamageBonus.java](../src/main/java/fr/cliker/clikerv2/level/LevelDamageBonus.java) | `d69a167933b5853ce570100ccdca5b13e90e0d8e20744a0e59714f68f1a0f081` |
| [src/main/java/fr/cliker/clikerv2/level/LevelUpListener.java](../src/main/java/fr/cliker/clikerv2/level/LevelUpListener.java) | `feb8cbe2d8ec1a7be446a23c775e4ee4cacea14ac84f82cca68b8ceafcd61b5c` |
| [src/main/java/fr/cliker/clikerv2/level/PlayerLevelStore.java](../src/main/java/fr/cliker/clikerv2/level/PlayerLevelStore.java) | `004bfbe457c56db3c79af03cc190fe1e628cde7671e2456888f9c7a73315b48e` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxCommand.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxCommand.java) | `e33e19a283b3ea5e4537849466fe3dbb5f0a27b78055b28210808851656ff8a3` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxItem.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxItem.java) | `b1c28f85524d0da279acc92845c477f464793f56b87671822c82e9811b7ee64e` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxManager.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxManager.java) | `aec57fb9bcecd561ebe3584b973a396bca4c9939ee0dfb6971b5ae828bc651b0` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxTier.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxTier.java) | `394d2e81391d42f1b4cd4d18ecc5b33e4e5950fea19c5544841db97d57995c43` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantCommand.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantCommand.java) | `307d980770e025c01f15a2042698d7bb78e363c078d0d83ef0a2aec2832e6e7d` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantManager.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantManager.java) | `659a2d126833824e047de7cb3359f48d47dcf95dbfa39eceba3b09189b89ecf9` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantMenu.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantMenu.java) | `8560907f5688fbfaaee26cb4eec0a344b60b63687227e7c350f3e408b7d4e279` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantStore.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantStore.java) | `5962c2ba365494b984c3202e98f8d8143eba814e649458a6c1b624abd1f55ba0` |
| [src/main/java/fr/cliker/clikerv2/mobs/BossEventManager.java](../src/main/java/fr/cliker/clikerv2/mobs/BossEventManager.java) | `2af2b2c5b481c343427495b6429711426e8b88297972377c5bd09c0fa3d4e4e8` |
| [src/main/java/fr/cliker/clikerv2/mobs/CustomMobManager.java](../src/main/java/fr/cliker/clikerv2/mobs/CustomMobManager.java) | `f9484c004f30485709179e2c6b9ef9178188c3fed34e4fb9233f129d1139e53e` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobKillStore.java](../src/main/java/fr/cliker/clikerv2/mobs/MobKillStore.java) | `5427af864d7ec23efb7d921db4414fc20cb9708048198d4627fcd56f894a923d` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobKillsCommand.java](../src/main/java/fr/cliker/clikerv2/mobs/MobKillsCommand.java) | `f9ad013bfafd3e103ab1ab91b58d1eb1555ec4563fb02d51e7f56ddc8160c6d0` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobSpawnCommand.java](../src/main/java/fr/cliker/clikerv2/mobs/MobSpawnCommand.java) | `3f251a566166e05eacaa553d3d7d3f913f7777f7f1c963dcc79677f62d0964a4` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobSpawnStore.java](../src/main/java/fr/cliker/clikerv2/mobs/MobSpawnStore.java) | `dd52dea6b5d26441f35c17f0b782c06e9177ece897f91b87962b9374d7fc08ca` |
| [src/main/java/fr/cliker/clikerv2/mobs/VanillaSpawnListener.java](../src/main/java/fr/cliker/clikerv2/mobs/VanillaSpawnListener.java) | `618899ace54336ecb13d5def2f4d43309cf4c2070918f6e698bae1f1aa7d33ac` |
| [src/main/java/fr/cliker/clikerv2/npc/CitizensNpc.java](../src/main/java/fr/cliker/clikerv2/npc/CitizensNpc.java) | `2e33b5dbe72d3d0bc968c8c2ee067fd5218f5089b82a9fbf39380775ca6a0711` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostModule.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostModule.java) | `b005c0675ad7423adb553a05aa22bade72c0793adeab5f41f6b5ed64911cd2cb` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostStore.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostStore.java) | `8eb61b5674857a60756fc1679e0f33b4af46b23195907330ec30b291148a5bb8` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeChatFormatter.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeChatFormatter.java) | `da029d3d3a4b9f3d1a3dacde609960cc5115248ae84517e9e83f3a47d4ea21fc` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeCommand.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeCommand.java) | `0c3859ef9f620fc2bf32f8758fe06faf0acd11fe13a5389ef6bfd0dad400d33d` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeManager.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeManager.java) | `db66891f593249fcd0a11e19a4b04189f6ae2df4b6884f9610515ee1dfacfc65` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeMenu.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeMenu.java) | `1abe9bab813485ee7712b89edaba7208b4214bc2dd7284c10f68b4c794d931fa` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeStore.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeStore.java) | `62c420ff865e86ab6f95667fea266602b9e6d69e8106cc4a05fe8d428b049c8e` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeType.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeType.java) | `1a545bbbbc13881341e2ccaeca804e5c91827c3643286d6086ef46f329a03143` |
| [src/main/java/fr/cliker/clikerv2/raid/ProportionalRewards.java](../src/main/java/fr/cliker/clikerv2/raid/ProportionalRewards.java) | `bc7e8702ed54d925827c14c2e61b89e0057a0de885656c80d2d73ffa33fd819c` |
| [src/main/java/fr/cliker/clikerv2/raid/RaidModule.java](../src/main/java/fr/cliker/clikerv2/raid/RaidModule.java) | `62f11310993dfd05921d8b96fa043e2a7ef5aa8fa4c8bedc2d32905dfe6482c8` |
| [src/main/java/fr/cliker/clikerv2/referral/ReferralModule.java](../src/main/java/fr/cliker/clikerv2/referral/ReferralModule.java) | `b85d5d882b403400f90e2cb0440339429c00fe1cfb397abb303392570756c010` |
| [src/main/java/fr/cliker/clikerv2/referral/ReferralStore.java](../src/main/java/fr/cliker/clikerv2/referral/ReferralStore.java) | `d0a4a6330c10d0dde7b97d25a1a5e7db918a7a79d35c692ec0e4d5ac531d1ba6` |
| [src/main/java/fr/cliker/clikerv2/rewards/ClaimService.java](../src/main/java/fr/cliker/clikerv2/rewards/ClaimService.java) | `f0c89af05f47a910946365f90953e06c58914661ec6029b00979d8b6de5cdc7a` |
| [src/main/java/fr/cliker/clikerv2/rewards/ItemDelivery.java](../src/main/java/fr/cliker/clikerv2/rewards/ItemDelivery.java) | `83996cb7a55d25f6b0cfbadf0031a1f835f86275c526260d83bb245c8de8f1bc` |
| [src/main/java/fr/cliker/clikerv2/rewards/MailStore.java](../src/main/java/fr/cliker/clikerv2/rewards/MailStore.java) | `3bb3b2ce4f18d055e07e54565d02003ed028e1bdd1c31000a5afa18b7c8c8068` |
| [src/main/java/fr/cliker/clikerv2/runtime/PlayerSessions.java](../src/main/java/fr/cliker/clikerv2/runtime/PlayerSessions.java) | `f721d9ed994c54140f39c2220d68fd7fcc3c75632981ea6be6fb36898f5c1f2a` |
| [src/main/java/fr/cliker/clikerv2/runtime/ServerTasks.java](../src/main/java/fr/cliker/clikerv2/runtime/ServerTasks.java) | `d77bd0f9ea6fcee886e7475b4825edba129676dcaa54171040dac6ebae0e475b` |
| [src/main/java/fr/cliker/clikerv2/security/SystemEggProtection.java](../src/main/java/fr/cliker/clikerv2/security/SystemEggProtection.java) | `5b79b638f0e1363fbd95daef4dee0b11e17edde4dd523dc5c0de881fade4296d` |
| [src/main/java/fr/cliker/clikerv2/spawn/RespawnListener.java](../src/main/java/fr/cliker/clikerv2/spawn/RespawnListener.java) | `cc72e0c5ba1f4d03c1df8022178aa08d0b4cda60358111b2e40be7e19f0a9212` |
| [src/main/java/fr/cliker/clikerv2/spawn/SetSpawnCommand.java](../src/main/java/fr/cliker/clikerv2/spawn/SetSpawnCommand.java) | `f498ee0108be97529a6366f9661018a30df09d104d05c03ca8d601ddbb2d4a3c` |
| [src/main/java/fr/cliker/clikerv2/spawn/SpawnCommand.java](../src/main/java/fr/cliker/clikerv2/spawn/SpawnCommand.java) | `699a59a6731928bfc2806a00d90d877dc091707bca6f86cab1d4baef1b861451` |
| [src/main/java/fr/cliker/clikerv2/spawn/SpawnManager.java](../src/main/java/fr/cliker/clikerv2/spawn/SpawnManager.java) | `f27a61e80bb968eea4a3664971a4c829dcb04ca86c17b09c7c5e6f5c073c1d48` |
| [src/main/java/fr/cliker/clikerv2/spawn/SpawnStore.java](../src/main/java/fr/cliker/clikerv2/spawn/SpawnStore.java) | `eac66bee6d1246cedd600d616517178ffaa695f8b8240f317b93c143884495cf` |
| [src/main/java/fr/cliker/clikerv2/station/StationCommand.java](../src/main/java/fr/cliker/clikerv2/station/StationCommand.java) | `8b0641cb8ef82d9d2ce8da24e8b425352192bad372f3cc91f5d2776a7bd7cab7` |
| [src/main/java/fr/cliker/clikerv2/station/StationManager.java](../src/main/java/fr/cliker/clikerv2/station/StationManager.java) | `0c7b36f6de5c633b6bf6ffa221b277524f9ff4c40728bd599f3af53b4968fb14` |
| [src/main/java/fr/cliker/clikerv2/station/StationStore.java](../src/main/java/fr/cliker/clikerv2/station/StationStore.java) | `6d7984d66742431fe092b79f451a68d1b1d15f4b767e4d1ff0f59aefe65e858b` |
| [src/main/java/fr/cliker/clikerv2/station/StationType.java](../src/main/java/fr/cliker/clikerv2/station/StationType.java) | `238dca1c1a39675b86d4c4a16f2234f135a90fe1df10e958642203a096721c2b` |
| [src/main/java/fr/cliker/clikerv2/tuto/TutoBook.java](../src/main/java/fr/cliker/clikerv2/tuto/TutoBook.java) | `061cc57d74a3ac3cf71a012263a9f420633be67ed610e76fd26abd22052e2ac2` |
| [src/main/java/fr/cliker/clikerv2/tuto/TutoCommand.java](../src/main/java/fr/cliker/clikerv2/tuto/TutoCommand.java) | `0af182925a279ee0cb7885b125e250ec6b29e5d9f340188616714b930688ef85` |
| [src/main/java/fr/cliker/clikerv2/tuto/TutoJoinListener.java](../src/main/java/fr/cliker/clikerv2/tuto/TutoJoinListener.java) | `524a0118a64163e5d87f83e63b213dfcf8d5834cc81442d5621c24cebd9fe471` |
| [src/main/java/fr/cliker/clikerv2/ui/MainMenu.java](../src/main/java/fr/cliker/clikerv2/ui/MainMenu.java) | `20b01f97dfc3ce8d582990bc6d1e8f504f346a291a03ee5437c9b3648d37172a` |
| [src/main/java/fr/cliker/clikerv2/ui/MenuNavigation.java](../src/main/java/fr/cliker/clikerv2/ui/MenuNavigation.java) | `5e95103a79347efeda8a6e06829b9312682135cb574707d8355a7765a7f999df` |
| [src/main/java/fr/cliker/clikerv2/ui/ViewRequests.java](../src/main/java/fr/cliker/clikerv2/ui/ViewRequests.java) | `730ae2d62b51f2d3b66545cf7bb43d771bdd3262d45409bf110cf9270da1d410` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeCommand.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeCommand.java) | `4c89c6bf39cc2fd688deb507878891e668b53b1d8d2faecc093e5039387fcd28` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeManager.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeManager.java) | `19aad1bbf46f4b722f093c65e4f42db9d953bab4951219939aca5392d2d45356` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeMenu.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeMenu.java) | `4c721abd18568814ba7e57b5200d90be5e71f03e55779fca7e2fc434b7dbbc16` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeStore.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeStore.java) | `54ca7151737c369c10984f66638654ef32e2085c4557cf5b6914010ae3ea2ac9` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeType.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeType.java) | `25576171b9d224f98804d504d12dca69b49b1fef7c4e77c8a1e69e7190f3f7a5` |
| [src/main/java/fr/cliker/clikerv2/world/ActivityWorlds.java](../src/main/java/fr/cliker/clikerv2/world/ActivityWorlds.java) | `dc5fc09bc4a93b4fd3f2e787dbf08fd5709739da2356439b8ed1e6407fd961b1` |
| [src/main/resources/avantpostes.yml](../src/main/resources/avantpostes.yml) | `d8fadce2fa09a42afc4791fe75b5d3af27aa58ada97c81315b0a7d0e5cc9e145` |
| [src/main/resources/boss.yml](../src/main/resources/boss.yml) | `23e10f84e75e37b9776e8a0783644b0ad2a8f8fef0ae8612f8d46b4b8d7bf333` |
| [src/main/resources/buyzone.yml](../src/main/resources/buyzone.yml) | `2f62e85a731a89701a03ae0d9ef5d09176a94fa38d0d8b37096e14ea8f63c919` |
| [src/main/resources/chasse.yml](../src/main/resources/chasse.yml) | `aab7da2e6dffa115fa298debb629215b92876ba534a4869d914b8ffbf3fd93a8` |
| [src/main/resources/claim.yml](../src/main/resources/claim.yml) | `be8c68578edc76bd869a613db94b6eea917add5935953c9a66a5a5486a289d35` |
| [src/main/resources/config.yml](../src/main/resources/config.yml) | `4a5fc39462f9e1715f210eba96a4fb3c1661a555ec0ead83b5a163d08e6e22de` |
| [src/main/resources/daily.yml](../src/main/resources/daily.yml) | `0706711b67175acd40561394c6dc395c75630c81a9a012e0988d9fa39090160e` |
| [src/main/resources/damage-popups.yml](../src/main/resources/damage-popups.yml) | `8a176eb44795df3fe7705720581d817073c7886cf4960a6ab9a6742a45c23256` |
| [src/main/resources/discord.yml](../src/main/resources/discord.yml) | `aae5e8b74dd59ba34803327e2d6311f07af3e70130118490067de0e9a17cc49d` |
| [src/main/resources/enchants.yml](../src/main/resources/enchants.yml) | `37ae5a5ecac5c7e8a571407fd5c1d4ee8264dc15cbc12c7173864b99b95feef0` |
| [src/main/resources/gui/menu.yml](../src/main/resources/gui/menu.yml) | `b6077bda73cc127c4c2ddec03747834ed3ec86d8c889f7da4e049352dffb50c4` |
| [src/main/resources/lootboxes.yml](../src/main/resources/lootboxes.yml) | `5b2d966ee0111ba4df62a70eb4a8c7ca0cc60fd5e54117624ab15e4cc2b5e2e3` |
| [src/main/resources/parrainage.yml](../src/main/resources/parrainage.yml) | `7d0a849eb624db2866de7592ce77405b46939495acb9917c425d3e5d4958ca24` |
| [src/main/resources/plugin.yml](../src/main/resources/plugin.yml) | `40f6f27f4cb8c6ccad0edc27d5cf883045d93eb1880ee941f77dbefd9a3e1fad` |
| [src/main/resources/raid.yml](../src/main/resources/raid.yml) | `565d3165a53e29189ae99c1416404bc23774cdfb8c188df0385554c42366be0d` |
| [src/main/resources/scoreboard.yml](../src/main/resources/scoreboard.yml) | `7df39b58c9c84b0faa72f592a841e6d19f35f326a2fbf6c9333a7385c06c0d90` |
