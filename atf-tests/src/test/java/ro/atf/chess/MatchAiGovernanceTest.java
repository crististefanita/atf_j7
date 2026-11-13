package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.junit.jupiter.api.Assertions.assertThrows;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class MatchAiGovernanceTest {
    @Test void opponentBudgetsMatchTheMeasuredProfiles(Checks check) {
        check.same("Basic uses at most three plies and zero extra nodes", new MatchAiPolicy.Search(3, 0),
                MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.BASIC));
        check.same("Basic uses two plies outside elementary mating endings", 2, MatchAiPolicy.CURRENT.basicPlies);
        check.same("Advanced uses the calibrated four-ply, 10,000-node budget", new MatchAiPolicy.Search(4, 10_000),
                MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.ADVANCED));
        check.same("Hard gains one stable horizon without a node-budget increase", new MatchAiPolicy.Search(6, 44_000),
                MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD));
        check.same("Hint remains able to cover the Hard horizon", true,
                MatchAiPolicy.CURRENT.hint.search().maxPlies() >= MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).maxPlies());
        check.same("Post-game review remains at least as deep as Hard", true,
                GameReviewPolicy.CURRENT.search.maxPlies() >= MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).maxPlies());
    }

    @Test void basicStandardDepthRejectsValuesOutsideTheSupportedRange(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var opponents = new HashMap<Object, Object>(ChessYaml.map(root, "opponents"));
        var basic = new HashMap<Object, Object>(ChessYaml.map(opponents, "BASIC"));
        root.put("opponents", opponents);
        opponents.put("BASIC", basic);
        for (int invalid : List.of(1, 4)) {
            basic.put("standard-max-plies", invalid);
            check.same("Basic rejects standard depth " + invalid, true,
                    assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("standard-max-plies"));
        }
        basic.put("standard-max-plies", 3);
        check.same("YAML accepts a three-ply standard horizon", 3, MatchAiPolicy.read(root).basicPlies);
        basic.put("extra-nodes", 1);
        check.same("Basic rejects a nonzero extra-node budget", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("zero extra nodes"));
    }

    @Test void hardCheckOrderingIsBoundedAndGovernedByYaml(Checks check) {
        check.same("Only the game Hard profile uses the configured check-order bonus", 10_000,
                MatchAiPolicy.CURRENT.hardOrdering.checkBonus());
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var ordering = new HashMap<Object, Object>(ChessYaml.map(root, "hard-ordering"));
        root.put("hard-ordering", ordering);
        ordering.put("check-bonus", 10_001);
        check.same("An oversized move-order bonus is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("check-bonus"));
        ordering.put("check-bonus", -1);
        check.same("A negative move-order bonus is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("check-bonus"));
        ordering.put("check-bonus", 0);
        check.same("A zero bonus cleanly disables the heuristic", 0, MatchAiPolicy.read(root).hardOrdering.checkBonus());
    }

    @Test void hardPawnHorizonGuardUsesAStrictBoolean(Checks check) {
        check.same("Hard uses the pawn capture horizon guard", true, MatchAiPolicy.CURRENT.preservePawnCapture);
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        root.put("hard-horizon", Map.of("preserve-pawn-capture", false, "pawn-exchange-nodes", 0));
        check.same("YAML can disable the guard for an A/B run", false, MatchAiPolicy.read(root).preservePawnCapture);
        root.put("hard-horizon", Map.of("preserve-pawn-capture", "true", "pawn-exchange-nodes", 0));
        check.rejects("A string cannot enable the guard", IllegalArgumentException.class, () -> MatchAiPolicy.read(root));
    }

    @Test void hardPawnExchangeReserveCanBeDisabledAndRejectsOversizedBudgets(Checks check) {
        check.same("Hard reserves at most 7,000 existing search nodes for exchange verification", 7_000, MatchAiPolicy.CURRENT.pawnExchangeNodes);
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        root.put("hard-horizon", Map.of("preserve-pawn-capture", true, "pawn-exchange-nodes", 0));
        check.same("Zero disables the verification reserve", 0, MatchAiPolicy.read(root).pawnExchangeNodes);
        for (int invalid : List.of(-1, 7_001)) {
            root.put("hard-horizon", Map.of("preserve-pawn-capture", true, "pawn-exchange-nodes", invalid));
            check.rejects("The verification reserve rejects " + invalid, IllegalArgumentException.class, () -> MatchAiPolicy.read(root));
        }
    }

    @Test void hardExactTranspositionCacheIsBoundedAndScalesWithTheHardNodeBudget(Checks check) {
        check.same("Transposition limits use the versioned match-AI schema", 7,
                ChessYaml.number(ChessYaml.resource("match-ai.yaml"), "schema"));
        var settings = MatchAiPolicy.CURRENT.hardTranspositions;
        check.same("The measured cache uses half as many entries as Hard extra nodes", 500,
                settings.entriesPerThousandNodes());
        check.same("The cache has a strict memory ceiling", 50_000, settings.maxEntries());
        check.same("The current Hard budget derives a bounded 22,000-entry table", 22_000,
                settings.entryLimit(MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes()));
        check.same("A zero-node search does not allocate a table", 0, settings.entryLimit(0));

        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var cache = new HashMap<Object, Object>(ChessYaml.map(root, "hard-transpositions"));
        root.put("hard-transpositions", cache);
        cache.put("entries-per-thousand-nodes", 1_001);
        check.same("A cache ratio above its governed range is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root))
                        .getMessage().contains("entries-per-thousand-nodes"));
        cache.put("entries-per-thousand-nodes", 500);
        cache.put("max-entries", 50_001);
        check.same("A cache cap above 50,000 entries is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root))
                        .getMessage().contains("max-entries"));
        cache.put("max-entries", 50_000);
        cache.put("entries-per-thousand-nodes", 0);
        check.same("The cache can be disabled without changing search budgets", 0,
                MatchAiPolicy.read(root).hardTranspositions.entryLimit(44_000));
    }

    @Test void hardQuestTransferFloorsStayWithinEachFrozenChapter(Checks check) {
        var root = ChessYaml.resource("hard-quest-transfer.yaml");
        var floors = ChessYaml.map(root, "minimum-solved");
        var chapters = Map.of("mate-in-one", QuestCatalog.load().size(), "mate-in-two", QuestCatalog.loadMateInTwo().size(),
                "mate-in-three", QuestCatalog.loadMateInThree().size(), "tactics", QuestCatalog.loadTactics().size(),
                "training", QuestCatalog.loadTraining().size(), "intermezzo", QuestCatalog.loadIntermezzo().size());
        var expectedFloors = Map.of("mate-in-one", 500, "mate-in-two", 300, "mate-in-three", 190,
                "tactics", 86, "training", 39, "intermezzo", 24);
        check.same("Hard quest transfer floor schema", 1, ChessYaml.number(root, "schema"));
        String trainingBaseline = ChessYaml.string(root, "training-baseline-through");
        check.same("Training expansions preserve the original 48-position benchmark", "training-048", trainingBaseline);
        check.same("The full training baseline is still present", 48L, QuestCatalog.loadTraining().stream()
                .filter(quest -> quest.id().compareTo(trainingBaseline) <= 0).count());
        check.same("Every quest chapter has one governed floor", chapters.keySet(), floors.keySet());
        chapters.forEach((chapter, count) -> {
            int floor = ChessYaml.number(floors, chapter);
            check.same(chapter + " regression floor is frozen until the baseline is reviewed", expectedFloors.get(chapter), floor);
            check.same(chapter + " floor is positive and no larger than its catalog", true, floor > 0 && floor <= count);
        });
    }

    @Test void rookEndgameSearchHasOneGovernedExtensionAndANarrowCandidateWindow(Checks check) {
        var policy = MatchAiPolicy.CURRENT.rookEndgame;
        check.same("Rook endings allow one extra selective ply", 1, policy.extensionPlies());
        check.same("Rook root comparisons keep the validated 50 cp window", 50, policy.candidateGapCp());
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var rook = new HashMap<Object, Object>(ChessYaml.map(root, "rook-endgame"));
        root.put("rook-endgame", rook);
        rook.put("extension-plies", 2);
        check.same("Unvalidated endgame extensions are rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root))
                        .getMessage().contains("extension-plies"));
        rook.put("extension-plies", 1);
        rook.put("candidate-gap-cp", 300);
        check.same("A wide root window that admits dominated rook moves is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root))
                        .getMessage().contains("candidate-gap-cp"));
    }

    @Test void repetitionPolicyRequiresAnAdvantageAndBoundsTheCostOfEscaping(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var repetition = new HashMap<Object, Object>(ChessYaml.map(root, "repetition"));
        root.put("repetition", repetition);
        int advantage = ChessYaml.number(repetition, "min-advantage-cp");
        repetition.put("min-advantage-cp", 0);
        check.same("An equal position does not qualify as an advantage", true, assertThrows(IllegalArgumentException.class,
                () -> MatchAiPolicy.read(root)).getMessage().contains("min-advantage-cp"));
        repetition.put("min-advantage-cp", advantage);
        repetition.put("max-score-loss-cp", 100);
        check.same("Escaping the cycle cannot cost an entire pawn", true, assertThrows(IllegalArgumentException.class,
                () -> MatchAiPolicy.read(root)).getMessage().contains("max-score-loss-cp"));
    }

    @Test void opponentLevelsCannotShareADepthAndTheCoachNeedsItsOwnHeadroom(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var opponents = new HashMap<Object, Object>(ChessYaml.map(root, "opponents"));
        root.put("opponents", opponents);
        var hard = ChessYaml.map(opponents, "HARD");
        opponents.put("HARD", ChessYaml.map(opponents, "ADVANCED"));
        check.same("Two identical opponent profiles are rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("strictly"));
        opponents.put("HARD", hard);
        var hint = new HashMap<Object, Object>(ChessYaml.map(root, "hint"));
        root.put("hint", hint);
        hint.put("extra-nodes", ChessYaml.number(hard, "extra-nodes"));
        check.same("The coach cannot use only the Hard budget", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("larger budget"));
    }

    @Test void calibrationControlsAreNewAndTheirLimitsStayBounded(Checks check) {
        var calibration = ChessYaml.resource("opening-calibration.yaml");
        var hard = ChessYaml.map(calibration, "hard-controls");
        var display = ChessYaml.map(calibration, "evaluation");
        var frozen = new HashSet<String>();
        for (Object item : ChessYaml.list(ChessYaml.resource("opening-ai-benchmark.yaml"), "checkpoints")) {
            frozen.add(ChessYaml.string((Map<?, ?>) item, "moves"));
        }
        var target = ChessYaml.map(calibration, "hard-regression");
        check.same("The French anchor is the original failed checkpoint", true, frozen.contains(ChessYaml.string(target, "moves")));
        var anchor = ChessYaml.map(display, "anchor");
        check.same("The display anchor remains the position after 1.e4 e5", "e2e4 e7e5", ChessYaml.string(anchor, "moves"));
        check.same("The calibration schema", 1, ChessYaml.number(calibration, "schema"));
        var ids = new HashSet<String>();
        for (var controls : List.of(ChessYaml.list(hard, "positions"), ChessYaml.list(display, "controls"))) {
            var colors = new HashSet<ChessPosition.Side>();
            check.same("Each calibration group has at least six control positions", true, controls.size() >= 6);
            for (Object item : controls) {
                var row = (Map<?, ?>) item;
                String id = ChessYaml.string(row, "id");
                String prefix = ChessYaml.string(row, "moves");
                check.same(id + " is a distinct control position", true, ids.add(id) && frozen.add(prefix));
                var position = ChessPosition.initial();
                for (String move : prefix.split(" ")) position = position.play(ChessPosition.Move.fromUci(move));
                colors.add(position.turn());
            }
            check.same("Both sides to move are represented in each group", 2, colors.size());
        }
        check.same("Hard regression and display comparison caps are enforced", true,
                ChessYaml.number(target, "max-loss") <= 80 && ChessYaml.number(hard, "max-mean-loss") <= 40
                        && ChessYaml.number(display, "max-anchor-error-cp") <= 30
                        && ChessYaml.number(display, "max-control-mean-error-cp") <= 35
                        && ChessYaml.number(display, "max-control-case-error-cp") <= 65);
    }

    @Test void rejectsDisplaySettingsThatDefeatTheSearchBudgetOrHorizonBound(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var evaluation = new HashMap<Object, Object>(ChessYaml.map(root, "evaluation"));
        root.put("evaluation", evaluation);
        int nodes = ChessYaml.number(evaluation, "nodes");
        evaluation.put("nodes", 0);
        check.same("An empty display budget is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("nodes"));
        evaluation.put("nodes", nodes);
        evaluation.put("max-horizon-correction-cp", 100);
        check.same("A full pawn correction is rejected", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("max-horizon-correction-cp"));
    }

    @Test void frozenOpeningCorpusCoversTheLiveCatalogWithBothColors(Checks check) {
        var catalog = OpeningCatalog.load();
        var suite = ChessYaml.resource("opening-ai-benchmark.yaml");
        var ids = new HashSet<String>();
        var prefixes = new HashSet<String>();
        var colors = new HashSet<ChessPosition.Side>();
        check.same("Opening benchmark schema", 1, ChessYaml.number(suite, "schema"));
        for (Object entry : ChessYaml.list(suite, "checkpoints")) {
            var row = (Map<?, ?>) entry;
            String id = ChessYaml.string(row, "id");
            String prefix = ChessYaml.string(row, "moves");
            var opening = catalog.stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
            var moves = List.of(prefix.split(" "));
            check.same(id + " has a unique, frozen opening checkpoint", true, ids.add(id) && prefixes.add(prefix));
            check.same(id + " still begins with the recorded moves", moves,
                    opening.steps().stream().limit(moves.size()).map(step -> step.move().uci()).toList());
            colors.add(opening.positionAt(moves.size()).turn());
        }
        check.same("Every lesson is represented in the oracle corpus", catalog.size(), ids.size());
        check.same("White and Black must each have a turn to analyze", 2, colors.size());
        var limits = ChessYaml.map(suite, "limits");
        var hint = ChessYaml.map(limits, "HINT");
        var hard = ChessYaml.map(limits, "HARD");
        check.same("Opening hints prohibit a major loss and use a tighter mean cap than Hard", true,
                ChessYaml.number(hint, "major-losses") == 0 && ChessYaml.number(hint, "mean-loss") > 0
                        && ChessYaml.number(hint, "mean-loss") <= 30 && ChessYaml.number(hard, "mean-loss") <= 65
                        && ChessYaml.number(hard, "major-losses") <= 1);
    }

    @Test void rejectsAnOverlongHintListOrSearchWeakerThanHard(Checks check) {
        var root = new HashMap<Object, Object>(ChessYaml.resource("match-ai.yaml"));
        var hint = new HashMap<Object, Object>(ChessYaml.map(root, "hint"));
        root.put("hint", hint);
        hint.put("max-variants", 6);
        check.same("Six suggestions violate the product limit", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("max-variants"));
        hint.put("max-variants", 5);
        hint.put("extra-nodes", 1);
        check.same("The coaching budget cannot be lower than Hard", true,
                assertThrows(IllegalArgumentException.class, () -> MatchAiPolicy.read(root)).getMessage().contains("Hard"));
    }

    @Test void corpusCoversBothColorsAndRejectsUnrestrictedRegressionLimits(Checks check) {
        var settings = ChessYaml.resource("match-ai-benchmark.yaml");
        var colors = new HashSet<ChessPosition.Side>();
        var ids = new HashSet<String>();
        var boards = new HashSet<String>();
        for (Object entry : ChessYaml.list(settings, "positions")) {
            var position = (Map<?, ?>) entry;
            String fen = ChessYaml.string(position, "fen");
            check.same("Independent benchmark case has a unique ID and FEN", true, ids.add(ChessYaml.string(position, "id")) && boards.add(fen));
            colors.add(ChessPosition.fromFen(fen).turn());
        }
        check.same("Both colors have benchmark positions", 2, colors.size());
        check.same("The benchmark has at least ten varied positions", true, ids.size() >= 10);
        var limits = ChessYaml.map(settings, "limits");
        for (String profile : java.util.List.of("BASIC", "ADVANCED", "HARD", "HINT")) {
            var limit = ChessYaml.map(limits, profile);
            check.same(profile + " has a meaningful loss cap", true, ChessYaml.number(limit, "mean-loss") < 500);
            check.same(profile + " has a meaningful major-loss cap", true, ChessYaml.number(limit, "major-losses") < ids.size());
        }
    }
}
