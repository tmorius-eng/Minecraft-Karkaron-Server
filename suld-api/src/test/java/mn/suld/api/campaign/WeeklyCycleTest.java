package mn.suld.api.campaign;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeeklyCycleTest {

    private static final ZoneId UB = ZoneId.of("Asia/Ulaanbaatar");
    private final WeeklyCycle cycle = new WeeklyCycle(UB, DayTime.parse("MONDAY 00:00"), DayTime.parse("SATURDAY 20:00"),
            DayTime.parse("SATURDAY 20:00"), DayTime.parse("SUNDAY 20:00"));

    private static Instant ub(int y, int m, int d, int h, int min) {
        return ZonedDateTime.of(y, m, d, h, min, 0, 0, UB).toInstant();
    }

    @Test
    void cycleIdsAreIsoWeeksInTheServerZone() {
        assertEquals("2026-W41", cycle.cycleId(ub(2026, 10, 11, 12, 0)), "Sunday 11 Oct 2026 is still ISO week 41");
        assertEquals("2026-W42", cycle.cycleId(ub(2026, 10, 12, 0, 0)), "Monday 00:00 UB starts the next week");
        assertEquals("2026-W41", cycle.cycleId(ub(2026, 10, 11, 23, 59)));
        // UTC Sunday 20:00 is already Monday 04:00 in Ulaanbaatar
        assertEquals("2026-W42", cycle.cycleId(Instant.parse("2026-10-11T20:00:00Z")));
        assertEquals(LocalDate.of(2026, 10, 5), cycle.monday("2026-W41"));
        assertEquals("2026-W42", cycle.nextCycleId("2026-W41"));
        assertEquals("2026-W40", cycle.previousCycleId("2026-W41"));
        assertEquals(41, WeeklyCycle.weekOf("2026-W41"));
    }

    @Test
    void yearRolloverKeepsIsoNumbering() {
        assertEquals("2026-W53", cycle.cycleId(ub(2026, 12, 31, 12, 0)), "2026 has 53 ISO weeks");
        assertEquals("2027-W01", cycle.nextCycleId("2026-W53"));
        assertEquals("2026-W53", cycle.previousCycleId("2027-W01"));
        assertEquals("2027-W01", cycle.cycleId(ub(2027, 1, 4, 0, 0)));
        assertEquals("2020-W53", cycle.cycleId(ub(2021, 1, 1, 12, 0)), "1 Jan 2021 belongs to 2020-W53");
    }

    @Test
    void phasesComeFromTheClock() {
        assertEquals(WeeklyCycle.Phase.CAMPAIGN, cycle.phase(ub(2026, 10, 5, 0, 0)), "Monday 00:00");
        assertEquals(WeeklyCycle.Phase.CAMPAIGN, cycle.phase(ub(2026, 10, 10, 19, 59)));
        assertEquals(WeeklyCycle.Phase.VOTING, cycle.phase(ub(2026, 10, 10, 20, 0)), "Saturday 20:00 the vote opens");
        assertEquals(WeeklyCycle.Phase.VOTING, cycle.phase(ub(2026, 10, 11, 19, 59)));
        assertEquals(WeeklyCycle.Phase.INTERLUDE, cycle.phase(ub(2026, 10, 11, 20, 0)), "Sunday 20:00 closed, nothing runs");
        assertEquals(WeeklyCycle.Phase.CAMPAIGN, cycle.phase(ub(2026, 10, 12, 0, 0)), "next Monday the winner starts");
        assertEquals(ub(2026, 10, 10, 20, 0), cycle.campaignEnds("2026-W41"));
        assertEquals(ub(2026, 10, 11, 20, 0), cycle.voteCloses("2026-W41"));
        assertEquals(ub(2026, 10, 5, 0, 0), cycle.campaignStarts("2026-W41"));
    }

    @Test
    void dayTimesParseAndOrder() {
        assertEquals("SATURDAY 20:00", DayTime.parse("saturday 20:00").toString());
        assertThrows(IllegalArgumentException.class, () -> DayTime.parse("Funday 20:00"));
        assertThrows(IllegalArgumentException.class, () -> DayTime.parse("MONDAY 25:00"));
        assertEquals(DayTime.parse("MONDAY 00:00"), DayTime.parseOr("nonsense", DayTime.parse("MONDAY 00:00")));
        assertThrows(IllegalArgumentException.class, () -> new WeeklyCycle(UB, DayTime.parse("SATURDAY 20:00"),
                DayTime.parse("MONDAY 00:00"), DayTime.parse("SATURDAY 20:00"), DayTime.parse("SUNDAY 20:00")), "ends before it starts");
    }
}
