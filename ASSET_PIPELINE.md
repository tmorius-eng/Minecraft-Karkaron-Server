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

## No mass generation
Only the pipeline is proven. Batch generation (queue in
`assets/generation_queue/`, human-approval lifecycle in the registry `status`)
begins only after the Meshy download path is unblocked and one fully-textured
asset is approved.
