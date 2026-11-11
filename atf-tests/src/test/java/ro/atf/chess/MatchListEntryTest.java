package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchListEntry.Status;
import ro.atf.chess.MatchSetup.Level;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchListEntryTest {
    private static final long STARTED = Instant.parse("2026-01-12T17:30:00Z").toEpochMilli();
    private static final long FINISHED = STARTED + 120_000;

    @Test void savedGamesExposeOpponentAndMoveCountWithOrWithoutATimestamp(Checks check) {
        var match = ChessMatch.start(new MatchSetup(Side.WHITE, Level.ADVANCED, false)).play(Move.fromUci("e2e4"));
        var dated = MatchListEntry.saved(3, match, STARTED);
        var legacy = MatchListEntry.saved(3, match, 0);
        check.same("A saved row describes one recorded move against the chosen opponent", List.of(3, Level.ADVANCED, 1, Status.IN_PROGRESS),
                List.of(dated.slot(), dated.opponent(), dated.moves(), dated.status()));
        check.same("The starting timestamp is retained", List.of(STARTED, true), List.of(dated.timestamp(), dated.hasTimestamp()));
        check.same("A legacy save still displays its opponent and moves", List.of(Level.ADVANCED, 1, false),
                List.of(legacy.opponent(), legacy.moves(), legacy.hasTimestamp()));
        var unreadable = MatchListEntry.saved(8, null, STARTED);
        check.same("An unreadable save keeps its slot and drops stale metadata", List.of(8, Status.UNAVAILABLE, false),
                List.of(unreadable.slot(), unreadable.status(), unreadable.hasTimestamp()));
    }

    @Test void resultsUseThePlayersSideAndKeepTheActualMoveCount(Checks check) {
        var won = ChessMatch.start(new MatchSetup(Side.BLACK, Level.HARD, false));
        for (String move : List.of("f2f3", "e7e5", "g2g4", "d8h4")) won = won.play(Move.fromUci(move));
        var victory = finished(won, true, FINISHED);
        var resignation = finished(ChessMatch.start().resign(), false, FINISHED);
        var draw = finished(ChessMatch.start().offerDraw(), false, 0);
        check.same("A player with Black can win and reopen a saved analysis", List.of(Level.HARD, 4, Status.WON, true, FINISHED),
                List.of(victory.opponent(), victory.moves(), victory.status(), victory.reviewed(), victory.timestamp()));
        check.same("Resigning before any move is a loss with zero moves", List.of(0, Status.LOST, false),
                List.of(resignation.moves(), resignation.status(), resignation.reviewed()));
        check.same("A legacy draw has a result and no invented date", List.of(Status.DRAW, false), List.of(draw.status(), draw.hasTimestamp()));
        check.same("The same completed game is marked finished in Saved games", Status.FINISHED, MatchListEntry.saved(4, won, STARTED).status());
    }

    private static MatchListEntry finished(ChessMatch match, boolean reviewed, long timestamp) {
        return MatchListEntry.finished(new MatchStore.ArchivedMatch("test", match, false, false, reviewed, timestamp));
    }
}
