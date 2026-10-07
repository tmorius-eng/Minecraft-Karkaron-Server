# SÜLD skill tree — QA record

Honest record of what was verified, by whom/what, and what is still open. Date of the runs: 2026-10-07.

## UNIT TESTED (suld-api, 52 skill-tree tests; full build 269 tests, 0 failures)

`SkillTreeTest` (22), `SkillEngineTest` (19), `SkillLoaderTest` (10), `JdbcSkillStateIT` (1, below). See `SKILL_TREE_TEST_PLAN.md`
for what each covers: graph rules, ranks, exclusive links, convergence, refund consistency, economy, respec cooldown/history,
builds, normalisation after level loss, forged-data/exploit cases, storage codec, data-file validation with exact
file/field messages, generated tooltips.

## INTEGRATION TESTED (real PostgreSQL 16, throwaway database)

* `JdbcSkillStateIT`: V10 migration on a fresh schema, empty state for a profile without skill data, full round trip of
  ranks/builds/granted/respec history, refusal to load newer/corrupt skill data.
* Existing PostgreSQL ITs (profile top, style, clan, relic) still pass on the V10 schema.

## LIVE PAPER TESTED (Paper 1.21.11, bots speaking the real protocol; the server's usual local dev setup)

Run with PostgreSQL storage, including a **full server restart**:

| Step | Result |
|---|---|
| JOIN → class Баатар → `/skillsadmin inspect` | 0 points, no nodes, consistent |
| `/skills` | map window opens, title «Чадварын Газрын Зураг · Баатар»; root, universal nodes, class nodes, 4 toolbar arrows, zoom, search, filter, points panel, respec button present with expected names |
| click a node at level 1 | refused: «Ган Бие» — түвшин 3 хэрэгтэй (action bar) |
| level 60 | 44 points; points panel shows 44 |
| left click l1 ×2, r1 | rank 2 (stack count 2, glint), r1 learned; connectors on the path switch from `tree_d2_next` to `tree_d2_on` / `tree_d1_on`; **max health attribute 28** (20 + 2×4) |
| right click l1 | rank 1, max health 24; refund works |
| admin unlock of m2 while its rival l2 is learned | refused: «Цавчилтын Өргөн» нь «Хүлцэл»-тай хамт байж болохгүй |
| `/skillsadmin fire … LOW_HP` with Эр Зориг learned | dispatcher ran the proc; **absorption 6.0** (after fixing MAX_ABSORPTION clamp found by this test) |
| `/skills search амь` | 6 nodes found and listed; map re-opened centred |
| zoom, filter, pan buttons | overview shows 9 columns with grey panes for locked nodes; filter label changes; pan shifts nodes |
| `/skills build save tank`, `list`, `load tank` | saved; listed `tank (1/3)`; load refused with 298 s cooldown after a reset |
| `/skill l1` | detail with clickable [нээх] [буцаах] [газраас харах] |
| `/skills info` | summary lines |
| Orb: admin gives, right click, confirmation screen, confirm | «Буцаагдлаа: 8 оноо», orb consumed, max health back to 20, respec count 1 |
| `/skillsadmin validate`, `reload` | valid, reloaded (175 nodes for 5 classes) |
| **Quit, restart the server, rejoin** | `l1=2`, build `tank`, respec count, max health 28, `◆42 оноо` on the action bar all restored from PostgreSQL |
| F ultimate, all five classes (Баатар Чингисийн Уур, Мэргэн Хэтийн Харваач, Бөө Өвгөдийн Залбирал, Дархан Мянган Алх, Хүлэгчин Салхины Гэгээн) | effects applied (strength/resistance/regeneration/speed…), action bar «✦✦ <name>», second press «— 44 сек», idle bar «F ✦ Ns · ◆N оноо» |
| Baatar spells with M-branch modifiers learned (`m1…m6`) | combos cast (✦ Хааны Хамгаалалт …), resource spent with the cost modifiers, no errors in the server log |

Bugs this testing found and fixed: unquoted `: ` in plugin.yml stopped the plugin loading; shields did nothing
(absorption is capped by the MAX_ABSORPTION attribute); spell clicks on a block that protection had "cancelled" never
counted (the city denies block use) — spell casting now ignores that cancellation.

Not measured live: the numeric effect of each stat/proc/modifier/keystone in a real fight (code paths compiled and reviewed),
craft of the Orb at a crafting table (recipe registered), tab completion, the sidebar points row.

## MANUAL MINECRAFT CLIENT TESTED

**Not performed.** Status of the interactive map: **FUNCTIONAL BUT MANUAL QA REQUIRED.**

Checklist for a person with the game and the resource pack:

1. `/skills`: does the parchment background fill the chest, do the toolbar wells line up with the bottom row?
2. Connector lines: do horizontal/vertical/diagonal pieces join between node icons at GUI scale 2, 3 and 4? Are gold / turquoise / brown / red clearly different?
3. Node icons: legible; glint on learned nodes; grey/red panes for locked/excluded; rank number on ranked nodes.
4. Tooltips: not cut off at the screen edge, orange trigger/cooldown lines readable, Mongolian text without missing glyphs.
5. Zoom, pan, filter, search (chat prompt then map re-opens), respec screen, confirmation screen, builds.
6. Ultimates: particles/sounds sensible and not overwhelming in a crowd.
7. Orb of Oblivion: icon, tooltip, crafting recipe (diamond centre, ender pearls on the four sides).
