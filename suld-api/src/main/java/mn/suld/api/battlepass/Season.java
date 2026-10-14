package mn.suld.api.battlepass;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;

/**
 * A pass season: {@code weeks} Хуралдай weeks (Monday to Sunday, the server's zone) counted from {@code epoch}'s Monday.
 * Season 1 begins on the epoch's week; a day before it belongs to season 0 (no pass yet).
 *
 * @param number 1, 2, … (0 before the first)
 * @param start  its first day (a Monday)
 * @param end    its last day (a Sunday)
 */
public record Season(int number, LocalDate start, LocalDate end) {

    /** The season {@code day} falls in. */
    public static Season of(LocalDate day, LocalDate epoch, int weeks) {
        if (weeks < 1) throw new IllegalArgumentException("weeks " + weeks);
        LocalDate first = epoch.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long days = ChronoUnit.DAYS.between(first, day);
        if (days < 0) return new Season(0, day, first.minusDays(1));
        int n = (int) (days / (7L * weeks)) + 1;
        LocalDate start = first.plusWeeks((long) (n - 1) * weeks);
        return new Season(n, start, start.plusWeeks(weeks).minusDays(1));
    }

    /** The id the database keeps a season's progress under: {@code S<number>}. */
    public String id() {
        return "S" + number;
    }

    /** Whole days left after {@code day} (0 on the last day). */
    public long daysLeft(LocalDate day) {
        return Math.max(0, ChronoUnit.DAYS.between(day, end));
    }
}
