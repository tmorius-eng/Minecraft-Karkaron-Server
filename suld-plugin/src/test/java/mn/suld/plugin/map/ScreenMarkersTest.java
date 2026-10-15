package mn.suld.plugin.map;

import mn.suld.api.map.MapPin;
import mn.suld.api.map.ScreenProjection;
import mn.suld.api.map.WaypointHud;
import mn.suld.api.style.PlayerSetting;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The waypoint cards on the game screen: what each says, which marks they stand for (docs/WORLD_MAP.md). */
class ScreenMarkersTest {

    private static final UUID OWNER = UUID.randomUUID();

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static MapPin pin(String name, String colour, int x, int y, int z) {
        return new MapPin(UUID.randomUUID(), OWNER, name, x, y, z, colour, 0L);
    }

    @Test
    void aCardInViewReadsLetterNameAndMetres() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0.5, 65.62, 0.5, 0, 0);
        WaypointHud.Mark cave = ScreenMarkers.pinMark(pin("Агуй", "blue", 0, 64, 28));
        WaypointHud.Placement p = WaypointHud.layout(cam, 0.5, 64.0, 0.5, List.of(cave), Set.of()).get(0);
        assertTrue(p.inView());
        assertEquals("А · Агуй\n28.0 м", plain(ScreenMarkers.card(p)));
        assertEquals(TextColor.color(WaypointHud.colourOf("blue")), ScreenMarkers.card(p).color(), "the letter in the pin's colour");
    }

    @Test
    void anEdgeCardIsOneLineWithTheArrow() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, 0, 0); // facing south
        WaypointHud.Mark cave = ScreenMarkers.pinMark(pin("cave", "red", -20, 64, -40)); // behind, to the right
        WaypointHud.Placement p = WaypointHud.layout(cam, 0, 64, 0, List.of(cave), Set.of()).get(0);
        assertFalse(p.inView());
        assertEquals("➡ C " + WaypointHud.meters(p.distance()), plain(ScreenMarkers.card(p)));
        // the waypoint's edge card names it
        WaypointGuide.Target t = new WaypointGuide.Target("Хархорум", "◆", "world", 0.5, WaypointGuide.GROUND, -300.5);
        WaypointHud.Placement w = WaypointHud.layout(cam, 0, 64, 0, List.of(ScreenMarkers.waypointMark(t, null)), Set.of()).get(0);
        assertFalse(w.inView());
        assertTrue(plain(ScreenMarkers.card(w)).matches("[⬅➡] ◆ Хархорум 301 м"), plain(ScreenMarkers.card(w)));
    }

    @Test
    void theReferenceCardReads28Point1Metres() {
        ScreenProjection.Camera cam = ScreenProjection.Camera.standard(0, 65.62, 0, 0, 0);
        WaypointHud.Mark m = new WaypointHud.Mark("waypoint", WaypointHud.Kind.WAYPOINT, "A", "Awesome Waypoint", 0xECBE4C, 0, 64, 28.1);
        WaypointHud.Placement p = WaypointHud.layout(cam, 0, 64, 0, List.of(m), Set.of()).get(0);
        assertEquals("A · Awesome Waypoint\n28.1 м", plain(ScreenMarkers.card(p)));
    }

    @Test
    void aWaypointToAPinWearsThePinsLetterAndColour() {
        MapPin p = pin("Худаг", "green", 10, 70, -4);
        WaypointGuide.Target t = new WaypointGuide.Target(p.name(), "◆", "world", p.x() + 0.5, p.y(), p.z() + 0.5, p.id());
        WaypointHud.Mark m = ScreenMarkers.waypointMark(t, p);
        assertEquals("Х", m.letter());
        assertEquals(WaypointHud.colourOf("green"), m.colour());
        assertEquals(WaypointHud.Kind.WAYPOINT, m.kind());
        assertEquals(70, m.y());
        // a place picked on the map keeps its own glyph and gold
        WaypointHud.Mark place = ScreenMarkers.waypointMark(new WaypointGuide.Target("Хархорум", "◆", "world", 0.5, WaypointGuide.GROUND, 0.5), null);
        assertEquals("◆", place.letter());
        assertEquals(0xECBE4C, place.colour());
        assertTrue(Double.isNaN(place.y()));
    }

    @Test
    void theWaypointIsNotShownTwice() {
        MapPin p = pin("Худаг", "green", 10, 70, -4);
        WaypointHud.Mark death = ScreenMarkers.deathMark(120.5, 66, -40.5);
        List<WaypointHud.Mark> all = List.of(ScreenMarkers.pinMark(p), death);
        WaypointGuide.Target toPin = new WaypointGuide.Target(p.name(), "◆", "world", p.x() + 0.5, p.y(), p.z() + 0.5, p.id());
        assertEquals(p.id().toString(), ScreenMarkers.sameAs(toPin, all));
        WaypointGuide.Target toDeath = new WaypointGuide.Target("Сүүлд унасан газар", "☠", "world", 120.5, WaypointGuide.GROUND, -40.5);
        assertEquals("death", ScreenMarkers.sameAs(toDeath, all));
        assertNull(ScreenMarkers.sameAs(new WaypointGuide.Target("x 0, z 0", "◆", "world", 0.5, WaypointGuide.GROUND, 0.5), all));
        assertNull(ScreenMarkers.sameAs(null, all));
        // and choose() then drops the duplicate
        List<WaypointHud.Mark> marks = List.of(ScreenMarkers.waypointMark(toPin, p), ScreenMarkers.pinMark(p), death);
        List<WaypointHud.Mark> shown = WaypointHud.choose(marks, 0, 64, 0, WaypointHud.MAX_MARKS, ScreenMarkers.sameAs(toPin, all));
        assertEquals(List.of("waypoint", "death"), shown.stream().map(WaypointHud.Mark::key).toList());
    }

    @Test
    void aPinsCardStandsOverItsBlockCentre() {
        WaypointHud.Mark m = ScreenMarkers.pinMark(pin("Агуй", "gold", -7, 52, 13));
        assertEquals(-6.5, m.x());
        assertEquals(52, m.y());
        assertEquals(13.5, m.z());
        assertEquals("А", m.letter());
        assertEquals("☠", ScreenMarkers.deathMark(0, 0, 0).letter());
    }

    @Test
    void theWaypointCardStandsOutFromThePins() {
        WaypointHud.Mark w = ScreenMarkers.waypointMark(new WaypointGuide.Target("Хархорум", "◆", "world", 0.5, 64, 0.5), null);
        WaypointHud.Mark p = ScreenMarkers.pinMark(pin("Агуй", "gold", 0, 64, 0));
        assertTrue(ScreenMarkers.background(w, true).getAlpha() > ScreenMarkers.background(p, true).getAlpha());
        assertTrue(ScreenMarkers.background(p, false).getAlpha() > ScreenMarkers.background(p, true).getAlpha(), "edge cards over bright sky");
    }

    @Test
    void theScreenCardsHaveTheirOwnSettingOnByDefault() {
        assertTrue(PlayerSetting.SCREEN_MARKERS.enabled(0));
        assertFalse(PlayerSetting.SCREEN_MARKERS.enabled(PlayerSetting.SCREEN_MARKERS.toggled(0)));
        assertEquals(WaypointGuide.TAG, ScreenMarkers.TAG, "a card left by a crash is cleared like the old label");
        assertEquals(ScreenMarkers.TEXT_TICKS, WaypointGuide.BAR_TICKS, "the bar and the cards show the same distance");
    }
}
