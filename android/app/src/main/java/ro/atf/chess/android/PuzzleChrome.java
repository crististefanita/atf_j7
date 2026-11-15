package ro.atf.chess.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import ro.atf.chess.PuzzleChromeLayout;

/** The controls shared by the six playable puzzle chapters. */
final class PuzzleChrome extends FrameLayout {
    private static final int BACKGROUND = 0xff1c211d;
    private static final int TEXT = 0xffeff2ec;
    private static final int MUTED = 0xffb7c5b4;
    private static final int ACCENT = 0xffd4eaa7;
    private static final int FLAME = 0xffffbc78;
    private static final int SURFACE = 0xff2b352c;
    private static final int STROKE = 0xff3c4a3d;
    private final LinearLayout content;
    private final FrameLayout options;
    private final Button flip;
    private final ImageButton menu;
    private final TextView rank;
    private final TextView points;
    private final TextView streak;
    private final int optionHeight;
    private View navigation;
    private View details;

    PuzzleChrome(LinearLayout content, int title, Runnable turnBoard, Runnable goHome) {
        super(content.getContext());
        this.content = content;
        setBackgroundColor(BACKGROUND);
        content.setBackgroundColor(BACKGROUND);
        addView(content, new LayoutParams(-1, -1));

        var header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(8), 0, dp(14), 0);
        menu = icon(R.id.puzzle_options, R.drawable.puzzle_menu, R.string.puzzle_options, false, false);
        menu.setOnClickListener(view -> showOptions());
        header.addView(menu, new LinearLayout.LayoutParams(dp(48), dp(48)));
        var label = text(19, TEXT);
        label.setId(R.id.puzzle_chapter);
        label.setText(title);
        label.setGravity(Gravity.CENTER_VERTICAL);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        label.setMaxLines(PuzzleChromeLayout.TITLE_LINES);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setPadding(dp(4), 0, dp(6), 0);
        header.addView(label, new LinearLayout.LayoutParams(0, -1, 1));
        rank = text(12, MUTED);
        rank.setId(R.id.puzzle_rank);
        rank.setSingleLine(true);
        header.addView(rank);
        int headerHeight = PuzzleChromeLayout.fit(0, dp(48), label.getLineHeight(), 0, 0, 0).headerHeight();
        content.addView(header, new LinearLayout.LayoutParams(-1, headerHeight));

        points = text(13, TEXT);
        points.setId(R.id.puzzle_points);
        points.setTypeface(points.getTypeface(), android.graphics.Typeface.BOLD);
        streak = text(12, FLAME);
        streak.setId(R.id.puzzle_streak);
        streak.setCompoundDrawablePadding(dp(4));
        streak.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.quest_flame, 0, 0, 0);
        var hud = new RewardsRow();
        var hudPosition = new LinearLayout.LayoutParams(-1, -2);
        hudPosition.setMargins(dp(14), 0, dp(14), dp(6));
        content.addView(hud, hudPosition);

        options = new FrameLayout(getContext());
        options.setVisibility(GONE);
        options.setOnClickListener(view -> dismissOptions());
        options.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        var panel = new LinearLayout(getContext());
        panel.setId(R.id.puzzle_options_panel);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(surface(SURFACE, 13));
        panel.setPadding(dp(4), dp(4), dp(4), dp(4));
        panel.setClickable(true);
        flip = option(R.id.puzzle_flip_board, R.string.flip, turnBoard);
        panel.addView(flip);
        var home = option(R.id.puzzle_main_menu, R.string.puzzle_main_menu, goHome);
        panel.addView(home);
        optionHeight = Math.max(dp(48), Math.max(home.getLineHeight(), flip.getLineHeight()) + dp(12));
        var panelPosition = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.LEFT);
        panelPosition.setMargins(dp(14), 0, dp(14), dp(12));
        options.addView(panel, panelPosition);
        addView(options, new LayoutParams(-1, -1));
    }

    int menuHeight() { return 2 * optionHeight + dp(20); }

    void renderRank(int index, int total) {
        rank.setText(getContext().getString(R.string.puzzle_rank, index, total));
        rank.setContentDescription(getContext().getString(R.string.puzzle_rank_spoken, index, total));
    }

    void renderRewards(int total, int earned, int series, boolean showsSeries) {
        points.setText(earned == 0 ? getContext().getString(R.string.chapter_points, total)
                : getContext().getString(R.string.earned_points, earned, total));
        streak.setText(getContext().getString(R.string.puzzle_series, series));
        streak.setContentDescription(getContext().getString(R.string.reward_streak, series));
        streak.setVisibility(showsSeries ? VISIBLE : INVISIBLE);
    }

    ImageButton arrow(int id, int description, boolean backwards) {
        return icon(id, R.drawable.match_chevron, description, backwards, false);
    }

    void styleLamp(ImageButton lamp) {
        lamp.setBackground(buttonSurface(true));
        lamp.setImageTintList(iconColors(true));
    }

    void addActions(ImageButton previous, ImageButton lamp, ImageButton next, TextView label, View details) {
        this.details = details;
        var area = new LinearLayout(getContext());
        area.setOrientation(LinearLayout.VERTICAL);
        area.setGravity(Gravity.CENTER_HORIZONTAL);
        var row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER);
        row.addView(previous, new LinearLayout.LayoutParams(dp(52), dp(48)));
        var center = new LinearLayout.LayoutParams(dp(52), dp(48));
        center.setMargins(dp(10), 0, dp(10), 0);
        row.addView(lamp, center);
        row.addView(next, new LinearLayout.LayoutParams(dp(52), dp(48)));
        area.addView(row, new LinearLayout.LayoutParams(-1, dp(48)));
        label.setMaxWidth(dp(176));
        area.addView(label, new LinearLayout.LayoutParams(-2, -2));
        navigation = area;
        content.addView(area);
    }

    private void showOptions() {
        navigation.setVisibility(INVISIBLE);
        details.setVisibility(INVISIBLE);
        options.setVisibility(VISIBLE);
        content.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        flip.requestFocus();
        flip.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    boolean dismissOptions() {
        if (options.getVisibility() != VISIBLE) return false;
        options.setVisibility(GONE);
        navigation.setVisibility(VISIBLE);
        details.setVisibility(VISIBLE);
        content.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        if (isInTouchMode()) menu.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        else menu.requestFocus();
        return true;
    }

    private ImageButton icon(int id, int drawable, int description, boolean rotate, boolean accent) {
        var button = new ImageButton(getContext());
        button.setId(id);
        button.setContentDescription(getContext().getString(description));
        button.setBackground(buttonSurface(accent));
        button.setImageResource(drawable);
        button.setScaleType(ImageButton.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(12), dp(12), dp(12), dp(12));
        button.setImageTintList(iconColors(accent));
        if (rotate) button.setRotation(180);
        return button;
    }

    private Button option(int id, int label, Runnable action) {
        var button = new Button(getContext());
        button.setId(id);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setIncludeFontPadding(false);
        button.setTextColor(TEXT);
        button.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        button.setMinimumWidth(dp(204));
        button.setMinHeight(dp(48));
        button.setPadding(dp(12), dp(6), dp(12), dp(6));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(STROKE), null, surface(SURFACE, 8)));
        button.setOnClickListener(view -> {
            dismissOptions();
            action.run();
        });
        button.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private ColorStateList iconColors(boolean accent) {
        return new ColorStateList(new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{0xff829084, accent ? ACCENT : TEXT});
    }

    private InsetDrawable buttonSurface(boolean accent) {
        var ripple = new RippleDrawable(ColorStateList.valueOf(STROKE),
                surface(accent ? 0xff344432 : SURFACE, 10), null);
        return new InsetDrawable(ripple, dp(4), dp(4), dp(4), dp(4));
    }

    private GradientDrawable surface(int color, int corner) {
        var background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(corner));
        background.setStroke(dp(1), STROKE);
        return background;
    }

    private TextView text(int size, int color) {
        var view = new TextView(getContext());
        view.setIncludeFontPadding(false);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private int dp(int size) {
        return Math.round(getResources().getDisplayMetrics().density * size);
    }

    private final class RewardsRow extends LinearLayout {
        private final int reservedWidth;
        private boolean stacked;

        RewardsRow() {
            super(PuzzleChrome.this.getContext());
            setGravity(Gravity.CENTER_VERTICAL);
            setBackground(surface(0xff252e26, 9));
            setPadding(dp(10), dp(6), dp(10), dp(6));
            float pointsWidth = points.getPaint().measureText(getContext().getString(R.string.earned_points, 99, 99_999));
            float seriesWidth = streak.getPaint().measureText(getContext().getString(R.string.puzzle_series, 1_238));
            reservedWidth = (int) Math.ceil(pointsWidth + seriesWidth) + dp(24);
            addView(points, new LinearLayout.LayoutParams(0, -2, 1));
            addView(streak, new LinearLayout.LayoutParams(-2, -2));
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int width = Math.max(0, MeasureSpec.getSize(widthSpec) - getPaddingLeft() - getPaddingRight());
            int lineHeight = Math.max(points.getLineHeight(), streak.getLineHeight());
            var fit = PuzzleChromeLayout.fit(width, dp(48), 0, lineHeight, reservedWidth, 0);
            boolean needsStack = fit.scoreLines() > 1;
            if (needsStack != stacked) {
                stacked = needsStack;
                setOrientation(stacked ? VERTICAL : HORIZONTAL);
                points.setLayoutParams(stacked ? new LinearLayout.LayoutParams(-1, -2)
                        : new LinearLayout.LayoutParams(0, -2, 1));
                streak.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
                setGravity(stacked ? Gravity.START : Gravity.CENTER_VERTICAL);
            }
            int minimum = Math.max(dp(34), fit.scoreHeight() + getPaddingTop() + getPaddingBottom());
            if (getMinimumHeight() != minimum) setMinimumHeight(minimum);
            super.onMeasure(widthSpec, heightSpec);
        }
    }
}
