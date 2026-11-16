package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.GameReview.Grade;
import ro.atf.chess.GameReviewSession.Action;
import ro.atf.chess.GameReviewSession.Outcome;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class GameReviewSessionTest {
    private static final String ARCHIVE_ID = "fools-mate";
    private static final String FOOLS_MATE = "1|f2f3 e7e5 g2g4 d8h4";
    private static final Move RECORDED = Move.fromUci("g2g4");
    private static final Move MATE = Move.fromUci("d8h4");
    private static final Move RECOMMENDED = Move.fromUci("d2d4");

    @Test void staleCommandsAndUnavailableActionsPreserveThePublishedState(Checks check) {
        var source = ChessMatch.restore(FOOLS_MATE);
        var session = new GameReviewSession(source, review(source), null);
        long entry = session.revision();
        check.same("Game Review opens on the last recorded move", new Snapshot(4, Branch.HISTORY, 0), session.snapshot());
        check.same("Next cannot move past the archived result", Outcome.UNAVAILABLE, session.dispatch(entry, Action.NEXT, 0));
        check.same("Unavailable navigation keeps the current revision", entry, session.revision());

        check.same("Previous selects the blunder in recorded history", Outcome.APPLIED, session.dispatch(entry, Action.PREVIOUS, 0));
        var blunder = new Snapshot(3, Branch.HISTORY, 0);
        check.same("Previous changes the history cursor and exposes its recorded move",
                List.of(blunder, RECORDED), List.of(session.snapshot(), session.lastMove()));
        long selected = session.revision();
        check.same("A successful command advances the revision once", entry + 1, selected);
        check.same("Retrying the same request with its old revision is stale", Outcome.STALE,
                session.dispatch(entry, Action.PREVIOUS, 0));
        check.same("An old request cannot replace the current cursor or revision", List.of(blunder, selected),
                List.of(session.snapshot(), session.revision()));

        check.same("A seek beyond the source game is unavailable", Outcome.UNAVAILABLE,
                session.dispatch(selected, Action.SEEK, 5));
        check.same("Invalid navigation preserves the recorded board", true,
                session.position().sameForRepetition(source.positionAt(3)));
        check.same("The same revision can open the explanation after a rejected action", Outcome.APPLIED,
                session.dispatch(selected, Action.OPEN_EXPLANATION, 0));
        check.same("Opening the explanation leaves the selected move visible", new Snapshot(3, Branch.HISTORY, 0, true),
                session.snapshot());
    }

    @Test void archivedExplanationResumesTheExactBranchAndReturnsToTheOriginalMove(Checks check) {
        var source = ChessMatch.restore(FOOLS_MATE);
        var original = source.checkpoint();
        var analysis = review(source);
        var cache = GameReviewCodec.encode(source, analysis);
        var session = new GameReviewSession(source, analysis, new Snapshot(3, Branch.HISTORY, 0));
        check.same("The recorded loss has an explanation", Outcome.APPLIED, dispatch(session, Action.OPEN_EXPLANATION));
        check.same("The recorded branch is available", Outcome.APPLIED, dispatch(session, Action.SELECT_PLAYED));
        check.same("The recorded branch starts immediately before the selected move", true,
                session.position().sameForRepetition(source.positionAt(2)));
        check.same("Stepping the played line highlights g4", Outcome.APPLIED, dispatch(session, Action.LINE_NEXT));
        check.same("The played move is highlighted", RECORDED, session.lastMove());

        var encoded = GameReviewBookmark.encode(ARCHIVE_ID, source, session.snapshot());
        var reopened = ChessMatch.restore(original);
        var restored = new GameReviewSession(reopened, GameReviewCodec.decode(reopened, cache),
                GameReviewBookmark.decode(ARCHIVE_ID, reopened, encoded));
        check.same("A new session restores the open explanation and substituted board exactly",
                List.of(new Snapshot(3, Branch.PLAYED, 1, true), RECORDED, true),
                List.of(restored.snapshot(), restored.lastMove(), restored.position().sameForRepetition(session.position())));
        check.same("The next variation step plays the mating queen reply", Outcome.APPLIED, dispatch(restored, Action.LINE_NEXT));
        check.same("The played variation ends in mate", List.of(MATE, true),
                List.of(restored.lastMove(), restored.position().isCheckmate()));
        check.same("The variation does not play beyond its mate", Outcome.UNAVAILABLE, dispatch(restored, Action.LINE_NEXT));

        check.same("The recommended branch can replace the played branch", Outcome.APPLIED,
                dispatch(restored, Action.SELECT_RECOMMENDED));
        check.same("The recommendation also starts before the same recorded ply", List.of(new Snapshot(3, Branch.RECOMMENDED, 0, true), true),
                List.of(restored.snapshot(), restored.position().sameForRepetition(source.positionAt(2))));
        check.same("The recommended branch advances its own cursor", Outcome.APPLIED, dispatch(restored, Action.LINE_NEXT));
        check.same("The recommended move is d4 and the position avoids the observed mate", List.of(RECOMMENDED, false),
                List.of(restored.lastMove(), restored.position().isCheckmate()));
        check.same("The recommended branch can rewind its own cursor", Outcome.APPLIED, dispatch(restored, Action.LINE_PREVIOUS));
        check.same("Rewinding the branch leaves the recorded history at the blunder", new Snapshot(3, Branch.RECOMMENDED, 0, true),
                restored.snapshot());

        check.same("Closing the explanation returns to the recorded move", Outcome.APPLIED,
                dispatch(restored, Action.CLOSE_EXPLANATION));
        check.same("The history cursor, original move and board are restored together",
                List.of(new Snapshot(3, Branch.HISTORY, 0), RECORDED, true),
                List.of(restored.snapshot(), restored.lastMove(), restored.position().sameForRepetition(source.positionAt(3))));
        check.same("Navigation never rewrites the archive or analysis", List.of(original, cache),
                List.of(reopened.checkpoint(), GameReviewCodec.encode(reopened, analysis)));
    }

    @Test void onlyABookmarkForTheSameArchiveCanRestoreAnExplanation(Checks check) {
        var source = ChessMatch.restore(FOOLS_MATE);
        var snapshot = new Snapshot(3, Branch.PLAYED, 1, true);
        var encoded = GameReviewBookmark.encode(ARCHIVE_ID, source, snapshot);
        check.same("A bookmark round trips all four presentation fields", snapshot,
                GameReviewBookmark.decode(ARCHIVE_ID, source, encoded));
        check.same("Another archive cannot reuse a bookmark with the same moves", null,
                GameReviewBookmark.decode("other-game", source, encoded));
        var other = ChessMatch.restore("1|f2f3 e7e6 g2g4 d8h4");
        check.same("A replay for another checkpoint cannot reuse the archive's bookmark", null,
                GameReviewBookmark.decode(ARCHIVE_ID, other, encoded));
        check.same("A malformed panel flag cannot reopen an explanation", null,
                GameReviewBookmark.decode(ARCHIVE_ID, source, encoded.replace("|true", "|yes")));

        var restored = new GameReviewSession(source, review(source), new Snapshot(3, Branch.PLAYED, 3, true));
        check.same("A corrupt branch step falls back to the selected history move", new Snapshot(3, Branch.HISTORY, 0),
                restored.snapshot());
        check.same("Fallback shows the original history board", true,
                restored.position().sameForRepetition(source.positionAt(3)));
    }

    @Test void keyMomentsAndTheBestLineKeepTheRecordedCursorIndependent(Checks check) {
        var source = ChessMatch.restore(FOOLS_MATE);
        var session = new GameReviewSession(source, review(source), new Snapshot(0, Branch.HISTORY, 0));
        check.same("The initial position has no played move to explain", Outcome.UNAVAILABLE,
                dispatch(session, Action.OPEN_EXPLANATION));
        check.same("Next key moment skips ordinary moves and finds the blunder", Outcome.APPLIED, dispatch(session, Action.NEXT_KEY));
        check.same("The moment keeps the post-move board in history", new Snapshot(3, Branch.HISTORY, 0), session.snapshot());
        check.same("Next key moment does not wrap past the last marked move", Outcome.UNAVAILABLE, dispatch(session, Action.NEXT_KEY));

        check.same("The bulb opens the recommended line without opening the explanation", Outcome.APPLIED,
                dispatch(session, Action.TOGGLE_BEST));
        check.same("Selecting the current branch does not restart the line", Outcome.UNAVAILABLE,
                dispatch(session, Action.SELECT_RECOMMENDED));
        check.same("The main Next action steps the recommended line", Outcome.APPLIED, dispatch(session, Action.NEXT));
        check.same("The recorded ply stays fixed while the recommended move is visible",
                List.of(new Snapshot(3, Branch.RECOMMENDED, 1, false), RECOMMENDED), List.of(session.snapshot(), session.lastMove()));
        check.same("The main Previous action rewinds only the recommended move", Outcome.APPLIED, dispatch(session, Action.PREVIOUS));
        check.same("The bulb can close the recommended line", Outcome.APPLIED, dispatch(session, Action.TOGGLE_BEST));
        check.same("The recorded blunder remains selected after the preview closes", new Snapshot(3, Branch.HISTORY, 0), session.snapshot());
    }

    private static Outcome dispatch(GameReviewSession session, Action action) {
        return session.dispatch(session.revision(), action, 0);
    }

    private static GameReview review(ChessMatch source) {
        var turns = new ArrayList<GameReview.Turn>();
        for (int ply = 1; ply <= source.ply(); ply++) {
            var move = source.moveAt(ply);
            var side = source.positionAt(ply - 1).turn();
            var turn = ply == 3
                    ? new GameReview.Turn(ply, side, move, Grade.BLUNDER, 544, 0, 544, 0, 4,
                            List.of(RECOMMENDED, Move.fromUci("e5d4")), List.of(RECORDED, MATE), 0)
                    : new GameReview.Turn(ply, side, move, Grade.GOOD, 500, 500, 0, 500, 4,
                            List.of(move), List.of(move), 0);
            turns.add(turn);
        }
        var player = new GameReview.Player(OptionalInt.empty(), Optional.empty(), 0, Map.of());
        return new GameReview(turns, null, 0, 500, player, player);
    }
}

/** Generates the browser's archive from the same analyzed match used by Android's sanity. */
final class GameReviewArchiveFixture {
    private GameReviewArchiveFixture() { }

    public static void main(String[] arguments) throws IOException {
        boolean focus = arguments.length == 2 && "--focus".equals(arguments[1]);
        if (arguments.length != 1 && !focus) {
            throw new IllegalArgumentException("Supply the destination for the browser archive fixture and optional --focus");
        }
        var source = ChessMatch.restore("3|WHITE|ADVANCED|0|f2f3 e7e5 g2g4 d8h4");
        var review = GameReview.analyze(source, ignored -> { });
        var blunder = review.turns().get(2);
        String recommended = blunder.bestLine().get(0).uci();
        int recommendedLength = blunder.bestLine().size();
        Files.writeString(Path.of(arguments[0]), """
                {
                  %s"id": "fools-mate-native",
                  "checkpoint": "%s",
                  "review": "%s",
                  "expected": {"ply": 3, "playedUci": "g2g4", "matingUci": "d8h4", "recommendedUci": "%s", "recommendedLength": %d}
                }
                """.formatted(focus ? "\"scope\": \"review-focus\",\n  " : "", source.checkpoint(), GameReviewCodec.encode(source, review),
                        recommended, recommendedLength));
    }
}
