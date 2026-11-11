package ro.atf.chess;

import java.util.Objects;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.OpeningCatalog.Opening;
import ro.atf.chess.OpeningCatalog.Step;

public record OpeningPractice(Opening opening, Side learner, int ply) {
    public enum Phase { LEARNER, REPLY, COMPLETE }
    public enum Attempt { ON_LINE, OFF_LINE, ILLEGAL }

    public OpeningPractice {
        Objects.requireNonNull(opening);
        Objects.requireNonNull(learner);
        if (ply < 0 || ply > opening.steps().size()) throw new IllegalArgumentException("Invalid opening ply: " + ply);
    }

    public ChessPosition position() { return opening.positionAt(ply); }
    public Move previous() { return ply == 0 ? null : opening.steps().get(ply - 1).move(); }
    public Step next() { return opening.steps().get(ply); }

    public Phase phase() {
        if (ply == opening.steps().size()) return Phase.COMPLETE;
        return position().turn() == learner ? Phase.LEARNER : Phase.REPLY;
    }

    public Attempt check(Move move) {
        if (phase() != Phase.LEARNER || !position().legalMovesFrom(move.from()).contains(move)) return Attempt.ILLEGAL;
        return next().move().equals(move) ? Attempt.ON_LINE : Attempt.OFF_LINE;
    }

    public OpeningPractice play(Move move) {
        if (check(move) != Attempt.ON_LINE) throw new IllegalArgumentException("Move is outside the opening line: " + move.uci());
        return new OpeningPractice(opening, learner, ply + 1);
    }

    public OpeningPractice reply() {
        if (phase() != Phase.REPLY) throw new IllegalStateException("The line has no reply to play");
        return new OpeningPractice(opening, learner, ply + 1);
    }
}
