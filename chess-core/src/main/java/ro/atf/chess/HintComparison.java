package ro.atf.chess;

import java.util.Optional;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;

/** Two legal principal variations scored from the same side at the same completed depth. */
public record HintComparison(TacticalAnalysis.Variation stronger, TacticalAnalysis.Variation alternative, int depth,
                             Optional<DecisiveMove> decisive) {
    public enum Reason { MATE, CAPTURE }

    /** Identifies the branch and the ply that supplies the explanation. */
    public record DecisiveMove(boolean inStronger, int ply, Move move, Kind piece, Reason reason) { }

    static HintComparison of(ChessPosition position, TacticalAnalysis.Variation first, TacticalAnalysis.Variation second, int depth) {
        var stronger = first.score() >= second.score() ? first : second;
        var alternative = stronger == first ? second : first;
        var decisive = stronger.score() == alternative.score() ? Optional.<DecisiveMove>empty()
                : explain(position, stronger, alternative);
        return new HintComparison(stronger, alternative, depth, decisive);
    }

    private static Optional<DecisiveMove> explain(ChessPosition start, TacticalAnalysis.Variation stronger,
                                                  TacticalAnalysis.Variation alternative) {
        if (stronger.signedMatePlies() > 0 && alternative.signedMatePlies() <= 0) {
            return matingMove(start, stronger, true);
        }
        if (alternative.signedMatePlies() < 0 && stronger.signedMatePlies() >= 0) {
            return matingMove(start, alternative, false);
        }
        if (stronger.ending() != TacticalAnalysis.Ending.HORIZON || alternative.ending() != TacticalAnalysis.Ending.HORIZON
                || stronger.line().size() != alternative.line().size()) return Optional.empty();
        if (alternative.materialChange() < Math.min(0, stronger.materialChange())) {
            return singleCapture(start, alternative, false);
        }
        if (stronger.materialChange() > Math.max(0, alternative.materialChange())) {
            return singleCapture(start, stronger, true);
        }
        return Optional.empty();
    }

    private static Optional<DecisiveMove> singleCapture(ChessPosition start, TacticalAnalysis.Variation line, boolean inStronger) {
        var position = start;
        DecisiveMove decisive = null;
        for (int index = 0; index < line.line().size(); index++) {
            Move move = line.line().get(index);
            if (move.promotion() != null) return Optional.empty();
            var captured = MatchMaterial.victim(position, move);
            if ((position.turn() == start.turn()) == inStronger && captured != null) {
                if (decisive != null) return Optional.empty();
                decisive = new DecisiveMove(inStronger, index + 1, move, captured.kind(), Reason.CAPTURE);
            }
            position = position.play(move);
        }
        return Optional.ofNullable(decisive);
    }

    private static Optional<DecisiveMove> matingMove(ChessPosition start, TacticalAnalysis.Variation line, boolean inStronger) {
        var position = start;
        for (Move move : line.line()) position = position.play(move);
        Move last = line.line().get(line.line().size() - 1);
        return position.isCheckmate()
                ? Optional.of(new DecisiveMove(inStronger, line.line().size(), last, position.pieceAt(last.to()).kind(), Reason.MATE))
                : Optional.empty();
    }
}
