package mn.suld.api.classgear;

import mn.suld.api.clazz.PlayerClass;
import mn.suld.api.item.ItemCatalog;
import mn.suld.api.item.ItemCatalogLoader;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemGenerator;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.item.ItemStat;
import mn.suld.api.item.ItemValidator;
import mn.suld.api.loot.Rng;
import mn.suld.api.mob.MobTier;
import mn.suld.api.skill.tree.StatKey;
import mn.suld.api.style.Cosmetic;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MasteryAndTiersTest {

    static final ItemCatalog CAT = ItemCatalogLoader.load(ItemCatalogLoader.classpath()).catalog();

    @Test
    void armourTierNamesNeverEqualARarityName() {
        Set<String> rarities = new HashSet<>();
        for (ItemRarity r : ItemRarity.values()) rarities.add(r.displayName());
        for (Cosmetic.Rarity r : Cosmetic.Rarity.values()) rarities.add(r.displayName());
        for (ArmorTier t : ArmorTier.values()) {
            assertFalse(rarities.contains(t.displayName()), t + " " + t.displayName() + " collides with a rarity name");
            for (String r : rarities) assertFalse(t.displayName().contains(r) || r.contains(t.displayName()), t + " vs " + r);
        }
        assertEquals(List.of("Эхлэл", "Сайжруулсан", "Элчин", "Хааны", "Тэнгэрлэг", "Дээдэс"),
                java.util.Arrays.stream(ArmorTier.values()).map(ArmorTier::displayName).toList());
    }

    @Test
    void weaponsHaveSixTiersAtTheArmourTierLevels() {
        assertEquals(1, WeaponTiers.tierFor(1));
        assertEquals(1, WeaponTiers.tierFor(11));
        assertEquals(2, WeaponTiers.tierFor(12));
        assertEquals(4, WeaponTiers.tierFor(36));
        assertEquals(6, WeaponTiers.tierFor(60));
        for (int t = 2; t <= 6; t++) assertEquals(ArmorTier.of(t).armorLevel(), WeaponTiers.LEVEL[t], "same depth as the armour");
        ItemGenerator gen = new ItemGenerator(CAT);
        ItemValidator val = new ItemValidator(CAT);
        UUID me = UUID.randomUUID();
        for (PlayerClass c : PlayerClass.values()) {
            double lastDamage = 0;
            for (int t = 1; t <= WeaponTiers.MAX_TIER; t++) {
                ItemDefinition d = CAT.require("weapon.class." + c.id() + "." + t);
                assertTrue(d.levelReq() <= WeaponTiers.LEVEL[t], d.id() + " must be wieldable when it is granted");
                ItemInstance i = gen.generate(d, d.rarity(), Math.max(d.levelReq(), WeaponTiers.LEVEL[t]), Rng.seeded(t), "upgrade", me);
                assertEquals(List.of(), val.problems(i), d.id());
                assertTrue(i.soulbound());
                double dmg = d.stats().get(ItemStat.DAMAGE).max();
                assertTrue(dmg > lastDamage, d.id() + " grows");
                lastDamage = dmg;
                assertEquals(871000 + c.ordinal() * 10 + t, d.model(), d.id() + " model");
            }
        }
    }

    @Test
    void masteryNeedsActivityAndMilestones() {
        assertEquals(300, MasteryRules.need(0));
        assertEquals(0, MasteryRules.need(10));
        long cum = 0;
        for (int r = 0; r < 10; r++) cum += MasteryRules.need(r);
        assertTrue(cum > 90_000, "about 94k mastery XP to rank 10: " + cum);
        assertEquals(0, MasteryRules.killXp(MobTier.NORMAL, 10, 20), "far below the player: nothing");
        assertEquals(1, MasteryRules.killXp(MobTier.NORMAL, 17, 20));
        assertEquals(4, MasteryRules.killXp(MobTier.ELITE, 20, 20));
        assertEquals(10, MasteryRules.killXp(MobTier.BOSS, 20, 20));
        // a level-5 player cannot pass rank 0 whatever the XP (rank 1 needs level 6)
        MasteryRules.Gain g = MasteryRules.gain(ClassGear.NONE, 1_000_000, 5);
        assertEquals(0, g.after().mastery());
        assertEquals(300, g.after().masteryXp(), 1e-9, "one rank's worth kept");
        assertEquals(1, MasteryRules.settle(g.after(), 6).after().mastery());
        // rank 4 also needs 2 different dungeons
        ClassGear lvl = ClassGear.NONE.withMastery(3, 0);
        assertEquals(3, MasteryRules.gain(lvl, 1_000_000, 60).after().mastery());
        ClassGear two = lvl.withCleared("dungeon.a").withCleared("dungeon.b");
        assertEquals(4, MasteryRules.gain(two, MasteryRules.need(3), 60).after().mastery());
        assertEquals(10, MasteryRules.gain(two.withCleared("dungeon.c").withCleared("dungeon.d").withCleared("dungeon.e")
                .withCleared("dungeon.f").withCleared("dungeon.g").withCleared("dungeon.h"), 1_000_000, 60).after().mastery());
        assertTrue(MasteryRules.milestone(10, 60, 8));
        assertFalse(MasteryRules.milestone(10, 59, 8), "rank 10 needs level 60: never within 7 days");
    }

    @Test
    void perMinuteCapsStopSpam() {
        MasteryRules.MinuteCap cap = new MasteryRules.MinuteCap(MasteryRules.CASTS_PER_MINUTE);
        int counted = 0;
        for (int i = 0; i < 100; i++) if (cap.take(1_000 + i * 100L)) counted++;
        assertEquals(12, counted, "100 casts in 10 s: 12 count");
        assertTrue(cap.take(61_000 + 1_000), "the next minute counts again");
    }

    @Test
    void perksOpenAtThreeSixNineThroughTheExistingBuild() {
        for (PlayerClass c : PlayerClass.values()) {
            List<MasteryPerks.Perk> perks = MasteryPerks.of(c);
            assertEquals(List.of(3, 6, 9), perks.stream().map(MasteryPerks.Perk::rank).toList(), c.name());
            assertTrue(MasteryPerks.open(c, 2).isEmpty());
            assertEquals(3, MasteryPerks.open(c, 10).size());
        }
        assertEquals(15, MasteryPerks.stats(PlayerClass.BAATAR, 6).get(StatKey.RESOURCE_MAX));
        assertTrue(MasteryPerks.stats(PlayerClass.BAATAR, 5).isEmpty());
        assertEquals(1.10, MasteryPerks.resourceGain(PlayerClass.BAATAR, 3), 1e-9);
        assertEquals(1.0, MasteryPerks.resourceGain(PlayerClass.MERGEN, 9), 1e-9);
        assertEquals(1.10, MasteryPerks.resourceRegen(PlayerClass.MERGEN, 3), 1e-9);
        assertEquals(1.025, MasteryRules.powerFactor(10), 1e-9);
    }

    @Test
    void tierGatesIncludeMastery() {
        ClassGear g = ClassGear.NONE.withProgress(24, 0).withTier(ArmorTier.T2).withCleared("dungeon.mosun_orgil");
        ArmorRules.Holdings rich = new ArmorRules.Holdings(1_000_000, 99, 0);
        assertFalse(ArmorRules.canUpgrade(g, rich), "T3 needs mastery 1");
        assertTrue(ArmorRules.canUpgrade(g.withMastery(1, 0), rich));
    }

    @Test
    void masterySurvivesTheRecordAndOldRecordsReadAsZero() {
        ClassGear g = ClassGear.NONE.withMastery(4, 123.5).withCleared("dungeon.x");
        assertEquals(g, ClassGear.fromJson(g.toJson()));
        ClassGear old = ClassGear.fromJson("{\"v\":1,\"al\":7,\"xp\":2,\"t\":1,\"e\":0,\"p\":{},\"c\":[],\"r\":[]}");
        assertEquals(0, old.mastery());
        assertEquals(7, old.armorLevel());
    }
}
