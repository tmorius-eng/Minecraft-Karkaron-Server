package mn.suld.api.item;

import mn.suld.api.json.Json;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The stored form of an {@link ItemInstance}: one compact JSON document (in the stack's PersistentDataContainer and
 * in the profile's equipment column). Unknown or newer schema versions are refused — an item the server cannot read
 * is never guessed at. Items written before the item engine (schema 1, separate PDC keys) are migrated by
 * {@link #legacy}.
 */
public final class ItemCodec {

    private ItemCodec() {
    }

    public static String encode(ItemInstance i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", ItemInstance.SCHEMA_VERSION);
        m.put("id", i.definitionId());
        m.put("uuid", i.uuid().toString());
        m.put("r", i.rarity().id());
        m.put("lvl", i.itemLevel());
        Map<String, Object> s = new LinkedHashMap<>();
        i.stats().forEach((k, v) -> s.put(k.id(), v));
        m.put("s", s);
        List<Object> a = new ArrayList<>();
        for (RolledAffix ra : i.affixes()) a.add(List.of(ra.affixId(), ra.value()));
        m.put("a", a);
        if (i.soulbound()) m.put("sb", true);
        if (i.boundTo() != null) m.put("bt", i.boundTo().toString());
        if (i.upgradeLevel() > 0) m.put("up", i.upgradeLevel());
        m.put("src", i.provenance());
        return Json.write(m);
    }

    /** The instance in {@code text}, or empty when it is malformed or from a newer server. */
    public static Optional<ItemInstance> decode(String text) {
        if (text == null || text.isEmpty()) return Optional.empty();
        try {
            Map<String, Object> m = Json.object(Json.parse(text));
            int v = Json.integer(m.get("v"));
            if (v != ItemInstance.SCHEMA_VERSION) return Optional.empty();
            String id = (String) m.get("id");
            UUID uuid = UUID.fromString((String) m.get("uuid"));
            ItemRarity rarity = ItemRarity.byId((String) m.get("r")).orElse(null);
            if (id == null || rarity == null) return Optional.empty();
            int lvl = Json.integer(m.get("lvl"));
            Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
            for (Map.Entry<String, Object> e : Json.object(m.getOrDefault("s", Map.of())).entrySet()) {
                ItemStat st = ItemStat.byId(e.getKey()).orElse(null);
                if (st == null || !(e.getValue() instanceof Number n)) return Optional.empty();
                stats.put(st, n.doubleValue());
            }
            List<RolledAffix> affixes = new ArrayList<>();
            for (Object o : Json.array(m.getOrDefault("a", List.of()))) {
                List<Object> pair = Json.array(o);
                if (pair.size() != 2 || !(pair.get(0) instanceof String aid) || !(pair.get(1) instanceof Number n)) return Optional.empty();
                affixes.add(new RolledAffix(aid, n.doubleValue()));
            }
            boolean sb = Boolean.TRUE.equals(m.get("sb"));
            UUID bt = m.get("bt") instanceof String s ? UUID.fromString(s) : null;
            int up = m.containsKey("up") ? Json.integer(m.get("up")) : 0;
            String src = m.get("src") instanceof String s ? s : "unknown";
            return Optional.of(new ItemInstance(id, uuid, rarity, lvl, stats, affixes, sb, bt, up, src, v));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * Schema 1: the separate PDC keys of the first item system. Crit chance and crit damage were fractions
     * (0.05 = 5%) and are now percent points; stat ids attack/health/resource became damage/max_health/resource_max.
     */
    public static Optional<ItemInstance> legacy(String id, String uuid, String rarityId, int level, String encodedStats,
                                                boolean soulbound, int upgrade) {
        try {
            UUID u = UUID.fromString(uuid);
            ItemRarity rarity = ItemRarity.byId(rarityId).orElse(ItemRarity.COMMON);
            Map<ItemStat, Double> stats = new EnumMap<>(ItemStat.class);
            if (encodedStats != null && !encodedStats.isEmpty()) {
                for (String part : encodedStats.split(";")) {
                    String[] kv = part.split(":");
                    if (kv.length != 2) continue;
                    ItemStat st = ItemStat.byId(kv[0]).orElse(null);
                    if (st == null) continue;
                    double val = Double.parseDouble(kv[1]);
                    if (st == ItemStat.CRIT_CHANCE || st == ItemStat.CRIT_DAMAGE) val = Math.round(val * 1000.0) / 10.0;
                    stats.put(st, val);
                }
            }
            return Optional.of(new ItemInstance(id, u, rarity, Math.max(1, level), stats, List.of(), soulbound, null,
                    upgrade, "legacy", ItemInstance.SCHEMA_VERSION));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
