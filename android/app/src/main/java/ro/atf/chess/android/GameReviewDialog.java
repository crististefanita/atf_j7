package ro.atf.chess.android;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview;
import ro.atf.chess.GameReviewTimeline;
import ro.atf.chess.QuestHint;

final class GameReviewDialog {
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int ACCENT = 0xffb8d88a;
    private final Context context;
    private final Side humanSide;
    private final boolean blackBelow;
    enum Mode {
        MATCH(R.string.game_review_rating_note), MANUAL_MATCH(R.string.game_review_manual_rating),
        LIVE_MATCH(R.string.game_review_live_rating), STUDY(R.string.game_review_study_rating);

        final int ratingNote;

        Mode(int ratingNote) { this.ratingNote = ratingNote; }

        boolean snapshot() { return this == LIVE_MATCH || this == STUDY; }
    }

    private final Mode mode;
    private final Function<Move, String> notation;
    private final IntConsumer showPosition;
    private final IntConsumer continueFrom;
    private final LinearLayout content;
    private final Dialog dialog;
    private GameReviewTimeline timeline;
    private BoardView reviewBoard;
    private TextView selectedMove;
    private TextView selectedScore;
    private TextView lineProgress;
    private Button previousMove;
    private Button nextMove;
    private Button nextKeyMoment;
    private Button previousLine;
    private Button showLine;
    private Button nextLine;
    private Button onBoard;
    private Button retry;
    private MoveRows moveRows;
    private EvaluationGraph graph;

    private GameReviewDialog(Context context, Side humanSide, boolean blackBelow, Mode mode, Function<Move, String> notation,
                             IntConsumer showPosition, IntConsumer continueFrom) {
        this.context = context;
        this.humanSide = humanSide;
        this.blackBelow = blackBelow;
        this.mode = mode;
        this.notation = notation;
        this.showPosition = showPosition;
        this.continueFrom = continueFrom;
        dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        var screen = column();
        screen.setBackgroundColor(0xff262522);
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        var header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), 0, dp(8), 0);
        var title = text(context.getString(mode == Mode.STUDY ? R.string.manual_game_review : R.string.match_game_review), 22, PRIMARY);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        var close = new Button(context, null, android.R.attr.borderlessButtonStyle);
        close.setAllCaps(false);
        close.setText(R.string.game_review_close);
        close.setMinHeight(dp(48));
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(-2, -2));
        screen.addView(header, new LinearLayout.LayoutParams(-1, -2));
        content = column();
        screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(screen);
    }

    static Dialog show(Context context, ChessMatch match, GameReview cached, ExecutorService worker,
                            Function<Move, String> notation, boolean manualMoves, boolean blackBelow,
                            String human, String bot, Consumer<GameReview> remember,
                            IntConsumer showPosition, IntConsumer continueFrom, Runnable onClose) {
        return open(context, match, cached, worker, notation, manualMoves ? Mode.MANUAL_MATCH : Mode.MATCH,
                blackBelow, human, bot, remember, showPosition, continueFrom, onClose);
    }

    static Dialog showLine(Context context, ChessMatch match, ExecutorService worker, Function<Move, String> notation,
                           Mode mode, boolean blackBelow, String human, String bot,
                           IntConsumer showPosition, IntConsumer continueFrom, Runnable onClose) {
        return open(context, match, null, worker, notation, mode, blackBelow, human, bot,
                result -> {}, showPosition, continueFrom, onClose);
    }

    private static Dialog open(Context context, ChessMatch match, GameReview cached, ExecutorService worker,
                               Function<Move, String> notation, Mode mode, boolean blackBelow,
                               String human, String bot, Consumer<GameReview> remember,
                               IntConsumer showPosition, IntConsumer continueFrom, Runnable onClose) {
        var view = new GameReviewDialog(context, mode == Mode.STUDY ? Side.WHITE : match.setup().humanSide(), blackBelow, mode,
                notation, showPosition, continueFrom);
        view.dialog.show();
        view.dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        var job = view.load(match, cached, worker, human, bot, remember);
        view.dialog.setOnDismissListener(dialog -> {
            if (job != null) job.cancel(true);
            onClose.run();
        });
        return view.dialog;
    }

    private Future<?> load(ChessMatch match, GameReview cached, ExecutorService worker, String human, String bot,
                           Consumer<GameReview> remember) {
        if (cached != null) {
            display(match, cached, human, bot);
            return null;
        }
        var label = text(context.getString(R.string.game_review_progress, 0, match.ply()), 16, PRIMARY);
        var progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(match.ply());
        content.setPadding(dp(16), dp(10), dp(16), 0);
        content.addView(label);
        content.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        return worker.submit(() -> {
            try {
                IntConsumer update = ply -> content.post(() -> {
                    if (!dialog.isShowing()) return;
                    progress.setProgress(ply);
                    label.setText(context.getString(R.string.game_review_progress, ply, match.ply()));
                });
                var result = mode.snapshot() ? GameReview.analyzeLine(match, update) : GameReview.analyze(match, update);
                content.post(() -> {
                    if (!dialog.isShowing()) return;
                    remember.accept(result);
                    display(match, result, human, bot);
                });
            } catch (CancellationException cancelled) {
                // Closing the dialog cancels the local search.
            } catch (RuntimeException | LinkageError failure) {
                android.util.Log.e("GameReview", "Unable to analyze the game", failure);
                content.post(() -> {
                    if (!dialog.isShowing()) return;
                    label.setText(R.string.game_review_failed);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void display(ChessMatch match, GameReview result, String human, String bot) {
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        timeline = new GameReviewTimeline(match, result);
        var review = column();
        review.setId(R.id.game_review_workspace);
        review.setPadding(dp(8), 0, dp(8), 0);

        var boardFrame = new FrameLayout(context);
        int screenDp = Math.round(context.getResources().getDisplayMetrics().heightPixels
                / context.getResources().getDisplayMetrics().density);
        int boardSize = Math.max(176, Math.min(292, Math.round(screenDp * .31f)));
        reviewBoard = new BoardView(context, ignored -> { });
        reviewBoard.setId(R.id.game_review_board);
        reviewBoard.setContentDescription(context.getString(R.string.game_review_board));
        var boardParams = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER);
        boardFrame.addView(reviewBoard, boardParams);
        review.addView(boardFrame, new LinearLayout.LayoutParams(-1, dp(boardSize)));

        selectedMove = text("", 16, ACCENT);
        selectedMove.setId(R.id.game_review_selection);
        selectedMove.setGravity(Gravity.CENTER);
        selectedMove.setMaxLines(2);
        selectedMove.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        review.addView(selectedMove, new LinearLayout.LayoutParams(-1, -2));
        selectedScore = text("", 12, MUTED);
        selectedScore.setId(R.id.game_review_selected_score);
        selectedScore.setGravity(Gravity.CENTER);
        selectedScore.setMaxLines(2);
        selectedScore.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        review.addView(selectedScore, new LinearLayout.LayoutParams(-1, -2));

        var navigation = row();
        previousMove = actionButton(R.string.game_review_previous, () -> moveCursor(false));
        previousMove.setId(R.id.game_review_previous);
        nextKeyMoment = actionButton(R.string.game_review_next_key, this::nextKeyMoment);
        nextKeyMoment.setId(R.id.game_review_next_key);
        nextMove = actionButton(R.string.game_review_next, () -> moveCursor(true));
        nextMove.setId(R.id.game_review_next);
        addWeighted(navigation, previousMove);
        addWeighted(navigation, nextKeyMoment);
        addWeighted(navigation, nextMove);
        review.addView(navigation, new LinearLayout.LayoutParams(-1, -2));

        var variation = row();
        previousLine = actionButton(R.string.game_review_line_previous, () -> stepBestLine(false));
        previousLine.setId(R.id.game_review_line_previous);
        showLine = actionButton(R.string.game_review_show_best, this::showBestLine);
        showLine.setId(R.id.game_review_show_best);
        nextLine = actionButton(R.string.game_review_line_next, () -> stepBestLine(true));
        nextLine.setId(R.id.game_review_line_next);
        addWeighted(variation, previousLine);
        addWeighted(variation, showLine);
        addWeighted(variation, nextLine);
        review.addView(variation, new LinearLayout.LayoutParams(-1, -2));
        lineProgress = text("", 12, MUTED);
        lineProgress.setId(R.id.game_review_line_progress);
        lineProgress.setGravity(Gravity.CENTER);
        review.addView(lineProgress, new LinearLayout.LayoutParams(-1, -2));

        var actions = row();
        onBoard = actionButton(R.string.game_review_on_board, () -> {
            int selected = timeline.ply();
            dialog.dismiss();
            showPosition.accept(selected);
        });
        onBoard.setId(R.id.game_review_on_board);
        retry = actionButton(R.string.game_review_retry, () -> {
            int beforeMove = timeline.ply() - 1;
            dialog.dismiss();
            continueFrom.accept(beforeMove);
        });
        retry.setId(R.id.game_review_retry);
        addWeighted(actions, onBoard);
        addWeighted(actions, retry);
        review.addView(actions, new LinearLayout.LayoutParams(-1, -2));

        var list = new ListView(context);
        list.setId(R.id.game_review_moves);
        var summary = column();
        summary.setPadding(dp(16), dp(6), dp(16), dp(10));
        if (result.opening() != null) {
            summary.addView(text(result.opening().name() + " · " + result.opening().eco(), 17, ACCENT));
        } else {
            summary.addView(text(context.getString(R.string.game_review_unknown_opening), 16, MUTED));
        }
        summary.addView(text(context.getString(R.string.game_review_graph), 13, MUTED));
        graph = new EvaluationGraph(result, this::selectPly);
        graph.setId(R.id.game_review_graph);
        summary.addView(graph, new LinearLayout.LayoutParams(-1, dp(64)));
        summary.addView(player(human, humanSide, humanSide == Side.WHITE ? result.white() : result.black()));
        summary.addView(player(bot, humanSide.other(), humanSide == Side.WHITE ? result.black() : result.white()));
        summary.addView(text(context.getString(mode.ratingNote), 12, MUTED));
        summary.addView(comparison(result));
        list.addHeaderView(summary, null, false);
        moveRows = new MoveRows(result.turns());
        list.setAdapter(moveRows);
        list.setOnItemClickListener((parent, item, position, id) -> {
            if (position > 0) selectPly(moveRows.getItem(position - 1).ply());
        });
        review.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
        content.addView(review, new LinearLayout.LayoutParams(-1, -1));
        refreshTimeline();
    }

    private LinearLayout row() {
        var layout = new LinearLayout(context);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private Button actionButton(int label, Runnable action) {
        var button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        button.setAllCaps(false);
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void addWeighted(LinearLayout parent, View child) {
        parent.addView(child, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void moveCursor(boolean forward) {
        if (forward) timeline.next();
        else timeline.previous();
        refreshTimeline();
    }

    private void nextKeyMoment() {
        timeline.nextKeyMomentPly().ifPresent(timeline::seek);
        refreshTimeline();
    }

    private void showBestLine() {
        timeline.showBestLine();
        refreshTimeline();
    }

    private void stepBestLine(boolean forward) {
        if (forward) timeline.nextBestLineStep();
        else timeline.previousBestLineStep();
        refreshTimeline();
    }

    private void selectPly(int ply) {
        timeline.seek(ply);
        refreshTimeline();
    }

    private void refreshTimeline() {
        var selected = timeline.turn().orElse(null);
        if (selected == null) {
            selectedMove.setText(R.string.game_review_start_position);
            selectedScore.setText(R.string.game_review_initial_position);
        } else {
            selectedMove.setText(context.getString(R.string.game_review_selected_move,
                    moveLabel(selected), gradeLabel(selected.grade())));
            String score = context.getString(R.string.game_review_selected_score,
                    selected.bestPoints() / 10.0, selected.playedPoints() / 10.0, selected.depth());
            if (selected.grade() == GameReview.Grade.BRILLIANT) {
                score += " · " + context.getString(R.string.game_review_sacrifice, selected.sacrificeCp() / 100.0);
            }
            selectedScore.setText(score);
        }
        reviewBoard.show(timeline.position(), blackBelow, -1, List.of(), timeline.lastMove(), timeline.cue(), false,
                R.id.game_review_selection);
        previousMove.setEnabled(timeline.hasPrevious());
        nextMove.setEnabled(timeline.hasNext());
        nextKeyMoment.setEnabled(timeline.nextKeyMomentPly().isPresent());
        previousLine.setEnabled(timeline.canStepBestLineBackward());
        showLine.setEnabled(timeline.canShowBestLine());
        showLine.setText(timeline.showingBestLine() ? R.string.game_review_restart_best : R.string.game_review_show_best);
        nextLine.setEnabled(timeline.canStepBestLineForward());
        String lineState = timeline.showingBestLine()
                ? context.getString(R.string.game_review_line_step, timeline.bestLineStep(), timeline.bestLineLength())
                : context.getString(R.string.game_review_line_ready, timeline.bestLineLength());
        if (timeline.showingBestLine()) {
            lineState += timeline.nextBestMove().map(move -> " · "
                    + context.getString(R.string.game_review_line_next_move, notation.apply(move))).orElse("");
        }
        lineProgress.setText(lineState);
        retry.setVisibility(selected != null && (mode == Mode.STUDY || selected.side() == humanSide) ? View.VISIBLE : View.GONE);
        retry.setEnabled(selected != null);
        if (selected == null) {
            onBoard.setContentDescription(context.getString(R.string.game_review_show_start));
        } else {
            onBoard.setContentDescription(context.getString(R.string.game_review_on_board_move, moveLabel(selected)));
        }
        if (graph != null) graph.select(timeline.ply());
        if (moveRows != null) moveRows.select(timeline.ply());
    }

    private TextView player(String name, Side side, GameReview.Player player) {
        String accuracy = player.accuracy().isPresent() ? player.accuracy().getAsInt() + "%" : "—";
        String rating = player.rating().map(value -> "≈ " + value.elo() + " ("
                + (value.low() == null ? "≤ " + value.high() : value.low() + "–" + value.high()) + ")")
                .orElseGet(() -> context.getString(R.string.game_review_insufficient, player.ratedMoves()));
        int label = side == humanSide ? R.string.game_review_player : R.string.game_review_ai;
        String stats = mode == Mode.MATCH ? context.getString(label, accuracy, rating) : context.getString(R.string.game_review_accuracy_only, accuracy);
        var caption = (mode == Mode.STUDY ? name : name + " · " + context.getString(BoardView.sideLabel(side))) + "\n" + stats;
        return text(caption, 15, PRIMARY);
    }

    private View comparison(GameReview result) {
        var table = column();
        var category = context.getString(R.string.game_review_category);
        boolean study = mode == Mode.STUDY;
        var human = context.getString(study ? R.string.white : R.string.game_review_human_column);
        var ai = context.getString(study ? R.string.black : R.string.game_review_ai_column);
        var header = comparisonRow(category, human, ai);
        if (Build.VERSION.SDK_INT >= 28) header.setAccessibilityHeading(true);
        table.addView(header);
        var numbers = java.text.NumberFormat.getIntegerInstance(context.getResources().getConfiguration().getLocales().get(0));
        for (var count : result.compareGrades(humanSide)) {
            String grade = gradeLabel(count.grade());
            String humanCount = numbers.format(count.human());
            String aiCount = numbers.format(count.ai());
            var row = comparisonRow(grade, humanCount, aiCount);
            int counts = study ? R.string.game_review_study_counts : R.string.game_review_grade_counts;
            row.setContentDescription(context.getString(counts, grade, humanCount, aiCount));
            for (int index = 0; index < row.getChildCount(); index++) {
                row.getChildAt(index).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            }
            if (table.getChildCount() % 2 != 0) row.setBackgroundColor(0xff2e2c28);
            table.addView(row);
        }
        return table;
    }

    private LinearLayout comparisonRow(String category, String human, String ai) {
        var row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        if (Build.VERSION.SDK_INT >= 28) row.setScreenReaderFocusable(true);
        else row.setFocusable(true);
        row.setPadding(dp(8), 0, dp(8), 0);
        row.addView(text(category, 14, PRIMARY), new LinearLayout.LayoutParams(0, -2, 2));
        for (String value : List.of(human, ai)) {
            var label = text(value, 14, PRIMARY);
            label.setGravity(Gravity.CENTER);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        }
        return row;
    }

    private String moveLabel(GameReview.Turn turn) {
        return (turn.ply() + 1) / 2 + (turn.side() == Side.WHITE ? ". " : "… ") + notation.apply(turn.played());
    }

    private String gradeLabel(GameReview.Grade grade) {
        return context.getString(switch (grade) {
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

    private LinearLayout column() {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        var label = new TextView(context);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(0, dp(5), 0, dp(5));
        return label;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private final class MoveRows extends BaseAdapter {
        private final List<GameReview.Turn> turns;
        private int selectedPly;

        private MoveRows(List<GameReview.Turn> turns) { this.turns = turns; }
        private void select(int ply) { selectedPly = ply; notifyDataSetChanged(); }
        @Override public int getCount() { return turns.size(); }
        @Override public GameReview.Turn getItem(int index) { return turns.get(index); }
        @Override public long getItemId(int index) { return getItem(index).ply(); }

        @Override public View getView(int index, View recycled, ViewGroup parent) {
            var label = recycled instanceof TextView view ? view : text("", 15, PRIMARY);
            label.setMinHeight(dp(48));
            label.setPadding(dp(16), dp(8), dp(16), dp(8));
            var turn = getItem(index);
            label.setText(moveLabel(turn) + " · " + gradeLabel(turn.grade()));
            label.setTextColor(switch (turn.grade()) {
                case BRILLIANT, GREAT, BEST -> ACCENT;
                case MISTAKE, MISS, BLUNDER -> 0xffffb591;
                default -> PRIMARY;
            });
            label.setBackgroundColor(turn.ply() == selectedPly ? 0xff3d4933 : 0x00000000);
            label.setContentDescription(context.getString(R.string.game_review_move_row,
                    moveLabel(turn), gradeLabel(turn.grade()), turn.ply(), turns.size()));
            return label;
        }
    }

    private final class EvaluationGraph extends View {
        private final GameReview result;
        private final IntConsumer select;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selected;

        private EvaluationGraph(GameReview result, IntConsumer select) {
            super(context);
            this.result = result;
            this.select = select;
            select(timeline.ply());
            setClickable(!result.turns().isEmpty());
        }

        private void select(int ply) {
            selected = Math.max(0, Math.min(result.turns().size(), ply));
            setContentDescription(context.getString(R.string.game_review_graph));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth() - dp(4);
            float height = getHeight() - dp(4);
            float inset = dp(2);
            paint.setColor(0xff56564c);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(inset, inset + height / 2, inset + width, inset + height / 2, paint);
            paint.setColor(ACCENT);
            paint.setStrokeWidth(dp(2));
            float x = inset;
            float y = inset + height * (1 - result.initialWhitePoints() / 1000f);
            int count = result.turns().size();
            if (count == 0) {
                paint.setColor(PRIMARY);
                canvas.drawCircle(inset + width / 2, y, dp(5), paint);
                return;
            }
            for (var turn : result.turns()) {
                float nextX = inset + width * turn.ply() / count;
                float nextY = inset + height * (1 - turn.whitePoints() / 1000f);
                canvas.drawLine(x, y, nextX, nextY, paint);
                x = nextX;
                y = nextY;
            }
            float selectedX = inset + width * selected / count;
            float selectedY = inset + height * (1 - (selected == 0 ? result.initialWhitePoints()
                    : result.turns().get(selected - 1).whitePoints()) / 1000f);
            paint.setColor(PRIMARY);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            canvas.drawCircle(selectedX, selectedY, dp(5), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (isClickable() && event.getActionMasked() == MotionEvent.ACTION_UP && getWidth() > 0) {
                int count = result.turns().size();
                selected = Math.max(0, Math.min(count, Math.round(count * event.getX() / getWidth())));
            }
            return super.onTouchEvent(event);
        }

        @Override public boolean performClick() {
            super.performClick();
            if (result.turns().isEmpty()) return false;
            select.accept(selected);
            return true;
        }
    }
}
