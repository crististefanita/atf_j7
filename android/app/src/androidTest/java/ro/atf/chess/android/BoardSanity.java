package ro.atf.chess.android;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
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
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.QuestCatalog;

public final class BoardSanity extends Instrumentation {
    private static final int CATALOG_SIZE = 102;
    private static final int GOLD = 0xffffb64d;
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
            List<View> views = viewsOf(activity);
            BoardView board = findBoard(views);
            if ("prepare".equals(phase)) prepare(activity, views, board);
            else if ("restore".equals(phase)) restore(activity, views, board);
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
        Intent intent = new Intent(getTargetContext(), ChessActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Activity activity = startActivitySync(intent);
        waitForIdleSync();
        return activity;
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
        compare("The first of 102 quests is shown", true, atQuest(activity, views, 1));
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
        captureFrame("hint-piece", "The simple hint renders a gold ring and no arrow",
                image -> hasColor(image, piece, GOLD, 16) && !hasColor(image, arrowMidpoint, GOLD, 1));
        tap(showSolution);
        compare("The full hint reveals d8 to h4 and leaves the position playable", true,
                hasText(views, activity.getString(R.string.solution_move, "d8", "h4"))
                        && activity.getString(R.string.hint_destination, "h4").contentEquals(destination.getContentDescription())
                        && piece.getContentDescription().toString().contains(origin));
        captureFrame("hint-solution", "The full hint renders an arrow across the board", image -> hasColor(image, arrowMidpoint, GOLD, 16));
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
        String solved = activity.getString(R.string.quest_solved);
        compare("Cold launch resumes the third quest", true, atQuest(activity, views, 3));
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
        compare("The fifth solve unlocks the next milestone without hiding the board or controls", true,
                solvedCount(counter, counterViews, 5) && track.getProgress() == 5
                        && hasText(counterViews, counter.getString(R.string.milestone_reached, 5, 15))
                        && fullyVisible(counterBoard) && fullyVisible(button(counter, counterViews, R.string.why_mate)));
        captureFrame("milestone", "The progress bar visibly fills at the first milestone", image -> hasColor(image, track, 0xffb8d88a, 8));
    }

    private void solveCounterMate(Activity activity, List<View> views, BoardView board, String id, String from, String to, int piece) throws Exception {
        tap(button(activity, views, R.string.hint));
        String hint = activity.getString(R.string.hint_description, from, activity.getString(R.string.white), activity.getString(piece));
        compare(id + " names the defending piece and origin", true, hasText(views, hint));
        tap(button(activity, views, R.string.show_solution));
        compare(id + " reveals the unique mating reply", true, hasText(views, activity.getString(R.string.solution_move, from, to)));
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

    private static String coordinate(View view) { return view.getContentDescription().subSequence(0, 2).toString(); }

    private static View cell(BoardView board, String coordinate) {
        for (int index = 0; index < 64; index++) {
            if (coordinate.equals(coordinate(board.getChildAt(index)))) return board.getChildAt(index);
        }
        throw new IllegalArgumentException("Missing square " + coordinate);
    }
}
