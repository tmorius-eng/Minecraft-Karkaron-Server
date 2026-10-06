package mn.suld.plugin.dungeon;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.DungeonRun;
import mn.suld.api.dungeon.DungeonRunState;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
import mn.suld.api.loot.LootRoller;
import mn.suld.api.mob.MobDefinition;
import mn.suld.api.party.Party;
import mn.suld.api.party.PartyState;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.progression.ExpGainResult;
import mn.suld.api.progression.ExpSource;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.mob.MobService;
import mn.suld.plugin.party.PartyService;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Owns dungeon runs end to end: entry validation, wave spawning, the boss
 * encounter, the party-wide completion rewards, and failure/cleanup. All state
 * lives on the main thread; rules (state machine, phases, party) are in the pure
 * domain. Runs are instanced <i>logically</i> (per party, tracked mobs) at the
 * leader's location — world-level instancing arrives with the WorldBuilder slice.
 */
public final class DungeonService {

    /** Hard ceiling so a stuck run can never hold a party forever. */
    private static final long MAX_RUN_SECONDS = 20 * 60;
    private static final long TICK_PERIOD = 10L;
    private static final long INTERMISSION_TICKS = 60L;
    private static final double SPAWN_RADIUS = 6.0;

    private static final class ActiveRun {
        final DungeonRun run;
        final DungeonDefinition def;
        final Party party;
        final Location origin;
        final Set<UUID> participants = new LinkedHashSet<>();
        final Set<UUID> downed = new HashSet<>();
        final Set<UUID> waveMobs = new HashSet<>();
        final BossBar bar = BossBar.bossBar(Component.empty(), 1f, BossBar.Color.GREEN, BossBar.Overlay.PROGRESS);
        UUID bossId;
        String phaseName = "";
        BukkitTask ticker;

        ActiveRun(DungeonRun run, DungeonDefinition def, Party party, Location origin) {
            this.run = run;
            this.def = def;
            this.party = party;
            this.origin = origin;
        }
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final MobService mobs;
    private final PartyService parties;
    private final BossService bosses;
    private final LootRoller lootRoller = new LootRoller(new Random());

    private final Map<UUID, ActiveRun> runsByParty = new HashMap<>();
    private final Map<UUID, ActiveRun> runsByEntity = new HashMap<>();

    public DungeonService(Plugin plugin, SuldServices services, MobService mobs,
                          PartyService parties, BossService bosses) {
        this.plugin = plugin;
        this.services = services;
        this.mobs = mobs;
        this.parties = parties;
        this.bosses = bosses;
        parties.onMemberRemoved(this::onMemberRemoved);
    }

    // ---------------------------------------------------------------- entry

    /** @return an error message for the player, or {@code null} when the run started. */
    public Component start(Player leader, DungeonDefinition def) {
        PlayerProfile leaderProfile = services.profiles().cached(leader.getUniqueId()).orElse(null);
        if (leaderProfile == null || leaderProfile.playerClass().isEmpty()) {
            return Messages.error("Эхлээд анги сонгоно уу.");
        }
        Party party = parties.registry().ensureParty(leader.getUniqueId());
        if (!party.isLeader(leader.getUniqueId())) {
            return Messages.error("Зөвхөн багийн ахлагч агуйд оруулна.");
        }
        if (party.state() == PartyState.IN_DUNGEON || runsByParty.containsKey(party.id())) {
            return Messages.error("Баг аль хэдийн агуйд явж байна.");
        }
        if (party.size() < def.minPartySize() || party.size() > def.maxPartySize()) {
            return Messages.error("Багийн хэмжээ " + def.minPartySize() + "–" + def.maxPartySize() + " байх ёстой.");
        }
        Set<UUID> members = new LinkedHashSet<>();
        for (UUID id : party.members()) {
            Player p = Bukkit.getPlayer(id);
            PlayerProfile profile = p == null ? null : services.profiles().cached(id).orElse(null);
            if (p == null || profile == null) {
                return Messages.error("Багийн гишүүн онлайн биш байна.");
            }
            if (profile.progression().level() < def.minLevel()) {
                return Messages.error(p.getName() + " түвшин " + def.minLevel() + "-д хүрээгүй байна.");
            }
            members.add(id);
        }

        // the run's arena is where the party stands: never inside Kharkhorum (a safe zone) or right at its walls
        org.bukkit.Location here = leader.getLocation();
        if (services.city().near(here.getWorld().getName(), here.getBlockX(), here.getBlockZ(), 24)) {
            return Messages.error("Хархорумд агуйн аян эхлэхгүй. Хотын хаалгаар гараад тал нутагт /dungeon enter.");
        }
        ActiveRun ar = new ActiveRun(new DungeonRun(def.id(), party.id(), def.totalWaves()),
                def, party, leader.getLocation().clone());
        ar.participants.addAll(members);
        runsByParty.put(party.id(), ar);
        party.enterDungeon();

        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (!p.getUniqueId().equals(leader.getUniqueId())) {
                p.teleport(ar.origin);
            }
            p.showBossBar(ar.bar);
            Presentation.banner(p, def.displayName().toUpperCase(java.util.Locale.ROOT),
                    "Бэлдэцгээ — эхний давалгаа ирж байна", NamedTextColor.GOLD);
            p.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f);
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_DUNGEON, id));
        }
        updateBar(ar);
        ar.ticker = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(ar), TICK_PERIOD, TICK_PERIOD);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (ar.run.state() == DungeonRunState.ENTERING) {
                beginWave(ar);
            }
        }, 60L);
        return null;
    }

    // ---------------------------------------------------------------- waves

    private void beginWave(ActiveRun ar) {
        List<String> mobIds = ar.def.waveSpawnIds().get(ar.run.currentWave());
        ar.run.startWave(mobIds.size());
        for (int i = 0; i < mobIds.size(); i++) {
            MobDefinition mobDef = SuldContent.mobFor(mobIds.get(i));
            if (mobDef == null) {
                plugin.getLogger().warning("Dungeon " + ar.def.id() + " references unknown mob " + mobIds.get(i));
                ar.run.recordWaveKill(); // keep the state machine consistent
                continue;
            }
            LivingEntity mob = mobs.spawn(mobDef, ringLocation(ar.origin, i, mobIds.size()));
            mob.setRemoveWhenFarAway(false);
            ar.waveMobs.add(mob.getUniqueId());
            runsByEntity.put(mob.getUniqueId(), ar);
        }
        broadcast(ar, Messages.accent("Давалгаа " + ar.run.currentWave() + "/" + ar.run.totalWaves()
                + " — " + mobIds.size() + " дайсан!"));
        playAll(ar, Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
        updateBar(ar);
        if (ar.waveMobs.isEmpty()) { // every mob id was invalid — don't deadlock
            advanceAfterWave(ar);
        }
    }

    private void advanceAfterWave(ActiveRun ar) {
        broadcast(ar, Messages.success("Давалгаа цэвэрлэгдлээ!"));
        boolean last = ar.run.isOnLastWave();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!ar.run.isActive() || ar.run.state() != DungeonRunState.WAVE) {
                return;
            }
            if (last) {
                spawnBoss(ar);
            } else {
                beginWave(ar);
            }
        }, INTERMISSION_TICKS);
    }

    private void spawnBoss(ActiveRun ar) {
        ar.run.enterBoss();
        MobDefinition bossMob = ar.def.bossDefinition().mob();
        LivingEntity boss = mobs.spawn(bossMob, ar.origin.clone());
        boss.setRemoveWhenFarAway(false);
        ar.bossId = boss.getUniqueId();
        runsByEntity.put(ar.bossId, ar);
        ar.phaseName = ar.def.bossDefinition().phases().get(0).phaseName();
        bosses.register(boss, ar.def.bossDefinition(), change -> onPhaseChange(ar, change));
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                Presentation.banner(p, bossMob.displayName().toUpperCase(java.util.Locale.ROOT),
                        "Агуйн Эзэн сэрлээ!", NamedTextColor.DARK_RED);
            }
        }
        playAll(ar, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.7f);
        updateBar(ar);
    }

    private void onPhaseChange(ActiveRun ar, BossService.PhaseChange change) {
        ar.phaseName = change.phase().phaseName();
        String text = change.enraged()
                ? "Босс ХАЛУУРЛАА! Хугацаа дууслаа!"
                : "Босс шинэ шат: " + change.phase().phaseName() + " (х" + change.phase().attackMultiplier() + ")";
        broadcast(ar, Messages.error(text));
        updateBar(ar);
    }

    // ----------------------------------------------------------- death hooks

    /** Called by the listener for every dying entity; ignores anything not in a run. */
    public void onEntityDeath(LivingEntity entity) {
        ActiveRun ar = runsByEntity.remove(entity.getUniqueId());
        if (ar == null || !ar.run.isActive()) {
            return;
        }
        UUID id = entity.getUniqueId();
        if (id.equals(ar.bossId)) {
            bosses.unregister(id);
            complete(ar);
        } else if (ar.waveMobs.remove(id)) {
            if (ar.run.recordWaveKill()) {
                advanceAfterWave(ar);
            }
            updateBar(ar);
        }
    }

    public void onParticipantDown(UUID player) {
        ActiveRun ar = runOf(player).orElse(null);
        if (ar == null || !ar.run.isActive() || !ar.downed.add(player)) {
            return;
        }
        broadcast(ar, Messages.error(nameOf(player) + " унав!"));
        checkWipe(ar);
    }

    private void onMemberRemoved(Party party, UUID member) {
        ActiveRun ar = runsByParty.get(party.id());
        if (ar == null || !ar.participants.remove(member)) {
            return;
        }
        ar.downed.remove(member);
        Player p = Bukkit.getPlayer(member);
        if (p != null) {
            p.hideBossBar(ar.bar);
            services.profiles().cached(member).ifPresent(profile -> services.hud().update(p, profile));
        }
        if (ar.run.isActive() && ar.participants.isEmpty()) {
            fail(ar, "Бүх гишүүн гарсан.");
        } else {
            checkWipe(ar);
        }
    }

    private void checkWipe(ActiveRun ar) {
        if (!ar.run.isActive()) {
            return;
        }
        boolean anyoneAlive = false;
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !ar.downed.contains(id) && !p.isDead()) {
                anyoneAlive = true;
                break;
            }
        }
        if (!anyoneAlive) {
            fail(ar, "Баг бүгд унасан.");
        }
    }

    // --------------------------------------------------------------- finish

    private void complete(ActiveRun ar) {
        ar.run.complete();
        long seconds = ar.run.elapsedSeconds();
        String provenance = "dungeon:" + ar.def.id() + ":" + ar.run.runId();
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            PlayerProfile profile = services.profiles().cached(id).orElse(null);
            if (p == null || profile == null) {
                continue;
            }
            if (ar.downed.contains(id)) {
                p.sendMessage(Messages.info("Та унасан тул агуйн шагнал авсангүй."));
                continue;
            }
            int from = profile.progression().level();
            long completionExp = services.boosts().apply(id, SuldContent.KHASAR_DEN_COMPLETION_EXP);
            ExpGainResult exp = services.progression().grantExp(profile, completionExp, ExpSource.DUNGEON);
            services.clans().contribute(id, SuldContent.CLAN_EXP_PER_DUNGEON_CLEAR);
            profile.addCurrency(SuldContent.KHASAR_DEN_COMPLETION_CURRENCY);
            Presentation.banner(p, "АГУЙ ДУУСЛАА", ar.def.displayName() + " · " + formatTime(seconds),
                    NamedTextColor.GREEN);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendMessage(Messages.success("+" + completionExp + " EXP, +"
                    + SuldContent.KHASAR_DEN_COMPLETION_CURRENCY + " зоос"));
            if (exp.leveledUp()) {
                Presentation.levelUp(p, from, exp.after().level());
            }
            for (ItemInstance inst : lootRoller.roll(ar.def.rewardTable(), provenance)) {
                ItemDefinition idef = SuldContent.definitionFor(inst.definitionId());
                if (idef == null) {
                    continue;
                }
                ItemStack stack = services.items().create(inst, idef);
                p.getInventory().addItem(stack).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(Messages.info("Шагнал: " + idef.displayName() + " [Lvl " + inst.itemLevel() + "]"));
                if (inst.rarity().ordinal() >= ItemRarity.RARE.ordinal()) {
                    services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_RARE_ITEM, id));
                }
            }
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_BOSS, id));
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.BOSS_PARTICIPATION, id,
                    Map.of("boss", ar.def.bossDefinition().id(), "seconds", seconds)));
            services.profiles().save(profile);
        }
        cleanup(ar);
    }

    private void fail(ActiveRun ar, String reason) {
        if (!ar.run.isActive()) {
            return;
        }
        ar.run.fail();
        broadcast(ar, Messages.error("Агуй бүтэлгүйтлээ: " + reason));
        playAll(ar, Sound.ENTITY_WITHER_DEATH, 0.6f, 1f);
        cleanup(ar);
    }

    /** Admin/leader abort. */
    public boolean abort(UUID player, boolean admin) {
        ActiveRun ar = runOf(player).orElse(null);
        if (ar == null || !ar.run.isActive()) {
            return false;
        }
        if (!admin && !ar.party.isLeader(player)) {
            return false;
        }
        fail(ar, "Ахлагч зогсоосон.");
        return true;
    }

    private void cleanup(ActiveRun ar) {
        if (ar.ticker != null) {
            ar.ticker.cancel();
        }
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.hideBossBar(ar.bar);
            }
        }
        List<UUID> tracked = runsByEntity.entrySet().stream()
                .filter(e -> e.getValue() == ar).map(Map.Entry::getKey).toList();
        for (UUID id : tracked) {
            runsByEntity.remove(id);
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
            }
        }
        if (ar.bossId != null) {
            bosses.unregister(ar.bossId);
        }
        ar.waveMobs.clear();
        runsByParty.remove(ar.party.id());
        if (!ar.party.isDisbanded()) {
            ar.party.exitDungeon();
        }
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            PlayerProfile profile = services.profiles().cached(id).orElse(null);
            if (p != null && profile != null) {
                services.hud().update(p, profile);
            }
        }
    }

    /** Plugin disable: tear everything down without rewards. */
    public void shutdown() {
        for (ActiveRun ar : List.copyOf(runsByParty.values())) {
            if (ar.run.isActive()) {
                ar.run.fail();
            }
            cleanup(ar);
        }
        bosses.clear();
    }

    // ------------------------------------------------------------- per-tick

    private void tick(ActiveRun ar) {
        if (!ar.run.isActive()) {
            return;
        }
        if (ar.run.elapsedSeconds() > MAX_RUN_SECONDS) {
            fail(ar, "Хугацаа дууслаа.");
            return;
        }
        if (ar.bossId != null && Bukkit.getEntity(ar.bossId) instanceof LivingEntity boss) {
            bosses.tick(boss);
            MobService.keepHostile(boss, onlineParticipants(ar), 48);
        }
        List<Player> fighters = onlineParticipants(ar);
        for (UUID id : ar.waveMobs) {
            if (Bukkit.getEntity(id) instanceof LivingEntity mob) {
                MobService.keepHostile(mob, fighters, 32); // vanilla wolves are neutral otherwise
            }
        }
        updateBar(ar);
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            PlayerProfile profile = services.profiles().cached(id).orElse(null);
            if (p != null && profile != null) {
                services.hud().update(p, profile);
            }
        }
    }

    private void updateBar(ActiveRun ar) {
        switch (ar.run.state()) {
            case ENTERING -> {
                ar.bar.name(Component.text(ar.def.displayName() + " — бэлдэж байна", NamedTextColor.GOLD));
                ar.bar.progress(1f);
                ar.bar.color(BossBar.Color.YELLOW);
            }
            case WAVE -> {
                int total = Math.max(1, ar.run.waveMobCount());
                ar.bar.name(Component.text("Давалгаа " + ar.run.currentWave() + "/" + ar.run.totalWaves()
                        + " — " + ar.waveMobs.size() + " дайсан үлдсэн", NamedTextColor.GREEN));
                ar.bar.progress(Math.max(0f, Math.min(1f, ar.waveMobs.size() / (float) total)));
                ar.bar.color(BossBar.Color.GREEN);
            }
            case BOSS -> {
                float progress = 1f;
                if (ar.bossId != null && Bukkit.getEntity(ar.bossId) instanceof LivingEntity boss) {
                    progress = bosses.status(boss).map(st -> (float) st.hpFraction()).orElse(1f);
                }
                ar.bar.name(Component.text(ar.def.bossDefinition().displayName() + " · " + ar.phaseName,
                        NamedTextColor.RED));
                ar.bar.progress(Math.max(0f, Math.min(1f, progress)));
                ar.bar.color(BossBar.Color.RED);
            }
            default -> {
            }
        }
    }

    // --------------------------------------------------------------- queries

    public boolean isInAnyRun(UUID player) {
        return runOf(player).isPresent();
    }

    /** One-line HUD status for a player currently in a run. */
    public Optional<String> statusLine(UUID player) {
        ActiveRun ar = runOf(player).orElse(null);
        if (ar == null || !ar.run.isActive()) {
            return Optional.empty();
        }
        return Optional.of(switch (ar.run.state()) {
            case ENTERING -> "Бэлдэж байна";
            case WAVE -> "Давалгаа " + ar.run.currentWave() + "/" + ar.run.totalWaves()
                    + " (" + ar.waveMobs.size() + ")";
            case BOSS -> "Босс: " + ar.phaseName;
            default -> "—";
        });
    }

    public Optional<DungeonRun> runFor(UUID player) {
        return runOf(player).map(ar -> ar.run);
    }

    private Optional<ActiveRun> runOf(UUID player) {
        return parties.partyOf(player).map(p -> runsByParty.get(p.id()));
    }

    // --------------------------------------------------------------- helpers

    private static List<Player> onlineParticipants(ActiveRun ar) {
        List<Player> out = new java.util.ArrayList<>();
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !ar.downed.contains(id)) {
                out.add(p);
            }
        }
        return out;
    }

    private void broadcast(ActiveRun ar, Component message) {
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    private void playAll(ActiveRun ar, Sound sound, float volume, float pitch) {
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.playSound(p.getLocation(), sound, volume, pitch);
            }
        }
    }

    /** Spread mobs on a ring around the origin, falling back to the origin if blocked. */
    private static Location ringLocation(Location origin, int index, int count) {
        double angle = 2 * Math.PI * index / Math.max(1, count);
        Location loc = origin.clone().add(Math.cos(angle) * SPAWN_RADIUS, 0, Math.sin(angle) * SPAWN_RADIUS);
        if (!loc.getBlock().isPassable() || !loc.clone().add(0, 1, 0).getBlock().isPassable()) {
            return origin.clone();
        }
        return loc;
    }

    private static String nameOf(UUID id) {
        Player p = Bukkit.getPlayer(id);
        return p != null ? p.getName() : id.toString().substring(0, 8);
    }

    private static String formatTime(long seconds) {
        return (seconds / 60) + "м " + (seconds % 60) + "с";
    }
}
