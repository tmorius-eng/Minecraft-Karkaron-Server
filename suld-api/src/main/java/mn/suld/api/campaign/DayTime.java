package mn.suld.api.campaign;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;

/**
 * A weekday and a wall-clock time, e.g. {@code "SATURDAY 20:00"}: the phase boundaries of the weekly cycle
 * (docs/KHURALDAI.md). Anchored to a week's Monday in the server zone.
 */
public record DayTime(DayOfWeek day, LocalTime time) {

    public static DayTime parse(String text) {
        if (text == null) throw new IllegalArgumentException("day-time is null");
        String[] p = text.trim().split("\\s+");
        if (p.length != 2) throw new IllegalArgumentException("day-time '" + text + "' is not '<WEEKDAY> HH:mm'");
        DayOfWeek d;
        try {
            d = DayOfWeek.valueOf(p[0].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("day-time '" + text + "': unknown weekday " + p[0]);
        }
        try {
            return new DayTime(d, LocalTime.parse(p[1]));
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException("day-time '" + text + "': time must be HH:mm");
        }
    }

    /** Parse, or the fallback when the text is malformed (the caller logs). */
    public static DayTime parseOr(String text, DayTime fallback) {
        try {
            return parse(text);
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    /** The instant of this day-time in the ISO week that starts on {@code monday} (a Monday). */
    public Instant at(LocalDate monday, ZoneId zone) {
        LocalDate date = monday.plusDays(day.getValue() - DayOfWeek.MONDAY.getValue());
        return date.atTime(time).atZone(zone).toInstant();
    }

    /** Minutes since Monday 00:00; the natural order of boundaries inside one week. */
    public int minuteOfWeek() {
        return (day.getValue() - 1) * 24 * 60 + time.getHour() * 60 + time.getMinute();
    }

    @Override
    public String toString() {
        return day + " " + String.format(Locale.ROOT, "%02d:%02d", time.getHour(), time.getMinute());
    }
}
