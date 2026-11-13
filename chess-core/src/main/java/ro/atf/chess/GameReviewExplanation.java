package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview.Turn;

/** Evidence for the selected move, derived by replaying the cached alternatives. */
public record GameReviewExplanation(Reason reason, Side side, int loss, int depth,
                                    int materialCp, Optional<MoveEvidence> evidence) {
    public enum Reason { ALLOWS_MATE, MISSES_MATE, LOSES_MATERIAL, EVALUATION }

    /** The highlighted move, its resulting piece, and the piece it captured. */
    public record MoveEvidence(Move move, Kind piece, Kind captured) { }

    static GameReviewExplanation compare(Turn turn, Line played, Line recommended) {
        if (turn.loss() > 0 && played.available() && recommended.available()) {
            Side side = turn.side();
            if (played.mates(side.other()) && !recommended.mates(side.other())) {
                return explained(turn, Reason.ALLOWS_MATE, 0, played.evidenceAt(played.length() - 1));
            }
            if (recommended.mates(side) && !played.mates(side)) {
                return explained(turn, Reason.MISSES_MATE, 0, recommended.evidenceAt(recommended.length() - 1));
            }
            var materialLoss = materialLoss(turn, played, recommended);
            if (materialLoss.isPresent()) return materialLoss.orElseThrow();
        }
        return new GameReviewExplanation(Reason.EVALUATION, turn.side(), turn.loss(), turn.depth(), 0, Optional.empty());
    }

    private static GameReviewExplanation explained(Turn turn, Reason reason, int materialCp, MoveEvidence move) {
        return new GameReviewExplanation(reason, turn.side(), turn.loss(), turn.depth(), materialCp, Optional.of(move));
    }

    private static Optional<GameReviewExplanation> materialLoss(Turn turn, Line played, Line recommended) {
        if (played.length() != recommended.length() || played.ending() != Result.IN_PROGRESS
                || recommended.ending() != Result.IN_PROGRESS) return Optional.empty();
        Side side = turn.side();
        int reference = Math.min(played.materialAt(0, side), recommended.materialAt(recommended.length(), side));
        int loss = reference - played.materialAt(played.length(), side);
        if (loss <= 0) return Optional.empty();
        return decisiveCapture(played, side, reference, loss)
                .map(capture -> explained(turn, Reason.LOSES_MATERIAL, loss * 100, capture));
    }

    private static Optional<MoveEvidence> decisiveCapture(Line played, Side side, int reference, int loss) {
        int sustainedDeficit = loss;
        int largestLoss = 0;
        MoveEvidence decisive = null;
        for (int index = played.length() - 1; index > 0; index--) {
            int deficitBefore = Math.max(0, reference - played.materialAt(index, side));
            int retainedLoss = sustainedDeficit - deficitBefore;
            sustainedDeficit = Math.min(sustainedDeficit, deficitBefore);
            if (retainedLoss <= 0 || retainedLoss < largestLoss || played.position(index).turn() == side) continue;
            var evidence = played.evidenceAt(index);
            // Do not attribute a shared or promotion-driven loss to a single capture.
            decisive = retainedLoss > largestLoss && evidence.captured() != null && evidence.move().promotion() == null
                    ? evidence : null;
            largestLoss = retainedLoss;
        }
        return Optional.ofNullable(decisive);
    }

    record Line(List<Move> moves, List<ChessPosition> positions, Result ending) {
        static final Line UNAVAILABLE = new Line(List.of(), List.of(), Result.IN_PROGRESS);

        static Line replay(ChessMatch root, List<Move> moves) {
            if (moves.isEmpty()) return UNAVAILABLE;
            var positions = new ArrayList<ChessPosition>(moves.size() + 1);
            positions.add(root.position());
            var branch = root;
            try {
                for (Move move : moves) {
                    branch = branch.play(move);
                    positions.add(branch.position());
                }
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                return UNAVAILABLE;
            }
            return new Line(moves, List.copyOf(positions), branch.result());
        }

        boolean available() { return !moves.isEmpty(); }
        int length() { return moves.size(); }
        ChessPosition position(int step) { return positions.get(step); }
        int materialAt(int step, Side side) { return MatchMaterial.balance(position(step), side); }

        boolean mates(Side winner) {
            return ending == (winner == Side.WHITE ? Result.WHITE_WINS : Result.BLACK_WINS);
        }

        MoveEvidence evidenceAt(int index) {
            var before = position(index);
            Move move = moves.get(index);
            var victim = MatchMaterial.victim(before, move);
            var moved = position(index + 1).pieceAt(move.to());
            return new MoveEvidence(move, moved.kind(), victim == null ? null : victim.kind());
        }
    }
}
