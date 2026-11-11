package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;

/** Newest-first order for completed games, independent of active slots. */
public final class MatchArchive {
    public static final int LIMIT = 10;

    private MatchArchive() { }

    public record Retention(List<String> kept, List<String> evicted) {
        public Retention {
            kept = List.copyOf(kept);
            evicted = List.copyOf(evicted);
        }
    }

    public static Retention retain(List<String> newestFirst, String gameId) {
        if (newestFirst.contains(gameId)) return new Retention(newestFirst, List.of());
        int retained = Math.min(newestFirst.size(), LIMIT - 1);
        var next = new ArrayList<String>();
        next.add(gameId);
        next.addAll(newestFirst.subList(0, retained));
        return new Retention(next, newestFirst.subList(retained, newestFirst.size()));
    }
}
