package mn.suld.api.worldboss;

import mn.suld.api.campaign.DayTime;
import mn.suld.api.dungeon.BossPhase;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBossTest {

    private static final ZoneId UB = ZoneId.of("Asia/Ulaanbaatar");

    static WorldBossDefinition def() {
        return new WorldBossDefinition("worldboss.test", "mob.test", "site.test", 8, 0,
                List.of(new BossPhase(1.0, 1.0, "Сэрсэн"), new BossPhase(0.5, 1.3, "Уурласан")), 600, 900, 40, "loot.test", 1000, 200, 500);
    }

    @Test
    void definitionRules() {
        assertThrows(IllegalArgumentException.class, () -> new WorldBossDefinition("boss.x", "m", "s", 0, 0,
                List.of(new BossPhase(1.0, 1, "a")), 0, 900, 40, "l", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossDefinition("worldboss.x", "m", "s", 0, 0,
                List.of(new BossPhase(0.9, 1, "a")), 0, 900, 40, "l", 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldBossDefinition("worldboss.x", "m", "s", 0, 0,
                List.of(new BossPhase(1.0, 1, "a")), 900, 900, 40, "l", 0, 0, 0), "enrage must come before the withdrawal");
    }

    @Test
    void healthGrowsWithTheCrowdBetweenTwoAndTwenty() {
        assertEquals(0.1, WorldBossFight.healthScale(0), 1e-9);
        assertEquals(0.1, WorldBossFight.healthScale(2), 1e-9);
        assertEquals(0.5, WorldBossFight.healthScale(10), 1e-9);
        assertEquals(1.0, WorldBossFight.healthScale(40), 1e-9);
    }

    @Test
    void everyoneWhoTookPartIsRewardedAndThePodiumGoesByDamage() {
        Instant t0 = Instant.parse("2026-10-14T13:00:00Z");
        WorldBossFight f = new WorldBossFight(def(), "r1", t0);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), healer = UUID.randomUUID(), tourist = UUID.randomUUID(), late = UUID.randomUUID();
        f.damage(a, "A", 5000); f.present(a, "A", 300);
        f.damage(b, "B", 3000); f.present(b, "B", 280);
        f.damage(healer, "Эмч", 10); f.present(healer, "Эмч", 290);   // healed the whole fight
        f.damage(tourist, "Аялагч", 1); f.present(tourist, "Аялагч", 20); // walked past
        f.damage(late, "Хожуу", 900); f.present(late, "Хожуу", 110);    // came for the last third
        List<WorldBossFight.Reward> r = f.rewards(t0.plusSeconds(300));
        assertEquals(List.of("A", "B", "Хожуу", "Эмч"), r.stream().map(WorldBossFight.Reward::name).toList());
        assertEquals(1.5, r.get(0).multiplier());
        assertEquals(1.3, r.get(1).multiplier());
        assertEquals(1.2, r.get(2).multiplier());
        assertEquals(1.0, r.get(3).multiplier(), "the healer gets the full reward without the podium");
    }

    @Test
    void noPodiumWithoutDamage() {
        Instant t0 = Instant.parse("2026-10-14T13:00:00Z");
        WorldBossFight f = new WorldBossFight(def(), "r1", t0);
        UUID a = UUID.randomUUID(), healer = UUID.randomUUID();
        f.damage(a, "A", 5000); f.present(a, "A", 60);
        f.present(healer, "Эмч", 60);
        List<WorldBossFight.Reward> r = f.rewards(t0.plusSeconds(60));
        assertEquals(2, r.size());
        assertEquals(2, r.get(1).rank());
        assertEquals(1.0, r.get(1).multiplier(), "second of two, but no damage: no podium");
    }

    @Test
    void aShortFightAsksForLessTimeButNeverNone() {
        WorldBossFight.Fighter f = new WorldBossFight.Fighter(UUID.randomUUID(), "x", 100, 10);
        assertTrue(WorldBossFight.eligible(f, 30, 1000));
        assertFalse(WorldBossFight.eligible(new WorldBossFight.Fighter(UUID.randomUUID(), "y", 100, 0), 30, 1000));
        assertFalse(WorldBossFight.eligible(new WorldBossFight.Fighter(UUID.randomUUID(), "z", 100, 59), 900, 1000), "a long fight asks 60 s");
    }

    @Test
    void timeoutAtTheDuration() {
        Instant t0 = Instant.parse("2026-10-14T13:00:00Z");
        WorldBossFight f = new WorldBossFight(def(), "r1", t0);
        assertFalse(f.timedOut(t0.plusSeconds(899)));
        assertTrue(f.timedOut(t0.plusSeconds(900)));
    }

    @Test
    void theScheduleFindsTheNextHourAndTheBossesTakeTurns() {
        WorldBossSchedule s = new WorldBossSchedule(List.of(DayTime.parse("SATURDAY 18:00"), DayTime.parse("WEDNESDAY 21:00")), UB);
        Instant tuesday = ZonedDateTime.of(2026, 10, 13, 12, 0, 0, 0, UB).toInstant();
        WorldBossSchedule.Slot wed = s.next(tuesday);
        assertEquals(ZonedDateTime.of(2026, 10, 14, 21, 0, 0, 0, UB).toInstant(), wed.at());
        WorldBossSchedule.Slot sat = s.next(wed.at());
        assertEquals(ZonedDateTime.of(2026, 10, 17, 18, 0, 0, 0, UB).toInstant(), sat.at());
        WorldBossSchedule.Slot nextWed = s.next(sat.at());
        assertEquals(ZonedDateTime.of(2026, 10, 21, 21, 0, 0, 0, UB).toInstant(), nextWed.at(), "rolls into the next week");
        assertNotEquals(WorldBossSchedule.bossFor(wed, 2), WorldBossSchedule.bossFor(sat, 2), "two hours a week: two bosses");
        assertEquals(-1, WorldBossSchedule.bossFor(wed, 0));
        assertTrue(new WorldBossSchedule(List.of(), UB).empty());
    }
}
