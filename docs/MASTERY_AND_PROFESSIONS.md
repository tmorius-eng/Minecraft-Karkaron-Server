# Weapon mastery and professions

The owner's direction for the endgame (2026-10-13): a **small power and a name to be proud of** («бага хүч + нэр
хүнд»). This file is what the code does; the numbers come from `suld-api` (`mn.suld.api.mastery`).

## Зэвсгийн ур чадвар: weapon mastery (built)

| Track | Weapon kind (`ItemType`) |
|---|---|
| Илд | sword |
| Нум | bow |
| Таяг | staff |
| Алх | axe |
| Жад | spear |

* **Earning:** a basic hit (melee or arrow) on a SÜLD mob of the content earns points for the track of the held
  weapon: `damage ÷ the player's own basic hit` (`WeaponMastery.points`). A level-10 hit and a level-60 hit weigh the
  same. Training dummies, the training yard's stand-ins and players earn nothing.
* **Levels:** 1–50. Level L needs `round(40 · L^1.5)` points for the next one: 40 at 1, 1 265 at 10, 5 000 at 25, 13 720
  at 49, and 275 808 in all to reach 50 (that many basic hits).
* **Power:** +0.05 % damage a level with that weapon kind, **+2.5 % at 50** (`WeaponMastery.damageBonus`, applied in
  `CombatListener` to the hit). Nothing in the training yard: its kit is the measure there.
* **Titles:** «<Track>-ийн Шавь» at 10, «… Мэргэжилтэн» at 25, «… Мастер» at 50 (`WeaponMastery.title`); level 50 is
  announced to the server. Each level-up records the analytics event `weapon_mastery_level`.
* **Storage:** V25 `suld_weapon_mastery` (`player_uuid`, `track`, `points` ever earned, `updated_at`). The totals are
  read at join. Points earned since then are added to the row every 30 s, at quit and at shutdown, by an additive
  upsert (`JdbcWeaponMasteryRepository.add`), so concurrent writes commute. A player's first write waits for their
  read, so the read can never miss or double one.
* **Commands:** `/mastery` opens the page (five tracks, level, progress bar, bonus, next title). Staff can run
  `/suld mastery <player> SCIMITAR|COMPOSITE_BOW|SHAMANIC_STAFF|HAMMER|LANCE <1-50>`, which is audited as
  `admin.weapon_mastery`.
* **Tests:** api `WeaponMasteryTest`; `JdbcWeaponMasteryIT` (H2, PostgreSQL 16).

## Мэргэжил: professions (rules only, not in the game yet)

The four steppe crafts are VERIFIED steppe trades; their numbers are the game's. Only the pure rules exist
(`mn.suld.api.mastery.Profession`). There are no gathering nodes, items, commands or storage yet.

| Craft | Gathers from |
|---|---|
| Төмрийн Дархан | rock: iron and copper ore |
| Эмийн Бариач | the steppe's healing plants |
| Хүлэгч / Адуучин | the wild herds: horsehair, mare's milk |
| Эсгийч | sheep's wool, beaten into felt |

* Levels 1–50; level L needs `round(50 · L^1.4)` points for the next one (243 098 in all).
* Node tiers 1–4 (the region bands, Хэрлэн … the outer lands) open at levels 1, 13, 25 and 37.
* A gather gives 10 points a tier, or half once the craft is 24 levels past that tier's level.
* A gather yields 1 item, 2 from level 20 and 3 from level 40.
* A gathered node comes back after 300 s, for everyone.
