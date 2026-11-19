package ro.atf.chess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/** New observations only: both searches precede every committed White or Black move. */
final class LearningGameAudit {
    static final int MAX_HINT_NODES=200_000;
    static final String GENESIS="0".repeat(64);
    enum Actor { HARD, STOCKFISH, THEORY }
    record Identity(String run,String revision,String runtimeDigest,String configurationDigest) {
        Identity {require(run.matches("[a-z0-9-]+")&&revision.matches("[a-f0-9]{40}")&&runtimeDigest.matches("[a-f0-9]{64}")&&configurationDigest.matches("[a-f0-9]{64}"),"Exact producer identity required");}
    }
    record Frame(Identity identity,String caseId,String initialFen,int ply,List<ChessPosition.Move> history,
                 MatchSetup setup,String checkpoint,String fen,String side,String nativeLevel,int requestedHintNodes,
                 ChessMatch.BotAnalysis hard,TacticalAnalysis.HintSearch hint,int separateEstimateLimit,MatchEvaluation estimate) {
        Frame {history=List.copyOf(history);}
    }
    record Body(Frame before,Actor actor,ChessPosition.Move played,String choiceEvidence,String previousDigest) { }
    record Envelope(Body body,String digest) { }
    private static final ObjectMapper JSON=mapper();
    private final Identity identity;
    private final String caseId,initialFen;
    private final Consumer<Envelope> journal;
    private final Function<ChessMatch,ChessMatch.BotAnalysis> hard;
    private final BiFunction<ChessPosition,Integer,TacticalAnalysis.HintSearch> hints;
    private ChessMatch game;
    private Frame pending;
    private String previous=GENESIS;

    LearningGameAudit(Identity identity,String caseId,String initialFen,ChessMatch game,Consumer<Envelope> journal) {
        this(identity,caseId,initialFen,game,journal,g->g.analyzeBot(MatchSetup.Level.HARD),TacticalAnalysis::verifiedHints);
    }
    LearningGameAudit(Identity identity,String caseId,String initialFen,ChessMatch game,Consumer<Envelope> journal,
                      Function<ChessMatch,ChessMatch.BotAnalysis> hard,BiFunction<ChessPosition,Integer,TacticalAnalysis.HintSearch> hints) {
        this.identity=Objects.requireNonNull(identity);this.caseId=caseId;this.initialFen=initialFen;this.game=Objects.requireNonNull(game);
        this.journal=Objects.requireNonNull(journal);this.hard=Objects.requireNonNull(hard);this.hints=Objects.requireNonNull(hints);
        require(caseId.matches("[a-z0-9-]+")&&game.ply()==0&&game.result()==ChessMatch.Result.IN_PROGRESS,"A new complete study origin is required; no retrospective prefix upgrade");
        requireZeroClock(initialFen);
        require(game.setup().level()==MatchSetup.Level.HARD,"The learning session uses Hard");
        require(game.position().searchKey().equals(ChessPosition.fromFen(initialFen).searchKey()),"Foreign initial board");
        require(MatchAiPolicy.CURRENT.hint.search().extraNodes()==MAX_HINT_NODES,"Learning must request the maximum public hint allowance");
    }
    ChessMatch match(){return game;}
    Frame observe() {
        if(pending==null) {
            require(game.result()==ChessMatch.Result.IN_PROGRESS,"The game has ended");
            var nativeSearch=Objects.requireNonNull(hard.apply(game));
            var hint=Objects.requireNonNull(hints.apply(game.position(),MAX_HINT_NODES));
            if(hint.proof()!=null&&!hint.proof().complete())throw new CoachingProof.Incomplete(hint.proof());
            var history=history(game);String fen=StudyFen.replay(initialFen,history);
            pending=new Frame(identity,caseId,initialFen,game.ply(),history,game.setup(),game.checkpoint(),fen,game.position().turn().name(),"HARD",MAX_HINT_NODES,
                    nativeSearch,hint,MatchAiPolicy.CURRENT.evaluation.nodes(),hint.proof()==null?MatchEvaluation.forPosition(game.position(),hint.lines().getFirst()):MatchEvaluation.from(game.position().turn(),hint.lines().getFirst()));
            validateFrame(pending,game);
        }return pending;
    }
    Envelope play(ChessPosition.Move move,Actor actor,String choiceEvidence) {
        require(pending!=null,"Observe Hard and the maximum hint before every actual move, including the opponent");
        require(game.position().legalMoves().contains(move),"Illegal played move");
        require(actor!=Actor.HARD||pending.hard().decision().move().equals(move),"The actual Hard move cannot borrow another decision");
        require(actor!=Actor.STOCKFISH||choiceEvidence!=null&&choiceEvidence.matches("[a-f0-9]{64}"),"A Stockfish move needs its actual free oracle receipt");
        var body=new Body(pending,Objects.requireNonNull(actor),move,choiceEvidence,previous);var receipt=new Envelope(body,digest(body));
        journal.accept(receipt); // The durable writer must finish before the match advances.
        game=game.play(move);previous=receipt.digest();pending=null;return receipt;
    }
    static ChessMatch validate(List<Envelope> receipts,Identity expected,String caseId,String initialFen) {
        require(!receipts.isEmpty(),"An empty game is not learning evidence");
        requireZeroClock(initialFen);var setup=receipts.getFirst().body().before().setup();require(setup.level()==MatchSetup.Level.HARD,"A foreign playing level cannot replace Hard");
        var board=ChessPosition.fromFen(initialFen);var game=ChessMatch.fromPosition(board,setup);
        String previous=GENESIS;
        for(var receipt:receipts) {
            var body=receipt.body();var frame=body.before();
            require(body.actor()!=null,"Missing actual move actor");
            require(frame.identity().equals(expected)&&frame.caseId().equals(caseId)&&frame.initialFen().equals(initialFen),"Foreign producer, game or origin");
            require(receipt.digest().equals(digest(body))&&body.previousDigest().equals(previous),"Changed, missing or reordered pre-move receipt");
            validateFrame(frame,game);
            require(body.actor()!=Actor.HARD||body.played().equals(frame.hard().decision().move()),"Substituted actual native move");
            require(body.actor()!=Actor.STOCKFISH||body.choiceEvidence()!=null&&body.choiceEvidence().matches("[a-f0-9]{64}"),"Missing actual opponent source");
            game=game.play(body.played());previous=receipt.digest();
        }return game;
    }
    static void validateFrame(Frame frame,ChessMatch game) {
        require(frame.ply()==game.ply()&&frame.history().equals(history(game))&&frame.setup().equals(game.setup())&&frame.checkpoint().equals(game.checkpoint()),"Foreign full actual prefix or after-move frame");
        require(frame.side().equals(game.position().turn().name())&&frame.fen().equals(StudyFen.replay(frame.initialFen(),frame.history())),"Foreign side, FEN, clock or en passant");
        require(frame.nativeLevel().equals("HARD")&&frame.requestedHintNodes()==MAX_HINT_NODES,"Missing Hard or maximum requested hint");
        require(frame.hard()!=null&&frame.hint()!=null&&frame.estimate()!=null,"Incomplete native/hint frame");
        require(frame.hard().search().scoreCredits().isEmpty(),"Native search cannot inherit hint credits");HardQuestAudit.validate(game,frame.hard());
        var hint=frame.hint();var comparison=hint.comparison();
        HardHintAuditedGame.validateHint(game.position(),new HintAuditSession.Search(hint.lines(),comparison==null?List.of():comparison.result().ranked(),
                hint.extraNodes(),hint.selectedDepth(),hint.seedDepth(),comparison==null?0:comparison.depth(),hint.proof()));
        require(frame.separateEstimateLimit()==MatchAiPolicy.CURRENT.evaluation.nodes(),"The separate estimate cannot be hidden in the hint budget");
    }
    static List<ChessPosition.Move> history(ChessMatch match) {var result=new ArrayList<ChessPosition.Move>();for(int ply=1;ply<=match.ply();ply++)result.add(match.moveAt(ply));return List.copyOf(result);}
    static String digest(Body body) {return HardLeagueAudit.digest(HardLeagueAudit.canonical(JSON.valueToTree(body)).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    static ObjectMapper mapper() {
        var module=new SimpleModule();
        module.addKeySerializer(ChessPosition.Move.class,new com.fasterxml.jackson.databind.JsonSerializer<>() {
            @Override public void serialize(ChessPosition.Move value,com.fasterxml.jackson.core.JsonGenerator output,com.fasterxml.jackson.databind.SerializerProvider provider)throws java.io.IOException{output.writeFieldName(value.uci());}
        });
        module.addKeyDeserializer(ChessPosition.Move.class,new com.fasterxml.jackson.databind.KeyDeserializer(){@Override public Object deserializeKey(String key,com.fasterxml.jackson.databind.DeserializationContext context){return ChessPosition.Move.fromUci(key);}});
        return new ObjectMapper().registerModule(module)
                .addMixIn(TacticalAnalysis.HintSearch.class,OptionalProof.class)
                .addMixIn(GameReview.Turn.class,OptionalProof.class)
                .addMixIn(HintAuditSession.Search.class,OptionalProof.class)
                .addMixIn(HintAuditSession.Decision.class,OptionalProof.class);
    }
    private abstract static class OptionalProof {
        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        abstract CoachingProof.Work proof();
    }
    private static void requireZeroClock(String fen) {var fields=fen.trim().split("\\s+");require(fields.length==6&&fields[4].equals("0"),"Use an explicit zero-clock study origin until composed FEN-clock restoration is qualified");}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalArgumentException(message);}
}
