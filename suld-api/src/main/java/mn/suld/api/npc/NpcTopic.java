package mn.suld.api.npc;

import java.util.List;

/** A «💬» topic an NPC can be asked about: a title and two or three paragraphs. */
public record NpcTopic(String title, List<NpcLine> paragraphs) {

    public NpcTopic {
        if (title == null || title.isBlank()) throw new IllegalArgumentException("a topic needs a title");
        paragraphs = List.copyOf(paragraphs);
        if (paragraphs.isEmpty()) throw new IllegalArgumentException("topic «" + title + "» needs at least one paragraph");
    }
}
