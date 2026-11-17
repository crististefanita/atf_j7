package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** A read-only replay of one engine line, starting at the exact position under review. */
public final class HintPreview {
    public enum Reason { CHECKMATE, CHECKMATE_AGAINST, STALEMATE, CAPTURE, ESTIMATE }

    public record Evidence(Reason reason, Move move, Kind piece, Kind captured, int netMaterial) { }

    public record Estimate(int initialCp, int finalCp) {
        public int changeCp() { return finalCp - initialCp; }
    }

    private record Frame(ChessPosition position, Move move, Kind piece, Kind captured) { }

    private final List<Frame> frames;
    private final Evidence evidence;
    private final Optional<Estimate> estimate;
    private final boolean addedDefense;
    private int step;

    private HintPreview(List<Frame> frames, Evidence evidence, Optional<Estimate> estimate, boolean addedDefense) {
        this.frames = List.copyOf(frames);
        this.evidence = evidence;
        this.estimate = estimate;
        this.addedDefense = addedDefense;
    }

    public static Optional<HintPreview> open(ChessPosition root, TacticalAnalysis.Variation line) {
        return open(root, line, false);
    }

    /** Replays the already scored defence without appending an unscored search result. */
    public static Optional<HintPreview> openDefended(ChessPosition root, TacticalAnalysis.Variation line) {
        return open(root, line, true);
    }

    private static Optional<HintPreview> open(ChessPosition root, TacticalAnalysis.Variation line, boolean defend) {
        if (line.line().isEmpty()) return Optional.empty();
        var board = root;
        var frames = new ArrayList<Frame>();
        frames.add(new Frame(root, null, null, null));
        for (Move move : line.line()) {
            try {
                board = append(frames, board, move);
            } catch (IllegalArgumentException invalid) {
                return Optional.empty();
            }
        }
        var ending = line.ending();
        if (defend) {
            ending = TacticalAnalysis.Ending.HORIZON;
            if (!board.hasLegalMove()) ending = board.isCheck() ? TacticalAnalysis.Ending.CHECKMATE : TacticalAnalysis.Ending.STALEMATE;
        }
        var estimate = defend && ending == TacticalAnalysis.Ending.HORIZON
                ? Optional.of(new Estimate(TacticalAnalysis.positionValue(root, root.turn()), TacticalAnalysis.positionValue(board, root.turn())))
                : Optional.<Estimate>empty();
        return Optional.of(new HintPreview(frames, evidence(root, board, frames, ending), estimate, false));
    }

    private static ChessPosition append(List<Frame> frames, ChessPosition board, Move move) {
        var next = board.play(move);
        var victim = MatchMaterial.victim(board, move);
        frames.add(new Frame(next, move, next.pieceAt(move.to()).kind(), victim == null ? null : victim.kind()));
        return next;
    }

    private static Evidence evidence(ChessPosition root, ChessPosition end, List<Frame> frames, TacticalAnalysis.Ending ending) {
        Side side = root.turn();
        if (ending == TacticalAnalysis.Ending.CHECKMATE && end.isCheckmate()) {
            var mate = frames.get(frames.size() - 1);
            var reason = end.turn() == side ? Reason.CHECKMATE_AGAINST : Reason.CHECKMATE;
            return new Evidence(reason, mate.move(), mate.piece(), mate.captured(), 0);
        }
        if (ending == TacticalAnalysis.Ending.STALEMATE && !end.isCheck() && end.legalMoves().isEmpty()) {
            return new Evidence(Reason.STALEMATE, null, null, null, 0);
        }
        if (ending == TacticalAnalysis.Ending.HORIZON) {
            var capture = singleCapture(frames, side);
            if (capture != null) {
                int gain = retainedGain(root, end, side);
                if (gain > 0) return new Evidence(Reason.CAPTURE, capture.move(), capture.piece(), capture.captured(), gain);
            }
        }
        return new Evidence(Reason.ESTIMATE, null, null, null, 0);
    }

    static int retainedGain(ChessPosition root, ChessPosition end, Side side) {
        int original = MatchMaterial.balance(root, side);
        int gain = MatchMaterial.balance(end, side) - original;
        if (gain <= 0 || end.turn() == side) return gain;
        for (Move reply : end.legalMoves()) {
            if (reply.promotion() != null || MatchMaterial.victim(end, reply) != null) {
                int afterReply = MatchMaterial.balance(end.playGenerated(reply), side) - original;
                gain = Math.min(gain, afterReply);
            }
        }
        return gain;
    }

    private static Frame singleCapture(List<Frame> frames, Side side) {
        Frame capture = null;
        for (int index = 1; index < frames.size(); index++) {
            var frame = frames.get(index);
            if (frame.move().promotion() != null) return null;
            if (frame.captured() == null || frame.position().turn() == side) continue;
            if (capture != null) return null;
            capture = frame;
        }
        return capture;
    }

    public Evidence evidence() { return evidence; }
    public Optional<Estimate> estimate() { return estimate; }
    public boolean addedDefense() { return addedDefense; }
    public int step() { return step; }
    public int length() { return frames.size() - 1; }
    public ChessPosition position() { return frames.get(step).position(); }
    public Move firstMove() { return frames.get(1).move(); }
    public Move lastMove() { return frames.get(step).move(); }
    public boolean hasPrevious() { return step > 0; }
    public boolean hasNext() { return step < length(); }
    public void previous() { if (hasPrevious()) step--; }
    public void next() { if (hasNext()) step++; }
    public void seek(int ply) {
        if (ply < 0 || ply > length()) throw new IllegalArgumentException("Invalid hint ply: " + ply);
        step = ply;
    }
}
