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

    public record Attempt(ChessPosition position, ChessPosition.Outcome outcome, Move defense, boolean continues,
                          TacticalAnalysis.Result replyAnalysis) {
        public Attempt(ChessPosition position, ChessPosition.Outcome outcome, Move defense, boolean continues) {
            this(position, outcome, defense, continues, null);
        }
    }

    public record Quest(String id, String fen, String theme, ChessPosition position, List<Move> line) {
        public Quest { line = List.copyOf(line); }

        public Quest(String id, String fen, Move solution, String theme, ChessPosition position) {
            this(id, fen, theme, position, List.of(solution));
        }

        public Move solution() { return playerMove(0); }

        public int mateLength() { return (line.size() + 1) / 2; }

        public Move playerMove(int stage) { return line.get(stage * 2); }

        public Move defenseAfter(int stage) { return line.get(stage * 2 + 1); }

        public ChessPosition positionAt(int stage) {
            var board = position;
            for (int index = 0; index < stage * 2; index++) board = board.play(line.get(index));
            return board;
        }

        public boolean solves(Move attempt) { return position.play(attempt).isCheckmate(); }

        public Attempt assess(Move move) { return assess(move, 0); }

        public Attempt assess(Move move, int stage) {
            if (stage < mateLength() - 1 && playerMove(stage).equals(move)) {
                return new Attempt(positionAt(stage + 1), ChessPosition.Outcome.PLAYING, defenseAfter(stage), true);
            }
            var after = positionAt(stage).play(move);
            if (!after.isCheck()) return new Attempt(after, after.outcome(), null, false);
            var replies = after.legalMoves();
            if (replies.isEmpty()) return new Attempt(after, ChessPosition.Outcome.CHECKMATE, null, false);
            var analysis = TacticalAnalysis.checkingReplies(after, replies);
            return new Attempt(after, ChessPosition.Outcome.CHECK, analysis.best().get(0).move(), false, analysis);
        }

        public Move matingMove() { return playerMove(mateLength() - 1); }

        public ChessPosition solvedPosition() { return positionAt(mateLength() - 1).play(matingMove()); }

        public ChessPosition.MateExplanation explanation() { return solvedPosition().explainMate(); }

        public Guidance guidance() {
            return new Guidance(position.pieceAt(solution().from()), solution(), matingPattern());
        }

        public MatePattern matingPattern() {
            Move move = matingMove();
            var before = positionAt(mateLength() - 1);
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
        return loadMultiMove("mate-in-two.yaml", "MATE_IN_TWO", 2);
    }

    public static List<Quest> loadMateInThree() {
        return loadMultiMove("mate-in-three.yaml", "MATE_IN_THREE", 3);
    }

    private static List<Quest> loadMultiMove(String file, String objective, int length) {
        var root = ChessYaml.resource(file);
        if (ChessYaml.number(root, "schema") != 1 || !objective.equals(ChessYaml.string(root, "objective"))) {
            throw new IllegalArgumentException("Invalid catalog schema or objective in " + file);
        }
        var result = new ArrayList<Quest>();
        var ids = new HashSet<String>();
        var positions = new HashSet<String>();
        int previousRating = 0;
        for (Object item : ChessYaml.list(root, "quests")) {
            if (!(item instanceof Map<?, ?> row) || row.size() != 4) throw new IllegalArgumentException("Invalid row in " + file);
            String id = ChessYaml.string(row, "id");
            String fen = ChessYaml.string(row, "fen");
            int rating = ChessYaml.number(row, "rating");
            var moves = ChessYaml.list(row, "line");
            if (!id.matches("mate" + length + "-[A-Za-z0-9]{5}") || !ids.add(id) || !positions.add(fen.replaceFirst(" [0-9]+ [0-9]+$", ""))
                    || rating < previousRating || moves.size() != length * 2 - 1 || moves.stream().anyMatch(move -> !(move instanceof String))) {
                throw new IllegalArgumentException("Invalid or duplicate quest " + id);
            }
            var line = new ArrayList<Move>(moves.size());
            for (Object move : moves) line.add(Move.fromUci((String) move));
            var quest = new Quest(id, fen, "TACTIC", ChessPosition.fromFen(fen), line);
            if (!quest.solvedPosition().isCheckmate()) throw new IllegalArgumentException("Invalid reference mate " + id);
            result.add(quest);
            previousRating = rating;
        }
        if (result.size() != 100) throw new IllegalArgumentException("Expected exactly 100 quests in " + file);
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
