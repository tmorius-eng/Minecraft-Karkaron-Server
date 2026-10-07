package mn.suld.api.skill.tree;

/** What sets a passive spell off. */
public enum TriggerEvent {
    HIT("цохиход"),
    CRIT("чухал цохилтоор"),
    KILL("дайсан алахад"),
    CAST("шид хэрэглэхэд"),
    SPELL_HIT("шидээр дайсан цохиход"),
    DAMAGED("гэмтэхэд"),
    LOW_HP("амь 35%-аас доош орход"),
    SNEAK("бөхийхөд");

    private final String label;

    TriggerEvent(String label) {
        this.label = label;
    }

    /** The "Triggered by ..." wording (lower case Mongolian). */
    public String label() { return label; }
}
