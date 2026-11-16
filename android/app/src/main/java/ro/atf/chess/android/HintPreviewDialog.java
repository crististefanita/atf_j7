package ro.atf.chess.android;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.HintComparison;
import ro.atf.chess.HintPreview;
import ro.atf.chess.MatchHint;
import ro.atf.chess.QuestHint;

/** Plays the suggested branch on its own board and returns to the original cursor. */
final class HintPreviewDialog {
    private static final int[] BRANCH_IDS = {R.id.hint_compare_best, R.id.hint_compare_other, R.id.hint_compare_third};

    private HintPreviewDialog() { }

    static AlertDialog show(Context context, ChessPosition root, MatchHint.Playback study,
                            boolean blackBelow, Function<Move, String> notation) {
        var body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        body.setPadding(padding, 0, padding, 0);
        if (study.depth() > 0) {
            var summary = new TextView(context);
            summary.setId(R.id.hint_compare_summary);
            summary.setText(context.getString(R.string.hint_compare_depth, study.depth()));
            body.addView(summary);
        }
        var choices = new LinearLayout(context);
        choices.setBaselineAligned(false);
        var choicesLayout = new LinearLayout.LayoutParams(-1, -2);
        choicesLayout.setMargins(0, dp(context, 6), 0, dp(context, 6));
        if (study.lines().size() > 1) body.addView(choices, choicesLayout);
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
        BoardCaption.reserve(step, 2);
        step.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        controls.addView(back);
        controls.addView(step, new LinearLayout.LayoutParams(0, -2, 1));
        controls.addView(forward);
        body.addView(controls);
        var details = new FrameLayout(context);
        body.addView(details);
        var scroll = new ScrollView(context);
        scroll.addView(body);
        var dialog = new AlertDialog.Builder(context).setTitle(R.string.hint_preview_title)
                .setView(scroll).setPositiveButton(R.string.hint_preview_return, null).create();
        var branches = branches(context, root, study, choices, details, notation);
        var playback = new Playback(board, step, back, forward, blackBelow, notation, branches);
        int selected = 0;
        for (int index = 0; index < branches.size(); index++) {
            var branch = branches.get(index);
            if (study.lines().get(index).move().equals(study.selected())) selected = index;
            int branchIndex = index;
            branch.button().setOnClickListener(view -> playback.select(branchIndex));
            study.comparison(root, index).flatMap(HintComparison::decisive).filter(proof -> {
                int target = proof.inStronger() ? 0 : branchIndex == 0 ? 1 : branchIndex;
                return !branches.get(target).replay().addedDefense();
            }).ifPresentOrElse(proof -> {
                int target = proof.inStronger() ? 0 : branchIndex == 0 ? 1 : branchIndex;
                branch.reason().setText(decisive(context, proof, target + 1, notation));
                branch.reason().setContentDescription(branch.reason().getText() + " " + context.getString(R.string.hint_compare_jump));
                branch.reason().setOnClickListener(view -> playback.seek(target, proof.ply()));
            }, () -> branch.reason().setText(reason(context, root, branch.replay().evidence(), notation)));
        }
        playback.select(selected);
        dialog.show();
        Button close = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        close.setId(R.id.hint_preview_return);
        close.setAllCaps(false);
        return dialog;
    }

    private static List<Branch> branches(Context context, ChessPosition root, MatchHint.Playback study,
                                         LinearLayout choices, FrameLayout details, Function<Move, String> notation) {
        var branches = new ArrayList<Branch>(study.lines().size());
        for (int index = 0; index < study.lines().size(); index++) {
            var line = study.lines().get(index);
            var preview = HintPreview.openDefended(root, line).orElseThrow();
            String evaluation = preview.estimate().map(estimate -> pawns(context, estimate.finalCp()))
                    .orElseGet(() -> terminalLabel(context, preview));
            var button = branchButton(context);
            button.setId(BRANCH_IDS[index]);
            button.setText(context.getString(R.string.hint_compare_choice, index + 1, ChessActivity.compactNotation(line.move())));
            int spoken = index == 0 ? R.string.hint_compare_recommended_spoken : R.string.hint_compare_choice_spoken;
            button.setContentDescription(context.getString(spoken, index + 1, notation.apply(line.move()), evaluation));
            var layout = new LinearLayout.LayoutParams(0, -1, 1);
            if (index + 1 < study.lines().size()) layout.setMarginEnd(dp(context, 6));
            choices.addView(button, layout);

            var label = new TextView(context);
            label.setText(preview.estimate().map(estimate -> context.getString(R.string.hint_preview_change,
                    pawns(context, estimate.initialCp()), evaluation, pawns(context, estimate.changeCp())))
                    .orElse(evaluation));
            preview.estimate().ifPresent(estimate -> label.setContentDescription(context.getString(R.string.hint_preview_change_spoken,
                    pawns(context, estimate.initialCp()), evaluation, pawns(context, estimate.changeCp()))));
            var reason = new TextView(context);
            var panel = new LinearLayout(context);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.addView(label);
            panel.addView(reason);
            details.addView(panel, new FrameLayout.LayoutParams(-1, -2));
            branches.add(new Branch(preview, button, panel, label, reason));
        }
        return branches;
    }

    private static Button branchButton(Context context) {
        var button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        int[] selected = {android.R.attr.state_selected};
        int[] defaultState = {};
        var surface = new StateListDrawable();
        surface.addState(selected, branchSurface(context, 0xff29473e, 0xff57e0b4));
        surface.addState(defaultState, branchSurface(context, 0xff34372e, 0xff525647));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x3257e0b4), surface, null));
        button.setTextColor(new ColorStateList(new int[][] {selected, defaultState}, new int[] {0xff9cf3d8, 0xfff5f3ec}));
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setMinHeight(dp(context, 48));
        button.setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4));
        return button;
    }

    private static GradientDrawable branchSurface(Context context, int fill, int border) {
        var surface = new GradientDrawable();
        surface.setColor(fill);
        surface.setCornerRadius(dp(context, 10));
        surface.setStroke(dp(context, 1), border);
        return surface;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private record Branch(HintPreview replay, Button button, LinearLayout details, TextView score, TextView reason) { }

    private static final class Playback {
        private final BoardView board;
        private final TextView step;
        private final ImageButton back;
        private final ImageButton forward;
        private final boolean blackBelow;
        private final Function<Move, String> notation;
        private final List<Branch> branches;
        private HintPreview preview;

        Playback(BoardView board, TextView step, ImageButton back, ImageButton forward, boolean blackBelow,
                 Function<Move, String> notation, List<Branch> branches) {
            this.board = board;
            this.step = step;
            this.back = back;
            this.forward = forward;
            this.blackBelow = blackBelow;
            this.notation = notation;
            this.branches = List.copyOf(branches);
            back.setOnClickListener(view -> { this.preview.previous(); render(); });
            forward.setOnClickListener(view -> { this.preview.next(); render(); });
        }

        void select(int active) {
            for (int index = 0; index < branches.size(); index++) {
                var branch = branches.get(index);
                boolean selected = index == active;
                branch.button().setSelected(selected);
                // Measure all explanations so changing the active branch cannot move the board.
                branch.details().setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
                branch.score().setId(selected ? R.id.hint_preview_delta : View.NO_ID);
                branch.reason().setId(selected ? R.id.hint_preview_reason : View.NO_ID);
            }
            preview = branches.get(active).replay();
            render();
        }

        void seek(int active, int ply) {
            branches.get(active).replay().seek(ply);
            select(active);
        }

        void render() {
            var context = board.getContext();
            var position = preview.position();
            Move last = preview.lastMove();
            var cue = preview.step() == 0 ? QuestHint.SOLUTION.cue(preview.firstMove()) : new QuestHint.Cue(-1, -1);
            board.show(position, blackBelow, -1, List.of(), last, cue, false, step.getId());
            String move = last == null ? context.getString(R.string.hint_preview_start)
                    : preview.addedDefense() && !preview.hasNext() ? context.getString(R.string.hint_preview_checked_reply, notation.apply(last))
                    : context.getString(R.string.hint_preview_played,
                            context.getString(BoardView.sideLabel(position.turn().other())), notation.apply(last));
            step.setText(context.getString(R.string.hint_preview_step, preview.step(), preview.length(), move));
            back.setEnabled(preview.hasPrevious());
            forward.setEnabled(preview.hasNext());
        }
    }

    private static String decisive(Context context, HintComparison.DecisiveMove proof, int rank, Function<Move, String> notation) {
        String piece = context.getString(BoardView.kindLabel(proof.piece()));
        return context.getString(proof.reason() == HintComparison.Reason.MATE ? R.string.hint_compare_mate : R.string.hint_compare_capture,
                rank, proof.ply(), notation.apply(proof.move()), piece);
    }

    private static ImageButton arrow(Context context, boolean forward) {
        var button = new ImageButton(context, null, android.R.attr.borderlessButtonStyle);
        button.setId(forward ? R.id.hint_preview_next : R.id.hint_preview_previous);
        button.setImageResource(R.drawable.match_chevron);
        if (!forward) button.setScaleX(-1);
        button.setContentDescription(context.getString(forward ? R.string.match_history_next : R.string.match_history_previous));
        int size = dp(context, 48);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        return button;
    }

    private static String reason(Context context, ChessPosition root, HintPreview.Evidence proof, Function<Move, String> notation) {
        if (proof.reason() == HintPreview.Reason.CHECKMATE || proof.reason() == HintPreview.Reason.CHECKMATE_AGAINST) {
            int text = proof.reason() == HintPreview.Reason.CHECKMATE ? R.string.hint_preview_mate : R.string.hint_preview_mate_against;
            return context.getString(text, context.getString(BoardView.kindLabel(proof.piece())), notation.apply(proof.move()));
        }
        if (proof.reason() == HintPreview.Reason.CAPTURE) {
            return context.getString(R.string.hint_preview_capture, notation.apply(proof.move()),
                    context.getString(BoardView.kindLabel(proof.captured())), proof.netMaterial());
        }
        if (proof.reason() == HintPreview.Reason.STALEMATE) return context.getString(R.string.match_draw_stalemate);
        return context.getString(R.string.hint_preview_estimate, context.getString(BoardView.sideLabel(root.turn())));
    }

    private static String terminalLabel(Context context, HintPreview preview) {
        return switch (preview.evidence().reason()) {
            case CHECKMATE -> context.getString(R.string.hint_compare_mate_score, "+", preview.length());
            case CHECKMATE_AGAINST -> context.getString(R.string.hint_compare_mate_score, "−", preview.length());
            case STALEMATE -> context.getString(R.string.match_draw_stalemate);
            default -> throw new IllegalStateException("A non-terminal preview requires a position estimate");
        };
    }

    private static String pawns(Context context, int centipawns) {
        var number = NumberFormat.getNumberInstance(context.getResources().getConfiguration().getLocales().get(0));
        number.setMinimumFractionDigits(2);
        number.setMaximumFractionDigits(2);
        return (centipawns > 0 ? "+" : "") + number.format(centipawns / 100.0);
    }
}
