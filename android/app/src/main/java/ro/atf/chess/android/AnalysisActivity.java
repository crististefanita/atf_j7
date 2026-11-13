package ro.atf.chess.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.ImageButton;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import ro.atf.chess.AnalysisLine;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** A local study board whose line belongs to the activity, independently of the saved match. */
public final class AnalysisActivity extends Activity {
    static final int CONTINUATION_REQUEST = 17;
    static final String CONTINUATION = "analysisContinuation";
    static final String AI_AUTOMATIC = "analysisAutomaticAi";
    static final String ORIENTATION = "analysisBlackBelow";
    private static final String LINE = "line";
    private static final String PLY = "ply";
    private static final String BLACK_BELOW = "blackBelow";
    private static final String ANALYSIS_ENABLED = "analysisEnabled";
    private static final String ARCHIVE_ID = "archiveId";
    private static final String MANUAL_MOVES = "manualMoves";
    private static final String HUMAN_NAME = "humanName";
    private static final String BOT_NAME = "botName";
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int ACTIVE = 0xffb8d88a;
    private AnalysisLine entry;
    private AnalysisLine study;
    private ChessMatch sourceMatch;
    private boolean blackBelow;
    private boolean analysisEnabled = true;
    private final ExecutorService reviewWorker = Executors.newSingleThreadExecutor();
    private android.app.Dialog reviewDialog;
    private int selected = -1;
    private List<Move> options = List.of();
    private BoardView board;
    private TextView top;
    private TextView bottom;
    private TextView status;
    private MatchHistoryView history;
    private MatchAnalysisView analysis;

    static void open(Activity owner, ChessMatch match, int ply, boolean blackBelow) {
        open(owner, match, ply, blackBelow, null, false, null, null);
    }

    static void open(Activity owner, ChessMatch match, int ply, boolean blackBelow,
                     String archiveId, boolean manualMoves, String humanName, String botName) {
        var intent = new Intent(owner, AnalysisActivity.class).putExtra(LINE, match.checkpoint())
                .putExtra(PLY, ply).putExtra(BLACK_BELOW, blackBelow).putExtra(ARCHIVE_ID, archiveId)
                .putExtra(MANUAL_MOVES, manualMoves).putExtra(HUMAN_NAME, humanName).putExtra(BOT_NAME, botName);
        owner.startActivityForResult(intent, CONTINUATION_REQUEST);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var intent = getIntent();
        sourceMatch = ChessMatch.restore(intent.getStringExtra(LINE));
        entry = new AnalysisLine(sourceMatch, intent.getIntExtra(PLY, 0));
        study = state == null ? entry : new AnalysisLine(ChessMatch.restore(state.getString(LINE)), state.getInt(PLY));
        blackBelow = state == null ? intent.getBooleanExtra(BLACK_BELOW, false) : state.getBoolean(BLACK_BELOW);
        analysisEnabled = state == null || state.getBoolean(ANALYSIS_ENABLED, true);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        var screen = new LinearLayout(this);
        screen.setOrientation(LinearLayout.VERTICAL);
        screen.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        screen.setBackgroundColor(0xff262522);
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        var header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        var back = button(R.drawable.match_chevron, R.string.manual_close, view -> finish());
        back.setScaleX(-1);
        addHeaderAction(header, back);
        var title = text(19);
        title.setText(R.string.manual_analysis);
        title.setGravity(Gravity.CENTER_VERTICAL);
        BoardCaption.reserve(title, 2);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        var reset = button(android.R.drawable.ic_menu_revert, R.string.manual_reset, view -> select(entry));
        reset.setId(R.id.manual_reset_line);
        addHeaderAction(header, reset);
        var menu = button(R.drawable.match_more, R.string.manual_options, this::showOptions);
        menu.setId(R.id.manual_options);
        addHeaderAction(header, menu);
        screen.addView(header);
        top = text(15);
        bottom = text(15);
        top.setId(View.generateViewId());
        bottom.setId(View.generateViewId());
        board = new BoardView(this, this::tap);
        analysis = new MatchAnalysisView(this, this::notation, () -> select(study));
        screen.addView(new BoardPanel(this, top, board, bottom, analysis.evaluationBar()), new LinearLayout.LayoutParams(-1, 0, 1));
        status = text(14);
        status.setGravity(Gravity.CENTER);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        BoardCaption.reserve(status, 2);
        screen.addView(status);
        screen.addView(analysis);
        history = new MatchHistoryView(this, this::notation, ply -> select(study.seek(ply)), analysis.bulb());
        screen.addView(history, new LinearLayout.LayoutParams(-1, -2));
        return screen;
    }

    private ImageButton button(int icon, int label, View.OnClickListener action) {
        var button = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(PRIMARY));
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setContentDescription(getString(label));
        button.setOnClickListener(action);
        return button;
    }

    private void addHeaderAction(LinearLayout header, ImageButton button) {
        int touchTarget = Math.round(48 * getResources().getDisplayMetrics().density);
        header.addView(button, new LinearLayout.LayoutParams(touchTarget, touchTarget));
    }

    private TextView text(int size) {
        var label = new TextView(this);
        label.setTextSize(size);
        label.setTextColor(PRIMARY);
        return label;
    }

    private void select(AnalysisLine line) {
        study = line;
        selected = -1;
        options = List.of();
        render();
    }

    private void showOptions(View anchor) {
        var optionsMenu = new PopupMenu(this, anchor);
        var items = optionsMenu.getMenu();
        items.add(R.string.manual_enable_analysis).setCheckable(true).setChecked(analysisEnabled).setOnMenuItemClickListener(item -> {
            analysisEnabled = !analysisEnabled;
            if (!analysisEnabled) analysis.cancel();
            render();
            return true;
        });
        items.add(R.string.manual_game_review).setEnabled(analysisEnabled && study.line().ply() > 0).setOnMenuItemClickListener(item -> {
            showReview();
            return true;
        });
        items.add(R.string.manual_continue_game).setEnabled(study.result() == ChessMatch.Result.IN_PROGRESS).setOnMenuItemClickListener(item -> {
            confirmContinuation(study);
            return true;
        });
        items.add(R.string.flip).setOnMenuItemClickListener(item -> {
            blackBelow = !blackBelow;
            render();
            return true;
        });
        optionsMenu.show();
    }

    private void showReview() {
        if (!analysisEnabled || study.line().ply() == 0 || reviewDialog != null) return;
        var snapshot = study;
        IntConsumer seek = ply -> select(snapshot.seek(ply));
        IntConsumer resume = ply -> confirmContinuation(snapshot.seek(ply));
        Runnable onClose = () -> reviewDialog = null;
        if (snapshot.preservesCompletedGame(sourceMatch)) {
            var intent = getIntent();
            var store = new ChessProgressStore(this);
            String id = intent.getStringExtra(ARCHIVE_ID);
            var cached = store.matches().readReview(id, sourceMatch);
            reviewDialog = GameReviewDialog.show(this, sourceMatch, cached, reviewWorker, this::notation,
                    intent.getBooleanExtra(MANUAL_MOVES, false), blackBelow,
                    intent.getStringExtra(HUMAN_NAME), intent.getStringExtra(BOT_NAME),
                    review -> {
                        if (store.matches().hasArchived(id) && !store.matches().saveReview(id, sourceMatch, review)) {
                            Toast.makeText(this, R.string.progress_save_failed, Toast.LENGTH_SHORT).show();
                        }
                    }, seek, resume, onClose);
            return;
        }
        reviewDialog = GameReviewDialog.showLine(this, snapshot.line(), reviewWorker, this::notation, GameReviewDialog.Mode.STUDY, blackBelow,
                getString(R.string.white), getString(R.string.black), seek, resume, onClose);
    }

    private void confirmContinuation(AnalysisLine cursor) {
        var continuation = cursor.resumeMatch(analysisEnabled);
        var automatic = new CheckBox(this);
        automatic.setText(R.string.match_automatic_ai);
        automatic.setChecked(true);
        new AlertDialog.Builder(this).setTitle(R.string.manual_continue_game)
                .setMessage(getString(R.string.manual_continue_confirm, cursor.ply())).setView(automatic)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.match_confirm_continue, (dialog, which) -> {
                    if (new ChessProgressStore(this).matches().freeMatchSlot() == 0) {
                        android.widget.Toast.makeText(this, R.string.manual_no_free_slot, android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    setResult(RESULT_OK, new Intent().putExtra(CONTINUATION, continuation.checkpoint())
                            .putExtra(AI_AUTOMATIC, automatic.isChecked()).putExtra(ORIENTATION, blackBelow));
                    finish();
                }).show();
    }

    private void tap(int square) {
        if (study.result() != ChessMatch.Result.IN_PROGRESS) return;
        var guided = analysisEnabled ? analysis.moveForTap(study.position(), selected, square) : null;
        if (guided != null) {
            select(study.play(guided));
            return;
        }
        var targets = options.stream().filter(move -> move.to() == square).collect(Collectors.toList());
        if (targets.size() == 1) {
            select(study.play(targets.get(0)));
        } else if (!targets.isEmpty()) {
            var snapshot = study;
            var labels = targets.stream().map(move -> getText(BoardView.kindLabel(move.promotion()))).toArray(CharSequence[]::new);
            new AlertDialog.Builder(this).setTitle(R.string.promote).setItems(labels, (dialog, choice) -> {
                if (study == snapshot) select(study.play(targets.get(choice)));
            }).show();
        } else {
            var piece = study.position().pieceAt(square);
            selected = selected != square && piece != null && piece.side() == study.position().turn() ? square : -1;
            options = study.position().legalMovesFrom(selected);
            render();
        }
    }

    private void render() {
        var lower = blackBelow ? Side.BLACK : Side.WHITE;
        boolean playable = study.result() == ChessMatch.Result.IN_PROGRESS;
        showSide(top, lower.other(), playable);
        showSide(bottom, lower, playable);
        String label = getString(R.string.manual_position, study.ply(), study.line().ply());
        int outcome = switch (study.result()) {
            case IN_PROGRESS -> 0;
            case WHITE_WINS, BLACK_WINS -> R.string.manual_checkmate;
            case STALEMATE -> R.string.match_draw_stalemate;
            case MATERIAL_DRAW -> R.string.match_draw_material;
            case REPETITION_DRAW -> R.string.match_draw_repetition;
            case MOVE_COUNT_DRAW -> R.string.match_draw_move_count;
            case AGREED_DRAW -> R.string.match_agreed_draw;
        };
        status.setText(outcome == 0 ? label : label + "\n" + getString(outcome));
        int visibility = analysisEnabled ? View.VISIBLE : View.GONE;
        analysis.setVisibility(visibility);
        analysis.bulb().setVisibility(visibility);
        analysis.evaluationBar().setVisibility(visibility);
        if (analysisEnabled) {
            analysis.evaluationBar().setBlackBelow(blackBelow);
            analysis.show(study);
        }
        board.show(study.position(), blackBelow, selected, options, study.lastMove(), analysis.cue(), playable,
                study.position().turn() == lower ? bottom.getId() : top.getId());
        history.show(study.line(), study.ply());
    }

    private void showSide(TextView label, Side side, boolean playable) {
        boolean active = playable && study.position().turn() == side;
        var color = getString(BoardView.sideLabel(side));
        label.setText(active ? getString(R.string.to_move, color) : color);
        label.setTextColor(active ? ACTIVE : PRIMARY);
    }

    private String notation(Move move) {
        return ChessActivity.notation(this, move);
    }

    @Override protected void onResume() { super.onResume(); render(); }
    @Override protected void onPause() { analysis.cancel(); super.onPause(); }
    @Override protected void onDestroy() {
        if (reviewDialog != null) reviewDialog.dismiss();
        reviewWorker.shutdownNow();
        analysis.close();
        super.onDestroy();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(LINE, study.line().checkpoint());
        state.putInt(PLY, study.ply());
        state.putBoolean(BLACK_BELOW, blackBelow);
        state.putBoolean(ANALYSIS_ENABLED, analysisEnabled);
        super.onSaveInstanceState(state);
    }
}
