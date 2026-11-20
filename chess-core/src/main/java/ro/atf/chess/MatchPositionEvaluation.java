package ro.atf.chess;

import ro.atf.chess.ChessPosition.Kind;
import ro.atf.chess.ChessPosition.Piece;
import ro.atf.chess.ChessPosition.Side;

/** Positional terms for live play and coaching, in the search's pawn units. */
final class MatchPositionEvaluation {
    private static final int FULL_PHASE = 24;
    private static final int[][] OPENING = table(true);
    private static final int[][] ENDGAME = table(false);
    private static final int[] PHASE = {0, 1, 1, 2, 4, 0};

    private MatchPositionEvaluation() { }

    static int phase(ChessPosition position) {
        int value = 0;
        for (int square = 0; square < 64; square++) {
            var piece = position.pieceAt(square);
            if (piece != null) value += PHASE[piece.kind().ordinal()];
        }
        return Math.min(FULL_PHASE, value);
    }

    static int bonus(ChessPosition position) {
        return bonus(position, PawnBlockadePolicy.CURRENT);
    }

    static int bonus(ChessPosition position, PawnBlockadePolicy pawnPolicy) {
        int opening = 0;
        int endgame = 0;
        int phase = 0;
        int whiteBishops = 0;
        int blackBishops = 0;
        int whiteKing = -1;
        int blackKing = -1;
        int pieces = 0;
        Piece lastPiece = null;
        for (int square = 0; square < 64; square++) {
            Piece piece = position.pieceAt(square);
            if (piece == null) continue;
            boolean white = piece.side() == Side.WHITE;
            int relative = white ? square : square ^ 56;
            int sign = white ? 1 : -1;
            int kind = piece.kind().ordinal();
            if (piece.kind() == Kind.KING) {
                if (white) whiteKing = square;
                else blackKing = square;
            } else {
                pieces++;
                lastPiece = piece;
            }
            phase += PHASE[kind];
            opening += sign * OPENING[kind][relative];
            endgame += sign * ENDGAME[kind][relative];
            if (piece.kind() == Kind.BISHOP) {
                if (white) whiteBishops++;
                else blackBishops++;
            }
            if ((piece.kind() == Kind.BISHOP || piece.kind() == Kind.KNIGHT) && (relative == 19 || relative == 20)
                    && isPawn(position.pieceAt(square + (white ? -8 : 8)), piece.side())) {
                opening -= sign * 18;
            }
        }
        int bishopPair = (whiteBishops >= 2 ? 1 : 0) - (blackBishops >= 2 ? 1 : 0);
        phase = Math.min(phase, FULL_PHASE);
        int whiteBonus = (opening * phase + endgame * (FULL_PHASE - phase)) / FULL_PHASE + bishopPair * 22;
        if (phase == 0 && pawnPolicy.enabled()) {
            int blockedAdvance = 0;
            for (int square = 0; square < 64; square++) {
                var pawn = position.pieceAt(square);
                if (pawn != null && pawn.kind() == Kind.PAWN && kingBlocksPassedPawn(position, square, pawn)
                        && uncatchableOutsidePasser(position, square, pawn)) {
                    int relative = pawn.side() == Side.WHITE ? square : square ^ 56;
                    blockedAdvance += (pawn.side() == Side.WHITE ? 1 : -1) * ENDGAME[Kind.PAWN.ordinal()][relative];
                }
            }
            whiteBonus -= pawnPolicy.discount(blockedAdvance);
        }
        if (pieces == 1 && (lastPiece.kind() == Kind.ROOK || lastPiece.kind() == Kind.QUEEN)) {
            whiteBonus += lastPiece.side() == Side.WHITE ? matePressure(whiteKing, blackKing) : -matePressure(blackKing, whiteKing);
        }
        return position.turn() == Side.WHITE ? whiteBonus : -whiteBonus;
    }

    static boolean kingBlocksPassedPawn(ChessPosition position, int square, Piece pawn) {
        int step=pawn.side()==Side.WHITE?8:-8,front=square+step;
        if(front<0||front>=64)return false;
        var blocker=position.pieceAt(front);
        if(blocker==null||blocker.side()==pawn.side()||blocker.kind()!=Kind.KING)return false;
        return passedPawn(position,square,pawn);
    }

    private static boolean passedPawn(ChessPosition position,int square,Piece pawn) {
        int step=pawn.side()==Side.WHITE?8:-8;
        for(int ahead=square+step;ahead>=0&&ahead<64;ahead+=step) {
            for(int file=Math.max(0,square%8-1);file<=Math.min(7,square%8+1);file++) {
                var other=position.pieceAt(ahead/8*8+file);
                if(other!=null&&other.side()!=pawn.side()&&other.kind()==Kind.PAWN)return false;
            }
        }
        return true;
    }

    static boolean uncatchableOutsidePasser(ChessPosition position,int blockedSquare,Piece blockedPawn) {
        return uncatchableOutsidePasser(position,blockedSquare,blockedPawn,-1);
    }

    static boolean outsidePawnAdvance(ChessPosition position,ChessPosition.Move move) {
        var pawn=position.pieceAt(move.from());
        if(pawn==null||pawn.kind()!=Kind.PAWN||pawn.side()!=position.turn()||phase(position)!=0
                ||position.pieceAt(move.to())!=null||move.to()-move.from()!=(pawn.side()==Side.WHITE?8:-8))return false;
        for(int square=0;square<64;square++) {
            var blocked=position.pieceAt(square);
            if(blocked!=null&&blocked.kind()==Kind.PAWN&&blocked.side()!=pawn.side()
                    &&kingBlocksPassedPawn(position,square,blocked)
                    &&uncatchableOutsidePasser(position,square,blocked,move.from()))return true;
        }
        return false;
    }

    private static boolean uncatchableOutsidePasser(ChessPosition position,int blockedSquare,Piece blockedPawn,int requiredPawn) {
        int defendingKing=-1;
        for(int square=0;square<64;square++) {
            var p=position.pieceAt(square);
            if(p!=null&&p.side()==blockedPawn.side()&&p.kind()==Kind.KING){defendingKing=square;break;}
        }
        for(int square=0;square<64;square++) {
            var pawn=position.pieceAt(square);
            if(requiredPawn>=0&&square!=requiredPawn||pawn==null||pawn.kind()!=Kind.PAWN||pawn.side()==blockedPawn.side()
                    ||Math.abs(square%8-blockedSquare%8)<2||!passedPawn(position,square,pawn))continue;
            int step=pawn.side()==Side.WHITE?8:-8,promotion=(pawn.side()==Side.WHITE?56:0)+square%8;
            boolean clear=true;
            for(int ahead=square+step;ahead>=0&&ahead<64;ahead+=step)if(position.pieceAt(ahead)!=null){clear=false;break;}
            if(!clear)continue;
            int pawnMoves=Math.abs(promotion/8-square/8);
            int kingMoves=pawnMoves+(position.turn()==blockedPawn.side()?1:0);
            int distance=Math.max(Math.abs(defendingKing%8-promotion%8),Math.abs(defendingKing/8-promotion/8));
            // Include a possible capture on the promotion square after the defender's initial tempo.
            // Double pawn pushes are ignored conservatively; this is a static adjustment, never a win certificate.
            if(distance>kingMoves)return true;
        }
        return false;
    }

    private static int matePressure(int attacker, int defender) {
        int file = defender % 8;
        int rank = defender / 8;
        int edge = Math.min(Math.min(file, 7 - file), Math.min(rank, 7 - rank));
        int distance = Math.max(Math.abs(attacker % 8 - file), Math.abs(attacker / 8 - rank));
        return 24 * (3 - edge) + 12 * (7 - distance);
    }

    private static boolean isPawn(Piece piece, Side side) {
        return piece != null && piece.kind() == Kind.PAWN && piece.side() == side;
    }

    private static int[][] table(boolean opening) {
        var table = new int[Kind.values().length][64];
        for (Kind kind : Kind.values()) for (int square = 0; square < 64; square++) {
            int file = square % 8;
            int rank = square / 8;
            int center = 14 - Math.abs(2 * file - 7) - Math.abs(2 * rank - 7);
            table[kind.ordinal()][square] = opening ? opening(kind, file, rank, center) : endgame(kind, rank, center);
        }
        return table;
    }

    private static int opening(Kind kind, int file, int rank, int center) {
        return switch (kind) {
            case PAWN -> (file == 3 || file == 4) ? Math.min(rank - 1, 2) * 12 : Math.min(rank - 1, 3) * 3;
            case KNIGHT -> center * 4 + (rank == 0 ? -14 : 0);
            case BISHOP -> center * 2 + (rank == 0 ? -10 : 0);
            case ROOK -> rank == 6 ? 16 : 0;
            case QUEEN -> rank > 1 ? -10 : 0;
            case KING -> rank == 0 ? (file == 2 || file == 6 ? 24 : 0) : -center * 3;
        };
    }

    private static int endgame(Kind kind, int rank, int center) {
        return switch (kind) {
            case PAWN -> (rank - 1) * (rank - 1) * 3;
            case KNIGHT -> center * 3;
            case BISHOP, QUEEN -> center;
            case ROOK -> rank == 6 ? 18 : 0;
            case KING -> center * 4;
        };
    }
}
