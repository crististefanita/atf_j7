package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Move;

/** Shared probe compiled into the test worker and run from the JVM with the original resource loader. */
public final class ChessWebParity {
    private ChessWebParity() { }

    public static String snapshot(String resources) {
        var rows = new StringBuilder();
        for (String name : resources.split("\n")) {
            var encoded = new StringBuilder();
            encode(encoded, ChessYaml.resource(name));
            row(rows, "yaml:" + name, encoded);
        }
        boardCases(rows);
        openingCases(rows);
        quests(rows, "mate-in-one", QuestCatalog.load(), "mate-001");
        quests(rows, "mate-in-two", QuestCatalog.loadMateInTwo(), "mate2-0042j");
        quests(rows, "mate-in-three", QuestCatalog.loadMateInThree(), "mate3-00o7c");
        quests(rows, "tactics", QuestCatalog.loadTactics(), "tactic-001");
        quests(rows, "training", QuestCatalog.loadTraining(), "training-001");
        quests(rows, "intermezzo", QuestCatalog.loadIntermezzo(), "intermezzo-001");
        return rows.toString();
    }

    private static void boardCases(StringBuilder rows) {
        var initial = ChessPosition.initial();
        row(rows, "initial-legal-moves", initial.legalMoves().size());
        var castles = ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        var kingside = castles.play(move("e1g1"));
        var queenside = castles.play(move("e1c1"));
        row(rows, "castling-moves-king-and-rook", piece(kingside, "g1") + "/" + piece(kingside, "f1")
                + "/" + piece(queenside, "c1") + "/" + piece(queenside, "d1"));
        var attacked = ChessPosition.fromFen("1k3r2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        row(rows, "castle-through-attacked-square", attacked.legalMoves().contains(move("e1g1")));

        var ready = position("e2e4 a7a6 e4e5 d7d5");
        var captured = ready.play(move("e5d6"));
        var late = ready.play(move("h2h3")).play(move("a6a5"));
        row(rows, "en-passant-capture-and-expiry", piece(captured, "d5") + "/" + piece(captured, "d6")
                + "/" + late.legalMoves().contains(move("e5d6")));
        var pinned = ChessPosition.fromFen("k7/8/8/4KPpr/8/8/8/8 w - g6 0 2");
        row(rows, "en-passant-cannot-expose-own-king", pinned.legalMoves().contains(move("f5g6")));

        var promotion = ChessPosition.fromFen("7k/P7/8/8/8/8/8/K7 w - - 0 1");
        var promotions = new ArrayList<String>();
        for (var option : promotion.legalMovesFrom(BoardSquare.parse("a7"))) promotions.add(option.uci());
        Collections.sort(promotions);
        var choices = new StringBuilder();
        for (String option : promotions) append(choices, ',', option);
        row(rows, "promotion-choices", choices);
        row(rows, "underpromotion", piece(promotion.play(move("a7a8n")), "a8"));

        var check = position("e2e4 d7d5 f1b5");
        row(rows, "move-that-leaves-king-in-check", denied(check, "h7h6"));
        row(rows, "blocking-a-check", check.legalMoves().contains(move("c7c6")));
        String moves = "f2f3 e7e5 g2g4 d8h4";
        var mate = position(moves);
        row(rows, "fools-mate", mate.outcome().name() + "/" + mate.legalMoves().size());
        var stalemate = ChessPosition.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
        row(rows, "stalemate", stalemate.outcome().name());
        var checkpoint = ChessMatch.restore("2|analysis|" + moves).checkpoint();
        var resumed = ChessMatch.restore(checkpoint);
        row(rows, "checkpoint-restores-result", resumed.result().name() + "/" + resumed.ply());
    }

    private static void openingCases(StringBuilder rows) {
        var chapters = new StringBuilder();
        for (var opening : OpeningCatalog.load()) append(chapters, ',', opening.id());
        row(rows, "authored-openings", chapters);
        var sicilian = new StringBuilder();
        for (var theory : OpeningTheory.CURRENT.choices(position("e2e4 c7c5"))) {
            append(sicilian, ',', theory.eco() + "/" + theory.move().uci());
        }
        row(rows, "sicilian-authored-choices", sicilian);
        var references = new StringBuilder();
        var italian = position("e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 c2c3 g8f6 d2d3 d7d6 e1g1 e8g8");
        for (var theory : OpeningTheory.CURRENT.choices(italian)) {
            append(references, ',', theory.eco() + "/" + theory.move().uci());
        }
        row(rows, "italian-eco-continuations", references);
        var book = new StringBuilder();
        for (var line : OpeningIndex.CURRENT.lines()) {
            encode(book, line.opening().eco());
            encode(book, line.opening().name());
            encode(book, line.moves());
        }
        row(rows, "book:ordered-eco-index", book);
    }

    private static void quests(StringBuilder rows, String chapter, List<QuestCatalog.Quest> quests, String anchor) {
        var content = new StringBuilder();
        for (var quest : quests) {
            encode(content, quest.id());
            encode(content, quest.fen());
            encode(content, quest.theme());
            encode(content, quest.objective().name());
            encode(content, quest.lesson() == null ? null : quest.lesson().name());
            for (var move : quest.line()) encode(content, move.uci());
            content.append(';');
            if (anchor.equals(quest.id())) {
                var solution = new StringBuilder();
                for (var move : quest.line()) append(solution, ',', move.uci());
                row(rows, "anchor:" + chapter, solution);
            }
        }
        row(rows, "count:" + chapter, quests.size());
        row(rows, "catalog:" + chapter, content);
    }

    private static ChessPosition position(String moves) {
        var position = ChessPosition.initial();
        for (String uci : moves.split(" ")) position = position.play(move(uci));
        return position;
    }

    private static Move move(String uci) { return Move.fromUci(uci); }

    private static String piece(ChessPosition position, String square) {
        var piece = position.pieceAt(BoardSquare.parse(square));
        return piece == null ? "-" : piece.side().name() + "-" + piece.kind().name();
    }

    private static boolean denied(ChessPosition position, String uci) {
        try {
            position.play(move(uci));
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private static void row(StringBuilder rows, String key, Object value) {
        append(rows, '\n', key + "\t" + value);
    }

    private static void append(StringBuilder values, char separator, String value) {
        if (values.length() > 0) values.append(separator);
        values.append(value);
    }

    // Values are type-tagged and length-prefixed. Escapes keep each resource on one line.
    private static void encode(StringBuilder target, Object value) {
        if (value instanceof Map<?, ?> fields) {
            target.append('m').append(fields.size()).append('{');
            for (var entry : fields.entrySet()) {
                encode(target, entry.getKey());
                encode(target, entry.getValue());
            }
            target.append('}');
        } else if (value instanceof List<?> items) {
            target.append('a').append(items.size()).append('[');
            for (var item : items) encode(target, item);
            target.append(']');
        } else if (value instanceof String text) {
            target.append('s').append(text.length()).append(':');
            target.append(text.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t"));
        } else if (value instanceof Integer integer) {
            target.append('i').append(integer.intValue()).append(';');
        } else if (value instanceof Long number) {
            target.append('l').append(number.longValue()).append(';');
        } else if (value instanceof Double number) {
            target.append('d').append(Long.toHexString(Double.doubleToLongBits(number))).append(';');
        } else if (value instanceof Boolean flag) {
            target.append(flag ? 't' : 'f');
        } else if (value == null) {
            target.append('n');
        } else {
            throw new IllegalArgumentException("Unsupported YAML value: " + value.getClass().getName());
        }
    }
}
