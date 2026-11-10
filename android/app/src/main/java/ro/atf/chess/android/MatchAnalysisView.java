package ro.atf.chess.android;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import ro.atf.chess.ChessMatch;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.MatchEvaluation;
import ro.atf.chess.MatchHint;
import ro.atf.chess.QuestHint;
import ro.atf.chess.TacticalAnalysis;

/** Displays analysis for the current frame, independently of the computer's move. */
final class MatchAnalysisView extends TextView {
    private final Function<Move, String> notation;
    private final ImageButton bulb;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ChessPosition shownPosition;
    private MatchHint hint;
    private Future<?> pending;
    private int request;

    MatchAnalysisView(Context context, Function<Move, String> notation, Runnable renderBoard) {
        super(context);
        this.notation = notation;
        setId(R.id.match_analysis);
        setGravity(Gravity.CENTER);
        setTextSize(13);
        setTextColor(0xffc0bfb4);
        BoardCaption.reserve(this, 2);
        setText(R.string.match_analysis_pending);
        bulb = (ImageButton) LayoutInflater.from(context).inflate(R.layout.quest_guidance, null);
        bulb.setId(R.id.match_hint);
        bulb.setOnClickListener(view -> {
            if (hint == null) return;
            hint.revealNext();
            display();
            renderBoard.run();
            announceForAccessibility(getContentDescription());
        });
        updateBulb();
    }

    ImageButton bulb() { return bulb; }

    QuestHint.Cue cue() {
        var revealed = hint == null ? null : hint.revealed();
        return revealed == null ? new QuestHint.Cue(-1, -1) : QuestHint.SOLUTION.cue(revealed.move());
    }

    void show(ChessMatch match, int ply) {
        var position = match.positionAt(ply);
        if (position == shownPosition) return;
        cancel();
        shownPosition = position;
        setText(R.string.match_analysis_pending);
        setContentDescription(null);
        int currentRequest = request;
        pending = worker.submit(() -> {
            var lines = match.bestLinesAt(ply);
            post(() -> {
                if (request != currentRequest) return;
                pending = null;
                if (lines.isEmpty()) {
                    setText(R.string.match_analysis_ended);
                    return;
                }
                hint = new MatchHint(lines);
                display();
            });
        });
    }

    void cancel() {
        request++;
        shownPosition = null;
        hint = null;
        updateBulb();
        if (pending != null) pending.cancel(false);
        pending = null;
    }

    private void updateBulb() {
        boolean revealed = hint != null && hint.revealed() != null;
        bulb.setEnabled(hint != null);
        bulb.setSelected(revealed);
        bulb.setImageTintList(ColorStateList.valueOf(revealed ? 0xff57e0b4 : hint != null ? 0xffc0bfb4 : 0xff77766d));
        int label = !revealed ? R.string.match_hint_show : hint.count() > 1 ? R.string.match_hint_next : R.string.match_hint_shown;
        bulb.setContentDescription(getContext().getString(label));
    }

    private void display() {
        var context = getContext();
        var revealed = hint.revealed();
        var evaluation = MatchEvaluation.from(shownPosition.turn(), revealed == null ? hint.first() : revealed);
        String score = switch (evaluation.ending()) {
            case CHECKMATE -> context.getString(R.string.match_analysis_mate,
                    leadingColor(evaluation.whiteMateMoves()), Math.abs(evaluation.whiteMateMoves()));
            case STALEMATE -> context.getString(R.string.match_draw_stalemate);
            case HORIZON -> {
                String value = evaluation.pawnLabel(getResources().getConfiguration().getLocales().get(0));
                yield evaluation.whiteCentipawns() == 0 ? context.getString(R.string.match_analysis_equal, value)
                        : context.getString(R.string.match_analysis_advantage, value, leadingColor(evaluation.whiteCentipawns()));
            }
        };
        String spokenScore = evaluation.ending() == TacticalAnalysis.Ending.HORIZON
                ? context.getString(R.string.match_analysis_pawn_units, score) : score;
        if (revealed == null) {
            setText(score);
            setContentDescription(spokenScore);
        } else {
            var compact = new StringJoiner(" · ");
            var spoken = new StringJoiner(". ");
            for (Move move : revealed.line()) {
                compact.add(ChessActivity.compactNotation(move));
                spoken.add(notation.apply(move));
            }
            boolean tied = hint.count() > 1;
            String line = tied ? context.getString(R.string.match_analysis_variant, hint.number(), hint.count(), compact.toString()) : compact.toString();
            setText(score + "\n" + line);
            setContentDescription(spokenScore + ". " + context.getString(R.string.match_analysis_variant_spoken, hint.number(), hint.count(), spoken.toString()));
        }
        updateBulb();
    }

    private String leadingColor(int advantage) { return getContext().getString(advantage > 0 ? R.string.white : R.string.black); }

    void close() {
        cancel();
        worker.shutdown();
    }
}
