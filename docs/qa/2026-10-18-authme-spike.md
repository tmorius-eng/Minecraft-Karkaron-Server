# Spike 2026-10-18: Velocity 3.5.1 + AuthMe 6.0.1 in front of Paper 1.21.11

Phase 0 of the hybrid login plan (`docs/AUTHENTICATION.md`). A scratch pair on the dev host only, no production data:
Velocity 3.5.1 (build 615, sha256 checked against the fill API) on `0.0.0.0:25580`, offline mode, modern forwarding,
one server `suld = 127.0.0.1:25581`; Paper 1.21.11 bound to `127.0.0.1:25581`, `online-mode=false`,
`proxies.velocity.enabled=true`, `online-mode=false`, the same forwarding secret; AuthMe 6.0.1 from Modrinth
`authmereloaded` (the two jars pinned in `deploy/plugins/plugins.lock.json`) on each side, AuthMe's store on the dev
PostgreSQL 16 (`authme_spike`). Clients: mineflayer 1.21.11 bots with offline names. Secrets were generated with
`openssl rand -hex 32` and never committed.

SÜLD itself was not in this pair: the spike answers what AuthMe and Velocity do, so the gate in `AuthenticationService`
is built against observed behaviour.

| # | Question | Result | Observed |
|---|---|---|---|
| S1 | AuthMe Velocity config keys | PASS | `plugins/authmevelocity/config.yml`: `authServers` (list, must name `suld`), `allServersAreAuthServers`, `serverSwitch.requiresAuth`, `autoLogin`, `commands.requireAuth` + `whitelist`, `chatRequiresAuth`, `proxySharedSecret` (generated on first start), `premium.keepOfflineUuidCompatibility` (default **false**). Startup lines: `Registered native Velocity premium verification`, `Current auth servers: > suld`, `premium.keepOfflineUuidCompatibility is enabled` |
| S2 | AuthMe Paper config and PostgreSQL | PASS | `DataSource.backend: POSTGRESQL` with the `mySQL*` keys (host, port `'5432'`, `mySQLUseSSL: false`, user, password, database), `caching: false` (AuthMe says so behind a proxy). Log: `AuthMePostgreSQLPool - Start completed`, `PostgreSQL setup finished`. One table `authme` (`username` unique lower-case, `realname`, `password`, `islogged`, `hassession`, `premiumuuid`, …). `Hooks.bungeecord: true`, `Hooks.proxySharedSecret` must equal the proxy's; `settings.enablePremium: true`. Paper fetches AuthMe's 15 `libraries:` from Maven on first boot |
| S3 | API and event classes | PASS | `fr.xephi.authme.api.v3.AuthMeApi.getInstance()`, `isAuthenticated(Player)`, `isRegistered(String)`; `fr.xephi.authme.events.LoginEvent(Player)`, `LogoutEvent(Player)`, `RestoreSessionEvent(Player, boolean)` (cancellable), all extending `CustomEvent extends org.bukkit.event.Event` |
| S4 | Logged in at `PlayerJoinEvent`? | **NO, a joined player can be unauthenticated** | With the pre-join dialog a wrong password still logs `SpikeR joined the game` and then `lost connection: Wrong password!` in the same tick; cancelling logs `joined the game`, then `You have canceled the login.`; with the dialog off (what a client older than 1.21.6 gets) the player joins and stays in the world until `/login`. AuthMe logs `SpikeR logged in` on its scheduler thread after the join. So every join action that pays, gives, opens a screen or grants OP must wait for AuthMe's login |
| S5 | Premium keeps the offline UUID | NOT TESTED | needs a real Microsoft account on a real client: owner check in `docs/qa/LAPTOP_TEST_2026-10.md` once the pair is deployed |
| S6 | Proxy UUID = `nameUUIDFromBytes("OfflinePlayer:"+name)` | PASS | `UUID of player SpikeA is 7894e439-d2ad-3f0f-bce8-9102c3761e4f` = the v3 UUID computed in Python for `OfflinePlayer:SpikeA` |
| S7 | Mineflayer and the pre-join dialog | PASS with a raw packet | The dialog arrives in the configuration phase (`show_dialog`, `Register` with inputs `password`, `confirm`, actions `authme:prejoin-register/submit` / `…/cancel`; `Login` with `authme:prejoin-login/submit`). A bot that sends serverbound `custom_click_action` (configuration id `0x08`) with a network NBT compound of the inputs registers and logs in. A bot that does not answer is kicked at the 30 s `loginTimeout` |
| S8 | Direct connect to the backend | PASS | `This server requires you to connect with Velocity.` from 127.0.0.1; from the host's own address the port is refused (loopback bind) |
| S9 | Mongolian messages | PASS | `messages/messages_mn.yml` with `messagesLanguage: mn`, `perPlayerLocale: false`: `Амжилттай нэвтэрлээ!` shown after `/login`. AuthMe ships no Mongolian file; SÜLD writes its own |
| S10 | SkinsRestorer behind the proxy | NOT TESTED | not in this pair; it is not in the plugin manifest either |
| S11 | Velocity ready line | PASS | `[com.velocitypowered.proxy.Velocity]: Done (2.11s)!` |
| S12 | Premium name squatting | **RISK CONFIRMED** | Premium is opt-in: a password account runs `/premium`, AuthMe kicks with `Premium verification requested. Please reconnect…`, and the proxy then demands Mojang authentication for that name. Nothing reserves premium names: an offline player can register any free name, including one that belongs to a Mojang account (`SpikeR` turned out to be one). After `/premium` on a name they do not own, the offline player is locked out with `You are not logged into your Minecraft account` on every reconnect; `authme freemium SpikeR` does not clear it (`Premium mode is not enabled for SpikeR`, the row's `premiumuuid` is still empty); only a proxy restart clears the pending check |
| S13 | `op <Name>` | PASS | `ops.json` holds `083eb2d5-caca-3293-bdfe-8d4278e9dc18`, the offline UUID of `SpikeR` |

Other observations:

* A lower-case twin is refused before login by AuthMe (`preventOtherCase: true`):
  `You should join using username SpikeR, not spiker.` SÜLD's own name check (`ProfileRepository.ownerOfName`) stays
  as a second wall for profiles created before AuthMe.
* Commands before `/login` are blocked by AuthMe (`/spawn` got no answer); `protectInventory` needs PacketEvents, so
  the inventory is visible before login (SÜLD opens no screen before login anyway).
* Velocity's `login-ratelimit` (3000 ms) refuses reconnects in quick succession: bots wait 4 s between joins.

What this decides for the code:

* `AuthMeBridge` reads `AuthMeApi` and the three events by class name; the gate holds join actions until `LoginEvent`
  and re-checks `isAuthenticated` once a second for players still waiting (the login runs on AuthMe's own thread).
* SÜLD guards `/premium` with a Mongolian warning and `/premium confirm` (S12 lockout).
* `authbot.js` answers the pre-join dialog with the raw packet from S7.
