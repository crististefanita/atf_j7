package ro.atf.chess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.IntConsumer;

/** Additive post-game review: it never rewrites the original pre-move journal. */
final class LearningReviewAudit {
    static void validateControlInventory(java.util.Set<String> collectedCases,int soundControls) {
        require(soundControls==(collectedCases.contains("sound-queen-offer")?1:0),
                "Missing, duplicated or invented sound-sacrifice control");
    }
    enum Mode { FINISHED_GAME, CAPPED_LINE }
    record Player(Integer accuracy, GameReview.Rating localRating, int ratedMoves, Map<GameReview.Grade,Integer> grades) { }
    record Why(GameReviewExplanation.Reason reason, ChessPosition.Side side, int loss, int depth,
               int materialCp, GameReviewExplanation.MoveEvidence evidence) { }
    record Snapshot(List<GameReview.Turn> turns, GameReview.Opening opening, int bookPlies, int initialWhitePoints,
                    Player white, Player black, List<Why> explanations) { }
    record Body(LearningGameAudit.Identity source, LearningGameAudit.Identity reviewer, String caseId,
                String journalSha256, String finalCheckpoint, ChessMatch.Result result, Mode mode,
                String reviewPolicySha256, int configuredReviewExtraNodes, List<Integer> observedPlies,
                String encodedReview, Snapshot snapshot) { }
    record Envelope(Body body, String digest) { }
    record Tail(int index, ChessPosition.Side lastMover, ChessPosition.Move move,
                ChessPosition.Kind movedKind, ChessPosition.Kind capturedKind, ChessPosition.Kind resultingKind,
                boolean terminal, List<ChessPosition.Move> legalCapturesOfMovedPiece) { }

    static Envelope capture(List<LearningGameAudit.Envelope> journal, String journalSha256,
                            LearningGameAudit.Identity reviewer, String policySha256) {
        return capture(journal,journalSha256,reviewer,policySha256,(game,progress)->game.result()==ChessMatch.Result.IN_PROGRESS
                ? GameReview.analyzeLine(game,progress) : GameReview.analyze(game,progress));
    }

    static Envelope capture(List<LearningGameAudit.Envelope> journal, String journalSha256,
                            LearningGameAudit.Identity reviewer, String policySha256,
                            BiFunction<ChessMatch,IntConsumer,GameReview> engine) {
        var origin=journal.getFirst().body().before();
        var game=LearningGameAudit.validate(journal,origin.identity(),origin.caseId(),origin.initialFen());
        String checkpoint=game.checkpoint();var observed=new ArrayList<Integer>();
        var review=engine.apply(game,observed::add);
        require(checkpoint.equals(game.checkpoint()),"Review changed the actual history");
        var body=new Body(origin.identity(),reviewer,origin.caseId(),journalSha256,checkpoint,game.result(),
                mode(game),policySha256,GameReviewPolicy.CURRENT.search.extraNodes(),List.copyOf(observed),
                GameReviewCodec.encode(game,review),snapshot(game,review));
        var envelope=new Envelope(body,digest(body));
        validate(envelope,journal,journalSha256,reviewer,policySha256);return envelope;
    }

    static GameReview validate(Envelope envelope,List<LearningGameAudit.Envelope> journal,String expectedJournal,
                              LearningGameAudit.Identity expectedReviewer,String expectedPolicy) {
        require(envelope!=null&&envelope.body()!=null&&envelope.digest().equals(digest(envelope.body())),"Changed review receipt");
        var origin=journal.getFirst().body().before();var body=envelope.body();
        var game=LearningGameAudit.validate(journal,origin.identity(),origin.caseId(),origin.initialFen());
        require(expectedJournal.matches("[a-f0-9]{64}")&&expectedPolicy.matches("[a-f0-9]{64}"),"Missing independent journal or policy digest");
        require(body.source().equals(origin.identity())&&body.reviewer().equals(expectedReviewer)&&body.caseId().equals(origin.caseId()),"Foreign review source, producer or game");
        require(body.journalSha256().equals(expectedJournal)&&body.reviewPolicySha256().equals(expectedPolicy),"Foreign journal or review policy");
        require(body.finalCheckpoint().equals(game.checkpoint())&&body.result()==game.result()&&body.mode()==mode(game),"Foreign final checkpoint or invented completion");
        require(body.observedPlies().equals(java.util.stream.IntStream.rangeClosed(1,game.ply()).boxed().toList()),"Partial or repeated review progress");
        require(body.configuredReviewExtraNodes()==GameReviewPolicy.CURRENT.search.extraNodes(),"Foreign review allowance");
        var decoded=game.result()==ChessMatch.Result.IN_PROGRESS
                ? GameReviewCodec.decodeLine(game,body.encodedReview(),GameReviewCodec.checkpointDigest(game))
                : GameReviewCodec.decode(game,body.encodedReview());
        require(body.snapshot().equals(snapshot(game,decoded)),"Readable review differs from its retained cache");
        new GameReviewTimeline(game,decoded);
        for(var turn:decoded.turns()) {
            require(turn.depth()>0&&turn.depth()<=GameReviewPolicy.CURRENT.search.maxPlies(),"Invalid completed review horizon");
            require(turn.loss()==Math.max(0,turn.bestPoints()-turn.playedPoints())
                    &&turn.whitePoints()==(turn.side()==ChessPosition.Side.WHITE?turn.playedPoints():1000-turn.playedPoints()),"Foreign review score perspective");
            var before=game.positionAt(turn.ply()-1);
            require(!turn.bestLine().isEmpty()&&!turn.playedLine().isEmpty()&&turn.playedLine().getFirst().equals(turn.played()),"Missing reviewed alternatives");
            for(var path:List.of(turn.bestLine(),turn.playedLine())) {var end=before;for(var move:path)end=end.play(move);}
        }
        return decoded;
    }

    static Tail tail(ChessPosition origin,List<ChessPosition.Move> path) {
        require(!path.isEmpty(),"An empty hint has no final move");var before=origin;
        for(int i=0;i<path.size()-1;i++)before=before.play(path.get(i));
        var move=path.getLast();var piece=before.pieceAt(move.from());var victim=MatchMaterial.victim(before,move);
        var end=before.play(move);var captures=end.legalMoves().stream().filter(reply->reply.to()==move.to()&&MatchMaterial.victim(end,reply)!=null).toList();
        return new Tail(path.size()-1,before.turn(),move,piece.kind(),victim==null?null:victim.kind(),end.pieceAt(move.to()).kind(),!end.hasLegalMove(),captures);
    }

    static Snapshot snapshot(ChessMatch game,GameReview review) {
        var timeline=new GameReviewTimeline(game,review);var why=new ArrayList<Why>();
        for(var turn:review.turns()) {
            timeline.seek(turn.ply());var explanation=timeline.explanation();
            why.add(explanation.map(e->new Why(e.reason(),e.side(),e.loss(),e.depth(),e.materialCp(),e.evidence().orElse(null))).orElse(null));
        }
        return new Snapshot(review.turns(),review.opening(),review.bookPlies(),review.initialWhitePoints(),player(review.white()),player(review.black()),java.util.Collections.unmodifiableList(why));
    }
    static String digest(Body body){return HardLeagueAudit.digest(HardLeagueAudit.canonical(LearningGameAudit.mapper().valueToTree(body)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}

    /** Compare separately validated cold receipts without rewriting either run's identity. */
    static double oracleLoss(com.fasterxml.jackson.databind.JsonNode free,com.fasterxml.jackson.databind.JsonNode forced) {
        require(free!=null&&forced!=null,"Missing independently validated oracle phase");
        var a=free.path("query");var b=forced.path("query");var ai=free.path("identity");var bi=forced.path("identity");
        require(a.path("root").isNull()&&b.path("root").isTextual(),"Foreign free/forced roles");
        require(a.path("history").isArray()&&a.path("history").equals(b.path("history"))&&a.path("initialFen").isTextual()&&a.path("initialFen").equals(b.path("initialFen")),"Foreign full history or origin");
        require((a.path("depth").asInt()==17||a.path("depth").asInt()==19)&&a.path("depth").equals(b.path("depth")),"Different oracle horizons");
        require(ai.path("source").isObject()&&ai.path("source").equals(bi.path("source")),"Different measured producer");
        require(a.path("binarySha256").asText().matches("[a-f0-9]{64}")&&a.path("binarySha256").equals(b.path("binarySha256"))&&a.path("binarySha256").equals(ai.path("binarySha256"))&&a.path("binarySha256").equals(bi.path("binarySha256")),"Different oracle binary");
        var options=a.path("options");
        require(options.equals(b.path("options"))&&options.equals(ai.path("options"))&&options.equals(bi.path("options"))&&options.path("Threads").asInt()==1&&options.path("Hash").asInt()==32&&options.path("UCI_LimitStrength").isBoolean()&&!options.path("UCI_LimitStrength").asBoolean()&&options.path("UCI_ShowWDL").asBoolean(),"Different cold oracle configuration");
        double x=free.path("expectedPermille").asDouble(-1),y=forced.path("expectedPermille").asDouble(-1);
        require(x>=0&&x<=1000&&y>=0&&y<=1000,"Missing validated WDL scale");return Math.max(0,x-y);
    }
    private static Player player(GameReview.Player p){return new Player(p.accuracy().isPresent()?p.accuracy().getAsInt():null,p.rating().orElse(null),p.ratedMoves(),p.grades());}
    private static Mode mode(ChessMatch game){return game.result()==ChessMatch.Result.IN_PROGRESS?Mode.CAPPED_LINE:Mode.FINISHED_GAME;}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
