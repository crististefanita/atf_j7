package ro.atf.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.QuestCatalog.Quest;

/** Compares positions from the solving side's perspective. */
final class QuestDiversity {
    private static final int MAX_CHANGED_SQUARES = 10;

    private QuestDiversity() { }

    static void requireDistinct(List<Quest> quests) {
        var seen = new ArrayList<Entry>();
        for (Quest quest : quests) {
            var candidate = new Entry(quest.id(), views(quest));
            for (Entry prior : seen) for (View first : prior.views()) for (View second : candidate.views()) {
                if (first.repeats(second)) {
                    throw new IllegalArgumentException("Repeated chess exercise: " + prior.id() + " / " + quest.id());
                }
            }
            seen.add(candidate);
        }
    }

    private static List<View> views(Quest quest) {
        var fen = quest.fen().trim().split("\\s+");
        String rights = fen[2];
        int reflection = quest.position().turn() == Side.WHITE ? 0 : 56;
        var direct = view(quest, rights, reflection, false, true);
        // File reflection does not preserve orthodox castling geometry.
        if (!"-".equals(rights)) return List.of(direct);
        var mirror = view(quest, rights, reflection ^ 7, false, true);
        if (fen[0].indexOf('P') >= 0 || fen[0].indexOf('p') >= 0) return List.of(direct, mirror);

        // In pawnless positions the other board symmetries also preserve every legal move.
        var result = new ArrayList<>(List.of(direct, mirror));
        result.add(view(quest, rights, reflection ^ 56, false, false));
        result.add(view(quest, rights, reflection ^ 63, false, false));
        for (int flip : new int[]{0, 7, 56, 63}) result.add(view(quest, rights, reflection ^ flip, true, false));
        return result;
    }

    private static View view(Quest quest, String rights, int reflection, boolean transpose, boolean compareNear) {
        var position = quest.position();
        byte[] cells = new byte[64];
        int king = -1;
        for (int square = 0; square < cells.length; square++) {
            var piece = position.pieceAt(square);
            if (piece == null) continue;
            int target = transform(square, reflection, transpose);
            int kind = piece.kind().ordinal() + 1;
            if (piece.side() == position.turn()) cells[target] = (byte) kind;
            else {
                cells[target] = (byte) -kind;
                if (piece.kind() == Kind.KING) king = target;
            }
        }
        int relativeRights = 0;
        String symbols = position.turn() == Side.WHITE ? "KQkq" : "kqKQ";
        for (int index = 0; index < symbols.length(); index++) {
            if (rights.indexOf(symbols.charAt(index)) >= 0) relativeRights |= 1 << index;
        }
        int enPassant = position.legalEnPassant();
        var move = quest.solution();
        Kind kind = position.pieceAt(move.from()).kind();
        boolean special = kind == Kind.KING && Math.abs(move.from() - move.to()) == 2
                || kind == Kind.PAWN && position.pieceAt(move.to()) == null && move.from() % 8 != move.to() % 8;
        var key = compareNear ? new Key(king, transform(move.to(), reflection, transpose), kind, move.promotion(), special) : null;
        return new View(cells, relativeRights, enPassant < 0 ? -1 : transform(enPassant, reflection, transpose), key);
    }

    private static int transform(int square, int reflection, boolean transpose) {
        return (transpose ? (square % 8) * 8 + square / 8 : square) ^ reflection;
    }

    private record Entry(String id, List<View> views) { }

    private record Key(int defendingKing, int destination, Kind piece, Kind promotion, boolean specialMove) { }

    private record View(byte[] cells, int rights, int enPassant, Key key) {
        boolean repeats(View other) {
            if (rights == other.rights && enPassant == other.enPassant && Arrays.equals(cells, other.cells)) return true;
            if (key == null || !key.equals(other.key)) return false;
            int changes = 0;
            for (int square = 0; square < cells.length; square++) {
                if (cells[square] != other.cells[square] && ++changes > MAX_CHANGED_SQUARES) return false;
            }
            return true;
        }
    }
}
