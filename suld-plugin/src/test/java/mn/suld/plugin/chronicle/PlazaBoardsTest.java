package mn.suld.plugin.chronicle;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The plaza boards' text: readable Mongolian, numbers from the live state, no tag injection from player names. */
class PlazaBoardsTest {

    private static String plain(String mini) {
        return PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(mini));
    }

    @Test
    void campaignBoardShowsProgressTopThreeAndTime() {
        String t = plain(PlazaBoards.campaignText("Чонын Их Аян", "чоно", 1500, 3000, 2 * 86_400 + 5,
                List.of("Bat", "Dorj", "<red>Hack</red>"), List.of(90L, 60L, 30L)));
        assertTrue(t.contains("Чонын Их Аян"));
        assertTrue(t.contains("1500 / 3000 чоно"));
        assertTrue(t.contains("1. Bat 90"));
        assertTrue(t.contains("3. <red>Hack</red> 30"), "a player name is shown literally, never as a tag");
        assertTrue(t.contains("2 өдөр үлдлээ"));
    }

    @Test
    void ballotBoardListsOptionsWithVotes() {
        String t = plain(PlazaBoards.ballotText(List.of("Жингийн Зам", "Газрын Нээлт"), List.of(4, 1), 3_700));
        assertTrue(t.contains("1. Жингийн Зам — 4 санал"));
        assertTrue(t.contains("/khuraldai vote <1-2>"));
        assertTrue(t.contains("1 цаг үлдлээ"));
    }

    @Test
    void hallOfFameShowsTheNewestLinesOrAnInvitation() {
        assertTrue(plain(PlazaBoards.hallText(List.of())).contains("Анхны түүхийг та бич"));
        String t = plain(PlazaBoards.hallText(List.of("Bat Саарлын Агуйг анх цэвэрлэв")));
        assertTrue(t.contains("• Bat Саарлын Агуйг анх цэвэрлэв"));
        assertFalse(t.contains("Анхны түүхийг"));
    }
}
