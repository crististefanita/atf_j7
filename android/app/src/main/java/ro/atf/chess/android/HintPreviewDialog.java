package ro.atf.chess.android;

import android.app.Dialog;
import android.content.Context;
import android.widget.Button;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import ro.atf.chess.ChessPosition;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.HintComparison;
import ro.atf.chess.HintPreview;
import ro.atf.chess.MatchHint;
import ro.atf.chess.OpeningReplay;
import ro.atf.chess.OpeningCatalog.Text;
import ro.atf.chess.QuestHint;
import ro.atf.chess.ReplayCheckpoint;

/** Plays the suggested branch on its own board and returns to the original cursor. */
final class HintPreviewDialog {
    private static final int[] BRANCH_IDS = {R.id.hint_compare_best, R.id.hint_compare_other, R.id.hint_compare_third};

    private HintPreviewDialog() { }

    record Session(Dialog dialog, Supplier<ReplayCheckpoint> checkpoint) { }

    static Session show(Context context, ChessPosition root, MatchHint.Playback study,
                       boolean blackBelow, Function<Move, String> notation, int sourceBoardSide,
                       String source, String suggestion, int offer, ReplayCheckpoint restored) {
        var workspace = new AnalysisReplayDialog(context, AnalysisReplayDialog.Mode.CALCULATED, blackBelow,
                study.lines().size() > 1, null, () -> { }, sourceBoardSide);
        workspace.setSummary(study.depth() > 0 ? context.getString(R.string.hint_compare_depth, study.depth()) : null);
        var branches = branches(context, root, study, workspace, notation);
        var playback = new Playback(context, root, workspace, notation, branches);
        int selected = 0;
        for (int index = 0; index < branches.size(); index++) {
            if (study.lines().get(index).move().equals(study.selected())) selected = index;
            int branchIndex = index;
            branches.get(index).button().setOnClickListener(view -> playback.select(branchIndex));
        }
        var lines = study.lines().stream().map(line -> moves(line.line())).collect(Collectors.toList());
        if (restored != null && !restored.matches(source, ReplayCheckpoint.Kind.CALCULATED, lines)) return null;
        if (restored != null && restored.matches(source, ReplayCheckpoint.Kind.CALCULATED, lines)) {
            for (int index = 0; index < branches.size(); index++) branches.get(index).replay().seek(restored.plies().get(index));
            selected = restored.branch();
        }
        playback.select(selected);
        return new Session(workspace.show(), () -> new ReplayCheckpoint(source, ReplayCheckpoint.Kind.CALCULATED, suggestion,
                lines, playback.active, branches.stream().map(branch -> branch.replay().step()).collect(Collectors.toList()), false, offer));
    }

    static Session showTheory(Context context, ChessPosition root, MatchHint.TheoryPlayback study,
                             boolean blackBelow, Function<Move, String> notation, int sourceBoardSide,
                             String source, String suggestion, int offer, ReplayCheckpoint restored) {
        var workspace = new AnalysisReplayDialog(context, AnalysisReplayDialog.Mode.THEORY, blackBelow,
                study.paths().size() > 1, null, () -> { }, sourceBoardSide);
        var paths = study.paths().stream().map(path -> new OpeningReplay(root, path)).collect(Collectors.toList());
        var buttons = new ArrayList<Button>(paths.size());
        var controls = new TheoryControls(context, workspace, study, paths, buttons, notation);
        for (int index = 0; index < paths.size(); index++) {
            String label = context.getString(R.string.hint_theory_path, index + 1);
            int active = index;
            buttons.add(workspace.addBranch(BRANCH_IDS[index], label, label + " · " + theoryTitle(context, study.paths().get(index)),
                    () -> controls.select(active)));
        }
        var lines = study.paths().stream().map(path -> moves(path.steps().stream().map(step -> step.move()).collect(Collectors.toList())))
                .collect(Collectors.toList());
        if (restored != null && !restored.matches(source, ReplayCheckpoint.Kind.THEORY, lines)) return null;
        int selected = study.selected();
        if (restored != null && restored.matches(source, ReplayCheckpoint.Kind.THEORY, lines)) {
            for (int index = 0; index < paths.size(); index++) paths.get(index).seek(restored.plies().get(index));
            selected = restored.branch();
        }
        controls.select(selected);
        return new Session(workspace.show(), () -> new ReplayCheckpoint(source, ReplayCheckpoint.Kind.THEORY, suggestion,
                lines, controls.active, paths.stream().map(OpeningReplay::ply).collect(Collectors.toList()), false, offer));
    }

    private static String moves(List<Move> moves) { return moves.stream().map(Move::uci).collect(Collectors.joining(" ")); }

    static String theoryTitle(Context context, MatchHint.Theory theory) {
        var locale = context.getResources().getConfiguration().getLocales().get(0);
        String name = theory.name().in(locale);
        String source = theory.idea() == null ? name : theory.family().in(locale) + " · " + name;
        return context.getString(R.string.hint_theory_source, source, theory.eco());
    }

    private static List<Branch> branches(Context context, ChessPosition root, MatchHint.Playback study,
                                         AnalysisReplayDialog workspace, Function<Move, String> notation) {
        var branches = new ArrayList<Branch>(study.lines().size());
        for (int index = 0; index < study.lines().size(); index++) {
            var line = study.lines().get(index);
            var preview = HintPreview.openDefended(root, line).orElseThrow();
            String evaluation = preview.estimate().map(estimate -> pawns(context, estimate.finalCp()))
                    .orElseGet(() -> terminalLabel(context, preview));
            String title = context.getString(R.string.hint_compare_choice, index + 1, ChessActivity.compactNotation(line.move()));
            int spoken = index == 0 ? R.string.hint_compare_recommended_spoken : R.string.hint_compare_choice_spoken;
            String description = context.getString(spoken, index + 1, notation.apply(line.move()), evaluation);
            var button = workspace.addBranch(BRANCH_IDS[index], title, description, () -> { });
            String score = preview.estimate().map(estimate -> context.getString(R.string.hint_preview_change,
                    pawns(context, estimate.initialCp()), evaluation, pawns(context, estimate.changeCp())))
                    .orElse(evaluation);
            String scoreDescription = preview.estimate().map(estimate -> context.getString(R.string.hint_preview_change_spoken,
                    pawns(context, estimate.initialCp()), evaluation, pawns(context, estimate.changeCp())))
                    .orElse(evaluation);
            branches.add(new Branch(preview, button, score, scoreDescription, study.comparison(root, index)));
        }
        return branches;
    }

    private record Branch(HintPreview replay, Button button, String score, String scoreDescription,
                          Optional<HintComparison> comparison) { }

    private static final class Playback {
        private final Context context;
        private final ChessPosition root;
        private final AnalysisReplayDialog workspace;
        private final Function<Move, String> notation;
        private final List<Branch> branches;
        private HintPreview preview;
        private int active;

        Playback(Context context, ChessPosition root, AnalysisReplayDialog workspace,
                 Function<Move, String> notation, List<Branch> branches) {
            this.context = context;
            this.root = root;
            this.workspace = workspace;
            this.notation = notation;
            this.branches = List.copyOf(branches);
            workspace.setNavigation(() -> { preview.previous(); render(); }, () -> { preview.next(); render(); });
        }

        void select(int index) {
            active = index;
            for (int branch = 0; branch < branches.size(); branch++) {
                branches.get(branch).button().setSelected(branch == index);
            }
            preview = branches.get(active).replay();
            render();
        }

        void seek(int branch, int ply) {
            branches.get(branch).replay().seek(ply);
            select(branch);
        }

        void render() {
            var branch = branches.get(active);
            var position = preview.position();
            Move last = preview.lastMove();
            String move = last == null ? context.getString(R.string.hint_preview_start)
                    : preview.addedDefense() && !preview.hasNext() ? context.getString(R.string.hint_preview_checked_reply, notation.apply(last))
                    : played(context, position, last, notation);
            var cue = preview.step() == 0 ? QuestHint.SOLUTION.cue(preview.firstMove()) : new QuestHint.Cue(-1, -1);
            String step = context.getString(R.string.hint_preview_step, preview.step(), preview.length(), move);
            var proof = branch.comparison().flatMap(HintComparison::decisive)
                    .filter(candidate -> !branches.get(proofBranch(candidate)).replay().addedDefense());
            String explanation = proof.map(candidate -> decisive(context, candidate, proofBranch(candidate) + 1, notation))
                    .orElseGet(() -> reason(context, root, preview.evidence(), notation));
            workspace.render(new AnalysisReplayDialog.Frame(position, last, cue, step, preview.hasPrevious(), preview.hasNext(),
                    branch.score(), explanation));
            workspace.setCaptionDescription(branch.scoreDescription());
            proof.ifPresent(candidate -> workspace.setReasonAction(
                    explanation + " " + context.getString(R.string.hint_compare_jump),
                    () -> seek(proofBranch(candidate), candidate.ply())));
        }

        private int proofBranch(HintComparison.DecisiveMove proof) {
            return proof.inStronger() ? 0 : active == 0 ? 1 : active;
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
