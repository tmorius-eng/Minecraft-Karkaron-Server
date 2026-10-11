package mn.suld.plugin.content;

import mn.suld.api.dungeon.BossDefinition;
import mn.suld.api.dungeon.BossPhase;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.dungeon.hall.HallTheme;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.mob.MobTier;

import java.util.List;

/**
 * Dungeons 5–10 of the ladder (docs/DUNGEON_PROGRESSION_SPEC.md, docs/world/DUNGEON_LADDER.md): levels 29–60, each in
 * its own themed hall behind a gate, each with its own mobs and a three-phase boss. Places are real (Хөвсгөл, Хар
 * Хот, Бурхан Халдун); the creatures and their stories are SÜLD fiction (лус = water spirits and савдаг = mountain
 * spirits are folk-belief names used as INSPIRED creature types; Хар Жанжин is INSPIRED by the Khara-Khoto legend of
 * the Black General; the Хөх Сүлд palace is SÜLD fiction). Stats continue the live curve of dungeons 1–4.
 */
public final class LadderContent {

    private LadderContent() {
    }

    private static List<BossPhase> phases() {
        return List.of(new BossPhase(1.0, 1.0, "Сэрсэн"), new BossPhase(0.6, 1.3, "Уурласан"), new BossPhase(0.3, 1.6, "Галзуурсан"));
    }

    /** Numbers follow from level and tier (progression v2, MobScaling); hp/dmg/exp are the retired hand-set values. */
    private static MobDefinition mob(String id, String name, String host, MobTier tier, int level, double hp, double dmg, long exp) {
        return MobDefinition.designed(id, name, host, tier, level, "loot." + id.substring("mob.".length()));
    }

    // ------------------------------------------------------------------ 5. Далайн Гүн (29+): under Хөвсгөл

    public static final MobDefinition LUS = mob("mob.usny_lus", "Усны Лус", "DROWNED", MobTier.NORMAL, 30, 52, 9.5, 420);
    public static final MobDefinition DEEP_WOLF = mob("mob.dalain_chono", "Далайн Чоно", "WOLF", MobTier.ELITE, 32, 40, 6.5, 560);
    public static final MobDefinition LUS_KHAAN = mob("mob.lusyn_khaan", "Лусын Хаан — Далайн Эзэн", "DROWNED", MobTier.BOSS, 34, 54, 0.72, 280);

    public static final DungeonDefinition DEEP = new DungeonDefinition("dungeon.dalain_gun", "Далайн Гүн", 29, 1, 4,
            List.of(List.of(LUS.id(), LUS.id(), LUS.id(), LUS.id()),
                    List.of(LUS.id(), LUS.id(), DEEP_WOLF.id(), DEEP_WOLF.id()),
                    List.of(DEEP_WOLF.id(), DEEP_WOLF.id(), LUS.id(), LUS.id(), LUS.id())),
            new BossDefinition(LUS_KHAAN, phases(), 255), "loot.dungeon.dalain_gun");

    // ------------------------------------------------------------------ 6. Хар Хотын Балгас (36+): the ruined Tangut city

    public static final MobDefinition TANGUT_GHOST = mob("mob.tangud_suns", "Тангудын Сүнс", "HUSK", MobTier.NORMAL, 37, 62, 10.5, 520);
    public static final MobDefinition RUIN_SCORPION = mob("mob.balgasny_khilents", "Балгасны Аварга Хилэнц", "CAVE_SPIDER", MobTier.ELITE, 39, 44, 7.0, 680);
    public static final MobDefinition BLACK_GENERAL = mob("mob.khar_janjin", "Хар Жанжин — Балгасны Эзэн", "WITHER_SKELETON", MobTier.BOSS, 41, 64, 0.84, 330);

    public static final DungeonDefinition RUIN = new DungeonDefinition("dungeon.khar_khot", "Хар Хотын Балгас", 36, 1, 4,
            List.of(List.of(TANGUT_GHOST.id(), TANGUT_GHOST.id(), TANGUT_GHOST.id(), TANGUT_GHOST.id()),
                    List.of(RUIN_SCORPION.id(), RUIN_SCORPION.id(), TANGUT_GHOST.id(), TANGUT_GHOST.id()),
                    List.of(TANGUT_GHOST.id(), TANGUT_GHOST.id(), TANGUT_GHOST.id(), RUIN_SCORPION.id(), RUIN_SCORPION.id())),
            new BossDefinition(BLACK_GENERAL, phases(), 270), "loot.dungeon.khar_khot");

    // ------------------------------------------------------------------ 7. Улаан Хадны Хүрээ (42+): the red-cliff stronghold

    public static final MobDefinition RAIDER = mob("mob.khureenii_kharuul", "Хүрээний Харуул", "PILLAGER", MobTier.NORMAL, 43, 72, 11.5, 620);
    public static final MobDefinition WAR_WOLF = mob("mob.dainy_chono", "Дайны Чоно", "WOLF", MobTier.ELITE, 45, 50, 7.5, 800);
    public static final MobDefinition RED_LORD = mob("mob.ulaan_khadny_noyon", "Улаан Хадны Ноён", "VINDICATOR", MobTier.BOSS, 47, 74, 0.95, 380);

    public static final DungeonDefinition REDROCK = new DungeonDefinition("dungeon.ulaan_khad", "Улаан Хадны Хүрээ", 42, 1, 4,
            List.of(List.of(RAIDER.id(), RAIDER.id(), RAIDER.id(), WAR_WOLF.id()),
                    List.of(RAIDER.id(), RAIDER.id(), WAR_WOLF.id(), WAR_WOLF.id()),
                    List.of(RAIDER.id(), RAIDER.id(), RAIDER.id(), WAR_WOLF.id(), WAR_WOLF.id())),
            new BossDefinition(RED_LORD, phases(), 285), "loot.dungeon.ulaan_khad");

    // ------------------------------------------------------------------ 8. Бурхан Халдуны Агуй (48+): the sacred mountain's cave

    public static final MobDefinition SAVDAG = mob("mob.uulyn_savdag", "Уулын Савдаг", "STRAY", MobTier.NORMAL, 49, 82, 12.5, 720);
    public static final MobDefinition CAVE_BEAR = mob("mob.aguin_baavgai", "Агуйн Баавгай", "POLAR_BEAR", MobTier.ELITE, 51, 58, 8.0, 940);
    public static final MobDefinition MOUNTAIN_LORD = mob("mob.khangai_savdag", "Хангай Савдаг — Уулын Эзэн", "RAVAGER", MobTier.BOSS, 53, 84, 1.05, 430);

    public static final DungeonDefinition SACRED = new DungeonDefinition("dungeon.burkhan_agui", "Бурхан Халдуны Агуй", 48, 1, 4,
            List.of(List.of(SAVDAG.id(), SAVDAG.id(), SAVDAG.id(), SAVDAG.id()),
                    List.of(SAVDAG.id(), SAVDAG.id(), CAVE_BEAR.id(), CAVE_BEAR.id()),
                    List.of(CAVE_BEAR.id(), SAVDAG.id(), SAVDAG.id(), SAVDAG.id(), CAVE_BEAR.id())),
            new BossDefinition(MOUNTAIN_LORD, phases(), 300), "loot.dungeon.burkhan_agui");

    // ------------------------------------------------------------------ 9. Тэнгэрийн Шат (54+): the stair to the sky

    public static final MobDefinition SKY_SOLDIER = mob("mob.tengeriin_tsereg", "Тэнгэрийн Цэрэг", "STRAY", MobTier.NORMAL, 55, 92, 13.5, 820);
    public static final MobDefinition SKY_WOLF = mob("mob.tengeriin_chono", "Тэнгэрийн Чоно", "WOLF", MobTier.ELITE, 57, 64, 8.5, 1080);
    public static final MobDefinition SKY_ENVOY = mob("mob.khukh_tengeriin_elch", "Хөх Тэнгэрийн Элч", "STRAY", MobTier.BOSS, 59, 94, 1.15, 480);

    public static final DungeonDefinition SKYSTAIR = new DungeonDefinition("dungeon.tengeriin_shat", "Тэнгэрийн Шат", 54, 1, 4,
            List.of(List.of(SKY_SOLDIER.id(), SKY_SOLDIER.id(), SKY_SOLDIER.id(), SKY_WOLF.id()),
                    List.of(SKY_SOLDIER.id(), SKY_SOLDIER.id(), SKY_WOLF.id(), SKY_WOLF.id()),
                    List.of(SKY_WOLF.id(), SKY_WOLF.id(), SKY_SOLDIER.id(), SKY_SOLDIER.id(), SKY_SOLDIER.id())),
            new BossDefinition(SKY_ENVOY, phases(), 300), "loot.dungeon.tengeriin_shat");

    // ------------------------------------------------------------------ 10. Тэнгэрийн Ордон (60, raid of 2–4)

    public static final MobDefinition PALACE_GUARD = mob("mob.ordny_sakhiul", "Ордны Сахиул", "VINDICATOR", MobTier.ELITE, 60, 70, 9.0, 1200);
    public static final MobDefinition BANNER_GUARDIAN = mob("mob.khukh_suldiin_sakhiul", "Хөх Сүлдийн Сахиул", "WITHER_SKELETON", MobTier.BOSS, 60, 110, 1.25, 560);

    public static final DungeonDefinition PALACE = new DungeonDefinition("dungeon.tengeriin_ordon", "Тэнгэрийн Ордон", 60, 2, 4,
            List.of(List.of(PALACE_GUARD.id(), PALACE_GUARD.id(), PALACE_GUARD.id()),
                    List.of(PALACE_GUARD.id(), PALACE_GUARD.id(), SKY_SOLDIER.id(), SKY_SOLDIER.id()),
                    List.of(PALACE_GUARD.id(), PALACE_GUARD.id(), PALACE_GUARD.id(), SKY_WOLF.id(), SKY_WOLF.id()),
                    List.of(PALACE_GUARD.id(), PALACE_GUARD.id(), PALACE_GUARD.id(), PALACE_GUARD.id())),
            new BossDefinition(BANNER_GUARDIAN, phases(), 315), "loot.dungeon.tengeriin_ordon");

    public static final List<DungeonDefinition> DUNGEONS = List.of(DEEP, RUIN, REDROCK, SACRED, SKYSTAIR, PALACE);

    public static final List<MobDefinition> MOBS = List.of(LUS, DEEP_WOLF, TANGUT_GHOST, RUIN_SCORPION, RAIDER, WAR_WOLF,
            SAVDAG, CAVE_BEAR, SKY_SOLDIER, SKY_WOLF, PALACE_GUARD);

    public static final List<MobDefinition> BOSSES = List.of(LUS_KHAAN, BLACK_GENERAL, RED_LORD, MOUNTAIN_LORD, SKY_ENVOY, BANNER_GUARDIAN);

    /** Gates: deep in each dungeon's region, further out as the level rises (all inside the 5 000-block border). */
    public static final List<DungeonSite> SITES = List.of(
            new DungeonSite(DEEP.id(), HallTheme.DEEP, 335, 2600),          // north-west: Хөвсгөл
            new DungeonSite(RUIN.id(), HallTheme.RUIN, 200, 3000),          // south-south-west: the Tangut border
            new DungeonSite(REDROCK.id(), HallTheme.REDROCK, 110, 3300),    // east: the far steppe
            new DungeonSite(SACRED.id(), HallTheme.SACRED, 60, 3700),       // east-north-east: the Хэнтий
            new DungeonSite(SKYSTAIR.id(), HallTheme.SKYSTAIR, 285, 4100),  // west: the high Altai
            new DungeonSite(PALACE.id(), HallTheme.PALACE, 0, 4500));       // due north, at the edge of the world

    /** Completion bonus (EXP, coins), continuing the ladder's curve. */
    public static DungeonContent.Completion completion(String id) {
        return switch (id) {
            case "dungeon.dalain_gun" -> new DungeonContent.Completion(8000, 560);
            case "dungeon.khar_khot" -> new DungeonContent.Completion(12500, 700);
            case "dungeon.ulaan_khad" -> new DungeonContent.Completion(18000, 840);
            case "dungeon.burkhan_agui" -> new DungeonContent.Completion(25000, 980);
            case "dungeon.tengeriin_shat" -> new DungeonContent.Completion(33000, 1120);
            case "dungeon.tengeriin_ordon" -> new DungeonContent.Completion(42000, 1400);
            default -> null;
        };
    }

    public static String where(String id) {
        return switch (id) {
            case "dungeon.dalain_gun" -> "Хөвсгөл (баруун хойд)";
            case "dungeon.khar_khot" -> "Тангудын хил (өмнө)";
            case "dungeon.ulaan_khad" -> "Алс зүүн тал";
            case "dungeon.burkhan_agui" -> "Хэнтий (зүүн хойд)";
            case "dungeon.tengeriin_shat" -> "Өндөр Алтай (баруун)";
            case "dungeon.tengeriin_ordon" -> "Ертөнцийн хойд хязгаар";
            default -> null;
        };
    }
}
