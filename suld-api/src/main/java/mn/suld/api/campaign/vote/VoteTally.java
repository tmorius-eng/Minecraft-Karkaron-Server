package mn.suld.api.campaign.vote;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Counting the Хуралдай: the most votes wins; ties go to the option that won least recently, then ballot order. */
public final class VoteTally {

    private VoteTally() {
    }

    /**
     * @param options   the ballot, in order
     * @param counts    votes per option id (missing = 0)
     * @param minVoters the quorum; below it there is no winner and the rotation decides
     * @param recentWinCounts how often each option won in the recent cycles (tie-break: fewer wins first)
     */
    public static Optional<String> winner(List<String> options, Map<String, Integer> counts, int minVoters, Map<String, Integer> recentWinCounts) {
        int voters = 0;
        for (String o : options) voters += counts.getOrDefault(o, 0);
        if (voters < Math.max(0, minVoters) || options.isEmpty()) return Optional.empty();
        String best = null;
        for (String o : options) {
            if (best == null) {
                best = o;
                continue;
            }
            int c = counts.getOrDefault(o, 0), b = counts.getOrDefault(best, 0);
            if (c > b || c == b && recentWinCounts.getOrDefault(o, 0) < recentWinCounts.getOrDefault(best, 0)) best = o;
        }
        return Optional.ofNullable(best);
    }

    public static int voters(List<String> options, Map<String, Integer> counts) {
        int n = 0;
        for (String o : options) n += counts.getOrDefault(o, 0);
        return n;
    }
}
