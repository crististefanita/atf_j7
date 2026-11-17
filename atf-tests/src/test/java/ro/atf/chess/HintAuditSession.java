package ro.atf.chess;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Move;
import ro.atf.chess.TacticalAnalysis.Variation;
import static ro.atf.chess.HintAuditPolicy.require;

/** Replayable diagnostic evidence, deliberately separate from live AI memory and search caches. */
final class HintAuditSession {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GENESIS = "0".repeat(64);
    private static final java.util.Map<?, ?> MATERIAL = ChessYaml.map(ChessYaml.resource("tactical-analysis.yaml"), "material");
    record Identity(String dataset, String run, String revision, String engineDigest, String configurationDigest) {
        Identity {
            require(dataset.matches("[a-z][a-z0-9-]+") && run.matches("[a-z][a-z0-9-]+"), "Invalid audit identity");
            require(revision.matches("[a-f0-9]{40}") && engineDigest.matches("[a-f0-9]{64}")
                    && configurationDigest.matches("[a-f0-9]{64}"), "Exact source and configuration identities required");
        }
    }
    record Search(List<Variation> lines, List<Variation> compared, int extraNodes, int selectedDepth, int seedDepth, int comparisonDepth) {
        Search { lines = List.copyOf(lines); compared = List.copyOf(compared); }
        Search(List<Variation> lines, int extraNodes, int selectedDepth, int seedDepth, int comparisonDepth) {
            this(lines, comparisonDepth == 0 ? List.of() : lines, extraNodes, selectedDepth, seedDepth, comparisonDepth);
        }
    }
    @FunctionalInterface interface Searcher { Search analyze(ChessPosition position, int extraNodeLimit); }
    record ReplyFact(int ply, String move, String piece, String captured, boolean check) { }
    record Variant(int rank, int scoreCp, int materialChangeCp, String ending, int signedMatePlies,
                   List<String> pv, List<ReplyFact> opponentReplies) {
        Variant { pv = List.copyOf(pv); opponentReplies = List.copyOf(opponentReplies); }
    }
    record Decision(Identity identity, String caseId, String family, int index, String checkpoint, String fen,
                    String side, String context, int repetitions, String cacheMode, String phase, String theoryMove,
                    int extraNodeLimit, int requestedRank, boolean requestedAvailable, int chosenRank, String chosen,
                    int selectedDepth, int seedDepth, int comparisonDepth, int extraNodes, List<Variant> variants, List<Variant> comparedVariants,
                    String previousDigest) {
        Decision { variants = List.copyOf(variants); comparedVariants = List.copyOf(comparedVariants); }
    }
    record Envelope(Decision decision, String digest) { }

    private final Identity identity;
    private final HintAuditPolicy.Run plan;
    private final List<Envelope> receipts = new ArrayList<>();
    private ChessMatch match;

    HintAuditSession(Identity identity, HintAuditPolicy.Run plan) {
        this.identity = Objects.requireNonNull(identity);
        this.plan = Objects.requireNonNull(plan);
        match = plan.start();
        require(match.result() == ChessMatch.Result.IN_PROGRESS && match.ply() >= 2, "Active full-history start required");
        require(plan.decisions() >= 1 && plan.decisions() <= 12 && !plan.budgets().isEmpty() && !plan.ranks().isEmpty(), "Invalid audit plan");
        require(plan.budgets().stream().allMatch(value -> value >= 1_000 && value <= HintAuditPolicy.MAX_NODES)
                && plan.ranks().stream().allMatch(value -> value >= 1 && value <= 3), "Invalid audit schedule");
    }

    ChessMatch match() { return match; }
    List<Envelope> receipts() { return List.copyOf(receipts); }

    static Path prepareOutput(Path directory, Path project) throws IOException {
        var target = directory.toAbsolutePath().normalize();
        var checkout = project.toRealPath();
        require(!target.startsWith(checkout), "Audit output must be external to the project");
        if (Files.exists(target)) {
            require(Files.isDirectory(target), "Audit output is not a directory");
            try (var files = Files.list(target)) {
                require(files.findAny().isEmpty(), "Audit raw output must be fresh or strictly empty; existing evidence is preserved");
            }
        } else Files.createDirectories(target);
        require(!target.toRealPath().startsWith(checkout), "Resolved audit output must be external to the project");
        return target.toRealPath();
    }

    Envelope advance(Searcher engine) {
        int index = receipts.size();
        require(index < plan.decisions() && match.result() == ChessMatch.Result.IN_PROGRESS, "Audit is complete or game has ended");
        int budget = plan.budget(index), requested = plan.rank(index);
        var board = match.position();
        // Exactly one computation per decision. Resumption validates receipts without invoking the engine.
        var search = Objects.requireNonNull(engine.analyze(board, budget));
        require(search.extraNodes() >= 0 && search.extraNodes() <= budget, "Search exceeded the declared extra-node limit");
        require(search.selectedDepth() >= 1 && search.seedDepth() >= 1 && search.comparisonDepth() >= 0
                && search.selectedDepth() <= MatchAiPolicy.MAX_PLIES && search.seedDepth() <= MatchAiPolicy.MAX_PLIES
                && search.comparisonDepth() <= MatchAiPolicy.MAX_PLIES, "Missing or invalid achieved horizon");
        require(!search.lines().isEmpty() && search.lines().size() <= 3, "Audit records one to three actual variants");
        require(search.compared().size() <= board.legalMoves().size(), "Compared roots exceed the actual legal choices");
        var variants = variants(board, search.lines(), search.selectedDepth());
        var compared = variants(board, search.compared(), search.comparisonDepth());
        boolean available = requested <= variants.size();
        int chosenRank = available ? requested : 1;
        String checkpoint = match.checkpoint();
        String context = context(checkpoint, budget);
        var book = OpeningRepertoire.CURRENT.playReply(board, MatchSetup.Level.HARD);
        int repetitions = 0;
        for (int ply = 0; ply <= match.ply(); ply++) if (match.positionAt(ply).sameForRepetition(board)) repetitions++;
        var decision = new Decision(identity, plan.id(), plan.family(), index, checkpoint, fen(match), board.turn().name(), context,
                repetitions, "FRESH_POSITION_ONLY_NO_PERSISTENT_CACHE", book == null ? "OFF_BOOK_HINT_SEARCH" : "THEORY_AVAILABLE_INDEPENDENT_HINT",
                book == null ? null : book.uci(), budget, requested,
                available, chosenRank, variants.get(chosenRank - 1).pv().getFirst(), search.selectedDepth(), search.seedDepth(),
                search.comparisonDepth(), search.extraNodes(), variants, compared, index == 0 ? GENESIS : receipts.getLast().digest());
        var envelope = new Envelope(decision, digest(json(decision)));
        var next = match.play(Move.fromUci(decision.chosen()));
        receipts.add(envelope);
        match = next;
        return envelope;
    }

    private static List<Variant> variants(ChessPosition board, List<Variation> lines, int horizon) {
        var seen = new HashSet<Move>();
        var variants = new ArrayList<Variant>();
        for (var line : lines) {
            require(!line.line().isEmpty() && seen.add(line.move()), "Missing or duplicate variant root");
            var replay = board;
            var replies = new ArrayList<ReplyFact>();
            for (int ply = 0; ply < line.line().size(); ply++) {
                var move = line.line().get(ply);
                var piece = replay.pieceAt(move.from());
                require(piece != null && replay.legalMoves().contains(move), "Illegal scored continuation");
                var captured = MatchMaterial.victim(replay, move);
                replay = replay.play(move);
                if ((ply & 1) == 1) replies.add(new ReplyFact(ply + 1, move.uci(), piece.kind().name(),
                        captured == null ? null : captured.kind().name(), replay.isCheck()));
            }
            require(line.ending() != TacticalAnalysis.Ending.CHECKMATE || replay.isCheckmate(), "Unproved mate label");
            require(line.ending() != TacticalAnalysis.Ending.STALEMATE || !replay.isCheck() && !replay.hasLegalMove(), "Unproved stalemate label");
            require(line.ending() != TacticalAnalysis.Ending.HORIZON || line.line().size() >= horizon, "PV does not reach its declared horizon");
            require(line.materialChange() == materialChangeCp(board, replay), "Scored material change differs from the actual PV");
            variants.add(new Variant(variants.size() + 1, line.score(), line.materialChange(), line.ending().name(),
                    line.signedMatePlies(), line.line().stream().map(Move::uci).toList(), replies));
        }
        return List.copyOf(variants);
    }

    String context(String checkpoint, int budget) {
        return digest(json(identity) + "\n" + plan.id() + "\n" + checkpoint + "\n" + budget + "\nFRESH_POSITION_ONLY");
    }

    String jsonl() { return String.join("\n", receipts.stream().map(HintAuditSession::json).toList()) + (receipts.isEmpty() ? "" : "\n"); }

    /** The digest must come from a separately trusted manifest, never from the received file itself. */
    static HintAuditSession resume(Identity identity, HintAuditPolicy.Run plan, String jsonl, String trustedRawDigest) throws IOException {
        require(trustedRawDigest != null && trustedRawDigest.matches("[a-f0-9]{64}") && digest(jsonl).equals(trustedRawDigest),
                "Raw audit bytes differ from the trusted manifest digest");
        var session = new HintAuditSession(identity, plan);
        for (String row : jsonl.lines().toList()) {
            require(!row.isBlank(), "Blank audit receipt");
            var received = JSON.readValue(row, Envelope.class);
            require(received != null && received.decision() != null && identity.equals(received.decision().identity()), "Foreign source, run or configuration receipt");
            var body = received.decision();
            require(digest(json(body)).equals(received.digest()), "Altered audit receipt digest");
            var expected = session.advance((board, budget) -> new Search(scored(body.variants()), scored(body.comparedVariants()),
                    body.extraNodes(), body.selectedDepth(), body.seedDepth(), body.comparisonDepth()));
            require(expected.equals(received), "Audit history, position, clock, context, chain or rank differs");
        }
        return session;
    }

    private static List<Variation> scored(List<Variant> variants) {
        return variants.stream().map(variant -> new Variation(variant.pv().stream().map(Move::fromUci).toList(),
                variant.scoreCp(), variant.materialChangeCp(), TacticalAnalysis.Ending.valueOf(variant.ending()))).toList();
    }

    static int materialChangeCp(ChessPosition start, ChessPosition end) {
        int balance = 0;
        for (int square = 0; square < 64; square++) {
            var before = start.pieceAt(square);
            var after = end.pieceAt(square);
            if (before != null) balance -= (before.side() == start.turn() ? 1 : -1) * ChessYaml.number(MATERIAL, before.kind().name());
            if (after != null) balance += (after.side() == start.turn() ? 1 : -1) * ChessYaml.number(MATERIAL, after.kind().name());
        }
        return balance;
    }

    String pgn() {
        var moves = new ArrayList<String>();
        for (int ply = 1; ply <= match.ply(); ply++) {
            if ((ply & 1) == 1) moves.add(((ply + 1) / 2) + ".");
            moves.add(san(match.positionAt(ply - 1), match.moveAt(ply)));
        }
        String result = switch (match.result()) {
            case IN_PROGRESS -> "*";
            case WHITE_WINS -> "1-0";
            case BLACK_WINS -> "0-1";
            default -> "1/2-1/2";
        };
        return "[Event \"" + identity.dataset() + "/" + plan.id() + "\"]\n[White \"Hard hint study\"]\n[Black \"Hard hint study\"]\n"
                + "[Result \"" + result + "\"]\n[SourceRevision \"" + identity.revision() + "\"]\n"
                + "[AuditRun \"" + identity.run() + "\"]\n\n" + String.join(" ", moves) + " " + result + "\n";
    }

    static String fen(ChessMatch match) {
        var board = match.position();
        var placement = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            if (rank < 7) placement.append('/');
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                var piece = board.pieceAt(BoardSquare.of(file, rank));
                if (piece == null) { empty++; continue; }
                if (empty > 0) { placement.append(empty); empty = 0; }
                char symbol = "pnbrqk".charAt(piece.kind().ordinal());
                placement.append(piece.side() == ChessPosition.Side.WHITE ? Character.toUpperCase(symbol) : symbol);
            }
            if (empty > 0) placement.append(empty);
        }
        int mask = board.searchKey().castleRights(), quiet = 0;
        String rights = ((mask & 2) != 0 ? "K" : "") + ((mask & 1) != 0 ? "Q" : "")
                + ((mask & 8) != 0 ? "k" : "") + ((mask & 4) != 0 ? "q" : "");
        String ep = "-";
        for (int ply = 1; ply <= match.ply(); ply++) {
            var before = match.positionAt(ply - 1);
            var move = match.moveAt(ply);
            boolean pawn = before.pieceAt(move.from()).kind() == Kind.PAWN;
            quiet = pawn || MatchMaterial.victim(before, move) != null ? 0 : quiet + 1;
            if (ply == match.ply() && pawn && Math.abs(move.to() - move.from()) == 16) ep = BoardSquare.name((move.to() + move.from()) / 2);
        }
        return placement + " " + (board.turn() == ChessPosition.Side.WHITE ? "w" : "b") + " "
                + (rights.isEmpty() ? "-" : rights) + " " + ep + " " + quiet + " " + (match.ply() / 2 + 1);
    }

    private static String san(ChessPosition before, Move move) {
        var piece = before.pieceAt(move.from());
        String notation;
        if (piece.kind() == Kind.KING && Math.abs(move.to() - move.from()) == 2) notation = move.to() > move.from() ? "O-O" : "O-O-O";
        else {
            boolean capture = MatchMaterial.victim(before, move) != null;
            var label = new StringBuilder(piece.kind() == Kind.PAWN ? "" : "PNBRQK".substring(piece.kind().ordinal(), piece.kind().ordinal() + 1));
            if (piece.kind() == Kind.PAWN && capture) label.append(BoardSquare.name(move.from()).charAt(0));
            if (piece.kind() != Kind.PAWN) {
                var peers = before.legalMoves().stream().filter(other -> other.to() == move.to() && other.from() != move.from()
                        && before.pieceAt(other.from()).kind() == piece.kind()).toList();
                if (!peers.isEmpty()) {
                    boolean fileFree = peers.stream().noneMatch(other -> other.from() % 8 == move.from() % 8);
                    boolean rankFree = peers.stream().noneMatch(other -> other.from() / 8 == move.from() / 8);
                    if (fileFree || !rankFree) label.append(BoardSquare.name(move.from()).charAt(0));
                    if (!fileFree) label.append(BoardSquare.name(move.from()).charAt(1));
                }
            }
            if (capture) label.append('x');
            label.append(BoardSquare.name(move.to()));
            if (move.promotion() != null) label.append('=').append("PNBRQK".charAt(move.promotion().ordinal()));
            notation = label.toString();
        }
        var after = before.play(move);
        return notation + (after.isCheckmate() ? "#" : after.isCheck() ? "+" : "");
    }

    static String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("Cannot encode audit evidence", failure); }
    }

    static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException unavailable) { throw new IllegalStateException(unavailable); }
    }
}
