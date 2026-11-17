package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Replays every analyzed and played ply before a native Hard calibration can be published. */
final class HardLeagueAudit {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> FILES = Set.of("moves.jsonl", "league.pgn", "results.json", "runtime.json", "freeze.json", "starts.tsv");
    record Verified(String revision, String runId, int games, int pairs, int analyses, int wins, int draws, int losses, int unfinished,
                    int anchor, PairedElo.Estimate deltaFromAnchor, LocalElo localElo) { }
    record LocalElo(double midpoint, double low95, double high95, double unfinishedAllLosses, double unfinishedAllWins) { }
    private static final class Replay {
        final JsonNode summary;
        final List<String> prefix;
        ChessMatch match = ChessMatch.start(new MatchSetup(ChessPosition.Side.WHITE, MatchSetup.Level.HARD, true));
        String last = "0".repeat(64);
        final MessageDigest raw = newDigest();
        int analyses;
        Replay(JsonNode summary, List<String> prefix) { this.summary = summary; this.prefix = prefix; }
    }

    static JsonNode trustedManifest(Path folder, String trustedDigest, String expectedRevision) throws IOException {
        var manifestPath = folder.resolve("manifest.json");
        require(trustedDigest != null && trustedDigest.matches("[a-f0-9]{64}") && digest(Files.readAllBytes(manifestPath)).equals(trustedDigest),
                "Calibration manifest differs from the separately trusted digest");
        var manifest = read(manifestPath);
        require(expectedRevision != null && expectedRevision.matches("[a-f0-9]{40}")
                && expectedRevision.equals(text(manifest.get("identity"), "sourceRevision")), "Foreign calibration source");
        var files = manifest.get("files");
        require(keys(files).equals(FILES), "Missing or unexpected calibration evidence");
        for (var name : FILES) {
            // Role names, not paths supplied by a received manifest, determine the files read.
            require(digest(Files.readAllBytes(folder.resolve(name))).equals(text(files.get(name), "sha256")),
                    "Changed calibration artifact: " + name);
        }
        return manifest;
    }

    static Verified verify(Path folder, String trustedDigest, String expectedRevision, Checks check) throws IOException {
        var manifest = trustedManifest(folder, trustedDigest, expectedRevision);
        var identity = manifest.get("identity");
        var results = read(folder.resolve("results.json"));
        var freeze = read(folder.resolve("freeze.json"));
        var settings = freeze.get("settings");
        require(identity.equals(results.get("identity")) && identity.equals(read(folder.resolve("runtime.json")).get("identity")), "Foreign run or runtime");
        require(text(identity, "freezeDigest").equals(digest(Files.readAllBytes(folder.resolve("freeze.json")))), "Foreign opening freeze");
        require(settings.equals(results.get("settings")), "Calibration settings changed");
        var anchor = settings.get("stockfish");
        require(text(anchor, "name").equals("Stockfish 19") && anchor.get("UCI_LimitStrength").asBoolean()
                && anchor.get("Threads").asInt() == 1 && anchor.get("nodes").asInt() == 6000, "Unexpected anchor protocol");
        validateSettings(settings);
        int pairs = settings.get("pairs").asInt();
        var starts = new LinkedHashMap<String, JsonNode>();
        var families = new java.util.HashSet<String>();
        for (var item : freeze.get("rows")) {
            var start = item;
            var prefix = ChessMatch.restore("3|WHITE|HARD|1|" + text(start,"moves"));
            require(prefix.ply() >= 4 && prefix.ply() <= 16 && prefix.result() == ChessMatch.Result.IN_PROGRESS, "Invalid calibration prefix");
            require(starts.putIfAbsent(text(start,"id"), start) == null && families.add(text(start,"family")), "Repeated opening pair/family");
        }
        require(starts.size() == pairs, "Incomplete opening deck");
        var games = new LinkedHashMap<String, Replay>();
        for (var item : results.get("games")) {
            var game = item;
            require(identity.equals(game.get("identity")), "Foreign game identity");
            var start = starts.get(text(game,"openingId"));
            require(start != null, "Unknown opening game");
            require(games.putIfAbsent(text(game,"gameId"), new Replay(game, List.of(text(start,"moves").split(" ")))) == null, "Duplicated game result");
        }
        require(games.size() == pairs * 2, "Missing paired game");
        int total = 0;
        try (var reader = Files.newBufferedReader(folder.resolve("moves.jsonl"), StandardCharsets.UTF_8)) {
            for (String raw; (raw = reader.readLine()) != null;) {
                var envelope = JSON.readTree(raw);
                var body = envelope.get("body");
                require(identity.equals(body.get("identity")), "Foreign move run/source");
                String id = text(body,"gameId");
                var game = games.get(id);
                require(game != null && text(body,"openingId").equals(text(game.summary,"openingId"))
                        && body.get("hardWhite").equals(game.summary.get("hardWhite")), "Foreign game phase");
                require(text(body,"previousDigest").equals(game.last) && text(envelope,"digest").equals(digest(canonical(body).getBytes(StandardCharsets.UTF_8))), "Altered move chain");
                var evidence = JSON.treeToValue(body.get("native"), HardMoveAudit.Entry.class);
                var before = game.match.position();
                var played = ChessPosition.Move.fromUci(text(body,"played"));
                var next = advance(game.match, evidence, played);
                boolean hardTurn = (before.turn() == ChessPosition.Side.WHITE) == game.summary.get("hardWhite").asBoolean();
                String actor = game.match.ply() < game.prefix.size() ? "OPENING_PREFIX" : hardTurn ? "HARD" : "STOCKFISH";
                require(text(body,"actor").equals(actor), "Foreign move actor");
                if (actor.equals("OPENING_PREFIX")) require(played.uci().equals(game.prefix.get(game.match.ply())), "Changed frozen prefix");
                if (actor.equals("HARD")) require(played.uci().equals(evidence.selected()), "A different move was played instead of the audited Hard choice");
                String phase = id + " ply " + game.match.ply();
                check.same(phase + " | full-history position", HintAuditSession.fen(game.match), evidence.fen());
                check.same(phase + " | actor=" + actor + " played=" + played.uci() + " Hard=" + evidence.selected()
                        + " H" + evidence.depth() + " nodes=" + evidence.extraNodes(), true, before.legalMoves().contains(played));
                for (var line : evidence.variations()) {
                    var end = before;
                    for (var move : line.line()) end = end.play(move);
                    String pv = line.line().stream().map(ChessPosition.Move::uci).collect(java.util.stream.Collectors.joining(" "));
                    var reason = line.ending() == TacticalAnalysis.Ending.HORIZON && line.line().size() < evidence.depth()
                            ? "THIRD_OCCURRENCE_PROVED_FROM_HISTORY_AND_PV" : line.ending().name();
                    check.same(phase + " | native score=" + line.score() + " " + reason + " PV=" + pv,
                            HintAuditSession.materialChangeCp(before, end), line.materialChange());
                }
                game.raw.update((raw + "\n").getBytes(StandardCharsets.UTF_8));
                game.match = next;game.last = text(envelope,"digest");game.analyses++;total++;
            }
        }
        var paired = new ArrayList<PairedElo.Pair>();int wins=0,draws=0,losses=0,unfinished=0;
        for (var opening : starts.keySet()) {
            var members = games.values().stream().filter(g -> text(g.summary,"openingId").equals(opening)).toList();
            require(members.size()==2 && members.get(0).summary.get("hardWhite").asBoolean()!=members.get(1).summary.get("hardWhite").asBoolean(), "Colors were not reversed");
            int w=0,d=0,l=0,u=0;
            for (var game : members) {
                require(game.analyses==game.match.ply() && game.analyses==game.summary.get("nativeAnalyses").asInt()
                        && game.analyses==game.summary.get("plies").asInt() && game.last.equals(text(game.summary,"finalDigest")), "Incomplete game audit");
                require(game.analyses > game.prefix.size() && HexFormat.of().formatHex(game.raw.digest()).equals(text(game.summary,"auditSha256")),
                        "No live play or changed per-game audit bytes");
                String result=switch(game.match.result()) {case IN_PROGRESS->"*";case WHITE_WINS->"1-0";case BLACK_WINS->"0-1";default->"1/2-1/2";};
                String status=result.equals("*")?"U":result.equals("1/2-1/2")?"D":result.equals("1-0")==game.summary.get("hardWhite").asBoolean()?"W":"L";
                require(result.equals(text(game.summary,"result")) && status.equals(text(game.summary,"status")), "Invented game result");
                require(!status.equals("U") || game.match.ply()==settings.get("absolutePlyCap").asInt(), "A premature stop was hidden as a capped game");
                switch(status) {case "W"->w++;case "D"->d++;case "L"->l++;case "U"->u++;default->throw new IllegalArgumentException("Unknown result");}
            }
            paired.add(new PairedElo.Pair(w,d,l,u));wins+=w;draws+=d;losses+=l;unfinished+=u;
        }
        require(total>0 && total==results.get("nativeAnalyses").asInt(), "Missing move-level analyses");
        var rating=PairedElo.estimate(paired,settings.get("bootstrapSamples").asInt(),settings.get("seed").asLong());
        check.same("Every played ply has a native analysis, including prefixes and opponent turns", total, games.values().stream().mapToInt(g->g.analyses).sum());
        check.same("Rating keeps unfinished games distinct from draws", wins+draws+losses+unfinished, games.size());
        check.same("Midpoint Elo agrees with the independent Python calculation", Math.round(results.get("rating").get("deltaMidpoint").asDouble()), Math.round(rating.eloMidpoint()));
        var rawRating = results.get("rating");
        require(rawRating.get("wins").asInt() == wins && rawRating.get("draws").asInt() == draws
                && rawRating.get("losses").asInt() == losses && rawRating.get("unfinished").asInt() == unfinished,
                "Published outcomes differ from the native replay");
        int anchorElo = anchor.get("UCI_Elo").asInt();
        var localElo = new LocalElo(anchorElo + rating.eloMidpoint(), anchorElo + rating.eloLow95(),
                anchorElo + rating.eloHigh95(), anchorElo + PairedElo.elo(rating.scoreLow()), anchorElo + PairedElo.elo(rating.scoreHigh()));
        return new Verified(expectedRevision,text(identity,"runId"),games.size(),pairs,total,wins,draws,losses,unfinished,anchorElo,rating,localElo);
    }

    static void validateSettings(JsonNode settings) {
        int pairs=settings.get("pairs").asInt();
        int cap=settings.get("absolutePlyCap").asInt();
        int samples=settings.get("bootstrapSamples").asInt();
        var hard=settings.get("hard");
        require(pairs>=2 && settings.get("games").asInt()==pairs*2 && cap>=100 && cap<=300
                && samples>=1000 && samples<=50000, "Invalid paired sample or rating limits");
        require(hard.get("maximumExtraNodes").asInt()==200000 && hard.get("analysisBeforeEveryPlayedPly").asBoolean()
                && hard.get("shadowOnOpponentTurn").asBoolean(), "Native analysis must cover every played position");
    }

    static ChessMatch advance(ChessMatch expected, HardMoveAudit.Entry evidence, ChessPosition.Move played) {
        require(evidence.checkpoint().equals(expected.checkpoint()), "Missing, repeated or reordered move analysis");
        HardMoveAudit.validate(evidence);
        return expected.play(played);
    }

    static String canonical(JsonNode value) {
        try { return JSON.writeValueAsString(sorted(value)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IllegalArgumentException("Invalid canonical receipt", error); }
    }

    private static JsonNode sorted(JsonNode value) {
        if (value.isObject()) {
            var result = JSON.createObjectNode();
            value.properties().stream().sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> result.set(entry.getKey(), sorted(entry.getValue())));
            return result;
        }
        if (value.isArray()) {
            var result = JSON.createArrayNode();
            value.forEach(entry -> result.add(sorted(entry)));
            return result;
        }
        return value;
    }

    private static Set<String> keys(JsonNode value) {
        var result = new java.util.HashSet<String>();
        value.fieldNames().forEachRemaining(result::add);
        return result;
    }
    static String digest(byte[] bytes) {return HexFormat.of().formatHex(newDigest().digest(bytes));}
    private static MessageDigest newDigest() {try{return MessageDigest.getInstance("SHA-256");}catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException(error);}}
    private static JsonNode read(Path path) throws IOException {return JSON.readTree(Files.readString(path,StandardCharsets.UTF_8));}
    private static String text(JsonNode object,String name) {return object.get(name).asText();}
    private static void require(boolean condition,String message) {if(!condition)throw new IllegalArgumentException(message);}
}
