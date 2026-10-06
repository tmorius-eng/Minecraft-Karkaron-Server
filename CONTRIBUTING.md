# Contributing to SULD

## The one rule that defines SULD

**Build the gameplay yourself.** SULD's MMORPG systems are custom-owned code in
this repository. Do **not** add, depend on, or require a ready-made gameplay
plugin as a substitute for a SULD system. Non-exhaustive blocklist:

> EssentialsX, LuckPerms (for gameplay logic), MythicMobs, MMOCore, MMOItems,
> ItemsAdder, Oraxen, Citizens, DeluxeMenus, Jobs, skill plugins, combat
> plugins, dungeon plugins, quest plugins, custom item/mob plugins, economy/
> auction/crate plugins, RPG progression plugins, or anything similar.

Third-party libraries are allowed **only** as infrastructure/utility
(database, pooling, networking, serialization, testing, platform API) and never
to replace a gameplay system. See [DEPENDENCIES.md](DEPENDENCIES.md).

## Dependency & repository governance

1. Prefer **Maven Central**; prefer **PaperMC** for Paper artifacts.
2. Avoid JitPack unless absolutely necessary.
3. Avoid snapshot dependencies in production (the Paper API is the sole,
   unavoidable exception — it is `compileOnly`, not shipped).
4. Pin every version. Keep the audit in [DEPENDENCIES.md](DEPENDENCIES.md) current.
5. **Before adding a Maven repository**, report in the PR: repository URL, the
   dependency it serves, and why Central/Paper cannot provide it. Never add a
   repository silently.

## Code conventions

- **`suld-api` stays pure** — no `org.bukkit` / `io.papermc` / Spigot imports.
  Game logic that can be server-agnostic belongs here and must be unit-tested.
- **No static global state.** Construct services once; pass them explicitly.
- **No synchronous DB on the main thread.** Repositories return
  `CompletableFuture`; hop back to the main thread before touching Bukkit.
- **Data-driven values.** New tunables go in config with a default in a settings
  record and mapping in `SuldConfigFactory` (+ a test).
- **Small classes, clear seams.** Prefer a new class/interface over growing a
  "god" class.
- Java 21 language level. 4-space indent, UTF-8, `-parameters`.
- Player-facing text supports Mongolian (Cyrillic); keep strings in the UI layer.

## Tests

- Add JUnit 5 tests for new domain logic in `suld-api` (fast, no server).
- For `suld-plugin`, unit-test logic that needs no Bukkit (SQL/config/dialect);
  use the Paper test server for integration checks.
- Keep `./gradlew build` green. For domain-only work, `./gradlew :suld-api:build`
  runs without any Minecraft dependency.

## Building & running

```bash
./gradlew build                      # everything (needs repo.papermc.io)
./gradlew :suld-api:build            # domain only, no Minecraft needed
./gradlew :suld-plugin:runServer     # Paper test server (needs Paper/Mojang CDNs)
scripts/run-test-server.sh 1.21.11   # self-contained server launcher
```

## Design guardrails

- Hardcore **PvE** focus; no pay-to-win, no gambling, no loot boxes.
- Monetization is cosmetic-only.
- Keep **fantasy lore separate from real history**; make no historical claims
  (see [GAME_DESIGN.md](GAME_DESIGN.md)).
- Anything sensitive (item grants/transfers, admin actions) must be audited.
