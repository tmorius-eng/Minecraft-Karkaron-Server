# What a crash can lose (`kill -9`, power loss, OOM)

**Scope.** This covers an abrupt stop with no shutdown hooks. A clean `stop` saves everything (`SuldPlugin.onDisable`: profiles, styles, clans, then the vanilla player files).

**Origin.** The audit of 2026-10-11 measured a level-up lost 9 s before a `kill -9` (`docs/audits/2026-10-11/11-evidence-and-reproducibility.md`, E-09). This page records what was changed in P1 and what remains.

**Two stores.**
- **SQL** (PostgreSQL in production) holds profiles, styles, credits, death state, relics and clans.
- The **vanilla player file** (`world/playerdata/<uuid>.dat`) holds inventory, ender chest and the player's PDC flags.

Neither store's writes block the main thread on the database. SQL writes go through async executors. The player file is written with `Player#saveData()` on the main thread, which is a small local file write already used by sell and trade.

## Maximum loss by kind of state

| State | Store | When it is committed | Max loss on abrupt stop |
|---|---|---|---|
| Level-ups | SQL `suld_profiles` | **Immediately** on every level-up (async save from `LevelUpEvent`, P1) | 0. A save in flight (milliseconds) can be lost |
| EXP from your own kills | SQL | At most one save per 30 s per player (`CombatListener`), plus the autosave | ≤ 30 s of kill EXP |
| EXP from party shares, vanilla mobs, discoveries without a save | SQL | Autosave of dirty profiles every **60 s** (was 300 s; P1) | ≤ 60 s |
| Admin `/suld exp` | SQL | Immediately (P1) | 0 |
| Coins (shop, sell, `/pay`, trade, quest/tutorial rewards) | SQL | Immediately, in the documented order (`PlayerDataSaves`) | 0 coins created. A sell can cost the coins (the item side is written first) |
| Quest progress / chapter | SQL (profile row) | With the next profile save (kill throttle, autosave, chapter reward save) | ≤ 60 s of progress counters; a finished chapter is saved at once |
| Credits | SQL `suld_player_style.credits` | Atomic update per change; store grants with a txid ledger (V16) | 0; a replayed store command is a no-op |
| Cosmetics owned/equipped, daily/task claims, level-reward claims | SQL style row | Style flush every 60 s; purchases write at once | ≤ 60 s. A claim made in that window can be claimed again after the crash (PERS-002, P3, open) |
| Death lock / wound | SQL `suld_death_state` | Immediately (CAS) | 0 |
| Relics | SQL `suld_world_unique_items` | Immediately (CAS); the item is re-issued from the DB on join | 0 |
| Clans | SQL | Flush every 60 s | ≤ 60 s |
| Inventory, ender chest | player file | Vanilla autosave (Paper default: every 5 min), on quit, and **immediately** wherever SÜLD trades items for DB state | ≤ 5 min of ordinary inventory changes (picked-up loot, crafting) |
| Class-armour upgrade materials, Orb of Oblivion | player file before SQL | **Player file first**, then the tier/respec (P1, PERS-001) | the materials or Orb can be lost; they can no longer come back with the upgrade kept |
| Ovoo first visit, historic-site first visit (guard flags) | player file before SQL | **Guard first**, then the EXP (P1, PERS-002) | the EXP can be lost; it can no longer be paid twice |
| Tutorial "paid steps" mask | player file | Written before the coins (already) | 0 double pay |
| Mailbox deliveries | file + player file | File deleted on delivery before the player file is saved (PERS-005, P3, open) | a delivered stack can be lost |

## Measured (2026-10-11, Paper 1.21.11, PostgreSQL 16.15, bots)

**Before P1.** `/suld exp Crash01 150` levelled 1 → 2 at 11:56:36. `kill -9` at 11:56:45. After restart the DB showed level 1, 190 EXP: the level-up was lost.

**After P1.** `/suld exp Crash02 300` levelled 1 → 2 at 12:49:32. Before it the DB showed level 1, 40 EXP, version 7.

| Point | DB row |
|---|---|
| `kill -9` at 12:49:41 | level 2, 98 EXP, version 11 |
| After restart | level 2, 98 EXP, version 11 |

The level-up survived.

**Not measured live:** the 30 s kill throttle, the 60 s autosave window, and the item-side orderings. These are established by reading the code.

## Judgement for a private alpha

**Acceptable**, provided:
- the host is not crash-prone;
- the operator restarts with `stop` (or `systemctl stop`), never `kill`;
- players are told that a server crash can roll back up to about a minute of EXP and up to 5 minutes of picked-up items.

Nothing in this table creates coins, credits, relics or class-gear upgrades out of a crash.

The open P3 items (PERS-002 style-row claims, PERS-004 trade revert, PERS-005 mailbox) are rare duplicate or loss windows. They stay listed in `docs/audits/2026-10-11/10-fix-roadmap.md`.
