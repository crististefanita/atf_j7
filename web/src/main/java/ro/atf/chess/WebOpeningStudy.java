package ro.atf.chess;

import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.OpeningCatalog.Opening;
import ro.atf.chess.OpeningPractice.Attempt;
import ro.atf.chess.OpeningPractice.Phase;
import static ro.atf.chess.WebBoardState.move;
import static ro.atf.chess.WebBoardState.placement;
import static ro.atf.chess.WebBoardState.quote;

/** Adapts the APK lesson and authored lines to worker messages. */
final class WebOpeningStudy {
    private static final class Catalog {
        static final java.util.List<Opening> ENTRIES = OpeningCatalog.load();
    }

    private record State(OpeningLesson lesson, QuestHint hint, boolean aiEnabled, boolean offLine) {
        State next(OpeningLesson next) { return new State(next, QuestHint.NONE, aiEnabled, false); }

        String saved() {
            var practice = lesson.practice();
            return practice.opening().id() + "|" + practice.learner() + "|" + practice.ply() + "|"
                    + lesson.shownPly() + "|" + (lesson.replyPaused() ? "1" : "0") + "|"
                    + (aiEnabled ? "1" : "0") + "|" + hint + "|" + (offLine ? "1" : "0");
        }
    }

    String command(String action, String value, int index, String saved, String option) {
        boolean romanian = "ro".equals(option);
        if ("CATALOG".equals(action)) return catalog(romanian);
        if ("START".equals(action)) {
            if (index != 0 && index != 1) throw new IllegalArgumentException("Expected a learner side");
            return view(new State(start(opening(value), index == 0 ? Side.WHITE : Side.BLACK), QuestHint.NONE, false, false), romanian);
        }
        var state = restore(saved);
        if ("ANALYZE".equals(action)) return analysis(state);
        var lesson = state.lesson();
        return view(switch (action) {
            case "RESTORE" -> state;
            case "SEEK" -> state.next(lesson.show(index));
            case "BACK" -> state.next(lesson.show(lesson.practice().ply()));
            case "FORWARD" -> state.next(lesson.forward());
            case "REPLAY" -> lesson.reviewing() ? state.next(lesson.replay()) : state;
            case "RESUME" -> lesson.replyPaused() && !lesson.reviewing() ? state.next(lesson.resume()) : state;
            case "RESTART" -> lesson.practice().phase() == Phase.COMPLETE && !lesson.reviewing()
                    ? state.next(start(lesson.practice().opening(), lesson.practice().learner())) : state;
            case "PLAY" -> play(state, value);
            case "REPLY" -> lesson.canAutoReply() ? state.next(lesson.reply()) : state;
            case "THEORY" -> lesson.canPlay()
                    ? new State(lesson, state.hint() == QuestHint.SOLUTION ? QuestHint.NONE : state.hint().advance(), state.aiEnabled(), false)
                    : state;
            case "CLEAR_HINT" -> state.next(lesson);
            case "AI_TOGGLE" -> new State(lesson, state.hint(), !state.aiEnabled(), state.offLine());
            default -> throw new IllegalArgumentException("Unknown opening action");
        }, romanian);
    }

    private static OpeningLesson start(Opening opening, Side learner) {
        return new OpeningLesson(new OpeningPractice(opening, learner, 0));
    }

    private static Opening opening(String id) {
        return Catalog.ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown opening"));
    }

    private static State restore(String saved) {
        if (saved == null || saved.length() > 160) throw new IllegalArgumentException("Invalid opening checkpoint");
        var fields = saved.split("\\|", -1);
        if (fields.length != 8) throw new IllegalArgumentException("Invalid opening checkpoint");
        var practice = new OpeningPractice(opening(fields[0]), Side.valueOf(fields[1]), Integer.parseInt(fields[2]));
        var lesson = new OpeningLesson(practice, Integer.parseInt(fields[3]), flag(fields[4]));
        var hint = QuestHint.valueOf(fields[6]);
        boolean aiEnabled = flag(fields[5]);
        boolean offLine = flag(fields[7]);
        if (!lesson.canPlay() && (hint != QuestHint.NONE || offLine)) throw new IllegalArgumentException("Invalid opening guidance");
        return new State(lesson, hint, aiEnabled, offLine);
    }

    private static boolean flag(String value) {
        return switch (value) {
            case "0" -> false;
            case "1" -> true;
            default -> throw new IllegalArgumentException("Invalid opening flag");
        };
    }

    private static State play(State state, String value) {
        if (!state.lesson().canPlay()) return state;
        var chosen = Move.fromUci(value);
        var attempt = state.lesson().practice().check(chosen);
        if (attempt == Attempt.ON_LINE) return state.next(state.lesson().play(chosen));
        return attempt == Attempt.OFF_LINE ? new State(state.lesson(), state.hint(), state.aiEnabled(), true) : state;
    }

    private static String catalog(boolean romanian) {
        var rows = new StringBuilder("[");
        for (var entry : Catalog.ENTRIES) {
            append(rows, "{\"id\":" + quote(entry.id()) + ",\"eco\":" + quote(entry.eco())
                    + ",\"title\":" + text(entry.title(), romanian) + ",\"variation\":" + text(entry.variation(), romanian)
                    + ",\"plan\":" + text(entry.plan(), romanian) + ",\"source\":" + quote(entry.source().toString()) + "}");
        }
        return "{\"entries\":" + rows.append(']') + "}";
    }

    private static String view(State state, boolean romanian) {
        var lesson = state.lesson();
        var practice = lesson.practice();
        var opening = practice.opening();
        int shown = lesson.shownPly();
        var position = opening.positionAt(shown);
        var previous = shown == 0 ? null : opening.steps().get(shown - 1).move();
        var next = lesson.canPlay() ? practice.next() : null;
        var legal = new StringBuilder("[");
        if (lesson.canPlay()) for (var choice : position.legalMoves()) append(legal, move(choice));
        var history = new StringBuilder("[");
        for (int ply = 1; ply <= practice.ply(); ply++) append(history, step(opening, ply - 1, romanian));
        return "{\"saved\":" + quote(state.saved()) + ",\"id\":" + quote(opening.id()) + ",\"title\":" + text(opening.title(), romanian)
                + ",\"plan\":" + text(opening.plan(), romanian) + ",\"phase\":" + quote(practice.phase().name())
                + ",\"learner\":" + quote(practice.learner().name()) + ",\"sideToMove\":" + quote(position.turn().name())
                + ",\"fen\":" + quote(placement(position)) + ",\"lastMove\":" + move(previous)
                + ",\"ply\":" + shown + ",\"practicePly\":" + practice.ply() + ",\"total\":" + opening.steps().size()
                + ",\"reviewing\":" + lesson.reviewing() + ",\"replyPaused\":" + lesson.replyPaused()
                + ",\"canPlay\":" + lesson.canPlay() + ",\"canAutoReply\":" + lesson.canAutoReply()
                + ",\"aiEnabled\":" + state.aiEnabled() + ",\"offLine\":" + state.offLine() + ",\"hint\":" + quote(state.hint().name())
                + ",\"shownStep\":" + (shown == 0 ? "null" : step(opening, shown - 1, romanian))
                + ",\"nextStep\":" + (next == null ? "null" : step(opening, practice.ply(), romanian))
                + ",\"hintPiece\":" + (next == null ? "null" : quote(position.pieceAt(next.move().from()).kind().name()))
                + ",\"legal\":" + legal.append(']') + ",\"history\":" + history.append(']') + "}";
    }

    private static String step(Opening opening, int index, boolean romanian) {
        var step = opening.steps().get(index);
        return "{\"move\":" + move(step.move()) + ",\"idea\":" + text(step.idea(), romanian) + "}";
    }

    private static String text(OpeningCatalog.Text text, boolean romanian) {
        return quote(romanian ? text.ro() : text.en());
    }

    private static String analysis(State state) {
        if (!state.aiEnabled()) throw new IllegalStateException("Opening analysis is disabled");
        var lesson = state.lesson();
        var position = lesson.practice().opening().positionAt(lesson.shownPly());
        var hint = MatchHint.analyze(position);
        var evaluation = hint.evaluation(position);
        if (evaluation == null) return "{\"evaluation\":null,\"variants\":[]}";
        var lines = new StringBuilder("[");
        for (int index = 0; index < hint.count(); index++) {
            hint.revealNext();
            var revealed = hint.revealed();
            var available = hint.playback(position).orElse(null);
            var verified = available == null ? revealed : available.lines().stream().filter(line -> line.move().equals(revealed.move()))
                    .findFirst().orElse(revealed);
            var replay = HintPreview.openDefended(position, verified).orElseThrow();
            append(lines, "{\"move\":" + move(revealed.move()) + ",\"frames\":" + frames(replay) + ",\"depth\":"
                    + (available == null ? 0 : available.depth()) + ",\"proof\":" + proof(replay)
                    + ",\"previewMoves\":" + previewMoves(available, revealed.move())
                    + ",\"decisive\":" + decisive(position, available, verified) + "}");
        }
        return "{\"evaluation\":{\"ending\":" + quote(evaluation.ending().name())
                + ",\"whiteCentipawns\":" + evaluation.whiteCentipawns() + ",\"whiteMateMoves\":" + evaluation.whiteMateMoves()
                + ",\"whiteShare\":" + evaluation.whiteShare() + "},\"variants\":" + lines.append(']') + "}";
    }

    private static String previewMoves(MatchHint.Playback playback, Move fallback) {
        if (playback == null) return "[" + move(fallback) + "]";
        var moves = new StringBuilder("[");
        for (var line : playback.lines()) append(moves, move(line.move()));
        return moves.append(']').toString();
    }

    private static String decisive(ChessPosition position, MatchHint.Playback playback, TacticalAnalysis.Variation selected) {
        if (playback == null) return "null";
        int branch = playback.lines().indexOf(selected);
        if (branch < 0) return "null";
        var proof = playback.comparison(position, branch).flatMap(HintComparison::decisive).orElse(null);
        if (proof == null) return "null";
        int target = proof.inStronger() ? 0 : Math.max(1, branch);
        var line = playback.lines().get(target);
        var proofReplay = HintPreview.openDefended(position, line).orElseThrow();
        if (proofReplay.addedDefense() || proof.ply() < 1 || proof.ply() > proofReplay.length()) return "null";
        proofReplay.seek(proof.ply());
        if (!proof.move().equals(proofReplay.lastMove())) return "null";
        return "{\"branch\":" + move(line.move()) + ",\"ply\":" + proof.ply()
                + ",\"move\":" + move(proof.move()) + ",\"piece\":" + quote(proof.piece().name())
                + ",\"reason\":" + quote(proof.reason().name()) + "}";
    }

    private static String proof(HintPreview replay) {
        var proof = replay.evidence();
        var estimate = replay.estimate().orElse(null);
        return "{\"reason\":" + quote(proof.reason().name()) + ",\"move\":" + move(proof.move())
                + ",\"piece\":" + (proof.piece() == null ? "null" : quote(proof.piece().name()))
                + ",\"captured\":" + (proof.captured() == null ? "null" : quote(proof.captured().name()))
                + ",\"netMaterial\":" + proof.netMaterial() + ",\"addedDefense\":" + replay.addedDefense()
                + ",\"estimate\":" + (estimate == null ? "null" : "{\"initial\":" + estimate.initialCp()
                + ",\"end\":" + estimate.finalCp() + ",\"change\":" + estimate.changeCp() + "}") + "}";
    }

    private static String frames(HintPreview replay) {
        var frames = new StringBuilder("[");
        append(frames, frame(replay.position(), null));
        while (replay.hasNext()) {
            replay.next();
            append(frames, frame(replay.position(), replay.lastMove()));
        }
        return frames.append(']').toString();
    }

    private static String frame(ChessPosition position, Move last) {
        return "{\"fen\":" + quote(placement(position)) + ",\"sideToMove\":" + quote(position.turn().name())
                + ",\"lastMove\":" + move(last) + "}";
    }

    private static void append(StringBuilder array, String value) {
        if (array.length() > 1) array.append(',');
        array.append(value);
    }
}
