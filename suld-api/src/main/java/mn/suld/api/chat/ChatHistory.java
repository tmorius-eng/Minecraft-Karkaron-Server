package mn.suld.api.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The recent lines of every channel, for the chat screen's tabs. Each line remembers exactly who heard it, so a
 * player's history shows only what they could have seen (a party line never leaks into another player's view).
 * Bounded per channel; thread-safe (chat arrives on async threads).
 */
public final class ChatHistory {

    /** One line: when, who, where, what, and who heard it (null = everyone online). */
    public record Line(long at, UUID sender, String senderName, ChatChannel channel, String text, Set<UUID> heardBy) {
        public boolean visibleTo(UUID viewer) {
            return heardBy == null || heardBy.contains(viewer) || sender.equals(viewer);
        }
    }

    private final int perChannel;
    private final Map<ChatChannel, Deque<Line>> lines = new EnumMap<>(ChatChannel.class);

    public ChatHistory(int perChannel) {
        if (perChannel < 1) throw new IllegalArgumentException("perChannel");
        this.perChannel = perChannel;
        for (ChatChannel c : ChatChannel.values()) if (c.speakable()) lines.put(c, new ArrayDeque<>());
    }

    public synchronized void add(Line line) {
        if (!line.channel().speakable()) throw new IllegalArgumentException("not a speakable channel: " + line.channel());
        Deque<Line> q = lines.get(line.channel());
        q.addLast(new Line(line.at(), line.sender(), line.senderName(), line.channel(), line.text(),
                line.heardBy() == null ? null : Set.copyOf(line.heardBy())));
        while (q.size() > perChannel) q.removeFirst();
    }

    /** The last {@code max} lines of a channel (or of all channels, merged by time) that {@code viewer} could hear. */
    public synchronized List<Line> view(ChatChannel channel, UUID viewer, int max) {
        List<Line> out = new ArrayList<>();
        for (Map.Entry<ChatChannel, Deque<Line>> e : lines.entrySet()) {
            if (channel != ChatChannel.ALL && e.getKey() != channel) continue;
            for (Line l : e.getValue()) if (l.visibleTo(viewer)) out.add(l);
        }
        out.sort((a, b) -> Long.compare(a.at(), b.at()));
        return out.size() <= max ? Collections.unmodifiableList(out) : List.copyOf(out.subList(out.size() - max, out.size()));
    }
}
