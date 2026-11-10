package ro.atf.chess.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.QuestCatalog;

public final class BoardSanity extends Instrumentation {
    private static final int CATALOG_SIZE = 102;
    private static final int HINT_TINT = 0xff57e0b4;
    private final Bundle receipt = new Bundle();
    private int comparisons;
    private String phase;
    private boolean externalFrames;

    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase = arguments.getString("phase", "");
        externalFrames = "true".equals(arguments.getString("externalFrames"));
        start();
    }

    @Override public void onStart() {
        receipt.putString("phase", phase);
        receipt.putString("pid", Integer.toString(android.os.Process.myPid()));
        try {
            Activity activity = launch();
            if ("prepare".equals(phase)) {
                var menu = viewsOf(activity);
                compare("The main menu offers three playable chapters and two disabled ToDo entries", true,
                        button(activity, menu, R.string.open_mate_one).isEnabled() && button(activity, menu, R.string.open_mate_two).isEnabled()
                                && button(activity, menu, R.string.open_mate_three).isEnabled() && !button(activity, menu, R.string.menu_chess_todo).isEnabled()
                                && !button(activity, menu, R.string.menu_analysis_todo).isEnabled());
                var playable = List.of(button(activity, menu, R.string.open_mate_one), button(activity, menu, R.string.open_mate_two),
                        button(activity, menu, R.string.open_mate_three));
                captureFrame("menu", "All three chapter buttons are fully rendered after launch", image -> playable.stream().allMatch(entry ->
                        fullyVisible(entry) && hasColor(image, entry, entry.getCurrentTextColor(), 32)));
                runOnMainSync(activity::finish);
                checkRomanianCompactScreen();
                activity = launch();
                openChapter(activity, R.string.open_mate_one);
                var views = viewsOf(activity);
                prepare(activity, views, findBoard(views));
            } else if ("restore".equals(phase)) {
                finishMateInThree(activity);
                switchChapter(activity, R.string.open_mate_two);
                var views = viewsOf(activity);
                restore(activity, views, findBoard(views));
            }
            else throw new IllegalArgumentException("Unknown sanity phase: " + phase);
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
        if (intent.getBooleanExtra("j7-romanian-compact", false)) {
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
        var menu = viewsOf(activity);
        var scroll = menu.stream().filter(ScrollView.class::isInstance).map(ScrollView.class::cast).findFirst().orElseThrow();
        var last = button(activity, menu, R.string.menu_analysis_todo);
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
        tap(button(activity, views, R.string.hint));
        tap(button(activity, views, R.string.show_solution));
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
        compare("The first of 102 quests is shown", true, atQuest(activity, views, 1));
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
        tap(button(activity, views, R.string.hint));
        String expectedHint = activity.getString(R.string.hint_description, "d8", activity.getString(R.string.black), activity.getString(R.string.queen));
        String origin = activity.getString(R.string.piece_square, "d8", activity.getString(R.string.black), activity.getString(R.string.queen));
        View piece = cell(board, "d8");
        View arrowMidpoint = cell(board, "f6");
        View destination = cell(board, "h4");
        var showSolution = button(activity, views, R.string.show_solution);
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
        tap(button(activity, views, R.string.why_mate));
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
        prepareMateInTwo(activity, views, board);
        prepareMateInThree(activity);
    }

    private AccessibilityNodeInfo dialogNode(String text) throws Exception {
        return dialogNode(text, node -> true);
    }

    private AccessibilityNodeInfo dialogNode(String text, Predicate<AccessibilityNodeInfo> matches) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 4500;
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

    private void restore(Activity activity, List<View> views, BoardView board) throws Exception {
        finishMateInTwo(activity, views, board);
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
        for (var quest : QuestCatalog.load()) order.add(quest.id());
        var store = new QuestProgressStore(getTargetContext());
        if (!store.save(store.read(order).select("mate-101"))) throw new AssertionError("Could not select the counter-mate fixture");
        Activity counter = launch();
        List<View> counterViews = viewsOf(counter);
        BoardView counterBoard = findBoard(counterViews);
        compare("mate-101 opens at position 57", true, atQuest(counter, counterViews, 57));
        compare("The counter-mate opens with both legacy solves", true, solvedCount(counter, counterViews, 2));
        solveCounterMate(counter, counterViews, counterBoard, "mate-101", "h6", "d2", R.string.bishop);
        tap(button(counter, counterViews, R.string.next_quest));
        compare("mate-102 follows at position 58", true, atQuest(counter, counterViews, 58));
        solveCounterMate(counter, counterViews, counterBoard, "mate-102", "h3", "g4", R.string.pawn);
        compare("The two new solves add to the two legacy solves", true, solvedCount(counter, counterViews, 4));
        tap(button(counter, counterViews, R.string.next_quest));
        var fifth = QuestCatalog.load().get(58).solution();
        if (fifth.promotion() != null) throw new AssertionError("The milestone fixture requires a move without promotion");
        tap(cell(counterBoard, BoardSquare.name(fifth.from())));
        tap(cell(counterBoard, BoardSquare.name(fifth.to())));
        var track = progressTrack(counterViews);
        String milestone = counter.getString(R.string.milestone_reached, 5, 15);
        compare("The fifth solve unlocks the next milestone without hiding the board or controls", true,
                solvedCount(counter, counterViews, 5) && track.getProgress() == 5
                        && hasText(counterViews, milestone) && hasDescription(counterViews, counter.getString(R.string.quest_solved) + ". " + milestone)
                        && fullyVisible(counterBoard) && fullyVisible(button(counter, counterViews, R.string.why_mate)));
        captureFrame("milestone", "The progress bar visibly fills at the first milestone", image -> hasColor(image, track, 0xffb8d88a, 8));
    }

    private void prepareMateInTwo(Activity activity, List<View> views, BoardView board) throws Exception {
        switchChapter(activity, R.string.open_mate_two);
        var quest = QuestCatalog.loadMateInTwo().get(0);
        compare("The new chapter opens with its own 100-quest counter", true,
                hasText(views, activity.getString(R.string.mate_two_progress, 1, 100)) && mateTwoCount(activity, views, 0)
                        && turnLabel(activity, board, R.string.white, R.string.black, true));
        Move wrong = quest.position().legalMoves().stream().filter(move -> !move.equals(quest.solution())).findFirst().orElseThrow();
        playMove(board, wrong);
        compare("A wrong opening keeps White to move and asks for forced mate in two", true,
                hasText(views, activity.getString(R.string.mate_two_try_again)) && turnLabel(activity, board, R.string.white, R.string.black, true));
        tap(button(activity, views, R.string.hint));
        tap(button(activity, views, R.string.show_solution));
        compare("The opening hint shows the unique first move", true, hasText(views, suggestedMove(activity, quest.solution())));
        long started = beginDefense(activity, views, board, quest.solution(), false);
        String defenseText = activity.getString(R.string.mate_two_reply, moveText(activity, quest.defenseAfter(0)));
        compare("The rendered defense follows the player's move by at least 650 ms", true, awaitText(views, defenseText) - started >= 650);
        var defense = quest.defenseAfter(0);
        compare("The opponent replies and the first move does not count as a solve", true,
                hasText(views, activity.getString(R.string.mate_two_reply, moveText(activity, defense))) && mateTwoCount(activity, views, 0)
                        && turnLabel(activity, board, R.string.white, R.string.black, true));
        var square = cell(board, BoardSquare.name(defense.to()));
        int color = BoardSquare.isLight(defense.to()) ? 0xfff4ef86 : 0xffbec953;
        captureFrame("mate-two", "The mate-in-two board highlights the opponent's defense", image -> hasColor(image, square, color, 16));
    }

    private void finishMateInTwo(Activity activity, List<View> views, BoardView board) {
        var quest = QuestCatalog.loadMateInTwo().get(0);
        compare("Cold launch restores the second move after the opponent's defense", true,
                hasText(views, activity.getString(R.string.mate_two_progress, 1, 100)) && mateTwoCount(activity, views, 0)
                        && turnLabel(activity, board, R.string.white, R.string.black, true)
                        && hasText(views, activity.getString(R.string.mate_two_reply, moveText(activity, quest.defenseAfter(0)))));
        tap(button(activity, views, R.string.hint));
        tap(button(activity, views, R.string.show_solution));
        compare("The second hint follows the current position and shows the mate", true, hasText(views, suggestedMove(activity, quest.matingMove())));
        playMove(board, quest.matingMove());
        compare("Playing the mate increments only the new chapter", true,
                mateTwoCount(activity, views, 1) && hasText(views, activity.getString(R.string.quest_solved)));
        switchChapter(activity, R.string.open_mate_one);
    }

    private void openChapter(Activity activity, int label) {
        tap(button(activity, viewsOf(activity), label));
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
        compare("Mate in three opens with 100 puzzles and White to move", true,
                hasText(views, activity.getString(R.string.mate_three_progress, 1, 100)) && mateTwoCount(activity, views, 0)
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
                        && !next.isEnabled() && mateTwoCount(activity, views, 0));
        int destination = quest.defenseAfter(1).to();
        var square = cell(board, BoardSquare.name(destination));
        int color = BoardSquare.isLight(destination) ? 0xfff4ef86 : 0xffbec953;
        captureFrame("mate-three", "The board highlights the restored second defense in mate in three", image -> hasColor(image, square, color, 16));
        playMove(board, quest.matingMove());
        compare("Only the final mate marks the three-move quest as solved", true,
                mateTwoCount(activity, views, 1) && hasText(views, activity.getString(R.string.quest_solved)) && next.isEnabled());
        tap(next);
        compare("Next keeps the player in Mate in 3 and locks again on the next unsolved quest", true,
                hasText(views, activity.getString(R.string.mate_three_progress, 2, 100)) && !next.isEnabled());
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
                    && !target.isEnabled() && !button(activity, views, R.string.hint).isEnabled()
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

    private static boolean mateTwoCount(Activity activity, List<View> views, int count) {
        return hasText(views, activity.getString(R.string.solved_count, count, 100));
    }

    private static boolean turnLabel(Activity activity, BoardView board, int active, int other, boolean bottom) {
        var panel = (ViewGroup) board.getParent();
        var mover = (TextView) panel.getChildAt(bottom ? 2 : 0);
        var opponent = (TextView) panel.getChildAt(bottom ? 0 : 2);
        return activity.getString(R.string.to_move, activity.getString(active)).contentEquals(mover.getText())
                && activity.getString(other).contentEquals(opponent.getText());
    }

    private void solveCounterMate(Activity activity, List<View> views, BoardView board, String id, String from, String to, int piece) throws Exception {
        tap(button(activity, views, R.string.hint));
        String hint = activity.getString(R.string.hint_description, from, activity.getString(R.string.white), activity.getString(piece));
        compare(id + " names the defending piece and origin", true, hasText(views, hint));
        tap(button(activity, views, R.string.show_solution));
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
        var controls = List.of(button(activity, views, R.string.previous_quest), button(activity, views, hintLabel),
                button(activity, views, R.string.next_quest));
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

    private void captureFrame(String name, String assertion, Predicate<Bitmap> rendered) throws IOException {
        long deadline = SystemClock.uptimeMillis() + 12000;
        do {
            Bitmap image = getUiAutomation().takeScreenshot();
            if (image != null) {
                try {
                    if (rendered.test(image)) {
                        saveFrame(name, image);
                        compare(assertion, true, true);
                        return;
                    }
                } finally {
                    image.recycle();
                }
            }
            SystemClock.sleep(75);
        } while (SystemClock.uptimeMillis() < deadline);
        compare(assertion, true, false);
    }

    private void saveFrame(String name, Bitmap image) throws IOException {
        var context = getTargetContext();
        var folder = externalFrames ? context.getExternalFilesDir(null) : context.getFilesDir();
        if (folder == null) throw new IOException("Screenshot storage is unavailable");
        try (var output = new FileOutputStream(new File(folder, "j7-" + name + ".png"))) {
            if (!image.compress(Bitmap.CompressFormat.PNG, 100, output)) throw new IOException("PNG write failed");
        }
    }

    private boolean hasColor(Bitmap image, View view, int color, int required) {
        int[] location = new int[2];
        runOnMainSync(() -> view.getLocationOnScreen(location));
        int marked = 0;
        int right = Math.min(image.getWidth(), location[0] + view.getWidth());
        int bottom = Math.min(image.getHeight(), location[1] + view.getHeight());
        for (int y = Math.max(0, location[1]); y < bottom; y++) {
            for (int x = Math.max(0, location[0]); x < right; x++) {
                if (image.getPixel(x, y) == color && ++marked >= required) return true;
            }
        }
        return false;
    }

    private void tap(View view) {
        float[] center = new float[2];
        Rect visible = new Rect();
        boolean[] available = {false};
        runOnMainSync(() -> {
            available[0] = view.getGlobalVisibleRect(visible) && view.isEnabled();
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            center[0] = location[0] + view.getWidth() / 2f;
            center[1] = location[1] + view.getHeight() / 2f;
        });
        if (!available[0]) {
            throw new AssertionError("Touch target unavailable: " + (view instanceof Button control ? control.getText() : view.getContentDescription()));
        }
        long start = SystemClock.uptimeMillis();
        inject(start, MotionEvent.ACTION_DOWN, center[0], center[1]);
        inject(start, MotionEvent.ACTION_UP, center[0], center[1]);
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
