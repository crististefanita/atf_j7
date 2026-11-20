package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest @ExtendWith(ExtentAudit.class)
class NativeModelAuditTest {
    @Test void completeCurrentSnapshotsRemainReadOnly(Checks check) {
        var header=current();var before=header.deepCopy();NativeModelAudit.validate(header);
        check.same("Validation cannot rewrite the producer or model snapshots",before,header);
    }
    @Test void rejectsMissingOrForeignPoliciesEvenWithTheOtherProfileFieldsPresent(Checks check) {
        for(String field:new String[]{"queenReplyPolicy","pawnBlockadePolicy"}) {
            var missing=current();missing.remove(field);
            check.rejects("Old metadata cannot silently borrow the active model: "+field,IllegalArgumentException.class,()->NativeModelAudit.validate(missing));
            var foreign=current();var policy=(ObjectNode)foreign.path(field);policy.put("enabled",!policy.path("enabled").asBoolean());
            check.rejects("A valid different model is still a different producer: "+field,IllegalArgumentException.class,()->NativeModelAudit.validate(foreign));
            var extra=current();((ObjectNode)extra.path(field)).put("foreign-reserve",500000);
            check.rejects("Unexpected model fields cannot be ignored: "+field,IllegalArgumentException.class,()->NativeModelAudit.validate(extra));
        }
    }
    private static ObjectNode current() {
        var json=LearningGameAudit.mapper();var header=json.createObjectNode();header.put("revision","a".repeat(40));header.put("run","model-header-control");
        header.set("nativePolicy",json.valueToTree(HardAdaptivePolicy.CURRENT));header.set("promotionExchangePolicy",json.valueToTree(PromotionExchangePolicy.CURRENT));header.set("pawnPushPolicy",json.valueToTree(PawnPushPolicy.CURRENT));
        header.set("queenReplyPolicy",json.valueToTree(QueenReplyPolicy.CURRENT));header.set("pawnBlockadePolicy",json.valueToTree(PawnBlockadePolicy.CURRENT));return header;
    }
}
