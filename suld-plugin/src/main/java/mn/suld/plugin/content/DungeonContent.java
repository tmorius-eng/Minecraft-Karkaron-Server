package mn.suld.plugin.content;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.loot.LootEntry;
import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;

import java.util.List;
import java.util.Map;

/**
 * The region dungeons after Khasar's Den: one per wild region, each a wave arena (where the party stands, outside
 * Kharkhorum) ending in a three-phase boss. Boss stats are the base values × the BOSS tier (health ×25, EXP ×10).
 */
public final class DungeonContent {

    private DungeonContent() {
    }

    /** Completion bonus paid to every participant who was not downed. */
    public record Completion(long exp, long coins) {
    }

    private static List<BossPhase> phases() {
        return List.of(new BossPhase(1.0, 1.0, "Сэрсэн"), new BossPhase(0.6, 1.3, "Уурласан"), new BossPhase(0.3, 1.6, "Галзуурсан"));
    }

    // ------------------------------------------------------------------ Говийн Булш (Gobi, level 8+)

    public static final MobDefinition SAND_KHAN = new MobDefinition("mob.elsnii_khaan", "Элсний Хаан — Булшны Эзэн", "HUSK",
            MobTier.BOSS, 12, 16.0, 0.36, 90, "loot.elsnii_khaan");

    public static final DungeonDefinition GOBI_TOMB = new DungeonDefinition("dungeon.govi_bulsh", "Говийн Булш", 8, 1, 4,
            List.of(List.of(WorldContent.SCORPION.id(), WorldContent.SCORPION.id(), WorldContent.SCORPION.id(), WorldContent.SCORPION.id()),
                    List.of(WorldContent.SAND_SPIRIT.id(), WorldContent.SAND_SPIRIT.id(), WorldContent.SAND_SPIRIT.id(),
                            WorldContent.SCORPION.id(), WorldContent.SCORPION.id()),
                    List.of(WorldContent.SAND_SPIRIT.id(), WorldContent.SAND_SPIRIT.id(), WorldContent.SAND_SPIRIT.id(), WorldContent.SAND_SPIRIT.id())),
            new BossDefinition(SAND_KHAN, phases(), 200),
            new LootTable("loot.dungeon.govi_bulsh", List.of(
                    new LootEntry(WorldContent.SCORPION_VENOM, 1.0, 9, 11),
                    new LootEntry(WorldContent.GOBI_DAGGER, 0.55, 10, 13),
                    new LootEntry(SuldContent.STEPPE_TALISMAN, 0.4, 9, 12))));

    // ------------------------------------------------------------------ Баавгайн Үүр (Khangai, level 14+)

    public static final MobDefinition FOREST_LORD = new MobDefinition("mob.oin_ezen", "Хар Баавгай — Ойн Эзэн", "POLAR_BEAR",
            MobTier.BOSS, 18, 26.0, 0.48, 130, "loot.oin_ezen");

    public static final DungeonDefinition BEAR_LAIR = new DungeonDefinition("dungeon.baavgain_uur", "Баавгайн Үүр", 14, 1, 4,
            List.of(List.of(WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id()),
                    List.of(WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.BEAR.id()),
                    List.of(WorldContent.BEAR.id(), WorldContent.BEAR.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id())),
            new BossDefinition(FOREST_LORD, phases(), 220),
            new LootTable("loot.dungeon.baavgain_uur", List.of(
                    new LootEntry(WorldContent.BEAR_PELT, 1.0, 15, 17),
                    new LootEntry(WorldContent.KHANGAI_AXE, 0.55, 16, 19),
                    new LootEntry(SuldContent.STEPPE_TALISMAN, 0.4, 15, 18))));

    // ------------------------------------------------------------------ Мөсөн Оргил (Altai, level 22+)

    public static final MobDefinition ICE_KHAN = new MobDefinition("mob.mosun_khaan", "Мөсөн Хаан — Оргилын Сахиул", "STRAY",
            MobTier.BOSS, 26, 40.0, 0.6, 200, "loot.mosun_khaan");

    public static final DungeonDefinition ICE_PEAK = new DungeonDefinition("dungeon.mosun_orgil", "Мөсөн Оргил", 22, 1, 4,
            List.of(List.of(WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id()),
                    List.of(WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.GIANT.id()),
                    List.of(WorldContent.GIANT.id(), WorldContent.GIANT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id())),
            new BossDefinition(ICE_KHAN, phases(), 240),
            new LootTable("loot.dungeon.mosun_orgil", List.of(
                    new LootEntry(WorldContent.ICE_STONE, 1.0, 23, 25),
                    new LootEntry(WorldContent.ALTAI_SPEAR, 0.45, 24, 27),
                    new LootEntry(SuldContent.KHASAR_HEART, 0.08, 24, 24))));

    // ------------------------------------------------------------------ registry

    /** Every dungeon in recommended order (Khasar's Den first). */
    public static final List<DungeonDefinition> ALL = List.of(SuldContent.KHASAR_DEN, GOBI_TOMB, BEAR_LAIR, ICE_PEAK);

    public static final List<MobDefinition> BOSSES = List.of(SAND_KHAN, FOREST_LORD, ICE_KHAN);

    /** Boss body drops (small; the real prize is the per-player completion reward). */
    public static final List<LootTable> BOSS_LOOT = List.of(
            new LootTable("loot.elsnii_khaan", List.of(new LootEntry(WorldContent.SCORPION_VENOM, 1.0, 10, 10))),
            new LootTable("loot.oin_ezen", List.of(new LootEntry(WorldContent.BEAR_PELT, 1.0, 16, 16))),
            new LootTable("loot.mosun_khaan", List.of(new LootEntry(WorldContent.ICE_STONE, 1.0, 24, 24))));

    private static final Map<String, Completion> COMPLETION = Map.of(
            SuldContent.KHASAR_DEN.id(), new Completion(SuldContent.KHASAR_DEN_COMPLETION_EXP, SuldContent.KHASAR_DEN_COMPLETION_CURRENCY),
            GOBI_TOMB.id(), new Completion(1200, 160),
            BEAR_LAIR.id(), new Completion(2600, 260),
            ICE_PEAK.id(), new Completion(4800, 420));

    public static Completion completion(String dungeonId) {
        return COMPLETION.getOrDefault(dungeonId, new Completion(SuldContent.KHASAR_DEN_COMPLETION_EXP, SuldContent.KHASAR_DEN_COMPLETION_CURRENCY));
    }

    /** The wild region a dungeon belongs to (where its quest tracker points). */
    public static String regionOf(String dungeonId) {
        return switch (dungeonId) {
            case "dungeon.govi_bulsh" -> WorldContent.GOBI.id();
            case "dungeon.baavgain_uur" -> WorldContent.KHANGAI.id();
            case "dungeon.mosun_orgil" -> WorldContent.ALTAI.id();
            default -> WorldContent.KHERLEN.id();
        };
    }

    /** Where to run it (shown in /dungeon list). */
    public static String where(String dungeonId) {
        return switch (dungeonId) {
            case "dungeon.govi_bulsh" -> "Говь (өмнө)";
            case "dungeon.baavgain_uur" -> "Хангай (хойд)";
            case "dungeon.mosun_orgil" -> "Алтай (баруун)";
            default -> "Хэрлэнгийн тал";
        };
    }
}
