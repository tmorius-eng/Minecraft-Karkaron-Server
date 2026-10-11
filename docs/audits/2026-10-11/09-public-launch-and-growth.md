# 09: Public launch and community growth

- **Audited commit:** `main` @ `50d5138`.
- **Evidence:** competitor and market facts come only from WebSearch result snippets. Each is cited with its URL and the date retrieved, 2026-10-11. Direct page fetches failed; the exact errors are in `11`.
- **Not:** a market forecast, nor legal advice. No player count, market share or growth outcome is claimed.

## Verdict

**SÜLD has a real, explainable niche.**
- Search found **no Mongolian-language Minecraft RPG/MMO server**; the Mongolian servers that appear are small survival/SMP servers.
- The content depth (42 chapters, 10 dungeons, 40 areas, 16 historic sites) is unusual for a project this young.
- The big incumbents (Wynncraft, Hypixel) are years ahead on content and polish, and are not in Mongolian.

**It is not launch-ready:**
- nothing has been deployed;
- no human has played it;
- the first five minutes have mechanical blockers (`03`);
- culturally sensitive names are on day-one content (`06`);
- 50-player capacity is unproven (`05`);
- the docs contradict the game in 22 places (table below).

**The path is a closed alpha** of 8–12, then 25–40 invited players, with the entry criteria in §3.3. A public beta comes only after the release gates in `10`.

**Differentiator, in one sentence** (for Mongolian players to repeat):
> «Монгол хэлээр, Монголчуудад зориулсан анхны Minecraft MMORPG: Хархорумаас гарч талын бодит түүхэн газруудаар аялж, ангиа сонгоод, нэг л ширхэг Хөх Сүлдний төлөө хатуу PvE түүхийг туулна.»

## Summary: confirmed contradictions

| # | Topic | Doc says | Code / data says | Status |
|---|---|---|---|---|
| C1 | Story length | 18 chapters: `README.md:25`, `GAME_DESIGN.md:86`, `PROJECT_STATE.md:10-11`, tip `TipsService.java:22` («18 бүлэг, 4 агуй») | 42 chapters (`content/quests.json`: 42 `quest.*` entries; `about` line 3: Act I 1–18, Act II 19–42); `docs/PROGRESSION_V2.md:13,30` agrees with the code | contradicts |
| C2 | Dungeons | 4 dungeons, "wave arenas that open where the party stands": `README.md:25`, `GAME_DESIGN.md:121-132` | 10 dungeons (`content/dungeons.json`), each with a world gate `site`, run in the void world `suld_halls` (`suld-plugin/.../dungeon/DungeonHalls.java`); ladder in `suld-api/.../balance/DungeonLadder.java:31+` | contradicts |
| C3 | Dungeon numbers | "2+ / 8+ / 14+ / 22+", party "2+" (`GAME_DESIGN.md:129-132`) | `minLevel` 3 / 9 / 15 / 22, `minParty` 1 (`dungeons.json`) | contradicts |
| C4 | Weapon tiers | "four level tiers" (`README.md:21`) | 6 tiers at levels 1/12/24/36/48/60 (`suld-api/.../classgear/WeaponTiers.java:10-12`) | contradicts |
| C5 | Spells | "20 spells" (`README.md:22`) | 25 (`suld-api/.../skill/Spell.java:17-46`, 5 per class) | contradicts |
| C6 | Wild regions | "four wild regions" (`README.md:24`) | 8 wild regions + the city (`content/world.json` `regions`: 9) | contradicts |
| C7 | Level curve | `round(100·L^1.75)` (`docs/PROGRESSION_EXPLOIT_AUDIT.md:16`) | `CURVE_BASE = 242, CURVE_EXP = 2.2` (`suld-api/.../balance/Balance.java:16`); `config.yml:21-26` (curve 242/2.2, legacy 100/1.75) | audit stale (historical) |
| C8 | Balance status | "Nothing of the proposed balance is in the game yet" (`PROJECT_STATE.md:19`) | "The proposed balance … is now the live game" (`docs/PROGRESSION_V2.md:3`); code in `suld-api/.../balance/*` | contradicts |
| C9 | Resource pack required | "`resource-pack.required` stays `false` by default" (`DEPLOYMENT.md:230`) | `required: true` (`suld-plugin/src/main/resources/config.yml:93`) | contradicts |
| C10 | Historical claims | "makes no historical claims" (`README.md:14-15`) | 6 NPC lines marked «түүхэн баримт» (`NpcDialogue.java:51,61,67,73,79,85`); 6 quest «бодит» lines; 10 site plates «Түүхэн газар (VERIFIED)» (`HistoricSiteService.java:289`) | contradicts |
| C11 | Naadam | minigames removed 2026-10-09 (`PROJECT_STATE.md:43-44`, commit `8c61d43`) | cosmetic tag «Наадамч» still sold (`suld-api/.../style/CosmeticCatalog.java:62`) | leftover |
| C12 | Resurrection / soul shards | "resurrection quest hooks, soul-shard hooks — later phases" (`GAME_DESIGN.md:68-69`; `ROADMAP.md:51`) | no matching code (grep `soul.?shard\|resurrection` → only a comment, `DeathSettings.java:7`) | missing (declared future) |
| C13 | Battle pass | "a cosmetic battle pass" (`GAME_DESIGN.md:163`, `ROADMAP.md:70`) | none; cosmetic categories are TAG, NAME_COLOR, CHAT_COLOR, JOIN_MESSAGE, EMOJI, AURA, TRAIL, KILL_EFFECT (`Cosmetic.java:22-30`) | missing |
| C14 | Emotes, pets, mount skins, UI themes | allowed monetisation (`GAME_DESIGN.md:162-163`) | not in the categories above | missing |
| C15 | Achievements | Phase 9 (`ROADMAP.md:59`); tables `suld_achievements` (`DATABASE.md:93-94`) | no code (grep `achievement` in `src/main` → 0); `docs/SKILL_TREE_ARCHITECTURE.md:59-60` agrees | missing |
| C16 | Test counts | "215 tests" (`README.md:19`); "289 api + 53 plugin + 12 simulator" (`PROJECT_STATE.md:7`) | `@Test`: 381 (api) + 99 (plugin) + 13 (simTest) = **493** | stale |
| C17 | CI | "Intended for local use and CI (see .github/workflows)" (`tools/validation/run_all.py:3`) | no `.github/` directory | missing |
| C18 | Death loss | tip «Үхвэл юмныхаа хагасыг алдана» (you lose half) (`TipsService.java:27`) | `loot-loss-fraction: 0.25` (`config.yml:59`) | contradicts |
| C19 | "Nothing for real money" | `CosmeticCatalog.java:11` "nothing is sold for real money" | credit store with ₮ prices (`config.yml:191-197`); Rare+ cosmetics credit-only (`coin-max-rarity: COMMON`, `config.yml:297`; commit `70a1fdc`) | contradicts (stale comment) |
| C20 | ROADMAP status | only Phase 1 ✅ (`ROADMAP.md:7`) | phases 2–8, 10 largely built (PROJECT_STATE, code) | stale |
| C21 | PROJECT_STATE date | "Last updated: 2026-10-07" (`PROJECT_STATE.md:6`) | the same file records 2026-10-09 events (`:43`) | stale |
| C22 | Analytics | 15/30/60-min retention, first death, item economy tracked (`GAME_DESIGN.md:169-173`) | constants exist (`AnalyticsEventType.java:21,25-27,33-35`) but are never emitted (see §3.4) | partial |

---

## 1. Advertised features versus code

Status values: implemented / partial / missing / contradicts.
- "Implemented" means the code exists and is wired in.
- It does **not** mean it was verified in a real client. The project marks nearly everything MANUAL_QA_REQUIRED.

| Feature | Doc claim (file:line) | Code reality (file:line) | Status |
|---|---|---|---|
| Five classes, one-time choice | `README.md:21`, `GAME_DESIGN.md:15-36` | `suld-api/.../clazz/PlayerClass.java:18-33`; `gui/ClassSelectionGui.java` | implemented |
| Class weapon tiers | "four level tiers" `README.md:21` | 6 tiers, `WeaponTiers.java:12` | contradicts |
| Click-combo spells | "20 spells" `README.md:22` | 25, `Spell.java:17-46`; 5th at 50 (`PROGRESSION_V2.md:42`) | contradicts (count) |
| Class resources | `README.md:22` | `PlayerClass.java:21-33`; HUD bars `hud.json` fill_res_* | implemented (see LANG-001 in `06`: «Хил») |
| Level curve | 100·L^1.75 (audit); 242·L^2.2 (`PROGRESSION_V2.md:29`) | `Balance.java:16`, `config.yml:21-26` | implemented (v2); audit stale |
| Progression v2 balance | "not in the game" `PROJECT_STATE.md:19`; "live" `PROGRESSION_V2.md:3` | `suld-api/.../balance/` (ExpRules, RestedPool, Ascension, …) | implemented; PROJECT_STATE contradicts |
| Rested EXP, Ascension I–III | `PROGRESSION_V2.md:32,45` | `RestedPool.java`, `Ascension.java`, `gui/AscensionMenu.java` | implemented |
| Kharkhorum safe city with NPCs | `README.md:23` | `npc/NpcDialogue.java` (7 personas), `worldbuild/kharkhorum/*` | implemented |
| Wild regions | "four" `README.md:24` | 8 + city, `world.json` | contradicts |
| Named areas, discovery EXP | `PROGRESSION_V2.md:43` | 40 areas in `world.json`; `Rewards.landmarkExp` | implemented |
| Historic sites | `docs/world/HISTORIC_SITES.md` (16) | 16 in `content/sites.json` | implemented |
| Ovoo blessing | `PROJECT_STATE.md:41-42` | `region/OvooService.java`; `OvooCircleTest` | implemented |
| Storyline | 18 chapters (`README.md:25`, `GAME_DESIGN.md:86`) | 42 (`quests.json`) | contradicts |
| Dungeons | 4, "open where the party stands" (`GAME_DESIGN.md:123`) | 10, halls in `suld_halls` (`DungeonHalls.java`) | contradicts |
| Three-phase bosses | `README.md:25` | `dungeons.json` `phases`; `dungeon/brain/KhasarBrain.java`, `ArchetypeBrain.java` | implemented |
| Boss enrage wipe | – | deferred (`PROGRESSION_V2.md:59`) | partial (declared) |
| World events | `README.md:26` | `worldevent/WorldEventService.java`, `content/events.json` | implemented |
| Two world-unique relics | `README.md:26`, `GAME_DESIGN.md:47-59` | `items/relics.json` (2), `relic/RelicService.java`, DB constraints | implemented |
| Clans, parties | `README.md:26` | `clan/`, `party/` packages | implemented |
| Hardcore death: loot/EXP loss, wear, soul, lock | `README.md:27`, `GAME_DESIGN.md:61-82` | `death/DeathService.java`, `DeathLock` (V12) | implemented |
| Resurrection quests, soul shards | `GAME_DESIGN.md:68-69`, `ROADMAP.md:51` | not found | missing |
| GUIs for every command | `README.md:28-29` | 24 distinct `new Menu(` titles; `gui/Menus.java` etc. | implemented |
| Daily reward, daily tasks, `/top`, `/trade`, horses, smith | `README.md:30-32` | `reward/`, `gui/LeaderboardService.java`, `trade/`, `mount/` (`MIN_LEVEL = 5`), `gui/SmithMenu.java` | implemented |
| Damage numbers, mob health bars | `README.md:32` | `combat/CombatFeedback.java`, `mob/MobService.java` | implemented |
| Cosmetics for coins or credits, cosmetics only | `README.md:31-32`, `GAME_DESIGN.md:160-165` | `CosmeticCatalog.java`, `StyleService.java:250-300`; credits buy cosmetics only (`Menus.java:792-793`) | implemented; comment at `CosmeticCatalog.java:11` stale |
| Credit store payments | `README.md:29` ("credit store") | delivery only by console `/credits give` (`MenuCommands.java:58-95`); `store-url: ''` → «Тун удахгүй»; payment provider NOT_IMPLEMENTED (`docs/PAYMENT_ARCHITECTURE.md:3`) | partial |
| Paid instant revive | design only (`docs/INSTANT_REVIVE.md:3`) | none | not built (correctly gated) |
| Cosmetic battle pass, emotes, pets, mount skins, UI themes | `GAME_DESIGN.md:162-163`, `ROADMAP.md:69-70` | none | missing |
| Achievements | `ROADMAP.md:59`, `DATABASE.md:93-94` | none | missing |
| Web/API dashboard | `ROADMAP.md:68`, `GAME_DESIGN.md:169` | none; analytics only via `JdbcAnalyticsSink` / `LoggingAnalyticsSink` | missing |
| First-5-minute beat sheet: cinematic, starter-skill sandbox, first elite, relic tease | `GAME_DESIGN.md:134-144` | 7-step tutorial (`suld-api/.../onboarding/TutorialProgress.java:15-22`); welcome screen for returning players (`MenuListener.java:150`); no sandbox or cinematic found | partial |
| Analytics funnel | `GAME_DESIGN.md:167-173` | see §3.4 | partial |
| Content as data, override, validate | `docs/CONTENT_DATA.md:1-40` | `content/Content.java`, `SuldCommand.java:44` (`/suld content`), `ContentLoader.java` | implemented |
| Resource pack required | `DEPLOYMENT.md:230` false | `config.yml:93` true | contradicts |
| Verified Microsoft accounts only | `README.md:33` | `auth/AuthenticationService.java`; deploy refuses `ONLINE_MODE≠true` (explore notes) | implemented (no genuine MS join verified, per PROJECT_STATE) |
| Historical stance | "no historical claims" `README.md:14-15` | see C10 | contradicts |
| Naadam | removed (`PROJECT_STATE.md:43`) | «Наадамч» tag (`CosmeticCatalog.java:62`) | leftover |
| Test suite size | 215 / 289+53+12 | 493 `@Test` | stale |
| CI | `run_all.py:3` | no `.github/` | missing |

---

## 2. UX and visual review (static; no client)

Visual polish and functional UI are kept apart below. Every item is **static review only**. GUI scale, legibility and layout need the real-client pass in `docs/HUD_MANUAL_QA.md`.

### 2.1 Is there a design system?

**What is consistent (functional UI):**
- **Menu primitives.** All chest menus go through `gui/Menu.java`: title, painted background glyph, items, invisible multi-slot buttons, `Menu.kv` key-value lore rows.
- **Text style rule.** `Menus.java:35`: "All text is bold white with coloured accents".
- **Rarity colours.** One source, `suld-api/.../item/ItemRarity.java:24-31`: `#9d9d9d`, `#1eff00`, `#0070dd`, `#a335ee`, `#ff8000`, `#e6cc80`, `#ff4040`, `#00ffd0`.
  - The first six are the familiar World of Warcraft item-quality palette.
  - That makes them instantly readable, but not distinctive. Mongolian rarity names have a separate problem (`06`, LANG-007).
- **Rank colours.** Hex per rank (`Rank.java:14-21`) and badge glyphs.
- **Colour tokens.** The material palette and class accents are in `docs/ASSET_STYLE_GUIDE.md` §2.

**What is inconsistent (visual polish):**
- **Two art styles share one screen family.**
  - Owner or AI paintings: `gui/main.png`, `welcome.png`, `cosmetics.png`, `questbook*.png`, `dungeons.png`.
  - Procedural pixel art: the HUD and badges from `tools/pack/gen_hud.py` and `gen_ui.py`.
  - Within the cosmetics screen, vanilla-looking Steve renders sit inside an HD painted frame (`06`, VIS-006).
- **No shared layout grid** in the menu code. Each `Menus.*` method hand-places slots.
- **Language policy is mixed.** Titles are bilingual ("Ур чадвар · Skills"); some lines are Mongolian-only (`06`, LANG-009).

### 2.2 HUD inventory (functional)

From `suld-plugin/.../hud/HudComposer.java:1-60`, `HudService.java:222-259` and `resourcepack/assets/suld/font/hud.json`. The font has 78 providers: 77 bitmap and 1 space.

| # | Element | Notes |
|---|---|---|
| 1 | HP bar, with a damage-chip trail and a low-HP pulse | row A |
| 2 | Class resource bar (5 colour variants) | row A |
| 3 | Food / mount / air bar | row B |
| 4 | EXP bar | row B |
| 5 | Level diamond (custom 7 px digits) | centre |
| 6 | Absorption overlay | row A |
| 7 | Spell slots I–V + ultimate (ready, cooldown, locked, no-resource states) | above the panel |
| 8 | Status and buff icons, up to 6 (`MAX_BUFFS`) from 21 icon types | above the panel |
| 9 | Text line: status chips (class · ₮ · ◆ points · ⚔ gear score) / notices / combo | action-bar height |
| 10 | Target frame (normal / elite / boss variants) | boss-bar canvas |

Other surfaces that can be on screen at the same time:
- **Sidebar**, about 8–12 rows: Анги, Цол, Түвшин, Зэрэг, Чадвар, Зоос, **Кредит**, quest lines, danger band.
- **TAB header** with the logo.
- **Other boss bars**: dungeon, world event, soul timer, quest tracker.
- **Toasts** and titles/banners.
- **Combat feedback**: damage numbers and mob health bars.
- **Rank badges** on name tags and in chat.

**About 10 panel elements and about 10 more surfaces.**

UX findings:

**UX-1 (Medium): too much on screen for a newcomer.**
- About 20 information surfaces can be visible at once.
- The sidebar permanently shows the **credit** balance. That is a monetisation nudge on the main HUD, and an odd first impression in a "cosmetics only" model.
- Recommendation: hide the sidebar during the tutorial; show credits only in the store.

**UX-2 (Medium): legibility of the custom pixel fonts.**
- **Digits.** HUD digits are a 3×5-px design (`gen_hud.py:336-337`, `DIGITS3`) at height 6 (advance 5 px; `HudGlyphs.java:138-142`).
- **Text line.** A custom 8-px **uppercase-only** font (`HudGlyphs.LINE`, `HudGlyphs.java:143`) covers А–Я, Ө, Ү and Latin, but not Щ or Ъ.
- **At GUI scale 2**, the digits render about 6×10 screen px, which is readable.
- **At GUI scale 1**, they render about 3×5 px, which is marginal.
- **At scale 4**, the ~256-px-wide panel canvas plus side bars may crowd small windows.
- `docs/HUD_MANUAL_QA.md` rows 1 and 4 (scale 1–4, crispness) are still open. `audit/hud-status.json` marks the GUI scales MANUAL_QA_REQUIRED.
- Recommendation: run the QA table at 1366×768 and 1920×1080, at scales 2, 3, 4 and Auto.

**UX-3 (Medium, functional): players without the pack.**
- `config.yml:93` has `required: true`.
  - On a required pack, the vanilla client disconnects a player who declines. That is my understanding of client behaviour; it is UNVERIFIED-IN-ENVIRONMENT. Paper and Minecraft docs, or a client test, would settle it.
  - A player who presses "Decline" is therefore lost at the first screen. This makes `resource_pack_ready` a funnel-critical event (§3.4).
- If a non-loaded state does reach the server:
  - **HUD.** It falls back to a plain action bar (`HudPanel.java:240`, `HudService.java:122`).
  - **Chat badges.** They have a fallback too (`ChatListener.java:41`).
  - **Menus.** `gui/Menu.java:46-58` does **not** check pack status. Titles carry private-use glyphs and negative-space shifts, and buttons are invisible items with model `suld:gui_blank` (`Menu.java:108-113`). Without the pack, players would most likely see missing-glyph boxes and missing-model items. The main menu has no title text at all, because the title is painted (`ASSET_STYLE_GUIDE.md`, "Сүлд Цэс").
  - Recommendation: give the menu a no-pack fallback (plain items and titles), or keep `required: true` and fix `DEPLOYMENT.md:230`.
- **Bedrock players.** Geyser and floodgate are in the core plugin profile (`deploy/plugins/plugins.json`). Bedrock clients do not receive Java resource packs, so the HUD, menus and painted GUIs need a Bedrock path or an explicit "Java only". UNVERIFIED-IN-ENVIRONMENT; test with a Bedrock client.

**UX-4 (Low, functional): one currency symbol for two currencies.**
- In-game coins are shown as **₮** (`HudService.java:238`, `Menus.java:461`).
- Real-money credit packages are priced in **₮** too («₮5,000», `config.yml:193-197`).
- The tögrög sign for a game currency next to real tögrög prices invites confusion.
- Recommendation: a coin glyph or «зоос» for in-game coins.

**UX-5 (Low): global reskin of vanilla screens.**
- Every vanilla chest, barrel, ender chest and the survival inventory are reskinned (`ASSET_STYLE_GUIDE.md`, "Every chest screen", commit `50d5138`).
- This is strong identity. It also changes vanilla readability, e.g. gold labels on navy.
- The decor glyph uses lang-key injection that only works in en_us/en_gb/mn_mn/ru_ru (`ASSET_STYLE_GUIDE.md` "survival inventory").
- Needs the client QA listed there: scales 3–4, recipe book open, potion list.

**UX-6 (Low): tutorial text errors in the first 5 minutes.**
- See `06`, LANG-002 and LANG-003 («Хүдэлмэрчин NPC», «Байгаа түгжих»).
- These are the first instructions a player reads.

### 2.3 HD painted GUIs versus functional GUIs

**HD painted art** (the owner's paintings or Gemini cards). These are recent commits: `567f984`, `a1d50ae`, `820b00f`, `50d5138`, `fc4c740`, `3e4cc9d`, `4b3b86a`, all 2026-10-11.

| Screen | Glyph / texture | Source |
|---|---|---|
| Welcome window | `Glyphs.GUI_WELCOME` → `font/gui/welcome.png` | owner painting `assets/art/source/gui_welcome.webp` |
| Main menu «Сүлд Цэс» | `GUI_MAIN` → `main.png` (title and labels painted) | `gui_main.webp` |
| Cosmetics (8 cards) | `GUI_COSMETICS` | `card_*.png` (Gemini, `tools/art/prompts.json`) |
| Dungeon ladder | `GUI_DUNGEONS` | owner art (`fc4c740`) |
| Quest map, quest book (3 tabs) | `GUI_QUESTS`, `GUI_QUESTBOOK` | owner art (`567f984`) |
| Skill map | `GUI_SKILLMAP` | `skilltree_bg.png` |
| Generic list frame | `GUI_FRAME` | CardKit pieces |
| Survival inventory (E) | vanilla `inventory.png` override + decor glyph | `gui_inventory.webp` |
| Every chest screen | `generic_54.png` override | `hd_art.chest` |
| Tooltip frame, badges | `567f984` | owner art |
| Server icon, pack icon, TAB logo | `server-icon.png`, `pack.png`, `font/logo.png` | `server_icon_emblem.jpg` |

**Functional, item-grid menus.** Since `50d5138` these sit on the HD navy chest background, but have no painting of their own:
- shop, sell, smith (repair and class armour)
- daily reward, daily tasks
- rank-up, level rewards, credit store
- help, tutorial, skills
- Тэнгэрийн Зэрэг (ascension), leaderboards (Шилдэг), badges, armour
- respec, confirm, travel (Өртөө), class selection (`ClassSelectionGui`)

**Visual-polish findings** are in `06`:
- VIS-001: pagoda towers.
- VIS-002: blue imperial banners.
- VIS-004: generic emblem.
- VIS-006: raw AI cards, Steve and a pumpkin.

A further polish note: painted text («ДҮР», «ЭРЭЛ», …) cannot be localised or corrected without repainting. The owner's «ЭРДЭН» label already had to be painted over («ЭРЭЛ»; `ASSET_STYLE_GUIDE.md`).
- Recommendation: draw labels as font text over the art.

---

## 3. Market, positioning and launch plan

### 3.1 Competitor facts (WebSearch snippets only; marketing claims labelled)

| Server | Fact | Source |
|---|---|---|
| Wynncraft | Free-to-play MMORPG. Five classes (Warrior, Archer, Mage, Shaman, Assassin). Quests carry level requirements. | per WebSearch result snippet, https://wynncraft.wiki.gg/wiki/Wynncraft, retrieved 2026-10-11 |
| Wynncraft | "Over 100 unique quests". Developers call it "the largest MMORPG in Minecraft" (**marketing claim**). A September 2025 listing showed 1,823 players online. | per WebSearch result snippet, https://ggservers.com/blog/top-10-best-minecraft-servers-of-2025/ and https://minecraft-server-list.com/server/230071/, retrieved 2026-10-11 |
| Wynncraft | "Fruma" expansion announced for March 2026 as a "seven-year-in-the-making" update: a 13-part storyline, level cap 120, 91 new abilities, Items 2.0. | per WebSearch result snippet, https://massivelyop.com/tag/fruma/, retrieved 2026-10-11 |
| Wynncraft | Peak of 10,716 concurrent players, February 2026, after the Fruma trailer. | per WebSearch result snippet, https://en.wikipedia.org/wiki/Wynncraft, retrieved 2026-10-11 |
| Hypixel (SkyBlock) | One listing showed 36,147 online (undated check). Sportskeeda says "over 45,000" (undated). SkyBlock patch notes 0.25–0.26.1 come from a user sandbox wiki page, **not official**. | per WebSearch result snippet, https://minecraft.how/server/7, https://www.sportskeeda.com/minecraft/minecraft-hypixel-server-how-join-server-ip-members, https://hypixelskyblock.minecraft.wiki/w/User:Joker876/sandbox/Main_Page, retrieved 2026-10-11 |
| Origin Realms | Free-to-play Java survival server with custom mobs, blocks, biomes, bosses and a Ruby currency. Run by Piston Solutions. Community modpacks exist for its resource pack. | per WebSearch result snippet, https://originrealms.wiki.gg/wiki/Origin_Realms and https://www.modpackindex.com/modpack/57949/origin-realms-advanced, retrieved 2026-10-11 |
| MCC Island (Noxcrew) | Public counterpart of MC Championship, launched summer 2023, free-to-play, Java only. The beta had a paid "fast pass" queue. | per WebSearch result snippet, https://merch.noxcrew.com/pages/about and https://sportskeeda.com/minecraft/how-get-minecraft-championship-island-server-beta, retrieved 2026-10-11 |
| Mongolian servers | MongolVibes: survival for Mongolian players, events every two weeks, registered July 2023, website mnvibes.org (**self-description**). HuushuurSMP: "players in mongolia get 5ms average" (**self-description**), 37 online when checked. Also HarvestMC Mongolia, YuKeGaSMP MN, ShawtyMC Mongolia; several listed offline. | per WebSearch result snippet, https://minecraft-server-list.com/server/516035/, https://www.minecraftiplist.com/server-tags/Mongolian, https://minecraft-mp.com/server-s359651, retrieved 2026-10-11 |
| Mongolian-language search | «майнкрафт сервер Монгол» and «Монгол майнкрафт сервер тоглогчид discord» returned no clearly Mongolian server or Discord. The results were Russian/CIS communities and a Kyrgyz "tribes" anarchy server (kyrgyzfc.com). | per WebSearch result snippet, https://24.kg/sport/331644_tsifrovoe_kochevnichestvo_vnbspMinecraft_poyavilsya_kyirgyizskiy_plemennoy_mir/ and https://discord.com/servers/minecraft-ru-855114187751424021, retrieved 2026-10-11 |

**Implication:** the Mongolian Minecraft scene visible to search is small survival/SMP servers. No search result showed a Mongolian-language RPG/MMO server. That is an opening for SÜLD, though the search is thin and Facebook groups are not indexed this way (UNVERIFIED).

### 3.2 Comparison

SÜLD facts come from the repo. Competitor cells only restate §3.1; "no data" means not searched.

| Dimension | SÜLD (repo evidence) | Wynncraft | Origin Realms | MCC Island | Mongolian SMPs |
|---|---|---|---|---|---|
| Onboarding | 7-step tutorial with rewards (`TutorialProgress.java`), NPC dialogs, welcome screen; required pack at join; typos in step text | quests with level gates | no data | no data | vanilla-like |
| Combat feel | click combos, 25 spells, lock-on, dodge, telegraphs (`KhasarBrain`), damage numbers; **not validated by humans** | five classes, abilities (91 more in Fruma) | custom bosses | minigames, not RPG | vanilla PvP |
| Content depth | 42 chapters, 10 dungeons, 40 areas, 16 sites, Ascension I–III, ~207 h to 60 (simulated, `PROGRESSION_V2.md:12`) | 100+ quests; level cap 120 (Fruma) | custom biomes and mobs | four launch games | economy, events |
| Originality / positioning | Mongolian-history-inspired hardcore PvE, in Mongolian; strong research base | fantasy world, years of content | custom survival | polish benchmark | local community |
| Social | clans, parties, chat channels, `/trade`, leaderboards, world events | (guilds per mein-mmo title; not detailed) | no data | competitive minigames | Discord, events |
| Mongolian localisation | full Mongolian UI (some English mixed in), MNT pricing, `Asia/Ulaanbaatar` daily reset (`config.yml:178`) | none found | none found | none found | Mongolian community, likely English UI (UNVERIFIED) |
| Update cadence | 151 commits in 6 days (pre-launch burst); no deploy yet (`DEPLOYMENT.md:3-4`) | Fruma took ~7 years | no data | no data | biweekly events (MongolVibes, self-reported) |
| Shareability | historic sites, relic broadcasts, world-first items, Mongolian identity; no clips or replay tooling | large creator base (not searched) | no data | event-origin fame | small |

**Candidate differentiator (one sentence):** "SÜLD is the first Minecraft MMORPG made in Mongolian for Mongolian players, where you ride from Kharkhorum to real historic sites of the steppe, with history labelled honestly, and play a hardcore class-based PvE story built around one server-unique banner."

### 3.3 Closed alpha plan

**Entry criteria. All must be done before the first invite:**
1. **Renames and text fixes:**
   - HIST-024 (Khasar boss), HIST-023 (Burkhan dungeon), HIST-025 (Chinggis items), HIST-002 (Khödöö Aral) renamed or corrected.
   - LANG-001/002/003 fixed. These are the first things players see.
2. **One human real-client pass** of `docs/HUD_MANUAL_QA.md` (16 rows) at GUI scales 2–4.
3. **Pack-decline behaviour** decided and tested: required plus a fallback, or not required plus a menu fallback (UX-3). `DEPLOYMENT.md:230` fixed.
4. **Hosting on target hardware** with a 2 h soak at the alpha's size.
   - REPORTED (`docs/perf/WORLD_50.md`): 25 players fell to about TPS 13.
   - MEASURED in this audit, on a shared 4-vCPU box with pre-generation paused: 25 bots held TPS 19.2 (p95 31 ms), but with stalls up to 7.9 s.
   - See `05`. Neither figure is the target host.
5. **Backups copied off-host**, and one restore drill on the current schema. The last restore test was at schema v2; the schema is now V15.
6. **Funnel events** from §3.4 emitted, and readable from `suld_analytics_events` / `suld_sessions`.
7. **Discord server** with #bug-report, #feedback and #announcements; `branding.discord` set (it is empty in `config.yml:186`).
8. **Moderation:** LibertyBans, CoreProtect and GrimAC configured; `ChatGuard` on; two staff with LuckPerms badges.

**Cohort and duration:**

| Wave | Size | Length | Who | Goal |
|---|---|---|---|---|
| A, friends | 8–12 | 1 week | trusted, mixed skill, PC Java | crash and blocker sweep, first-hour flow |
| B, closed | 25–40 invited, at most 20 concurrent | 2 weeks | Mongolian players aged 13+, recruited from Mongolian SMP Discords and school/uni clubs; at least 30 % never played an MMO | funnel, retention D1/D7, class balance by feel, culture review |
| C, open beta | uncapped | after C1–C22 docs fixes and the 25-player perf gate | – | – |

Exit from B:
- **Funnel:** at least 70 % of `join_success` reach `first_combat`, and at least 50 % reach `five_minute_active`.
- **Retention:** D1 retention at least 40 %.
- **Blockers:** no open P0/P1 bugs.
- **Culture:** no unresolved culture complaint marked severe by the native reviewers.

### 3.4 Funnel events mapped to `AnalyticsEventType`

`suld-api/src/main/java/mn/suld/api/analytics/AnalyticsEventType.java`, with emit sites in `suld-plugin/src/main`:

| Funnel step | Existing constant / event | Emitted where | Status |
|---|---|---|---|
| join_attempt | – (pre-login is in `AuthenticationService`) | – | **missing.** Add at `AsyncPlayerPreLoginEvent`, with an outcome (allowed / denied, reason). |
| join_success | `SESSION_START` (every join), `FIRST_LOGIN` (first only) | `PlayerLifecycleListener.java:48`; `AuthenticationService.java:158` | exists |
| resource_pack_ready | – (status kept only in memory: `ResourcePackService.java:47,169-182`) | – | **missing.** Emit `resource_pack_status` with ACCEPTED / DECLINED / FAILED / SUCCESSFULLY_LOADED and a time-to-load. |
| class_selected | `CLASS_SELECTED` | `ClassSelectionGui.java:178` | exists |
| first_action | – | – | **missing.** Suggest tutorial step HOLD_WEAPON or OPEN_SKILLS (`TutorialProgress.java:17-18`); emit `tutorial_step` with the step index. |
| first_objective | – (quest completion and tutorial steps are not emitted) | – | **missing.** Emit `quest_complete` / `tutorial_step`. |
| first_combat | `FIRST_MOB_KILL` (a kill, not the first hit) | `CombatListener.java:285-286` | partial. Add `first_hit` or accept the kill. |
| first_reward | `FIRST_ITEM`, `FIRST_RARE_ITEM` | `CombatListener.java:367-371`; `DungeonService.java:574` | exists (loot); quest rewards not covered |
| five_minute_active | `RETENTION_15M/30M/1H` defined but **never emitted**; `ActivityTracker` (api) classifies active time | – | **missing.** Emit `active_5m` from the active-playtime tracker, not wall-clock time. |

Other gaps:
- **Defined but never emitted:** `FIRST_DEATH` (death uses the literal `"player_death"`, `DeathService.java:252`), `LEVEL_UP`, `EXP_GAIN`, `ITEM_ECONOMY`, `DEATH`, `RETENTION_*`.
- **`FIRST_BOSS`.** It appears to be emitted on every boss clear, not only the first (`DungeonService.java:580`). No first-clear guard is visible in the completion block at `:535-580`. Rename it to `boss_clear`, or guard it.

### 3.5 Five-person usability test script (Mongolian players)

**Setup:**
- Five players, recruited to cover these profiles:
  1. a teen who plays vanilla only;
  2. a Hypixel or Wynncraft player;
  3. a casual player;
  4. a Mongolian history enthusiast;
  5. a laptop player at 1366×768.
- Each session is 60 minutes, think-aloud, with screen and voice recorded (with consent; a parent's for minors).
- The moderator speaks Mongolian.
- Fresh account, default GUI scale.
- Run the funnel events in §3.4 at the same time.

**Tasks.** No hints, unless the player is stuck for 2 minutes.

| # | Task (spoken in Mongolian) | Success criterion |
|---|---|---|
| 1 | "Join the server and get ready to play." | pack accepted; reaches the class NPC within 3 minutes |
| 2 | "Choose the class that suits you, and tell me why." | class chosen; can explain the role in their own words |
| 3 | "Find out what your next goal is." | opens `/quest` or reads the tracker within 2 minutes |
| 4 | "Use one ability on an enemy." | casts a combo within 5 minutes of leaving the gate |
| 5 | "Lock on to an enemy and fight it." | uses Q lock-on, read from the tutorial text |
| 6 | "Tell me your health, level, coins and how many skill points you have." | reads all four from the HUD/sidebar correctly |
| 7 | "Spend a skill point." | opens `/skills`, buys a node |
| 8 | "Find the first dungeon's gate." | follows the compass/tracker to the gate |
| 9 | "Visit a historic place and tell me what it says." | reads the site text; can say whether it is history or legend |
| 10 | "Change your name colour or tag." | finds `/cosmetics`; understands coins vs credits |

**Interview questions** (asked after the session, in Mongolian):
1. What was confusing in the first 5 minutes?
2. Which words or menu labels seemed wrong or odd to you? Show the «Хил», «Ховор», «Зэрэг» and «Цол» screens.
3. Did anything about history, Chinggis, Khasar, Burkhan Khaldun or the ovoo feel disrespectful? Why?
4. Did the city and the menus feel Mongolian, or like another country? Show `welcome.png` and `main.png`.
5. Is the HUD too busy? What would you remove?
6. Would you invite a friend? What would you tell them SÜLD is?
7. What would you pay for, if anything? Is anything sold that should not be sold?
8. Rate 1–5: fun, clarity, Mongolian feel, performance (lag).

**Success criteria for the test:**
- At least 4 of 5 complete tasks 1–4 without help.
- At least 3 of 5 complete 6 and 9.
- No player reports a culturally offensive element that the team agrees is valid.
- Average "clarity" of at least 3.5.

### 3.6 Feedback and bug-triage loop

- **Intake channels:**
  - Discord #bug-report, using a form (template: what, where `/suldworld` coords, when, screenshot, GUI scale).
  - An in-game `/report <text>` command. None exists today; add one that writes to the audit log.
  - Analytics: funnel drop-offs.
- **Triage**, daily, 15 minutes, by the owner or developer:

  | Priority | Meaning | Response target |
  |---|---|---|
  | P0 | crash, data loss, dupe | same day |
  | P1 | blocks progress | within 2 days |
  | P2 | wrong or confusing | weekly batch |
  | P3 | polish | backlog |

  Add the labels `culture` and `language`, each routed to a named native reviewer.
- **Weekly:**
  - Patch notes in Mongolian.
  - A reply in each thread with its status.
  - The funnel chart reviewed.
  - The top 3 drop-off points fixed first.

### 3.7 Moderation and support plan

- **Staff:** at least 2 moderators per 20 concurrent players during the alpha, across UB evenings (18:00–24:00 `Asia/Ulaanbaatar`).
- **Tools already in the profile:**
  - LibertyBans (bans/mutes)
  - CoreProtect (rollback)
  - GrimAC plus SÜLD's bridge (anticheat)
  - InventoryRollbackPlus (optional)
  - `ChatGuard` (chat filter)
  - LuckPerms staff badges
  - audit-logged admin commands (`/deathrevive` etc.)
- **Rules:**
  - A rules page in Mongolian: harassment, cheating, real-money trading of items or accounts, impersonating staff.
  - Escalation path: warn → mute → temp-ban → ban, with every action logged.
- **Minors:**
  - The target audience includes teens, so no DMs from staff; keep it to public channels.
  - A Mongolian-language privacy note covering what analytics collects (`docs/ANALYTICS.md`).
- **Support:**
  - Discord #help.
  - FAQ: pack install, Bedrock (UX-3), death lock, credits.
  - A refund policy written down before any payment goes live.

### 3.8 Monetisation and EULA / Usage Guidelines: items for manual review (not legal advice)

**What the code sells:**

| Purchase | Paid with | Effect | Real-money path? | Review flag |
|---|---|---|---|---|
| Credit packages: 100 / 250 / 600 / 1300 / 2800 for ₮5,000–₮100,000 | real MNT (planned) | credits | yes, but delivered by console `/credits give` (`MenuCommands.java:58-95`); provider NOT_IMPLEMENTED | review once a provider is built |
| Cosmetics with a credit price (all shop cosmetics: 1/10 of the coin price, `Cosmetic.java:72-76`); Rare+ are credit-only (`config.yml:297`) | credits | tags, name/chat colours, join messages, emoji, auras, trails, kill effects | yes | cosmetic. Generally allowed per third-party guides (below). Cultural items: `06`, HIST-025/027. |
| Ranks Ард → Хаан (`Rank.java:14-21`) | **in-game coins only** (`StyleService.java:350`) | badge **and mount speed / jump** (`mount/*.java:65-67,129-140`) | no (coins are not sold for credits; no conversion found) | OK while coins are never sold. Flag if a "coin pack" or "rank pack" is ever added. |
| Instant revive | design only | skips the death lock and wound | would be | **gameplay advantage.** The project's own doc says it conflicts with its rules and possibly with the Usage Guidelines (`docs/INSTANT_REVIVE.md:20-28`). Keep it unbuilt, or use the earned-token alternative. |
| Credits balance on the sidebar | – | – | – | UX nudge (UX-1) |

**What the search found on the rules:**
- Per WebSearch result snippet, https://www.gameserverkings.com/knowledge-base/minecraft/monetising-your-server/ and https://ggservers.com/blog/how-to-monetize-your-minecraft-server-legally-and-effectively, retrieved 2026-10-11. These are **third-party guides, not Mojang text**:
  - Cosmetics, and ranks that are cosmetic only, are generally described as allowed.
  - Gameplay advantages for money are described as not allowed.
  - Paid server access and "gray areas" are disputed.
- The official text could not be fetched (`getaddrinfo ENOTFOUND www.minecraft.net`). It is **UNVERIFIED-IN-ENVIRONMENT**.

**The owner must read the current Minecraft Usage Guidelines and the EULA, and confirm:**
1. Credit-only cosmetics with real-money pricing in MNT.
2. Whether a rank that gives mount speed may ever appear in any paid bundle.
3. A ban on instant revive or other paid time-skips.
4. The use of the Minecraft name and visuals: Steve-like art in `cosmetics.png`, and the "Minecraft-style" Gemini prompts in `tools/art/prompts.json`.
5. Any Mongolian consumer-protection or payment rules for minors buying credits.
6. Mongolian rules on commercial use of Chinggis Khaan's name, for the paid «Их Хааны Ач» tag (`06`, HIST-025).

---

## 4. Recommended doc fixes (quick wins)

1. `README.md:19-26`:
   - 493 tests
   - 6 weapon tiers
   - 25 spells
   - 8 wild regions
   - 42 chapters
   - 10-dungeon ladder in halls
   - replace "no historical claims" with "history is labelled in game"
2. `GAME_DESIGN.md:84-132`:
   - replace the 18-chapter and 4-dungeon tables with links to `quests.json` and `DUNGEON_LADDER.md`
   - mark battle pass, soul shards and resurrection quests as "not built"
3. `PROJECT_STATE.md`:
   - fix line 6 (date)
   - fix line 7 (test counts)
   - fix line 19: replace the "not in the game" line with a pointer to `PROGRESSION_V2.md`
4. `DEPLOYMENT.md:230`: state that `required: true` is the default.
5. `ROADMAP.md`: tick the built phases; mark achievements, dashboard and battle pass as open.
6. `TipsService.java:22,27`: «42 бүлэг, 10 агуй»; «Үхвэл юмныхаа дөрөвний нэгийг алдана».
7. `CosmeticCatalog.java`:
   - line 11: fix the stale comment
   - line 62: remove «Наадамч»
8. `tools/validation/run_all.py:3`: either add a workflow or drop the CI reference.
