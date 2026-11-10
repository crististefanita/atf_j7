package ro.atf.chess.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import ro.atf.chess.BoardPanelLayout;

final class BoardPanel extends ViewGroup {
    private final View topLabel;
    private final BoardView board;
    private final View bottomLabel;
    private BoardPanelLayout placement;

    BoardPanel(Context context, View topLabel, BoardView board, View bottomLabel) {
        super(context);
        this.topLabel = topLabel;
        this.board = board;
        this.bottomLabel = bottomLabel;
        addView(topLabel);
        addView(board);
        addView(bottomLabel);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        int labelWidth = MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST);
        int labelHeight = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        topLabel.measure(labelWidth, labelHeight);
        bottomLabel.measure(labelWidth, labelHeight);
        placement = BoardPanelLayout.fit(width, height, topLabel.getMeasuredHeight(), bottomLabel.getMeasuredHeight());
        int boardSize = MeasureSpec.makeMeasureSpec(placement.side(), MeasureSpec.EXACTLY);
        board.measure(boardSize, boardSize);
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = placement.left();
        int boardTop = placement.top() + topLabel.getMeasuredHeight();
        placeLabel(topLabel, placement.top());
        board.layout(x, boardTop, x + placement.side(), boardTop + placement.side());
        placeLabel(bottomLabel, boardTop + placement.side());
    }

    private void placeLabel(View label, int top) {
        int width = label.getMeasuredWidth();
        int left = Math.min(placement.left(), getWidth() - width);
        label.layout(left, top, left + width, top + label.getMeasuredHeight());
    }
}
