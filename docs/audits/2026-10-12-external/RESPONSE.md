# Answer to the external forensic audit (2026-10-12)

The owner received an outside audit (FIND-01 … FIND-10, roadmap, GDD). Every finding was checked against the code on
`07d4a3d`. This file says which claims hold, what changed, and what stays the owner's.

**Товчоор:** 10 дүгнэлтээс 4 нь кодын бодит алдаа байсан (засав), 2 нь дизайны шийдвэр (үхлийн түгжээ 30 мин + сорил,
зөвхөн Java), 1 нь хэмжилт хийгдээгүй байсан (одоо хэмжинэ), 3 нь эзэмшигчийн ажил (зураг, native review, клиент QA,
санал асуулга).

| Finding | Verdict | What changed |
|---|---|---|
| FIND-01 `PlayerDataSaves.now()` → `p.saveData()` on the main thread | **True, by design.** It is the crash-dupe ordering (item file before coins, PERS); removing it would reopen the dupe. Its cost was never measured. | `PerfProbe` `player.save_data` around the call (29e579d). Coalescing per tick was tried and **reverted**: a second save in the same tick may carry newer state, skipping it breaks the order. Measured (M3 leg B, 25 bots): p50 5.1 ms, p95 8.5 ms, 27 calls in 190 s — over the 2 ms line per call but about one call every 7 s, never two in a tick, so coalescing would not help; the cost is the synchronous file write. Re-measure on the VPS before touching the order (`docs/perf/M3_2026-10-12.md`). |
| FIND-02 H2 fetched by Paper `libraries:` → an offline host cannot boot | **True** | H2 shaded into the jar (not relocated, loaded by class name) and dropped from `plugin.yml libraries:` (29e579d). Checked: the jar holds `org/h2/Driver.class`; the dev server boots on `database.type: h2` with `libraries/com/h2database` removed. |
| FIND-03 Geyser/Floodgate shipped, no Bedrock UI | **True** | Owner default: **Java Edition 1.21.11 only**. Both plugins moved to the optional profile; `firewall.sh` opens UDP only when `BEDROCK_PORT` is set (29e579d). A Bedrock UI is built only if players ask. |
| FIND-04 static `CombatListener.spellDamage` | True, but already restored in `finally` | Scoped `CombatListener.asSpell(Runnable)` with a depth counter; both skill call sites use it (29e579d). Behaviour unchanged. |
| FIND-05 death lock up to 24 h | **True** (the owner's approved curve) | Owner default: **keep hardcore, end the multi-hour wait.** Lock capped at **30 min** (`death.lock.max-minutes: 30`, config v7 migrates old configs) and **«Тэнгэрийн сорил»** (`/trial`): a soul fights three spirit wolves for 3 min; a win ends the lock now, the wound stays; a loss returns it to its timer, retry after 5 min. No paid revive. `DeathLockTest`, `DeathTrialTest`, `trialbot.js` 6/6. Side effect: simulated hardcore time to level 60 fell from ~200 to ~146 active hours (`SimulationTest` re-baselined); the owner accepted ~146 h as the target (2026-10-12), the EXP curve stays. |
| FIND-06 online-mode shuts out cracked clients | True as a fact; the «60–70 % cracked» figure has **no source** | Owner default: **keep online-mode.** Ask players first (survey: which client do you use?) before any offline-account bridge — `ROADMAP.md` «Open questions». |
| FIND-07 50 players → TPS 11.8 | True but stale (2026-10-08, shared 4-vCPU bench) | M3 today, 25 bots: TPS 17.7 → 17.9, p95 85 → 84 ms, max 1.5 s → 0.56 s with the tuning (`docs/perf/M3_2026-10-12.md`). The production gate is on the real VPS (`WORLD_50.md`). |
| FIND-08 mailbox dupe when the file cannot be deleted | **True** (narrow) | `Mailbox`: a failed delete falls back to writing an empty list, and the UUID is held in a `stuck` set so the file is never delivered twice this run; a severe log names the file (29e579d). No unit test: the path needs Bukkit; covered by review. |
| FIND-09 pagoda / fleur-de-lis art, no native sign-off | Known (`CULTURAL_REVIEW_P1.md`) | Owner and artist work. |
| FIND-10 no human client QA | Partly stale (G7 owner-attested) | The GUI-scale checklist for the new hub windows is still owed by the owner (`docs/LOCAL_TEST.md` «Цонхны шалгалт»). |

## Stays the owner's

Art replacement and two native reviewers (FIND-09), the client checklist (FIND-10), the player survey (FIND-06), the
VPS capacity run (FIND-07). Decided 2026-10-12: ~146 h to level 60 is accepted (FIND-05).
