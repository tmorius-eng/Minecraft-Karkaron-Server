# Validation

## Compile-time (no server) — `CityValidator.validate(CompiledCity)`

| Check | Rule |
|---|---|
| OVERLAP | Two placements write different blocks on the same layer (error, unless `allow_overlap`). |
| MISSING_MODULE / MODULE_FAILED | The placement's module is unknown, or it threw an error. |
| DUPLICATE_LANDMARK / DUPLICATE_ID | A landmark module is placed twice, or a placement id is reused. |
| OUT_OF_BOUNDS | A placement block lies outside the slice bounds. |
| FLOATING | A non-air block is not face-connected (6-neighbour) to the ground through other blocks. |
| POINT_BLOCKED | A gameplay point is not a standing position: floor not standable, or feet/head not free. |
| POINT_UNREACHABLE | It can't be reached on foot from spawn: steps up 1, drops ≤ 3, ladders climb, no swimming. |
| CONNECTOR_UNREACHABLE | A walkable connector (door, gate passage) can't be reached. |

Run offline:
`./gradlew :suld-api:worldTool --args="slice assets/world/kharkhorum/slices/slice-1.json assets/world/kharkhorum/points.json /tmp/slice1.txt"`.
The plugin runs the same validation against the **real** height map before it builds. Any error refuses the
build: the first live attempt was refused because the exit point lay on lower real ground outside the slice,
and the point was moved.

## In-world (after building) — `/worldbuild validate`

1. Chunk snapshots of the slice plus the walk margin are taken on the main thread. Analysis runs async.
2. Points and reachability are re-checked **on the real blocks**.
3. **World diff**: every compiled block is compared with the block in the world, normalised through
   `Bukkit.createBlockData`, so implicit default states and renames such as `chain` → `iron_chain` compare
   equal. More than 0.1 % (minimum 50) mismatched blocks is an **error**.

Reports are appended to `plugins/SULD/worldbuild/slice-1.validation.txt`.

## Live results (2026-10-06, Paper 1.21.11)

| Run | Result |
|---|---|
| First build | PASS — 646,236 blocks compared, 0 mismatches, 15/15 points |
| Crash test #1 (resume continued from the saved cursor) | **48,786 mismatches**: chunks lost by `kill -9` had not been saved. Fixed: a resume replays the plan from the start. |
| Crash test #2 (fixed) | `kill -9` at unit 96 → restart → resumed automatically → PASS: 646,236 blocks compared, **0 mismatches**, 15/15 points |

A mineflayer bot joining after the build spawns at the plaza spawn point, standing on polished andesite.
