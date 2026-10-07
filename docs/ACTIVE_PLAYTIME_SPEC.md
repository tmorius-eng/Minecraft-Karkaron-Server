# ActivePlaytime — the activity tracker (proposed)

**Not implemented yet.** It feeds armour level (`docs/ARMOR_PROGRESSION.md`), class mastery and analytics. It is
reusable by any system that must not reward idle time.

## Today

There is no SÜLD activity or AFK detection. The facts:

* `SESSION_START` / `SESSION_END` analytics events carry no duration (`listener/PlayerLifecycleListener.java:47,76`).
* `lastSeenAt` is touched at login only.
* AFK exists only as EssentialsX `/afk`.

## Design: event-driven, no per-tick work per player

A player's time is cut into **one-minute windows**. A window is **active** if it holds at least two **activity
signals** from different families, or one strong signal:

| Family | Signals (Bukkit events already fired) | Strength |
|---|---|---|
| Combat | `EntityDamageByEntityEvent` (dealt or taken, SÜLD mobs), kills, spell casts (`SkillService.castDirect`) | strong |
| Quest / dungeon | chapter progress, dungeon wave cleared, boss phase | strong |
| Exploration | region change, landmark, first visit of a 16×16 chunk area | strong |
| Crafting / economy | craft, salvage, reforge, temper, trade completed | normal |
| Movement | `PlayerMoveEvent` sampled by `getTo` block change, at most 1 per 5 s; only if the 1-minute displacement is > 6 blocks and not a straight water/minecart/elytra glide | normal |
| Interaction | inventory clicks outside SÜLD menus, block break/place outside the city | weak |

These do **not** count:

* menu-only time (SÜLD GUIs open);
* chat;
* standing still;
* riding a vehicle on a rail;
* moving inside a water current;
* being in soul state or locked out.

## Anti-AFK and anti-macro

The tracker keeps four cheap counters per window:

* **Repetition**: more than 120 identical attack intervals in a minute (±10 ms jitter), or the same yaw/pitch sequence
  repeated, means an auto-clicker or macro. The window is inactive and the mob grinder fatigue (below) applies.
* **Area fatigue**: more than 150 kills within the same 64-block area during the last 30 active minutes cut EXP to 30 %
  and armour XP to 0 until the player moves on. This is activity-based, not a timer.
* **Damage-in pattern**: being hit with no damage dealt for 3+ minutes means a mob-farm AFK. Those windows are inactive.
* **Idle kick**: no signal for 15 minutes moves the player to "away". The away state counts nothing and shows on the
  TAB list.

## Persistence

* Active minutes are tracked per category (combat, quest, dungeon, exploration, crafting, other) and stored in the
  profile. One new column `active_minutes` (JSON per category) goes in the V12 migration alongside the death state.
* Saved with the profile (batched, on the storage pool); never written per event.
* Session totals (session, active, by category) go to analytics on quit. The JDBC analytics sink must be wired: today
  `analytics.sink: database` is parsed but ignored.

## Cost budget

| Item | Budget |
|---|---|
| Per signal | 1 map lookup + counter increment (< 1 µs) |
| Per minute per player | 1 window close: classify, add to totals |
| Move sampling | at most 1 per 5 s per player |
| Memory | ~200 bytes per online player |

The window close runs from the existing HUD/regen tick. There is no new scheduler and no per-tick scan.

## Simulated effect

* The AFK scenario (auto-clicker in one spot, 10 h/day for 7 days) ends day 7 at **armour level 1**.
* Its EXP per online hour is 17k, against 126k for active play (C6 PASS).

## Tests (implementation phase)

* Unit (pure, `suld-api`): the window classifier with synthetic signal streams (idle, macro, real play, menu-only,
  water current, minecart).
* Integration: a bot that idles, one that macro-clicks at a grinder, and one that plays. Only the last gains active
  minutes and armour XP.
* `MANUAL_QA_REQUIRED`: real players at work (AFK at the bank, fishing) to check false negatives.
