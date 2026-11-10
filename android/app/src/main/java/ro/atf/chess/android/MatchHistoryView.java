package ro.atf.chess.android;

import android.content.Context;
import android.view.Gravity;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.util.function.Function;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition.Move;

final class MatchHistoryView extends LinearLayout {
    private final LinearLayout moves;
    private final HorizontalScrollView scroll;
    private final Button previous;
    private final Button next;
    private final Button latest;
    private final Function<Move, String> notation;
    private final IntConsumer select;
    private int shown = -1;
    private int end;

    MatchHistoryView(Context context, Function<Move, String> notation, IntConsumer select) {
        super(context);
        this.notation = notation;
        this.select = select;
        setGravity(Gravity.CENTER_VERTICAL);
        setBaselineAligned(false);
        setBackgroundColor(0xff302e2a);
        previous = control(R.id.match_history_previous, "‹", R.string.match_history_previous, () -> select.accept(shown - 1));
        scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        moves = new LinearLayout(context);
        moves.setId(R.id.match_history_moves);
        moves.setBaselineAligned(false);
        scroll.addView(moves, new HorizontalScrollView.LayoutParams(-2, -2));
        addView(scroll, new LayoutParams(0, -2, 1));
        next = control(R.id.match_history_next, "›", R.string.match_history_next, () -> select.accept(shown + 1));
        latest = control(R.id.match_history_latest, "»", R.string.match_history_latest, () -> select.accept(end));
    }

    void show(ChessMatch match, int ply) {
        end = match.ply();
        while (moves.getChildCount() <= end) append(match, moves.getChildCount());
        previous.setEnabled(ply > 0);
        next.setEnabled(ply < end);
        latest.setEnabled(ply < end);
        if (shown == ply) return;
        if (shown >= 0) mark((Button) moves.getChildAt(shown), false);
        shown = ply;
        var selected = (Button) moves.getChildAt(ply);
        mark(selected, true);
        scroll.post(() -> {
            if (shown != ply) return;
            int left = selected.getLeft();
            if (left < scroll.getScrollX()) scroll.scrollTo(left, 0);
            else if (selected.getRight() > scroll.getScrollX() + scroll.getWidth()) {
                scroll.scrollTo(selected.getRight() - scroll.getWidth(), 0);
            }
        });
    }

    private void append(ChessMatch match, int ply) {
        var context = getContext();
        Button move = button();
        if (ply == 0) {
            move.setText(R.string.match_history_start);
        } else {
            String formatted = notation.apply(match.moveAt(ply));
            int number = (ply + 1) / 2;
            boolean white = ply % 2 != 0;
            move.setText(context.getString(white ? R.string.match_history_white_move : R.string.match_history_black_move, number, formatted));
            move.setContentDescription(context.getString(R.string.match_history_move_description, number,
                    context.getString(white ? R.string.white : R.string.black), formatted));
        }
        move.setOnClickListener(view -> select.accept(ply));
        mark(move, false);
        moves.addView(move, new LayoutParams(-2, dp(48)));
    }

    private static void mark(Button button, boolean selected) {
        button.setSelected(selected);
        button.setTextColor(selected ? 0xffb8d88a : 0xfff5f3ec);
        button.setBackgroundColor(selected ? 0xff414737 : 0x00000000);
    }

    private Button control(int id, String glyph, int description, Runnable action) {
        Button button = button();
        button.setId(id);
        button.setText(glyph);
        button.setTextSize(24);
        button.setContentDescription(getContext().getString(description));
        button.setOnClickListener(view -> action.run());
        addView(button, new LayoutParams(dp(48), dp(48)));
        return button;
    }

    private Button button() {
        var button = new Button(getContext(), null, android.R.attr.borderlessButtonStyle);
        button.setAllCaps(false);
        button.setMinWidth(dp(48));
        button.setMinHeight(dp(48));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setTextSize(13);
        button.setSingleLine(true);
        return button;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
