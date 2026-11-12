package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Curated problems. The catalog oracle is used for acceptance; the position decides success. */
public final class QuestCatalog {
    private static final int MATE_IN_TWO_COUNT = 300;
    private static final int MATE_IN_THREE_COUNT = 200;
    public enum Objective { MATE, MATERIAL }
    public enum MatePattern { DIRECT, DISCOVERED, CASTLING, EN_PASSANT, PROMOTION }

    public record Guidance(ChessPosition.Piece piece, Move move, MatePattern pattern) { }

    public record Attempt(ChessPosition position, ChessPosition.Outcome outcome, Move defense, boolean continues,
                          boolean solved, TacticalAnalysis.Result replyAnalysis) { }

    public record Quest(String id, String fen, String theme, ChessPosition position, List<Move> line, Objective objective) {
        public Quest { line = List.copyOf(line); }

        public Quest(String id, String fen, String theme, ChessPosition position, List<Move> line) {
            this(id, fen, theme, position, line, Objective.MATE);
        }

        public Quest(String id, String fen, Move solution, String theme, ChessPosition position) {
            this(id, fen, theme, position, List.of(solution));
        }

        public Move solution() { return playerMove(0); }

        public int playerMoves() { return (line.size() + 1) / 2; }
        public int mateLength() { return playerMoves(); }

        public Move playerMove(int stage) { return line.get(stage * 2); }

        public Move defenseAfter(int stage) { return line.get(stage * 2 + 1); }

        public ChessPosition positionAt(int stage) {
            var board = position;
            for (int index = 0; index < stage * 2; index++) board = board.play(line.get(index));
            return board;
        }

        public boolean solves(Move attempt) {
            var after = position.play(attempt);
            return objective == Objective.MATERIAL ? solution().equals(attempt) : after.isCheckmate();
        }

        public Attempt assess(Move move) { return assess(move, 0); }

        public Attempt assess(Move move, int stage) {
            if (stage < playerMoves() - 1 && playerMove(stage).equals(move)) {
                return new Attempt(positionAt(stage + 1), ChessPosition.Outcome.PLAYING, defenseAfter(stage), true, false, null);
            }
            var after = positionAt(stage).play(move);
            if (objective == Objective.MATERIAL) {
                return new Attempt(after, after.outcome(), null, false, solution().equals(move), null);
            }
            if (!after.isCheck()) return new Attempt(after, after.outcome(), null, false, false, null);
            var replies = after.legalMoves();
            if (replies.isEmpty()) return new Attempt(after, ChessPosition.Outcome.CHECKMATE, null, false, true, null);
            var analysis = TacticalAnalysis.checkingReplies(after, replies);
            return new Attempt(after, ChessPosition.Outcome.CHECK, analysis.best().get(0).move(), false, false, analysis);
        }

        public Move finishingMove() { return playerMove(playerMoves() - 1); }
        public Move matingMove() { return finishingMove(); }

        /** Replays an accepted finish, with the reference line as the legacy fallback. */
        public Quest restoreFinish(String checkpoint) {
            if (objective != Objective.MATE || checkpoint == null) return this;
            int separator = checkpoint.indexOf(':');
            if (separator < 1) return this;
            try {
                int stage = Integer.parseInt(checkpoint.substring(0, separator));
                if (stage < 0 || stage >= playerMoves()) return this;
                Move move = Move.fromUci(checkpoint.substring(separator + 1));
                if (stage == playerMoves() - 1 && move.equals(finishingMove())) return this;
                if (!positionAt(stage).play(move).isCheckmate()) return this;
                var completedLine = new ArrayList<>(line.subList(0, stage * 2));
                completedLine.add(move);
                return new Quest(id, fen, theme, position, completedLine, objective);
            } catch (IllegalArgumentException invalid) {
                return this;
            }
        }

        public ChessPosition solvedPosition() { return positionAt(playerMoves() - 1).play(finishingMove()); }

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
        return loadMultiMove("mate-in-two.yaml", "MATE_IN_TWO", 2, MATE_IN_TWO_COUNT);
    }

    public static List<Quest> loadMateInThree() {
        return loadMultiMove("mate-in-three.yaml", "MATE_IN_THREE", 3, MATE_IN_THREE_COUNT);
    }

    public static List<Quest> loadTactics() {
        return readMaterial(ChessYaml.resource("tactics.yaml"), "tactic");
    }

    public static List<Quest> loadTraining() {
        return readMaterial(ChessYaml.resource("training.yaml"), "training");
    }

    public static List<Quest> loadIntermezzo() {
        return readMaterial(ChessYaml.resource("intermezzo.yaml"), "intermezzo");
    }

    static List<Quest> readMaterial(Map<?, ?> root, String chapter) {
        if (ChessYaml.number(root, "schema") != 2 || !"WIN_MATERIAL".equals(ChessYaml.string(root, "objective"))) {
            throw new IllegalArgumentException("Invalid tactics catalog schema or objective");
        }
        var quests = new ArrayList<Quest>();
        var ids = new HashSet<String>();
        var positions = new HashSet<String>();
        var difficulty = new HashMap<String, Integer>();
        var ranks = new HashSet<Integer>();
        for (Object item : ChessYaml.list(root, "quests")) {
            if (!(item instanceof Map<?, ?> row) || row.size() != 4) throw new IllegalArgumentException("Invalid tactics quest row");
            String id = ChessYaml.string(row, "id");
            requireAvailableId(id);
            String fen = ChessYaml.string(row, "fen");
            int rank = ChessYaml.number(row, "difficulty");
            if (rank < 1 || !ranks.add(rank)) throw new IllegalArgumentException("Invalid or duplicate tactics difficulty at " + id);
            if (!id.matches(chapter + "-[0-9]{3}") || !ids.add(id) || !positions.add(fen.replaceFirst(" [0-9]+ [0-9]+$", ""))) {
                throw new IllegalArgumentException("Invalid or duplicate tactics quest " + id);
            }
            var quest = new Quest(id, fen, "MATERIAL", ChessPosition.fromFen(fen),
                    List.of(Move.fromUci(ChessYaml.string(row, "solution"))), Objective.MATERIAL);
            if (quest.solvedPosition().isCheckmate()) throw new IllegalArgumentException("Unexpected mate in " + id);
            quests.add(quest);
            difficulty.put(id, rank);
        }
        if (quests.isEmpty()) throw new IllegalArgumentException("Empty tactics catalog");
        quests.sort(Comparator.comparingInt(quest -> difficulty.get(quest.id())));
        return Collections.unmodifiableList(quests);
    }

    private static List<Quest> loadMultiMove(String file, String objective, int length, int expectedCount) {
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
            requireAvailableId(id);
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
        if (result.size() != expectedCount) throw new IllegalArgumentException("Expected exactly " + expectedCount + " quests in " + file);
        return Collections.unmodifiableList(result);
    }

    static List<QuestDifficulty.Rank> loadOrder() {
        var root = ChessYaml.resource("quest-order.yaml");
        if (ChessYaml.number(root, "schema") != 1) throw new IllegalArgumentException("Invalid quest order schema");
        var ranks = new ArrayList<QuestDifficulty.Rank>();
        int previousScore = -1;
        for (Object item : ChessYaml.list(root, "order")) {
            if (!(item instanceof List<?> row) || row.size() != 2 || !(row.get(0) instanceof String id)
                    || !(row.get(1) instanceof Integer score) || score <= previousScore) {
                throw new IllegalArgumentException("Quest order must have strictly increasing scores: " + item);
            }
            ranks.add(new QuestDifficulty.Rank(id, score));
            previousScore = score;
        }
        return Collections.unmodifiableList(ranks);
    }

    static List<Quest> loadPositions() {
        var root = ChessYaml.resource("quests.yaml");
        if (ChessYaml.number(root, "schema") != 1 || !"MATE_IN_ONE".equals(ChessYaml.string(root, "objective"))) {
            throw new IllegalArgumentException("Invalid quest catalog schema or objective");
        }
        var result = new ArrayList<Quest>();
        var ids = new HashSet<String>();
        for (Object item : ChessYaml.list(root, "quests")) {
            if (!(item instanceof Map<?, ?> row) || row.size() != 4) throw new IllegalArgumentException("Invalid quest row");
            String id = ChessYaml.string(row, "id");
            requireAvailableId(id);
            String fen = ChessYaml.string(row, "fen");
            String theme = ChessYaml.string(row, "theme");
            if (!id.matches("mate-[0-9]{3,}") || !ids.add(id) || !theme.matches("TACTIC|EN_PASSANT|CASTLING|PROMOTION")) {
                throw new IllegalArgumentException("Invalid or duplicate quest " + id);
            }
            var quest = new Quest(id, fen, Move.fromUci(ChessYaml.string(row, "solution")), theme, ChessPosition.fromFen(fen));
            requireReferenceMate(quest);
            result.add(quest);
        }
        QuestDiversity.requireDistinct(result);
        return Collections.unmodifiableList(result);
    }

    static void requireReferenceMate(Quest quest) {
        String label = quest.id() + " " + quest.solution().uci();
        ChessPosition after;
        try {
            after = quest.solvedPosition();
        } catch (IllegalArgumentException illegalMove) {
            throw new IllegalArgumentException("Illegal reference mate " + label, illegalMove);
        }
        if (!after.isCheckmate()) throw new IllegalArgumentException("Invalid reference mate " + label);
        MatePattern pattern = quest.matingPattern();
        String actualTheme = switch (pattern) {
            case DIRECT, DISCOVERED -> "TACTIC";
            default -> pattern.name();
        };
        if (!actualTheme.equals(quest.theme())) {
            throw new IllegalArgumentException("Wrong theme for " + label + ": expected " + actualTheme);
        }
    }

    static void requireAvailableId(String id) {
        if (Retired.IDS.contains(id)) throw new IllegalArgumentException("Retired quest ID cannot be reused: " + id);
    }

    private static final class Retired {
        private static final Set<String> IDS = load();

        private static Set<String> load() {
            var root = ChessYaml.resource("quest-retired.yaml");
            if (!root.keySet().equals(Set.of("schema", "ids")) || ChessYaml.number(root, "schema") != 1) {
                throw new IllegalArgumentException("Invalid retired quest schema");
            }
            var ids = new HashSet<String>();
            for (Object item : ChessYaml.list(root, "ids")) {
                if (!(item instanceof String id) || !id.matches("mate-[0-9]{3,}|mate[23]-[A-Za-z0-9]{5}|tactic-[0-9]{3}")
                        || !ids.add(id)) throw new IllegalArgumentException("Invalid or repeated retired quest ID: " + item);
            }
            return Collections.unmodifiableSet(ids);
        }
    }
}
