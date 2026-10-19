package mn.suld.api.npc;

/**
 * One paragraph an NPC says, with its history label: {@code VERIFIED} (a cited fact, the only kind that may say
 * «түүхэн баримт»), {@code INSPIRED} (in the spirit of a verified element) or {@code FICTION} (SÜLD's own tale).
 */
public record NpcLine(String text, String history) {

    public static final String FACT_MARK = "түүхэн баримт";

    public NpcLine {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("an NPC line needs text");
        if (history == null || history.isBlank()) throw new IllegalArgumentException("an NPC line needs a history label");
    }

    /** True when the line claims to be history («түүхэн баримт») without being VERIFIED. */
    public boolean overclaims() {
        return text.contains(FACT_MARK) && !"VERIFIED".equals(history);
    }
}
