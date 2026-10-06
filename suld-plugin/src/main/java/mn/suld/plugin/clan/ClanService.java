package mn.suld.plugin.clan;

import mn.suld.api.clan.Clan;
import mn.suld.api.clan.ClanMember;
import mn.suld.api.clan.ClanProgression;
import mn.suld.api.clan.ClanRegistry;
import mn.suld.api.clan.ClanResult;
import mn.suld.api.config.SocialSettings;
import mn.suld.api.persistence.ClanRepository;
import mn.suld.api.profile.PlayerProfile;
import mn.suld.plugin.SuldServices;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Bukkit adapter for clans: messaging, the founding cost, persistence scheduling and the
 * level perks. Every rule lives in the pure {@link ClanRegistry}.
 *
 * <p>Persistence: structural changes (create/join/leave/kick/rank) save immediately;
 * contribution EXP marks the clan dirty and is flushed every minute and on shutdown.
 * All writes go through one ordered (single-threaded) repository executor.
 */
public final class ClanService {

    private static final Duration INVITE_TTL = Duration.ofMinutes(5);

    private final Plugin plugin;
    private final SuldServices services;
    private final ClanRepository repository;
    private final SocialSettings settings;
    private final ClanRegistry registry = new ClanRegistry(Clock.systemUTC(), INVITE_TTL);
    private final Set<UUID> dirty = new HashSet<>();
    /** Thread-safe tag cache for the async chat renderer. */
    private final Map<UUID, String> tagCache = new ConcurrentHashMap<>();

    public ClanService(Plugin plugin, SuldServices services, ClanRepository repository, SocialSettings settings) {
        this.plugin = plugin;
        this.services = services;
        this.repository = repository;
        this.settings = settings;
    }

    /** Load every clan at startup (blocking, bounded). */
    public int load() {
        try {
            List<Clan> clans = repository.loadAll().get(30, TimeUnit.SECONDS);
            for (Clan clan : clans) {
                registry.register(clan);
                refreshTags(clan);
            }
            return clans.size();
        } catch (Exception ex) {
            throw new IllegalStateException("Could not load clans: " + ex.getMessage(), ex);
        }
    }

    public ClanRegistry registry() {
        return registry;
    }

    public Optional<Clan> clanOf(UUID player) {
        return registry.clanOf(player);
    }

    /** Safe from any thread (chat runs async). */
    public Optional<String> tagOf(UUID player) {
        return Optional.ofNullable(tagCache.get(player));
    }

    /** Personal EXP after the clan-level bonus (+2%/level above 1). */
    public long boostedExp(UUID player, long base) {
        double bonus = registry.clanOf(player).map(c -> ClanProgression.expBonus(c.level())).orElse(0.0);
        return Math.round(base * (1.0 + bonus));
    }

    /** Credit clan EXP earned by a member; announces level-ups. */
    public void contribute(UUID player, long amount) {
        registry.contribute(player, amount).ifPresent(c -> {
            dirty.add(c.clan().id());
            if (c.levelsGained() > 0) {
                Clan clan = c.clan();
                broadcast(clan, Messages.accent("Овог [" + clan.tag() + "] " + clan.level() + "-р түвшинд хүрлээ! "
                        + "Багтаамж " + clan.capacity() + ", EXP бонус +"
                        + Math.round(ClanProgression.expBonus(clan.level()) * 100) + "%"));
                forEachOnline(clan, p -> p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f));
                saveNow(clan);
            }
        });
    }

    /** HUD line: tag + level. */
    public Optional<String> hudLine(UUID player) {
        return registry.clanOf(player).map(c -> "§7Овог: §6[" + c.tag() + "] §7Lv§e" + c.level());
    }

    /** Called on join: keep the member's stored name current (UUID is the identity). */
    public void onJoin(Player player) {
        registry.clanOf(player.getUniqueId()).ifPresent(clan -> {
            long before = clan.version();
            registry.updateName(player.getUniqueId(), player.getName());
            if (clan.version() != before) {
                saveNow(clan);
            }
        });
    }

    // ------------------------------------------------------------- commands

    public void create(Player player, String name, String tag) {
        PlayerProfile profile = services.profiles().cached(player.getUniqueId()).orElse(null);
        if (profile == null) {
            player.sendMessage(Messages.error("Профайл ачааллагдаагүй байна."));
            return;
        }
        long cost = settings.clanCreateCost();
        if (registry.clanOf(player.getUniqueId()).isEmpty() && profile.currency() < cost) {
            player.sendMessage(Messages.error("Овог байгуулахад " + cost + " зоос хэрэгтэй (танд " + profile.currency() + ")."));
            return;
        }
        ClanRegistry.Action a = registry.create(player.getUniqueId(), player.getName(), name, tag);
        if (a.result() != ClanResult.CREATED) {
            player.sendMessage(Messages.error(describe(a.result())));
            return;
        }
        profile.addCurrency(-cost);
        services.profiles().save(profile);
        refreshTags(a.clan());
        saveNow(a.clan());
        Bukkit.broadcast(Messages.accent(player.getName() + " шинэ овог байгууллаа: [" + a.clan().tag() + "] "
                + a.clan().name()));
        refreshHud(player);
    }

    public void invite(Player inviter, Player target) {
        ClanRegistry.Action a = registry.invite(inviter.getUniqueId(), target.getUniqueId());
        if (a.result() != ClanResult.INVITED) {
            inviter.sendMessage(Messages.error(describe(a.result())));
            return;
        }
        inviter.sendMessage(Messages.success(target.getName() + "-г [" + a.clan().tag() + "] овогт урилаа."));
        target.sendMessage(Messages.accent(inviter.getName() + " таныг [" + a.clan().tag() + "] " + a.clan().name()
                + " овогт урьж байна. /clan accept (5 минутад)"));
    }

    public void accept(Player player) {
        ClanRegistry.Action a = registry.accept(player.getUniqueId(), player.getName());
        if (a.result() != ClanResult.JOINED) {
            player.sendMessage(Messages.error(describe(a.result())));
            return;
        }
        refreshTags(a.clan());
        saveNow(a.clan());
        broadcast(a.clan(), Messages.success(player.getName() + " овогт нэгдлээ (" + a.clan().size() + "/"
                + a.clan().capacity() + ")."));
        refreshHud(player);
    }

    public void leave(Player player) {
        ClanRegistry.Action a = registry.leave(player.getUniqueId());
        switch (a.result()) {
            case LEFT -> {
                tagCache.remove(player.getUniqueId());
                saveNow(a.clan());
                player.sendMessage(Messages.info("Та [" + a.clan().tag() + "] овгоос гарлаа."));
                broadcast(a.clan(), Messages.info(player.getName() + " овгоос гарлаа."));
                refreshHud(player);
            }
            case DISBANDED -> onDisbanded(a.clan());
            default -> player.sendMessage(Messages.error(describe(a.result())));
        }
    }

    public void kick(Player actor, String targetName) {
        UUID target = memberByName(actor, targetName).orElse(null);
        if (target == null) {
            actor.sendMessage(Messages.error(targetName + " таны овогт байхгүй."));
            return;
        }
        ClanRegistry.Action a = registry.kick(actor.getUniqueId(), target);
        if (a.result() != ClanResult.KICKED) {
            actor.sendMessage(Messages.error(describe(a.result())));
            return;
        }
        tagCache.remove(target);
        saveNow(a.clan());
        broadcast(a.clan(), Messages.info(targetName + " овгоос хасагдлаа."));
        Player online = Bukkit.getPlayer(target);
        if (online != null) {
            online.sendMessage(Messages.error("Таныг [" + a.clan().tag() + "] овгоос хаслаа."));
            refreshHud(online);
        }
    }

    public void rank(Player actor, String targetName, String op) {
        UUID target = memberByName(actor, targetName).orElse(null);
        if (target == null) {
            actor.sendMessage(Messages.error(targetName + " таны овогт байхгүй."));
            return;
        }
        ClanRegistry.Action a = switch (op) {
            case "promote" -> registry.promote(actor.getUniqueId(), target);
            case "demote" -> registry.demote(actor.getUniqueId(), target);
            default -> registry.transfer(actor.getUniqueId(), target);
        };
        if (!a.ok()) {
            actor.sendMessage(Messages.error(describe(a.result())));
            return;
        }
        saveNow(a.clan());
        String rank = a.clan().member(target).map(m -> m.rank().displayName()).orElse("?");
        broadcast(a.clan(), Messages.accent(targetName + " одоо " + rank + " боллоо."));
    }

    public void disband(Player actor) {
        ClanRegistry.Action a = registry.disband(actor.getUniqueId());
        if (a.result() != ClanResult.DISBANDED) {
            actor.sendMessage(Messages.error(describe(a.result())));
            return;
        }
        onDisbanded(a.clan());
    }

    public void info(Player viewer, String tagOrNull) {
        Optional<Clan> clan = tagOrNull == null ? registry.clanOf(viewer.getUniqueId()) : registry.byTag(tagOrNull);
        if (clan.isEmpty()) {
            viewer.sendMessage(Messages.info(tagOrNull == null
                    ? "Та овоггүй. /clan create <нэр> <таг>  (" + settings.clanCreateCost() + " зоос)"
                    : "Ийм овог алга."));
            return;
        }
        Clan c = clan.get();
        viewer.sendMessage(Messages.accent("[" + c.tag() + "] " + c.name() + " — түвшин " + c.level()
                + " (" + c.size() + "/" + c.capacity() + ")"));
        long toNext = ClanProgression.expToNext(c.exp());
        viewer.sendMessage(Messages.info("Овгийн EXP: " + c.exp() + (toNext > 0 ? " (дараагийн түвшин хүртэл " + toNext + ")" : " (дээд)")
                + " · гишүүдийн EXP бонус +" + Math.round(ClanProgression.expBonus(c.level()) * 100) + "%"));
        c.members().stream()
                .sorted(Comparator.comparingInt((ClanMember m) -> m.rank().authority()).reversed()
                        .thenComparing(Comparator.comparingLong(ClanMember::contribution).reversed()))
                .forEach(m -> viewer.sendMessage(Component.text("  " + m.rank().displayName() + " · " + m.name()
                        + " · " + m.contribution() + " EXP" + (Bukkit.getPlayer(m.playerId()) != null ? " ●" : ""),
                        NamedTextColor.GRAY)));
    }

    public void top(Player viewer) {
        viewer.sendMessage(Messages.accent("Шилдэг овгууд"));
        registry.all().stream().sorted(Comparator.comparingLong(Clan::exp).reversed()).limit(10).forEach(c ->
                viewer.sendMessage(Messages.info("[" + c.tag() + "] " + c.name() + " — Lv" + c.level() + " · "
                        + c.exp() + " EXP · " + c.size() + " гишүүн")));
    }

    public void chat(Player sender, String message) {
        Clan clan = registry.clanOf(sender.getUniqueId()).orElse(null);
        if (clan == null) {
            sender.sendMessage(Messages.error("Та овоггүй."));
            return;
        }
        Component line = Component.text("[" + clan.tag() + "] ", NamedTextColor.GOLD)
                .append(Component.text(sender.getName(), NamedTextColor.AQUA))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));
        forEachOnline(clan, p -> p.sendMessage(line));
        plugin.getLogger().info("[clan-chat][" + clan.tag() + "] " + sender.getName() + ": " + message);
    }

    // ---------------------------------------------------------- persistence

    public void flushDirty() {
        for (UUID id : List.copyOf(dirty)) {
            registry.byId(id).ifPresent(this::saveNow);
        }
        dirty.clear();
    }

    /** Shutdown: flush everything and wait for the ordered writes to finish. */
    public void shutdown() {
        try {
            java.util.concurrent.CompletableFuture<?>[] saves = registry.all().stream()
                    .filter(c -> dirty.contains(c.id()))
                    .map(c -> repository.save(c.snapshot()))
                    .toArray(java.util.concurrent.CompletableFuture[]::new);
            dirty.clear();
            java.util.concurrent.CompletableFuture.allOf(saves).get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            plugin.getLogger().warning("Clan flush on shutdown incomplete: " + ex.getMessage());
        }
    }

    private void saveNow(Clan clan) {
        dirty.remove(clan.id());
        repository.save(clan.snapshot()).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to save clan [" + clan.tag() + "]: " + ex.getMessage());
            Bukkit.getScheduler().runTask(plugin, () -> dirty.add(clan.id())); // retry on next flush
            return null;
        });
    }

    private void onDisbanded(Clan clan) {
        dirty.remove(clan.id());
        clan.members().forEach(m -> tagCache.remove(m.playerId()));
        repository.delete(clan.id()).exceptionally(ex -> {
            plugin.getLogger().severe("Failed to delete clan [" + clan.tag() + "]: " + ex.getMessage());
            return null;
        });
        Bukkit.broadcast(Messages.info("[" + clan.tag() + "] " + clan.name() + " овог тарлаа."));
        forEachOnline(clan, this::refreshHud);
    }

    // --------------------------------------------------------------- helpers

    private void refreshTags(Clan clan) {
        clan.members().forEach(m -> tagCache.put(m.playerId(), clan.tag()));
    }

    private void refreshHud(Player player) {
        services.profiles().cached(player.getUniqueId()).ifPresent(p -> services.hud().update(player, p));
    }

    private Optional<UUID> memberByName(Player actor, String name) {
        return registry.clanOf(actor.getUniqueId()).flatMap(c -> c.members().stream()
                .filter(m -> m.name().equalsIgnoreCase(name)).map(ClanMember::playerId).findFirst());
    }

    private void broadcast(Clan clan, Component message) {
        forEachOnline(clan, p -> p.sendMessage(message));
    }

    private void forEachOnline(Clan clan, java.util.function.Consumer<Player> action) {
        for (ClanMember m : clan.members()) {
            Player p = Bukkit.getPlayer(m.playerId());
            if (p != null) {
                action.accept(p);
            }
        }
    }

    static String describe(ClanResult r) {
        return switch (r) {
            case ALREADY_IN_CLAN -> "Та аль хэдийн овогт байна.";
            case NOT_IN_CLAN -> "Та овоггүй байна.";
            case TARGET_IN_CLAN -> "Тэр тоглогч өөр овогт байна.";
            case NOT_A_MEMBER -> "Тэр тоглогч таны овогт байхгүй.";
            case SELF_TARGET -> "Өөрөө өөр дээрээ хийх боломжгүй.";
            case INVALID_NAME -> "Нэр 3–24 тэмдэгт, үсэг/тоо/зай байна.";
            case INVALID_TAG -> "Таг 2–5 үсэг/тоо байна (жишээ: ХЧ, KHAN).";
            case NAME_TAKEN -> "Энэ нэр аль хэдийн байна.";
            case TAG_TAKEN -> "Энэ таг аль хэдийн байна.";
            case NO_PERMISSION -> "Танд эрх алга.";
            case LEADER_MUST_TRANSFER -> "Ноён гарахаас өмнө /clan transfer <тоглогч> хийнэ (эсвэл /clan disband).";
            case CLAN_FULL -> "Овог дүүрсэн — овгийн түвшинг өсгө.";
            case ALREADY_MAX_RANK -> "Аль хэдийн Түшмэл. Ноён болгох бол /clan transfer.";
            case ALREADY_MIN_RANK -> "Аль хэдийн Цэрэг.";
            case NO_INVITE -> "Танд хүлээгдэж буй урилга алга.";
            case INVITE_EXPIRED -> "Урилгын хугацаа дууссан.";
            case CLAN_GONE -> "Тэр овог тарсан байна.";
            default -> "Амжилтгүй.";
        };
    }
}
