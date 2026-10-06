# SÜLD World-Unique Relics

Some items exist **exactly once on the entire server**, such as **Хөх Сүлд** (the Blue Standard) and
**Алтан Гэрэгэ** (the Golden Paiza). Whoever bears one is famous, powerful and hunted.

The design rests on one rule:

> **The database row is the only truth about who bears a relic. A physical item is a disposable
> copy of that truth: valid only in the bearer's own inventory, only at the current generation,
> and only once.**

Everything else follows from this rule. Dupe glitches, creative cloning, restored backups, edited NBT,
`/give`, items stashed in chests before a restart: no matter how a copy appears, it is reconciled
against the database and destroyed if it isn't the one legitimate copy.

## 1. Gameplay

| | |
|---|---|
| **Find** | Each relic waits at a **shrine**: a stone platform with a lodestone altar and blue pillars with soul lanterns. On first boot SÜLD builds each shrine on the overworld surface 600–1200 blocks from spawn (configurable). `/relic hint` gives a coarse distance band and 8-point direction, e.g. "~750 блок, зүүн хойд зүгт" (5-minute cooldown). |
| **Claim** | Right-click the altar and perform a **10-second ritual**: stay within 3.5 blocks and take no damage. Requires a chosen class and the relic's minimum level (Хөх Сүлд 10, Алтан Гэрэгэ 6). You can bear at most **one** relic. The claim is a database compare-and-set, so if two players finish together, exactly one wins. |
| **Bear** | +25% EXP (Хөх Сүлд) or +15% (Алтан Гэрэгэ), added to the clan bonus. **The bearer glows**: everyone can see them through walls. HUD line `Сүлд: <name>`. |
| **Lose** | Killed **by another player** who bears no relic → they **seize** it (broadcast "⚔ X Y-ээс Z-ийг булааж авлаа!"). Any other death (mob, fall, void, `/kill`, suicide, or a killer who already bears one) → the relic **returns to its shrine**. Offline more than 72 h → returns to its shrine. |
| **Never** | A relic can't be dropped, stored in any container (chest, ender chest, shulker, hopper, bundle), put in an item frame or on an armor stand, given to an allay, or crafted with. It never exists as an item on the ground: its death drop is removed. |

Every claim, seize and return is announced server-wide.

## 2. Data model (schema V4 on top of V1)

`suld_world_unique_items` holds one row per relic:

| Column | Notes |
|---|---|
| `item_key` | **PK**, e.g. `relic.khukh_suld` |
| `item_uuid` | **UNIQUE**. Minted once on first boot (`ensure` = insert-if-absent; a restart never mints a second identity). |
| `state` | `UNCLAIMED` / `OWNED`. **CHECK**: `OWNED` ⇔ `owner_uuid IS NOT NULL`. |
| `owner_uuid` | **UNIQUE**, so a player bears at most one relic, enforced by the database. |
| `owner_name` | Display only; identity is always the UUID (see [AUTHENTICATION.md](AUTHENTICATION.md)). |
| `acquired_at` | When the current bearer took it. |
| `version` | Bumped on every ownership change. Also the **generation** stamped on the physical copy. |
| `shrine_world`, `shrine_x/y/z` | The altar position. |

`suld_world_unique_history` is append-only, with columns `event`, `owner_uuid`, `actor` and `detail`. Events:
`CREATED`, `SHRINE_SET`, `DISCOVERED`, `SEIZED`, `RETURNED`, `EXPIRED`, `ADMIN_GRANTED`, `ADMIN_RETURNED`, `RECOVERED`.

**Every ownership change is a single compare-and-set**
(`UPDATE … SET …, version = version + 1 WHERE item_key = ? AND version = ?`), written in the same
transaction as its history row. Any decision made from a stale view (a claim racing a seize, two ritual
finishers, an admin acting on old data) matches zero rows and changes nothing. PostgreSQL re-evaluates
the `WHERE` after the row lock, so this holds under true concurrency. It holds across restarts and
crashes, and even with two servers on one database.

## 3. The physical copy

A relic copy is a `nether_star` with resource-pack model data (870100 / 870101), the UNIQUE rarity
tooltip "✦ ДЭЛХИЙД ГАНЦ · 1 / 1", an enchantment glint and max stack 1. Its PDC carries:
`suld:relic_key`, `suld:item_uuid` and `suld:relic_gen` (= the record `version`).

`RelicValidator` (pure, unit-tested) reconciles one inventory against the records:

| Found copy | Verdict |
|---|---|
| unknown relic key | removed (`UNKNOWN_RELIC`) |
| wrong `item_uuid` (forged) | removed (`COUNTERFEIT_UUID`) |
| in any container or bundle | removed (`IN_CONTAINER` / `IN_BUNDLE`), even the genuine one |
| in someone who isn't the bearer | removed (`NOT_BEARER`) |
| generation older than the record | removed (`STALE_GENERATION`) |
| a second copy in the bearer's inventory | removed (`EXTRA_COPY`) |
| the bearer's single current copy | kept (stack trimmed to 1) |
| bearer has **no** copy | a fresh copy is **issued** |

It runs:
- on join
- every 10 s for all online players
- right after any ownership change
- on every container open, which purges the container

Every removal is audited as `relic.copy_removed` with its reason. If the bearer's inventory is full when
a copy is issued, an ordinary item is displaced to the ground: the relic itself must be carried.

## 4. Commands

| Command | Who | Does |
|---|---|---|
| `/relic` or `/relic list` | everyone | every relic and its bearer, or "waiting at its shrine" |
| `/relic info <key>` | everyone | legend, requirements, perks, current state (admins also see the item UUID, generation and shrine) |
| `/relic hint` | everyone | coarse direction and distance to the nearest unclaimed shrine |
| `/relic history <key>` | everyone | last 10 ownership events |
| `/relic setshrine <key>` | `suld.admin.relic` | build and move the shrine to your position |
| `/relic tp <key>` | admin | teleport to the shrine |
| `/relic return <key>` | admin | send it back to its shrine (`ADMIN_RETURNED`) |
| `/relic give <key> <player>` | admin | grant it (`ADMIN_GRANTED`); the database still forbids two relics per bearer |
| `/relic recover <key>` | admin | **bump the generation**: every existing copy is invalidated and the bearer gets a fresh one (`RECOVERED`), for use after a suspected dupe |

Aliases: `/suld-unique`, `/suldee`. Shrine blocks are protected from breaking (admins: sneak to edit) and
from explosions.

Config (`config.yml`, `relics:`): `enabled`, `auto-place-min-radius` / `auto-place-max-radius`,
`offline-return-hours` (72), `ritual-seconds` (10), `hint-cooldown-seconds` (300).

## 5. Verification

**Unit tests:**
- `RelicValidatorTest` (10): every row of the table in §3.
- `RelicRulesAndHintsTest` (4): claim requirements, death outcomes (seize / mob / suicide / killer
  already bearing), transition invariants, compass directions on Minecraft axes, distance bands.
- `InMemoryRelicRepositoryTest` (3): idempotent minting, stale compare-and-set rejected, 64 simultaneous
  claims → exactly 1 bearer.

**Real PostgreSQL** (`JdbcRelicRepositoryIT`, opt-in):
- 24 truly concurrent claims, each on its own connection → exactly 1 winner.
- The same item UUID can't be minted twice.
- `UNIQUE(owner_uuid)` rejects a second relic even when the service check is bypassed.
- Generations are monotonic through seize, recover and return.

**Live, Paper 1.21.11 + PostgreSQL 16 + mineflayer bots** (2026-10-06):

| Step | Observed |
|---|---|
| First boot | V4 applied; both relics minted once; both shrines auto-built and persisted (`SHRINE_SET server:auto`). |
| Bot right-clicks the altar, stands 10 s | `DISCOVERED`; DB `OWNED Seeker v1`; bot holds 1 copy; server-wide broadcast. |
| Forged copy (fake UUID) given via `/give` with custom data | Removed within one cycle (`COUNTERFEIT_UUID`); bearer still holds 1. |
| **Exact** duplicate (real UUID + generation) given | Removed (`EXTRA_COPY`); bearer holds 1. |
| Exact copy planted in a chest with `/item replace` | Purged when the chest opened; the bot saw 0 relics inside (`IN_CONTAINER`). |
| Bearer `/kill`ed (no player killer) | `RETURNED`; DB `UNCLAIMED v2`; bearer 0 copies; nothing dropped; broadcast. |
| `/relic give altan_gerege Seeker` | `ADMIN_GRANTED`; bearer gets 1 copy. |
| **PvP: Rival kills Seeker with a sword** | `SEIZED`; DB `OWNED Rival v2`; Rival 1 copy, Seeker 0; "⚔ Rival Seeker-ээс Алтан Гэрэгэ-ийг булааж авлаа!" |

The first live run caught a real bug. Statless items crashed in `ItemInstance` (`new EnumMap<>(Map.of())`
throws), so bearers were never issued their copy. It is fixed at the root, with a regression test
(`ItemInstanceTest`).

**Not verified here:**
- Offline expiry, which needs 72 h of absence. Its logic reuses the same compare-and-set release path.
- How the relic looks on a real 1.21.11 client: no client is available in this sandbox. The pack is
  validated structurally (format 75, item model definitions resolve; see ASSET_PIPELINE.md).
