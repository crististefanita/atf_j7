package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import ro.atf.chess.ChessMatch.Result;
import ro.atf.chess.ChessPosition.Move;
import static ro.atf.chess.WebBoardState.frame;
import static ro.atf.chess.WebBoardState.json;
import static ro.atf.chess.WebBoardState.marks;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.quote;
import static ro.atf.chess.WebBoardState.text;

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
    private final WebHintReplay replay = new WebHintReplay();

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
                if (!replay.isOpen() && study.result() == Result.IN_PROGRESS) {
                    promotions.stream().filter(candidate -> value.equals(candidate.uci())).findFirst()
                            .ifPresent(candidate -> select(study.play(candidate)));
                }
            }
            case "cancel-promotion" -> promotions = List.of();
            case "evaluate" -> { if (index == revision) evaluate(); }
            case "hint" -> {
                if (!replay.isOpen()) {
                    evaluate();
                    if (hint != null) hint.revealNext();
                }
            }
            case "preview" -> replay.open(study.position(), hint);
            case "preview-close" -> replay.close();
            case "preview-branch" -> replay.select(index);
            case "preview-seek" -> replay.seek(index);
            case "preview-proof" -> replay.prove();
            case "continue" -> {
                if (!replay.isOpen() && study.result() == Result.IN_PROGRESS) continuation = study.resumeMatch(analysis).checkpoint();
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
        replay.close();
    }

    private void invalidateAnalysis() {
        hint = null;
        evaluation = null;
        replay.close();
        revision++;
    }

    private void tap(int square) {
        if (replay.isOpen() || !promotions.isEmpty() || study.result() != Result.IN_PROGRESS) return;
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
                + ",\"evaluation\":" + evaluationState() + ",\"hint\":" + hintState() + ",\"preview\":" + replay.state()
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
            return WebBoardState.terminalEvaluation(study.result());
        }
        return WebBoardState.evaluation(evaluation);
    }

    private String hintState() {
        if (hint == null) return "null";
        var theory = hint.revealedTheory();
        return "{\"count\":" + hint.count() + ",\"number\":" + hint.number() + ",\"move\":" + move(hint.revealedMove())
                + ",\"family\":" + text(theory == null ? null : theory.family()) + "}";
    }

    private static boolean empty(String value) { return value == null || value.isEmpty(); }
}
