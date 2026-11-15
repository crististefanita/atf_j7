package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.MatchControls.Action;
import ro.atf.chess.MatchControls.Automation;
import ro.atf.chess.MatchControls.MenuItem;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchControlsTest {
    private static final List<MenuItem> NAVIGATION = List.of(new MenuItem(Action.FLIP, true), new MenuItem(Action.HOME, true),
            new MenuItem(Action.SAVED_GAMES, true), new MenuItem(Action.ANALYSIS_BOARD, true));

    @Test void offersOnlySafeCommandsWhileBrowsingHistory(Checks check) {
        var match = ChessMatch.start(true).play(ChessPosition.Move.fromUci("e2e4"));
        check.same("The current position can open Game Review and resign while the AI turn blocks a draw",
                List.of(NAVIGATION.get(0), NAVIGATION.get(1), NAVIGATION.get(2), NAVIGATION.get(3),
                        new MenuItem(Action.GAME_REVIEW, true), new MenuItem(Action.OFFER_DRAW, false), new MenuItem(Action.RESIGN, true)),
                MatchControls.menu(match, false, false));
        check.same("History keeps flip, home, saved games and analysis board available",
                NAVIGATION, MatchControls.menu(match, true, false));
        check.same("Even manual control of the AI cannot offer a draw for the AI side",
                false, MatchControls.menu(match, false, true).contains(new MenuItem(Action.OFFER_DRAW, true)));
    }

    @Test void keepsReviewAndOutcomeActionsConsistentWithTheGame(Checks check) {
        var plain = ChessMatch.restore("1|e2e4 e7e5");
        var ready = MatchControls.menu(plain, false, false);
        check.same("A regular game without analysis exposes the playable outcome commands",
                List.of(Action.FLIP, Action.HOME, Action.SAVED_GAMES, Action.ANALYSIS_BOARD, Action.OFFER_DRAW, Action.RESIGN),
                ready.stream().map(MenuItem::action).toList());
        check.same("A human turn enables an offer", true, ready.contains(new MenuItem(Action.OFFER_DRAW, true)));
        check.same("With analysis enabled the review waits for a first move", true,
                MatchControls.menu(ChessMatch.start(true), false, false).contains(new MenuItem(Action.GAME_REVIEW, false)));
        check.same("A finished match keeps Game Review and removes draw and resign",
                List.of(NAVIGATION.get(0), NAVIGATION.get(1), NAVIGATION.get(2), NAVIGATION.get(3), new MenuItem(Action.GAME_REVIEW, true)),
                MatchControls.menu(plain.offerDraw(), false, false));
    }

    @Test void checkedMeansTheAiPlaysAndUnavailableStatesKeepTheStoredPreference(Checks check) {
        var live = ChessMatch.restore("1|e2e4 e7e5");
        check.same("Default automatic play is checked on the live board", new Automation(true, true, true),
                MatchControls.automation(live, false, false));
        check.same("A saved manual slot shows the unchecked control", new Automation(false, true, true),
                MatchControls.automation(live, false, true));
        check.same("History shows the preference but cannot toggle it", new Automation(true, false, true),
                MatchControls.automation(live, true, false));
        var finished = live.resign();
        check.same("A finished game hides automation regardless of how the archive restores it",
                List.of(false, false), List.of(MatchControls.automation(finished, false, true).visible(),
                        MatchControls.automation(finished, true, false).visible()));
    }
}
