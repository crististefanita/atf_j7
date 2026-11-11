package ro.atf.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** A local match against the computer. */
public final class ChessMatch {
    public enum Result { IN_PROGRESS, WHITE_WINS, BLACK_WINS, STALEMATE, MATERIAL_DRAW, REPETITION_DRAW, MOVE_COUNT_DRAW }

    private static final String FORMAT = "1|";
    private static final String ANALYSIS_FORMAT = "2|analysis|";
    private static final String SETUP_FORMAT = "3|";
    private static final MatchSetup DEFAULT_SETUP = new MatchSetup(Side.WHITE, MatchSetup.Level.BASIC, false);
    private final List<ChessPosition> positions;
    private final List<Move> moves;
    private final int quietPlies;
    private final MatchSetup setup;
    private final Result result;

    private ChessMatch(List<ChessPosition> positions, List<Move> moves, int quietPlies, MatchSetup setup) {
        this.positions = positions;
        this.moves = moves;
        this.quietPlies = quietPlies;
        this.setup = setup;
        result = evaluateResult();
    }

    public static ChessMatch start() { return start(false); }

    public static ChessMatch start(boolean analysisEnabled) {
        return start(new MatchSetup(Side.WHITE, MatchSetup.Level.BASIC, analysisEnabled));
    }

    public static ChessMatch start(MatchSetup setup) {
        return new ChessMatch(List.of(ChessPosition.initial()), List.of(), 0, setup);
    }

    static ChessMatch fromPosition(ChessPosition position) { return fromPosition(position, DEFAULT_SETUP); }

    static ChessMatch fromPosition(ChessPosition position, MatchSetup setup) {
        return new ChessMatch(List.of(position), List.of(), 0, setup);
    }

    public ChessPosition position() { return positionAt(ply()); }
    public int ply() { return moves.size(); }
    public Move lastMove() { return moveAt(ply()); }
    public ChessPosition positionAt(int ply) { return positions.get(ply); }
    public MatchMaterial materialAt(int ply) { return MatchMaterial.at(this, ply); }

    /** Move that produced the given position, or null at the starting position. */
    public Move moveAt(int ply) { return ply == 0 ? null : moves.get(ply - 1); }
    public Result result() { return result; }
    public MatchSetup setup() { return setup; }
    public boolean analysisEnabled() { return setup.analysisEnabled(); }
    public boolean awaitingBot() { return result == Result.IN_PROGRESS && position().turn() != setup.humanSide(); }

    /** Analyzes the displayed position from the point of view of the side to move. */
    public Optional<TacticalAnalysis.Variation> analysisAt(int ply) {
        return bestLinesAt(ply).stream().findFirst();
    }

    /** Returns the strongest hint and nearby alternatives for the displayed position. */
    public List<TacticalAnalysis.Variation> bestLinesAt(int ply) {
        var board = positionAt(ply);
        if (!analysisEnabled() || ply == ply() && result != Result.IN_PROGRESS) return Collections.emptyList();
        return TacticalAnalysis.recommendations(board);
    }

    public ChessMatch play(Move move) {
        if (result != Result.IN_PROGRESS) throw new IllegalStateException("The match has ended");
        var before = position();
        var after = before.play(move);
        var nextPositions = new ArrayList<>(positions);
        nextPositions.add(after);
        var nextMoves = new ArrayList<>(moves);
        nextMoves.add(move);
        return new ChessMatch(nextPositions, nextMoves, resetsQuietCount(before, move) ? 0 : quietPlies + 1, setup);
    }

    /** Starts a new continuation using exactly the history leading to the selected position. */
    public ChessMatch continueFrom(int ply) {
        if (ply < 0 || ply > ply()) throw new IllegalArgumentException("Invalid continuation ply: " + ply);
        if (ply == ply()) return this;
        int quiet = 0;
        for (int index = ply - 1; index >= 0 && !resetsQuietCount(positionAt(index), moves.get(index)); index--) quiet++;
        return new ChessMatch(List.copyOf(positions.subList(0, ply + 1)), List.copyOf(moves.subList(0, ply)), quiet, setup);
    }

    private static boolean resetsQuietCount(ChessPosition position, Move move) {
        return position.pieceAt(move.from()).kind() == Kind.PAWN || position.pieceAt(move.to()) != null;
    }

    Set<Move> automaticDrawMoves() {
        var board = position();
        var draws = new HashSet<Move>();
        for (var move : board.legalMoves()) {
            var after = board.playGenerated(move);
            if (deadMaterial(after) || !resetsQuietCount(board, move) && (quietPlies >= 149 || occurrences(after, ply()) >= 4)) {
                if (!after.isCheck() || after.hasLegalMove()) draws.add(move);
            }
        }
        return draws;
    }

    public String checkpoint() {
        String prefix = setup.humanSide() == Side.WHITE && setup.level() == MatchSetup.Level.BASIC
                ? analysisEnabled() ? ANALYSIS_FORMAT : FORMAT
                : SETUP_FORMAT + setup.humanSide().name() + "|" + setup.level().name() + "|" + (analysisEnabled() ? "1|" : "0|");
        var encoded = new StringJoiner(" ", prefix, "");
        for (Move move : moves) encoded.add(move.uci());
        return encoded.toString();
    }

    public static ChessMatch restore(String checkpoint) {
        if (checkpoint.startsWith(SETUP_FORMAT)) {
            String[] fields = checkpoint.split("\\|", -1);
            if (fields.length != 5 || !(fields[3].equals("0") || fields[3].equals("1"))) {
                throw new IllegalArgumentException("Invalid match setup checkpoint");
            }
            var setup = new MatchSetup(Side.valueOf(fields[1]), MatchSetup.Level.valueOf(fields[2]), fields[3].equals("1"));
            return replay(start(setup), fields[4]);
        }
        boolean analysis = checkpoint.startsWith(ANALYSIS_FORMAT);
        if (!analysis && !checkpoint.startsWith(FORMAT)) throw new IllegalArgumentException("Unsupported match checkpoint");
        return replay(start(analysis), checkpoint.substring((analysis ? ANALYSIS_FORMAT : FORMAT).length()));
    }

    private static ChessMatch replay(ChessMatch match, String history) {
        if (!history.isEmpty()) for (String move : history.split(" ", -1)) match = match.play(Move.fromUci(move));
        return match;
    }

    public Move chooseBotMove() {
        return botDecision().move();
    }

    record BotDecision(Move move, int depth, int extraNodes) { }

    BotDecision botDecision() {
        if (!awaitingBot()) throw new IllegalStateException("It is not the computer's turn");
        return botDecision(setup.level());
    }

    BotDecision botDecision(MatchSetup.Level level) {
        if (result != Result.IN_PROGRESS) throw new IllegalStateException("The match has ended");
        var board = position();
        boolean hard = level == MatchSetup.Level.HARD;
        boolean cycle = hard && occurrences(board, positions.size() - 2) >= 2;
        var policy = MatchAiPolicy.CURRENT.repetition;
        var repetitionOffers = cycle ? repetitionOffers(board) : Set.<Move>of();
        var search = TacticalAnalysis.botAnalysis(board, level, cycle ? policy.maxScoreLossCp() : 0, repetitionOffers);
        Move chosen = null;
        int fewestVisits = Integer.MAX_VALUE;
        int mostActivity = Integer.MIN_VALUE;
        for (var variation : search.result().best()) {
            Move move = variation.move();
            int visits = visitsAfter(move);
            int activity = activity(board, move);
            if (visits < fewestVisits || visits == fewestVisits && activity > mostActivity) {
                chosen = move;
                fewestVisits = visits;
                mostActivity = activity;
            }
        }
        if (hard) {
            var leader = search.result().ranked().get(0);
            if ((leader.score() < 0 || cycle && leader.score() == 0) && quietPlies >= 15) {
                // Four prior occurrences need at least sixteen reversible plies, including the draw move.
                chosen = board.legalMoves().stream().filter(move -> visitsAfter(move) >= 4).findFirst().orElse(chosen);
            } else if (cycle && fewestVisits > 0 && leader.ending() == TacticalAnalysis.Ending.HORIZON) {
                int floor = Math.max(policy.minAdvantageCp(), leader.score() - policy.maxScoreLossCp());
                int visits = fewestVisits;
                chosen = search.result().ranked().stream().filter(line -> line.score() >= floor)
                        .map(TacticalAnalysis.Variation::move).filter(move -> visitsAfter(move) < visits).findFirst().orElse(chosen);
            }
        }
        return new BotDecision(chosen, search.depth(), search.extraNodes());
    }

    private int visitsAfter(Move move) {
        return occurrences(position().playGenerated(move), positions.size() - 1);
    }

    private Set<Move> repetitionOffers(ChessPosition board) {
        var offers = new HashSet<Move>();
        for (var move : board.legalMoves()) if (visitsAfter(move) >= 2) offers.add(move);
        return offers;
    }

    static int activity(ChessPosition board, Move move) {
        Kind kind = board.pieceAt(move.from()).kind();
        int centralization = center(move.to()) - center(move.from());
        return switch (kind) {
            case KNIGHT, BISHOP -> 3 * centralization;
            case PAWN -> centralization + (move.to() / 8 - move.from() / 8) * (board.turn() == Side.WHITE ? 1 : -1);
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
