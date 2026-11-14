package ro.atf.chess.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import ro.atf.chess.BoardPanelLayout;

final class BoardPanel extends ViewGroup {
    private final View topLabel;
    private final BoardView board;
    private final View bottomLabel;
    private final EvaluationBar evaluation;
    private final int barWidth;
    private final int gutter;
    private BoardPanelLayout placement;

    BoardPanel(Context context, View topLabel, BoardView board, View bottomLabel, EvaluationBar evaluation) {
        super(context);
        this.topLabel = topLabel;
        this.board = board;
        this.bottomLabel = bottomLabel;
        this.evaluation = evaluation;
        float density = context.getResources().getDisplayMetrics().density;
        barWidth = Math.round(8 * density);
        gutter = evaluation == null ? 0 : barWidth + Math.round(5 * density);
        if (topLabel != null) addView(topLabel);
        addView(board);
        if (bottomLabel != null) addView(bottomLabel);
        if (evaluation != null) addView(evaluation);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        measureLabels(width);
        placement = BoardPanelLayout.fit(width, height, heightOf(topLabel), heightOf(bottomLabel),
                evaluationVisible() ? gutter : 0);
        int boardSize = MeasureSpec.makeMeasureSpec(placement.side(), MeasureSpec.EXACTLY);
        board.measure(boardSize, boardSize);
        if (evaluationVisible()) evaluation.measure(MeasureSpec.makeMeasureSpec(Math.min(width, barWidth), MeasureSpec.EXACTLY), boardSize);
        setMeasuredDimension(width, height);
    }

    int measureLabels(int width) {
        int labelWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
        int labelHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        if (topLabel != null) topLabel.measure(labelWidth, labelHeight);
        if (bottomLabel != null) bottomLabel.measure(labelWidth, labelHeight);
        return heightOf(topLabel) + heightOf(bottomLabel);
    }

    private static int heightOf(View label) { return label == null ? 0 : label.getMeasuredHeight(); }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = placement.left();
        int boardTop = placement.top() + heightOf(topLabel);
        placeLabel(topLabel, placement.top());
        board.layout(x, boardTop, x + placement.side(), boardTop + placement.side());
        if (evaluationVisible()) {
            int barLeft = Math.max(0, x - gutter);
            evaluation.layout(barLeft, boardTop, barLeft + evaluation.getMeasuredWidth(), boardTop + placement.side());
        }
        placeLabel(bottomLabel, boardTop + placement.side());
    }

    private boolean evaluationVisible() { return evaluation != null && evaluation.getVisibility() != View.GONE; }

    private void placeLabel(View label, int top) {
        if (label == null) return;
        int width = label.getMeasuredWidth();
        int left = Math.min(placement.left(), getWidth() - width);
        label.layout(left, top, left + width, top + label.getMeasuredHeight());
    }
}
