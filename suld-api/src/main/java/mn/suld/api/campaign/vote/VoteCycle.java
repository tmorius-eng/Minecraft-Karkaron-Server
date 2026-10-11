package mn.suld.api.campaign.vote;

import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * One week's Хуралдай ({@code suld_khuraldai_cycles}): the ballot, when it opened and closes, and once closed the
 * tally and the winner. Closing is a compare-and-set on {@code version}.
 */
public record VoteCycle(String cycleId, List<String> options, long opensAt, long closesAt, @Nullable Long closedAt,
                        @Nullable String winner, Map<String, Integer> tally, int voters, long version) {

    public VoteCycle {
        options = List.copyOf(options);
        tally = tally == null ? Map.of() : Map.copyOf(tally);
    }

    public boolean closed() {
        return closedAt != null;
    }

    /** The options that did not win (empty while open). */
    public List<String> losers() {
        if (winner == null) return List.of();
        return options.stream().filter(o -> !o.equals(winner)).toList();
    }
}
