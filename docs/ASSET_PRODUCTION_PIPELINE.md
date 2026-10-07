# Asset production pipeline (end to end)

Status: **design document, 2026-10-07.** It describes the pipeline with the tools that exist in this repository,
says plainly which stages are verified, and lists the tools still missing. The older
[`ASSET_PIPELINE.md`](../ASSET_PIPELINE.md) at the repo root stays as the record of the first proven asset
(`suld_ild_tenger`). Parts of it are out of date (see §8).

```
SPEC → GEMINI (concept) → MESHY (3D reference) → BLENDER headless (normalise, render, bake reference)
     → BLOCKBENCH (Java cuboid model, UV, validate) → RESOURCE PACK (validate, budget)
     → PAPER (SÜLD integration) → MINECRAFT CLIENT (QA) → registry status
```

## 1. Roles

| Who / what | Does | Does not |
|---|---|---|
| Claude Code | Specs, prompts, orchestration, validation, integration code, reports, registry | Ship raw AI output |
| Gemini (`tools/art/gemini_image.py`) | Concept sheets (front/side/back), mood, icon and UI drafts | Final textures |
| Meshy (`tools/meshy/meshy_client.py`) | 3D reference meshes for proportions, materials and colour | Shipped geometry |
| Blender 4.0.2 headless (`tools/blender/*.py`) | Normalise, measure, render orthographic and hero views, export clean glTF, bake colour references | Final pack files |
| Blockbench (`tools/blockbench/bb_mcp.py` + headless MCP server) | Java cuboid models, bones, UVs, display transforms, `bbmodel_validate` | Render previews (its renderer needs a GPU; Blender renders instead) |
| `tools/pack/*.py` | Procedural pack art (weapons, sprites, skins, HUD, UI) and item definitions | — |
| `tools/validation/*.py` | Registry, pack and budget gates | — |
| Owner / tester | Approvals; real-client QA | — |

## 2. The cuboid constraint

Java Edition item, block and equipment geometry is **axis-aligned cuboids**. Item-model elements live in a −16…32
box (3 × 3 × 3 blocks) with limited element rotation. Equipment layers cannot change the armour's shape at all,
only its texture. A resource pack cannot ship a triangle mesh.

So a Meshy mesh is never converted to a game asset. It is used three ways:

1. **Proportion reference**: orthographic renders at a known scale are the backdrop for building cuboids in
   Blockbench.
2. **Colour and material reference**: a bake or a render gives the value and hue per region, which is then
   quantised to the SÜLD palette and painted at 16–64 px.
3. **Promo / showcase**: hero renders for the website and Discord (`hero_render.py`), never in the pack.

The proven example: `suld_ild_tenger` has a 30,147-triangle refined Meshy GLB; in game it is a 16×16 sprite
(`assets/registry/assets.json`).

## 3. Stages, tools, inputs, outputs and gates

| # | Stage | Command (repo tool) | Input | Output | Gate (must pass to continue) |
|---|---|---|---|---|---|
| 0 | Spec | — | design doc section | registry entry `CONCEPT` in `assets/registry.json` | Spec approved (silhouette, palette, budget, history labels) |
| 1 | Concept drafts | `python3 tools/art/gemini_image.py --prompts tools/art/<set>.json --out assets/art/concepts/<type> --only <id>` with `"size": "1K"` | prompt set | 1K PNGs | Style guide check; ≤ 4 drafts per asset |
| 2 | Production reference | same, `"size": "2K"`, one image | chosen draft + corrections | 2K sheet: front, side, back on a neutral grey background | Owner or lead picks it; `status: GENERATED` once Meshy also exists |
| 3 | 3D reference | `meshy_client.py text3d --prompt … --art-style realistic`, then `poll <task> --download-dir assets/sources` | approved prompt (image-to-3D: see §7) | GLB/FBX/OBJ + thumbnail in `assets/sources/` (git-ignored) | Silhouette matches the sheet; credits logged; ≤ 2 attempts |
| 4 | Normalise and measure | `blender -b -P tools/blender/process_and_render.py -- --input <glb> --render <png> --export-glb <clean.glb> --size <blocks>` | raw GLB | `SULD_QC verts= polys= tris= materials= dims=` line, a preview PNG, a clean GLB | Scale and orientation right; tris and materials reported |
| 5 | Showcase (optional) | `blender -b -P tools/blender/hero_render.py -- --input <glb> --out <png>` | clean GLB | hero still in `assets/previews/` | — |
| 6 | Reference views and bake | **missing**: `tools/blender/ortho_views.py`, `tools/blender/bake_layer.py` | clean GLB | front/side/top orthographic PNGs at 16 px per block; colour bake on the target UV layout | Views line up with the Minecraft grid |
| 7 | Pixel texture | **missing**: `tools/art/pixelize.py` (named in `gemini_image.py`'s docstring but not in the repo) + hand clean-up | bake or concept | palette-quantised 32/64/128 px PNG | Palette and size per [ASSET_BUDGET](ASSET_BUDGET.md); no AI noise left |
| 8 | Java model | Blockbench MCP through `python3 tools/blockbench/bb_mcp.py --root assets/models --root resourcepack --calls <calls.json>` | views, texture | `.bbmodel` in `assets/models/blockbench/<type>/` + exported Java model JSON | `bbmodel_validate` 0 errors; elements in bounds; display transforms set; element budget |
| 9 | Preview without a GPU | `blender -b -P tools/blender/render_mc_model.py -- --model <json> --texture <png> --out <png>` | Java model + texture | in-game-geometry preview in `assets/previews/` | Visual check against the sheet; `status: BLOCKBENCH_VALIDATED` |
| 10 | Pack | write `assets/suld/items/…`, `equipment/…`, textures; procedural assets through `tools/pack/gen_*.py` | model + texture | files in `resourcepack/` | `python3 tools/validation/run_all.py` green |
| 11 | Paper integration | code: `ItemFactory` (EQUIPPABLE + ITEM_MODEL), mob renderer, `NpcService`, `SkillService` | pack keys | gameplay binding | Unit tests + `./gradlew build`; `status: INTEGRATED` |
| 12 | Client QA | real 1.21.11 client, pack served by `ResourcePackService` (bundled by Gradle `resourcePackZip`, `suld-plugin/build.gradle.kts:106`) | build | screenshots in `audit/qa/visual/<id>/` + checklist | Every checklist row passes; `status: CLIENT_TESTED` with date and tester |
| 13 | Approval | owner | QA record | — | `status: PRODUCTION_READY` |

## 4. Per-asset report

Every asset that reaches stage 4 gets `assets/reports/<asset_id>.json`, updated at each later stage. The directive's
Blender report fields are mandatory.

```json
{
  "id": "armor.baatar_t3",
  "gemini": { "drafts_1k": 3, "refs_2k": 1, "files": ["assets/art/concepts/armor/baatar_t3_ref2k.png"] },
  "meshy": { "tasks": ["<preview_id>", "<refine_id>"], "credits": 30, "balance_after": 990 },
  "blender": { "source_tris": 29870, "materials": 1, "texture_px": [2048, 2048],
               "glb_bytes": 27100000, "dims_blocks": [1.0, 2.0, 0.5], "warnings": [] },
  "minecraft": { "elements": 28, "tri_upper_bound": 336, "textures": { "helmet": "32x32", "humanoid": "128x64" },
                 "pack_bytes": 5120, "bbmodel_validate": { "errors": 0, "warnings": 0 } },
  "qa": { "status": "NOT_TESTED", "date": null, "tester": null, "screenshots": [] }
}
```

`process_and_render.py` already prints verts, polys, tris, materials and dimensions (`SULD_QC`, line 79). It does
**not** yet print texture size, file size or warnings. Adding a JSON report mode (`--report <json>`) is phase-3 work.

## 5. Naming and folders

| What | Pattern | Example | In git? |
|---|---|---|---|
| Asset id | `<type>.<name>`, `[a-z0-9_]` | `armor.baatar_t3`, `boss.khasar` | — |
| Class-tier stem | `<class>_t<n>` | `baatar_t3` | — |
| Piece suffix | `_helmet`, `_chest`, `_legs`, `_boots`, `_pauldrons`, `_back` | `baatar_t3_helmet` | — |
| Concept drafts | `assets/art/concepts/<type>/<stem>_draft<k>.png` (1K) | `…/armor/baatar_t3_draft2.png` | only the chosen one, ≤ 1 MB |
| Production reference | `assets/art/concepts/<type>/<stem>_ref2k.png` | | no (keep a ≤ 1 MB downscale in git) |
| Meshy output | `assets/sources/<stem>__<task_id>.glb` | `assets/sources/baatar_t3__01a1….glb` | no |
| Clean GLB | `assets/sources/<stem>_clean.glb` | | no |
| Blockbench source | `assets/models/blockbench/<type>/<stem>.bbmodel` | | yes |
| Previews | `assets/previews/<type>/<stem>_{front,side,back,ingame}.png`, ≤ 512 px | | yes |
| Report | `assets/reports/<asset_id>.json` | | yes |
| Pack files | `resourcepack/assets/suld/{items,models,textures,equipment}/…` | see [ARMOR_ASSET_PIPELINE §4](ARMOR_ASSET_PIPELINE.md#4-file-naming) | yes |

Keep Meshy files **flat** in `assets/sources/`. `.gitignore` only ignores `assets/sources/*.glb|*.fbx|*.obj` at
that level, so a sub-folder would get committed. `.blend` is ignored only under `assets/source/` (singular).

## 6. What is verified today

| Item | State on 2026-10-07 | Evidence |
|---|---|---|
| Meshy generation and **download from `assets.meshy.ai`** | **Verified**: one preview task (20 credits), 63,880-byte GLB, 873 triangles, imported by Blender | `ASSET_PIPELINE.md` §"download path (verified 2026-10-07)" |
| Meshy balance | 1,050 credits | `audit/visual-content-status.json` |
| Blender 4.0.2 headless | **Works** (Cycles CPU) | `audit/visual-content-status.json` (`pipelineBlender`) |
| Gemini image script | Works; produced the 21 GUI source images in `assets/art/source/` (JPEG data with a `.png` name; check with `file` and convert) | audit |
| Gemini `size` 1K/2K | Supported by the script for Gemini 3 image models (`gemini_image.py:26-28`); not re-tested this session | code |
| Blockbench headless MCP | **Not connected** this session (`.tools/` missing). `scripts/setup-cloud-tools.sh` installs it; MCP tools load at the **next** session start | session state, `.mcp.json` |
| Blender MCP server | **Not connected** (same cause). Plain `blender -b` scripts do not need it | same |
| Validators | registry OK, resource pack OK, world plan OK, **pack budget FAILS** (102 findings in HUD/GUI textures) | `python3 tools/validation/run_all.py` |
| Any asset client-tested | **No** | `assets/registry.json` |

## 7. Missing tools (phase 3)

| Tool | Purpose | Why it matters |
|---|---|---|
| `meshy_client.py image3d` and `refine` sub-commands | Image-to-3D from the approved 2K sheet; refine with PBR | Today only `text3d` (preview) and `poll` exist; the 4K refine was a manual POST |
| `tools/blender/ortho_views.py` | Orthographic front/side/top at 16 px per block | The Blockbench blocking backdrop |
| `tools/blender/bake_layer.py` | Project mesh colour onto the armour UV layout (128×64) or a bone atlas | The colour reference for painting |
| `process_and_render.py --report` | Write the §4 JSON (texture size, file size, warnings) | The directive's report fields |
| `tools/art/pixelize.py` | Palette quantisation and downscale to 16/32/64/128 px | Referenced, missing |
| `tools/blockbench/export_bones.py` | `.bbmodel` → per-bone item models + rig/animation JSON for the SÜLD bone renderer | Needed only if renderer A is chosen |
| `tools/validation/validate_asset_registry.py` | Validate `assets/registry.json` (fields, statuses, paths) | Nothing validates it yet |
| Equipment checks in `validate_resourcepack.py` | Every `equipment/*.json` layer texture exists at `textures/entity/equipment/<layer>/` | Catches the most likely armour mistake |
| `.psd` in `pack_budget.json` `forbidden_extensions` | The directive forbids PSD in the pack | Today only `.glb .fbx .obj .blend .stl .usdz` are listed |

## 8. Corrections to the root `ASSET_PIPELINE.md`

Not edited here; listed for whoever updates it:

* It says Blockbench headless MCP is "Working". In this session it is not connected.
* It still calls `suld_ild_tenger`'s in-game model a 5-cuboid model; it is now a 16×16 sprite.
* It refers to `assets/source/` (the real folder is `assets/sources/`), `assets/generation_queue/` (missing) and
  `tools/art/pixelize.py` (missing).

## 9. Credit and generation policy

* Spec first; no Meshy task without an approved 2K sheet (or, for text-to-3D, an approved prompt).
* Gemini: 1K drafts, 2K for the one production reference per asset, 4K only with a written reason in the report.
* Meshy per-asset caps: armour tier 30 (+1 re-roll), elite 30, dungeon boss 60, weapon or relic 30.
* Run `meshy_client.py balance` before and after every task; write the delta into the report. Stop and report to
  the owner at **300** credits spent in the slice.
* No batches. One asset at a time until the Баатар slice is CLIENT_TESTED.

## 10. Status

| Stage | Status |
|---|---|
| Gemini concept stage | FUNCTIONAL_BUT_INCOMPLETE (works; no concept-sheet prompt set yet) |
| Meshy text-to-3D + download | FUNCTIONAL_BUT_INCOMPLETE (image-to-3D and refine commands missing) |
| Blender normalise and render | FUNCTIONAL_BUT_INCOMPLETE (no ortho views, bake or JSON report) |
| Blockbench modelling and validation | SCAFFOLD (driver exists; server not connected) |
| Pixel texture step | NOT_IMPLEMENTED |
| Resource-pack validation | FUNCTIONAL_BUT_INCOMPLETE (budget validator failing; no equipment checks) |
| Paper integration of new asset kinds | NOT_IMPLEMENTED |
| Client QA process | NOT_IMPLEMENTED |
| Per-asset reports | NOT_IMPLEMENTED |
