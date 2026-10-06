package mn.suld.plugin.party;

import mn.suld.api.party.Party;
import mn.suld.api.party.PartyRegistry;
import mn.suld.api.party.PartyResult;
import mn.suld.plugin.ui.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Bukkit-facing party adapter: all rules live in the pure {@link PartyRegistry};
 * this class resolves players, sends messages, and notifies the dungeon layer
 * when a member leaves an active run.
 */
public final class PartyService {

    public static final int MAX_PARTY_SIZE = 4;
    private static final Duration INVITE_TTL = Duration.ofSeconds(60);

    private final PartyRegistry registry = new PartyRegistry(Clock.systemUTC(), MAX_PARTY_SIZE, INVITE_TTL);
    private BiConsumer<Party, UUID> memberRemovedHook = (party, member) -> { };

    public PartyRegistry registry() {
        return registry;
    }

    public Optional<Party> partyOf(UUID player) {
        return registry.partyOf(player);
    }

    /** Called (after removal) whenever a member leaves/is kicked/quits. */
    public void onMemberRemoved(BiConsumer<Party, UUID> hook) {
        this.memberRemovedHook = hook;
    }

    public void invite(Player inviter, Player target) {
        PartyRegistry.Action a = registry.invite(inviter.getUniqueId(), target.getUniqueId());
        switch (a.result()) {
            case INVITED -> {
                inviter.sendMessage(Messages.success(target.getName() + "-д урилга илгээлээ."));
                target.sendMessage(Messages.accent(inviter.getName()
                        + " таныг багтаа урьж байна. /party accept (60 секундэд)"));
            }
            case SELF_TARGET -> inviter.sendMessage(Messages.error("Өөрийгөө урих боломжгүй."));
            case TARGET_IN_PARTY -> inviter.sendMessage(Messages.error(target.getName() + " аль хэдийн багт байна."));
            case NOT_LEADER -> inviter.sendMessage(Messages.error("Зөвхөн багийн ахлагч урина."));
            case PARTY_FULL -> inviter.sendMessage(Messages.error("Баг дүүрсэн (" + MAX_PARTY_SIZE + ")."));
            case PARTY_BUSY -> inviter.sendMessage(Messages.error("Баг агуйд явж байна."));
            default -> inviter.sendMessage(Messages.error("Урилга илгээж чадсангүй."));
        }
    }

    public void accept(Player player) {
        PartyRegistry.Action a = registry.accept(player.getUniqueId());
        switch (a.result()) {
            case JOINED -> {
                broadcast(a.party(), Messages.success(player.getName() + " багт нэгдлээ. ("
                        + a.party().size() + "/" + a.party().capacity() + ")"));
            }
            case NO_INVITE -> player.sendMessage(Messages.error("Танд хүлээгдэж буй урилга алга."));
            case INVITE_EXPIRED -> player.sendMessage(Messages.error("Урилгын хугацаа дууссан."));
            case ALREADY_IN_PARTY -> player.sendMessage(Messages.error("Та аль хэдийн багт байна."));
            case PARTY_FULL -> player.sendMessage(Messages.error("Баг дүүрсэн."));
            case PARTY_BUSY -> player.sendMessage(Messages.error("Баг агуйд явж байна."));
            default -> player.sendMessage(Messages.error("Баг олдсонгүй."));
        }
    }

    /** Voluntary leave (also used silently on quit). */
    public void leave(Player player, boolean announce) {
        PartyRegistry.Action a = registry.leave(player.getUniqueId());
        if (a.result() != PartyResult.LEFT) {
            if (announce) {
                player.sendMessage(Messages.error("Та багт байхгүй байна."));
            }
            return;
        }
        memberRemovedHook.accept(a.party(), player.getUniqueId());
        if (announce) {
            player.sendMessage(Messages.info("Та багаас гарлаа."));
        }
        if (!a.party().isDisbanded()) {
            broadcast(a.party(), Messages.info(player.getName() + " багаас гарлаа."
                    + " Ахлагч: " + nameOf(a.party().leader())));
        }
    }

    public void kick(Player leader, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        UUID targetId = target != null ? target.getUniqueId() : null;
        if (targetId == null) {
            leader.sendMessage(Messages.error("Тоглогч онлайн биш байна."));
            return;
        }
        PartyRegistry.Action a = registry.kick(leader.getUniqueId(), targetId);
        switch (a.result()) {
            case KICKED -> {
                memberRemovedHook.accept(a.party(), targetId);
                target.sendMessage(Messages.error("Таныг багаас хаслаа."));
                broadcast(a.party(), Messages.info(target.getName() + " багаас хасагдлаа."));
            }
            case NOT_LEADER -> leader.sendMessage(Messages.error("Зөвхөн багийн ахлагч хасна."));
            case NOT_A_MEMBER -> leader.sendMessage(Messages.error(targetName + " таны багт байхгүй."));
            case SELF_TARGET -> leader.sendMessage(Messages.error("Өөрийгөө хасах боломжгүй. /party leave"));
            default -> leader.sendMessage(Messages.error("Та багт байхгүй байна."));
        }
    }

    public void promote(Player leader, String targetName) {
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            leader.sendMessage(Messages.error("Тоглогч онлайн биш байна."));
            return;
        }
        PartyRegistry.Action a = registry.promote(leader.getUniqueId(), target.getUniqueId());
        switch (a.result()) {
            case PROMOTED -> broadcast(a.party(), Messages.accent(target.getName() + " шинэ ахлагч боллоо."));
            case NOT_LEADER -> leader.sendMessage(Messages.error("Зөвхөн багийн ахлагч шилжүүлнэ."));
            case NOT_A_MEMBER -> leader.sendMessage(Messages.error(targetName + " таны багт байхгүй."));
            default -> leader.sendMessage(Messages.error("Та багт байхгүй байна."));
        }
    }

    public void disband(Player leader) {
        Party party = registry.partyOf(leader.getUniqueId()).orElse(null);
        if (party == null) {
            leader.sendMessage(Messages.error("Та багт байхгүй байна."));
            return;
        }
        if (!party.isLeader(leader.getUniqueId())) {
            leader.sendMessage(Messages.error("Зөвхөн багийн ахлагч тарааж чадна."));
            return;
        }
        broadcast(party, Messages.info("Баг тарлаа."));
        for (UUID member : java.util.List.copyOf(party.members())) {
            memberRemovedHook.accept(party, member);
        }
        registry.disband(party);
    }

    public void info(Player player) {
        Party party = registry.partyOf(player.getUniqueId()).orElse(null);
        if (party == null) {
            player.sendMessage(Messages.info("Та багт байхгүй. /party invite <тоглогч>"));
            return;
        }
        player.sendMessage(Messages.accent("Баг (" + party.size() + "/" + party.capacity() + ")"));
        for (UUID member : party.members()) {
            player.sendMessage(Component.text("  " + (party.isLeader(member) ? "★ " : "• ") + nameOf(member),
                    party.isLeader(member) ? Messages.BRAND : net.kyori.adventure.text.format.NamedTextColor.GRAY));
        }
    }

    public void broadcast(Party party, Component message) {
        for (UUID member : party.members()) {
            Player p = Bukkit.getPlayer(member);
            if (p != null) {
                p.sendMessage(message);
            }
        }
    }

    private static String nameOf(UUID id) {
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            return online.getName();
        }
        String offline = Bukkit.getOfflinePlayer(id).getName();
        return offline != null ? offline : id.toString().substring(0, 8);
    }
}
