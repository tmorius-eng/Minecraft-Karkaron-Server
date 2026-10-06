package mn.suld.api.persistence;

import mn.suld.api.clan.Clan;
import mn.suld.api.clan.ClanSnapshot;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous clan storage. Saves take an immutable {@link ClanSnapshot} captured on the
 * main thread. Implementations must apply writes in submission order (a player moving
 * between clans is saved as "leave A" then "join B").
 */
public interface ClanRepository {

    CompletableFuture<List<Clan>> loadAll();

    CompletableFuture<Void> save(ClanSnapshot clan);

    CompletableFuture<Void> delete(UUID clanId);
}
