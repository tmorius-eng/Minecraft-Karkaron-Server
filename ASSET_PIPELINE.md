# SÜLD Asset Pipeline

Coordinated, reproducible pipeline. Each tool does the job it is best at; Claude
Code orchestrates and validates. **Raw AI output is never shipped directly** —
every asset is processed, validated, and integrated.

```
CONCEPT → MESHY (text/image→3D) → RAW GLB → BLENDER (optimize, UV, bake, render,
export) → BLOCKBENCH (Minecraft model, UV, validate, export) → RESOURCE PACK
→ SÜLD integration (registry + item) → TEST
```

| Stage | Tool | Role | Status in this cloud env |
|---|---|---|---|
| Generate | **Meshy** (`api.meshy.ai`) | text/image→3D, refine, PBR texture | **Generation works** (proxy-injected credential, balance 1100, preview task SUCCEEDED). **Download blocked** — see below. |
| Process | **Blender 4.0.2** headless | import, normalise, optimize, bake, render, export glTF/OBJ | **Working** (Cycles CPU; numpy installed; OIDN off). Tools in `tools/blender/`. |
| Model | **Blockbench headless MCP** (23 tools) | Minecraft `.bbmodel`, UV, validate, export | **Working.** Driver: `tools/blockbench/bb_mcp.py`. |
| Integrate | Claude Code | registry, resource pack, validation | **Working.** `tools/validation/`. |

## Credentials
Meshy auth is injected by the cloud egress proxy for `api.meshy.ai`. This repo
and all tooling **never read, print, or commit any key** (`tools/meshy/meshy_client.py`
sends no Authorization header). Never add a key to env or source.

## ⛔ Current hard blocker: `assets.meshy.ai`
`api.meshy.ai` (control plane) is allowed, but Meshy serves generated models and
textures from **`assets.meshy.ai`**, which the egress policy **denies (403)**.
Consequently preview/refine tasks SUCCEED but their GLB/FBX/texture files cannot
be downloaded in this session — blocking Meshy→Blender ingestion of the real
textured mesh.

**Fix:** allow `assets.meshy.ai` in the environment's Network access → Allowed
domains. Then the full pipeline runs unattended.

## Running the pipeline (once `assets.meshy.ai` is allowed)

```bash
# 1. (fresh session) install toolchain
scripts/setup-cloud-tools.sh

# 2. generate (preview already done: task 01a110c4-130e-70d5-be0a-5edd28a6c70c)
python3 tools/meshy/meshy_client.py balance

# 3. REFINE the preview -> 4K PBR textured GLB, then download
#    POST /v2/text-to-3d {mode:refine, preview_task_id, ai_model:meshy-7.1,
#    enable_pbr:true, texture_resolution:4096, texture_prompt:"dark-blue steel
#    blade, aged forged metal, gold Mongolian ornamental engravings, wolf and
#    eagle motifs, dark-blue leather grip, bronze/gold fittings, worn premium
#    legendary weapon, realistic material separation, no floating decorations,
#    no European medieval styling"}
python3 tools/meshy/meshy_client.py poll <refine_task_id> --download-dir assets/sources

# 4. Blender: normalise + render + export (preserves baked textures)
blender -b -P tools/blender/process_and_render.py -- \
  --input assets/sources/<refine_task_id>.glb \
  --render assets/previews/suld_ild_tenger_meshy.png \
  --export-glb assets/sources/suld_ild_tenger_clean.glb --size 2.0

# 5. Blockbench: author the Minecraft model, validate, export (see bb_mcp.py)
# 6. Integrate into resourcepack/ + update assets/registry/assets.json
# 7. Validate
python3 tools/validation/run_all.py
```

## Quality control (directive §43)
Every 3D asset is checked for polycount/tris, UV, texture size, materials,
scale, origin, orientation, broken refs, duplicate IDs, and Minecraft
compatibility. `bbmodel_validate` gates geometry; `tools/validation/` gates the
registry and resource pack. AI meshes are decimated/retopo'd in Blender before
becoming game assets.

## Proven asset
`weapon.suld_ild_tenger` — full Blockbench→resource-pack→Blender-render path
proven (render in `assets/previews/`). Its **Blockbench geometry is an interim
placeholder**; it will be replaced by the Meshy-refined textured model once
`assets.meshy.ai` is reachable (registry `status: needs_review`).

## Resource pack format (Minecraft 1.21.11)
- `pack.mcmeta` uses `min_format`/`max_format` = **75** (1.21.11's `pack_version.resource_major`, read from the
  vanilla jar's `version.json`). The original `pack_format: 34` targeted 1.21–1.21.1 and would be flagged
  incompatible.
- Custom models are selected by **item model definitions** in `assets/minecraft/items/<item>.json`
  (`range_dispatch` on `minecraft:custom_model_data`, index 0, plus a sentinel above the highest id).
  The legacy `overrides` list in `models/item/*.json` was removed in 1.21.4 and is rejected by the validator.
- `validate_resourcepack.py` cross-checks every registry asset's `custom_model_data` → `minecraft_model`.

## Tool-selection matrix (art direction)

Target look: **clear voxel/pixel-art silhouettes** — sharp, readable, cheap. Do
not over-render ordinary gameplay items. Use the heaviest tool only where it pays.

| Asset | Tool(s) |
|---|---|
| Ordinary sword / potion bottle / shields (e.g. 20 variants) | **Blockbench** (pixel-art) |
| Yurt decoration / small props | Blockbench (+ Blender if needed) |
| Epic armor | Meshy + Blender |
| **Legendary** weapon (hero) | Meshy + Blender + Blockbench |
| Huge boss | Meshy + Blender |
| Khan statue / cinematic / promo | Meshy + Blender |
| Spawn buildings / structures | **Minecraft blocks + SÜLD WorldBuilder** (never a giant imported mesh) |

## Meshy cost & quality policy

- **Default 2K** texture for ordinary/important items; `meshy-7.1` for important.
- **4K ONLY** for hero legendaries, major bosses, statues, cinematic/promo.
- **8K ONLY** for exceptional non-gameplay assets.
- Final **Minecraft** textures: **64×64 or 128×128**; 256 only when justified
  (enforced by `tools/validation/validate_pack_budget.py`). Raw Meshy source may
  keep higher detail; the shipped pack carries optimized game-ready assets only.
- Never spend 4K/8K credits without a justified visual benefit.

Every produced asset reports: **Meshy credits used · source tris · final tris ·
final texture resolution · final pack file size**.

### Asset report — `weapon.suld_ild_tenger` (legendary hero, 4K justified)
| Metric | Value |
|---|---|
| Meshy credits used | 30 (preview + 4K PBR refine; balance 1100 → 1070) |
| Source model triangles | 30,147 (Meshy refined GLB) |
| In-game model triangles | ~60 (Blockbench 5-cuboid item model) |
| In-game texture resolution | 16×16 (pixel-art) |
| Resource-pack size impact | ~6 KB uncompressed (model JSON + 16×16 PNG) |
| 4K asset | promo/NPC/reference only — NOT shipped in the pack |

## No mass generation
Only the pipeline is proven. Batch generation (queue in
`assets/generation_queue/`, human-approval lifecycle in the registry `status`)
begins only after the Meshy download path is unblocked and one fully-textured
asset is approved.
