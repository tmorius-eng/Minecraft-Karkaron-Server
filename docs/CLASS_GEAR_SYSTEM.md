# SÜLD class gear — soulbound signature armour and weapon (proposed)

**Status: SPEC.** It is built in Stage C2 after the owner approves Stage B. The current state is in
`audit/death-gear-status.json` and `docs/PROGRESSION_EXPLOIT_AUDIT.md` §2.8.

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

| Vector | Rule | Today |
|---|---|---|
| Drop (Q, drag out) | cancelled | protected (soulbound) |
| Ground pickup by another player / hopper / allay | the item is never on the ground. If it ever is (full inventory), it goes to the class-gear stash instead | **open**: `ClassSelectionGui.java:166` drops it at the feet |
| Containers (chest, barrel, shulker, ender chest, dispenser, dropper, crafter, decorated pot, hopper minecart, brewing/furnace slots) | click and drag into any non-player top inventory are cancelled; shift-click too | **open** (relics only) |
| Bundles | cannot be inserted; existing bundles are stripped (`RelicService.java:208-224` pattern) | **open** |
| Item frames, armour stands, allays, display entities | interaction cancelled | **open** outside the city |
| Hopper / `InventoryMoveItemEvent` | cancelled | **open** |
| Trade window, including a shulker or bundle carrying gear | refused (`TradeService.java:252-277`, extended to look inside containers) | partly open |
| Sell / salvage / craft input / anvil / grindstone / smithing | refused | sell & salvage protected; the anvil/grindstone/smithing inputs need guards |
| `/item destroy` | refused for class gear (today it deletes class weapons) | **broken** (`ItemCommands.java:275-286`) |
| Death | always kept, never dropped, no durability loss | protected |
| Admin `/itemsadmin give` of a class piece to another player | refused; class gear is created only by the class-gear service for the owner | **open** |
| `ClassWeapons.upgrade` | regenerates only items whose `boundTo` is this player | **broken** (re-binds, SB-1) |
| Creative middle-click copies | the duplicate sweep + creative-event guard; class gear is never accepted from the creative inventory | partial |
| Death in lava, the void, cactus, despawn | not applicable (never on the ground); if missing, recovery | n/a |

The relic code already contains most of these guards for unique relics (`RelicListener.java:92-177`). The class gear
reuses one shared "never leaves the owner" guard instead of copying them.

## Recovery: `/classgear recover`

This is idempotent and duplication-safe:

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
