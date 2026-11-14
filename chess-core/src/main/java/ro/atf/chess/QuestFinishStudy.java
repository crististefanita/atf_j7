package ro.atf.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Move;

/** A read-only alternative for the last move of a material puzzle. */
public record QuestFinishStudy(int stage, Move key, ChessPosition start, List<Move> line, String notation,
                               String explanationRo, String explanationEn, String alternativeRo, String alternativeEn) {
    private static final Set<String> FIELDS = Set.of("stage", "key", "fen", "line", "notation",
            "explanation-ro", "explanation-en", "alternative-ro", "alternative-en");

    public QuestFinishStudy { line = List.copyOf(line); }

    public static QuestFinishStudy forStage(QuestCatalog.Quest quest, int stage) {
        if (quest.objective() != QuestCatalog.Objective.MATERIAL || stage != quest.playerMoves() - 1) return null;
        var study = Catalog.STUDIES.get(quest.id());
        return study != null && study.matches(quest, stage) ? study : null;
    }

    public Move alternative() { return line.get(0); }

    public QuestPayoff replay(QuestCatalog.Quest quest) {
        if (quest.objective() != QuestCatalog.Objective.MATERIAL || stage != quest.playerMoves() - 1 || !matches(quest, stage)) {
            throw new IllegalArgumentException("Study does not match this puzzle finish");
        }
        return QuestPayoff.replay(quest.positionAt(stage), line, null, null);
    }

    private boolean matches(QuestCatalog.Quest quest, int stage) {
        return this.stage == stage && key.equals(quest.playerMove(stage)) && start.sameForRepetition(quest.positionAt(stage));
    }

    static Map<String, QuestFinishStudy> read(Map<?, ?> root) {
        if (!root.keySet().equals(Set.of("schema", "studies")) || ChessYaml.number(root, "schema") != 1) {
            throw new IllegalArgumentException("Invalid puzzle finish study schema");
        }
        var studies = new HashMap<String, QuestFinishStudy>();
        ChessYaml.map(root, "studies").forEach((key, value) -> {
            if (!(key instanceof String id) || !(value instanceof Map<?, ?> row) || !row.keySet().equals(FIELDS)) {
                throw new IllegalArgumentException("Invalid puzzle finish study " + key);
            }
            int stage = ChessYaml.number(row, "stage") - 1;
            var finish = Move.fromUci(ChessYaml.string(row, "key"));
            var start = ChessPosition.fromFen(ChessYaml.string(row, "fen"));
            var line = new ArrayList<Move>();
            for (String token : ChessYaml.string(row, "line").trim().split("\\s+")) line.add(Move.fromUci(token));
            if (stage < 0 || line.size() < 3 || line.get(0).equals(finish)) {
                throw new IllegalArgumentException("Invalid alternative for puzzle finish " + id);
            }
            var board = start;
            for (var move : line) board = board.play(move);
            studies.put(id, new QuestFinishStudy(stage, finish, start, line, ChessYaml.string(row, "notation"),
                    ChessYaml.string(row, "explanation-ro"), ChessYaml.string(row, "explanation-en"),
                    ChessYaml.string(row, "alternative-ro"), ChessYaml.string(row, "alternative-en")));
        });
        return Map.copyOf(studies);
    }

    private static final class Catalog {
        static final Map<String, QuestFinishStudy> STUDIES = read(ChessYaml.resource("quest-finish-studies.yaml"));
    }
}
