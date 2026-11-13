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
    private static final int VERSION = 1;
    static final int MAX_LINE_LENGTH = 64;

    private GameReviewCodec() { }

    public static String encode(ChessMatch match, GameReview review) {
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeUTF(checkpointDigest(match));
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
            }
        } catch (IOException failure) {
            throw new IllegalArgumentException("Review cannot be encoded", failure);
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }

    public static GameReview decode(ChessMatch match, String encoded) {
        try (var in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            if (in.readInt() != VERSION || match.result() == ChessMatch.Result.IN_PROGRESS || !checkpointDigest(match).equals(in.readUTF())) {
                throw new IllegalArgumentException("Review belongs to another checkpoint or version");
            }
            var opening = in.readBoolean() ? new GameReview.Opening(in.readUTF(), in.readUTF()) : null;
            int book = in.readInt();
            int initial = in.readInt();
            var white = readPlayer(in);
            var black = readPlayer(in);
            if (in.readInt() != match.ply()) throw new IllegalArgumentException("Incomplete review");
            var turns = new ArrayList<GameReview.Turn>();
            for (int ply = 1; ply <= match.ply(); ply++) {
                var move = Move.fromUci(in.readUTF());
                if (!move.equals(match.moveAt(ply))) throw new IllegalArgumentException("Review move differs at " + ply);
                Side side = match.positionAt(ply - 1).turn();
                var grade = Grade.valueOf(in.readUTF());
                int best = in.readInt(), played = in.readInt(), loss = in.readInt(), graph = in.readInt();
                int depth = in.readInt(), sacrifice = in.readInt();
                turns.add(new GameReview.Turn(ply, side, move, grade, best, played, loss, graph, depth,
                        readLine(in), readLine(in), sacrifice));
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
