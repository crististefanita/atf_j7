package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        check.same("All scores and their editorial tie breaks reproduce from YAML", QuestDifficulty.rank(canonical), order);
        check.same("Catalog follows the scored order", order.stream().map(QuestDifficulty.Rank::id).toList(),
                QuestCatalog.load().stream().map(QuestCatalog.Quest::id).toList());
        check.same("The configured introduction is first", ChessYaml.string(ChessYaml.resource("quest-difficulty.yaml"), "introduction"), order.getFirst().id());
        check.same("Every position advances in estimated difficulty", (long) order.size() - 1,
                IntStream.range(1, order.size()).filter(i -> order.get(i - 1).score() < order.get(i).score()).count());
    }

    @Test void changingTheDecoyWeightInYamlChangesTheCurriculumScore(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("quest-difficulty.yaml"));
        root.put("introduction", "mate-001");
        root.put("tie-breaks", Map.of());
        var weights = new HashMap<Object, Object>(ChessYaml.map(root, "weights"));
        weights.put("checking-decoy", ChessYaml.number(weights, "checking-decoy") + 1);
        root.put("weights", weights);
        var quests = QuestCatalog.loadPositions();
        var amended = QuestDifficulty.rank(quests, QuestDifficulty.Profile.read(root));
        var baseline = new HashMap<Object, Object>(root);
        baseline.put("weights", ChessYaml.map(ChessYaml.resource("quest-difficulty.yaml"), "weights"));
        int before = QuestDifficulty.rank(quests, QuestDifficulty.Profile.read(baseline)).stream()
                .filter(rank -> rank.id().equals("mate-129")).findFirst().orElseThrow().score();
        int after = amended.stream().filter(rank -> rank.id().equals("mate-129")).findFirst().orElseThrow().score();
        check.same("The decoy-rich sample gains ten score units per decoy before editorial ties", 14, after / 10 - before / 10);
        weights.put("checking-decoy", -1);
        check.same("A negative weight is rejected with the offending YAML key", true,
                assertThrows(IllegalArgumentException.class, () -> QuestDifficulty.Profile.read(root)).getMessage().contains("checking-decoy"));
    }

    @Test void noChapterRepeatsAnExerciseFromTheSameOrAnotherChapter(Checks check) {
        var all = new ArrayList<>(QuestCatalog.load());
        all.addAll(QuestCatalog.loadMateInTwo());
        all.addAll(QuestCatalog.loadMateInThree());
        all.addAll(QuestCatalog.loadTactics());
        QuestDiversity.requireDistinct(all);
        check.same("All chapters contribute 314 distinct IDs and positions", 314L, all.stream().map(QuestCatalog.Quest::id).distinct().count());
    }

    @Test void ignoresAnEditorialTieBreakOnceTheBaseScoresDiffer(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("quest-difficulty.yaml"));
        root.put("introduction", "mate-001");
        root.put("tie-breaks", Map.of("mate-014", 1));
        var quests = QuestCatalog.loadPositions().stream().filter(q -> List.of("mate-001", "mate-014", "mate-047").contains(q.id())).toList();
        var weights = new HashMap<Object, Object>(ChessYaml.map(root, "weights"));
        weights.replaceAll((key, value) -> 0);
        root.put("weights", weights);
        var tied = QuestDifficulty.rank(quests, QuestDifficulty.Profile.read(root));
        check.same("An explicit tie-break orders two equal base scores", "mate-014", tied.getLast().id());
        weights.put("legal-move", 1);
        var separated = QuestDifficulty.rank(quests, QuestDifficulty.Profile.read(root));
        check.same("The old tie-break contributes zero after the scores diverge",
                new QuestDifficulty.Rank("mate-014", 270), separated.get(1));
    }

    @Test void rejectsAnAlternativeMateEvenInTheIntroduction(Checks check) {
        var problem = fixture("mate-001", "7k/5Q2/6K1/8/8/8/8/8 w - - 0 1", "f7g7");
        var root = new HashMap<Object, Object>(ChessYaml.resource("quest-difficulty.yaml"));
        root.put("introduction", "mate-001");
        root.put("tie-breaks", Map.of());
        QuestCatalog.requireReferenceMate(problem);
        String error = assertThrows(IllegalArgumentException.class,
                () -> QuestDifficulty.rank(List.of(problem), QuestDifficulty.Profile.read(root))).getMessage();
        check.same("The diagnostic names the position, declared key and alternative", true,
                error.startsWith("Multiple mates for mate-001: f7g7 and f7"));
    }

    @Test void rejectsAWrongSpecialMoveThemeAndReusedHistoricalIds(Checks check) {
        var ep = QuestCatalog.load().stream().filter(q -> q.id().equals("mate-004")).findFirst().orElseThrow();
        var mislabeled = fixture(ep.id(), ep.fen(), ep.solution().uci());
        check.same("A legal en passant mate cannot be labeled as an ordinary tactic", "Wrong theme for mate-004 e5d6: expected EN_PASSANT",
                assertThrows(IllegalArgumentException.class, () -> QuestCatalog.requireReferenceMate(mislabeled)).getMessage());
        for (String id : List.of("mate-011", "mate3-02GAF")) {
            check.same("Progress ID remains reserved after retirement: " + id, "Retired quest ID cannot be reused: " + id,
                    assertThrows(IllegalArgumentException.class, () -> QuestCatalog.requireAvailableId(id)).getMessage());
        }
    }

    @Test void rejectsARepeatedKeyAndColorSwappedCopies(Checks check) {
        var original = fixture("mate-010", "r1b5/p2pb1pQ/2q2k2/1P6/6P1/2P5/7r/4K2R w K - 0 28", "h7f5");
        var near = fixture("mate-011", "r1b5/p2pb1pQ/2q2k2/1p6/P5P1/2P4r/7P/4K2R w K - 0 27", "h7f5");
        var exact = fixture("mate-003", "3k4/8/3KP2p/7P/5P2/1P3PR1/8/8 w - - 1 48", "g3g8");
        var swapped = fixture("mate-999", "8/8/1p3pr1/5p2/7p/3kp2P/8/3K4 b - - 0 50", "g6g1");
        check.same("The duplicate diagnostic identifies both published IDs", "Repeated chess exercise: mate-010 / mate-011",
                assertThrows(IllegalArgumentException.class, () -> QuestDiversity.requireDistinct(List.of(original, near))).getMessage());
        check.same("Swapping colors and reflecting ranks still repeats the same exercise", "Repeated chess exercise: mate-003 / mate-999",
                assertThrows(IllegalArgumentException.class, () -> QuestDiversity.requireDistinct(List.of(exact, swapped))).getMessage());
    }

    @Test void rejectsAQuarterTurnOfAPawnlessPosition(Checks check) {
        var original = fixture("mate-900", "7k/8/5KQ1/8/8/8/8/8 w - - 0 1", "g6g7");
        var rotated = fixture("mate-901", "k7/2Q5/2K5/8/8/8/8/8 w - - 0 1", "c7b7");
        QuestCatalog.requireReferenceMate(original);
        QuestCatalog.requireReferenceMate(rotated);
        check.same("The same pawnless mate cannot reappear at a different angle", "Repeated chess exercise: mate-900 / mate-901",
                assertThrows(IllegalArgumentException.class, () -> QuestDiversity.requireDistinct(List.of(original, rotated))).getMessage());
    }

    private static QuestCatalog.Quest fixture(String id, String fen, String uci) {
        return new QuestCatalog.Quest(id, fen, Move.fromUci(uci), "TACTIC", ChessPosition.fromFen(fen));
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
        var direct = catalog.stream().filter(q -> q.id().equals("mate-001")).findFirst().orElseThrow();
        check.same("The Fool's Mate hint is direct", QuestCatalog.MatePattern.DIRECT, direct.guidance().pattern());
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
