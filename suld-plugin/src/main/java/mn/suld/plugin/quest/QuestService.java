package mn.suld.plugin.quest;

import mn.suld.api.item.ItemInstance;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpSource;
import mn.suld.api.quest.QuestChain;
import mn.suld.api.quest.QuestDefinition;
import mn.suld.api.quest.QuestEngine;
import mn.suld.api.quest.QuestState;
import mn.suld.api.quest.QuestType;
import mn.suld.api.service.ProgressionService;
import mn.suld.plugin.content.QuestContent;
import mn.suld.plugin.item.ItemFactory;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * Drives the storyline {@link QuestContent#STORY} with the pure {@link QuestEngine}/{@link QuestChain}: objective
 * events come in from combat (kills), progression (level-ups), the region tracker (discovery), pickups (collect) and
 * dungeons (clears); completing a chapter pays its rewards and starts the next one at once.
 */
public final class QuestService {

    private final QuestEngine engine = new QuestEngine();
    private final QuestChain chain = QuestContent.STORY;
    private final ProgressionService progression;
    private ItemFactory items;
    private BiConsumer<Player, PlayerProfile> onChange = (p, pr) -> { };

    public QuestService(ProgressionService progression) {
        this.progression = progression;
    }

    /** Needed for collect quests (reading SÜLD item ids). */
    public void items(ItemFactory items) {
        this.items = items;
    }

    /** Called after any quest change (HUD refresh). */
    public void onChange(BiConsumer<Player, PlayerProfile> listener) {
        this.onChange = listener;
    }

    public QuestChain chain() {
        return chain;
    }

    public Optional<QuestDefinition> definition(String questId) {
        return chain.byId(questId);
    }

    /** Assign the first chapter if the player has none (true if assigned). */
    public boolean startFirstQuestIfNeeded(PlayerProfile profile) {
        QuestState s = profile.questState();
        if (s.questId().isEmpty() && !s.completed()) {
            profile.questState(chain.first().initialState());
            return true;
        }
        return false;
    }

    /**
     * Make sure the player stands on a valid chapter (first chapter, or the successor of a completed one) and
     * re-check objectives that depend on current state (level, held items).
     */
    public void ensure(Player player, PlayerProfile profile) {
        if (profile.playerClass().isEmpty()) return;
        QuestState before = profile.questState();
        QuestState now = chain.normalise(before);
        if (!now.equals(before)) {
            profile.questState(now);
            definition(now.questId()).ifPresent(d -> announceStart(player, d));
        }
        recheck(player, profile);
        onChange.accept(player, profile);
    }

    private void recheck(Player player, PlayerProfile profile) {
        QuestDefinition def = definition(profile.questState().questId()).orElse(null);
        if (def == null || !profile.questState().active()) return;
        if (def.type() == QuestType.REACH_LEVEL) {
            apply(player, profile, QuestType.REACH_LEVEL, "", profile.progression().level());
        } else if (def.type() == QuestType.COLLECT_ITEM) {
            apply(player, profile, QuestType.COLLECT_ITEM, def.targetId(), count(player, def.targetId()));
        }
    }

    // ---- objective events ------------------------------------------------------------------------------------

    public boolean onMobKilled(Player player, PlayerProfile profile, String mobId) {
        return apply(player, profile, QuestType.KILL_MOB, mobId, 1);
    }

    public boolean onLevel(Player player, PlayerProfile profile, int level) {
        return apply(player, profile, QuestType.REACH_LEVEL, "", level);
    }

    public boolean onRegion(Player player, PlayerProfile profile, String regionId) {
        return apply(player, profile, QuestType.DISCOVER_LOCATION, regionId, 1);
    }

    public boolean onDungeonCleared(Player player, PlayerProfile profile, String dungeonId) {
        return apply(player, profile, QuestType.COMPLETE_DUNGEON, dungeonId, 1);
    }

    /** Re-count a collect objective (after a pickup or inventory change). */
    public boolean onInventory(Player player, PlayerProfile profile) {
        QuestDefinition def = definition(profile.questState().questId()).orElse(null);
        if (def == null || def.type() != QuestType.COLLECT_ITEM || !profile.questState().active()) return false;
        return apply(player, profile, QuestType.COLLECT_ITEM, def.targetId(), count(player, def.targetId()));
    }

    /** Whether the active chapter is of this kind (cheap pre-check for frequent events). */
    public boolean wants(PlayerProfile profile, QuestType type) {
        QuestState s = profile.questState();
        return s.active() && definition(s.questId()).map(d -> d.type() == type).orElse(false);
    }

    private boolean apply(Player player, PlayerProfile profile, QuestType type, String target, long value) {
        QuestState before = profile.questState();
        QuestDefinition def = definition(before.questId()).orElse(null);
        if (def == null || !before.active()) return false;
        QuestState after = engine.advance(before, def, type, target, value);
        if (after.equals(before)) return false;
        profile.questState(after);
        if (engine.justCompleted(before, after)) {
            complete(player, profile, def);
        } else if (type != QuestType.REACH_LEVEL && after.progress() > before.progress()) {
            player.sendMessage(Component.text("✦ " + def.title() + " ", Messages.BRAND, TextDecoration.BOLD)
                    .append(Component.text(after.progress() + "/" + def.requiredCount(), NamedTextColor.WHITE, TextDecoration.BOLD)));
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.4f);
        }
        onChange.accept(player, profile);
        return true;
    }

    private void complete(Player player, PlayerProfile profile, QuestDefinition def) {
        if (def.type() == QuestType.COLLECT_ITEM) take(player, def.targetId(), def.requiredCount());
        int chapter = chain.indexOf(def.id()) + 1;
        Optional<QuestDefinition> next = chain.next(def.id());
        // Move on before granting EXP: the level-up that EXP may cause then counts for the next chapter.
        next.ifPresent(n -> profile.questState(n.initialState()));
        profile.addCurrency(def.currencyReward());
        player.showTitle(Title.title(
                Component.text("ЭРЭЛ ДУУСЛАА", Messages.BRAND, TextDecoration.BOLD),
                Component.text(chapter + "/" + chain.size() + " · " + def.title(), NamedTextColor.WHITE, TextDecoration.BOLD),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        player.sendMessage(Messages.success("Эрэл дууслаа: " + def.title()
                + "  (+" + def.expReward() + " EXP, +" + def.currencyReward() + " ₮)"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        int from = profile.progression().level();
        var gained = progression.grantExp(profile, def.expReward(), ExpSource.QUEST);
        if (gained.leveledUp()) mn.suld.plugin.ui.Presentation.levelUp(player, from, gained.after().level());
        if (next.isPresent()) {
            announceStart(player, next.get());
            recheck(player, profile);
        } else {
            player.sendMessage(Component.text("✦ Сүлдний Зам төгсөв! Та Монголын бүх нутгийг хамгааллаа.", Messages.BRAND, TextDecoration.BOLD));
            org.bukkit.Bukkit.broadcast(Component.text("✦ " + player.getName() + " «Сүлдний Зам»-ыг бүрэн дуусгалаа!", Messages.BRAND, TextDecoration.BOLD));
        }
    }

    private void announceStart(Player player, QuestDefinition d) {
        QuestContent.Lore lore = QuestContent.lore(d.id());
        player.sendMessage(Component.text("✦ Шинэ эрэл (" + (chain.indexOf(d.id()) + 1) + "/" + chain.size() + "): ", Messages.BRAND, TextDecoration.BOLD)
                .append(Component.text(d.title(), NamedTextColor.WHITE, TextDecoration.BOLD)));
        player.sendMessage(Component.text(lore.giver() + ": «" + d.description() + "»", NamedTextColor.WHITE, TextDecoration.BOLD));
        if (!lore.hint().isEmpty()) player.sendMessage(Component.text("➜ " + lore.hint(), NamedTextColor.GRAY, TextDecoration.BOLD));
    }

    // ---- items -----------------------------------------------------------------------------------------------

    private boolean is(ItemStack it, String definitionId) {
        if (items == null || it == null || it.getType().isAir()) return false;
        return items.read(it).map(ItemInstance::definitionId).map(definitionId::equals).orElse(false);
    }

    private int count(Player p, String definitionId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (is(it, definitionId)) n += it.getAmount();
        }
        return n;
    }

    private void take(Player p, String definitionId, int amount) {
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (!is(inv[i], definitionId)) continue;
            int use = Math.min(amount, inv[i].getAmount());
            amount -= use;
            if (use == inv[i].getAmount()) inv[i] = null;
            else inv[i].setAmount(inv[i].getAmount() - use);
        }
        p.getInventory().setStorageContents(inv);
    }
}
