package ro.atf.chess;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/** Small, context-free UI state; restoring it never plays a move on the source game. */
public record ReplayCheckpoint(String source, Kind kind, String suggestion, List<String> lines,
                               int branch, List<Integer> plies, boolean danger) {
    public enum Kind { INLINE, CALCULATED, THEORY, MATING }

    public ReplayCheckpoint {
        if (lines == null || plies == null || lines.stream().anyMatch(java.util.Objects::isNull) || plies.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Missing replay branches");
        }
        lines = List.copyOf(lines);
        plies = List.copyOf(plies);
        if (source == null || source.isBlank() || kind == null || suggestion == null || lines.isEmpty()
                || lines.size() > 3 || plies.size() != lines.size() || branch < 0 || branch >= lines.size()) {
            throw new IllegalArgumentException("Invalid replay checkpoint");
        }
        if (!suggestion.isEmpty()) ChessPosition.Move.fromUci(suggestion);
        if (kind != Kind.MATING && suggestion.isEmpty()) throw new IllegalArgumentException("Missing revealed move");
        if ((kind == Kind.MATING || kind == Kind.INLINE) && (lines.size() != 1 || branch != 0)
                || kind != Kind.MATING && danger
                || kind == Kind.INLINE && (!lines.get(0).equals(suggestion) || plies.get(0) != 0)) {
            throw new IllegalArgumentException("Invalid replay mode");
        }
        for (int index = 0; index < lines.size(); index++) {
            String[] moves = lines.get(index).split(" ", -1);
            for (String move : moves) ChessPosition.Move.fromUci(move);
            if (plies.get(index) < 0 || plies.get(index) > moves.length) throw new IllegalArgumentException("Invalid replay cursor");
        }
    }

    public boolean matches(String currentSource, Kind currentKind, List<String> currentLines) {
        return source.equals(currentSource) && kind == currentKind && lines.equals(currentLines);
    }

    public String encode() {
        return String.join("\t", "1", encoded(source), kind.name(), suggestion, Integer.toString(branch),
                lines.stream().map(ReplayCheckpoint::encoded).collect(Collectors.joining(",")),
                plies.stream().map(String::valueOf).collect(Collectors.joining(",")), Boolean.toString(danger));
    }

    public static Optional<ReplayCheckpoint> decode(String text) {
        if (text == null) return Optional.empty();
        try {
            String[] fields = text.split("\t", -1);
            if (fields.length != 8 || !fields[0].equals("1") || !List.of("true", "false").contains(fields[7])) return Optional.empty();
            return Optional.of(new ReplayCheckpoint(decoded(fields[1]), Kind.valueOf(fields[2]), fields[3],
                    java.util.Arrays.stream(fields[5].split(",", -1)).map(ReplayCheckpoint::decoded).collect(Collectors.toList()),
                    Integer.parseInt(fields[4]), java.util.Arrays.stream(fields[6].split(",", -1)).map(Integer::parseInt).collect(Collectors.toList()),
                    Boolean.parseBoolean(fields[7])));
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    private static String encoded(String text) { return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)); }
    private static String decoded(String text) { return new String(Base64.getDecoder().decode(text), StandardCharsets.UTF_8); }
}
