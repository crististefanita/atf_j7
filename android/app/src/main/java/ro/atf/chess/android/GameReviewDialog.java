package ro.atf.chess.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview;
import ro.atf.chess.GameReviewExplanation;
import ro.atf.chess.GameReviewGraphLayout;
import ro.atf.chess.GameReviewTimeline;
import ro.atf.chess.GameReviewTimeline.Branch;
import ro.atf.chess.GameReviewTimeline.Snapshot;

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
    private final BiConsumer<Integer, Runnable> continueFrom;
    private final Snapshot initialSnapshot;
    private final Consumer<Snapshot> rememberSnapshot;
    private final LinearLayout content;
    private final Dialog dialog;
    private GameReviewTimeline timeline;
    private BoardView reviewBoard;
    private TextView selectedMove;
    private TextView selectedScore;
    private TextView lineProgress;
    private TextView opening;
    private ImageButton previousMove;
    private ImageButton nextMove;
    private ImageButton nextKeyMoment;
    private ImageButton showLine;
    private Button why;
    private Button playedBranch;
    private Button recommendedBranch;
    private LinearLayout explanationPanel;
    private TextView reason;
    private ListView report;
    private boolean explanationOpen;
    private Snapshot lastSnapshot;
    private MoveRows moveRows;
    private EvaluationGraph graph;

    private GameReviewDialog(Context context, Side humanSide, boolean blackBelow, Mode mode, Function<Move, String> notation,
                             IntConsumer showPosition, BiConsumer<Integer, Runnable> continueFrom,
                             Snapshot initialSnapshot, Consumer<Snapshot> rememberSnapshot) {
        this.context = context;
        this.humanSide = humanSide;
        this.blackBelow = blackBelow;
        this.mode = mode;
        this.notation = notation;
        this.showPosition = showPosition;
        this.continueFrom = continueFrom;
        this.initialSnapshot = initialSnapshot;
        this.rememberSnapshot = rememberSnapshot;
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
        header.setPadding(dp(16), 0, dp(4), 0);
        var title = text(context.getString(mode == Mode.STUDY ? R.string.manual_game_review : R.string.match_game_review), 18, PRIMARY);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        var close = new Button(context, null, android.R.attr.borderlessButtonStyle);
        close.setAllCaps(false);
        close.setText(R.string.game_review_close);
        close.setMinimumHeight(0);
        close.setMinHeight(dp(48));
        close.setOnClickListener(view -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(-2, dp(48)));
        screen.addView(header, new LinearLayout.LayoutParams(-1, -2));
        content = column();
        screen.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(screen);
    }

    static Dialog show(Context context, ChessMatch match, GameReview cached, ExecutorService worker,
                            Function<Move, String> notation, boolean manualMoves, boolean blackBelow,
                            String human, String bot, Consumer<GameReview> remember,
                            Snapshot initialSnapshot, Consumer<Snapshot> rememberSnapshot,
                            IntConsumer showPosition, BiConsumer<Integer, Runnable> continueFrom, Runnable onClose) {
        return open(context, match, cached, worker, notation, manualMoves ? Mode.MANUAL_MATCH : Mode.MATCH,
                blackBelow, human, bot, remember, initialSnapshot, rememberSnapshot, showPosition, continueFrom, onClose);
    }

    static Dialog showLine(Context context, ChessMatch match, ExecutorService worker, Function<Move, String> notation,
                           Mode mode, boolean blackBelow, String human, String bot,
                           IntConsumer showPosition, BiConsumer<Integer, Runnable> continueFrom, Runnable onClose) {
        return open(context, match, null, worker, notation, mode, blackBelow, human, bot,
                result -> {}, null, snapshot -> {}, showPosition, continueFrom, onClose);
    }

    private static Dialog open(Context context, ChessMatch match, GameReview cached, ExecutorService worker,
                               Function<Move, String> notation, Mode mode, boolean blackBelow,
                               String human, String bot, Consumer<GameReview> remember,
                               Snapshot initialSnapshot, Consumer<Snapshot> rememberSnapshot,
                               IntConsumer showPosition, BiConsumer<Integer, Runnable> continueFrom, Runnable onClose) {
        var view = new GameReviewDialog(context, mode == Mode.STUDY ? Side.WHITE : match.setup().humanSide(), blackBelow, mode,
                notation, showPosition, continueFrom, initialSnapshot, rememberSnapshot);
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
        if (initialSnapshot != null) timeline.restore(initialSnapshot);
        explanationOpen = timeline.branch() != Branch.HISTORY;
        lastSnapshot = timeline.snapshot();
        reviewBoard = new BoardView(context, ignored -> { });
        reviewBoard.setId(R.id.game_review_board);
        reviewBoard.setContentDescription(context.getString(R.string.game_review_board));

        var controls = column();
        selectedMove = text("", 15, ACCENT);
        selectedMove.setId(R.id.game_review_selection);
        selectedMove.setGravity(Gravity.CENTER);
        selectedMove.setSingleLine();
        selectedMove.setEllipsize(TextUtils.TruncateAt.END);
        selectedMove.setPadding(dp(8), dp(4), dp(8), 0);
        selectedMove.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        controls.addView(selectedMove, new LinearLayout.LayoutParams(-1, -2));

        var navigation = row();
        navigation.setGravity(Gravity.CENTER);
        previousMove = icon(R.drawable.match_chevron, R.id.game_review_previous, R.string.game_review_previous);
        previousMove.setScaleX(-1);
        previousMove.setOnClickListener(view -> moveCursor(false));
        nextMove = icon(R.drawable.match_chevron, R.id.game_review_next, R.string.game_review_next);
        nextMove.setOnClickListener(view -> moveCursor(true));
        showLine = icon(R.drawable.quest_bulb, R.id.game_review_show_best, R.string.game_review_show_best);
        showLine.setOnClickListener(view -> toggleBestLine());
        nextKeyMoment = icon(R.drawable.match_latest, R.id.game_review_next_key, R.string.game_review_next_key);
        nextKeyMoment.setOnClickListener(view -> nextKeyMoment());
        var actions = icon(R.drawable.match_more, R.id.game_review_actions, R.string.game_review_actions);
        actions.setOnClickListener(view -> showActions(view, result));
        for (var button : List.of(previousMove, showLine, nextMove, nextKeyMoment)) {
            navigation.addView(button, new LinearLayout.LayoutParams(dp(48), dp(48)));
        }
        why = textAction(R.id.game_review_why, R.string.game_review_why);
        why.setOnClickListener(view -> toggleExplanation());
        navigation.addView(why, new LinearLayout.LayoutParams(-2, dp(48)));
        navigation.addView(actions, new LinearLayout.LayoutParams(dp(48), dp(48)));
        var toolbar = new HorizontalScrollView(context);
        toolbar.setFillViewport(true);
        toolbar.addView(navigation);
        controls.addView(toolbar, new LinearLayout.LayoutParams(-1, -2));

        var list = new ListView(context) {
            @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
                if (action != AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_TO_POSITION.getId()) {
                    return super.performAccessibilityAction(action, arguments);
                }
                int row = arguments == null ? -1 : arguments.getInt(AccessibilityNodeInfo.ACTION_ARGUMENT_ROW_INT, -1);
                if (row < 0 || row >= getCount()) return false;
                // Jump directly to the requested row, including when the report is long.
                setSelectionFromTop(row, 0);
                return true;
            }
        };
        report = list;
        list.setId(R.id.game_review_moves);
        var summary = column();
        summary.setPadding(dp(16), dp(2), dp(16), dp(10));
        opening = text(result.opening() == null ? "" : openingName(result), 14, ACCENT);
        opening.setId(R.id.game_review_opening);
        opening.setSingleLine();
        opening.setEllipsize(TextUtils.TruncateAt.END);
        opening.setPadding(0, dp(3), 0, dp(3));
        summary.addView(opening);
        explanationPanel = column();
        reason = text("", 15, PRIMARY);
        reason.setId(R.id.game_review_reason);
        reason.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        explanationPanel.addView(reason);
        var branches = row();
        playedBranch = branchAction(R.id.game_review_played_branch, R.string.game_review_played_branch, Branch.PLAYED);
        playedBranch.setContentDescription(context.getString(R.string.game_review_played_branch_description));
        recommendedBranch = branchAction(R.id.game_review_recommended_branch, R.string.game_review_recommended_branch, Branch.RECOMMENDED);
        branches.addView(playedBranch, new LinearLayout.LayoutParams(0, -2, 1));
        branches.addView(recommendedBranch, new LinearLayout.LayoutParams(0, -2, 1));
        explanationPanel.addView(branches);
        summary.addView(explanationPanel);
        var graphTitle = text(context.getString(R.string.game_review_graph), 12, MUTED);
        graphTitle.setPadding(0, dp(3), 0, dp(2));
        summary.addView(graphTitle);
        graph = new EvaluationGraph(result, this::selectPly);
        graph.setId(R.id.game_review_graph);
        summary.addView(graph, new LinearLayout.LayoutParams(-1, dp(64)));
        selectedScore = text("", 12, MUTED);
        selectedScore.setId(R.id.game_review_selected_score);
        summary.addView(selectedScore);
        lineProgress = text("", 12, MUTED);
        lineProgress.setId(R.id.game_review_line_progress);
        summary.addView(lineProgress);
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
        var review = new GameReviewWorkspace(context, reviewBoard, controls, list);
        review.setId(R.id.game_review_workspace);
        content.addView(review, new LinearLayout.LayoutParams(-1, -1));
        refreshTimeline();
    }

    private LinearLayout row() {
        var layout = new LinearLayout(context);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private ImageButton icon(int drawable, int id, int label) {
        var button = new ImageButton(context);
        var background = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, background, true);
        button.setBackgroundResource(background.resourceId);
        button.setImageResource(drawable);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setId(id);
        labelIcon(button, label);
        button.setImageTintList(new ColorStateList(
                new int[][] {{-android.R.attr.state_enabled}, {android.R.attr.state_selected}, {}},
                new int[] {0xff77766d, 0xff57e0b4, MUTED}));
        return button;
    }

    private Button textAction(int id, int label) {
        var button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        button.setId(id);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {android.R.attr.state_selected}, {}},
                new int[]{0xff77766d, ACCENT, PRIMARY}));
        return button;
    }

    private Button branchAction(int id, int label, Branch branch) {
        var button = textAction(id, label);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(view -> {
            if (timeline.branch() == branch) timeline.showBranch(Branch.HISTORY);
            else timeline.showBranch(branch);
            refreshTimeline();
        });
        return button;
    }

    private void toggleExplanation() {
        explanationOpen = !explanationOpen;
        refreshTimeline();
        if (explanationOpen) report.setSelection(0);
    }

    private void showActions(View anchor, GameReview result) {
        var menu = new PopupMenu(context, anchor);
        menu.getMenu().add(R.string.game_review_on_board).setOnMenuItemClickListener(item -> {
            int selected = timeline.ply();
            dialog.dismiss();
            showPosition.accept(selected);
            return true;
        });
        if (timeline.turn().filter(turn -> mode == Mode.STUDY || turn.side() == humanSide).isPresent()) {
            menu.getMenu().add(R.string.game_review_retry).setOnMenuItemClickListener(item -> {
                int beforeMove = timeline.ply() - 1;
                continueFrom.accept(beforeMove, dialog::dismiss);
                return true;
            });
        }
        if (result.opening() != null) {
            menu.getMenu().add(R.string.game_review_opening_details).setOnMenuItemClickListener(item -> {
                new AlertDialog.Builder(context).setMessage(openingName(result)).setPositiveButton(android.R.string.ok, null).show();
                return true;
            });
        }
        menu.show();
    }

    private static String openingName(GameReview result) { return result.opening().name() + " · " + result.opening().eco(); }

    private void moveCursor(boolean forward) {
        if (timeline.branch() != Branch.HISTORY) {
            if (forward) timeline.stepForward();
            else timeline.stepBackward();
        } else if (forward) timeline.next();
        else timeline.previous();
        refreshTimeline();
    }

    private void nextKeyMoment() {
        timeline.nextKeyMomentPly().ifPresent(this::selectPly);
    }

    private void toggleBestLine() {
        timeline.showBranch(timeline.branch() == Branch.RECOMMENDED ? Branch.HISTORY : Branch.RECOMMENDED);
        refreshTimeline();
    }

    private void selectPly(int ply) {
        explanationOpen = false;
        timeline.seek(ply);
        refreshTimeline();
    }

    private void refreshTimeline() {
        var selected = timeline.turn().orElse(null);
        var branch = timeline.branch();
        boolean inLine = branch != Branch.HISTORY;
        String caption;
        if (selected == null) {
            caption = context.getString(R.string.game_review_start_position);
            selectedScore.setText(R.string.game_review_initial_position);
        } else {
            caption = context.getString(R.string.game_review_selected_move, moveLabel(selected), gradeLabel(selected.grade()));
            String score = context.getString(R.string.game_review_selected_score,
                    selected.bestPoints() / 10.0, selected.playedPoints() / 10.0, selected.depth());
            if (selected.grade() == GameReview.Grade.BRILLIANT) {
                score += " · " + context.getString(R.string.game_review_sacrifice, selected.sacrificeCp() / 100.0);
            }
            selectedScore.setText(score);
        }
        reviewBoard.show(timeline.position(), blackBelow, -1, List.of(), timeline.lastMove(), timeline.cue(), false,
                R.id.game_review_selection);
        previousMove.setId(inLine ? R.id.game_review_line_previous : R.id.game_review_previous);
        nextMove.setId(inLine ? R.id.game_review_line_next : R.id.game_review_next);
        labelIcon(previousMove, inLine ? R.string.game_review_line_previous : R.string.game_review_previous);
        labelIcon(nextMove, inLine ? R.string.game_review_line_next : R.string.game_review_next);
        previousMove.setEnabled(inLine ? timeline.canStepBackward() : timeline.hasPrevious());
        nextMove.setEnabled(inLine ? timeline.canStepForward() : timeline.hasNext());
        nextKeyMoment.setEnabled(timeline.nextKeyMomentPly().isPresent());
        showLine.setEnabled(timeline.canShowBestLine());
        showLine.setSelected(branch == Branch.RECOMMENDED);
        labelIcon(showLine, branch == Branch.RECOMMENDED ? R.string.game_review_hide_best : R.string.game_review_show_best);
        opening.setVisibility(timeline.atEntry() && opening.length() > 0 ? View.VISIBLE : View.GONE);
        lineProgress.setVisibility(inLine ? View.VISIBLE : View.GONE);
        if (inLine) {
            int label = branch == Branch.PLAYED ? R.string.game_review_played_branch : R.string.game_review_recommended_branch;
            String lineState = context.getString(R.string.game_review_branch_step, context.getString(label), timeline.lineStep(), timeline.lineLength());
            lineState += timeline.nextLineMove().map(move -> " · "
                    + context.getString(R.string.game_review_line_next_move, notation.apply(move))).orElse("");
            lineProgress.setText(lineState);
            selectedMove.setContentDescription(caption + " · " + lineState);
            selectedMove.setText(context.getString(R.string.game_review_pv_caption,
                    caption, timeline.lineStep(), timeline.lineLength()));
        } else {
            selectedMove.setContentDescription(null);
            selectedMove.setText(caption);
        }
        refreshExplanation(selected);
        if (graph != null) graph.select(timeline.ply());
        if (moveRows != null) moveRows.select(timeline.ply());
        var snapshot = timeline.snapshot();
        if (!snapshot.equals(lastSnapshot)) {
            rememberSnapshot.accept(snapshot);
            lastSnapshot = snapshot;
        }
    }

    private void refreshExplanation(GameReview.Turn turn) {
        var explanation = timeline.explanation().filter(value -> value.loss() > 0).orElse(null);
        why.setVisibility(explanation == null ? View.GONE : View.VISIBLE);
        if (explanation == null) explanationOpen = false;
        why.setSelected(explanationOpen);
        why.setContentDescription(context.getString(explanationOpen ? R.string.game_review_hide_why : R.string.game_review_why));
        explanationPanel.setVisibility(explanationOpen ? View.VISIBLE : View.GONE);
        if (!explanationOpen) return;
        reason.setText(explain(explanation, turn));
        playedBranch.setEnabled(timeline.canShowBranch(Branch.PLAYED));
        recommendedBranch.setEnabled(timeline.canShowBranch(Branch.RECOMMENDED));
        playedBranch.setSelected(timeline.branch() == Branch.PLAYED);
        recommendedBranch.setSelected(timeline.branch() == Branch.RECOMMENDED);
    }

    private String explain(GameReviewExplanation explanation, GameReview.Turn turn) {
        return explanation.evidence().map(evidence -> explainMove(explanation, evidence)).orElseGet(() ->
                context.getString(R.string.game_review_why_score, context.getString(BoardView.sideLabel(turn.side())),
                        turn.playedPoints() / 10.0, turn.bestPoints() / 10.0, turn.depth()));
    }

    private String explainMove(GameReviewExplanation explanation, GameReviewExplanation.MoveEvidence evidence) {
        String piece = pieceName(evidence.piece());
        String square = BoardSquare.name(evidence.move().to());
        return switch (explanation.reason()) {
            case ALLOWS_MATE -> context.getString(R.string.game_review_why_allows_mate, piece, square);
            case MISSES_MATE -> context.getString(R.string.game_review_why_misses_mate, piece, square);
            case LOSES_MATERIAL -> context.getString(R.string.game_review_why_material, piece, square,
                    pieceName(evidence.captured()), explanation.materialCp() / 100.0);
            case EVALUATION -> throw new IllegalArgumentException("A score-only explanation has no move evidence");
        };
    }

    private String pieceName(Kind piece) {
        var locale = context.getResources().getConfiguration().getLocales().get(0);
        return context.getString(BoardView.kindLabel(piece)).toLowerCase(locale);
    }

    private void labelIcon(ImageButton button, int label) {
        String description = context.getString(label);
        button.setContentDescription(description);
        button.setTooltipText(description);
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
            int whitePoints = selected == 0 ? result.initialWhitePoints() : result.turns().get(selected - 1).whitePoints();
            setContentDescription(context.getString(R.string.game_review_graph_selected, selected, result.turns().size(), whitePoints / 10.0));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = dp(2);
            float width = getWidth();
            float height = Math.max(0, getHeight() - 2 * inset);
            int count = result.turns().size();
            float x = (float) GameReviewGraphLayout.xForPly(0, count, width, inset);
            float axisStart = Math.min(inset, width / 2);
            paint.setColor(0xff56564c);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(axisStart, inset + height / 2, width - axisStart, inset + height / 2, paint);
            paint.setColor(ACCENT);
            paint.setStrokeWidth(dp(2));
            float y = inset + height * (1 - result.initialWhitePoints() / 1000f);
            if (count == 0) {
                paint.setColor(PRIMARY);
                canvas.drawCircle(x, y, dp(5), paint);
                return;
            }
            for (var turn : result.turns()) {
                float nextX = (float) GameReviewGraphLayout.xForPly(turn.ply(), count, width, inset);
                float nextY = inset + height * (1 - turn.whitePoints() / 1000f);
                canvas.drawLine(x, y, nextX, nextY, paint);
                x = nextX;
                y = nextY;
            }
            float selectedX = (float) GameReviewGraphLayout.xForPly(selected, count, width, inset);
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
                selected = GameReviewGraphLayout.plyAtX(event.getX(), result.turns().size(), getWidth(), dp(2));
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
