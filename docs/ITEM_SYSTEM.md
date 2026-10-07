# SÜLD item, equipment & loot engine

Architecture, rules and operations. The pre-engine forensic audit is in [ITEM_SYSTEM_AUDIT.md](ITEM_SYSTEM_AUDIT.md);
the test evidence is in [ITEM_SYSTEM_TEST_PLAN.md](ITEM_SYSTEM_TEST_PLAN.md) and `audit/item-system-status.json`.

## Layers

| Layer | Where | Responsibility |
|---|---|---|
| Model (pure, no Bukkit) | `suld-api/.../item/*` | `ItemDefinition`, `ItemInstance` (schema 2), `ItemRarity`, `ItemStat`, `ItemType`, `EquipSlot`, `Binding`, `Affix`/`RolledAffix`, `ItemSet`, `Recipe`, `StatRange` |
| Content | `suld-api/src/main/resources/items/*.json` | 89 items, 22 affixes, 3 sets, 10 loot-tier rarity bands, 21 loot tables, 4 recipes; loaded and validated by `ItemCatalogLoader` (exact file/field issues, the skill tree's `Issue` type) |
| Rules (pure) | `ItemGenerator`, `ItemValidator`, `ItemCodec`, `Equipment`, `ItemTooltip`, `ItemEconomy`, `loot/LootEngine` | rolling, legality, the stored document, what the equipment adds, tooltip lines, sell/salvage, weighted loot |
| Plugin | `suld-plugin/.../item/ItemFactory` | the single ItemStack ↔ instance codec (PDC `suld:item`, schema 2; schema-1 stacks migrated on read) |
| | `item/ItemService` | catalog holder, generation, loot rolls and delivery, validation cache, duplicate guard, quarantine |
| | `item/EquipmentService` | the nine slots, debounced recompute, binding on equip, accessory equip/unequip, events |
| | `command/ItemCommands`, `gui/ItemMenus` | `/item`, `/items`, `/equipment`, `/loot`, `/itemsadmin` |
| Persistence | `V11__equipment.sql` (`equipment_data`), `PlayerProfile.equipment()` | accessory slots (the only slots the game does not save itself) |

No second stat system exists: equipment produces an `Equipment.Bonus` that `SkillTreeService.refreshRuntime` merges into the
player's `SkillBuild` (`SkillBuild.plus`, item procs indexed from 100000). Every combat hook verified for the skill tree
(313 measured checks) therefore applies to items unchanged.

## Identity

An item is identified by its **definition id** (`weapon.khaany_ild`) and a **UUID** per copy, never by name or lore. The
whole instance is one JSON document in the PDC key `suld:item`, plus `suld:item_id`/`suld:item_uuid` for older readers.
Lore is generated output only; nothing parses it. Stackable materials use a stable name-derived UUID per
definition + rarity so identical copies stack.

Stored fields (schema 2): definition id, uuid, rarity, item level, rolled stats, rolled affixes, soulbound, bound-to,
upgrade level, provenance (source), schema version. Definition fields: name, lore, material, model, type (→ slots),
rarity range, level requirement, class restriction, base stat ranges, per-level growth, durability, binding,
tradable, stackable/max stack, sell value, set, unique effects, lootable.

## Rarity mechanics

| Rarity | Affixes | Stat × | Roll floor | Durability × | Sell × | Salvage | Binding |
|---|---|---|---|---|---|---|---|
| COMMON | 0 | 1.00 | 0.00 | 1.0 | 1 | 1 | definition |
| UNCOMMON | 1 | 1.10 | 0.00 | 1.2 | 2 | 1 | definition |
| RARE | 2 | 1.25 | 0.10 | 1.5 | 5 | 2 | definition |
| EPIC | 3 | 1.40 | 0.25 | 1.8 | 12 | 3 | definition |
| LEGENDARY | 4 | 1.60 | 0.40 | 2.2 | 30 | 5 | binds on equip, announced |
| ANCIENT | 4–5 | 1.80 | 0.55 | 2.6 | 60 | 7 | binds on pickup, announced |
| MYTHIC | 5 | 2.00 | 0.70 | 3.0 | 120 | 10 | binds on pickup, announced |
| UNIQUE | 0 | 2.00 | 1.00 | never wears | not sold | not salvaged | soulbound, one global copy |

The stat multiplier is relative to the definition's own lowest rarity, so fixed-rarity items (class weapons) keep their
exact numbers. The roll floor is where in the stat range a roll may start (higher rarity never rolls a weak item).

## Stats (all wired into gameplay)

MAX_HEALTH, HEALTH_REGEN, DAMAGE (flat attack in the SÜLD hit formula), ARMOR, MOVE_SPEED, ATTACK_SPEED, CRIT_CHANCE,
CRIT_DAMAGE, DODGE, LIFESTEAL, XP_GAIN, LOOT_CHANCE, COOLDOWN_REDUCTION, RESOURCE_REGEN, RESOURCE_MAX, SPELL_DAMAGE.
Percent stats are stored in percent points. RESOURCE_MAX is a whole number (the resource pool is an integer): it rolls
rounded down, and items made before that rule count and display the whole part (found by the live QA, see the test plan).

## Equipment slots

HEAD, CHEST, LEGS, FEET, MAIN_HAND, OFF_HAND (read from the player's own inventory: the game saves them), ACCESSORY_1,
ACCESSORY_2 (profile `equipment_data`), RELIC (the genuine copy of the relic the player bears). An item in a slot is
inactive, with a reason shown in the menu, action bar and admin inspect, when it is in the wrong slot, of another class,
above the player's level (`max(levelReq, itemLevel)`), bound to someone else, broken, or unknown.

Flow: any change (armour change, scroll, swap, drop, pickup, click/drag/close, respawn, level/class change, damage that
breaks an item) marks the player dirty; once per tick dirty players are re-read; if the fingerprint (class, level, slot →
uuid/level/upgrade/binding/broken) differs, the skill build is rebuilt (attributes, procs, spell mods) and the HUD
refreshed. A 40-tick fingerprint check is the safety net. Reconnect: armour/hands come back with the vanilla player
data, accessories from the profile, the relic from the relic record; the join sweep then recomputes.

## Affixes and sets

22 affixes (17 stat, 5 class-spell modifiers), prefix/suffix, value range + per-level growth, minimum rarity, allowed
item categories, weight; at most one affix per stat or spell modifier. Sets: Чингисийн Өв (5 pieces, 2/3/4/5 bonuses),
Талын Анчин (4 pieces), Бөөгийн Ёслол (3 pieces); bonuses reuse the skill tree's effect types (stat, spell mod, proc).

## Unique items (Хөх Сүлд, Алтан Гэрэгэ)

Unique items are the existing relic system, not a copy of it: one database record per relic with compare-and-set on a
version that is also the copy's generation (an older copy is destroyed on sight), shrine claim, PvP seizure, offline
return, container purge, history table, admin give/return/recover/history, audit log. They never come from loot
(`ItemCatalogLoader` refuses a unique item in any loot table; `LootEngine` never rolls UNIQUE) and `/itemsadmin give`
refuses them (use `/relic give`). Their stats now come from the catalog definition.

## Loot

`LootTable` = guaranteed entries + N weighted rolls (with a "nothing" weight) + independent rare drops (chance raised by
the receiver's LOOT_CHANCE). An entry names an item or a pool of item categories near the loot level. Rarity comes from
the table's tier band (`tiers.json`: NORMAL → common/uncommon … BOSS → legendary/ancient/mythic, WORLD_EVENT →
ancient/mythic) intersected with the definition's and the entry's range; pools prefer definitions that can reach the
band; class weighting ×3 own class / ×0.25 other class; item level = loot level ± spread, clamped to the requirement.
`Rng` is injectable and seedable (tests are deterministic). Callers: mob kills (`CombatListener`, the verified "loot
chance = one extra roll" rule), dungeon rewards (`DungeonService`, DUNGEON tier), quests/world events via their table ids.

## Exploit prevention

* Every SÜLD stack is validated on read (`ItemValidator`): unknown definition/affix, rarity outside the definition's
  range, impossible stat or affix value for its rarity and level, too many affixes, duplicate affix, class/slot
  mismatch, missing soulbound. Results are cached per exact document (LRU 4096), so combat does not re-parse JSON.
* A forged stack gives nothing and is quarantined: removed from the inventory, written to `plugins/SULD/quarantine/`
  with an audit entry; `/itemsadmin quarantine list|restore` recovers false positives.
* Duplicates (two stacks with one UUID): join sweep (inventory, ender chest, stored accessories) and an online-wide sweep
  every 30 s; the extra copy is quarantined. Stackable materials are exempt (shared identity by design).
* Trade refuses soulbound, bound, untradable and forged items in the offered slots; relics are also refused inside
  shulker boxes and bundles (as before). Bound items nested in containers are not inspected yet (see remaining work).

## Economy foundation

Sell (`/item sell`, `/shop` "sell all" for materials) uses `ItemEconomy` (definition value × rarity × level).
Salvage (`/item salvage`) turns gear into materials by rarity (Түмрийн хэлтархай, Алтан тоос, Тэнгэрийн чулуу).
Crafting (`/item recipes`, `/item craft <recipe>`): 4 recipes consuming real materials. Upgrade/repair stay at the smith
NPC (`ItemGenerator.upgrade` keeps the UUID and rolls, adds per-level growth). Every destructive action needs a
`confirm` within 30 s.

## Commands

| Command | Permission | What |
|---|---|---|
| `/item inspect\|compare\|equip [slot]\|unequip <slot>\|sell\|destroy\|bind\|salvage\|recipes\|craft <id>` | `suld.items` (default) | the item in hand |
| `/items` | `suld.items` | carried SÜLD items, compared with the worn one; click equips, right click inspects |
| `/equipment` | `suld.items` | nine slots with inactive reasons, accessory picker, stat total, set progress, gear score |
| `/loot [table]` | `suld.items` | what a table can drop |
| `/itemsadmin inspect\|give\|roll\|validate\|reload\|sweep\|quarantine` | `suld.admin.items` (op) | administration |

## Resource pack

Only what the catalog needs: three original 16×16 ring sprites (`mungun_bugj`, `khash_bugj`, `altan_bugj`, generated by
`tools/pack/gen_item_textures.py`, mapped by `tools/pack/gen_items.py` to custom model data 872001–872003). Every other
item uses an existing SÜLD model or its vanilla material. No externally generated art.
