package mn.suld.api.loot;

import java.util.List;

/**
 * A named set of independent {@link LootEntry} rolls. Data-driven; rolled by
 * {@link LootRoller}.
 *
 * @param id      stable loot-table id (e.g. "loot.goviin_chono")
 * @param entries possible drops
 */
public record LootTable(String id, List<LootEntry> entries) {

    public LootTable {
        entries = entries == null ? List.of() : List.copyOf(entries);
    }
}
