package mn.suld.plugin.relic;

import mn.suld.api.analytics.AnalyticsEvent;
import mn.suld.api.audit.AuditEvent;
import mn.suld.api.config.RelicSettings;
import mn.suld.api.persistence.RelicRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.api.relic.CasResult;
import mn.suld.api.relic.RelicDefinition;
import mn.suld.api.relic.RelicEvent;
import mn.suld.api.relic.RelicHints;
import mn.suld.api.relic.RelicHistoryEntry;
import mn.suld.api.relic.RelicRecord;
import mn.suld.api.relic.RelicRules;
import mn.suld.api.relic.RelicTransition;
import mn.suld.api.relic.RelicValidator;
import mn.suld.api.relic.ShrineLocation;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.content.SuldContent;
import mn.suld.plugin.ui.Messages;
import mn.suld.plugin.ui.Presentation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * World-unique relics end to end. Invariants:
 * <ul>
 *   <li>The database row is the only truth about who bears a relic. Every ownership change is a
 *       compare-and-set ({@link RelicRepository#apply}); the cache here only mirrors results.</li>
 *   <li>A physical copy may exist only in its bearer's own inventory, at the current
 *       generation, exactly once. {@link RelicValidator} enforces this on join, every 10 s and
 *       whenever a container opens; the bearer is re-issued a copy if theirs went missing.</li>
 *   <li>Relics never exist as dropped items, in containers, bundles, frames or stands.</li>
 * </ul>
 */
public final class RelicService {

    private static final long VALIDATE_EVERY_TICKS = 20L * 10;
    private static final long RITUAL_TICK = 20L;
    private static final long EXPIRY_EVERY_TICKS = 20L * 60 * 30;
    private static final double RITUAL_RADIUS = 3.5;
    private static final int SHRINE_HALF = 2;

    private record Ritual(String key, Location shrineCenter, int secondsLeft) {
    }

    private final Plugin plugin;
    private final SuldServices services;
    private final RelicRepository repository;
    private final RelicSettings settings;
    private final RelicItems items;
    private final Map<String, RelicRecord> records = new HashMap<>();
    private final Map<UUID, Ritual> rituals = new HashMap<>();
    private final Map<UUID, Long> hintUsedAt = new HashMap<>();

    public RelicService(Plugin plugin, SuldServices services, RelicRepository repository,
                        RelicSettings settings, RelicItems items) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
        this.items = items;
    }

    // ------------------------------------------------------------- startup

    /** Mint (first boot only) and load every relic. Blocking, bounded; run in onEnable. */
    public int load() {
        try {
            for (RelicDefinition def : SuldContent.RELICS) {
                repository.ensure(def.key(), UUID.randomUUID()).get(15, TimeUnit.SECONDS);
            }
            for (RelicRecord r : repository.loadAll().get(15, TimeUnit.SECONDS)) {
                records.put(r.key(), r);
            }
            return records.size();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load relics: " + ex.getMessage(), ex);
        }
    }

    public void start() {
        if (!settings.enabled()) {
            plugin.getLogger().info("Relics disabled in config.");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, this::autoPlaceShrines);
        Bukkit.getScheduler().runTaskTimer(plugin, this::validateAll, VALIDATE_EVERY_TICKS, VALIDATE_EVERY_TICKS);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRituals, RITUAL_TICK, RITUAL_TICK);
        Bukkit.getScheduler().runTaskTimer(plugin, this::expireAbsentBearers, 20L * 60, EXPIRY_EVERY_TICKS);
    }

    // ------------------------------------------------------------- queries

    public Collection<RelicRecord> all() {
        return List.copyOf(records.values());
    }

    public Optional<RelicRecord> record(String key) {
        RelicDefinition def = SuldContent.relicFor(key);
        return def == null ? Optional.empty() : Optional.ofNullable(records.get(def.key()));
    }

    public Optional<RelicRecord> borneBy(UUID player) {
        return records.values().stream().filter(r -> r.isOwnedBy(player)).findFirst();
    }

    public double expBonus(UUID player) {
        return borneBy(player).map(r -> SuldContent.relicFor(r.key())).map(RelicDefinition::expBonus).orElse(0.0);
    }

    public Optional<String> hudLine(UUID player) {
        return borneBy(player).map(r -> "§7Сүлд: §b" + SuldContent.relicFor(r.key()).displayName());
    }

    public RelicItems items() {
        return items;
    }

    // ------------------------------------------------------- anti-duplication

    /** Reconcile a player's own inventory with the database. Main thread. */
    public void validate(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        List<RelicValidator.Found> found = new ArrayList<>();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            stripFromBundle(stack, "bundle in inventory of " + player.getName());
            items.read(stack, slot).ifPresent(found::add);
        }
        ItemStack cursor = player.getItemOnCursor();
        if (items.isRelic(cursor)) {
            // Never reconcile mid-drag; the next pass sees it once it lands in a slot.
            return;
        }
        RelicValidator.Plan plan = RelicValidator.plan(player.getUniqueId(), found, records);
        for (RelicValidator.Removal r : plan.removals()) {
            inv.setItem(r.slot(), null);
            auditRemoval(player.getUniqueId().toString(), player.getName(), r.key(), r.reason().name());
        }
        for (int slot : plan.fixAmount()) {
            ItemStack s = inv.getItem(slot);
            if (s != null) {
                s.setAmount(1);
            }
        }
        for (String key : plan.issue()) {
            issueCopy(player, records.get(key));
        }
        boolean bears = borneBy(player.getUniqueId()).isPresent();
        if (player.isGlowing() != bears) {
            player.setGlowing(bears); // the bearer is visible to everyone: holding a relic means being hunted
        }
    }

    public void validateAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            validate(p);
        }
    }

    /** Relics may never rest in any container (chests, ender chests, shulkers, hoppers...). */
    public int purgeContainer(Inventory inventory, String where) {
        int removed = 0;
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack stack = contents[slot];
            if (stripFromBundle(stack, where)) {
                inventory.setItem(slot, stack);
            }
            Optional<RelicValidator.Found> f = items.read(stack, slot);
            if (f.isPresent()) {
                inventory.setItem(slot, null);
                auditRemoval("server", where, f.get().key(), RelicValidator.Reason.IN_CONTAINER.name());
                removed++;
            }
        }
        return removed;
    }

    private boolean stripFromBundle(ItemStack stack, String where) {
        if (stack == null || !(stack.getItemMeta() instanceof BundleMeta bundle) || !bundle.hasItems()) {
            return false;
        }
        List<ItemStack> kept = new ArrayList<>();
        boolean changed = false;
        for (ItemStack inner : bundle.getItems()) {
            Optional<RelicValidator.Found> f = items.read(inner, -1);
            if (f.isPresent()) {
                changed = true;
                auditRemoval("server", where, f.get().key(), "IN_BUNDLE");
            } else {
                kept.add(inner);
            }
        }
        if (changed) {
            bundle.setItems(kept);
            stack.setItemMeta(bundle);
        }
        return changed;
    }

    private void issueCopy(Player player, RelicRecord record) {
        RelicDefinition def = SuldContent.relicFor(record.key());
        ItemStack copy = items.create(def, record);
        PlayerInventory inv = player.getInventory();
        if (!inv.addItem(copy).isEmpty()) {
            // Inventory full: the relic must still be carried. Displace an ordinary item to the ground.
            int slot = 35;
            ItemStack displaced = inv.getItem(slot);
            if (displaced != null && !items.isRelic(displaced)) {
                player.getWorld().dropItemNaturally(player.getLocation(), displaced);
            }
            inv.setItem(slot, copy);
        }
    }

    private void removeAllCopies(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (items.isRelic(contents[slot])) {
                inv.setItem(slot, null);
            }
        }
        if (items.isRelic(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void auditRemoval(String actor, String where, String key, String reason) {
        services.audit().record(AuditEvent.of(actor, "relic.copy_removed", key, reason + " @ " + where));
    }

    // ---------------------------------------------------------- discovery

    /** Shrines without a location are built automatically on the overworld surface. */
    public void autoPlaceShrines() {
        World world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (world == null) {
            return;
        }
        for (RelicRecord r : List.copyOf(records.values())) {
            if (r.shrine() != null) {
                // Make sure the structure exists (e.g. after a world reset), without moving it.
                Location c = toLocation(r.shrine());
                if (c != null && c.getBlock().getType() != Material.LODESTONE) {
                    buildShrine(c);
                }
                continue;
            }
            Location center = findShrineSpot(world);
            if (center == null) {
                plugin.getLogger().warning("Could not find a dry surface spot for " + r.key() + "; set it with /relic setshrine");
                continue;
            }
            buildShrine(center);
            persistShrine(r.key(), center, "server:auto");
        }
    }

    private Location findShrineSpot(World world) {
        Location spawn = world.getSpawnLocation();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
            double radius = ThreadLocalRandom.current().nextDouble(settings.autoPlaceMinRadius(), settings.autoPlaceMaxRadius());
            int x = spawn.getBlockX() + (int) Math.round(Math.cos(angle) * radius);
            int z = spawn.getBlockZ() + (int) Math.round(Math.sin(angle) * radius);
            world.getChunkAt(x >> 4, z >> 4); // generate/load the one chunk we need
            int y = world.getHighestBlockYAt(x, z);
            Block ground = world.getBlockAt(x, y, z);
            if (ground.isLiquid() || ground.getType() == Material.ICE || y <= world.getMinHeight() + 5) {
                continue;
            }
            return new Location(world, x, y + 1, z);
        }
        return null;
    }

    /** A small ovoo-style shrine: stone platform, lodestone altar, blue pillars with soul lanterns. */
    public void buildShrine(Location center) {
        World w = center.getWorld();
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();
        for (int dx = -SHRINE_HALF; dx <= SHRINE_HALF; dx++) {
            for (int dz = -SHRINE_HALF; dz <= SHRINE_HALF; dz++) {
                w.getBlockAt(cx + dx, cy - 1, cz + dz).setType(Material.STONE_BRICKS, false);
                for (int dy = 0; dy <= 3; dy++) {
                    w.getBlockAt(cx + dx, cy + dy, cz + dz).setType(Material.AIR, false);
                }
                if (Math.abs(dx) == SHRINE_HALF && Math.abs(dz) == SHRINE_HALF) {
                    w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.BLUE_TERRACOTTA, false);
                    w.getBlockAt(cx + dx, cy + 1, cz + dz).setType(Material.BLUE_TERRACOTTA, false);
                    w.getBlockAt(cx + dx, cy + 2, cz + dz).setType(Material.SOUL_LANTERN, false);
                }
            }
        }
        w.getBlockAt(cx, cy, cz).setType(Material.LODESTONE, false);
    }

    private void persistShrine(String key, Location center, String actor) {
        ShrineLocation s = new ShrineLocation(center.getWorld().getName(), center.getBlockX(), center.getBlockY(), center.getBlockZ());
        whenDone(repository.setShrine(key, s, actor), (rec, err) -> {
            if (err != null) {
                plugin.getLogger().severe("Failed to store shrine for " + key + ": " + err.getMessage());
                return;
            }
            records.put(rec.key(), rec);
            plugin.getLogger().info("Shrine for " + key + " at " + s);
        });
    }

    /** Which relic's shrine (if any) this block belongs to; the lodestone is the altar. */
    public Optional<RelicRecord> shrineAt(Block block, boolean altarOnly) {
        for (RelicRecord r : records.values()) {
            ShrineLocation s = r.shrine();
            if (s == null || !s.world().equals(block.getWorld().getName())) {
                continue;
            }
            int dx = block.getX() - s.x(), dy = block.getY() - s.y(), dz = block.getZ() - s.z();
            if (altarOnly ? (dx == 0 && dy == 0 && dz == 0)
                    : (Math.abs(dx) <= SHRINE_HALF && Math.abs(dz) <= SHRINE_HALF && dy >= -1 && dy <= 3)) {
                return Optional.of(r);
            }
        }
        return Optional.empty();
    }

    /** Right-click on an altar: begin the claim ritual if the player qualifies. */
    public void beginRitual(Player player, RelicRecord record) {
        if (services.isSoul.test(player.getUniqueId())) {
            player.sendMessage(Messages.error("Сүнс байхдаа реликс эзэмшихгүй."));
            return;
        }
        RelicDefinition def = SuldContent.relicFor(record.key());
        if (!record.isAvailable()) {
            player.sendMessage(Messages.info(def.displayName() + " одоо " + record.ownerName() + "-д байна. Сүм хоосон."));
            return;
        }
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) {
            return;
        }
        int borne = borneBy(player.getUniqueId()).isPresent() ? 1 : 0;
        Optional<RelicRules.ClaimDenial> denial = RelicRules.checkClaim(record, def, profile.playerClass().isPresent(),
                profile.progression().level(), borne);
        if (denial.isPresent()) {
            player.sendMessage(Messages.error(switch (denial.get()) {
                case LEVEL_TOO_LOW -> "Сүлд таныг хүлээн зөвшөөрсөнгүй — " + def.minLevel() + "-р түвшин хэрэгтэй.";
                case ALREADY_BEARER -> "Та аль хэдийн нэг сүлд барьж байна.";
                case NO_CLASS -> "Эхлээд ангиа сонгоно уу.";
                default -> "Энэ сүлдийг одоо авах боломжгүй.";
            }));
            return;
        }
        if (rituals.containsKey(player.getUniqueId())) {
            return;
        }
        Location center = toLocation(record.shrine());
        rituals.put(player.getUniqueId(), new Ritual(record.key(), center, settings.ritualSeconds()));
        player.sendMessage(Messages.accent(def.displayName() + "-ийн тахилга эхэллээ. " + settings.ritualSeconds()
                + " секунд хөдөлгөөнгүй, гэмтэлгүй зогсоорой..."));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.8f);
    }

    public void interruptRitual(Player player, String reason) {
        if (rituals.remove(player.getUniqueId()) != null) {
            player.sendMessage(Messages.error("Тахилга тасарлаа: " + reason));
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1f);
        }
    }

    private void tickRituals() {
        for (Map.Entry<UUID, Ritual> e : List.copyOf(rituals.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            Ritual r = e.getValue();
            if (p == null || p.isDead() || !p.getWorld().equals(r.shrineCenter().getWorld())
                    || p.getLocation().distance(r.shrineCenter().clone().add(0.5, 0, 0.5)) > RITUAL_RADIUS) {
                rituals.remove(e.getKey());
                if (p != null) {
                    interruptRitualMessage(p);
                }
                continue;
            }
            int left = r.secondsLeft() - 1;
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, r.shrineCenter().clone().add(0.5, 1.2, 0.5), 12, 0.6, 0.6, 0.6, 0.01);
            if (left > 0) {
                rituals.put(e.getKey(), new Ritual(r.key(), r.shrineCenter(), left));
                services.hud().toast(p, Component.text("Тахилга: " + left + "с", NamedTextColor.AQUA));
                p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 0.5f + 0.1f * (settings.ritualSeconds() - left));
            } else {
                rituals.remove(e.getKey());
                completeClaim(p, r.key());
            }
        }
    }

    private void interruptRitualMessage(Player p) {
        p.sendMessage(Messages.error("Тахилга тасарлаа: та сүмээс холдлоо."));
    }

    private void completeClaim(Player player, String key) {
        RelicRecord rec = records.get(key);
        RelicDefinition def = SuldContent.relicFor(key);
        if (rec == null || !rec.isAvailable()) {
            player.sendMessage(Messages.error("Хэн нэгэн таныг түрүүлжээ."));
            return;
        }
        UUID id = player.getUniqueId();
        whenDone(repository.apply(RelicTransition.claim(rec, id, player.getName(), RelicEvent.DISCOVERED, id.toString(),
                "ritual at shrine")), (cas, err) -> {
            if (err != null) {
                player.sendMessage(Messages.error("Сүлд таныг хүлээн авсангүй (" + rootName(err) + ")."));
                refresh(key);
                return;
            }
            records.put(key, cas.current());
            if (!cas.success()) {
                player.sendMessage(Messages.error("Хэн нэгэн таныг түрүүлжээ."));
                return;
            }
            services.audit().record(AuditEvent.of(id.toString(), "relic.discovered", key, player.getName()));
            services.analytics().record(AnalyticsEvent.of("relic_discovered", id, Map.of("relic", key)));
            if (player.isOnline()) {
                validate(player); // issues the copy, turns on glowing
                Presentation.banner(player, def.displayName().toUpperCase(java.util.Locale.ROOT), "Цор ганц сүлд таных боллоо",
                        NamedTextColor.AQUA);
            }
            announce(Component.text("✦ " + player.getName() + " " + def.displayName() + "-ийг олж авлаа! ✦", NamedTextColor.AQUA),
                    Sound.UI_TOAST_CHALLENGE_COMPLETE);
            refreshHud(player);
        });
    }

    public void hint(Player player) {
        long now = System.currentTimeMillis();
        long last = hintUsedAt.getOrDefault(player.getUniqueId(), 0L);
        long wait = settings.hintCooldownSeconds() * 1000L - (now - last);
        if (wait > 0 && !player.hasPermission("suld.admin.relic")) {
            player.sendMessage(Messages.error("Сүнс тайван бус байна... " + (wait / 1000) + "с хүлээнэ үү."));
            return;
        }
        RelicRecord nearest = null;
        double best = Double.MAX_VALUE;
        for (RelicRecord r : records.values()) {
            if (!r.isAvailable() || r.shrine() == null || !r.shrine().world().equals(player.getWorld().getName())) {
                continue;
            }
            double d = r.shrine().horizontalDistance(player.getLocation().getX(), player.getLocation().getZ());
            if (d < best) {
                best = d;
                nearest = r;
            }
        }
        if (nearest == null) {
            player.sendMessage(Messages.info("Энэ ертөнцөд эзэнгүй сүлд мэдрэгдсэнгүй."));
            return;
        }
        hintUsedAt.put(player.getUniqueId(), now);
        player.sendMessage(Messages.accent(SuldContent.relicFor(nearest.key()).displayName() + " — "
                + RelicHints.describe(player.getLocation().getX(), player.getLocation().getZ(),
                nearest.shrine().x(), nearest.shrine().z())));
    }

    /** New players get a teaser about the relics after five minutes (once per session). */
    public void scheduleTease(Player player, PlayerProfile profile) {
        if (Duration.between(profile.createdAt(), Instant.now()).toHours() >= 24) {
            return;
        }
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.sendMessage(Messages.accent("…Тал нутагт ганцхан Хөх Сүлд байдаг гэдэг. Түүнийг барьсан нэгэн бүхнийг удирдана."));
                p.sendMessage(Messages.info("/relic — сүлднүүдийн тухай, /relic hint — зүг чиг"));
            }
        }, 20L * 60 * 5);
    }

    // ------------------------------------------------------------- death

    /**
     * Bearer died: the physical copy never drops. A different player who killed them seizes the
     * relic (if they bear none); any other death returns it to the shrine.
     */
    public void onDeath(Player victim, List<ItemStack> drops) {
        drops.removeIf(items::isRelic);
        interruptRitual(victim, "та нас барлаа");
        Optional<RelicRecord> borne = borneBy(victim.getUniqueId());
        if (borne.isEmpty()) {
            return;
        }
        RelicRecord rec = borne.get();
        RelicDefinition def = SuldContent.relicFor(rec.key());
        Player killer = victim.getKiller();
        int killerBears = killer != null && borneBy(killer.getUniqueId()).isPresent() ? 1 : 0;
        RelicRules.DeathOutcome outcome = RelicRules.onBearerDeath(victim.getUniqueId(),
                killer == null ? null : killer.getUniqueId(), killerBears);
        removeAllCopies(victim);
        if (outcome == RelicRules.DeathOutcome.SEIZE_BY_KILLER) {
            UUID kid = killer.getUniqueId();
            whenDone(repository.apply(RelicTransition.claim(rec, kid, killer.getName(), RelicEvent.SEIZED, kid.toString(),
                    "killed " + victim.getName())), (cas, err) -> {
                if (cas != null) {
                    records.put(rec.key(), cas.current());
                }
                if (err != null || !cas.success()) {
                    // Only fall back to the shrine if the dead bearer still holds it; if anyone else
                    // changed it meanwhile (admin grant, ...), their newer state wins untouched.
                    returnToShrine(rec.key(), victim.getUniqueId(), "seize failed");
                    return;
                }
                records.put(rec.key(), cas.current());
                services.audit().record(AuditEvent.of(kid.toString(), "relic.seized", rec.key(),
                        "from " + victim.getUniqueId() + " (" + victim.getName() + ")"));
                if (killer.isOnline()) {
                    validate(killer);
                    refreshHud(killer);
                }
                refreshHud(victim);
                announce(Component.text("⚔ " + killer.getName() + " " + victim.getName() + "-ээс " + def.displayName()
                        + "-ийг булааж авлаа!", NamedTextColor.RED), Sound.ENTITY_WITHER_SPAWN);
            });
        } else {
            returnToShrine(rec.key(), victim.getUniqueId(), "bearer " + victim.getName() + " died");
        }
    }

    /** Release to the shrine, but only while {@code expectedBearer} still holds it. */
    private void returnToShrine(String key, UUID expectedBearer, String reason) {
        RelicRecord rec = records.get(key);
        if (rec == null || !rec.isOwnedBy(expectedBearer)) {
            return;
        }
        UUID formerBearer = rec.owner();
        whenDone(repository.apply(RelicTransition.release(rec, RelicEvent.RETURNED, "server", reason)), (cas, err) -> {
            if (err != null) {
                plugin.getLogger().severe("Relic return failed for " + key + ": " + rootName(err));
                return;
            }
            records.put(key, cas.current());
            if (!cas.success()) {
                return; // something else changed it first; the fresh record is now cached
            }
            Player former = formerBearer == null ? null : Bukkit.getPlayer(formerBearer);
            if (former != null) {
                validate(former);
                refreshHud(former);
            }
            announce(Component.text("✦ " + SuldContent.relicFor(key).displayName() + " сүмдээ буцлаа. Шинэ эзнээ хүлээж байна…",
                    NamedTextColor.AQUA), Sound.BLOCK_BEACON_POWER_SELECT);
        });
    }

    /** Bearers who stay offline too long lose the relic back to its shrine. */
    private void expireAbsentBearers() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(settings.offlineReturnHours()));
        for (RelicRecord r : List.copyOf(records.values())) {
            if (r.isAvailable() || r.owner() == null || Bukkit.getPlayer(r.owner()) != null) {
                continue;
            }
            UUID owner = r.owner();
            services.profileRepository().find(owner).whenComplete((profile, err) -> Bukkit.getScheduler().runTask(plugin, () -> {
                if (err != null || profile.isEmpty()) {
                    return;
                }
                if (profile.get().lastSeenAt().isBefore(cutoff) && Bukkit.getPlayer(owner) == null) {
                    RelicRecord cur = records.get(r.key());
                    if (cur != null && cur.isOwnedBy(owner)) {
                        whenDone(repository.apply(RelicTransition.release(cur, RelicEvent.EXPIRED, "server",
                                "bearer offline > " + settings.offlineReturnHours() + "h")), (cas, e2) -> {
                            if (e2 == null) {
                                records.put(cur.key(), cas.current());
                                if (cas.success()) {
                                    announce(Component.text("✦ " + SuldContent.relicFor(cur.key()).displayName()
                                            + "-ийн эзэн удаан алга болсон тул сүлд сүмдээ буцлаа.", NamedTextColor.AQUA),
                                            Sound.BLOCK_BEACON_POWER_SELECT);
                                }
                            }
                        });
                    }
                }
            }));
        }
    }

    // ------------------------------------------------------------- admin

    public void adminSetShrine(Player admin, String key) {
        RelicRecord rec = record(key).orElse(null);
        if (rec == null) {
            admin.sendMessage(Messages.error("Ийм сүлд алга."));
            return;
        }
        Location center = admin.getLocation().getBlock().getLocation();
        buildShrine(center);
        persistShrine(rec.key(), center, admin.getUniqueId().toString());
        services.audit().record(AuditEvent.of(admin.getUniqueId().toString(), "relic.admin_setshrine", rec.key(),
                center.getWorld().getName() + " " + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ()));
        admin.sendMessage(Messages.success("Сүм барьж, байршлыг хадгаллаа."));
    }

    public void adminReturn(org.bukkit.command.CommandSender admin, String key) {
        RelicRecord rec = record(key).orElse(null);
        if (rec == null || rec.isAvailable()) {
            admin.sendMessage(Messages.error("Сүлд олдсонгүй эсвэл аль хэдийн сүмдээ байна."));
            return;
        }
        UUID former = rec.owner();
        adminApply(admin, RelicTransition.release(rec, RelicEvent.ADMIN_RETURNED, actorOf(admin), "admin return"), () -> {
            Player p = former == null ? null : Bukkit.getPlayer(former);
            if (p != null) {
                validate(p);
                refreshHud(p);
            }
        });
    }

    public void adminGive(org.bukkit.command.CommandSender admin, String key, Player target) {
        RelicRecord rec = record(key).orElse(null);
        if (rec == null) {
            admin.sendMessage(Messages.error("Ийм сүлд алга."));
            return;
        }
        Optional<RelicRecord> other = borneBy(target.getUniqueId());
        if (other.isPresent() && !other.get().key().equals(rec.key())) {
            admin.sendMessage(Messages.error(target.getName() + " аль хэдийн өөр сүлд барьж байна."));
            return;
        }
        UUID former = rec.owner();
        adminApply(admin, RelicTransition.claim(rec, target.getUniqueId(), target.getName(), RelicEvent.ADMIN_GRANTED,
                actorOf(admin), "admin grant"), () -> {
            Player prev = former == null ? null : Bukkit.getPlayer(former);
            if (prev != null && !prev.equals(target)) {
                validate(prev);
                refreshHud(prev);
            }
            validate(target);
            refreshHud(target);
        });
    }

    /** Re-issue: bump the generation so every existing physical copy becomes invalid. */
    public void adminRecover(org.bukkit.command.CommandSender admin, String key) {
        RelicRecord rec = record(key).orElse(null);
        if (rec == null) {
            admin.sendMessage(Messages.error("Ийм сүлд алга."));
            return;
        }
        adminApply(admin, RelicTransition.regenerate(rec, RelicEvent.RECOVERED, actorOf(admin), "admin recover"), () -> {
            Player bearer = rec.owner() == null ? null : Bukkit.getPlayer(rec.owner());
            if (bearer != null) {
                validate(bearer);
            }
        });
    }

    private void adminApply(org.bukkit.command.CommandSender admin, RelicTransition t, Runnable after) {
        whenDone(repository.apply(t), (cas, err) -> {
            if (err != null) {
                admin.sendMessage(Messages.error("Амжилтгүй: " + rootName(err)));
                refresh(t.key());
                return;
            }
            records.put(t.key(), cas.current());
            if (!cas.success()) {
                admin.sendMessage(Messages.error("Төлөв өөрчлөгдсөн байна — дахин оролдоно уу."));
                return;
            }
            services.audit().record(AuditEvent.of(t.actor(), "relic." + t.event().name().toLowerCase(java.util.Locale.ROOT),
                    t.key(), t.detail() + (t.newOwner() == null ? "" : " -> " + t.newOwner())));
            admin.sendMessage(Messages.success(SuldContent.relicFor(t.key()).displayName() + ": " + t.event()
                    + " (үе " + cas.current().version() + ")"));
            after.run();
        });
    }

    public void history(org.bukkit.command.CommandSender viewer, String key) {
        RelicRecord rec = record(key).orElse(null);
        if (rec == null) {
            viewer.sendMessage(Messages.error("Ийм сүлд алга."));
            return;
        }
        whenDone(repository.history(rec.key(), 10), (rows, err) -> {
            if (err != null) {
                viewer.sendMessage(Messages.error("Түүх уншиж чадсангүй."));
                return;
            }
            viewer.sendMessage(Messages.accent(SuldContent.relicFor(rec.key()).displayName() + " — түүх"));
            for (RelicHistoryEntry h : rows) {
                String who = h.owner() == null ? "" : " → " + nameOf(h.owner());
                viewer.sendMessage(Messages.info(h.at().toString().substring(0, 16).replace('T', ' ') + " " + h.event() + who
                        + (h.detail().isEmpty() ? "" : " (" + h.detail() + ")")));
            }
        });
    }

    // ------------------------------------------------------------- helpers

    public Location shrineLocation(RelicRecord r) {
        return r.shrine() == null ? null : toLocation(r.shrine());
    }

    private static Location toLocation(ShrineLocation s) {
        World w = Bukkit.getWorld(s.world());
        return w == null ? null : new Location(w, s.x(), s.y(), s.z());
    }

    private void refresh(String key) {
        whenDone(repository.loadAll(), (all, err) -> {
            if (err == null) {
                all.stream().filter(r -> r.key().equals(key)).findFirst().ifPresent(r -> records.put(key, r));
            }
        });
    }

    private void refreshHud(Player player) {
        if (player.isOnline()) {
            services.profiles().cached(player.getUniqueId()).ifPresent(p -> services.hud().update(player, p));
        }
    }

    private void announce(Component message, Sound sound) {
        Bukkit.broadcast(message);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), sound, 0.7f, 1f);
        }
        plugin.getLogger().info("[relic] " + net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(message));
    }

    /** Complete on the main thread, always. */
    private <T> void whenDone(CompletableFuture<T> future, java.util.function.BiConsumer<T, Throwable> callback) {
        future.whenComplete((value, err) -> Bukkit.getScheduler().runTask(plugin, () -> callback.accept(value, err)));
    }

    private static String rootName(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName();
    }

    private static String actorOf(org.bukkit.command.CommandSender sender) {
        return sender instanceof Player p ? p.getUniqueId().toString() : "console";
    }

    private static String nameOf(UUID id) {
        String n = Bukkit.getOfflinePlayer(id).getName();
        return n == null ? id.toString().substring(0, 8) : n;
    }
}
