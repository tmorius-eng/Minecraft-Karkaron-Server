package mn.suld.api.campaign;

import java.util.List;

/**
 * The result of a resolved campaign, built from the database totals: what the chronicle records and the server hears.
 *
 * @param top     the contributors in rank order (largest first)
 * @param topClan the clan with the most units, or null
 */
public record CampaignOutcome(String cycleId, CampaignDefinition campaign, boolean success, long total, long target,
                              List<Contribution> top, String topClan, int participants) {

    public CampaignOutcome {
        top = List.copyOf(top);
    }

    /** The headline the server hears. */
    public String headline() {
        String base = success
                ? "✦ «" + campaign.name() + "» амжилттай: " + total + "/" + target + " " + campaign.goal().unitWord() + ", " + participants + " баатар оролцов."
                : "«" + campaign.name() + "» дуусав: " + total + "/" + target + " " + campaign.goal().unitWord() + " — зорилгод хүрсэнгүй.";
        if (!top.isEmpty()) base += " Тэргүүн: " + top.get(0).name() + " (" + top.get(0).units() + ").";
        return base;
    }
}
