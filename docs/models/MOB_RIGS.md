# Mob and boss rigs

Every SÜLD mob and boss is drawn by a Display-Entity rig (docs/MODEL_RENDERER.md). Хасар has its own hand-authored
rig (`tools/model/khasar*.py`, 26 displays). The other twelve are built from four **body plans** in
`tools/model/fauna.py`. All are original cuboid work: no meshes, no third-party models, no copied textures.

| Rig | Mob | Plan | Displays | Look |
|---|---|---|---|---|
| `chono_govi` | Говийн Чоно | canine | 8 | lean sand-tan wolf, dark saddle |
| `chono_orkhon` | Орхоны Чоно (Хасар's pack) | canine | 8 | grey-brown wolf with a chest ruff |
| `chono_saaral` | Хангайн Саарал Чоно | canine | 8 | big grey wolf, pale belly, yellow eyes |
| `baavgai` | Хангайн Баавгай | ursine | 7 | brown bear: shoulder hump, long claws |
| `oin_ezen` | **Хар Баавгай — Ойн Эзэн** (boss) | ursine ×1.55 | 7 | black bear with moss on its back, spruce twigs and a crown of branches, green eyes |
| `deeremchin` | Дээрэмчин | humanoid | 8 | blue deel with a diagonal front flap and trim, leather belt with plaques, лоовууз fur hat with a red top and knot, saber |
| `elsnii_suns` | Элсний Сүнс | wraith | 6 | sand-cloth wraith, hood with a dark opening, glowing amber eyes, ragged robe, floats |
| `mosun_suns` | Алтайн Мөсөн Сүнс | wraith | 6 | pale ice wraith with an icicle crown |
| `elsnii_khaan` | **Элсний Хаан — Булшны Эзэн** (boss) | humanoid ×1.35 | 9 | wrapped tomb king: red-and-gold deel, gold crown with a gem, gold staff |
| `mosun_khaan` | **Мөсөн Хаан — Оргилын Сахиул** (boss) | humanoid ×1.45 | 9 | ice-armoured guardian, icicle crown, ice-tipped spear |
| `altai_avarga` | Алтайн Аварга | brute ×2.1 | 9 | grey-skinned giant, fur loincloth, stone club |
| `khilents` | Говийн Хилэнц | arachnid | 8 | Gobi scorpion: plated shell, pincers, curled tail with a red bulb |

## Body plans

There are few bones on purpose: region mobs are on screen by the dozen.

* **canine:** body, head, jaw, 4 legs, tail.
* **ursine:** body (with the hump), head, jaw, 4 legs.
* **humanoid:** hips (robe), torso, head, 2 arms, 2 legs, weapon. The wraith variant has no legs and a long ragged
  robe; the brute variant has bare arms.
* **arachnid:** body, 2 claws, 2 leg banks, 3 tail segments.

The renderer's LOD updates a rig every 2 ticks near a player, every 4 ticks within 48 blocks, and not at all further
away. Unchanged transforms are never re-sent.

## Painting and clips

* `tools/model/fauna_paint.py` is one deterministic procedural painter, driven by each rig's palette:
  * fur strands;
  * cloth weave, with the deel's front flap and trim, and belt plaques;
  * metal and ice with an edge highlight;
  * shell plates, wood grain;
  * a face with brows, eyes, nose and mouth;
  * glowing spirit eyes.
* Atlases are 64² (128² for the bosses with ornaments).
* `tools/model/fauna_clips.py` gives each body plan these clips:
  * `idle`, `walk`, `run` (the wraiths glide instead);
  * `attack`, with an `attack_hit` event at the strike: a wolf lunges and bites, a bear rears and swipes, a
    humanoid cuts overhead, a scorpion stings;
  * `hurt`;
  * `death`: quadrupeds fall onto their side, humanoids fall backwards, spirits rise and fade, the scorpion flips.

## Pipeline

```
python3 tools/model/export_fauna.py            # every rig → resource pack + plugin resources + models/index.json
python3 tools/model/export_rig.py baavgai      # one rig (falls back to fauna.RIGS when there is no <rig>.py)
python3 tools/model/preview_rig.py baavgai --out /tmp/p --views threequarter
```

* `suld-plugin/src/main/resources/models/index.json` lists the loaded rigs and the mob → rig map (`"mobs"`).
* The plugin dresses every SÜLD mob of that map on spawn.
* `models.mobs: false` keeps only the bosses rigged, for a weaker server.
* A rigged mob plays `attack` whenever it lands a hit. Bosses with a brain (Хасар) play their own clips.

## QA

* **Verified offline:** the export checks (bones, atlases, clips) and software-rendered previews.
* **Verified on the dev server:** the rigs load and are attached when mobs spawn (see the commit).
* **MANUAL_QA_REQUIRED in a real client:**
  * proportions;
  * texture readability;
  * that the animations read well (gait sync, attack timing);
  * the 180° display turn (docs/MODEL_RENDERER.md).
