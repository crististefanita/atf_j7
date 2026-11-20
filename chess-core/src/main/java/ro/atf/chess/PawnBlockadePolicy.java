package ro.atf.chess;

import java.util.Map;
import java.util.Set;

/** Discounts a king-blockaded passer only in the presence of an uncatchable outside-pawn race. */
record PawnBlockadePolicy(boolean enabled, int advanceDiscountPermille) {
    static final PawnBlockadePolicy CURRENT=read(ChessYaml.resource("pawn-blockade.yaml"));

    PawnBlockadePolicy {
        if(advanceDiscountPermille<0||advanceDiscountPermille>1000)throw new IllegalArgumentException("Invalid pawn blockade discount");
    }
    int discount(int advancement) {
        return enabled ? advancement * advanceDiscountPermille / 1000 : 0;
    }
    boolean confirms(ChessPosition position,ChessPosition.Move proposed) {
        return enabled && MatchPositionEvaluation.outsidePawnAdvance(position,proposed);
    }
    static PawnBlockadePolicy read(Map<?,?> row) {
        if(!row.keySet().equals(Set.of("schema","enabled","advance-discount-per-mille"))
                ||ChessYaml.number(row,"schema")!=1||!(row.get("enabled") instanceof Boolean enabled))
            throw new IllegalArgumentException("Invalid pawn blockade policy");
        return new PawnBlockadePolicy(enabled,ChessYaml.number(row,"advance-discount-per-mille"));
    }
}
