package mn.suld.api.worldbuild;

/**
 * What kind of thing a module is, for overlap rules and write priority (higher wins).
 * INFRA (roads, canals, plazas) may cross each other; STRUCTURE/LANDMARK may not overlap anything
 * else's solid blocks unless the placement explicitly allows it.
 */
public enum Layer {
    TERRAIN(0), INFRA(1), PROP(2), STRUCTURE(3), LANDMARK(4);

    public final int priority;

    Layer(int priority) {
        this.priority = priority;
    }
}
