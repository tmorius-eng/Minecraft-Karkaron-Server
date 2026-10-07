# Client QA: Stage C3b (class armour × 5, weapons × 6 tiers, smith, mastery)

Everything here was tested on Paper with bots, and nothing visual has been seen in a real client. Every item below is
**MANUAL_QA_REQUIRED** until the owner checks it in Minecraft Java **1.21.11** with the SÜLD resource pack accepted.
Record PASS or FAIL with a screenshot under `docs/qa/screens/c3b/`.

Staff shortcuts (op on a dev server):
* `/classgear set <you> level|tier|enhance|mastery <n>` and `/classgear set <you> cleared <dungeon id>`
* `/suld exp <you> <n>`, `/suld coins <you> <n>`, `/itemsadmin give <you> <item id> <rarity> <level>`
* `/suld spawnmob <you> <mob id> <count>`

## A. Worn armour: 5 classes × 6 tiers (30 checks)
For each class, pick it on a fresh character. Then step through `/classgear set <you> tier 1..6` (with
`level 60` first, so the pieces are active). Look in third person (F5) from the front, back and side:
1. All four pieces render with the SÜLD look (`suld:<class>_tN`), not vanilla leather, chain, iron or netherite.
2. The helmet sits on the head, there are no transparent holes on the body or arms, and the boots start at the shin.
3. The leggings and the waist line up with the chest piece, with no z-fighting at the belt.
4. Each tier is visibly richer than the one before, and the five classes are told apart at a glance:
   * Баатар: lamellar;
   * Мэргэн: leather with a quiver;
   * Бөө: robe with fringe;
   * Дархан: apron and plates;
   * Хүлэгчин: rider's deel with a sash.
5. Known limits: both arms mirror the same texture, and Хүлэгчин's tall boots come from the leggings layer.

## B. Inventory and tooltips
6. The armour item icons are vanilla material icons by tier. Expected until C4 adds item models.
7. Each tooltip shows: name with the new tier name (Эхлэл … Дээдэс), rarity, «Зэрэг N» = armour level, class, stats,
   set bonus (n/4), «Сүнсэнд холбоотой», «Арилжаалах боломжгүй».
8. No tier name equals a rarity name (for example EPIC «Домогт» is never a tier).

## C. Class weapons: 6 tiers × 5 classes (30 checks)
9. `/suld exp` to levels 12, 24, 36, 48 and 60. At each step the held weapon upgrades in place with a title. It is the
   same item: it stays in the same slot and nothing drops.
10. Each tier changes silhouette and ornament, not only colour:
    * sabre: fuller → winged guard → spine → wolf pommel → forked star-steel tip;
    * bow: siyahs → ears → bone plates → eagle limbs → celestial bow;
    * staff: rings → ongon face → mirror → bone bells → sky gate;
    * hammer: bands → spikes → ember core → wolf maw → star anvil;
    * spear: tassel → wings → hook → horse skull → comet.
11. The bow draws correctly at every tier (3 pulling frames), aimed down and right towards the player.
12. Effects while held and on hit: T4 steel light, T5 turquoise, T6 celestial star-light.

## D. Smith (Дархан, Kharkhorum crafting district)
13. Right-click the smith. The repair screen shows «⚔ Ангийн хуяг», which opens «Дархан · Ангийн хуяг».
14. The screen shows the 4 pieces and the weapon with tooltips, the tier/level/mastery/power summary, the next tier
    preview, and every requirement with ✔ / ✘ and held/needed counts.
15. With requirements unmet the upgrade button is a barrier and does nothing.
16. With requirements met (staff shortcuts), clicking opens «Баталгаажуулах».
    * ✖ returns without changes.
    * ✔ upgrades: the new look appears on the body at once and the coins and materials are taken.
17. Open the confirmation, walk more than 8 blocks away, then click ✔. It must refuse and change nothing.
18. `/classgear upgrade` as a normal player says to go to the smith.

## E. Mastery and farming (UI text)
19. `/classgear` shows mastery rank and XP, the class perks (✔ when open) and the area farming factor when it is below 1.
20. Kill one mob type in one spot more than 40 times. The EXP message gains «нэг газарт хэт олон агнасан ×0.xx». Move
    about 50 blocks away and EXP is back to full.
21. Mastery rank 3/6/9 messages and perks. Check the resource bar: for example Баатар Хил fills about 10 % faster after
    rank 3.

## F. Persistence (real client)
22. Log out and back in, then restart the server. Armour, tier, enhancement, mastery and weapon tier stay the same.
23. Die (hardcore rules). All class pieces and the weapon stay in the inventory. During the soul lock the smith refuses
    upgrades.
