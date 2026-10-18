package mn.suld.plugin.hologram;

import mn.suld.api.hologram.HologramBoard;
import mn.suld.api.hologram.HologramLoader;
import mn.suld.api.hologram.HologramSet;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bundled content/holograms.json: it loads clean, the envoy board stands on the class board, and the boards read
 * as Mongolian with live numbers and player names shown literally.
 */
class HologramServiceTest {

    private static String plain(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(Holograms.component(mini));
    }

    private static HologramSet bundled() throws Exception {
        HologramLoader.Result r = HologramLoader.load(Holograms.bundledText());
        assertTrue(r.ok(), r.issues().toString());
        return r.set();
    }

    private static HologramBoard board(String id) throws Exception {
        return bundled().board(id).orElseThrow();
    }

    @Test
    void theBundledFileLoadsWithEveryLabelTheGameAsksFor() throws Exception {
        HologramSet s = bundled();
        for (String id : HologramLoader.REQUIRED_LABELS) assertTrue(s.label(id).isPresent(), id);
        for (String id : List.of("guide.welcome", "guide.coins", "guide.classes", "guide.lands", "guide.commands", "envoy",
                "plaza.notice", "plaza.hall")) {
            assertTrue(s.board(id).isPresent(), id);
        }
    }

    @Test
    void theEnvoyBoardStandsAboveTheClassBoard() throws Exception {
        HologramBoard envoy = board("envoy");
        assertEquals(HologramBoard.Placement.ABOVE, envoy.placement());
        assertEquals("guide.classes", envoy.above());
        assertTrue(envoy.dy() > 0, "a gap between the two boards");
        assertTrue(plain(String.join("\n", board("guide.classes").lines(null))).contains("АНГИ БА ТУЛААН"));
    }

    @Test
    void theClassBoardIsTallEnoughThatTheEnvoyClearsIt() throws Exception {
        HologramBoard classes = board("guide.classes");
        String mini = HologramService.render(classes, new HologramService.Data(HologramBoard.TEXT, Map.of(), Map.of()));
        double h = Holograms.height(mini, classes.style());
        int lines = mini.split("\n", -1).length;
        assertTrue(h >= lines * 10 * 0.025 * classes.style().scale(), "at least one row a line: " + h);
    }

    @Test
    void envoyBoardListsNamesOrInvitesTheFirst() throws Exception {
        HologramBoard envoy = board("envoy");
        String none = plain(HologramService.render(envoy, HologramService.envoyData(List.of())));
        assertTrue(none.contains("МӨНХ ТЭНГЭРИЙН ЭЛЧ"));
        assertTrue(none.contains("Анхны баатрын нэр"));
        String two = plain(HologramService.render(envoy, HologramService.envoyData(List.of("Bat", "<red>Hack</red>"))));
        assertTrue(two.contains("Bat"));
        assertTrue(two.contains("<red>Hack</red>"), "a player name is shown literally, never as a tag");
        assertFalse(two.contains("Анхны баатрын нэр"));
    }

    @Test
    void campaignBoardShowsProgressTopThreeAndTime() throws Exception {
        String t = plain(HologramService.render(board("plaza.notice"), HologramService.campaignData("Чонын Их Аян", "чоно", 1500, 3000,
                2 * 86_400 + 5, List.of("Bat", "Dorj", "<red>Hack</red>"), List.of(90L, 60L, 30L))));
        assertTrue(t.contains("Чонын Их Аян"));
        assertTrue(t.contains("1500 / 3000 чоно"));
        assertTrue(t.contains("Тэргүүлэгчид"));
        assertTrue(t.contains("1. Bat 90"));
        assertTrue(t.contains("3. <red>Hack</red> 30"), "a player name is shown literally, never as a tag");
        assertTrue(t.contains("2 өдөр үлдлээ"));
    }

    @Test
    void campaignBoardWithoutContributorsHasNoLeaderHeader() throws Exception {
        String t = plain(HologramService.render(board("plaza.notice"), HologramService.campaignData("Чонын Их Аян", "чоно", 0, 3000,
                600, List.of(), List.of())));
        assertFalse(t.contains("Тэргүүлэгчид"));
        assertTrue(t.contains("10 минут үлдлээ"));
    }

    @Test
    void ballotBoardListsOptionsWithVotes() throws Exception {
        String t = plain(HologramService.render(board("plaza.notice"),
                HologramService.ballotData(List.of("Жингийн Зам", "Газрын Нээлт"), List.of(4, 1), 3_700)));
        assertTrue(t.contains("1. Жингийн Зам — 4 санал"));
        assertTrue(t.contains("/khuraldai vote <1-2>"), t);
        assertTrue(t.contains("1 цаг үлдлээ"));
    }

    @Test
    void hallOfFameShowsTheNewestLinesOrAnInvitation() throws Exception {
        HologramBoard hall = board("plaza.hall");
        assertTrue(plain(HologramService.render(hall, HologramService.hallData(List.of()))).contains("Анхны түүхийг та бич"));
        String t = plain(HologramService.render(hall, HologramService.hallData(List.of("Bat Саарлын Агуйг анх цэвэрлэв"))));
        assertTrue(t.contains("• Bat Саарлын Агуйг анх цэвэрлэв"));
        assertFalse(t.contains("Анхны түүхийг"));
    }

    @Test
    void labelsFillTheirValues() throws Exception {
        assertEquals("Сүнсний Гэрэлт\n+120 EXP · 4:05", PlainTextComponentSerializer.plainText().serialize(
                Holograms.text("soul.beacon", null, Map.of("exp", "120", "time", "4:05"))));
        String vein = PlainTextComponentSerializer.plainText().serialize(Holograms.text("profession.vein", "spent",
                Map.of("name", "Төмрийн судал", "tier", "II", "craft", "Төмрийн дархан", "level", "10", "time", "1:30")));
        assertEquals("⛏ Төмрийн судал · II\nдахин 1:30", vein);
        String station = PlainTextComponentSerializer.plainText().serialize(Holograms.text("training.station", "boo", Map.of()));
        assertTrue(station.startsWith("БӨӨ\n"), station);
    }
}
