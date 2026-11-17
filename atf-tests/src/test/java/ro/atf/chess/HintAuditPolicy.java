package ro.atf.chess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Small, versioned offline study plan; it never configures live opponent strength. */
record HintAuditPolicy(String dataset, List<Run> runs) {
    static final int MAX_NODES = 200_000;
    record Run(String id, String family, String prefix, int decisions, List<Integer> budgets, List<Integer> ranks) {
        Run { budgets = List.copyOf(budgets); ranks = List.copyOf(ranks); }
        int budget(int index) { return budgets.get(index % budgets.size()); }
        int rank(int index) { return ranks.get(index % ranks.size()); }
        ChessMatch start() { return ChessMatch.restore("3|WHITE|HARD|1|" + prefix); }
    }
    HintAuditPolicy { runs = List.copyOf(runs); }

    static HintAuditPolicy load(Map<?, ?> root) {
        require(root.keySet().equals(Set.of("schema", "dataset", "max-nodes", "runs")), "Unexpected audit policy fields");
        require(ChessYaml.number(root, "schema") == 1 && ChessYaml.number(root, "max-nodes") == MAX_NODES,
                "Audit schema and 200k ceiling must be explicit");
        String dataset = identifier(ChessYaml.string(root, "dataset"));
        var runs = new ArrayList<Run>();
        var ids = new HashSet<String>();
        for (Object item : ChessYaml.list(root, "runs")) {
            require(item instanceof Map<?, ?>, "Expected an audit run mapping");
            var row = (Map<?, ?>) item;
            require(row.keySet().equals(Set.of("id", "family", "prefix", "decisions", "budgets", "ranks")), "Unexpected run fields");
            String id = identifier(ChessYaml.string(row, "id"));
            String family = identifier(ChessYaml.string(row, "family"));
            int decisions = ChessYaml.number(row, "decisions");
            require(ids.add(id) && decisions >= 1 && decisions <= 12, "Duplicate run or unbounded decision count");
            var run = new Run(id, family, ChessYaml.string(row, "prefix"), decisions,
                    integers(row, "budgets", 1_000, MAX_NODES), integers(row, "ranks", 1, 3));
            var start = run.start();
            require(start.ply() >= 2 && start.ply() <= 32 && start.result() == ChessMatch.Result.IN_PROGRESS,
                    "An audit must start at a legal, active opening prefix");
            runs.add(run);
        }
        require(!runs.isEmpty(), "An audit collection needs at least one run");
        return new HintAuditPolicy(dataset, runs);
    }

    private static List<Integer> integers(Map<?, ?> row, String name, int min, int max) {
        var result = new ArrayList<Integer>();
        for (Object item : ChessYaml.list(row, name)) {
            require(item instanceof Integer value && value >= min && value <= max, "Invalid " + name + " entry");
            result.add((Integer) item);
        }
        require(!result.isEmpty() && result.size() <= 12, "A schedule must have one to twelve entries");
        return List.copyOf(result);
    }

    private static String identifier(String value) {
        require(value.matches("[a-z][a-z0-9]*(?:-[a-z0-9]+)*"), "Invalid audit identifier");
        return value;
    }

    static void require(boolean accepted, String message) {
        if (!accepted) throw new IllegalArgumentException(message);
    }
}
