# SÜLD item engine — test plan and evidence

## 1. Unit tests (suld-api, pure, deterministic `Rng.seeded`)

| Test | Proves |
|---|---|
| `ItemCatalogLoaderTest` | the bundled catalog loads with 0 issues (89 items, 22 affixes, 3 sets, 21 tables, 10 tier bands, 4 recipes); every broken field is reported with file + JSON path (unknown stat, bad range, unique in loot, relic not unique, unknown set piece, affix on common, duplicate id…) |
| `ItemGeneratorTest` | every definition × every allowed rarity × 25 levels (> 3000 items) is generated genuine; affix count per rarity, one affix per stat; roll floors; rarity factor relative to the definition's own rarity; upgrade keeps identity and rolls; whole-number RESOURCE_MAX rolls and legacy fractions |
| `ItemValidatorCodecTest` | impossible stats/affixes, wrong rarity, too many affixes, unknown ids, missing soulbound are refused; codec round trip; schema-1 migration (crit fractions ×100) |
| `EquipmentTest` | slot rules, class/level/binding/broken gates, stat sums, set bonuses 2–5 pieces, inactive reasons, gear score |
| `RarityTooltipEconomyTest` | rarity mechanics table, tooltip sections (requirements red/green, set progress, comparison arrows), sell/salvage values |
| `loot/LootEngineTest` | guaranteed + weighted + rare, nothing-weight, quantities, tier bands, class weighting, level clamp, never UNIQUE, distribution over 20 000 seeded rolls |
| `skill/tree/SkillBuildPlusTest` | equipment bonus merges into the skill build without touching node effects |
| `ReforgeTest`, `ItemInstanceTest` | upgrade path and instance invariants after the schema change |

## 2. Integration tests (suld-plugin, real PostgreSQL, opt-in `SULD_TEST_PG_*`)

`JdbcEquipmentIT`: V11 migration; nothing worn → NULL → `EquipmentState.NONE`; two accessories (affixed, bound) survive
save/load bit-for-bit; unequip persists; a newer/corrupt `equipment_data` row stops the load (saving would erase it).
Run with the other ITs (`JdbcSkillStateIT`, `JdbcRelicRepositoryIT`, …) against a throwaway cluster.

## 3. Live Paper 1.21.11 (dev server, offline-mode bot — local only)

`/skillsadmin qa <bot> items` — 20 measured checks on a real player:

* 16 stat cases: each stat added by a real item in its real slot and measured through the game (max-health attribute,
  damage dealt to a dummy, armour reduction, attack-speed modifier, crit/dodge/lifesteal rates, regen ticks, EXP and loot
  rolls, cooldown length, resource capacity/regen, spell damage).
* gates: broken chestplate gives nothing; a level-50 helmet at level 30 gives nothing; a Мэргэн weapon held by a Бөө gives
  nothing; the Бөөгийн Ёслол 2-piece bonus (+15 resource) appears with the second piece.
* inventory and accessories are snapshotted and restored.

`/skillsadmin qa <bot> all` — the full combat verification (skill tree + items) as the regression.

Restart persistence (`persistbot.js`, outside the repo): join → `/itemsadmin give` amulet + chestplate + helmet →
`/item equip` each → `/itemsadmin inspect` → quit → server stop/start → rejoin → inspect again; the slots, UUIDs, rarity,
level and stat totals must be identical.

## 4. Findings from the live runs

| Run | Result | Action |
|---|---|---|
| items #1 | 18/20: RESOURCE_MAX item gave +10 while its tooltip said +10.1 (resource pool is an integer) | **product bug fixed**: `ItemStat.integral()`, rolls rounded down, legacy fractions count/show whole; unit test added |
| all #1 | 328 pass / 4 fail / 1 n/m: four BURN checks read 0 fire ticks | **QA harness fix**: rain extinguished the open-sky arena; the harness now forces clear weather for the run and restores it |
| all #2 (in rain) | 331 / 1 / 1: Олон Сум DAMAGE_PCT read ×0.997 for +20% | history showed ×1.09–1.13 in earlier runs too; 4 reruns ×1.13–1.34: vanilla critical-arrow randomness. **QA harness fix**: measured volley arrows fly non-critical → ×1.1999 |
| all #3 (in rain, final) | **332 pass / 0 fail / 1 not measurable** of 333 | the one n/m is the heal radius (needs a second player) |
| restart | join → equip ×3 → restart (PostgreSQL) → rejoin: inspect output identical | — |
| actions | `/item sell` +113 ₮, `bind` → bound, `salvage` → material, `destroy`, `craft` 4 pelts + 20 ₮ → helmet; each behind `confirm` | — |
| menus | `/equipment` 9 slots + panels; empty accessory → picker → equip; click → unequip; `/items` lists | — |

Results of the final runs are recorded in `audit/item-system-status.json`.

## 5. Manual QA (needs a real client)

Item tooltips and colours, ring sprites, `/items` and `/equipment` menu layout and clicks, inactive-reason action bar,
legendary-drop broadcast. Steps: join the dev server, `/itemsadmin give <you> jewel.altan_bugj epic 20`, open `/items`,
click the ring, open `/equipment`, check ACCESSORY_1 and the stat panel, `/item compare` with another ring in hand.
