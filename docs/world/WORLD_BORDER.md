# World border

The overworld is a **10 000 × 10 000** block square (radius 5 000), centred on the **world spawn**: Kharkhorum's plaza.
Every region ring and named area (docs/world/AREAS.md) is measured from the spawn, so the outer ring (1500–5000) ends
exactly at the border.

| Setting (`config.yml` → `world.border`) | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Manage the native border. |
| `center` | `spawn` | `spawn`, or fixed coordinates `"x z"`. |
| `diameter` | `10000` | Edge length in blocks. |
| `warning-distance` | `24` | Red screen tint this close to the edge (vanilla). |
| `damage-buffer` / `damage-per-block` | `4` / `0.5` | Vanilla damage past the edge. |

## How it works

* It is Minecraft's own world border, stored in `level.dat`. SÜLD sets it **once** on start, and again only when the
  config or the spawn changes (the city build moves the spawn and calls `WorldBorderService.apply`). Nothing runs per
  tick, and no `/worldborder` command is repeated.
* The vanilla client draws it, and vanilla collision keeps walkers, riders, horses and boats inside.
* What vanilla lets through is closed by events only (no per-player scans):
  * **Teleports** (ender pearl, chorus fruit, commands, plugins) whose destination is outside are cancelled, with the
    message «Их Монголын хилээс гадна гарах боломжгүй.». `suld.border.bypass` (op) lets staff through.
  * A **respawn point** outside is moved to the spawn.
  * A player who **logs in outside** (an old save) is moved to Kharkhorum asynchronously.
* SÜLD has one overworld. Dungeons run in that world near the party leader, so they are inside the border too.

`/suldworld border` shows the live border; `/suldworld border apply` re-applies the config.

## Verified (dev server, 2026-10-07)

* On start: `World border (start): 10000 x 10000 blocks centred on -16, -46 (radius 5000)`, which is the spawn.
* A non-op bot teleported to x = 6000 was refused with the message above. Teleports to x = 4900 and op teleports
  passed.

MANUAL_QA_REQUIRED: walk, sprint and ride a horse into the edge in a real client (N/S/E/W and a corner); throw an
ender pearl over it.
