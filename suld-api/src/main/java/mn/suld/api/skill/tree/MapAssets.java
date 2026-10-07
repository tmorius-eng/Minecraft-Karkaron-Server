package mn.suld.api.skill.tree;

import java.util.ArrayList;
import java.util.List;

/** Names of the resource-pack items the skill map and the respec item use ({@code suld:<name>}); a test checks them against the pack. */
public final class MapAssets {

    private MapAssets() {
    }

    /** Connector pieces: horizontal, vertical, diagonal down-right, diagonal down-left. */
    public static final List<String> CONNECTOR_KINDS = List.of("h", "v", "d1", "d2");
    /** Learned path, next step, not reached, exclusive choice. */
    public static final List<String> CONNECTOR_STATES = List.of("on", "next", "off", "red");
    public static final String ORB_MODEL = "orb_oblivion";

    public static String connector(String kind, String state) {
        return "tree_" + kind + "_" + state;
    }

    public static List<String> connectorModels() {
        List<String> out = new ArrayList<>();
        for (String kind : CONNECTOR_KINDS) for (String state : CONNECTOR_STATES) out.add(connector(kind, state));
        return out;
    }
}
