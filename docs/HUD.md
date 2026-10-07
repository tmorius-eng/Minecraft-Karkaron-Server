# SÜLD HUD

The in-game HUD: an Ares-HUD-*style layout* (two rows of long bars either side of a central level diamond, directly
above the hotbar; a target frame at the top) drawn entirely in SÜLD's own Mongolian art. No Ares HUD asset, code or
texture is used; its screenshots were a layout reference only. Everything is server-side (Paper plugin + the SÜLD
resource pack); no client mod.

```
   [◆ buff 12][buff 4]                                  [I][3][III][🔒][✦]      ← status icons · spell slots
  ╔♥ 184/200 ═══════════════╗◇╔═══════════════ ⚡ 75/140 ╗                       ← health │ class resource
  ╚▰ 18/20 ═════════════════╝12╚════════════════ ◆ 57% ══╝                       ← food/mount/air │ EXP
  ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁ hotbar (re-skinned) ▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁▁
                 БААТАР  ₮ 12,340  ◆ 3  ⚔ 57    ← text line (status / notice / combo), at the action-bar height
  top of screen:  LV 5  ХАСАР — АГУЙН ЭЗЭН   ╔══════ 1500/3000 ══════╗   ← target frame (boss bar)
```

## What it shows (all live game state, nothing hard-coded)

| Element | Source |
|---|---|
| Health bar, numbers, absorption (gold), damage chip (pale, 0.7 s), low-health pulse (<25 %) | player health / max-health attribute / absorption |
| Class resource bar in the class's colour (Баатар orange-red, Мэргэн green, Бөө violet, Дархан amber, Хүлэгчин blue) | `SkillService.pool` |
| Second-row left: food, or the mount's health while riding, or breath under water | food level / horse health / remaining air |
| EXP into the level (turquoise), "MAX" at the cap | `ProgressionEngine.progressFraction` |
| Level on the diamond | profile progression |
| Spell slots I–IV + ultimate: ready (class colour), cooldown countdown, locked (level), not enough resource (red) | `SkillService.cooldownLeft`, `Spell.UNLOCK_LEVEL`, `SkillService.costFor`, `SkillTreeService.ultimateOf/ultimateCooldownSeconds` |
| Status icons (up to 6, most important first): soul, broken gear, burning, poison, wither, slowness, weakness, hunger, empowered arrows (×n), strength, speed, regeneration, absorption, resistance, haste, jump, night vision, relic borne, safe zone | death state, equipment bonus, potion effects, `CombatListener.EMPOWERED_*`, relic record, city zone |
| Text line: combo being typed (`R - L - _`) › notice › status: class, coins ₮, unspent skill points ◆, gear score ⚔ | `SkillService.comboInProgress`, `HudService.toast`, profile, skill tree, `Equipment.gearScore` |
| Target frame: name, level, tier frame (bronze / silver elite / gold boss), health, damage chip | crosshair entity (24 blocks, held 3 s) or the last thing you hit / that hit you (12 s); SÜLD mob definition (PDC `mob_id`) |
| Quest progress | the existing quest compass bar (`QuestTracker`) and sidebar — reused, not duplicated |

Shift + right click while holding the class weapon opens the skill-tree map (not a combo click, so it never
interferes with spells).

## Architecture

* **One HUD manager: `HudService`.** It already owned the sidebar, TAB list and name tags; it now also owns the action
  bar and the target frame (through its package-private `HudPanel`). `SkillService` no longer writes the action bar;
  every other system that used to (`WorldEventService`, `RelicService`, `OnboardingService`, `TaskService`,
  `CityProtectionListener`, `HorseService`, `SkillMapMenu`) calls `HudService.toast(...)`, which shows the message in
  the HUD's text line instead of replacing the panel. (`EquipmentService`, part of the completed item engine, was left
  unchanged: its rare "item inactive" action bar replaces the panel for at most 1 s.)
* **Pure composition:** `HudState` (data) → `HudComposer` (glyph layout, no Bukkit) → `HudCanvas` (a pen over the
  `suld:hud` font). The line is always exactly 256 px wide (pen −128 → +128), so the client's centring puts canvas x=0
  at the screen centre to the pixel, whatever is drawn.
* **Refresh:** event-driven (damage, healing, food, casts, combo clicks, notices) flushed on the next tick, plus a
  4-tick pass for countdowns and the crosshair; a line is sent only when it changed, or every second so the action bar
  never fades.
* **Clients without the pack** (status not `SUCCESSFULLY_LOADED`) get the previous plain-text bar; notices go to them
  as a plain action bar.

## Resource pack

`tools/pack/gen_hud.py` builds font `suld:hud` (76 providers): 2× bronze bar frames with gold khee end caps, gradient
bar fills split into power-of-two pieces (any width is exact; columns are identical so pieces tile seamlessly), the
level diamond (Gemini art `assets/art/source/hud_diamond.png`, prompt in `tools/art/hud_prompts.json`), a cartouche,
spell slots, numerals, 20 status icons, 3×5 digit sets and the 5×7 Mongolian pixel font per row, and the space glyphs.
Each element's row is its glyph ascent (`ascent = Y − 65` for an element whose top is Y px above the screen bottom).
It also writes transparent vanilla sprites for what the panel replaces (hearts incl. hardcore/absorbing/vehicle,
food, armour, air, XP bar), the re-skinned hotbar/selection/off-hand slot, transparent white boss-bar sprites (the
target frame draws its own bar; no other system uses white bars), and the generated `HudGlyphs.java` with exact
advances (computed like the client: trimmed width × scale, rounded, + 1). `tools/pack/hud_render.py` renders a
composed line offline with the pack's own font for review.

## Limits (server-side HUD)

* The hotbar cannot move (Ares' left/vertical hotbar needs a client mod); the panel sits above it.
* The vanilla jump/locator strip just above the hotbar is left free; the cartouche covers its centre 14 px.
* The vanilla green level number (vanilla XP is unused by SÜLD) is hidden under the diamond and cartouche.
* GUI scale and screen size are the player's; at very small widths the panel (184 px) is narrower than the screen
  anyway (hotbar 182 px).
