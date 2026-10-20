package mn.suld.plugin.content;

import mn.suld.api.loot.LootTable;
import mn.suld.api.mob.MobDefinition;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DATA-01 (audit 2026-10-14, GAMEPLAY_IMPROVEMENT_ROADMAP P1-5): every loot table in items/loot.json has a caller,
 * so a table cannot sit in the catalog, validate, and never drop anything. The callers are the ones the game rolls:
 * a mob's table, a dungeon's reward, a world boss, a world event's {@code loot.world_event.<name>}
 * (WorldEventService), the open-world rare roll (CombatListener) and a side quest's gift.
 */
class LootTableCallersTest {

    /** Tables that may stay without a caller, with the reason beside each. Empty: none may. */
    private static final Set<String> ALLOWLIST = Set.of();

    @Test
    void everyLootTableHasACaller() {
        ContentPack pack = Content.pack();
        Set<String> called = new TreeSet<>();
        for (MobDefinition m : pack.mobs().values()) if (m.lootTableId() != null) called.add(m.lootTableId());
        for (var d : pack.dungeons()) if (d.rewardTableId() != null) called.add(d.rewardTableId());
        for (var b : pack.worldBosses()) if (b.lootTable() != null) called.add(b.lootTable());
        for (var e : pack.worldEvents()) called.add("loot.world_event." + e.id().substring(e.id().indexOf('.') + 1));
        for (var q : pack.sideQuests()) if (q.lootTableId() != null) called.add(q.lootTableId());
        called.add(mn.suld.plugin.combat.CombatListener.WORLD_RARE_TABLE);

        Set<String> orphans = new TreeSet<>();
        for (LootTable t : SuldContent.items().lootTables()) {
            if (!called.contains(t.id()) && !ALLOWLIST.contains(t.id())) orphans.add(t.id());
        }
        assertEquals(Set.of(), orphans, "loot tables nothing rolls (wire them or delete them)");
    }
}
