package ro.atf.chess.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import java.util.List;
import java.util.function.IntConsumer;
import ro.atf.chess.BoardSquare;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.QuestHint;

final class BoardView extends ViewGroup {
    private static final int LIGHT = 0xffedf0d5;
    private static final int DARK = 0xff769556;
    private static final int LIGHT_MARK = 0xfff4ef86;
    private static final int DARK_MARK = 0xffbec953;
    private static final int HINT_MARK = 0xffffb64d;
    private static final int HINT_OUTLINE = 0xff442d16;
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private Cell hintOrigin;
    private Cell hintDestination;

    BoardView(Context context, IntConsumer select) {
        super(context);
        for (int index = 0; index < 64; index++) {
            Cell cell = new Cell(context);
            cell.setOnClickListener(view -> select.accept(cell.square));
            cell.setFocusable(true);
            addView(cell);
        }
    }

    void show(ChessPosition position, boolean blackBelow, int selected, List<ChessPosition.Move> moves, ChessPosition.Move last, QuestHint.Cue hint) {
        hintOrigin = null;
        hintDestination = null;
        for (int index = 0; index < 64; index++) {
            int column = index % 8;
            int row = index / 8;
            Cell cell = (Cell) getChildAt(index);
            int square = BoardSquare.atDisplay(column, row, blackBelow);
            cell.square = square;
            cell.piece = position.pieceAt(square);
            cell.light = BoardSquare.isLight(square);
            cell.highlight = selected == square || last != null && (last.from() == square || last.to() == square);
            cell.destination = moves.stream().anyMatch(move -> move.to() == square);
            cell.hinted = square == hint.origin();
            if (cell.hinted) hintOrigin = cell;
            if (square == hint.destination()) hintDestination = cell;
            cell.rankLabel = column == 0;
            cell.fileLabel = row == 7;
            cell.setSelected(selected == square);
            String coordinate = BoardSquare.name(square);
            String description = cell.piece == null ? coordinate : getContext().getString(R.string.piece_square, coordinate,
                    getContext().getString(sideLabel(cell.piece.side())), getContext().getString(kindLabel(cell.piece.kind())));
            if (cell.destination) description = getContext().getString(R.string.legal_destination, description);
            if (cell.hinted) description = getContext().getString(R.string.hint_origin, description);
            if (cell == hintDestination) description = getContext().getString(R.string.hint_destination, description);
            cell.setContentDescription(description);
            cell.invalidate();
        }
        invalidate();
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (hintOrigin == null || hintDestination == null) return;
        float unit = hintOrigin.getWidth();
        if (unit == 0) return;
        float x = hintOrigin.getLeft() + unit / 2;
        float y = hintOrigin.getTop() + hintOrigin.getHeight() / 2f;
        float dx = hintDestination.getLeft() + hintDestination.getWidth() / 2f - x;
        float dy = hintDestination.getTop() + hintDestination.getHeight() / 2f - y;
        float distance = (float) Math.hypot(dx, dy);
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate((float) Math.toDegrees(Math.atan2(dy, dx)));
        float start = unit * .31f;
        float tip = distance - unit * .20f;
        float head = tip - unit * .21f;
        float halfShaft = unit * .04f;
        float halfHead = unit * .12f;
        arrow.rewind();
        arrow.moveTo(start, -halfShaft);
        arrow.lineTo(head, -halfShaft);
        arrow.lineTo(head, -halfHead);
        arrow.lineTo(tip, 0);
        arrow.lineTo(head, halfHead);
        arrow.lineTo(head, halfShaft);
        arrow.lineTo(start, halfShaft);
        arrow.close();
        arrowPaint.setColor(HINT_OUTLINE);
        arrowPaint.setStyle(Paint.Style.STROKE);
        arrowPaint.setStrokeJoin(Paint.Join.ROUND);
        arrowPaint.setStrokeWidth(unit * .045f);
        canvas.drawPath(arrow, arrowPaint);
        arrowPaint.setStyle(Paint.Style.FILL);
        arrowPaint.setColor(HINT_MARK);
        canvas.drawPath(arrow, arrowPaint);
        canvas.restore();
    }

    static int sideLabel(ChessPosition.Side side) {
        return side == ChessPosition.Side.WHITE ? R.string.white : R.string.black;
    }

    static int kindLabel(ChessPosition.Kind kind) {
        return switch (kind) {
            case PAWN -> R.string.pawn;
            case KNIGHT -> R.string.knight;
            case BISHOP -> R.string.bishop;
            case ROOK -> R.string.rook;
            case QUEEN -> R.string.queen;
            case KING -> R.string.king;
        };
    }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        int size = Math.min(MeasureSpec.getSize(widthSpec), MeasureSpec.getSize(heightSpec));
        for (int index = 0; index < 64; index++) {
            int column = index % 8;
            int row = index / 8;
            getChildAt(index).measure(exact(edge(size, column + 1) - edge(size, column)), exact(edge(size, row + 1) - edge(size, row)));
        }
        setMeasuredDimension(size, size);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int size = getWidth();
        for (int index = 0; index < 64; index++) {
            int column = index % 8;
            int row = index / 8;
            getChildAt(index).layout(edge(size, column), edge(size, row), edge(size, column + 1), edge(size, row + 1));
        }
    }

    private static int edge(int size, int index) { return size * index / 8; }

    private static int exact(int size) { return MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY); }

    private static final class Cell extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int square;
        private ChessPosition.Piece piece;
        private boolean light;
        private boolean highlight;
        private boolean destination;
        private boolean hinted;
        private boolean rankLabel;
        private boolean fileLabel;

        Cell(Context context) { super(context); }

        @Override protected void onDraw(Canvas canvas) {
            int width = getWidth();
            int height = getHeight();
            canvas.drawColor(highlight ? (light ? LIGHT_MARK : DARK_MARK) : (light ? LIGHT : DARK));
            if (piece != null) drawPiece(canvas, width, height);
            if (destination) {
                paint.setColor(0x66445626);
                if (piece == null) {
                    canvas.drawCircle(width / 2f, height / 2f, width * .115f, paint);
                } else {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(width * .065f);
                    canvas.drawCircle(width / 2f, height / 2f, width * .435f, paint);
                    paint.setStyle(Paint.Style.FILL);
                }
            }
            if (hinted) drawHintRing(canvas, width, height);
            paint.setColor(light ? 0xff506638 : LIGHT);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(width * .19f);
            String label = BoardSquare.name(square);
            if (rankLabel) canvas.drawText(label.substring(1), width * .05f, height * .21f, paint);
            if (fileLabel) canvas.drawText(label.substring(0, 1), width * .83f, height * .96f, paint);
        }

        private void drawHintRing(Canvas canvas, int width, int height) {
            float inset = width * .06f;
            float radius = width * .15f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(width * .085f);
            paint.setColor(HINT_OUTLINE);
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint);
            paint.setStrokeWidth(width * .045f);
            paint.setColor(HINT_MARK);
            canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawPiece(Canvas canvas, int width, int height) {
            canvas.save();
            canvas.translate(width * .10f, height * .05f);
            canvas.scale(width * .80f / 48, height * .88f / 48);
            boolean white = piece.side() == ChessPosition.Side.WHITE;
            Path silhouette = PieceArt.shape(piece.kind());
            paint.setColor(white ? 0xfffffef9 : 0xff51535a);
            canvas.drawPath(silhouette, paint);
            paint.setColor(white ? 0xff414244 : 0xff26272c);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeWidth(1.35f);
            canvas.drawPath(silhouette, paint);
            paint.setColor(white ? 0xff666762 : 0xff313238);
            paint.setStrokeWidth(1.12f);
            canvas.drawPath(PieceArt.details(piece.kind()), paint);
            paint.setStyle(Paint.Style.FILL);
            canvas.restore();
        }
    }
}
