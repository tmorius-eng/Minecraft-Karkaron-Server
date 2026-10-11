# Content data files

SÜLD's game content is data, not code. The plugin reads it from JSON files; nothing needs a rebuild to add a mob,
move a dungeon gate, retune a region or rewrite a quest.

| What | File | Data-folder override |
|---|---|---|
| Mobs (name, vanilla host, tier, level, loot table, role, model rig) | `content/mobs.json` | `plugins/SULD/content/mobs.json` |
| Regions, named areas, inner/world edge | `content/world.json` | `plugins/SULD/content/world.json` |
| The dungeon ladder (waves, boss and phases, gate site, completion bonus) | `content/dungeons.json` | `plugins/SULD/content/dungeons.json` |
| The story «Сүлдний Зам» (chapters, giver, hint, story text) | `content/quests.json` | `plugins/SULD/content/quests.json` |
| World events, world-unique relics | `content/events.json` | `plugins/SULD/content/events.json` |
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
- Each area owns one discovery bit (`index` 16..63, unique).
- Boss phases start at 1.0 and go strictly down.
- Every dungeon has a gate `site` and a `completion` bonus.
- The ids the plugin's code uses by name (tutorial, the first dungeon, boss brains, the outer regions) still exist:
  `ContentLoader.REQUIRED`. They can be edited, but not removed or renamed.

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

## For developers

- `ContentLoader` reads and checks the files; `ContentPack` holds the result; `Content.install` (plugin `onLoad`)
  picks the server's files or the bundled ones; `Content.pack()` serves them.
- `SuldContent`, `WorldContent`, `DungeonContent`, `LadderContent` and `QuestContent` are views of the pack. Their
  named constants (`WorldContent.BANDIT`, `DungeonContent.GOBI_TOMB`, ...) are the ids the code uses.
- The simulator reads the same bundled files, so live balance numbers always match the shipped content.
- Tests: `ContentDataTest` (valid bundle, broken edits refused, per-file override, a new dungeon without code),
  `ContentIntegrityTest`, `AreaContentTest`.
