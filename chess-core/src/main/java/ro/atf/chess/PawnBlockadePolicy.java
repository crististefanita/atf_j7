package ro.atf.chess;

import java.util.Map;
import java.util.Set;

/** Discounts advancement that a passed pawn cannot currently use in a king-and-pawn ending. */
record PawnBlockadePolicy(boolean enabled, int advanceDiscountPermille) {
    static final PawnBlockadePolicy CURRENT=read(ChessYaml.resource("pawn-blockade.yaml"));

    PawnBlockadePolicy {
        if(advanceDiscountPermille<0||advanceDiscountPermille>1000)throw new IllegalArgumentException("Invalid pawn blockade discount");
    }
    static PawnBlockadePolicy read(Map<?,?> row) {
        if(!row.keySet().equals(Set.of("schema","enabled","advance-discount-per-mille"))
                ||ChessYaml.number(row,"schema")!=1||!(row.get("enabled") instanceof Boolean enabled))
            throw new IllegalArgumentException("Invalid pawn blockade policy");
        return new PawnBlockadePolicy(enabled,ChessYaml.number(row,"advance-discount-per-mille"));
    }
}
