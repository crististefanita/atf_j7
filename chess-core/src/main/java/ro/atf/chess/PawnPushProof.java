package ro.atf.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;

/** Conservative AND/OR proof of a safe queen; unproved is unknown, never a draw or loss. */
final class PawnPushProof {
    record Proof(List<Move> line,String strategySha256) { Proof { line=List.copyOf(line); } }
    record Certificate(int maxPlies,int linePlies,int extraNodes,int defenderEdgesVisited,
                       int cacheLimit,int quietPlies,String historySha256,String strategySha256) { }
    private record Key(ChessPosition.SearchKey position,int left,int quiet) { }
    private final Side attacker;
    private final Runnable visit;
    private final int cacheLimit;
    private final Map<Key,Proof> memo=new HashMap<>();
    private final java.util.Set<ChessPosition.SearchKey> forbidden;
    private int defenderReplies;

    PawnPushProof(Side attacker,Runnable visit,int cacheLimit) {
        this(attacker,visit,cacheLimit,List.of());
    }
    PawnPushProof(Side attacker,Runnable visit,int cacheLimit,List<ChessPosition> history) {
        this.attacker=attacker;this.visit=visit;this.cacheLimit=cacheLimit;
        forbidden=new HashSet<>();for(var previous:history)forbidden.add(previous.searchKey());
    }
    int defenderReplies(){return defenderReplies;}

    static boolean eligible(ChessPosition position,int maxPawns) {
        int pawns=0;
        for(int square=0;square<64;square++) {
            var piece=position.pieceAt(square);if(piece==null||piece.kind()==Kind.KING)continue;
            if(piece.side()!=position.turn()||piece.kind()!=Kind.PAWN)return false;
            pawns++;
        }
        return pawns>0&&pawns<=maxPawns;
    }

    Proof prove(ChessPosition position,int left) {
        return prove(position,left,0);
    }
    Proof prove(ChessPosition position,int left,int quietPlies) {
        return solve(position,left,quietPlies,true);
    }
    private Proof solve(ChessPosition position,int left,int quiet,boolean root) {
        visit.run();var key=new Key(position.searchKey(),left,quiet);
        // The attacker moves monotonically between irreversible pawn moves/captures.
        // Excluding prior game boards also makes this proof safe against historical repetition.
        if(!root&&forbidden.contains(key.position()))return null;
        if(memo.containsKey(key))return memo.get(key);
        var moves=new ArrayList<>(position.legalMoves());moves.sort(Comparator.comparingInt((Move move)->order(position,move)).reversed().thenComparing(Move::uci));
        if(moves.isEmpty())return remember(key,position.isCheck()&&position.turn()!=attacker?leaf(key):null);
        if(position.turn()!=attacker&&(quiet>=100||quiet==99&&moves.stream().anyMatch(m->position.pieceAt(m.to())==null)))return remember(key,null);
        if(safeQueen(position,moves))return remember(key,leaf(key));
        if(left==0)return remember(key,null);
        if(position.turn()==attacker) {
            for(var move:moves) {
                if(move.promotion()!=null&&move.promotion()!=Kind.QUEEN)continue;
                var next=position.playGenerated(move);
                if(position.pieceAt(move.from()).kind()==Kind.KING&&potential(next)>=potential(position))continue;
                int nextQuiet=position.pieceAt(move.from()).kind()==Kind.PAWN||position.pieceAt(move.to())!=null?0:quiet+1;
                var child=solve(next,left-1,nextQuiet,false);
                if(child!=null)return remember(key,prepend(move,child,hash(key+"|OR|"+move.uci()+"|"+child.strategySha256())));
            }
            return remember(key,null);
        }
        Proof longest=null;var covered=new StringBuilder(key+"|AND");
        for(var move:moves) {
            defenderReplies++;var child=solve(position.playGenerated(move),left-1,position.pieceAt(move.to())==null?quiet+1:0,false);
            if(child==null)return remember(key,null);
            covered.append('|').append(move.uci()).append(':').append(child.strategySha256());
            if(longest==null||child.line().size()+1>longest.line().size())longest=prepend(move,child,"");
        }
        return remember(key,new Proof(longest.line(),hash(covered.toString())));
    }

    private boolean safeQueen(ChessPosition position,List<Move> replies) {
        if(position.turn()==attacker)return false;int queen=-1;
        for(int square=0;square<64;square++) {
            var piece=position.pieceAt(square);if(piece==null||piece.kind()==Kind.KING)continue;
            if(piece.side()!=attacker)return false;
            if(piece.kind()==Kind.QUEEN&&queen<0)queen=square;else if(piece.kind()!=Kind.PAWN)return false;
        }
        if(queen<0)return false;
        for(var reply:replies)if(reply.to()==queen)return false;
        return true;
    }
    private Proof remember(Key key,Proof proof) {if(memo.size()<cacheLimit)memo.put(key,proof);return proof;}
    private int order(ChessPosition board,Move move) {
        if(board.turn()!=attacker)return board.pieceAt(move.to())!=null?100:0;
        if(board.pieceAt(move.from()).kind()==Kind.PAWN)return move.promotion()!=null?10000:100;
        int closest=8;for(int square=0;square<64;square++) {
            var piece=board.pieceAt(square);if(piece==null||piece.kind()!=Kind.PAWN||piece.side()!=attacker)continue;
            closest=Math.min(closest,Math.max(Math.abs(move.to()%8-square%8),Math.abs(move.to()/8-square/8)));
        }return -closest;
    }
    private int potential(ChessPosition board) {
        int king=-1;for(int square=0;square<64;square++){var p=board.pieceAt(square);if(p!=null&&p.side()==attacker&&p.kind()==Kind.KING)king=square;}
        int distance=0;for(int square=0;square<64;square++){var p=board.pieceAt(square);if(p!=null&&p.side()==attacker&&p.kind()==Kind.PAWN)distance+=Math.max(Math.abs(king%8-square%8),Math.abs(king/8-square/8));}
        return distance*8+(attacker==Side.WHITE?7-king/8:king/8);
    }
    private static Proof leaf(Key key){return new Proof(List.of(),hash(key+"|SAFE_WIN"));}
    private static Proof prepend(Move move,Proof proof,String hash) {var line=new ArrayList<Move>();line.add(move);line.addAll(proof.line());return new Proof(line,hash);}
    static String historyHash(List<ChessPosition> history) {
        var text=new StringBuilder();for(var board:history)text.append(board.searchKey()).append('\n');return hash(text.toString());
    }
    private static String hash(String text) {
        try{var bytes=java.security.MessageDigest.getInstance("SHA-256").digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));var hex=new StringBuilder();for(byte value:bytes){hex.append(Character.forDigit((value&255)>>>4,16));hex.append(Character.forDigit(value&15,16));}return hex.toString();}
        catch(java.security.NoSuchAlgorithmException failure){throw new IllegalStateException(failure);}
    }
}
