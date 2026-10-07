# SÜLD chat

SÜLD has its own chat design (no third-party chat plugin). The tabbed screen is an original design built on Paper's
native dialog API. It is in the spirit of chat-app style Minecraft chats, but no code or assets are copied.

## Channels

| Channel | Tag | Who hears it | Speak in it |
|---|---|---|---|
| Нийт (global) | `[Н]` gold | everyone | default; `!text` from any channel; `/g text` |
| Ойр (local) | `[О]` grey | players within 80 blocks in the same world | `/ch ойр`, `/l text` |
| Бүлэг (party) | `[Б]` blue | your party | `/ch бүлэг`, `/pc text` |
| Овог (clan) | `[Ов]` green | your clan | `/ch овог`, `/cc text` |
| Худалдаа (trade) | `[Х]` orange | everyone (trade ads, kept apart in the screen) | `/ch худалдаа`, `/tr text` |

* The **sticky channel** (`/ch <channel>`, or a tab in the screen) is stored in the player's data and survives
  relogs.
* `/g`, `/l`, `/pc` and `/tr` **without text** switch the sticky channel, like `/ch`.
* Party and clan chat without a party or clan is refused privately and nothing is sent.
* Local chat with nobody in range is still sent, with a hint that `!text` reaches everyone.
* Every line starts with the **speaker's head** (an Adventure player-head object component, no resource pack
  needed), then the coloured channel tag, then the existing SÜLD badges, clan tag, name, title and message.
  `chat.heads: false` turns the heads off.
* The chat guard (rate limit, repeats, links, caps) runs first, for every channel.

## The screen: `/chat [channel]`, or `/ch` with no argument

It is a native dialog, so Esc closes it and the game does not pause. It has:
* tabs Бүгд · Нийт · Ойр · Бүлэг · Овог · Худалдаа (the active tab is bold and in its colour);
* the last 12 lines of that tab, each with time, head, tag, name and text;
* the current speaking channel;
* a text box, «Илгээх ✉», «Шинэчлэх ⟳» and «Хаах».

**Бүгд** merges every channel the player could hear. **History is per listener**: each line records exactly who heard
it, so a party or clan line never shows up for someone outside it. Clicking a channel tab also makes it the speaking
channel. Sending reopens the screen with the new line in it.

## How it works (performance)

* Chat events are asynchronous, so routing never reads Bukkit state on the chat thread.
  * Once a second the main thread publishes an immutable snapshot of every online player: world, x/z, party id and
    clan id. This is one `HashMap` per second.
  * The async handler (`ChatChannels`, NORMAL priority) only reads that snapshot and narrows `event.viewers()`.
* The renderer (`ChatListener`, HIGH priority) prefixes the head and tag that were decided for that event.
* `ChatHistory` keeps at most 80 lines per channel (bounded, synchronized). Opening the screen builds a dialog from
  at most 12 of them.
* Dialog buttons are single-use callbacks that expire after 10 minutes, so an abandoned screen leaks nothing.

## Code

* `suld-api/.../chat/ChatChannel.java` and `ChatHistory.java`: pure; tests in `ChatChannelTest` and
  `ChatHistoryTest`.
* `suld-plugin/.../clan/ChatChannels.java`: routing, sticky channel, one-shot lines, history.
* `ChatScreen.java`: the dialog and the commands.
* `ChatListener.java`: rendering.

## QA

* **Verified with bots:** routing (who receives global, local, party and clan lines) and history visibility.
* **MANUAL_QA_REQUIRED in a real client:**
  * the dialog layout and tabs;
  * the heads in chat lines and in the screen;
  * that the text box sends;
  * that Esc closes the screen.
