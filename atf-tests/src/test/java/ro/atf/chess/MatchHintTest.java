package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MatchHintTest {
    @Test void playsOnlyTheRevealedLegalDestinationWithoutReplacingAnExplicitSelection(Checks check) {
        var position = ChessPosition.fromFen("7k/P7/8/8/8/8/8/7K w - - 0 1");
        var knight = ChessPosition.Move.fromUci("a7a8n");
        var queen = ChessPosition.Move.fromUci("a7a8q");
        var hint = new MatchHint(List.of(new TacticalAnalysis.Variation(List.of(knight), 0, 0, TacticalAnalysis.Ending.HORIZON),
                new TacticalAnalysis.Variation(List.of(queen), 0, 0, TacticalAnalysis.Ending.HORIZON)));
        check.same("A hidden recommendation cannot play a move", null, hint.moveForTap(position, -1, knight.to()));
        hint.revealNext();
        check.same("Only the indicated destination plays the exact suggested promotion", knight, hint.moveForTap(position, -1, knight.to()));
        check.same("A tap on the source goes to ordinary piece selection", null, hint.moveForTap(position, -1, knight.from()));
        check.same("Selecting the pawn explicitly keeps the ordinary promotion chooser", null,
                hint.moveForTap(position, knight.from(), knight.to()));
        check.same("The hint cannot override an explicit selection of a different piece", null,
                hint.moveForTap(position, BoardSquare.parse("h1"), knight.to()));
        hint.revealNext();
        check.same("Cycling a shared destination plays the newly suggested promotion", queen, hint.moveForTap(position, -1, queen.to()));
        check.same("A move no longer legal for the current side cannot be applied", null,
                hint.moveForTap(position.play(ChessPosition.Move.fromUci("h1g1")), -1, queen.to()));
    }

    @Test void hidesThenCyclesAtMostFiveContinuationsWithoutPlayingThem(Checks check) {
        String checkpoint = "2|analysis|e2e4 c7c5";
        var match = ChessMatch.restore(checkpoint);
        var lines = match.bestLinesAt(match.ply());
        var hint = new MatchHint(lines);
        check.same("The open Sicilian has multiple eligible alternatives within the configured limit", true,
                lines.size() >= 2 && lines.size() <= MatchAiPolicy.CURRENT.hint.maxVariants());
        check.same("Every displayed alternative stays within the leader's score window", true,
                lines.stream().allMatch(line -> lines.getFirst().score() - line.score() <= MatchAiPolicy.CURRENT.hint.maxLossCp()));
        check.same("Evaluation is available while its hint is still hidden", true, hint.first() != null && hint.revealed() == null);
        var seen = new HashSet<ChessPosition.Move>();
        for (int index = 0; index < lines.size(); index++) {
            hint.revealNext();
            seen.add(hint.revealed().move());
            check.same("Line " + (index + 1) + " preserves the complete legal engine continuation", lines.get(index), hint.revealed());
        }
        check.same("There is one continuation per best first move", lines.size(), seen.size());
        hint.revealNext();
        check.same("One more press wraps to the first line", lines.getFirst(), hint.revealed());
        check.same("Showing a line leaves the match checkpoint untouched", checkpoint, match.checkpoint());
    }

    @Test void stopsAtTheConfiguredLossAndKeepsOnlyEquivalentForcedMates(Checks check) {
        var policy = MatchAiPolicy.CURRENT.hint;
        var moves = ChessPosition.initial().legalMoves();
        var finite = new ArrayList<TacticalAnalysis.Variation>();
        for (int index = 0; index < 6; index++) {
            int score = index < 4 ? 200 : index == 4 ? 200 - policy.maxLossCp() : 199 - policy.maxLossCp();
            finite.add(new TacticalAnalysis.Variation(List.of(moves.get(index)), score, 0, TacticalAnalysis.Ending.HORIZON));
        }
        check.same("The boundary is inclusive", finite.subList(0, 5),
                TacticalAnalysis.hintLines(new TacticalAnalysis.Result(finite), policy));
        var shorter = List.of(finite.get(0), finite.get(5));
        check.same("A significant loss stops the list before reaching five", shorter.subList(0, 1),
                TacticalAnalysis.hintLines(new TacticalAnalysis.Result(shorter), policy));
        var mates = List.of(new TacticalAnalysis.Variation(List.of(moves.get(0)), 99_999, 0, TacticalAnalysis.Ending.CHECKMATE),
                new TacticalAnalysis.Variation(List.of(moves.get(1)), 99_997, 0, TacticalAnalysis.Ending.CHECKMATE));
        check.same("A slower forced mate is excluded even though the numeric gap is small", mates.subList(0, 1),
                TacticalAnalysis.hintLines(new TacticalAnalysis.Result(mates), policy));
    }

    @Test void basicGetsTheSameFivePlyForcedMateHintAsHard(Checks check) {
        var board = ChessPosition.fromFen("2r3k1/pb3ppp/1p2pq2/1P6/2Q1PP2/6P1/P5BP/2R3K1 w - - 1 25");
        var basic = ChessMatch.fromPosition(board, new MatchSetup(ChessPosition.Side.BLACK, MatchSetup.Level.BASIC, true));
        var hard = ChessMatch.fromPosition(board, new MatchSetup(ChessPosition.Side.BLACK, MatchSetup.Level.HARD, true));
        var hints = basic.bestLinesAt(0);
        check.same("The opponent setting does not change the hint", hard.bestLinesAt(0), hints);
        check.same("The strongest line proves mate in three", List.of("c4c8", 5),
                List.of(hints.getFirst().move().uci(), hints.getFirst().signedMatePlies()));
        check.same("The Basic opponent search does not prove this five-ply mate", 0,
                TacticalAnalysis.botAnalysis(board, MatchSetup.Level.BASIC).result().best().getFirst().signedMatePlies());
        for (var hint : hints) {
            var after = board;
            for (var move : hint.line()) after = after.play(move);
            check.same("Every displayed forced line legally ends in mate", true, after.isCheckmate());
        }
    }

    @Test void looksPastTheRecaptureInTheSicilian(Checks check) {
        var board = ChessPosition.fromFen("rnbqkbnr/pp1p1ppp/8/2p1p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 3");
        var hint = TacticalAnalysis.hintAnalysis(board);
        check.same("The hint takes on e5 instead of an unrelated quiet move", "f3e5", hint.result().ranked().getFirst().move().uci());
        var policy = MatchAiPolicy.CURRENT.hint.search();
        check.same("The hint completes enough plies to look past the recapture within its configured budget", true,
                hint.depth() >= 5 && hint.depth() <= policy.maxPlies() && hint.extraNodes() <= policy.extraNodes());
        var line = hint.result().ranked().getFirst().line();
        int extension = MatchAiPolicy.CURRENT.hint.tacticalExtensionPlies();
        check.same("The continuation reaches the completed horizon within its tactical extension", true,
                line.size() >= hint.depth() && line.size() <= hint.depth() + extension);
        for (var move : line) board = board.play(move);
    }

    @Test void aSingleMateStaysVisibleAndAnotherFrameStartsHidden(Checks check) {
        var match = ChessMatch.restore("2|analysis|f2f3 e7e5 g2g4");
        var current = new MatchHint(match.bestLinesAt(3));
        check.same("The fastest mate is the only top continuation", 1, current.count());
        current.revealNext();
        current.revealNext();
        check.same("Repeated presses retain the same mate in one", "d8h4", current.revealed().move().uci());
        var earlier = new MatchHint(match.bestLinesAt(2));
        check.same("An earlier position starts with its variants hidden", null, earlier.revealed());
        check.same("An ended game has no lines for its final frame", List.of(), match.play(current.revealed().move()).bestLinesAt(4));
        var disabled = new MatchHint(ChessMatch.start().bestLinesAt(0));
        disabled.revealNext();
        check.same("A disabled analysis cannot reveal a line", null, disabled.revealed());
    }
}
