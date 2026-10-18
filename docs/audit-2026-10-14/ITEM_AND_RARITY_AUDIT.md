# SÜLD — Item and rarity audit (audit 2026-10-14)

Sources: `suld-api/src/main/resources/items/*.json`, `ItemCatalogLoader`, `ItemGenerator`, `LootEngine`,
`ItemEconomy`, `balance/Economy`, `suld-plugin/.../item/*`, `gui/Menus` (sell), `npc/NpcService` (smith). The
web-session `items.md` is not in the repository; IT IDs follow the execution prompt and were re-verified in code.

## 1. Catalog (234 definitions)

| File | Items | common | uncommon | rare | epic | legendary | ancient | mythic | unique |
|---|---|---|---|---|---|---|---|---|---|
| weapons.json | 54 | 10 | 6 | 16 | 7 | 5 | 5 | 5 | — |
| armor.json | 27 | 8 | 4 | 7 | 4 | 4 | — | — | — |
| class_armor.json | 120 | — | 20 | 20 | 20 | 20 | 20 | 20 | — |
| jewelry.json | 10 | 1 | 3 | 3 | 1 | 2 | — | — | — |
| materials.json | 21 | 8 | 8 | 3 | 1 | 1 | — | — | — |
| relics.json | 2 | — | — | — | — | — | — | — | 2 |
| **total** | **234** | 27 | 41 | 49 | 33 | 32 | 25 | 25 | 2 |

(Base rarity of each definition; most gear rolls up to its `maxRarity`.) Also: 22 affixes, 33 sets, 52 recipes,
46 loot tables. The 30 class weapons (5 classes × 6 tiers) are in weapons.json; the 120 class armour pieces are
generated (`tools/content/gen_class_armor.py`).

## 2. Rarity table and drop bands

| Band (`tiers.json`) | Before | After (IT-02) |
|---|---|---|
| NORMAL | common 75 / uncommon 25 | unchanged |
| ELITE | uncommon 65 / rare 35 | uncommon 55 / rare 35 / epic 10 |
| CHAMPION / MYTHIC | 70/30, 75/25 | unchanged (no mob uses these tiers) |
| BOSS | legendary 70 / ancient 25 / mythic 5 | rare 50 / epic 38 / legendary 10 / ancient 2 |
| WORLD_EVENT | ancient 75 / mythic 25 | unchanged — see roadmap P1 (2026-10-18: now rare 52 / epic 38 / legendary 10) |
| DUNGEON | uncommon 40 / rare 40 / epic 17 / legendary 3 | uncommon 35 / rare 45 / epic 17 / legendary 3 |
| QUEST / CHEST / CRAFT | — | unchanged |

The new BOSS / ELITE / DUNGEON bands are exactly the simulator's designed v2 bands (`ProposedRules.catalog`,
`docs/GEAR_PROGRESSION_SPEC.md`), not new numbers. The prompt's example (rare 45 / epic 24 / legendary 6) was not used
because the simulator already holds the owner-reviewed v2 values. `SimulationTest.liveBossBandIsLegendaryOrBetter`
pinned the old band (> 90 % legendary+) as a golden of the defect; it is now `liveBossBandIsTheV2Band` (< 20 %
legendary+, none mythic, some legendary) — a stronger assertion, not a weaker one.

## 3. IT findings and fixes

| ID | Finding | Fix | Test |
|---|---|---|---|
| IT-01 / IT-15 | No loot pool carries WEAPON; `weapon.talyn_ild`, `khasar_soyo`, `altai_jad`, `anchin_num` were in no table and no recipe → set `set.talyn_anchin` incomplete | Named rare drops: Талын Илд ← Говийн Чоно 2 %, Дээрэмчин 3 %; Саарлын Соёо ← Саарлын Агуй completion 8 %, loot.khasar 5 % (was rolls 0 / rare [] ); Анчны Нум ← Хангайн Баавгай (elite 14) 4 %; Алтайн Жад ← Алтайн Аварга (elite 23) 5 %. The generator lifts a drop's level to the item's own level. | `ItemObtainabilityTest` |
| IT-02 | BOSS band 70 % legendary | v2 bands (above) | `liveBossBandIsTheV2Band` (simTest) |
| IT-03 | ON_PICKUP (ANCIENT/MYTHIC forced, and data `ON_PICKUP`) bound only on a ground pickup; rewards, crafts, mailbox items stayed tradable; `bindOnEquip` skipped them | `ItemService.bindForReceiver/stackFor` binds on delivery (give, dungeon reward, world boss, world event, craft order incl. mailbox); `bindOnEquip` binds ON_PICKUP as a fallback. Ground drops keep `stack()` (they bind to whoever picks them up). | `PickupBindingTest` |
| IT-04 | Class weapons had `perLevel: {}`: flat damage from tier level to tier level; a level-20 classless rare sword (8 + 1.5/level ≈ 37) dealt ~4× a tier-2 class weapon (9) | weapons.json class weapons `perLevel.damage` = 1.0 (Баатар, Мэргэн, Дархан), 1.2 (Бөө), 0.8 (Хүлэгчин) — the bases' class ratios; `ClassWeapons.upgrade` rebuilds within a tier when the level outgrew the item (keeps identity, binding and the smith's +N) | `ClassWeaponScalingTest` (grows in tier, never drops at tier-up, level-20 classless < 1.6×) |
| IT-05 | Class weapon rolls used `Rng.threadLocal()` on every rebuild and on `/classgear recover` — a free affix re-roll | `ClassWeapons.rollSeed(owner, item, tier)` seeds upgrade and recover | `ClassWeaponRefreshTest` |
| IT-07 | Reforge / repair should save | The plugin has no reforge or repair action (`Economy.reforgeCoins`, `ItemEconomy.repairCost` have no caller) — nothing to save; whetstone repair (below) saves | — |
| IT-08 | Affix reroll (Эртний Хас) saved the coins only | player file (jade + affixes) then the coins | manual S-05 |
| IT-09 | Accessory equip saved the slot only (crash: worn and still in the bag); unequip gave the jewel unsaved (crash: lost) | equip: player file then SQL; unequip: SQL committed, then bag + file (mailbox if full / offline; kept worn if the save failed) | manual S-06 |
| IT-12 / IT-20 | `ItemType.CONSUMABLE` unused | 4 consumables (below) | `ConsumablesTest` |
| IT-16 | T4 class armour had no armour growth (the legendary proxy has less armour at 48 than at 36 → flat fit): inside the tier T3 overtook T4 (helmet from AL 48, chest ~46) | generator: a flat armour fit takes the next tier's slope; T4 data regenerated by hand to the same rule (helmet/boots 0.052, chest 0.111, leggings 0.082; mean at AL 36 unchanged). T4 stays above T3 to AL 60 and below T5 from AL 48 | `ClassArmorScalingTest` |
| IT-17 | Саарлын Зүрх (legendary, level 5, 6 % from the first dungeon) salvaged into 5 Тэнгэрийн Чулуу (the T6 armour material) | `ItemEconomy.salvageRarity`: legendary-or-better below item level 30 salvages as epic | `SalvageAndSaleAuditTest` |
| IT-19 | Тэнгэрийн Чулуу declares `sell: 4` but `sellPrice` returned 0 for every legendary+ item, so the data price was dead (tooltip, `/item sell`, sell menu all agree on 0) | the legendary "salvage only" rule now applies to gear only; priced stackables sell at their data price | `SalvageAndSaleAuditTest` |
| IT-06/10/11/13/14/18 | — | Named in the prompt's range but not specified; the source items.md is absent. Not invented. | — |

Note on IT-16 data: `class_armor.json` T4 values were written by hand from the generator's formula (base = mean@36 −
slope × 35, range = base / k × [0.88, 1.12], k = 1.048 for legendary). A re-run of `gen_class_armor.py` may differ in the
second decimal from rounding of the stored T5 slopes — expected, not a defect. Run it once in Phase 7 to confirm.

## 4. New consumables (IT-12 / IT-20)

Rules: `suld-api/.../item/Consumables.java` (pure, tested). Runtime: `suld-plugin/.../item/ConsumableService.java`.
Data: `items/materials.json` (type `CONSUMABLE`, stack 16). Tooltip line from `ItemTooltip` («Баруун товч: … · N сек
завсарлага»).

| Item | Name | Effect | Cooldown | Source |
|---|---|---|---|---|
| item.airag_flask | Айраг | 25 % of the class pool at once; +10 % resource regeneration 60 s (a new flask replaces, never stacks) | 30 s | Говийн Чоно 4 %, Хангайн Баавгай 10 % |
| item.steppe_salve | Талын Эмийн Ургамал | 20 % max HP over 5 s (one heal per second); cleanses Poison, Wither, Slowness | 20 s | Дээрэмчин 5 %, Хангайн Баавгай 10 % |
| item.ovoo_incense | Арц Хүж | −8 % damage from spirits (mob ids with `suns` / `savdag` / `lus`) for 180 s, refreshes; eases a death wound by 10 minutes of healing (`DeathService.healWound`) | 30 s | Элсний Сүнс 5 % |
| item.darkhan_whetstone | Дарханы Билүү | 35 % of max durability back on the most worn SÜLD weapon at hand (off hand / hotbar) or worn armour piece; refused in Kharkhorum, within 5 s of being hurt, or with nothing to repair | 10 s | Дээрэмчин 5 % |

Order of a use (anti-dupe): checks (soul, cooldown, place, target) → one item off the hand → `PlayerDataSaves.now` →
effect → audit record. The vanilla use (drinking the potion, planting the reed) is denied in both hands, and
`PlayerItemConsumeEvent` is cancelled for a consumable as a second guard.

## 5. Class equipment build analysis

* **Weapon.** The class weapon is the guaranteed baseline (soulbound, never sold/salvaged). With IT-04 it grows ~1
  damage per level, so a classless weapon of the same level is an upgrade of ~1.3× at rare, not 4×: drops matter
  without making the class weapon worthless.
* **Armour.** Class armour follows the armour level (not the character level), T1–T6 at AL 1/12/24/36/48/60; with
  IT-16 each tier now grows inside its span and never falls behind the tier below.
* **Jewelry / off-hand.** Pooled by level and class affinity (`LootEngine.weightFor`); the only legendary low-level
  jewel (Саарлын Зүрх) can no longer seed end-game materials.
* **Binding.** Common–epic free; legendary binds on equip; ancient/mythic bind on receipt (now on every path);
  unique soulbound.
