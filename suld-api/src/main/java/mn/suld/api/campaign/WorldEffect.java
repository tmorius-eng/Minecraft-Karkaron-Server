package mn.suld.api.campaign;

/**
 * What changes in the world when a campaign succeeds (docs/KHURALDAI.md §Амьд ертөнц). Applied idempotently from a
 * {@code suld_world_state} flag, re-applied on every start while the flag lives, undone when it expires.
 *
 * @param type   PROP: a prop appears near a site; NPC: a role appears at a city point; UNLOCK_EVENT: a world event
 *               joins the automatic rotation
 * @param ref    the prop id, NPC role id or world-event id
 * @param anchor the site id (PROP) or city point id (NPC); empty for UNLOCK_EVENT
 * @param dx     block offset east of the anchor (PROP)
 * @param dz     block offset south of the anchor (PROP)
 * @param yaw    facing (PROP)
 */
public record WorldEffect(Type type, String ref, String anchor, int dx, int dz, float yaw) {

    public enum Type { PROP, NPC, UNLOCK_EVENT }

    public WorldEffect {
        if (type == null) throw new IllegalArgumentException("effect type");
        if (ref == null || ref.isBlank()) throw new IllegalArgumentException("effect needs a ref");
        anchor = anchor == null ? "" : anchor;
        if (type != Type.UNLOCK_EVENT && anchor.isBlank()) throw new IllegalArgumentException(type + " needs an anchor");
    }

    public static WorldEffect unlockEvent(String eventId) {
        return new WorldEffect(Type.UNLOCK_EVENT, eventId, "", 0, 0, 0);
    }

    /** The world-state flag this effect lives under; one effect per campaign, one flag per effect. */
    public String flagKey(String campaignId) {
        return "effect:" + campaignId;
    }

    /** The flag's value: enough to re-apply after a restart without the campaign definition. */
    public String encode() {
        return type + "|" + ref + "|" + anchor + "|" + dx + "|" + dz + "|" + yaw;
    }

    public static WorldEffect decode(String value) {
        String[] p = value.split("\\|", -1);
        if (p.length != 6) throw new IllegalArgumentException("effect value " + value);
        return new WorldEffect(Type.valueOf(p[0]), p[1], p[2], Integer.parseInt(p[3]), Integer.parseInt(p[4]), Float.parseFloat(p[5]));
    }
}
