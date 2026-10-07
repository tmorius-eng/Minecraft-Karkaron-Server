package mn.suld.api.skill.tree;

/** Where a node sits in the map (used for colours, filters and branch resets). */
public enum SkillCategory {
    CORE("Үндэс"),
    DEFENSE("Хамгаалалт"),
    SPELL("Шидийн өөрчлөлт"),
    OFFENSE("Довтолгоо"),
    UTILITY("Нийтлэг"),
    KEYSTONE("Түлхүүр чадвар"),
    ULTIMATE("Дуулал");

    private final String label;

    SkillCategory(String label) {
        this.label = label;
    }

    public String label() { return label; }
}
