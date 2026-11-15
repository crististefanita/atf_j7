package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;

/** Availability of controls on the live board, a historical position or a finished game. */
public final class MatchControls {
    public enum Action { FLIP, HOME, SAVED_GAMES, ANALYSIS_BOARD, GAME_REVIEW, OFFER_DRAW, RESIGN }

    public record MenuItem(Action action, boolean enabled) { }

    public record Automation(boolean checked, boolean enabled, boolean visible) { }

    private MatchControls() { }

    public static Automation automation(ChessMatch match, boolean reviewing, boolean manualAi) {
        boolean inProgress = match.result() == ChessMatch.Result.IN_PROGRESS;
        return new Automation(!manualAi, inProgress && !reviewing, inProgress);
    }

    public static List<MenuItem> menu(ChessMatch match, boolean reviewing, boolean manualAi) {
        var items = new ArrayList<>(List.of(enabled(Action.FLIP), enabled(Action.HOME),
                enabled(Action.SAVED_GAMES), enabled(Action.ANALYSIS_BOARD)));
        if (reviewing) return List.copyOf(items);

        boolean finished = match.result() != ChessMatch.Result.IN_PROGRESS;
        if (finished || match.analysisEnabled()) items.add(new MenuItem(Action.GAME_REVIEW, match.ply() > 0 || finished));
        if (!finished) {
            items.add(new MenuItem(Action.OFFER_DRAW, MatchSlots.canMove(match, manualAi, false) && !match.awaitingBot()));
            items.add(enabled(Action.RESIGN));
        }
        return List.copyOf(items);
    }

    private static MenuItem enabled(Action action) { return new MenuItem(action, true); }
}
