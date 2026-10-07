# Dungeon halls and gates

Each dungeon has a **gate** you can find in the open world and a **hall** with real terrain where the fight happens.
Before this, a run started wherever the party leader stood: no place, no terrain, and waves spawning inside whatever
blocks were there.

## Gates (overworld)

| Dungeon | Region | Bearing from the spawn | Distance | Theme |
|---|---|---|---|---|
| Хасарын Агуй | Хэрлэн (east) | 75° | 380 | DEN: deepslate, bones, cobwebs, soul lanterns |
| Говийн Булш | Говь (south) | 165° | 650 | TOMB: sandstone, gold, sand, decorated pots, lanterns |
| Баавгайн Үүр | Хангай (north) | 20° | 1000 | LAIR: podzol, moss, spruce, ferns, lanterns |
| Мөсөн Оргил | Алтай (west) | 255° | 1400 | PEAK: snow, packed and blue ice, calcite, soul lanterns |

* Positions are offsets from the spawn (like every region ring), set in `DungeonContent.SITES`, all well inside the
  10 000-block border.
* **The gate** (`EntranceBlueprint`, about 650 blocks) is built once on the surface: a round platform with an
  8-deep foundation, two pillars and a carved lintel in the theme's accent block, a dark doorway, lamps and standing
  stones.
  * The chunk is loaded asynchronously before building.
  * The position and blueprint version are stored in `plugins/SULD/halls.yml`. A gate whose spawn-relative position
    moved is built again at the new place.
* A floating title ("⚔ Говийн Булш · Түвшин 8+") and a clickable doorway (an `Interaction` entity) are respawned
  whenever the gate's chunk is loaded. They are not persistent.
* The ground around every gate is pre-generated: priority **P2**, radius 96 (docs/world/PREGENERATION.md).

**Entering:** right-click the doorway, or `/dungeon enter <id>` within **16 blocks** of the gate. Admins with
`suld.admin.world` can start from anywhere. Away from the gate, the error names the gate's coordinates and the
compass direction to it.

## Halls (the `suld_halls` world)

* A void world: no terrain, structures, caves or natural mobs; no daylight or weather cycle; fixed midnight; no spawn
  chunks kept loaded.
* **Instance slots:** each theme has a row of up to 12 slots (`x = theme × 1024`, `z = slot × 160`), so up to 12
  parties can run the same dungeon at once without sharing a room. Since 50 players make at most 12–13 parties,
  every party can get its own hall.
* **Built lazily:** the first run that needs a slot builds it.
  * The chunks are prepared asynchronously, then the blueprint is placed at `world.build-blocks-per-tick` (4000)
    blocks per tick, which takes about 10 ticks.
  * Later runs reuse the slot, which is recorded with the blueprint version in `halls.yml`.
  * A changed blueprint (`HallBlueprint.VERSION`) rebuilds the slots.
* **The hall** (`HallBlueprint`, about 9–10 k blocks) is a round arena with radius 20:
  * an uneven floor with mounds along the wall and scattered cover;
  * a 2-thick wall 12 high with accent ledges;
  * eight 2×2 pillars, each with a light block on top and a lamp at its foot;
  * a raised boss dais in the north with a rim and two lamps;
  * a lit corridor in the south where the party arrives;
  * a stone roof for caves (DEN, LAIR), and an invisible barrier roof under the open night sky (TOMB, PEAK).
* **Run flow:**
  1. The leader is at the gate; the run is validated as before (class, party size, level, soul state).
  2. A hall is acquired (it may take ~0.5 s to build the first time).
  3. Everyone is moved to the corridor with `teleportAsync`.
  4. Waves start after 6 s around the hall's centre; the boss spawns on the dais.
* While a party is inside, the hall's chunks hold a plugin ticket. They are released afterwards, so empty halls
  unload.
* **On the way out:**
  * After a win, the party has 5 s to see it, then everyone is moved back to the gate.
  * After a wipe, a leave or an abort, they go back after 1 s.
  * Leftover entities in the hall (drops, arrows) are removed and the slot is freed.
* **Safety:**
  * Nobody but `suld.admin.world` can break or place blocks in the halls.
  * Anyone who respawns in the halls, or logs in there without an active run (crash, restart), is moved to their
    last dungeon's gate, or to the spawn.
  * The region spawner and region titles work in the overworld only.

## Code and tests

* `suld-api/.../dungeon/hall/`: pure.
  * `HallTheme`: palettes.
  * `HallBlueprint`, `EntranceBlueprint`: layouts.
  * `DungeonSite`: placement.
  * `HallBlueprintTest` checks, for every theme:
    * determinism and one block per position;
    * valid block-data syntax and bounds;
    * a closed wall on every column outside the arena (except the doorway);
    * a roof over the whole arena;
    * solid ground with two clear blocks at the player, wave and boss anchors;
    * at least 16 lights;
    * the gate's doorway and approach.
* `suld-plugin/.../dungeon/DungeonHalls.java`: world, slots, building, gates, protection and stray players.
* `DungeonService`: gate check, asynchronous hall acquisition, anchors, sending the party out and releasing the
  slot.
* `/suldworld halls`: built, busy and building slots per theme, and the gate positions.

## QA

* **Verified on the dev server:** the world was created, gates and halls were built, a run started and ended with
  bots, the party returned to the gate, and the slot was freed (see the commit).
* **MANUAL_QA_REQUIRED in a real client:** how each hall and gate looks, the lighting, and walking through the
  corridor.
