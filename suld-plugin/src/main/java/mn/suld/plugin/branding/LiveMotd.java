package mn.suld.plugin.branding;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * The server list's second line, chosen from what is happening (docs/DISCORD.md's sister for people not yet inside):
 * a world boss standing now, else an open Их Хуралдай vote, else a world boss rising within {@link #SOON_HOURS},
 * else the fallback (the static counts line). Pure, so the choice is unit-tested; the server list shows about 45
 * characters per line, so every line is short.
 */
public final class LiveMotd {

    public static final long SOON_HOURS = 6;
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final String[] DAYS = {"Да", "Мя", "Лх", "Пү", "Ба", "Бя", "Ня"};

    private LiveMotd() {
    }

    /** What the list should say now. Every argument may be empty; {@code fallback} is returned when nothing is on. */
    public static String pick(Optional<String> standingBoss, Optional<Instant> voteClosesAt, Optional<String> nextBoss,
                              Optional<Instant> nextBossAt, Instant now, ZoneId zone, String fallback) {
        if (standingBoss.isPresent()) return "⚔ «" + standingBoss.get() + "» босоод байна — ирээрэй!";
        if (voteClosesAt.isPresent() && voteClosesAt.get().isAfter(now)) {
            return "✦ Их Хуралдай: санал хураалт " + when(voteClosesAt.get(), zone) + " хүртэл";
        }
        if (nextBoss.isPresent() && nextBossAt.isPresent() && nextBossAt.get().isAfter(now)
                && nextBossAt.get().getEpochSecond() - now.getEpochSecond() <= SOON_HOURS * 3600) {
            return "⚔ «" + nextBoss.get() + "» " + when(nextBossAt.get(), zone) + " босно";
        }
        return fallback;
    }

    /** «Ня 20:00» in the server's zone. */
    public static String when(Instant at, ZoneId zone) {
        ZonedDateTime z = at.atZone(zone);
        return day(z.getDayOfWeek()) + " " + HOUR.format(z);
    }

    static String day(DayOfWeek d) {
        return DAYS[d.getValue() - 1];
    }
}
