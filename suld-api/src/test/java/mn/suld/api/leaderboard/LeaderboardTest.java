package mn.suld.api.leaderboard;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LeaderboardTest {

    private static final UUID A = UUID.randomUUID(), B = UUID.randomUUID(), C = UUID.randomUUID();

    @Test
    void ranksByLevelThenProgressAndLiveRowsWin() {
        var stored = List.of(new Leaderboard.Entry(A, "Anu", 10, 500, 100), new Leaderboard.Entry(B, "Bat", 10, 900, 50),
                new Leaderboard.Entry(C, "Chimeg", 3, 0, 9000));
        var live = new Leaderboard.Entry(A, "Anu", 11, 10, 120); // Anu levelled up since the last save
        List<Leaderboard.Entry> top = Leaderboard.LEVEL.rank(concat(stored, live), 10);
        assertEquals(List.of(A, B, C), top.stream().map(Leaderboard.Entry::player).toList());
        assertEquals(11, top.get(0).level());
        assertEquals(List.of(C, A), Leaderboard.COINS.rank(concat(stored, live), 2).stream().map(Leaderboard.Entry::player).toList());
    }

    private static List<Leaderboard.Entry> concat(List<Leaderboard.Entry> a, Leaderboard.Entry b) {
        var out = new java.util.ArrayList<>(a);
        out.add(b);
        return out;
    }
}
