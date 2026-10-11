package mn.suld.api.campaign;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;

/**
 * The weekly calendar of Их Хуралдай (docs/KHURALDAI.md): ISO weeks in the server zone, each a <em>cycle</em> named
 * {@code "2026-W41"}. Inside a week the campaign runs from {@code campaignStarts} to {@code campaignEnds}, and the vote
 * for the next week is open from {@code voteOpens} to {@code voteCloses}. Every phase is derived from the clock, never
 * from a timer, so a restart at any moment lands in the right phase.
 */
public final class WeeklyCycle {

    public enum Phase { CAMPAIGN, VOTING, INTERLUDE }

    private final ZoneId zone;
    private final DayTime campaignStarts, campaignEnds, voteOpens, voteCloses;

    public WeeklyCycle(ZoneId zone, DayTime campaignStarts, DayTime campaignEnds, DayTime voteOpens, DayTime voteCloses) {
        this.zone = zone;
        this.campaignStarts = campaignStarts;
        this.campaignEnds = campaignEnds;
        this.voteOpens = voteOpens;
        this.voteCloses = voteCloses;
        if (campaignStarts.minuteOfWeek() >= campaignEnds.minuteOfWeek()) throw new IllegalArgumentException("campaign must start before it ends");
        if (voteOpens.minuteOfWeek() >= voteCloses.minuteOfWeek()) throw new IllegalArgumentException("the vote must open before it closes");
    }

    public ZoneId zone() {
        return zone;
    }

    /** The cycle {@code now} falls in, e.g. {@code 2026-W41} (ISO week-based year and week, server zone). */
    public String cycleId(Instant now) {
        LocalDate d = now.atZone(zone).toLocalDate();
        // ISO week fields explicitly: a locale-driven formatter would start weeks on Sunday in some locales
        return String.format(Locale.ROOT, "%04d-W%02d", d.get(IsoFields.WEEK_BASED_YEAR), d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** The Monday a cycle starts on. */
    public LocalDate monday(String cycleId) {
        String[] p = cycleId.split("-W");
        if (p.length != 2) throw new IllegalArgumentException("cycle id " + cycleId);
        int year = Integer.parseInt(p[0]), week = Integer.parseInt(p[1]);
        return LocalDate.of(year, 1, 4).with(IsoFields.WEEK_OF_WEEK_BASED_YEAR, week).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public String nextCycleId(String cycleId) {
        return cycleId(monday(cycleId).plusWeeks(1).atStartOfDay(zone).toInstant());
    }

    public String previousCycleId(String cycleId) {
        return cycleId(monday(cycleId).minusWeeks(1).atStartOfDay(zone).toInstant());
    }

    public Instant campaignStarts(String cycleId) {
        return campaignStarts.at(monday(cycleId), zone);
    }

    public Instant campaignEnds(String cycleId) {
        return campaignEnds.at(monday(cycleId), zone);
    }

    public Instant voteOpens(String cycleId) {
        return voteOpens.at(monday(cycleId), zone);
    }

    public Instant voteCloses(String cycleId) {
        return voteCloses.at(monday(cycleId), zone);
    }

    /** The phase of the current cycle at {@code now}: campaign running, vote open, or neither. */
    public Phase phase(Instant now) {
        String id = cycleId(now);
        if (!now.isBefore(campaignStarts(id)) && now.isBefore(campaignEnds(id))) return Phase.CAMPAIGN;
        if (!now.isBefore(voteOpens(id)) && now.isBefore(voteCloses(id))) return Phase.VOTING;
        return Phase.INTERLUDE;
    }

    /** The week-based year of a cycle, for display ("2026 оны 41-р долоо хоног"). */
    public static int weekOf(String cycleId) {
        return Integer.parseInt(cycleId.substring(cycleId.indexOf("-W") + 2));
    }

}
