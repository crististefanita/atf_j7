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

    /** The highlighted move and the pieces involved, before applying that move. */
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
        int before = MatchMaterial.balance(played.position(0), side);
        int playedAfter = MatchMaterial.balance(played.position(played.length()), side);
        int recommendedAfter = MatchMaterial.balance(recommended.position(recommended.length()), side);
        int loss = Math.min(before, recommendedAfter) - playedAfter;
        if (loss <= 0) return Optional.empty();
        for (int index = played.length() - 1; index > 0; index--) {
            if (played.position(index).turn() == side) continue;
            var capture = played.evidenceAt(index);
            if (capture.captured() != null) return Optional.of(explained(turn, Reason.LOSES_MATERIAL, loss * 100, capture));
        }
        return Optional.empty();
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

        boolean mates(Side winner) {
            return ending == (winner == Side.WHITE ? Result.WHITE_WINS : Result.BLACK_WINS);
        }

        MoveEvidence evidenceAt(int index) {
            var before = position(index);
            Move move = moves.get(index);
            var victim = MatchMaterial.victim(before, move);
            return new MoveEvidence(move, before.pieceAt(move.from()).kind(), victim == null ? null : victim.kind());
        }
    }
}
