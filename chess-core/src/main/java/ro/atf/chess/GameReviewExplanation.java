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
    public enum Reason { ALLOWS_MATE, MISSES_MATE, LOSES_MATERIAL, MISSES_MATERIAL, EVALUATION }

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
            var material = materialDifference(turn, played, recommended);
            if (material.isPresent()) return material.orElseThrow();
        }
        return new GameReviewExplanation(Reason.EVALUATION, turn.side(), turn.loss(), turn.depth(), 0, Optional.empty());
    }

    private static GameReviewExplanation explained(Turn turn, Reason reason, int materialCp, MoveEvidence move) {
        return new GameReviewExplanation(reason, turn.side(), turn.loss(), turn.depth(), materialCp, Optional.of(move));
    }

    private static Optional<GameReviewExplanation> materialDifference(Turn turn, Line played, Line recommended) {
        if (played.length() < 2 || played.length() != recommended.length() || played.ending() != Result.IN_PROGRESS
                || recommended.ending() != Result.IN_PROGRESS) return Optional.empty();
        Side side = turn.side();
        int initial = played.materialAt(0, side);
        int playedBalance = played.materialAt(played.length(), side);
        int recommendedBalance = recommended.materialAt(recommended.length(), side);
        int lossReference = Math.min(initial, recommendedBalance);
        int loss = lossReference - playedBalance;
        if (loss > 0) {
            return decisiveCapture(played, side.other(), -lossReference, loss)
                    .map(capture -> explained(turn, Reason.LOSES_MATERIAL, loss * 100, capture));
        }

        int gainReference = Math.max(initial, playedBalance);
        int gain = recommendedBalance - gainReference;
        if (gain <= 0 || !recommended.sustainsMaterialAgainstNextReply(side, recommendedBalance)) return Optional.empty();
        return decisiveCapture(recommended, side, gainReference, gain)
                .map(capture -> explained(turn, Reason.MISSES_MATERIAL, gain * 100, capture));
    }

    private static Optional<MoveEvidence> decisiveCapture(Line line, Side beneficiary, int reference, int gain) {
        int sustainedGain = gain;
        int largestGain = 0;
        MoveEvidence decisive = null;
        for (int index = line.length() - 1; index >= 0; index--) {
            int gainBefore = Math.max(0, line.materialAt(index, beneficiary) - reference);
            int retainedGain = sustainedGain - gainBefore;
            sustainedGain = Math.min(sustainedGain, gainBefore);
            if (retainedGain <= 0 || retainedGain < largestGain || line.position(index).turn() != beneficiary) continue;
            var evidence = line.evidenceAt(index);
            decisive = retainedGain > largestGain && evidence.captured() != null && evidence.move().promotion() == null
                    ? evidence : null;
            largestGain = retainedGain;
        }
        return Optional.ofNullable(decisive);
    }

    record Line(List<Move> moves, List<ChessPosition> positions, ChessMatch finalMatch) {
        static final Line UNAVAILABLE = new Line(List.of(), List.of(), null);

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
            return new Line(moves, List.copyOf(positions), branch);
        }

        boolean available() { return !moves.isEmpty(); }
        int length() { return moves.size(); }
        ChessPosition position(int step) { return positions.get(step); }
        int materialAt(int step, Side side) { return MatchMaterial.balance(position(step), side); }
        Result ending() { return finalMatch.result(); }

        boolean sustainsMaterialAgainstNextReply(Side side, int balance) {
            var end = position(length());
            if (end.turn() == side) return true;
            for (var reply : end.legalMoves()) {
                var after = finalMatch.play(reply);
                if (MatchMaterial.balance(after.position(), side) < balance
                        || after.result() != Result.IN_PROGRESS && after.result() != win(side)) return false;
            }
            return true;
        }

        boolean mates(Side winner) {
            return ending() == win(winner);
        }

        private static Result win(Side side) { return side == Side.WHITE ? Result.WHITE_WINS : Result.BLACK_WINS; }

        MoveEvidence evidenceAt(int index) {
            var before = position(index);
            Move move = moves.get(index);
            var victim = MatchMaterial.victim(before, move);
            var moved = position(index + 1).pieceAt(move.to());
            return new MoveEvidence(move, moved.kind(), victim == null ? null : victim.kind());
        }
    }
}
