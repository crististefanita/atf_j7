package ro.atf.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Move;

/** A reviewed defense and continuation for the explanation of a material quest. */
public record QuestDefenseStudy(String sourceId, int depth, Integer evaluationCp, Integer mateIn,
                                List<Move> line, String lessonRo, String lessonEn) {
    private static final Set<String> FIELDS = Set.of("source-id", "depth", "evaluation-cp", "mate-in", "line", "lesson-ro", "lesson-en");

    public QuestDefenseStudy { line = List.copyOf(line); }

    static QuestDefenseStudy forId(String id) { return Catalog.STUDIES.get(id); }

    static Set<String> ids() { return Catalog.STUDIES.keySet(); }

    static Map<String, QuestDefenseStudy> read(Map<?, ?> yaml) {
        if (!yaml.keySet().equals(Set.of("schema", "studies")) || ChessYaml.number(yaml, "schema") != 1) {
            throw new IllegalArgumentException("Invalid quest defense study schema");
        }
        var studies = new HashMap<String, QuestDefenseStudy>();
        ChessYaml.map(yaml, "studies").forEach((key, value) -> {
            if (!(key instanceof String id) || !id.matches("(?:tactic|training|intermezzo)-[0-9]{3}") || !(value instanceof Map<?, ?> row)) {
                throw new IllegalArgumentException("Invalid quest defense study " + key);
            }
            studies.put(id, readStudy(id, row));
        });
        return Map.copyOf(studies);
    }

    private static QuestDefenseStudy readStudy(String id, Map<?, ?> row) {
        boolean hasCp = row.containsKey("evaluation-cp");
        boolean hasMate = row.containsKey("mate-in");
        if (row.size() != 6 || !FIELDS.containsAll(row.keySet()) || hasCp == hasMate) {
            throw new IllegalArgumentException("Expected one reviewed evaluation in " + id);
        }
        String source = ChessYaml.string(row, "source-id");
        int depth = ChessYaml.number(row, "depth");
        Integer evaluation = hasCp ? ChessYaml.number(row, "evaluation-cp") : null;
        Integer mate = hasMate ? ChessYaml.number(row, "mate-in") : null;
        String ro = ChessYaml.string(row, "lesson-ro");
        String en = ChessYaml.string(row, "lesson-en");
        var tokens = ChessYaml.string(row, "line").trim().split("\\s+");
        var moves = new ArrayList<Move>(tokens.length);
        for (String token : tokens) moves.add(Move.fromUci(token));
        if (!source.matches("[0-9A-Za-z]{5}") || depth < 1 || depth > 99 || moves.size() < 3
                || (evaluation != null && Math.abs((long) evaluation) > 20_000) || (mate != null && mate == 0)) {
            throw new IllegalArgumentException("Invalid evidence in quest defense study " + id);
        }
        return new QuestDefenseStudy(source, depth, evaluation, mate, moves, ro, en);
    }

    private static final class Catalog {
        static final Map<String, QuestDefenseStudy> STUDIES = read(ChessYaml.resource("quest-defense-studies.yaml"));
    }
}
