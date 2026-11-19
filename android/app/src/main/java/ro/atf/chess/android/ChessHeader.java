package ro.atf.chess.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import ro.atf.chess.UiChrome;

/** Native adapter for Back, adjacent title and right-aligned contextual actions. */
final class ChessHeader extends LinearLayout {
    private final TextView title;
    private final UiChrome.Page page;
    private final ImageButton leading;

    ChessHeader(Context context, UiChrome.Page page, int backId, CharSequence text, Runnable back) {
        super(context);
        this.page = page;
        setGravity(Gravity.CENTER_VERTICAL);
        setBaselineAligned(false);
        setMinimumHeight(dp(UiChrome.TARGET));
        setPadding(dp(UiChrome.INSET), 0, dp(UiChrome.INSET), 0);
        leading = action(context, backId, R.drawable.match_chevron, backLabel(page.back()), view -> back.run());
        leading.setScaleX(-1);
        addView(leading, new LayoutParams(dp(UiChrome.TARGET), dp(UiChrome.TARGET)));
        title = new TextView(context);
        title.setId(R.id.screen_title);
        title.setText(text);
        title.setTextSize(UiChrome.TITLE);
        title.setTextColor(0xfff5f3ec);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        title.setMaxLines(2);
        title.setEllipsize(TextUtils.TruncateAt.END);
        if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        var label = new LayoutParams(0, -2, 1);
        label.setMarginStart(dp(UiChrome.GAP));
        label.setMarginEnd(dp(UiChrome.GAP));
        addView(title, label);
    }

    TextView title() { return title; }

    void setReviewing(boolean reviewing) {
        leading.setContentDescription(getContext().getString(backLabel(UiChrome.back(page, reviewing))));
    }

    void addAction(View action) { addView(action, new LayoutParams(dp(UiChrome.TARGET), dp(UiChrome.TARGET))); }

    static ImageButton action(Context context, int id, int icon, int description, View.OnClickListener listener) {
        var button = new ImageButton(context);
        button.setId(id);
        button.setImageResource(icon);
        button.setContentDescription(context.getString(description));
        button.setImageTintList(ColorStateList.valueOf(0xfff5f3ec));
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        int padding = Math.round(12 * context.getResources().getDisplayMetrics().density);
        button.setPadding(padding, padding, padding, padding);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x224f6a54), null, null));
        button.setOnClickListener(listener);
        return button;
    }

    private static int backLabel(UiChrome.Back destination) {
        return switch (destination) {
            case HOME -> R.string.chrome_back_home;
            case PUZZLES -> R.string.chrome_back_puzzles;
            case OPENINGS -> R.string.chrome_back_openings;
            case SOURCE -> R.string.chrome_back_source;
        };
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
