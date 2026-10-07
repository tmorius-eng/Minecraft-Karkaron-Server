package mn.suld.api.skill.tree;

/** How a node reshapes one of the class spells (applied only to spells that support the modifier). */
public enum ModKey {
    DAMAGE_PCT("хохирол", "%"),
    COST_PCT("зардал", "%"),
    RADIUS_PCT("хүрээ", "%"),
    BURN("шатаалт", " сек"),
    SLOW("удаашрал", " сек"),
    WEAKEN("сулрал", " сек"),
    VULN("нэмэлт хохирол авна (4 сек)", "%"),
    HEAL_ON_HIT("цохилт бүрт эдгээлт", " ❤"),
    KNOCKUP("дээш хөөрөлт", ""),
    SHIELD("хамгаалалтын давхарга", " ❤"),
    HASTE("шидсэний дараах хурд", " сек"),
    REFUND("цохилт бүрт нөөц буцаалт", ""),
    ECHO_PCT("давтан шидэгдэх магадлал", "%"),
    PULL("татах хүч", "");

    private final String label;
    private final String unit;

    ModKey(String label, String unit) {
        this.label = label;
        this.unit = unit;
    }

    public String label() { return label; }
    public String unit() { return unit; }
}
