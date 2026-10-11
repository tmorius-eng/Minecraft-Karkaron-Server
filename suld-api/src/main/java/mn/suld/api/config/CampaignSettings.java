package mn.suld.api.config;

import mn.suld.api.campaign.DayTime;

/**
 * Их Хуралдай and the weekly campaign (docs/KHURALDAI.md). Times are {@code "<WEEKDAY> HH:mm"} in {@code daily.timezone}.
 *
 * @param minAccountDays   a character must be this many days old to vote (alt-account friction)
 * @param options          ballot size (2..3)
 * @param minVoters        quorum; below it the rotation picks
 * @param flushSeconds     how often buffered contributions reach the database
 * @param rewardClaimDays  days an offline player may still claim a tier reward on login (0 = online at resolution only)
 * @param effectCycles     how many later cycles a world effect stays (0 = permanent)
 */
public record CampaignSettings(boolean khuraldaiEnabled, DayTime voteOpens, DayTime voteCloses, int minLevel, int minAccountDays,
                               int options, int minVoters, boolean campaignEnabled, DayTime campaignStarts, DayTime campaignEnds,
                               int flushSeconds, int rewardClaimDays, int effectCycles) {

    public CampaignSettings {
        minLevel = Math.max(1, minLevel);
        minAccountDays = Math.max(0, minAccountDays);
        options = Math.max(2, Math.min(3, options));
        minVoters = Math.max(0, minVoters);
        flushSeconds = Math.max(5, Math.min(120, flushSeconds));
        rewardClaimDays = Math.max(0, Math.min(30, rewardClaimDays));
        effectCycles = Math.max(0, Math.min(52, effectCycles));
        if (voteOpens == null || voteCloses == null || campaignStarts == null || campaignEnds == null) throw new IllegalArgumentException("day-times");
    }

    public static CampaignSettings defaults() {
        return new CampaignSettings(true, DayTime.parse("SATURDAY 20:00"), DayTime.parse("SUNDAY 20:00"), 5, 1, 3, 3,
                true, DayTime.parse("MONDAY 00:00"), DayTime.parse("SATURDAY 20:00"), 10, 7, 1);
    }
}
