package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.HintAuditSession.Envelope;
import ro.atf.chess.HintAuditSession.Identity;
import ro.atf.chess.HintAuditSession.Search;
import ro.atf.chess.TacticalAnalysis.Variation;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class HintAuditSessionTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path files;

    @Test void preservesInterruptedRawEvidenceAndRequiresAnEmptyExternalDirectory(Checks check) throws IOException {
        var raw = Files.createDirectory(files.resolve("partial"));
        var partial = Files.writeString(raw.resolve("interrupted.pgn"), "Preserve this partial game\n");
        check.rejects("A partial study cannot be silently overwritten on retry", IllegalArgumentException.class,
                () -> HintAuditSession.prepareOutput(raw, ProjectRoot.locate()));
        check.same("Existing partial bytes are preserved", "Preserve this partial game\n", Files.readString(partial));
        var fresh = files.resolve("fresh");
        check.same("A new external raw directory is prepared", fresh.toAbsolutePath(), HintAuditSession.prepareOutput(fresh, ProjectRoot.locate()));
        check.same("An existing strictly empty directory is accepted", fresh.toAbsolutePath(), HintAuditSession.prepareOutput(fresh, ProjectRoot.locate()));
        check.rejects("A file cannot be treated as an output directory", IllegalArgumentException.class,
                () -> HintAuditSession.prepareOutput(partial, ProjectRoot.locate()));
        check.rejects("The project checkout cannot become an audit artifact destination", IllegalArgumentException.class,
                () -> HintAuditSession.prepareOutput(ProjectRoot.locate(), ProjectRoot.locate()));
    }

    @Test void validatesTheVersionedPlanWithoutExpandingTheHardBudgetOrInventingRanks(Checks check) {
        var settings = ChessYaml.resource("hint-audit.yaml");
        var policy = HintAuditPolicy.load(settings);
        check.same("The first collection has three distinct opening families and eight decisions per path", List.of("sicilian", "caro-kann", "italian"),
                policy.runs().stream().map(HintAuditPolicy.Run::family).toList());
        check.same("The full path plan is concise", 24, policy.runs().stream().mapToInt(HintAuditPolicy.Run::decisions).sum());
        var altered = new HashMap<Object, Object>(settings);
        altered.put("max-nodes", 200001);
        check.rejects("A larger ceiling cannot silently enter the study", IllegalArgumentException.class, () -> HintAuditPolicy.load(altered));
        altered.put("max-nodes", 200000);
        altered.put("unexpected", true);
        check.rejects("Unknown policy fields are rejected", IllegalArgumentException.class, () -> HintAuditPolicy.load(altered));
        var emptyRuns = new HashMap<Object, Object>(settings); emptyRuns.put("runs", List.of());
        check.rejects("An empty run collection is rejected during parsing", IllegalArgumentException.class, () -> HintAuditPolicy.load(emptyRuns));
        var run = new HashMap<Object, Object>((java.util.Map<?, ?>) ChessYaml.list(settings, "runs").getFirst());
        for (String schedule : List.of("budgets", "ranks")) {
            var empty = new HashMap<Object, Object>(run); empty.put(schedule, List.of());
            var emptyRoot = new HashMap<Object, Object>(settings); emptyRoot.put("runs", List.of(empty));
            check.rejects("An empty " + schedule + " schedule is rejected during parsing", IllegalArgumentException.class,
                    () -> HintAuditPolicy.load(emptyRoot));
        }
        for (var invalid : List.of(List.of(0), List.of(200001))) {
            run.put("budgets", invalid);
            var root = new HashMap<Object, Object>(settings);
            root.put("runs", List.of(run));
            check.rejects("Invalid declared extra-node limit " + invalid, IllegalArgumentException.class, () -> HintAuditPolicy.load(root));
        }
        run.put("budgets", List.of(50000)); run.put("ranks", List.of(4));
        var root = new HashMap<Object, Object>(settings); root.put("runs", List.of(run));
        check.rejects("A nonexistent fourth public variant cannot be requested", IllegalArgumentException.class, () -> HintAuditPolicy.load(root));
    }

    @Test void recordsOneComputationAndEveryActualVariantWithAnExplicitUnavailableRank(Checks check) {
        var plan = plan("ranked", "e2e4 e7e5", List.of(50000, 200000), List.of(3, 2));
        var study = new HintAuditSession(identity("one"), plan);
        var calls = new AtomicInteger();
        var first = study.advance((board, budget) -> {
            calls.incrementAndGet();
            var report = mock(board, budget, 3);
            return new Search(report.lines().subList(0, 1), report.lines(), report.extraNodes(), 2, 2, 2);
        }).decision();
        check.same("A requested third choice is recorded as unavailable, with the explicit first-choice fallback",
                List.of(3, false, 1, 1), List.of(first.requestedRank(), first.requestedAvailable(), first.chosenRank(), first.variants().size()));
        check.same("Rejected diagnostic candidates remain separate from the single offered recommendation", List.of(1, 3),
                List.of(first.variants().size(), first.comparedVariants().size()));
        var second = study.advance((board, budget) -> { calls.incrementAndGet(); return mock(board, budget, 3); }).decision();
        check.same("The next request uses its actual second-ranked choice and scheduled budget", List.of(2, true, 2, 200000),
                List.of(second.requestedRank(), second.requestedAvailable(), second.chosenRank(), second.extraNodeLimit()));
        check.same("There is exactly one search call per appended decision", 2, calls.get());
        check.same("All three actual PVs, scores, and opponent-reply facts remain in evidence", true,
                second.variants().size() == 3 && second.variants().stream().allMatch(line -> line.pv().size() == 2 && line.opponentReplies().size() == 1));
        check.same("The full checkpoint advances only by the chosen legal move", first.checkpoint() + " " + first.chosen(), second.checkpoint());
        check.same("Fresh position-only search is labelled honestly", "FRESH_POSITION_ONLY_NO_PERSISTENT_CACHE", second.cacheMode());
        check.same("The available book move is kept separate from independent hint selection", true,
                first.phase().equals("THEORY_AVAILABLE_INDEPENDENT_HINT") && first.theoryMove() != null);
    }

    @Test void resumesByRevalidatingTheWholeHistoryWithoutCallingTheEngine(Checks check) throws IOException {
        var plan = plan("resume", "e2e4 e7e5", List.of(50000), List.of(1));
        var study = new HintAuditSession(identity("resume"), plan);
        study.advance((board, budget) -> mock(board, budget, 3));
        study.advance((board, budget) -> mock(board, budget, 2));
        var resumed = HintAuditSession.resume(identity("resume"), plan, study.jsonl(), HintAuditSession.digest(study.jsonl()));
        check.same("Offline restore preserves all scored data and the complete move history", study.receipts(), resumed.receipts());
        check.same("PGN and checkpoint remain identical after resumption", List.of(study.pgn(), study.match().checkpoint()),
                List.of(resumed.pgn(), resumed.match().checkpoint()));
        var firstOnly = study.jsonl().lines().findFirst().orElseThrow() + "\n";
        check.same("A complete prefix is a valid interrupted study, rather than an invented completed run", 1,
                HintAuditSession.resume(identity("resume"), plan, firstOnly, HintAuditSession.digest(firstOnly)).receipts().size());
        check.rejects("A complete prefix cannot masquerade as a qualified full collection", IllegalArgumentException.class,
                () -> HintAuditSession.resume(identity("resume"), plan, firstOnly, HintAuditSession.digest(study.jsonl())));
        check.rejects("A duplicated receipt cannot replay the move twice", IllegalArgumentException.class,
                () -> HintAuditSession.resume(identity("resume"), plan, firstOnly + firstOnly, HintAuditSession.digest(firstOnly + firstOnly)));
        check.rejects("A truncated raw receipt is rejected", IOException.class,
                () -> HintAuditSession.resume(identity("resume"), plan, firstOnly.substring(0, firstOnly.length() / 2),
                        HintAuditSession.digest(firstOnly.substring(0, firstOnly.length() / 2))));
    }

    @Test void rejectsForeignOrAlteredReceiptsEvenAfterTheirHashIsRecomputed(Checks check) throws Exception {
        var plan = plan("integrity", "e2e4 e7e5", List.of(50000), List.of(1));
        var study = new HintAuditSession(identity("integrity"), plan);
        study.advance((board, budget) -> mock(board, budget, 2));
        for (String field : List.of("fen", "context", "previousDigest", "checkpoint")) {
            String altered = alter(study.jsonl(), field, field.equals("previousDigest") ? "f".repeat(64) : "foreign");
            check.rejects("Recomputed digest does not hide altered " + field, IllegalArgumentException.class,
                    () -> HintAuditSession.resume(identity("integrity"), plan, altered, HintAuditSession.digest(altered)));
        }
        String fen = study.receipts().getFirst().decision().fen();
        for (String changedFen : List.of(fen.replace(" 0 2", " 4 2"), fen.replace(" KQkq ", " - "), fen.replace(" e6 ", " - "))) {
            String altered = alter(study.jsonl(), "fen", changedFen);
            check.rejects("A rehashed change to the clock, castling rights or raw en-passant field cannot pass replay",
                    IllegalArgumentException.class, () -> HintAuditSession.resume(identity("integrity"), plan, altered, HintAuditSession.digest(altered)));
        }
        var changedScore = (ObjectNode) JSON.readTree(study.jsonl());
        ((ObjectNode) changedScore.path("decision").path("variants").get(0)).put("scoreCp", 999);
        var changedBody = JSON.treeToValue(changedScore.get("decision"), HintAuditSession.Decision.class);
        changedScore.put("digest", HintAuditSession.digest(HintAuditSession.json(changedBody)));
        check.rejects("Rehashing an altered score cannot replace the separately trusted raw-manifest digest", IllegalArgumentException.class,
                () -> HintAuditSession.resume(identity("integrity"), plan, changedScore + "\n", HintAuditSession.digest(study.jsonl())));
        var original = identity("integrity");
        for (var foreign : List.of(identity("another-run"), new Identity(original.dataset(), original.run(), "2".repeat(40), original.engineDigest(), original.configurationDigest()),
                new Identity(original.dataset(), original.run(), original.revision(), "3".repeat(64), original.configurationDigest()),
                new Identity(original.dataset(), original.run(), original.revision(), original.engineDigest(), "4".repeat(64)))) {
            check.rejects("Receipts are bound to their exact run, source and policy", IllegalArgumentException.class,
                    () -> HintAuditSession.resume(foreign, plan, study.jsonl(), HintAuditSession.digest(study.jsonl())));
        }
        check.rejects("Another opening history cannot accept this receipt", IllegalArgumentException.class,
                () -> HintAuditSession.resume(original, plan("integrity", "d2d4 d7d5", List.of(50000), List.of(1)), study.jsonl(), HintAuditSession.digest(study.jsonl())));
    }

    @Test void repeatedBoardsDoNotEraseClocksCastlingEnPassantOrCacheContext(Checks check) {
        var first = new HintAuditSession(identity("context"), plan("context", "e2e4 e7e5", List.of(100000), List.of(1)));
        var repeated = new HintAuditSession(identity("context"), plan("context", "e2e4 e7e5 g1f3 g8f6 f3g1 f6g8", List.of(100000), List.of(1)));
        check.same("The piece arrangement and repetition key genuinely agree", true, first.match().position().sameForRepetition(repeated.match().position()));
        check.same("Quiet-move clocks and full-move numbers are retained", true,
                HintAuditSession.fen(first.match()).endsWith("0 2") && HintAuditSession.fen(repeated.match()).endsWith("4 4"));
        check.same("A repeated board still has a distinct history-bound search context", false,
                first.context(first.match().checkpoint(), 100000).equals(repeated.context(repeated.match().checkpoint(), 100000)));
        check.same("Changing only the node policy also changes the context", false,
                first.context(first.match().checkpoint(), 50000).equals(first.context(first.match().checkpoint(), 200000)));
        first.advance((board, budget) -> mock(board, budget, 1));
        check.rejects("An identical piece arrangement reached through a different full history rejects the old receipt", IllegalArgumentException.class,
                () -> HintAuditSession.resume(identity("context"), plan("context", "e2e4 e7e5 g1f3 g8f6 f3g1 f6g8", List.of(100000), List.of(1)), first.jsonl(), HintAuditSession.digest(first.jsonl())));
        var repeatedRow = repeated.advance((board, budget) -> mock(board, budget, 1)).decision();
        check.same("Actual repeated-position evidence retains both occurrences", 2, repeatedRow.repetitions());
        var lostRights = plan("rights", "e2e4 e7e5 e1e2 e8e7 e2e1 e7e8", List.of(100000), List.of(1)).start();
        check.same("Returning kings to their original squares does not restore castling rights", "-", HintAuditSession.fen(lostRights).split(" ")[2]);
        var ep = plan("ep", "e2e4 a7a6 e4e5 d7d5", List.of(100000), List.of(1)).start();
        String fen = HintAuditSession.fen(ep);
        check.same("Legal en passant and its clock are retained in the recorded FEN", true,
                fen.endsWith(" d6 0 3") && ChessPosition.fromFen(fen).legalMoves().contains(Move.fromUci("e5d6")));
    }

    @Test void invalidEngineEvidenceCannotAdvanceTheSavedStudy(Checks check) {
        var plan = plan("invalid", "e2e4 e7e5", List.of(50000), List.of(1));
        var study = new HintAuditSession(identity("invalid"), plan);
        String checkpoint = study.match().checkpoint();
        var good = mock(study.match().position(), 50000, 1);
        for (var invalid : List.of(new Search(good.lines(), 50001, 2, 2, 2), new Search(good.lines(), 10, 0, 2, 2),
                new Search(List.of(), 10, 2, 2, 2), new Search(List.of(good.lines().getFirst(), good.lines().getFirst()), 10, 2, 2, 2),
                new Search(List.of(new Variation(good.lines().getFirst().line(), 20, good.lines().getFirst().materialChange() + 1,
                        TacticalAnalysis.Ending.HORIZON)), 10, 2, 2, 2),
                new Search(List.of(new Variation(List.of(Move.fromUci("e1e8")), 0, 0, TacticalAnalysis.Ending.HORIZON)), 10, 2, 2, 2))) {
            check.rejects("Invalid result is rejected transactionally", IllegalArgumentException.class, () -> study.advance((board, budget) -> invalid));
            check.same("No move or receipt survives a rejected result", List.of(checkpoint, 0), List.of(study.match().checkpoint(), study.receipts().size()));
        }
        var opening = new HintAuditSession(identity("pgn"), plan("pgn", "e2e4 e7e5 g1f3 b8c6 f1b5 a7a6", List.of(50000), List.of(1)));
        check.same("The PGN retains standard SAN and honestly marks an unfinished study", true, opening.pgn().contains("1. e4 e5 2. Nf3 Nc6 3. Bb5 a6 *"));
    }

    @Test @Tag("benchmark")
    void collectsShortRankedPathsAndAnIdenticalPositionBudgetGrid(Checks check) throws Exception {
        var policy = HintAuditPolicy.load(ChessYaml.resource("hint-audit.yaml"));
        String configured = System.getProperty("chess.hintAudit.output", "");
        check.same("An explicitly selected audit collection requires an output directory", true, !configured.isBlank());
        Path output = HintAuditSession.prepareOutput(Path.of(configured), ProjectRoot.locate());
        check.same("The explicitly selected raw directory is empty and external before collection", true, Files.isDirectory(output));
        String rawPolicy;
        try (var input = getClass().getResourceAsStream("/hint-audit.yaml")) { rawPolicy = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        var identity = new Identity(policy.dataset(), System.getProperty("chess.hintAudit.run", ""), System.getProperty("chess.hintAudit.revision", ""),
                System.getProperty("chess.hintAudit.engineDigest", ""), HintAuditSession.digest(rawPolicy));
        var git = new ProcessBuilder("git", "-C", ProjectRoot.locate().toString(), "rev-parse", "HEAD").redirectErrorStream(true).start();
        boolean finished = git.waitFor(10, TimeUnit.SECONDS);
        if (!finished) git.destroyForcibly();
        String actualRevision = finished ? new String(git.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).strip() : "GIT_TIMEOUT";
        check.same("Full audit source/configuration/runtime identity: " + identity, identity.revision(), actualRevision);
        var collection = new StringBuilder();
        var inventory = new ArrayList<Object>();
        for (var run : policy.runs()) {
            var study = new HintAuditSession(identity, run);
            for (int index = 0; index < run.decisions() && study.match().result() == ChessMatch.Result.IN_PROGRESS; index++) actual(check, study);
            save(output, run.id(), study, collection, inventory);
            for (int budget : List.of(50000, 100000, 200000)) {
                var grid = new HintAuditPolicy.Run(run.id() + "-grid-" + budget, run.family(), run.prefix(), 1, List.of(budget), List.of(1));
                var control = new HintAuditSession(identity, grid);
                actual(check, control);
                check.same("Budget comparison starts from the identical full checkpoint", run.start().checkpoint(), control.receipts().getFirst().decision().checkpoint());
                save(output, grid.id(), control, collection, inventory);
            }
        }
        writeNew(output.resolve("collection.jsonl"), collection.toString());
        writeNew(output.resolve("manifest.json"), HintAuditSession.json(java.util.Map.of("identity", identity, "runs", inventory,
                "collectionDigest", HintAuditSession.digest(collection.toString()), "qualityStatus", "NOT_ORACLE_QUALIFIED",
                "integritySemantics", "Replay requires a separately trusted manifest digest; a self hash does not authenticate an untrusted manifest",
                "searchSemantics", "Position-only fresh search; full history retained for audit; no Elo claim")));
        check.same("The collection contains the declared three path studies and nine identical-position budget controls", 12, inventory.size());
    }

    private static void actual(Checks check, HintAuditSession study) {
        var receipt = study.advance((board, budget) -> {
            var search = TacticalAnalysis.verifiedHints(board, budget);
            var compared = search.comparison();
            return new Search(search.lines(), compared == null ? List.of() : compared.result().ranked(), search.extraNodes(),
                    search.selectedDepth(), search.seedDepth(), compared == null ? 0 : compared.depth());
        }).decision();
        var restored = ChessMatch.restore(receipt.checkpoint());
        check.same(receipt.caseId() + " #" + receipt.index() + " | full input=" + receipt.checkpoint() + " | phase=" + receipt.phase()
                + " book=" + receipt.theoryMove() + " | context=" + receipt.context(),
                List.of(restored.position().searchKey(), HintAuditSession.fen(restored)),
                List.of(ChessPosition.fromFen(receipt.fen()).searchKey(), receipt.fen()));
        check.same(receipt.caseId() + " #" + receipt.index() + " | " + receipt.side() + " | rank " + receipt.requestedRank()
                + " available=" + receipt.requestedAvailable() + " chosen=" + receipt.chosen() + " | H" + receipt.selectedDepth()
                + " seedH" + receipt.seedDepth() + " | " + receipt.extraNodes() + "/" + receipt.extraNodeLimit()
                + " | offered=" + receipt.variants() + " | compared=" + receipt.comparedVariants(),
                true, receipt.extraNodes() <= receipt.extraNodeLimit() && receipt.variants().size() <= 3);
    }

    private static void save(Path output, String id, HintAuditSession study, StringBuilder combined, List<Object> inventory) throws Exception {
        var restored = HintAuditSession.resume(study.receipts().getFirst().decision().identity(),
                new HintAuditPolicy.Run(id, study.receipts().getFirst().decision().family(), study.receipts().getFirst().decision().checkpoint().split("\\|", -1)[4],
                        study.receipts().size(), study.receipts().stream().map(row -> row.decision().extraNodeLimit()).toList(),
                        study.receipts().stream().map(row -> row.decision().requestedRank()).toList()), study.jsonl(), HintAuditSession.digest(study.jsonl()));
        if (!restored.match().checkpoint().equals(study.match().checkpoint())) throw new IllegalStateException("Restored audit differs");
        writeNew(output.resolve(id + ".jsonl"), study.jsonl());
        writeNew(output.resolve(id + ".pgn"), study.pgn());
        combined.append(study.jsonl());
        inventory.add(java.util.Map.of("id", id, "decisions", study.receipts().size(), "checkpoint", study.match().checkpoint(),
                "result", study.match().result().name(), "receiptDigest", HintAuditSession.digest(study.jsonl()), "pgnDigest", HintAuditSession.digest(study.pgn())));
    }

    private static void writeNew(Path path, String value) throws IOException {
        Files.writeString(path, value, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private static String alter(String jsonl, String field, String value) throws Exception {
        var envelope = (ObjectNode) JSON.readTree(jsonl);
        ((ObjectNode) envelope.path("decision")).put(field, value);
        var body = JSON.treeToValue(envelope.get("decision"), HintAuditSession.Decision.class);
        envelope.put("digest", HintAuditSession.digest(HintAuditSession.json(body)));
        return envelope + "\n";
    }

    private static Identity identity(String run) { return new Identity("test-study", run, "1".repeat(40), "2".repeat(64), "3".repeat(64)); }
    private static HintAuditPolicy.Run plan(String id, String prefix, List<Integer> budgets, List<Integer> ranks) {
        return new HintAuditPolicy.Run(id, "test-family", prefix, 4, budgets, ranks);
    }
    private static Search mock(ChessPosition board, int budget, int count) {
        var lines = new ArrayList<Variation>();
        for (var move : board.legalMoves().stream().sorted(java.util.Comparator.comparing(Move::uci)).limit(count).toList()) {
            var after = board.play(move);
            var reply = after.legalMoves().stream().sorted(java.util.Comparator.comparing(Move::uci)).findFirst().orElseThrow();
            lines.add(new Variation(List.of(move, reply), 20 - lines.size() * 10,
                    HintAuditSession.materialChangeCp(board, after.play(reply)), TacticalAnalysis.Ending.HORIZON));
        }
        return new Search(lines, budget - 7, 2, 2, 2);
    }
}
