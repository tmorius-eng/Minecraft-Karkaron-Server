package mn.suld.plugin.mastery;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** professions.yml: the built pens survive a restart under their full ids (a pen's id holds dots). */
class PenFileTest {

    private static YamlConfiguration reread(YamlConfiguration st) throws InvalidConfigurationException {
        YamlConfiguration back = new YamlConfiguration();
        back.loadFromString(st.saveToString());
        return back;
    }

    @Test
    void penIdsWithDotsComeBackWhole() throws InvalidConfigurationException {
        Map<String, int[]> pens = new LinkedHashMap<>();
        pens.put("pen.taats", new int[]{-134, 70, 118});
        pens.put("pen.tuul~2", new int[]{2210, 64, -905});
        pens.put("pen.orkhon", new int[]{-40, 63, -1802});
        List<String> bad = new ArrayList<>();
        Map<String, int[]> back = ProfessionService.readPens(reread(ProfessionService.penYaml(pens)), bad::add);
        assertEquals(pens.keySet(), back.keySet());
        for (String id : pens.keySet()) assertArrayEquals(pens.get(id), back.get(id), id);
        assertTrue(bad.isEmpty(), bad.toString());
    }

    @Test
    void theFileListsThePensInIdOrder() {
        Map<String, int[]> pens = new LinkedHashMap<>();
        pens.put("pen.tuul", new int[]{1, 2, 3});
        pens.put("pen.altai~1", new int[]{4, 5, 6});
        assertEquals(List.of("pen.altai~1 4 5 6", "pen.tuul 1 2 3"), ProfessionService.penYaml(pens).getStringList("pens"));
    }

    @Test
    void aLineThatDoesNotReadIsSkippedAndNamed() throws InvalidConfigurationException {
        YamlConfiguration st = new YamlConfiguration();
        st.loadFromString("pens:\n- pen.tuul 1 2 3\n- pen.ongi 1 two 3\n- pen.zavkhan 1 2\n- vein.tuul.0 1 2 3\n");
        List<String> bad = new ArrayList<>();
        Map<String, int[]> back = ProfessionService.readPens(st, bad::add);
        assertEquals(List.of("pen.tuul"), List.copyOf(back.keySet()));
        assertEquals(List.of("pen.ongi 1 two 3", "pen.zavkhan 1 2", "vein.tuul.0 1 2 3"), bad);
    }
}
