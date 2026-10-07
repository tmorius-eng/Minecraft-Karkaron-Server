# SÜLD class armour system (proposed)

**Not implemented yet.**

| Topic | Where |
|---|---|
| Binding, recovery, protection vectors | `docs/CLASS_GEAR_SYSTEM.md` |
| Progression numbers | `docs/ARMOR_PROGRESSION.md` |
| Overall design | `docs/ARMOR_MASTER_DESIGN.md` |
| Look | `docs/ARMOR_PROGRESSION_VISUAL_SPEC.md` |
| Asset pipeline | `docs/ARMOR_ASSET_PIPELINE.md` |
| Status | `audit/armor-system-status.json` |

## What exists today

* 27 generic armour definitions in 8 vanilla-material sets (`suld-api/src/main/resources/items/armor.json`), with no
  class key and no binding. They render as vanilla leather, chainmail, iron, diamond or netherite.
* The class *weapons* exist: 5 classes × 4 tiers, soulbound and procedurally modelled.
* There is no class armour.

## Item model

There is no parallel armour system. Class armour is ordinary SÜLD items:

| Field | Value |
|---|---|
| Definition id | `armor.class.<class>.<piece>.t<tier>`, e.g. `armor.class.baatar.chestplate.t3` (6 tiers × 4 pieces × 5 classes = 120 definitions) |
| Type / slot | HELMET / CHESTPLATE / LEGGINGS / BOOTS (existing `ItemType`) |
| `classes` | the one class (existing field) |
| `binding` | `SOULBOUND` (existing) + `boundTo` = owner UUID |
| Rarity | fixed per tier (T1 uncommon … T6 mythic), so `fixedRarity` |
| Level requirement | 1. The item level is the armour level, so it never blocks equipping |
| Stats | per-piece ranges + `statPerLevel` with the same stat budget as the generic armour of the same item level and rarity; the simulation uses exactly that budget |
| Affixes | normal rarity affix rules; re-rolled only by tier upgrades |
| Set | `set.class.<class>`: one set per class whose 2/4-piece bonuses are the class bonuses in `ARMOR_PROGRESSION` |
| Durability | unbreakable (as class weapons, `ItemFactory.java:99-100`); wear is replaced by the death wound |
| Visual | `assetId` = `suld:<class>_t<tier>` (equipment layer); helmet item model `suld:item/armor/<class>_t<tier>_helmet` |
| Item instance | the same `ItemInstance` (UUID, rolled stats, affixes); class gear progress (AL, tier, enhancement) lives in the profile, and the stack is regenerated from it |

## Lifecycle

1. **Class pick**: `ClassSelectionGui` grants the T1 set + weapon (today only the weapon:
   `ClassSelectionGui.java:165`), bound and equipped.
2. **Armour level up**: the pieces are regenerated in place, keeping the same item UUIDs, at the new item level. This
   uses the pattern of `ClassWeapons.upgrade` **after the re-bind fix**: it only regenerates items whose `boundTo` is
   this player (SB-1).
3. **Tier upgrade**: done at the smith Дархан NPC in Kharkhorum when the gates are met. The pieces regenerate at the
   new rarity with a new look, and enhancement resets.
4. **Death**: always kept. Never dropped, never worn down (`docs/DEATH_AND_RECOVERY.md`).
5. **Loss** (destroyed, a bugged container): `/classgear recover` rebuilds missing pieces from the profile.
   It is idempotent and will not duplicate (`CLASS_GEAR_SYSTEM`).

## Integration points (no duplicate managers)

| Concern | Existing code reused |
|---|---|
| Item creation | `ItemFactory` / `ItemService.stack` (+ the new `Equippable.assetId` component) |
| Stats | `Equipment.compute` → `SkillTreeService.refreshRuntime` |
| Persistence | the profile row (V12 adds `class_gear` JSON: AL, armour XP, tier, enhancement, mastery link) |
| Binding checks | `ItemValidator` (requires soulbound) + `EquipmentService` listeners extended to every vector |
| Tooltip | `ItemTooltip` + a class-gear line (AL, tier, wound) |
| Trade / sell / salvage | already refuse soulbound (`TradeService.java:252-264`, `ItemEconomy`) |

## Baatar vertical slice first

The order follows the owner's plan, Stage C:

1. Baatar T1–T6 definitions.
2. Generation and upgrade.
3. Rendering of T1 and T3 first.
4. Client QA.
5. The remaining tiers.
6. The other four classes, only after the slice passes.
