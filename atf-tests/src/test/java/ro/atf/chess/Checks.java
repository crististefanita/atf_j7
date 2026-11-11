package ro.atf.chess;

import atf.reporting.contract.AtfAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.function.Executable;
import org.opentest4j.AssertionFailedError;

final class Checks {
    private final List<AtfAssertion> rows = new ArrayList<>();

    void same(String name, Object expected, Object actual) {
        rows.add(new AtfAssertion(name, String.valueOf(expected), String.valueOf(actual), java.util.Objects.equals(expected, actual)));
        Assertions.assertEquals(expected, actual, name);
    }

    void rejects(String name, Class<? extends Throwable> type, Executable operation) {
        try {
            var observed = Assertions.assertThrows(type, operation, name);
            rows.add(new AtfAssertion(name, type.getName(), observed.getClass().getName(), true));
        } catch (AssertionFailedError failure) {
            String actual = failure.getCause() == null ? "No exception" : failure.getCause().getClass().getName();
            rows.add(new AtfAssertion(name, type.getName(), actual, false));
            throw failure;
        }
    }

    List<AtfAssertion> rows() { return List.copyOf(rows); }
}
