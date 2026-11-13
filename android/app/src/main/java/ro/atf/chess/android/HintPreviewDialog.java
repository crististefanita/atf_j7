package ro.atf.chess.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.HintComparison;
import ro.atf.chess.HintPreview;
import ro.atf.chess.QuestHint;
import ro.atf.chess.TacticalAnalysis;

/** Plays the suggested branch on its own board and returns to the original cursor. */
final class HintPreviewDialog {
    private HintPreviewDialog() { }

    static AlertDialog show(Context context, ChessPosition root, HintPreview initial, TacticalAnalysis.Variation line,
                            Optional<HintComparison> result, boolean blackBelow, Function<Move, String> notation) {
        var body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
        body.setPadding(padding, 0, padding, 0);
        var reason = new TextView(context);
        reason.setId(R.id.hint_preview_reason);
        reason.setText(reason(context, initial.evidence(), line, notation));
        body.addView(reason);
        var summary = new TextView(context);
        summary.setId(R.id.hint_compare_summary);
        summary.setText(R.string.hint_compare_unavailable);
        body.addView(summary);
        var choices = new LinearLayout(context);
        var best = branchButton(context);
        var other = branchButton(context);
        best.setId(R.id.hint_compare_best);
        other.setId(R.id.hint_compare_other);
        choices.addView(best, new LinearLayout.LayoutParams(0, -2, 1));
        choices.addView(other, new LinearLayout.LayoutParams(0, -2, 1));
        choices.setVisibility(TextView.INVISIBLE);
        body.addView(choices);
        var board = new BoardView(context, ignored -> { });
        board.setId(R.id.hint_preview_board);
        board.setContentDescription(context.getString(R.string.game_review_board));
        var resources = context.getResources();
        int boardLimit = Math.round(resources.getConfiguration().screenHeightDp * .48f * resources.getDisplayMetrics().density);
        var boardLayout = new LinearLayout.LayoutParams(-1, boardLimit);
        boardLayout.gravity = Gravity.CENTER_HORIZONTAL;
        body.addView(board, boardLayout);
        var controls = new LinearLayout(context);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        var back = arrow(context, false);
        var forward = arrow(context, true);
        var step = new TextView(context);
        step.setId(R.id.hint_preview_step);
        step.setGravity(Gravity.CENTER);
        step.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        controls.addView(back);
        controls.addView(step, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(forward);
        body.addView(controls);
        var scroll = new ScrollView(context);
        scroll.addView(body);
        var dialog = new AlertDialog.Builder(context).setTitle(R.string.hint_preview_title)
                .setView(scroll).setPositiveButton(R.string.hint_preview_return, null).create();
        var playback = new Playback(board, step, back, forward, blackBelow, notation, initial);
        result.ifPresent(comparison -> {
            var bestReplay = HintPreview.open(root, comparison.stronger()).orElseThrow();
            var otherReplay = HintPreview.open(root, comparison.alternative()).orElseThrow();
            summary.setText(context.getString(R.string.hint_compare_scores, comparison.depth(),
                    score(context, comparison.stronger()), score(context, comparison.alternative())));
            comparison.decisive().ifPresentOrElse(proof -> {
                reason.setText(decisive(context, proof, notation));
                reason.setOnClickListener(view -> {
                    var branch = proof.inStronger() ? bestReplay : otherReplay;
                    branch.seek(proof.ply());
                    selectBranch(proof.inStronger() ? best : other, proof.inStronger() ? other : best, branch, playback);
                });
                reason.setContentDescription(reason.getText() + " " + context.getString(R.string.hint_compare_jump));
            }, () -> reason.setText(R.string.hint_compare_estimate));
            int bestLabel = comparison.stronger().score() == comparison.alternative().score()
                    ? R.string.hint_compare_equal : R.string.hint_compare_best;
            best.setText(context.getString(bestLabel, ChessActivity.compactNotation(comparison.stronger().move())));
            other.setText(context.getString(R.string.hint_compare_other, ChessActivity.compactNotation(comparison.alternative().move())));
            choices.setVisibility(TextView.VISIBLE);
            best.setOnClickListener(view -> selectBranch(best, other, bestReplay, playback));
            other.setOnClickListener(view -> selectBranch(other, best, otherReplay, playback));
            boolean isBest = line.equals(comparison.stronger());
            selectBranch(isBest ? best : other, isBest ? other : best, isBest ? bestReplay : otherReplay, playback);
        });
        if (result.isEmpty()) playback.render();
        dialog.show();
        Button close = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        close.setId(R.id.hint_preview_return);
        return dialog;
    }

    private static void selectBranch(Button selected, Button other, HintPreview branch, Playback playback) {
        selected.setSelected(true);
        other.setSelected(false);
        playback.preview = branch;
        playback.render();
    }

    private static Button branchButton(Context context) {
        var button = new Button(context);
        button.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_selected}, {}},
                new int[] {0xff57e0b4, button.getCurrentTextColor()}));
        return button;
    }

    private static final class Playback {
        private final BoardView board;
        private final TextView step;
        private final ImageButton back;
        private final ImageButton forward;
        private final boolean blackBelow;
        private final Function<Move, String> notation;
        private HintPreview preview;

        Playback(BoardView board, TextView step, ImageButton back, ImageButton forward, boolean blackBelow,
                 Function<Move, String> notation, HintPreview preview) {
            this.board = board;
            this.step = step;
            this.back = back;
            this.forward = forward;
            this.blackBelow = blackBelow;
            this.notation = notation;
            this.preview = preview;
            back.setOnClickListener(view -> { this.preview.previous(); render(); });
            forward.setOnClickListener(view -> { this.preview.next(); render(); });
        }

        void render() {
            var context = board.getContext();
            var position = preview.position();
            Move last = preview.lastMove();
            var cue = preview.step() == 0 ? QuestHint.SOLUTION.cue(preview.firstMove()) : new QuestHint.Cue(-1, -1);
            board.show(position, blackBelow, -1, List.of(), last, cue, false, step.getId());
            String move = last == null ? context.getString(R.string.hint_preview_start)
                    : context.getString(R.string.hint_preview_played,
                            context.getString(BoardView.sideLabel(position.turn().other())), notation.apply(last));
            step.setText(context.getString(R.string.hint_preview_step, preview.step(), preview.length(), move));
            back.setEnabled(preview.hasPrevious());
            forward.setEnabled(preview.hasNext());
        }
    }

    private static String decisive(Context context, HintComparison.DecisiveMove proof, Function<Move, String> notation) {
        String branch = context.getString(proof.inStronger() ? R.string.hint_compare_stronger_label : R.string.hint_compare_other_label);
        String piece = context.getString(BoardView.kindLabel(proof.piece()));
        return context.getString(proof.reason() == HintComparison.Reason.MATE ? R.string.hint_compare_mate : R.string.hint_compare_capture,
                branch, proof.ply(), notation.apply(proof.move()), piece);
    }

    private static ImageButton arrow(Context context, boolean forward) {
        var button = new ImageButton(context, null, android.R.attr.borderlessButtonStyle);
        button.setId(forward ? R.id.hint_preview_next : R.id.hint_preview_previous);
        button.setImageResource(R.drawable.match_chevron);
        if (!forward) button.setScaleX(-1);
        button.setContentDescription(context.getString(forward ? R.string.match_history_next : R.string.match_history_previous));
        int size = Math.round(48 * context.getResources().getDisplayMetrics().density);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return button;
    }

    private static String reason(Context context, HintPreview.Evidence proof, TacticalAnalysis.Variation line, Function<Move, String> notation) {
        if (proof.reason() == HintPreview.Reason.CHECKMATE || proof.reason() == HintPreview.Reason.CHECKMATE_AGAINST) {
            int text = proof.reason() == HintPreview.Reason.CHECKMATE ? R.string.hint_preview_mate : R.string.hint_preview_mate_against;
            return context.getString(text, context.getString(BoardView.kindLabel(proof.piece())), notation.apply(proof.move()));
        }
        if (proof.reason() == HintPreview.Reason.CAPTURE) {
            return context.getString(R.string.hint_preview_capture, notation.apply(proof.move()),
                    context.getString(BoardView.kindLabel(proof.captured())), proof.netMaterial());
        }
        if (proof.reason() == HintPreview.Reason.STALEMATE) return context.getString(R.string.match_draw_stalemate);
        return context.getString(R.string.hint_preview_estimate, pawns(context, line.score()));
    }

    private static String score(Context context, TacticalAnalysis.Variation line) {
        int mate = line.signedMatePlies();
        if (mate != 0) return context.getString(R.string.hint_compare_mate_score, mate > 0 ? "+" : "−", Math.abs(mate));
        if (line.ending() == TacticalAnalysis.Ending.STALEMATE) return context.getString(R.string.match_draw_stalemate);
        return pawns(context, line.score());
    }

    private static String pawns(Context context, int centipawns) {
        var number = NumberFormat.getNumberInstance(context.getResources().getConfiguration().getLocales().get(0));
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        return number.format(centipawns / 100.0);
    }
}
