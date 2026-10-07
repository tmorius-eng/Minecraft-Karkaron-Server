# NPC dialogue

Kharkhorum's people talk before they act (`NpcDialogue`, Paper's native dialog, no Citizens).

* Right-click opens a dialog with:
  * the speaker's name;
  * their trade's icon;
  * one of their greetings (the class shaman greets a player by the path they chose);
  * their **action button** (Дэлгүүр, Аяны зам, Өртөөгөөр явах, Засвар · Хуяг, Анги сонгох, Заавар нээх, Ивээл хүсэх);
  * one **💬 topic** per subject they know about. A topic opens 2–3 paragraphs, with «Буцах» and the action again.
* **Shift + right-click** skips the talk and runs the action at once.
* `npc.dialogue: false` turns the talk off.
* Esc closes the dialog. Callbacks are single-use and expire after 5 minutes.

| NPC | Topics |
|---|---|
| Ангийн Бөө | Таван зам (the five classes) |
| Хөтөч | Хархорум; Эхний алхам (first steps, lock-on, the first gate) |
| Анчдын Ахлагч | Их ан (the great battue hunt) |
| Худалдаачин | Торгоны зам (trade roads, the paiza) |
| Дархан | Дархан цол (the darkhan title) |
| Өртөөчин | Өртөө (the relay network) |
| Тэнгэрийн Тахилч | Мөнх Хөх Тэнгэр |

## History safety

Lines marked «түүхэн баримт» in game are limited to well-attested facts:
* Ögedei's walls and palace at Karakorum (1235);
* the battue hunt as military training;
* safe trade roads and the paiza in the empire;
* the darkhan title as an exemption from taxes and duties;
* the örtöö relay network founded under Ögedei;
* the veneration of Мөнх Хөх Тэнгэр.

SÜLD's own story is told as the speaker's belief or as SÜLD's tale, never as history:
* the city as a fictional reconstruction;
* the Blue Banner legend (Хөх Сүлд);
* the merchant's family.

No sacred symbol is shown.

MANUAL_QA_REQUIRED in a real client: the dialog layout, text length per line, icons.
