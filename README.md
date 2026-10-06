# SULD — Mongolian Hardcore MMORPG

A production-grade **PaperMC** MMORPG server for Mongolian players, focused on
hardcore PvE: exploration, progression, loot, class builds, dungeons, bosses,
clans, hardcore death, and extremely rare world-first items.

Every gameplay system is **custom-built in this repository** — classes, combat, spells, items, quests, dungeons,
relics, death, HUD, menus, NPCs and the city. Third-party server plugins provide generic plumbing only
(permissions, moderation, anti-cheat, world tools, Bedrock/version bridging); plugins that would own an RPG or UI
layer (MythicMobs, MMOItems/MMOCore, Citizens, ItemsAdder, DeluxeMenus, TAB, …) are never used — see
[DEPENDENCIES.md](DEPENDENCIES.md) and `deploy/plugins/plugins.json`.

> Naming note: the class names (Баатар, Мэргэн, Бөө, Дархан, Хүлэгчин) are
> Mongolian-*inspired* fantasy archetypes. SULD keeps fantasy lore separate from
> real history and makes no historical claims — see [GAME_DESIGN.md](GAME_DESIGN.md).

## Status — playable

Built, live-tested on Paper 1.21.11 with bots, `./gradlew build` green (215 tests; PostgreSQL ITs opt-in):

- **Classes and combat:** five classes, 3D class weapons in four level tiers with effects, Wynncraft-style click
  combos (20 spells), class resources, a data-driven level curve.
- **World:** Kharkhorum (safe city with glowing NPCs: class, tutorial, quests, shop, smith, travel, blessing) and
  four wild regions (Хэрлэн, Говь, Хангай, Алтай) with their own mobs, level bands, discovery EXP and danger warnings.
- **Content:** the 18-chapter storyline «Сүлдний Зам» with a quest tracker; four dungeons with three-phase
  bosses; world events; two world-unique relics; clans and parties.
- **Hardcore death:** loot and EXP loss, gear wear, soul state.
- **UI:** Mongolian-themed resource pack, sidebar, TAB, chat badges, GUIs for every command (menu, help, tutorial,
  class, skills, quests, rank-up, level rewards, cosmetics, shop, credit store, daily reward, leaderboards).
- **Retention and social:** daily streak reward, daily hunting tasks, `/top` leaderboards, `/trade`, personal
  steppe horses, blacksmith upgrades, level milestones, rank ladder, cosmetics bought with coins or store credits
  (cosmetics only). Damage numbers and mob health bars.
- **Operations:** verified Microsoft accounts only, chat guard, MOTD and icon, tips, plugin installer (Modrinth,
  hash-verified), production deploy/update/backup scripts. See [docs/OPERATIONS.md](docs/OPERATIONS.md).

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
- [docs/OPERATIONS.md](docs/OPERATIONS.md) — running the server: commands, staff badges (LuckPerms), credit store, death rules, Bedrock, MOTD.
- [docs/AUTHENTICATION.md](docs/AUTHENTICATION.md) — identity = Minecraft/Microsoft-authenticated UUID; no /login; join pipeline, races, audit.
- [DEPLOYMENT.md](DEPLOYMENT.md) — production runbook for a fresh Ubuntu 24.04 (Vultr) VPS: deploy, update/rollback, backup/restore.
