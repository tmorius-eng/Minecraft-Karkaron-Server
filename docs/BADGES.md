# Badges (тэмдэг)

The badges are the owner's HD pills (`assets/art/source/badges.webp`, built by `tools/pack/gen_ui.py` and `hd_art.py`).
They show in three places:
* the TAB list;
* chat;
* the name tag above a player's head.

Every one of them is the same glyph of the pack font `suld:ui`.

Before the name: `[staff badge] [rank badge] Name`. After the name: `[class badge] [ОВОГ TAG]`, then any equipped tag
cosmetic.

| Badges | How a player gets one | Command |
|---|---|---|
| Rank: АРД, ЦЭРЭГ, АРАВТ, ЗУУТ, МЯНГАТ, ТҮМЭН, НОЁН, ХААН | `/rankup`: the level and the ₮ price of the next rank | `/suld setrank <player> <ard…khaan>` (admin, no checks) |
| Staff and supporters: ЭЗЭН, АДМИН, DEV, МОД, ТУСЛАГЧ, СТРИМЕР, ДЭМЖИГЧ | An admin gives it | `/suld badge <player> <owner\|admin\|developer\|mod\|helper\|streamer\|sponsor\|none>`, kept in the player's data. Or a permission `suld.badge.<id>` on a LuckPerms group (`lp group mod permission set suld.badge.mod true`), which also covers offline players and wins over the command. An operator without either shows АДМИН. |
| Class: БААТАР, МЭРГЭН, БӨӨ, ДАРХАН, ХҮЛЭГЧИН | The class chosen with `/class` | — |
| ОВОГ + clan tag | Being in a clan: `/clan create <name> <tag>`, or `/clan accept` after an invitation | — |
| АЮУЛГҮЙ / АЮУЛТАЙ | Shown by the HUD inside or outside Kharkhorum | — |

Players see every badge and how to get it in `/cosmetics` → «Тэмдгүүд», or in `/rankup` → «Бүх тэмдэг».

Config (`config.yml`, defaults on):

```yaml
ui:
  badges:
    class: true   # class badge after the name (TAB, name tag)
    clan: true    # ОВОГ + clan tag after the name
```

The badge labels and colours are code (`mn.suld.api.style.Rank`, `StaffBadge`, `PlayerClass`). The pictures are the
pack.

Status: **MANUAL_QA_REQUIRED**. The bots checked:
* the name-tag prefix and suffix glyphs;
* the staff badge right after `/suld badge`;
* the `/rankup` badges;
* the badge page.

Nobody has looked at the name tags in a real client yet.
