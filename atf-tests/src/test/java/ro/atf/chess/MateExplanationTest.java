package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Threat;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class MateExplanationTest {
    @Test void allCatalogMatesExplainTheSameCheckersAndKingFlightsAsTheIndependentOracle(Checks check) throws Exception {
        StringBuilder normalized = new StringBuilder();
        for (var quest : QuestCatalog.loadPositions()) {
            var proof = quest.explanation();
            normalized.append(quest.id()).append(' ').append(BoardSquare.name(proof.king())).append(' ').append(squares(proof.checkers()));
            proof.exits().stream().sorted(Comparator.comparing(exit -> BoardSquare.name(exit.square()))).forEach(exit -> {
                String cause = exit.ownBlocker() == null ? squares(exit.attackers()) : "own";
                normalized.append(' ').append(BoardSquare.name(exit.square())).append('=').append(cause);
            });
            normalized.append('\n');
        }
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.toString().getBytes(StandardCharsets.UTF_8)));
        check.same("All mate proofs match chess.js", ChessYaml.string(ChessYaml.resource("quest-oracle.yaml"), "mate-proof-sha256"), digest);
    }

    @Test void publishedCounterMatesCoverBlockingAndCapturingThePieceGivingCheck(Checks check) {
        var catalog = QuestCatalog.load();
        var interpose = catalog.stream().filter(quest -> quest.id().equals("mate-101")).findFirst().orElseThrow();
        var capture = catalog.stream().filter(quest -> quest.id().equals("mate-102")).findFirst().orElseThrow();
        verifyCounterMate(check, interpose, "h6d2", "h3", ChessPosition.Kind.BISHOP, List.of("f2e1", "f2e3", "f2f1", "f2f3", "f2g3", "h6d2"));
        verifyCounterMate(check, capture, "h3g4", "h4", ChessPosition.Kind.PAWN, List.of("f2e1", "f2f1", "f2f3", "h3g4", "h4g4"));
        check.same("Capturing the same knight with the rook only parries the check", ChessPosition.Outcome.PLAYING,
                capture.assess(Move.fromUci("h4g4")).outcome());
    }

    @Test void aCheckingDecoyReturnsAnActualLegalDefenseWithoutPlayingIt(Checks check) {
        var position = ChessPosition.fromFen("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2");
        var move = Move.fromUci("f1b5");
        var attempt = new QuestCatalog.Quest("fixture", "", move, "TACTIC", position).assess(move);
        check.same("The bishop gives check with a defense", ChessPosition.Outcome.CHECK, attempt.outcome());
        check.same("The shown defense is legal in the attempted position", true, attempt.position().legalMoves().contains(attempt.defense()));
        check.same("The original position keeps the white bishop on f1", ChessPosition.Kind.BISHOP,
                position.pieceAt(BoardSquare.parse("f1")).kind());
    }

    private static void verifyCounterMate(Checks check, QuestCatalog.Quest quest, String key, String checker, ChessPosition.Kind piece, List<String> legal) {
        String label = quest.id();
        var position = quest.position();
        check.same(label + " starts in check", true, position.isCheck());
        check.same(label + " legal replies", legal, position.legalMoves().stream().map(Move::uci).sorted().toList());
        check.same(label + " has one mate", List.of(key), position.legalMoves().stream().filter(quest::solves).map(Move::uci).toList());
        check.same(label + " names the discovered checker", checker, squares(quest.explanation().checkers()));
        check.same(label + " hint names the defending piece", piece, quest.guidance().piece().kind());
        check.same(label + " hint points to the origin of the unique mate", key.substring(0, 2), BoardSquare.name(quest.guidance().move().from()));
        check.same(label + " solution recognizes the discovered attack", QuestCatalog.MatePattern.DISCOVERED, quest.guidance().pattern());
    }

    private static String squares(List<Threat> attackers) {
        return String.join(",", attackers.stream().map(threat -> BoardSquare.name(threat.square())).sorted().toList());
    }
}
