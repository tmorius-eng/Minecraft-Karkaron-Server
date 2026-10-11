# SÜLD skill map — manual client QA guide (Minecraft 1.21.11)

For a person with the real game. Everything a bot can check over the network protocol has been checked already (see
`SKILL_TREE_QA.md`: every slot, state, connector model, tooltip line, click result and the pack download). What is left is
only what needs eyes and hands. This guide puts you in a known situation, tells you what you should see, and where to look when it is wrong.

## 0. Setup (local development server)

1. Start the server, join with the game (accept the resource pack: it is required). First join: pick any class.
2. Give yourself the admin permission on the dev server: `/op <you>` in the server console.
3. Recommended: GUI scale **2** and **3** (Options → Video → GUI Scale); repeat the visual checks at both and at **4**.

QA situations (one command each, `<you>` = your name):

| command | situation |
|---|---|
| `/skillsadmin qakit <you> fresh` | a brand-new character: level 1, no points, empty tree |
| `/skillsadmin qakit <you> states` | level 14 with a build that shows **every node state** on the first screens, 1 free point (Баатар: the only class with a cost-2 node there, so «needs points» shows next to «available») |
| `/skillsadmin qakit <you> states0` | the same build with **no** free point: every unlearned reachable node shows «needs points» (use it for Мэргэн, Бөө, Дархан, Хүлэгчин; then `/skillsadmin grantpoints <you> 1` and reopen `/skills` to see «available») |
| `/skillsadmin qakit <you> rich` | level 60, empty tree, 60 points to click through |
| `/skillsadmin reset <you>` | clear the tree (keeps level/points) |

## 1. Opens (`fresh`)

`/skills` (or main menu → «Ур чадвар»). Expect: a chest window titled «Чадварын Газрын Зураг · <класс>», a **parchment map**
(tan paper with faint contour lines, bronze frame, small turquoise corner studs), a **dark leather bottom row** with nine bronze-rimmed
button wells. Nodes must sit **on the paper**, buttons **inside the wells**. Look for: background shifted left/right, wells not under the buttons, cut-off title.

## 2. Node states (`states`, first screen)

Open `/skills`. On the first screen (top-left of the tree) you should recognise:

| where / what | state | how it must look |
|---|---|---|
| root, centre top («<Класс>-ын Сүлд», nether star) | learned (maxed) | glowing |
| left of the root: `Нүүдэлчний Мэдлэг`, right: `Уулын Гар` | available | normal icon, **no glow**, green name |
| `Ган Бие` (iron chestplate, left branch) | learned 2/3 | glowing, stack count **2** |
| `Дайчны Сүнс` (experience bottle, middle) | maxed 3/3 | glowing, stack count **3** |
| `Хурц Ир` (iron sword, right branch) | available | normal icon, no glow |
| `Цавчилтын Өргөн` (middle, row 3) | excluded by a red link | **red glass pane** |
| grey panes | not reachable yet | **grey glass pane** (name still shows on hover) |
| `Цус Ундаа` (row 4, left, redstone block) | needs points | normal icon, orange name |

Pan down (▼ twice) to see: `Үхэшгүй Эр`-type ultimate (`✦`, level locked, orange), the second ultimate (**prerequisite missing**, orange),
the keystone `★` (purple name, glowing, learned), hidden node (`l5`) revealed next to the 4th-row node.

**Key check — the glow:** only learned nodes glow. Icons that glow by themselves in vanilla (experience bottle, nether star, enchanted items)
must **not** glow when the node is not learned (the code forces the glint off for those).

## 3. Connector lines

* Learned path: **gold** lines. Next possible step: **turquoise**. Not reached: **dark brown**. Exclusive choice: **red**.
* Lines must **touch the icons' neighbours without gaps** at GUI scale 2, 3 and 4 (the slot gap is closed by drawing the line at 18 px).
* Diagonals must run the right way: a node below-left of its parent has a `/` line, below-right a `\` line.
* Look at the junction where a gold line meets a turquoise one (next to the last learned node).

## 4. Zoom, pan, filter, search

* ◀ ▲ ▼ ▶ move the map by one node; at the edges nothing happens (no error, no blank screen).
* **Spyglass** toggles detailed ↔ overview (9 columns, no lines, one node per slot). Toggling twice returns to **exactly the same place**.
* **Hopper** cycles the branch filter; nodes of other branches turn into black panes; Shift-click clears it.
* **Compass**: click → the window closes and asks for a search word in chat; type `амь` → the map re-opens centred on the first hit and
  hit nodes glow with a 🔍 in the name. Right-click the compass clears it. Type `болих` to cancel.

## 5. Tooltips

Hover several nodes (learned, available, locked, keystone, ultimate, a ranked node). Each must show: name (coloured), branch line, rank line
for ranked nodes, effect lines (`▸`), passive-spell lines (`◆`) with orange `Өдөөгч:` / `Хүлээлт:` lines, cost, required level (red if too low),
`Шаардлага:` lines (✔/✖), `✖ Хамт авч болохгүй:` for red-link nodes, a state line, and the click hint. Check that the tooltip **fits on screen** at GUI scale 4 and that
Mongolian text has no missing-glyph boxes.

## 6. Interactions

* Left-click an available node → it glows, points drop by its cost, connectors become gold.
* Right-click it → refunded; Shift-click → details in chat.
* Click a locked node → a red message in the action bar says why (level / points / path / red link / requirement).
* Rank-up: left-click `Ган Бие` until 3/3, then once more → «дээд түвшиндээ хүрсэн».

## 7. Skill points

The bottle button shows «Чадварын оноо: N» — it must equal the number in the action bar line (`◆N оноо`) and the sidebar row «Чадвар ◆ N оноо /skills».
Level up (use `/suld exp <you> 500`) → chat «◆ +N чадварын оноо!» and the number rises.

## 8. Builds and respec

Ender-eye button → respec screen: build slots (books), reset buttons, orb status. Click an empty slot to save; click the book to load;
Shift+right-click deletes. Reset → confirmation screen (green ✔ / red ✖). `/skillsadmin orb <you>` gives an **Мартагдлын Бөмбөрцөг** (violet orb icon);
right-click it → confirmation → free refund. Crafting: diamond in the centre, ender pearls on the four sides.

## 9. Class trees

Repeat `states0` (then `grantpoints <you> 1`) for each other class (`/skillsadmin` can't switch your class; create one character per
class or use five accounts): the names and icons must differ per class; the layout is the same. The protocol check found no node of
another class on any class's map.

## 10. Combat feel (ultimates)

With `rich` + `/skillsadmin unlock <you> <ult node>` (or take the path), hold the class weapon and press **F**: the effect, particles and
sounds should be clear but not overwhelming in a crowd; a second press shows the cooldown. `/skillsadmin qa <you> ultimates` measures the numbers; you judge the look.

## Reporting

Note for each failure: GUI scale, what you saw, what the table above says. A screenshot of the whole window helps most.
