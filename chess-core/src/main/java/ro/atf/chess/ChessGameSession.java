package ro.atf.chess;

import java.util.Objects;
import ro.atf.chess.ChessPosition.Move;

/** Applies moves to a single recorded game and rejects commands for an older position. */
public final class ChessGameSession {
    public enum Outcome { APPLIED, UNAVAILABLE, STALE }

    private final boolean automatic;
    private ChessMatch match;
    private long revision;

    public ChessGameSession(ChessMatch initial) { this(initial, false); }

    public ChessGameSession(ChessMatch initial, boolean automatic) {
        match = Objects.requireNonNull(initial);
        this.automatic = automatic;
    }

    public ChessMatch match() { return match; }
    public long revision() { return revision; }
    public boolean automatic() { return automatic; }
    public boolean awaitingBot() { return MatchSlots.needsBot(match, !automatic); }

    public Outcome play(long expectedRevision, Move move) {
        if (expectedRevision != revision) return Outcome.STALE;
        if (awaitingBot() || match.result() != ChessMatch.Result.IN_PROGRESS) return Outcome.UNAVAILABLE;
        return applyLegal(move);
    }

    public Outcome playBot(long expectedRevision) {
        if (expectedRevision != revision) return Outcome.STALE;
        return awaitingBot() ? apply(match.chooseBotMove()) : Outcome.UNAVAILABLE;
    }

    public Outcome playBot(long expectedRevision, Move move) {
        if (expectedRevision != revision) return Outcome.STALE;
        return awaitingBot() ? applyLegal(move) : Outcome.UNAVAILABLE;
    }

    private Outcome applyLegal(Move move) {
        if (move == null) return Outcome.UNAVAILABLE;
        try {
            return apply(move);
        } catch (IllegalArgumentException illegal) {
            return Outcome.UNAVAILABLE;
        }
    }

    private Outcome apply(Move move) {
        match = match.play(move);
        revision++;
        return Outcome.APPLIED;
    }
}
