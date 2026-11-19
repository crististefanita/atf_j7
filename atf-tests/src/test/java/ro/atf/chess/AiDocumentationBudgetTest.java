package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class AiDocumentationBudgetTest {
    @Test void overviewDistinguishesTheConfiguredOrdinaryAndProofAllowances(Checks check) throws Exception {
        String guide = Files.readString(ProjectRoot.locate().resolve("docs/board/ai-quality.md"));
        String summary = guide.lines().filter(line -> line.startsWith("- **Hint budgets.**")
                || line.startsWith("- **Bugetele indiciului.**")).findFirst().orElse("");
        int ordinary = ((Number) ChessYaml.map(ChessYaml.resource("match-ai.yaml"), "hint").get("extra-nodes")).intValue();
        int reserve = CoachingProof.CURRENT.maxNodes();
        check.same("The overview declares ordinary work and the separate configured proof reserve", true,
                declares(summary, ordinary, reserve));
        check.same("The ordinary budget retains its own configuration source", true, guide.contains("match-ai.yaml"));
        String tableRow = guide.lines().filter(line -> line.startsWith("| Hint |") || line.startsWith("| Indiciu |"))
                .findFirst().orElse("");
        check.same("The league table does not present ordinary work as the total coaching cost", true,
                number(tableRow, ordinary) && !tableRow.contains("total")
                        && (tableRow.contains("ordinary") || tableRow.contains("ordinare"))
                        && (tableRow.contains("reserve") || tableRow.contains("rezervă")));
    }

    @Test void missingOrChangedProofLimitsCannotPassAsATotalHintCap(Checks check) {
        String correct = "Ordinary 200,000 nodes; separate reserve 5,000,000 nodes; coaching-proof.yaml";
        check.same("A separate ordinary and proof declaration is accepted", true, declares(correct, 200_000, 5_000_000));
        for (String wrong : List.of("One total 200,000-node ceiling", correct.replace("5,000,000", "6,000,000"),
                correct.replace("coaching-proof.yaml", "match-ai.yaml"))) {
            check.same("An incomplete or stale declaration is rejected: " + wrong, false,
                    declares(wrong, 200_000, 5_000_000));
        }
        check.same("Romanian formatting preserves the same configured values", true,
                declares("Ordinar 200.000; rezervă separată 5.000.000; coaching-proof.yaml", 200_000, 5_000_000));
    }

    private static boolean declares(String summary, int ordinary, int reserve) {
        return summary.contains("coaching-proof.yaml") && number(summary, ordinary) && number(summary, reserve)
                && (summary.contains("Ordinary") || summary.contains("ordinar") || summary.contains("Ordinar"))
                && (summary.contains("reserve") || summary.contains("rezervă"));
    }

    private static boolean number(String text, int value) {
        return List.of(Locale.US, Locale.forLanguageTag("ro-RO")).stream()
                .map(locale -> NumberFormat.getIntegerInstance(locale).format(value))
                .anyMatch(formatted -> Pattern.compile("(?<![0-9.,])" + Pattern.quote(formatted) + "(?![0-9.,])")
                        .matcher(text).find());
    }
}
