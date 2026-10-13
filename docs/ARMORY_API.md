# The armory API (read-only)

Directive Phase 8: the game's public state as JSON for a web page (a player's sheet, the clan board, the week's
Хуралдай). `suld-plugin` `mn.suld.plugin.web.ArmoryServer`; the views and the rate limit are pure (`suld-api`
`mn.suld.api.web`).

## Switching it on

It is off by default. In `plugins/SULD/config.yml`:

```yaml
web:
  armory:
    enabled: true
    bind: 127.0.0.1     # keep it local; put a reverse proxy (TLS) in front for the web
    port: 8086
    cache-seconds: 30   # how old an answer may be
    burst: 20           # requests a client address may make at once…
    per-minute: 60      # …and then per minute
    allow-origin: "*"   # the CORS header; empty for none
    trusted-proxies: [] # e.g. ["127.0.0.1"] behind nginx: then its X-Forwarded-For names the client
```

Behind a reverse proxy every request comes from the proxy, so the limit would count all clients as one. With the
proxy's address in `trusted-proxies`, the last address of `X-Forwarded-For` (the one the proxy itself saw) is the
client. A client's own earlier entries are ignored, so it cannot forge another address. A peer not in the list is
always counted by its own address.

The server answers on its own threads (four), never on the main thread.

## Endpoints

All are `GET` (or `HEAD`); anything else is `405`. Bodies are `application/json; charset=utf-8`.

| Path | What |
|---|---|
| `/api/player/{uuid}` | A player's sheet: name, online, class, level, Тэнгэрийн Зэрэг (rank and title), clan (tag, name), weapon mastery levels, craft levels, and the gear worn (slot, name, rarity, item level; online players only) |
| `/api/clans/leaderboard` | The top 50 clans by EXP: rank, tag, name, level, EXP, members |
| `/api/khuraldai/active` | The week's cycle and phase, the ballot with its votes, the running campaign (total, target, state) and the world's live campaign marks |

Errors are `{"error": "…", "status": n}`, sent with that status:
* `400`: the player key is not a UUID (`8-4-4-4-12` hex digits, either case).
* `404`: no such player or path.
* `429`: over the client's rate (with `Retry-After`).
* `500`: an error of the server's own (logged).
* `503`: warming up, before the first snapshot.
* `504`: the database did not answer an offline sheet in 3 s (its three reads share the one wait). Never cached:
  the next request asks again.

## How it keeps the game safe

* **No tick is touched.** The main thread takes a snapshot every `cache-seconds`: the clan board, the Хуралдай, the
  sheets of the players online, and every clan member's clan. The HTTP threads only read that snapshot.
* **Offline players** are read from the database on the io pool: profile, weapon mastery and crafts. Their gear lives
  in the player file and is not served. A sheet is kept `cache-seconds`, and at most 5 000 are kept.
* **Nothing private.** The views carry what any player sees in the game. There is never an address, a location, a
  balance or credits, a session, or anything of the staff; `ArmoryTest` checks every view's keys against
  `Armory.NEVER`. A player is named only by a UUID, so a name or a path trick is a `400`.
* **Rate limit.** Each client address has a token bucket (`RateLimiter`). At most 10 000 addresses are kept; idle ones
  are forgotten first, and while all are busy a new address waits.
* **Read-only.** There is no endpoint that changes anything, and no credential is taken or needed.

## Tests

* api: `RateLimiterTest` (the burst, the refill, the bounded map, concurrency, the wait it reports) and
  `ArmoryTest` (the sheet's fields, no forbidden key in any view, the UUID rule, the error body).
* Live, 2026-10-13, the dev server (PostgreSQL 16) with the armory on, by `curl`:

| Request | Answer |
|---|---|
| `/api/clans/leaderboard`, `/api/khuraldai/active` | 200 (the week's ballot with its votes, the world's marks) |
| `/api/player/<a bot online>` | 200, `"online":true`, the five worn pieces |
| `/api/player/<a bot offline>` | 200, crafts and weapon mastery read from the database |
| the same UUID in capitals | 200, the same sheet |
| `/api/player/Pf27169`, `/api/player/..%2Fclans` | 400 |
| `/api/player/00000000-0000-3000-8000-000000000000`, `/api/nothing`, `/` | 404 |
| `POST /api/clans/leaderboard` | 405 |
| `HEAD /api/clans/leaderboard` | 200, no body |
| 30 requests at once | 20 × 200, then 10 × 429 with `Retry-After: 1` |
| 25 requests, each with another `X-Forwarded-For` (no trusted proxy) | 20 × 200, 5 × 429: the header is ignored |
| the same behind `trusted-proxies: ["127.0.0.1"]` | 25 × 200: each forwarded address is its own client |
| 25 requests forging the first hops, the same last hop | 20 × 200, 5 × 429 |
| an offline sheet while `suld_profiles` is locked (`LOCK TABLE … ACCESS EXCLUSIVE`, 7 s) | 504 after 3.0 s, `Cache-Control: no-store`; 200 once the lock is gone |

The last row found a bug: the 504 body had gone out with status 200. Fixed the same day.
