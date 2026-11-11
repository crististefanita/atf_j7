package ro.atf.chess.android;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview;

final class GameReviewDialog {
    private static final int PRIMARY = 0xfff5f3ec;
    private static final int MUTED = 0xffc0bfb4;
    private static final int ACCENT = 0xffb8d88a;
    private final Context context;
    private final Side humanSide;
    private final Function<Move, String> notation;
    private final IntConsumer showPosition;
    private final IntConsumer continueFrom;
    private final LinearLayout content;
    private final AlertDialog dialog;

    private GameReviewDialog(Context context, Side humanSide, Function<Move, String> notation,
                             IntConsumer showPosition, IntConsumer continueFrom) {
        this.context = context;
        this.humanSide = humanSide;
        this.notation = notation;
        this.showPosition = showPosition;
        this.continueFrom = continueFrom;
        content = column();
        dialog = new AlertDialog.Builder(context).setTitle(R.string.match_game_review).setView(content)
                .setNegativeButton(R.string.game_review_close, null).create();
    }

    static AlertDialog show(Context context, ChessMatch match, GameReview cached, ExecutorService worker,
                            Function<Move, String> notation, String human, String bot, Consumer<GameReview> remember,
                            IntConsumer showPosition, IntConsumer continueFrom, Runnable onClose) {
        var view = new GameReviewDialog(context, match.setup().humanSide(), notation, showPosition, continueFrom);
        view.dialog.show();
        var job = view.load(match, cached, worker, human, bot, remember);
        view.dialog.setOnDismissListener(dialog -> {
            if (job != null) job.cancel(true);
            onClose.run();
        });
        return view.dialog;
    }

    private Future<?> load(ChessMatch match, GameReview cached, ExecutorService worker, String human, String bot,
                           Consumer<GameReview> remember) {
        if (cached != null) {
            display(cached, human, bot);
            return null;
        }
        var label = text(context.getString(R.string.game_review_progress, 0, match.ply()), 16, PRIMARY);
        var progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(match.ply());
        content.setPadding(dp(16), dp(10), dp(16), 0);
        content.addView(label);
        content.addView(progress, new LinearLayout.LayoutParams(-1, -2));
        return worker.submit(() -> {
            try {
                var result = GameReview.analyze(match, ply -> content.post(() -> {
                    if (!dialog.isShowing()) return;
                    progress.setProgress(ply);
                    label.setText(context.getString(R.string.game_review_progress, ply, match.ply()));
                }));
                content.post(() -> {
                    if (!dialog.isShowing()) return;
                    remember.accept(result);
                    display(result, human, bot);
                });
            } catch (CancellationException cancelled) {
                // Closing the dialog cancels the local search.
            } catch (RuntimeException failure) {
                android.util.Log.e("GameReview", "Unable to analyze the game", failure);
                content.post(() -> {
                    if (!dialog.isShowing()) return;
                    label.setText(R.string.game_review_failed);
                    progress.setVisibility(View.GONE);
                });
            }
        });
    }

    private void display(GameReview result, String human, String bot) {
        dialog.getWindow().setLayout(-1, -1);
        content.removeAllViews();
        content.setPadding(0, 0, 0, 0);
        var list = new ListView(context);
        var summary = column();
        summary.setPadding(dp(16), dp(6), dp(16), dp(10));
        if (result.opening() != null) {
            summary.addView(text(result.opening().name() + " · " + result.opening().eco(), 17, ACCENT));
        } else {
            summary.addView(text(context.getString(R.string.game_review_unknown_opening), 16, MUTED));
        }
        summary.addView(text(context.getString(R.string.game_review_graph), 13, MUTED));
        summary.addView(new EvaluationGraph(result, ply -> detail(result.turns().get(ply - 1))),
                new LinearLayout.LayoutParams(-1, dp(64)));
        summary.addView(player(humanSide == Side.WHITE ? human : bot, Side.WHITE, result.white()));
        summary.addView(player(humanSide == Side.BLACK ? human : bot, Side.BLACK, result.black()));
        summary.addView(text(context.getString(R.string.game_review_rating_note), 12, MUTED));
        list.addHeaderView(summary, null, false);
        list.setAdapter(new MoveRows(result.turns()));
        list.setOnItemClickListener((parent, row, position, id) -> detail(result.turns().get(position - 1)));
        content.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));
    }

    private TextView player(String name, Side side, GameReview.Player player) {
        String accuracy = player.accuracy().isPresent() ? player.accuracy().getAsInt() + "%" : "—";
        String rating = player.rating().map(value -> "≈ " + value.elo() + " ("
                + (value.low() == null ? "≤ " + value.high() : value.low() + "–" + value.high()) + ")")
                .orElseGet(() -> context.getString(R.string.game_review_insufficient, player.ratedMoves()));
        var counts = new StringJoiner(" · ");
        for (var grade : List.of(GameReview.Grade.BRILLIANT, GameReview.Grade.GREAT, GameReview.Grade.MISTAKE,
                GameReview.Grade.MISS, GameReview.Grade.BLUNDER)) {
            int count = player.grades().getOrDefault(grade, 0);
            if (count > 0) counts.add(gradeLabel(grade) + " " + count);
        }
        int label = side == humanSide ? R.string.game_review_player : R.string.game_review_ai;
        var caption = name + " · " + context.getString(BoardView.sideLabel(side)) + "\n" + context.getString(label, accuracy, rating);
        return text(counts.length() == 0 ? caption : caption + "\n" + counts, 15, PRIMARY);
    }

    private void detail(GameReview.Turn turn) {
        String summary = context.getString(R.string.game_review_move_detail,
                gradeLabel(turn.grade()), turn.bestPoints() / 10.0, turn.playedPoints() / 10.0, turn.depth(),
                line(turn.bestLine()), line(turn.playedLine()));
        if (turn.grade() == GameReview.Grade.BRILLIANT) {
            summary += "\n" + context.getString(R.string.game_review_sacrifice, turn.sacrificeCp() / 100.0);
        }
        var options = new AlertDialog.Builder(context).setTitle(moveLabel(turn)).setMessage(summary)
                .setPositiveButton(R.string.game_review_on_board, (ignored, which) -> {
                    dialog.dismiss();
                    showPosition.accept(turn.ply());
                }).setNeutralButton(R.string.game_review_close, null);
        if (turn.side() == humanSide) {
            options.setNegativeButton(R.string.game_review_retry, (ignored, which) -> {
                dialog.dismiss();
                continueFrom.accept(turn.ply() - 1);
            });
        }
        options.show();
    }

    private String line(List<Move> moves) {
        var line = new StringJoiner(" · ");
        for (var move : moves) line.add(ChessActivity.compactNotation(move));
        return line.toString();
    }

    private String moveLabel(GameReview.Turn turn) {
        return (turn.ply() + 1) / 2 + (turn.side() == Side.WHITE ? ". " : "… ") + notation.apply(turn.played());
    }

    private String gradeLabel(GameReview.Grade grade) {
        return context.getString(switch (grade) {
            case BOOK -> R.string.game_review_book;
            case FORCED -> R.string.game_review_forced;
            case BRILLIANT -> R.string.game_review_brilliant;
            case GREAT -> R.string.game_review_great;
            case BEST -> R.string.game_review_best;
            case EXCELLENT -> R.string.game_review_excellent;
            case GOOD -> R.string.game_review_good;
            case INACCURACY -> R.string.game_review_inaccuracy;
            case MISTAKE -> R.string.game_review_mistake;
            case MISS -> R.string.game_review_miss;
            case BLUNDER -> R.string.game_review_blunder;
        });
    }

    private LinearLayout column() {
        var layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private TextView text(String value, int size, int color) {
        var label = new TextView(context);
        label.setText(value);
        label.setTextSize(size);
        label.setTextColor(color);
        label.setPadding(0, dp(5), 0, dp(5));
        return label;
    }

    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }

    private final class MoveRows extends BaseAdapter {
        private final List<GameReview.Turn> turns;

        private MoveRows(List<GameReview.Turn> turns) { this.turns = turns; }
        @Override public int getCount() { return turns.size(); }
        @Override public GameReview.Turn getItem(int index) { return turns.get(index); }
        @Override public long getItemId(int index) { return getItem(index).ply(); }

        @Override public View getView(int index, View recycled, ViewGroup parent) {
            var label = recycled instanceof TextView view ? view : text("", 15, PRIMARY);
            label.setMinHeight(dp(48));
            label.setPadding(dp(16), dp(8), dp(16), dp(8));
            var turn = getItem(index);
            label.setText(moveLabel(turn) + " · " + gradeLabel(turn.grade()));
            label.setTextColor(switch (turn.grade()) {
                case BRILLIANT, GREAT, BEST -> ACCENT;
                case MISTAKE, MISS, BLUNDER -> 0xffffb591;
                default -> PRIMARY;
            });
            return label;
        }
    }

    private final class EvaluationGraph extends View {
        private final GameReview result;
        private final IntConsumer select;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int selected = 1;

        private EvaluationGraph(GameReview result, IntConsumer select) {
            super(context);
            this.result = result;
            this.select = select;
            setContentDescription(context.getString(R.string.game_review_graph));
            setClickable(!result.turns().isEmpty());
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth() - dp(4);
            float height = getHeight() - dp(4);
            float inset = dp(2);
            paint.setColor(0xff56564c);
            paint.setStrokeWidth(dp(1));
            canvas.drawLine(inset, inset + height / 2, inset + width, inset + height / 2, paint);
            paint.setColor(ACCENT);
            paint.setStrokeWidth(dp(2));
            float x = inset;
            float y = inset + height * (1 - result.initialWhitePoints() / 1000f);
            int count = result.turns().size();
            for (var turn : result.turns()) {
                float nextX = inset + width * turn.ply() / count;
                float nextY = inset + height * (1 - turn.whitePoints() / 1000f);
                canvas.drawLine(x, y, nextX, nextY, paint);
                x = nextX;
                y = nextY;
            }
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (isClickable() && event.getActionMasked() == MotionEvent.ACTION_UP && getWidth() > 0) {
                int count = result.turns().size();
                selected = Math.max(1, Math.min(count, Math.round(count * event.getX() / getWidth())));
            }
            return super.onTouchEvent(event);
        }

        @Override public boolean performClick() {
            super.performClick();
            if (result.turns().isEmpty()) return false;
            select.accept(selected);
            return true;
        }
    }
}
