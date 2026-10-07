# SÜLD class gear — soulbound signature armour and weapon

**Status: IMPLEMENTED for the weapon (Stage C2) and the Баатар armour with the profile `class_gear` record (Stage C3, V13).**
Per-vector statuses are in `audit/death-gear-status.json`; the audit findings in `docs/PROGRESSION_EXPLOIT_AUDIT.md` §2.8.

What C2 built: one shared guard, `item/SoulboundGuard.java`, for every soulbound SÜLD item (the class weapon now,
the class armour in C3 with no further code); the upgrade fix in `ClassWeapons.upgrade`; `/classgear recover`.
Since C3 the profile's `class_gear` record holds the weapon and armour UUIDs. The player's `suld:class_weapon` data
is kept as a fallback for characters from before C3.

Every character owns one class weapon and one class armour set: helmet, chest, legs, boots. Both are **permanently
bound to the player's UUID**, never transferable, kept on death, and restored when lost. They are progression
equipment: item level from the armour level, rarity from the tier, plus enhancement and mastery
(`docs/ARMOR_PROGRESSION.md`).

## Binding (server-authoritative; lore is display only)

* **Item data** (existing): `ItemInstance.soulbound = true`, `boundTo = owner` (`ItemCodec` keys `sb` / `bt`).
  `ItemValidator` already requires the soulbound flag for SOULBOUND definitions (`ItemValidator.java:64`). Forged
  copies are quarantined by `ItemService.sweep`.
* **Profile record** (new, V12, `class_gear` JSON on the profile): the UUIDs of the five pieces, armour level, armour
  XP, tier, enhancement. The profile is the source of truth. A stack is only a rendering of it.
* **One canonical copy**: every piece UUID may exist once. The existing duplicate sweep (`ItemService.java:262,
  303-325`) is extended to run on the owner's inventory + ender chest on join and on every class-gear change.

## Protection vectors

Live status is in brackets (from the audit).

| Vector | Rule | After C2 |
|---|---|---|
| Drop (Q, drag out of the window) | cancelled | **protected**, live-tested |
| Ground pickup by another player / hopper / allay | the item is never on the ground; a hopper or hopper minecart can never pick one up | **protected**: at class pick on a full inventory the weapon takes hotbar slot 0 and the displaced item drops instead (`ClassSelectionGui`); `InventoryPickupItemEvent` guard (code path, not reachable live) |
| Containers (chest, barrel, shulker, ender chest, dispenser, dropper, crafter, hopper, furnace/brewing, anvil, grindstone, smithing, enchanting, crafting table, merchant) | any click (pick/place, shift-click, number-key swap, offhand swap) or drag that involves a bound item while any non-player top inventory is open is cancelled; SÜLD menus are exempt (they cancel their own clicks) | **protected**; live-tested chest (shift, pick+place, number key), shulker box, hopper, anvil |
| Decorated pot (right-click insert) | cancelled | **protected** (code; not live-tested) |
| Bundles | a bound item can't be put into a bundle and a bundle can't take it (any click involving both) | **protected**, live-tested |
| 2x2 crafting grid | allowed to sit there (player's own view), never an ingredient: the result is cleared (vanilla repair recipe) | **protected**, live-tested |
| Item frames, armour stands, allays, foxes, dolphins | interaction holding a bound item (or a shulker/bundle holding one) is cancelled | **protected**; live-tested item frame and armour stand |
| Hopper / `InventoryMoveItemEvent` | cancelled (also for a shulker box carrying one) | **protected** (code) |
| Trade window, including a shulker or bundle carrying gear | refused (`TradeService.tradable` + `SoulboundGuard.holdsBound`) | **protected** (code; the direct case was live-tested in the item stage) |
| Sell / salvage | refused | protected |
| `/item destroy` | refused before the confirmation is asked | **protected**, live-tested (it used to delete class weapons) |
| Death | always kept, never dropped, no durability loss (class weapons are unbreakable) | protected, live-tested |
| Reconnect / server restart | vanilla player data | live-tested |
| `ClassWeapons.upgrade` | regenerates only the holder's own class weapon of their class (same UUID); a copy bound to someone else is left alone | **fixed**, live-tested (a level-10 player holding another player's tier-1 copy: own → tier 2, the foreign one stays tier 1 bound to its owner; foreign bound gear gives no stats, `Inactive.BOUND_TO_OTHER`) |
| Admin `/itemsadmin give` of a class piece to another player | should be refused | **open** (staff-only and audited; C3 moves creation into the class-gear service) |
| Creative middle-click copies | duplicate sweep | partial (as before) |
| Death in lava, the void, cactus, despawn | not applicable (never on the ground); if missing, recovery | n/a |

The relic code already contains most of these guards for unique relics (`RelicListener.java:92-177`). The class gear
reuses one shared "never leaves the owner" guard instead of copying them.

## Recovery: `/classgear recover`

This is idempotent and duplication-safe. **As built in C2 (weapon only):** if the player carries (inventory, armour,
offhand, cursor, ender chest) a class weapon of their class bound to them, nothing happens and no cooldown is spent.
Otherwise the weapon is rebuilt at the tier of their level with the **remembered UUID**, so a resurfacing old copy is
a duplicate for the existing sweep. Refused while a soul or with a full inventory. Live-tested: held → no-op; cleared
→ restored with the same UUID `6fcc01e8…`; second try → cooldown; staff path → restored, second staff call → no-op;
one `classgear.recover` audit row per restore.

**Target design (C3, with the profile record and the armour):**

1. Read the profile's `class_gear` record.
2. Scan the owner's inventory, armour slots and ender chest.
3. Each recorded piece UUID that is **missing** is regenerated in place. It has the same UUID, item level from AL,
   rarity from tier, enhancement and binding.
4. Any **extra** copy of a recorded UUID is quarantined (the existing quarantine path).
5. Audit: `classgear.recover` with the restored UUIDs.

A player may recover at most once per 10 minutes (anti-spam, not a gameplay lock). Admins can run
`/classgear recover <player>` (audited).

## Integration (no duplicate managers)

| Piece | Code |
|---|---|
| grant at class pick | `ClassSelectionGui` → class-gear service (new methods on `ClassWeapons`, renamed in spirit to "class gear") |
| level / tier changes | `LevelUpEvent` / armour XP → regenerate via `ItemService.generator()` |
| stats | `Equipment.compute` (wound factor: `docs/DEATH_AND_RECOVERY.md`) |
| guards | `EquipmentService` listeners plus the shared guard |
| storage | `JdbcProfileRepository` column + `SqlDialect` upsert (V12 on PostgreSQL and MySQL) |

## Required tests

From the death/gear directive §16, items 1–7, 15 and 19:

* **Unit (pure):**
  * the recovery planner (missing / extra / ok);
  * the guard decision table for every inventory type;
  * the upgrade only touches the owner's items.
* **JDBC IT:** `class_gear` round trip on PostgreSQL.
* **Live bots:**
  * drop;
  * chest / shulker / bundle / hopper / frame / armour stand / trade attempts;
  * `/item destroy`;
  * death;
  * reconnect;
  * restart;
  * a second player taking the item.
* **Real client:** tooltip and UI (`MANUAL_QA_REQUIRED`).
