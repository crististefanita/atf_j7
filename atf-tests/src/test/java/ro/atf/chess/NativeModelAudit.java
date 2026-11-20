package ro.atf.chess;

import com.fasterxml.jackson.databind.JsonNode;

/** Current publications must identify every active selective model; old receipts are never relabeled. */
final class NativeModelAudit {
    private NativeModelAudit() { }

    static void validate(JsonNode identity) {
        require(identity,"queenReplyPolicy",QueenReplyPolicy.CURRENT);
        require(identity,"pawnBlockadePolicy",PawnBlockadePolicy.CURRENT);
    }

    private static void require(JsonNode identity,String name,Object expected) {
        if(!LearningGameAudit.mapper().valueToTree(expected).equals(identity.path(name)))
            throw new IllegalArgumentException("Missing or foreign native model: "+name);
    }
}
