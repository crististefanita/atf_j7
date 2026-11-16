package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** The recorded APK graph scenario, with a deterministic stand-in for engine evaluation. */
final class GameReviewGraphFixture {
    static final int SELECTED_PLY = 184;
    static final int TOTAL_PLIES = 185;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectNode nativeSample;
    private final ChessMatch match;

    GameReviewGraphFixture() throws IOException {
        try (var source = Objects.requireNonNull(getClass().getResourceAsStream("/review-graph-native.json"))) {
            nativeSample = (ObjectNode) JSON.readTree(source);
        }
        match = ChessMatch.restore(nativeSample.path("checkpoint").textValue());
        if (match.ply() != TOTAL_PLIES || match.result() != ChessMatch.Result.WHITE_WINS) {
            throw new IllegalArgumentException("Expected the recorded APK game ending in White mate");
        }
    }

    ChessMatch match() { return match; }
    JsonNode samples() { return nativeSample.path("expected").path("samples"); }

    GameReview review() {
        var player = new GameReview.Player(OptionalInt.empty(), Optional.empty(), 0, Map.of());
        var turns = new ArrayList<GameReview.Turn>();
        for (int ply = 1; ply <= match.ply(); ply++) {
            var move = match.moveAt(ply);
            int whitePoints = ply == SELECTED_PLY ? 750 : 500;
            turns.add(new GameReview.Turn(ply, match.positionAt(ply - 1).turn(), move, GameReview.Grade.GOOD,
                    500, 500, 0, whitePoints, 1, List.of(move), List.of(move), 0));
        }
        return new GameReview(turns, null, 0, 500, player, player);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Supply the Web fixture output path");
        var fixture = new GameReviewGraphFixture();
        var archive = fixture.nativeSample.deepCopy();
        archive.put("id", "185-ply-native-review");
        archive.put("review", GameReviewCodec.encode(fixture.match(), fixture.review()));
        JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(args[0]).toFile(), archive);
    }
}
