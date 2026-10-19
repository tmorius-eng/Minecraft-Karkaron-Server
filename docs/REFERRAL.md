# Урилга — the referral loop (`/urih`)

A friend who brings a friend is the cheapest player a server ever gets, and a player who arrives with a friend stays
(the churn research in the master plan: churn travels along friendships). SÜLD rewards exactly that, once, with
numbers the owner sets.

## For players

* A newcomer types **`/urih <нэр>`** with the name of the friend who brought them. Only a newcomer may: level
  `max-level-to-name` (5) at most, and only once — the first name stands.
* When the newcomer reaches level `level` (10) **both are paid**: the newcomer `invitee-coins` (100), the friend
  `inviter-coins` (200). The newcomer sees it at once; the friend at once when online, else at their next login.
* After `badge-after` (3) friends who reached that level the inviter earns the **«Элч»** tag (`/cosmetics`).
* `/urih` alone shows who invited you and how many you invited.

The numbers in `config.yml` `referral:` are a proposal until the owner sets them; `enabled: false` turns the loop off
(`/urih` then says so).

## How it works

| Piece | Where |
|---|---|
| Rules (who may name whom, when a payment is due, when the tag is earned) | `suld-api` `mn.suld.api.social.ReferralRules`, `ReferralSettings`; `ReferralRulesTest` |
| Storage: one row per invitee, `rewarded_at` and `inviter_paid_at` set by compare-and-set | V31 `suld_referrals`; `JdbcReferralRepository`; `JdbcReferralIT` (24 concurrent payments → one) |
| Service: `/urih`, the 20-second level check for online invitees, the payments, the login catch-up | `mn.suld.plugin.social.ReferralService` |
| Command | `/urih [нэр]` (`invite`, `referral`), `ReferralCommand` |
| Analytics | `referral_created`, `referral_rewarded`, `referral_inviter_paid` (`docs/ANALYTICS.md`) |
| Audit | `referral.named`, `referral.paid` |
| Bot | `tools/loadtest/bots/referralbot.js` (in the sweep) |

Why no level-up hook: a player levels up in six places (combat, quests, campaigns, the spawner); the service instead
re-reads the cached profile of every online invitee whose row is unpaid every 20 seconds, which costs nothing and
cannot miss a level reached offline (`/suld exp`, a correction). Every payment is a compare-and-set in the database
first and the coins second, so a second tick, a second server or a double login pays nobody twice; the coins ride
the profile's normal save.

## Abuse and limits

* One inviter per invitee, ever; nobody names themselves; a player above level 5 is no newcomer.
* The inviter is looked up by name in the profile store (`ProfileRepository.ownerOfName`), so a friend who is offline
  or has never been online at the same time can still be named; a name nobody plays is refused.
* Alts: a player can invite their own second account. The reward is small coins and a tag, both worthless outside
  the game, so the loop accepts that; `referral.named` audits every naming with the invitee's level, and
  `referral_created` carries the inviter, so a farm shows up as one inviter with many level-1 invitees in the funnel
  report's data.
