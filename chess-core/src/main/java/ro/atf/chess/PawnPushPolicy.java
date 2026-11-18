package ro.atf.chess;

import java.util.Map;
import java.util.Set;

/** A bounded winning-plan certificate, separate from the ordinary evaluation horizon. */
record PawnPushPolicy(boolean enabled,int maxPawns,int maxPlies) {
    static final PawnPushPolicy CURRENT=read(ChessYaml.resource("hard-pawn-promotion.yaml"));

    static PawnPushPolicy read(Map<?,?> values) {
        if(!values.keySet().equals(Set.of("schema","enabled","max-pawns","max-plies"))
                ||ChessYaml.number(values,"schema")!=1||!(values.get("enabled") instanceof Boolean enabled))
            throw new IllegalArgumentException("Invalid forced pawn promotion policy");
        int pawns=ChessYaml.number(values,"max-pawns"),plies=ChessYaml.number(values,"max-plies");
        if(pawns<1||pawns>2||plies<3||plies>32)throw new IllegalArgumentException("Unsupported pawn proof limits");
        return new PawnPushPolicy(enabled,pawns,plies);
    }
}
