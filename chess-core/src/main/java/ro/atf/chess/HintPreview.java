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

    private record Frame(ChessPosition position, Move move, Kind piece, Kind captured) { }

    private final List<Frame> frames;
    private final Evidence evidence;
    private int step;

    private HintPreview(List<Frame> frames, Evidence evidence) {
        this.frames = List.copyOf(frames);
        this.evidence = evidence;
    }

    public static Optional<HintPreview> open(ChessPosition root, TacticalAnalysis.Variation line) {
        if (line.line().isEmpty()) return Optional.empty();
        var board = root;
        var frames = new ArrayList<Frame>();
        frames.add(new Frame(root, null, null, null));
        for (Move move : line.line()) {
            try {
                var next = board.play(move);
                var victim = MatchMaterial.victim(board, move);
                frames.add(new Frame(next, move, next.pieceAt(move.to()).kind(), victim == null ? null : victim.kind()));
                board = next;
            } catch (IllegalArgumentException invalid) {
                return Optional.empty();
            }
        }
        return Optional.of(new HintPreview(frames, evidence(root, board, frames, line)));
    }

    private static Evidence evidence(ChessPosition root, ChessPosition end, List<Frame> frames, TacticalAnalysis.Variation line) {
        Side side = root.turn();
        if (line.ending() == TacticalAnalysis.Ending.CHECKMATE && end.isCheckmate()) {
            var mate = frames.get(frames.size() - 1);
            var reason = end.turn() == side ? Reason.CHECKMATE_AGAINST : Reason.CHECKMATE;
            return new Evidence(reason, mate.move(), mate.piece(), mate.captured(), 0);
        }
        if (line.ending() == TacticalAnalysis.Ending.STALEMATE && !end.isCheck() && end.legalMoves().isEmpty()) {
            return new Evidence(Reason.STALEMATE, null, null, null, 0);
        }
        int gain = MatchMaterial.balance(end, side) - MatchMaterial.balance(root, side);
        if (line.ending() == TacticalAnalysis.Ending.HORIZON && gain > 0) {
            var capture = singleCapture(frames, side);
            if (capture != null) return new Evidence(Reason.CAPTURE, capture.move(), capture.piece(), capture.captured(), gain);
        }
        return new Evidence(Reason.ESTIMATE, null, null, null, 0);
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
