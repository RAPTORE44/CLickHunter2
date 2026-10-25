# Inventaire statique de l’audit ClickHunter

Produit par `python3 scripts/audit-inventory.py`. Chaque fichier de production est indexé ; cette liste n’est pas une certification de son comportement.

| Composant | Fichiers Java | Lignes |
| --- | ---: | ---: |
| noyau | 5 | 1576 |
| auction | 5 | 1059 |
| bag | 6 | 721 |
| buyzone | 3 | 865 |
| combat | 5 | 487 |
| comfort | 5 | 1210 |
| config | 3 | 191 |
| cooldown | 1 | 62 |
| daily | 6 | 787 |
| discord | 1 | 143 |
| display | 5 | 648 |
| economy | 4 | 280 |
| essence | 8 | 689 |
| features | 12 | 2596 |
| gear | 19 | 2140 |
| hologram | 3 | 548 |
| hunting | 6 | 1188 |
| level | 3 | 140 |
| lootbox | 4 | 515 |
| merchant | 4 | 1001 |
| mobs | 7 | 1677 |
| npc | 1 | 292 |
| outpost | 4 | 1725 |
| prestige | 6 | 860 |
| raid | 2 | 635 |
| referral | 2 | 576 |
| rewards | 3 | 891 |
| runtime | 2 | 37 |
| security | 1 | 32 |
| spawn | 5 | 279 |
| station | 4 | 414 |
| tuto | 3 | 278 |
| ui | 5 | 686 |
| upgrade | 6 | 986 |
| world | 1 | 98 |

Total : **160 fichiers Java**, **24 configurations YAML**.

## Fichiers indexés

| Fichier | SHA-256 au moment de l’inventaire |
| --- | --- |
| [src/main/java/fr/cliker/clikerv2/ClikerPlugin.java](../src/main/java/fr/cliker/clikerv2/ClikerPlugin.java) | `c02c75d7289de4b5317ff988c4152b9a0a5ddd4d44b60daae90be77f36791d07` |
| [src/main/java/fr/cliker/clikerv2/DatabaseManager.java](../src/main/java/fr/cliker/clikerv2/DatabaseManager.java) | `7bf07a2273a5694290c6293065fa411df5a0cf013ac19f61a3711ed98a04323a` |
| [src/main/java/fr/cliker/clikerv2/EconomyService.java](../src/main/java/fr/cliker/clikerv2/EconomyService.java) | `98912dd947c1c61a7e103091fc5ae036f4a8e5dcbbc2fb77dea50237b5573754` |
| [src/main/java/fr/cliker/clikerv2/MobTier.java](../src/main/java/fr/cliker/clikerv2/MobTier.java) | `f7489c7fede9215108dfa529cf50d449b5b37ce739b042db58edb7835352f7aa` |
| [src/main/java/fr/cliker/clikerv2/Numbers.java](../src/main/java/fr/cliker/clikerv2/Numbers.java) | `7fcf8011789d9aa6949c9e29e80bba5bdb0afc182da9403106a79cf9fa32437a` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionCommand.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionCommand.java) | `acfacf319afd93bed49715b39955e7f1dd971728a2d80fbe2dcf17806cf8f3d9` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionListing.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionListing.java) | `2a9c309e2b156fd7ed7dfa38146b4bcfc657805dec3fb08f6b8ace83c094aaf3` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionManager.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionManager.java) | `a777971b3f6c94a5b12d870f688eca7a60e565624ce5bc999384aeb2ba88c0c0` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionMenu.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionMenu.java) | `d04e6a17a20bea3a17bf8bdc45f9f7e4fc27ba01c1a331b911b864780a38eeea` |
| [src/main/java/fr/cliker/clikerv2/auction/AuctionStore.java](../src/main/java/fr/cliker/clikerv2/auction/AuctionStore.java) | `ec8b7c8345b1e342cb24ae4c9b188e32bd0408c1bccec47f8acaceb36c64980e` |
| [src/main/java/fr/cliker/clikerv2/bag/BagCommand.java](../src/main/java/fr/cliker/clikerv2/bag/BagCommand.java) | `25b3eb454c2755e53020fc58cf43ee079357ea74a05ee9a21225be207a97bedd` |
| [src/main/java/fr/cliker/clikerv2/bag/BagManager.java](../src/main/java/fr/cliker/clikerv2/bag/BagManager.java) | `966335fdb07a45abf425adc7c82d4f310e0497342963574032ad3e79e97b36fd` |
| [src/main/java/fr/cliker/clikerv2/bag/BagMenu.java](../src/main/java/fr/cliker/clikerv2/bag/BagMenu.java) | `ac86976fa7f984302ebafedcfe3a7afa0ddcfcc1bf353c1f1f59fe2b735114c0` |
| [src/main/java/fr/cliker/clikerv2/bag/BagStore.java](../src/main/java/fr/cliker/clikerv2/bag/BagStore.java) | `ccacb253e2aa052215ccb7c3bc647b0e5b7995917ac82480feebb56b03211976` |
| [src/main/java/fr/cliker/clikerv2/bag/TalismanCommand.java](../src/main/java/fr/cliker/clikerv2/bag/TalismanCommand.java) | `e28ee6f76f384d735ff24ed4e126e8291c4116e5a1491d137dbc80365d7e4ca2` |
| [src/main/java/fr/cliker/clikerv2/bag/TalismanItem.java](../src/main/java/fr/cliker/clikerv2/bag/TalismanItem.java) | `05f90eb73fba5dcf3f5fa1509673c6876b73859e5728baff70c17ca8b796b40a` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZone.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZone.java) | `22550ae14d4733cfb089ff1d7d5f8fff35bbbcc2a0b02f1bbd85d3324fd72d50` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneModule.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneModule.java) | `ee231826bdb98cc54125f5e933684d5d9316f98fc578162f2beb09e40985590c` |
| [src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneStore.java](../src/main/java/fr/cliker/clikerv2/buyzone/BuyZoneStore.java) | `83ab3b1e10e8970ad1f24234ee0abc62b825e4638594d7d00da2100443f97c01` |
| [src/main/java/fr/cliker/clikerv2/combat/AutoClickManager.java](../src/main/java/fr/cliker/clikerv2/combat/AutoClickManager.java) | `0e4a6983a7d23fb732163d1bb19c001a69cd26bd7b804c78a618846956beaf0c` |
| [src/main/java/fr/cliker/clikerv2/combat/DamagePopups.java](../src/main/java/fr/cliker/clikerv2/combat/DamagePopups.java) | `46b21bea7bebe0da649a5461a13e247b7ecb35ee3430bc50d4a97eacd29d89d7` |
| [src/main/java/fr/cliker/clikerv2/combat/DeathLogFilter.java](../src/main/java/fr/cliker/clikerv2/combat/DeathLogFilter.java) | `225281aa212c41d0ef98f79509d3a5df5a6d48b0bd1238d3a0b06307cd32edd8` |
| [src/main/java/fr/cliker/clikerv2/combat/StarterSwordListener.java](../src/main/java/fr/cliker/clikerv2/combat/StarterSwordListener.java) | `eebd5c9bf7eedf5c8b3b7f2dcd32f6699c260b98f7f15cc2ccefde6aefa1a78e` |
| [src/main/java/fr/cliker/clikerv2/combat/SwordCommand.java](../src/main/java/fr/cliker/clikerv2/combat/SwordCommand.java) | `d63002ae28f6f5a634cccd07ca8361cb0dd4129195420ac4fc60338c388a7632` |
| [src/main/java/fr/cliker/clikerv2/comfort/FarmTelemetry.java](../src/main/java/fr/cliker/clikerv2/comfort/FarmTelemetry.java) | `19d8fa7167fca4773ed24ce2cbd0eb88aefbdb2a16142afdc998a98a1b7e7f8f` |
| [src/main/java/fr/cliker/clikerv2/comfort/PlayerComfort.java](../src/main/java/fr/cliker/clikerv2/comfort/PlayerComfort.java) | `2faf1f91884da9d734d208781958b8e727134f0c4ae465b0f48283655408622c` |
| [src/main/java/fr/cliker/clikerv2/comfort/ProgressNavigation.java](../src/main/java/fr/cliker/clikerv2/comfort/ProgressNavigation.java) | `f886e7e4288b0c4c30c6ad1323a62619a2413bf73026282d9d0c30f82857afa4` |
| [src/main/java/fr/cliker/clikerv2/comfort/SessionConvenience.java](../src/main/java/fr/cliker/clikerv2/comfort/SessionConvenience.java) | `e87c87b3f2df1e6937c8e4aa41fe00a9251a6bd4dd5bf50058b30e0e41ce0858` |
| [src/main/java/fr/cliker/clikerv2/comfort/UpgradeGuide.java](../src/main/java/fr/cliker/clikerv2/comfort/UpgradeGuide.java) | `d18e29334d1d4aaa0fd531ed3efe81ce3e8aee0abac8381a5b679b4dfa471f0b` |
| [src/main/java/fr/cliker/clikerv2/config/ConfigFiles.java](../src/main/java/fr/cliker/clikerv2/config/ConfigFiles.java) | `5b3b50311b74006dca9dcc019bfd84592f2f0f74de3315d4e3ce823806d63020` |
| [src/main/java/fr/cliker/clikerv2/config/StoredLocation.java](../src/main/java/fr/cliker/clikerv2/config/StoredLocation.java) | `49336cc9cf11bbca0c01d34b81c1b725bd047510edfe59e15eb70cc62e1bbea0` |
| [src/main/java/fr/cliker/clikerv2/config/YamlFiles.java](../src/main/java/fr/cliker/clikerv2/config/YamlFiles.java) | `f7d02fd04fdef8e2f6c383823212dfefd31af38fb58417976f6a3aa9924fa072` |
| [src/main/java/fr/cliker/clikerv2/cooldown/CooldownCommand.java](../src/main/java/fr/cliker/clikerv2/cooldown/CooldownCommand.java) | `6dcf2122a5537f4854cf0a8963e4f6c8ebea7d24e7ee5d367f6d78262c898b09` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyMenu.java](../src/main/java/fr/cliker/clikerv2/daily/DailyMenu.java) | `755f6f65cfb68fcc699d10953fa280e363a74167555d20ea7653a0d197e2a82e` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyModule.java](../src/main/java/fr/cliker/clikerv2/daily/DailyModule.java) | `7a4247ae4d141e90d8dbe10b0ab2974b47957c979788ea9f3e657884f63427c0` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyNpc.java](../src/main/java/fr/cliker/clikerv2/daily/DailyNpc.java) | `84905fda36e6b4cce8f0a8d801617ede984f7a1684661007ab16c53a443270ec` |
| [src/main/java/fr/cliker/clikerv2/daily/DailyProgress.java](../src/main/java/fr/cliker/clikerv2/daily/DailyProgress.java) | `ee1d65ca541999b328c21857902abc1779cc6b9f1e509514adc31400d83984e4` |
| [src/main/java/fr/cliker/clikerv2/daily/DailySettings.java](../src/main/java/fr/cliker/clikerv2/daily/DailySettings.java) | `9544861e9b36e6bd028e1bf45209fa94de741064b11f9dfe2c8cdb77353c4dec` |
| [src/main/java/fr/cliker/clikerv2/daily/RewardStore.java](../src/main/java/fr/cliker/clikerv2/daily/RewardStore.java) | `5f74f798fcb937f95846ce6b0237378755b2369d698abdd205833a06d1826c48` |
| [src/main/java/fr/cliker/clikerv2/discord/DiscordModule.java](../src/main/java/fr/cliker/clikerv2/discord/DiscordModule.java) | `ef929784325c563d44fbb4b6847671bd1d65585a93033ef43eaaa88103add923` |
| [src/main/java/fr/cliker/clikerv2/display/MoneyScoreboard.java](../src/main/java/fr/cliker/clikerv2/display/MoneyScoreboard.java) | `4cb12ece245bbc8cfe5dc8a40f925174d11dce8aab650b0e8a0153f4b360ee01` |
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
| [src/main/java/fr/cliker/clikerv2/essence/SellMenu.java](../src/main/java/fr/cliker/clikerv2/essence/SellMenu.java) | `b421dfaa733d49c40e5a26adc514fe8cc52693c7dd48a15b3dc1b2e8e7930a23` |
| [src/main/java/fr/cliker/clikerv2/features/Boosts.java](../src/main/java/fr/cliker/clikerv2/features/Boosts.java) | `f15aff58c1f4fb8fc797758bd791204da6fdbe2ba6b1862e14b40073879b47b4` |
| [src/main/java/fr/cliker/clikerv2/features/CreatureSettings.java](../src/main/java/fr/cliker/clikerv2/features/CreatureSettings.java) | `6966af66e5273ef513d51282092eba6db35411c6bf90437cd8e90654c5f9aaa7` |
| [src/main/java/fr/cliker/clikerv2/features/ExpansionModule.java](../src/main/java/fr/cliker/clikerv2/features/ExpansionModule.java) | `035f6b41b6ecc74439beaeafc73ceb0399f5e05cc4132eea8eeb0d0a29cbbd2f` |
| [src/main/java/fr/cliker/clikerv2/features/HuntKillEvent.java](../src/main/java/fr/cliker/clikerv2/features/HuntKillEvent.java) | `600a2a06738091ec967d8954fbfd11c4ba6ce238311977a3e32290e493d11456` |
| [src/main/java/fr/cliker/clikerv2/features/Menus.java](../src/main/java/fr/cliker/clikerv2/features/Menus.java) | `1dfbff572890cd9f5ecbfbc93c372d3234ad4f730fdf65849d9a8b1c23049415` |
| [src/main/java/fr/cliker/clikerv2/features/PartyRaids.java](../src/main/java/fr/cliker/clikerv2/features/PartyRaids.java) | `bd2753002f1de92b55984c17eee206a29ea7cca63256be47b5690ce5d4e5b3fb` |
| [src/main/java/fr/cliker/clikerv2/features/PlainGear.java](../src/main/java/fr/cliker/clikerv2/features/PlainGear.java) | `a5814544697550f6b7c309d6a9100c6d898f42a92a449545cd88bda6b897fbf0` |
| [src/main/java/fr/cliker/clikerv2/features/ServiceNpcs.java](../src/main/java/fr/cliker/clikerv2/features/ServiceNpcs.java) | `d1dd691ed54a237922a09233a74ceef7914420cfc919da88e805392ce310a945` |
| [src/main/java/fr/cliker/clikerv2/features/Settings.java](../src/main/java/fr/cliker/clikerv2/features/Settings.java) | `a8754ba8aff79e12a8a15f3084ed1668c25474fc49b6bdc6495fc6db10dd0181` |
| [src/main/java/fr/cliker/clikerv2/features/ShopStore.java](../src/main/java/fr/cliker/clikerv2/features/ShopStore.java) | `11c0dcd98121a2f70c6b91ea4dc75da3e7a7f7bf1d70ba08ae9886c22be5a962` |
| [src/main/java/fr/cliker/clikerv2/features/Talismans.java](../src/main/java/fr/cliker/clikerv2/features/Talismans.java) | `572e7af4630a8e8939fe88cddb90a71e06ff7e23aa120e00910f12293e733eaf` |
| [src/main/java/fr/cliker/clikerv2/features/TravelAndTools.java](../src/main/java/fr/cliker/clikerv2/features/TravelAndTools.java) | `b1249ce905b2654af021ee9ee430e7ad476e0cac9302a4ea6de207cd13bebdfe` |
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
| [src/main/java/fr/cliker/clikerv2/gear/TierGear.java](../src/main/java/fr/cliker/clikerv2/gear/TierGear.java) | `1106b5300a9c309f0d56c830192f5ecbc03efe415e057d808dc10aab5e07326c` |
| [src/main/java/fr/cliker/clikerv2/gear/TierGearCommand.java](../src/main/java/fr/cliker/clikerv2/gear/TierGearCommand.java) | `a42f067d3958d7488c04db69e9db22bacfd19fcb8818b5b58c8fc7d4c5444634` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramCommand.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramCommand.java) | `5d4bf7ede24059215758406ad4a14691baf81e05a151e039eb895a4220128fc8` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramManager.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramManager.java) | `de3780d0cb22ecf2ed68fd2547838a281b60afe9958e4a28cb93254c1f9a84dc` |
| [src/main/java/fr/cliker/clikerv2/hologram/HologramStore.java](../src/main/java/fr/cliker/clikerv2/hologram/HologramStore.java) | `6fdf300503be19b637e982589656c2b1151268cef34d5470029800f68deceb3c` |
| [src/main/java/fr/cliker/clikerv2/hunting/ContractStore.java](../src/main/java/fr/cliker/clikerv2/hunting/ContractStore.java) | `1b6b5643cfb5a1014444180215a211ceab137326e1e7ebc7aa1104d8b092d273` |
| [src/main/java/fr/cliker/clikerv2/hunting/Gains.java](../src/main/java/fr/cliker/clikerv2/hunting/Gains.java) | `71c26a760810e6d32f85ae41d51e848c7d9389f910ce79e03ee9e80c1dc51869` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntUI.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntUI.java) | `0c773605d9cf80f58d8f22eaf2ecf7afb6142d2fa92538eaf1ca02fc31f0cde2` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntingModule.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntingModule.java) | `d83898747f2ffe903a09aa3e46d60ccc4221374da0de457be29448b25ad77074` |
| [src/main/java/fr/cliker/clikerv2/hunting/HuntingStore.java](../src/main/java/fr/cliker/clikerv2/hunting/HuntingStore.java) | `a18a20a1baaee4285b8bc8cfbec550bfa3c89652647182b9054eda4cccb45254` |
| [src/main/java/fr/cliker/clikerv2/hunting/RewardLedger.java](../src/main/java/fr/cliker/clikerv2/hunting/RewardLedger.java) | `c870722463ab79ca31c7aec5e87c9055e5987d267c8b475f42bae3a2d0684fc0` |
| [src/main/java/fr/cliker/clikerv2/level/LevelDamageBonus.java](../src/main/java/fr/cliker/clikerv2/level/LevelDamageBonus.java) | `d69a167933b5853ce570100ccdca5b13e90e0d8e20744a0e59714f68f1a0f081` |
| [src/main/java/fr/cliker/clikerv2/level/LevelUpListener.java](../src/main/java/fr/cliker/clikerv2/level/LevelUpListener.java) | `feb8cbe2d8ec1a7be446a23c775e4ee4cacea14ac84f82cca68b8ceafcd61b5c` |
| [src/main/java/fr/cliker/clikerv2/level/PlayerLevelStore.java](../src/main/java/fr/cliker/clikerv2/level/PlayerLevelStore.java) | `004bfbe457c56db3c79af03cc190fe1e628cde7671e2456888f9c7a73315b48e` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxCommand.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxCommand.java) | `e33e19a283b3ea5e4537849466fe3dbb5f0a27b78055b28210808851656ff8a3` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxItem.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxItem.java) | `b1c28f85524d0da279acc92845c477f464793f56b87671822c82e9811b7ee64e` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxManager.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxManager.java) | `aec57fb9bcecd561ebe3584b973a396bca4c9939ee0dfb6971b5ae828bc651b0` |
| [src/main/java/fr/cliker/clikerv2/lootbox/LootboxTier.java](../src/main/java/fr/cliker/clikerv2/lootbox/LootboxTier.java) | `a638051a76ab234d16d6ffc1577daf8203c61954b09f8c413cca229794fb7e40` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantCommand.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantCommand.java) | `165056084e520643917ec2247677b0655099893c63678523de376b37f199be87` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantManager.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantManager.java) | `0ee42938223618ebdea4cff4a26847a88a654bf222b2e339bb071a111f6817a8` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantMenu.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantMenu.java) | `8560907f5688fbfaaee26cb4eec0a344b60b63687227e7c350f3e408b7d4e279` |
| [src/main/java/fr/cliker/clikerv2/merchant/MerchantStore.java](../src/main/java/fr/cliker/clikerv2/merchant/MerchantStore.java) | `5962c2ba365494b984c3202e98f8d8143eba814e649458a6c1b624abd1f55ba0` |
| [src/main/java/fr/cliker/clikerv2/mobs/BossEventManager.java](../src/main/java/fr/cliker/clikerv2/mobs/BossEventManager.java) | `6f2f41d914e0077577a2146e5a21d9bf74f0e445e668d7035b1f2172f240c735` |
| [src/main/java/fr/cliker/clikerv2/mobs/CustomMobManager.java](../src/main/java/fr/cliker/clikerv2/mobs/CustomMobManager.java) | `d868342c87c12b286012071edf246e0985627dd24632f27ce768a8b623739b2d` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobKillStore.java](../src/main/java/fr/cliker/clikerv2/mobs/MobKillStore.java) | `5427af864d7ec23efb7d921db4414fc20cb9708048198d4627fcd56f894a923d` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobKillsCommand.java](../src/main/java/fr/cliker/clikerv2/mobs/MobKillsCommand.java) | `f9ad013bfafd3e103ab1ab91b58d1eb1555ec4563fb02d51e7f56ddc8160c6d0` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobSpawnCommand.java](../src/main/java/fr/cliker/clikerv2/mobs/MobSpawnCommand.java) | `3f251a566166e05eacaa553d3d7d3f913f7777f7f1c963dcc79677f62d0964a4` |
| [src/main/java/fr/cliker/clikerv2/mobs/MobSpawnStore.java](../src/main/java/fr/cliker/clikerv2/mobs/MobSpawnStore.java) | `dd52dea6b5d26441f35c17f0b782c06e9177ece897f91b87962b9374d7fc08ca` |
| [src/main/java/fr/cliker/clikerv2/mobs/VanillaSpawnListener.java](../src/main/java/fr/cliker/clikerv2/mobs/VanillaSpawnListener.java) | `618899ace54336ecb13d5def2f4d43309cf4c2070918f6e698bae1f1aa7d33ac` |
| [src/main/java/fr/cliker/clikerv2/npc/ZnpcsNpc.java](../src/main/java/fr/cliker/clikerv2/npc/ZnpcsNpc.java) | `e01acd0818383873dd10c1f73e83c8a25c16bf3bf36fd7c2c2f75ccfa286628a` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostBlocks.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostBlocks.java) | `172273873ff4f9567f0777297f4d84819390be596632f633b5edc7c0e8d774a3` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostModule.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostModule.java) | `020facb093d62aa434461018317275fa83a40324e6a9aefa2a6c6eaa5ce38113` |
| [src/main/java/fr/cliker/clikerv2/outpost/OutpostStore.java](../src/main/java/fr/cliker/clikerv2/outpost/OutpostStore.java) | `8eb61b5674857a60756fc1679e0f33b4af46b23195907330ec30b291148a5bb8` |
| [src/main/java/fr/cliker/clikerv2/outpost/WorldInventories.java](../src/main/java/fr/cliker/clikerv2/outpost/WorldInventories.java) | `eb5ea4705b75252a0981248d26b5a9bcaf220e4bbdb30bd617617013cc3f3de0` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeChatFormatter.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeChatFormatter.java) | `da029d3d3a4b9f3d1a3dacde609960cc5115248ae84517e9e83f3a47d4ea21fc` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeCommand.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeCommand.java) | `0c3859ef9f620fc2bf32f8758fe06faf0acd11fe13a5389ef6bfd0dad400d33d` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeManager.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeManager.java) | `c31f900106b31c1484b8b70f6b47eb6a4321b6e12163b6542f19ee72409d9312` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeMenu.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeMenu.java) | `1abe9bab813485ee7712b89edaba7208b4214bc2dd7284c10f68b4c794d931fa` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeStore.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeStore.java) | `62c420ff865e86ab6f95667fea266602b9e6d69e8106cc4a05fe8d428b049c8e` |
| [src/main/java/fr/cliker/clikerv2/prestige/PrestigeType.java](../src/main/java/fr/cliker/clikerv2/prestige/PrestigeType.java) | `1a545bbbbc13881341e2ccaeca804e5c91827c3643286d6086ef46f329a03143` |
| [src/main/java/fr/cliker/clikerv2/raid/ProportionalRewards.java](../src/main/java/fr/cliker/clikerv2/raid/ProportionalRewards.java) | `bc7e8702ed54d925827c14c2e61b89e0057a0de885656c80d2d73ffa33fd819c` |
| [src/main/java/fr/cliker/clikerv2/raid/RaidModule.java](../src/main/java/fr/cliker/clikerv2/raid/RaidModule.java) | `4380af26e0186784e30421055384534c54ddddb675f956fc5510d8229360ce09` |
| [src/main/java/fr/cliker/clikerv2/referral/ReferralModule.java](../src/main/java/fr/cliker/clikerv2/referral/ReferralModule.java) | `088a78f8cffa45ad7c43a8a3af32bda634abaaf55f380f6204808c21df598680` |
| [src/main/java/fr/cliker/clikerv2/referral/ReferralStore.java](../src/main/java/fr/cliker/clikerv2/referral/ReferralStore.java) | `d0a4a6330c10d0dde7b97d25a1a5e7db918a7a79d35c692ec0e4d5ac531d1ba6` |
| [src/main/java/fr/cliker/clikerv2/rewards/ClaimService.java](../src/main/java/fr/cliker/clikerv2/rewards/ClaimService.java) | `2e9c8d47df37f93f890d7d97771eb17aa7a2499098e8352351de7432bcab4070` |
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
| [src/main/java/fr/cliker/clikerv2/ui/GainBar.java](../src/main/java/fr/cliker/clikerv2/ui/GainBar.java) | `d35fa63373256c5761a844774f439e3db34b18bb9645e6a4b06a12a9020e11d1` |
| [src/main/java/fr/cliker/clikerv2/ui/MainMenu.java](../src/main/java/fr/cliker/clikerv2/ui/MainMenu.java) | `afafdb30873ecceb911653f2a34e38f3f4333b5ea5e8bde8280b7d7ff6abd7f0` |
| [src/main/java/fr/cliker/clikerv2/ui/MenuIcons.java](../src/main/java/fr/cliker/clikerv2/ui/MenuIcons.java) | `0d1e12ca821d04282eb33cba8de9917f958b8b585832bd29c37f33e788f50c58` |
| [src/main/java/fr/cliker/clikerv2/ui/MenuNavigation.java](../src/main/java/fr/cliker/clikerv2/ui/MenuNavigation.java) | `5e95103a79347efeda8a6e06829b9312682135cb574707d8355a7765a7f999df` |
| [src/main/java/fr/cliker/clikerv2/ui/ViewRequests.java](../src/main/java/fr/cliker/clikerv2/ui/ViewRequests.java) | `730ae2d62b51f2d3b66545cf7bb43d771bdd3262d45409bf110cf9270da1d410` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeCommand.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeCommand.java) | `4c89c6bf39cc2fd688deb507878891e668b53b1d8d2faecc093e5039387fcd28` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeManager.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeManager.java) | `89410b7bcabe87a4cf467dec763c89ee892dd0f1b846337d5869fd00817a36c7` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeMenu.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeMenu.java) | `4114f5f450b5cf67e30fc0c7978e7a6a7bcb79b052630b1fb281d05312018b16` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeQuantityMenu.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeQuantityMenu.java) | `1947cf56e83357e4404927e431fd373b0d4b8f0915968c3573ca9b26ed50dd18` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeStore.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeStore.java) | `9c2dd56f3adfbee795489094642cc183ea52090ef1d632c3c9230d90d89ebd94` |
| [src/main/java/fr/cliker/clikerv2/upgrade/UpgradeType.java](../src/main/java/fr/cliker/clikerv2/upgrade/UpgradeType.java) | `25576171b9d224f98804d504d12dca69b49b1fef7c4e77c8a1e69e7190f3f7a5` |
| [src/main/java/fr/cliker/clikerv2/world/ActivityWorlds.java](../src/main/java/fr/cliker/clikerv2/world/ActivityWorlds.java) | `dc5fc09bc4a93b4fd3f2e787dbf08fd5709739da2356439b8ed1e6407fd961b1` |
| [src/main/resources/activites/raids-groupes.yml](../src/main/resources/activites/raids-groupes.yml) | `6c6ea9c658b62d08ffb8d88ac3851f7a94cf401b06e8d55228502715850193ae` |
| [src/main/resources/affichage/classements.yml](../src/main/resources/affichage/classements.yml) | `7ad350ced24b0c34f47b0ecf8575df66a79e01d7b93f2f4e8929788d77daf245` |
| [src/main/resources/avantpostes.yml](../src/main/resources/avantpostes.yml) | `127652f1615a631c6c2733f0a5022e57bc5f5c55dd3c4a0497757044afb3bf12` |
| [src/main/resources/boss.yml](../src/main/resources/boss.yml) | `23e10f84e75e37b9776e8a0783644b0ad2a8f8fef0ae8612f8d46b4b8d7bf333` |
| [src/main/resources/boutiques/catalogues.yml](../src/main/resources/boutiques/catalogues.yml) | `99154c2bdb526d8a76c6d5d4834713a9af8a692fe5509518c98ac06fed5dff07` |
| [src/main/resources/buyzone.yml](../src/main/resources/buyzone.yml) | `2f62e85a731a89701a03ae0d9ef5d09176a94fa38d0d8b37096e14ea8f63c919` |
| [src/main/resources/chasse.yml](../src/main/resources/chasse.yml) | `96bc80fb7ecaf4209c6b6f9532fead647dc1018a6e12621170bdd7779d6cc014` |
| [src/main/resources/claim.yml](../src/main/resources/claim.yml) | `be8c68578edc76bd869a613db94b6eea917add5935953c9a66a5a5486a289d35` |
| [src/main/resources/config.yml](../src/main/resources/config.yml) | `4a5fc39462f9e1715f210eba96a4fb3c1661a555ec0ead83b5a163d08e6e22de` |
| [src/main/resources/daily.yml](../src/main/resources/daily.yml) | `58078d54a85254cc41e9745287872d18caeec3d47ca3d22a42a3c401bce467ea` |
| [src/main/resources/damage-popups.yml](../src/main/resources/damage-popups.yml) | `8a176eb44795df3fe7705720581d817073c7886cf4960a6ab9a6742a45c23256` |
| [src/main/resources/discord.yml](../src/main/resources/discord.yml) | `7742894dc5e91ba3973e8e55cee44f7f8243e51ea11be5af8e750383a0db1777` |
| [src/main/resources/enchants.yml](../src/main/resources/enchants.yml) | `37ae5a5ecac5c7e8a571407fd5c1d4ee8264dc15cbc12c7173864b99b95feef0` |
| [src/main/resources/gui/icons.yml](../src/main/resources/gui/icons.yml) | `f59c12d4f079b636506049ffe20f79b0560f2b55f1732da70bc1320faae92714` |
| [src/main/resources/gui/menu.yml](../src/main/resources/gui/menu.yml) | `b6077bda73cc127c4c2ddec03747834ed3ec86d8c889f7da4e049352dffb50c4` |
| [src/main/resources/lootboxes.yml](../src/main/resources/lootboxes.yml) | `5b2d966ee0111ba4df62a70eb4a8c7ca0cc60fd5e54117624ab15e4cc2b5e2e3` |
| [src/main/resources/mobs/creatures.yml](../src/main/resources/mobs/creatures.yml) | `ec41f2fb21245ab900ca72732736cbe7b2fe1721cb5b61b23032d4a3e8ebe98a` |
| [src/main/resources/navigation/warps.yml](../src/main/resources/navigation/warps.yml) | `b72e6e48fe5395f2742e445f27238b03cd334328a5832f0135ad88e2045ab30f` |
| [src/main/resources/parrainage.yml](../src/main/resources/parrainage.yml) | `7d0a849eb624db2866de7592ce77405b46939495acb9917c425d3e5d4958ca24` |
| [src/main/resources/plugin.yml](../src/main/resources/plugin.yml) | `ed794f43235b4bec19c98a24515b7a83ad22833e7197e05f0eba60d693c9137e` |
| [src/main/resources/pnj/marchands.yml](../src/main/resources/pnj/marchands.yml) | `4612239b5da619690f46c168065705b0ea3567df69f39679f284c29c3d8e977e` |
| [src/main/resources/progression/talismans.yml](../src/main/resources/progression/talismans.yml) | `217b2c8fa04111239fec852abc16667343c3c29e50d7b6d8df01384a5ce51db0` |
| [src/main/resources/raid.yml](../src/main/resources/raid.yml) | `565d3165a53e29189ae99c1416404bc23774cdfb8c188df0385554c42366be0d` |
| [src/main/resources/scoreboard.yml](../src/main/resources/scoreboard.yml) | `7df39b58c9c84b0faa72f592a841e6d19f35f326a2fbf6c9333a7385c06c0d90` |
