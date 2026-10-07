package mn.suld.api.skill.tree;

import java.util.List;

/** Writes a tree back to the data-file format (used by tests for the round trip, and to regenerate files). */
public final class SkillTreeJson {

    private SkillTreeJson() {
    }

    private static String q(String s) { return SkillState.quote(s); }

    private static String n(double v) { return EffectText.num(v).replace(',', '.'); }

    /**
     * One class's file. When {@code universal} is true only the universal nodes (ids from {@code firstIndex}) are written.
     */
    public static String write(SkillTree t, String classId, List<SkillNode> nodes, List<String[]> edges, List<String[]> exclusive) {
        StringBuilder sb = new StringBuilder("{\n  \"version\": ").append(SkillTreeLoader.FORMAT_VERSION)
                .append(",\n  \"class\": ").append(q(classId)).append(",\n  \"nodes\": [\n");
        for (int i = 0; i < nodes.size(); i++) {
            node(sb, nodes.get(i));
            sb.append(i + 1 < nodes.size() ? ",\n" : "\n");
        }
        sb.append("  ],\n  \"edges\": ").append(pairs(edges)).append(",\n  \"exclusive\": ").append(pairs(exclusive)).append("\n}\n");
        return sb.toString();
    }

    private static String pairs(List<String[]> p) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < p.size(); i++) {
            if (i > 0) sb.append(i % 4 == 0 ? ",\n    " : ", ");
            else sb.append("\n    ");
            sb.append('[').append(q(p.get(i)[0])).append(", ").append(q(p.get(i)[1])).append(']');
        }
        return sb.append(p.isEmpty() ? "]" : "\n  ]").toString();
    }

    private static void node(StringBuilder sb, SkillNode n) {
        sb.append("    {\"id\": ").append(q(n.id())).append(", \"name\": ").append(q(n.name()));
        if (!n.description().isEmpty()) sb.append(", \"description\": ").append(q(n.description()));
        sb.append(", \"category\": ").append(q(n.category().name())).append(", \"icon\": ").append(q(n.icon()))
                .append(", \"x\": ").append(n.x()).append(", \"y\": ").append(n.y()).append(", \"cost\": ").append(n.cost());
        if (n.maxRank() != 1) sb.append(", \"maxRank\": ").append(n.maxRank());
        sb.append(", \"level\": ").append(n.requiredLevel());
        if (!n.requires().isEmpty()) {
            sb.append(", \"requires\": [");
            for (int i = 0; i < n.requires().size(); i++) {
                sb.append(i > 0 ? ", " : "").append("{\"node\": ").append(q(n.requires().get(i).node())).append(", \"rank\": ").append(n.requires().get(i).rank()).append('}');
            }
            sb.append(']');
        }
        if (n.keystone()) sb.append(", \"keystone\": true");
        if (n.capstone()) sb.append(", \"capstone\": true");
        if (n.hidden()) sb.append(", \"hidden\": true");
        if (n.secret()) sb.append(", \"secret\": true");
        if (!n.tags().isEmpty()) {
            sb.append(", \"tags\": [");
            for (int i = 0; i < n.tags().size(); i++) sb.append(i > 0 ? ", " : "").append(q(n.tags().get(i)));
            sb.append(']');
        }
        sb.append(",\n     \"effects\": [");
        for (int i = 0; i < n.effects().size(); i++) {
            sb.append(i > 0 ? ", " : "");
            switch (n.effects().get(i)) {
                case Effect.Stat s -> sb.append("{\"type\": \"stat\", \"key\": ").append(q(s.key().name())).append(", \"value\": ").append(n(s.value())).append('}');
                case Effect.SpellMod m -> sb.append("{\"type\": \"mod\", \"spell\": ").append(q(m.spell().name())).append(", \"key\": ")
                        .append(q(m.key().name())).append(", \"value\": ").append(n(m.value())).append('}');
                case Effect.Proc p -> sb.append("{\"type\": \"proc\", \"event\": ").append(q(p.event().name())).append(", \"chance\": ").append(n(p.chance()))
                        .append(", \"kind\": ").append(q(p.kind().name())).append(", \"a\": ").append(n(p.a())).append(", \"b\": ").append(n(p.b()))
                        .append(", \"cooldown\": ").append(n(p.cooldown())).append('}');
                case Effect.UnlockUltimate u -> sb.append("{\"type\": \"ultimate\", \"ultimate\": ").append(q(u.ultimate().name())).append('}');
                case Effect.Keystone k -> sb.append("{\"type\": \"keystone\", \"kind\": ").append(q(k.kind().name())).append('}');
            }
        }
        sb.append("]}");
    }
}
