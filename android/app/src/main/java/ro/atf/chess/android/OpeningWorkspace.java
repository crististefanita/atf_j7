package ro.atf.chess.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import ro.atf.chess.OpeningLessonLayout;

/** Anchors the board while optional analysis and long captions use the available details space. */
final class OpeningWorkspace extends ViewGroup {
    private final BoardPanel board;
    private final ScrollView scroll;
    private final LinearLayout controls;
    private final View caption;
    private final View actions;
    private final float density;
    private boolean sideBySide;

    OpeningWorkspace(Context context, BoardPanel board, LinearLayout controls, View caption, View actions) {
        super(context);
        this.board = board;
        this.controls = controls;
        this.caption = caption;
        this.actions = actions;
        density = getResources().getDisplayMetrics().density;
        scroll = new ScrollView(context);
        scroll.addView(controls, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(board);
        addView(scroll);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        int height = MeasureSpec.getSize(heightSpec);
        int windowHeight = getResources().getConfiguration().screenHeightDp;
        sideBySide = OpeningLessonLayout.sideBySide(Math.round(width / density), windowHeight);
        int panelWidth = sideBySide ? width / 2 : width;
        int controlWidth = sideBySide ? width - panelWidth : width;
        int boardLimit = OpeningLessonLayout.boardLimit(Math.round(panelWidth / density), sideBySide);
        int availableHeight = sideBySide ? height : height - Math.min(essentialControlsHeight(controlWidth), height / 3);
        int boardHeight = OpeningLessonLayout.panelHeight(panelWidth, board.leadingGutter(), board.measureLabels(panelWidth),
                Math.round(boardLimit * density), availableHeight);
        board.measure(exact(panelWidth), exact(boardHeight));
        scroll.measure(exact(controlWidth), exact(sideBySide ? height : height - boardHeight));
        setMeasuredDimension(width, height);
    }

    private int essentialControlsHeight(int width) {
        int contentWidth = Math.max(0, width - controls.getPaddingLeft() - controls.getPaddingRight());
        int natural = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        caption.measure(exact(contentWidth), natural);
        var actionLayout = (LinearLayout.LayoutParams) actions.getLayoutParams();
        return controls.getPaddingTop() + controls.getPaddingBottom() + caption.getMeasuredHeight()
                + actionLayout.height + actionLayout.topMargin;
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int panelWidth = board.getMeasuredWidth();
        int panelHeight = board.getMeasuredHeight();
        board.layout(0, 0, panelWidth, panelHeight);
        scroll.layout(sideBySide ? panelWidth : 0, sideBySide ? 0 : panelHeight, getWidth(), getHeight());
    }

    private static int exact(int size) { return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY); }
}
