package mn.suld.plugin.content;

import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.hall.DungeonSite;
import mn.suld.api.mob.MobDefinition;

import java.util.List;

/**
 * Dungeons 5–10 of the ladder (docs/DUNGEON_PROGRESSION_SPEC.md, docs/world/DUNGEON_LADDER.md), levels 29–60, read
 * from {@code content/dungeons.json} and {@code content/mobs.json}. Places are real (Хөвсгөл, Хар Хот, Бурхан
 * Халдун); the creatures and their stories are SÜLD fiction (лус, савдаг: folk-belief names used as INSPIRED
 * creature types; Хар Жанжин is INSPIRED by the Khara-Khoto legend; the Хөх Сүлд palace is SÜLD fiction).
 */
public final class LadderContent {

    private LadderContent() {
    }

    // 5. Далайн Гүн (29+): under Хөвсгөл
    public static final MobDefinition LUS = Content.mob("mob.usny_lus");
    public static final MobDefinition DEEP_WOLF = Content.mob("mob.dalain_chono");
    public static final MobDefinition LUS_KHAAN = Content.mob("mob.lusyn_khaan");
    public static final DungeonDefinition DEEP = Content.pack().dungeon("dungeon.dalain_gun");
    // 6. Хар Хотын Балгас (36+): the ruined Tangut city
    public static final MobDefinition TANGUT_GHOST = Content.mob("mob.tangud_suns");
    public static final MobDefinition RUIN_SCORPION = Content.mob("mob.balgasny_khilents");
    public static final MobDefinition BLACK_GENERAL = Content.mob("mob.khar_janjin");
    public static final DungeonDefinition RUIN = Content.pack().dungeon("dungeon.khar_khot");
    // 7. Улаан Хадны Хүрээ (42+): the red-cliff stronghold
    public static final MobDefinition RAIDER = Content.mob("mob.khureenii_kharuul");
    public static final MobDefinition WAR_WOLF = Content.mob("mob.dainy_chono");
    public static final MobDefinition RED_LORD = Content.mob("mob.ulaan_khadny_noyon");
    public static final DungeonDefinition REDROCK = Content.pack().dungeon("dungeon.ulaan_khad");
    // 8. Бурхан Халдуны Агуй (48+): the sacred mountain's cave
    public static final MobDefinition SAVDAG = Content.mob("mob.uulyn_savdag");
    public static final MobDefinition CAVE_BEAR = Content.mob("mob.aguin_baavgai");
    public static final MobDefinition MOUNTAIN_LORD = Content.mob("mob.khangai_savdag");
    public static final DungeonDefinition SACRED = Content.pack().dungeon("dungeon.burkhan_agui");
    // 9. Тэнгэрийн Шат (54+): the stair to the sky
    public static final MobDefinition SKY_SOLDIER = Content.mob("mob.tengeriin_tsereg");
    public static final MobDefinition SKY_WOLF = Content.mob("mob.tengeriin_chono");
    public static final MobDefinition SKY_ENVOY = Content.mob("mob.khukh_tengeriin_elch");
    public static final DungeonDefinition SKYSTAIR = Content.pack().dungeon("dungeon.tengeriin_shat");
    // 10. Тэнгэрийн Ордон (60, raid of 2–4)
    public static final MobDefinition PALACE_GUARD = Content.mob("mob.ordny_sakhiul");
    public static final MobDefinition BANNER_GUARDIAN = Content.mob("mob.khukh_suldiin_sakhiul");
    public static final DungeonDefinition PALACE = Content.pack().dungeon("dungeon.tengeriin_ordon");

    public static final List<DungeonDefinition> DUNGEONS = List.of(DEEP, RUIN, REDROCK, SACRED, SKYSTAIR, PALACE);

    /** The ladder's open-world kin, who also roam the outer lands (role "ladder"). */
    public static final List<MobDefinition> MOBS = Content.pack().mobsOf("ladder");

    public static final List<MobDefinition> BOSSES = List.of(LUS_KHAAN, BLACK_GENERAL, RED_LORD, MOUNTAIN_LORD, SKY_ENVOY, BANNER_GUARDIAN);

    /** Gates: deep in each dungeon's region, further out as the level rises. */
    public static final List<DungeonSite> SITES = DUNGEONS.stream()
            .map(d -> Content.pack().sites().stream().filter(s -> s.dungeonId().equals(d.id())).findFirst().orElseThrow()).toList();

    private static boolean ladder(String id) {
        return DUNGEONS.stream().anyMatch(d -> d.id().equals(id));
    }

    /** Completion bonus (EXP, coins) of a ladder dungeon, or null for another. */
    public static DungeonContent.Completion completion(String id) {
        return ladder(id) ? Content.pack().completions().get(id) : null;
    }

    public static String where(String id) {
        return ladder(id) ? Content.pack().dungeonWhere().get(id) : null;
    }
}
