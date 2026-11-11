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
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.stream.Collectors;
import ro.atf.chess.AnalysisLine;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** A local study board whose line belongs to the activity, independently of the saved match. */
public final class AnalysisActivity extends Activity {
    private static final String LINE = "line";
    private static final String PLY = "ply";
    private static final String BLACK_BELOW = "blackBelow";
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int ACTIVE = 0xffb8d88a;
    private AnalysisLine entry;
    private AnalysisLine study;
    private boolean blackBelow;
    private int selected = -1;
    private List<Move> options = List.of();
    private BoardView board;
    private TextView top;
    private TextView bottom;
    private TextView status;
    private MatchHistoryView history;
    private MatchAnalysisView analysis;

    static void open(Activity owner, ChessMatch match, int ply, boolean blackBelow) {
        owner.startActivity(new Intent(owner, AnalysisActivity.class).putExtra(LINE, match.checkpoint())
                .putExtra(PLY, ply).putExtra(BLACK_BELOW, blackBelow));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        var intent = getIntent();
        entry = new AnalysisLine(ChessMatch.restore(intent.getStringExtra(LINE)), intent.getIntExtra(PLY, 0));
        study = state == null ? entry : new AnalysisLine(ChessMatch.restore(state.getString(LINE)), state.getInt(PLY));
        blackBelow = state == null ? intent.getBooleanExtra(BLACK_BELOW, false) : state.getBoolean(BLACK_BELOW);
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
        var back = button(R.drawable.match_chevron, R.string.manual_close, this::finish);
        back.setScaleX(-1);
        addHeaderAction(header, back);
        var title = text(19);
        title.setText(R.string.manual_analysis);
        title.setGravity(Gravity.CENTER_VERTICAL);
        BoardCaption.reserve(title, 2);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        addHeaderAction(header, button(android.R.drawable.ic_menu_rotate, R.string.flip, () -> {
            blackBelow = !blackBelow;
            render();
        }));
        var reset = button(android.R.drawable.ic_menu_revert, R.string.manual_reset, () -> select(entry));
        reset.setId(R.id.manual_reset_line);
        addHeaderAction(header, reset);
        screen.addView(header);
        top = text(15);
        bottom = text(15);
        top.setId(View.generateViewId());
        bottom.setId(View.generateViewId());
        board = new BoardView(this, this::tap);
        analysis = new MatchAnalysisView(this, this::notation, this::render);
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

    private ImageButton button(int icon, int label, Runnable action) {
        var button = new ImageButton(this, null, android.R.attr.borderlessButtonStyle);
        button.setImageResource(icon);
        button.setImageTintList(ColorStateList.valueOf(PRIMARY));
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setContentDescription(getString(label));
        button.setOnClickListener(view -> action.run());
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

    private void tap(int square) {
        if (study.result() != ChessMatch.Result.IN_PROGRESS) return;
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
        analysis.evaluationBar().setBlackBelow(blackBelow);
        analysis.show(study);
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
    @Override protected void onDestroy() { analysis.close(); super.onDestroy(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString(LINE, study.line().checkpoint());
        state.putInt(PLY, study.ply());
        state.putBoolean(BLACK_BELOW, blackBelow);
        super.onSaveInstanceState(state);
    }
}
