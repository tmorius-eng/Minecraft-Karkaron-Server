# Historic sites of the Mongol lands

Sixteen historical places stand in the wild, each inside the named area where the real place lies, in roughly its
direction from Kharkhorum (the map is compressed). Data: `suld-plugin/src/main/resources/content/sites.json`
(data-driven, `docs/CONTENT_DATA.md`). Builds: `mn.suld.api.world.site.SiteKind`. Plugin:
`mn.suld.plugin.region.HistoricSiteService`.

| Site | Kind | Area | History label |
|---|---|---|---|
| Аураг — Их Ордны Туурь | ordo ruins | Хэрлэнгийн Тал | VERIFIED place |
| Хөдөө Арал — Есөн Хөлт Цагаан Туг | nine white standards | Хөдөө Арал | VERIFIED place |
| Бурхан Халдуны Их Овоо | great ovoo | Бурхан Халдуны Бэл | VERIFIED place |
| Дэлүүн Болдог | marked stone | Онон | INSPIRED (exact place disputed) |
| Хар Балгасын Хэрэм | rammed-earth walls | Хар Балгас | VERIFIED place |
| Хөшөө Цайдамын Гэрэлт Хөшөө | stele on a turtle base | Орхон | VERIFIED place |
| Уушгийн Өврийн Буган Хөшөө | deer stones in a ring | Хөвсгөл | VERIFIED place |
| Дарьгангын Хүн Чулуу | stone figures | Дарьганга | VERIFIED place |
| Чингисийн Хэрэм | earthen rampart | Мэнэнгийн Тал | VERIFIED place (built before Chinggis) |
| Алтайн Хадны Зураг | petroglyph rocks | Таван Богд | VERIFIED place |
| Тангудын Хилийн Харуул | ruined watchtower | Тангудын Хил | INSPIRED |
| Өглөгчийн Хэрэм | dry-stone wall ring | Хэнтийн Тайга | VERIFIED place |
| Зүүн / Өмнөд / Баруун / Хойд Замын Өртөө | yam relay posts | Онон, Өмнийн Говь, Отгонтэнгэр, Сэлэнгэ | INSPIRED (the yam system is real) |

## Rules

* The label is about the **place**. Every build is a small, simplified, original game version: no real carving,
  inscription or monument is copied. Descriptions say so in game.
* No Soyombo or modern national symbol on anything of the empire period (`docs/research/history/README.md`).
* The facts come from secondary summaries; check them against primary sources before any public use.

## In the game

* **Built** when a player first loads its chunks (never a forced load), on a level pad at the footprint's median
  height; remembered in `plugins/SULD/sites.yml` and rebuilt when its kind's build changes.
* **Protected:** the pad and the build cannot be broken, built over, burned, flooded, pushed or blown up
  (`VillageProtection.protect`); `suld.admin.world` bypasses it.
* **Name plate** over each site (not saved with the chunk; respawned).
* **First visit:** a title, the place's story and its label in chat, and a landmark's EXP
  (`Rewards.landmarkExp`, at least 60).
* **Staff:** `/suldworld sites` lists them with coordinates and build state; `/suldworld site <id>` teleports.

## Protected ground in the overworld (VillageProtection)

* Every generated structure: villages, temples, outposts, ruins, shipwrecks, mansions, monuments, mineshafts,
  strongholds, ancient cities and the rest, by the structure's bounding box.
* Every historic site and every ovoo.
* Villagers and wandering traders cannot be hurt (only the void and /kill) or converted.
* Blocked: breaking, placing, buckets, explosions, burning and fire, pistons, liquids flowing in, mob griefing.
  Chests, doors and trading still work.
