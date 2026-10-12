# Дархан — crafting

Plan Phase 3 (`docs/STATUS.md`): the economy's maker. Class armour has its own smith page (tiers, enhancement,
mastery); everything else a player wears or wields can also be made from a recipe.

## The recipe book

`suld-api/src/main/resources/items/recipes.json` — 43 recipes over the existing items, no new art:

| Band | What | Materials |
|---|---|---|
| 1–19 | Эсгий and Гинжин armour, the first swords, axes, bows, spears, the wooden shield, the Бөө staff and tome, the silver ring, the wolf-fang amulet | wolf pelts, iron shards, scorpion venom (staff, tome), a little gold dust (jewellery) |
| 20–29 | Ган armour, Ган shield, Хаш ring, the fast bow, the Онгон totem, the gold axe, Тэнгэрийн Таяг | bear hide, ice stone, iron shards; gold dust for jewellery |
| 30–49 | Тэнгэрийн armour, Хааны Илд, Тэнгэрийн Нум, Эзэн Жад, the gold ring, the sky charm | ice stone, gold dust, iron shards |
| 50–60 | Хааны armour | gold dust, a sky stone, iron shards |

Every material comes from loot or salvage (`RecipeCatalogTest`): salvage turns common/uncommon gear into iron shards,
rare/epic into gold dust, legendary and above into sky stone. Drop-only prestige (the Хаадын set, Хаадын Тамга,
Саарлын Соёо/Зүрх, the class sets) is never crafted.

Fields: `id`, `result`, `materials` (1–64 each), `coins`, `level`, `rarity` ([min, max] within the item's range),
`maxLevel` (optional, default `level + 9`).

## The level band

The item's level is the crafter's level **held to the recipe's band** [`level`, `maxLevel`] (`Recipe.itemLevel`).
Before, it was the crafter's level outright: four wolf pelts and twenty coins made a level-60 helmet for a level-60
smith. Bands are nine levels (Тэнгэрийн armour 35–49, the top Хааны set 50–60); `RecipeCatalogTest` checks that
every level 1–60 has a recipe and that no band reaches further.

## Crafting

- At the smith: «Жорын ном — урлах» on the smith page, or `/item recipes` (the chat list: `/item recipes list`).
  Each recipe shows its band, the level you would make, its rarity range, every material as have/need and the coins.
  A click asks once; the second crafts and the book opens again.
- `/item craft <recipe>` does the same in one step.
- Both go through `CraftService`: a class, not a soul, the level, the coins, every material and a free bag slot, all
  checked before anything is taken; then materials, coins, the item, the profile save and an `item.craft` audit row.
- Rarity is rolled in the recipe's range with the CRAFT band's weights.

## Crafting orders (`/order`)

The Дархан's order board: a player who cannot (or does not want to) make an item puts up a fee for a recipe; any
other player who can make it fills the order with their own materials and the recipe's coins.

- `/order post <recipe> <fee>` (fee 10–100 000 coins, at most 3 open orders each). The poster's level must reach the
  recipe (no ordering gear one cannot wear). The fee leaves the poster and that profile is **stored** before the order
  is written (`craft_orders.yml`, written at once); a failed save gives the fee back.
- `/order` opens the board: someone else's order shows its materials as have/need and fills on a confirmed click;
  your own withdraws on a confirmed click (the fee comes back).
- Filling: the order is removed from the file first (so it can never be filled twice), then the crafter pays the
  materials and the recipe's coins and gets the fee; the item is made at the poster's level held to the recipe band,
  bound to the poster when its rarity binds, and goes to their bag, or to the mailbox when they are offline (given on
  the next join).
- Never one's own order. Orders expire after 7 days; the fee goes back, to an offline poster on their next join (owed
  refunds live in the same file).
- Audit rows: `craft.order_post`, `craft.order_fill`, `craft.order_withdraw`.

## Tests

`RecipeCatalogTest` (band clamp, every level covered, no long band, every material obtainable), the catalogue loader
tests, and `tools/loadtest/bots/craft.js` on the dev server (a level-30 smith gets the level-10 cap from a level-1
recipe; the window asks, crafts and reopens; a level-50 recipe is refused), `CraftOrderRulesTest`, and
`tools/loadtest/bots/craft-orders.js` (post holds the fee, a fill pays the crafter and gives the poster the item,
withdraw pays back, an offline poster gets the item from the mailbox on the next join).
