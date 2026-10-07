# SÜLD armour master design

The one-page view of the class armour programme. Details:

* `docs/CLASS_ARMOR_SYSTEM.md`: items and lifecycle.
* `docs/ARMOR_PROGRESSION.md`: armour level, tier, mastery, with simulated numbers.
* `docs/ACTIVE_PLAYTIME_SPEC.md`: the activity tracker.
* `docs/CLASS_GEAR_SYSTEM.md`: soulbinding and protection.
* `docs/ARMOR_PROGRESSION_VISUAL_SPEC.md` and `docs/ARMOR_ASSET_PIPELINE.md`: the look and how it is produced.

**Status: SPEC.** No class armour exists in the game.

## The promise to the player

> "This armour is MY class, and I have developed it through my journey."

* **Identity**: one set per class, never dropped and never replaced by loot. The visual silhouette changes at every
  tier, not just the colour.
* **Development**:
  * the armour level grows only with real play;
  * the tier is earned with dungeon clears and the materials of their regions;
  * mastery grows with class play.
* **Pressure**: death never takes the armour. It leaves a wound (−5 % effective stats per death, at most −15 %) that
  heals with active play.

## Shape of the programme

| Axis | Count | Gate | Simulated arrival (hardcore / active / casual) |
|---|---|---|---|
| T1 Эхлэл | at class pick | — | day 1 |
| T2 Сайжруулсан | AL 12 + Говийн Булш | first dungeons | day 2–3 / 3 / 6 |
| T3 Элчин | AL 24 + Мөсөн Оргил | the Алтай band | day 4 / ~10 / ~30 |
| T4 Хааны | AL 36 + Хар Хотын Балгас | Act II | ~day 10 / ~20 / ~55 |
| T5 Тэнгэрлэг | AL 48 + Бурхан Халдуны Агуй | late game | ~day 20 / ~30 / ~85 |
| T6 Дээдэс | AL 60 + Тэнгэрийн Ордон + Ascension III | endgame | days 90–180 / ~180 / beyond 180 |

The day numbers are p50 readings of the armour table in `docs/PROGRESSION_SIMULATION.md`.

## Production order (from the owner's directives, unchanged)

| Phase | Work | Gate to the next |
|---|---|---|
| 0 | audit (done: `audit/armor-system-status.json`) | — |
| 1 | maths (this spec + the simulation) | **owner approval** |
| 2 | ActivePlaytime tracker | tests + bot run |
| 3 | Baatar T1→T6 visual vertical slice | client QA of T1 and T3 |
| 4 | item / equipment / SkillBuild integration | regression suite |
| 5 | Gemini → Meshy → Blender → Blockbench → pack for the slice | budget + validation |
| 6 | real-client QA (fit, clipping, first/third person, tooltip) | **CLIENT_TESTED** |
| 7–10 | Мэргэн, Бөө, Дархан, Хүлэгчин (mounted look QA) | per class |
| 11 | armour performance audit (pack size, entity attachments, frame time) | — |

## Non-goals

* No generic armour plugin.
* No recolours sold as new tiers.
* No paid armour and no paid stats. Credits buy cosmetics only.
* No mass generation of all 30 tier looks before the Baatar slice passes.
