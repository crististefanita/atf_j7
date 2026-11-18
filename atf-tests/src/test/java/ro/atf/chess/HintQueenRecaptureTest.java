package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintQueenRecaptureTest {
    private static final String ITALIAN = "e2e4 e7e5 g1f3 b8c6 f1c4 f8c5 b1c3 d8f6";

    @Test void theItalianBulbUsesNd5AndTheScoredContinuationWithoutChangingTheGame(Checks check) {
        var game = ChessMatch.restore("3|WHITE|HARD|1|" + ITALIAN);
        var checkpoint = game.checkpoint();
        var search = TacticalAnalysis.verifiedHints(game.position(), 200_000);
        check.same("The H17/H19 witness replaces Na4 by the developing queen attack", List.of(move("c3d5")),
                search.lines().stream().map(TacticalAnalysis.Variation::move).toList());
        check.same("The search retains its executed seed and selection horizons", List.of(4, 6, 200_000),
                List.of(search.seedDepth(), search.selectedDepth(), search.extraNodes()));
        check.same("A stored opening exception does not choose this recommendation", null, OpeningRepertoire.CURRENT.reply(game.position()));
        var hint = game.hintAt(game.ply()); hint.revealNext();
        check.same("Bulb and ordinary game use the same scored first line", search.lines().getFirst(), hint.revealed());
        var replay = hint.preview(game.position()).orElseThrow();
        while (replay.hasNext()) replay.next();
        check.same("The preview does not append an unscored reply", false, replay.addedDefense());
        check.same("Destination tap executes the same Nd5", move("c3d5"), hint.moveForTap(game.position(), -1, move("c3d5").to()));
        check.same("Analysis retains the original complete checkpoint", checkpoint, game.checkpoint());
        var manual = ChessMatch.restore("2|analysis|" + ITALIAN);
        check.same("The manual analysis board uses the same kernel", hint.first(), manual.hintAt(manual.ply()).first());
    }

    @Test void theExplicitNd5Na4ComparisonUsesOneActualCompletedHorizon(Checks check) {
        var board = ChessMatch.restore("2|analysis|" + ITALIAN).position();
        var pair = TacticalAnalysis.compareHints(board, move("c3a4"), move("c3d5")).orElseThrow();
        check.same("A common-horizon search also ranks Nd5 before Na4", move("c3d5"), pair.stronger().move());
        check.same("The old move remains available for explanation", move("c3a4"), pair.alternative().move());
        check.same("An interrupted deeper iteration is not relabeled as completed", 5, pair.depth());
        for (var line : List.of(pair.stronger(), pair.alternative())) {
            var end = board;
            for (var action : line.line()) end = end.play(action);
            check.same("The compared material is replayable for " + line.move().uci(), line.materialChange(), HintAuditSession.materialChangeCp(board, end));
        }
    }

    @Test void theQueenCaptureMustIncludeTheLegalPawnRecapture(Checks check) throws Exception {
        var board = ChessMatch.restore("2|analysis|" + ITALIAN + " c3d5 f6d6 e1g1 c6a5 c4b5 c5f2 f1f2 d6d5").position();
        var frontier = new Frontier(0); frontier.queens(1);
        var resolved = frontier.boundary(board, move("d6d5").to());
        check.same("After the opponent actually takes the knight, exd5 captures the queen", List.of(move("e4d5")), value(resolved, "moves"));
        var ordinary = new Frontier(0).boundary(board, move("d6d5").to());
        check.same("The legal reply changes the actual evaluation, not a root label", true, (int) value(resolved, "score") > (int) value(ordinary, "score"));
        check.same("The recapture consumes one existing search visit", 19_999, frontier.remaining());
    }

    @Test void pinnedPawnRecapturesCannotBeInventedAndTheSignalIsColorIndependent(Checks check) throws Exception {
        var probe = TacticalAnalysis.class.getDeclaredMethod("pawnCanRecaptureQueen", ChessPosition.class, int.class); probe.setAccessible(true);
        var free = ChessPosition.fromFen("r5k1/8/8/3q4/4P3/8/8/4K3 w - - 0 1");
        var pinned = ChessPosition.fromFen("4r1k1/8/8/3q4/4P3/8/8/4K3 w - - 0 1");
        check.same("The unpinned white pawn can capture the queen", true, probe.invoke(null, free, move("e4d5").to()));
        check.same("The e-file pin makes the apparent white recapture illegal", false, probe.invoke(null, pinned, move("e4d5").to()));
        var blackFree = ChessPosition.fromFen("4k3/8/8/4p3/3Q4/8/8/R5K1 b - - 0 1");
        var blackPinned = ChessPosition.fromFen("4k3/8/8/4p3/3Q4/8/8/4R1K1 b - - 0 1");
        check.same("The mirrored unpinned black pawn is equally valid", true, probe.invoke(null, blackFree, move("e5d4").to()));
        check.same("The mirrored pin cannot receive fake material credit", false, probe.invoke(null, blackPinned, move("e5d4").to()));
        check.same("An absent capture has no queen to resolve", false, probe.invoke(null, free, -1));
    }

    @Test void warmBoundsAndSeedValuesCannotImpersonateTheQueenRecaptureMode(Checks check) throws Exception {
        var board = ChessPosition.fromFen("3q2k1/8/8/8/3p4/4P3/3R4/6K1 w - - 0 1");
        var warm = new Frontier(64); var seed = warm.search(board, -100_001, 100_001); warm.queens(1);
        var actual = warm.search(board, -100_001, 100_001);
        var cold = new Frontier(64); cold.queens(1); var expected = cold.search(board, -100_001, 100_001);
        var disabled = new Frontier(0); disabled.queens(1);
        check.same("The old seed value cannot survive the change of frontier semantics", true, (int) value(actual, "score") > (int) value(seed, "score"));
        check.same("Cold and warmed values agree", value(expected, "score"), value(actual, "score"));
        check.same("Disabling the cache preserves the same proof", value(expected, "score"), value(disabled.search(board, -100_001, 100_001), "score"));
        var bounded = new Frontier(64); bounded.queens(1); int score = (int) value(expected, "score");
        check.same("A narrow cutoff stays a bound", false, value(bounded.search(board, score - 2, score - 1), "exact"));
        check.same("The bound cannot poison a later full window", value(expected, "score"), value(bounded.search(board, -100_001, 100_001), "score"));
    }

    @Test void aSoundQueenOfferToAPawnStillProvesMate(Checks check) throws Exception {
        var board = ChessPosition.fromFen("4r2r/p1p2kp1/2p2p2/2Qb4/8/2B3Nq/PPP2R1P/5RK1 b - - 11 30");
        var search = TacticalAnalysis.verifiedHints(board, 200_000); var first = search.lines().getFirst();
        check.same("The independently catalogued queen sacrifice remains the mating recommendation", List.of(move("h3g3"), 3), List.of(first.move(), first.signedMatePlies()));
        var after = board.play(first.move()).play(move("h2g3"));
        check.same("The offered queen is genuinely capturable by a pawn", true, after.play(move("h8h1")).isCheckmate());
        check.same("The sacrifice has the same whole-decision ceiling", true, search.extraNodes() <= 200_000);
    }

    private static Move move(String text) { return Move.fromUci(text); }
    private static Object value(Object record, String name) throws Exception { var m=record.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(record); }
    private static final class Frontier {
        final Object budget, objective; final Method search, settle;
        @SuppressWarnings({"rawtypes", "unchecked"}) Frontier(int cache) throws Exception {
            var type=Class.forName("ro.atf.chess.TacticalAnalysis$SearchBudget");var c=type.getDeclaredConstructor(int.class,int.class,int.class);c.setAccessible(true);budget=c.newInstance(20_000,2,cache);
            var goal=Class.forName("ro.atf.chess.TacticalAnalysis$Objective");objective=Enum.valueOf((Class)goal,"POSITION");
            search=TacticalAnalysis.class.getDeclaredMethod("search",ChessPosition.class,int.class,int.class,int.class,int.class,type,goal);search.setAccessible(true);
            settle=TacticalAnalysis.class.getDeclaredMethod("settle",ChessPosition.class,int.class,int.class,int.class,int.class,type,int.class,int.class);settle.setAccessible(true);
        }
        void queens(int plies) throws Exception {var f=budget.getClass().getDeclaredField("queenRecaptureExtension");f.setAccessible(true);f.setInt(budget,plies);}
        int remaining() throws Exception {var f=budget.getClass().getDeclaredField("remaining");f.setAccessible(true);return f.getInt(budget);}
        Object search(ChessPosition board,int alpha,int beta) throws Exception{return search.invoke(null,board,0,0,alpha,beta,budget,objective);}
        Object boundary(ChessPosition board,int square) throws Exception{return settle.invoke(null,board,0,8,-100_001,100_001,budget,square,-1);}
    }
}
