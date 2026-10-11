package mn.suld.plugin.dungeon;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.analytics.AnalyticsEventType;
import mn.suld.api.dungeon.DungeonDefinition;
import mn.suld.api.dungeon.DungeonRun;
import mn.suld.api.dungeon.DungeonRunState;
import mn.suld.api.item.ItemDefinition;
import mn.suld.api.item.ItemInstance;
import mn.suld.api.item.ItemRarity;
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

    /** Scoreboard tag on every dungeon mob, so leftovers can be found after a restart or a failed run. */
    public static final String DUNGEON_TAG = "suld_dungeon";

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
        DungeonHalls.Hall hall;
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

    private final Map<UUID, ActiveRun> runsByParty = new HashMap<>();
    private final Map<UUID, ActiveRun> runsByEntity = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private final Map<UUID, String> lastDungeon = new java.util.concurrent.ConcurrentHashMap<>();
    private DungeonHalls halls;

    public Optional<DungeonHalls> halls() {
        return Optional.ofNullable(halls);
    }

    /** Runs then take place in the dungeon's hall (docs/world/DUNGEON_HALLS.md). */
    public void halls(DungeonHalls h) {
        this.halls = h;
        h.hooks(this::isInAnyRun, id -> Optional.ofNullable(lastDungeon.get(id)));
    }

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
            if (services.isSoul.test(id)) {
                return Messages.error("Сүнс төлөвтэй гишүүн байна — амилтал хүлээнэ үү.");
            }
            Player p = Bukkit.getPlayer(id);
            PlayerProfile profile = p == null ? null : services.profiles().cached(id).orElse(null);
            if (p == null || profile == null) {
                return Messages.error("Багийн гишүүн онлайн биш байна.");
            }
            if (profile.progression().level() < def.minLevel()) {
                return Messages.error(p.getName() + " түвшин " + def.minLevel() + "-д хүрээгүй байна.");
            }
            DungeonDefinition prev = mn.suld.plugin.content.DungeonContent.previous(def.id());
            if (prev != null && !cleared(p, prev.id()) && !leader.hasPermission("suld.admin.world")) {
                return Messages.error(p.getName() + " эхлээд «" + prev.displayName() + "»-г нэг удаа давах ёстой.");
            }
            // progression v2 gates (DungeonLadder): the story chapter before it and, from the fifth rung, gear power.
            // A dungeon already cleared once stays open whatever changed since.
            Component gate = v2Gate(p, def);
            if (gate != null && !cleared(p, def.id()) && !leader.hasPermission("suld.admin.world")) return gate;
            members.add(id);
        }

        if (halls != null && halls.ready()) {
            // the run happens in the dungeon's own hall; the party gathers at its gate in the open world
            mn.suld.api.dungeon.hall.DungeonSite site = halls.site(def.id()).orElse(null);
            if (site == null) return Messages.error("Энэ агуйд танхим тохируулаагүй байна.");
            if (!halls.nearGate(leader, def.id()) && !leader.hasPermission("suld.admin.world")) {
                int[] xz = halls.gateXZ(site);
                return Messages.error(def.displayName() + "-ийн хаалга дээр ирж орно: " + xz[0] + ", " + xz[1]
                        + " (" + mn.suld.api.region.Navigation.compass(mn.suld.api.region.Navigation.bearing(
                        xz[0] - leader.getLocation().getX(), xz[1] - leader.getLocation().getZ())) + " зүгт)");
            }
            if (!pending.add(party.id())) return Messages.error("Танхим бэлдэж байна, түр хүлээнэ үү.");
            leader.sendMessage(Messages.info("Танхим бэлдэж байна…"));
            Party fParty = party;
            halls.acquire(site.theme()).whenComplete((hall, err) -> {
                pending.remove(fParty.id());
                if (err != null || hall == null || hall.isEmpty()) {
                    if (leader.isOnline()) leader.sendMessage(Messages.error("Бүх танхим завгүй байна — хэдэн минутын дараа дахин оролдоно уу."));
                    return;
                }
                Component again = begin(leader, def, fParty, members, hall.get());
                if (again != null) {
                    halls.release(hall.get());
                    if (leader.isOnline()) leader.sendMessage(again);
                }
            });
            return null;
        }
        // no halls world: the run's arena is where the party stands, never inside Kharkhorum or right at its walls
        org.bukkit.Location here = leader.getLocation();
        if (services.city().near(here.getWorld().getName(), here.getBlockX(), here.getBlockZ(), 24)) {
            return Messages.error("Хархорумд агуйн аян эхлэхгүй. Хотын хаалгаар гараад тал нутагт /dungeon enter.");
        }
        return begin(leader, def, party, members, null);
    }

    /** Starts the run (main thread); with a hall the party is moved into it, otherwise it fights where the leader stands. */
    private Component begin(Player leader, DungeonDefinition def, Party party, Set<UUID> members, DungeonHalls.Hall hall) {
        if (!leader.isOnline()) return Messages.error("Ахлагч гарсан.");
        if (party.isDisbanded() || party.state() == PartyState.IN_DUNGEON || runsByParty.containsKey(party.id())) {
            return Messages.error("Баг аль хэдийн агуйд явж байна.");
        }
        if (!party.members().equals(members)) return Messages.error("Танхим бэлдэх зуур баг өөрчлөгдсөн — дахин оролдоно уу.");
        for (UUID id : members) {
            if (Bukkit.getPlayer(id) == null) return Messages.error("Багийн гишүүн онлайн биш байна.");
            if (services.isSoul.test(id)) return Messages.error("Сүнс төлөвтэй гишүүн байна — амилтал хүлээнэ үү.");
        }
        Location origin = hall == null ? leader.getLocation().clone() : hall.at(mn.suld.api.dungeon.hall.HallBlueprint.WAVE_CENTER);
        ActiveRun ar = new ActiveRun(new DungeonRun(def.id(), party.id(), def.totalWaves()), def, party, origin);
        ar.hall = hall;
        ar.participants.addAll(members);
        for (UUID id : members) services.session(id).dungeonRuns++;
        for (UUID id : members) services.dismissHorse.accept(id); // no riding an invulnerable horse through the run
        for (UUID id : members) lastDungeon.put(id, def.id());
        runsByParty.put(party.id(), ar);
        party.enterDungeon();

        Location enter = hall == null ? null : hall.at(mn.suld.api.dungeon.hall.HallBlueprint.PLAYER_SPAWN);
        for (UUID id : members) {
            Player p = Bukkit.getPlayer(id);
            if (enter != null) {
                p.teleportAsync(enter);
            } else if (!p.getUniqueId().equals(leader.getUniqueId())) {
                p.teleportAsync(ar.origin); // the leader's chunk is loaded, but a member far away must not block the tick
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
        }, hall == null ? 60L : 120L); // in a hall: time to walk out of the corridor
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
            mob.addScoreboardTag(DUNGEON_TAG);
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
        // boss health is tuned for the dungeon's recommended party (×0.44 solo dungeon … ×1.0 for four)
        double bossHp = bossMob.scaledHealth() * mn.suld.api.balance.MobScaling.bossPartyScale(ar.def.recommendedParty());
        LivingEntity boss = mobs.spawn(bossMob, ar.hall == null ? ar.origin.clone() : ar.hall.at(mn.suld.api.dungeon.hall.HallBlueprint.BOSS_SPAWN), bossHp);
        boss.setRemoveWhenFarAway(false);
        boss.addScoreboardTag(DUNGEON_TAG);
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
                ? "Босс галзуурлаа! Тулаан хэт удсан тул цохилт нь ×" + BossService.ENRAGE_MULTIPLIER + " хүчтэй боллоо!"
                : "Босс шинэ шат: " + change.phase().phaseName() + " (х" + change.phase().attackMultiplier() + ")";
        broadcast(ar, Messages.error(text));
        updateBar(ar);
    }

    // ----------------------------------------------------------- death hooks

    /**
     * Track a mob a boss spawned mid-fight (a howl's wolves) as part of the boss's run, so the run's cleanup removes
     * it. Not a wave mob: killing it does not advance anything. False when the boss is in no run.
     */
    public boolean adopt(UUID boss, LivingEntity add) {
        ActiveRun ar = runsByEntity.get(boss);
        if (ar == null || !ar.run.isActive()) return false;
        runsByEntity.put(add.getUniqueId(), ar);
        add.addScoreboardTag(DUNGEON_TAG);
        return true;
    }

    private final org.bukkit.NamespacedKey clearsKey = new org.bukkit.NamespacedKey("suld", "dungeon_clears");

    /** True once {@code p} has cleared the dungeon (kept in the player's data; opens the next one on the ladder). */
    /** Why {@code p} may not enter {@code def} yet under the ladder's chapter and gear-power gates, or null. */
    Component v2Gate(Player p, DungeonDefinition def) {
        mn.suld.api.balance.DungeonLadder.Rung rung = mn.suld.api.balance.DungeonLadder.rung(def.id());
        if (rung == null) return null;
        int story = services.quests().chain().size();
        int need = Math.min(rung.chapterGate() + 1, story);
        int done = services.skillTree() == null ? story : services.skillTree().context(p).finishedChapters();
        if (done < need) {
            return Messages.error(p.getName() + ": «" + def.displayName() + "» — түүхийн " + need + "-р бүлгийг дуусгасны дараа нээгдэнэ (одоо "
                    + done + "). /quest");
        }
        if (mn.suld.api.balance.DungeonLadder.index(def.id()) >= 4) {
            double gp = gearPower(p), min = 0.75 * mn.suld.api.balance.GearPower.par(rung.min());
            if (gp < min) {
                return Messages.error(p.getName() + ": тоног хэрэгслийн хүч " + Math.round(gp) + " / " + Math.round(min)
                        + " — илүү сайн, өндөр түвшний хуяг зэвсэг өмс.");
            }
        }
        return null;
    }

    /** Gear power of what {@code p} wears (GearPower: item level × rarity × roll quality). */
    double gearPower(Player p) {
        mn.suld.plugin.item.EquipmentService eq = services.equipment();
        mn.suld.plugin.item.ItemService items = services.itemService();
        if (eq == null || items == null) return Double.MAX_VALUE;
        double gp = 0;
        for (ItemInstance i : eq.worn(p).values()) {
            ItemDefinition d = items.catalog().item(i.definitionId()).orElse(null);
            if (d != null) gp += mn.suld.api.balance.GearPower.item(d, i);
        }
        return gp;
    }

    public boolean cleared(Player p, String dungeonId) {
        String s = p.getPersistentDataContainer().get(clearsKey, org.bukkit.persistence.PersistentDataType.STRING);
        return s != null && java.util.Arrays.asList(s.split(",")).contains(dungeonId);
    }

    /** Support: count a dungeon as cleared for {@code p} (opens the next one). */
    public void grantClear(Player p, String dungeonId) {
        markCleared(p, dungeonId);
    }

    private void markCleared(Player p, String dungeonId) {
        if (cleared(p, dungeonId)) return;
        String s = p.getPersistentDataContainer().get(clearsKey, org.bukkit.persistence.PersistentDataType.STRING);
        p.getPersistentDataContainer().set(clearsKey, org.bukkit.persistence.PersistentDataType.STRING, s == null || s.isEmpty() ? dungeonId : s + "," + dungeonId);
    }

    /**
     * Clears of a dungeon by a player in the last {@link #FATIGUE_WINDOW_MS} (loot fatigue). Kept in
     * {@code plugins/SULD/dungeon-fatigue.yml} as well, so a restart does not reset it.
     */
    private final Map<String, java.util.ArrayDeque<Long>> recentClears = new HashMap<>();
    private boolean fatigueLoaded;

    private java.io.File fatigueFile() {
        return new java.io.File(plugin.getDataFolder(), "dungeon-fatigue.yml");
    }

    private void loadFatigue(long now) {
        fatigueLoaded = true;
        java.io.File f = fatigueFile();
        if (!f.exists()) return;
        org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        for (String key : y.getKeys(false)) {
            java.util.ArrayDeque<Long> q = new java.util.ArrayDeque<>();
            for (long t : y.getLongList(key)) if (now - t <= FATIGUE_WINDOW_MS) q.addLast(t);
            if (!q.isEmpty()) recentClears.put(key.replace('|', ':'), q);
        }
    }

    /** Written off the main thread, atomically; expired entries are dropped first. */
    private void saveFatigue(long now) {
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        recentClears.entrySet().removeIf(en -> {
            en.getValue().removeIf(t -> now - t > FATIGUE_WINDOW_MS);
            return en.getValue().isEmpty();
        });
        recentClears.forEach((k, q) -> y.set(k.replace(':', '|'), new java.util.ArrayList<>(q)));
        String text = y.saveToString();
        java.nio.file.Path file = fatigueFile().toPath();
        Runnable write = () -> {
            try {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Path tmp = file.resolveSibling("dungeon-fatigue.yml.tmp");
                java.nio.file.Files.writeString(tmp, text);
                java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (java.io.IOException e) {
                plugin.getLogger().warning("dungeon-fatigue.yml: " + e.getMessage());
            }
        };
        if (plugin.isEnabled()) Bukkit.getScheduler().runTaskAsynchronously(plugin, write);
        else write.run();
    }
    static final long FATIGUE_WINDOW_MS = 2 * 60 * 60 * 1000L;

    /**
     * Gear chance of the completion reward: full for the first 3 clears of one dungeon in a rolling 2 hours, then
     * ×0.5, then ×0.25 (never zero). Materials, EXP and coins are not reduced. Records this clear.
     */
    double clearFatigue(UUID player, String dungeonId, long now) {
        if (!fatigueLoaded) loadFatigue(now);
        double f = clearFatigue0(player, dungeonId, now);
        saveFatigue(now);
        return f;
    }

    private double clearFatigue0(UUID player, String dungeonId, long now) {
        java.util.ArrayDeque<Long> q = recentClears.computeIfAbsent(player + ":" + dungeonId, k -> new java.util.ArrayDeque<>());
        while (!q.isEmpty() && now - q.peekFirst() > FATIGUE_WINDOW_MS) q.pollFirst();
        int before = q.size();
        q.addLast(now);
        return fatigueFactor(before);
    }

    static double fatigueFactor(int clearsBefore) {
        return clearsBefore < 3 ? 1.0 : clearsBefore < 5 ? 0.5 : 0.25;
    }

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
        if (p != null && ar.hall != null && halls != null) halls.sendOut(p, ar.def.id()); // never left behind in a hall
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

    /** A participant counts only while they are in the arena (same world, within 80 blocks of where it opened). */
    private boolean present(ActiveRun ar, Player p) {
        return p.getWorld().equals(ar.origin.getWorld()) && p.getLocation().distanceSquared(ar.origin) <= 80 * 80;
    }

    /** True while a living dungeon mob belongs to a run (used to sweep leftovers from before a restart or a failed run). */
    public boolean ownsEntity(UUID entity) {
        return runsByEntity.containsKey(entity);
    }

    private void checkWipe(ActiveRun ar) {
        if (!ar.run.isActive()) {
            return;
        }
        boolean anyoneAlive = false;
        for (UUID id : ar.participants) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !ar.downed.contains(id) && !p.isDead() && present(ar, p)) {
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
            if (!present(ar, p)) {
                p.sendMessage(Messages.info("Та агуйн талбарт байгаагүй тул шагнал авсангүй."));
                continue;
            }
            int from = profile.progression().level();
            services.session(id).dungeonClears++;
            services.session(id).bossesDefeated++;
            var bonus = completionReward(ar, profile);
            long completionExp = services.boosts().apply(id, bonus.exp());
            ExpGainResult exp = services.progression().grantExp(profile, completionExp, ExpSource.DUNGEON);
            services.clans().contribute(id, SuldContent.CLAN_EXP_PER_DUNGEON_CLEAR);
            profile.addCurrency(bonus.coins());
            Presentation.banner(p, "АГУЙ ДУУСЛАА", ar.def.displayName() + " · " + formatTime(seconds),
                    NamedTextColor.GREEN);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            p.sendMessage(Messages.success("+" + completionExp + " EXP, +"
                    + bonus.coins() + " зоос"));
            if (exp.leveledUp()) {
                Presentation.levelUp(p, from, exp.after().level());
            }
            mn.suld.plugin.item.ItemService items = services.itemService();
            // reward items at the player's own level (wearable now), capped at the dungeon's band: an over-levelled player
            // farming an early dungeon gets that dungeon's gear (DungeonDefinition.rewardLevel)
            mn.suld.api.loot.LootContext ctx = new mn.suld.api.loot.LootContext(ar.def.rewardLevel(profile.progression().level()),
                    mn.suld.api.loot.LootTier.DUNGEON, profile.playerClass().orElse(null), lootBonus(p), id, provenance);
            java.util.List<mn.suld.api.loot.LootDrop> rewards = new java.util.ArrayList<>(items == null ? java.util.List.of() : items.roll(ar.def.rewardTableId(), ctx));
            double fatigue = clearFatigue(id, ar.def.id(), System.currentTimeMillis());
            if (fatigue < 1 && items != null) {
                rewards.removeIf(d -> !items.catalog().require(d.item().definitionId()).stackable()
                        && java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= fatigue);
                p.sendMessage(Messages.info("Энэ агуйг саяхан олон удаа цэвэрлэсэн: хуяг зэвсгийн шагнал ×" + fatigue + " (2 цагийн дотор)."));
            }
            if (items != null) rewards = items.filtered(p, rewards);
            for (mn.suld.api.loot.LootDrop drop : rewards) {
                ItemInstance inst = drop.item();
                ItemDefinition idef = items.catalog().require(inst.definitionId());
                ItemStack stack = items.stack(inst, p, drop.amount());
                p.getInventory().addItem(stack).values()
                        .forEach(left -> p.getWorld().dropItemNaturally(p.getLocation(), left));
                p.sendMessage(Messages.info("Шагнал: " + mn.suld.api.item.ItemTooltip.name(items.catalog(), idef, inst)
                        + (drop.amount() > 1 ? " ×" + drop.amount() : "") + " [" + inst.rarity().displayName() + ", Зэрэг " + inst.itemLevel() + "]"));
                items.announce(p, inst);
                if (inst.rarity().ordinal() >= ItemRarity.RARE.ordinal()) {
                    services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_RARE_ITEM, id));
                }
            }
            services.quests().onDungeonCleared(p, profile, ar.def.id());
            markCleared(p, ar.def.id());
            if (services.classArmor != null) services.classArmor.dungeonCleared(p, ar.def.id());
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.FIRST_BOSS, id));
            services.analytics().record(AnalyticsEvent.of(AnalyticsEventType.BOSS_PARTICIPATION, id,
                    Map.of("boss", ar.def.bossDefinition().id(), "seconds", seconds)));
            services.profiles().save(profile);
        }
        cleanup(ar);
    }

    /**
     * Completion EXP and coins (progression v2): 4 % of a level at the dungeon's content level and 60 + 12·that level
     * coins (Rewards), × the repeat factor (−15 % per clear of it among the last 8 clears, floor 25 %) × the carry
     * factor (above the dungeon's max level ×0.1; 10+ levels below the party's best ×0.5). Off the ladder: the table.
     */
    private mn.suld.plugin.content.DungeonContent.Completion completionReward(ActiveRun ar, PlayerProfile profile) {
        mn.suld.api.balance.DungeonLadder.Rung rung = mn.suld.api.balance.DungeonLadder.rung(ar.def.id());
        if (rung == null) return mn.suld.plugin.content.DungeonContent.completion(ar.def.id());
        int top = 1;
        for (UUID m : ar.participants) top = Math.max(top, services.profiles().cached(m).map(x -> x.progression().level()).orElse(1));
        double f = mn.suld.api.balance.DungeonRules.repeatFactor(profile.classGear().recent(), ar.def.id())
                * mn.suld.api.balance.DungeonRules.carryFactor(profile.progression().level(), top, rung.max());
        var curve = services.progression().engine().curve();
        return new mn.suld.plugin.content.DungeonContent.Completion(
                Math.max(1, Math.round(mn.suld.api.balance.Rewards.dungeonExp(curve, rung.contentLevel()) * f)),
                Math.max(1, Math.round(mn.suld.api.balance.Rewards.dungeonCoins(rung.contentLevel()) * f)));
    }

    private double lootBonus(Player p) {
        mn.suld.plugin.skill.SkillTreeService t = services.skillTree();
        return t == null ? 0 : t.lootPct(p);
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
        if (ar.hall != null) {
            DungeonHalls.Hall hall = ar.hall;
            List<UUID> people = List.copyOf(ar.participants);
            long delay = ar.run.state() == DungeonRunState.COMPLETE ? 100L : 20L; // a moment to see the win
            Runnable out = () -> {
                for (UUID id : people) {
                    Player p = Bukkit.getPlayer(id);
                    if (p != null) halls.sendOut(p, ar.def.id());
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> halls.release(hall), 40L);
            };
            if (plugin.isEnabled()) Bukkit.getScheduler().runTaskLater(plugin, out, delay); else halls.release(hall);
        }
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
