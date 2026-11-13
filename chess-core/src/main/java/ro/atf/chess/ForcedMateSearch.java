package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Proves short mates from the board alone, with an explicit cap on examined nodes. */
final class ForcedMateSearch {
    enum Status { PROVED, NO_MATE, BUDGET_EXHAUSTED }

    record Proof(Status status, List<Move> line, int nodes) { }

    private record Key(ChessPosition.SearchKey position, int plies) { }
    private record Candidate(Move move, ChessPosition position) { }

    private static final class Exhausted extends RuntimeException { }

    private final Side attacker;
    private final int limit;
    // A null entry records a disproved mate at this horizon.
    private final Map<Key, List<Move>> cache = new HashMap<>();
    private int nodes;

    private ForcedMateSearch(Side attacker, int limit) {
        this.attacker = attacker;
        this.limit = limit;
    }

    static Proof prove(ChessPosition position, int maxPlies, int limit) {
        if (maxPlies < 1 || maxPlies > MatchAiPolicy.MAX_PLIES || maxPlies % 2 == 0 || limit < 1) {
            throw new IllegalArgumentException("Invalid forced mate search limits");
        }
        var search = new ForcedMateSearch(position.turn(), limit);
        try {
            for (int plies = 1; plies <= maxPlies; plies += 2) {
                var line = search.visit(position, plies);
                if (line != null && !line.isEmpty()) return new Proof(Status.PROVED, line, search.nodes);
            }
            return new Proof(Status.NO_MATE, List.of(), search.nodes);
        } catch (Exhausted exhausted) {
            return new Proof(Status.BUDGET_EXHAUSTED, List.of(), search.nodes);
        }
    }

    private List<Move> visit(ChessPosition position, int plies) {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException();
        if (nodes == limit) throw new Exhausted();
        nodes++;
        if (plies == 0) return position.turn() != attacker && position.isCheckmate() ? List.of() : null;

        var key = new Key(position.searchKey(), plies);
        if (cache.containsKey(key)) return cache.get(key);
        var moves = position.legalMoves();
        boolean attacking = position.turn() == attacker;
        List<Move> line;
        if (moves.isEmpty()) line = !attacking && position.isCheck() ? List.of() : null;
        else if (attacking) line = attack(position, moves, plies);
        else line = defend(position, moves, plies);
        cache.put(key, line);
        return line;
    }

    private List<Move> attack(ChessPosition position, List<Move> moves, int plies) {
        if (plies == 1) {
            for (Move move : moves) {
                var line = visit(position.playGenerated(move), plies - 1);
                if (line != null) return prepend(move, line);
            }
            return null;
        }
        var candidates = new ArrayList<Candidate>(moves.size());
        for (Move move : moves) candidates.add(new Candidate(move, position.playGenerated(move)));
        candidates.sort(Comparator.comparing((Candidate next) -> next.position().isCheck()).reversed());
        for (Candidate candidate : candidates) {
            var line = visit(candidate.position(), plies - 1);
            if (line != null) return prepend(candidate.move(), line);
        }
        return null;
    }

    private List<Move> defend(ChessPosition position, List<Move> moves, int plies) {
        // One refutation is sufficient. A proved line shows the longest defense.
        List<Move> longest = List.of();
        for (Move move : moves) {
            var line = visit(position.playGenerated(move), plies - 1);
            if (line == null) return null;
            if (line.size() + 1 > longest.size()) longest = prepend(move, line);
        }
        return longest;
    }

    private static List<Move> prepend(Move move, List<Move> tail) {
        var line = new ArrayList<Move>(tail.size() + 1);
        line.add(move);
        line.addAll(tail);
        return List.copyOf(line);
    }
}
