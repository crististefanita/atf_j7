package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReviewSession.Action;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;
import ro.atf.chess.MatchLedger.Continuation;
import ro.atf.chess.MatchLedger.ContinuationOutcome;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ArchivedReviewContinuationTest {
    private static final String LOCAL = "1|f2f3 e7e5 g2g4 d8h4";
    private static final String AI = "3|BLACK|ADVANCED|0|f2f4 e7e6 g2g4 d8h4";
    private static final GameReview LOCAL_REVIEW = GameReview.analyzeLine(ChessMatch.restore(LOCAL), ignored -> { });
    private static final GameReview AI_REVIEW = GameReview.analyzeLine(ChessMatch.restore(AI), ignored -> { });

    @Test void acceptsOnlyAnEarlierRecordedHistoryPosition(Checks check) {
        var source = ChessMatch.restore(LOCAL);
        var session = new GameReviewSession(source, LOCAL_REVIEW, null);
        check.same("The final recorded position cannot continue an archived result", false, eligible(source, session));
        session.dispatch(session.revision(), Action.SEEK, 3);
        check.same("An actual earlier recorded position can continue", true, eligible(source, session));
        session.dispatch(session.revision(), Action.OPEN_EXPLANATION, 0);
        check.same("An open explanation cannot trigger a continuation behind the panel", false, eligible(source, session));
        session.dispatch(session.revision(), Action.SELECT_PLAYED, 0);
        session.dispatch(session.revision(), Action.LINE_NEXT, 0);
        check.same("The played variation cannot continue even after replaying the real move", false, eligible(source, session));
        session.dispatch(session.revision(), Action.SELECT_RECOMMENDED, 0);
        check.same("The recommended variation also cannot continue", false, eligible(source, session));
        session.dispatch(session.revision(), Action.CLOSE_EXPLANATION, 0);
        check.same("Closing the explanation restores the actual recorded cursor and eligibility",
                List.of(history(3), true), List.of(session.snapshot(), eligible(source, session)));
        check.same("Earlier pre-move history remains eligible while invalid and unfinished positions do not",
                List.of(true, false, false), List.of(MatchLedger.canContinueArchiveReview(source, history(0)),
                        MatchLedger.canContinueArchiveReview(source, history(-1)),
                        MatchLedger.canContinueArchiveReview(source.continueFrom(3), history(2))));
    }

    @Test void forksASeparateLocalGameWithoutChangingTheArchivedAnalysisOrCursor(Checks check) {
        var original = archive(false);
        var source = original.match();
        var review = new GameReviewSession(source, LOCAL_REVIEW, null);
        review.dispatch(review.revision(), Action.SEEK, 2);
        var chosen = review.snapshot();
        var saved = storeReview(original, LOCAL_REVIEW, chosen);
        var store = original.ledger().matches();

        var created = original.ledger().continueArchiveReview(original.id(), source, chosen, true);
        check.same("Archived Review creates a separate available slot", new Continuation(ContinuationOutcome.APPLIED, 2), created);
        var branch = store.readSavedMatch(created.slot());
        check.same("The new game starts at the exact recorded position", List.of("1|f2f3 e7e5", true),
                List.of(branch.checkpoint(), review.position().sameForRepetition(branch.position())));
        check.same("The fork has a new ID and local play cannot enable the bot", List.of(true, true, false),
                List.of(!original.id().equals(store.archivedId(created.slot())), store.matchManualAi(created.slot()),
                        new ChessGameSession(branch, !store.matchManualAi(created.slot())).awaitingBot()));
        check.same("Playing an alternate move updates only the fork", true,
                store.saveMatch(created.slot(), branch.play(Move.fromUci("d2d4")), false, true, true));

        var reopened = reopen(original.ledger()).matches();
        check.same("The new game and original result restore independently", List.of("1|f2f3 e7e5 d2d4", LOCAL, saved),
                List.of(reopened.readSavedMatch(created.slot()).checkpoint(), reopened.readArchivedMatch(original.id()).match().checkpoint(),
                        reopened.readReviewRecord(original.id())));
        check.same("The original review still points to the selected historical move", chosen,
                reopened.readReviewBookmark(original.id(), source));
    }

    @Test void refusesMissingCachesForeignCheckpointsAndAFullSavedLibraryWithoutWriting(Checks check) {
        var original = archive(false);
        var ledger = original.ledger();
        var store = ledger.matches();
        var source = original.match();
        String empty = ledger.snapshot();
        check.same("A continuation requires an existing archived analysis", new Continuation(ContinuationOutcome.UNAVAILABLE, 0),
                ledger.continueArchiveReview(original.id(), source, history(2), false));
        check.same("Refusing a missing cache leaves the library unchanged", empty, ledger.snapshot());
        storeReview(original, LOCAL_REVIEW, history(2));
        String archived = ledger.snapshot();
        check.same("A different checkpoint cannot continue the selected result", new Continuation(ContinuationOutcome.UNAVAILABLE, 0),
                ledger.continueArchiveReview(original.id(), ChessMatch.restore(AI), history(2), false));
        check.same("A missing source ID or hypothetical variation cannot create a fork", List.of(
                new Continuation(ContinuationOutcome.UNAVAILABLE, 0), new Continuation(ContinuationOutcome.UNAVAILABLE, 0)),
                List.of(ledger.continueArchiveReview("other-archive", source, history(2), false),
                        ledger.continueArchiveReview(original.id(), source, new Snapshot(3, Branch.PLAYED, 1, true), false)));
        check.same("Rejected requests keep the source analysis and exact library bytes", archived, ledger.snapshot());

        for (int count = 0; count < MatchSlots.LIMIT; count++) {
            store.startMatch(store.slotForNewMatch(), ChessMatch.start(), false, true, true);
        }
        String full = ledger.snapshot();
        check.same("A full Saved library refuses an archived-review fork", new Continuation(ContinuationOutcome.NO_FREE_SLOT, 0),
                ledger.continueArchiveReview(original.id(), source, history(2), false));
        check.same("A refused fork leaves all active and archived records byte-identical", full, ledger.snapshot());
    }

    @Test void optionalAiReplyBelongsOnlyToTheNewResumedGame(Checks check) {
        var original = archive(true);
        var source = original.match();
        var cursor = history(2);
        var saved = storeReview(original, AI_REVIEW, cursor);
        var created = original.ledger().continueArchiveReview(original.id(), source, cursor, true);
        check.same("Continuing the archived AI game creates a second slot", new Continuation(ContinuationOutcome.APPLIED, 2), created);
        var ledger = reopen(original.ledger());
        var store = ledger.matches();
        var staged = store.readSavedMatch(created.slot());
        var active = new ChessGameSession(staged, !store.matchManualAi(created.slot()));
        check.same("The new slot restores Advanced, Black's seat and the pending computer turn",
                List.of(source.setup(), source.continueFrom(2).checkpoint(), true),
                List.of(staged.setup(), staged.checkpoint(), active.awaitingBot()));
        check.same("The pending computer applies one move to the new game", ChessGameSession.Outcome.APPLIED, active.playBot(active.revision()));
        check.same("The computer move is stored only in the new slot", true,
                store.saveMatch(created.slot(), active.match(), true, false, false));
        var reopened = reopen(ledger).matches();
        var finishedReply = new ChessGameSession(reopened.readSavedMatch(created.slot()), true);
        check.same("Restoring after the committed reply cannot apply a second computer move", ChessGameSession.Outcome.UNAVAILABLE,
                finishedReply.playBot(finishedReply.revision()));
        check.same("The archive remains untouched while the fork advances by exactly one ply", List.of(3, AI, saved),
                List.of(finishedReply.match().ply(), reopened.readArchivedMatch(original.id()).match().checkpoint(),
                        reopened.readReviewRecord(original.id())));
    }

    private static boolean eligible(ChessMatch source, GameReviewSession session) {
        return MatchLedger.canContinueArchiveReview(source, session.snapshot());
    }

    private static Snapshot history(int ply) { return new Snapshot(ply, Branch.HISTORY, 0, false); }

    private static Source archive(boolean ai) {
        var ids = new AtomicInteger();
        var ledger = MatchLedger.open(null, ai ? null : LOCAL, ai ? "ai|" + AI : null,
                () -> 1_794_830_400_000L, () -> "game-" + ids.incrementAndGet());
        var archive = ledger.matches().archivedMatches().getFirst();
        return new Source(ledger, archive.id(), archive.match());
    }

    private static MatchStore.ReviewRecord storeReview(Source source, GameReview review, Snapshot bookmark) {
        var store = source.ledger().matches();
        String digest = GameReviewCodec.checkpointDigest(source.match());
        if (!store.saveReviewRecord(source.id(), source.match(), GameReviewCodec.encode(review, digest), digest)
                || !store.saveReviewBookmark(source.id(), source.match(), bookmark, digest)) {
            throw new IllegalStateException("Could not prepare the archived review");
        }
        return store.readReviewRecord(source.id());
    }

    private static MatchLedger reopen(MatchLedger source) {
        return MatchLedger.open(source.snapshot(), null, null, () -> 1_794_830_400_000L, () -> "unexpected-game-id");
    }

    private record Source(MatchLedger ledger, String id, ChessMatch match) { }
}
