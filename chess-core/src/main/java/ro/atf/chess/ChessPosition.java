package ro.atf.chess;

import java.util.ArrayList;
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

    public List<Move> legalMovesFrom(int from) {
        if (from < 0 || from >= board.length || board[from] == null || board[from].side() != turn) {
            return Collections.emptyList();
        }
        List<Move> result = new ArrayList<>();
        for (int to = 0; to < board.length; to++) {
            boolean promotes = board[from].kind() == Kind.PAWN && to / 8 == RULES.pawn(turn).promotion();
            Move move = new Move(from, to, promotes ? RULES.promotions.get(0) : null);
            if (isLegal(move)) {
                if (promotes) {
                    for (Kind kind : RULES.promotions) result.add(new Move(from, to, kind));
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

    public List<Move> legalMoves() {
        var moves = new ArrayList<Move>();
        for (int square = 0; square < 64; square++) moves.addAll(legalMovesFrom(square));
        return Collections.unmodifiableList(moves);
    }

    public boolean isCheckmate() {
        if (!isCheck()) return false;
        for (int square = 0; square < 64; square++) if (!legalMovesFrom(square).isEmpty()) return false;
        return true;
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
        for (int square = 0; square < board.length; square++) {
            if (!legalMovesFrom(square).isEmpty()) return check ? Outcome.CHECK : Outcome.PLAYING;
        }
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
        var pattern = RULES.pattern(piece.kind());
        if (!pattern.ray()) return pattern.vectors().contains(new ChessRules.Vector(dx, dy));
        int distance = Math.max(Math.abs(dx), Math.abs(dy));
        if (distance == 0 || dx % distance != 0 || dy % distance != 0) return false;
        return pattern.vectors().contains(new ChessRules.Vector(dx / distance, dy / distance)) && clearPath(from, to);
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
