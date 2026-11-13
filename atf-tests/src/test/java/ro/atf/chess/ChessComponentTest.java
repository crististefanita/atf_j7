package ro.atf.chess;

import static ro.atf.chess.BoardSquare.parse;
import static ro.atf.chess.ChessPosition.Kind.*;
import static ro.atf.chess.ChessPosition.Side.*;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import ro.atf.chess.ChessPosition.Move;

@AtfComponentTest
@ExtendWith(ExtentAudit.class)
class ChessComponentTest {
    @Test void legalMoveTreeMatchesStockfishAcrossCastlingAndPromotion(Checks check) {
        var castling = ChessPosition.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        var promotion = ChessPosition.fromFen("rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8");
        var pawnAndRook = ChessPosition.fromFen("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1");
        check.same("Stockfish 19 perft 3 from the starting position", 8_902L, legalContinuations(ChessPosition.initial(), 3));
        check.same("Stockfish 19 perft 3 with castling rights on both sides", 97_862L, legalContinuations(castling, 3));
        check.same("Stockfish 19 perft 3 with promotion and a discovered attack", 62_379L, legalContinuations(promotion, 3));
        check.same("Stockfish 19 perft 4 in a pawn and rook ending", 43_238L, legalContinuations(pawnAndRook, 4));
    }

    private static long legalContinuations(ChessPosition position, int depth) {
        var moves = position.legalMoves();
        if (depth == 1) return moves.size();
        long count = 0;
        for (Move move : moves) count += legalContinuations(position.playGenerated(move), depth - 1);
        return count;
    }

    @Test void initialPositionAndFlippedCoordinates(Checks check) {
        var position = ChessPosition.initial();
        check.same("White moves first", WHITE, position.turn());
        check.same("White queen starts on d1", new ChessPosition.Piece(WHITE, QUEEN), position.pieceAt(parse("d1")));
        check.same("Black king starts on e8", new ChessPosition.Piece(BLACK, KING), position.pieceAt(parse("e8")));
        check.same("White orientation has h1 in the lower right", parse("h1"), BoardSquare.atDisplay(7, 7, false));
        check.same("Black orientation has a8 in the lower right", parse("a8"), BoardSquare.atDisplay(7, 7, true));
        check.same("The lower right square is light", true, BoardSquare.isLight(parse("a8")));
    }

    @Test void legalOpeningPerft(Checks check) {
        var start = ChessPosition.initial();
        check.same("Legal positions at ply one", 20L, positions(start, 1));
        check.same("Legal positions at ply two", 400L, positions(start, 2));
        check.same("Legal positions at ply three", 8902L, positions(start, 3));
        var kiwipete = ChessPosition.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        check.same("Stockfish reference includes both castling sides and all replies in Kiwipete", 2039L, positions(kiwipete, 2));
    }

    @Test void enPassantConsumesTheImmediateRight(Checks check) {
        var ready = line("e2e4", "a7a6", "e4e5", "d7d5");
        var captured = ready.play(move("e5d6"));
        check.same("En passant removes the adjacent pawn", null, captured.pieceAt(parse("d5")));
        check.same("The capturing pawn occupies d6", new ChessPosition.Piece(WHITE, PAWN), captured.pieceAt(parse("d6")));
        var late = ready.play(move("h2h3")).play(move("a6a5"));
        check.same("The same move is illegal one turn later", false, late.legalMovesFrom(parse("e5")).contains(move("e5d6")));
    }

    @Test void castlingMovesBothPiecesAndMateEndsTheGame(Checks check) {
        var castle = line("e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1");
        check.same("White king lands on g1", new ChessPosition.Piece(WHITE, KING), castle.pieceAt(parse("g1")));
        check.same("White rook lands on f1", new ChessPosition.Piece(WHITE, ROOK), castle.pieceAt(parse("f1")));
        var mate = line("f2f3", "e7e5", "g2g4", "d8h4");
        check.same("Fool's mate ends the game", ChessPosition.Outcome.CHECKMATE, mate.outcome());
        check.same("White has no legal move", 0L, positions(mate, 1));
    }

    @Test void promotionOffersFourChoices(Checks check) {
        var ready = line("a2a4", "h7h5", "a4a5", "h5h4", "a5a6", "h4h3", "a6b7", "h3g2");
        int from = parse("b7");
        int to = parse("a8");
        check.same("Promotion has four legal piece choices", 4L, ready.legalMovesFrom(from).stream().filter(m -> m.to() == to).count());
        check.same("Explicit underpromotion to knight", new ChessPosition.Piece(WHITE, KNIGHT), ready.play(new Move(from, to, KNIGHT)).pieceAt(to));
        check.same("No promotion is not a legal move", false, ready.legalMovesFrom(from).contains(new Move(from, to)));
    }

    @Test void aMoveMustResolveCheck(Checks check) {
        var position = line("e2e4", "d7d5", "f1b5");
        check.same("The bishop gives check", ChessPosition.Outcome.CHECK, position.outcome());
        check.same("Unrelated pawn move remains illegal", false, position.legalMovesFrom(parse("h7")).contains(move("h7h6")));
        check.same("Blocking the checking ray is legal", true, position.legalMovesFrom(parse("c7")).contains(move("c7c6")));
        check.same("The original position remains unchanged", null, position.pieceAt(parse("c6")));
    }

    private static long positions(ChessPosition position, int depth) {
        if (depth == 0) return 1;
        long count = 0;
        for (int square = 0; square < 64; square++) {
            for (Move move : position.legalMovesFrom(square)) count += positions(position.play(move), depth - 1);
        }
        return count;
    }

    private static ChessPosition line(String... moves) {
        var position = ChessPosition.initial();
        for (String text : moves) position = position.play(move(text));
        return position;
    }

    private static Move move(String text) { return new Move(parse(text.substring(0, 2)), parse(text.substring(2))); }
}
