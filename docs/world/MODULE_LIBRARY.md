# Kharkhorum module library

Modules face +z (south). y 0 is the surface the module stands on. Materials are palette roles
(`Palette.KHARKHORUM`): stone/tuff walls on a mud-brick base course, deepslate-tile roofs, gold ridges used
sparingly, red mangrove columns, white felt with sky-blue and orange trim, patinated bronze for statues.

| Module | Layer | Params | Notes |
|---|---|---|---|
| `road.paved` | INFRA | points, width, class (main/cross/secondary/lane), lanterns | follows the terrain; steps on +1 rises; arm lanterns |
| `canal.segment` | INFRA | length, cap_start, cap_end, rail_gaps | 9 wide, 7 water, surface 1 below banks, balustrade |
| `bridge.arched` | STRUCTURE | half_width | arched deck rising 3, parapets, lanterns |
| `stair.grand` | STRUCTURE | rise, half_width, landing_depth | 11 wide, landing every 4 steps, braziers |
| `wall.retaining` | STRUCTURE | length, height, gaps, hangings | terrace face, balustrade, pilasters, SÜLD hangings |
| `gate.imperial` | LANDMARK | — | 35 wide, 7×9 arched passage, twin towers, gate hall, braziers, tug |
| `wall.straight` | STRUCTURE | length, height, end_bastion | 5 thick, 12 high, crenels, walkway, pilasters |
| `tower.watch` | STRUCTURE | — | 9×9, ladder, floors, crenellated deck, pavilion roof, brazier |
| `plaza.ceremonial` | INFRA | radius, avoid | Ø57 raised plaza, rings/spokes, 9 white tug, lanterns, benches, planters |
| `monument.equestrian` | LANDMARK | scale | Khan on a rearing horse with the SÜLD spear, stepped pedestal |
| `palace.gate` | LANDMARK | wall_length | podium, red colonnade, painted frieze, broad hip roof, wall stubs |
| `market.stall` | STRUCTURE | variant (0-2), colour | 5×4, three awning styles, goods; merchant stands at (0,1,-2) |
| `shop.house` | STRUCTURE | roof (hip/flat) | 9×11 two-storey merchant house, balcony, roof terrace |
| `market.street` | STRUCTURE | length | stalls every 8, houses every 12, sidewalks, lanterns |
| `yurt` | STRUCTURE | size (small/medium/large) | Mongol ger: felt wall and roof, toono, stove pipe, furniture |
| `yard.fence` | PROP | x1, z1, x2, z2, gates | fenced yard with gates |
| `prop.camp` | PROP | kind (firepit/cart/hay/woodpile/hitching/drying) | yard life |
| `blacksmith.forge` | STRUCTURE | — | open forge, hearth + chimney, anvil, grindstone, quench |
| `shrine.sky` | LANDMARK | — | Tenger shrine: terrace, ovoo, sky pole, prayer flags, braziers |
| `prop.cairn`, `prop.class_stones`, `prop.relay_post` | PROP | — | small gameplay props |
| `landscape.grove` | PROP | rect, count, species, spacing, avoid, flowers | cherry, larch, birch, elm (persistent leaves) |
| `schem:<id>` | from catalogue | replace, clear, pass | imported Sponge/Litematica schematics |

Render any module offline:
`./gradlew :suld-api:worldTool --args="module gate.imperial /tmp/gate.txt"`, then
`blender -b -P tools/blender/render_blueprint.py -- --input /tmp/gate.txt --output gate.png`.
