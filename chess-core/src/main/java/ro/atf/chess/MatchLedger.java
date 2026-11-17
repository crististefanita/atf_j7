package ro.atf.chess;

import java.util.function.LongSupplier;
import java.util.function.Supplier;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;
import ro.atf.chess.MatchStore.GameOrigin;

/** Reuses the native match store for a portable library and its one-time migration. */
final class MatchLedger {
    static final String AI_PREFIX = "ai|";

    private final MatchLedgerPreferences preferences;
    private final MatchStore matches;

    static MatchLedger open(String snapshot, String legacyLocal, String legacyAi, LongSupplier now, Supplier<String> ids) {
        var preferences = snapshot == null ? new MatchLedgerPreferences() : new MatchLedgerPreferences(snapshot);
        var ledger = new MatchLedger(preferences, new MatchStore(preferences, now, ids));
        if (snapshot == null) {
            ledger.importLegacy(legacyLocal, false);
            ledger.importLegacy(legacyAi, true);
        }
        return ledger;
    }

    private MatchLedger(MatchLedgerPreferences preferences, MatchStore matches) {
        this.preferences = preferences;
        this.matches = matches;
    }

    MatchStore matches() { return matches; }
    String snapshot() { return preferences.snapshot(); }

    boolean importLegacyReview(String id, ChessMatch match, String review, String bookmark, String digest) {
        return matches.importLegacyReview(id, match, review, bookmark, digest);
    }

    enum ContinuationOutcome { APPLIED, UNAVAILABLE, NO_FREE_SLOT, SAVE_FAILED, STALE }

    record Continuation(ContinuationOutcome outcome, int slot) {
        Continuation(ContinuationOutcome outcome) { this(outcome, 0); }
    }

    static boolean canContinueArchiveReview(ChessMatch match, Snapshot cursor) {
        return match.result() != ChessMatch.Result.IN_PROGRESS && cursor.branch() == Branch.HISTORY
                && !cursor.explanationOpen() && cursor.ply() >= 0 && cursor.ply() < match.ply();
    }

    Continuation continueArchiveReview(String archiveId, ChessMatch source, Snapshot cursor, boolean automatic) {
        var entry = matches.readArchivedMatch(archiveId);
        if (entry == null || !entry.reviewed() || !source.checkpoint().equals(entry.match().checkpoint())
                || !canContinueArchiveReview(source, cursor)) {
            return new Continuation(ContinuationOutcome.UNAVAILABLE);
        }
        return continueArchived(entry, cursor.ply(), entry.blackBelow(), automatic);
    }

    int continueGame(int slot, String archiveId, int ply, boolean automatic, boolean blackBelow) {
        var entry = archiveId == null ? null : matches.readArchivedMatch(archiveId);
        if (archiveId != null && entry == null) return 0;
        var source = entry == null ? matches.readSavedMatch(slot) : entry.match();
        if (source == null || ply < 0 || ply >= source.ply()) return 0;
        if (entry != null) {
            return continueArchived(entry, ply, blackBelow, automatic).slot();
        }
        GameOrigin origin = matches.matchOrigin(slot);
        boolean manual = !automatic || origin == GameOrigin.LOCAL;
        return matches.saveMatch(slot, source.continueFrom(ply), blackBelow, origin, manual, matches.matchManualUsed(slot)) ? slot : 0;
    }

    private Continuation continueArchived(MatchStore.ArchivedMatch entry, int ply, boolean blackBelow, boolean automatic) {
        int slot = matches.freeMatchSlot();
        if (slot == 0) return new Continuation(ContinuationOutcome.NO_FREE_SLOT);
        var prefix = entry.match().continueFrom(ply);
        GameOrigin origin = matches.archiveOrigin(entry);
        boolean manual = !automatic || origin == GameOrigin.LOCAL;
        if (!matches.startMatch(slot, prefix, blackBelow, origin, manual, entry.manualUsed())) {
            return new Continuation(ContinuationOutcome.SAVE_FAILED);
        }
        return new Continuation(ContinuationOutcome.APPLIED, slot);
    }

    private void importLegacy(String checkpoint, boolean automatic) {
        if (checkpoint == null || checkpoint.startsWith(AI_PREFIX) != automatic) return;
        ChessMatch match;
        try {
            match = ChessMatch.restore(automatic ? checkpoint.substring(AI_PREFIX.length()) : checkpoint);
        } catch (IllegalArgumentException | IllegalStateException invalidLegacyMatch) {
            return;
        }
        int slot = MatchSlots.firstFree(matches::hasMatch);
        GameOrigin origin = automatic ? GameOrigin.AI : GameOrigin.LOCAL;
        if (!matches.startMatch(slot, match, match.setup().humanSide() == ChessPosition.Side.BLACK, origin, !automatic, !automatic)) {
            throw new IllegalStateException("Could not migrate the saved match");
        }
    }
}
