package ro.atf.chess.android;

import android.view.ViewGroup;
import android.widget.ScrollView;
import ro.atf.chess.BoardPanelLayout;
import ro.atf.chess.ReplayBoardLayout;

/** Keeps room for the square and leaves the remaining height for scrollable text. */
final class BoardDetails extends ViewGroup {
    private final BoardPanel panel;
    private final ScrollView details;
    private final int detailsMinimum;
    private final int sourceBoardSide;

    BoardDetails(BoardPanel panel, ScrollView details, int detailsMinimum) {
        this(panel, details, detailsMinimum, 0);
    }

    BoardDetails(BoardPanel panel, ScrollView details, int detailsMinimum, int sourceBoardSide) {
        super(panel.getContext());
        this.panel = panel;
        this.details = details;
        this.detailsMinimum = detailsMinimum;
        this.sourceBoardSide = sourceBoardSide;
        addView(panel);
        addView(details);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        int labels = panel.measureLabels(width);
        var replay = ReplayBoardLayout.fit(width, Math.max(0, height - labels), sourceBoardSide, 0, detailsMinimum);
        var layout = sourceBoardSide > 0 ? new BoardPanelLayout.WithDetails(replay.side() + Math.min(height, labels), replay.detailsHeight())
                : BoardPanelLayout.withDetails(width, height, labels, detailsMinimum);
        panel.measure(exact(width), exact(layout.boardHeight()));
        details.measure(exact(width), exact(layout.detailsHeight()));
        setMeasuredDimension(width, height);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int split = panel.getMeasuredHeight();
        panel.layout(0, 0, getWidth(), split);
        details.layout(0, split, getWidth(), getHeight());
    }

    private static int exact(int size) { return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY); }
}
