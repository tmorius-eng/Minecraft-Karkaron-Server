package mn.suld.api.dungeon;

import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DungeonLootLevelBandTest {

    private static DungeonDefinition dungeon(int minLevel, int bossLevel) {
        MobDefinition boss = new MobDefinition("mob.b", "B", "RAVAGER", MobTier.BOSS, bossLevel, 8, 1, 10, "loot.b");
        return new DungeonDefinition("dungeon.t", "T", minLevel, 1, 4, List.of(List.of("mob.w")),
                new BossDefinition(boss, List.of(new BossPhase(1.0, 1.0, "I")), 0), "loot.t");
    }

    @Test
    void anOverLevelledPlayerGetsTheDungeonsBandNotTheirOwnLevel() {
        DungeonDefinition first = dungeon(2, 5); // Хасарын Агуй: boss level 5
        assertEquals(10, first.rewardLevel(60)); // a level-60 farmer: level-10 gear at most
        assertEquals(10, first.rewardLevel(10));
        assertEquals(4, first.rewardLevel(4)); // a player at the dungeon's level: their own level
        assertEquals(60, dungeon(58, 60).rewardLevel(60)); // the last dungeon: full level-60 gear
        assertEquals(2, first.rewardLevel(0), "never below the dungeon's own minimum");
    }

    @Test
    void ladderDungeonsUseTheLadderBand() {
        MobDefinition boss = new MobDefinition("mob.b", "B", "RAVAGER", MobTier.BOSS, 8, 8, 1, 10, "loot.b");
        DungeonDefinition den = new DungeonDefinition("dungeon.khasar_den", "Хасарын Агуй", 3, 1, 4, List.of(List.of("mob.w")),
                new BossDefinition(boss, List.of(new BossPhase(1.0, 1.0, "I")), 0), "loot.t");
        assertEquals(10, den.maxLevel());
        assertEquals(10, den.rewardLevel(60));
        assertEquals(1, den.recommendedParty());
    }
}
