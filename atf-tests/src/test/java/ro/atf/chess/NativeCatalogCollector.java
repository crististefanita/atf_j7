package ro.atf.chess;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Runs the ordinary opponent before consulting any educational answer. */
final class NativeCatalogCollector {
    private record Chapter(String name, List<QuestCatalog.Quest> quests) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[1].matches("[a-f0-9]{40}"))
            throw new IllegalArgumentException("Expected external output directory and exact producer revision");
        var out = Path.of(args[0]).toAbsolutePath().normalize();
        if (out.startsWith(ProjectRoot.locate().toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Keep native observations outside the checkout");
        for (String name : List.of("identity.json", "catalog.json", "native.jsonl"))
            if (Files.exists(out.resolve(name))) throw new IllegalArgumentException("Preserve existing native evidence: " + name);
        var revision = args[1];
        Files.createDirectories(out);
        var json = LearningGameAudit.mapper();
        var chapters = List.of(new Chapter("mate-in-one", QuestCatalog.load()),
                new Chapter("mate-in-two", QuestCatalog.loadMateInTwo()),
                new Chapter("mate-in-three", QuestCatalog.loadMateInThree()),
                new Chapter("tactics", QuestCatalog.loadTactics()),
                new Chapter("training", QuestCatalog.loadTraining()),
                new Chapter("intermezzo", QuestCatalog.loadIntermezzo()));
        var identity = new LinkedHashMap<String,Object>();
        identity.put("revision", revision);
        identity.put("run", "native-catalog-" + revision.substring(0, 12));
        identity.put("scope", "Complete current ordinary native catalog, keys read after search; canonical interventions explicit");
        identity.put("nativePolicy", HardAdaptivePolicy.CURRENT);
        identity.put("promotionExchangePolicy", PromotionExchangePolicy.CURRENT);
        identity.put("pawnPushPolicy", PawnPushPolicy.CURRENT);
        identity.put("queenReplyPolicy", QueenReplyPolicy.CURRENT);
        identity.put("pawnBlockadePolicy", PawnBlockadePolicy.CURRENT);
        identity.put("initialQuietCounter", "MATCH_FROM_POSITION_STARTS_AT_ZERO");
        identity.put("unbudgetedBasePlies", MatchAiPolicy.BASE_PLIES);
        identity.put("repertoireExtraNodes", MatchAiPolicy.CURRENT.opponent(MatchSetup.Level.HARD).extraNodes());
        var catalog = new ArrayList<Object>();
        for (var chapter : chapters) for (var quest : chapter.quests())
            catalog.add(java.util.Map.of("chapter", chapter.name(), "id", quest.id(), "fen", quest.fen(),
                    "line", quest.line(), "objective", quest.objective()));
        Files.writeString(out.resolve("identity.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(identity), StandardOpenOption.CREATE_NEW);
        Files.writeString(out.resolve("catalog.json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(catalog), StandardOpenOption.CREATE_NEW);
        int total = 0;
        try (var writer = Files.newBufferedWriter(out.resolve("native.jsonl"), StandardOpenOption.CREATE_NEW)) {
            for (var chapter : chapters) {
                int exercises = 0;
                for (var quest : chapter.quests()) {
                    var game = ChessMatch.fromPosition(quest.position(), new MatchSetup(quest.position().turn().other(), MatchSetup.Level.HARD, false));
                    boolean prior = true, intervention = false;
                    for (int stage = 0; stage < quest.playerMoves(); stage++) {
                        long started = System.nanoTime();
                        var actual = game.analyzeBot(MatchSetup.Level.HARD);
                        long elapsed = System.nanoTime() - started;
                        HardQuestAudit.validate(game, actual);
                        // Only after the complete native receipt exists may the answer be consulted.
                        var acceptance = quest.assess(actual.decision().move(), stage);
                        boolean accepted = acceptance.continues() || acceptance.solved();
                        var body = new LinkedHashMap<String,Object>();
                        body.put("identity", identity); body.put("chapter", chapter.name()); body.put("id", quest.id());
                        body.put("stage", stage); body.put("rootFen", quest.fen());
                        body.put("checkpoint", game.checkpoint());
                        body.put("fen", StudyFen.replay(quest.fen(), quest.line().subList(0, stage * 2)));
                        body.put("analysis", actual); body.put("elapsedNanos", elapsed);
                        body.put("maximumExtraNodes", HardQuestAudit.maximumNodes(game.position()));
                        body.put("nativeQuietPlies", game.searchQuietPlies());
                        body.put("canonicalStageAccepted", accepted); body.put("priorNativeStagesAccepted", prior);
                        body.put("prefixReferenceIntervention", intervention);
                        body.put("referenceKeyReadAfterSearch", quest.playerMove(stage).uci());
                        body.put("catalogKeyMatched", actual.decision().move().equals(quest.playerMove(stage)));
                        var value = json.valueToTree(body);
                        writer.write(json.writeValueAsString(java.util.Map.of("body", value, "digest",
                                HardLeagueAudit.digest(HardLeagueAudit.canonical(value).getBytes(StandardCharsets.UTF_8)))));
                        writer.newLine(); writer.flush();
                        total++; prior &= accepted;
                        if (stage < quest.playerMoves() - 1 && !actual.decision().move().equals(quest.playerMove(stage))) intervention = true;
                        game = game.play(quest.playerMove(stage));
                        if (stage < quest.playerMoves() - 1) game = game.play(quest.defenseAfter(stage));
                    }
                    if (++exercises % 25 == 0) System.out.println("PUZZLES " + chapter.name() + " " + exercises + "/" + chapter.quests().size() + " stages=" + total);
                }
                System.out.println("CHAPTER_COMPLETE " + chapter.name() + " puzzles=" + exercises + " allStages=" + total);
            }
        }
        System.out.println("COMPLETE ordinary native stages=" + total);
    }
}
