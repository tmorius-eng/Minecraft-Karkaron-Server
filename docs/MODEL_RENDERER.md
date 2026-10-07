# SÜLD model renderer (Display-Entity rigs)

**Status:** SCAFFOLD → being built in Stage D1. The renderer and every rig are MANUAL_QA_REQUIRED until they are seen
in a real 1.21.11 client.

This is SÜLD's own server-side renderer for custom creatures (bosses, elites, future mobs). It uses no third-party
model plugin: no ModelEngine, BetterModel or MythicMobs. The geometry and textures ship in the resource pack as ordinary
item models. The server owns the creature: a normal (invisible) vanilla host entity does the AI, hitbox, pathing and
damage, and a rig of `ItemDisplay` bones shows the creature.

## 1. Runtime architecture

```
host (vanilla LivingEntity, invisible + silent; AI, hitbox, HP, damage)
  └─ followed by ─ root ItemDisplay (no item; teleported to the host when it moves, teleport duration 2 ticks)
                     └─ passengers: one ItemDisplay per visible bone
                          item = paper with item_model suld:entity/<rig>/<bone>
                          transformation = the bone's model-space transform (sampled from the clips),
                          rotated by the host's body yaw, interpolated over 2–3 ticks
```

* **Pure part.** `suld-api/.../model/*` is Bukkit-free and unit-tested: math (`Vec3`, `Quat`, `Mat4`), `Rig`, `Clip`,
  `Sampler` and `RigLoader`.
* **Plugin part.** `suld-plugin/.../model/*` contains one manager, `ModelService`, ticked by a single timer.
  * `ModelInstance` holds the displays of one creature.
  * Mobs whose definition names a `modelId` get a rig on spawn (`MobService`).
* **Update rate.** 10 Hz while players are within 24 blocks, 5 Hz up to 48 blocks, and frozen beyond 48. Only changed
  transforms are sent.
* **Hit flash.** Every bone item uses a `minecraft:custom_model_data` tint (index 0, default white). On damage the
  colour is set red for 4 ticks.
* **Death.** The root stops following, the `death` clip plays, then everything despawns.
* **Cleanup.** Bones and the root are non-persistent and tagged `suld_model`. An orphan sweep on entity load removes
  leftovers; the host's removal, chunk unload and plugin disable all despawn the rig.
* **PerfProbe probes:** `model.tick`, `model.spawn`, `model.despawn`, `model.anim`, `model.flash`.

## 2. Coordinates and maths (shared by the Java sampler and the Python preview)

* Units are **blocks**. Model space: +Y is up, **+Z is the creature's forward**, +X is its left.
* **Rotations** are Euler degrees `[x, y, z]`, composed as **q = qz · qy · qx**. Applied to a vector, X turns first,
  then Y, then Z, all about the parent's axes.
* **A bone's local transform** is
  `L = T(pivot − parentPivot + clipPos) · R(rest ⊕ clipRot) · S(clipScale)`,
  where `⊕` adds the Euler angles component-wise before building the quaternion. Model space is
  `M = M_parent · L`, and the root's parent is the identity.
* **The display transformation** of a bone is `translation = M·(0,0,0)`, `leftRotation = rotation(M)` and
  `scale = boneScale × clipScale` (uniform). The whole rig is then rotated about +Y by `−yaw` degrees (Minecraft yaw;
  yaw 0 faces +Z) and scaled by the rig's `scale`.

## 3. Asset contract

### Bone item models
`resourcepack/assets/suld/models/entity/<rig>/<bone>.json` are Java item models with cuboid elements only.

* **The bone's pivot is the model point (8, 8, 8)** (pixels). An `ItemDisplay` with transform `NONE` renders the model
  so that (8, 8, 8) sits at the display's origin.
* Elements must stay inside −16…32 px. Large bones are authored at `1/boneScale` and get `boneScale` > 1 in the rig.
* Each element face uses `"tintindex": 0`, so the hit flash can tint it.
* Textures: `resourcepack/assets/suld/textures/entity/<rig>/*.png` (≤ 128², ASSET_BUDGET).

### Item definitions
Each bone has a definition at `resourcepack/assets/suld/items/entity/<rig>/<bone>.json`:
```json
{"model": {"type": "minecraft:model", "model": "suld:entity/<rig>/<bone>",
           "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}
```
The plugin sets item_model `suld:entity/<rig>/<bone>`.

### `suld-plugin/src/main/resources/models/<rig>/rig.json`
```json
{"id": "khasar", "scale": 1.0, "hostHeight": 2.2,
 "bones": [
   {"id": "root",  "parent": null,   "pivot": [0, 0, 0],    "rest": [0, 0, 0], "model": false},
   {"id": "body",  "parent": "root", "pivot": [0, 1.2, 0],  "rest": [0, 0, 0], "model": true, "scale": 2.0},
   {"id": "head",  "parent": "neck", "pivot": [0, 1.6, 1.4], "rest": [10, 0, 0], "model": true, "scale": 1.0}
 ],
 "parts": [{"bone": "head", "width": 1.2, "height": 1.0}]}
```
* `model: false` marks a pure transform node with no display.
* `pivot` is in blocks, model space, at rest.
* `parts` are optional extra `Interaction` hitboxes that follow a bone.

### `suld-plugin/src/main/resources/models/<rig>/clips.json`
```json
{"clips": {
  "idle": {"length": 40, "loop": true,
           "bones": {"head": {"rot": [[0, 0, 0, 0], [20, 4, 0, 0], [40, 0, 0, 0]],
                              "pos": [[0, 0, 0, 0], [20, 0, 0.02, 0]],
                              "scl": [[0, 1.0]]}},
           "events": []},
  "bite": {"length": 16, "loop": false, "bones": {}, "events": [[7, "bite_hit"]]}
}}
```
* Keyframes are `[tick, …values]`, sorted, with linear interpolation (`step` per channel is allowed later). A channel
  with no keyframes is the rest value.
* `events` are `[tick, name]`; the boss code turns them into damage, VFX and sound.
* Required clips for a creature: `idle`, `walk`, `run`, `death`. A boss adds its abilities and `hurt` and `roar`.
  Хасар's list is in docs/BOSS_VISUAL_SPEC.md §5.

## 4. Budgets (docs/ASSET_BUDGET.md)

* Dungeon boss: ≤ 30 displays, ≤ 260 cuboids, 2 × 128² atlases, ≤ 90 KB of pack.
* Elite: ≤ 15 displays.
* Particles: ≤ 80 per tick.
* Network: ≤ 20 KB/s per boss per viewer.
* Server: a 4-player fight at 20 TPS with mspt < 35.
