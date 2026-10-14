package mn.suld.plugin.persistence;

import mn.suld.api.persistence.AchievementRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** V28 deeds, achievements and the pass: every write commutes, a claim and an unlock happen once. */
class JdbcAchievementIT {

    private JdbcAchievementRepository repo;
    private final ExecutorService pool = Executors.newFixedThreadPool(8);

    @BeforeEach
    void freshSchema() throws Exception {
        TestDb.Db db = TestDb.fresh();
        assertEquals(SchemaMigrator.latestVersion(), new SchemaMigrator(db.ds(), db.dialect()).migrate());
        repo = new JdbcAchievementRepository(db.ds(), db.dialect(), pool);
    }

    @AfterEach
    void stop() {
        pool.shutdownNow();
    }

    @Test
    void countsAddBestsKeepTheGreaterAndAnUnlockIsWrittenOnce() {
        UUID p = UUID.randomUUID();
        AchievementRepository.Stored empty = repo.load(p).join();
        assertTrue(empty.deeds().isEmpty() && empty.unlocked().isEmpty());
        repo.write(p, Map.of("DUNGEON_CLEAR|dungeon.khasar_den", 2L), Map.of("LEVEL|", 12L), Map.of("ach.battle.first_clear", 100L), 1).join();
        repo.write(p, Map.of("DUNGEON_CLEAR|dungeon.khasar_den", 3L), Map.of("LEVEL|", 9L), Map.of("ach.battle.first_clear", 200L), 2).join();
        repo.write(p, Map.of("MOB_KILL|mob.goviin_chono", -5L), Map.of("LEVEL|", 30L), Map.of(), 3).join();
        AchievementRepository.Stored s = repo.load(p).join();
        assertEquals(5L, s.deeds().get("DUNGEON_CLEAR|dungeon.khasar_den"));
        assertEquals(30L, s.deeds().get("LEVEL|"), "the best stands; a lower value never lowers it");
        assertFalse(s.deeds().containsKey("MOB_KILL|mob.goviin_chono"), "a negative count writes nothing");
        assertEquals(Map.of("ach.battle.first_clear", 100L), s.unlocked(), "the first unlock's time stands");
    }

    @Test
    void concurrentCountsEndAtTheirSumAndBestsAtTheirMaximum() {
        UUID p = UUID.randomUUID();
        List<CompletableFuture<Void>> all = new ArrayList<>();
        long sum = 0;
        for (int i = 1; i <= 48; i++) {
            sum += i;
            all.add(repo.write(p, Map.of("GATHER|TOMRIIN_DARKHAN", (long) i), Map.of("CRAFT_LEVEL|TOMRIIN_DARKHAN", (long) (i % 37)), Map.of(), i));
        }
        CompletableFuture.allOf(all.toArray(CompletableFuture[]::new)).join();
        AchievementRepository.Stored s = repo.load(p).join();
        assertEquals(sum, s.deeds().get("GATHER|TOMRIIN_DARKHAN"));
        assertEquals(36L, s.deeds().get("CRAFT_LEVEL|TOMRIIN_DARKHAN"));
    }

    @Test
    void aSeasonsPointsAddAndARewardIsClaimedOnceEvenAtOnce() {
        UUID p = UUID.randomUUID();
        assertEquals(AchievementRepository.Pass.NONE, repo.pass(p, "S1").join());
        repo.addPoints(p, "S1", 700, 1).join();
        repo.addPoints(p, "S1", 600, 2).join();
        repo.addPoints(p, "S2", 50, 3).join();
        List<CompletableFuture<Boolean>> claims = new ArrayList<>();
        for (int i = 0; i < 16; i++) claims.add(repo.claim(p, "S1", "FREE/1", i));
        long made = claims.stream().map(CompletableFuture::join).filter(Boolean::booleanValue).count();
        assertEquals(1, made, "sixteen claims at once: one makes it");
        assertTrue(repo.openHeroic(p, "S1", "staff", 5).join());
        assertFalse(repo.openHeroic(p, "S1", "credits", 6).join(), "open once");
        AchievementRepository.Pass s1 = repo.pass(p, "S1").join();
        assertEquals(1300, s1.points());
        assertTrue(s1.heroic());
        assertEquals(java.util.Set.of("FREE/1"), s1.claimed());
        AchievementRepository.Pass s2 = repo.pass(p, "S2").join();
        assertEquals(50, s2.points());
        assertFalse(s2.heroic(), "each season its own");
        repo.reset(p).join();
        assertEquals(AchievementRepository.Pass.NONE, repo.pass(p, "S1").join());
        assertTrue(repo.load(p).join().deeds().isEmpty());
    }
}
