package ro.atf.chess;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ro.atf.chess.ChessPosition.Move;

/** Verified replies at exact opening positions. All subsequent moves come from search. */
final class OpeningRepertoire {
    static final OpeningRepertoire CURRENT = read(ChessYaml.resource("ai-repertoire.yaml"));

    private record Entry(ChessPosition position, Move reply) { }

    private final List<Entry> entries;

    private OpeningRepertoire(List<Entry> entries) { this.entries = List.copyOf(entries); }

    Move reply(ChessPosition position) {
        for (var entry : entries) if (entry.position().sameForRepetition(position)) return entry.reply();
        return null;
    }

    static OpeningRepertoire read(Map<?, ?> root) {
        if (!root.keySet().equals(Set.of("schema", "positions")) || ChessYaml.number(root, "schema") != 1) {
            throw new IllegalArgumentException("Invalid AI opening repertoire schema");
        }
        var entries = new ArrayList<Entry>();
        var ids = new HashSet<String>();
        for (Object item : ChessYaml.list(root, "positions")) {
            if (!(item instanceof Map<?, ?> row) || !row.keySet().equals(Set.of("id", "moves", "reply"))) {
                throw new IllegalArgumentException("Invalid AI opening repertoire entry");
            }
            String id = ChessYaml.string(row, "id");
            String[] prefix = ChessYaml.string(row, "moves").split(" ");
            if (!id.matches("[a-z]+(?:-[a-z]+)*") || !ids.add(id) || prefix.length < 4 || prefix.length > 16) {
                throw new IllegalArgumentException("Invalid AI opening identity " + id);
            }
            var position = ChessPosition.initial();
            for (String move : prefix) position = position.play(Move.fromUci(move));
            var board = position;
            Move reply = Move.fromUci(ChessYaml.string(row, "reply"));
            var after = board.play(reply);
            if (entries.stream().anyMatch(entry -> entry.position().sameForRepetition(board))) {
                throw new IllegalArgumentException("Duplicate AI opening position " + id);
            }
            if (!after.isCheckmate() && board.legalMoves().stream().map(board::playGenerated).anyMatch(ChessPosition::isCheckmate)) {
                throw new IllegalArgumentException("AI opening reply overlooks mate in one: " + id);
            }
            entries.add(new Entry(board, reply));
        }
        return new OpeningRepertoire(entries);
    }
}
