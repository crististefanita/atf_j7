package ro.atf.chess;

import java.util.function.LongSupplier;
import java.util.function.Supplier;

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

    int continueGame(int slot, String archiveId, int ply, boolean automatic) {
        var entry = archiveId == null ? null : matches.readArchivedMatch(archiveId);
        if (archiveId != null && entry == null) return 0;
        var source = entry == null ? matches.readSavedMatch(slot) : entry.match();
        if (source == null || ply < 0 || ply >= source.ply()) return 0;
        var prefix = source.continueFrom(ply);
        if (entry != null) {
            int free = matches.freeMatchSlot();
            return free > 0 && matches.startMatch(free, prefix, entry.blackBelow(), !automatic, !automatic) ? free : 0;
        }
        return matches.saveMatch(slot, prefix, matches.matchBlackBelow(slot), !automatic, !automatic) ? slot : 0;
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
        if (!matches.startMatch(slot, match, match.setup().humanSide() == ChessPosition.Side.BLACK, !automatic, !automatic)) {
            throw new IllegalStateException("Could not migrate the saved match");
        }
    }
}
