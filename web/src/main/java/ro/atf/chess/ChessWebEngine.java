package ro.atf.chess;

import org.teavm.jso.JSExport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Exposes the portable chess engine to the browser worker. */
public final class ChessWebEngine {
    private static final WebGameReview REVIEW = new WebGameReview();
    private static final WebChessGame GAME = new WebChessGame();

    private ChessWebEngine() { }

    @JSExport
    public static String snapshot(String resourceNames) {
        return ChessWebParity.snapshot(resourceNames);
    }

    @JSExport
    public static String canonicalCheckpoint(String checkpoint) {
        return ChessMatch.restore(checkpoint).checkpoint();
    }

    @JSExport
    public static String openReview(String id, String checkpoint, String review, String bookmark, String digest) {
        return REVIEW.open(id, ChessMatch.restore(checkpoint), review, bookmark, digest, false, false);
    }

    @JSExport
    public static String archiveReviewCheckpoint(String id) {
        return archivedMatch(id).match().checkpoint();
    }

    @JSExport
    public static String openArchiveReview(String id, String digest) {
        var entry = archivedMatch(id);
        var record = GAME.matches().readReviewRecord(id);
        if (record.review() == null) return null;
        try {
            return REVIEW.open(id, entry.match(), record.review(), record.bookmark(), digest, true, entry.blackBelow());
        } catch (IllegalArgumentException invalidCache) {
            return null;
        }
    }

    @JSExport
    public static String saveArchiveReview(String id, String checkpoint, String review, String digest) {
        if (!GAME.matches().saveReviewRecord(id, ChessMatch.restore(checkpoint), review, digest)) {
            throw new IllegalArgumentException("The analysis cannot be saved for this archived match");
        }
        return openArchiveReview(id, digest);
    }

    @JSExport
    public static String importLegacyReview(String id, String checkpoint, String review, String bookmark, String digest) {
        return GAME.importLegacyReview(id, checkpoint == null ? null : ChessMatch.restore(checkpoint), review, bookmark, digest);
    }

    @JSExport
    public static String reviewAction(String archiveId, int revision, String action, int ply) {
        return REVIEW.action(archiveId, revision, action, ply, archiveId == null ? null : GAME.matches());
    }

    @JSExport
    public static String ledgerState() { return GAME.state(null); }

    private static MatchStore.ArchivedMatch archivedMatch(String id) {
        var entry = GAME.matches().readArchivedMatch(id);
        if (entry == null) throw new IllegalArgumentException("The archived match is unavailable");
        return entry;
    }

    @JSExport
    public static String openLedger(String ledger, String legacyLocal, String legacyAi) {
        REVIEW.clear();
        return GAME.openLedger(ledger, legacyLocal, legacyAi);
    }

    @JSExport
    public static String openMatch(String route, int slot, String archiveId, String mode, String humanSide, String level) {
        return GAME.open(route, slot, archiveId, mode, humanSide, level);
    }

    @JSExport
    public static String playMatch(int revision, String move) { return GAME.play(revision, move); }

    @JSExport
    public static String playBot(int revision) { return GAME.playBot(revision); }

    @JSExport
    public static String matchHistory(int revision, int ply) { return GAME.history(revision, ply); }

    @JSExport
    public static String continueMatch(int revision, boolean automatic) { return GAME.continueGame(revision, automatic); }

    @JSExport
    public static String analyzeReview(String checkpoint, String digest, Progress progress) {
        var match = ChessMatch.restore(checkpoint);
        // The local Web board records both sides without assigning a bot level or estimating Elo.
        var review = GameReview.analyzeLine(match, ply -> progress.onPly(ply, match.ply()));
        return GameReviewCodec.encode(review, digest);
    }

    @JSFunctor
    public interface Progress extends JSObject {
        void onPly(int completed, int total);
    }
}
