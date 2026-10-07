package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SkyLayoutTest {

    static final SkillTreeLoader.Result BUNDLED = SkillTreeLoader.loadAll(SkillTreeLoader.classpath());

    @Test
    void nodesNeverOverlapAndTheRootIsTheCentre() {
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = BUNDLED.trees().get(c);
            SkyLayout l = SkyLayout.of(t);
            assertEquals(new SkyLayout.Pos(0, 0), l.of(t.root()));
            for (SkillNode a : t.nodes()) {
                for (SkillNode b : t.nodes()) {
                    if (a.index() >= b.index()) continue;
                    double d = l.of(a).distance(l.of(b));
                    // a node frame is 0.6 spacings wide (a satellite 0.45): 0.75 leaves a visible gap
                    assertTrue(d >= 0.75, c + ": " + a.id() + " and " + b.id() + " are " + d + " apart");
                }
            }
        }
    }

    @Test
    void classBranchesGrowUpUniversalHangsBelow() {
        SkillTree t = BUNDLED.trees().get(PlayerClass.BAATAR);
        SkyLayout l = SkyLayout.of(t);
        for (SkillNode n : t.nodes()) {
            if (n.root()) continue;
            if (n.tags().contains("universal")) assertTrue(l.of(n).v() < 0, n.id());
            else assertTrue(l.of(n).v() > 0, n.id());
        }
        double[] e = l.extent();
        assertTrue(e[0] < 22 && e[1] < 22, "fits a zoomed-out screen: " + e[0] + "x" + e[1]);
    }

    /** Dumps the layouts for the preview renderer (tools/pack/preview_skytree.py). */
    @Test
    void dumpForPreview() throws IOException {
        StringBuilder sb = new StringBuilder("{");
        for (PlayerClass c : PlayerClass.values()) {
            SkillTree t = BUNDLED.trees().get(c);
            SkyLayout l = SkyLayout.of(t);
            sb.append(sb.length() > 1 ? "," : "").append('"').append(c.id()).append("\":{\"nodes\":[");
            for (SkillNode n : t.nodes()) {
                SkyLayout.Pos p = l.of(n);
                sb.append(n.index() > 0 ? "," : "").append(String.format(Locale.ROOT, "{\"id\":\"%s\",\"icon\":\"%s\",\"cat\":\"%s\",\"u\":%.3f,\"v\":%.3f}",
                        n.id(), n.icon(), n.category(), p.u(), p.v()));
            }
            sb.append("],\"edges\":[");
            boolean first = true;
            for (SkillNode n : t.nodes()) {
                for (int nb : t.neighbours(n.index())) {
                    if (nb < n.index()) continue;
                    sb.append(first ? "" : ",").append("[").append(n.index()).append(",").append(nb).append(",").append(t.exclusiveLink(n.index(), nb) ? 1 : 0).append("]");
                    first = false;
                }
                for (int ex : t.exclusives(n.index())) {
                    if (ex < n.index() || t.linked(n.index(), ex)) continue;
                    sb.append(first ? "" : ",").append("[").append(n.index()).append(",").append(ex).append(",1]");
                    first = false;
                }
            }
            sb.append("]}");
        }
        Path out = Path.of("build", "skytree-layout.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, sb.append("}").toString());
    }
}
