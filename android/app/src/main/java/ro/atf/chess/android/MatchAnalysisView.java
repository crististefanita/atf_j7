package ro.atf.chess.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageButton;
import android.widget.TextView;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Supplier;
import ro.atf.chess.AnalysisLine;
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
    private final EvaluationBar evaluationBar;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ChessPosition shownPosition;
    private double shownTerminal = Double.NaN;
    private MatchHint hint;
    private MatchEvaluation shownEvaluation;
    private AlertDialog preview;
    private Future<?> pending;
    private int request;

    MatchAnalysisView(Context context, Function<Move, String> notation, Runnable renderBoard) {
        super(context);
        this.notation = notation;
        evaluationBar = new EvaluationBar(context);
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
    EvaluationBar evaluationBar() { return evaluationBar; }

    Move revealedMove() {
        return hint == null ? null : hint.revealedMove();
    }

    Move moveForTap(ChessPosition position, int selected, int square) {
        return position == shownPosition && hint != null ? hint.moveForTap(position, selected, square) : null;
    }

    QuestHint.Cue cue() {
        var move = revealedMove();
        return move == null ? new QuestHint.Cue(-1, -1) : QuestHint.SOLUTION.cue(move);
    }

    void show(ChessMatch match, int ply) {
        double terminal = match.result() != ChessMatch.Result.IN_PROGRESS && ply == match.ply()
                ? MatchEvaluation.terminalWhiteShare(match.result()) : Double.NaN;
        show(match.positionAt(ply), () -> match.hintAt(ply), terminal);
    }

    void show(ChessPosition position) {
        show(position, () -> MatchHint.analyze(position), Double.NaN);
    }

    void show(AnalysisLine line) {
        double terminal = line.result() == ChessMatch.Result.IN_PROGRESS
                ? Double.NaN : MatchEvaluation.terminalWhiteShare(line.result());
        show(line.position(), line::hint, terminal);
    }

    private void show(ChessPosition position, Supplier<MatchHint> search, double terminal) {
        if (position == shownPosition && Double.compare(terminal, shownTerminal) == 0) return;
        cancel();
        shownPosition = position;
        shownTerminal = terminal;
        setText(R.string.match_analysis_pending);
        setContentDescription(null);
        int currentRequest = request;
        pending = worker.submit(() -> {
            var analyzed = search.get();
            var evaluation = analyzed.evaluation(position);
            post(() -> {
                if (request != currentRequest) return;
                pending = null;
                if (evaluation == null) {
                    setText(R.string.match_analysis_ended);
                    evaluationBar.setWhiteShare(terminal);
                    return;
                }
                hint = analyzed;
                shownEvaluation = evaluation;
                display();
            });
        });
    }

    void hideSuggestion() {
        if (revealedMove() == null) return;
        hint.hide();
        display();
    }

    void cancel() {
        if (preview != null) preview.dismiss();
        preview = null;
        request++;
        shownPosition = null;
        shownTerminal = Double.NaN;
        hint = null;
        shownEvaluation = null;
        setOnClickListener(null);
        setClickable(false);
        evaluationBar.setWhiteShare(Double.NaN);
        updateBulb();
        if (pending != null) pending.cancel(true);
        pending = null;
    }

    private void updateBulb() {
        boolean revealed = revealedMove() != null;
        bulb.setEnabled(hint != null);
        bulb.setSelected(revealed);
        bulb.setImageTintList(ColorStateList.valueOf(revealed ? 0xff57e0b4 : hint != null ? 0xffc0bfb4 : 0xff77766d));
        int label = !revealed ? R.string.match_hint_show : hint.count() > 1 ? R.string.match_hint_next : R.string.match_hint_shown;
        bulb.setContentDescription(getContext().getString(label));
    }

    private void display() {
        var context = getContext();
        var revealed = hint.revealed();
        var theory = hint.revealedTheory();
        var evaluation = shownEvaluation;
        evaluationBar.setWhiteShare(evaluation.whiteShare());
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
        if (theory != null) {
            displayTheory(score, spokenScore, theory);
        } else if (revealed == null) {
            setText(score);
            setContentDescription(spokenScore);
            setOnClickListener(null);
            setClickable(false);
        } else {
            var spoken = new StringJoiner(". ");
            for (Move move : revealed.line()) spoken.add(notation.apply(move));
            String firstMove = ChessActivity.compactNotation(revealed.move());
            String choice = hint.count() > 1
                    ? context.getString(R.string.match_analysis_variant, hint.number(), hint.count(), firstMove) : firstMove;
            String action = context.getString(R.string.hint_preview_action, choice);
            String full = context.getString(R.string.match_analysis_variant_spoken, hint.number(), hint.count(), spoken.toString());
            suggestion(score, action, spokenScore + ". " + full + ". " + context.getString(R.string.hint_preview_open), this::openPreview);
        }
        updateBulb();
    }

    private void displayTheory(String score, String spokenScore, MatchHint.Theory theory) {
        var context = getContext();
        var locale = getResources().getConfiguration().getLocales().get(0);
        String firstMove = ChessActivity.compactNotation(theory.move());
        String family = theory.family().in(locale);
        String title = HintPreviewDialog.theoryTitle(context, theory);
        String action = hint.count() == 1 ? context.getString(R.string.hint_theory_caption, firstMove, family)
                : context.getString(R.string.hint_theory_numbered, hint.number(), hint.count(), firstMove, family);
        String detail = theory.idea() == null ? context.getString(R.string.hint_theory_index_detail)
                : theory.idea().in(locale);
        String move = notation.apply(theory.move());
        String spoken = context.getString(R.string.hint_theory_accessible, move, title, detail);
        suggestion(score, action, spokenScore + ". " + action + ". " + spoken, this::openTheory);
    }

    private void suggestion(String score, String action, String description, Runnable open) {
        var caption = new SpannableString(score + "\n" + action);
        caption.setSpan(new ForegroundColorSpan(0xff57e0b4), score.length() + 1, caption.length(), 0);
        setText(caption);
        setContentDescription(description);
        setOnClickListener(view -> open.run());
    }

    private void openTheory() {
        if (hint == null || preview != null) return;
        hint.theoryPlayback(shownPosition).ifPresent(study ->
                rememberPreview(HintPreviewDialog.showTheory(getContext(), shownPosition, study, evaluationBar.blackBelow(), notation)));
    }

    private void openPreview() {
        var position = shownPosition;
        var current = hint;
        if (current == null || preview != null) return;
        current.playback(position).ifPresent(playback ->
                rememberPreview(HintPreviewDialog.show(getContext(), position, playback, evaluationBar.blackBelow(), notation)));
    }

    private void rememberPreview(AlertDialog screen) {
        preview = screen;
        screen.setOnDismissListener(dialog -> { if (preview == screen) preview = null; });
    }

    private String leadingColor(int advantage) { return getContext().getString(advantage > 0 ? R.string.white : R.string.black); }

    void close() {
        cancel();
        worker.shutdown();
    }
}
