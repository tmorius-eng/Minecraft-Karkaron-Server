package mn.suld.plugin.content;

import mn.suld.api.quest.QuestChain;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestType;

import java.util.List;
import java.util.Map;

/**
 * The main storyline «Сүлдний Зам» (Path of the Sülde): eighteen chapters that walk a new player from the gates of
 * Kharkhorum through the Kherlen steppe, Khasar's Den, the Gobi, the Khangai forests and up to the Altai peaks, with
 * a dungeon in every region —
 * one region (and level band) at a time, Wynncraft-style. Each chapter starts as soon as the previous one ends.
 */
public final class QuestContent {

    private QuestContent() {
    }

    /** Who gives the chapter and where to go — shown in /quest and the quest menu. */
    public record Lore(String giver, String hint) {
    }

    private static QuestDefinition q(String id, String title, String desc, QuestType type, String target, int count, long exp, long coins) {
        return new QuestDefinition(id, title, desc, type, target, count, exp, coins);
    }

    public static final QuestChain STORY = new QuestChain(List.of(
            SuldContent.FIRST_HUNT,
            q("quest.wolf_pelts", "Чонын Арьс", "Анчинд 3 чонын арьс авчирч өг.",
                    QuestType.COLLECT_ITEM, "item.chonon_arisan", 3, 200, 30),
            q("quest.kherlen_bandits", "Хэрлэнгийн Дээрэмчид", "Худалдааны замыг дээрэмдэгч 5 дээрэмчнийг устга.",
                    QuestType.KILL_MOB, WorldContent.BANDIT.id(), 5, 350, 50),
            q("quest.oath_5", "Цэргийн Тангараг", "Аравтад элсэхийн тулд 5-р түвшинд хүр.",
                    QuestType.REACH_LEVEL, "", 5, 300, 60),
            q("quest.khasar_den", "Хасарын Агуй", "Бүлэгтэйгээ Хасарын Агуйг цэвэрлэ — Хасарыг ялж, унахгүй үлд.",
                    QuestType.COMPLETE_DUNGEON, SuldContent.KHASAR_DEN.id(), 1, 800, 120),
            q("quest.gobi_road", "Говийн Зам", "Өмнө зүг рүү аялж Говийн цөлд хүр.",
                    QuestType.DISCOVER_LOCATION, WorldContent.GOBI.id(), 1, 250, 40),
            q("quest.gobi_scorpions", "Хилэнцийн Хор", "Жингийн замыг хаасан 6 Говийн хилэнцийг устга.",
                    QuestType.KILL_MOB, WorldContent.SCORPION.id(), 6, 700, 90),
            q("quest.sand_spirits", "Элсний Сүнснүүд", "Элсэн шуурганаас гарсан 5 элсний сүнсийг тайвшруул.",
                    QuestType.KILL_MOB, WorldContent.SAND_SPIRIT.id(), 5, 900, 110),
            q("quest.gobi_tomb", "Говийн Булш", "Элсний Хааны булшийг бүлгээрээ цэвэрлэ.",
                    QuestType.COMPLETE_DUNGEON, DungeonContent.GOBI_TOMB.id(), 1, 1400, 160),
            q("quest.oath_12", "Мянгатын Сорил", "Мянгатын ноёны сорилд орохын тулд 12-р түвшинд хүр.",
                    QuestType.REACH_LEVEL, "", 12, 800, 150),
            q("quest.khangai_forest", "Хангайн Ой", "Хойд зүгийн Хангайн ойд хүр.",
                    QuestType.DISCOVER_LOCATION, WorldContent.KHANGAI.id(), 1, 400, 60),
            q("quest.grey_wolves", "Саарал Чонын Сүрэг", "Малчдын хотыг сүйтгэсэн 8 саарал чоныг ан.",
                    QuestType.KILL_MOB, WorldContent.GREY_WOLF.id(), 8, 1600, 180),
            q("quest.khangai_bear", "Хангайн Эзэн", "Ойн эзэн 2 баавгайг ялж хүчээ батал.",
                    QuestType.KILL_MOB, WorldContent.BEAR.id(), 2, 2000, 220),
            q("quest.bear_lair", "Баавгайн Үүр", "Ойн Эзэн хар баавгайн үүрийг цэвэрлэ.",
                    QuestType.COMPLETE_DUNGEON, DungeonContent.BEAR_LAIR.id(), 1, 2800, 260),
            q("quest.altai_peaks", "Алтайн Оргил", "Баруун зүгийн мөсөн Алтайд хүр.",
                    QuestType.DISCOVER_LOCATION, WorldContent.ALTAI.id(), 1, 600, 80),
            q("quest.ice_spirits", "Мөсөн Сүнс", "Оргилын замыг хамгаалдаг 8 мөсөн сүнсийг устга.",
                    QuestType.KILL_MOB, WorldContent.ICE_SPIRIT.id(), 8, 3000, 300),
            q("quest.ice_peak", "Мөсөн Оргил", "Оргилын сахиул Мөсөн Хааныг ялж замаа нээ.",
                    QuestType.COMPLETE_DUNGEON, DungeonContent.ICE_PEAK.id(), 1, 4500, 420),
            q("quest.altai_giant", "Алтайн Аварга", "Тэнгэрийн шүтээнийг эзэлсэн 3 аваргыг ялж Сүлдийг сэргээ.",
                    QuestType.KILL_MOB, WorldContent.GIANT.id(), 3, 5000, 600)));

    private static final Map<String, Lore> LORE = Map.ofEntries(
            Map.entry("quest.first_hunt", new Lore("Анчин", "Хотын хаалгаар гараад зүүн зүгийн тал руу яв (Хэрлэн).")),
            Map.entry("quest.wolf_pelts", new Lore("Анчин", "Говийн чоноос арьс унана. Цүнхэнд 3 арьс цуглахад эрэл дуусна.")),
            Map.entry("quest.kherlen_bandits", new Lore("Худалдаачин", "Дээрэмчид Хэрлэнгийн тал (зүүн зүг)-д байна.")),
            Map.entry("quest.oath_5", new Lore("Хотын Ноён", "Мангас ан, агуйд ор — EXP цуглуул.")),
            Map.entry("quest.khasar_den", new Lore("Хотын Ноён", "Бүлэг байгуул (/party), тал нутагт /dungeon enter.")),
            Map.entry("quest.gobi_road", new Lore("Морьтон", "Хотын өмнөд хаалгаар (Z+) гар — хэрмийн цаана Говь эхэлнэ.")),
            Map.entry("quest.gobi_scorpions", new Lore("Жингийн Тэргүүн", "Хилэнцүүд Говьд (өмнө зүг) нуугдана.")),
            Map.entry("quest.sand_spirits", new Lore("Бөө", "Элсний сүнснүүд Говийн гүнд тэнүүчилнэ.")),
            Map.entry("quest.gobi_tomb", new Lore("Бөө", "Говьд (өмнө зүг) бүлгээ цуглуулаад /dungeon enter govi_bulsh.")),
            Map.entry("quest.oath_12", new Lore("Мянгатын Ноён", "Говь, Хэрлэнд ан хийж хүчээ нэм.")),
            Map.entry("quest.khangai_forest", new Lore("Морьтон", "Хотын хойд хаалгаар (Z−) гар — хэрмийн цаана Хангай эхэлнэ.")),
            Map.entry("quest.grey_wolves", new Lore("Малчин", "Саарал чононууд Хангайн ойд (хойд зүг) сүрэглэнэ.")),
            Map.entry("quest.khangai_bear", new Lore("Малчин", "Баавгай бол элит мангас — бүлгээрээ яв.")),
            Map.entry("quest.bear_lair", new Lore("Малчин", "Хангайд (хойд зүг) /dungeon enter baavgain_uur.")),
            Map.entry("quest.altai_peaks", new Lore("Бөө", "Хотын баруун хаалгаар (X−) гар — хэрмийн цаана Алтай эхэлнэ.")),
            Map.entry("quest.ice_spirits", new Lore("Бөө", "Мөсөн сүнснүүд Алтайн оргилд байна.")),
            Map.entry("quest.ice_peak", new Lore("Их Бөө", "Алтайд (баруун зүг) /dungeon enter mosun_orgil.")),
            Map.entry("quest.altai_giant", new Lore("Их Бөө", "Аваргууд бол элит — бүлэг, сайн зэвсэг хэрэгтэй.")));

    /**
     * The story of each chapter, told on its card in the story map (/quest). Places, the relay posts (өртөө), the
     * decimal army (аравт, зуут, мянгат), caravans on the trade roads and the stone statues of the Altai are real;
     * the creatures, the dungeons and the people are SÜLD fiction.
     */
    private static final Map<String, String> STORY_TEXT = Map.ofEntries(
            Map.entry("quest.first_hunt", "Хэрлэн гол бол Их Монгол Улсын өлгий нутаг. Сүүлийн үед тал нутагт чоно олширч, малчдын хотонд халдах болжээ."),
            Map.entry("quest.wolf_pelts", "Өвлийн хүйтэнд анчид чонын арьсаар дах, малгай оёдог. Хотын анчин таны авчрах арьсыг хүлээж байна."),
            Map.entry("quest.kherlen_bandits", "Хэрлэнгийн дагуух худалдааны замаар Хархорум руу тэмээн жин явдаг. Дээрэмчид жинг тонож, өртөөчдийг айлгаж байна."),
            Map.entry("quest.oath_5", "Хотын ноён: «Тал нутгийг хамгаалах хүн эхлээд өөрөө хүчтэй байх ёстой. Дайчин болж ир.»"),
            Map.entry("quest.khasar_den", "Хасарын Агуй хэмээн домоглодог хадан агуйд араатан эзэрхэж, ойр орчмын айлууд нүүж одож байна."),
            Map.entry("quest.gobi_road", "Говиор дамжин худалдааны их зам өнгөрдөг. Их хааны элч нар өртөө өртөөгөөр морь сольж давхидаг байв."),
            Map.entry("quest.gobi_scorpions", "Тэмээчид шөнө хөдөлдөг: өдөр Говийн аварга хилэнцүүд элсэн дотроос гарч ирдэг."),
            Map.entry("quest.sand_spirits", "Бөө: «Говийн гүнд эртний оршуулгын газрууд бий. Элсний сүнснүүд тэднийг сахиж, аянчдыг төөрүүлдэг.»"),
            Map.entry("quest.gobi_tomb", "Элсэнд дарагдсан булшны эзэн Элсний Хаан сэрсэн гэнэ. Түүнийг дарахгүй бол жин Говийг гатлахгүй."),
            Map.entry("quest.oath_12", "Мянгатын ноён: «Манай цэрэг аравт, зуут, мянгатаар зохион байгуулагддаг. Мянгатад нэгдэхийн тулд хүчээ батал.»"),
            Map.entry("quest.khangai_forest", "Хангайн нуруунаас Орхон гол эх авдаг. Энэ хөндийд Хархорум босож, нүүдэлчдийн нийслэл болсон юм."),
            Map.entry("quest.grey_wolves", "Малчид: «Хангайн саарал чононууд сүрэглэн бууж, адуу сүргийг тарааж байна. Туслаач!»"),
            Map.entry("quest.khangai_bear", "Ойн баавгайг эртнээс «ойн эзэн» хэмээн хүндэлдэг. Харин зэрлэгшсэн баавгай малчдын гэрийг сүйтгэж байна."),
            Map.entry("quest.bear_lair", "Ойн гүнд Хар Баавгайн үүр бий. Түүнийг ялсан хүн Хангайн ан агнуурыг дахин нээнэ."),
            Map.entry("quest.altai_peaks", "Алтай бол «Алтан уул». Мөнх цаст оргилуудыг эртнээс тэнгэрт хамгийн ойр газар гэж үздэг."),
            Map.entry("quest.ice_spirits", "Бөө: «Тэнгэр хилэгнэвэл оргилд мөсөн сүнс бууна. Тэднийг номхруулж, замаа цэвэрлэ.»"),
            Map.entry("quest.ice_peak", "Мөсөн Оргилын сахиул Мөсөн Хаан тэнгэр өөд гарах замыг хааж байна."),
            Map.entry("quest.altai_giant", "Алтайд эртний хүн чулуу, тахилын овоод бий. Аваргууд тэдгээрийг эзэлжээ: сэргээвэл Сүлдний зам нээгдэнэ."));

    public static String story(String questId) {
        return STORY_TEXT.getOrDefault(questId, "");
    }

    public static Lore lore(String questId) {
        return LORE.getOrDefault(questId, new Lore("Хархорум", ""));
    }
}
