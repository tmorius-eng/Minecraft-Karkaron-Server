package mn.suld.api.campaign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Which campaigns stand for election this week. The last winner never runs again at once and a winner rests for its
 * {@code cooldownCycles}; last week's losers lead the ballot (a lost cause comes back, docs/KHURALDAI.md); the rest is
 * shuffled with the cycle id as the seed, so every server and every restart builds the same ballot.
 */
public final class CampaignRotation {

    private CampaignRotation() {
    }

    /**
     * @param all           every campaign definition, in file order
     * @param recentWinners winners of the most recent cycles, newest first (ids)
     * @param lastLosers    the options of the last vote that did not win (ids)
     * @param options       how many to put on the ballot (2..3)
     * @param cycleId       the cycle the ballot is for (the shuffle seed)
     */
    public static List<CampaignDefinition> pick(List<CampaignDefinition> all, List<String> recentWinners, List<String> lastLosers,
                                                int options, String cycleId) {
        int want = Math.max(2, Math.min(3, options));
        List<CampaignDefinition> eligible = new ArrayList<>();
        for (CampaignDefinition d : all) if (!resting(d, recentWinners)) eligible.add(d);
        if (eligible.size() < 2) { // too few campaigns to honour every cooldown: only the last winner rests
            eligible = new ArrayList<>();
            String last = recentWinners.isEmpty() ? null : recentWinners.get(0);
            for (CampaignDefinition d : all) if (!d.id().equals(last)) eligible.add(d);
        }
        if (eligible.size() < 2) eligible = new ArrayList<>(all); // a ballot of fewer than two is no vote

        List<CampaignDefinition> ballot = new ArrayList<>();
        Set<String> losers = new HashSet<>(lastLosers);
        for (CampaignDefinition d : eligible) if (losers.contains(d.id()) && ballot.size() < want) ballot.add(d);
        List<CampaignDefinition> rest = new ArrayList<>();
        for (CampaignDefinition d : eligible) if (!ballot.contains(d)) rest.add(d);
        java.util.Collections.shuffle(rest, new Random(cycleId.hashCode()));
        for (CampaignDefinition d : rest) if (ballot.size() < want) ballot.add(d);
        return ballot;
    }

    /** A winner rests for its cooldown: it may not run in the {@code cooldownCycles} ballots after the one it won. */
    static boolean resting(CampaignDefinition d, List<String> recentWinners) {
        int cycles = Math.max(1, d.cooldownCycles()); // the last winner always sits out the next ballot
        for (int i = 0; i < Math.min(cycles, recentWinners.size()); i++) if (d.id().equals(recentWinners.get(i))) return true;
        return false;
    }
}
