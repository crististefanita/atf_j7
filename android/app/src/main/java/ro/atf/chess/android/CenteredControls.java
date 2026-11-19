package ro.atf.chess.android;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import ro.atf.chess.UiChrome;

/** A bounded centered rail; owners retain their callbacks, history and selected line. */
final class CenteredControls extends ViewGroup {
    private final View content;

    CenteredControls(Context context, View content) {
        super(context);
        this.content = content;
        addView(content);
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int width = MeasureSpec.getSize(widthSpec);
        float density = getResources().getDisplayMetrics().density;
        int rail = UiChrome.navigation(width, density).width();
        content.measure(MeasureSpec.makeMeasureSpec(rail, MeasureSpec.EXACTLY), heightSpec);
        setMeasuredDimension(width, content.getMeasuredHeight());
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = UiChrome.navigation(getWidth(), getResources().getDisplayMetrics().density).left();
        content.layout(x, 0, x + content.getMeasuredWidth(), content.getMeasuredHeight());
    }
}
