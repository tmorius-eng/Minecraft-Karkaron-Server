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
import java.util.UUID;
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
    /** Set by SuldServices: the analytics sink (a chapter completion is a funnel step, docs/ANALYTICS.md). */
    public volatile java.util.function.Consumer<mn.suld.api.analytics.AnalyticsEvent> analytics = e -> { };

    /** Called when a chapter completes (coins and EXP granted, collected items taken): persist both stores now. */
    private BiConsumer<Player, PlayerProfile> onComplete = (p, pr) -> { };
    /** Told the id of every chapter a player completes (the achievements; SuldPlugin sets it). */
    public volatile BiConsumer<Player, String> onChapter = (p, id) -> { };

    /**
     * The side quests' share of every objective event (docs/SIDE_QUESTS.md): each hook below reaches the story chapter
     * and the player's active side quests alike, so no caller of this service knows the side quests exist.
     */
    public interface SideHooks {
        /** An objective event for the player's active side quests; true when one of them moved. */
        boolean objective(Player p, PlayerProfile pr, QuestType type, String target, long value);

        /** Whether an active side quest of the player has this kind of objective (and this target, when not null). */
        boolean wants(UUID player, QuestType type, String target);

        /** Re-count the COLLECT side quests from the bag. */
        void recount(Player p, PlayerProfile pr);
    }

    public static final SideHooks NO_SIDE = new SideHooks() {
        @Override
        public boolean objective(Player p, PlayerProfile pr, QuestType type, String target, long value) {
            return false;
        }

        @Override
        public boolean wants(UUID player, QuestType type, String target) {
            return false;
        }

        @Override
        public void recount(Player p, PlayerProfile pr) {
        }
    };

    /** Set once at start by the side-quest service; until then the events reach the story only. */
    public volatile SideHooks side = NO_SIDE;

    public void onComplete(BiConsumer<Player, PlayerProfile> listener) {
        this.onComplete = listener;
    }

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

    /** Tell the player about the chapter they are on (giver line and hint), e.g. right after the first assignment. */
    public void announceCurrent(Player player, PlayerProfile profile) {
        definition(profile.questState().questId()).ifPresent(d -> announceStart(player, d));
    }

    /**
     * Make sure the player stands on a valid chapter (first chapter, or the successor of a completed one) and
     * re-check objectives that depend on current state (level, held items).
     */
    public void ensure(Player player, PlayerProfile profile) {
        if (profile.playerClass().isEmpty()) return;
        QuestState before = profile.questState();
        // QS-03: an id quests.json no longer has is placed by the player's level — chapters paid at a level below theirs
        // (ContentLoader prices each chapter at its running level, so the EXP grows along the chain) are behind them
        long behind = mn.suld.api.balance.Rewards.chapterExp(mn.suld.api.balance.Balance.curve(), profile.progression().level());
        QuestState now = chain.normalise(before, d -> d.expReward() < behind);
        if (chain.indexOf(before.questId()) < 0 && !before.questId().isEmpty() && !now.equals(before)) {
            org.bukkit.Bukkit.getLogger().warning("[quests] " + player.getName() + ": unknown chapter " + before.questId() + " -> " + now.questId());
        }
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
        } else if (def.type() == QuestType.DISCOVER_LOCATION && regionHere != null) {
            String here = regionHere.apply(player);
            if (here != null) apply(player, profile, QuestType.DISCOVER_LOCATION, here, 1);
        }
    }

    private java.util.function.Function<Player, String> regionHere;

    /** The wild region id at a player's feet (so a chapter to discover where you already stand is not stuck). */
    public void regionHere(java.util.function.Function<Player, String> resolver) {
        this.regionHere = resolver;
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

    /** A named prop (stele, shrine, clue) looked at: an INSPECT_CLUE chapter on it advances (audit 2026-10-14). */
    public boolean onInspect(Player player, PlayerProfile profile, String propId) {
        return apply(player, profile, QuestType.INSPECT_CLUE, propId, 1);
    }

    /** An item made at the Дархан's table: a CRAFT_ITEM chapter on its definition advances by {@code amount}. */
    public boolean onCrafted(Player player, PlayerProfile profile, String itemId, int amount) {
        return apply(player, profile, QuestType.CRAFT_ITEM, itemId, amount);
    }

    /** An Ovoo blessing received in a region: an OVOO_RITUAL chapter on that region advances. */
    public boolean onOvooRitual(Player player, PlayerProfile profile, String regionId) {
        return apply(player, profile, QuestType.OVOO_RITUAL, regionId, 1);
    }

    /**
     * An Ovoo blessing on an ovoo of {@code areaId} in {@code regionId} (null: none). The side quests hear both ids; the
     * story hears the area, else the region — one blessing is one chapter, so a chapter on the area and the next on its
     * region are never both completed by it.
     */
    public boolean onOvooBlessing(Player player, PlayerProfile profile, String areaId, String regionId) {
        boolean sideMoved = side.objective(player, profile, QuestType.OVOO_RITUAL, areaId, 1);
        if (regionId != null) sideMoved |= side.objective(player, profile, QuestType.OVOO_RITUAL, regionId, 1);
        boolean story = applyStory(player, profile, QuestType.OVOO_RITUAL, areaId, 1)
                || regionId != null && applyStory(player, profile, QuestType.OVOO_RITUAL, regionId, 1);
        return story || sideMoved;
    }

    /** Re-count a collect objective (after a pickup or inventory change). */
    public boolean onInventory(Player player, PlayerProfile profile) {
        side.recount(player, profile);
        QuestDefinition def = definition(profile.questState().questId()).orElse(null);
        if (def == null || def.type() != QuestType.COLLECT_ITEM || !profile.questState().active()) return false;
        return apply(player, profile, QuestType.COLLECT_ITEM, def.targetId(), count(player, def.targetId()));
    }

    /** Whether the active chapter is of this kind (cheap pre-check for frequent events). */
    public boolean wants(PlayerProfile profile, QuestType type) {
        QuestState s = profile.questState();
        return s.active() && definition(s.questId()).map(d -> d.type() == type).orElse(false)
                || side.wants(profile.playerId(), type, null);
    }

    /** Whether the active chapter or an active side quest asks to come to this place (a site visit's cheap pre-check). */
    public boolean wantsPlace(PlayerProfile profile, String placeId) {
        QuestState s = profile.questState();
        return s.active() && definition(s.questId()).map(d -> d.type() == QuestType.DISCOVER_LOCATION && d.targetId().equals(placeId)).orElse(false)
                || side.wants(profile.playerId(), QuestType.DISCOVER_LOCATION, placeId);
    }

    private boolean apply(Player player, PlayerProfile profile, QuestType type, String target, long value) {
        // COLLECT side quests count the bag themselves (recount); every other event reaches them here
        boolean sideMoved = type != QuestType.COLLECT_ITEM && side.objective(player, profile, type, target, value);
        return applyStory(player, profile, type, target, value) || sideMoved;
    }

    private boolean applyStory(Player player, PlayerProfile profile, QuestType type, String target, long value) {
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
                + "  (+" + def.expReward() + " EXP, +" + def.currencyReward() + " зоос)"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        int from = profile.progression().level();
        var gained = progression.grantExp(profile, def.expReward(), ExpSource.QUEST);
        if (gained.leveledUp()) mn.suld.plugin.ui.Presentation.levelUp(player, from, gained.after().level());
        analytics.accept(mn.suld.api.analytics.AnalyticsEvent.of(mn.suld.api.analytics.AnalyticsEventType.QUEST_COMPLETE, player.getUniqueId(),
                java.util.Map.of("quest", def.id(), "chapter", chapter, "of", chain.size(), "level", from)));
        onChapter.accept(player, def.id());
        onComplete.accept(player, profile);
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

    /** How many of an item definition the player's bag holds. */
    public int count(Player p, String definitionId) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (is(it, definitionId)) n += it.getAmount();
        }
        return n;
    }

    /**
     * Takes {@code amount} of an item definition from the bag (the caller has counted them) and returns the stacks it
     * took, so a refused hand-in can give exactly those back.
     */
    public java.util.List<ItemStack> take(Player p, String definitionId, int amount) {
        java.util.List<ItemStack> taken = new java.util.ArrayList<>();
        ItemStack[] inv = p.getInventory().getStorageContents();
        for (int i = 0; i < inv.length && amount > 0; i++) {
            if (!is(inv[i], definitionId)) continue;
            int use = Math.min(amount, inv[i].getAmount());
            ItemStack part = inv[i].clone();
            part.setAmount(use);
            taken.add(part);
            amount -= use;
            if (use == inv[i].getAmount()) inv[i] = null;
            else inv[i].setAmount(inv[i].getAmount() - use);
        }
        p.getInventory().setStorageContents(inv);
        return taken;
    }
}
