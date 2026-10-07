# Тэнгэрийн мод: the full-screen skill tree

**Status:** FUNCTIONAL_BUT_INCOMPLETE + MANUAL_QA_REQUIRED. The server side is verified with a bot (below). The look
needs the owner's 1.21.11 client test (`docs/qa/CLIENT_QA_SKILL_SKY.md`).

## Why it is built this way
A Paper server cannot open a new screen on a vanilla client. Pufferfish's Skills, for example, is a client mod. So
SÜLD builds the tree in the world, in front of a locked camera, out of display entities that only the owner sees:

* `/skills` (or Shift + right-click with the class weapon) lifts the player to a private stage high above where they
  stand. They are seated on an invisible display, so the camera does not move.
* A box of panels rides with the seat and fills the whole view:
  * the front panel is the night-sky backdrop (`assets/art/source/skilltree_bg.png`, one image from
    `gemini-2.5-flash-image`, the cheapest Gemini image model);
  * every other panel is dark navy.
* `SkyLayout` (suld-api, unit-tested) lays the class tree out radially:
  * the root is the centre;
  * the grid columns fan over the upper half like branches, and each row is one ring further out;
  * the universal nodes hang below the root.
* **Node:** a slot frame tinted by state, with the node's icon on it (icon transform `GUI`, so it looks like the
  inventory icon). The frame colours are:

  | Colour | State |
  |---|---|
  | gold | learned |
  | white | can learn |
  | grey | needs points, a level or a prerequisite |
  | red | excluded |
  | dark | locked |

  Keystones, ultimates and the root are 1.2× larger.
* **Edges** are tinted quads:

  | Colour | Meaning |
  |---|---|
  | gold | both ends learned |
  | grey | one end learned |
  | dark | neither end learned |
  | red | exclusive pair |

* **Cursor:** the crosshair. The server intersects the look ray with the tree plane every tick. The node under the
  crosshair glows and shows a tooltip, the same text as the chest map: name, category, effects, trigger, cooldown,
  cost, level, requirements and state.
* **Controls:**

  | Input | Action |
  |---|---|
  | Left click | learn / rank up |
  | Right click (on the node's Interaction box) | refund |
  | W A S D | pan |
  | Hotbar wheel | zoom (camera distance 4.5–17 blocks) |
  | Shift | close |

* A header (class, free points, nodes learned, level) and a footer (controls) ride with the camera like a HUD.

## Safety
* **Refused** in combat (8 s), in a dungeon run, as a soul, mid-air, or while riding. The chest map (`/skills chest`)
  opens instead.
* While the tree is open the player is invulnerable and their damage is cancelled.
* Any command closes the tree first, so a teleport or `/spawn` runs from the real spot.
* Quitting closes it. A crash is covered too: `suld:sky_return` is in the player's PDC and the join hook sends them
  back.
* Every entity is non-persistent and tagged `suld_skysky`. An orphan sweep runs on chunk load.

## Verified live (bot `skybot.js`, dev Paper 1.21.11)
* Opening spawns 179 entities, all visible to the owner (the backdrop box, the header and footer, 36 frames, 36 icons,
  36 Interactions and about 60 edges).
* Hover: a look packet at node m1 → hover sound, tooltip shown.
* Left click → `m1=1`, and 5 → 4 free points.
* W for 1 s → the camera rose 3.1 blocks. The wheel zoomed out by 3.3 blocks.
* Shift → back at the exact starting block, and 0 entities left.

## Not done yet / honest gaps
* The look in a real client is unverified: icon orientation, backdrop crop, text size, smoothness.
* The trees have 31 class nodes + 5 universal nodes each. The reference picture has about 200, so expanding the
  content is a separate task.
* Node icons are vanilla items, not painted skill icons.
