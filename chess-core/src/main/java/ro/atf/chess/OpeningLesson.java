package ro.atf.chess;

import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.OpeningPractice.Phase;

/** Tracks the position on screen independently of the current practice position. */
public record OpeningLesson(OpeningPractice practice, int shownPly, boolean replyPaused) {
    public OpeningLesson {
        if (shownPly < 0 || shownPly > practice.ply() || replyPaused && practice.phase() != Phase.REPLY) {
            throw new IllegalArgumentException("Invalid opening lesson state");
        }
    }

    public OpeningLesson(OpeningPractice practice) { this(practice, practice.ply(), false); }

    public boolean reviewing() { return shownPly < practice.ply(); }
    public boolean canPlay() { return !reviewing() && practice.phase() == Phase.LEARNER; }
    public boolean canAutoReply() { return !reviewing() && !replyPaused && practice.phase() == Phase.REPLY; }

    public OpeningLesson show(int ply) { return new OpeningLesson(practice, ply, replyPaused); }

    public OpeningLesson forward() {
        if (reviewing()) {
            int next = shownPly + 1;
            boolean pause = replyPaused || next == practice.ply() && practice.phase() == Phase.REPLY;
            return new OpeningLesson(practice, next, pause);
        }
        var next = switch (practice.phase()) {
            case LEARNER -> practice.play(practice.next().move());
            case REPLY -> practice.reply();
            case COMPLETE -> practice;
        };
        return new OpeningLesson(next, next.ply(), next.phase() == Phase.REPLY);
    }

    public OpeningLesson replay() {
        return new OpeningLesson(new OpeningPractice(practice.opening(), practice.learner(), shownPly));
    }

    public OpeningLesson resume() { return new OpeningLesson(practice); }

    public OpeningLesson play(Move move) {
        if (!canPlay()) throw new IllegalStateException("The shown position is unavailable for practice");
        return new OpeningLesson(practice.play(move));
    }

    public OpeningLesson reply() {
        if (!canAutoReply()) throw new IllegalStateException("No automatic opening reply is pending");
        return new OpeningLesson(practice.reply());
    }
}
