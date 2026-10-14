# SÜLD — Weapon animation audit (audit 2026-10-14)

Sources: `resourcepack/assets/minecraft/items/bow.json`, `resourcepack/assets/suld/models/item/**`,
`resourcepack/assets/suld/models/entity/vfx/ring.json`, `SkillService`, `BowQuiver`, `CombatFeel`, `CombatListener`,
`ClassWeapons`, `tools/validation/validate_resourcepack.py`. The web-session `weapons.md` is not in the repository; the
"bytecode-verified" root cause in the prompt was re-derived from source here (no bytecode was read in this session).

## 1. The five class weapons

| Class | Base item | Models | Draw / use animation | L feedback | R feedback (new) |
|---|---|---|---|---|---|
| Баатар | iron_sword | 6 tiers, cmd 871001–871006 | none (swing) | arm swing + red horizontal slash decal (`CombatFeel.swing`) | off-hand swing + soft sweep |
| Мэргэн | bow | 6 tiers + 3 pulling stages each, cmd 871011–871016 | **bow draw pulling_0/1/2** (was never shown) | arm swing + small decal | off-hand swing + string tick; hold R = draw |
| Бөө | blaze_rod | 6 tiers, cmd 871021–871026 | none | purple slash decal | off-hand swing + amethyst chime |
| Дархан | iron_axe | 6 tiers, cmd 871031–871036 | none | orange vertical slash decal | off-hand swing + soft sweep |
| Хүлэгчин | iron_sword (spear model) | 6 tiers, cmd 871041–871046 | none | thrust decal | off-hand swing + soft sweep |

Assets verified on disk: 21 pulling models and 21 pulling textures (`mergen_1..6_pulling_0..2`,
`surgamj_num_pulling_0..2`); every `using_item` branch of bow.json has an idle model and a three-stage `use_duration`
range dispatch (0.65 s → pulling_1, 0.9 s → pulling_2, scale 0.05).

## 2. Мэргэн bow draw — root cause (re-derived from source)

1. `BowQuiver` stashes every arrow while the class bow is held. The vanilla client's `BowItem.use` finds no projectile
   and returns FAIL locally, so the client never enters `using_item = true` — the pack's pulling models are never
   selected.
2. `SkillService.onBowUse` set `setUseItemInHand(DENY)` and fired a full-power `shoot(p)` on the right-click *press*,
   by design (a drawing client drops left clicks, which broke L-R-L combos).

Result: the bow could never visibly draw; every shot was an instant full-draw arrow with no pull.

## 3. Fix (WP-01 … WP-10)

| ID | Change | Where |
|---|---|---|
| WP-01/02 | Hybrid tap / hold. Arrows stay stashed and the vanilla use stays denied. A right click with no combo under way and the bow ready **starts the use on the server** (`Player#startUsingItem(HAND)`); the client mirrors the server's living-entity "using item" flag (LocalPlayer honours it), so the 3-stage model plays in first and third person. Release: Paper's `PlayerStopUsingItemEvent` (client let go) → `release` → `shoot(p, drawForce(ticks))`. Force: tap (< 4 ticks) 0.6; then ticks / 18, floor 0.6, full 1.0 at 18 ticks = the pack's pulling_2 threshold. One draw per second from its start (`SHOT_MS`), so a full draw (0.9 s) keeps the simulated pace. | `SkillService.onBowUse/startDraw/onBowRelease/release/drawForce` |
| WP-01 fallback | A per-draw tick task: put the bow away / die / log out → cancel; no active use after 2 ticks → release; > 5 s → release at full; a client that did not mirror the draw keeps sending 4-tick use repeats — when they stop for > 6 ticks the shot is released at the last repeat | `startDraw` |
| WP-01 safety | A vanilla `EntityShootBowEvent` from the class bow that is not SÜLD's own (an arrow left in a full bag) is cancelled | `SkillService.onStrayClassBowShot` |
| WP-02 combo rule | An L that reaches the server during a draw cancels it and counts as the combo's L; a right click during a draw is never a combo R | `SkillService.click` |
| WP-03 | Bow not ready: dry string click `BLOCK_DISPENSER_FAIL` (0.35, 1.6) + «Нум — 0.4 сек», at most every 0.4 s | `SkillService.dryString` |
| WP-04 | Release ring: the model is a flat XZ plane; it was laid flat at eye height (seen edge-on, a line). Now rotated 90° about X so it stands across the shot, takes the shooter's yaw and pitch, 0.18 below the eye line | `CombatFeel.ring`, `RING_DROP` |
| WP-05 | Every accepted combo R swings the off hand (first + third person) and plays a class cue. Main-hand swing is not used: a server swing of the main hand returns from the client as ARM_SWING and would type a phantom L | `SkillService.rightClickCue` |
| WP-06 | Held right button: (a) during a draw the 4-tick repeats are ignored for draws and combos; (b) for every class an R whose gap and previous gap are both one 4-tick repeat (170–230 ms) is dropped, so holding R types at most R-R, never R-R-R | `SkillService.heldRepeat` |
| WP-07 | An arrow shot while Чонын Нүд / Хэтийн Харваач is active trails soul fire + crit until it lands (≤ 3 s) and is tagged `suld_empowered`; the ×1.5 still applies on hit to every SÜLD target (unchanged consumption on hit) | `SkillService.empoweredTrail`, `CombatListener.empowered` |
| WP-08 | Arrow damage used `event.getDamage() / 6` on impact — the air's drag lowered long shots. The draw force is written to the arrow's PDC (`suld:draw_force`) at launch (SÜLD shot and any player bow shot) and used on hit; untagged arrows keep the old rule | `CombatListener.DRAW_FORCE/markDraw/onBowShot/arrowScale` |
| WP-09 | Validator: every model referenced anywhere in an item definition (range_dispatch entries/fallback, condition on_true/on_false) is checked with its suld: textures and suld: parent chain; bow.json's `using_item` branches must have an idle model and three distinct draw stages | `validate_resourcepack.py` `model_problems`, `draw_stage_problems`, `DRAW_ITEMS` |
| WP-10 | No separate specification in the prompt; covered by the animation matrix (§4) and the playtest sheet | — |

Deviations from the prompt, with reasons: full draw at 18 ticks (not 10/15) to match the pack's pulling_2 threshold;
no `swingMainHand()` on release or on R (phantom-L echo); `setCooldown(Material.BOW, 20)` not used (the item cooldown
overlay would also block the draw itself and greys every bow in the hotbar).

## 4. Animation test matrix (12 rows — run on the laptop, see LAPTOP_PLAYTEST_PLAN.md W-01…W-12)

| # | Class / weapon | Input | Expected first person | Expected third person (second client) | Server-side check |
|---|---|---|---|---|---|
| 1 | Мэргэн T1 bow | hold R 1.2 s, release | pulling_0 → 1 (0.65 s) → 2 (0.9 s), release ring upright | draw pose and stages | arrow speed 3.0, `suld:draw_force` = 1.0 |
| 2 | Мэргэн T1 | tap R | short pull, quick arrow | short pull | force 0.6 |
| 3 | Мэргэн | hold R 0.75 s | stages 0 → 1, release | same | force ≈ 0.83 (15/18) |
| 4 | Мэргэн | R twice within 1 s | second press: dry click + «Нум — x сек» | nothing | no second arrow |
| 5 | Мэргэн | L-R-L | L swing, R off-hand swing + tick, L → Чонын Нүд | swings | no draw started during the combo |
| 6 | Мэргэн | hold R, press L mid-draw | draw continues (client drops L) or ends and L counts | — | no stuck draw (`drawStart` cleared) |
| 7 | Мэргэн | hold R 6 s | full pull held; auto-release at 5 s | — | MAX_DRAW_TICKS release |
| 8 | Мэргэн | Чонын Нүд, then 3 shots | soul-fire trail on each | trail visible | ×1.5 on hit, count 3 → 0 |
| 9 | Мэргэн | 40-block shot at a SÜLD mob | — | — | damage same as a 5-block shot (±crit) |
| 10 | Баатар sword | R-L-R | off-hand swing on each R, sweep cue, slash decal on L | off-hand swings | Тэнгэрийн Цавчилт cast once |
| 11 | Бөө staff | hold R 2 s | at most R-R on the HUD, never a cast | — | no R-R-R spell |
| 12 | Дархан / Хүлэгчин | R-R-L | off-hand swings + cues | swings | slot 4 spell cast |
