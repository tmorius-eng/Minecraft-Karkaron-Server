# SÜLD — Combat and feedback audit (audit 2026-10-14)

Sources: `CombatListener`, `CombatFeel`, `api/combat/CombatCalculator`, `api/balance/CombatRules`, `SkillService`,
`SkillTreeService`, `HudPanel`, `ConsumableService`.

## 1. Damage formula (player → SÜLD mob), verified by reading `CombatListener.onHit0`

```
attack  = (class base + 0.75·(level−1) + gear flat damage) × (1 + Ascension power) × skill-tree attack multiplier
scale   = melee: 0.2 + 0.8·charge²  (×0.3 sweep, ×0.3 punching with a bow/crossbow)
          arrow: draw force from the arrow's PDC (new, WP-08), else vanilla damage / 6, clamped 0.25–1.4
                 × volley factor (Олон Сум) or ×1.4 single-arrow keystone; ×1.5 while empowered
scale  ×= CombatRules.gapDealt(mob level − player level)      (−4 % per level above, floor 40 %)
scale  ×= 1 + weapon mastery (≤ 2.5 %)
damage  = CombatCalculator(attack × scale, crit 5 % + build, crit × build) ; Ascension VI lightning 2 %
```

Mob → player: `def.scaledAttack × MobScaling.hitScale × CombatRules.gapTaken × newbieTaken` (NORMAL), then at HIGH:
Арц Хүж ward −8 % against spirits (new) and armour mitigation `a / (a + 10 + 2.5·attacker level)` ≤ 75 %.

Spell damage goes through `CombatListener.asSpell` (a depth counter restored in `finally`), so the hit formula never
overwrites a spell's amount.

## 2. Findings and fixes in combat feel

| Area | Finding | Status |
|---|---|---|
| Long-range arrows | lost damage with distance (drag lowered `getDamage()`) | Fixed (WP-08) |
| Empowered arrows | ×1.5 applied, nothing showed which arrow carried it | Fixed (WP-07 trail) |
| Release ring | flat ring at eye level, seen edge-on | Fixed (WP-04) |
| R clicks | no animation or sound for melee / staff R | Fixed (WP-05) |
| Held R | auto-repeat typed R-R-R spells | Fixed (WP-06) |
| Bow cooldown | silent failure | Fixed (WP-03) |
| Single-target spell with no target | charged + cooldown + partial refund; echo refunded unpaid cost | Fixed (`CastResult.NO_TARGET`) |
| Targeted ultimate with no enemy | 60 resource + 45 s spent, no visual | Fixed (`Ultimates.hasTarget`) |
| Hit decals / damage numbers | class-coloured decals on swings with full charge (≥ 0.9), 250 ms rate limit; crit numbers via `CRIT_HITS` | Verified (read) |
| Sound readability | casts, refusals (bass), crits (`ENTITY_PLAYER_ATTACK_CRIT`), draw/release, consumables each have a distinct cue | Verified / added |

## 3. Final Questions (Part 12)

The execution prompt asks for "answers to all 20 Final Questions from Part 12", but **Part 12 is not in
`CLAUDE_MASTER_EXECUTION_PROMPT.md` nor anywhere in the repository**, and the web-session reports that may have held it
are absent. Rather than invent the questions, the 20 questions below are the ones this audit's scope directly raises;
each answer cites the evidence. If the owner supplies the original Part 12, it should replace this list.

| # | Question | Answer |
|---|---|---|
| 1 | Does the Мэргэн bow now visibly draw? | In code, yes: the server starts the use and the client mirrors it; needs the laptop check W-01 (not run here). |
| 2 | Do L-R-L combos still work with a drawable bow? | Yes: a combo under way turns R into a combo click (no draw); a draw's R never enters the combo. An L *during* a draw is usually dropped by the client. |
| 3 | Is the archer's DPS unchanged? | Full draw 0.9 s inside the 1 s shot pace → one full-force shot per second as before; taps are 0.6. |
| 4 | Can a stuck draw happen? | No: release event, a no-active-use check, a repeat-gap check, item switch / death / logout cancel, and a 5 s cap. |
| 5 | Do long shots lose damage? | Not any more for player bow shots (launch force on the arrow). |
| 6 | Does holding R cast spells? | No: at most R-R; the third 4-tick repeat is dropped. |
| 7 | Is every combo step visible? | L: arm swing + decal; R: off-hand swing + cue; HUD shows `L - R - _`. |
| 8 | Can a spell cost resource and do nothing? | Only area/aimed spells that miss (legitimate). The single-target spell and the targeted ultimate are refused unpaid. |
| 9 | Are boss drops still a Legendary farm? | No: BOSS band legendary+ ~12 % (was ~100 %). |
| 10 | Can every regional weapon and the Талын Анчин set be obtained? | Yes (named drops); `ItemObtainabilityTest`. |
| 11 | Do ancient/mythic rewards stay tradable? | No: ON_PICKUP binds on every delivery path and on equip. |
| 12 | Does the class weapon grow with level? | Yes, every level (~1 dmg/level), with fixed rolls per tier. |
| 13 | Can a low-level legendary produce Sky Stones? | No: below item level 30 it salvages as epic. |
| 14 | Do the consumables exist and work? | Four, with cooldowns, combat rules and anti-dupe order; `ConsumablesTest`. |
| 15 | Do party members progress quests together? | Yes: story kill objectives and daily hunting tasks within 48 blocks. |
| 16 | Does the quest tracker lie about a closed gate? | No: it shows the gate's exact refusal. |
| 17 | Can a removed chapter soft-lock a player? | No: aliases or level-based placement; chapter skill points kept. |
| 18 | Are persistence orders crash-safe? | Reroll, accessory equip/unequip, craft-order delivery and consumables now follow PlayerDataSaves. |
| 19 | Were 24be54a / 411842d features preserved? | Yes: no file of the store, pass, professions, armory API, ascension, heroic or atlas changed; 411842d fixes untouched. |
| 20 | Is it ready to push? | **No** — nothing could be compiled or tested in this session; see EXECUTION_CHECKPOINT.md. |
