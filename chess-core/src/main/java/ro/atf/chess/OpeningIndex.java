package ro.atf.chess;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class OpeningIndex {
    static final String RESOURCE = "opening-book.tsv";
    static final OpeningIndex CURRENT = load();

    record Match(GameReview.Opening opening, int bookPlies) { }
    private record Entry(ChessPosition position, GameReview.Opening name) { }

    private final Set<String> prefixes = new HashSet<>();
    private final Map<Integer, List<Entry>> named = new HashMap<>();
    private int longest;

    private static OpeningIndex load() {
        var stream = OpeningIndex.class.getResourceAsStream("/" + RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing opening index");
        var result = new OpeningIndex();
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String source = reader.readLine();
            if (source == null || !source.matches("# lichess-org/chess-openings [0-9a-f]{40} CC0-1.0")
                    || !"eco\tname\tepd\tmoves".equals(reader.readLine())) {
                throw new IllegalArgumentException("Invalid opening index header");
            }
            for (String row; (row = reader.readLine()) != null;) result.add(row);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read opening index", failure);
        }
        return result;
    }

    private void add(String row) {
        var fields = row.split("\t", -1);
        if (fields.length != 4 || !fields[0].matches("[A-E][0-9]{2}") || fields[1].isBlank() || fields[3].isBlank()) {
            throw new IllegalArgumentException("Invalid opening entry: " + row);
        }
        var position = ChessPosition.fromFen(fields[2] + " 0 1");
        var entries = named.computeIfAbsent(boardHash(position), ignored -> new ArrayList<>());
        entries.add(new Entry(position, new GameReview.Opening(fields[0], fields[1])));
        var moves = fields[3].split(" ");
        longest = Math.max(longest, moves.length);
        var prefix = new StringBuilder();
        for (String move : moves) {
            if (prefix.length() > 0) prefix.append(' ');
            prefixes.add(prefix.append(move).toString());
        }
    }

    Match recognize(ChessMatch match) {
        int limit = Math.min(match.ply(), longest);
        int bookPlies = 0;
        var prefix = new StringBuilder();
        for (int ply = 1; ply <= limit; ply++) {
            if (prefix.length() > 0) prefix.append(' ');
            if (!prefixes.contains(prefix.append(match.moveAt(ply).uci()).toString())) break;
            bookPlies = ply;
        }
        for (int ply = limit; ply > 0; ply--) {
            var position = match.positionAt(ply);
            for (var entry : named.getOrDefault(boardHash(position), List.of())) {
                if (entry.position().sameForRepetition(position)) return new Match(entry.name(), bookPlies);
            }
        }
        return new Match(null, bookPlies);
    }

    private static int boardHash(ChessPosition position) {
        int hash = position.turn().ordinal();
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            hash = 31 * hash + (piece == null ? 0 : 1 + piece.kind().ordinal() + 6 * piece.side().ordinal());
        }
        return hash;
    }
}
