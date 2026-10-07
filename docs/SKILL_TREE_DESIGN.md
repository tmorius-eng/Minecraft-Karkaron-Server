# SÜLD skill tree — design

The tree is a map the player walks outward from a root node. The left branch (L) is survival, the middle one (M) reshapes
the class's four spells, the right one (R) is offence and passive spells, the top row offers three ultimates of which one can be
learned, and five universal nodes sit beside the root. **Red links** connect nodes that cannot both be learned, so no player
can have everything: a full tree costs more than a level-60 character earns (44 level points + up to 9 from the story and discovery).

## Shape (shared by the five classes, 31 class nodes on a 5 × 9 grid + 5 universal)

```
row 8   u1(ult)  u2(ult)  u3(ult)            ult row: pick ONE (red links), u1/u2/u3 each also need l6/m6/r6
row 7   l7(*)  ✖  m7  ✖  r7               one keystone per class sits at l7 or r7
row 6   l6  — m6  — r6                      bridge row
row 5   l5 — l5b ✖ m5 ✖ r5b — r5
row 4   l4  ✖ m4  ✖ r4
row 3   l3b — l3 — m3 — r3 — r3b            bridge row
row 2   l2b — l2 ✖ m2 ✖ r2 — r2b
row 1   l1     m1     r1
row 0   x2 — x1 — ROOT — x3 — x4 — x5      universal nodes: EXP, loot, mining, dodge, cooldown reduction
```

`—` path, `✖` exclusive (red) link. l5 / r5 are hidden dead ends that appear when a neighbour is learned.

## Per class

### Баатар (baatar)

| id | name | branch | cost | rank | level | effect |
|---|---|---|---|---|---|---|
| l1 | Ган Бие | DEFENSE | 1 | 3 | 3 | HEALTH +4 |
| m1 | Дайчны Сүнс | SPELL | 1 | 3 | 3 | RESOURCE_MAX +15 |
| r1 | Хурц Ир | OFFENSE | 1 | 3 | 3 | ATTACK_PCT +4 |
| l2b | Хатуу Арьс | DEFENSE | 1 | 2 | 6 | ARMOR +5 |
| l2 | Хүлцэл | DEFENSE | 1 | 2 | 6 | DAMAGE_REDUCTION +3 |
| m2 | Цавчилтын Өргөн | SPELL | 2 | 1 | 6 | TENGER_TSAVCHILT: RADIUS_PCT +25; TENGER_TSAVCHILT: DAMAGE_PCT +10 |
| r2 | Цуст Цохилт | OFFENSE | 1 | 2 | 6 | CRIT_CHANCE +4 |
| r2b | Хүнд Алх | OFFENSE | 1 | 2 | 6 | CRIT_DAMAGE +15 |
| l3b | Цус Ундаа | DEFENSE | 2 | 2 | 10 | LIFESTEAL +3 |
| l3 | Хариу Цохилт | DEFENSE | 1 | 2 | 10 | THORNS +12 |
| m3 | Уур Бадраах | SPELL | 1 | 2 | 10 | RESOURCE_REGEN +1 |
| r3 | Уур Хилэн | OFFENSE | 2 | 1 | 10 | proc HIT 25% → RESOURCE(8,0) cd 2s |
| r3b | Довтлогчийн Хөл | OFFENSE | 1 | 2 | 10 | MOVE_PCT +4 |
| l4 | Эр Зориг | DEFENSE | 3 | 1 | 14 | proc LOW_HP 100% → SHIELD(6,6) cd 45s |
| m4 | Хашгираанд Хөнөөл | SPELL | 2 | 1 | 14 | DAINY_KHASHGIRAAN: WEAKEN +6; DAINY_KHASHGIRAAN: COST_PCT -15 |
| r4 | Цус Бялдар | OFFENSE | 2 | 1 | 14 | proc CRIT 100% → HEAL(3,0) cd 3s |
| l5 | Зүрх Сэтгэл | DEFENSE | 1 | 2 | 20 | HEALTH +6 (hidden) |
| l5b | Асгарсан Цус | DEFENSE | 2 | 1 | 20 | proc KILL 100% → HEAL(5,0) cd 0s |
| m5 | Газар Доргио | SPELL | 2 | 1 | 20 | DOVTLOKH_USRELT: DAMAGE_PCT +25; DOVTLOKH_USRELT: KNOCKUP +4; DOVTLOKH_USRELT: RADIUS_PCT +20 |
| r5b | Дайны Бүжиг | OFFENSE | 2 | 1 | 20 | proc HIT 15% → STRENGTH(1,4) cd 8s |
| r5 | Цавчин Тайлах | OFFENSE | 2 | 1 | 20 | proc CRIT 35% → AOE(1,3.5) cd 3s (hidden) |
| l6 | Үл Хөдлөх | DEFENSE | 2 | 2 | 24 | KB_RESIST +60; ARMOR +4 |
| m6 | Хааны Бат | SPELL | 2 | 1 | 24 | KHAAN_KHAMGAALALT: SHIELD +6; KHAAN_KHAMGAALALT: COST_PCT -20 |
| r6 | Үхлийн Гар | OFFENSE | 2 | 2 | 24 | ATTACK_PCT +8 |
| l7 | Мөнх Тэсвэр | KEYSTONE | 4 | 1 | 30 | KEYSTONE MUNKH_TESVER |
| m7 | Тэнгэр Нурах | SPELL | 3 | 1 | 30 | TENGER_TSAVCHILT: DAMAGE_PCT +40; TENGER_TSAVCHILT: ECHO_PCT +30 |
| r7 | Хаадын Хэрцгий | OFFENSE | 3 | 1 | 30 | ATTACK_PCT +10; CRIT_DAMAGE +25 |
| u1 | Үхэшгүй Эр | ULTIMATE | 4 | 1 | 36 | ULTIMATE UKHEL_UNDER requires l6 |
| u2 | Бүхний Нурал | ULTIMATE | 4 | 1 | 36 | ULTIMATE BUKHNII_NURAL requires m6 |
| u3 | Чингисийн Уур | ULTIMATE | 4 | 1 | 36 | ULTIMATE CHINGISIIN_UUR requires r6 |

### Мэргэн (mergen)

| id | name | branch | cost | rank | level | effect |
|---|---|---|---|---|---|---|
| l1 | Хөнгөн Алхаа | DEFENSE | 1 | 3 | 3 | MOVE_PCT +4 |
| m1 | Харцны Төвлөрөл | SPELL | 1 | 3 | 3 | RESOURCE_MAX +15 |
| r1 | Хурц Сум | OFFENSE | 1 | 3 | 3 | ATTACK_PCT +4 |
| l2b | Ангийн Арьс | DEFENSE | 1 | 2 | 6 | ARMOR +3 |
| l2 | Салхин Бие | DEFENSE | 1 | 2 | 6 | HEALTH +4 |
| m2 | Нүдний Гялбаа | SPELL | 2 | 1 | 6 | CHONYN_NUD: VULN +20; CHONYN_NUD: RADIUS_PCT +25 |
| r2 | Нарийн Онилго | OFFENSE | 1 | 2 | 6 | CRIT_CHANCE +5 |
| r2b | Хурц Хошуу | OFFENSE | 1 | 2 | 6 | CRIT_DAMAGE +15 |
| l3b | Нуугдсан Хөл | DEFENSE | 1 | 2 | 10 | KB_RESIST +20; MOVE_PCT +2 |
| l3 | Хурдан Гар | DEFENSE | 1 | 2 | 10 | RESOURCE_REGEN +1 |
| m3 | Төвлөрсөн Амьсгал | SPELL | 1 | 2 | 10 | COST_REDUCTION +5 |
| r3 | Нарийн Цохилт | OFFENSE | 2 | 1 | 10 | proc HIT 20% → BONUS(0.6,0) cd 3s |
| r3b | Шонхрын Нум | OFFENSE | 1 | 2 | 10 | ATTACK_PCT +3 |
| l4 | Хоргодох Сүүдэр | DEFENSE | 2 | 1 | 14 | proc DAMAGED 30% → SPEED(2,3) cd 10s |
| m4 | Олон Сумны Сүр | SPELL | 2 | 1 | 14 | OLON_SUM: DAMAGE_PCT +20; OLON_SUM: COST_PCT -15 |
| r4 | Тэнгэрийн Нүд | OFFENSE | 2 | 1 | 14 | proc CRIT 40% → CHAIN(0.8,0) cd 3s |
| l5 | Ойн Эдгээл | DEFENSE | 2 | 1 | 20 | proc KILL 100% → HEAL(4,0) cd 0s (hidden) |
| l5b | Чонын Гишгүүр | DEFENSE | 2 | 1 | 20 | UKHRAKH_USRELT: HASTE +4; UKHRAKH_USRELT: COST_PCT -20 |
| m5 | Цусан Хур | SPELL | 2 | 1 | 20 | OLON_SUM: HEAL_ON_HIT +1.4 |
| r5b | Галт Сум | OFFENSE | 2 | 1 | 20 | OLON_SUM: BURN +4; OLON_SUM: DAMAGE_PCT +10 |
| r5 | Салхин Харвалт | OFFENSE | 2 | 1 | 20 | proc CAST 50% → RESOURCE(15,0) cd 6s (hidden) |
| l6 | Нүүдэлчний Тэсвэр | DEFENSE | 2 | 2 | 24 | HEALTH +5; DAMAGE_REDUCTION +3 |
| m6 | Тэнгэрийн Туяа | SPELL | 2 | 1 | 24 | TENGERIIN_SUM: DAMAGE_PCT +30; TENGERIIN_SUM: COST_PCT -10 |
| r6 | Ан Агнуурын Зам | OFFENSE | 2 | 2 | 24 | ATTACK_PCT +8 |
| l7 | Салхи Шиг | DEFENSE | 3 | 1 | 30 | MOVE_PCT +6; proc SNEAK 100% → SPEED(2,2) cd 10s |
| m7 | Ухрах Сүүдэр | SPELL | 3 | 1 | 30 | UKHRAKH_USRELT: SLOW +4; UKHRAKH_USRELT: WEAKEN +6; UKHRAKH_USRELT: RADIUS_PCT +30 |
| r7 | Нэг Сумны Хувь | KEYSTONE | 4 | 1 | 30 | KEYSTONE NEG_SUMNII_KHUVI |
| u1 | Үхлийн Тэмдэг | ULTIMATE | 4 | 1 | 36 | ULTIMATE UKHLIIN_TEMDEG requires l6 |
| u2 | Сумын Борооны Цаг | ULTIMATE | 4 | 1 | 36 | ULTIMATE SUM_BORON requires m6 |
| u3 | Хэтийн Харваач | ULTIMATE | 4 | 1 | 36 | ULTIMATE KHETIIN_KHARVAACH requires r6 |

### Бөө (boo)

| id | name | branch | cost | rank | level | effect |
|---|---|---|---|---|---|---|
| l1 | Өвгөдийн Адислал | DEFENSE | 1 | 3 | 3 | HEAL_POWER +10; HEALTH +2 |
| m1 | Тэнгэрийн Холбоо | SPELL | 1 | 3 | 3 | RESOURCE_MAX +20 |
| r1 | Сүнсний Хүч | OFFENSE | 1 | 3 | 3 | SPELL_DAMAGE +5 |
| l2b | Хөх Тэнгэр | DEFENSE | 1 | 2 | 6 | RESOURCE_REGEN +1 |
| l2 | Дулаан Сэтгэл | DEFENSE | 1 | 2 | 6 | HEALTH +4 |
| m2 | Залбирлын Тойрог | SPELL | 2 | 1 | 6 | SUNSNII_ZALBIRAL: RADIUS_PCT +40; SUNSNII_ZALBIRAL: DAMAGE_PCT +15 |
| r2 | Онгоны Нүд | OFFENSE | 1 | 2 | 6 | CRIT_CHANCE +4 |
| r2b | Хар Тамга | OFFENSE | 1 | 2 | 6 | SPELL_DAMAGE +6 |
| l3b | Сүнсэн Алхам | DEFENSE | 1 | 2 | 10 | MOVE_PCT +4 |
| l3 | Хамгаалагч Онгон | DEFENSE | 2 | 1 | 10 | proc DAMAGED 25% → SHIELD(4,5) cd 12s |
| m3 | Сүнсний Урсгал | SPELL | 1 | 2 | 10 | COST_REDUCTION +5 |
| r3 | Сүнсний Гинж | OFFENSE | 2 | 1 | 10 | proc SPELL_HIT 30% → CHAIN(0.8,0) cd 3s |
| r3b | Шившлэгийн Хүч | OFFENSE | 1 | 2 | 10 | SPELL_DAMAGE +6 |
| l4 | Эдгээгчийн Адис | DEFENSE | 2 | 1 | 14 | proc CAST 35% → HEAL(3,0) cd 4s |
| m4 | Онгоны Түүх | SPELL | 2 | 1 | 14 | ONGONY_DUUDLAGA: DAMAGE_PCT +25; ONGONY_DUUDLAGA: REFUND +4 |
| r4 | Хэнгэргийн Цохилт | OFFENSE | 2 | 1 | 14 | KHENGERGIIN_DUU: DAMAGE_PCT +25; KHENGERGIIN_DUU: SLOW +3 |
| l5 | Өршөөл | DEFENSE | 2 | 2 | 20 | HEALTH +5; HEAL_POWER +10 (hidden) |
| l5b | Сүнс Цэвэрлэгч | DEFENSE | 2 | 1 | 20 | proc CAST 25% → CLEANSE(0,0) cd 8s |
| m5 | Алтан Хаалга | SPELL | 2 | 1 | 20 | TENGERIIN_KHAALGA: RADIUS_PCT +25; TENGERIIN_KHAALGA: DAMAGE_PCT +20 |
| r5b | Цус Сорогч Сүнс | OFFENSE | 2 | 1 | 20 | ONGONY_DUUDLAGA: HEAL_ON_HIT +1.5 |
| r5 | Тэнгэрийн Үг | OFFENSE | 2 | 1 | 20 | proc SPELL_HIT 20% → SMITE(1.2,0) cd 4s (hidden) |
| l6 | Сүнсний Тэнхээ | DEFENSE | 2 | 2 | 24 | HEALTH +5; DAMAGE_REDUCTION +3 |
| m6 | Өвгөдийн Хэнгэрэг | SPELL | 2 | 1 | 24 | KHENGERGIIN_DUU: COST_PCT -15; KHENGERGIIN_DUU: HEAL_ON_HIT +1 |
| r6 | Бөөгийн Тамга | OFFENSE | 2 | 2 | 24 | SPELL_DAMAGE +10; ATTACK_PCT +4 |
| l7 | Тэнгэртэй Холбогдох | KEYSTONE | 4 | 1 | 30 | KEYSTONE TENGERTEI_KHOLBOGDOKH |
| m7 | Тэнгэр Нээгдэх | SPELL | 3 | 1 | 30 | TENGERIIN_KHAALGA: DAMAGE_PCT +40; TENGERIIN_KHAALGA: ECHO_PCT +25; TENGERIIN_KHAALGA: COST_PCT -10 |
| r7 | Хар Бөө | OFFENSE | 3 | 1 | 30 | SPELL_DAMAGE +20; CRIT_DAMAGE +20 |
| u1 | Өвгөдийн Залбирал | ULTIMATE | 4 | 1 | 36 | ULTIMATE OVGODIIN_ZALBIRAL requires l6 |
| u2 | Тэнгэрийн Шийтгэл | ULTIMATE | 4 | 1 | 36 | ULTIMATE TENGERIIN_SHIITGEL requires m6 |
| u3 | Сүнсний Хөл | ULTIMATE | 4 | 1 | 36 | ULTIMATE SUNSNII_KHUL requires r6 |

### Дархан (darkhan)

| id | name | branch | cost | rank | level | effect |
|---|---|---|---|---|---|---|
| l1 | Төмөр Арьс | DEFENSE | 1 | 3 | 3 | ARMOR +4 |
| m1 | Гал Хөрөх | SPELL | 1 | 3 | 3 | RESOURCE_MAX +15 |
| r1 | Алх | OFFENSE | 1 | 3 | 3 | ATTACK_PCT +4 |
| l2b | Гангийн Бие | DEFENSE | 1 | 2 | 6 | HEALTH +4 |
| l2 | Гал Тэсвэр | DEFENSE | 1 | 2 | 6 | DAMAGE_REDUCTION +3 |
| m2 | Давталтын Дөл | SPELL | 2 | 1 | 6 | GALYN_DAVTALT: DAMAGE_PCT +15; GALYN_DAVTALT: BURN +3 |
| r2 | Цохиур | OFFENSE | 1 | 2 | 6 | CRIT_CHANCE +4 |
| r2b | Хайлсан Ирмэг | OFFENSE | 1 | 2 | 6 | CRIT_DAMAGE +15 |
| l3b | Хөвсгөр Тэсвэр | DEFENSE | 1 | 2 | 10 | KB_RESIST +30 |
| l3 | Дөшний Хатуу | DEFENSE | 1 | 2 | 10 | THORNS +12 |
| m3 | Үнсэн Дулаан | SPELL | 1 | 2 | 10 | RESOURCE_REGEN +1 |
| r3 | Дөлт Цохилт | OFFENSE | 2 | 1 | 10 | proc HIT 20% → IGNITE(4,0) cd 2s |
| r3b | Халуун Гар | OFFENSE | 1 | 2 | 10 | ATTACK_PCT +3 |
| l4 | Гангийн Сүнс | DEFENSE | 3 | 1 | 14 | proc LOW_HP 100% → SHIELD(8,6) cd 50s |
| m4 | Хатуулсан Бамбай | SPELL | 2 | 1 | 14 | GAN_BAMBAI: SHIELD +6; GAN_BAMBAI: COST_PCT -20 |
| r4 | Гал Цус | OFFENSE | 2 | 1 | 14 | proc CRIT 40% → AOE(0.8,3) cd 3s |
| l5 | Хүчит Хуяг | DEFENSE | 2 | 2 | 20 | ARMOR +6 (hidden) |
| l5b | Хайлуулах Дулаан | DEFENSE | 2 | 1 | 20 | proc KILL 100% → HEAL(4,0) cd 0s |
| m5 | Хайлсан Цутгалт | SPELL | 2 | 1 | 20 | KHAILSAN_TUMUR: BURN +5; KHAILSAN_TUMUR: DAMAGE_PCT +20 |
| r5b | Галт Хуяг | OFFENSE | 2 | 1 | 20 | proc DAMAGED 35% → AOE(0.8,3.5) cd 5s |
| r5 | Дөлийн Тэсрэлт | OFFENSE | 2 | 1 | 20 | proc CAST 40% → AOE(1,4) cd 6s (hidden) |
| l6 | Хөрөнгө | DEFENSE | 2 | 2 | 24 | HEALTH +6; DAMAGE_REDUCTION +3 |
| m6 | Дарханы Дөш | SPELL | 2 | 1 | 24 | DARKHANY_DARANGUI: DAMAGE_PCT +30; DARKHANY_DARANGUI: RADIUS_PCT +30 |
| r6 | Хүчтэй Гар | OFFENSE | 2 | 2 | 24 | ATTACK_PCT +8 |
| l7 | Мөнхийн Төмөр | DEFENSE | 3 | 1 | 30 | DAMAGE_REDUCTION +7; THORNS +15 |
| m7 | Давталтын Гал | SPELL | 3 | 1 | 30 | GALYN_DAVTALT: DAMAGE_PCT +35; GALYN_DAVTALT: RADIUS_PCT +30; GALYN_DAVTALT: ECHO_PCT +25 |
| r7 | Алтан Дөш | KEYSTONE | 4 | 1 | 30 | KEYSTONE ALTAN_DOSH |
| u1 | Бамбайн Хэрэм | ULTIMATE | 4 | 1 | 36 | ULTIMATE BAMBAIN_KHEREM requires l6 |
| u2 | Хайлсан Далай | ULTIMATE | 4 | 1 | 36 | ULTIMATE KHAILSAN_DALAI requires m6 |
| u3 | Мянган Алх | ULTIMATE | 4 | 1 | 36 | ULTIMATE MYANGAN_ALKH requires r6 |

### Хүлэгчин (khulegchin)

| id | name | branch | cost | rank | level | effect |
|---|---|---|---|---|---|---|
| l1 | Хурдан Хөл | DEFENSE | 1 | 3 | 3 | MOVE_PCT +5 |
| m1 | Давхианы Тэнхээ | SPELL | 1 | 3 | 3 | RESOURCE_MAX +15 |
| r1 | Хурц Жад | OFFENSE | 1 | 3 | 3 | ATTACK_PCT +4 |
| l2b | Морин Хуяг | DEFENSE | 1 | 2 | 6 | ARMOR +4 |
| l2 | Салхин Хөл | DEFENSE | 1 | 2 | 6 | HEALTH +4 |
| m2 | Давхилтын Хүч | SPELL | 2 | 1 | 6 | KHURDAN_DOVTOLGOO: DAMAGE_PCT +20; KHURDAN_DOVTOLGOO: RADIUS_PCT +25 |
| r2 | Хурц Харц | OFFENSE | 1 | 2 | 6 | CRIT_CHANCE +4 |
| r2b | Хүнд Давхилт | OFFENSE | 1 | 2 | 6 | CRIT_DAMAGE +15 |
| l3b | Хөнгөн Мөр | DEFENSE | 1 | 2 | 10 | KB_RESIST +25; MOVE_PCT +2 |
| l3 | Давхианы Амьсгал | DEFENSE | 1 | 2 | 10 | RESOURCE_REGEN +1 |
| m3 | Салхины Захиас | SPELL | 1 | 2 | 10 | COST_REDUCTION +5 |
| r3 | Давхих Хүч | OFFENSE | 2 | 1 | 10 | proc HIT 20% → SPEED(1,3) cd 6s |
| r3b | Зэр Зэвсэг | OFFENSE | 1 | 2 | 10 | ATTACK_PCT +3 |
| l4 | Морины Сүнс | DEFENSE | 2 | 1 | 14 | proc DAMAGED 30% → SPEED(2,3) cd 10s |
| m4 | Салхи Хурд | SPELL | 2 | 1 | 14 | SALKHINY_KHURD: HASTE +5; SALKHINY_KHURD: SHIELD +4; SALKHINY_KHURD: COST_PCT -15 |
| r4 | Урсгал Давалгаа | OFFENSE | 2 | 1 | 14 | proc CRIT 40% → CHAIN(0.8,0) cd 3s |
| l5 | Нүүдэлчний Эрүүл | DEFENSE | 2 | 2 | 20 | HEALTH +5 (hidden) |
| l5b | Дүүргэх Цус | DEFENSE | 2 | 1 | 20 | proc KILL 100% → HEAL(4,0) cd 0s |
| m5 | Татах Жад | SPELL | 2 | 1 | 20 | ZHADNY_SHIDELT: DAMAGE_PCT +25; ZHADNY_SHIDELT: PULL +1 |
| r5b | Жадны Ширхэг | OFFENSE | 2 | 1 | 20 | ZHADNY_SHIDELT: VULN +20; ZHADNY_SHIDELT: WEAKEN +4 |
| r5 | Давхианы Сүр | OFFENSE | 2 | 1 | 20 | proc CAST 40% → RESOURCE(12,0) cd 5s (hidden) |
| l6 | Тал Нутгийн Тэсвэр | DEFENSE | 2 | 2 | 24 | HEALTH +5; DAMAGE_REDUCTION +3 |
| m6 | Адууны Дайралт | SPELL | 2 | 1 | 24 | KHULGIIN_DAIRALT: DAMAGE_PCT +30; KHULGIIN_DAIRALT: RADIUS_PCT +25; KHULGIIN_DAIRALT: KNOCKUP +2 |
| r6 | Давхианы Үхэл | OFFENSE | 2 | 2 | 24 | ATTACK_PCT +8 |
| l7 | Талын Салхи | KEYSTONE | 4 | 1 | 30 | KEYSTONE TALYN_SALKHI |
| m7 | Хурдан Давхилт | SPELL | 3 | 1 | 30 | KHURDAN_DOVTOLGOO: DAMAGE_PCT +30; KHURDAN_DOVTOLGOO: ECHO_PCT +30; KHURDAN_DOVTOLGOO: COST_PCT -15 |
| r7 | Хаадын Жад | OFFENSE | 3 | 1 | 30 | ATTACK_PCT +8; CRIT_CHANCE +6; CRIT_DAMAGE +20 |
| u1 | Салхины Гэгээн | ULTIMATE | 4 | 1 | 36 | ULTIMATE SALKHINY_GEGEEN requires l6 |
| u2 | Мянган Морь | ULTIMATE | 4 | 1 | 36 | ULTIMATE MYANGAN_MORI requires m6 |
| u3 | Шуурга Давхилт | ULTIMATE | 4 | 1 | 36 | ULTIMATE SHUURGA_DAVKHILT requires r6 |

### Universal (all classes)

| id | name | cost | rank | level | effect |
|---|---|---|---|---|---|
| x1 | Нүүдэлчний Мэдлэг | 1 | 3 | 1 | EXP_PCT +4 per rank |
| x2 | Олзны Нүд | 1 | 3 | 5 | LOOT_PCT +5 per rank |
| x3 | Уулын Гар | 1 | 3 | 5 | MINING_SPEED_PCT +15 per rank |
| x4 | Салхин Мөр | 1 | 3 | 10 | DODGE_PCT +2 per rank |
| x5 | Цаг Хугацааны Эзэн | 1 | 3 | 20 | COOLDOWN_REDUCTION +5 per rank |

## Ultimates (F, 60 resource, 45 s cooldown)

* **Чингисийн Уур** (Baatar, `CHINGISIIN_UUR`): 12 секунд: Хүч II, Хамгаалалт I, Сэргэлт II.
* **Бүхний Нурал** (Baatar, `BUKHNII_NURAL`): 8 блок хүрээнд газрыг хагалан 6 дахин хүчтэй цохиж, дайснуудыг дээш хөөнө.
* **Үхэшгүй Эр** (Baatar, `UKHEL_UNDER`): 8 секунд: Хамгаалалт IV ба 10 ❤ нэмэлт амь.
* **Сумын Борооны Цаг** (Mergen, `SUM_BORON`): Зорьсон газарт 5 секунд сум бороо шиг асгарна.
* **Хэтийн Харваач** (Mergen, `KHETIIN_KHARVAACH`): 10 секунд: сум бүр +50% хүчтэй, Хурд II.
* **Үхлийн Тэмдэг** (Mergen, `UKHLIIN_TEMDEG`): 24 блок дотор бүх дайсныг тэмдэглэнэ: 10 секунд +40% хохирол авна.
* **Өвгөдийн Залбирал** (Boo, `OVGODIIN_ZALBIRAL`): 14 блок дотор нөхдийг бүрэн эдгээж, муу нөлөөг арилгана.
* **Тэнгэрийн Шийтгэл** (Boo, `TENGERIIN_SHIITGEL`): 20 блок дотор 8 дайсан руу тэнгэрээс аянга буулгана.
* **Сүнсний Хөл** (Boo, `SUNSNII_KHUL`): 8 секунд сүнс болно: Хурд III, Хамгаалалт II, Сэргэлт III.
* **Хайлсан Далай** (Darkhan, `KHAILSAN_DALAI`): 5 секунд 7 блок хүрээнд хайлсан төмрөөр шатаана.
* **Бамбайн Хэрэм** (Darkhan, `BAMBAIN_KHEREM`): 8 секунд: Хамгаалалт III, 8 ❤ нэмэлт амь, авсан хохирлын 30% буцаана.
* **Мянган Алх** (Darkhan, `MYANGAN_ALKH`): Ойролцоох дайснуудын дээр 10 дөш унана.
* **Шуурга Давхилт** (Khulegchin, `SHUURGA_DAVKHILT`): Урагш гурван удаа давхиж замд таарсныг цохино.
* **Салхины Гэгээн** (Khulegchin, `SALKHINY_GEGEEN`): 10 секунд: Хурд IV, Хүч I, үсрэлт өндөр.
* **Мянган Морь** (Khulegchin, `MYANGAN_MORI`): Бүх 8 зүг рүү сүнсэн адуу давхиж цохино.

## Keystones

* **Мөнх Тэсвэр** (Baatar): Амь 30%-аас доош бол авах хохирол -35%, гэвч авах эдгээлт 50%-иар буурна.
* **Нэг Сумны Хувь** (Mergen): Энгийн сум +40% хохирол өгнө, гэвч Олон Сум -30% сул болно.
* **Тэнгэртэй Холбогдох** (Boo): Сүнсний нөөц +40, секундэд +3 сэргэнэ, гэвч дээд амь -25%.
* **Алтан Дөш** (Darkhan): Хуяг зэвсгийн элэгдэл 60%-иар буурч, шидийн хүч +15%, шидийн шатаалт +3 сек.
* **Талын Салхи** (Khulegchin): Морин дээр байхад хурд +60%, гэвч явган үед хурд -10%.

## Reading the map

* gold line = learned path, turquoise = next step you can take, brown = not reached, red = exclusive choice
* learned nodes glow; a stack number is the rank; grey pane = not reachable yet; red pane = a rival is learned
* left click learn / rank up, right click refund one rank, Shift+click details in chat
* toolbar: pan ◀▲▼▶, zoom, search (type in chat), category filter, points panel (click for the spell list), respec and builds
