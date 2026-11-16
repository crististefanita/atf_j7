package ro.atf.chess;

import java.util.Objects;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

/** Commands for reviewing a recorded game. The source game stays immutable. */
public final class GameReviewSession {
    public enum Action {
        PREVIOUS, NEXT, SEEK, NEXT_KEY, TOGGLE_BEST, OPEN_EXPLANATION, CLOSE_EXPLANATION,
        SELECT_PLAYED, SELECT_RECOMMENDED, LINE_PREVIOUS, LINE_NEXT
    }

    public enum Outcome { APPLIED, UNAVAILABLE, STALE }

    private final GameReviewTimeline timeline;
    private long revision;

    public GameReviewSession(ChessMatch match, GameReview review, Snapshot bookmark) {
        timeline = new GameReviewTimeline(match, review);
        if (bookmark != null) timeline.restore(bookmark);
    }

    /** Display adapters read the timeline and send every change through dispatch. */
    public GameReviewTimeline timeline() { return timeline; }
    public long revision() { return revision; }
    public Snapshot snapshot() { return timeline.snapshot(); }
    public ChessPosition position() { return timeline.position(); }
    public Move lastMove() { return timeline.lastMove(); }

    public Outcome dispatch(long expectedRevision, Action action, int ply) {
        if (expectedRevision != revision) return Outcome.STALE;
        Objects.requireNonNull(action);
        boolean changed = switch (action) {
            case PREVIOUS -> timeline.branch() == Branch.HISTORY ? timeline.previous() : timeline.stepBackward();
            case NEXT -> timeline.branch() == Branch.HISTORY ? timeline.next() : timeline.stepForward();
            case SEEK -> seek(ply);
            case NEXT_KEY -> seek(timeline.nextKeyMomentPly().orElse(-1));
            case TOGGLE_BEST -> select(timeline.showingBestLine() ? Branch.HISTORY : Branch.RECOMMENDED);
            case OPEN_EXPLANATION -> openExplanation();
            case CLOSE_EXPLANATION -> closeExplanation();
            case SELECT_PLAYED -> select(Branch.PLAYED);
            case SELECT_RECOMMENDED -> select(Branch.RECOMMENDED);
            case LINE_PREVIOUS -> timeline.stepBackward();
            case LINE_NEXT -> timeline.stepForward();
        };
        if (!changed) return Outcome.UNAVAILABLE;
        revision++;
        return Outcome.APPLIED;
    }

    private boolean seek(int ply) {
        if (ply < 0 || ply > timeline.totalPlies()) return false;
        if (ply == timeline.ply() && !timeline.atEntry() && timeline.branch() == Branch.HISTORY && !timeline.explanationOpen()) return false;
        timeline.seek(ply);
        return true;
    }

    private boolean select(Branch branch) {
        return timeline.branch() != branch && timeline.showBranch(branch);
    }

    private boolean openExplanation() {
        if (timeline.explanationOpen()) return false;
        timeline.setExplanationOpen(true);
        return timeline.explanationOpen();
    }

    private boolean closeExplanation() {
        if (!timeline.explanationOpen()) return false;
        timeline.returnFromExplanation();
        return true;
    }
}
