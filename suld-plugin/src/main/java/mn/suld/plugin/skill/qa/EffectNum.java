package mn.suld.plugin.skill.qa;

final class EffectNum {
    private EffectNum() {
    }

    static String n(double v) {
        return mn.suld.api.skill.tree.EffectText.num(v);
    }
}
