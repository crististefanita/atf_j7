package ro.atf.chess;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.MatchSetup.Level;

final class MatchAiPolicy {
    static final int BASE_PLIES = 3;
    static final int MAX_PLIES = 6;
    static final MatchAiPolicy CURRENT = read(ChessYaml.resource("match-ai.yaml"));

    record Search(int maxPlies, int extraNodes) { }
    record Hint(Search search, int maxVariants, int maxLossCp, int tacticalExtensionPlies, int verificationNodes) { }
    record Evaluation(int nodes, int maxHorizonCorrectionCp) { }
    record Repetition(int minAdvantageCp, int maxScoreLossCp) { }
    record RookEndgame(int extensionPlies, int candidateGapCp) { }
    record HardOrdering(int checkBonus) { }

    private final Map<Level, Search> opponents;
    final Hint hint;
    final Evaluation evaluation;
    final Repetition repetition;
    final RookEndgame rookEndgame;
    final HardOrdering hardOrdering;

    private MatchAiPolicy(Map<Level, Search> opponents, Hint hint, Evaluation evaluation, Repetition repetition,
                          RookEndgame rookEndgame, HardOrdering hardOrdering) {
        this.opponents = opponents;
        this.hint = hint;
        this.evaluation = evaluation;
        this.repetition = repetition;
        this.rookEndgame = rookEndgame;
        this.hardOrdering = hardOrdering;
    }

    Search opponent(Level level) { return opponents.get(level); }

    static MatchAiPolicy read(Map<?, ?> root) {
        requireKeys(root, "schema", "opponents", "hint", "evaluation", "repetition", "rook-endgame", "hard-ordering");
        if (ChessYaml.number(root, "schema") != 3) throw new IllegalArgumentException("Unknown match AI schema");
        var settings = ChessYaml.map(root, "opponents");
        requireKeys(settings, "BASIC", "ADVANCED", "HARD");
        var opponents = new EnumMap<Level, Search>(Level.class);
        Search previous = null;
        for (Level level : Level.values()) {
            var row = ChessYaml.map(settings, level.name());
            requireKeys(row, "max-plies", "extra-nodes");
            Search current = search(row);
            if (previous != null && (current.maxPlies() <= previous.maxPlies() || current.extraNodes() <= previous.extraNodes())) {
                throw new IllegalArgumentException("AI depth and budget must strictly increase with level: " + level);
            }
            opponents.put(level, current);
            previous = current;
        }
        var row = ChessYaml.map(root, "hint");
        requireKeys(row, "max-plies", "extra-nodes", "max-variants", "max-loss-cp", "tactical-extension-plies", "verification-nodes");
        Search search = search(row);
        int verification = bounded(row, "verification-nodes", 1_000, 60_000);
        if (search.maxPlies() < previous.maxPlies() || search.extraNodes() <= previous.extraNodes() + verification) {
            throw new IllegalArgumentException("Hint search needs at least the Hard depth and a larger budget");
        }
        var evaluation = ChessYaml.map(root, "evaluation");
        requireKeys(evaluation, "nodes", "max-horizon-correction-cp");
        var repetition = ChessYaml.map(root, "repetition");
        requireKeys(repetition, "min-advantage-cp", "max-score-loss-cp");
        var rookEndgame = ChessYaml.map(root, "rook-endgame");
        requireKeys(rookEndgame, "extension-plies", "candidate-gap-cp");
        int extension = bounded(rookEndgame, "extension-plies", 0, 1);
        if (opponents.get(Level.HARD).maxPlies() + extension > MAX_PLIES) {
            throw new IllegalArgumentException("Rook endgame extension exceeds the supported search horizon");
        }
        var hardOrdering = ChessYaml.map(root, "hard-ordering");
        requireKeys(hardOrdering, "check-bonus");
        return new MatchAiPolicy(opponents, new Hint(search,
                bounded(row, "max-variants", 1, 5), bounded(row, "max-loss-cp", 0, 100),
                bounded(row, "tactical-extension-plies", 0, 2), verification), new Evaluation(
                        bounded(evaluation, "nodes", 1_000, 40_000), bounded(evaluation, "max-horizon-correction-cp", 0, 50)),
                new Repetition(bounded(repetition, "min-advantage-cp", 75, 250), bounded(repetition, "max-score-loss-cp", 1, 75)),
                new RookEndgame(extension, bounded(rookEndgame, "candidate-gap-cp", 25, 75)),
                new HardOrdering(bounded(hardOrdering, "check-bonus", 0, 10_000)));
    }

    private static Search search(Map<?, ?> row) {
        int depth = bounded(row, "max-plies", BASE_PLIES, MAX_PLIES);
        int budget = bounded(row, "extra-nodes", 0, 500_000);
        if (depth > BASE_PLIES && budget == 0) throw new IllegalArgumentException("Deeper AI search requires extra nodes");
        return new Search(depth, budget);
    }

    private static int bounded(Map<?, ?> row, String key, int min, int max) {
        int value = ChessYaml.number(row, key);
        if (value < min || value > max) throw new IllegalArgumentException("Out-of-range match AI setting: " + key);
        return value;
    }

    private static void requireKeys(Map<?, ?> row, String... keys) {
        if (!row.keySet().equals(Set.of(keys))) throw new IllegalArgumentException("Invalid match AI keys: " + row.keySet());
    }
}
