package ro.atf.chess.android;

import android.view.ViewGroup;
import android.widget.ScrollView;
import ro.atf.chess.BoardPanelLayout;

/** Keeps room for the square and leaves the remaining height for scrollable text. */
final class BoardDetails extends ViewGroup {
    private final BoardPanel panel;
    private final ScrollView details;
    private final int detailsMinimum;

    BoardDetails(BoardPanel panel, ScrollView details, int detailsMinimum) {
        super(panel.getContext());
        this.panel = panel;
        this.details = details;
        this.detailsMinimum = detailsMinimum;
        addView(panel);
        addView(details);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        var layout = BoardPanelLayout.withDetails(width, height, panel.measureLabels(width), detailsMinimum);
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
