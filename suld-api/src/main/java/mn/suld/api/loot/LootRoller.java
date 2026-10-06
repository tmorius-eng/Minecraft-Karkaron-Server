package mn.suld.api.loot;

import mn.suld.api.item.ItemInstance;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Rolls a {@link LootTable} into concrete {@link ItemInstance}s. The RNG is
 * injected so loot is deterministic under test; each dropped instance gets a
 * fresh UUID (anti-dup provenance).
 */
public final class LootRoller {

    private final Random random;

    public LootRoller(Random random) {
        this.random = random == null ? new Random() : random;
    }

    public List<ItemInstance> roll(LootTable table, String provenance) {
        List<ItemInstance> drops = new ArrayList<>();
        if (table == null) {
            return drops;
        }
        for (LootEntry entry : table.entries()) {
            if (random.nextDouble() < entry.dropChance()) {
                int span = entry.maxItemLevel() - entry.minItemLevel() + 1;
                int itemLevel = entry.minItemLevel() + random.nextInt(span);
                drops.add(entry.definition().roll(UUID.randomUUID(), itemLevel, provenance));
            }
        }
        return drops;
    }
}
