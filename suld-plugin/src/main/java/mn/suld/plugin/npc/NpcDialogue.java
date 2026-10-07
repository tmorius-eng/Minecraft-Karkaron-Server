package mn.suld.plugin.npc;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import mn.suld.api.profile.PlayerProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Conversations with Kharkhorum's people (docs/NPC_DIALOGUE.md): a native dialog with the speaker's words, one button
 * for what they do (shop, quest, travel, smith, class) and «Яриа» for what they know about the empire. History in the
 * talk is kept to well-attested facts (marked VERIFIED in the doc) and says so when it is legend; the SÜLD story
 * (the Blue Banner, the spirits) is told as the speaker's belief. Shift + right-click skips the talk.
 */
public final class NpcDialogue {

    /** What a person says: greetings (one picked), topics (title → paragraphs), the icon of their trade. */
    record Persona(String name, Material icon, List<String> greetings, Map<String, List<String>> topics) {
    }

    private static final ClickCallback.Options ONCE = ClickCallback.Options.builder().uses(1).lifetime(Duration.ofMinutes(5)).build();

    private static final Map<String, Persona> PEOPLE = Map.of(
            "class_selection", new Persona("Ангийн Бөө", Material.AMETHYST_SHARD,
                    List.of("Тэнгэр, газар, ус гурваас хүч ирдэг. Чи алинаас нь авах вэ, аянчин?",
                            "Хэнгэрэг минь чиний замыг сонсож байна. Таван зам бий — нэгийг нь сонго."),
                    Map.of("Таван зам", List.of(
                            "Баатар бамбай, илдээр бусдыг хамгаалдаг. Мэргэн нумаараа холын байг ононо.",
                            "Бөө сүнсний хүчээр нөхдөө эдгээж, дайсныг зовооно. Дархан галаар зэвсгээ хүчирхэгжүүлнэ.",
                            "Хүлэгчин морь, жадаараа давшилж, тал нутгийг эзэлдэг. Сонголтоо сайн бод — буцааж солих амаргүй."))),
            "tutorial", new Persona("Хөтөч", Material.MAP,
                    List.of("Хархорумд тавтай морил! Юуг мэдмээр байна?", "Анх ирсэн үү? Би чамд замаа зааж өгье."),
                    Map.of("Хархорум", List.of(
                            "Хархорум Орхоны хөндийд байдаг. Өгэдэй хаан 1235 онд энд хэрэм, ордноо бариулжээ (түүхэн баримт).",
                            "Олон орны элч, худалдаачин, урчууд энд цуглардаг байсныг тэр үеийн аялагчид тэмдэглэн үлдээсэн.",
                            "SÜLD-ийн Хархорум бол тэр хотын дурсамж дээр бүтээгдсэн зохиомол хот — гудамж бүр нь тоглоомын шинэ бүтээл."),
                            "Эхний алхам", List.of(
                            "Ангиа сонгоод зэвсгээ барь. Дараа нь /skills — чадварын мод.",
                            "Хотын хаалгаар гарвал тал нутаг. Q товчоор дайсандаа түгжигдэж болно.",
                            "Хасарын Агуйн хаалга зүүн зүгт бий — /dungeon list."))),
            "quest.first_hunt", new Persona("Анчдын Ахлагч", Material.BOW,
                    List.of("Чонын мөр хүйтэн байна. Ан хийх үү?", "Тал нутаг тайван биш — чоно, дээрэмчид олширчээ."),
                    Map.of("Их ан", List.of(
                            "Эрт цагт их ан буюу ав хомрого нь цэргийн сургууль байсан: олон зуун хүн цагираг үүсгэн араатныг шахдаг байжээ (түүхэн баримт).",
                            "Ангийн дэг журам хатуу байсан — эгнээгээ эвдсэн хүн шийтгэл хүлээдэг байв.",
                            "Чи ч гэсэн жижиг ангаас эхэл. Анхны ан чинь чиний нэрийг тал нутагт таниулна."))),
            "merchant", new Persona("Худалдаачин", Material.GOLD_NUGGET,
                    List.of("Торго, давс, төмөр — юу хэрэгтэй вэ?", "Олзоо зарах уу? Үнэ шударга, амлалт бат."),
                    Map.of("Торгоны зам", List.of(
                            "Эзэнт гүрний үед зам харгуй аюулгүй болж, худалдаачид Хятадаас Персийн нутаг хүртэл аялдаг байжээ (түүхэн баримт).",
                            "Пайз гэдэг гэрэгэ зам дагуух эрх, хамгаалалтыг баталдаг байсан.",
                            "Би бол тэр зам дээр гурван үе дамжсан худалдаачны хүү — SÜLD-ийн түүхийнх."))),
            "blacksmith", new Persona("Дархан", Material.ANVIL,
                    List.of("Гал бэлэн, төмөр хайлж байна. Юу засах вэ?", "Сайн зэвсэг эзнээ хамгаална. Муу зэвсэг эзнээ хаяна."),
                    Map.of("Дархан цол", List.of(
                            "Дархан гэдэг нь урлаач гэсэн утгаас гадна татвар, албанаас чөлөөлөгдсөн хүний цол байсан (түүхэн баримт).",
                            "Урчуудыг эзэнт гүрэн маш их хүндэлдэг байв — тэдний бүтээл цэргийн хүчийг тодорхойлдог.",
                            "Чиний ангийн хуяг надаар дамжин зэрэг ахина. Материалаа цуглуулаад ир."))),
            "fast_travel", new Persona("Өртөөчин", Material.LEATHER_HORSE_ARMOR,
                    List.of("Морь бэлэн. Хаашаа давхих вэ?", "Өртөө бүрт шинэ морь хүлээж байна."),
                    Map.of("Өртөө", List.of(
                            "Өгэдэй хааны үед өртөөний сүлжээ байгуулагдаж, элч нар өртөө бүрт морио сольж хурдан давхидаг байжээ (түүхэн баримт).",
                            "Тэр сүлжээ эзэнт гүрний мэдээг асар хол газар хүргэдэг байв.",
                            "Би энэ хотын хаалгануудын хооронд чамайг хүргэнэ — бага зэргийн мөнгөөр."))),
            "shrine.sky", new Persona("Тэнгэрийн Тахилч", Material.NETHER_STAR,
                    List.of("Мөнх Хөх Тэнгэр бидний дээр. Ивээл хүсэх үү?", "Тэнгэрийн дор хүн бүр адил."),
                    Map.of("Мөнх Хөх Тэнгэр", List.of(
                            "Эртний монголчууд Мөнх Хөх Тэнгэрийг дээдэлж, газар, уснаа шүтэж байжээ (түүхэн баримт).",
                            "Хөх Сүлдийн тухай домог бол SÜLD-ийн түүх: тугийн сүнс хүчтэй баатрыг сонгодог гэж бид итгэдэг.",
                            "Тэнгэрийн ивээл чамайг хамгаалах болтугай."))));

    private final Plugin plugin;

    public NpcDialogue(Plugin plugin) {
        this.plugin = plugin;
    }

    static String personaKey(String npcId) {
        if (npcId.startsWith("merchant.")) return "merchant";
        if (npcId.startsWith("fast_travel.")) return "fast_travel";
        return npcId;
    }

    /** True when there is a conversation for this NPC (otherwise the action runs at once). */
    public boolean has(String npcId) {
        return PEOPLE.containsKey(personaKey(npcId));
    }

    /** Shows the greeting with the NPC's action button and the topics. */
    public void open(Player p, String npcId, PlayerProfile pr, String actionLabel, Runnable action) {
        Persona who = PEOPLE.get(personaKey(npcId));
        if (who == null) {
            action.run();
            return;
        }
        String greet = who.greetings().get(ThreadLocalRandom.current().nextInt(who.greetings().size()));
        if (pr != null && pr.playerClass().isPresent() && npcId.equals("class_selection")) {
            greet = "Чи " + pr.playerClass().get().displayName() + "-ийн замыг сонгосон. Тэнгэр чамайг харж байна.";
        }
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.item(new ItemStack(who.icon())).description(DialogBody.plainMessage(
                Component.text("«" + greet + "»", NamedTextColor.WHITE), 260)).showTooltip(false).build());
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(ActionButton.create(Component.text(actionLabel, NamedTextColor.GREEN, TextDecoration.BOLD), null, 150,
                DialogAction.customClick((view, a) -> onMain(() -> {
                    if (a instanceof Player pl) {
                        pl.closeDialog();
                        action.run();
                    }
                }), ONCE)));
        for (Map.Entry<String, List<String>> t : who.topics().entrySet()) {
            buttons.add(ActionButton.create(Component.text("💬 " + t.getKey(), NamedTextColor.GOLD), null, 150,
                    DialogAction.customClick((view, a) -> onMain(() -> {
                        if (a instanceof Player pl) topic(pl, npcId, pr, who, t.getKey(), actionLabel, action);
                    }), ONCE)));
        }
        show(p, who, body, buttons);
    }

    private void topic(Player p, String npcId, PlayerProfile pr, Persona who, String title, String actionLabel, Runnable action) {
        List<DialogBody> body = new ArrayList<>();
        body.add(DialogBody.plainMessage(Component.text(title, NamedTextColor.GOLD, TextDecoration.BOLD), 300));
        for (String para : who.topics().get(title)) body.add(DialogBody.plainMessage(Component.text(para, NamedTextColor.GRAY), 300));
        List<ActionButton> buttons = List.of(
                ActionButton.create(Component.text(actionLabel, NamedTextColor.GREEN, TextDecoration.BOLD), null, 150,
                        DialogAction.customClick((view, a) -> onMain(() -> {
                            if (a instanceof Player pl) {
                                pl.closeDialog();
                                action.run();
                            }
                        }), ONCE)),
                ActionButton.create(Component.text("« Буцах"), null, 150,
                        DialogAction.customClick((view, a) -> onMain(() -> {
                            if (a instanceof Player pl) open(pl, npcId, pr, actionLabel, action);
                        }), ONCE)));
        show(p, who, body, buttons);
    }

    private void show(Player p, Persona who, List<DialogBody> body, List<ActionButton> buttons) {
        Dialog d = Dialog.create(f -> f.empty()
                .base(DialogBase.builder(Component.text(who.name(), NamedTextColor.GOLD, TextDecoration.BOLD))
                        .canCloseWithEscape(true).pause(false).afterAction(DialogBase.DialogAfterAction.CLOSE)
                        .body(body).build())
                .type(DialogType.multiAction(buttons).columns(2)
                        .exitAction(ActionButton.create(Component.text("Баяртай"), null, 100, null)).build()));
        p.showDialog(d);
    }

    private void onMain(Runnable r) {
        if (Bukkit.isPrimaryThread()) r.run(); else Bukkit.getScheduler().runTask(plugin, r);
    }
}
