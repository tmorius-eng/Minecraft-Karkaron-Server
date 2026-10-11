package mn.suld.api.skill.tree;

import mn.suld.api.clazz.PlayerClass;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Clicking a far node learns the cheapest chain up to it, within the ordinary rules. */
class SkillPathTest {

    private final SkillTree tree = SkillTreeTest.tree(PlayerClass.BAATAR);

    private SkillNode named(String name) {
        for (SkillNode n : tree.nodes()) if (n.name().equals(name)) return n;
        throw new AssertionError("no node " + name);
    }

    /** The owner's case: level 6, 7 points, nothing learned, «Цус Ундаалагч» (level 3) behind «Хурц Ир». */
    @Test
    void theOwnersNodeGetsItsChain() {
        SkillAllocation a = SkillAllocation.empty(tree);
        SkillNode target = named("Цус Ундаалагч");
        assertEquals(SkillAllocation.Why.NOT_CONNECTED, a.canUnlock(target, 6, 7).why());
        List<SkillNode> chain = a.pathTo(target, 6);
        assertNotNull(chain);
        assertEquals(target, chain.get(chain.size() - 1));
        assertTrue(chain.stream().anyMatch(n -> n.name().equals("Хурц Ир")), chain.toString());
        assertTrue(SkillAllocation.cost(chain) <= 7, "cost " + SkillAllocation.cost(chain));
        SkillAllocation b = a;
        for (SkillNode n : chain) {
            assertTrue(b.canUnlock(n, 6, Integer.MAX_VALUE).ok(), n.id());
            b = b.plusRank(n);
        }
        assertNull(b.firstInconsistent());
    }

    @Test
    void everyVisibleNodeOnTheMapHasAChainAtLevel60() {
        SkillAllocation a = SkillAllocation.empty(tree);
        for (SkillNode n : tree.nodes()) {
            if (n.root() || !tree.exclusives(n.index()).isEmpty()) continue;
            if (n.requires().isEmpty()) assertNotNull(a.pathTo(n, 60), n.id());
        }
    }

    @Test
    void noChainThroughALevelGate() {
        SkillAllocation a = SkillAllocation.empty(tree);
        SkillNode high = null;
        for (SkillNode n : tree.nodes()) if (n.effectiveLevel() > 10 && (high == null || n.effectiveLevel() > high.effectiveLevel())) high = n;
        assertNotNull(high);
        assertNull(a.pathTo(high, 5));
    }
}
