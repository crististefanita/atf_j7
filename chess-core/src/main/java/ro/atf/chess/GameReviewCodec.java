package ro.atf.chess;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.ChessPosition.Side;
import ro.atf.chess.GameReview.Grade;
import ro.atf.chess.GameReview.Player;
import ro.atf.chess.GameReview.Rating;

/** Versioned analysis cache bound to the exact completed checkpoint. */
public final class GameReviewCodec {
    private static final int VERSION = 3;
    static final int MAX_LINE_LENGTH = 64;

    private GameReviewCodec() { }

    public static String encode(ChessMatch match, GameReview review) {
        return encode(review, checkpointDigest(match));
    }

    static String encode(GameReview review, String digest) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeUTF(digest);
            out.writeBoolean(review.opening() != null);
            if (review.opening() != null) {
                out.writeUTF(review.opening().eco());
                out.writeUTF(review.opening().name());
            }
            out.writeInt(review.bookPlies());
            out.writeInt(review.initialWhitePoints());
            writePlayer(out, review.white());
            writePlayer(out, review.black());
            out.writeInt(review.turns().size());
            for (var turn : review.turns()) {
                out.writeUTF(turn.played().uci());
                out.writeUTF(turn.grade().name());
                for (int value : new int[]{turn.bestPoints(), turn.playedPoints(), turn.loss(), turn.whitePoints(),
                        turn.depth(), turn.sacrificeCp()}) out.writeInt(value);
                writeLine(out, turn.bestLine());
                writeLine(out, turn.playedLine());
                writeProof(out,turn.proof());
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Review cannot be encoded", failure);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static GameReview decode(ChessMatch match, String encoded) {
        if (match.result() == ChessMatch.Result.IN_PROGRESS) {
            throw new IllegalArgumentException("Review belongs to another checkpoint or version");
        }
        return decodeLine(match, encoded, checkpointDigest(match));
    }

    /** Includes unrated snapshots. The platform supplies the canonical checkpoint digest. */
    static GameReview decodeLine(ChessMatch match, String encoded, String digest) {
        try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            int version=in.readInt();
            if ((version<1||version>VERSION) || !digest.equals(in.readUTF())) {
                throw new IllegalArgumentException("Review belongs to another checkpoint or version");
            }
            var opening = in.readBoolean() ? new GameReview.Opening(in.readUTF(), in.readUTF()) : null;
            int book = in.readInt();
            int initial = in.readInt();
            var white = readPlayer(in);
            var black = readPlayer(in);
            if (match.result() == ChessMatch.Result.IN_PROGRESS && (white.rating().isPresent() || black.rating().isPresent())) {
                throw new IllegalArgumentException("An active line cannot have a rating");
            }
            if (in.readInt() != match.ply()) throw new IllegalArgumentException("Incomplete review");
            var turns = new ArrayList<GameReview.Turn>();
            for (int ply = 1; ply <= match.ply(); ply++) {
                var move = Move.fromUci(in.readUTF());
                if (!move.equals(match.moveAt(ply))) throw new IllegalArgumentException("Review move differs at " + ply);
                Side side = match.positionAt(ply - 1).turn();
                var grade = Grade.valueOf(in.readUTF());
                int best = in.readInt(), played = in.readInt(), loss = in.readInt(), graph = in.readInt();
                int depth = in.readInt(), sacrifice = in.readInt();
                var bestLine=readLine(in);var playedLine=readLine(in);var proof=version==1?null:readProof(in,version);
                if(proof!=null)proof.validate(match.positionAt(ply-1),depth);
                turns.add(new GameReview.Turn(ply, side, move, grade, best, played, loss, graph, depth,bestLine,playedLine,sacrifice,proof));
            }
            if (in.read() != -1) throw new IllegalArgumentException("Unexpected review data");
            return new GameReview(turns, opening, book, initial, white, black);
        } catch (IOException failure) {
            throw new IllegalArgumentException("Incomplete review", failure);
        }
    }

    static String checkpointDigest(ChessMatch match) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(match.checkpoint().getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(digest);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 unavailable", failure);
        }
    }

    private static void writeProof(DataOutputStream out,CoachingProof.Work p)throws IOException {
        out.writeBoolean(p!=null);if(p==null)return;
        out.writeUTF(p.status().name());for(int n:new int[]{p.nodes(),p.allocatedNodes(),p.ceiling(),p.ordinaryPlies(),p.forcingPlies(),p.captureReplies(),p.checkEvasions(),p.repeatedDraws(),p.ordinaryNodes(),p.boundedRoots(),p.tacticalPlies()})out.writeInt(n);
        out.writeUTF(p.positionKey());out.writeInt(p.roots().size());for(var move:p.roots())out.writeUTF(move.uci());
        var choice=p.confirmation();out.writeBoolean(choice!=null);
        if(choice!=null){out.writeInt(choice.plies());out.writeUTF(choice.incumbent().uci());out.writeUTF(choice.challenger().uci());out.writeUTF(choice.selected().uci());for(int n:new int[]{choice.incumbentScore(),choice.challengerScore(),choice.earlierIncumbentScore(),choice.earlierChallengerScore(),choice.minGainCp()})out.writeInt(n);}
        var reply=p.counterplay();out.writeBoolean(reply!=null);
        if(reply!=null){out.writeInt(reply.earlierPlies());for(var line:List.of(reply.earlierIncumbent(),reply.earlierChallenger(),reply.incumbent(),reply.challenger()))writeVariation(out,line);out.writeUTF(reply.selected().uci());out.writeInt(reply.leaderScore());out.writeInt(reply.minGainCp());out.writeInt(reply.minMaterialLoss());}
    }
    private static CoachingProof.Work readProof(DataInputStream in,int version)throws IOException {
        if(!in.readBoolean())return null;var status=CoachingProof.Status.valueOf(in.readUTF());
        int nodes=in.readInt(),allocated=in.readInt(),ceiling=in.readInt(),ordinary=in.readInt(),forcing=in.readInt(),captures=in.readInt(),evasions=in.readInt(),draws=in.readInt(),base=in.readInt(),bounded=in.readInt(),tactical=in.readInt();
        String position=in.readUTF();int count=in.readInt();if(count<1||count>512)throw new IllegalArgumentException("Invalid proof root count");
        var roots=new ArrayList<Move>();for(int i=0;i<count;i++)roots.add(Move.fromUci(in.readUTF()));
        var choice=in.readBoolean()?new CoachingProof.Confirmation(in.readInt(),Move.fromUci(in.readUTF()),Move.fromUci(in.readUTF()),Move.fromUci(in.readUTF()),in.readInt(),in.readInt(),in.readInt(),in.readInt(),in.readInt()):null;
        var reply=version>=3&&in.readBoolean()?new CoachingProof.Counterplay(in.readInt(),readVariation(in),readVariation(in),readVariation(in),readVariation(in),Move.fromUci(in.readUTF()),in.readInt(),in.readInt(),in.readInt()):null;
        return new CoachingProof.Work(status,nodes,allocated,ceiling,ordinary,forcing,captures,evasions,draws,position,roots,base,bounded,choice,tactical,reply);
    }
    private static void writeVariation(DataOutputStream out,TacticalAnalysis.Variation line)throws IOException {out.writeInt(line.score());out.writeInt(line.materialChange());out.writeUTF(line.ending().name());writeLine(out,line.line());}
    private static TacticalAnalysis.Variation readVariation(DataInputStream in)throws IOException {int score=in.readInt(),material=in.readInt();var ending=TacticalAnalysis.Ending.valueOf(in.readUTF());return new TacticalAnalysis.Variation(readLine(in),score,material,ending);}

    private static void writePlayer(DataOutputStream out, Player player) throws IOException {
        out.writeInt(player.accuracy().orElse(-1));
        out.writeInt(player.ratedMoves());
        out.writeBoolean(player.rating().isPresent());
        if (player.rating().isPresent()) {
            var rating = player.rating().orElseThrow();
            out.writeInt(rating.elo());
            out.writeBoolean(rating.low() != null);
            if (rating.low() != null) out.writeInt(rating.low());
            out.writeInt(rating.high());
        }
        out.writeInt(player.grades().size());
        for (var grade : Grade.values()) {
            if (!player.grades().containsKey(grade)) continue;
            out.writeUTF(grade.name());
            out.writeInt(player.grades().get(grade));
        }
    }

    private static Player readPlayer(DataInputStream in) throws IOException {
        int accuracy = in.readInt();
        int rated = in.readInt();
        Optional<Rating> rating = Optional.empty();
        if (in.readBoolean()) rating = Optional.of(new Rating(in.readInt(), in.readBoolean() ? in.readInt() : null, in.readInt()));
        int count = in.readInt();
        if (count < 0 || count > Grade.values().length) throw new IllegalArgumentException("Invalid grade count");
        var grades = new EnumMap<Grade, Integer>(Grade.class);
        for (int i = 0; i < count; i++) {
            if (grades.put(Grade.valueOf(in.readUTF()), in.readInt()) != null) throw new IllegalArgumentException("Duplicate grade");
        }
        return new Player(accuracy < 0 ? OptionalInt.empty() : OptionalInt.of(accuracy), rating, rated, grades);
    }

    private static void writeLine(DataOutputStream out, List<Move> line) throws IOException {
        out.writeInt(line.size());
        for (var move : line) out.writeUTF(move.uci());
    }

    private static List<Move> readLine(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 0 || size > MAX_LINE_LENGTH) throw new IllegalArgumentException("Invalid analysis line length");
        var line = new ArrayList<Move>(size);
        for (int i = 0; i < size; i++) line.add(Move.fromUci(in.readUTF()));
        return line;
    }
}
