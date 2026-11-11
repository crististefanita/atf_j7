package ro.atf.chess.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.OpeningCatalog;
import ro.atf.chess.OpeningCatalog.Opening;
import ro.atf.chess.OpeningCatalog.Text;
import ro.atf.chess.OpeningPractice;
import ro.atf.chess.OpeningPractice.Phase;
import ro.atf.chess.QuestHint;

public final class OpeningActivity extends Activity {
    private static final int BACKGROUND = 0xff262522;
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int ACCENT = 0xffb8d88a;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable bookReply = this::playReply;
    private List<Opening> catalog;
    private OpeningPractice practice;
    private BoardView board;
    private TextView top;
    private TextView bottom;
    private TextView progress;
    private TextView message;
    private Button hintButton;
    private Button aiButton;
    private MatchAnalysisView analysis;
    private boolean resumed;
    private boolean aiEnabled;
    private boolean offLine;
    private QuestHint theoryHint = QuestHint.NONE;
    private int selected = -1;
    private int reviewPly = -1;
    private List<Move> targets = List.of();

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        catalog = OpeningCatalog.load();
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::back);
        if (state != null && state.containsKey("opening")) {
            String id = state.getString("opening");
            int ply = state.getInt("ply");
            for (var opening : catalog) if (opening.id().equals(id) && ply >= 0 && ply <= opening.steps().size()) {
                begin(new OpeningPractice(opening, state.getBoolean("black") ? Side.BLACK : Side.WHITE, ply),
                        state.getBoolean("analysis"));
                int savedReview = state.getInt("review", -1);
                if (savedReview >= 0 && savedReview <= ply) selectReview(savedReview);
                return;
            }
        }
        showCatalog();
    }

    private LinearLayout screen() {
        var root = column();
        root.setBackgroundColor(BACKGROUND);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        if (Build.VERSION.SDK_INT >= 35) root.setOnApplyWindowInsetsListener((view, insets) -> {
            var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
        return root;
    }

    private void showCatalog() {
        closePractice();
        practice = null;
        var root = screen();
        root.addView(header(getString(R.string.openings_title)));
        var scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        var cards = column();
        cards.setId(R.id.opening_catalog);
        cards.setPadding(dp(18), dp(4), dp(18), dp(14));
        for (var opening : catalog) cards.addView(card(opening));
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private View card(Opening opening) {
        var card = column();
        card.setPadding(dp(16), dp(12), dp(16), dp(12));
        var background = new GradientDrawable();
        background.setColor(0xff32312d);
        background.setCornerRadius(dp(12));
        card.setBackground(background);
        var ripple = new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x227fbe6b), null, background.getConstantState().newDrawable());
        card.setForeground(ripple);
        var meta = text(opening.eco() + "  ·  " + localized(opening.variation()), 12, ACCENT);
        var title = text(localized(opening.title()), 19, PRIMARY);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        card.addView(meta);
        card.addView(title);
        var params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        card.setLayoutParams(params);
        card.setFocusable(true);
        card.setContentDescription(localized(opening.title()) + ". " + localized(opening.variation()) + ". " + opening.eco());
        meta.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.setOnClickListener(view -> overview(opening));
        return card;
    }

    private void overview(Opening opening) {
        new AlertDialog.Builder(this).setTitle(localized(opening.title()))
                .setMessage(localized(opening.variation()) + "\n\n" + localized(opening.plan()))
                .setPositiveButton(R.string.opening_train_white, (dialog, which) -> begin(new OpeningPractice(opening, Side.WHITE, 0), false))
                .setNegativeButton(R.string.opening_train_black, (dialog, which) -> begin(new OpeningPractice(opening, Side.BLACK, 0), false))
                .setNeutralButton(R.string.opening_source, (dialog, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(opening.source().toString())));
                    } catch (ActivityNotFoundException unavailable) {
                        Toast.makeText(this, R.string.opening_source_unavailable, Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void begin(OpeningPractice next, boolean enableAi) {
        closePractice();
        practice = next;
        aiEnabled = enableAi;
        reviewPly = -1;
        clearSelection();
        theoryHint = QuestHint.NONE;
        offLine = false;
        var root = screen();
        root.addView(header(localized(next.opening().title())));
        progress = text("", 13, ACCENT);
        progress.setId(R.id.opening_progress);
        progress.setGravity(Gravity.CENTER);
        progress.setPadding(0, dp(3), 0, dp(4));
        progress.setOnClickListener(view -> review());
        root.addView(progress);

        var workspace = new LinearLayout(this);
        boolean landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        workspace.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.addView(workspace, new LinearLayout.LayoutParams(-1, 0, 1));
        top = seat();
        bottom = seat();
        board = new BoardView(this, this::tap);
        board.setId(R.id.opening_board);
        analysis = new MatchAnalysisView(this, this::notation, () -> {
            theoryHint = QuestHint.NONE;
            offLine = false;
            render();
        });
        analysis.evaluationBar().setBlackBelow(next.learner() == Side.BLACK);
        var panel = new BoardPanel(this, top, board, bottom, analysis.evaluationBar());
        workspace.addView(panel, landscape ? new LinearLayout.LayoutParams(0, -1, 1)
                : new LinearLayout.LayoutParams(-1, 0, 1));
        var controls = controls();
        if (landscape) {
            var scroll = new ScrollView(this);
            scroll.addView(controls);
            workspace.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            workspace.addView(controls, new LinearLayout.LayoutParams(-1, -2));
        }
        setContentView(root);
        render();
        scheduleReply();
    }

    private LinearLayout controls() {
        var controls = column();
        controls.setPadding(dp(14), dp(4), dp(14), dp(6));
        message = text("", 15, PRIMARY);
        message.setId(R.id.opening_message);
        message.setGravity(Gravity.CENTER);
        message.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        BoardCaption.reserve(message, 3);
        message.setOnClickListener(view -> new AlertDialog.Builder(this).setMessage(message.getText())
                .setPositiveButton(android.R.string.ok, null).show());
        controls.addView(message, new LinearLayout.LayoutParams(-1, -2));
        controls.addView(analysis, new LinearLayout.LayoutParams(-1, -2));
        var actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        hintButton = button(R.string.opening_hint);
        hintButton.setId(R.id.opening_theory_hint);
        hintButton.setOnClickListener(view -> theory());
        aiButton = button(R.string.opening_ai);
        aiButton.setId(R.id.opening_toggle_ai);
        aiButton.setOnClickListener(view -> {
            aiEnabled = !aiEnabled;
            if (!aiEnabled) analysis.cancel();
            render();
        });
        actions.addView(hintButton, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(aiButton, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(analysis.bulb());
        controls.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        return controls;
    }

    private void theory() {
        if (reviewPly >= 0) {
            selectReview(-1);
            return;
        }
        if (practice.phase() == Phase.COMPLETE) {
            begin(new OpeningPractice(practice.opening(), practice.learner(), 0), aiEnabled);
            return;
        }
        if (practice.phase() != Phase.LEARNER) return;
        theoryHint = theoryHint == QuestHint.SOLUTION ? QuestHint.NONE : theoryHint.advance();
        offLine = false;
        analysis.hideSuggestion();
        render();
    }

    private void tap(int square) {
        if (reviewPly >= 0 || practice.phase() != Phase.LEARNER) return;
        if (selected >= 0 && selected != square) {
            var choices = targets.stream().filter(move -> move.to() == square).collect(java.util.stream.Collectors.toList());
            if (choices.size() == 1) {
                tryMove(choices.get(0));
                return;
            }
            if (choices.size() > 1) {
                var labels = choices.stream().map(move -> getText(BoardView.kindLabel(move.promotion()))).toArray(CharSequence[]::new);
                new AlertDialog.Builder(this).setItems(labels, (dialog, which) -> tryMove(choices.get(which))).show();
                return;
            }
        }
        var piece = practice.position().pieceAt(square);
        if (selected == square || piece == null || piece.side() != practice.learner()) clearSelection();
        else {
            selected = square;
            targets = practice.position().legalMovesFrom(square);
        }
        render();
    }

    private void tryMove(Move move) {
        var attempt = practice.check(move);
        clearSelection();
        if (attempt == OpeningPractice.Attempt.ON_LINE) {
            practice = practice.play(move);
            theoryHint = QuestHint.NONE;
            offLine = false;
        } else if (attempt == OpeningPractice.Attempt.OFF_LINE) {
            offLine = true;
        }
        render();
        scheduleReply();
    }

    private void playReply() {
        if (!resumed || practice == null || practice.phase() != Phase.REPLY) return;
        practice = practice.reply();
        render();
    }

    private void scheduleReply() {
        main.removeCallbacks(bookReply);
        if (resumed && practice.phase() == Phase.REPLY) main.postDelayed(bookReply, 450);
    }

    private void render() {
        var phase = practice.phase();
        boolean reviewing = reviewPly >= 0;
        int ply = reviewing ? reviewPly : practice.ply();
        boolean playable = !reviewing && phase == Phase.LEARNER;
        progress.setText(getString(R.string.opening_progress_label, ply, practice.opening().steps().size(),
                getString(reviewing ? R.string.opening_reviewing : switch (phase) {
                    case LEARNER -> R.string.opening_your_turn;
                    case REPLY -> R.string.opening_book_reply;
                    case COMPLETE -> R.string.opening_complete;
                })));
        progress.setContentDescription(practice.ply() == 0 ? null
                : progress.getText() + ". " + getString(R.string.opening_review));
        var position = practice.opening().positionAt(ply);
        var learner = practice.learner();
        boolean markTurn = reviewing || phase != Phase.COMPLETE;
        renderSeat(top, learner.other(), markTurn && position.turn() == learner.other());
        renderSeat(bottom, learner, markTurn && position.turn() == learner);
        message.setText(reviewing ? explanationAt(ply) : messageFor(phase));
        message.setTextColor(offLine ? 0xffffbc78 : PRIMARY);
        hintButton.setEnabled(reviewing || phase != Phase.REPLY);
        hintButton.setText(reviewing ? R.string.opening_resume : phase == Phase.COMPLETE ? R.string.opening_retry : switch (theoryHint) {
            case NONE -> R.string.opening_hint;
            case PIECE -> R.string.opening_show_move;
            case SOLUTION -> R.string.opening_hide_hint;
        });
        aiButton.setSelected(aiEnabled);
        aiButton.setText(aiEnabled ? R.string.opening_ai_enabled : R.string.opening_ai);
        analysis.setVisibility(aiEnabled ? View.VISIBLE : View.INVISIBLE);
        analysis.bulb().setVisibility(aiEnabled ? View.VISIBLE : View.INVISIBLE);
        analysis.evaluationBar().setVisibility(aiEnabled ? View.VISIBLE : View.INVISIBLE);
        if (aiEnabled && resumed) analysis.show(position);
        var cue = playable && theoryHint != QuestHint.NONE
                ? theoryHint.cue(practice.next().move()) : analysis.cue();
        var previous = ply == 0 ? null : practice.opening().steps().get(ply - 1).move();
        board.show(position, learner == Side.BLACK, selected, targets, previous, cue, playable,
                (position.turn() == learner ? bottom : top).getId());
    }

    private String messageFor(Phase phase) {
        if (offLine) return getString(R.string.opening_off_line);
        if (phase == Phase.COMPLETE) return localized(practice.opening().plan());
        if (phase == Phase.LEARNER && theoryHint == QuestHint.SOLUTION) return localized(practice.next().idea());
        if (phase == Phase.LEARNER && theoryHint == QuestHint.PIECE) {
            var piece = practice.position().pieceAt(practice.next().move().from());
            return getString(R.string.opening_hint_piece, getString(BoardView.kindLabel(piece.kind())));
        }
        return explanationAt(practice.ply());
    }

    private void review() {
        if (practice.ply() == 0 || practice.phase() == Phase.REPLY) return;
        String[] positions = new String[practice.ply() + 1];
        positions[0] = getString(R.string.opening_initial);
        for (int ply = 1; ply < positions.length; ply++) positions[ply] = explanationAt(ply);
        new AlertDialog.Builder(this).setTitle(R.string.opening_review)
                .setItems(positions, (dialog, ply) -> selectReview(ply))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void selectReview(int ply) {
        reviewPly = ply;
        clearSelection();
        theoryHint = QuestHint.NONE;
        offLine = false;
        analysis.hideSuggestion();
        render();
    }

    private String explanationAt(int ply) {
        return ply == 0 ? localized(practice.opening().plan())
                : moveLabel(ply - 1) + " · " + localized(practice.opening().steps().get(ply - 1).idea());
    }

    private void renderSeat(TextView label, Side side, boolean toMove) {
        String name = getString(BoardView.sideLabel(side));
        label.setText(toMove ? getString(R.string.to_move, name) : name);
        label.setTextColor(toMove ? ACCENT : MUTED);
    }

    private LinearLayout header(String title) {
        var header = new LinearLayout(this);
        header.setPadding(dp(10), 0, dp(14), 0);
        header.setGravity(Gravity.CENTER_VERTICAL);
        var back = button(R.string.opening_back);
        back.setText("‹");
        back.setTextSize(24);
        back.setContentDescription(getString(R.string.opening_back));
        back.setOnClickListener(view -> back());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), -2));
        var label = text(title, 21, PRIMARY);
        label.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) label.setAccessibilityHeading(true);
        header.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        return header;
    }

    private TextView seat() {
        var label = text("", 13, MUTED);
        label.setId(View.generateViewId());
        label.setPadding(dp(12), dp(3), dp(12), dp(3));
        BoardCaption.reserve(label, 1);
        return label;
    }

    private Button button(int label) {
        var button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setMinHeight(dp(48));
        return button;
    }

    private TextView text(String value, int size, int color) {
        var label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        return label;
    }

    private LinearLayout column() {
        var column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private void clearSelection() { selected = -1; targets = List.of(); }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
    private String localized(Text value) { return value.in(getResources().getConfiguration().getLocales().get(0)); }

    private String moveLabel(int ply) {
        String number = (ply / 2 + 1) + (ply % 2 == 0 ? ". " : "… ");
        return number + notation(practice.opening().steps().get(ply).move());
    }

    private String notation(Move move) {
        String from = BoardSquare.name(move.from());
        String to = BoardSquare.name(move.to());
        return move.promotion() == null ? getString(R.string.solution_move, from, to)
                : getString(R.string.solution_promotion, from, to, getString(BoardView.kindLabel(move.promotion())));
    }

    private void back() {
        if (reviewPly >= 0) selectReview(-1);
        else if (practice != null) showCatalog();
        else finish();
    }

    @Override public void onBackPressed() { back(); }

    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (practice != null) { render(); scheduleReply(); }
    }

    @Override protected void onPause() {
        resumed = false;
        main.removeCallbacks(bookReply);
        if (analysis != null) analysis.cancel();
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        if (practice == null) return;
        state.putString("opening", practice.opening().id());
        state.putBoolean("black", practice.learner() == Side.BLACK);
        state.putInt("ply", practice.ply());
        state.putInt("review", reviewPly);
        state.putBoolean("analysis", aiEnabled);
    }

    @Override protected void onDestroy() {
        closePractice();
        super.onDestroy();
    }

    private void closePractice() {
        main.removeCallbacks(bookReply);
        if (analysis != null) analysis.close();
        analysis = null;
    }
}
