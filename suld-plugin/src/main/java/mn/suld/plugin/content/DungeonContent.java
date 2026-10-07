package mn.suld.plugin.content;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
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
            "loot.dungeon.govi_bulsh");

    // ------------------------------------------------------------------ Баавгайн Үүр (Khangai, level 14+)

    public static final MobDefinition FOREST_LORD = new MobDefinition("mob.oin_ezen", "Хар Баавгай — Ойн Эзэн", "POLAR_BEAR",
            MobTier.BOSS, 18, 26.0, 0.48, 130, "loot.oin_ezen");

    public static final DungeonDefinition BEAR_LAIR = new DungeonDefinition("dungeon.baavgain_uur", "Баавгайн Үүр", 14, 1, 4,
            List.of(List.of(WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id()),
                    List.of(WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id(), WorldContent.BEAR.id()),
                    List.of(WorldContent.BEAR.id(), WorldContent.BEAR.id(), WorldContent.GREY_WOLF.id(), WorldContent.GREY_WOLF.id())),
            new BossDefinition(FOREST_LORD, phases(), 220),
            "loot.dungeon.baavgain_uur");

    // ------------------------------------------------------------------ Мөсөн Оргил (Altai, level 22+)

    public static final MobDefinition ICE_KHAN = new MobDefinition("mob.mosun_khaan", "Мөсөн Хаан — Оргилын Сахиул", "STRAY",
            MobTier.BOSS, 26, 40.0, 0.6, 200, "loot.mosun_khaan");

    public static final DungeonDefinition ICE_PEAK = new DungeonDefinition("dungeon.mosun_orgil", "Мөсөн Оргил", 22, 1, 4,
            List.of(List.of(WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id()),
                    List.of(WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.GIANT.id()),
                    List.of(WorldContent.GIANT.id(), WorldContent.GIANT.id(), WorldContent.ICE_SPIRIT.id(), WorldContent.ICE_SPIRIT.id())),
            new BossDefinition(ICE_KHAN, phases(), 240),
            "loot.dungeon.mosun_orgil");

    // ------------------------------------------------------------------ registry

    /**
     * Where each dungeon's gate stands (bearing from the spawn, distance) and how its hall looks
     * (docs/world/DUNGEON_HALLS.md): each inside its region, further out as the level rises.
     */
    public static final List<mn.suld.api.dungeon.hall.DungeonSite> SITES = List.of(
            new mn.suld.api.dungeon.hall.DungeonSite(SuldContent.KHASAR_DEN.id(), mn.suld.api.dungeon.hall.HallTheme.DEN, 75, 380),
            new mn.suld.api.dungeon.hall.DungeonSite("dungeon.govi_bulsh", mn.suld.api.dungeon.hall.HallTheme.TOMB, 165, 650),
            new mn.suld.api.dungeon.hall.DungeonSite("dungeon.baavgain_uur", mn.suld.api.dungeon.hall.HallTheme.LAIR, 20, 1000),
            new mn.suld.api.dungeon.hall.DungeonSite("dungeon.mosun_orgil", mn.suld.api.dungeon.hall.HallTheme.PEAK, 255, 1400),
            LadderContent.SITES.get(0), LadderContent.SITES.get(1), LadderContent.SITES.get(2),
            LadderContent.SITES.get(3), LadderContent.SITES.get(4), LadderContent.SITES.get(5));

    /** Every dungeon in recommended order (Khasar's Den first). */
    public static final List<DungeonDefinition> ALL = List.of(SuldContent.KHASAR_DEN, GOBI_TOMB, BEAR_LAIR, ICE_PEAK,
            LadderContent.DEEP, LadderContent.RUIN, LadderContent.REDROCK, LadderContent.SACRED, LadderContent.SKYSTAIR, LadderContent.PALACE);

    public static final List<MobDefinition> BOSSES = List.of(SAND_KHAN, FOREST_LORD, ICE_KHAN,
            LadderContent.LUS_KHAAN, LadderContent.BLACK_GENERAL, LadderContent.RED_LORD, LadderContent.MOUNTAIN_LORD,
            LadderContent.SKY_ENVOY, LadderContent.BANNER_GUARDIAN);

    /** The dungeon before {@code id} on the ladder (it must be cleared once first), or null for the first. */
    public static DungeonDefinition previous(String id) {
        for (int i = 1; i < ALL.size(); i++) if (ALL.get(i).id().equals(id)) return ALL.get(i - 1);
        return null;
    }

    private static final Map<String, Completion> COMPLETION = Map.of(
            SuldContent.KHASAR_DEN.id(), new Completion(SuldContent.KHASAR_DEN_COMPLETION_EXP, SuldContent.KHASAR_DEN_COMPLETION_CURRENCY),
            GOBI_TOMB.id(), new Completion(1200, 160),
            BEAR_LAIR.id(), new Completion(2600, 260),
            ICE_PEAK.id(), new Completion(4800, 420));

    public static Completion completion(String dungeonId) {
        Completion ladder = LadderContent.completion(dungeonId);
        if (ladder != null) return ladder;
        return COMPLETION.getOrDefault(dungeonId, new Completion(SuldContent.KHASAR_DEN_COMPLETION_EXP, SuldContent.KHASAR_DEN_COMPLETION_CURRENCY));
    }

    /** The wild region a dungeon belongs to (where its quest tracker points). */
    public static String regionOf(String dungeonId) {
        return switch (dungeonId) {
            case "dungeon.govi_bulsh" -> WorldContent.GOBI.id();
            case "dungeon.baavgain_uur" -> WorldContent.KHANGAI.id();
            case "dungeon.mosun_orgil", "dungeon.tengeriin_shat" -> WorldContent.ALTAI.id();
            case "dungeon.dalain_gun", "dungeon.tengeriin_ordon" -> WorldContent.KHANGAI.id();
            case "dungeon.khar_khot" -> WorldContent.GOBI.id();
            default -> WorldContent.KHERLEN.id();
        };
    }

    /** Where to run it (shown in /dungeon list). */
    public static String where(String dungeonId) {
        return switch (dungeonId) {
            case "dungeon.govi_bulsh" -> "Говь (өмнө)";
            case "dungeon.baavgain_uur" -> "Хангай (хойд)";
            case "dungeon.mosun_orgil" -> "Алтай (баруун)";
            default -> LadderContent.where(dungeonId) != null ? LadderContent.where(dungeonId) : "Хэрлэнгийн тал";
        };
    }
}
