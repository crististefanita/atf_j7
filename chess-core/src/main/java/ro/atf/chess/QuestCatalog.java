package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Curated problems. The catalog oracle is used for acceptance; the position decides success. */
public final class QuestCatalog {
    public enum MatePattern { DIRECT, DISCOVERED, CASTLING, EN_PASSANT, PROMOTION }

    public record Guidance(ChessPosition.Piece piece, Move move, MatePattern pattern) { }

    public record Attempt(ChessPosition position, ChessPosition.Outcome outcome, Move defense, boolean continues) { }

    public record Line(Move defense, Move mate) { }

    public record Quest(String id, String fen, Move solution, String theme, ChessPosition position, Line line) {
        public Quest(String id, String fen, Move solution, String theme, ChessPosition position) {
            this(id, fen, solution, theme, position, null);
        }

        public boolean mateInTwo() { return line != null; }

        public boolean solves(Move attempt) { return position.play(attempt).isCheckmate(); }

        public Attempt assess(Move move) { return assess(move, false); }

        public Attempt assess(Move move, boolean awaitingMate) {
            if (mateInTwo() && !awaitingMate && solution.equals(move)) {
                return new Attempt(continuationPosition(), ChessPosition.Outcome.PLAYING, line.defense(), true);
            }
            var before = awaitingMate ? continuationPosition() : position;
            var after = before.play(move);
            if (!after.isCheck()) return new Attempt(after, after.outcome(), null, false);
            var replies = after.legalMoves();
            return replies.isEmpty() ? new Attempt(after, ChessPosition.Outcome.CHECKMATE, null, false)
                    : new Attempt(after, ChessPosition.Outcome.CHECK, replies.get(0), false);
        }

        public ChessPosition continuationPosition() { return mateInTwo() ? position.play(solution).play(line.defense()) : position; }

        public Move matingMove() { return mateInTwo() ? line.mate() : solution; }

        public ChessPosition solvedPosition() { return continuationPosition().play(matingMove()); }

        public ChessPosition.MateExplanation explanation() { return solvedPosition().explainMate(); }

        public Guidance guidance() {
            return new Guidance(position.pieceAt(solution.from()), solution, matingPattern());
        }

        public MatePattern matingPattern() {
            Move move = matingMove();
            var before = continuationPosition();
            Kind kind = before.pieceAt(move.from()).kind();
            if (move.promotion() != null) return MatePattern.PROMOTION;
            if (kind == Kind.KING && Math.abs(move.from() - move.to()) == 2) return MatePattern.CASTLING;
            if (kind == Kind.PAWN && move.from() % 8 != move.to() % 8 && before.pieceAt(move.to()) == null) return MatePattern.EN_PASSANT;
            return before.play(move).isCheckedFrom(move.to()) ? MatePattern.DIRECT : MatePattern.DISCOVERED;
        }
    }

    private QuestCatalog() { }

    public static List<Quest> load() {
        var remaining = new HashMap<String, Quest>();
        for (Quest quest : loadPositions()) remaining.put(quest.id(), quest);
        var ordered = new ArrayList<Quest>();
        for (var entry : loadOrder()) {
            Quest quest = remaining.remove(entry.id());
            if (quest == null) throw new IllegalArgumentException("Unknown or repeated quest in order: " + entry.id());
            ordered.add(quest);
        }
        if (!remaining.isEmpty()) throw new IllegalArgumentException("Incomplete quest order");
        return Collections.unmodifiableList(ordered);
    }

    public static List<Quest> loadMateInTwo() {
        var root = ChessYaml.resource("mate-in-two.yaml");
        if (ChessYaml.number(root, "schema") != 1 || !"MATE_IN_TWO".equals(ChessYaml.string(root, "objective"))) {
            throw new IllegalArgumentException("Invalid mate-in-two catalog schema or objective");
        }
        var result = new ArrayList<Quest>();
        var ids = new HashSet<String>();
        var positions = new HashSet<String>();
        int previousRating = 0;
        for (Object item : ChessYaml.list(root, "quests")) {
            if (!(item instanceof Map<?, ?> row) || row.size() != 4) throw new IllegalArgumentException("Invalid mate-in-two row");
            String id = ChessYaml.string(row, "id");
            String fen = ChessYaml.string(row, "fen");
            int rating = ChessYaml.number(row, "rating");
            var moves = ChessYaml.list(row, "line");
            if (!id.matches("mate2-[A-Za-z0-9]{5}") || !ids.add(id) || !positions.add(fen.replaceFirst(" [0-9]+ [0-9]+$", ""))
                    || rating < previousRating || moves.size() != 3 || moves.stream().anyMatch(move -> !(move instanceof String))) {
                throw new IllegalArgumentException("Invalid or duplicate mate-in-two quest " + id);
            }
            var line = new Line(Move.fromUci((String) moves.get(1)), Move.fromUci((String) moves.get(2)));
            var quest = new Quest(id, fen, Move.fromUci((String) moves.get(0)), "TACTIC", ChessPosition.fromFen(fen), line);
            if (!quest.solvedPosition().isCheckmate()) throw new IllegalArgumentException("Invalid reference mate " + id);
            result.add(quest);
            previousRating = rating;
        }
        if (result.size() != 100) throw new IllegalArgumentException("Expected exactly 100 mate-in-two quests");
        return Collections.unmodifiableList(result);
    }

    static List<QuestDifficulty.Rank> loadOrder() {
        var root = ChessYaml.resource("quest-order.yaml");
        if (ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Invalid quest order schema");
        var ranks = new ArrayList<QuestDifficulty.Rank>();
        for (Object item : ChessYaml.list(root, "order")) {
            if (!(item instanceof List<?> row) || row.size() != 2 || !(row.get(0) instanceof String id)
                    || !(row.get(1) instanceof Integer score) || score < 0) throw new IllegalArgumentException("Invalid quest rank");
            ranks.add(new QuestDifficulty.Rank(id, score));
        }
        return Collections.unmodifiableList(ranks);
    }

    static List<Quest> loadPositions() {
        var root = ChessYaml.resource("quests.yaml");
        if (ChessYaml.number(root, "schema") != 1 || !"MATE_IN_ONE".equals(ChessYaml.string(root, "objective"))) {
            throw new IllegalArgumentException("Invalid quest catalog schema or objective");
        }
        var result = new ArrayList<Quest>();
        var positions = new HashSet<String>();
        for (Object item : ChessYaml.list(root, "quests")) {
            if (!(item instanceof Map<?, ?> row) || row.size() != 4) throw new IllegalArgumentException("Invalid quest row");
            String id = ChessYaml.string(row, "id");
            String expected = String.format(java.util.Locale.ROOT, "mate-%03d", result.size() + 1);
            String fen = ChessYaml.string(row, "fen");
            String theme = ChessYaml.string(row, "theme");
            String boardKey = fen.replaceFirst(" [0-9]+ [0-9]+$", "");
            if (!id.equals(expected) || !positions.add(boardKey) || !theme.matches("TACTIC|EN_PASSANT|CASTLING|PROMOTION")) {
                throw new IllegalArgumentException("Invalid or duplicate quest " + id);
            }
            result.add(new Quest(id, fen, Move.fromUci(ChessYaml.string(row, "solution")), theme, ChessPosition.fromFen(fen)));
        }
        if (result.size() != 102) throw new IllegalArgumentException("Expected exactly 102 mate-in-one quests");
        return Collections.unmodifiableList(result);
    }
}
