package mn.suld.plugin.dungeon.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** BOSS-02: a brain's warnings name the boss as content/mobs.json does, in the short form before the dash. */
class ArchetypeBrainTitleTest {

    @Test
    void theShortTitleStopsAtTheDash() {
        assertEquals("Хадны Мангас", ArchetypeBrain.shortTitleOf("Хадны Мангас — Агуйн Эзэн"));
        assertEquals("Хар Баавгай", ArchetypeBrain.shortTitleOf("Хар Баавгай — Ойн Эзэн"));
    }

    @Test
    void aNameWithoutADashIsItsOwnShortTitle() {
        assertEquals("Элсний Хаан", ArchetypeBrain.shortTitleOf("Элсний Хаан"));
    }
}
