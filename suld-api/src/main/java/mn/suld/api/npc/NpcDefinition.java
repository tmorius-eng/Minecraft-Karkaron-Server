package mn.suld.api.npc;

import java.util.List;

/**
 * One of Kharkhorum's people (content/npcs.json). The id is a city point id ({@code assets/world/kharkhorum/points.json})
 * or a dynamic role id a campaign's world effect names; {@code points} lists further point ids the same person
 * serves (the four market merchants are one entry). The look (skin, name, line, glow), the action button, the
 * greetings and the topics are data; what the action does is the plugin's.
 *
 * @param skin   {@code resourcepack/assets/suld/textures/entity/npc/<skin>.png}
 * @param glow   a NamedTextColor name ({@code GOLD}, {@code AQUA} …)
 * @param shopId the shop a {@link NpcAction#SHOP} NPC sells from, else null
 * @param icon   the Material shown beside the greeting
 */
public record NpcDefinition(String id, List<String> points, String skin, String name, String line, String glow,
                            NpcAction action, String shopId, String icon, List<String> greetings, List<NpcTopic> topics) {

    public NpcDefinition {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("an NPC needs an id");
        if (name == null || name.isBlank()) throw new IllegalArgumentException(id + ": an NPC needs a name");
        if (action == null) throw new IllegalArgumentException(id + ": an NPC needs an action");
        if (action == NpcAction.SHOP && (shopId == null || shopId.isBlank())) throw new IllegalArgumentException(id + ": a SHOP action needs a shop");
        points = points == null ? List.of() : List.copyOf(points);
        greetings = greetings == null ? List.of() : List.copyOf(greetings);
        topics = topics == null ? List.of() : List.copyOf(topics);
        line = line == null ? "" : line;
        skin = skin == null ? "guide" : skin;
        glow = glow == null ? "WHITE" : glow;
        icon = icon == null ? "PAPER" : icon;
    }

    /** True for the entry's own id and for every point it also serves. */
    public boolean serves(String pointId) {
        return id.equals(pointId) || points.contains(pointId);
    }

    /** Whether there is a conversation to show before the action (a greeting at least). */
    public boolean talks() {
        return !greetings.isEmpty();
    }
}
