# SÜLD death and recovery (proposed)

**Status: IMPLEMENTED in Stage C1 (FUNCTIONAL_BUT_INCOMPLETE; real-client UX is MANUAL_QA_REQUIRED).** Persistent
lock (V12), level-scaled geometric lock, wound through `Equipment.Wearer.boundFactor`, audited admin commands, all
live-tested on Paper 1.21.11 + PostgreSQL including a server restart during a lock. Paid Instant Revive is
**deferred** (owner decision); see `docs/INSTANT_REVIVE.md`. The current state is in `audit/death-gear-status.json`.

## Today

`DeathRules` + `DeathService`:

* −10 % of the current level bar;
* 50 % of droppable stacks dropped;
* 25 % wear;
* a **30 s soul state**, free revive.

The soul state lives **in memory only**: a restart frees everyone (DT-2). `/revive` is not in the audit log (DT-3).
`GAME_DESIGN.md:69` and `config.yml:39-40` say "no real-money mechanics; recovery is free in-game".

## The lock: real time, scaled by level, chosen by simulation

The owner decided the lock scales by level. Five curves were simulated with 3 player types over 90 days (C7,
`docs/PROGRESSION_SIMULATION.md` §Death lock curves):

| Curve | L5 | L20 | L40 | L60 | Play time lost (casual / active / hardcore) | Day-30 level, active | Verdict |
|---|---|---|---|---|---|---|---|
| 30 s (today) | 30 s | 30 s | 30 s | 30 s | 0 / 0 / 0 % | 60 | no pressure; deaths ×4 more frequent |
| Linear | 1.7 h | 7.8 h | 15.9 h | 24 h | 5 / 5 / 7 % | 54.5 | punishes new players hardest |
| Step | 5 min | 2 h | 12 h | 24 h | 3 / 3 / 6 % | 54.7 | good, but cliffs at each decade |
| **Geometric** | **7 min** | **31 min** | **3.5 h** | **24 h** | **2.5 / 3.2 / 4.2 %** | **58.1** | **recommended** |
| Log | 9.4 h | 17.6 h | 21.6 h | 24 h | 3 / 7 / 7 % | 51.3 | far too harsh early |

**Recommended: geometric**, lock = 5 min × 288^((L−1)/59):

* 5 minutes at level 1, doubling about every 7 levels, 24 hours at 60 and in Ascension.
* It gives real pressure where a death matters, and stays forgiving while a player is learning.
* It loses the least play time and never locked any archetype out for a whole day twice in a row (p90).
* The lock changes behaviour: simulated hardcore deaths fall from 9.2 to 1.7 per 100 hours, because players stop
  taking coin-flip fights.

Exact values:

<!-- spec:begin lock -->
| Level | 1 | 5 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 | 50 | 55 | 60 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Lock (real time) | 5 min | 7 min | 12 min | 19 min | 31 min | 50 min | 1.3 h | 2.2 h | 3.5 h | 5.7 h | 9.2 h | 14.9 h | 24.0 h |
<!-- spec:end lock -->

**The owner picks the final curve.** The simulator can re-run any alternative in a minute
(`ProposedRules.LockCurve`).

**Real time, absolute.** The persisted `locked_until` timestamp is compared with the wall clock. Ticks, online time
and server uptime play no part (directive §5). It survives restarts, reconnects and database reloads.

**During the lock** the player can log in to a **recovery state**: a spectator-like soul at the death site or the
Kharkhorum shrine. They cannot fight, loot, trade or join parties. Chat and menus work, and they can see the exact
recovery time. Refusing login altogether was rejected because players would lose contact with their clan and the
community. Gameplay is gated, the connection is not.

## The wound (after normal recovery)

* **−5 % effective stats** of the class armour and weapon per death, stacking to at most **−15 %**.
* Each step heals after **3 active hours** (ActivePlaytime), so the wound is pressure, not destruction.
* It is implemented as a per-item factor in `Equipment.compute` (`Equipment.java:126`): it scales `itemStats`,
  `statKeys` and flat damage of the class pieces together. Item stats are never deleted. The factor is part of
  `EquipmentService.fingerprint` so stats recompute when it changes.
* Visible on the HUD (a wound icon), in the class-gear tooltip, and on the death screen.

## Other death costs (proposed)

| Cost | Today | Proposed |
|---|---|---|
| EXP | −10 % of the bar | **−5 %** of the bar (the lock is the main cost now) |
| Materials | 50 % of droppable stacks | **25 %** (the fraction is rounded at random: 3 stacks lose 0 or 1, on average 0.75) |
| Durability | 25 % wear on kept items | repair cost of 3 hours' wear (class gear is unbreakable; the wound replaces wear) |
| Class gear | kept | kept, never dropped (`docs/CLASS_GEAR_SYSTEM.md`) |
| Dungeon | downed members lose the reward | unchanged; a party wipe ends the run (enrage wipes) |

## DeathState (persistent, V12)

Table `suld_death_state`, one row per death, on both PostgreSQL and MySQL:

| Column | Type | Notes |
|---|---|---|
| `death_id` | UUID PK | |
| `player_uuid` | UUID, indexed | |
| `death_seq` | int | per-player sequence; unique `(player_uuid, death_seq)` |
| `died_at` | timestamp (UTC) | real time |
| `locked_until` | timestamp (UTC) | from the lock curve at `died_at` |
| `world`, `x`, `y`, `z` | | death location |
| `cause` | text ≤ 64 | `EntityDamageEvent.DamageCause` + killer id |
| `level`, `ascension` | int | inputs of the lock |
| `wound_after` | int | wound stacks after this death |
| `revive_state` | enum | `LOCKED`, `RECOVERED`, `ADMIN_REVIVED`, `RESET`, `INSTANT_REVIVED` (the last reserved, unused) |
| `recovered_at` | timestamp, nullable | |
| `version` | int | optimistic locking |

**Rules:**

* At most one row per player is `LOCKED`. A second death while locked is impossible, because there is no gameplay
  while locked.
* Multiple-death races (two damage events in one tick) are serialised per player on the profile sequencer that
  already serialises profile access (`DefaultProfileService`).
* The current `souls` map becomes a cache of the open row.

## Admin commands (all audited through `services.audit()`)

| Command | Permission | Does |
|---|---|---|
| `/deathstatus <player>` | `suld.admin.death` | lock state, time left, wound stacks, last 5 deaths |
| `/deathinfo <player> [death#]` | `suld.admin.death` | one death in full (cause, place, level, lock) |
| `/deathrevive <player>` | `suld.admin.death.revive` | ends the lock now (`ADMIN_REVIVED`); the wound is still applied |
| `/deathreset <player>` | `suld.admin.death.reset` | clears lock and wound (`RESET`; staff-error recovery only) |

`/revive` becomes an alias of `/deathrevive`, and its missing audit entry (DT-3) is fixed.

## Death UI

The screen title reads "ТА УНАЛАА" ("YOU HAVE FALLEN"), then:

* death time and recovery time, as local time and the countdown;
* what the wound will be;
* class gear: armour, weapon, tier, current wound;
* "your gear is yours forever; death has a price".

Options are **[ХҮЛЭЭХ / WAIT FOR RECOVERY]** only. The Instant Revive button is absent until a payment provider exists
and the owner enables it (`docs/INSTANT_REVIVE.md`).

## Tests (Stage C1)

From the death/gear directive §16, items 8–10, 16, 17 and 19:

* unit tests of the lock curve and wound;
* JDBC IT of `suld_death_state` (insert, one-open-row constraint, restart reload);
* bots: die, reconnect, restart the server during the lock, admin commands with audit rows;
* a real client for the death UI.

Instant-revive tests 11–14 and 18 stay **NOT_IMPLEMENTED** by owner decision.
