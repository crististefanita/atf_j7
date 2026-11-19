package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ReplayCheckpointTest {
    private static final List<String> LINES = List.of("e2e4 e7e5 g1f3", "d2d4 d7d5", "g1f3 d7d5 d2d4 g8f6");

    @Test void rotationRoundTripKeepsTheBranchAndEveryIndependentCursor(Checks check) {
        for (var kind : List.of(ReplayCheckpoint.Kind.CALCULATED, ReplayCheckpoint.Kind.THEORY)) {
            var state = new ReplayCheckpoint("STUDY|2|analysis|e2e4 e7e5|0", kind, "e2e4", LINES, 2, List.of(1, 2, 3), false);
            var restored = ReplayCheckpoint.decode(state.encode()).orElseThrow();
            check.same(kind + " restores every branch after two recreations", state,
                    ReplayCheckpoint.decode(restored.encode()).orElseThrow());
            var original = ChessMatch.restore("2|analysis|");
            String unchanged = original.checkpoint();
            for (int branch = 0; branch < LINES.size(); branch++) {
                var prefix = original.position();
                var moves = LINES.get(branch).split(" ");
                for (int ply = 0; ply < restored.plies().get(branch); ply++) prefix = prefix.play(ChessPosition.Move.fromUci(moves[ply]));
                var legalLine = java.util.Arrays.stream(moves).map(ChessPosition.Move::fromUci).toList();
                var calculated = HintPreview.openDefended(original.position(),
                        new TacticalAnalysis.Variation(legalLine, 0, 0, TacticalAnalysis.Ending.HORIZON)).orElseThrow();
                calculated.seek(restored.plies().get(branch));
                check.same(kind + " branch " + branch + " restores the exact calculated board", true, prefix.sameForRepetition(calculated.position()));
                var text = new OpeningCatalog.Text("Study", "Studiu");
                var path = new MatchHint.Theory("A00", text, text,
                        legalLine.stream().map(move -> new OpeningCatalog.Step(move, text)).toList());
                var theory = new OpeningReplay(original.position(), path);
                theory.seek(restored.plies().get(branch));
                check.same(kind + " branch " + branch + " restores the exact theory board", true, prefix.sameForRepetition(theory.position()));
                check.rejects("Theory rejects a stale cursor beyond its own line", IllegalArgumentException.class, () -> theory.seek(theory.length() + 1));
            }
            check.same(kind + " restoration leaves source history intact", unchanged, original.checkpoint());
        }
    }

    @Test void rejectsChangedSourceOrderedLinesAndReplayMode(Checks check) {
        var state = new ReplayCheckpoint("MATCH|history|6", ReplayCheckpoint.Kind.CALCULATED, "e2e4", LINES, 1, List.of(1, 2, 0), false);
        check.same("An exact recomputed continuation may restore", true, state.matches(state.source(), state.kind(), LINES));
        check.same("Another source cursor cannot borrow the dialog", false, state.matches("MATCH|history|8", state.kind(), LINES));
        check.same("Another mode cannot borrow the dialog", false, state.matches(state.source(), ReplayCheckpoint.Kind.THEORY, LINES));
        check.same("Changed order cannot apply a cursor to another branch", false,
                state.matches(state.source(), state.kind(), List.of(LINES.get(1), LINES.get(0), LINES.get(2))));
        check.same("Changed opposing reply rejects stale state", false,
                state.matches(state.source(), state.kind(), List.of("e2e4 c7c5 g1f3", LINES.get(1), LINES.get(2))));
    }

    @Test void preservesMatingDangerAndInlineHintWithoutOpeningAnotherLine(Checks check) {
        var mate = new ReplayCheckpoint("QUEST|mate-002|2|false", ReplayCheckpoint.Kind.MATING, "", List.of("e2e4 e7e5"), 0, List.of(2), true);
        check.same("Mating cursor and danger survive recreation", mate, ReplayCheckpoint.decode(mate.encode()).orElseThrow());
        var inline = new ReplayCheckpoint("MATCH|source|0", ReplayCheckpoint.Kind.INLINE, "e2e4", List.of("e2e4"), 0, List.of(0), false);
        check.same("An inline hint stays inline", inline, ReplayCheckpoint.decode(inline.encode()).orElseThrow());
        check.same("No saved state means no restoration", true, ReplayCheckpoint.decode(null).isEmpty());
        check.rejects("Inline cannot secretly resume playback", IllegalArgumentException.class,
                () -> new ReplayCheckpoint("s", ReplayCheckpoint.Kind.INLINE, "e2e4", List.of("e2e4"), 0, List.of(1), false));
    }

    @Test void malformedStateCannotOpenOrIndexAnUnrelatedReplay(Checks check) {
        var valid = new ReplayCheckpoint("source", ReplayCheckpoint.Kind.THEORY, "e2e4", LINES, 0, List.of(0, 2, 4), false).encode();
        for (String changed : List.of("", "2" + valid.substring(1), valid.replace("THEORY", "UNKNOWN"),
                valid.replace("0,2,4", "0,2,5"), valid.replace("0,2,4", "-1,2,4"), valid.replace("0,2,4", "0,2"),
                valid.replace("e2e4", "e9e4"), valid.replace("false", "TRUE"), valid.replace("false", "true"))) {
            check.same("Malformed checkpoint rejected: " + changed, true, ReplayCheckpoint.decode(changed).isEmpty());
        }
        var mutable = new ArrayList<>(LINES);
        var state = new ReplayCheckpoint("source", ReplayCheckpoint.Kind.THEORY, "e2e4", mutable, 0, List.of(0, 0, 0), false);
        mutable.clear();
        check.same("The checkpoint owns immutable branch signatures", LINES, state.lines());
        check.rejects("Missing branches are rejected", IllegalArgumentException.class,
                () -> new ReplayCheckpoint("source", ReplayCheckpoint.Kind.THEORY, "e2e4", null, 0, List.of(0), false));
    }
}
