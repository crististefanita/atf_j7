package ro.atf.chess.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import ro.atf.chess.GameReviewLayout;

/** Places the measured board, fixed controls and scrolling report in the dialog's usable window. */
@SuppressLint("ViewConstructor") // Constructed in code with its three measured children.
final class GameReviewWorkspace extends ViewGroup {
    private final View board;
    private final View controls;
    private final View report;
    private GameReviewLayout placement;

    GameReviewWorkspace(Context context, View board, View controls, View report) {
        super(context);
        this.board = board;
        this.controls = controls;
        this.report = report;
        addView(board);
        addView(controls);
        addView(report);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        controls.measure(exact(width), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST));
        int gutter = dp(8);
        placement = GameReviewLayout.fit(width, height, controls.getMeasuredHeight(), dp(48) * 5,
                dp(132), dp(248), gutter);
        int side = placement.boardSide();
        controls.measure(exact(placement.panelWidth()), exact(controls.getMeasuredHeight()));
        board.measure(exact(side), exact(side));
        int reportWidth = placement.sideBySide() ? width - placement.panelWidth() - gutter : width;
        int reportHeight = placement.sideBySide() ? height : height - side - controls.getMeasuredHeight();
        report.measure(exact(reportWidth), exact(reportHeight));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int side = placement.boardSide();
        int panel = placement.panelWidth();
        int x = (panel - side) / 2;
        board.layout(x, 0, x + side, side);
        int controlsBottom = side + controls.getMeasuredHeight();
        controls.layout(0, side, panel, controlsBottom);
        int reportLeft = placement.sideBySide() ? panel + dp(8) : 0;
        int reportTop = placement.sideBySide() ? 0 : controlsBottom;
        report.layout(reportLeft, reportTop, getWidth(), getHeight());
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private static int exact(int size) { return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY); }
}
