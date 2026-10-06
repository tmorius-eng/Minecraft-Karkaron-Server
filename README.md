# SULD — Mongolian Hardcore MMORPG

A production-grade **PaperMC** MMORPG server for Mongolian players, focused on
hardcore PvE: exploration, progression, loot, class builds, dungeons, bosses,
clans, hardcore death, and extremely rare world-first items.

Every gameplay system is **custom-built in this repository**. SULD does **not**
use ready-made gameplay plugins (no MythicMobs, MMOItems/MMOCore, EssentialsX,
Citizens, ItemsAdder, DeluxeMenus, quest/skill/crate plugins, …). Third-party
libraries are infrastructure only (database pool, JDBC drivers, serialization,
testing) — see [DEPENDENCIES.md](DEPENDENCIES.md).

> Naming note: the class names (Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин) are
> Mongolian-*inspired* fantasy archetypes. SULD keeps fantasy lore separate from
> real history and makes no historical claims — see [GAME_DESIGN.md](GAME_DESIGN.md).

## Status — Phase 1 (foundation)

Implemented and building green:

- Multi-module Gradle project (`suld-api` pure domain + `suld-plugin` Paper plugin).
- Player profiles, character level/EXP with a **data-driven** curve.
- Five classes scaffolded (identity, role, selection).
- Configuration system (data-driven, Bukkit YAML ↔ dependency-free model).
- Async persistence (HikariCP + MySQL/PostgreSQL) with a forward-only migrator,
  plus an in-memory backend for dev/tests.
- Extensible analytics interface + a logging sink (retention-funnel event keys).
- `/suld` (info/profile) and `/revive <player>` (admin) commands; join/quit
  lifecycle with analytics.
- Item-rarity tiers and the world-unique-item storage constraints (schema).
- 24 unit tests; plugin verified to compile **and link** against Paper API 1.21.11.

See [ROADMAP.md](ROADMAP.md) for what each later phase adds.

## Target stack

| | |
|---|---|
| Platform | PaperMC `1.21.11` (`paper-api:1.21.11-R0.1-SNAPSHOT`) |
| Java | 21 (required by Paper 1.21.x; toolchain is `-PjavaVersion=` configurable) |
| Build | Gradle 8.14 (wrapper included) |
| Database | MySQL / MariaDB or PostgreSQL (HikariCP pooling), or in-memory for dev |
| UI/text | Adventure API (bundled with Paper) |

## Build

```bash
./gradlew build          # compile + test everything (needs repo.papermc.io)
./gradlew :suld-api:build # domain layer only — no Minecraft dependency needed
```

The runnable plugin jar is produced at
`suld-plugin/build/libs/suld-plugin-<version>.jar` (shaded; drop it into a
Paper server's `plugins/` folder).

## Run a test server

```bash
./gradlew :suld-plugin:runServer      # run-paper plugin, or:
scripts/run-test-server.sh 1.21.11    # self-contained downloader + launcher
```

Both need outbound access to Paper's download CDN (`fill.papermc.io`,
`fill-data.papermc.io`) and Mojang (`piston-data.mojang.com`,
`piston-meta.mojang.com`, `libraries.minecraft.net`) to fetch the server jar.

## Documentation

- [ARCHITECTURE.md](ARCHITECTURE.md) — modules, layers, data flow, concurrency, hard rules.
- [DATABASE.md](DATABASE.md) — schema (current + target), anti-duplication design.
- [GAME_DESIGN.md](GAME_DESIGN.md) — classes, items, death, retention funnel, monetization, lore.
- [ROADMAP.md](ROADMAP.md) — phased implementation plan.
- [CONTRIBUTING.md](CONTRIBUTING.md) — conventions and the "build it yourself" rule.
- [DEPENDENCIES.md](DEPENDENCIES.md) — auditable dependency & repository list.
- [docs/AUTHENTICATION.md](docs/AUTHENTICATION.md) — identity = Minecraft/Microsoft-authenticated UUID; no /login; join pipeline, races, audit.
- [DEPLOYMENT.md](DEPLOYMENT.md) — production runbook for a fresh Ubuntu 24.04 (Vultr) VPS: deploy, update/rollback, backup/restore.
