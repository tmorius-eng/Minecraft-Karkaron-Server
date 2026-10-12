package mn.suld.api.worldboss;

import mn.suld.api.campaign.DayTime;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * When world bosses rise: fixed weekly hours in the server zone (config {@code worldboss.times}), and which boss takes
 * each hour. The bosses take turns in content order across the year's hours, so every boss comes round and a week
 * with two hours shows two different ones when there are two or more.
 */
public final class WorldBossSchedule {

    public record Slot(Instant at, int index) {
    }

    private final List<DayTime> times;
    private final ZoneId zone;

    public WorldBossSchedule(List<DayTime> times, ZoneId zone) {
        List<DayTime> sorted = new ArrayList<>(times);
        sorted.sort(Comparator.comparingInt(DayTime::minuteOfWeek));
        this.times = List.copyOf(sorted);
        this.zone = zone;
    }

    public boolean empty() {
        return times.isEmpty();
    }

    /** The first slot strictly after {@code now}, or null without times. */
    public Slot next(Instant now) {
        if (times.isEmpty()) return null;
        LocalDate monday = now.atZone(zone).toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int week = 0; week < 2; week++) {
            LocalDate m = monday.plusWeeks(week);
            for (DayTime t : times) {
                Instant at = t.at(m, zone);
                if (at.isAfter(now)) return new Slot(at, index(m, times.indexOf(t)));
            }
        }
        return null; // unreachable with at least one time
    }

    /** A running number of the slot over the year: the week's number times the hours per week, plus the hour. */
    private int index(LocalDate monday, int slotInWeek) {
        return monday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) * times.size() + slotInWeek;
    }

    /** The boss of a slot among {@code count} bosses. */
    public static int bossFor(Slot slot, int count) {
        return count <= 0 ? -1 : Math.floorMod(slot.index(), count);
    }
}
