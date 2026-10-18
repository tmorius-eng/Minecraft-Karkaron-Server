package mn.suld.api.hologram;

import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** content/holograms.json is read whole or not at all, and every problem names its place. */
class HologramLoaderTest {

    private static String labels(String extra) {
        String required = HologramLoader.REQUIRED_LABELS.stream()
                .map(id -> "\"" + id + "\": {\"dy\": 1, \"text\": [\"" + id + "\"]}")
                .collect(Collectors.joining(", "));
        return "{" + required + extra + "}";
    }

    private static String file(String boards, String extraLabels) {
        return "{\"format\": 1, \"boards\": [" + boards + "], \"labels\": " + labels(extraLabels) + "}";
    }

    private static final String BASE = "{\"id\": \"guide.classes\", \"anchor\": \"spawn\", \"place\": \"offset\", \"offset\": [0, 1.4, -9],"
            + " \"text\": [\"<bold>АНГИ БА ТУЛААН</bold>\", \"мөр\"]}";

    private static String issues(HologramLoader.Result r) {
        return r.issues().stream().map(Object::toString).collect(Collectors.joining("\n"));
    }

    @Test
    void readsBoardsLabelsAndStyle() {
        String envoy = "{\"id\": \"envoy\", \"place\": \"above\", \"above\": \"guide.classes\", \"gap\": 0.5,"
                + " \"billboard\": \"vertical\", \"background\": \"#99000000\", \"lineWidth\": 240, \"source\": \"envoys\","
                + " \"variants\": {\"names\": [\"Элч\", \"{names}\"], \"empty\": [\"Элч алга\"]},"
                + " \"items\": {\"names\": \"<yellow>{name}</yellow>\"}}";
        HologramLoader.Result r = HologramLoader.load(file(BASE + ", " + envoy, ", \"extra.one\": {\"dy\": 2.5, \"scale\": 1.6,"
                + " \"variants\": {\"a\": [\"A\"], \"b\": [\"B\"]}}"));
        assertTrue(r.ok(), issues(r));
        HologramBoard classes = r.set().board("guide.classes").orElseThrow();
        assertEquals(HologramBoard.Placement.OFFSET, classes.placement());
        assertEquals(-9, classes.dz());
        assertEquals(HologramStyle.DEFAULT, classes.style());
        HologramBoard e = r.set().board("envoy").orElseThrow();
        assertEquals(HologramBoard.Placement.ABOVE, e.placement());
        assertEquals("guide.classes", e.above());
        assertEquals(0.5, e.dy(), 1e-9);
        assertEquals(HologramStyle.Billboard.VERTICAL, e.style().billboard());
        assertEquals(0x99000000, e.style().background());
        assertEquals(240, e.style().lineWidth());
        assertEquals("Элч алга", e.lines("empty").get(0));
        HologramLabel l = r.set().label("extra.one").orElseThrow();
        assertEquals(2.5, l.dy(), 1e-9);
        assertEquals(1.6f, l.style().scale(), 1e-6);
        assertEquals("B", l.lines("b").get(0));
    }

    @Test
    void aGapDefaultsAndAboveNeedsAnExistingBoard() {
        String e = "{\"id\": \"envoy\", \"place\": \"above\", \"above\": \"guide.missing\", \"text\": [\"x\"]}";
        HologramLoader.Result r = HologramLoader.load(file(BASE + ", " + e, ""));
        assertFalse(r.ok());
        assertNull(r.set());
        assertTrue(issues(r).contains("boards.envoy.above: no board guide.missing"), issues(r));
    }

    @Test
    void boardsStandingOnEachOtherInARingAreRefused() {
        String a = "{\"id\": \"a\", \"place\": \"above\", \"above\": \"b\", \"text\": [\"a\"]}";
        String b = "{\"id\": \"b\", \"place\": \"above\", \"above\": \"a\", \"text\": [\"b\"]}";
        HologramLoader.Result r = HologramLoader.load(file(a + ", " + b, ""));
        assertTrue(issues(r).contains("ring"), issues(r));
    }

    @Test
    void aRequiredLabelCannotBeRemoved() {
        String json = file(BASE, "").replace("\"soul.beacon\": {\"dy\": 1, \"text\": [\"soul.beacon\"]}, ", "")
                .replace(", \"soul.beacon\": {\"dy\": 1, \"text\": [\"soul.beacon\"]}", "");
        HologramLoader.Result r = HologramLoader.load(json);
        assertTrue(issues(r).contains("label soul.beacon is used by the game"), issues(r));
    }

    @Test
    void aSourceBoardNeedsEveryVariantOfItsSource() {
        String b = "{\"id\": \"plaza.notice\", \"anchor\": \"event.plaza\", \"offset\": [1, 1, 1], \"source\": \"khuraldai\","
                + " \"variants\": {\"ballot\": [\"x\"], \"idle\": [\"y\"]}}";
        HologramLoader.Result r = HologramLoader.load(file(b, ""));
        assertTrue(issues(r).contains("needs the variant campaign"), issues(r));
    }

    @Test
    void badFieldsAreListedWithTheirPath() {
        String b = "{\"id\": \"Bad Id\", \"anchor\": \"spawn\", \"offset\": [1, 2, 3], \"text\": [\"x\"]},"
                + " {\"id\": \"short.offset\", \"anchor\": \"spawn\", \"offset\": [1, 2], \"text\": [\"x\"]},"
                + " {\"id\": \"ok.one\", \"anchor\": \"spawn\", \"offset\": [0, 0, 0], \"billboard\": \"sideways\","
                + " \"background\": \"blue\", \"source\": \"weather\", \"tag\": \"Not A Tag\", \"text\": [\"x\"]},"
                + " {\"id\": \"ok.one\", \"anchor\": \"spawn\", \"offset\": [0, 0, 0], \"text\": [\"x\"]}";
        String all = issues(HologramLoader.load(file(b, "")));
        assertTrue(all.contains("boards[0].id"), all);
        assertTrue(all.contains("[x, y, z] in blocks"), all);
        assertTrue(all.contains("center, vertical, horizontal or fixed"), all);
        assertTrue(all.contains("#AARRGGBB, #RRGGBB or none"), all);
        assertTrue(all.contains("one of"), all);
        assertTrue(all.contains("a tag looks like suld_guide"), all);
        assertTrue(all.contains("boards[3].id: a second board ok.one"), all);
    }

    @Test
    void notJsonAndAWrongFormatAreRefused() {
        assertTrue(issues(HologramLoader.load("{ nope")).contains("not valid JSON"));
        assertTrue(issues(HologramLoader.load(file(BASE, "").replace("\"format\": 1", "\"format\": 2"))).contains("format must be 1"));
    }
}
