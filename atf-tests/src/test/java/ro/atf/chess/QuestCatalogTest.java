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

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class QuestCatalogTest {
    @Test void everyPuzzleHasExactlyOneMateAndTheSameLegalMovesAsTheIndependentOracle(Checks check) throws Exception {
        var catalog = QuestCatalog.load();
        check.same("There are exactly 102 mate-in-one puzzles", 102, catalog.size());
        check.same("52 positions start with White to move", 52L,
                catalog.stream().filter(q -> q.position().turn() == ChessPosition.Side.WHITE).count());
        StringBuilder normalized = new StringBuilder();
        for (var quest : catalog.stream().sorted(Comparator.comparing(QuestCatalog.Quest::id)).toList()) {
            List<Move> legal = quest.position().legalMoves();
            var mates = legal.stream().filter(quest::solves).map(Move::uci).sorted().toList();
            check.same(quest.id() + " has exactly the listed checkmating move", List.of(quest.solution().uci()), mates);
            normalized.append(quest.id()).append(' ').append(String.join(",", legal.stream().map(Move::uci).sorted().toList())).append('\n');
        }
        var oracle = ChessYaml.resource("quest-oracle.yaml");
        String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.toString().getBytes(StandardCharsets.UTF_8)));
        check.same("All legal move sets match chess.js 1.4.0", ChessYaml.string(oracle, "legal-moves-sha256"), actual);
    }

    @Test void displayOrderMatchesTheDifficultyModelWithoutRenamingAnyQuest(Checks check) {
        var canonical = QuestCatalog.loadPositions();
        var order = QuestCatalog.loadOrder();
        check.same("All scores and their tie breaks reproduce", QuestDifficulty.rank(canonical), order);
        check.same("Catalog follows the scored order", order.stream().map(QuestDifficulty.Rank::id).toList(),
                QuestCatalog.load().stream().map(QuestCatalog.Quest::id).toList());
        check.same("The introductory mate is first", "mate-001", order.getFirst().id());
        check.same("The highest ranks include a substantial decoy or special-move burden", true, order.getLast().score() >= 35);
        check.same("Equal counter-mate scores use stable IDs as the tie break",
                List.of(new QuestDifficulty.Rank("mate-101", 32), new QuestDifficulty.Rank("mate-102", 32)),
                order.stream().filter(rank -> rank.id().equals("mate-101") || rank.id().equals("mate-102")).toList());
    }

    @Test void everyHintIdentifiesTheActualMatingPieceAndMove(Checks check) {
        var catalog = QuestCatalog.load();
        long complete = catalog.stream().filter(quest -> {
            var guidance = quest.guidance();
            return guidance.piece().equals(quest.position().pieceAt(guidance.move().from()))
                    && guidance.piece().side() == quest.position().turn() && quest.solves(guidance.move());
        }).count();
        check.same("All 102 hints name a piece that can play the recorded mate", 102L, complete);
        var promotion = catalog.stream().filter(q -> q.solution().uci().equals("f7f8n")).findFirst().orElseThrow().guidance();
        check.same("Underpromotion hint starts with a pawn", ChessPosition.Kind.PAWN, promotion.piece().kind());
        check.same("The revealed solution preserves the knight promotion", ChessPosition.Kind.KNIGHT, promotion.move().promotion());
        check.same("The first mating move is direct", QuestCatalog.MatePattern.DIRECT, catalog.getFirst().guidance().pattern());
    }

    @Test void ordinaryCheckAndStalemateNeverSatisfyTheObjective(Checks check) {
        var playable = ChessPosition.fromFen("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq d6 0 2");
        var givesCheck = Move.fromUci("f1b5");
        check.same("Bishop move gives check", ChessPosition.Outcome.CHECK, playable.play(givesCheck).outcome());
        check.same("A check with a defense is not a solution", false, new QuestCatalog.Quest("fixture", "", givesCheck, "TACTIC", playable).solves(givesCheck));
        var stalemate = ChessPosition.fromFen("7k/5K2/6Q1/8/8/8/8/8 b - - 0 1");
        check.same("Position without legal moves and without check is stalemate", ChessPosition.Outcome.STALEMATE, stalemate.outcome());
        check.same("Stalemate never counts as checkmate", false, stalemate.isCheckmate());
    }

    @Test void ruleBoundariesForCastlingAndEnPassantRemainLegalOrIllegalForTheRightReason(Checks check) {
        var castle = ChessPosition.fromFen("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        check.same("Queenside rook ends on d1", new ChessPosition.Piece(ChessPosition.Side.WHITE, ChessPosition.Kind.ROOK),
                castle.play(Move.fromUci("e1c1")).pieceAt(BoardSquare.parse("d1")));
        var attackedTransit = ChessPosition.fromFen("1k3r2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        check.same("Kingside castle through attacked f1 is illegal", false, attackedTransit.legalMoves().contains(Move.fromUci("e1g1")));
        check.same("Queenside castle is still legal", true, attackedTransit.legalMoves().contains(Move.fromUci("e1c1")));
        var pinnedCapture = ChessPosition.fromFen("k7/8/8/4KPpr/8/8/8/8 w - g6 0 2");
        check.same("En passant cannot uncover a rook attack on the moving side's king", false, pinnedCapture.legalMoves().contains(Move.fromUci("f5g6")));
        var catalog = QuestCatalog.load();
        for (String uci : List.of("e1c1", "e1g1", "e5d6", "f7f8n")) {
            check.same("Catalog includes a verified mate via " + uci, true, catalog.stream().anyMatch(q -> q.solution().uci().equals(uci) && !q.theme().equals("TACTIC")));
        }
    }

    @Test void aPinnedPieceStillControlsItsAttackedSquares(Checks check) {
        var position = ChessPosition.fromFen("4k3/4n3/8/8/2K5/8/8/4R3 w - - 0 1");
        check.same("White king cannot enter d5 controlled by the pinned knight", false,
                position.legalMoves().contains(Move.fromUci("c4d5")));
        check.same("The king can move to the safe neighboring square c5", true,
                position.legalMoves().contains(Move.fromUci("c4c5")));
    }
}
