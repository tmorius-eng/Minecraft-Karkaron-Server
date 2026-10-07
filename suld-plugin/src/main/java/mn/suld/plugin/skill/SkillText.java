package mn.suld.plugin.skill;

import mn.suld.api.skill.tree.SkillAllocation;
import mn.suld.api.skill.tree.SkillEngine;

/** Player-facing wording (Mongolian) for the outcomes of skill-tree actions; one place so GUI and commands agree. */
public final class SkillText {

    private SkillText() {
    }

    public static String why(SkillAllocation.Check c) {
        String n = c.node() == null ? "" : c.node().name();
        String o = c.other() == null ? "" : c.other().name();
        return switch (c.why()) {
            case OK -> "Амжилттай";
            case ROOT -> "Үндэс нод байнга нээлттэй";
            case MAXED -> "«" + n + "» дээд түвшиндээ хүрсэн";
            case NOT_CONNECTED -> "«" + n + "» — эхлээд үүнтэй шугамаар холбогдсон чадварыг нээ (алтан замаас үргэлжлүүл)";
            case LEVEL -> "«" + n + "» — түвшин " + c.amount() + " хэрэгтэй";
            case POINTS -> "«" + n + "» — оноо хүрэлцэхгүй (" + c.amount() + " хэрэгтэй)";
            case EXCLUSIVE -> "«" + n + "» нь «" + o + "»-тай хамт байж болохгүй — түүнийг эхлээд буцаа";
            case REQUIRES -> "«" + n + "» — «" + o + "» шаардлагатай";
            case NOT_UNLOCKED -> "«" + n + "» нээгдээгүй байна";
            case WOULD_BREAK -> "«" + o + "» нь «" + n + "»-ээс хамаардаг — түүнийг эхлээд буцаа";
            case COINS -> "«" + n + "»-г буцаахад " + c.amount() + " ₮ хэрэгтэй";
        };
    }

    public static String build(SkillEngine.Result r) {
        return switch (r.outcome()) {
            case OK -> "Амжилттай: " + r.detail();
            case BAD_NAME -> "Нэр 1–16 тэмдэгт (үсэг, тоо, _ , -) байх ёстой";
            case NO_SLOT -> "Бүтцийн слот дүүрсэн (" + r.detail() + "). Нэгийг устга.";
            case NOT_FOUND -> "«" + r.detail() + "» нэртэй бүтэц алга";
            case INVALID -> "Бүтэц одоогийн мод/түвшинд тохирохгүй байна: " + r.detail();
            case COOLDOWN -> "Дахин тохируулахад " + r.detail() + " секунд хүлээх хэрэгтэй";
            case NOTHING -> "Өөрчлөх зүйл алга";
        };
    }

    public static String reset(SkillTreeService.ResetResult r) {
        return switch (r.outcome()) {
            case OK -> "Буцаагдлаа: " + r.refunded() + " оноо" + (r.coins() > 0 ? " (−" + r.coins() + " ₮)" : "");
            case NOTHING -> "Буцаах оноо алга";
            case COOLDOWN -> "Дахин тохируулахад " + r.waitSeconds() + " секунд хүлээх хэрэгтэй";
            case NO_COINS -> "Мөнгө хүрэлцэхгүй: " + r.coins() + " ₮ хэрэгтэй";
            case NO_CLASS -> "Эхлээд ангиа сонго";
        };
    }
}
