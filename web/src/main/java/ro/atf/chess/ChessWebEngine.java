package ro.atf.chess;

import org.teavm.jso.JSBody;
import org.teavm.jso.JSExport;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/** Exposes the portable chess engine to the browser worker. */
public final class ChessWebEngine {
    private static final WebGameReview REVIEW = new WebGameReview();
    private static final WebGameReview STUDY_REVIEW = new WebGameReview();
    private static final WebChessGame GAME = new WebChessGame();
    private static final WebQuestStudy QUESTS = new WebQuestStudy();
    private static final WebOpeningStudy OPENINGS = new WebOpeningStudy();
    private static final WebAnalysisStudy ANALYSIS = new WebAnalysisStudy();
    private static final WebMatchAnalysis MATCH_ANALYSIS = new WebMatchAnalysis();

    private ChessWebEngine() { }

    @JSExport
    public static String snapshot(String resourceNames) {
        return ChessWebParity.snapshot(resourceNames);
    }

    @JSExport
    public static String pieceArtwork() { return WebGameReview.pieceArtwork(); }

    @JSExport
    public static String studyCommand(String feature, String action, String value, int index, String saved, String option, String locale) {
        return switch (feature) {
            case "quests" -> QUESTS.command(action, value, index, saved, option);
            case "openings" -> OPENINGS.command(action, value, index, saved, option.isEmpty() ? locale : option);
            case "analysis" -> ANALYSIS.command(action, value, index, saved, option);
            default -> throw new IllegalArgumentException("Unknown study feature");
        };
    }

    @JSExport
    public static String normalizePlayerName(String name) { return PlayerProfile.normalize(name, ChessWebEngine::normalizeNfc); }

    @JSBody(params = "name", script = "return name.normalize('NFC');")
    private static native String normalizeNfc(String name);

    @JSExport
    public static String canonicalCheckpoint(String checkpoint) {
        return ChessMatch.restore(checkpoint).checkpoint();
    }

    @JSExport
    public static String openReview(String id, String checkpoint, String review, String bookmark, String digest) {
        return REVIEW.open(id, ChessMatch.restore(checkpoint), review, bookmark, digest, false, false, false, false);
    }

    @JSExport
    public static String openStudyReview(String id, String checkpoint, String review, String digest, boolean blackBelow, int ply) {
        var match = ChessMatch.restore(checkpoint);
        String bookmark = GameReviewBookmark.encode(id, match,
                new GameReviewTimeline.Snapshot(ply < 0 ? match.ply() : ply, GameReviewTimeline.Branch.HISTORY, 0), digest);
        return STUDY_REVIEW.open(id, match, review, bookmark, digest, false, blackBelow, false, false);
    }

    @JSExport
    public static String studyReviewAction(int revision, String action, int ply) {
        return STUDY_REVIEW.action(null, revision, action, ply, null);
    }

    @JSExport
    public static String studyReviewGraphSelect(int revision, double x, double width) {
        return STUDY_REVIEW.selectGraph(null, revision, x, width, null);
    }

    @JSExport
    public static String archiveReviewCheckpoint(String id) {
        return archivedMatch(id).match().checkpoint();
    }

    @JSExport
    public static boolean archiveReviewRated(String id) {
        var entry = archivedMatch(id);
        return GAME.matches().archiveOrigin(entry) == MatchStore.GameOrigin.AI && !entry.manualUsed();
    }

    @JSExport
    public static String openArchiveReview(String id, String digest) {
        var entry = archivedMatch(id);
        var record = GAME.matches().readReviewRecord(id);
        if (record.review() == null) return null;
        try {
            boolean usesAi = GAME.matches().archiveOrigin(entry) == MatchStore.GameOrigin.AI;
            return REVIEW.open(id, entry.match(), record.review(), record.bookmark(), digest, true, entry.blackBelow(),
                    usesAi, usesAi && !entry.manualUsed());
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
    public static String reviewGraphSelect(String archiveId, int revision, double x, double width) {
        return REVIEW.selectGraph(archiveId, revision, x, width, archiveId == null ? null : GAME.matches());
    }

    @JSExport
    public static String continueArchiveReview(String archiveId, int revision, boolean automatic) {
        var result = REVIEW.continueArchive(archiveId, revision, automatic, GAME.ledger());
        if (result.outcome() == MatchLedger.ContinuationOutcome.APPLIED) REVIEW.clear();
        return GAME.openContinuation(result);
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
    public static String openMatch(String route, int slot, String archiveId, String mode, String colorChoice, String level, boolean analysis) {
        return GAME.open(route, slot, archiveId, mode, colorChoice, level, analysis);
    }

    @JSExport
    public static String playMatch(int revision, String move) { return GAME.play(revision, move); }

    @JSExport
    public static String playBot(int revision) { return GAME.playBot(revision); }

    @JSExport
    public static String computeBotMove(String checkpoint) { return ChessMatch.restore(checkpoint).chooseBotMove().uci(); }

    @JSExport
    public static String openMatchAnalysis(String checkpoint, int ply, int revision) {
        return MATCH_ANALYSIS.open(checkpoint, ply, revision);
    }

    @JSExport
    public static String matchAnalysisAction(int revision, String action, int index) {
        return MATCH_ANALYSIS.action(revision, action, index);
    }

    @JSExport
    public static String applyBotMove(int revision, String move) { return GAME.applyBot(revision, move); }

    @JSExport
    public static String matchAutomation(int revision, boolean automatic) { return GAME.changeAutomation(revision, automatic); }

    @JSExport
    public static String matchDecision(int revision, String action) { return GAME.decide(revision, MatchControls.Action.valueOf(action)); }

    @JSExport
    public static String matchHistory(int revision, int ply) { return GAME.history(revision, ply); }

    @JSExport
    public static String flipMatchBoard(int revision) { return GAME.flipBoard(revision); }

    @JSExport
    public static String continueMatch(int revision, boolean automatic) { return GAME.continueGame(revision, automatic); }

    @JSExport
    public static String continueStudyMatch(String checkpoint, boolean automatic, boolean blackBelow) {
        return GAME.continueStudy(checkpoint, automatic, blackBelow);
    }

    @JSExport
    public static String analyzeReview(String checkpoint, String digest, Progress progress) {
        var match = ChessMatch.restore(checkpoint);
        // The local Web board records both sides without assigning a bot level or estimating Elo.
        var review = GameReview.analyzeLine(match, ply -> progress.onPly(ply, match.ply()));
        return GameReviewCodec.encode(review, digest);
    }

    @JSExport
    public static String analyzeRatedReview(String checkpoint, String digest, Progress progress) {
        var match = ChessMatch.restore(checkpoint);
        var review = GameReview.analyze(match, ply -> progress.onPly(ply, match.ply()));
        return GameReviewCodec.encode(review, digest);
    }

    @JSFunctor
    public interface Progress extends JSObject {
        void onPly(int completed, int total);
    }
}
