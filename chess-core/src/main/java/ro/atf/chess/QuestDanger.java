package ro.atf.chess;

import ro.atf.chess.ChessPosition.Outcome;

/** Evidence for an optional mark on a single puzzle replay position. */
public record QuestDanger(Kind kind, int square, int retainedCp) {
    public enum Kind { MATE, CAPTURE }

    static QuestDanger at(QuestPayoff payoff, int ply) {
        if (ply == 0) return null;
        var frame = payoff.at(ply);
        var position = frame.position();
        var solver = payoff.at(0).position().turn();
        if (position.turn() == solver) return null;

        var replies = position.legalMoves();
        if (replies.isEmpty()) {
            return position.isCheck() ? new QuestDanger(Kind.MATE, position.explainMate().king(), 0) : null;
        }
        if (frame.capture() == null) return null;

        int baseline = Math.max(0, payoff.at(ply - 1).materialChange());
        int retained = frame.materialChange() - baseline;
        if (retained <= 0) return null;
        int initial = QuestPayoff.balance(payoff.at(0).position(), solver);
        for (var reply : replies) {
            var answered = position.play(reply);
            var outcome = answered.outcome();
            if (outcome == Outcome.CHECKMATE || outcome == Outcome.STALEMATE) return null;
            retained = Math.min(retained, QuestPayoff.balance(answered, solver) - initial - baseline);
            if (retained <= 0) return null;
        }
        return new QuestDanger(Kind.CAPTURE, frame.capture().square(), retained);
    }
}
