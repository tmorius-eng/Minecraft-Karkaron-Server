# 3D props (Meshy)

Static 3D monuments made with Meshy (text to 3D), turned into Minecraft box models and placed in the world as item
displays.

| Prop | Where | History | Boxes |
|---|---|---|---|
| Хархорумын Чулуун Мэлхий (stone turtle) | Kharkhorum, both sides of the south avenue (layout ±26, 60) | VERIFIED | 1009 |
| Мөнгөн Мод (silver tree fountain) | Kharkhorum palace courtyard, beside the block fountain (−20, −77) | INSPIRED (Rubruck's description) | 3859 |
| Буган Хөшөө (deer stone) | site.uushig, 10 blocks north of the centre | VERIFIED (the kind of monument) | 5145 |
| Хүн Чулуу (stone man) | site.dariganga, 8 blocks east of the centre | VERIFIED (the kind of monument) | 3402 |

Each is a simplified game version, never a copy of a particular monument. Clicking a prop shows its name, history
label and a short note.

## Pipeline

1. `python3 tools/meshy/generate_props.py`
   * Takes the prompts in `tools/meshy/props.json`.
   * Runs a Meshy preview and the textured refine (meshy-7.1).
   * Saves the GLB in `assets/sources/props/` (git-ignored).
   * The tasks are cached in `assets/.meshy_cache/`, so a re-run spends nothing.
   * The credential is injected by the environment's proxy; no key is read, printed or stored.
2. `python3 tools/meshy/build_props.py [--preview DIR]` runs two stages:
   * `voxel_sample.py` (Blender) samples the mesh into surface voxels. Each voxel gets the base-colour texture at its
     nearest surface point, plus ambient occlusion from short rays, so carvings and folds show as darker voxels.
   * `voxel_model.py` builds the model:
     * fills the inside;
     * reduces the colours (median cut);
     * smooths the speckle;
     * merges voxels greedily into boxes;
     * leaves out faces that can never be seen;
     * writes `resourcepack/assets/suld/{models,items}/prop/<id>.json` and a 16×16 palette texture.

   It is deterministic. The voxel settings per prop are in `props.json` (`voxel`). A box model fits 48 units
   (−16..32); half-unit voxels give twice the detail.
3. `suld-plugin/src/main/resources/content/props.json` places them. `PropService` reads the server's copy in
   `plugins/SULD/content/props.json` first, so props can be moved or added without a build.
   * A place is `city: [x, z]` (city layout coordinates) or `site: <id>` plus `offset`, with `yaw`.
   * The display is scaled to `height` blocks and stands on the highest block.
   * It is not saved with the chunk; it appears when the chunk loads.
   * `/suld props` lists them.

Rejected: the white banner (цагаан туг). Meshy made a cloth banner twice (once with a sun-like emblem) instead of the
horsehair standard, so it is not shipped.

Meshy credits: 210 used (5 props, plus the stone man and the banner made a second time); about 820 left.

Status: **MANUAL_QA_REQUIRED**.
* The bots saw the displays and interaction boxes spawn and the click text.
* How they look in a real client (scale, facing, lighting) and the frame rate near the silver tree (≈10k faces) have
  not been checked.
