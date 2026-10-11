package mn.suld.sim;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.loot.LootTier;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.Progression;
import mn.suld.api.progression.ProgressionEngine;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Golden tests: the live model mirrors the real game, the engine is deterministic, the proposed rules hold. */
class SimulationTest {

    static final LiveRules LIVE = new LiveRules();
    static final ProposedRules PROPOSED = new ProposedRules();

    @Test
    void liveCurveIsTheConfiguredOne() {
        long total = 0;
        for (int lv = 1; lv < 60; lv++) total += LIVE.curve().expForLevel(lv);
        assertEquals(28_315_352L, total, 5, "progression v2: 190·L^2.2 (Balance)");
        ExpGainResult r = new ProgressionEngine(LIVE.curve()).grant(new Progression(1, 0), total);
        assertEquals(60, r.after().level());
        assertEquals(0, r.wastedExp());
    }

    @Test
    void liveWorldMirrorsTheContentClasses() {
        World w = LIVE.world();
        assertEquals(4 + 4 * 3, w.zones().size(), "4 home regions + 4 outer lands in three level thirds");
        assertEquals(18, w.story().size());
        for (int i = 0; i < 18; i++) {
            assertEquals(mn.suld.plugin.content.QuestContent.STORY.chapters().get(i).expReward(), w.story().get(i).exp(), "chapter " + i);
        }
        for (World.Dungeon d : w.dungeons()) {
            var rung = mn.suld.api.balance.DungeonLadder.rung(d.id());
            assertEquals(mn.suld.api.balance.Rewards.dungeonExp(LIVE.curve(), rung.contentLevel()), d.completionExp(), d.id());
            assertEquals(rung.max(), d.max(), d.id());
        }
        assertEquals(List.of(3, 9, 15, 22, 29, 36, 42, 48, 54, 60), w.dungeons().stream().map(World.Dungeon::min).toList());
        assertEquals(60, w.maxContentLevel(), "the dungeon ladder ends at level 60 (Тэнгэрийн Ордон)");
        // every SÜLD mob hits with its designed attack (CombatListener.onMobHitsPlayer); bosses × their phases
        World.Mob khasar = w.dungeons().get(0).boss();
        var kb = mn.suld.plugin.content.SuldContent.KHASAR_BOSS;
        assertEquals(kb.mob().scaledAttack() * mn.suld.api.balance.MobScaling.hitScale(kb.mob().backingEntity(), true)
                * LiveRules.phaseAverage(kb.phases()), khasar.dmg(), 1e-9);
        var wolf = mn.suld.plugin.content.SuldContent.GOVIIN_CHONO;
        assertEquals(wolf.scaledAttack() * mn.suld.api.balance.MobScaling.hitScale("WOLF", false), w.mob("mob.goviin_chono").dmg(), 1e-9);
    }

    @Test
    void liveDungeonLootLevelIsClampedToTheBand() {
        World.Dungeon den = LIVE.world().dungeons().get(0);
        assertEquals(10, LIVE.dungeonLootLevel(den, 60), "DungeonDefinition.rewardLevel: boss level + 5");
        assertEquals(7, LIVE.dungeonLootLevel(den, 7), "below the band the player's own level");
        assertEquals(10, PROPOSED.dungeonLootLevel(PROPOSED.world().dungeons().get(0), 60), "proposed: clamped to the band");
    }

    @Test
    void sameSeedSameResult() {
        Engine.Result a = Engine.run(LIVE, Profile.hardcore(), PlayerClass.MERGEN, 99, 2);
        Engine.Result b = Engine.run(LIVE, Profile.hardcore(), PlayerClass.MERGEN, 99, 2);
        assertEquals(a.snaps(), b.snaps());
        Engine.Result c = Engine.run(PROPOSED, Profile.active(), PlayerClass.BOO, 7, 3);
        Engine.Result d = Engine.run(PROPOSED, Profile.active(), PlayerClass.BOO, 7, 3);
        assertEquals(c.snaps(), d.snaps());
    }

    @Test
    void cachedLootFollowsTheRarityBand() {
        // proposed dungeon bosses: rare 50 / epic 38 / legendary 10 / ancient 2 (gear share of a 1-roll table)
        Map<ItemRarity, Integer> n = new EnumMap<>(ItemRarity.class);
        SplittableRandom rng = new SplittableRandom(1);
        int gear = 0;
        for (int i = 0; i < 20_000; i++) {
            for (Loot.Drop d : PROPOSED.loot().roll("loot.p.boss.3", 25, LootTier.BOSS, PlayerClass.BAATAR, rng)) {
                if (!d.gear()) continue;
                gear++;
                n.merge(d.item().rarity(), 1, Integer::sum);
            }
        }
        assertTrue(gear > 15_000);
        double legendaryPlus = (n.getOrDefault(ItemRarity.LEGENDARY, 0) + n.getOrDefault(ItemRarity.ANCIENT, 0) + n.getOrDefault(ItemRarity.MYTHIC, 0)) / (double) gear;
        assertTrue(legendaryPlus < 0.2, "proposed boss band is legendary+ only ~12 % of the time, live ~100 %: " + legendaryPlus);
        assertEquals(0, n.getOrDefault(ItemRarity.MYTHIC, 0), "no mythic from a normal dungeon boss");
    }

    @Test
    void liveBossBandIsLegendaryOrBetter() {
        SplittableRandom rng = new SplittableRandom(2);
        int gear = 0, legendaryPlus = 0;
        for (int i = 0; i < 5_000; i++) {
            for (Loot.Drop d : LIVE.loot().roll("loot.mosun_khaan", 26, LootTier.BOSS, PlayerClass.BAATAR, rng)) {
                if (!d.gear()) continue;
                gear++;
                if (d.item().rarity().atLeast(ItemRarity.LEGENDARY)) legendaryPlus++;
            }
        }
        assertTrue(gear > 1_000);
        assertTrue(legendaryPlus > 0.9 * gear, "DG-3: live dungeon bosses roll the BOSS band (legendary 70 / ancient 25 / mythic 5)");
    }

    @Test
    void neitherHasDeadLevels() {
        int deadLive = 0, deadProposed = 0;
        for (int lv = 1; lv <= 60; lv++) {
            if (!covered(LIVE.world(), lv)) deadLive++;
            if (!covered(PROPOSED.world(), lv)) deadProposed++;
        }
        assertEquals(0, deadProposed);
        assertEquals(0, deadLive, "the outer lands carry live to 60");
    }

    static boolean covered(World w, int lv) {
        for (World.Zone z : w.zones()) for (World.Mob m : z.mobs()) if (!m.elite() && Math.abs(m.level() - lv) <= 3) return true;
        return false;
    }

    @Test
    void hardcoreWeekDoesNotFinishTheGame() {
        for (PlayerClass c : PlayerClass.values()) {
            Engine.Result r = Engine.run(PROPOSED, Profile.hardcore(), c, 1234 + c.ordinal(), 7);
            Map<String, Double> day7 = r.snaps().get(r.snaps().size() - 1);
            assertTrue(day7.get("level") < 60, c + " reached " + day7.get("level"));
            assertTrue(day7.get("maxGearPct") < 100);
            assertTrue(day7.get("ascension") == 0);
        }
    }

    @Test
    void liveIsNoLongerConsumedInDays() {
        Engine.Result r = Engine.run(LIVE, Profile.hardcore(), PlayerClass.BAATAR, 5, 3);
        assertTrue(r.player().level < 40, "three hardcore days no longer finish live (progression v2): " + r.player().level);
    }

    @Test
    void afkEarnsNoArmour() {
        Engine.Result r = Engine.run(PROPOSED, Profile.hardcore().exploit(Profile.Exploit.AFK), PlayerClass.DARKHAN, 3, 3);
        assertEquals(1, r.player().armorLevel);
        assertTrue(r.player().activeMinutes < 60, "AFK time is not active time");
    }

    @Test
    void deathLockCurvesAreMonotoneAndBounded() {
        for (ProposedRules.LockCurve c : ProposedRules.LockCurve.values()) {
            ProposedRules r = PROPOSED.withLock(c);
            double prev = 0;
            for (int lv = 1; lv <= 60; lv++) {
                double m = r.deathLockMinutes(lv, 0);
                assertTrue(m >= prev - 1e-9, c + " not monotone at " + lv);
                assertTrue(m <= 24 * 60 + 1e-9);
                prev = m;
            }
        }
        assertEquals(5, PROPOSED.withLock(ProposedRules.LockCurve.GEOMETRIC).deathLockMinutes(1, 0), 1e-9);
        assertEquals(24 * 60, PROPOSED.withLock(ProposedRules.LockCurve.GEOMETRIC).deathLockMinutes(60, 0), 1e-6);
    }

    @Test
    void contentCopyStaysBukkitFree() throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src/main/java/mn/suld/plugin/content"))) {
            for (Path f : files.toList()) {
                String text = Files.readString(f);
                assertFalse(text.contains("import org.bukkit") || text.contains("import io.papermc") || text.contains("import net.kyori"),
                        f + " must stay Bukkit-free: the simulator compiles it without the Paper API");
            }
        }
    }
}
