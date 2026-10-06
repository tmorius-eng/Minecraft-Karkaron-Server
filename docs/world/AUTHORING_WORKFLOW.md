# Authoring workflow (Axiom / WorldEdit as tools only)

SÜLD builds the world itself. Axiom and WorldEdit are **development tools** for hand-authoring detail and
are never installed on the production server.

1. **Plan in data.** Districts, roads, points and slices live in `assets/world/kharkhorum/*.json`
   (KHARKHORUM_MASTER_PLAN.md).
2. **Author a detail build** on a local creative server with Axiom or WorldEdit, inside an empty plot. The
   ground surface layer is part of the build; note how many layers are foundation below the surface. That
   number is `sink`.
3. **Export** as Sponge `.schem` (`//copy` then `//schem save name` in WorldEdit; Axiom: *Export → Schematic*).
   Litematica `.litematic` also works.
4. **Commit** the file to `assets/world/schematics/` and add a catalogue entry to `schematics.json` with its
   `front`, `sink`, `layer` and the **credit** (author, source, licence). For third-party downloads, use only
   files whose licence or author allows server use.
5. **Place** it in a slice as module `schem:<id>`, with `at`, `rotate`, `mirror` and `params.replace` for
   palette swaps.
6. **Validate offline** (`:suld-api:worldTool slice …`), then build on the test server
   (`/worldbuild rollback confirm`, then `/worldbuild build`), then `/worldbuild validate`, then
   `/worldbuild dump` and render it.

## Third-party schematic sources

2026-10-06: every post on minecraftschem.com (about 4,400 post ids) was probed for direct downloads. Only a handful
are free. The only two buildings among them, adripika12's "Fountain Square" and "Tower House", are European
village builds that don't fit Kharkhorum's style. All Asian, palace, gate, market, wall and statue builds there
are paid Patreon downloads. The importer is ready; paid or hand-authored files can be dropped in.
