# Хасар, Агуйн Эзэн: boss design and rig

Status: **rig v1, 2026-10-07. FUNCTIONAL_BUT_INCOMPLETE, MANUAL_QA_REQUIRED.** The model, textures and clips
exist and load in the Java `RigLoader` with no issues. Nobody has seen them in a real 1.21.11 client yet.
Specs: [BOSS_VISUAL_SPEC §5](../BOSS_VISUAL_SPEC.md#5-хасар--full-specification) (size, bones, budgets, clips) and
[MODEL_RENDERER](../MODEL_RENDERER.md) (the asset contract). Where this document differs from §5, the owner's
2026-10-07 directive wins. The differences are listed in §7.

## 1. Design brief

Хасар is an **original** ancient cave wolf-beast. It is not a werewolf, it is never bipedal, and it is not a
recoloured vanilla wolf. Picture an enormous old male from a steppe wolf line, as big as a ravager, which has lived
in a limestone cave for longer than anyone remembers. Its build is that of a predator that has stopped running far
and now ambushes:

* **Body.** A long, low body. The forequarters are very heavy: deep chest, massive shoulders and a thick
  black hackle mane from the crown to the middle of the back. The hindquarters are slimmer and the waist is tucked.
* **Head.** Carried low, below the shoulder line. A long wolf muzzle on a broad skull, a heavy brow over
  deep-set eyes, and pale cheeks and muzzle (the grizzled mask that says "wolf" at a distance).
* **Scars.** Natural and asymmetric. The left ear is torn, with its tip gone and a notch bitten out. Pale cuts
  cross the muzzle. Three healed claw rakes run across the left flank, there is a bald seam on the left haunch, and
  a scar runs from the torn ear towards the eye.
* **Adornments.** Only two, and each has a reason:
  * A **broken bone collar.** Long ago, someone tried to bind it. A cracked rawhide cord with yellowed bone plates
    is all that is left. Half of the plates are gone, and the right side ends in a frayed stub with one bone still
    dangling.
  * **Cave-stone shards.** Over centuries the den's dripping mineral water has calcified into the matted hackles
    and into old wounds on the left shoulder and the right ribs. The stone grew into the creature: it is not
    armour and not a weapon.
* **The supernatural is restrained.** The eyes are dim ember, 2 px, and never glow. In the last phase they
  brighten (the `head_rage` variant) and frost breath appears as VFX only. Nothing else is magical.

Tone: patient, old, heavy and dangerous. The menace comes from silhouette and value, not from spikes and skulls
(ASSET_STYLE_GUIDE §1).

## 2. Silhouette

| Measure | Rig v1 (rest pose, measured by `export_rig.py`) | Spec |
|---|---|---|
| Nose to tail tip | **3.52** blocks (z −1.84 … +1.68) | 3.4 |
| Withers (top of the shoulders) | **1.97** | 2.0 |
| Top of the hackles / stone shards | 2.12 / 2.25 | — |
| Highest point while roaring | 2.48 (`roar` @ 20: raised hackles ×1.2 and the stone shards, not the head) | head top 2.3 |
| Width (shoulders + mane drape) | **1.65** | 1.6 |
| Chest bottom / shaggy bib | 0.75 / 0.64 | — |
| Head out of the RAVAGER box (±0.975) | nose at 1.68: 0.7 block, so it gets an `Interaction` part (1.0 × 0.9) | ≤ 0.6 |

Proportions come from the single Meshy preview (§8). On that mesh the front legs sit at 0.24–0.35 of the length
from the nose, the hind legs at 0.70–0.82, and the chest bottom at 0.41 of the withers height. Хасар then had its
head lowered, its forequarters made heavier and its legs shortened slightly (chest bottom at 0.38 of the withers).

Readability checks, all in `assets/previews/boss/`:
* **Greyscale side silhouette at 64 px** (`khasar_silhouette.png`). It shows a humped, front-heavy canid with a
  low head, pointed ears, a long muzzle, a long low tail and the dog-leg hind legs. The tail, ears and muzzle
  separate it from a bear; the low head and the mane separate it from a vanilla wolf.
* **Values.** The black mane and saddle sit over soot-grey flanks. The cheeks, bib, chin and underbelly are pale.
  One warm accent (the eyes, plus the red mouth when it opens) and two light materials (bone, stone).

## 3. Palette

All texels are swatches from these ramps (`tools/model/khasar_paint.py`). There are no gradients and no dithering
noise.

| Material | Ramp (dark → light) | Where |
|---|---|---|
| Fur | `#1B1A1F` `#2B2A2E` `#3A393F` `#4A4A50` `#5E5D63` `#6E6D73` `#807F85` `#97969A` | body, legs, head (spec fur `#2B2A2E/#4A4A50/#77767C` sits inside this ramp) |
| Mane / tail tip | `#111014` `#1B1A1F` `#26252A` `#34333A` (+ `#5E5D63` grizzled tips) | hackles, crown, tail tip |
| Bib / cheeks / chin | `#3A393F` … `#8C857A` `#A0978A` | chest bib, throat, cheek ruff, muzzle sides |
| Underbelly | `#4E463C` `#6B5F50` `#8A7B68` `#A39279` | belly (spec `#8A7B68`) |
| Cave stone | `#2A2C30` `#45484D` `#5F6368` `#7E8287` `#A3A7AB` | shards, crust |
| Bone | `#5E5646` `#857B65` `#ACA187` `#D0C6AC` | collar plates (style-guide bone `#D8CFB8`, aged) |
| Rawhide cord | `#2E1E14` `#4A2E1E` `#6E4A2F` | collar cord |
| Claws / pads / nose | `#121115` … `#5E5650`; `#141317` … `#302A2B`; `#0E0D10` … `#4A4A50` | paws, nose |
| Eyes (dim ember) | `#120B0A` `#5A2414` `#9A4420` `#C8642A` `#E08A44` | phase 1 |
| Eyes (`head_rage`) | `#1A0A08` `#8A2A12` `#D2501E` `#F58A2A` `#FFC46A` | phases 2–3 |
| Gums / tongue / teeth | `#3A0E10` … `#94302C`; `#5A1418` … `#B8402F`; `#6E6552` … `#E8E4DA` | mouth (spec gums `#7A1E1E`) |
| Scars | `#5A5254` `#7A7272` `#958C88` `#AAA29A` | healed cuts |

Pixels brighter than `#E6E6E6` exist only as tooth tips. The light comes from the top left: fur strands have a lit
left edge, and stone and bone are bevelled.

## 4. Rig (v1)

Source: `tools/model/khasar.py`, which holds Python data in px relative to each bone pivot.
`tools/model/export_rig.py khasar` writes everything. **25 displays, 26 bones (root has no model), 132 cuboids**
(budget ≤ 30 / ≤ 260), triangle upper bound 1,584. Every bone has scale 1.0, so 16 px is one block, and every
element stays inside −16…32.

| Bone | Parent | Cuboids | Atlas | Notes |
|---|---|---|---|---|
| `root` | — | 0 | — | follows the host (yaw, position); death and spawn move it |
| `body_front` | root | 7 | body | chest barrel, withers, brisket, shaggy bib, fore-chest, rib taper, spine ridge (merged); flank claw-scars |
| `body_rear` | body_front | 6 | body | loin, belly tuck, croup, buttocks, tail root, ridge |
| `neck` | body_front | 12 | body | neck, throat ruff, **broken bone collar** (cord + 5 plates + dangling bone); rest pitch 40° down |
| `head` | neck | 14 | head | skull, brow, cheek ruffs, two-step muzzle, nose, lip, canines, ember eyes, crown; scarred muzzle |
| `jaw` | head | 5 | head | lower jaw, chin fur, tongue, lower canines (gum + teeth on the top face) |
| `ear_l` | head | 2 | head | **torn** (tip gone, notch) |
| `ear_r` | head | 3 | head | whole, pointed |
| `mane_1` | body_front | 12 | body | hackles (stepped crest, drapes) + 3 stone shards (22.5° element rotations) |
| `mane_2` | neck | 3 | body | nape hackles |
| `shards_flank` | body_front | 6 | body | cave-stone crust in old wounds (left shoulder, right ribs); falls off in phase 3 |
| `leg_f{l,r}_upper` | body_front | 4 + 4 | body | shoulder mass, forearm, elbow, feathering |
| `leg_f{l,r}_lower` | upper | 3 + 3 | body | wrist, pastern, dew fringe |
| `leg_f{l,r}_paw` | lower | 10 + 10 | body | paw, 4 toes, **4 long claws**, heel pad |
| `leg_h{l,r}_upper` | body_rear | 3 + 3 | body | thigh (rest −20°), fringe; left haunch scar |
| `leg_h{l,r}_lower` | upper | 3 + 3 | body | gaskin (rest +55°), hock point, metatarsus (−45° element rotation) |
| `leg_h{l,r}_paw` | lower | 5 + 5 | body | paw (rest −35°, level), 4 toes |
| `tail_1` … `tail_3` | body_rear / chain | 2 + 2 + 2 | body | droop −50°, −12°, −8°; black tip |

* **Variant model** `head_rage`: the same geometry as `head`, with its eye faces on brighter ember texels. It
  ships as `suld:entity/khasar/head_rage` (an item-model swap, not a display) and is listed in `rig.json` under
  `variants`.
* **Atlases.** `khasar_body.png` and `khasar_head.png` are 128 × 128 each, 7,342 B together (budget ≤ 40 KB). The
  body is at 1 texel per px (16 per block) and packs with no padding, like a vanilla entity texture; it is 61 %
  used. The head is at 2 texels per px (32 per block) with a 1-texel bleed; it is 40 % used. The right legs, the
  right cheek ruff, the right canines and the right eye mirror the left ones and share their texels.
* **Pack bytes.** 54 files (25 + 1 models, 26 item definitions, 2 PNGs) plus 2 atlas-source files come to
  **75,472 B** raw and 34.4 KB zipped (budget ≤ 90 KB).
* **Pivots.** `rig.json` `pivot` is the running sum of the bones' offsets. In the Blockbench sense it is the
  unrotated pivot, exactly the `pivot − parentPivot` of MODEL_RENDERER §2. When a parent has a rest rotation (the
  neck, the hind legs, the tail), the child really sits at `parentPivot + R(parent rest)·(pivot − parentPivot)`.
  The Java `Sampler` and `tools/model/preview_rig.py` both compute it that way.

## 5. History and fiction classification

Labels: **VERIFIED HISTORY** (a cited source, or general knowledge marked as such); **INSPIRED** (an original
design loosely based on a verified element); **ORIGINAL FICTION** (invented for SÜLD). These map to the research
set's HISTORICALLY VERIFIED / INSPIRED / ORIGINAL SULD FICTION
([docs/research/history/README.md](../research/history/README.md)).

| Element | Label | Note |
|---|---|---|
| **Хасар, Агуйн Эзэн, the boss** (its being, its age, its den, its powers) | **ORIGINAL FICTION** | An invented creature |
| The name *Хасар* | VERIFIED HISTORY: the name of a historical person, Qasar, a brother of Temüjin (general knowledge; **not** in SÜLD's research set) | **The boss is not that person**, not his animal, not a symbol of him and not connected to him in art or lore. The name may evoke a Mongolian identity, and that is all. SÜLD makes no claim about what the name means |
| *Агуйн Эзэн* ("master of the cave") | ORIGINAL FICTION | Эзэн in its plain sense of "master". SÜLD makes no claim about folk-religious spirits of places |
| Steppe wolf anatomy (long legs, deep chest, pale cheek mask, black tail tip, a pack that answers a howl) | INSPIRED | Grey wolves of the Mongolian steppe (general natural-history knowledge; not in the research set), exaggerated into a cave giant. The summoning howl is a game mechanic |
| Scars, torn ear, broken collar of **bone** plates on a rawhide cord | INSPIRED | Bone is a verified worked material of the Karakorum workshops (MATERIAL_CULTURE, [S4]). A collar on a wolf-beast is fiction and claims no historical practice of collaring wolves |
| Cave stone calcified into the hackles and old wounds | ORIGINAL FICTION | |
| Dim ember eyes, brighter in phase 2+; frost breath in the last phase | ORIGINAL FICTION | |
| Хэрлэн, as the home of its cave wolves (BOSS_VISUAL_SPEC §5) | The river is real (general knowledge); the cave and the wolves are ORIGINAL FICTION | No real cave is depicted |
| The wolf of the *Secret History*'s origin story | **Not used** | Хасар carries no ancestral, totemic or dynastic symbolism |
| Soyombo, any tamga, seals, script, banners | **Not used** | Nothing on the model or its textures is an emblem or text |

## 6. Clips

Authored in `tools/model/khasar_clips.py` and sampled to `clips.json` (linear keys at 1–4 ticks). The names and
events are the ones `KhasarBrain` and `ModelInstance` use.

| Clip | Ticks | Loop | Events | Played by | Content |
|---|---|---|---|---|---|
| `idle` | 80 | yes | — | base layer (speed ≤ 0.03) | deep breathing, slow pant (4×), head look, torn ear flicks at 50, tail sway, weight shifts |
| `walk` | 20 | yes | — | base (speed > 0.03) | **trot, diagonal pairs** (LF+RH / RF+LH); paws counter-rotated flat in stance, curl in swing; body bob and roll, head and tail secondary motion |
| `run` | 12 | yes | — | base (speed > 0.2) | bounding gallop (fore pair, then hind pair), spine flex, mane lags, tail streams, ears flat |
| `frenzy_idle` | 40 | yes | — | needs code (phase 3 base) | low stance, mouth open, fast breathing, hackles ×1.25, ears flat, head tremble |
| `sleep` | 80 | yes | — | needs code (den before the fight) | sphinx pose, head on the forepaws, tail curled, slow breath, ear twitch |
| `bite` | 12 | no | `bite_hit` @ 6 | `KhasarBrain.bite` | wind-up 0–5 (head back, jaw 34°), snap 5–7 (lunge 0.24), recover 7–12 |
| `pounce` | 34 | no | `pounce_land` @ 24 | `KhasarBrain.pounce` | crouch 0–6 (telegraph; the host leaps at 6), stretch, forepaws reach and jaw opens 14–21, impact absorb 24–29 |
| `roar` | 40 | no | `roar_wave` @ 14 | `KhasarBrain.roar` (phase 2+) | chest up, head up, jaw 38° with tremble, hackles ×1.2, ears back |
| `howl` | 44 | no | `howl` @ 16 | `KhasarBrain.howl` (phase 3) | haunches drop, muzzle to the roof, rounded jaw with vibrato |
| `hurt` | 4 | no | — | optional (the tint flash is in code) | flinch |
| `phase_change` | 30 | no | `phase_wave` @ 14 | `KhasarBrain.onPhase` | shake 0–10 (stone flakes jolt), roar 14–24, hackles to ×1.3 |
| `enrage` | 30 | no | `enrage_pulse` @ 12 | needs code (enraged) | howl-roar and shudder, hackles ×1.3 |
| `spawn` | 40 | no | `spawn_burst` @ 2 | needs code (arrival) | climbs out of a fissure from 1.5 below, alternating forepaw claws, shakes |
| `death` | 50 | no | `death_fall` @ 24, `stone_fall` @ 32 | `ModelInstance.die` | staggers, forelegs buckle, rolls onto its right side, stone shards drop out, sinks 0.3 |

Scale channels are only put on leaf bones (mane, shards), because a parent's clip scale would also scale its
children's displays (`Xf.then`). The contact sheet of every clip is
`tools/model/preview_rig.py khasar --out <dir> --sheet`.

## 7. Deviations from BOSS_VISUAL_SPEC §5 and what the renderer still needs

| Topic | §5 says | Rig v1 | Why / to do |
|---|---|---|---|
| Adornments | rusted lamellar plates, 3 broken spear shafts | broken bone collar, cave-stone shards | Owner directive: adornments only where justified |
| `plates_flank` | hidden in phase 3 | **`shards_flank`** | Same role. Hiding it needs code (scale 0 or despawn the display from phase 3) |
| Eyes | pale amber `#E0B24A` | dim ember `#C8642A`; `head_rage` brighter | Owner directive |
| Clip names | `attack_bite`, `telegraph_pounce`, `phase_2`, `phase_3`, `attack_swipe`, `stagger` | `bite`, `pounce`, `phase_change`, plus `howl`, `frenzy_idle`, `sleep` | Matches `KhasarBrain` and the owner's 14-clip list. Swipe and stagger are not authored |
| Pounce length | 24 | 34, land at 24 | The host's leap starts at tick 6 and lands about 18 ticks later |
| Size | 3.4 × 2.0 × 1.6 | 3.52 × 1.97 (withers) × 1.65 | Within about 4 % |
| Gemini | 4 drafts + 2 refs | 0 | The API answered 403 (no credential this session); not retried |
| Meshy | preview + refine, ≤ 60 credits | 1 preview, 20 credits | Proportion reference only |

The renderer (Java; nothing here was changed) needs the following before the phase visuals show:
1. **Persistent phase state.** Phase 2+: `mane_1` and `mane_2` display scale ×1.35, and the `head` item model swapped to
   `head_rage`. Phase 3: `shards_flank` hidden. The base clip becomes `frenzy_idle`. Clips end, so a clip
   cannot hold these states.
2. Play `spawn` on arrival, `sleep` before the fight, `enrage` when enraged and optionally `hurt`. Use the extra
   events (`phase_wave`, `enrage_pulse`, `spawn_burst`, `death_fall`, `stone_fall`) for the §5.4 VFX.

## 8. Pipeline record

| Step | Tool | Result |
|---|---|---|
| Concept | `tools/art/gemini_image.py --prompts tools/art/khasar_prompts.json --out assets/art/concepts/boss` | 2 prompts; **HTTP 403** "unregistered callers" (no Gemini credential reached the API). Not retried; no images |
| 3-D reference | `tools/meshy/meshy_client.py text3d` (preview, realistic), one task `01a1166a-4c56-7173-b025-56bfa0ff1317` | 31,106 tris, an ordinary standing wolf (usable for proportions). Balance 1,050 → **1,030 (20 credits)**. GLB in `assets/sources/` (git-ignored), never shipped |
| Measure | `blender -b -P tools/blender/ortho_views.py -- --input <glb> --out-dir <dir> --length 3.4` | bbox 1.07 × 3.40 × 2.20 blocks; a height profile in 17 bins; front, side and top views with a 1-block grid (`assets/previews/boss/khasar_meshy_ref_*.png`) |
| Rig, paint, clips | `tools/model/khasar.py`, `khasar_paint.py`, `khasar_clips.py` | authored |
| Export | `python3 tools/model/export_rig.py khasar` | pack models, item definitions, PNGs, atlas sources, `rig.json`, `clips.json`, `assets/models/blockbench/boss/khasar.bbmodel`, `assets/reports/boss.khasar.json` |
| Preview / verify | `python3 tools/model/preview_rig.py khasar --out <dir> [--views …] [--sheet] [--silhouette]` | software rasterizer over the **shipped files**. The Java `RigLoader` loads them with 0 issues, and 20 Java `Sampler` display transforms equal `rigmath.py` to 5 decimals |

## 9. Client QA checklist (MANUAL_QA_REQUIRED)

1. **Facing.** Vanilla `ItemDisplayRenderer` turns the item 180° about Y. The exporter pre-turns every bone
   model to cancel it, and the preview emulates it. If Хасар's bones face backwards in game, re-export with
   `--no-display-flip`.
2. **Textures resolve.** Item models draw from the block (or newer item) atlas, so
   `assets/minecraft/atlases/blocks.json` and `items.json` add a directory source `entity/khasar`. Look for
   missing-texture magenta.
3. **Element rotations.** The stone shards should lean outwards and the hind metatarsus should be near vertical.
   The preview assumes right-handed element rotation.
4. **Hit flash** tints every bone: `tintindex 0` on every face, default −1.
5. At 10 Hz with 3-tick interpolation, the walk and run paws stay on the ground, and the pounce lands on the
   `pounce_land` ring.
6. Silhouette in the arena from 32 blocks; the head `Interaction` part follows the head.
