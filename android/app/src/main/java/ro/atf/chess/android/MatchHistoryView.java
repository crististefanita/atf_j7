package ro.atf.chess.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import java.util.function.Function;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition.Move;

final class MatchHistoryView extends LinearLayout {
    private static final int MUTED = 0xffc0bfb4;
    private static final int SELECTED = 0xffb8d88a;
    private static final int CONTROL_SIZE = 48;
    private final LinearLayout moves;
    private final HorizontalScrollView scroll;
    private final ImageButton previous;
    private final ImageButton next;
    private final ImageButton latest;
    private final Function<Move, String> notation;
    private final IntConsumer select;
    private int shown = -1;
    private int end;

    MatchHistoryView(Context context, Function<Move, String> notation, IntConsumer select, View hint) {
        super(context);
        this.notation = notation;
        this.select = select;
        setGravity(Gravity.CENTER_VERTICAL);
        setBaselineAligned(false);
        setBackgroundColor(0xff2b2926);
        previous = control(R.id.match_history_previous, R.drawable.match_chevron, R.string.match_history_previous, () -> select.accept(shown - 1));
        previous.setScaleX(-1);
        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(dp(6));
        moves = new LinearLayout(context);
        moves.setId(R.id.match_history_moves);
        moves.setBaselineAligned(false);
        scroll.addView(moves, new HorizontalScrollView.LayoutParams(-2, -2));
        addView(scroll, new LayoutParams(0, -2, 1));
        next = control(R.id.match_history_next, R.drawable.match_chevron, R.string.match_history_next, () -> select.accept(shown + 1));
        latest = control(R.id.match_history_latest, R.drawable.match_latest, R.string.match_history_latest, () -> select.accept(end));
        if (hint != null) addView(hint, new LayoutParams(dp(CONTROL_SIZE), dp(CONTROL_SIZE)));
        moves.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> keepSelectionVisible());
        scroll.addOnLayoutChangeListener((view, l, t, r, b, oldL, oldT, oldR, oldB) -> keepSelectionVisible());
    }

    void show(ChessMatch match, int ply) {
        end = match.ply();
        if (moves.getChildCount() > end + 1) {
            moves.removeViews(end + 1, moves.getChildCount() - end - 1);
            if (shown > end) shown = -1;
        }
        while (moves.getChildCount() <= end) append(match, moves.getChildCount());
        previous.setEnabled(ply > 0);
        next.setEnabled(ply < end);
        latest.setEnabled(ply < end);
        if (shown == ply) return;
        if (shown >= 0) moves.getChildAt(shown).setSelected(false);
        shown = ply;
        moves.getChildAt(ply).setSelected(true);
        keepSelectionVisible();
    }

    private void keepSelectionVisible() {
        if (shown < 0 || scroll.getWidth() == 0) return;
        View selected = moves.getChildAt(shown);
        int left = selected.getLeft();
        int right = selected.getRight();
        if (left < scroll.getScrollX()) scroll.scrollTo(left, 0);
        else if (right > scroll.getScrollX() + scroll.getWidth()) scroll.scrollTo(right - scroll.getWidth(), 0);
    }

    private void append(ChessMatch match, int ply) {
        var context = getContext();
        Button move = moveToken();
        if (ply == 0) {
            move.setText(R.string.match_history_start);
        } else {
            var savedMove = match.moveAt(ply);
            int number = (ply + 1) / 2;
            boolean white = ply % 2 != 0;
            move.setText(context.getString(white ? R.string.match_history_white_move : R.string.match_history_black_move,
                    number, ChessActivity.compactNotation(savedMove)));
            move.setContentDescription(context.getString(R.string.match_history_move_description, number,
                    context.getString(white ? R.string.white : R.string.black), notation.apply(savedMove)));
        }
        move.setOnClickListener(view -> select.accept(ply));
        moves.addView(move, new LayoutParams(-2, dp(CONTROL_SIZE)));
    }

    private ImageButton control(int id, int icon, int description, Runnable action) {
        var button = new ImageButton(getContext(), null, android.R.attr.borderlessButtonStyle);
        button.setId(id);
        button.setImageResource(icon);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setImageTintList(new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}}, new int[]{0xff74736b, MUTED}));
        button.setContentDescription(getContext().getString(description));
        button.setOnClickListener(view -> action.run());
        addView(button, new LayoutParams(dp(CONTROL_SIZE), dp(CONTROL_SIZE)));
        return button;
    }

    private Button moveToken() {
        var button = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setTextSize(12);
        button.setSingleLine(true);
        int[][] states = {{android.R.attr.state_selected}, {}};
        button.setTextColor(new ColorStateList(states, new int[]{SELECTED, MUTED}));
        var chip = new GradientDrawable();
        chip.setColor(new ColorStateList(states, new int[]{0xff3c4333, 0x00000000}));
        chip.setCornerRadius(dp(8));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x28ffffff), new InsetDrawable(chip, dp(2), dp(6), dp(2), dp(6)), null));
        button.setPadding(dp(7), 0, dp(7), 0);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
