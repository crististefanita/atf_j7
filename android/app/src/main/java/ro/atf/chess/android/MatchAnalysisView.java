package ro.atf.chess.android;

import android.content.Context;
import android.view.Gravity;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis;

/** Displays analysis for the current frame, independently of the computer's move. */
final class MatchAnalysisView extends TextView {
    private final Function<Move, String> notation;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ChessPosition shownPosition;
    private Future<?> pending;
    private int request;

    MatchAnalysisView(Context context, Function<Move, String> notation) {
        super(context);
        this.notation = notation;
        setId(R.id.match_analysis);
        setGravity(Gravity.CENTER);
        setTextSize(13);
        setTextColor(0xffc0bfb4);
        setText(R.string.match_analysis_pending);
    }

    void show(ChessMatch match, int ply) {
        var position = match.positionAt(ply);
        if (position == shownPosition) return;
        cancel();
        shownPosition = position;
        setText(R.string.match_analysis_pending);
        int currentRequest = request;
        pending = worker.submit(() -> {
            var analysis = match.analysisAt(ply);
            post(() -> {
                if (request != currentRequest) return;
                pending = null;
                setText(analysis.isPresent() ? format(position, analysis.get()) : getContext().getString(R.string.match_analysis_ended));
            });
        });
    }

    void cancel() {
        request++;
        shownPosition = null;
        if (pending != null) pending.cancel(false);
        pending = null;
    }

    private String format(ChessPosition position, TacticalAnalysis.Variation variation) {
        var context = getContext();
        String score = switch (variation.ending()) {
            case CHECKMATE -> context.getString(variation.signedMatePlies() > 0
                    ? R.string.match_analysis_mate_for : R.string.match_analysis_mate_against, Math.abs(variation.signedMatePlies()));
            case STALEMATE -> context.getString(R.string.match_draw_stalemate);
            case HORIZON -> {
                var number = NumberFormat.getNumberInstance(getResources().getConfiguration().getLocales().get(0));
                String value = number.format(variation.score() / 100.0);
                yield context.getString(R.string.match_analysis_material, variation.score() > 0 ? "+" + value : value);
            }
        };
        return context.getString(R.string.match_analysis_result, context.getString(BoardView.sideLabel(position.turn())),
                notation.apply(variation.move()), score);
    }

    void close() {
        cancel();
        worker.shutdown();
    }
}
