package mn.suld.plugin.ui;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The boss bars step aside while a sky window is open and come back after (the atlas's title sat under three of them). */
class BossBarsTest {

    private static BossBar bar(String name) {
        return BossBar.bossBar(Component.text(name), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
    }

    @Test
    void openingAWindowHidesEveryWantedBarAndClosingItBringsThemBack() {
        BossBars.Gate g = new BossBars.Gate();
        UUID p = UUID.randomUUID();
        BossBar quest = bar("Жингийн Зам"), tutorial = bar("Заавар 4/7"), event = bar("Анхны Ан");
        assertTrue(g.show(p, quest));
        assertTrue(g.show(p, tutorial));
        assertTrue(g.show(p, event));
        assertEquals(Set.of(quest, tutorial, event), Set.copyOf(g.quiet(p, true)), "the atlas opens: all three leave the top of the screen");
        assertEquals(List.of(), g.quiet(p, true), "still open: nothing more to do");
        assertEquals(Set.of(quest, tutorial, event), Set.copyOf(g.quiet(p, false)), "the atlas closes: all three come back");
        assertEquals(List.of(), g.quiet(p, false));
    }

    @Test
    void aBarShownWhileTheWindowIsOpenWaitsForItToClose() {
        BossBars.Gate g = new BossBars.Gate();
        UUID p = UUID.randomUUID();
        g.quiet(p, true);
        BossBar event = bar("Анхны Ан");
        assertFalse(g.show(p, event), "a world event starting under the atlas does not cover its title");
        assertFalse(g.show(p, event), "a service that shows its bar every tick is still held back");
        assertEquals(List.of(event), g.quiet(p, false));
    }

    @Test
    void aBarItsServiceHidMeanwhileStaysHidden() {
        BossBars.Gate g = new BossBars.Gate();
        UUID p = UUID.randomUUID();
        BossBar quest = bar("Жингийн Зам"), tutorial = bar("Заавар 4/7");
        g.show(p, quest);
        g.show(p, tutorial);
        g.quiet(p, true);
        g.hide(p, quest); // the quest was handed in while the atlas was open
        assertEquals(List.of(tutorial), g.quiet(p, false), "no stale quest bar after the atlas");
    }

    @Test
    void playersAreGatedApart() {
        BossBars.Gate g = new BossBars.Gate();
        UUID inAtlas = UUID.randomUUID(), outside = UUID.randomUUID();
        BossBar boss = bar("Аварга");
        g.show(inAtlas, boss);
        g.quiet(inAtlas, true);
        assertTrue(g.show(outside, boss), "another player's open atlas never hides my bar");
        g.forget(inAtlas);
        assertFalse(g.isQuiet(inAtlas));
        assertEquals(List.of(), g.quiet(inAtlas, false), "a player who left keeps nothing");
    }
}
