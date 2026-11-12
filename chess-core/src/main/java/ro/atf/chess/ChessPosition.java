package ro.atf.chess;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Immutable position for local orthodox chess. The match owns history and draw claims. */
public final class ChessPosition {
    public enum Side {
        WHITE, BLACK;

        public Side other() { return this == WHITE ? BLACK : WHITE; }
    }

    public enum Kind { PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING }
    public enum Outcome { PLAYING, CHECK, CHECKMATE, STALEMATE }

    public record Piece(Side side, Kind kind) { }
    record SearchKey(long first, long second, long third, long fourth, int turn, int castleRights, int enPassant) { }

    public record Threat(int square, Piece piece) { }

    public record KingExit(int square, Piece ownBlocker, List<Threat> attackers) { }

    public record MateExplanation(int king, List<Threat> checkers, List<KingExit> exits) { }

    public record Move(int from, int to, Kind promotion) {
        public Move(int from, int to) { this(from, to, null); }

        public static Move fromUci(String text) {
            if (!text.matches("[a-h][1-8][a-h][1-8][qrbn]?")) throw new IllegalArgumentException("Invalid UCI move " + text);
            return new Move(BoardSquare.parse(text.substring(0, 2)), BoardSquare.parse(text.substring(2, 4)),
                    text.length() == 5 ? kind(text.charAt(4)) : null);
        }

        public String uci() {
            return BoardSquare.name(from) + BoardSquare.name(to) + (promotion == null ? "" : "pnbrqk".substring(promotion.ordinal(), promotion.ordinal() + 1));
        }
    }

    private static final ChessRules RULES = ChessRules.STANDARD;
    private static final int[][][][] TARGETS = possibleTargets();
    private static final String START = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private final Piece[] board;
    private final Side turn;
    private final int castleRights;
    private final int enPassant;

    private ChessPosition(Piece[] board, Side turn, int castleRights, int enPassant) {
        this.board = board;
        this.turn = turn;
        this.castleRights = castleRights;
        this.enPassant = enPassant;
    }

    public static ChessPosition initial() {
        return fromFen(START);
    }

    public static ChessPosition fromFen(String fen) {
        String[] fields = fen.trim().split("\\s+");
        if (fields.length != 6 || !fields[4].matches("[0-9]+") || !fields[5].matches("[1-9][0-9]*")) throw invalidFen(fen);
        Piece[] board = new Piece[64];
        String[] ranks = fields[0].split("/", -1);
        if (ranks.length != 8) throw invalidFen(fen);
        int[] kings = new int[2];
        for (int row = 0; row < 8; row++) {
            int file = 0;
            for (char token : ranks[row].toCharArray()) {
                if (token >= '1' && token <= '8') { file += token - '0'; continue; }
                if (file >= 8) throw invalidFen(fen);
                Kind type = kind(Character.toLowerCase(token));
                Side side = Character.isUpperCase(token) ? Side.WHITE : Side.BLACK;
                if (type == Kind.KING) kings[side.ordinal()]++;
                if (type == Kind.PAWN && (row == 0 || row == 7)) throw invalidFen(fen);
                board[BoardSquare.of(file++, 7 - row)] = new Piece(side, type);
            }
            if (file != 8) throw invalidFen(fen);
        }
        if (kings[0] != 1 || kings[1] != 1) throw invalidFen(fen);
        Side turn = switch (fields[1]) { case "w" -> Side.WHITE; case "b" -> Side.BLACK; default -> throw invalidFen(fen); };
        if (!fields[2].matches("-|K?Q?k?q?")) throw invalidFen(fen);
        int rights = 0;
        for (Side side : Side.values()) {
            int base = homeBase(side);
            for (ChessRules.Castle castle : new ChessRules.Castle[]{RULES.shortCastle, RULES.longCastle}) {
                char symbol = castle == RULES.shortCastle ? 'K' : 'Q';
                if (fields[2].indexOf(side == Side.WHITE ? symbol : Character.toLowerCase(symbol)) < 0) continue;
                if (!new Piece(side, Kind.KING).equals(board[base + RULES.kingFrom])
                        || !new Piece(side, Kind.ROOK).equals(board[base + castle.rookFrom()])) throw invalidFen(fen);
                rights |= rookRight(base + castle.rookFrom());
            }
        }
        int enPassant = fields[3].equals("-") ? -1 : BoardSquare.parse(fields[3]);
        if (enPassant >= 0) {
            var previous = RULES.pawn(turn.other());
            int step = previous.forward() * 8;
            if (enPassant / 8 != previous.home() + previous.forward() || board[enPassant] != null
                    || board[enPassant - step] != null || !new Piece(turn.other(), Kind.PAWN).equals(board[enPassant + step])) throw invalidFen(fen);
        }
        var result = new ChessPosition(board, turn, rights, enPassant);
        if (result.inCheck(turn.other())) throw invalidFen(fen);
        return result;
    }

    private static Kind kind(char token) {
        int index = "pnbrqk".indexOf(token);
        if (index < 0) throw new IllegalArgumentException("Unknown piece " + token);
        return Kind.values()[index];
    }

    private static IllegalArgumentException invalidFen(String fen) { return new IllegalArgumentException("Invalid position: " + fen); }

    public Side turn() { return turn; }

    public Piece pieceAt(int square) { return board[square]; }

    SearchKey searchKey() {
        long first = 0;
        long second = 0;
        long third = 0;
        long fourth = 0;
        int kinds = Kind.values().length;
        for (int square = 0; square < board.length; square++) {
            Piece piece = board[square];
            int code = piece == null ? 0 : 1 + piece.side().ordinal() * kinds + piece.kind().ordinal();
            long packed = (long) code << ((square & 15) * 4);
            switch (square >>> 4) {
                case 0 -> first |= packed;
                case 1 -> second |= packed;
                case 2 -> third |= packed;
                case 3 -> fourth |= packed;
                default -> throw new IllegalStateException("Board square is outside the search key");
            }
        }
        return new SearchKey(first, second, third, fourth, turn.ordinal(), castleRights, legalEnPassant());
    }

    boolean sameForRepetition(ChessPosition other) {
        return turn == other.turn && castleRights == other.castleRights && Arrays.equals(board, other.board)
                && (enPassant == other.enPassant || legalEnPassant() == other.legalEnPassant());
    }

    int legalEnPassant() {
        if (enPassant < 0) return -1;
        int adjacentRank = enPassant - 8 * RULES.pawn(turn).forward();
        for (int offset : new int[]{-1, 1}) {
            int from = adjacentRank + offset;
            if (from / 8 == adjacentRank / 8 && from >= 0 && from < 64
                    && new Piece(turn, Kind.PAWN).equals(board[from]) && isLegal(new Move(from, enPassant))) {
                return enPassant;
            }
        }
        return -1;
    }

    public List<Move> legalMovesFrom(int from) {
        return legalMovesFrom(from, false, false);
    }

    private List<Move> legalMovesFrom(int from, boolean tacticalOnly, boolean firstOnly) {
        if (from < 0 || from >= board.length || board[from] == null || board[from].side() != turn) {
            return Collections.emptyList();
        }
        List<Move> result = new ArrayList<>();
        Kind kind = board[from].kind();
        for (int to : TARGETS[turn.ordinal()][kind.ordinal()][from]) {
            boolean promotes = kind == Kind.PAWN && to / 8 == RULES.pawn(turn).promotion();
            if (tacticalOnly && !promotes && board[to] == null && !(kind == Kind.PAWN && to == enPassant)) continue;
            Move move = new Move(from, to, promotes ? RULES.promotions.get(0) : null);
            if (isLegal(move)) {
                if (firstOnly) return Collections.singletonList(move);
                if (promotes) {
                    for (Kind promotion : RULES.promotions) result.add(new Move(from, to, promotion));
                } else {
                    result.add(move);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    public ChessPosition play(Move move) {
        if (!isLegal(move)) throw new IllegalArgumentException("Illegal move: " + move);
        return apply(move);
    }

    // Used by search with moves generated as legal on this exact position.
    ChessPosition playGenerated(Move move) { return apply(move); }

    public List<Move> legalMoves() {
        var moves = new ArrayList<Move>();
        for (int square = 0; square < 64; square++) moves.addAll(legalMovesFrom(square));
        return Collections.unmodifiableList(moves);
    }

    List<Move> legalCapturesAndPromotions() {
        var moves = new ArrayList<Move>();
        for (int square = 0; square < 64; square++) moves.addAll(legalMovesFrom(square, true, false));
        return moves;
    }

    public boolean isCheckmate() {
        return isCheck() && !hasLegalMove();
    }

    boolean hasLegalMove() {
        for (int square = 0; square < 64; square++) if (!legalMovesFrom(square, false, true).isEmpty()) return true;
        return false;
    }

    public boolean isCheck() { return inCheck(turn); }

    public MateExplanation explainMate() {
        if (!isCheckmate()) throw new IllegalStateException("Position has no checkmate to explain");
        int king = kingSquare(turn);
        var exits = new ArrayList<KingExit>();
        for (int square = 0; square < board.length; square++) {
            if (square == king || !reaches(board[king], king, square)) continue;
            Piece occupant = board[square];
            if (occupant != null && occupant.side() == turn) {
                exits.add(new KingExit(square, occupant, Collections.emptyList()));
            } else {
                // Test attacks after the king vacates its old square and removes any captured piece.
                var after = apply(new Move(king, square));
                exits.add(new KingExit(square, null, after.attackersTo(square, turn.other())));
            }
        }
        return new MateExplanation(king, attackersTo(king, turn.other()), Collections.unmodifiableList(exits));
    }

    private List<Threat> attackersTo(int destination, Side by) {
        var attackers = new ArrayList<Threat>();
        for (int square = 0; square < board.length; square++) {
            Piece piece = board[square];
            if (piece != null && piece.side() == by && reaches(piece, square, destination)) attackers.add(new Threat(square, piece));
        }
        return Collections.unmodifiableList(attackers);
    }

    boolean isCheckedFrom(int square) {
        Piece attacker = board[square];
        return attacker != null && attacker.side() == turn.other() && reaches(attacker, square, kingSquare(turn));
    }

    public Outcome outcome() {
        boolean check = inCheck(turn);
        if (hasLegalMove()) return check ? Outcome.CHECK : Outcome.PLAYING;
        return check ? Outcome.CHECKMATE : Outcome.STALEMATE;
    }

    private boolean isLegal(Move move) {
        int from = move.from();
        int to = move.to();
        if (from < 0 || from >= 64 || to < 0 || to >= 64 || from == to) return false;
        Piece piece = board[from];
        Piece target = board[to];
        if (piece == null || piece.side() != turn || target != null && (target.side() == turn || target.kind() == Kind.KING)) return false;
        boolean promotes = piece.kind() == Kind.PAWN && to / 8 == RULES.pawn(turn).promotion();
        if (promotes ? !RULES.promotions.contains(move.promotion()) : move.promotion() != null) return false;
        int dx = to % 8 - from % 8;
        int dy = to / 8 - from / 8;
        boolean allowed = switch (piece.kind()) {
            case PAWN -> pawnMove(from, to, dx, dy, target);
            case KING -> reaches(piece, from, to) || canCastle(from, to);
            default -> reaches(piece, from, to);
        };
        return allowed && !apply(move).inCheck(turn);
    }

    private boolean pawnMove(int from, int to, int dx, int dy, Piece target) {
        var pawn = RULES.pawn(turn);
        int forward = pawn.forward();
        if (Math.abs(dx) == RULES.captureFileStep) return dy == forward && (target != null || to == enPassant);
        if (dx != 0 || target != null) return false;
        return dy == forward || from / 8 == pawn.home() && dy == forward * RULES.initialAdvance && board[from + forward * 8] == null;
    }

    private boolean canCastle(int from, int to) {
        int base = homeBase(turn);
        var castle = castleAt(to, base);
        if (from != base + RULES.kingFrom || castle == null) return false;
        int rook = base + castle.rookFrom();
        if ((castleRights & rookRight(rook)) == 0 || !new Piece(turn, Kind.ROOK).equals(board[rook]) || !clearPath(from, rook)) return false;
        return !inCheck(turn) && !apply(new Move(from, base + castle.rookTo())).inCheck(turn);
    }

    private static int homeBase(Side side) { return side == Side.WHITE ? 0 : 56; }

    private static ChessRules.Castle castleAt(int to, int base) {
        if (to == base + RULES.shortCastle.kingTo()) return RULES.shortCastle;
        return to == base + RULES.longCastle.kingTo() ? RULES.longCastle : null;
    }

    private boolean inCheck(Side side) {
        int king = kingSquare(side);
        for (int square = 0; square < board.length; square++) {
            Piece piece = board[square];
            if (piece != null && piece.side() != side && reaches(piece, square, king)) return true;
        }
        return false;
    }

    private int kingSquare(Side side) {
        Piece royal = new Piece(side, Kind.KING);
        int king = 0;
        while (!royal.equals(board[king])) king++;
        return king;
    }

    private boolean reaches(Piece piece, int from, int to) {
        int dx = to % 8 - from % 8;
        int dy = to / 8 - from / 8;
        if (piece.kind() == Kind.PAWN) return Math.abs(dx) == RULES.captureFileStep && dy == RULES.pawn(piece.side()).forward();
        return geometry(piece.kind(), dx, dy) && (!RULES.ray(piece.kind()) || clearPath(from, to));
    }

    private static boolean geometry(Kind kind, int dx, int dy) {
        if (!RULES.ray(kind)) return RULES.direction(kind, dx, dy);
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (distance == 0 || dx % distance != 0 || dy % distance != 0) return false;
        return RULES.direction(kind, dx / distance, dy / distance);
    }

    private static int[][][][] possibleTargets() {
        var targets = new int[Side.values().length][Kind.values().length][64][];
        for (Side side : Side.values()) for (Kind kind : Kind.values()) for (int from = 0; from < 64; from++) {
            int[] squares = new int[64];
            int count = 0;
            for (int to = 0; to < 64; to++) if (possibleTarget(side, kind, from, to)) squares[count++] = to;
            targets[side.ordinal()][kind.ordinal()][from] = Arrays.copyOf(squares, count);
        }
        return targets;
    }

    private static boolean possibleTarget(Side side, Kind kind, int from, int to) {
        int dx = to % 8 - from % 8;
        int dy = to / 8 - from / 8;
        if (kind == Kind.PAWN) {
            var pawn = RULES.pawn(side);
            return dy == pawn.forward() && Math.abs(dx) <= RULES.captureFileStep
                    || dx == 0 && from / 8 == pawn.home() && dy == pawn.forward() * RULES.initialAdvance;
        }
        int base = homeBase(side);
        return geometry(kind, dx, dy) || kind == Kind.KING && from == base + RULES.kingFrom && castleAt(to, base) != null;
    }

    private boolean clearPath(int from, int to) {
        int step = Integer.signum(to % 8 - from % 8) + 8 * Integer.signum(to / 8 - from / 8);
        for (int current = from + step; current != to; current += step) {
            if (board[current] != null) return false;
        }
        return true;
    }

    private ChessPosition apply(Move move) {
        int from = move.from();
        int to = move.to();
        Piece piece = board[from];
        Piece[] next = board.clone();
        next[from] = null;
        next[to] = move.promotion() == null ? piece : new Piece(turn, move.promotion());
        int rights = castleRights & ~rookRight(from) & ~rookRight(to);
        int nextEnPassant = -1;
        if (piece.kind() == Kind.PAWN) {
            if (to == enPassant && from % 8 != to % 8) next[(from / 8) * 8 + to % 8] = null;
            if (Math.abs(to - from) == RULES.initialAdvance * 8) nextEnPassant = (from + to) / 2;
        } else if (piece.kind() == Kind.KING) {
            rights &= turn == Side.WHITE ? 12 : 3;
            int base = homeBase(turn);
            var castle = castleAt(to, base);
            if (castle != null && from == base + RULES.kingFrom) {
                int rook = base + castle.rookFrom();
                next[base + castle.rookTo()] = next[rook];
                next[rook] = null;
            }
        }
        return new ChessPosition(next, turn.other(), rights, nextEnPassant);
    }

    private static int rookRight(int square) {
        return switch (square) {
            case 0 -> 1;
            case 7 -> 2;
            case 56 -> 4;
            case 63 -> 8;
            default -> 0;
        };
    }
}
