# Content data files

SÜLD's game content is data, not code. The plugin reads it from JSON files; nothing needs a rebuild to add a mob,
move a dungeon gate, retune a region or rewrite a quest.

| What | File | Data-folder override |
|---|---|---|
| Mobs (name, vanilla host, tier, level, loot table, role, model rig) | `content/mobs.json` | `plugins/SULD/content/mobs.json` |
| Regions, named areas, inner/world edge | `content/world.json` | `plugins/SULD/content/world.json` |
| The dungeon ladder (waves, boss and phases, gate site, completion bonus) | `content/dungeons.json` | `plugins/SULD/content/dungeons.json` |
| The story «Сүлдний Зам» (chapters, giver, hint, story text) | `content/quests.json` | `plugins/SULD/content/quests.json` |
| World events, world bosses (`docs/WORLD_BOSSES.md`), world-unique relics | `content/events.json` | `plugins/SULD/content/events.json` |
| Historic sites (place, kind of build, bearing and distance, history label, story) | `content/sites.json` | `plugins/SULD/content/sites.json` |
| Holograms: the floating boards (guide, «Мөнх Тэнгэрийн Элч», plaza) and the labels over gates, sites, the training yard, profession nodes and soul beacons | `content/holograms.json` | `plugins/SULD/content/holograms.json` |
| Kharkhorum's people: point, skin, name, glow, action, greetings, topics with history labels (`docs/NPC_DIALOGUE.md`) | `content/npcs.json` | `plugins/SULD/content/npcs.json` |
| The merchants' stock: the market street's general shop and the four gate markets (material or catalog item, amount, price, minLevel) | `content/shops.json` | `plugins/SULD/content/shops.json` |
| Side quests: giver, objective, level, classes, requirements, reward, history label (`docs/SIDE_QUESTS.md`) | `content/side_quests.json` | `plugins/SULD/content/side_quests.json` |
| Items, affixes, sets, loot tables | `items/*.json` (suld-api) | `plugins/SULD/items/` |
| Skill trees (5 classes) | `skills/*.json` (suld-api) | `plugins/SULD/skills/` |
| Game rules: EXP curve, death, economy, protection, database | `config.yml` | `plugins/SULD/config.yml` |

The **rules** stay in code (`suld-api`, `mn.suld.api.balance`): how a level and tier become health, attack and
EXP, how chapter EXP follows from the chapter's level, mitigation, party sharing. They are tuned by the simulator
(`docs/PROGRESSION_SIMULATION.md`); content files say *what* exists, the rules say *how strong* it is.

## Editing on a server

1. `/suld content export`: writes the bundled files to `plugins/SULD/content/`. A file that is already there is kept.
2. Edit the files. Delete any file you do not want to change: the bundled one is used for it.
3. `/suld content validate`: checks the server's files, including loot tables and items against the item catalog,
   and lists each problem with its file and path, for example
   `dungeons.json: dungeons[1].waves[0][0]: no mob mob.no_such_mob in mobs.json`.
4. Restart the server. Content is read once, at plugin load, before anything uses it.

If the files have **any** problem at startup, the console lists them and the bundled content runs instead. A broken
edit never takes the server down and never half-applies.

## What is checked

- Every file parses, has `"format": 1` and the fields it needs, with the right kinds and ranges.
- Ids have their form (`mob.x`, `region.x`, `area.x`, `dungeon.x`, `quest.x`, `event.x`, `relic.x`) and are unique.
- References resolve: a region's and a wave's mobs, a boss, an area's region, a dungeon's region, a quest's target
  (mob, dungeon, region or item), an event's target mobs, loot tables (on `validate`).
- Mob hosts are living vanilla entity types; tiers, hall themes and quest types are known values.
- Each region owns one discovery bit (`bit` 0..15, unique) and each area one (`index` 16..63, unique): a player's
  discoveries are stored by these numbers, so a region or area can move in the list, and a new one goes in with a
  free number. Never renumber one that players may have discovered.
- Boss phases start at 1.0 and go strictly down.
- Every dungeon has a gate `site`. A dungeon outside the ladder (`DungeonLadder`) also needs a `completion` bonus; on a
  ladder dungeon `completion` is ignored, because its reward comes from the progression rules (PROG-003).
- `discoveryExp` on regions and areas is ignored: discovery EXP comes from the rules (`Rewards.landmarkExp`,
  `Rewards.regionDiscoveryExp`) so it scales with the curve (PROG-003).
- The ids the plugin's code uses by name (tutorial, the first dungeon, boss brains, the outer regions) still exist:
  `ContentLoader.REQUIRED`. They can be edited, but not removed or renamed.
- **npcs.json:** every id is a slice-1 point of `assets/world/kharkhorum/points.json` (jar: `/world/kharkhorum/points.json`,
  `CityPoints`) or a `"dynamic": true` role; a point is served once; every campaign NPC effect names an entry; the skin
  name, glow, action and icon are known; a `SHOP` names a shop that exists; a paragraph saying «түүхэн баримт» is
  `VERIFIED` (`docs/NPC_DIALOGUE.md`).
- **shops.json:** unique `shop.*` ids, non-empty stock, each card exactly one of `material` (a known Material) or `item`
  (a catalog id), `amount` 1–64, `price` ≥ 0, `minLevel` 1–100.
- **side_quests.json:** unique `squest.*` ids; the giver is an NPC of npcs.json (not a dynamic one); the type is one a
  side quest can have (no REACH_LEVEL) and its target exists like a chapter's; `classes` are class names; every
  `requires` names a chapter or a side quest, with no loop; `loot` is a table of items/loot.json; `repeatable` is
  refused; a story saying «түүхэн баримт» is `VERIFIED`.
- **Every loot table has a caller** (`LootTableCallersTest`): a mob, a dungeon reward, a world boss, a world event's
  `loot.world_event.<name>`, the open-world rare roll or a side quest; a table nothing rolls is deleted, not kept.
- **Achievement subjects exist** (`AchievementSubjectsTest`): the chapters, sites and side quests an achievement names.

## Field notes

- **mobs.json:** health, attack and EXP come from `level` and `tier` (progression v2). `health`, `attack` and
  `exp` may be given to override them, but the simulator's balance assumes the curve. `role`: `core` (the first hunt
  and dungeon), `wild` (home regions), `ladder` (outer lands), `boss`. `model` names a rig in
  `resources/models/<rig>/` (docs/MODEL_RENDERER.md).
- **world.json:** positions are offsets from the Kharkhorum plaza; bearings are degrees (0 = north, 90 = east).
  Region shapes: `square` (`half`), `sector` (`minRadius`, `maxRadius`, `fromDeg`, `toDeg`), `circle`. An `outer`
  region's level grows with the distance from `innerEdge` (its `minLevel`) to `worldEdge` (its `maxLevel`); other
  regions take the middle of the named area's band. `history`: `VERIFIED`, `INSPIRED` or `FICTION`
  (the rules in docs/research/history/README.md apply to every name and text).
- **dungeons.json:** the list order is the ladder: each dungeon needs the one before it cleared. A boss's health is
  sized for the dungeon's party by the rules. `site.radius` is 64..4900 blocks from the spawn.
- **quests.json:** the list order is the story. A chapter's EXP is set by the rules (35 % of a level at the
  chapter's level) unless it gives `exp`; `coins` are paid as written.
- **holograms.json:** read on its own (`mn.suld.api.hologram.HologramLoader`), and again with
  `/suld holograms reload` (no restart; `/suld holograms list` shows where each board stands). A server copy with any
  problem is listed in the log and the bundled file is shown instead.
  - `boards`: each has an `id`, a `place` and its lines. `place: offset` stands at a city point (`anchor`: `spawn`,
    `event.plaza` or any point of `assets/world/kharkhorum/points.json`; `city` = the plaza origin) plus
    `offset: [x, y, z]`; `place: arc` does the same but looks for clear ground within `radius` (default 7.5) of
    it; `place: above` stands on another board (`above: <board id>`) `gap` blocks (default 0.3) over the top of its
    text, and follows it. The height of the board below is measured from its lines, wrapped at its `lineWidth`.
  - Look (boards and labels alike): `billboard` (`center`, `vertical`, `horizontal`, `fixed`), `scale`, `background`
    (`#AARRGGBB`, `#RRGGBB` or `none`), `lineWidth` (pixels), `align`, `shadow`, `viewRange` (1 ≈ 64 blocks).
    `tag` adds a scoreboard tag; `sparkle` adds the guide boards' particles.
  - Lines are MiniMessage. A fixed board has `text`. A board with a `source` shows live values and needs a line list
    for each state of it in `variants`: `envoys` → `names`, `empty`; `khuraldai` → `ballot`, `campaign`, `idle`;
    `chronicle` → `lines`, `empty`. `{key}` is filled with a live value (player names are escaped, never tags). A
    line that is only `{list}` becomes `headers.<list>` (when the list has entries) and one `items.<list>` line per
    entry. Guide lines may use the content counts `{chapters}`, `{dungeons}`, `{regions}`, `{areas}`, `{sites}`,
    `{relics}`, `{campaigns}`.
  - `labels`: by id, each with `dy` (blocks above the thing it names) and `text` or `variants`. The labels the code
    asks for (`HologramLoader.REQUIRED_LABELS`: `dungeon.gate`, `site.plate`, `training.*`, `profession.node`,
    `profession.vein`, `soul.beacon`) can be edited, not removed or renamed; their `{keys}` are listed in the bundled
    file.

## For developers

- `ContentLoader` reads and checks the files; `ContentPack` holds the result; `Content.install` (plugin `onLoad`)
  picks the server's files or the bundled ones; `Content.pack()` serves them.
- `SuldContent`, `WorldContent`, `DungeonContent`, `LadderContent` and `QuestContent` are views of the pack. Their
  named constants (`WorldContent.BANDIT`, `DungeonContent.GOBI_TOMB`, ...) are the ids the code uses.
- The simulator reads the same bundled files, so live balance numbers always match the shipped content.
- Tests: `ContentDataTest` (valid bundle, broken edits refused, per-file override, a new dungeon without code),
  `ContentIntegrityTest`, `AreaContentTest`.
