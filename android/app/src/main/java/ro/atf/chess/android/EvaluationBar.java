package ro.atf.chess.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class EvaluationBar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private double whiteShare = Double.NaN;
    private boolean blackBelow;

    EvaluationBar(Context context) {
        super(context);
        setId(R.id.match_evaluation_bar);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void setBlackBelow(boolean blackBelow) {
        if (this.blackBelow == blackBelow) return;
        this.blackBelow = blackBelow;
        invalidate();
    }

    boolean blackBelow() { return blackBelow; }

    void setWhiteShare(double share) {
        if (Double.compare(whiteShare, share) == 0) return;
        whiteShare = share;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width < 3 || height < 3) return;
        paint.setColor(0xff77756c);
        canvas.drawRect(0, 0, width, height, paint);
        paint.setColor(0xff48463f);
        canvas.drawRect(1, 1, width - 1, height - 1, paint);
        if (Double.isNaN(whiteShare)) return;
        float whiteHeight = (float) ((height - 2) * whiteShare);
        paint.setColor(0xff262522);
        canvas.drawRect(1, 1, width - 1, height - 1, paint);
        paint.setColor(0xffedeadf);
        if (blackBelow) canvas.drawRect(1, 1, width - 1, 1 + whiteHeight, paint);
        else canvas.drawRect(1, height - 1 - whiteHeight, width - 1, height - 1, paint);
    }
}
