package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** A local match with White controlled by the player and Black by the computer. */
public final class ChessMatch {
    public enum Result { IN_PROGRESS, WHITE_WINS, BLACK_WINS, STALEMATE, MATERIAL_DRAW, REPETITION_DRAW, MOVE_COUNT_DRAW }

    private static final String FORMAT = "1|";
    private static final String ANALYSIS_FORMAT = "2|analysis|";
    private static final int SEARCH_PLIES = 3;
    private final List<ChessPosition> positions;
    private final List<Move> moves;
    private final int quietPlies;
    private final boolean analysisEnabled;
    private final Result result;

    private ChessMatch(List<ChessPosition> positions, List<Move> moves, int quietPlies, boolean analysisEnabled) {
        this.positions = positions;
        this.moves = moves;
        this.quietPlies = quietPlies;
        this.analysisEnabled = analysisEnabled;
        result = evaluateResult();
    }

    public static ChessMatch start() { return start(false); }

    public static ChessMatch start(boolean analysisEnabled) {
        return new ChessMatch(List.of(ChessPosition.initial()), List.of(), 0, analysisEnabled);
    }

    static ChessMatch fromPosition(ChessPosition position) { return new ChessMatch(List.of(position), List.of(), 0, false); }

    public ChessPosition position() { return positionAt(ply()); }
    public int ply() { return moves.size(); }
    public Move lastMove() { return moveAt(ply()); }
    public ChessPosition positionAt(int ply) { return positions.get(ply); }

    /** Move that produced the given position, or null at the starting position. */
    public Move moveAt(int ply) { return ply == 0 ? null : moves.get(ply - 1); }
    public Result result() { return result; }
    public boolean analysisEnabled() { return analysisEnabled; }
    public boolean awaitingBot() { return result == Result.IN_PROGRESS && position().turn() == Side.BLACK; }

    /** Analyzes the displayed position from the point of view of the side to move. */
    public Optional<TacticalAnalysis.Variation> analysisAt(int ply) {
        return bestLinesAt(ply).stream().findFirst();
    }

    /** Returns one continuation per best-scoring legal first move, in engine order. */
    public List<TacticalAnalysis.Variation> bestLinesAt(int ply) {
        var board = positionAt(ply);
        if (!analysisEnabled || ply == ply() && result != Result.IN_PROGRESS) return Collections.emptyList();
        return TacticalAnalysis.analyze(board, SEARCH_PLIES).best();
    }

    public ChessMatch play(Move move) {
        if (result != Result.IN_PROGRESS) throw new IllegalStateException("The match has ended");
        var before = position();
        var after = before.play(move);
        boolean resetsCount = before.pieceAt(move.from()).kind() == Kind.PAWN || before.pieceAt(move.to()) != null;
        var nextPositions = new ArrayList<>(positions);
        nextPositions.add(after);
        var nextMoves = new ArrayList<>(moves);
        nextMoves.add(move);
        return new ChessMatch(nextPositions, nextMoves, resetsCount ? 0 : quietPlies + 1, analysisEnabled);
    }

    public String checkpoint() {
        var encoded = new StringJoiner(" ", analysisEnabled ? ANALYSIS_FORMAT : FORMAT, "");
        for (Move move : moves) encoded.add(move.uci());
        return encoded.toString();
    }

    public static ChessMatch restore(String checkpoint) {
        boolean analysis = checkpoint.startsWith(ANALYSIS_FORMAT);
        if (!analysis && !checkpoint.startsWith(FORMAT)) throw new IllegalArgumentException("Unsupported match checkpoint");
        var match = start(analysis);
        String history = checkpoint.substring((analysis ? ANALYSIS_FORMAT : FORMAT).length());
        if (!history.isEmpty()) for (String move : history.split(" ", -1)) match = match.play(Move.fromUci(move));
        return match;
    }

    public Move chooseBotMove() {
        if (!awaitingBot()) throw new IllegalStateException("It is not the computer's turn");
        var board = position();
        Move chosen = null;
        int fewestVisits = Integer.MAX_VALUE;
        int mostActivity = Integer.MIN_VALUE;
        for (var variation : TacticalAnalysis.analyze(board, SEARCH_PLIES).best()) {
            Move move = variation.move();
            int visits = occurrences(board.play(move), positions.size() - 1);
            int activity = activity(board, move);
            if (visits < fewestVisits || visits == fewestVisits && activity > mostActivity) {
                chosen = move;
                fewestVisits = visits;
                mostActivity = activity;
            }
        }
        return chosen;
    }

    private static int activity(ChessPosition board, Move move) {
        Kind kind = board.pieceAt(move.from()).kind();
        int centralization = center(move.to()) - center(move.from());
        return switch (kind) {
            case KNIGHT, BISHOP -> 3 * centralization;
            case PAWN -> centralization + (move.from() / 8 - move.to() / 8);
            case KING -> Math.abs(move.from() - move.to()) == 2 ? 12 : -centralization;
            case ROOK, QUEEN -> centralization;
        };
    }

    private static int center(int square) {
        return 14 - Math.abs(2 * (square % 8) - 7) - Math.abs(2 * (square / 8) - 7);
    }

    private Result evaluateResult() {
        var board = position();
        var outcome = board.outcome();
        if (outcome == ChessPosition.Outcome.CHECKMATE) {
            return board.turn() == Side.WHITE ? Result.BLACK_WINS : Result.WHITE_WINS;
        }
        if (outcome == ChessPosition.Outcome.STALEMATE) return Result.STALEMATE;
        if (deadMaterial(board)) return Result.MATERIAL_DRAW;
        if (quietPlies >= 150) return Result.MOVE_COUNT_DRAW;
        if (occurrences(board, positions.size() - 2) >= 4) return Result.REPETITION_DRAW;
        return Result.IN_PROGRESS;
    }

    private int occurrences(ChessPosition board, int lastIndex) {
        int count = 0;
        int earliest = positions.size() - 1 - quietPlies;
        for (int index = lastIndex; index >= earliest; index--) {
            if (board.sameForRepetition(positions.get(index))) count++;
        }
        return count;
    }

    private static boolean deadMaterial(ChessPosition board) {
        int minorPieces = 0;
        int knights = 0;
        Boolean lightBishops = null;
        for (int square = 0; square < 64; square++) {
            var piece = board.pieceAt(square);
            if (piece == null || piece.kind() == Kind.KING) continue;
            minorPieces++;
            if (piece.kind() == Kind.KNIGHT) {
                knights++;
            } else if (piece.kind() == Kind.BISHOP) {
                boolean light = BoardSquare.isLight(square);
                if (lightBishops != null && lightBishops != light) return false;
                lightBishops = light;
            } else return false;
        }
        return minorPieces <= 1 || knights == 0;
    }
}
