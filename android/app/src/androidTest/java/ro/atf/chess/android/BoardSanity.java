package ro.atf.chess.android;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Predicate;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview;
import ro.atf.chess.GameReviewTimeline;
import ro.atf.chess.MatchSetup;
import ro.atf.chess.MatchSlots;
import ro.atf.chess.MatchStore;
import ro.atf.chess.QuestCatalog;
import ro.atf.chess.QuestPayoff;

public final class BoardSanity extends Instrumentation {
    private static final int CATALOG_SIZE = 500;
    private static final int MATE_TWO_SIZE = 300;
    private static final int MATE_THREE_SIZE = 200;
    private static final int TACTICS_SIZE = 100;
    private static final int HINT_TINT = 0xff57e0b4;
    private static final int MAX_REVIEW_SCROLLS = 12;
    private static final String REVIEW_MATE = "3|WHITE|ADVANCED|0|f2f3 e7e5 g2g4 d8h4";
    // Fixed 185-ply game from the offline J7 league, game 1500-11.
    private static final String LONG_REVIEW_MOVES = """
            e2e4 d7d5 e4d5 d8d5 b1c3 d5a5 h2h3 a5e5 c3e2 b8c6 d2d4 c6d4
            d1d4 e5d4 e2d4 g8f6 d4b5 f6d5 g1f3 c8f5 f1c4 f5e4 c2c3 e4f3
            c4d5 f3d5 b5c7 e8d8 c7d5 a8c8 d5b4 a7a5 b4c2 b7b5 c2e3 c8c5
            h1f1 c5e5 a2a4 b5a4 a1a4 d8e8 e1d1 e5c5 d1e2 c5e5 e2d2 e5c5
            a4a2 e7e5 d2e1 f8d6 f1h1 g7g5 e3f5 d6c7 g2g3 h8g8 g3g4 g8g6
            f2f3 f7f6 e1e2 h7h6 c1e3 c5c4 b2b4 c4c3 e3c5 a5b4 c5b4 c3c4
            h1b1 e8f7 b4d6 c7d6 f5d6 f7g8 a2a8 g8h7 d6c4 g6g7 a8a5 g7d7
            c4a3 d7d4 a5b5 h7g6 a3c2 d4c4 c2e1 c4d4 e1d3 e5e4 d3c1 d4c4
            b5b6 e4f3 e2f2 c4c2 f2e1 f3f2 e1f1 c2c4 c1e2 c4e4 b6a6 e4e2
            b1a1 e2e1 a1e1 f2e1b f1e1 h6h5 a6a7 f6f5 g4f5 g6f5 e1e2 f5g6
            e2f3 g6h6 f3e3 g5g4 a7a6 h6h7 h3h4 h7h8 a6a7 g4g3 a7a8 h8h7
            a8b8 g3g2 e3f2 g2g1b f2g3 g1d4 b8b4 d4e5 g3f2 h7h8 b4b5 e5d4
            f2g2 h8g8 b5h5 d4f6 g2h3 f6e7 h5a5 e7d6 h3g4 g8h8 a5d5 d6b4
            h4h5 b4c3 d5d3 c3e5 d3d7 h8g8 g4g5 g8h8 g5g6 e5g7 d7d8 g7f8
            h5h6 h8g8 d8c8 g8h8 c8b8 h8g8 b8c8 g8h8 c8b8 h8g8 b8e8 g8h8
            e8d8 h8g8 d8e8 g8h8 e8f8
            """;
    private final Bundle receipt = new Bundle();
    private int comparisons;
    private String phase;
    private boolean externalFrames;
    private String runId;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments.getString("phase", "");
        runId = arguments.getString("runId", "");
        externalFrames = "true".equals(arguments.getString("externalFrames"));
        start();
    }

    @Override public void onStart() {
        receipt.putString("phase", phase);
        receipt.putString("pid", Integer.toString(android.os.Process.myPid()));
        if (!runId.isEmpty()) receipt.putString("runId", runId);
        try {
            if ("openings".equals(phase)) checkOpenings();
            else if ("game-review".equals(phase)) checkGameReview();
            else if ("game-review-why".equals(phase)) checkReviewExplanation();
            else if ("game-review-why-restore".equals(phase)) checkReviewExplanationRestore();
            else if ("tactics-explanation".equals(phase)) checkTacticsExplanation();
            else if ("training-finish".equals(phase)) checkTrainingFinish();
            else if ("puzzle-mate-replay".equals(phase)) checkPuzzleMateReplay();
            else if ("puzzle-danger".equals(phase)) checkPuzzleDanger();
            else if ("manual-analysis".equals(phase)) checkManualAnalysis();
            else if ("match-slots".equals(phase)) checkMatchSlots();
            else if ("match-archive".equals(phase)) checkMatchArchive();
            else if (phase.startsWith("analysis-")) checkAnalysis();
            else if (phase.startsWith("match-")) checkMatch();
            else checkQuests();
            receipt.putString("result", "PASS");
            finish(Activity.RESULT_OK, receipt);
        } catch (Throwable failure) {
            receipt.putString("result", "FAIL");
            receipt.putString("diagnostic", failure.toString().replace('\n', ' '));
            try {
                Bitmap image = getUiAutomation().takeScreenshot();
                if (image != null) {
                    try { saveFrame("failure", image); } finally { image.recycle(); }
                }
            } catch (Exception captureFailure) {
                receipt.putString("capture_diagnostic", captureFailure.toString().replace('\n', ' '));
            }
            finish(Activity.RESULT_CANCELED, receipt);
        }
    }

    private void checkMatchArchive() throws Exception {
        var mate = ChessMatch.restore("1|f2f3 e7e5 g2g4 d8h4");
        seedGame(1, mate);
        seedGame(2, ChessMatch.start(new MatchSetup(Side.WHITE, MatchSetup.Level.ADVANCED, false)).offerDraw());
        seedGame(3, ChessMatch.start(new MatchSetup(Side.WHITE, MatchSetup.Level.HARD, false)).resign());
        seedGame(4, ChessMatch.start());
        var home = launch();
        var list = openMatchList(home, R.id.match_archive);
        captureLastGameRow(list, "finished-games", 3);
        chooseListEntry(list, 2);
        awaitListTransition(home);
        var board = findBoard(viewsOf(home));
        var result = (TextView) home.findViewById(R.id.match_status);
        captureFrame("match-archive", "Selecting a finished row opens its result and review entry point", image -> fullyVisible(board)
                && result.getText().toString().contains(home.getString(R.string.match_bot_wins))
                && hasColor(image, result, result.getCurrentTextColor(), 32));
    }

    private void checkMatchSlots() throws Exception {
        var levels = MatchSetup.Level.values();
        for (int slot = 1; slot <= MatchSlots.LIMIT; slot++) {
            var match = ChessMatch.start(new MatchSetup(Side.WHITE, levels[(slot - 1) % levels.length], false));
            if (slot % 3 != 0) match = match.play(Move.fromUci("e2e4")).play(Move.fromUci("e7e5"));
            seedGame(slot, match);
        }
        seedGame(MatchSlots.LIMIT, ChessMatch.start().resign());
        var activity = launch(true);
        var list = openMatchList(activity, R.id.resume_match);
        captureLastGameRow(list, "saved-games", MatchSlots.LIMIT - 1);
        chooseListEntry(list, MatchSlots.LIMIT - 2);
        awaitListTransition(activity);
        var board = findBoard(viewsOf(activity));
        captureFrame("match-slots", "Selecting a saved row restores the recorded position",
                image -> fullyVisible(board) && hasPawn(activity, board, "e4") && hasPawn(activity, board, "e5")
                        && (hasColor(image, cell(board, "e5"), 0xffbec953, 24) || hasColor(image, cell(board, "e5"), 0xfff4ef86, 24)));
    }

    private void seedGame(int slot, ChessMatch match) {
        var preferences = getTargetContext().getSharedPreferences("quest-progress", android.content.Context.MODE_PRIVATE);
        var instant = Instant.parse("2026-01-12T17:30:00Z").plusSeconds(slot * 93_600L);
        var store = new MatchStore(new AndroidGamePreferences(preferences), Clock.fixed(instant, ZoneOffset.UTC));
        if (!store.startMatch(slot, match, false, false, false) || !store.leaveMatch()) {
            throw new AssertionError("Could not prepare a game list fixture");
        }
    }

    private void chooseListEntry(Activity list, int index) {
        var rows = (ViewGroup) list.findViewById(R.id.match_list_rows);
        var row = rows.getChildAt(index);
        if (!fullyVisible(row) || !row.isEnabled()) throw new AssertionError("The expected game row is not available");
        runOnMainSync(row::performClick);
    }

    private Activity openMatchList(Activity owner, int entry) throws Exception {
        var monitor = addMonitor(MatchListActivity.class.getName(), null, false);
        try {
            click(owner, entry);
            var list = waitForMonitorWithTimeout(monitor, 7000);
            if (list == null) throw new AssertionError("The game list did not open as a screen");
            awaitListTransition(list);
            return list;
        } finally {
            removeMonitor(monitor);
        }
    }

    private void awaitListTransition(Activity activity) throws java.util.concurrent.TimeoutException {
        waitForIdleSync();
        getUiAutomation().waitForIdle(350, 5000);
        if (!activity.hasWindowFocus()) throw new AssertionError("The game list transition did not transfer window focus");
    }

    private void captureLastGameRow(Activity list, String frame, int expectedRows) throws IOException {
        var scroll = (ScrollView) list.findViewById(R.id.match_list_scroll);
        var rows = (ViewGroup) list.findViewById(R.id.match_list_rows);
        if (rows.getChildCount() != expectedRows) {
            throw new AssertionError(frame + " expected " + expectedRows + " rows, found " + rows.getChildCount());
        }
        var last = rows.getChildAt(rows.getChildCount() - 1);
        var badge = (TextView) last.findViewById(R.id.match_row_date);
        var label = (TextView) last.findViewById(R.id.match_row_label);
        var summary = (TextView) last.findViewById(R.id.match_row_details);
        var title = (TextView) list.findViewById(R.id.match_list_title);
        var back = (Button) list.findViewById(R.id.match_list_back);
        runOnMainSync(() -> scroll.scrollTo(0, scroll.getChildAt(0).getHeight()));
        waitForIdleSync();
        var rightHalfOfTitle = screenBounds(title);
        rightHalfOfTitle.left = rightHalfOfTitle.centerX();
        var group = screenBounds(rows);
        int lowerEdge = group.bottom - 3;
        captureFrame(frame, "The last game shows its title, moves and local time with Back visible", image -> fullyVisible(last) && fullyVisible(back)
                && fullyVisible(badge) && badge.getText().length() > 0
                && screenBounds(last).bottom <= screenBounds(back).top
                // The rounded corner must be painted at the bottom, even if the view tree has already scrolled.
                && image.getPixel(group.left + 3, lowerEdge) == image.getPixel(group.left - 3, lowerEdge)
                && hasColor(image, rightHalfOfTitle, title.getCurrentTextColor(), 32)
                && hasColor(image, label, label.getCurrentTextColor(), 32)
                && hasColor(image, summary, summary.getCurrentTextColor(), 24)
                && hasColor(image, badge, badge.getCurrentTextColor(), 24)
                && hasColor(image, back, back.getCurrentTextColor(), 32));
    }

    private void checkManualAnalysis() throws Exception {
        enableReviewNodeIds();
        var store = new ChessProgressStore(getTargetContext());
        var home = launch();
        var monitor = addMonitor(AnalysisActivity.class.getName(), null, false);
        tap(home.findViewById(R.id.open_analysis));
        var study = waitForMonitorWithTimeout(monitor, 7000);
        removeMonitor(monitor);
        if (study == null) throw new AssertionError("Home did not open the analysis board");
        waitForIdleSync();
        var board = findBoard(viewsOf(study));
        compare("Home opens the manual board without creating a saved game", true,
                cell(board, "e2").isEnabled() && store.matches().readMatch() == null && awaitHiddenAnalysis(study, 0));
        var lines = ro.atf.chess.TacticalAnalysis.recommendations(ChessPosition.initial());
        tap(study.findViewById(R.id.match_hint));
        compare("The manual hint marks the recommended first move", true, hintShown(study, board, lines, 0));
        checkHintPreview(study, "manual-analysis", lines.get(0).move());
        var destination = lines.get(0).move().to();
        tap(cell(board, BoardSquare.name(destination)));
        var history = (ViewGroup) study.findViewById(R.id.match_history_moves);
        compare("Returning to the study lets the player apply the hint without saving a match", true,
                history.getChildCount() == 2 && history.getChildAt(1).isSelected() && store.matches().readMatch() == null);
    }
    private static boolean hasPawn(Activity activity, BoardView board, String square) {
        return cell(board, square).getContentDescription().toString().contains(activity.getString(R.string.pawn));
    }

    private void checkGameReview() throws Exception {
        enableReviewNodeIds();
        var store = new ChessProgressStore(getTargetContext());
        checkLongGameGraph(store);
        String cycle = "b1a3 b8a6 a3b1 a6b8";
        var finished = ChessMatch.restore("3|WHITE|ADVANCED|0|" + String.join(" ", cycle, cycle, cycle,
                "a2a3 a7a6 g1h3 g8h6 h3g1 h6g8 f2f3 e7e5 g2g4 d8h4"));
        int branchPly = finished.ply() - 2;
        var branch = finished.continueFrom(branchPly);
        if (!store.matches().startMatch(store.matches().activeMatchSlot(), finished, false, false, false)) {
            throw new AssertionError("Could not seed the completed match");
        }
        var activity = launch();
        var status = (TextView) activity.findViewById(R.id.match_status);
        var board = findBoard(viewsOf(activity));
        var bounds = screenBounds(board);
        compare("A finished match offers a visible chart action for review with live analysis disabled", true,
                status.getText().toString().contains(activity.getString(R.string.match_game_review))
                        && status.getText().toString().contains("📊") && status.isClickable()
                        && activity.findViewById(R.id.match_analysis) == null);
        tap(status);
        var close = dialogNode(activity.getString(R.string.game_review_close), AccessibilityNodeInfo::isClickable);
        var reviewBounds = activeWindowBounds();
        compare("Game review fills the app window as soon as it opens", true,
                close != null && reviewBounds.contains(screenBounds(activity.findViewById(android.R.id.content))));
        var reviewBoard = reviewNode(R.id.game_review_board, 120_000);
        var reviewBoardBounds = nodeBounds(reviewBoard);
        var scrollableReport = reviewNode(R.id.game_review_moves, 4500);
        var scrollBounds = nodeBounds(scrollableReport);
        var opening = reviewNode(R.id.game_review_opening, 4500);
        var matches = store.matches();
        var review = matches.readReview(matches.archivedId(matches.activeMatchSlot()), finished);
        float density = activity.getResources().getDisplayMetrics().density;
        boolean tallPortrait = reviewBounds.height() >= reviewBounds.width() * 1.6f;
        boolean separateReport = scrollBounds.top >= reviewBoardBounds.bottom || scrollBounds.left >= reviewBoardBounds.right;
        var entryGaps = new ArrayList<String>();
        if (reviewBoardBounds.isEmpty() || reviewBoardBounds.width() != reviewBoardBounds.height()
                || !reviewBounds.contains(reviewBoardBounds) || tallPortrait && reviewBoardBounds.width() < reviewBounds.width() * .8f) {
            entryGaps.add("square board " + reviewBoardBounds + " in " + reviewBounds);
        }
        if (!separateReport || scrollableReport == null || !scrollableReport.isScrollable()
                || scrollBounds.height() < Math.round(72 * density) || !reviewBounds.contains(scrollBounds)) {
            entryGaps.add("scrollable report " + scrollBounds);
        }
        if (review == null || review.opening() == null || opening == null || opening.getText() == null
                || !opening.getText().toString().contains(review.opening().name())) entryGaps.add("opening name");
        if (!reviewIcon(activity, R.id.game_review_previous, R.string.game_review_previous)) entryGaps.add("previous arrow");
        if (!reviewIcon(activity, R.id.game_review_next, R.string.game_review_next)) entryGaps.add("next arrow");
        if (!reviewIcon(activity, R.id.game_review_show_best, R.string.game_review_show_best)) entryGaps.add("best-line bulb");
        compare("The entry presents a large square board, opening and accessible icons with a visible scrollable report", List.of(), entryGaps);
        var graphNode = reviewNode(R.id.game_review_graph, 4500);
        var graphBounds = nodeBounds(graphNode);
        captureFrame("game-review", "The finished review visibly draws its interactive evaluation line",
                image -> graphNode != null && graphNode.isClickable() && hasColor(image, graphBounds, 0xffb8d88a, 24));
        int scrolls = 0;
        boolean playerRatingVisible = false;
        boolean aiRatingVisible = false;
        while (review != null) {
            playerRatingVisible |= showsRating(activity, R.string.game_review_player, review.white());
            aiRatingVisible |= showsRating(activity, R.string.game_review_ai, review.black());
            if ((playerRatingVisible && aiRatingVisible) || scrolls >= MAX_REVIEW_SCROLLS) break;
            swipeReview(scrollBounds, true);
            scrolls++;
        }
        compare("The player and AI rows show their own saved rating intervals", true, playerRatingVisible && aiRatingVisible);
        String brilliant = activity.getString(R.string.game_review_grade_counts, activity.getString(R.string.game_review_brilliant), "0", "0");
        var row = dialogNode(brilliant, AccessibilityNodeInfo::isVisibleToUser, 500);
        while (row == null && scrolls < MAX_REVIEW_SCROLLS) {
            swipeReview(scrollBounds, true);
            scrolls++;
            row = dialogNode(brilliant, AccessibilityNodeInfo::isVisibleToUser, 500);
        }
        boolean tableVisible = row != null && reviewBounds.equals(activeWindowBounds()) && sameReviewBoard(reviewBoardBounds);
        captureFrame("game-review-comparison", "The scrollable report exposes both comparison counts without moving the board", image -> tableVisible);
        var returnedGraph = visibleReviewNode(R.id.game_review_graph);
        while ((returnedGraph == null || visibleReviewNode(R.id.game_review_opening) == null) && scrolls > 0) {
            swipeReview(scrollBounds, false);
            scrolls--;
            returnedGraph = visibleReviewNode(R.id.game_review_graph);
        }
        if (returnedGraph == null) throw new AssertionError("The evaluation graph did not return after viewing the comparison");
        Rect returnedGraphBounds = nodeBounds(returnedGraph);
        float earlierPosition = branchPly / (float) finished.ply();
        tapAt(returnedGraphBounds.left + returnedGraphBounds.width() * earlierPosition, returnedGraphBounds.exactCenterY());
        review = store.matches().readReview(store.matches().archivedId(store.matches().activeMatchSlot()), finished);
        if (review == null) throw new AssertionError("The completed game review was not saved");
        var selectedEarlier = review.turns().get(branchPly - 1);
        String selectedEarlierText = activity.getString(R.string.game_review_selected_move,
                moveLabel(activity, selectedEarlier), gradeLabel(activity, selectedEarlier.grade()));
        var blunder = review.turns().get(branchPly);
        String selectedBlunderText = activity.getString(R.string.game_review_selected_move,
                moveLabel(activity, blunder), gradeLabel(activity, blunder.grade()));
        boolean graphSelected = reviewSelection(selectedEarlierText) && visibleReviewNode(R.id.game_review_opening) == null
                && sameReviewBoard(reviewBoardBounds);
        clickReviewIcon(R.id.game_review_next);
        boolean nextSelected = reviewSelection(selectedBlunderText) && sameReviewBoard(reviewBoardBounds);
        clickReviewIcon(R.id.game_review_previous);
        compare("Graph and arrow navigation synchronize the selected move, hide the opening and keep the board anchored", true,
                graphSelected && nextSelected && reviewSelection(selectedEarlierText) && sameReviewBoard(reviewBoardBounds));
        clickReviewIcon(R.id.game_review_next_key);
        String g4Pawn = pieceDescription(activity, ChessPosition.Side.WHITE, ChessPosition.Kind.PAWN, "g4");
        compare("Next key moment synchronizes the blunder, score context and played board position", true,
                reviewSelection(selectedBlunderText) && dialogNode(g4Pawn) != null && sameReviewBoard(reviewBoardBounds));

        clickReviewIcon(R.id.game_review_show_best);
        String firstStep = reviewBranchStep(activity, R.string.game_review_recommended_branch, 0, blunder.bestLine().size());
        var recommended = blunder.bestLine().get(0);
        String beforeDestination = pieceDescription(activity, finished.positionAt(branchPly).pieceAt(recommended.to()),
                recommended.to());
        compare("The bulb starts the best line before the played move at step zero", true,
                reviewLineStep(firstStep) && (beforeDestination == null || dialogNode(beforeDestination) == null)
                        && sameReviewBoard(reviewBoardBounds)
                        && reviewIcon(activity, R.id.game_review_line_previous, R.string.game_review_line_previous)
                        && reviewIcon(activity, R.id.game_review_line_next, R.string.game_review_line_next)
                        && reviewIcon(activity, R.id.game_review_show_best, R.string.game_review_hide_best));
        captureFrame("game-review-line", "The recommended first move is marked on the review board",
                image -> hasColor(image, reviewBoardBounds, 0xff57e0b4, 20));
        clickReviewIcon(R.id.game_review_line_next);
        var afterRecommended = finished.positionAt(branchPly).play(recommended);
        String destinationPiece = pieceDescription(activity, afterRecommended.pieceAt(recommended.to()), recommended.to());
        compare("Forward PV step applies the legal recommendation to the preview board", true,
                reviewLineStep(reviewBranchStep(activity, R.string.game_review_recommended_branch, 1, blunder.bestLine().size()))
                        && destinationPiece != null && dialogNode(destinationPiece) != null && sameReviewBoard(reviewBoardBounds));
        clickReviewIcon(R.id.game_review_line_previous);
        boolean returnedToLineStart = reviewLineStep(firstStep)
                && (beforeDestination == null || dialogNode(beforeDestination) == null) && sameReviewBoard(reviewBoardBounds);
        clickReviewIcon(R.id.game_review_show_best);
        compare("Backward PV restores step zero and the bulb returns to the played move without changing the saved game", true,
                returnedToLineStart && reviewSelection(selectedBlunderText) && dialogNode(g4Pawn) != null
                        && sameReviewBoard(reviewBoardBounds) && finished.checkpoint().equals(store.matches().readMatch().checkpoint()));

        String retry = activity.getString(R.string.game_review_retry);
        clickReviewIcon(R.id.game_review_actions);
        boolean retryAvailable = menuItem(retry) != null;
        clickMenuItem(activity.getString(R.string.game_review_opening_details));
        String expectedOpening = review.opening().name() + " · " + review.opening().eco();
        var openingDetails = dialogNode(expectedOpening, node -> node.isVisibleToUser() && node.getText() != null
                && expectedOpening.contentEquals(node.getText()));
        tapDialog(activity.getString(android.R.string.ok));
        var returnedBoard = reviewNode(R.id.game_review_board, 4500);
        var actionGaps = new ArrayList<String>();
        if (!retryAvailable) actionGaps.add("Retry unavailable");
        if (openingDetails == null) actionGaps.add("opening details missing: " + expectedOpening);
        if (returnedBoard == null) actionGaps.add("board unavailable after dismissing opening details");
        else if (!reviewBoardBounds.equals(nodeBounds(returnedBoard))) actionGaps.add("board moved after dismissing opening details");
        if (visibleReviewNode(R.id.game_review_opening) != null) actionGaps.add("inline opening visible again");
        compare("Review actions expose Retry and the exact opening details without reopening the inline heading", List.of(), actionGaps);
        clickReviewIcon(R.id.game_review_actions);
        clickMenuItem(retry);
        tapDialog(activity.getString(android.R.string.cancel));
        var restoredSelection = reviewNode(R.id.game_review_selection, 4500);
        compare("Cancelling retry preserves the original archived result and the selected move", true,
                finished.checkpoint().equals(store.matches().readMatch().checkpoint()) && restoredSelection != null && restoredSelection.getText() != null
                        && selectedBlunderText.contentEquals(restoredSelection.getText()));
        clickReviewIcon(R.id.game_review_actions);
        clickMenuItem(activity.getString(R.string.game_review_on_board));
        compare("Opening the selected move in the match keeps the archived game intact", true,
                status.getText().toString().contains(activity.getString(R.string.match_continue_from_here))
                        && finished.checkpoint().equals(store.matches().readMatch().checkpoint()));
        tap(activity.findViewById(R.id.match_history_previous));
        compare("History keeps the board anchored and offers continuation from the selected position", true,
                bounds.equals(screenBounds(board)) && status.getText().toString().contains(activity.getString(R.string.match_continue_from_here))
                        && !cell(board, "h2").isEnabled());
        tap(status);
        tapDialog(activity.getString(R.string.match_confirm_continue));
        long deadline = SystemClock.uptimeMillis() + 5000;
        while (!branch.checkpoint().equals(store.matches().readMatch().checkpoint()) && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(40);
        }
        waitForIdleSync();
        var tokens = (ViewGroup) activity.findViewById(R.id.match_history_moves);
        compare("Confirming saves the prefix, removes old history chips and enables the selected side",
                List.of(branch.checkpoint(), branchPly + 1, true),
                List.of(store.matches().readMatch().checkpoint(), tokens.getChildCount(), cell(board, "b2").isEnabled()));
        tap(cell(board, "b2"));
        tap(cell(board, "b3"));
        compare("The alternative move becomes the new saved continuation", Move.fromUci("b2b3"), store.matches().readMatch().moveAt(branchPly + 1));
        closeActivity(activity);
        checkMatchActions(store);
    }

    private void checkReviewExplanation() throws Exception {
        enableReviewNodeIds();
        var match = ChessMatch.restore(REVIEW_MATE);
        var store = new ChessProgressStore(getTargetContext()).matches();
        seedGame(1, match);
        String archiveId = store.archivedId(1);
        var review = GameReview.analyze(match, ignored -> { });
        if (!store.saveReview(archiveId, match, review)) throw new AssertionError("Could not prepare the archived review");
        var activity = openArchivedReview();
        var board = nodeBounds(reviewNode(R.id.game_review_board, 4500));
        for (int ply = match.ply(); ply > 1; ply--) clickReviewIcon(R.id.game_review_previous);
        clickReviewIcon(R.id.game_review_why);
        checkReviewFallback(activity, board, review.turns().get(0));
        clickReviewIcon(R.id.game_review_why);
        clickReviewIcon(R.id.game_review_next);
        clickReviewIcon(R.id.game_review_next);
        clickReviewIcon(R.id.game_review_why);
        var reason = reviewNode(R.id.game_review_reason, 4500);
        String reasonText = reason == null || reason.getText() == null ? "" : reason.getText().toString();
        String queen = activity.getString(R.string.queen);
        boolean namesMate = reasonText.contains("h4#") && reasonText.toLowerCase(Locale.ROOT).contains(queen.toLowerCase(Locale.ROOT));
        captureFrame("game-review-why", "Why names the mating queen on h4 for the archived g4 blunder",
                image -> namesMate && sameReviewBoard(board) && visibleReviewNode(R.id.game_review_reason) != null);

        var blunder = review.turns().get(2);
        var recommendation = blunder.bestLine().get(0);
        var root = match.positionAt(2);
        clickReviewIcon(R.id.game_review_recommended_branch);
        String originalPiece = pieceDescription(activity, root.pieceAt(recommendation.from()), recommendation.from());
        var cue = awaitRenderedFrame(image -> hasColor(image, board, HINT_TINT, 20));
        if (cue != null) cue.recycle();
        boolean fromCommonRoot = reviewLineStep(reviewBranchStep(activity, R.string.game_review_recommended_branch, 0, blunder.bestLine().size()))
                && dialogNode(originalPiece) != null && cue != null;
        clickReviewIcon(R.id.game_review_line_next);
        String recommendedPiece = pieceDescription(activity, root.play(recommendation).pieceAt(recommendation.to()), recommendation.to());
        boolean recommendedStep = reviewLineStep(reviewBranchStep(activity, R.string.game_review_recommended_branch, 1, blunder.bestLine().size()))
                && dialogNode(recommendedPiece) != null;
        captureFrame("game-review-why-recommended", "The recommended branch starts before g4 and replays its first legal move",
                image -> fromCommonRoot && recommendedStep && sameReviewBoard(board));

        clickReviewIcon(R.id.game_review_played_branch);
        String g2 = pieceDescription(activity, Side.WHITE, ChessPosition.Kind.PAWN, "g2");
        boolean playedStartsAtRoot = reviewLineStep(reviewBranchStep(activity, R.string.game_review_played_branch, 0, 2))
                && dialogNode(g2) != null;
        clickReviewIcon(R.id.game_review_line_next);
        String g4 = pieceDescription(activity, Side.WHITE, ChessPosition.Kind.PAWN, "g4");
        boolean playedG4 = reviewLineStep(reviewBranchStep(activity, R.string.game_review_played_branch, 1, 2))
                && dialogNode(g4) != null;
        clickReviewIcon(R.id.game_review_line_next);
        compare("The played branch replays g4 and the mating reply from the same starting position", true,
                playedStartsAtRoot && playedG4 && matingReplyVisible(activity) && sameReviewBoard(board));
        clickReviewIcon(R.id.game_review_actions);
        clickMenuItem(activity.getString(R.string.game_review_retry));
        tapDialog(activity.getString(android.R.string.cancel));
        var restoredSelection = reviewNode(R.id.game_review_selection, 4500);
        compare("Cancelling Try again keeps the played line and the original review open", true,
                restoredSelection != null && matingReplyVisible(activity) && sameReviewBoard(board) && archiveMatches(store, archiveId, match, review));
        // Leave the dialog open. The runner stops this process before the restore phase.
    }

    private void checkReviewFallback(Activity activity, Rect board, GameReview.Turn turn) throws IOException {
        var locale = activity.getResources().getConfiguration().getLocales().get(0);
        String played = String.format(locale, "%.1f%%", turn.playedPoints() / 10.0);
        String recommended = String.format(locale, "%.1f%%", turn.bestPoints() / 10.0);
        String depth = Integer.toString(turn.depth());
        String expected = "Scor estimat pentru Alb. Jucată " + played + " · Recomandată " + recommended
                + " · Adâncime " + depth + " semimutări";
        var frame = awaitRenderedFrame(image -> {
            var fallback = visibleReviewNode(R.id.game_review_reason);
            return fallback != null && expected.contentEquals(fallback.getText()) && sameReviewBoard(board);
        });
        if (frame != null) {
            try { saveFrame("game-review-why-score", frame); }
            finally { frame.recycle(); }
        }
        var observed = visibleReviewNode(R.id.game_review_reason);
        compare("Why shows both expected scores and search depth when the line proves no concrete reason",
                List.of(expected, true), List.of(observed == null ? "missing" : String.valueOf(observed.getText()), frame != null));
    }

    private void checkReviewExplanationRestore() throws Exception {
        enableReviewNodeIds();
        var match = ChessMatch.restore(REVIEW_MATE);
        var store = new ChessProgressStore(getTargetContext()).matches();
        String archiveId = store.archivedId(1);
        var review = GameReview.analyze(match, ignored -> { });
        var activity = openArchivedReview();
        var board = nodeBounds(reviewNode(R.id.game_review_board, 4500));
        boolean restored = matingReplyVisible(activity) && visibleReviewNode(R.id.game_review_reason) != null;
        captureFrame("game-review-why-restore", "A restarted process reopens the archived analysis on the played mating reply",
                image -> restored && !board.isEmpty() && sameReviewBoard(board));

        clickReviewIcon(R.id.game_review_actions);
        clickMenuItem(activity.getString(R.string.game_review_retry));
        tapDialog(activity.getString(R.string.match_confirm_continue));
        waitForIdleSync();
        var expected = match.continueFrom(2).checkpoint();
        var continuation = store.readMatch();
        compare("Confirming from the reopened review creates a separate game and keeps the archived analysis", true,
                continuation != null && store.activeMatchSlot() != 1 && expected.equals(continuation.checkpoint())
                        && match.checkpoint().equals(store.readMatch(1).checkpoint())
                        && archiveMatches(store, archiveId, match, review));
    }

    private void enableReviewNodeIds() {
        var automation = getUiAutomation();
        var service = automation.getServiceInfo();
        service.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(service);
    }

    private Activity openArchivedReview() throws Exception {
        var activity = launch(true);
        var list = openMatchList(activity, R.id.match_archive);
        chooseListEntry(list, 0);
        awaitListTransition(activity);
        tap(activity.findViewById(R.id.match_status));
        if (reviewNode(R.id.game_review_board, 4500) == null) throw new AssertionError("The archived review did not open");
        return activity;
    }

    private boolean matingReplyVisible(Activity activity) throws Exception {
        return reviewLineStep(reviewBranchStep(activity, R.string.game_review_played_branch, 2, 2))
                && dialogNode(pieceDescription(activity, Side.BLACK, ChessPosition.Kind.QUEEN, "h4")) != null;
    }

    private static String reviewBranchStep(Activity activity, int branch, int step, int total) {
        return activity.getString(R.string.game_review_branch_step, activity.getString(branch), step, total);
    }

    private static boolean archiveMatches(MatchStore store, String id, ChessMatch match, GameReview review) {
        var archived = store.readArchivedMatch(id);
        return archived != null && match.checkpoint().equals(archived.match().checkpoint())
                && review.equals(store.readReview(id, archived.match()));
    }

    private void checkLongGameGraph(ChessProgressStore store) throws Exception {
        var match = ChessMatch.restore("3|WHITE|ADVANCED|0|" + LONG_REVIEW_MOVES.strip().replace('\n', ' '));
        int targetPly = 184;
        if (match.ply() != 185 || match.result() != ChessMatch.Result.WHITE_WINS) {
            throw new AssertionError("Invalid long Game Review fixture");
        }
        // Cache a complete review to test graph navigation independently of engine timing.
        var player = new GameReview.Player(OptionalInt.empty(), Optional.empty(), 0, Map.of());
        var turns = new ArrayList<GameReview.Turn>();
        for (int ply = 1; ply <= match.ply(); ply++) {
            var move = match.moveAt(ply);
            int whitePoints = ply == targetPly ? 750 : 500;
            turns.add(new GameReview.Turn(ply, match.positionAt(ply - 1).turn(), move, GameReview.Grade.GOOD,
                    500, 500, 0, whitePoints, 1, List.of(move), List.of(move), 0));
        }
        var review = new GameReview(turns, null, 0, 500, player, player);
        var matches = store.matches();
        int slot = matches.activeMatchSlot();
        if (!matches.startMatch(slot, match, false, false, false) || !matches.saveReview(matches.archivedId(slot), match, review)) {
            throw new AssertionError("Could not seed the cached long Game Review");
        }
        var activity = launch(true);
        try {
            tap(activity.findViewById(R.id.match_status));
            var graph = nodeBounds(reviewNode(R.id.game_review_graph, 4500));
            var board = nodeBounds(reviewNode(R.id.game_review_board, 4500));
            int inset = Math.round(2 * activity.getResources().getDisplayMetrics().density);
            if (graph.width() <= 2 * inset || graph.height() <= 2 * inset || board.isEmpty()) {
                throw new AssertionError("Game Review graph or board is not visible: " + graph + ", " + board);
            }
            float x = inset + (graph.width() - 2 * inset) * targetPly / (float) match.ply();
            float y = inset + (graph.height() - 2 * inset) * .25f;
            String geometry = graph.width() + " px wide, inset " + inset + " px, former hit ply "
                    + Math.round(x / graph.width() * match.ply());
            tapAt(graph.left + x, graph.top + y);
            var turn = turns.get(targetPly - 1);
            String move = moveLabel(activity, turn);
            String grade = gradeLabel(activity, turn.grade());
            String caption = activity.getString(R.string.game_review_selected_move, move, grade);
            var selection = visibleReviewNode(R.id.game_review_selection);
            String actual = selection == null || selection.getText() == null ? "missing" : selection.getText().toString();
            boolean exactBoard = dialogNode(pieceDescription(activity, Side.BLACK, ChessPosition.Kind.KING, "h8"),
                    AccessibilityNodeInfo::isVisibleToUser, 500) != null
                    && dialogNode(pieceDescription(activity, Side.WHITE, ChessPosition.Kind.ROOK, "e8"),
                    AccessibilityNodeInfo::isVisibleToUser, 500) != null;
            var list = reviewNode(R.id.game_review_moves, 4500);
            var arguments = new Bundle();
            arguments.putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, targetPly);
            if (list == null || !list.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId(), arguments)) {
                throw new AssertionError("Could not scroll the review list to ply " + targetPly);
            }
            String rowText = activity.getString(R.string.game_review_move_row, move, grade, targetPly, match.ply());
            int readableHeight = Math.round(36 * activity.getResources().getDisplayMetrics().density);
            var screen = awaitRenderedFrame(image -> {
                var row = visibleReviewMoveRow(rowText, readableHeight);
                return row != null && hasColor(image, nodeBounds(row), 0xff3d4933, 20);
            });
            if (screen != null) {
                try { saveFrame("game-review-navigation", screen); }
                finally { screen.recycle(); }
            }
            compare("A plotted point in a 185-ply review selects ply 184 on the board and highlights its move row",
                    List.of(caption, true, true, true, geometry), List.of(actual, exactBoard, screen != null, sameReviewBoard(board), geometry));
        } finally { closeActivity(activity); }
    }

    private AccessibilityNodeInfo visibleReviewMoveRow(String description, int minimumHeight) {
        var window = getUiAutomation().getRootInActiveWindow();
        if (window == null) return null;
        return window.findAccessibilityNodeInfosByText(description).stream()
                .filter(row -> row.isVisibleToUser() && row.getContentDescription() != null
                        && description.contentEquals(row.getContentDescription())
                        && nodeBounds(row).height() >= minimumHeight)
                .findFirst().orElse(null);
    }

    private AccessibilityNodeInfo visibleReviewNode(int id) {
        var root = getUiAutomation().getRootInActiveWindow();
        if (root == null) return null;
        String name = getTargetContext().getResources().getResourceName(id);
        return root.findAccessibilityNodeInfosByViewId(name).stream().filter(AccessibilityNodeInfo::isVisibleToUser)
                .findFirst().orElse(null);
    }

    private AccessibilityNodeInfo reviewNode(int id, long timeoutMs) {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            var node = visibleReviewNode(id);
            if (node != null) return node;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private static Rect nodeBounds(AccessibilityNodeInfo node) {
        var bounds = new Rect();
        if (node != null) node.getBoundsInScreen(bounds);
        return bounds;
    }

    private boolean sameReviewBoard(Rect expected) {
        return expected.equals(nodeBounds(visibleReviewNode(R.id.game_review_board)));
    }

    private boolean reviewIcon(Activity activity, int id, int label) {
        var node = visibleReviewNode(id);
        if (node == null || node.getContentDescription() == null
                || !activity.getString(label).contentEquals(node.getContentDescription())) return false;
        var bounds = nodeBounds(node);
        int touchSize = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        return bounds.width() >= touchSize && bounds.height() >= touchSize
                && (node.getText() == null || node.getText().length() == 0);
    }

    private boolean reviewSelection(String text) {
        var node = visibleReviewNode(R.id.game_review_selection);
        return node != null && node.getText() != null && text.contentEquals(node.getText());
    }

    private boolean reviewLineStep(String text) {
        var node = visibleReviewNode(R.id.game_review_selection);
        return node != null && node.getContentDescription() != null && node.getContentDescription().toString().contains(text);
    }

    private void clickReviewIcon(int id) throws Exception {
        var node = reviewNode(id, 4500);
        var bounds = nodeBounds(node);
        if (node == null || !node.isEnabled() || !node.isClickable() || bounds.isEmpty()) {
            throw new AssertionError("Review icon unavailable: " + getTargetContext().getResources().getResourceName(id));
        }
        tapAt(bounds.exactCenterX(), bounds.exactCenterY());
        getUiAutomation().waitForIdle(100, 2500);
    }

    private void swipeReview(Rect bounds, boolean forward) {
        float x = bounds.left + bounds.width() * 0.2f;
        float upper = bounds.top + bounds.height() * 0.22f;
        float lower = bounds.top + bounds.height() * 0.72f;
        float start = forward ? lower : upper;
        float end = forward ? upper : lower;
        long down = SystemClock.uptimeMillis();
        for (int step = 0; step <= 8; step++) {
            int action = step == 0 ? MotionEvent.ACTION_DOWN : step == 8 ? MotionEvent.ACTION_UP : MotionEvent.ACTION_MOVE;
            var event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, start + (end - start) * step / 8, 0);
            try {
                sendPointerSync(event);
            } finally {
                event.recycle();
            }
            SystemClock.sleep(35);
        }
        waitForIdleSync();
    }

    private boolean showsRating(Activity activity, int role, ro.atf.chess.GameReview.Player player) throws Exception {
        if (player.rating().isEmpty() || player.accuracy().isEmpty()) return false;
        var rating = player.rating().orElseThrow();
        if (rating.low() == null) return false;
        String interval = "≈ " + rating.elo() + " (" + rating.low() + "–" + rating.high() + ")";
        return dialogNode(activity.getString(role, player.accuracy().getAsInt() + "%", interval), AccessibilityNodeInfo::isVisibleToUser, 500) != null;
    }

    private String moveLabel(Activity activity, ro.atf.chess.GameReview.Turn turn) {
        Move move = turn.played();
        String from = BoardSquare.name(move.from());
        String to = BoardSquare.name(move.to());
        String squares = move.promotion() == null ? activity.getString(R.string.solution_move, from, to)
                : activity.getString(R.string.solution_promotion, from, to, activity.getString(BoardView.kindLabel(move.promotion())));
        return (turn.ply() + 1) / 2 + (turn.side() == ChessPosition.Side.WHITE ? ". " : "… ") + squares;
    }

    private String gradeLabel(Activity activity, ro.atf.chess.GameReview.Grade grade) {
        return activity.getString(switch (grade) {
            case BOOK -> R.string.game_review_book;
            case FORCED -> R.string.game_review_forced;
            case BRILLIANT -> R.string.game_review_brilliant;
            case GREAT -> R.string.game_review_great;
            case BEST -> R.string.game_review_best;
            case EXCELLENT -> R.string.game_review_excellent;
            case GOOD -> R.string.game_review_good;
            case INACCURACY -> R.string.game_review_inaccuracy;
            case MISTAKE -> R.string.game_review_mistake;
            case MISS -> R.string.game_review_miss;
            case BLUNDER -> R.string.game_review_blunder;
        });
    }

    private String pieceDescription(Activity activity, ChessPosition.Piece piece, int square) {
        if (piece == null) return null;
        return pieceDescription(activity, piece.side(), piece.kind(), BoardSquare.name(square));
    }

    private String pieceDescription(Activity activity, ChessPosition.Side side, ChessPosition.Kind kind, String square) {
        return activity.getString(R.string.piece_square, square,
                activity.getString(BoardView.sideLabel(side)), activity.getString(BoardView.kindLabel(kind)));
    }

    private void checkMatchActions(ChessProgressStore store) throws Exception {
        var equal = ChessMatch.restore("1|e2e4 e7e5");
        if (!store.matches().saveMatch(equal, false)) throw new AssertionError("Could not seed the balanced game");
        var game = launch();
        tap(game.findViewById(R.id.match_actions));
        String resign = game.getString(R.string.match_resign);
        String offer = game.getString(R.string.match_offer_draw);
        if (menuItem(offer) == null || menuItem(resign) == null) throw new AssertionError("Both match actions must be visible");
        Bitmap menuFrame = getUiAutomation().takeScreenshot();
        if (menuFrame == null) throw new AssertionError("Missing match actions screenshot");
        try { saveFrame("game-review-actions", menuFrame); } finally { menuFrame.recycle(); }
        clickMenuItem(resign);
        tapDialog(game.getString(android.R.string.cancel));
        compare("Cancelling resignation preserves the saved game", equal.checkpoint(), store.matches().readMatch().checkpoint());
        tap(game.findViewById(R.id.match_actions));
        clickMenuItem(resign);
        tapDialog(game.getString(R.string.match_confirm_resign));
        compare("Confirming resignation saves the opponent's win", ChessMatch.Result.BLACK_WINS, store.matches().readMatch().result());
        closeActivity(game);
        if (!store.matches().saveMatch(equal, false)) throw new AssertionError("Could not restore the balanced game");
        game = launch();
        tap(game.findViewById(R.id.match_actions));
        clickMenuItem(offer);
        compare("The AI accepts the draw in a balanced position", ChessMatch.Result.AGREED_DRAW, store.matches().readMatch().result());
        closeActivity(game);
        var ahead = ChessMatch.restore("1|e2e4 d7d5 f1b5 c7c6 b5c6 b7c6");
        if (!store.matches().saveMatch(ahead, false)) throw new AssertionError("Could not seed the material imbalance");
        game = launch();
        tap(game.findViewById(R.id.match_actions));
        clickMenuItem(offer);
        compare("The AI declines a draw when ahead and preserves the checkpoint", ahead.checkpoint(), store.matches().readMatch().checkpoint());
    }

    private void closeActivity(Activity activity) {
        runOnMainSync(activity::finish);
        waitForIdleSync();
    }

    private AccessibilityNodeInfo menuItem(String label) throws Exception {
        return dialogNode(label, node -> node.isVisibleToUser() && node.isEnabled()
                && node.getText() != null && label.contentEquals(node.getText()));
    }

    private void clickMenuItem(String label) throws Exception {
        getUiAutomation().waitForIdle(150, 2500);
        var item = menuItem(label);
        if (item == null) throw new AssertionError("Menu item unavailable: " + label);
        for (var target = item; target != null; target = target.getParent()) {
            if (target.isClickable() && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                getUiAutomation().waitForIdle(150, 2500);
                return;
            }
        }
        var bounds = new Rect();
        item.getBoundsInScreen(bounds);
        if (bounds.isEmpty()) throw new AssertionError("Menu item has no visible bounds: " + label);
        tapAt(bounds.exactCenterX(), bounds.exactCenterY());
        getUiAutomation().waitForIdle(150, 2500);
        if (dialogNode(label, node -> node.isVisibleToUser() && node.getText() != null
                && label.contentEquals(node.getText()), 500) != null) {
            throw new AssertionError("Native menu did not dismiss after selecting: " + label);
        }
    }

    private void checkOpenings() throws Exception {
        var home = launch();
        var monitor = addMonitor(OpeningActivity.class.getName(), null, false);
        tap(home.findViewById(R.id.open_openings));
        Activity lesson = waitForMonitorWithTimeout(monitor, 7000);
        if (lesson == null) throw new AssertionError("Home did not open the Openings screen");
        waitForIdleSync();
        var cards = (ViewGroup) lesson.findViewById(R.id.opening_catalog);
        var catalog = ro.atf.chess.OpeningCatalog.load();
        compare("The Openings button exposes every configured lesson", catalog.size(), cards.getChildCount());
        captureFrame("opening-catalog", "The first opening cards are fully visible",
                image -> fullyVisible(cards.getChildAt(0)) && fullyVisible(cards.getChildAt(1)));
        var scroll = (android.widget.ScrollView) cards.getParent();
        var last = cards.getChildAt(cards.getChildCount() - 1);
        Bitmap beforeScroll = getUiAutomation().takeScreenshot();
        if (beforeScroll == null) throw new AssertionError("Missing opening catalog frame");
        try {
            var catalogBounds = screenBounds(scroll);
            runOnMainSync(() -> scroll.scrollTo(0, cards.getHeight()));
            captureFrame("opening-catalog-end", "The last opening card is fully rendered after scrolling",
                    image -> fullyVisible(last) && catalogScrolled(beforeScroll, image, catalogBounds));
        } finally { beforeScroll.recycle(); }
        tap(last);
        var locale = lesson.getResources().getConfiguration().getLocales().get(0);
        compare("The last lesson opens with the King’s Indian plan", true,
                dialogNode(catalog.get(catalog.size() - 1).title().in(locale)) != null);
        tapDialog(lesson.getString(R.string.opening_train_white));
        awaitOpeningBoard(lesson);
        systemBack();
        var firstPage = (ViewGroup) lesson.findViewById(R.id.opening_catalog);
        tap(firstPage.getChildAt(0));
        var title = ro.atf.chess.OpeningCatalog.load().get(0).title().in(lesson.getResources().getConfiguration().getLocales().get(0));
        compare("The first lesson explains the Italian line and offers both colors", true,
                dialogNode(title) != null && dialogNode(lesson.getString(R.string.opening_train_white)) != null
                        && dialogNode(lesson.getString(R.string.opening_train_black)) != null);
        tapDialog(lesson.getString(R.string.opening_train_white));
        var board = (BoardView) awaitOpeningBoard(lesson);
        var status = (TextView) lesson.findViewById(R.id.opening_progress);
        var explanation = (TextView) lesson.findViewById(R.id.opening_message);
        var initial = screenBounds(board);
        checkOpeningBoardGeometry(lesson, board);
        tap(cell(board, "d2"));
        tap(cell(board, "d4"));
        compare("A legal alternative receives feedback and leaves the board at the initial step", true,
                status.getText().toString().startsWith("0 / 12") && lesson.getString(R.string.opening_off_line).contentEquals(explanation.getText())
                        && "d4".contentEquals(cell(board, "d4").getContentDescription()));
        View hint = lesson.findViewById(R.id.opening_theory_hint);
        tap(hint);
        compare("The first lesson hint reveals the source square only", true,
                cell(board, "e2").getContentDescription().toString().contains(lesson.getString(R.string.hint_origin, ""))
                        && "e4".contentEquals(cell(board, "e4").getContentDescription()));
        tap(hint);
        captureFrame("opening-hint", "The opening lesson draws its arrow and explains the move",
                image -> hasColor(image, cell(board, "e3"), HINT_TINT, 12) && fullyVisible(explanation)
                        && cell(board, "e2").isSelected());
        tap(cell(board, "e4"));
        awaitOpeningPly(status, 2);
        compare("Tapping only the hinted destination plays e4 and the configured e5 reply once", List.of(true, initial, true),
                List.of(status.getText().toString().startsWith("2 / 12"), screenBounds(board),
                        cell(board, "e5").getContentDescription().toString().contains(lesson.getString(R.string.pawn))));
        tap(lesson.findViewById(R.id.opening_toggle_ai));
        var bulb = lesson.findViewById(R.id.match_hint);
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (!bulb.isEnabled() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
        compare("Optional AI evaluates the current position without advancing the lesson", true, bulb.isEnabled()
                && lesson.findViewById(R.id.match_evaluation_bar).getVisibility() == View.VISIBLE && status.getText().toString().startsWith("2 / 12"));
        var analysisText = (TextView) lesson.findViewById(R.id.match_analysis);
        String positionScore = analysisText.getText().toString();
        tap(bulb);
        captureFrame("opening-ai", "The AI recommendation keeps the calibrated position score and the previous-move explanation visible",
                image -> bulb.isSelected() && hasTwoRenderedLines(image, analysisText) && positionScore.startsWith("+0")
                        && analysisText.getText().toString().startsWith(positionScore + "\n")
                        && explanation.getText().toString().startsWith("1… " + lesson.getString(R.string.solution_move, "e7", "e5")));
        tap(cell(board, "f3"));
        awaitOpeningPly(status, 4);
        compare("An AI move matching the lesson can be played by its highlighted destination", true,
                status.getText().toString().startsWith("4 / 12")
                        && cell(board, "f3").getContentDescription().toString().contains(lesson.getString(R.string.knight)));
        deadline = SystemClock.uptimeMillis() + 15_000;
        while (!bulb.isEnabled() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
        tap(bulb);
        compare("The Ruy López suggestion is identified as an AI alternative to the Italian lesson", true,
                explanation.getText().toString().startsWith(lesson.getString(R.string.opening_ai_alternative))
                        && cell(board, "b5").getContentDescription().toString().contains(lesson.getString(R.string.hint_destination, ""))
                        && !cell(board, "f1").isSelected());
        tap(cell(board, "f1"));
        tap(cell(board, "b5"));
        compare("Trying the AI alternative leaves the authored lesson at the same move", true,
                status.getText().toString().startsWith("4 / 12")
                        && lesson.getString(R.string.opening_off_line).contentEquals(explanation.getText()));
        View previous = lesson.findViewById(R.id.opening_previous);
        View next = lesson.findViewById(R.id.opening_next);
        View practice = lesson.findViewById(R.id.opening_practice);
        tap(previous);
        compare("Previous shows exactly the board before Black's second reply", true,
                status.getText().toString().startsWith("3 / 12")
                        && "c6".contentEquals(cell(board, "c6").getContentDescription()) && !cell(board, "b8").isEnabled());
        tap(next);
        compare("Next returns to the played position without adding another move", true,
                status.getText().toString().startsWith("4 / 12")
                        && cell(board, "c6").getContentDescription().toString().contains(lesson.getString(R.string.knight))
                        && cell(board, "f1").isEnabled());
        tap(status);
        compare("The lesson offers an annotated review of moves already played", true, dialogNode(lesson.getString(R.string.opening_review)) != null);
        tapDialog(lesson.getString(R.string.opening_initial));
        deadline = SystemClock.uptimeMillis() + 15_000;
        while (!bulb.isEnabled() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
        compare("The historical position can be analyzed on the anchored, read-only board", true,
                status.getText().toString().contains(lesson.getString(R.string.opening_reviewing))
                        && "e4".contentEquals(cell(board, "e4").getContentDescription()) && !cell(board, "e2").isEnabled()
                        && screenBounds(board).equals(initial) && bulb.isEnabled() && !bulb.isSelected());
        captureFrame("opening-replay", "The review exposes a separate replay action and both step controls",
                image -> fullyVisible(practice) && fullyVisible(previous) && fullyVisible(next)
                        && !previous.isEnabled() && next.isEnabled()
                        && lesson.getString(R.string.opening_replay).contentEquals(((TextView) practice).getText()));
        tap(practice);
        compare("Play from here starts practice on the selected position", true,
                status.getText().toString().startsWith("0 / 12") && cell(board, "e2").isEnabled()
                        && hint.isShown() && !previous.isEnabled());
        tap(next);
        SystemClock.sleep(650);
        compare("Forward plays exactly e4 and waits for the next explicit step", true,
                status.getText().toString().startsWith("1 / 12") && "e5".contentEquals(cell(board, "e5").getContentDescription())
                        && lesson.getString(R.string.opening_continue).contentEquals(((TextView) practice).getText()));
        tap(next);
        compare("Another Forward plays the authored e5 reply and returns the turn to the learner", true,
                status.getText().toString().startsWith("2 / 12") && cell(board, "g1").isEnabled()
                        && cell(board, "e5").getContentDescription().toString().contains(lesson.getString(R.string.pawn)));
        tap(previous);
        systemBack();
        compare("System Back returns from review to the current practice position", true,
                status.getText().toString().startsWith("2 / 12") && cell(board, "g1").isEnabled() && screenBounds(board).equals(initial));
        systemBack();
        var returnedCards = (ViewGroup) lesson.findViewById(R.id.opening_catalog);
        tap(returnedCards.getChildAt(0));
        tapDialog(lesson.getString(R.string.opening_train_black));
        var reversed = (BoardView) awaitOpeningBoard(lesson);
        var blackProgress = (TextView) lesson.findViewById(R.id.opening_progress);
        awaitOpeningPly(blackProgress, 1);
        compare("Training as Black flips the board and starts with the scripted White move", true,
                blackProgress.getText().toString().startsWith("1 / 12") && "h1".equals(coordinate(reversed.getChildAt(0)))
                        && cell(reversed, "e4").getContentDescription().toString().contains(lesson.getString(R.string.pawn)));
    }

    private void awaitOpeningPly(TextView progress, int ply) {
        long deadline = SystemClock.uptimeMillis() + 6000;
        String expected = ply + " / ";
        boolean[] reached = {false};
        do {
            runOnMainSync(() -> reached[0] = progress.getText().toString().startsWith(expected));
            if (reached[0]) return;
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
    }

    private static boolean catalogScrolled(Bitmap before, Bitmap after, Rect area) {
        int right = Math.min(area.right, Math.min(before.getWidth(), after.getWidth()));
        int bottom = Math.min(area.bottom, Math.min(before.getHeight(), after.getHeight()));
        int changed = 0;
        for (int y = Math.max(0, area.top); y < bottom; y += 8) {
            for (int x = Math.max(0, area.left); x < right; x += 8) {
                if (before.getPixel(x, y) != after.getPixel(x, y) && ++changed >= 100) return true;
            }
        }
        return false;
    }

    private View awaitOpeningBoard(Activity lesson) {
        long deadline = SystemClock.uptimeMillis() + 6000;
        var current = new View[1];
        do {
            runOnMainSync(() -> current[0] = lesson.findViewById(R.id.opening_board));
            if (current[0] != null && current[0].getWidth() > 0) return current[0];
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Color selection did not display the lesson board");
    }

    private void tapDialog(String label) throws Exception {
        var node = dialogNode(label, candidate -> candidate.isClickable() && candidate.isEnabled()
                && candidate.getText() != null && label.equalsIgnoreCase(candidate.getText().toString()));
        if (node == null) throw new AssertionError("Dialog touch target unavailable: " + label);
        var bounds = new Rect();
        node.getBoundsInScreen(bounds);
        tapAt(bounds.exactCenterX(), bounds.exactCenterY());
    }

    private void checkQuests() throws Exception {
        Activity activity = launch();
        if ("prepare".equals(phase)) {
            boolean home = activity.findViewById(R.id.new_match).isEnabled() && activity.findViewById(R.id.edit_player_name).isEnabled()
                    && activity.findViewById(R.id.resume_match).getVisibility() == View.GONE;
            openPuzzles(activity);
            var menu = viewsOf(activity);
            compare("Home exposes play and profile, and Puzzles groups six playable chapters", true, home &&
                    button(activity, menu, R.string.open_mate_one).isEnabled() && button(activity, menu, R.string.open_mate_two).isEnabled()
                            && button(activity, menu, R.string.open_mate_three).isEnabled() && button(activity, menu, R.string.open_tactics).isEnabled()
                            && button(activity, menu, R.string.open_training).isEnabled()
                            && button(activity, menu, R.string.open_intermezzo).isEnabled());
            var playable = List.of(button(activity, menu, R.string.open_mate_one), button(activity, menu, R.string.open_mate_two),
                    button(activity, menu, R.string.open_mate_three), button(activity, menu, R.string.open_tactics));
            captureFrame("menu", "The opening viewport renders the puzzle chapters", image -> playable.stream().allMatch(entry ->
                    fullyVisible(entry) && hasColor(image, entry, entry.getCurrentTextColor(), 32)));
            runOnMainSync(activity::finish);
            checkRomanianCompactScreen();
            activity = launch();
            checkTactics(activity);
            openChapter(activity, R.string.open_training);
            var training = viewsOf(activity);
            playTactic(activity, findBoard(training), QuestCatalog.loadTraining().get(0));
            tap(button(activity, training, R.string.next_quest));
            switchChapter(activity, R.string.open_mate_one);
            var views = viewsOf(activity);
            prepare(activity, views, findBoard(views));
        } else if ("restore".equals(phase)) {
            finishMateInThree(activity);
            switchChapter(activity, R.string.open_tactics);
            var views = viewsOf(activity);
            var lastTactic = QuestCatalog.loadTactics().get(1);
            compare("A real process restart keeps the tactics chapter position and its two awards", true,
                    hasText(views, activity.getString(R.string.tactics_progress, 2, TACTICS_SIZE))
                            && hasText(views, solvedTactic(activity, lastTactic))
                            && hasText(views, activity.getString(R.string.chapter_points, 22)));
            switchChapter(activity, R.string.open_training);
            var training = viewsOf(activity);
            int trainingSize = QuestCatalog.loadTraining().size();
            compare("Training reopens its second unsolved puzzle after process restart", true,
                    hasText(training, activity.getString(R.string.training_progress, 2, trainingSize))
                            && chapterCount(activity, training, 1, trainingSize)
                            && !button(activity, training, R.string.next_quest).isEnabled());
            switchChapter(activity, R.string.open_mate_two);
            restore(activity);
        } else throw new IllegalArgumentException("Unknown sanity phase: " + phase);
    }

    private void playTactic(Activity activity, BoardView board, QuestCatalog.Quest quest) {
        var store = new ChessProgressStore(getTargetContext()).quests();
        for (int stage = 0; stage < quest.playerMoves(); stage++) {
            playMove(board, quest.playerMove(stage));
            if (stage == quest.playerMoves() - 1) break;
            int ready = (stage + 1) * 2;
            long deadline = SystemClock.uptimeMillis() + 2500;
            while (store.ply(quest.id()) != ready && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
            waitForIdleSync();
            compare("The authored tactical defense advances once without solving", true,
                    store.ply(quest.id()) == ready && !button(activity, viewsOf(activity), R.string.next_quest).isEnabled());
        }
    }

    private String solvedTactic(Activity activity, QuestCatalog.Quest quest) {
        return activity.getString(R.string.tactics_solved, QuestPayoffDialog.material(activity, quest.materialChange(), quest.line().size() == 1));
    }

    private void checkTactics(Activity activity) throws Exception {
        openChapter(activity, R.string.open_tactics);
        var views = viewsOf(activity);
        var board = findBoard(views);
        var next = button(activity, views, R.string.next_quest);
        var bulb = guidance(activity, R.string.hint);
        compare("The accessible bulb fits below the board and Next starts locked", true,
                !next.isEnabled() && bulb instanceof android.widget.ImageButton && compactLayoutFits(activity, views, board, R.string.hint));
        var tactics = QuestCatalog.loadTactics();
        for (int index = 0; index < 2; index++) {
            playTactic(activity, board, tactics.get(index));
            if (index == 0) tap(next);
        }
        String streak = activity.getString(R.string.reward_streak, 2);
        compare("A tactics solve renders its result and unlocks Next", true,
                hasText(views, solvedTactic(activity, tactics.get(1))) && next.isEnabled());
        TextView badge = views.stream().filter(TextView.class::isInstance).map(TextView.class::cast)
                .filter(view -> streak.contentEquals(view.getText())).findFirst().orElseThrow();
        captureFrame("tactics-streak", "Tactics shows the amber flame below the chapter progress",
                image -> fullyVisible(badge) && badge.getCompoundDrawables()[0] != null && hasColor(image, badge, 0xffffbc78, 16));
        var completed = QuestPayoff.forQuest(tactics.get(1));
        tap(guidance(activity, R.string.why_tactic));
        var finish = reviewNode(R.id.quest_payoff_move, 2500);
        var replay = reviewNode(R.id.quest_payoff_board, 2500);
        compare("The tactic explanation opens on the final board and announces the actual net gain", true,
                finish != null && replay != null && finish.getContentDescription() != null
                        && finish.getContentDescription().toString().contains(QuestPayoffDialog.material(activity, completed.last().materialChange(), completed.plies() == 1)));
        var back = reviewNode(R.id.quest_payoff_previous, 2500);
        compare("The explanation lets the player rewind to the previous position", true,
                back != null && back.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        waitForIdleSync();
        compare("The previous position enables forward replay", true,
                reviewNode(R.id.quest_payoff_next, 2500) != null && visibleReviewNode(R.id.quest_payoff_next).isEnabled());
        clickDialog(activity.getString(android.R.string.ok));
        checkDefenseStudy(activity);
        tap(button(activity, views, R.string.main_menu));
        var menu = viewsOf(activity);
        var quests = new ChessProgressStore(getTargetContext()).quests();
        var tacticIds = new ArrayList<String>();
        for (var tactic : tactics) tacticIds.add(tactic.id());
        var totals = quests.readRewards().summarize(tacticIds, quests.read(tacticIds));
        int totalQuests = CATALOG_SIZE + MATE_TWO_SIZE + MATE_THREE_SIZE + TACTICS_SIZE
                + QuestCatalog.loadTraining().size() + QuestCatalog.loadIntermezzo().size();
        compare("The puzzle menu displays the saved chapter and overall totals", true,
                hasText(menu, activity.getString(R.string.chapter_totals, totals.solved(), totals.total(), totals.points()))
                        && hasText(menu, activity.getString(R.string.all_quest_totals, totals.solved(), totalQuests, totals.points())));
    }

    private void checkTacticsExplanation() throws Exception {
        enableReviewNodeIds();
        var progressStore = new ChessProgressStore(getTargetContext());
        var tactics = QuestCatalog.loadTactics();
        var order = new ArrayList<String>();
        for (var quest : tactics) order.add(quest.id());

        var simple = launchSavedQuest(progressStore, order, "tactic-002");
        var views = viewsOf(simple);
        var quest = tactics.stream().filter(row -> row.id().equals("tactic-002")).findFirst().orElseThrow();
        playMove(findBoard(views), quest.solution());
        var savedProgress = progressStore.quests().read(order);
        var savedRewards = progressStore.quests().readRewards();
        tap(guidance(simple, R.string.why_tactic));
        var finish = reviewNode(R.id.quest_payoff_move, 2500);
        var forward = reviewNode(R.id.quest_payoff_next, 2500);
        captureFrame("tactics-explanation", "Tactic 002 opens the normal explanation on its final move", image ->
                payoffBoardRendered(image) && payoffMoveHighlighted(image, quest.solution(), true)
                        && payoffShows(finish, simple, quest.solution())
                        && finish.getContentDescription().toString().contains(QuestPayoffDialog.material(simple, 500, true))
                        && forward != null && !forward.isEnabled());
        clickReviewIcon(R.id.quest_payoff_previous);
        var start = reviewNode(R.id.quest_payoff_move, 2500);
        compare("The normal explanation rewinds and allows forward replay", true, start != null
                && simple.getString(R.string.tactics_replay_start).contentEquals(start.getText())
                        && payoffCanAdvance());
        clickDialog(simple.getString(android.R.string.ok));
        compare("Closing the normal explanation preserves the solved tactic and points", true, savedProgress.currentSolved()
                && savedProgress.equals(progressStore.quests().read(order)) && savedRewards.equals(progressStore.quests().readRewards()));
        runOnMainSync(simple::finish);

        var studied = launchSavedQuest(progressStore, order, "tactic-043");
        var studiedQuest = tactics.stream().filter(row -> row.id().equals("tactic-043")).findFirst().orElseThrow();
        playMove(findBoard(viewsOf(studied)), studiedQuest.solution());
        var studiedProgress = progressStore.quests().read(order);
        var studiedRewards = progressStore.quests().readRewards();
        tap(guidance(studied, R.string.why_tactic));
        var key = reviewNode(R.id.quest_payoff_move, 2500);
        compare("Tactic 043 opens the defense study on the accepted key", true,
                payoffShows(key, studied, Move.fromUci("e2h5")) && payoffCanAdvance());
        clickReviewIcon(R.id.quest_payoff_next);
        var defense = Move.fromUci("h7g8");
        captureFrame("tactics-defense", "The defense study replays Kg8 and shows the material on the board",
                image -> {
                    var reply = visibleReviewNode(R.id.quest_payoff_move);
                    return payoffShows(reply, studied, defense)
                            && reply.getContentDescription().toString().contains(QuestPayoffDialog.materialAtStep(studied, 100))
                            && payoffBoardRendered(image) && payoffMoveHighlighted(image, defense, false);
                });
        clickDialog(studied.getString(android.R.string.ok));
        compare("Closing the defense study preserves the solved tactic and points", true, studiedProgress.currentSolved()
                && studiedProgress.equals(progressStore.quests().read(order)) && studiedRewards.equals(progressStore.quests().readRewards()));
    }

    private static boolean payoffShows(AccessibilityNodeInfo node, Activity activity, Move move) {
        return node != null && node.getText() != null && node.getContentDescription() != null
                && node.getText().toString().contains(ChessActivity.notation(activity, move));
    }

    private void checkTrainingFinish() throws Exception {
        enableReviewNodeIds();
        var store = new ChessProgressStore(getTargetContext()).quests();
        var order = List.of("training-069");
        if (!store.save(store.read(order), store.readRewards(), 10)) throw new AssertionError("Could not prepare stage six");
        var activity = launch();
        var views = viewsOf(activity);
        var board = findBoard(views);
        var before = store.read(order);
        var goal = activity.getString(R.string.tactics_final_capture);
        compare("Stage six shows the immediate material objective", true, hasText(views, goal));

        playMove(board, Move.fromUci("c4f7"));
        compare("Bxf7+ leaves the exercise open", true, hasText(views, activity.getString(R.string.tactics_try_again))
                && store.read(order).equals(before) && store.ply(before.currentId()) == 10);
        playMove(board, Move.fromUci("d1d8"));
        var message = activity.getString(R.string.tactics_alternative_available, "Rd8+");
        var status = views.stream().filter(TextView.class::isInstance).map(TextView.class::cast)
                .filter(view -> message.contentEquals(view.getText())).findFirst().orElseThrow();
        var rewards = store.readRewards();
        tap(status);
        captureFrame("training-finish", "The puzzle explains Qxb2 and the delayed capture after Rd8+", image ->
                dialogContains("Qxb2") && dialogContains("Qxd1+") && dialogContains("Kg7"));
        clickDialog(activity.getString(R.string.tactics_alternative_line, "Rd8+"));
        clickReviewIcon(R.id.quest_payoff_next);
        clickReviewIcon(R.id.quest_payoff_next);
        var capture = Move.fromUci("c1b2");
        captureFrame("training-finish-line", "The replay shows Qxb2 on its third ply and +5 material", image ->
                payoffShows(visibleReviewNode(R.id.quest_payoff_move), activity, capture) && payoffMoveHighlighted(image, capture, false)
                        && dialogContains("+5"));
        clickDialog(activity.getString(android.R.string.ok));
        compare("Studying the alternative preserves stage six and its unawarded score", true,
                before.equals(store.read(order)) && rewards.equals(store.readRewards()) && store.ply(before.currentId()) == 10);
        playMove(board, capture);
        compare("Playing Qxb2 solves the puzzle and earns exactly ten points", true,
                store.read(order).currentSolved() && "5:c1b2".equals(store.finish(before.currentId()))
                        && Integer.valueOf(10).equals(store.readRewards().points().get(before.currentId())));
        runOnMainSync(activity::finish);
        var reopened = launch();
        tap(guidance(reopened, R.string.why_tactic));
        boolean explanationRestored = dialogContains("Qxb2") && dialogContains("Qxd1+");
        clickDialog(reopened.getString(R.string.tactics_puzzle_replay));
        compare("The explanation remains available after reopening the solved puzzle", true,
                explanationRestored && payoffShows(reviewNode(R.id.quest_payoff_move, 2500), reopened, capture)
                        && dialogContains("11/11") && store.read(order).currentSolved()
                        && Integer.valueOf(10).equals(store.readRewards().points().get(before.currentId())));
        clickDialog(reopened.getString(android.R.string.ok));
    }

    private boolean dialogContains(String text) {
        var root = getUiAutomation().getRootInActiveWindow();
        return root != null && !root.findAccessibilityNodeInfosByText(text).isEmpty();
    }

    private void checkPuzzleMateReplay() throws Exception {
        enableReviewNodeIds();
        var store = new ChessProgressStore(getTargetContext()).quests();
        var two = QuestCatalog.loadMateInTwo().get(0);
        var twoOrder = List.of(two.id());
        if (!store.save(store.read(twoOrder), store.readRewards(), 2)) throw new AssertionError("Could not prepare the second move");
        var activity = launch(true);
        var views = viewsOf(activity);
        var board = findBoard(views);
        var caption = (TextView) activity.findViewById(R.id.quest_guidance_label);
        boolean piece = activity.getString(R.string.quest_hint_next_piece).contentEquals(caption.getText());
        tap(caption);
        boolean move = activity.getString(R.string.quest_hint_next_move).contentEquals(caption.getText());
        tap(guidance(activity, R.string.show_solution));
        boolean compact = compactLayoutFits(activity, views, board, R.string.why_mate);
        captureFrame("puzzle-mate-replay", "The Romanian bulb names its next action at each step and fits in a short window", image ->
                piece && move && activity.getString(R.string.quest_hint_next_explanation).contentEquals(caption.getText())
                        && compact && fullyVisible(caption)
                        && hasColor(image, caption, caption.getCurrentTextColor(), 4));
        var progress = store.read(twoOrder);
        var rewards = store.readRewards();
        tap(guidance(activity, R.string.why_mate));
        boolean opensAfterDefense = payoffShows(reviewNode(R.id.quest_payoff_move, 2500), activity, two.defenseAfter(0));
        clickReviewIcon(R.id.quest_payoff_next);
        captureFrame("puzzle-mate-two", "Mate in two opens after the saved defense and reaches the actual mating square", image ->
                opensAfterDefense && dialogContains(activity.getString(R.string.mate_replay_done))
                        && payoffShows(visibleReviewNode(R.id.quest_payoff_move), activity, two.matingMove())
                        && payoffMoveHighlighted(image, two.matingMove(), two.position().turn() == Side.BLACK) && !payoffCanAdvance());
        clickDialog(activity.getString(android.R.string.ok));
        compare("Closing the two-move replay preserves the unsolved puzzle, stage and rewards", true,
                !progress.currentSolved() && progress.equals(store.read(twoOrder)) && store.ply(two.id()) == 2 && rewards.equals(store.readRewards()));
        runOnMainSync(activity::finish);

        var three = QuestCatalog.loadMateInThree().get(0);
        var threeOrder = List.of(three.id());
        if (!store.save(store.read(threeOrder).select(three.id()), store.readRewards(), 4)) {
            throw new AssertionError("Could not prepare the third move");
        }
        var lastStage = launch();
        tap(button(lastStage, viewsOf(lastStage), R.string.flip));
        playMove(findBoard(viewsOf(lastStage)), three.matingMove());
        var solved = store.read(threeOrder);
        var scored = store.readRewards();
        tap(guidance(lastStage, R.string.why_mate));
        boolean beginsAtStart = dialogContains(lastStage.getString(R.string.tactics_replay_start));
        var replayBoard = nodeBounds(reviewNode(R.id.quest_payoff_board, 2500));
        for (int ply = 0; ply < 4; ply++) clickReviewIcon(R.id.quest_payoff_next);
        clickReviewIcon(R.id.quest_payoff_previous);
        clickReviewIcon(R.id.quest_payoff_next);
        captureFrame("puzzle-mate-three", "Mate in three replays the second defense and can step back without moving the board",
                image -> beginsAtStart && replayBoard.equals(nodeBounds(visibleReviewNode(R.id.quest_payoff_board)))
                        && payoffShows(visibleReviewNode(R.id.quest_payoff_move), lastStage, three.defenseAfter(1))
                        && payoffMoveHighlighted(image, three.defenseAfter(1), three.position().turn() == Side.WHITE));
        clickDialog(lastStage.getString(android.R.string.ok));
        compare("Closing the three-move replay keeps the solved puzzle and its award", true,
                solved.currentSolved() && solved.equals(store.read(threeOrder)) && scored.equals(store.readRewards())
                        && Integer.valueOf(10).equals(scored.points().get(three.id())));
    }

    private boolean payoffCanAdvance() {
        var next = visibleReviewNode(R.id.quest_payoff_next);
        return next != null && next.isEnabled();
    }

    private void checkPuzzleDanger() throws Exception {
        enableReviewNodeIds();
        var store = new ChessProgressStore(getTargetContext()).quests();
        var mate = QuestCatalog.load().get(0);
        var mateOrder = List.of(mate.id());
        var activity = launch();
        openPuzzles(activity);
        openChapter(activity, R.string.open_mate_one);
        playMove(findBoard(viewsOf(activity)), mate.matingMove());
        var progress = store.read(mateOrder);
        var rewards = store.readRewards();
        tap(guidance(activity, R.string.why_mate));
        int king = mate.explanation().king();
        var danger = reviewNode(R.id.quest_payoff_danger, 2500);
        boolean startsUnmarked = danger != null && !danger.isSelected() && visibleReviewNode(R.id.quest_payoff_danger_reason) == null;
        clickReviewIcon(R.id.quest_payoff_danger);
        String reason = activity.getString(R.string.quest_danger_mate, BoardSquare.name(king));
        captureFrame("puzzle-danger-mate", "Requesting danger marks the mated king and announces its square", image ->
                startsUnmarked && dialogContains(reason)
                        && hasColor(image, payoffCell(king, mate.position().turn() == Side.BLACK), 0xffffa86b, 12));
        clickReviewIcon(R.id.quest_payoff_previous);
        compare("Rewinding clears the marker, reason and control", true,
                visibleReviewNode(R.id.quest_payoff_danger) == null && visibleReviewNode(R.id.quest_payoff_danger_reason) == null);
        clickReviewIcon(R.id.quest_payoff_next);
        compare("Returning to mate keeps the marker off until requested again", true,
                !reviewNode(R.id.quest_payoff_danger, 2500).isSelected() && visibleReviewNode(R.id.quest_payoff_danger_reason) == null);
        clickDialog(activity.getString(android.R.string.ok));
        compare("Requesting and navigating danger preserves the solved ID and award", true,
                progress.currentSolved() && progress.equals(store.read(mateOrder)) && rewards.equals(store.readRewards()));

        var capture = QuestCatalog.loadTactics().stream().filter(row -> row.id().equals("tactic-002")).findFirst().orElseThrow();
        var payoff = QuestPayoff.forQuest(capture);
        runOnMainSync(() -> QuestPayoffDialog.open(activity, payoff, true));
        clickReviewIcon(R.id.quest_payoff_danger);
        String captureReason = activity.getString(R.string.quest_danger_capture, "h1", "+5");
        captureFrame("puzzle-danger-capture", "The retained capture marks h1 with five pawn units after any reply", image ->
                dialogContains(captureReason) && hasColor(image, payoffCell(BoardSquare.parse("h1"), true), 0xffffa86b, 12));
        clickReviewIcon(R.id.quest_payoff_danger);
        compare("Tapping the control again hides the capture reason", true,
                visibleReviewNode(R.id.quest_payoff_danger_reason) == null);
    }

    private boolean payoffBoardRendered(Bitmap image) {
        var board = nodeBounds(visibleReviewNode(R.id.quest_payoff_board));
        return !board.isEmpty() && hasColor(image, board, 0xffedf0d5, 24) && hasColor(image, board, 0xff769556, 24);
    }

    private boolean payoffMoveHighlighted(Bitmap image, Move move, boolean blackBelow) {
        return hasColor(image, payoffCell(move.to(), blackBelow), BoardSquare.isLight(move.to()) ? 0xfff4ef86 : 0xffbec953, 24);
    }

    private Rect payoffCell(int square, boolean blackBelow) {
        var board = nodeBounds(visibleReviewNode(R.id.quest_payoff_board));
        int index = BoardSquare.displayIndex(square, blackBelow);
        int column = index % 8;
        int row = index / 8;
        return new Rect(board.left + column * board.width() / 8, board.top + row * board.height() / 8,
                board.left + (column + 1) * board.width() / 8, board.top + (row + 1) * board.height() / 8);
    }

    private void checkDefenseStudy(Activity activity) throws Exception {
        var quest = QuestCatalog.loadTraining().stream().filter(row -> row.id().equals("training-001")).findFirst().orElseThrow();
        var study = QuestPayoff.forExplanation(quest);
        runOnMainSync(() -> QuestPayoffDialog.open(activity, study, false));
        var key = reviewNode(R.id.quest_payoff_move, 2500);
        var playable = QuestPayoff.forQuest(quest);
        compare("The defense study starts just after the key and leaves the playable result separate", true,
                key != null && key.getText() != null && key.getText().toString().contains(ChessActivity.notation(activity, quest.solution()))
                        && study.plies() > playable.plies() && playable.plies() == 1);
        var forward = reviewNode(R.id.quest_payoff_next, 2500);
        compare("The read-only defense reply is available", true, forward != null && forward.performAction(AccessibilityNodeInfo.ACTION_CLICK));
        waitForIdleSync();
        var reply = reviewNode(R.id.quest_payoff_move, 2500);
        compare("The replay advances to the actual defensive move and announces the board material", true,
                reply != null && reply.getText() != null && reply.getContentDescription() != null
                        && reply.getText().toString().contains(ChessActivity.notation(activity, study.at(2).move()))
                        && reply.getContentDescription().toString().contains(QuestPayoffDialog.materialAtStep(activity, study.at(2).materialChange())));
        var language = activity.getResources().getConfiguration().getLocales().get(0).getLanguage();
        var lesson = study.defenseStudy();
        String expectedLesson = "ro".equals(language) ? lesson.lessonRo() : lesson.lessonEn();
        var lessonNode = payoffDetail(R.id.quest_payoff_study_lesson);
        compare("The individual lesson is displayed in the app language", true,
                lessonNode != null && lessonNode.getText() != null && expectedLesson.contentEquals(lessonNode.getText()));
        var evidence = payoffDetail(R.id.quest_payoff_study_evidence);
        compare("A separate Stockfish estimate is identified as analysis after the key", true,
                evidence != null && evidence.getText() != null && QuestPayoffDialog.studyEvidence(activity, study).contentEquals(evidence.getText()));
        for (int ply = 2; ply < study.plies(); ply++) clickReviewIcon(R.id.quest_payoff_next);
        var finish = payoffDetail(R.id.quest_payoff_move, false);
        compare("The end of the study announces both the current loss and the worse immediate reply", true,
                finish != null && finish.getContentDescription() != null
                        && finish.getContentDescription().toString().contains(QuestPayoffDialog.materialAtStep(activity, -300))
                        && finish.getContentDescription().toString().contains(QuestPayoffDialog.materialFloor(activity, -400)));
        clickDialog(activity.getString(android.R.string.ok));
    }

    private AccessibilityNodeInfo payoffDetail(int id) {
        return payoffDetail(id, true);
    }

    private AccessibilityNodeInfo payoffDetail(int id, boolean forward) {
        var node = visibleReviewNode(id);
        for (int attempt = 0; node == null && attempt < 3; attempt++) {
            var details = reviewNode(R.id.quest_payoff_details, 2500);
            if (details == null || !details.performAction(forward ? AccessibilityNodeInfo.ACTION_SCROLL_FORWARD : AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break;
            waitForIdleSync();
            node = visibleReviewNode(id);
        }
        return node;
    }

    private void checkAnalysis() throws Exception {
        enableReviewNodeIds();
        var store = new ChessProgressStore(getTargetContext());
        if ("analysis-restore".equals(phase)) {
            var activity = launch();
            var saved = store.matches().readMatch();
            compare("The enabled setting and two moves survive a process restart", true, saved.analysisEnabled() && saved.ply() == 2);
            compare("The reopened match analyzes its live position with the hint hidden", true, awaitHiddenAnalysis(activity, 2));
            return;
        }
        if (!"analysis-prepare".equals(phase)) throw new IllegalArgumentException("Unknown analysis phase: " + phase);
        var saved = ChessMatch.restore("2|analysis|e2e4 e7e5");
        if (!store.matches().saveMatch(saved, false)) throw new AssertionError("Could not seed the analysis fixture");
        var activity = launch();
        var board = findBoard(viewsOf(activity));
        tap(activity.findViewById(R.id.match_history_previous));
        var bounds = screenBounds(board);
        compare("The saved game opens analysis at the selected historical position", true,
                awaitHiddenAnalysis(activity, 1) && hasPawn(activity, board, "e4") && !hasPawn(activity, board, "e5"));
        var lines = saved.bestLinesAt(1);
        tap(activity.findViewById(R.id.match_hint));
        compare("The historical hint marks its own legal recommendation", true, hintShown(activity, board, lines, 0));
        checkHintPreview(activity, "match-analysis", lines.get(0).move());
        compare("Returning to history preserves the selected move and the saved game", true,
                bounds.equals(screenBounds(board)) && hintShown(activity, board, lines, 0)
                        && saved.checkpoint().equals(store.matches().readMatch().checkpoint()));
    }

    private void checkHintPreview(Activity activity, String frame, ChessPosition.Move expected) throws Exception {
        tap(activity.findViewById(R.id.match_analysis));
        var recommended = reviewNode(R.id.hint_compare_best, 12000);
        if (recommended == null || reviewNode(R.id.hint_compare_other, 2500) == null) {
            throw new AssertionError("Both verified hint lines must be selectable");
        }
        String label = recommended.getText().toString().toLowerCase(java.util.Locale.ROOT);
        boolean selectedRecommendation = recommended.isSelected()
                && label.endsWith(ChessActivity.compactNotation(expected).toLowerCase(java.util.Locale.ROOT));
        var previewBoard = nodeBounds(reviewNode(R.id.hint_preview_board, 4500));
        clickReviewIcon(R.id.hint_compare_other);
        clickReviewIcon(R.id.hint_preview_next);
        var alternative = reviewNode(R.id.hint_preview_step, 2500);
        String alternativeText = alternative == null ? "" : alternative.getText().toString();
        clickReviewIcon(R.id.hint_compare_best);
        clickReviewIcon(R.id.hint_preview_next);
        clickReviewIcon(R.id.hint_preview_next);
        var second = reviewNode(R.id.hint_preview_step, 2500);
        boolean secondReached = second != null && second.getText().toString().startsWith("2/");
        clickReviewIcon(R.id.hint_preview_previous);
        var first = reviewNode(R.id.hint_preview_step, 2500);
        compare("Next shows the calculated reply and Previous returns to the first move", true,
                selectedRecommendation && secondReached && first != null && first.getText().toString().startsWith("1/")
                        && first.getText().toString().endsWith(ChessActivity.notation(activity, expected)) && alternativeText.startsWith("1/")
                        && !alternativeText.split(" · ", 2)[1].equals(first.getText().toString().split(" · ", 2)[1]));
        captureFrame(frame, "The suggested line and its navigation are visible with a fixed preview board",
                image -> !previewBoard.isEmpty() && previewBoard.equals(nodeBounds(visibleReviewNode(R.id.hint_preview_board)))
                        && visibleReviewNode(R.id.hint_preview_reason) != null
                        && visibleReviewNode(R.id.hint_compare_summary) != null
                        && visibleReviewNode(R.id.hint_preview_next) != null && visibleReviewNode(R.id.hint_preview_return) != null);
        clickReviewIcon(R.id.hint_preview_return);
    }
    private boolean awaitHiddenAnalysis(Activity activity, int ply) {
        TextView panel = activity.findViewById(R.id.match_analysis);
        View bulb = activity.findViewById(R.id.match_hint);
        ViewGroup moves = activity.findViewById(R.id.match_history_moves);
        if (panel == null || bulb == null || moves == null) return false;
        boolean[] matches = {false};
        long deadline = SystemClock.uptimeMillis() + 12_000;
        do {
            runOnMainSync(() -> matches[0] = ply < moves.getChildCount() && moves.getChildAt(ply).isSelected()
                    && bulb.isEnabled() && !bulb.isSelected() && !panel.getText().toString().contains("\n"));
            if (matches[0]) return true;
            SystemClock.sleep(40);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private void systemBack() {
        sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
        waitForIdleSync();
    }

    private static boolean hintShown(Activity activity, BoardView board, List<ro.atf.chess.TacticalAnalysis.Variation> lines, int index) {
        var variation = lines.get(index);
        var sequence = new java.util.StringJoiner(" · ");
        for (int step = 0; step < Math.min(3, variation.line().size()); step++) {
            sequence.add(ChessActivity.compactNotation(variation.line().get(step)));
        }
        if (variation.line().size() > 3) sequence.add("…");
        String expected = lines.size() == 1 ? sequence.toString()
                : activity.getString(R.string.match_analysis_variant, index + 1, lines.size(), sequence.toString());
        TextView panel = activity.findViewById(R.id.match_analysis);
        View destination = cell(board, BoardSquare.name(variation.move().to()));
        return activity.findViewById(R.id.match_hint).isSelected() && panel.getText().toString().endsWith("\n" + expected)
                && destination.getContentDescription().toString().endsWith(activity.getString(R.string.hint_destination, ""));
    }

    private void checkMatch() throws Exception {
        var store = new ChessProgressStore(getTargetContext());
        var saved = store.matches().readMatch();
        Activity activity = launch();
        switch (phase) {
            case "match-prepare" -> {
                compare("A fresh install offers a new game and hides Resume", true,
                        saved == null && activity.findViewById(R.id.resume_match).getVisibility() == View.GONE);
                openGameSetup(activity);
                click(activity, R.id.match_start);
                var board = findBoard(viewsOf(activity));
                boolean[] locked = {false};
                runOnMainSync(() -> {
                    cell(board, "e2").performClick();
                    cell(board, "e4").performClick();
                    locked[0] = !cell(board, "e4").isEnabled();
                    activity.finish();
                });
                var pending = store.matches().readMatch();
                compare("The human move is committed before the delayed bot reply", true, pending.ply() == 1 && pending.awaitingBot());
                compare("The board locks while the computer has the turn", true, locked[0]);
            }
            case "match-restore" -> {
                compare("Process restart finds the human move and one bot reply due", true,
                        saved.ply() == 1 && saved.awaitingBot() && "e2e4".equals(saved.lastMove().uci()));
                long deadline = SystemClock.uptimeMillis() + 12_000;
                while (store.matches().readMatch().ply() == 1 && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(40);
                waitForIdleSync();
                var afterReply = store.matches().readMatch();
                compare("The restarted process commits exactly one reply", 2, afterReply.ply());
                assertMatchBoard(activity, afterReply);
                var board = findBoard(viewsOf(activity));
                int destination = afterReply.lastMove().to();
                int highlight = BoardSquare.isLight(destination) ? 0xfff4ef86 : 0xffbec953;
                captureFrame("match-resumed", "The restarted match shows the last move and the human turn",
                        image -> fullyVisible(board) && hasColor(image, cell(board, BoardSquare.name(destination)), highlight, 16));
                verifyMatchHistory(activity, store, afterReply);
                tap(button(activity, viewsOf(activity), R.string.main_menu));
                compare("Returning to the menu exposes the saved game", View.VISIBLE, activity.findViewById(R.id.resume_match).getVisibility());
                var list = openMatchList(activity, R.id.resume_match);
                tap(((ViewGroup) list.findViewById(R.id.match_list_rows)).getChildAt(0));
                awaitListTransition(activity);
                assertMatchBoard(activity, afterReply);
                runOnMainSync(activity::finish);
            }
            case "match-settled" -> {
                compare("The second restart begins after the committed bot reply", true, saved.ply() == 2 && !saved.awaitingBot());
                SystemClock.sleep(1_000);
                compare("The computer does not replay its saved reply", saved.checkpoint(), store.matches().readMatch().checkpoint());
                assertMatchBoard(activity, saved);
            }
            default -> throw new IllegalArgumentException("Unknown match phase: " + phase);
        }
    }

    private void verifyMatchHistory(Activity activity, ChessProgressStore store, ChessMatch live) throws IOException {
        var board = findBoard(viewsOf(activity));
        View previousMove = activity.findViewById(R.id.match_history_previous);
        View nextMove = activity.findViewById(R.id.match_history_next);
        View latest = activity.findViewById(R.id.match_history_latest);
        ViewGroup moves = activity.findViewById(R.id.match_history_moves);
        compare("The strip lists the start and every saved move", live.ply() + 1, moves.getChildCount());
        tap(previousMove);
        compare("The previous arrow shows the position after e4 with play disabled", true,
                moves.getChildAt(1).isSelected() && !cell(board, "e4").isEnabled()
                        && cell(board, "e4").getContentDescription().toString().contains(activity.getString(R.string.pawn)));
        tap(previousMove);
        compare("The start shows the white pawn on e2 and disables Previous", true,
                moves.getChildAt(0).isSelected() && !previousMove.isEnabled()
                        && cell(board, "e2").getContentDescription().toString().contains(activity.getString(R.string.pawn)));
        tap(nextMove);
        compare("The next arrow restores the next position", true,
                moves.getChildAt(1).isSelected() && "e2".contentEquals(cell(board, "e2").getContentDescription()));
        captureFrame("match-history", "The board and move controls fit in history",
                image -> fullyVisible(board) && fullyVisible(previousMove) && fullyVisible(latest)
                        && hasColor(image, cell(board, "e4"), 0xfff4ef86, 16));
        tap(latest);
        compare("Jump to latest returns to the live turn and disables Next", true,
                moves.getChildAt(live.ply()).isSelected() && !nextMove.isEnabled() && cell(board, "e4").isEnabled());
        runOnMainSync(() -> ((android.widget.HorizontalScrollView) moves.getParent()).scrollTo(0, 0));
        tap(moves.getChildAt(1));
        compare("Tapping a listed move selects it without changing the saved game", true,
                moves.getChildAt(1).isSelected() && live.checkpoint().equals(store.matches().readMatch().checkpoint()));
        tap(latest);
    }

    private void assertMatchBoard(Activity activity, ChessMatch match) {
        var views = viewsOf(activity);
        var board = findBoard(views);
        var move = match.lastMove();
        String displayMove = activity.getString(R.string.solution_move, BoardSquare.name(move.from()), BoardSquare.name(move.to()));
        compare("The displayed last move matches the saved bot move", true,
                hasText(views, activity.getString(R.string.match_last_move, displayMove)));
        compare("The restored board shows the human pawn and accepts the next move", true,
                cell(board, "e4").getContentDescription().toString().contains(activity.getString(R.string.pawn))
                        && cell(board, "e4").isEnabled());
    }

    private Activity launch() {
        return launch(false);
    }

    private Activity launch(boolean romanianCompact) {
        Intent intent = new Intent(getTargetContext(), ChessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (romanianCompact) intent.putExtra("j7-romanian-compact", true);
        Activity activity = startActivitySync(intent);
        waitForIdleSync();
        return activity;
    }

    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity activity = super.newActivity(loader, name, intent);
        if (intent.getBooleanExtra("j7-romanian-compact", false)
                || ("match-slots".equals(phase) && MatchListActivity.class.getName().equals(name))) {
            var config = new Configuration();
            config.fontScale = 1.3f;
            config.setLocale(Locale.forLanguageTag("ro-RO"));
            activity.applyOverrideConfiguration(config);
        }
        return activity;
    }

    private void checkRomanianCompactScreen() {
        Activity activity = launch(true);
        var config = activity.getResources().getConfiguration();
        compare("The compact probe runs with Romanian resources and font scale 1.3", true,
                config.fontScale == 1.3f && "ro".equals(config.getLocales().get(0).getLanguage()));
        openPuzzles(activity);
        var menu = viewsOf(activity);
        var scroll = menu.stream().filter(ScrollView.class::isInstance).map(ScrollView.class::cast).findFirst().orElseThrow();
        var last = activity.findViewById(R.id.puzzles_home);
        boolean[] reachable = {false};
        runOnMainSync(() -> {
            View root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
            var original = new Rect(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
            int compactHeight = Math.min(original.height() - 1, Math.round(320 * activity.getResources().getDisplayMetrics().density));
            try {
                measureScreen(root, original, compactHeight);
                boolean scrolls = scroll.canScrollVertically(1);
                scroll.scrollTo(0, scroll.getChildAt(0).getHeight());
                reachable[0] = scrolls && fullyVisible(last);
            } finally {
                measureScreen(root, original, original.height());
                scroll.scrollTo(0, 0);
            }
        });
        compare("The last Romanian menu entry is reachable in a short window", true, reachable[0]);
        openChapter(activity, R.string.open_mate_one);
        var views = viewsOf(activity);
        var board = findBoard(views);
        tap(guidance(activity, R.string.hint));
        tap(guidance(activity, R.string.show_solution));
        String black = activity.getString(R.string.black);
        String pieceHint = activity.getString(R.string.hint_description, "d8", black, activity.getString(R.string.queen));
        String announcement = activity.getString(R.string.full_hint_announcement, activity.getString(R.string.to_move, black), pieceHint, "h4");
        compare("The Romanian board, active color and controls fit with the enlarged move hint", true,
                compactLayoutFits(activity, views, board, R.string.why_mate)
                        && turnLabel(activity, board, R.string.black, R.string.white, true) && hasDescription(views, announcement));
        View origin = cell(board, "d8");
        View destination = cell(board, "h4");
        View active = ((ViewGroup) board.getParent()).getChildAt(2);
        boolean blackBelow = origin.getAccessibilityTraversalAfter() == active.getId() && destination.getAccessibilityTraversalAfter() == origin.getId();
        tap(button(activity, views, R.string.flip));
        View flippedOrigin = cell(board, "d8");
        View flippedDestination = cell(board, "h4");
        View flippedActive = ((ViewGroup) board.getParent()).getChildAt(0);
        compare("Accessibility traversal follows color, piece and destination after flipping, with old links cleared", true,
                blackBelow && flippedOrigin.getAccessibilityTraversalAfter() == flippedActive.getId()
                        && flippedDestination.getAccessibilityTraversalAfter() == flippedOrigin.getId()
                        && origin.getAccessibilityTraversalAfter() == View.NO_ID && destination.getAccessibilityTraversalAfter() == View.NO_ID);
        tap(button(activity, views, R.string.main_menu));
        runOnMainSync(activity::finish);
    }

    private static List<View> viewsOf(Activity activity) {
        List<View> views = new ArrayList<>();
        collect(activity.findViewById(android.R.id.content), views);
        return views;
    }

    private static BoardView findBoard(List<View> views) {
        return views.stream().filter(BoardView.class::isInstance).map(BoardView.class::cast).findFirst()
                .orElseThrow(() -> new AssertionError("Chess board not found"));
    }

    private void prepare(Activity activity, List<View> views, BoardView board) throws Exception {
        Button next = button(activity, views, R.string.next_quest);
        String solved = activity.getString(R.string.quest_solved);
        compare("64 visible cells", 64, board.getChildCount());
        compare("Square board", true, board.getWidth() > 0 && board.getWidth() == board.getHeight());
        compare("Both player labels are adjacent to the board", true, labelsTouchBoard(board));
        compare("A compact layout keeps the labels, square board and three controls visible", true, compactLayoutFits(activity, views, board, R.string.hint));
        compare("The first of 500 quests is shown", true, atQuest(activity, views, 1));
        compare("Next is disabled on the first unsolved quest", false, next.isEnabled());
        compare("Black is to move and is shown next to the bottom rank", true, turnLabel(activity, board, R.string.black, R.string.white, true));
        var flip = button(activity, views, R.string.flip);
        tap(flip);
        compare("Flipping moves the label to the top without changing the active color", true,
                turnLabel(activity, board, R.string.black, R.string.white, false));
        tap(flip);
        compare("Fresh storage shows zero solved quests", true, solvedCount(activity, views, 0));
        var track = progressTrack(views);
        compare("The collection opens with a zero meter and goal of five", true,
                track.getProgress() == 0 && track.getMax() == CATALOG_SIZE && hasText(views, activity.getString(R.string.next_milestone, 5)));
        tap(guidance(activity, R.string.hint));
        String expectedHint = activity.getString(R.string.hint_description, "d8", activity.getString(R.string.black), activity.getString(R.string.queen));
        String origin = activity.getString(R.string.piece_square, "d8", activity.getString(R.string.black), activity.getString(R.string.queen));
        View piece = cell(board, "d8");
        View arrowMidpoint = cell(board, "f6");
        View destination = cell(board, "h4");
        var showSolution = guidance(activity, R.string.show_solution);
        compare("The simple hint exposes only the black queen on d8 and keeps the board and controls visible", true,
                hasText(views, expectedHint) && activity.getString(R.string.hint_origin, origin).contentEquals(piece.getContentDescription())
                        && "h4".contentEquals(destination.getContentDescription()) && fullyVisible(board) && fullyVisible(showSolution));
        captureFrame("hint-piece", "The simple hint renders a mint outline and no arrow",
                image -> hasColor(image, piece, HINT_TINT, 16) && !hasColor(image, arrowMidpoint, HINT_TINT, 1));
        tap(showSolution);
        compare("The full hint reveals d8 to h4 and leaves the position playable", true,
                hasText(views, activity.getString(R.string.suggested_move, activity.getString(R.string.solution_move, "d8", "h4")))
                        && activity.getString(R.string.hint_destination, "h4").contentEquals(destination.getContentDescription())
                        && piece.getContentDescription().toString().contains(origin));
        captureFrame("hint-solution", "The full hint renders an arrow across the board", image -> hasColor(image, arrowMidpoint, HINT_TINT, 16));
        tap(guidance(activity, R.string.why_mate));
        String queenOnH4 = activity.getString(R.string.piece_at_square, activity.getString(R.string.queen), "h4");
        compare("The solution names the checking queen and king square", true,
                dialogNode(activity.getString(R.string.mate_checkers, "e1", queenOnH4)) != null);
        compare("The solution explains why the king cannot escape to f2", true,
                dialogNode(activity.getString(R.string.mate_exit_attacked, "f2", queenOnH4)) != null);
        clickDialog(activity.getString(android.R.string.ok));
        compare("Revealing the solution does not mark a solve", true, solvedCount(activity, views, 0));
        tap(cell(board, "e5"));
        tap(cell(board, "e4"));
        compare("Wrong legal move keeps the puzzle open", true, hasText(views, activity.getString(R.string.quest_try_again)));
        compare("Rejected attempt leaves the pawn on e5", activity.getString(R.string.piece_square, "e5", activity.getString(R.string.black),
                activity.getString(R.string.pawn)), cell(board, "e5").getContentDescription().toString());
        tap(cell(board, "d8"));
        tap(cell(board, "h4"));
        compare("The first mating move solves the quest", true, hasText(views, solved));
        tap(button(activity, views, R.string.flip));
        compare("Flipped lower-right cell", "h1", coordinate(board.getChildAt(63)));
        tap(next);
        compare("The second quest is shown", true, atQuest(activity, views, 2));
        var second = QuestCatalog.load().get(1).solution();
        if (second.promotion() != null) throw new AssertionError("The second curriculum position requires a promotion fixture");
        tap(cell(board, BoardSquare.name(second.from())));
        tap(cell(board, BoardSquare.name(second.to())));
        compare("A second quest can be solved independently", true, hasText(views, solved));
        compare("The application shows two solves", true, solvedCount(activity, views, 2));
        tap(next);
        compare("The third quest is the current selection", true, atQuest(activity, views, 3));
        compare("The selected third quest is unsolved", false, hasText(views, solved));
        prepareMateInTwo(activity);
        prepareMateInThree(activity);
    }

    private AccessibilityNodeInfo dialogNode(String text) throws Exception {
        return dialogNode(text, node -> true);
    }

    private Rect activeWindowBounds() {
        var root = getUiAutomation().getRootInActiveWindow();
        var bounds = new Rect();
        if (root != null) root.getBoundsInScreen(bounds);
        return bounds;
    }

    private AccessibilityNodeInfo dialogNode(String text, Predicate<AccessibilityNodeInfo> matches) throws Exception {
        return dialogNode(text, matches, 4500);
    }

    private AccessibilityNodeInfo dialogNode(String text, Predicate<AccessibilityNodeInfo> matches, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        do {
            AccessibilityNodeInfo window = getUiAutomation().getRootInActiveWindow();
            if (window != null) {
                for (var node : window.findAccessibilityNodeInfosByText(text)) {
                    if (matches.test(node)) return node;
                }
            }
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private void clickDialog(String text) throws Exception {
        AccessibilityNodeInfo node = dialogNode(text, candidate -> candidate.isEnabled() && candidate.isClickable()
                && candidate.getText() != null && text.equalsIgnoreCase(candidate.getText().toString()));
        if (node == null || !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            throw new AssertionError("Dialog action unavailable: " + text);
        }
        getUiAutomation().waitForIdle(100, 2500);
    }

    private void restore(Activity activity) throws Exception {
        var mateTwoViews = viewsOf(activity);
        finishMateInTwo(activity, mateTwoViews, findBoard(mateTwoViews));
        var views = viewsOf(activity);
        var board = findBoard(views);
        String solved = activity.getString(R.string.quest_solved);
        compare("Switching back resumes the third one-move quest", true, atQuest(activity, views, 3));
        compare("Both prior solves survive process death", true, solvedCount(activity, views, 2));
        compare("The resumed quest is still unsolved", false, hasText(views, solved));
        Button previous = button(activity, views, R.string.previous_quest);
        tap(previous);
        compare("Previous opens the second quest", true, atQuest(activity, views, 2));
        compare("Second quest restores its solved position", true, hasText(views, solved));
        tap(previous);
        compare("Previous opens the first quest", true, atQuest(activity, views, 1));
        View destination = cell(board, "h4");
        String queen = activity.getString(R.string.piece_square, "h4", activity.getString(R.string.black), activity.getString(R.string.queen));
        compare("First quest restores its solved position", true, hasText(views, solved) && queen.contentEquals(destination.getContentDescription()));
        captureFrame("board", "The screenshot shows the highlight on the mating destination", image -> hasColor(image, destination, 0xffbec953, 16));
        runOnMainSync(activity::finish);
        waitForIdleSync();
        var order = new ArrayList<String>();
        var catalog = QuestCatalog.load();
        for (var quest : catalog) order.add(quest.id());
        var store = new ChessProgressStore(getTargetContext());
        Activity counter = launchSavedQuest(store, order, "mate-101");
        List<View> counterViews = viewsOf(counter);
        BoardView counterBoard = findBoard(counterViews);
        compare("The counter-mate opens with both legacy solves", true, solvedCount(counter, counterViews, 2));
        solveCounterMate(counter, counterViews, counterBoard, "mate-101", "h6", "d2", R.string.bishop);
        runOnMainSync(counter::finish);
        waitForIdleSync();
        Activity capture = launchSavedQuest(store, order, "mate-102");
        var captureViews = viewsOf(capture);
        solveCounterMate(capture, captureViews, findBoard(captureViews), "mate-102", "h3", "g4", R.string.pawn);
        runOnMainSync(capture::finish);
        waitForIdleSync();
        var progress = store.quests().read(order);
        var fifth = catalog.stream().filter(quest -> !progress.solvedIds().contains(quest.id()) && quest.solution().promotion() == null)
                .findFirst().orElseThrow();
        Activity milestoneActivity = launchSavedQuest(store, order, fifth.id());
        var milestoneViews = viewsOf(milestoneActivity);
        var milestoneBoard = findBoard(milestoneViews);
        tap(cell(milestoneBoard, BoardSquare.name(fifth.solution().from())));
        tap(cell(milestoneBoard, BoardSquare.name(fifth.solution().to())));
        var track = progressTrack(milestoneViews);
        String milestone = milestoneActivity.getString(R.string.milestone_reached, 5, 15);
        compare("The fifth solve unlocks the next milestone without hiding the board or controls", true,
                solvedCount(milestoneActivity, milestoneViews, 5) && track.getProgress() == 5 && hasText(milestoneViews, milestone)
                        && hasDescription(milestoneViews, milestoneActivity.getString(R.string.quest_solved) + ". " + milestone)
                        && fullyVisible(milestoneBoard) && fullyVisible(guidance(milestoneActivity, R.string.why_mate)));
        captureFrame("milestone", "The progress bar visibly fills at the first milestone", image -> hasColor(image, track, 0xffb8d88a, 8));
    }

    private Activity launchSavedQuest(ChessProgressStore store, List<String> order, String id) {
        var quests = store.quests();
        if (!quests.save(quests.read(order).select(id), quests.readRewards())) {
            throw new AssertionError("Could not select quest " + id);
        }
        return launch();
    }

    private void prepareMateInTwo(Activity activity) throws Exception {
        switchChapter(activity, R.string.open_mate_two);
        var views = viewsOf(activity);
        var board = findBoard(views);
        var quest = QuestCatalog.loadMateInTwo().get(0);
        compare("Mate in two opens with its own 300-quest counter", true,
                hasText(views, activity.getString(R.string.mate_two_progress, 1, MATE_TWO_SIZE)) && chapterCount(activity, views, 0, MATE_TWO_SIZE)
                        && turnLabel(activity, board, R.string.white, R.string.black, true));
        Move wrong = quest.position().legalMoves().stream().filter(move -> !move.equals(quest.solution())).findFirst().orElseThrow();
        playMove(board, wrong);
        compare("A wrong opening keeps White to move and asks for forced mate in two", true,
                hasText(views, activity.getString(R.string.mate_two_try_again)) && turnLabel(activity, board, R.string.white, R.string.black, true));
        tap(guidance(activity, R.string.hint));
        tap(guidance(activity, R.string.show_solution));
        compare("The opening hint shows the unique first move", true, hasText(views, suggestedMove(activity, quest.solution())));
        long started = beginDefense(activity, views, board, quest.solution(), false);
        String defenseText = activity.getString(R.string.mate_two_reply, moveText(activity, quest.defenseAfter(0)));
        compare("The rendered defense follows the player's move by at least 650 ms", true, awaitText(views, defenseText) - started >= 650);
        var defense = quest.defenseAfter(0);
        compare("The opponent replies and the first move does not count as a solve", true,
                hasText(views, activity.getString(R.string.mate_two_reply, moveText(activity, defense))) && chapterCount(activity, views, 0, MATE_TWO_SIZE)
                        && turnLabel(activity, board, R.string.white, R.string.black, true));
        var square = cell(board, BoardSquare.name(defense.to()));
        int color = BoardSquare.isLight(defense.to()) ? 0xfff4ef86 : 0xffbec953;
        captureFrame("mate-two", "The mate-in-two board highlights the opponent's defense", image -> hasColor(image, square, color, 16));
    }

    private void finishMateInTwo(Activity activity, List<View> views, BoardView board) {
        var quest = QuestCatalog.loadMateInTwo().get(0);
        compare("Cold launch restores the second move after the opponent's defense", true,
                hasText(views, activity.getString(R.string.mate_two_progress, 1, MATE_TWO_SIZE)) && chapterCount(activity, views, 0, MATE_TWO_SIZE)
                        && turnLabel(activity, board, R.string.white, R.string.black, true)
                        && hasText(views, activity.getString(R.string.mate_two_reply, moveText(activity, quest.defenseAfter(0)))));
        tap(guidance(activity, R.string.hint));
        tap(guidance(activity, R.string.show_solution));
        compare("The second hint follows the current position and shows the mate", true, hasText(views, suggestedMove(activity, quest.matingMove())));
        playMove(board, quest.matingMove());
        compare("Playing the mate increments only the new chapter", true,
                chapterCount(activity, views, 1, MATE_TWO_SIZE) && hasText(views, activity.getString(R.string.quest_solved)));
        switchChapter(activity, R.string.open_mate_one);
    }

    private void openChapter(Activity activity, int label) {
        openPuzzles(activity);
        var entry = button(activity, viewsOf(activity), label);
        runOnMainSync(() -> {
            if (!fullyVisible(entry)) entry.requestRectangleOnScreen(new Rect(0, 0, entry.getWidth(), entry.getHeight()), true);
        });
        waitForIdleSync();
        tap(entry);
    }

    private void openPuzzles(Activity activity) {
        if (activity.findViewById(R.id.open_puzzles) != null) click(activity, R.id.open_puzzles);
    }

    private void openGameSetup(Activity activity) { click(activity, R.id.new_match); }

    private void click(Activity activity, int id) {
        boolean[] clicked = {false};
        runOnMainSync(() -> {
            View view = activity.findViewById(id);
            if (view != null && view.isEnabled() && view.isClickable()) {
                view.performClick();
                clicked[0] = true;
            }
        });
        if (!clicked[0]) throw new AssertionError("Expected enabled control " + activity.getResources().getResourceEntryName(id));
        waitForIdleSync();
    }

    private void switchChapter(Activity activity, int label) {
        tap(button(activity, viewsOf(activity), R.string.main_menu));
        openChapter(activity, label);
    }

    private void prepareMateInThree(Activity activity) throws IOException {
        switchChapter(activity, R.string.open_mate_three);
        var views = viewsOf(activity);
        var board = findBoard(views);
        var quest = QuestCatalog.loadMateInThree().get(0);
        compare("Mate in three opens with 200 puzzles and White to move", true,
                hasText(views, activity.getString(R.string.mate_three_progress, 1, MATE_THREE_SIZE)) && chapterCount(activity, views, 0, MATE_THREE_SIZE)
                        && turnLabel(activity, board, R.string.white, R.string.black, true));
        playMove(board, quest.playerMove(0));
        awaitText(views, activity.getString(R.string.mate_three_reply, moveText(activity, quest.defenseAfter(0))));
        compare("The first defense leaves two moves and keeps White to move", true,
                hasText(views, activity.getString(R.string.mate_three_reply, moveText(activity, quest.defenseAfter(0))))
                        && turnLabel(activity, board, R.string.white, R.string.black, true));
        beginDefense(activity, views, board, quest.playerMove(1), true);
    }

    private void finishMateInThree(Activity activity) throws IOException {
        var views = viewsOf(activity);
        var board = findBoard(views);
        var quest = QuestCatalog.loadMateInThree().get(0);
        String defenseText = activity.getString(R.string.mate_two_reply, moveText(activity, quest.defenseAfter(1)));
        awaitText(views, defenseText);
        var next = button(activity, views, R.string.next_quest);
        compare("Restart reopens Mate in 3 and plays the saved second defense without unlocking Next", true,
                hasText(views, defenseText) && turnLabel(activity, board, R.string.white, R.string.black, true)
                        && !next.isEnabled() && chapterCount(activity, views, 0, MATE_THREE_SIZE));
        int destination = quest.defenseAfter(1).to();
        var square = cell(board, BoardSquare.name(destination));
        int color = BoardSquare.isLight(destination) ? 0xfff4ef86 : 0xffbec953;
        captureFrame("mate-three", "The board highlights the restored second defense in mate in three", image -> hasColor(image, square, color, 16));
        playMove(board, quest.matingMove());
        compare("Only the final mate marks the three-move quest as solved", true,
                chapterCount(activity, views, 1, MATE_THREE_SIZE) && hasText(views, activity.getString(R.string.quest_solved)) && next.isEnabled());
        tap(next);
        compare("Next keeps the player in Mate in 3 and locks again on the next unsolved quest", true,
                hasText(views, activity.getString(R.string.mate_three_progress, 2, MATE_THREE_SIZE)) && !next.isEnabled());
    }

    private long beginDefense(Activity activity, List<View> views, BoardView board, Move move, boolean closeBeforeReply) {
        String originName = BoardSquare.name(move.from());
        View origin = cell(board, originName);
        tap(origin);
        View target = cell(board, BoardSquare.name(move.to()));
        long[] started = {0};
        boolean[] waiting = {false};
        runOnMainSync(() -> {
            started[0] = SystemClock.uptimeMillis();
            target.performClick();
            waiting[0] = hasText(views, activity.getString(R.string.opponent_reply_pending))
                    && turnLabel(activity, board, R.string.black, R.string.white, false)
                    && originName.contentEquals(origin.getContentDescription())
                    && !BoardSquare.name(move.to()).contentEquals(target.getContentDescription())
                    && !target.isEnabled() && !guidance(activity, R.string.quest_hint_next_wait).isEnabled()
                    && !button(activity, views, R.string.next_quest).isEnabled();
            if (closeBeforeReply) activity.finish();
        });
        compare(closeBeforeReply ? "The second defense is pending when the board closes"
                : "The player's move leaves the opponent to move and locks input during the pause", true, waiting[0]);
        return started[0];
    }

    private long awaitText(List<View> views, String expected) {
        long deadline = SystemClock.uptimeMillis() + 6000;
        boolean[] found = {false};
        do {
            runOnMainSync(() -> found[0] = hasText(views, expected));
            if (found[0]) return SystemClock.uptimeMillis();
            SystemClock.sleep(25);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError("Timed out waiting for " + expected);
    }

    private void playMove(BoardView board, Move move) {
        if (move.promotion() != null) throw new AssertionError("Sanity fixture requires a move without promotion");
        tap(cell(board, BoardSquare.name(move.from())));
        tap(cell(board, BoardSquare.name(move.to())));
    }

    private static String moveText(Activity activity, Move move) {
        return activity.getString(R.string.solution_move, BoardSquare.name(move.from()), BoardSquare.name(move.to()));
    }

    private static String suggestedMove(Activity activity, Move move) {
        return activity.getString(R.string.suggested_move, moveText(activity, move));
    }

    private static boolean chapterCount(Activity activity, List<View> views, int count, int size) {
        return hasText(views, activity.getString(R.string.solved_count, count, size));
    }

    private static boolean turnLabel(Activity activity, BoardView board, int active, int other, boolean bottom) {
        var panel = (ViewGroup) board.getParent();
        var mover = (TextView) panel.getChildAt(bottom ? 2 : 0);
        var opponent = (TextView) panel.getChildAt(bottom ? 0 : 2);
        return activity.getString(R.string.to_move, activity.getString(active)).contentEquals(mover.getText())
                && activity.getString(other).contentEquals(opponent.getText());
    }

    private void solveCounterMate(Activity activity, List<View> views, BoardView board, String id, String from, String to, int piece) throws Exception {
        tap(guidance(activity, R.string.hint));
        String hint = activity.getString(R.string.hint_description, from, activity.getString(R.string.white), activity.getString(piece));
        compare(id + " names the defending piece and origin", true, hasText(views, hint));
        tap(guidance(activity, R.string.show_solution));
        compare(id + " reveals the unique mating reply", true, hasText(views, activity.getString(R.string.suggested_move,
                activity.getString(R.string.solution_move, from, to))));
        tap(cell(board, from));
        tap(cell(board, to));
        compare(id + " is solved by playing the reply", true, hasText(views, activity.getString(R.string.quest_solved)));
    }

    private static Button button(Activity activity, List<View> views, int label) {
        return views.stream().filter(Button.class::isInstance).map(Button.class::cast)
                .filter(view -> view.getText().toString().equals(activity.getString(label))).findFirst()
                .orElseThrow(() -> new AssertionError("Button missing: " + activity.getString(label)));
    }

    private static View guidance(Activity activity, int label) {
        View view = activity.findViewById(R.id.quest_guidance);
        if (view == null || !activity.getString(label).contentEquals(view.getContentDescription())) {
            throw new AssertionError("Guidance control missing its accessible name: " + activity.getString(label));
        }
        return view;
    }

    private static boolean atQuest(Activity activity, List<View> views, int index) {
        return hasText(views, activity.getString(R.string.quest_progress, index, CATALOG_SIZE));
    }

    private static boolean solvedCount(Activity activity, List<View> views, int count) {
        return hasText(views, activity.getString(R.string.solved_count, count, CATALOG_SIZE));
    }

    private static ProgressBar progressTrack(List<View> views) {
        return views.stream().filter(ProgressBar.class::isInstance).map(ProgressBar.class::cast).findFirst().orElseThrow();
    }

    private static boolean fullyVisible(View view) {
        Rect bounds = new Rect();
        return view.getGlobalVisibleRect(bounds) && bounds.width() == view.getWidth() && bounds.height() == view.getHeight();
    }

    private static boolean labelsTouchBoard(BoardView board) {
        ViewGroup panel = (ViewGroup) board.getParent();
        View top = panel.getChildAt(0);
        View bottom = panel.getChildAt(2);
        return top.getBottom() == board.getTop() && bottom.getTop() == board.getBottom()
                && fullyVisible(top) && fullyVisible(bottom);
    }

    private boolean compactLayoutFits(Activity activity, List<View> views, BoardView board, int hintLabel) {
        var root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        var caption = (TextView) activity.findViewById(R.id.quest_guidance_label);
        var controls = List.of(button(activity, views, R.string.previous_quest), guidance(activity, hintLabel),
                button(activity, views, R.string.next_quest), caption);
        boolean[] fits = {false};
        runOnMainSync(() -> {
            Rect original = new Rect(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
            int originalBoard = board.getWidth();
            int densityLimit = Math.round(480 * activity.getResources().getDisplayMetrics().density);
            int compact = Math.min(original.height() - 1, densityLimit);
            try {
                measureScreen(root, original, compact);
                fits[0] = board.getWidth() > 0 && board.getWidth() < originalBoard && board.getWidth() == board.getHeight()
                        && labelsTouchBoard(board) && fullyVisible(board) && controls.stream().allMatch(BoardSanity::fullyVisible)
                        && caption.getLayout() != null && caption.getLayout().getEllipsisCount(caption.getLineCount() - 1) == 0
                        && ((View) board.getParent()).getBottom() <= ((View) controls.get(0).getParent()).getTop();
            } finally {
                measureScreen(root, original, original.height());
            }
        });
        return fits[0];
    }

    private static void measureScreen(View root, Rect bounds, int height) {
        root.measure(View.MeasureSpec.makeMeasureSpec(bounds.width(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        root.layout(bounds.left, bounds.top, bounds.right, bounds.top + height);
    }

    private record BoardFootprint(Rect board, Rect a1, Rect h8) { }

    private void checkOpeningBoardGeometry(Activity activity, BoardView board) {
        View next = activity.findViewById(R.id.opening_next);
        View previous = activity.findViewById(R.id.opening_previous);
        View replay = activity.findViewById(R.id.opening_practice);
        var start = openingFootprints(activity, board);
        tap(next);
        compare("Next preserves the board and its corner squares when Resume lesson appears", start, openingFootprints(activity, board));
        tap(next);
        compare("Next preserves the board and its corner squares when Lesson hint returns", start, openingFootprints(activity, board));
        tap(previous);
        tap(previous);
        tap(replay);
    }

    private List<BoardFootprint> openingFootprints(Activity activity, BoardView board) {
        var root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        var footprints = new ArrayList<BoardFootprint>();
        runOnMainSync(() -> {
            var original = new Rect(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
            float density = activity.getResources().getDisplayMetrics().density;
            try {
                footprints.add(footprint(board, null));
                for (int[] size : new int[][]{{320, 480}, {280, 440}}) {
                    int width = Math.min(original.width(), Math.round(size[0] * density));
                    int height = Math.min(original.height(), Math.round(size[1] * density));
                    var bounds = new Rect(original.left, original.top, original.left + width, original.top + height);
                    measureScreen(root, bounds, height);
                    footprints.add(footprint(board, null));
                }
            } finally {
                measureScreen(root, original, original.height());
            }
        });
        return footprints;
    }

    private List<BoardFootprint> boardFootprints(Activity activity, BoardView board) {
        var root = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        TextView analysis = activity.findViewById(R.id.match_analysis);
        View bulb = activity.findViewById(R.id.match_hint);
        var footprints = new ArrayList<BoardFootprint>();
        runOnMainSync(() -> {
            var original = new Rect(root.getLeft(), root.getTop(), root.getRight(), root.getBottom());
            float density = activity.getResources().getDisplayMetrics().density;
            try {
                measureScreen(root, original, original.height());
                footprints.add(footprint(board, analysis));
                checkHistoryControls(activity, bulb);
                for (int[] size : new int[][]{{320, 480}, {440, 400}}) {
                    int width = Math.min(original.width(), Math.round(size[0] * density));
                    int height = Math.min(original.height(), Math.round(size[1] * density));
                    var bounds = new Rect(original.left, original.top, original.left + width, original.top + height);
                    measureScreen(root, bounds, height);
                    footprints.add(footprint(board, analysis));
                    checkHistoryControls(activity, bulb);
                }
            } finally {
                measureScreen(root, original, original.height());
            }
        });
        return footprints;
    }

    private static void checkHistoryControls(Activity activity, View bulb) {
        if (bulb == null) return;
        var controls = List.of(bulb, activity.findViewById(R.id.match_history_previous), activity.findViewById(R.id.match_history_next),
                activity.findViewById(R.id.match_history_latest));
        int touchSize = Math.round(48 * activity.getResources().getDisplayMetrics().density);
        if (!controls.stream().allMatch(view -> fullyVisible(view) && view.getWidth() >= touchSize && view.getHeight() >= touchSize)) {
            throw new AssertionError("The hint and history controls must fit with 48 dp targets");
        }
    }

    private static BoardFootprint footprint(BoardView board, TextView analysis) {
        if (!fullyVisible(board) || board.getWidth() == 0 || board.getWidth() != board.getHeight() || !labelsTouchBoard(board)) {
            throw new AssertionError("Expected a fully visible square board with both player labels");
        }
        if (analysis != null && analysis.getText().toString().contains("\n")) {
            var layout = analysis.getLayout();
            if (!fullyVisible(analysis) || layout.getLineCount() != 2 || layout.getEllipsisCount(1) != 0) {
                throw new AssertionError("Expected the full score and suggestion on two visible lines");
            }
        }
        return new BoardFootprint(screenBounds(board), screenBounds(cell(board, "a1")), screenBounds(cell(board, "h8")));
    }

    private static Rect screenBounds(View view) {
        int[] xy = new int[2];
        view.getLocationOnScreen(xy);
        return new Rect(xy[0], xy[1], xy[0] + view.getWidth(), xy[1] + view.getHeight());
    }

    private static boolean hasTwoRenderedLines(Bitmap image, TextView text) {
        var bounds = screenBounds(text);
        int color = text.getCurrentTextColor();
        int firstTextRow = -1;
        for (int y = Math.max(0, bounds.top); y < Math.min(image.getHeight(), bounds.bottom); y++) {
            for (int x = Math.max(0, bounds.left); x < Math.min(image.getWidth(), bounds.right); x++) {
                if (image.getPixel(x, y) != color) continue;
                if (firstTextRow < 0) firstTextRow = y;
                if (y - firstTextRow >= text.getLineHeight()) return true;
                break;
            }
        }
        return false;
    }

    private void captureFrame(String name, String assertion, Predicate<Bitmap> rendered) throws IOException {
        var image = awaitRenderedFrame(rendered);
        if (image != null) {
            try { saveFrame(name, image); }
            finally { image.recycle(); }
        }
        compare(assertion, true, image != null);
    }

    private Bitmap awaitRenderedFrame(Predicate<Bitmap> rendered) {
        long deadline = SystemClock.uptimeMillis() + 12000;
        do {
            Bitmap image = getUiAutomation().takeScreenshot();
            if (image != null) {
                boolean matches = false;
                try {
                    matches = rendered.test(image);
                    if (matches) return image;
                } finally {
                    if (!matches) image.recycle();
                }
            }
            SystemClock.sleep(75);
        } while (SystemClock.uptimeMillis() < deadline);
        return null;
    }

    private void saveFrame(String name, Bitmap image) throws IOException {
        var context = getTargetContext();
        var folder = externalFrames ? context.getExternalFilesDir(null) : context.getFilesDir();
        if (folder == null) throw new IOException("Screenshot storage is unavailable");
        var file = new File(folder, "j7-" + name + ".png");
        try (var output = new FileOutputStream(file)) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG write failed");
        }
        if ("analysis-prepare".equals(phase) || "manual-analysis".equals(phase) || "tactics-explanation".equals(phase)
                || "training-finish".equals(phase) || "puzzle-mate-replay".equals(phase) || "puzzle-danger".equals(phase)) {
            receipt.putString("capture_" + name, captureDigest(file));
        }
    }

    private static String captureDigest(File file) throws IOException {
        try (var input = new FileInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
            var hex = new StringBuilder(64);
            for (byte value : digest.digest()) {
                hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
                hex.append(Character.forDigit(value & 0xf, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private boolean hasColor(Bitmap image, View view, int color, int required) {
        int[] location = new int[2];
        runOnMainSync(() -> view.getLocationOnScreen(location));
        return hasColor(image, new Rect(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight()), color, required);
    }

    private boolean hasColor(Bitmap image, Rect screenBounds, int color, int required) {
        var display = new DisplayMetrics();
        ((WindowManager) getTargetContext().getSystemService(android.content.Context.WINDOW_SERVICE))
                .getDefaultDisplay().getRealMetrics(display);
        if (display.widthPixels <= 0 || display.heightPixels <= 0) {
            throw new IllegalStateException("The emulator reported invalid real-display dimensions.");
        }
        double scaleX = (double) image.getWidth() / display.widthPixels;
        double scaleY = (double) image.getHeight() / display.heightPixels;
        Rect bounds = new Rect((int) Math.floor(screenBounds.left * scaleX), (int) Math.floor(screenBounds.top * scaleY),
                (int) Math.ceil(screenBounds.right * scaleX), (int) Math.ceil(screenBounds.bottom * scaleY));
        int marked = 0;
        int right = Math.min(image.getWidth(), bounds.right);
        int bottom = Math.min(image.getHeight(), bounds.bottom);
        for (int y = Math.max(0, bounds.top); y < bottom; y++) {
            for (int x = Math.max(0, bounds.left); x < right; x++) {
                if (image.getPixel(x, y) == color && ++marked >= required) return true;
            }
        }
        return false;
    }

    private void tap(View view) {
        if (view == null) throw new AssertionError("Touch target is missing");
        float[] center = new float[2];
        Rect visible = new Rect();
        boolean[] available = {false};
        runOnMainSync(() -> {
            available[0] = view.getGlobalVisibleRect(visible) && view.isEnabled();
            int[] rootOnScreen = new int[2];
            view.getRootView().getLocationOnScreen(rootOnScreen);
            center[0] = rootOnScreen[0] + visible.exactCenterX();
            center[1] = rootOnScreen[1] + visible.exactCenterY();
        });
        if (!available[0]) {
            throw new AssertionError("Touch target unavailable: " + (view instanceof Button control ? control.getText() : view.getContentDescription()));
        }
        tapAt(center[0], center[1]);
    }

    private void tapAt(float x, float y) {
        long start = SystemClock.uptimeMillis();
        inject(start, MotionEvent.ACTION_DOWN, x, y);
        inject(start, MotionEvent.ACTION_UP, x, y);
        waitForIdleSync();
    }

    private void inject(long start, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0);
        try {
            event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
            if (!getUiAutomation().injectInputEvent(event, true)) throw new AssertionError("Android rejected touch input");
        } finally {
            event.recycle();
        }
    }

    private void compare(String name, Object expected, Object actual) {
        boolean passed = java.util.Objects.equals(expected, actual);
        receipt.putString(String.format(java.util.Locale.ROOT, "check_%02d", ++comparisons),
                name + "\t" + expected + "\t" + actual + "\t" + passed);
        if (!passed) throw new AssertionError(name + " expected " + expected + " but got " + actual);
    }

    private static void collect(View view, List<View> views) {
        views.add(view);
        if (view instanceof ViewGroup group) {
            for (int index = 0; index < group.getChildCount(); index++) collect(group.getChildAt(index), views);
        }
    }

    private static boolean hasText(List<View> views, String expected) {
        return views.stream().filter(TextView.class::isInstance).map(TextView.class::cast)
                .anyMatch(view -> view.getText().toString().equals(expected));
    }

    private static boolean hasDescription(List<View> views, String expected) {
        return views.stream().map(View::getContentDescription).filter(java.util.Objects::nonNull).anyMatch(expected::contentEquals);
    }

    private static String coordinate(View view) { return view.getContentDescription().subSequence(0, 2).toString(); }

    private static View cell(BoardView board, String coordinate) {
        for (int index = 0; index < 64; index++) {
            if (coordinate.equals(coordinate(board.getChildAt(index)))) return board.getChildAt(index);
        }
        throw new IllegalArgumentException("Missing square " + coordinate);
    }
}
