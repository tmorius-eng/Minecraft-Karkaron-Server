# SULD Game Design

> **Lore disclaimer.** SULD is a *fantasy* MMORPG. Class names and flavour are
> Mongolian-**inspired**. This document separates **fantasy lore** (invented for
> the game) from **historical note** (real-world context), and makes no claim
> that the fantasy content is historical fact. Where a real term is borrowed, it
> is used respectfully as inspiration only.

## Pillars

Hardcore **PvE**: exploration, progression, loot, class builds, dungeons,
bosses, clans, meaningful death, and extremely rare world-first items. No
pay-to-win, no gambling mechanics.

## Classes

Five classes. Each has active, passive, and ultimate skills via a class skill
tree (built: `/skills`, see docs/SKILL_TREE_DESIGN.md). Numbers below are **direction**, not final balance — all
real values are data-driven in config.

| Class | Fantasy archetype | Role | Difficulty | DMG | DEF | Mobility |
|---|---|---|---|---|---|---|
| **Баатар** (Baatar) | Warrior / tank / berserker | Frontline bruiser | ★☆☆ | ●●○ | ●●● | ●○○ |
| **Мэргэн** (Mergen) | Archer / ranged / assassin | Ranged burst | ★★☆ | ●●● | ●○○ | ●●○ |
| **Бөө** (Boo) | Spirit mage / support | AoE + party sustain | ★★★ | ●●○ | ●○○ | ●●○ |
| **Дархан** (Darkhan) | Forge warrior / weapon crafter | Craft-driven power | ★★☆ | ●●○ | ●●○ | ●○○ |
| **Хүлэгчин** (Khulegchin) | Mounted mobility / charge | Engage & kite | ★★☆ | ●●○ | ●○○ | ●●● |

- *Historical note:* "баатар" (hero), "мэргэн" (sharpshooter/wise), "бөө"
  (shaman), "дархан" (smith/craftsman), and mounted traditions are real
  Mongolian cultural concepts. Their SULD kits, stats, and story are invented.

The class UI (first-login selector) communicates role, difficulty, DMG/DEF/
mobility, starter skill, ultimate, and a short lore line. Class selection is a
one-time choice (admin can reset). Phase 1 scaffolds classes + selection; the
polished GUI selector and skill trees are later phases (see ROADMAP).

## Items

Rarities (ascending): `COMMON → UNCOMMON → RARE → EPIC → LEGENDARY → ANCIENT →
MYTHIC → UNIQUE`. Each tier has a display name, colour, and default drop weight
(`ItemRarity`). Items support: item level, rarity, stats, affixes, unique
effects, soulbound state, durability, upgrade level, provenance, and a
persistent UUID (globally unique identity when required). Systems built on top
(later phases): loot tables, reforging, affix rolls, upgrades.

### World-unique items

Some items exist **once on the entire server** — e.g. **Хөх Сүлд (The Blue
Standard / Khukh Suld)**. Guarantees (storage + service): database-level
uniqueness, transactional acquisition, duplication protection, item UUID,
ownership history, server-wide broadcast on acquisition, audit log, and an admin
recovery command. Because uniqueness is enforced by DB constraints, a restart or
crash can never create a duplicate. See [DATABASE.md](DATABASE.md).

**Implemented (Slice 4, [docs/RELICS.md](docs/RELICS.md)):** relics wait at auto-built shrines; a 10-second
ritual claims one (level-gated, one per bearer); the bearer glows and gains EXP; a player who kills the
bearer seizes it, any other death returns it to its shrine; the physical item can never be dropped, stored
or duplicated — any non-genuine copy is destroyed on sight.

## Hardcore death system

Configurable (see `death:` in `config.yml`). A death may place the character in
a temporary **soul/death state** for `soul-state-seconds`, apply gear durability
damage, lose a fraction of current-level EXP, and drop a configurable fraction
of non-soulbound loot. Recovery is **free in-game** (resurrection quest hooks,
soul-shard hooks — later phases) or an **admin action**.

- **No real-money resurrection** is part of the core architecture.
- Admin recovery: `/revive <player>` (permission `suld.admin.revive`), audited.

Implemented (`DeathRules` in suld-api, `DeathService` in the plugin):

- **Soulbound, always kept:** class weapons, armour/shield/elytra and the menu item. Relics follow the relic rules.
- **Loot:** `loot-loss-fraction` of the other stacks (rounded down, chosen at random) drops where you died; the rest stays in its slot.
- **EXP:** `exp-loss-fraction` of the progress into the current level is lost — never a level. No vanilla EXP orbs.
- **Durability:** kept damageable gear takes `durability-damage-fraction` of its max durability (never breaks).
- **Soul state (Сүнс):** after respawning you are a soul for `soul-state-seconds` — slowed and weakened; you cannot
  deal or take damage and mobs ignore you; a boss bar counts down. With `allow-free-revive: false` the soul waits for
  `/revive`. Relogging does not reset the timer. A chat line summarises what the death cost.

## Storyline — «Сүлдний Зам»

Eighteen chapters (`QuestContent.STORY`), one active at a time; finishing a chapter pays its EXP/₮ and starts the
next at once. Objective kinds: kill (`KILL_MOB`), reach a level, collect (items are handed in on completion),
discover a region (crossing into it), clear a dungeon (without being downed).

| # | Chapter | Objective | Region |
|---|---|---|---|
| 1 | Анхны Ан | 3 Говийн Чоно | Хэрлэн |
| 2 | Чонын Арьс | collect 3 wolf pelts | Хэрлэн |
| 3 | Хэрлэнгийн Дээрэмчид | 5 bandits | Хэрлэн |
| 4 | Цэргийн Тангараг | level 5 | — |
| 5 | Хасарын Агуй | clear Khasar's Den | dungeon |
| 6 | Говийн Зам | reach the Gobi | Говь |
| 7 | Хилэнцийн Хор | 6 scorpions | Говь |
| 8 | Элсний Сүнснүүд | 5 sand spirits | Говь |
| 9 | Говийн Булш | clear the Gobi Tomb | dungeon |
| 10 | Мянгатын Сорил | level 12 | — |
| 11 | Хангайн Ой | reach the Khangai | Хангай |
| 12 | Саарал Чонын Сүрэг | 8 grey wolves | Хангай |
| 13 | Хангайн Эзэн | 2 bears (elite) | Хангай |
| 14 | Баавгайн Үүр | clear the Bear Lair | dungeon |
| 15 | Алтайн Оргил | reach the Altai | Алтай |
| 16 | Мөсөн Сүнс | 8 ice spirits | Алтай |
| 17 | Мөсөн Оргил | clear the Ice Peak | dungeon |
| 18 | Алтайн Аварга | 3 giants (elite) | Алтай |

`/quest` opens the chapter board (done / active / locked), `/quest info` prints the active chapter; the sidebar
tracks progress and a boss-bar tracker points (arrow, compass word, distance) at the objective's region (`/quest track`). Admins: `/suld quest <player> <1..18|reset>`.

## Blacksmith upgrades

The city blacksmith (Дархан) repairs gear for coins and upgrades SÜLD items one item level at a time (never above
the player's level, cap 60) for coins plus two pieces of the material of the item's band: wolf pelts (< 8),
scorpion venom (< 15), bear pelts (< 22), ice stones. The item keeps its identity and shows "+N". Class weapons
grow with the player's level instead.

## Dungeons

Wave arenas that open where the party stands (never in Kharkhorum): waves of the region's mobs, then a three-phase
boss (enrages after a timer). Every participant who was not downed gets the completion bonus and a reward roll.
`/dungeon list`, `/dungeon enter <name>`.

| Dungeon | Region | Level | Boss | Bonus |
|---|---|---|---|---|
| Хасарын Агуй `khasar_den` | Хэрлэн | 2+ | Хасар (ravager) | 400 EXP · 75 ₮ |
| Говийн Булш `govi_bulsh` | Говь | 8+ | Элсний Хаан (husk) | 1200 EXP · 160 ₮ |
| Баавгайн Үүр `baavgain_uur` | Хангай | 14+ | Ойн Эзэн (bear) | 2600 EXP · 260 ₮ |
| Мөсөн Оргил `mosun_orgil` | Алтай | 22+ | Мөсөн Хаан (stray archer) | 4800 EXP · 420 ₮ |

## First five minutes (designed beat sheet)

| Time | Beat |
|---|---|
| 0:00–0:30 | Cinematic entry (title, ambience, framing of the world) |
| 0:30–1:00 | Class selection (the polished selector) |
| 1:00–2:00 | Starter-skill demonstration (safe sandbox) |
| 2:00–3:00 | First quest |
| 3:00–4:00 | First elite mob |
| 4:00–5:00 | First meaningful loot drop |
| ~5:00 | Tease of a mysterious server-wide unique relic |

## First six hours (retention funnel)

| Window | Content |
|---|---|
| 0–30 min | Tutorial + first loot |
| 30–60 min | First skill unlock + first dungeon |
| 1–2 h | Class specialization + better gear |
| 2–3 h | Clan/social content + world events |
| 3–4 h | Legendary quest chain |
| 4–5 h | Advanced dungeon |
| 5–6 h | Mythic boss + long-term goal |

No gambling or predatory systems anywhere in this funnel.

## Monetization (cosmetics only)

Allowed: cosmetics, particles, titles, emotes, mount **skins**, UI themes,
visual pets, a cosmetic battle pass. **Never** paid competitive advantage, loot
boxes, or gambling. Monetization must not touch power, drop rates, or
progression speed.

## Analytics

Tracked via the `AnalyticsSink` interface (extensible toward a web dashboard):
first login, class selection, first mob kill, first item, first rare item, first
death, first dungeon, first boss; 15-/30-minute and 1-hour retention; session
duration; logout location; class distribution; item economy; death rate; boss
participation. Funnel-critical keys are centralized in `AnalyticsEventType`.
