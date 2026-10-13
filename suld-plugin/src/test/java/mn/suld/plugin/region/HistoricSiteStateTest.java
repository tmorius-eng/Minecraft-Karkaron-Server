package mn.suld.plugin.region;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** sites.yml as the server writes it: dotted site ids nest under «site», and every built site must read back. */
class HistoricSiteStateTest {

    @Test
    void dottedIdsReadBack() throws InvalidConfigurationException {
        YamlConfiguration written = new YamlConfiguration();
        written.set("site.aurag.x", 525);
        written.set("site.aurag.y", 66);
        written.set("site.aurag.z", 99);
        written.set("site.aurag.v", 7);
        written.set("site.uushig.x", -680);
        written.set("site.uushig.y", 71);
        written.set("site.uushig.z", -2091);
        written.set("site.uushig.v", 9);
        // the stray entry the old reader wrote back (its «site» key read as a site at 0 0 0)
        written.set("site.x", 0);
        written.set("site.y", 0);
        written.set("site.z", 0);
        written.set("site.v", 0);
        YamlConfiguration read = new YamlConfiguration();
        read.loadFromString(written.saveToString());
        Map<String, int[]> built = HistoricSiteService.readBuilt(read, Set.of("site.aurag", "site.uushig", "site.khodoo_aral"));
        assertEquals(Set.of("site.aurag", "site.uushig"), built.keySet(), "both built sites, nothing else");
        assertArrayEquals(new int[]{525, 66, 99, 7}, built.get("site.aurag"));
        assertArrayEquals(new int[]{-680, 71, -2091, 9}, built.get("site.uushig"));
    }
}
