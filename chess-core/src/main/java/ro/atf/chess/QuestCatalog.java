package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Move;

/** Curated problems. The catalog oracle is used for acceptance; the position decides success. */
public final class QuestCatalog {
    public enum MatePattern { DIRECT, DISCOVERED, CASTLING, EN_PASSANT, PROMOTION }

    public record Guidance(ChessPosition.Piece piece, Move move, MatePattern pattern) { }

    public record Attempt(ChessPosition position, ChessPosition.Outcome outcome, Move defense) { }

    public record Quest(String id, String fen, Move solution, String theme, ChessPosition position) {
        public boolean solves(Move attempt) { return position.play(attempt).isCheckmate(); }

        public Attempt assess(Move move) {
            var after = position.play(move);
            if (!after.isCheck()) return new Attempt(after, after.outcome(), null);
            var replies = after.legalMoves();
            return replies.isEmpty() ? new Attempt(after, ChessPosition.Outcome.CHECKMATE, null)
                    : new Attempt(after, ChessPosition.Outcome.CHECK, replies.get(0));
        }

        public ChessPosition.MateExplanation explanation() { return position.play(solution).explainMate(); }

        public Guidance guidance() {
            ChessPosition mate = position.play(solution);
            MatePattern pattern = switch (theme) {
                case "CASTLING" -> MatePattern.CASTLING;
                case "EN_PASSANT" -> MatePattern.EN_PASSANT;
                case "PROMOTION" -> MatePattern.PROMOTION;
                default -> mate.isCheckedFrom(solution.to()) ? MatePattern.DIRECT : MatePattern.DISCOVERED;
            };
            return new Guidance(position.pieceAt(solution.from()), solution, pattern);
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
