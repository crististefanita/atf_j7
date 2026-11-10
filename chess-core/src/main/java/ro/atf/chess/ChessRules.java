package ro.atf.chess;

import static ro.atf.chess.ChessYaml.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Side;

final class ChessRules {
    record Vector(int file, int rank) { }
    record Pattern(boolean ray, List<Vector> vectors) { }
    record Pawn(int forward, int home, int promotion) { }
    record Castle(int rookFrom, int kingTo, int rookTo) { }

    static final ChessRules STANDARD = new ChessRules(resource("chess-rules.yaml"));

    private final Map<Kind, Pattern> patterns = new EnumMap<>(Kind.class);
    private final Pawn white;
    private final Pawn black;
    final int initialAdvance;
    final int captureFileStep;
    final List<Kind> promotions;
    final int kingFrom;
    final Castle shortCastle;
    final Castle longCastle;

    private ChessRules(Map<?, ?> root) {
        if (number(root, "schema") != 1 || !"alternate".equals(string(root, "turn"))
                || !"empty-or-opponent-except-king".equals(string(root, "destination"))
                || !"required".equals(string(root, "king-safety"))
                || !"includes-pinned-pieces".equals(string(root, "attack-model"))
                || !"check-without-legal-reply".equals(string(root, "mate"))) throw invalid();
        var pieces = map(root, "pieces");
        for (Kind kind : Kind.values()) {
            if (kind == Kind.PAWN) continue;
            var data = map(pieces, kind.name());
            String mode = string(data, "mode");
            if (!"ray".equals(mode) && !"leap".equals(mode)) throw invalid();
            boolean ray = "ray".equals(mode);
            var vectors = new ArrayList<Vector>();
            for (Object entry : list(data, "vectors")) {
                if (!(entry instanceof List<?> xy) || xy.size() != 2 || !(xy.get(0) instanceof Integer x)
                        || !(xy.get(1) instanceof Integer y) || x == 0 && y == 0
                        || Math.abs((long) x) > (ray ? 1 : 2) || Math.abs((long) y) > (ray ? 1 : 2)) throw invalid();
                vectors.add(new Vector(x, y));
            }
            if (new HashSet<>(vectors).size() != vectors.size()) throw invalid();
            patterns.put(kind, new Pattern(ray, Collections.unmodifiableList(vectors)));
        }
        var pawn = map(root, "pawn");
        white = pawn(map(pawn, "white"), 1, 2, 8);
        black = pawn(map(pawn, "black"), -1, 7, 1);
        initialAdvance = number(pawn, "initial-advance");
        captureFileStep = number(pawn, "capture-file-step");
        if (initialAdvance != 2 || captureFileStep != 1 || !"immediate".equals(string(pawn, "en-passant"))) throw invalid();
        var choices = new ArrayList<Kind>();
        for (Object choice : list(pawn, "promote-to")) choices.add(Kind.valueOf(String.valueOf(choice)));
        if (choices.size() != 4 || new HashSet<>(choices).size() != 4 || choices.contains(Kind.PAWN) || choices.contains(Kind.KING)) throw invalid();
        promotions = Collections.unmodifiableList(choices);
        var castling = map(root, "castling");
        if (!Boolean.TRUE.equals(castling.get("require-clear")) || !Boolean.TRUE.equals(castling.get("require-unattacked"))) throw invalid();
        kingFrom = file(castling, "king-from");
        shortCastle = castle(map(castling, "short"));
        longCastle = castle(map(castling, "long"));
        if (kingFrom != 4 || !shortCastle.equals(new Castle(7, 6, 5)) || !longCastle.equals(new Castle(0, 2, 3))) throw invalid();
    }

    Pawn pawn(Side side) { return side == Side.WHITE ? white : black; }

    Pattern pattern(Kind kind) { return patterns.get(kind); }

    private static Pawn pawn(Map<?, ?> data, int forward, int home, int promotion) {
        Pawn rule = new Pawn(number(data, "forward"), number(data, "home") - 1, number(data, "promotion") - 1);
        if (!rule.equals(new Pawn(forward, home - 1, promotion - 1))) throw invalid();
        return rule;
    }

    private static Castle castle(Map<?, ?> data) {
        return new Castle(file(data, "rook-from"), file(data, "king-to"), file(data, "rook-to"));
    }

    private static int file(Map<?, ?> data, String key) {
        String text = string(data, key);
        if (text.length() != 1 || text.charAt(0) < 'a' || text.charAt(0) > 'h') throw invalid();
        return text.charAt(0) - 'a';
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid standard chess rule configuration"); }
}
