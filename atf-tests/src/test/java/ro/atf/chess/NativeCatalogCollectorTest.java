package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

@AtfComponentTest @ExtendWith(ExtentAudit.class)
class NativeCatalogCollectorTest {
    @TempDir Path folder;
    @Test void observationsCannotBeWrittenInsideTheCheckout(Checks check) {
        check.rejects("A native study must not pollute project sources",IllegalArgumentException.class,
                ()->NativeCatalogCollector.main(new String[]{ProjectRoot.locate().toString(),"a".repeat(40)}));
    }
    @Test void existingEvidenceIsRejectedBeforeAnyOtherFileIsCreated(Checks check)throws Exception {
        Files.writeString(folder.resolve("native.jsonl"),"retained-observation");
        check.rejects("A completed or interrupted observation is immutable",IllegalArgumentException.class,
                ()->NativeCatalogCollector.main(new String[]{folder.toString(),"a".repeat(40)}));
        check.same("The old bytes remain unchanged","retained-observation",Files.readString(folder.resolve("native.jsonl")));
        check.same("No new identity can be placed beside an old game",false,Files.exists(folder.resolve("identity.json")));
    }
    @Test void anAmbiguousProducerCannotCreateAnOutputDirectory(Checks check) {
        var target=folder.resolve("unqualified");
        check.rejects("A short or missing source revision is not producer identity",IllegalArgumentException.class,
                ()->NativeCatalogCollector.main(new String[]{target.toString(),"main"}));
        check.same("Preflight fails before filesystem mutation",false,Files.exists(target));
    }
}
