# SÜLD skill tree — QA record

Honest record of what was verified, by whom/what, and what is still open. Date of the runs: 2026-10-07.

## UNIT TESTED (suld-api skill-tree tests + `SkillTest` cooldowns + plugin `SkillPackAssetsTest`; unit tests 274, 0 failures)

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

## LIVE PAPER — COMBAT VERIFICATION (measured, not "the code ran")

`/skillsadmin qa <player> all` on Paper 1.21.11: **313 checks, 312 pass, 0 fail, 1 not measurable** (one run of every suite,
2026-10-07 05:44 UTC). Full table: [`SKILL_TREE_COMBAT_VERIFICATION.md`](SKILL_TREE_COMBAT_VERIFICATION.md); raw data:
`audit/qa/skill-qa-2026-10-07.json`. Every effect in the data files has a row (checked against the JSON):

| category | effects in the data | rows | pass | how it is measured |
|---|---|---|---|---|
| stat nodes | 93 | 93 | 93 | attribute values, final damage of real hits, crit share over 4000 hits, dodge over 3000 hits, EXP from real kills, items from 300 real boss kills… |
| spell modifiers | 65 | 65 | 64 | the real spell cast through the normal cast path: damage per hit, resource spent, reach, fire/slow/weakness ticks, mark ratio, heal, refund, velocity, echo over 150 casts; Олон Сум with real arrows |
| passive spells | 28 | 61 | 61 | chance over 300 triggers (4σ), per-node cooldown, effect size |
| triggers on real events | 8 | 8 | 8 | melee hit, crit, kill, damage taken, low health, sneak, cast, spell hit — each from the real Bukkit event |
| keystones | 5 | 13 | 13 | every upside and downside |
| ultimates | 15 | 52 | 52 | buffs, damage, reach, heal/cleanse, 45 s cooldown |
| per-spell cooldowns | 20 | 21 | 21 | cooldown left after the cast, recast refused, ready again |

Not measurable on one account: `boo.m2.SUNSNII_ZALBIRAL.RADIUS_PCT` (the heal radius only matters for other players).

**Product bugs found by these measurements and fixed:**

* Beam spells (Мэргэн «Тэнгэрийн Сум», Хүлэгчин «Жадны Шидэлт») never hit an enemy standing on the same ground when aimed
  straight: the hit test measured from the eye-height ray to the enemy's **feet** (1.6 blocks > the 1.2 radius). It now
  measures to the centre of the hitbox.
* BURN modifiers: «Хайлсан Төмөр» set its own 2 s fire **after** the node's longer fire and wiped it; on «Галын Давталт» the
  node's +3 s equalled the spell's own 3 s, so the node did nothing. Node seconds (and Алтан Дөш's +3 s) now add to the spell's fire.
* Map: icons that glow in vanilla (experience bottle, nether star) looked learned when they were not; zoom out/in lost the view.
* Looking straight up/down made spell directions NaN; shields did nothing above the absorption cap (fixed earlier this phase).

Harness problems that were the suite's own fault, not the game's (fixed in the suite, listed so nobody re-chases them): wolves
reset their max health; unloaded chunks removed parked dummies; a non-collidable entity cannot be hit by projectiles; mineflayer
mis-scales the 1.21.11 velocity packet; night monsters killed the test player; echoes and lingering ultimates leaked into the next
test; random arrows/anvils hit one dummy more than once; horses roll a random speed.

## LIVE PAPER — SKILL MAP OVER THE PROTOCOL (all five classes)

A bot opens the real chest window and checks every slot against an independent JavaScript re-implementation of the rules
(written from the data files, not from the Java code). Final jar, 2026-10-07:

| class | fixture | checks | failures | nodes checked | connectors checked | tooltips checked | jar |
|---|---|---|---|---|---|---|---|
| Баатар | states | 380 | 0 | 107 | 142 | 102 | final |
| Мэргэн | states0 | 417 | 0 | 120 | 158 | 114 | final |
| Бөө | states0 | 417 | 0 | 120 | 158 | 114 | final |
| Дархан | states0 | 417 | 0 | 120 | 158 | 114 | final |
| Хүлэгчин | states0 | 417 | 0 | 120 | 158 | 114 | final |

Covered per class: `/skills` opens with the class title (1); every node's material, name, glint, stack count and state text in
five views (2); the model of every connector between visible neighbours, and no stray items (3); pan incl. the clamped edges,
zoom round trip back to the same view, overview geometry (4); tooltip cost, level, requirements, red-link rivals, rank, hint (5);
unlock / rank up / maxed / rank down / refund / excluded / needs-points clicks with the server's answer (6); the points panel at 0
and with the fixture (7); saving and listing a build from the respec screen (8); no node of another class on the map (9);
resource-pack offer, SHA-1 of the downloaded zip equal to the one in the offer, every map asset present in the zip (10).

## MANUAL MINECRAFT CLIENT TESTED

**Not performed** (no person has looked at it in the game yet). Status of the interactive map: **FUNCTIONAL BUT MANUAL QA REQUIRED.**
The step-by-step guide with fixtures is [`SKILL_TREE_CLIENT_QA.md`](SKILL_TREE_CLIENT_QA.md).

Checklist for a person with the game and the resource pack:

1. `/skills`: does the parchment background fill the chest, do the toolbar wells line up with the bottom row?
2. Connector lines: do horizontal/vertical/diagonal pieces join between node icons at GUI scale 2, 3 and 4? Are gold / turquoise / brown / red clearly different?
3. Node icons: legible; glint on learned nodes; grey/red panes for locked/excluded; rank number on ranked nodes.
4. Tooltips: not cut off at the screen edge, orange trigger/cooldown lines readable, Mongolian text without missing glyphs.
5. Zoom, pan, filter, search (chat prompt then map re-opens), respec screen, confirmation screen, builds.
6. Ultimates: particles/sounds sensible and not overwhelming in a crowd.
7. Orb of Oblivion: icon, tooltip, crafting recipe (diamond centre, ender pearls on the four sides).
