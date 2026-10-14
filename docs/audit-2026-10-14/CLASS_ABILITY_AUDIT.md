# SÜLD — Class ability audit (audit 2026-10-14)

Sources: `suld-api/.../skill/Spell.java`, `skill/tree/Ultimate.java`, `skill/ComboTracker.java`,
`suld-plugin/.../skill/SkillService.java`, `SkillTreeService.java` (ultimate), `Ultimates.java`, `hud/HudPanel.java`.

## 1. Controls (all classes, class weapon in the main hand)

| Input | Action | Where |
|---|---|---|
| 3-click combo | spell slots 1–4. Melee classes start with **R**, Мэргэн with **L** (so plain attacks / shots never start a combo); 1 s window between clicks | `ComboTracker`, `Spell.comboFor` |
| Slot 1 / 2 / 3 / 4 | melee `R-L-R` / `R-R-R` / `R-L-L` / `R-R-L`; Мэргэн `L-R-L` / `L-L-L` / `L-R-R` / `L-L-R` | `Spell.comboFor` |
| **Shift + F** | the fifth spell (level 50) | `SkillService.onShiftF` |
| **F** (swap hands) | the build's ultimate (if learned), 60 resource, 45 s × (1 − CDR) | `SkillTreeService.onSwap/castUltimate` |
| **Shift + R** | opens the skill sky / skill map (never a combo click) | `SkillService.mapShortcut` |
| Мэргэн **hold R** (no combo under way) | draws the class bow: pulling_0 → 1 (0.65 s) → 2 (0.9 s); let go to shoot (new) | `SkillService.onBowUse/onBowRelease` |
| Мэргэн **tap R** | quick shot at 0.6 force | `SkillService.drawForce` |
| L while drawing | lets the draw go and becomes the combo's first L (if the client sends it — see §5) | `SkillService.click` |
| Sneak + Jump | dodge — listed by the execution prompt; not changed and not re-verified in this audit | — |

Unlock levels: slot 1 = 1, 2 = 10, 3 = 20, 4 = 35, fifth = 50 (`Spell.UNLOCK_LEVEL`). Base cooldowns: 1.5 / 4 / 5 /
10 / 18 s (`Spell.COOLDOWN_SECONDS`), reduced by the build's cooldown reduction (≤ 60 %) and by 10 % at rank III.
Ranks: II at 40, III at 55 (fifth: 55 / 60); power ×1 / ×1.15 / ×1.30.

## 2. Ability matrix — 25 spells

Cost = base resource cost (build modifiers apply, never below 1). Dmg = damage multiplier of the caster's ATK.

| Class (resource) | Slot / keys | Spell | Cost | Dmg | Target shape | Verified behaviour |
|---|---|---|---|---|---|---|
| Баатар (Хил) | 1 R-L-R | Тэнгэрийн Цавчилт | 25 | 1.6 | cone 4.5 m in front | hits every enemy in the cone, knockback |
| | 2 R-R-R | Дайны Хашгираан | 35 | 0 | 9 m taunt | mobs retarget the caster; Strength + Resistance 8 s |
| | 3 R-L-L | Довтлох Үсрэлт | 30 | 1.3 | leap, landing AoE | movement exemption (anticheat) |
| | 4 R-R-L | Хааны Хамгаалалт | 50 | 0 | self | Absorption II 10 s, Resistance II 6 s |
| | 5 Shift+F | Сүлдний Цохилт | 60 | 3.2 | 5.5 m around | knockback, Resistance II 5 s |
| Мэргэн (Анхаарал) | 1 L-R-L | Чонын Нүд | 20 | 0 | 24 m mark | Glowing 10 s; next 3 arrows ×1.5 for 12 s — trail now visible (WP-07) |
| | 2 L-L-L | Олон Сум | 35 | 0.9 | 5 arrows, 7° fan | tagged `suld_volley` |
| | 3 L-R-R | Ухрах Үсрэлт | 25 | 0 | backflip, 5 m slow | |
| | 4 L-L-R | Тэнгэрийн Сум | 50 | 3.0 | 30 m piercing beam | stops at blocks |
| | 5 Shift+F | Сумны Бороо | 60 | 0.8 | aimed 4.5 m, 5 waves | |
| Бөө (Сүнс) | 1 R-L-R | Сүнсний Залбирал | 25 | 0 | heal 8 m | heals allies |
| | 2 R-R-R | Онгоны Дуудлага | 30 | 2.0 | **single target**, 20 m forward cone | **fixed**: refused before cost/cooldown with no target (`CastResult.NO_TARGET`); echo no longer refunds |
| | 3 R-L-L | Хэнгэргийн Дуу | 40 | 0.8 | 3 pulses around | |
| | 4 R-R-L | Тэнгэрийн Хаалга | 70 | 4.0 | aimed gate, 4 m | burns enemies, heals allies |
| | 5 Shift+F | Хар Салхи | 70 | 0.9 | aimed 5.5 m, 5 hits | pulls, heals caster |
| Дархан (Дөл) | 1 R-L-R | Галын Давталт | 25 | 1.5 | 4 m around | ignites |
| | 2 R-R-R | Ган Бамбай | 30 | 0 | self | Resistance II 6 s |
| | 3 R-L-L | Хайлсан Төмөр | 35 | 0.6 | 3 forward sprays | |
| | 4 R-R-L | Дарханы Дөш | 60 | 4.0 | aimed anvil drop | |
| | 5 Shift+F | Галт Уул | 70 | 3.6 | aimed eruption | |
| Хүлэгчин (Хурд) | 1 R-L-R | Хурдан Довтолгоо | 20 | 1.3 | dash, hits on path | |
| | 2 R-R-R | Салхины Хурд | 25 | 0 | self | Speed III 6 s |
| | 3 R-L-L | Жадны Шидэлт | 35 | 2.2 | 18 m piercing line | |
| | 4 R-R-L | Хүлгийн Дайралт | 60 | 3.5 | spirit stampede | |
| | 5 Shift+F | Хийморийн Давхилт | 60 | 2.8 | double dash | Speed 6 s |

Audit rule checked on every spell: **cost is spent only for a cast that can act.** All spells except Онгоны Дуудлага
are self-buffs, area-around or aimed-at-a-point spells: they act wherever they land, so a cast with no enemy in reach is
a legitimate miss (the particles show where it went). Онгоны Дуудлага was the only single-target spell; it charged the
build's cost, started its cooldown and refunded only the *base* cost — with a cost-reduction build that was a resource
gain, and an echo (free re-run 12 ticks later) refunded a cost that was never paid. Both fixed.

## 3. The 15 ultimates (F; 60 resource, 45 s base cooldown)

| Class | Ultimate | Effect | Check |
|---|---|---|---|
| Баатар | Баатрын Их Уур | Strength II, Resistance I, Regeneration II 12 s | self |
| | Бүхний Нурал | 8 m ×6 ATK, launch | area-around |
| | Үхэшгүй Эр | Resistance IV 8 s, 10 ❤ shield | self |
| Мэргэн | Сумын Борооны Цаг | aimed arrow rain 5 s | aimed |
| | Хэтийн Харваач | every arrow ×1.5 10 s, Speed II (trail shown) | self |
| | Үхлийн Тэмдэг | marks ≤ 30 enemies in 24 m: +40 % damage taken 10 s | area-around |
| Бөө | Өвгөдийн Залбирал | full heal 14 m, cleanse | allies |
| | Тэнгэрийн Шийтгэл | lightning on up to 8 enemies in 20 m | **fixed**: refused unpaid with no enemy in 20 m (`Ultimates.hasTarget`); it spent 60 + the cooldown silently |
| | Сүнсний Хөл | Speed III, Resistance II, Regeneration III 8 s | self |
| Дархан | Хайлсан Далай | 7 m molten pulses 5 s | area-around |
| | Бамбайн Хэрэм | Resistance III, 8 ❤, 30 % thorns 8 s | self |
| | Мянган Алх | 10 anvils on nearby enemies | area |
| Хүлэгчин | Шуурга Давхилт | three dashes | movement |
| | Салхины Гэгээн | Speed IV, Strength I, jump 10 s | self |
| | Мянган Морь | spirit horses in 8 directions | area |

## 4. HUD and feedback (Phase 5)

| Requirement | Status | Evidence |
|---|---|---|
| Combo progress `[L - R - _]` | Verified | `HudPanel.line` (pack HUD) and `plainBar` (no pack) |
| Class resource name and bar | Verified | `HudComposer.resourceColor`, `plainBar` |
| Spell cooldowns | Verified | `HudPanel` slots: `skills.cooldownLeft`, locked slots show their unlock level |
| Ultimate readiness (F) | Verified | `plainBar`: `F ✦ бэлэн` / `F ✦ Nс`; pack HUD slot 0 |
| Refused cast: level lock / cooldown / no resource | Verified | action-bar toast + `BLOCK_NOTE_BLOCK_BASS` (`castDirect0`) |
| Refused cast: soul | Fixed | bass cue added (spell and ultimate) |
| Refused cast: no target | Fixed | Онгоны Дуудлага, Тэнгэрийн Шийтгэл |
| Bow not ready | Fixed | dry string `BLOCK_DISPENSER_FAIL` 0.35 / 1.6 + «Нум — 0.4 сек», at most every 0.4 s |
| R step feedback | Fixed | off-hand swing + class cue (Бөө chime, Мэргэн string, melee sweep) |
| Non-spammy | Verified | toasts replace each other (1.5 s); the anti-spam gap (400 ms) refuses silently by design |

## 5. Known limits

* While a bow is drawn the vanilla client drops attack-key presses, so an L pressed *during* a draw usually never
  reaches the server; the archer's combos therefore start after the release. The server handles the L if it arrives.
* An off-hand swing is used for the R cue because a server-sent main-hand swing comes back from the client as an
  ARM_SWING packet and would type a phantom L.
