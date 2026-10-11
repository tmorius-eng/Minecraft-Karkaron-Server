# Их Хуралдай → Амьд ертөнц → Баатрын Хроник

**Status:** IMPLEMENTED (bots: pending; real client: pending). Schema V17–V19. Config `khuraldai:`, `campaign:`,
`chronicle:`. Content `content/campaigns.json`. Commands `/khuraldai`, `/chronicle`, `/party find|join|recruit|role`.

The signature loop of SÜLD: every week the players **choose** the next campaign, the whole server **works** toward
one goal, the world **changes** when they succeed, and the **chronicle** remembers who did it. One player's action
reaches everyone else and stays in the server's history.

## 1. The week (server zone, `daily.timezone`)

| When (defaults) | What |
|---|---|
| Monday 00:00 | The campaign the last Хуралдай chose starts (`campaign.starts`). Boss bar, banner, horn. |
| all week | Contributions count: kills, dungeon clears, deliveries, discoveries (per the campaign's goal). Each player climbs the bronze / silver / gold tier. |
| Saturday 20:00 | The campaign ends (`campaign.ends`): success or not, the tiers are paid, the world effect applies, the chronicle is written. The Хуралдай opens (`khuraldai.vote-opens`) with 2–3 options for next week. |
| Sunday 20:00 | The vote closes (`khuraldai.vote-closes`). The winner is announced and goes into the chronicle. |
| Monday 00:00 | The winner starts. |

Every phase is derived from the clock (`WeeklyCycle`), never from a timer: a restart at any moment lands in the right
phase, and a boundary that passed while the server was down is performed at start. A cycle is an ISO week,
`2026-W41`. The vote held during week X elects the campaign of week X+1 and is stored under X.

## 2. Campaigns (`content/campaigns.json`)

```json
{"id": "campaign.chonyn_ayan", "name": "Чонын Их Аян", "description": "…",
 "goal": {"type": "KILL_MOBS", "targets": ["mob.goviin_chono", "mob.orkhon_chono"], "target": 3000},
 "tiers": [30, 80, 150], "tierRewards": [{"exp": 400, "coins": 80}, {"exp": 1200, "coins": 200}, {"exp": 3000, "coins": 450}],
 "podium": [1.5, 1.3, 1.2], "clanExpPerUnit": 2, "clanSuccessBonus": 1500,
 "effect": {"type": "UNLOCK_EVENT", "ref": "event.chonyn_dovtolgoo"}, "cooldownCycles": 2}
```

| Field | Rule |
|---|---|
| `goal.type` | `KILL_MOBS` (kills of the listed mobs) · `CLEAR_DUNGEON` (completed runs of the dungeon, one per rewarded participant) · `DELIVER_ITEMS` (items handed in with `/khuraldai deliver`) · `DISCOVER_SITES` (a player's first visit to a listed site) |
| `goal.target` | the server-wide goal in units |
| `tiers` | personal units for bronze / silver / gold, ascending; **gold ≤ 5 % of the target** so a 2–3 session player can reach it (catch-up) |
| `tierRewards` | EXP and coins per tier; the top three contributors get `podium` multipliers; a failed week pays half |
| `clanExpPerUnit`, `clanSuccessBonus` | clan EXP per unit a member brings; a bonus once per clan with a tier-1+ member when the week succeeds |
| `effect` | `UNLOCK_EVENT ref=event id` (joins the automatic world-event rotation) · `PROP ref=prop id, anchor=site id, dx, dz, yaw` (a prop appears by a historic site) · `NPC ref=role id, anchor=city point or "city:x,z"` (an NPC appears in Kharkhorum: `merchant.caravan`, `steward.campaign`) |
| `cooldownCycles` | weeks the campaign rests after winning |

The loader checks every reference (mobs, dungeons, sites, items, events) and the tier rules; a broken file keeps the
bundled one, like every other content file. `CulturalNamingTest` scans the names.

**Ballot:** `CampaignRotation` — the last winner never runs again at once, a winner rests its cooldown, last week's
losers lead the ballot (a lost cause comes back), the rest is shuffled with the cycle id as the seed.
**Tally:** most votes wins; a tie goes to the option that won least recently, then ballot order; below `min-voters`
the rotation's first option is taken and the broadcast says so. **Eligibility:** a class, `min-level`, and a character
at least `min-account-days` old; one ballot per UUID per week, enforced by the primary key.

## 3. Rewards and catch-up

Tier rewards go to online players at resolution; an offline player's tier is recorded and **paid on their next login**
within `reward-claim-days` (0 restores the hardcore rule "online at the payout or nothing"). Souls can watch and vote but
not contribute. Contributions never decay on death. The HUD shows the campaign line; the boss bar shows the server
total and the time left.

## 4. Амьд ертөнц: world effects

A successful campaign writes a **world-state flag** (`suld_world_state`: key `effect:<campaign id>`, value, source
cycle, expiry cycle) and then makes the change live. On every start and every new week `CampaignEffects.restore`
re-reads the flags: live ones are applied again (idempotent), lapsed ones are undone and cleared. `campaign.effect-cycles`
is how many later weeks an effect stays (0 = permanent).

| Effect | Live change | Undone |
|---|---|---|
| `UNLOCK_EVENT` | `WorldEventService.unlock(id)`: the event is drawn beside the wolf raid at automatic starts | `lock(id)` |
| `PROP` | `PropService.placeDynamic`: an item-display prop with its barriers at `site + (dx, dz)` | entities and barriers removed |
| `NPC` | `NpcService.addDynamic`: a mannequin NPC with a role (caravan merchant opens the shop; steward opens `/khuraldai`) | removed |

The city is hand-built now (`kharkhorum-auto-repair: false`), so effects **add entities, never blocks**.

## 5. Баатрын Хроник

`suld_chronicle` is append-only (an admin can `hide`, never delete); `suld_chronicle_records` holds the one first or
record per (kind, subject). A first is an INSERT that succeeds or hits the primary key; a record is replaced only when
better, retried on a lost compare-and-set so the best of concurrent attempts always holds it.

| Kind | Written when | Subject |
|---|---|---|
| `FIRST_DUNGEON_CLEAR` | the first party to clear a dungeon (present, not downed) | dungeon id |
| `FASTEST_DUNGEON_CLEAR` | a clear faster than the record | dungeon id, value = seconds |
| `FIRST_SITE_DISCOVERY` | the first player to stand on a historic site | site id |
| `RELIC_CLAIMED` | a relic claimed at its shrine | relic key |
| `CAMPAIGN_RESULT` | a campaign resolved, with the top three and the best clan | cycle id |
| `KHURALDAI_RESULT` | a vote closed | cycle id |

Every new line is broadcast (`chronicle.broadcast`), logged, and recorded as the `chronicle_entry` analytics event.
`/chronicle` opens the book by kind; `/chronicle mine` («Миний түүх») lists the player's own lines; the profile menu
links both. The Hall of Fame displays in the palace are v1.1 (`hall-entries` is reserved for them).

Pre-existing clears live only in the player file without a time, so the book starts with the alpha reset: "Хроник
эхэлснээс хойш". The V17 marks mirror (`suld_player_marks`) backfills those clears for queries, not for firsts.

## 6. Party finder

`/party recruit [dungeon]` lists the leader's party; `/party find [dungeon]` shows the board (parties still missing a
role first, then by free seats) with a click-to-join line; `/party join <leader>` joins without an invite;
`/party role <tank|healer|dps>` declares what you bring (Баатар defaults to Бамбай, Бөө to Эмч). A full party or one
entering a dungeon leaves the board.

## 7. Threads, restarts, failure

* Repositories return `CompletableFuture` on the single `suld-campaign-io` thread; every callback hops back to the
  main thread (`whenDone`). Menus read cached state only.
* Contributions are buffered on the main thread and flushed every `flush-seconds` (one transaction: per-player
  upserts, a ledger row each, the total added). A hard crash loses at most one flush interval of progress.
* Resolution reads the **database totals**, not the in-memory map, and changes state with a compare-and-set on
  `version`; two servers or a crash-restart cannot resolve a week twice. The effect application is guarded by
  `effect_applied_at`.
* Shutdown flushes the buffer inside the shutdown budget (`SuldServices.close`).

## 8. Commands and permissions

| Command | Who | What |
|---|---|---|
| `/khuraldai` | all | the screen: this week's campaign, your tier, the top ten, the ballot |
| `/khuraldai vote <1-3>` | class, level ≥ `min-level` | one ballot per week |
| `/khuraldai status`, `/khuraldai deliver [n]` | all | text status; hand matching items to a `DELIVER_ITEMS` campaign |
| `/khuraldai open|close|force <id>|resolve|cancel|list` | `suld.admin.campaign` | audited admin actions |
| `/chronicle [mine|dungeon|record|relic|site|khuraldai]`, `/chronicle hide <id>` | all / admin | the book; hide a line |

## 9. Verification

* `./gradlew build`: `WeeklyCycleTest`, `CampaignRunTest`, `VoteAndRotationTest`, `ChronicleTest`, `PartyFinderTest`,
  `InMemoryCampaignRepositoryTest`, `SuldConfigFactoryTest`, `ContentDataTest`, `CulturalNamingTest`;
  `JdbcCampaignRepositoryIT` and `JdbcChronicleRepositoryIT` (H2 always, PostgreSQL with `SULD_TEST_PG_URL`).
* Bot smoke, run 2026-10-11 on a local Paper 1.21.11 + PostgreSQL 16 with the dev offline flag:
  `tools/loadtest/bots/khuraldai-smoke.js` **22/22** (ballot opens and stays open outside the window, a level-1 and a
  day-old character are refused, one vote per player, close with the quorum rule, forced campaign with its boss bar,
  party finder recruit/find/join/role, resolve, chronicle, `/chronicle` and `/chronicle mine` screens, a PvP hit without
  consent does no damage) and `tools/loadtest/bots/campaign-deliver.js` **7/7** (60 deliveries → SUCCEEDED → gold tier
  +450 EXP / +45 coins → `UNLOCK_EVENT` applied and stored in `suld_world_state` → chronicle). A stop/start mid-campaign
  logged `[campaign] restored … 0/60` and `[campaign] world effects: 1 live`.
* Not yet bot-exercised: the kill and dungeon-clear contribution hooks, PROP and NPC effects in the world.
* Real client (owner): `/khuraldai` GUI voting, the Аян card, `/chronicle mine`, the boss bar text at GUI scale 2–4.
