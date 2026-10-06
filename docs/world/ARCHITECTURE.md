# SÜLD WorldBuilder v2 — Architecture

The WorldBuilder turns machine-readable city plans into real Minecraft blocks, **server-side and without
WorldEdit, Axiom or any other world-editing plugin**. The production server never needs those tools.
Builders may still author detail in them and export `.schem` files, which SÜLD imports (see
[AUTHORING_WORKFLOW.md](AUTHORING_WORKFLOW.md)).

The old one-shot "generate the whole town" prototype is retired. The city is built **slice by slice**, and
each slice goes through BUILD → VALIDATE → RENDER → REVIEW → FIX → APPROVE → LOCK.

```
assets/world/kharkhorum/                        suld-api (pure Java, unit-tested)               suld-plugin (Paper)
  master-plan.json  points.json                 ┌──────────────────────────────────┐            ┌──────────────────────────────┐
  slices/slice-1.json ──── SliceLoader ───────► │ CitySpec                          │            │ WorldBuildService            │
assets/world/schematics/                        │  ├ TerrainPlan (zones, feather)   │  natural   │  plan: anchor + height map   │
  schematics.json + *.schem ── SchematicLibrary►│  ├ Placements → ModuleLibrary     │◄─ ground ──│  build: passes × chunks,     │
                                                │  │   Kharkhorum modules (Java)    │            │    time budget, rollback log,│
                                                │  │   schem:* modules (imported)   │            │    resumable state           │
                                                │ CityCompiler → CompiledCity       │──blocks──► │  spawn on the plaza          │
                                                │ CityValidator / Walkability       │◄─snapshot─ │  validate in-world, dump     │
                                                └──────────────────────────────────┘            │ /worldbuild command          │
                                                                                                 └──────────────────────────────┘
```

## Pure core (`mn.suld.api.worldbuild`)

| Type | Role |
|---|---|
| `Module`, `ModuleCanvas`, `ModuleContext` | A parametric, seeded, deterministic building block. It writes in local space (origin = anchor, y 0 = the ground block it stands on, front = +z) and can nest other modules. |
| `Transform`, `Rotation`, `BlockStates` | Mirror and rotation with block-state rewriting: facing, axis, 16-step rotation, door hinge, stair shape, chest type, connection keys. |
| `Palette` | `@role` tokens (`@roof_stairs[facing=north]`). Resolution order: base → district → placement overrides. Unknown roles are errors. |
| `Layer` | TERRAIN < INFRA < PROP < STRUCTURE < LANDMARK. A higher layer overrides a lower one, a lower one never clobbers a higher one, and same-layer clashes are **OVERLAP errors** unless the placement sets `allow_overlap`. |
| `TerrainPlan` | Elevation zones (rect/circle, crisp or with falloff). A feather band blends the slice into the real terrain, so there are no cliffs or trenches at the edges. |
| `CityCompiler` → `CompiledCity` | Compiles terrain plus all placements into blocks tagged with pass, layer and owner. It resolves fence/wall/pane connections across modules, orders output by pass → chunk → bottom-up, and produces a fingerprint. |
| `CityValidator`, `Walkability` | Checks bounds, floating blocks (6-connected support), points walkable and reachable on foot from spawn (no swimming), walkable connectors, missing modules, and duplicate landmarks/ids. See [VALIDATION.md](VALIDATION.md). |
| `schematic.Schematic`, `SchematicModule`, `SchematicLibrary` | Reads Sponge `.schem` v2/v3 (WorldEdit, FAWE, Axiom) and Litematica `.litematic`, and wraps them as `schem:<id>` modules with credits. |
| `kharkhorum.*` | The Kharkhorum kit and modules ([MODULE_LIBRARY.md](MODULE_LIBRARY.md)), plus `KharkhorumTool` (offline dump and validation). |

Everything above runs without a server: `./gradlew :suld-api:test` and
`./gradlew :suld-api:worldTool --args="slice …"`.

## Plugin side (`mn.suld.plugin.worldbuild`)

1. **Plan.** Anchor the city origin on a chunk corner at the world spawn. Load the slice area plus the
   feather band and sample the natural ground (the top solid block, ignoring trees and plants). Set the base
   y to the median ground under the plaza. Save the height map (`worldbuild/slice-1.heights.bin`), then
   compile and validate. **Any validation error stops the build.**
2. **Build.** Units are (pass, chunk), terrain first, under a 25 ms per-tick time budget plus a block cap. Before a
   position changes, its original block is appended to `worldbuild/slice-1.rollback.gz`. Terrain columns
   also clear obstacles above the new surface. Blocks are placed without physics. Unknown block states fall
   back through known renames (e.g. `chain` → `iron_chain`); anything still unknown is skipped and reported.
3. **Resume.** State is saved atomically in `worldbuild/slice-1.json`. After a restart, the build replays the
   plan against the saved height map. Placement is idempotent and skips blocks already in place, which also
   covers chunks lost in a crash (Paper had not saved them yet).
4. **Finish.** Set the world spawn to `points.spawn` and spawn radius 0. Bring each existing player to the plaza
   once. Run in-world validation: the point and reachability checks on real chunk snapshots, plus a diff of
   every compiled block against the world.
5. **Review.** `/worldbuild approve` then `/worldbuild lock`. A rebuild or rollback over an approved or locked build is
   refused until `/worldbuild unlock`.

`/worldbuild status | build | pause | resume | validate | dump | rollback confirm | approve | lock | unlock | tp <point>`
requires `suld.admin.world` (op by default).

## Measured (slice 1, Paper 1.21.11, 2026-10-06)

| Measure | Value |
|---|---|
| Placements / modules | 61 placements, 23 module types |
| Compiled blocks (incl. terrain) | 646,236 (flat-ground compile: 217,971) |
| Chunks touched | 270 |
| First build | 627,956 placed, 223,624 cleared, 0 skipped, **8.7 s**, worst MSPT 41 ms |
| Compile + validate on the server | 2.8 s (async) |
| In-world validation | 646,236 blocks compared, **0 mismatches**, 15/15 points walkable and reachable, 1.1 s |
| Rollback | 851,580 original blocks restored |

Crash test: `kill -9` at unit 96 of 823, then a restart. The build resumed by itself, finished, and in-world validation found **0 mismatches** (the first resume design failed this test; see
[VALIDATION.md](VALIDATION.md)).

## What is honest to say

- Slice 1 is **built in a real world and validated there**. It is not just a blueprint.
- The previews in `docs/world/kharkhorum/renders/` come from **blocks read back from the server**, rendered by
  `tools/blender/render_blueprint.py`. They use flat per-block colours, not Minecraft textures, so in-game it
  looks better than the renders.
- The city around slice 1 is still open grass. Palace interior, more market, residential, military and
  undercity come in later slices (master plan §6).
