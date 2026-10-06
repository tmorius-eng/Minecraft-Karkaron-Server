# World data formats

All city data is machine-readable JSON under `assets/world/`, bundled into the plugin jar under `world/`.

## Slice file — `assets/world/<city>/slices/<slice>.json`

```json
{
  "city": "kharkhorum", "slice": "slice.1", "seed": 1220,
  "bounds": {"x1": -112, "z1": -92, "x2": 104, "z2": 136},
  "terrain": {
    "feather": 12, "clearance": 32, "surface": "minecraft:grass_block",
    "zones": [
      {"id": "zone.plaza", "circle": [0, 0, 28], "elevation": 1, "falloff": 0},
      {"id": "zone.palace_terrace", "rect": [-72, -92, 71, -57], "elevation": 8, "falloff": 0, "surface": null}
    ]
  },
  "district_palettes": {"district.market": {"roof": "minecraft:deepslate_tiles"}},
  "placements": [
    {"id": "gate.south", "module": "gate.imperial", "at": [0, 0, 127], "district": "district.outer_wall", "landmark": true},
    {"id": "wall.south_west", "module": "wall.straight", "at": [-18, 0, 127], "mirror": true, "params": {"length": 35}},
    {"id": "ger.c", "module": "yurt", "at": [-44, 1, 25], "rotate": 90, "params": {"size": "large"}}
  ]
}
```

- **Coordinates** are city-local: origin = plaza centre at base ground level; +x east, +z south, +y up.
  The origin sits on a chunk corner in the world.
- **Elevation** `e` means the top solid block is at y = e and players stand at y = e + 1.
- **Placements**:

  | Field | Meaning |
  |---|---|
  | `at` | Module origin. y is the surface the module stands on. |
  | `rotate` | 0, 90, 180 or 270, clockwise seen from above. |
  | `mirror` | Mirror on x before rotating. |
  | `params` | Module parameters (see MODULE_LIBRARY.md). |
  | `palette` | Role overrides for this placement. |
  | `district` | Selects the district palette and is used in reports. |
  | `landmark` | Unique: a second landmark placement of the same module is an error. |
  | `allow_overlap` | May overwrite same-layer blocks of earlier placements. |
  | `seed` | Defaults to a hash of the id. |

## Points — `assets/world/<city>/points.json`

`{"points": [{"id", "type", "district", "x", "y", "z", "yaw", "required", "slice", "note"}]}`.
`y` is the block the player's feet occupy. Every point of a slice must be walkable and reachable on foot
from that slice's `spawn` point, or the build is refused (`required: false` downgrades unreachability to
a warning).

## Schematic catalogue — `assets/world/schematics/schematics.json`

```json
{"schematics": [
  {"id": "fountain_square", "file": "fountain_square.schem", "front": "south", "sink": 1, "layer": "structure",
   "title": "Fountain Square", "author": "adripika12", "source": "https://minecraftschem.com/content/3964/x",
   "licence": "not stated by the author — ask before public use"}
]}
```

Each entry becomes module `schem:<id>`. `front` is the side the build faces as authored (it is turned so the
front faces +z like every module). `sink` is how many bottom layers are foundation below the ground. The
module params `replace` (block substitutions), `clear` (clear the empty cells inside the bounds, default true)
and `pass` apply. **Every third-party schematic must carry author, source and licence.**

Readable formats: Sponge `.schem` v2/v3 (WorldEdit, FAWE, Axiom) and Litematica `.litematic`. Legacy MCEdit
`.schematic` (numeric ids) is rejected, so re-save it as `.schem`.

## Voxel dump (renderer input)

Plain text, one block per line: `x y z minecraft:block[state]`. `#` lines are comments. It is produced by
`:suld-api:worldTool` (compiled modules and slices) and by `/worldbuild dump` (blocks read back from the
world), and read by `tools/blender/render_blueprint.py`.
