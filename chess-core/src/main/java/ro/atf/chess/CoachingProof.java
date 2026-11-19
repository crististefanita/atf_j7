package ro.atf.chess;

import java.util.*;
import java.util.concurrent.CancellationException;
import ro.atf.chess.ChessPosition.Move;

/** A separately charged common-horizon comparison whose frontier resolves captures and check evasions. */
public final class CoachingProof {
    private static final int MATE=100_000, LIMIT=MATE+1;
    private static final Map<?,?> MATERIAL=ChessYaml.map(ChessYaml.resource("tactical-analysis.yaml"),"material");
    static final Policy CURRENT=Policy.read(ChessYaml.resource("coaching-proof.yaml"));

    public enum Status { COMPLETE, NODE_LIMIT, FORCING_LIMIT }
    /** Two deeper free-reply comparisons, rather than a fixed refutation appended to a PV. */
    public record Counterplay(int earlierPlies,TacticalAnalysis.Variation earlierIncumbent,
                              TacticalAnalysis.Variation earlierChallenger,TacticalAnalysis.Variation incumbent,
                              TacticalAnalysis.Variation challenger,ChessPosition.Move selected,int leaderScore,
                              int minGainCp,int minMaterialLoss) {
        public Counterplay {
            if(earlierPlies<5||minGainCp<0||minMaterialLoss<1||earlierIncumbent==null||earlierChallenger==null||incumbent==null||challenger==null
                    ||!earlierIncumbent.move().equals(incumbent.move())||!earlierChallenger.move().equals(challenger.move())
                    ||incumbent.move().equals(challenger.move())||selected==null||leaderScore<Math.max(incumbent.score(),challenger.score())||!selected.equals(accepts(earlierIncumbent,earlierChallenger,incumbent,challenger,leaderScore,minGainCp,minMaterialLoss)?challenger.move():incumbent.move()))
                throw new IllegalArgumentException("Invalid free-counterplay confirmation");
        }
        public boolean accepted(){return selected.equals(challenger.move());}
        private static boolean accepts(TacticalAnalysis.Variation before,TacticalAnalysis.Variation alternative,
                                       TacticalAnalysis.Variation now,TacticalAnalysis.Variation next,int best,int gap,int loss) {
            return before.materialChange()<=-loss&&now.materialChange()<=-loss
                    &&alternative.score()-before.score()>gap&&next.score()-now.score()>gap&&best-next.score()<=gap;
        }
    }
    public record Confirmation(int plies,Move incumbent,Move challenger,Move selected,int incumbentScore,int challengerScore,
                               int earlierIncumbentScore,int earlierChallengerScore,int minGainCp) {
        public Confirmation {
            if(plies<2||plies%2!=0||incumbent==null||challenger==null||selected==null||incumbent.equals(challenger)
                    ||minGainCp<0||!selected.equals(earlierChallengerScore>earlierIncumbentScore&&challengerScore-incumbentScore>minGainCp?challenger:incumbent))throw new IllegalArgumentException("Invalid equal-turn confirmation");
        }
    }
    public record Work(Status status, int nodes, int allocatedNodes, int ceiling, int ordinaryPlies,
                       int forcingPlies, int captureReplies, int checkEvasions, int repeatedDraws,
                       String positionKey,List<Move> roots,int ordinaryNodes,int boundedRoots,Confirmation confirmation,int tacticalPlies,Counterplay counterplay) {
        public Work(Status status,int nodes,int allocatedNodes,int ceiling,int ordinaryPlies,int forcingPlies,int captureReplies,int checkEvasions,int repeatedDraws,
                    String positionKey,List<Move> roots,int ordinaryNodes,int boundedRoots,Confirmation confirmation,int tacticalPlies) {
            this(status,nodes,allocatedNodes,ceiling,ordinaryPlies,forcingPlies,captureReplies,checkEvasions,repeatedDraws,positionKey,roots,ordinaryNodes,boundedRoots,confirmation,tacticalPlies,null);
        }
        public Work {
            roots=List.copyOf(roots);
            if(status==null||nodes<0||nodes>allocatedNodes||allocatedNodes>ceiling||ceiling<1||ceiling>5_000_000||ordinaryPlies<1
                    ||ordinaryPlies>MatchAiPolicy.MAX_PLIES||forcingPlies<0||forcingPlies>48||captureReplies<0
                    ||checkEvasions<0||repeatedDraws<0||positionKey==null||positionKey.isBlank()||roots.isEmpty()
                    ||new HashSet<>(roots).size()!=roots.size()||ordinaryNodes<0||boundedRoots<0||boundedRoots>roots.size()||tacticalPlies<0||tacticalPlies>48
                    ||confirmation!=null&&(!completeStatus(status)||confirmation.plies()>=ordinaryPlies||!roots.contains(confirmation.incumbent())||!roots.contains(confirmation.challenger()))
                    ||counterplay!=null&&(!completeStatus(status)||counterplay.earlierPlies()+1!=ordinaryPlies||!roots.contains(counterplay.incumbent().move())||!roots.contains(counterplay.challenger().move())))throw new IllegalArgumentException("Invalid tactical work receipt");
        }
        private static boolean completeStatus(Status status){return status==Status.COMPLETE;}
        public boolean complete(){return status==Status.COMPLETE;}
        public int totalNodes(){return ordinaryNodes+nodes;}
        Work afterOrdinary(int nodes){return new Work(status,this.nodes,allocatedNodes,ceiling,ordinaryPlies,forcingPlies,captureReplies,checkEvasions,repeatedDraws,positionKey,roots,nodes,boundedRoots,confirmation,tacticalPlies,counterplay);}
        void validate(ChessPosition position,int depth) {
            if(!positionKey.equals(position.searchKey().toString())||ordinaryPlies!=depth||!position.legalMoves().containsAll(roots))throw new IllegalArgumentException("Foreign tactical proof context");
            if(counterplay!=null)for(var line:List.of(counterplay.earlierIncumbent(),counterplay.earlierChallenger(),counterplay.incumbent(),counterplay.challenger())) {
                var end=position;for(var move:line.line())end=end.play(move);
                if(line.materialChange()!=material(end,position.turn())-material(position,position.turn()))throw new IllegalArgumentException("Foreign counterplay material ledger");
            }
        }
    }
    record Policy(int maxNodes,int allocationStep,int maxForcingPlies) {
        Policy {
            if(maxNodes<1||maxNodes>5_000_000||allocationStep<1||allocationStep>maxNodes||maxForcingPlies<1||maxForcingPlies>48)throw new IllegalArgumentException("Invalid coaching proof limits");
        }
        static Policy read(Map<?,?> row) {
            if(!row.keySet().equals(Set.of("schema","max-nodes","allocation-step","max-forcing-plies"))
                    ||ChessYaml.number(row,"schema")!=1)throw new IllegalArgumentException("Invalid coaching proof policy");
            int max=ChessYaml.number(row,"max-nodes"),step=ChessYaml.number(row,"allocation-step"),plies=ChessYaml.number(row,"max-forcing-plies");
            if(max<1||max>5_000_000||step<1||step>max||plies<1||plies>48)throw new IllegalArgumentException("Invalid coaching proof limits");
            return new Policy(max,step,plies);
        }
    }
    record Result(List<TacticalAnalysis.Variation> lines,Work work) {
        Result {lines=List.copyOf(lines);if(!work.complete()&&!lines.isEmpty())throw new IllegalArgumentException("Incomplete tactical proof cannot carry scored lines");}
    }

    /** A new frontier model must not bypass the existing equal-turn stability contract. */
    static Result confirmLeader(ChessPosition position,Result current,Move incumbent,Policy policy) {
        if(!current.work().complete()||current.lines().get(0).move().equals(incumbent)||current.work().ordinaryPlies()<4)return current;
        var old=current.lines().stream().filter(v->v.move().equals(incumbent)).findFirst().orElseThrow();
        var challenger=current.lines().get(0);int depth=current.work().ordinaryPlies();int prior=depth%2==0?depth-2:depth-1;
        if(old.score()==challenger.score())return new Result(List.of(old),current.work());
        int remaining=policy.maxNodes()-current.work().nodes();
        Result earlier=remaining==0?null:compare(position,List.of(incumbent,challenger.move()),prior,Set.of(),new Policy(remaining,Math.min(policy.allocationStep(),remaining),policy.maxForcingPlies()),Set.of(),current.work().tacticalPlies());
        Work a=current.work(),b=earlier==null?null:earlier.work();int used=a.nodes()+(b==null?0:b.nodes());
        Status status=b==null?Status.NODE_LIMIT:b.status();
        Confirmation choice=null;boolean retain=false;
        if(status==Status.COMPLETE) {
            var previous=earlier.lines().stream().filter(v->v.move().equals(incumbent)).findFirst().orElseThrow();
            var alternative=earlier.lines().stream().filter(v->v.move().equals(challenger.move())).findFirst().orElseThrow();
            int minGain=MatchAiPolicy.CURRENT.hint.maxLossCp();
            retain=alternative.score()<=previous.score()||challenger.score()-old.score()<=minGain;
            choice=new Confirmation(prior,incumbent,challenger.move(),retain?incumbent:challenger.move(),old.score(),challenger.score(),previous.score(),alternative.score(),minGain);
        }
        int allocation=(int)Math.min(policy.maxNodes(),((long)used+policy.allocationStep()-1)/policy.allocationStep()*policy.allocationStep());
        var work=new Work(status,used,allocation,policy.maxNodes(),a.ordinaryPlies(),Math.max(a.forcingPlies(),b==null?0:b.forcingPlies()),a.captureReplies()+(b==null?0:b.captureReplies()),a.checkEvasions()+(b==null?0:b.checkEvasions()),a.repeatedDraws()+(b==null?0:b.repeatedDraws()),a.positionKey(),a.roots(),a.ordinaryNodes(),a.boundedRoots(),choice,a.tacticalPlies());
        return new Result(status!=Status.COMPLETE?List.of():retain?List.of(old):current.lines(),work);
    }
    /** Review is transactional: no partial classification, graph or cache may be published. */
    public static final class Incomplete extends IllegalStateException {
        private final Work work;
        Incomplete(Work work){super("Tactical verification incomplete: "+work.status()+" after "+work.nodes()+" nodes");this.work=work;}
        public Work work(){return work;}
    }

    private CoachingProof() { }

    static boolean shortCheckingLine(ChessPosition board,TacticalAnalysis.Variation line,int depth) {
        return depth<=MatchAiPolicy.BASE_PLIES+1&&line.ending()==TacticalAnalysis.Ending.HORIZON
                &&line.signedMatePlies()==0&&MatchMaterial.victim(board,line.move())==null
                &&line.move().promotion()==null&&board.playGenerated(line.move()).isCheck();
    }

    static Result counterplay(ChessPosition board,List<Move> roots,Move incumbent,int tactical,Policy policy,Set<Move> draws,Set<Move> required) {
        int earlierDepth=MatchAiPolicy.BASE_PLIES+2,depth=earlierDepth+1;
        var exact=new HashSet<>(required);exact.add(incumbent);
        var early=compare(board,roots,earlierDepth,draws,policy,exact,tactical);
        if(!early.work().complete())return early;
        var candidate=early.lines().get(0);exact.add(candidate.move());
        int gap=MatchAiPolicy.CURRENT.hint.maxLossCp(),loss=value(ChessPosition.Kind.KNIGHT)-value(ChessPosition.Kind.PAWN);
        var initial=variation(early,incumbent);
        if(candidate.move().equals(incumbent))return early;
        if(initial.materialChange()>-loss||candidate.score()-initial.score()<=gap) {
            var stable=confirmLeader(board,early,incumbent,policy);
            return new Result(stable.work().complete()?early.lines():List.of(),stable.work());
        }
        int remaining=policy.maxNodes()-early.work().nodes();
        if(remaining==0)return new Result(List.of(),combine(early.work(),early.work(),Status.NODE_LIMIT,policy,0,null));
        var late=compare(board,roots,depth,draws,new Policy(remaining,Math.min(policy.allocationStep(),remaining),policy.maxForcingPlies()),exact,tactical);
        if(!late.work().complete())return new Result(List.of(),combine(early.work(),late.work(),late.work().status(),policy,late.work().nodes(),null));
        Counterplay facts=null;var old=variation(late,incumbent);
        if(!candidate.move().equals(incumbent)) {
            var before=variation(early,incumbent);var next=variation(late,candidate.move());
            boolean accepted=Counterplay.accepts(before,candidate,old,next,late.lines().get(0).score(),gap,loss);
            facts=new Counterplay(earlierDepth,before,candidate,old,next,accepted?next.move():incumbent,late.lines().get(0).score(),gap,loss);
        }
        // Keep the stable H5 candidate when H6 differs only within the existing equivalence window.
        return new Result(late.lines(),combine(early.work(),late.work(),Status.COMPLETE,policy,late.work().nodes(),facts));
    }
    static Move selected(Result result) {
        var work=result.work();return work.counterplay()!=null?work.counterplay().selected():work.confirmation()!=null?work.confirmation().selected():result.lines().get(0).move();
    }
    private static TacticalAnalysis.Variation variation(Result result,Move root){return result.lines().stream().filter(v->v.move().equals(root)).findFirst().orElseThrow();}
    private static Work combine(Work early,Work late,Status status,Policy policy,int laterNodes,Counterplay counterplay) {
        int nodes=early.nodes()+laterNodes;int allocation=(int)Math.min(policy.maxNodes(),((long)nodes+policy.allocationStep()-1)/policy.allocationStep()*policy.allocationStep());
        return new Work(status,nodes,allocation,policy.maxNodes(),late.ordinaryPlies(),Math.max(early.forcingPlies(),late.forcingPlies()),early.captureReplies()+(laterNodes==0?0:late.captureReplies()),early.checkEvasions()+(laterNodes==0?0:late.checkEvasions()),early.repeatedDraws()+(laterNodes==0?0:late.repeatedDraws()),early.positionKey(),early.roots(),0,late.boundedRoots(),null,early.tacticalPlies(),counterplay);
    }

    static boolean needed(ChessPosition start,List<TacticalAnalysis.Variation> lines) {
        return frontierNeeded(start,lines)||interiorOffer(start,lines);
    }

    static int verificationPlies(ChessPosition start,List<TacticalAnalysis.Variation> lines,Policy policy,int ordinary) {
        return interiorOffer(start,lines)?policy.maxForcingPlies():ordinary;
    }

    private static boolean frontierNeeded(ChessPosition start,List<TacticalAnalysis.Variation> lines) {
        for(var line:lines) {
            if(line.ending()!=TacticalAnalysis.Ending.HORIZON||line.line().isEmpty())continue;
            var before=start;for(var move:line.line().subList(0,line.line().size()-1))before=before.playGenerated(move);
            var last=line.line().get(line.line().size()-1);var after=before.playGenerated(last);
            if(!after.hasLegalMove())continue;
            if(after.isCheck())return true;
            boolean capture=MatchMaterial.victim(before,last)!=null;
            boolean important=before.pieceAt(last.from()).kind()!=ChessPosition.Kind.PAWN||last.promotion()!=null;
            if((capture||important)&&after.legalCapturesAndPromotions().stream().anyMatch(m->m.to()==last.to()&&MatchMaterial.victim(after,m)!=null))return true;
        }
        return false;
    }

    private static boolean interiorOffer(ChessPosition start,List<TacticalAnalysis.Variation> lines) {
        for(var line:lines) {
            if(line.ending()!=TacticalAnalysis.Ending.HORIZON||line.line().isEmpty())continue;
            var before=start;
            for(int i=0;i<line.line().size()-1;i++) {
                var move=line.line().get(i);var after=before.playGenerated(move);
                boolean capture=MatchMaterial.victim(before,move)!=null;
                boolean important=before.pieceAt(move.from()).kind()!=ChessPosition.Kind.PAWN||move.promotion()!=null;
                // A quiet opposing offer inside the preview also needs freely searched alternatives.
                boolean opposingOffer=i%2==1&&!capture&&important&&move.promotion()==null&&!after.isCheck();
                if(opposingOffer&&after.legalCapturesAndPromotions().stream()
                        .anyMatch(m->m.to()==move.to()&&MatchMaterial.victim(after,m)!=null))return true;
                before=after;
            }
        }
        return false;
    }

    static Result compare(ChessPosition position,int plies,Set<Move> draws,Policy policy) {
        return compare(position,position.legalMoves(),plies,draws,policy,Set.of());
    }
    static Result compare(ChessPosition position,List<Move> roots,int plies,Set<Move> draws,Policy policy) {
        return compare(position,roots,plies,draws,policy,Set.of());
    }
    static Result compare(ChessPosition position,List<Move> roots,int plies,Set<Move> draws,Policy policy,Set<Move> exactRoots) {
        return compare(position,roots,plies,draws,policy,exactRoots,2);
    }
    static Result compare(ChessPosition position,List<Move> roots,int plies,Set<Move> draws,Policy policy,Set<Move> exactRoots,int tacticalPlies) {
        if(plies<1||plies>MatchAiPolicy.MAX_PLIES)throw new IllegalArgumentException("Invalid ordinary coaching horizon");
        if(roots.isEmpty()||new HashSet<>(roots).size()!=roots.size()||!position.legalMoves().containsAll(roots))throw new IllegalArgumentException("Invalid tactical roots");
        var budget=new Budget(policy,plies,position,roots,tacticalPlies);var ranked=new ArrayList<TacticalAnalysis.Variation>();
        int balance=material(position,position.turn());
        var path=new HashMap<ChessPosition.SearchKey,Integer>();path.put(position.searchKey(),1);
        try {
            for(var move:ordered(position,roots)) {
                // Keep the best two exact values; discarded roots receive only an upper bound.
                // A played review move is always searched at full width, even far below the leaders.
                ranked.sort(Comparator.comparingInt(TacticalAnalysis.Variation::score).reversed());
                int floor=ranked.size()<2||exactRoots.contains(move)?-LIMIT:
                        Math.min(ranked.get(0).score()-MatchAiPolicy.CURRENT.hint.maxLossCp(),ranked.get(1).score());
                Node line;
                if(draws.contains(move))line=new Node(0,List.of(move),TacticalAnalysis.Ending.HORIZON);
                else line=search(position.playGenerated(move),plies-1,1,-LIMIT,floor==-LIMIT?LIMIT:1-floor,budget,path,0,move.to()).prepend(move);
                if(line.score()<floor){budget.bounded++;continue;}
                var end=position;for(var m:line.moves())end=end.playGenerated(m);
                ranked.add(new TacticalAnalysis.Variation(line.moves(),line.score(),material(end,position.turn())-balance,line.ending()));
            }
            ranked.sort(Comparator.comparingInt(TacticalAnalysis.Variation::score).reversed().thenComparing(v->v.move().uci()));
            return new Result(ranked,budget.work(Status.COMPLETE));
        }catch(Stopped stopped){return new Result(List.of(),budget.work(stopped.status));}
    }

    private static Node search(ChessPosition position,int left,int distance,int alpha,int beta,Budget budget,
                               Map<ChessPosition.SearchKey,Integer> path,int forcing) {
        return search(position,left,distance,alpha,beta,budget,path,forcing,-1);
    }
    private static Node search(ChessPosition position,int left,int distance,int alpha,int beta,Budget budget,
                               Map<ChessPosition.SearchKey,Integer> path,int forcing,int target) {
        budget.visit(forcing);
        var key=position.searchKey();int count=path.merge(key,1,Integer::sum);
        try {
            var legal=position.legalMoves();
            if(legal.isEmpty())return new Node(position.isCheck()?distance-MATE:0,List.of(),position.isCheck()?TacticalAnalysis.Ending.CHECKMATE:TacticalAnalysis.Ending.STALEMATE);
            if(count>=3){budget.repeated++;return new Node(0,List.of(),TacticalAnalysis.Ending.HORIZON);}
            boolean frontier=left==0,check=position.isCheck();
            var moves=frontier&&!check?position.legalCapturesAndPromotions():legal;
            if(frontier&&!check&&forcing>=budget.tactical&&budget.tactical<budget.policy.maxForcingPlies()) {
                moves=new ArrayList<>(moves);
                moves.removeIf(m->m.to()!=target||MatchMaterial.victim(position,m)==null);
            }
            if(frontier&&forcing>=budget.policy.maxForcingPlies()&&(!moves.isEmpty()||check))throw new Stopped(Status.FORCING_LIMIT);
            Node best=frontier&&!check?new Node(TacticalAnalysis.positionValue(position,position.turn()),List.of(),TacticalAnalysis.Ending.HORIZON):null;
            if(best!=null){if(best.score()>=beta)return best;alpha=Math.max(alpha,best.score());}
            for(var move:ordered(position,moves)) {
                if(frontier){if(check)budget.evasions++;if(MatchMaterial.victim(position,move)!=null)budget.captures++;}
                var child=search(position.playGenerated(move),Math.max(0,left-1),distance+1,-beta,-alpha,budget,path,frontier?forcing+1:0,move.to()).prepend(move);
                if(best==null||child.score()>best.score())best=child;
                alpha=Math.max(alpha,child.score());if(alpha>=beta)break;
            }
            return best;
        }finally{if(count==1)path.remove(key);else path.put(key,count-1);}
    }

    private static List<Move> ordered(ChessPosition board,List<Move> moves) {
        var result=new ArrayList<>(moves);
        result.sort(Comparator.comparingInt((Move m)->{
            var victim=MatchMaterial.victim(board,m);
            return (victim==null?0:10*value(victim.kind())-value(board.pieceAt(m.from()).kind()))+(m.promotion()==null?0:10_000+value(m.promotion()));
        }).reversed().thenComparing(Move::uci));return result;
    }
    private static int value(ChessPosition.Kind kind){return ChessYaml.number(MATERIAL,kind.name());}
    private static int material(ChessPosition board,ChessPosition.Side side) {
        int score=0;for(int s=0;s<64;s++){var p=board.pieceAt(s);if(p!=null)score+=(p.side()==side?1:-1)*value(p.kind());}return score;
    }
    private record Node(int score,List<Move> moves,TacticalAnalysis.Ending ending) {
        Node prepend(Move move){var next=new ArrayList<Move>();next.add(move);next.addAll(moves);return new Node(-score,List.copyOf(next),ending);}
    }
    private static final class Budget {
        final Policy policy;final int ordinary,tactical;final String positionKey;final List<Move> roots;int nodes,allocated,deepest,captures,evasions,repeated,bounded;
        Budget(Policy policy,int ordinary,ChessPosition position,List<Move> roots){this(policy,ordinary,position,roots,2);}
        Budget(Policy policy,int ordinary,ChessPosition position,List<Move> roots,int tactical){this.policy=policy;this.ordinary=ordinary;this.tactical=tactical;positionKey=position.searchKey().toString();this.roots=List.copyOf(roots);}
        void visit(int forcing) {
            if(Thread.currentThread().isInterrupted())throw new CancellationException();
            if(nodes==allocated){if(allocated==policy.maxNodes())throw new Stopped(Status.NODE_LIMIT);allocated=(int)Math.min(policy.maxNodes(),(long)allocated+policy.allocationStep());}
            nodes++;deepest=Math.max(deepest,forcing);
        }
        Work work(Status status){return new Work(status,nodes,allocated,policy.maxNodes(),ordinary,deepest,captures,evasions,repeated,positionKey,roots,0,bounded,null,tactical);}
    }
    private static final class Stopped extends RuntimeException {final Status status;Stopped(Status status){this.status=status;}}
}
