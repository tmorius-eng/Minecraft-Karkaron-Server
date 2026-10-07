# Шагай буулгах (casting the ankle bones)

Шагай are sheep ankle bones. Mongolians play many games with them and cast them to read a fortune. That custom is
VERIFIED tradition (shagai shooting, шагай харвах, is on UNESCO's intangible heritage list). Each bone can land on
four sides, named horse (морь), camel (тэмээ), sheep (хонь) and goat (ямаа). The horse and camel sides are narrow
and come up rarely.

## In SÜLD

`/shagai` casts four bones once per UTC day. Souls cannot cast. The faces show as a title and in chat.

The side chances are a game approximation of real bones: horse 10 %, camel 12 %, sheep 39 %, goat 39 %.

What a cast means is game fiction, read by the pure, tested `mn.suld.api.worldevent.Shagai`:

| Cast | Blessing |
|---|---|
| four horses | +10 % EXP for 60 min |
| three horses | +8 % for 40 min |
| four alike (not horses) | +5 % for 30 min |
| four different | +5 % for 30 min |
| two horses | +3 % for 20 min |
| one horse | +2 % for 10 min |
| no horse | nothing; cast again tomorrow |

On average a cast gives about 1–2 % EXP, a daily nudge and not a power source (`ShagaiTest`).

The blessing is its own source in `ProgressionBoosts`, so it adds to an ovoo's blessing instead of replacing it. It
is kept on the player (PDC) and comes back on rejoin until it runs out. The day of the last cast is kept on the
player too.

MANUAL_QA_REQUIRED: the title and sound in a real client.
