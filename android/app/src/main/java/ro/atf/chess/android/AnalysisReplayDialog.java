package ro.atf.chess.android;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.List;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.QuestHint;

/** The same fullscreen board, branch controls and scrollable explanation for both replay flows. */
final class AnalysisReplayDialog {
    private static final int SURFACE = 0xff262522;
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int ACCENT = 0xff57e0b4;

    enum Mode {
        CALCULATED(R.string.hint_preview_title, R.id.hint_preview_board, R.id.hint_preview_step, R.id.hint_preview_delta,
                R.id.hint_preview_reason, R.id.hint_preview_previous, R.id.hint_preview_next),
        THEORY(R.string.hint_theory_title, R.id.hint_preview_board, R.id.hint_preview_step, R.id.hint_preview_delta,
                R.id.hint_preview_reason, R.id.hint_preview_previous, R.id.hint_preview_next),
        REVIEW(R.string.game_review_why, R.id.game_review_board, R.id.game_review_line_progress, R.id.game_review_selection,
                R.id.game_review_reason, R.id.game_review_line_previous, R.id.game_review_line_next);

        final int title;
        final int board;
        final int step;
        final int caption;
        final int reason;
        final int back;
        final int next;

        Mode(int title, int board, int step, int caption, int reason, int back, int next) {
            this.title = title;
            this.board = board;
            this.step = step;
            this.caption = caption;
            this.reason = reason;
            this.back = back;
            this.next = next;
        }
    }

    record Frame(ChessPosition position, Move lastMove, QuestHint.Cue cue, String step,
                 boolean previous, boolean next, String caption, String reason) { }

    private final Context context;
    private final boolean blackBelow;
    private final Dialog dialog;
    private final BoardView board;
    private final TextView step;
    private final TextView caption;
    private final TextView reason;
    private final TextView summary;
    private final ImageButton previous;
    private final ImageButton next;
    private final LinearLayout choices;

    AnalysisReplayDialog(Context context, Mode mode, boolean blackBelow, boolean showChoices, View action, Runnable onReturn) {
        this(context, mode, blackBelow, showChoices, action, onReturn, 0);
    }

    AnalysisReplayDialog(Context context, Mode mode, boolean blackBelow, boolean showChoices, View action, Runnable onReturn, int sourceBoardSide) {
        this.context = context;
        this.blackBelow = blackBelow;
        var screen = column(context);
        dialog = fullScreen(context, screen);

        var header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        var close = arrow(context, R.id.hint_preview_return, R.string.hint_preview_return, false);
        close.setOnClickListener(ignored -> {
            onReturn.run();
            dialog.dismiss();
        });
        header.addView(close, touchTarget(context));
        var title = label(View.NO_ID, 19, PRIMARY);
        title.setText(mode.title);
        title.setSingleLine();
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) header.addView(action, touchTarget(context));
        screen.addView(header, new LinearLayout.LayoutParams(-1, -2));
        dialog.setOnCancelListener(ignored -> onReturn.run());

        board = new BoardView(context, ignored -> { });
        board.setId(mode.board);
        board.setContentDescription(context.getString(R.string.game_review_board));
        var controls = column(context);
        choices = new LinearLayout(context);
        choices.setBaselineAligned(false);
        int margin = dp(context, 8);
        choices.setPadding(margin, dp(context, 4), margin, 0);
        if (showChoices) controls.addView(choices, new LinearLayout.LayoutParams(-1, -2));
        var navigation = new LinearLayout(context);
        navigation.setGravity(Gravity.CENTER_VERTICAL);
        previous = arrow(context, mode.back,
                mode == Mode.REVIEW ? R.string.game_review_line_previous : R.string.match_history_previous, false);
        next = arrow(context, mode.next,
                mode == Mode.REVIEW ? R.string.game_review_line_next : R.string.match_history_next, true);
        step = label(mode.step, 14, MUTED);
        step.setGravity(Gravity.CENTER);
        BoardCaption.reserve(step, 2);
        step.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        navigation.addView(previous, touchTarget(context));
        navigation.addView(step, new LinearLayout.LayoutParams(0, -2, 1));
        navigation.addView(next, touchTarget(context));
        controls.addView(navigation, new LinearLayout.LayoutParams(-1, -2));

        var details = column(context);
        details.setPadding(dp(context, 16), dp(context, 6), dp(context, 16), dp(context, 16));
        summary = label(R.id.hint_compare_summary, 13, MUTED);
        summary.setVisibility(View.GONE);
        details.addView(summary, new LinearLayout.LayoutParams(-1, -2));
        caption = label(mode.caption, 16, PRIMARY);
        caption.setTypeface(null, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) caption.setAccessibilityHeading(true);
        details.addView(caption, new LinearLayout.LayoutParams(-1, -2));
        reason = label(mode.reason, 15, MUTED);
        reason.setPadding(0, dp(context, 6), 0, 0);
        reason.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        details.addView(reason, new LinearLayout.LayoutParams(-1, -2));
        var scroll = new ScrollView(context);
        scroll.addView(details);
        screen.addView(new GameReviewWorkspace(context, board, controls, scroll, sourceBoardSide), new LinearLayout.LayoutParams(-1, 0, 1));
    }

    Button addBranch(int id, String title, String description, Runnable select) {
        var button = branchButton(context);
        button.setId(id);
        button.setText(title);
        if (description != null) button.setContentDescription(description);
        button.setOnClickListener(ignored -> select.run());
        var placement = new LinearLayout.LayoutParams(0, dp(context, 52), 1);
        if (choices.getChildCount() > 0) placement.setMarginStart(dp(context, 6));
        choices.addView(button, placement);
        return button;
    }

    void setNavigation(Runnable back, Runnable forward) {
        previous.setOnClickListener(ignored -> back.run());
        next.setOnClickListener(ignored -> forward.run());
    }

    void setSummary(String text) {
        summary.setText(text);
        summary.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    void setCaptionDescription(String description) { caption.setContentDescription(description); }

    void setReasonAction(String description, Runnable action) {
        reason.setContentDescription(description);
        reason.setOnClickListener(action == null ? null : ignored -> action.run());
        reason.setClickable(action != null);
        reason.setFocusable(action != null);
        reason.setMinHeight(action == null ? 0 : dp(context, 48));
    }

    void render(Frame frame) {
        board.show(frame.position(), blackBelow, -1, List.of(), frame.lastMove(), frame.cue(), false, step.getId());
        step.setText(frame.step());
        previous.setEnabled(frame.previous());
        next.setEnabled(frame.next());
        caption.setText(frame.caption());
        reason.setText(frame.reason());
        setReasonAction(null, null);
    }

    Dialog show() {
        showFullScreen(dialog);
        return dialog;
    }

    static Dialog fullScreen(Context context, View screen) {
        var dialog = new Dialog(context, android.R.style.Theme_Material_NoActionBar);
        screen.setBackgroundColor(SURFACE);
        if (Build.VERSION.SDK_INT >= 35) {
            screen.setOnApplyWindowInsetsListener((view, insets) -> {
                var bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
        dialog.setContentView(screen);
        return dialog;
    }

    static void showFullScreen(Dialog dialog) {
        dialog.show();
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    static Button branchButton(Context context) {
        var button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        int[] disabled = {-android.R.attr.state_enabled};
        int[] selected = {android.R.attr.state_selected};
        int[] normal = {};
        var surface = new StateListDrawable();
        surface.addState(selected, branchSurface(context, 0xff29473e, ACCENT));
        surface.addState(normal, branchSurface(context, 0xff34372e, 0xff525647));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3257e0b4), surface, null));
        button.setTextColor(new ColorStateList(new int[][] {disabled, selected, normal}, new int[] {0xff77766d, 0xff9cf3d8, PRIMARY}));
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMaxLines(2);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 4), 0, dp(context, 4), 0);
        return button;
    }

    private static GradientDrawable branchSurface(Context context, int fill, int border) {
        var surface = new GradientDrawable();
        surface.setColor(fill);
        surface.setCornerRadius(dp(context, 10));
        surface.setStroke(dp(context, 1), border);
        return surface;
    }

    private static ImageButton arrow(Context context, int id, int label, boolean forward) {
        var button = new ImageButton(context, null, android.R.attr.borderlessButtonStyle);
        button.setId(id);
        button.setImageResource(R.drawable.match_chevron);
        if (!forward) button.setScaleX(-1);
        button.setContentDescription(context.getString(label));
        button.setImageTintList(new ColorStateList(new int[][] {{-android.R.attr.state_enabled}, {}}, new int[] {0xff77766d, PRIMARY}));
        return button;
    }

    private TextView label(int id, int size, int color) {
        var view = new TextView(context);
        view.setId(id);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private static LinearLayout column(Context context) {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout.LayoutParams touchTarget(Context context) {
        int size = dp(context, 48);
        return new LinearLayout.LayoutParams(size, size);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
