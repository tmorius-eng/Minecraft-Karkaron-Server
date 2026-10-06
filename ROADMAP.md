# SULD Roadmap

Phased so that no single change is a giant implementation. Each phase keeps the
build green, adds tests, and respects the hard rules in
[ARCHITECTURE.md](ARCHITECTURE.md). Gameplay is always custom-built.

## Phase 1 — Foundation ✅ (this phase)

- Multi-module Gradle build (`suld-api` pure domain + `suld-plugin` Paper).
- Config system (data-driven, Bukkit↔model bridge, unit-tested).
- Persistence: async `ProfileRepository`, HikariCP, MySQL/PostgreSQL migrator,
  in-memory dev backend; `suld_schema_version` + world-unique constraints.
- Player profiles; level/EXP engine (data-driven curve).
- Five classes scaffolded (identity, role, one-time selection).
- Analytics interface + logging sink; retention-funnel event keys.
- Plugin bootstrap, `SuldServices` container, join/quit lifecycle,
  `/suld` and admin `/revive`.
- Item rarity tiers; world-unique item storage model.
- Tests + green build; plugin links against Paper API 1.21.11.

## Phase 2 — Class skill system

Skill trees (active / passive / ultimate), cooldowns, resource costs, a
data-driven skill registry, and the persisted `suld_skill_trees` /
`suld_player_skills`. Starter-skill demo for the first-5-minutes beat.

## Phase 3 — Items & economy

Item instances with persistent UUID + provenance (`suld_items`), loot tables,
affix rolls, reforging, upgrades, soulbound handling, durability. Server-
authoritative item validation (anti-dup hardening).

## Phase 4 — World-unique items (full)

Transactional acquisition service, server-wide broadcast, ownership history,
`/suld-unique` admin recovery, and *Хөх Сүлд* as the flagship relic.

## Phase 5 — Mobs & bosses

Custom mob framework (levels, Elite / Champion / Mythic tiers), bosses and world
bosses, boss-participation analytics. Hand-written AI/stat scaling (no mob
plugins).

## Phase 6 — Dungeons & world events

Instanced/keyed dungeons, scheduled world events, discovery/exploration unlocks.

## Phase 7 — Hardcore death (full)

Soul-state state machine, loot/durability application, free in-game recovery
(resurrection quests, soul shards), death ledger.

## Phase 8 — Social

Clans (`suld_clans`), parties, shared progression hooks, clan events.

## Phase 9 — Progression meta

Quests, achievements, leaderboards, player statistics, long-term goals.

## Phase 10 — UI & presentation

Polished class-selection GUI, custom inventory GUIs, resource-pack integration
(fonts/figures/sounds), the first-5-minutes cinematic flow.

## Phase 11 — Live ops & integrations

Discord integration hooks, web/API dashboard (consuming the analytics stream),
cosmetic-only monetization (cosmetics, particles, titles, emotes, mount skins,
UI themes, pets, cosmetic battle pass), anti-exploit heuristics, richer audit.

## Cross-cutting (continuous)

Anti-duplication, exploit detection, audit logging, performance (async I/O,
region-safe scheduling), and test coverage grow alongside every phase.
