package ro.atf.chess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchSetup.Level;

final class AiLeague {
    record Opening(String id, String moves, ChessMatch start) { }
    record Policy(int maxPlies, int samples, int seed, List<Opening> openings) {
        Policy { openings = List.copyOf(openings); }
    }
    record Seat(int turns, int captures, int checks, int castles, int depthSum, long extraNodes) { }
    record Game(Opening opening, Level white, Level black, ChessMatch match, Seat whiteStats, Seat blackStats) {
        PairedElo.Pair pairWith(Game reverse) {
            if (!opening.id().equals(reverse.opening.id()) || white != reverse.black || black != reverse.white) {
                throw new IllegalArgumentException("The second game must reverse the opening's profiles");
            }
            int wins = outcome(Result.WHITE_WINS, reverse, Result.BLACK_WINS);
            int losses = outcome(Result.BLACK_WINS, reverse, Result.WHITE_WINS);
            int unfinished = outcome(Result.IN_PROGRESS, reverse, Result.IN_PROGRESS);
            return new PairedElo.Pair(wins, 2 - wins - losses - unfinished, losses, unfinished);
        }

        private int outcome(Result first, Game reverse, Result second) {
            return (match.result() == first ? 1 : 0) + (reverse.match.result() == second ? 1 : 0);
        }
    }

    private AiLeague() { }

    static Policy load(Map<?, ?> root) {
        if (!root.keySet().equals(Set.of("schema", "max-plies", "bootstrap-samples", "bootstrap-seed", "openings"))
                || ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Invalid AI league schema");
        int cap = ChessYaml.number(root, "max-plies");
        int samples = ChessYaml.number(root, "bootstrap-samples");
        if (cap < 100 || cap > 300 || samples < 1_000 || samples > 50_000) throw new IllegalArgumentException("Invalid AI league limits");
        var openings = new ArrayList<Opening>();
        var ids = new HashSet<String>();
        for (Object item : ChessYaml.list(root, "openings")) {
            var row = (Map<?, ?>) item;
            String id = ChessYaml.string(row, "id");
            String moves = ChessYaml.string(row, "moves");
            var match = ChessMatch.restore("1|" + moves);
            if (!row.keySet().equals(Set.of("id", "moves")) || !id.matches("[a-z]+(?:-[a-z]+)*") || !ids.add(id)
                    || match.result() != Result.IN_PROGRESS || match.ply() < 4 || match.ply() > 16
                    || openings.stream().anyMatch(opening -> opening.start().position().sameForRepetition(match.position()))) {
                throw new IllegalArgumentException("Invalid or duplicate opening " + id);
            }
            openings.add(new Opening(id, moves, match));
        }
        return new Policy(cap, samples, ChessYaml.number(root, "bootstrap-seed"), openings);
    }

    static List<Game> play(Policy policy, int workers) throws Exception {
        var tasks = new ArrayList<Callable<Game>>();
        var levels = Level.values();
        for (int first = 1; first < levels.length; first++) for (int second = 0; second < first; second++) {
            Level stronger = levels[first];
            Level weaker = levels[second];
            for (Opening opening : policy.openings()) {
                tasks.add(() -> game(opening, stronger, weaker, policy.maxPlies()));
                tasks.add(() -> game(opening, weaker, stronger, policy.maxPlies()));
            }
        }
        try (var pool = Executors.newFixedThreadPool(workers)) {
            var result = new ArrayList<Game>();
            for (var game : pool.invokeAll(tasks)) result.add(game.get());
            return List.copyOf(result);
        }
    }

    static Game game(Opening opening, Level white, Level black, int cap) {
        var match = opening.start();
        var whiteStats = new SeatStats();
        var blackStats = new SeatStats();
        while (match.result() == Result.IN_PROGRESS && match.ply() < cap) {
            var before = match.position();
            boolean whiteTurn = before.turn() == Side.WHITE;
            var decision = match.botDecision(whiteTurn ? white : black);
            match = match.play(decision.move());
            (whiteTurn ? whiteStats : blackStats).add(before, match.position(), decision);
        }
        return new Game(opening, white, black, match, whiteStats.snapshot(), blackStats.snapshot());
    }

    private static final class SeatStats {
        private int turns;
        private int captures;
        private int checks;
        private int castles;
        private int depth;
        private long nodes;

        void add(ChessPosition before, ChessPosition after, ChessMatch.BotDecision decision) {
            Move move = decision.move();
            Kind kind = before.pieceAt(move.from()).kind();
            turns++;
            if (before.pieceAt(move.to()) != null || kind == Kind.PAWN && move.from() % 8 != move.to() % 8) captures++;
            if (after.isCheck()) checks++;
            if (kind == Kind.KING && Math.abs(move.from() - move.to()) == 2) castles++;
            depth += decision.depth();
            nodes += decision.extraNodes();
        }

        Seat snapshot() { return new Seat(turns, captures, checks, castles, depth, nodes); }
    }
}
