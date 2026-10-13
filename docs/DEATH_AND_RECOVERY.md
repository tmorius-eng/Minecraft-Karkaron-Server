# SÜLD death and recovery

**Status: IMPLEMENTED (bot-verified on Paper 1.21.11 + PostgreSQL; real-client UX is MANUAL_QA_REQUIRED).** Persistent
death rows (V12), the soul beacons (V23), the wound through `Equipment.Wearer.boundFactor`, audited admin commands.
Paid Instant Revive is **deferred** (owner decision); see `docs/INSTANT_REVIVE.md`. There is no paid path, ever.

## Today: the reform of 2026-10-13 (config v8)

The owner's directive: no passive waiting after a death. What a death does now (`config.yml death:`):

| Level | What a death costs | How the player comes back |
|---|---|---|
| **1–10** (no Ascension) | **nothing**: every stack kept, no EXP, no wear, no soul, no wound (owner: «бүгд хамгаалагдана») | respawns as usual |
| **11+** | **10 %** of the progress into the level (never a level), **25 %** of the droppable stacks, wear on kept gear, then **one wound step** (−5 %, at most −15 %) | a **soul without a timer**: «Тэнгэрийн сорил» or «Овооны цацал» (or staff) |

1. The soul **rises beside the ovoo nearest to where it fell** (5 blocks south of the cairn, inside the ritual ring).
   A death in Хархорум or another world keeps the usual respawn; a death in the halls sends the soul to the
   dungeon's gate (DungeonHalls).
2. A pale blue **«Сүнсний Гэрэлт»** beam stands for **10 minutes** where the player fell (the player alone sees
   it); for a death in the halls, which the soul cannot walk back into, it stands at the gate the soul is sent to.
   Reaching it as a soul gives back **70 %** of the EXP the death took, once (a compare-and-set on
   `suld_soul_beacons`; the EXP is paid only after it). The soul's boss bar shows the beam's distance, direction and
   time left. The net EXP cost of a death walked back to is therefore 3 % of the level's progress.
3. The soul comes back by acting, with no wait in between:
   * **«Тэнгэрийн сорил»** (`/trial`, below): three spirit wolves, three minutes, tried again at once after a loss.
     A win also leaves a **«Сүнсний Хэлтэрхий»** in the bag.
   * **«Овооны цацал»** (the game's own rite, INSPIRED by the milk offering; the sunwise rounds are VERIFIED
     custom, `docs/world/OVOO.md`): at any ovoo, walk the three sunwise rounds (2–9 blocks out) and stay by the
     cairn until the ritual has lasted **90 seconds**. Leaving the ring breaks it; it starts over. A soul is never
     blessed by an ovoo (the blessing is for the living).
   * staff: `/deathrevive`.
4. When the soul state ends, the wound step applies. A **«Сүнсний Хэлтэрхий»** (echo shard, bound to its winner,
   7 days) heals **60 active minutes** of it at once (right-click; the spent shard reaches the player file before the
   wound is stored, so a crash can lose the healing but never repeat it).

`death.lock.mode: TIMED` brings back the old real-time lock below (the reference for the simulator); `UNTIL_RECOVERY`
is shipped. Existing timed locks from before v8 still end by their time. The loot loss and the wear are unchanged
by the reform (`death.drop-loot`, `loot-loss-fraction`, `durability-damage-fraction`: the owner may turn them off).

## The timed lock (`death.lock.mode: TIMED`, before 2026-10-13)

The owner decided the lock scales by level. Five curves were simulated with 3 player types over 90 days (C7,
`docs/PROGRESSION_SIMULATION.md` §Death lock curves):

| Curve | L5 | L20 | L40 | L60 | Play time lost (casual / active / hardcore) | Day-30 level, active | Verdict |
|---|---|---|---|---|---|---|---|
| 30 s (today) | 30 s | 30 s | 30 s | 30 s | 0 / 0 / 0 % | 60 | no pressure; deaths ×4 more frequent |
| Linear | 1.7 h | 7.8 h | 15.9 h | 24 h | 5 / 5 / 7 % | 54.5 | punishes new players hardest |
| Step | 5 min | 2 h | 12 h | 24 h | 3 / 3 / 6 % | 54.7 | good, but cliffs at each decade |
| **Geometric** | **7 min** | **31 min** | **3.5 h** | **24 h** | **2.5 / 3.2 / 4.2 %** | **58.1** | **recommended** |
| Log | 9.4 h | 17.6 h | 21.6 h | 24 h | 3 / 7 / 7 % | 51.3 | far too harsh early |

**Chosen: geometric.** It shipped as 5 min × 288^((L−1)/59) (24 h at 60). Since config v7 the top is **30 min**:
lock = 5 min × 6^((L−1)/59), so a top-level death costs half an hour, never a day (the table above is the 2026-10-08
study; the exact values below are the live curve).

* 5 minutes at level 1, 30 minutes at 60 and in Ascension.
* It gives real pressure where a death matters, and stays forgiving while a player is learning.
* It loses the least play time and never locked any archetype out for a whole day twice in a row (p90).
* The lock changes behaviour: simulated hardcore deaths fall from 9.2 to 1.7 per 100 hours, because players stop
  taking coin-flip fights.

Exact values:

<!-- spec:begin lock -->
| Level | 1 | 5 | 10 | 15 | 20 | 25 | 30 | 35 | 40 | 45 | 50 | 55 | 60 |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Lock (real time) | 5 min | 6 min | 7 min | 8 min | 9 min | 10 min | 12 min | 14 min | 16 min | 19 min | 22 min | 26 min | 30 min |
<!-- spec:end lock -->

The simulator can re-run any alternative in a minute (`ProposedRules.LockCurve`). With the cap, live hardcore
time to level 60 fell from ~200 to ~146 active hours (`SimulationTest`); the owner accepted ~146 h as the target
(2026-10-12), the EXP curve stays.

## «Тэнгэрийн сорил» — the active way back

`/trial` (`/soril`, `/сорил`), `DeathTrialService` + pure `api/death/DeathTrial`:

* Only a soul, outside Хархорум. Three glowing **«Сүнсний чоно»** spawn around it, on the ground (never on a tree's
  crown: in a forest the leaves are the highest block, and a wolf up there could not reach the soul nor be shot through
  the leaves — fixed 2026-10-13), and hunt only that soul; nobody else can hit them, they bite nobody else. HP = max(10 + 1.6·L, 5 × the soul's SÜLD attack) — about five of its
  hits each, at any level and gear (`DeathTrial.wolfHealth(level, attack)`); bite 2 + 0.25·L.
* **The soul fights with its whole class** while the trial runs: the soul's slowness and weakness are lifted, its
  hits use the SÜLD attack (`CombatListener.isTarget` counts trial wolves), Мэргэн's bow shoots
  (`DeathService.onSoulLaunch`, `SkillService.soulBlocked`) and spells cast and reach the wolves
  (`SkillService.hostile`); `onSoulAttack` still lets them hurt only its own wolves. Before this, a soul hit for ≈ 0
  (weakness V on vanilla damage) and an archer could not shoot at all, so the trial could not be won.
* Kill all three within **3 minutes** → the soul state ends now (`DeathRecord.State.RECOVERED`, actor `trial`); the
  wound still applies, and a **«Сүнсний Хэлтэрхий»** (tier FAINT, 60 active minutes of healing) lands in the bag.
* Time out, a bite that would take the soul to 0 HP, or logging out → the wolves vanish; since 2026-10-13 the soul
  may call them again at once (`DeathTrial.DEFAULT` cooldown 0), or walk the ovoo ritual. The soul's bar says
  «сорил бүтсэнгүй, дахин /trial» (`SpiritTrialState.FAILED`).
* **The soul state ends during the fight** (staff `/deathrevive`, or the timer of an old timed lock) → the trial stops
  at once (`DeathTrial.Outcome.LOCK_ENDED`): the wolves vanish, «Та амилсан тул сорил зогслоо.» Before this (audit
  2026-10-13 BUG-DEATH-01) the wolves kept biting the now-living player, who could die for real. `trial-lockend.js`.
* The wolves are plain vanilla wolves tagged `suld_trial`: no drops, no EXP, no quest/task/campaign credit; they are
  not persistent and are removed on quit and on shutdown.

**Persisted, absolute.** The soul state is the stored `locked_until` (`Long.MAX_VALUE` for a soul without a timer,
`DeathRecord.UNTIL_RECOVERY`; a real timestamp in `TIMED` mode). Ticks, online time and server uptime play no part.
It survives restarts, reconnects and database reloads; so does the beam (`suld_soul_beacons`, read again on join).

**As a soul** the player plays on in a **recovery state**: it cannot fight (except its own trial wolves), be hurt,
loot, build, open containers, cast, ride, enter dungeons or trade. Chat, menus, NPC talk, `/trial` and the ovoo ritual
work. Refusing login altogether was rejected because players would lose contact with their clan and the community.

## The wound (after normal recovery)

* **−5 % effective stats** of the class armour and weapon per death, stacking to at most **−15 %**.
* Each step heals after **3 active hours** (ActivePlaytime), so the wound is pressure, not destruction.
* It is implemented as a per-item factor in `Equipment.compute` (`Equipment.java:126`): it scales `itemStats`,
  `statKeys` and flat damage of the class pieces together. Item stats are never deleted. The factor is part of
  `EquipmentService.fingerprint` so stats recompute when it changes.
* Visible on the HUD (a wound icon), in the class-gear tooltip, and on the death screen.

## Other death costs (level 11+)

| Cost | Before 2026-10-13 | Today |
|---|---|---|
| EXP | −5 % of the bar, plus the timed lock | **−10 %** of the bar; the beam gives 70 % of it back |
| Materials | 25 % of droppable stacks | unchanged: **25 %** (rounded at random: 3 stacks lose 0 or 1, on average 0.75) |
| Durability | 25 % wear on kept items | unchanged |
| Class gear, the menu item, a Сүнсний Хэлтэрхий | kept | kept, never dropped |
| Dungeon | downed members lose the reward | unchanged; the soul goes to the gate, where its beam stands |

The simulator mirrors this (`LiveRules`, and the reference model `ProposedRules` the same way): protected deaths cost a
minute, the refund is assumed walked to (except for an idle player),
and a soul's way back is modelled as **4 minutes** (an assumption, not a measurement: the walk to the beam and back
at soul speed plus the 90-second ritual or the 3-minute trial).

## DeathState (persistent, V12)

Table `suld_death_state`, one row per death, on both PostgreSQL and MySQL:

| Column | Type | Notes |
|---|---|---|
| `death_id` | UUID PK | |
| `player_uuid` | UUID, indexed | |
| `death_seq` | int | per-player sequence; unique `(player_uuid, death_seq)` |
| `died_at` | timestamp (UTC) | real time |
| `locked_until` | epoch ms | `Long.MAX_VALUE` for a soul without a timer; in `TIMED` mode from the lock curve at `died_at` |
| `world`, `x`, `y`, `z` | | death location |
| `cause` | text ≤ 64 | `EntityDamageEvent.DamageCause` + killer id |
| `level`, `ascension` | int | inputs of the lock |
| `wound_after` | int | wound stacks after this death |
| `revive_state` | enum | `LOCKED`, `RECOVERED`, `ADMIN_REVIVED`, `RESET`, `INSTANT_REVIVED` (the last reserved, unused) |
| `recovered_at` | timestamp, nullable | |
| `version` | int | optimistic locking |

**Rules:**

* At most one row per player is `LOCKED`. A second death while locked is impossible, because there is no gameplay
  while locked.
* Multiple-death races (two damage events in one tick) are serialised per player on the profile sequencer that
  already serialises profile access (`DefaultProfileService`).
* The current `souls` map becomes a cache of the open row.
* A protected (level ≤ 10) death is stored too, closed at once (`RECOVERED`, `locked_until = died_at`), so the history
  and the funnel's first death see it.

Table `suld_soul_beacons` (V23): `death_id` PK, `player_uuid` (indexed), `world`, `x`, `y`, `z`, `exp_lost`,
`created_at`, `expires_at`, `claimed_at` (null until reached). The claim is
`UPDATE … SET claimed_at = ? WHERE death_id = ? AND claimed_at IS NULL AND expires_at > ?` (one row updated = the one
claim; `JdbcDeathRepositoryIT`: 24 concurrent claims, one winner, on H2 and PostgreSQL 16).

## Concurrency (audit of the 2026-10-13 reform)

| Shared state | How it stays correct |
|---|---|
| The beam's refund | `claimBeacon` is a compare-and-set in storage (`… WHERE claimed_at IS NULL AND expires_at > ?`); the EXP is granted only on the main thread after it returned true. 24 concurrent claims from 8 threads: one winner (`JdbcDeathRepositoryIT`, H2 and PostgreSQL 16). In process, one claim per player is in flight at a time (`claiming`). A rejoin that reads the beam before the claim committed gets `false` from the second claim: never paid twice. |
| Order of the refund | Guard before value: the claim commits, then the EXP is granted and the profile saved. A crash between the two loses the refund; a quit in that same instant too (the profile is no longer cached). It never pays twice. |
| Death row, EXP loss | The profile is saved at the death (audit F04); the death row is inserted on the single-threaded death executor after any older open row is closed; one open row per player is a unique index. A protected death's closed row is chained the same way. |
| Wound saves | The minute flush, the quit save and the shard's save all run on the same single-threaded death executor, in the main thread's order: no older value can overwrite a newer one. |
| The shard | The spent shard reaches the player file first (`PlayerDataSaves.now`, main thread by the owner's decision), then the wound is stored: a crash can lose the healing, never repeat it. |
| Beams, rituals, the soul's rise | Main thread only (Bukkit scheduler). The async beacon read on join hops to the main thread before it touches the map. |
| Main-thread I/O | No database call on the main thread: every read and write is a `CompletableFuture` on the death executor. |

## Admin commands (all audited through `services.audit()`)

| Command | Permission | Does |
|---|---|---|
| `/deathstatus <player>` | `suld.admin.death` | soul state («амилах хүртэл» or time left), wound stacks, last 5 deaths |
| `/deathinfo <player> [death#]` | `suld.admin.death` | one death in full (cause, place, level, lock) |
| `/deathrevive <player>` | `suld.admin.death.revive` | ends the soul state now (`ADMIN_REVIVED`); the wound is still applied |
| `/deathreset <player>` | `suld.admin.death.reset` | clears soul state and wound (`RESET`; staff-error recovery only) |

Audited without staff: `death.recovered` (actor `trial` or `ovoo`), `death.beacon_claim`, `death.shard_use`.

`/revive` becomes an alias of `/deathrevive`, and its missing audit entry (DT-3) is fixed.

## Death UI

The title reads «ТА УНАЛАА», «/trial эсвэл овоонд цацал өргөж амил»; after the respawn one chat block says what the
death cost (EXP, stacks, wear), where the beam is and how much it gives back, how to come back and what the wound will
be. A beginner's death says «❤ Шинэ баатрын хамгаалалт: юу ч алдсангүй». The soul's boss bar: «☠ Сүнс — /trial
эсвэл овооны цацал · Гэрэлт 230 м З · 7:41 · +700 EXP» (its fill is the beam's time left). The ritual shows
«Овооны цацал: 2/3 тойрог · 41/90 с» on the action bar. The Instant Revive button is absent until a payment provider
exists and the owner enables it (`docs/INSTANT_REVIVE.md`).

## Tests

* api: `DeathLockTest`, `DeathRulesTest`, `DeathTrialTest`, `DeathReformTest` (beginner protection, the untimed
  soul, the shipped defaults), `SoulBeaconTest`, `OvooRitualTest`, `SoulShardTest`;
* JDBC IT of `suld_death_state` and `suld_soul_beacons` (insert, one open row, one beacon claim under 24 threads);
* bots on the dev server: `soulreform.js` (protection, the beam and its refund, the soul's rise at the ovoo, the ritual
  and a broken ritual, the shard), `trialbot.js`, `trial-lockend.js`;
* a real client for the death UI (owner).

Instant-revive tests 11–14 and 18 stay **NOT_IMPLEMENTED** by owner decision.
