# SÜLD → Discord

The server speaks to Discord through webhooks: no bot token, no library, one HTTPS POST per message
(`plugin/social/DiscordNotifier`). What players outside the server should see goes to a **public channel**; what staff
should see goes to a **staff channel**. Chat between Discord and the game is not part of this (DiscordSRV, below).

## What posts where

| Moment | Channel | Line |
|---|---|---|
| The server comes up / goes down | public | «🟢 SÜLD асаалаа — suld.mn» / «🔴 SÜLD унтарлаа.» |
| A chronicle first (first clear of a dungeon, first stand at a site, first world boss kill, «Мөнх Тэнгэрийн Элч») and every relic claim | public | «📜 …» the chronicle headline (`docs/KHURALDAI.md`) |
| The world boss: the call 10 minutes before and the rise | public | «⚔ 10 минутын дараа «…» … босно!», «⚔ «…» … бослоо!» |
| Их Хуралдай: the vote opens (with the ballot) and its result | public | «✦ Их Хуралдай нээгдлээ! …», «✦ Их Хуралдай шийдвэрлэв: …» |
| `/report <text>` | staff | «⚑ Мэдэгдэл <player> (<world x y z>): <text>» |
| `/suld discord test` | both | «🔔 SÜLD Discord холболтын шалгалт (<who>)» |

Records (fastest clears) and campaign results stay in the game's chronicle only; the weekly funnel summary
(`docs/ANALYTICS.md`) and the operators' alerts (`docs/OPERATIONS.md` «Alerts») use their own webhooks.

## Setup

1. In Discord: the channel → *Edit channel → Integrations → Webhooks → New webhook* → copy the URL. One for the public
   channel (`#suld` or `#announcements`), one for the staff channel (the same `#suld-alerts` the health check uses is
   fine: `/report` lines belong with the alerts).
2. On the server, in `/etc/suld/suld.env`: `DISCORD_WEBHOOK_URL=` (public) and `ALERT_WEBHOOK_URL=` (staff), then
   `sudo /opt/suld/bin/update.sh` (or `deploy.sh`): they are written into the plugin's `config.yml`
   (`discord.webhook`, `discord.staff-webhook`, file mode 600) and the server restarts.
3. In the game or on the console: `/suld discord test` posts one line to each channel; `/suld discord` shows
   `sent`/`failed` counts and the queue.

A webhook URL is a secret: whoever has it can post to the channel. It is never logged, never shown by a command, and
must be `https://` (a dev server may use `http://127.0.0.1:…` for a local stand-in, `tools/loadtest/bots/discordbot.js`).
Blank = that channel is off; both blank = the bridge does not run.

## How it posts

Lines queue per channel and leave as one message every two seconds (Discord allows about 30 posts a minute per
webhook): a burst (a reset server writing many firsts) becomes a few multi-line messages, at most 50 lines wait and
the next message says how many were dropped. Sending is off the main thread with a 10 s timeout; a webhook that
fails (Discord down, URL revoked) is logged once a minute without the URL or the body, the line is dropped, nothing
retries in a loop. On shutdown the «унтарлаа» line and whatever still waits are sent with three seconds each.

Tests: `DiscordQueueTest` (batching, the cut, the bounded backlog), `DiscordNotifierTest` (the JSON body, the URL
rule); `discordbot.js` in the sweep (`tools/loadtest/regress.sh`) runs a local webhook and checks `/report`, the test
line and the JSON shape against it.

## Chat bridge (DiscordSRV, optional)

DiscordSRV is installed by the deploy (`deploy/plugins/plugins.json`, profile `core`) and idles until it has a bot
token. To bridge the game chat with a Discord channel: create a bot at discord.com/developers (Message Content
intent on), invite it, and set in `plugins/DiscordSRV/config.yml` `BotToken` and `Channels: {"global": "<channel id>"}`,
then restart. SÜLD does not manage that file; keep the token out of the repository and out of chat. DiscordSRV's own
documentation covers the rest (roles, linking, console channel).
