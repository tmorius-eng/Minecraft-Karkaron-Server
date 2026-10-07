# SÜLD item, equipment & loot — forensic audit (before the item engine)

Baseline: `59ce447` (skill tree combat-verified). What existed, what it really did, and what the item engine
reuses instead of duplicating.

## What existed

| Area | Where | What it really did |
|---|---|---|
| Item template | `suld-api/item/ItemDefinition` | id, name, material, rarity, custom model data, base stats + per-level growth, soulbound flag. 8-field record. |
| Item instance | `suld-api/item/ItemInstance` | definition id, **UUID per item** (already player-independent), rarity, item level, stat map, soulbound, upgrade level, provenance. |
| Rarity | `suld-api/item/ItemRarity` | the 8 required tiers (COMMON…UNIQUE) with name, colour and a drop weight. **No mechanical difference** besides colour/name: weights were not used by any roller. |
| Item stats | `suld-api/item/ItemStat` | 6 stats (attack, health, armor, crit chance, crit damage, resource). |
| Stack identity | `suld-plugin/item/ItemFactory` | writes id/uuid/rarity/level/stats/soulbound/upgrade into the **PersistentDataContainer** and reads it back. Tooltip: rarity line + stat lines. No schema version, no validation on read: any stack with the two keys was accepted with whatever stats it claimed. |
| Content | `SuldContent`, `WorldContent`, `DungeonContent`, `ClassWeapons` | ~13 hard-coded definitions (6 weapons, materials, 2 trinkets) + 20 class-weapon tiers; 19 loot tables in Java. |
| Loot | `suld-api/loot/LootTable/LootEntry/LootRoller` | independent drop chance per entry + item level range; injected `Random`. No weights, quantities, tiers, rarity rules, class weighting, guaranteed or rare drops. Rolled in `CombatListener` (mob kill) and `DungeonService` (rewards). |
| Upgrade | `suld-api/item/Reforge` + smith NPC | +1 item level for coins + 2 band materials; works. |
| Sell | `Menus.sellAll` | fixed price list for 5 material ids. |
| World-unique | `relic/*` (api + plugin) | **complete singleton system**: DB record per relic, compare-and-set on a version that is also the copy's generation (older copies are destroyed), shrine claim ritual, seize on PvP death, offline return, container purge, history table, admin give/return/recover/history. Хөх Сүлд and Алтан Гэрэгэ. Stats: only an EXP bonus. |
| Trade | `TradeService` | refuses relics (also inside shulkers/bundles) and soulbound items. |
| Death | `DeathService` | keeps armour, class weapons and soulbound items; wears durability. |
| Combat stats | `CombatListener` | **only the main-hand item's ATTACK and CRIT_CHANCE were used.** |
| Player stat pipeline | `SkillTreeService` / `SkillBuild` | the combat-verified pipeline (313 measured checks): max health, movement, armour, knockback, crit, crit damage, spell damage, reduction, lifesteal, resource, thorns, cost, heal power, EXP, loot, dodge, cooldown reduction — all from one `SkillBuild`. |
| Persistence | `JdbcProfileRepository`, migrations V1–V10 | profile row with JSON columns (skill_data V10). Vanilla inventories/armour/off-hand are saved by the server in player data. |
| Audit | `AuditLog` (+ JDBC) | append-only, sanitised. |

## Gaps (what was lore only, or missing)

* HEALTH, ARMOR, CRIT_DAMAGE, RESOURCE item stats were **tooltip text only**: nothing read them.
* No equipment slots beyond "the item in the main hand": armour pieces, off-hand, accessories and relic gave nothing.
* No item types, level or class requirements, affixes, sets, binding states other than a soulbound flag, durability rules, sell values, stack rules, schema version.
* A forged stack (any item with `suld:item_id` + `suld:item_uuid`) was accepted with any stats; no impossible-stat check, no duplicate detection.
* Rarity had no mechanical effect; loot tables had no weights/quantities/tiers; `MobTier` (NORMAL…WORLD_BOSS) existed but loot ignored it.
* Content in Java constants, not data files; no validator.

## Decisions (reuse, no duplicate managers)

* **ItemInstance / ItemDefinition / ItemRarity / ItemStat are extended in place**, not replaced by parallel classes; old stacks
  (no schema version) are read and migrated (fraction crit values → percent points).
* **ItemFactory stays the single stack ↔ instance codec**; it gains a versioned codec and validation.
* **Item stats feed the existing `SkillBuild`** (as bonus stats/modifiers) so every measured combat hook — crit, dodge,
  lifesteal, health attribute, cooldowns, EXP, loot — applies to equipment without a second stat system.
* **UNIQUE items are the relic system**: the RELIC equipment slot is the relic the player bears; relic stats come from the
  item catalog; ownership, generation, history, recovery stay in `RelicService`.
* **LootTable/LootEntry/LootRoller are replaced** by the weighted engine (same package), and every caller moves to it.
* Content moves to validated JSON (`items/*.json`) loaded like the skill tree; the Java constants become lookups.
