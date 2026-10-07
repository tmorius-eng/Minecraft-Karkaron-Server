package mn.suld.api.item;

import mn.suld.api.json.Json;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The equipment the game itself does not store: the two accessory slots (the armour, hands and the relic live in the
 * player's own inventory). Persisted with the profile as one versioned JSON document; unreadable or newer data stops
 * the profile load instead of being wiped by the next save.
 */
public final class EquipmentState {

    public static final int VERSION = 1;
    public static final EquipmentState NONE = new EquipmentState(Map.of());

    private final Map<EquipSlot, ItemInstance> slots;

    private EquipmentState(Map<EquipSlot, ItemInstance> slots) {
        Map<EquipSlot, ItemInstance> m = new EnumMap<>(EquipSlot.class);
        slots.forEach((k, v) -> {
            if (v != null && stored(k)) m.put(k, v);
        });
        this.slots = java.util.Collections.unmodifiableMap(m);
    }

    /** Slots kept here rather than in the player's inventory. */
    public static boolean stored(EquipSlot s) {
        return s == EquipSlot.ACCESSORY_1 || s == EquipSlot.ACCESSORY_2;
    }

    public static EquipmentState of(Map<EquipSlot, ItemInstance> slots) {
        return slots.isEmpty() ? NONE : new EquipmentState(slots);
    }

    public Optional<ItemInstance> get(EquipSlot s) {
        return Optional.ofNullable(slots.get(s));
    }

    public Map<EquipSlot, ItemInstance> slots() {
        return slots;
    }

    public EquipmentState with(EquipSlot s, ItemInstance item) {
        if (!stored(s)) throw new IllegalArgumentException(s + " is not stored with the profile");
        Map<EquipSlot, ItemInstance> m = new EnumMap<>(EquipSlot.class);
        m.putAll(slots);
        if (item == null) m.remove(s);
        else m.put(s, item);
        return of(m);
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    public String toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("v", VERSION);
        Map<String, Object> s = new LinkedHashMap<>();
        slots.forEach((k, v) -> s.put(k.name(), ItemCodec.encode(v)));
        m.put("slots", s);
        return Json.write(m);
    }

    /** null / empty = nothing stored. Throws for unreadable or newer data. */
    public static EquipmentState fromJson(String json) {
        if (json == null || json.isBlank()) return NONE;
        Map<String, Object> m;
        try {
            m = Json.object(Json.parse(json));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("broken equipment data: " + e.getMessage(), e);
        }
        int v = m.get("v") instanceof Number n ? n.intValue() : -1;
        if (v != VERSION) throw new IllegalArgumentException("equipment data version " + v + " is not supported (expected " + VERSION + ")");
        Map<EquipSlot, ItemInstance> out = new EnumMap<>(EquipSlot.class);
        for (Map.Entry<String, Object> e : Json.object(m.getOrDefault("slots", Map.of())).entrySet()) {
            EquipSlot slot = EquipSlot.byName(e.getKey()).filter(EquipmentState::stored)
                    .orElseThrow(() -> new IllegalArgumentException("unknown equipment slot " + e.getKey()));
            ItemInstance item = e.getValue() instanceof String s ? ItemCodec.decode(s).orElse(null) : null;
            if (item == null) throw new IllegalArgumentException("unreadable item in " + slot);
            out.put(slot, item);
        }
        return of(out);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EquipmentState that && slots.equals(that.slots);
    }

    @Override
    public int hashCode() {
        return slots.hashCode();
    }
}
