package ro.atf.chess.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.TextView;
import java.util.StringJoiner;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.MatchMaterial;

final class MatchCapturesView extends TextView {
    private MatchMaterial.Seat shown;
    private Side shownSide;

    MatchCapturesView(Context context, int id) {
        super(context);
        setId(id);
        int inset = Math.round(14 * getResources().getDisplayMetrics().density);
        setPadding(inset, 0, inset, 0);
        setTextSize(12);
        setTextColor(0xffc0bfb4);
        setIncludeFontPadding(false);
        BoardCaption.reserve(this, 1);
        // An icon-only line can report different font metrics from a text line.
        setHeight(getLineHeight());
        setVisibility(View.INVISIBLE);
    }

    void show(Side side, MatchMaterial.Seat seat) {
        if (side == shownSide && seat.equals(shown)) return;
        shown = seat;
        shownSide = side;
        var content = new SpannableStringBuilder();
        var spoken = new StringJoiner(", ");
        for (var capture : seat.captured()) {
            if (content.length() > 0) content.append(" ");
            int start = content.length();
            content.append("\ufffc");
            int size = Math.round(getTextSize() * .82f);
            var icon = new CapturedPiece(capture.kind(), side.other());
            icon.setBounds(0, 0, size, size);
            content.setSpan(new ImageSpan(icon, ImageSpan.ALIGN_BASELINE), start, start + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (capture.count() > 1) content.append(String.valueOf(capture.count()));
            spoken.add(getContext().getString(R.string.match_capture_count, capture.count(),
                    getContext().getString(BoardView.kindLabel(capture.kind()))));
        }
        var description = new StringJoiner(". ");
        if (spoken.length() > 0) description.add(getContext().getString(R.string.match_captures_description, spoken));
        if (seat.advantage() > 0) {
            if (content.length() > 0) content.append("  ");
            int start = content.length();
            content.append(getContext().getString(R.string.match_material_plus, seat.advantage()));
            content.setSpan(new ForegroundColorSpan(0xffb8d88a), start, content.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            description.add(getResources().getQuantityString(R.plurals.match_material_advantage, seat.advantage(), seat.advantage()));
        }
        setText(content, BufferType.SPANNABLE);
        setContentDescription(description.length() == 0 ? null : description.toString());
        setVisibility(content.length() == 0 ? View.INVISIBLE : View.VISIBLE);
    }

    private static final class CapturedPiece extends Drawable {
        private final Kind kind;
        private final Side color;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        CapturedPiece(Kind kind, Side color) {
            this.kind = kind;
            this.color = color;
        }

        @Override public void draw(Canvas canvas) {
            canvas.save();
            canvas.translate(getBounds().left, getBounds().top);
            canvas.scale(getBounds().width() / 48f, getBounds().height() / 48f);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color == Side.WHITE ? 0xfff1efe5 : 0xff65666d);
            canvas.drawPath(PieceArt.shape(kind), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(color == Side.WHITE ? 0xff5a5b56 : 0xffc0bfb4);
            canvas.drawPath(PieceArt.shape(kind), paint);
            canvas.restore();
        }

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter colorFilter) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
