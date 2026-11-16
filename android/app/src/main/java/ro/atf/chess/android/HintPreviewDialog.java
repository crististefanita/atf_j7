package ro.atf.chess.android;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
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
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.HintComparison;
import ro.atf.chess.HintPreview;
import ro.atf.chess.MatchHint;
import ro.atf.chess.OpeningReplay;
import ro.atf.chess.OpeningCatalog.Text;
import ro.atf.chess.QuestHint;

/** Plays the suggested branch on its own board and returns to the original cursor. */
final class HintPreviewDialog {
    private static final int[] BRANCH_IDS = {R.id.hint_compare_best, R.id.hint_compare_other, R.id.hint_compare_third};

    private HintPreviewDialog() { }

    static AlertDialog show(Context context, ChessPosition root, MatchHint.Playback study,
                            boolean blackBelow, Function<Move, String> notation) {
        String summary = study.depth() > 0 ? context.getString(R.string.hint_compare_depth, study.depth()) : null;
        var workspace = workspace(context, R.string.hint_preview_title, summary, study.lines().size() > 1);
        var choices = workspace.choices();
        var details = workspace.details();
        var branches = branches(context, root, study, choices, details, notation);
        var playback = new Playback(workspace, blackBelow, notation, branches);
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
        return open(workspace.dialog());
    }

    static Dialog showTheory(Context context, ChessPosition root, MatchHint.TheoryPlayback study,
                             boolean blackBelow, Function<Move, String> notation) {
        var workspace = new AnalysisReplayDialog(context, AnalysisReplayDialog.Mode.THEORY, blackBelow,
                study.paths().size() > 1, null, () -> { });
        var paths = study.paths().stream().map(path -> new OpeningReplay(root, path)).collect(Collectors.toList());
        var buttons = new ArrayList<Button>(paths.size());
        var controls = new TheoryControls(context, workspace, study, paths, buttons, notation);
        for (int index = 0; index < paths.size(); index++) {
            String label = context.getString(R.string.hint_theory_path, index + 1);
            int active = index;
            buttons.add(workspace.addBranch(BRANCH_IDS[index], label, label + " · " + theoryTitle(context, study.paths().get(index)),
                    () -> controls.select(active)));
        }
        controls.select(study.selected());
        return workspace.show();
    }

    static String theoryTitle(Context context, MatchHint.Theory theory) {
        var locale = context.getResources().getConfiguration().getLocales().get(0);
        String name = theory.name().in(locale);
        String source = theory.idea() == null ? name : theory.family().in(locale) + " · " + name;
        return context.getString(R.string.hint_theory_source, source, theory.eco());
    }

    private static AlertDialog open(AlertDialog dialog) {
        dialog.show();
        Button close = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        close.setId(R.id.hint_preview_return);
        close.setAllCaps(false);
        return dialog;
    }

    private static Workspace workspace(Context context, int dialogTitle, String summaryText, boolean showChoices) {
        var body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 16);
        body.setPadding(padding, 0, padding, 0);
        if (summaryText != null) {
            var summary = new TextView(context);
            summary.setId(R.id.hint_compare_summary);
            summary.setText(summaryText);
            body.addView(summary);
        }
        var choices = new LinearLayout(context);
        choices.setBaselineAligned(false);
        var choicesLayout = new LinearLayout.LayoutParams(-1, -2);
        choicesLayout.setMargins(0, dp(context, 6), 0, dp(context, 6));
        if (showChoices) body.addView(choices, choicesLayout);
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
        var dialog = new AlertDialog.Builder(context).setTitle(dialogTitle)
                .setView(scroll).setPositiveButton(R.string.hint_preview_return, null).create();
        return new Workspace(dialog, choices, board, step, back, forward, details);
    }

    private static List<Branch> branches(Context context, ChessPosition root, MatchHint.Playback study,
                                         LinearLayout choices, FrameLayout details, Function<Move, String> notation) {
        var branches = new ArrayList<Branch>(study.lines().size());
        for (int index = 0; index < study.lines().size(); index++) {
            var line = study.lines().get(index);
            var preview = HintPreview.openDefended(root, line).orElseThrow();
            String evaluation = preview.estimate().map(estimate -> pawns(context, estimate.finalCp()))
                    .orElseGet(() -> terminalLabel(context, preview));
            var button = AnalysisReplayDialog.branchButton(context);
            button.setId(BRANCH_IDS[index]);
            button.setText(context.getString(R.string.hint_compare_choice, index + 1, ChessActivity.compactNotation(line.move())));
            int spoken = index == 0 ? R.string.hint_compare_recommended_spoken : R.string.hint_compare_choice_spoken;
            button.setContentDescription(context.getString(spoken, index + 1, notation.apply(line.move()), evaluation));
            addBranchButton(context, choices, button, index + 1 < study.lines().size());

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

    private static void addBranchButton(Context context, LinearLayout choices, Button button, boolean more) {
        var layout = new LinearLayout.LayoutParams(0, -1, 1);
        if (more) layout.setMarginEnd(dp(context, 6));
        choices.addView(button, layout);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private record Branch(HintPreview replay, Button button, LinearLayout details, TextView score, TextView reason) { }

    private record Workspace(AlertDialog dialog, LinearLayout choices, BoardView board, TextView step, ImageButton back,
                             ImageButton forward, FrameLayout details) {
        void render(ChessPosition position, boolean blackBelow, Move first, Move last, int ply, int length,
                    String moveDescription) {
            var cue = ply == 0 ? QuestHint.SOLUTION.cue(first) : new QuestHint.Cue(-1, -1);
            board.show(position, blackBelow, -1, List.of(), last, cue, false, step.getId());
            step.setText(board.getContext().getString(R.string.hint_preview_step, ply, length, moveDescription));
            back.setEnabled(ply > 0);
            forward.setEnabled(ply < length);
        }
    }

    private static final class Playback {
        private final Workspace workspace;
        private final boolean blackBelow;
        private final Function<Move, String> notation;
        private final List<Branch> branches;
        private HintPreview preview;

        Playback(Workspace workspace, boolean blackBelow, Function<Move, String> notation, List<Branch> branches) {
            this.workspace = workspace;
            this.blackBelow = blackBelow;
            this.notation = notation;
            this.branches = List.copyOf(branches);
            workspace.back().setOnClickListener(view -> { preview.previous(); render(); });
            workspace.forward().setOnClickListener(view -> { preview.next(); render(); });
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
            var context = workspace.board().getContext();
            var position = preview.position();
            Move last = preview.lastMove();
            String move = last == null ? context.getString(R.string.hint_preview_start)
                    : preview.addedDefense() && !preview.hasNext() ? context.getString(R.string.hint_preview_checked_reply, notation.apply(last))
                    : played(context, position, last, notation);
            workspace.render(position, blackBelow, preview.firstMove(), last, preview.step(), preview.length(), move);
        }
    }

    private static final class TheoryControls {
        private final Context context;
        private final AnalysisReplayDialog workspace;
        private final MatchHint.TheoryPlayback study;
        private final List<OpeningReplay> paths;
        private final List<Button> buttons;
        private final Function<Move, String> notation;
        private int active;

        TheoryControls(Context context, AnalysisReplayDialog workspace, MatchHint.TheoryPlayback study,
                       List<OpeningReplay> paths, List<Button> buttons, Function<Move, String> notation) {
            this.context = context;
            this.workspace = workspace;
            this.study = study;
            this.paths = paths;
            this.buttons = buttons;
            this.notation = notation;
            workspace.setNavigation(() -> { paths.get(active).previous(); render(); },
                    () -> { paths.get(active).next(); render(); });
        }

        void select(int index) {
            active = index;
            for (int button = 0; button < buttons.size(); button++) buttons.get(button).setSelected(button == index);
            render();
        }

        private void render() {
            var path = study.paths().get(active);
            var replay = paths.get(active);
            var position = replay.position();
            Move last = replay.lastMove();
            String move = last == null ? notation.apply(path.move()) : played(context, position, last, notation);
            String step = context.getString(R.string.hint_preview_step, replay.ply(), replay.length(), move);
            var cue = replay.ply() == 0 ? QuestHint.SOLUTION.cue(path.move()) : new QuestHint.Cue(-1, -1);
            Text explanation = replay.explainedStep().idea();
            String detail = explanation == null ? context.getString(R.string.hint_theory_index_detail)
                    : explanation.in(context.getResources().getConfiguration().getLocales().get(0));
            workspace.render(new AnalysisReplayDialog.Frame(position, last, cue, step, replay.hasPrevious(), replay.hasNext(),
                    theoryTitle(context, path), detail));
        }
    }

    private static String played(Context context, ChessPosition position, Move move, Function<Move, String> notation) {
        return context.getString(R.string.hint_preview_played,
                context.getString(BoardView.sideLabel(position.turn().other())), notation.apply(move));
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
