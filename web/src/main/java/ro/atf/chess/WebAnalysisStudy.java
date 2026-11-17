package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.OpeningCatalog.Text;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

/** An independent manual study using the same line, evaluation and hint cursors as Android. */
final class WebAnalysisStudy {
    private AnalysisLine entry;
    private AnalysisLine study;
    private boolean blackBelow;
    private boolean analysis = true;
    private int revision;
    private int selected = -1;
    private List<Move> options = List.of();
    private List<Move> promotions = List.of();
    private MatchHint hint;
    private MatchEvaluation evaluation;
    private List<Replay> previews = List.of();
    private int branch;

    String command(String action, String value, int index, String saved, String option) {
        if ("resume".equals(action) || entry == null && !"open".equals(action)) resume(saved);
        String continuation = null;
        switch (action) {
            case "resume" -> { }
            case "open" -> open(empty(value) ? new AnalysisEntry(ChessMatch.start(), index, "black".equals(option))
                    : AnalysisEntry.restore(value, index, "black".equals(option)));
            case "seek" -> select(study.seek(index));
            case "reset" -> select(entry);
            case "flip" -> blackBelow = !blackBelow;
            case "analysis" -> {
                boolean enabled = "on".equals(option);
                if (analysis != enabled) {
                    analysis = enabled;
                    invalidateAnalysis();
                }
            }
            case "tap" -> tap(BoardSquare.parse(value));
            case "promote" -> {
                if (previews.isEmpty() && study.result() == Result.IN_PROGRESS) {
                    promotions.stream().filter(candidate -> value.equals(candidate.uci())).findFirst()
                            .ifPresent(candidate -> select(study.play(candidate)));
                }
            }
            case "cancel-promotion" -> promotions = List.of();
            case "evaluate" -> { if (index == revision) evaluate(); }
            case "hint" -> {
                if (previews.isEmpty()) {
                    evaluate();
                    if (hint != null) hint.revealNext();
                }
            }
            case "preview" -> preview();
            case "preview-close" -> previews = List.of();
            case "preview-branch" -> { if (index >= 0 && index < previews.size()) branch = index; }
            case "preview-seek" -> {
                if (!previews.isEmpty() && index >= 0 && index <= previews.get(branch).length()) previews.get(branch).seek(index);
            }
            case "preview-proof" -> {
                if (!previews.isEmpty() && previews.get(branch) instanceof TacticalReplay line && line.proof() != null) {
                    branch = line.proof().branch();
                    previews.get(branch).seek(line.proof().move().ply());
                }
            }
            case "continue" -> {
                if (previews.isEmpty() && study.result() == Result.IN_PROGRESS) continuation = study.resumeMatch(analysis).checkpoint();
            }
            default -> throw new IllegalArgumentException("Unknown analysis action");
        }
        return state(continuation);
    }

    private void resume(String saved) {
        if (!empty(saved)) {
            String[] values = saved.split("\n", -1);
            if (values.length == 6 && (values[4].equals("true") || values[4].equals("false"))
                    && (values[5].equals("true") || values[5].equals("false"))) {
                try {
                    var restoredEntry = new AnalysisLine(ChessMatch.restore(values[0]), Integer.parseInt(values[1]));
                    var restored = new AnalysisLine(ChessMatch.restore(values[2]), Integer.parseInt(values[3]));
                    if (restored.line().positionAt(0).sameForRepetition(restoredEntry.line().positionAt(0))
                            && restored.line().setup().equals(restoredEntry.line().setup())) {
                        entry = restoredEntry;
                        blackBelow = Boolean.parseBoolean(values[4]);
                        analysis = Boolean.parseBoolean(values[5]);
                        select(restored);
                        return;
                    }
                } catch (IllegalArgumentException | IllegalStateException invalid) {
                    // A damaged browser bookmark must not block a fresh study.
                }
            }
        }
        open(new AnalysisEntry(ChessMatch.start(), 0, false));
    }

    private void open(AnalysisEntry initial) {
        entry = initial.line();
        blackBelow = initial.blackBelow();
        analysis = true;
        select(entry);
    }

    private void select(AnalysisLine line) {
        if (study == null || study.position() != line.position() || study.result() != line.result()) invalidateAnalysis();
        study = line;
        selected = -1;
        options = List.of();
        promotions = List.of();
        previews = List.of();
    }

    private void invalidateAnalysis() {
        hint = null;
        evaluation = null;
        previews = List.of();
        revision++;
    }

    private void tap(int square) {
        if (!previews.isEmpty() || !promotions.isEmpty() || study.result() != Result.IN_PROGRESS) return;
        var position = study.position();
        var guided = analysis && hint != null ? hint.moveForTap(position, selected, square) : null;
        if (guided != null) {
            select(study.play(guided));
            return;
        }
        var targets = options.stream().filter(candidate -> candidate.to() == square).collect(Collectors.toList());
        if (targets.size() == 1) {
            select(study.play(targets.get(0)));
        } else if (targets.size() > 1) {
            promotions = targets;
        } else {
            var piece = position.pieceAt(square);
            selected = selected != square && piece != null && piece.side() == position.turn() ? square : -1;
            options = position.legalMovesFrom(selected);
        }
    }

    private void evaluate() {
        if (!analysis || hint != null || study.result() != Result.IN_PROGRESS) return;
        hint = study.hint();
        evaluation = hint.evaluation(study.position());
    }

    private void preview() {
        if (hint == null || !previews.isEmpty()) return;
        var root = study.position();
        var theory = hint.theoryPlayback(root);
        if (theory.isPresent()) {
            var paths = theory.get();
            previews = paths.paths().stream().<Replay>map(path -> new TheoryReplay(path, new OpeningReplay(root, path)))
                    .collect(Collectors.toList());
            branch = paths.selected();
            return;
        }
        hint.playback(root).ifPresent(playback -> {
            var replays = new ArrayList<HintPreview>();
            var lines = playback.lines();
            branch = 0;
            for (int index = 0; index < lines.size(); index++) {
                var line = lines.get(index);
                replays.add(HintPreview.openDefended(root, line).orElseThrow());
                if (line.move().equals(playback.selected())) branch = index;
            }
            var available = new ArrayList<Replay>();
            for (int index = 0; index < lines.size(); index++) {
                var proof = playback.comparison(root, index).flatMap(HintComparison::decisive).orElse(null);
                int decisiveBranch = proof == null || proof.inStronger() ? 0 : Math.max(1, index);
                var decisive = proof != null && !replays.get(decisiveBranch).addedDefense() ? new Proof(decisiveBranch, proof) : null;
                available.add(new TacticalReplay(replays.get(index), playback.depth(), decisive));
            }
            previews = List.copyOf(available);
        });
    }

    private String state(String continuation) {
        var position = study.position();
        var past = new ArrayList<Move>();
        for (int cursor = 1; cursor <= study.line().ply(); cursor++) past.add(study.line().moveAt(cursor));
        var targets = options.stream().map(candidate -> BoardSquare.name(candidate.to())).distinct().collect(Collectors.toList());
        var suggestion = hint == null ? null : hint.revealedMove();
        return "{" + frame(position, study.lastMove())
                + ",\"revision\":" + revision + ",\"ply\":" + study.ply() + ",\"total\":" + study.line().ply()
                + ",\"result\":" + quote(study.result().name()) + ",\"blackBelow\":" + blackBelow
                + ",\"selected\":" + quote(selected < 0 ? null : BoardSquare.name(selected)) + ",\"targets\":" + json(targets, WebBoardState::quote)
                + ",\"marks\":" + marks(suggestion) + ",\"history\":" + json(past, WebBoardState::move)
                + ",\"promotions\":" + json(promotions, WebBoardState::move) + ",\"analysis\":" + analysis
                + ",\"evaluation\":" + evaluationState() + ",\"hint\":" + hintState() + ",\"preview\":" + previewState()
                + ",\"checkpoint\":" + quote(study.line().checkpoint()) + ",\"continuation\":" + quote(continuation)
                + ",\"saved\":" + quote(bookmark()) + "}";
    }

    private String bookmark() {
        return String.join("\n", entry.line().checkpoint(), String.valueOf(entry.ply()), study.line().checkpoint(),
                String.valueOf(study.ply()), String.valueOf(blackBelow), String.valueOf(analysis));
    }

    private String evaluationState() {
        if (!analysis) return "null";
        if (study.result() != Result.IN_PROGRESS) {
            return "{\"ending\":\"FINISHED\",\"whiteShare\":" + MatchEvaluation.terminalWhiteShare(study.result()) + "}";
        }
        return evaluation == null ? "null" : "{\"ending\":" + quote(evaluation.ending().name())
                + ",\"whiteShare\":" + evaluation.whiteShare() + ",\"centipawns\":" + evaluation.whiteCentipawns()
                + ",\"mate\":" + evaluation.whiteMateMoves() + "}";
    }

    private String hintState() {
        if (hint == null) return "null";
        var theory = hint.revealedTheory();
        return "{\"count\":" + hint.count() + ",\"number\":" + hint.number() + ",\"move\":" + move(hint.revealedMove())
                + ",\"family\":" + text(theory == null ? null : theory.family()) + "}";
    }

    private String previewState() {
        if (previews.isEmpty()) return "null";
        var preview = previews.get(branch);
        return "{" + frame(preview.position(), preview.last()) + ",\"ply\":" + preview.ply() + ",\"total\":" + preview.length()
                + ",\"branch\":" + branch + ",\"branches\":" + json(previews, item -> move(item.first()))
                + ",\"marks\":" + marks(preview.ply() == 0 ? preview.first() : null) + preview.evidence() + "}";
    }

    private static String frame(ChessPosition position, Move last) {
        String squares = placement(position).replace("/", "");
        for (int count = 1; count <= 8; count++) squares = squares.replace(String.valueOf(count), " ".repeat(count));
        return "\"board\":" + quote(squares) + ",\"sideToMove\":" + quote(position.turn().name()) + ",\"lastMove\":" + move(last);
    }

    private static String marks(Move suggested) {
        return suggested == null ? "[]" : "[" + quote(BoardSquare.name(suggested.from())) + "," + quote(BoardSquare.name(suggested.to())) + "]";
    }

    private static String text(Text label) {
        return label == null ? "null" : "{\"en\":" + quote(label.en()) + ",\"ro\":" + quote(label.ro()) + "}";
    }

    private static <T> String json(List<T> values, Function<T, String> encode) {
        return values.stream().map(encode).collect(Collectors.joining(",", "[", "]"));
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }

    private interface Replay {
        ChessPosition position();
        int ply();
        int length();
        Move first();
        Move last();
        void seek(int ply);
        String evidence();
    }

    private record TheoryReplay(MatchHint.Theory path, OpeningReplay replay) implements Replay {
        @Override public ChessPosition position() { return replay.position(); }
        @Override public int ply() { return replay.ply(); }
        @Override public int length() { return replay.length(); }
        @Override public Move first() { return path.move(); }
        @Override public Move last() { return replay.lastMove(); }
        @Override public void seek(int ply) {
            while (replay.ply() < ply) replay.next();
            while (replay.ply() > ply) replay.previous();
        }
        @Override public String evidence() {
            return ",\"kind\":\"THEORY\",\"eco\":" + quote(path.eco()) + ",\"name\":" + text(path.name())
                    + ",\"idea\":" + text(replay.explainedStep().idea());
        }
    }

    private record Proof(int branch, HintComparison.DecisiveMove move) {
        String json() {
            return "{\"branch\":" + branch + ",\"ply\":" + move.ply() + ",\"move\":" + WebBoardState.move(move.move())
                    + ",\"piece\":" + quote(move.piece().name()) + ",\"reason\":" + quote(move.reason().name()) + "}";
        }
    }

    private record TacticalReplay(HintPreview replay, int depth, Proof proof) implements Replay {
        @Override public ChessPosition position() { return replay.position(); }
        @Override public int ply() { return replay.step(); }
        @Override public int length() { return replay.length(); }
        @Override public Move first() { return replay.firstMove(); }
        @Override public Move last() { return replay.lastMove(); }
        @Override public void seek(int ply) { replay.seek(ply); }
        @Override public String evidence() {
            var evidence = replay.evidence();
            var estimate = replay.estimate().orElse(null);
            return ",\"kind\":\"TACTICAL\",\"proof\":" + quote(evidence.reason().name()) + ",\"proofMove\":" + move(evidence.move())
                    + ",\"piece\":" + quote(evidence.piece() == null ? null : evidence.piece().name())
                    + ",\"captured\":" + quote(evidence.captured() == null ? null : evidence.captured().name())
                    + ",\"gain\":" + evidence.netMaterial() + ",\"initialCp\":" + (estimate == null ? "null" : estimate.initialCp())
                    + ",\"finalCp\":" + (estimate == null ? "null" : estimate.finalCp())
                    + ",\"depth\":" + depth + ",\"decisive\":" + (proof == null ? "null" : proof.json());
        }
    }
}
