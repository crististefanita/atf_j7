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
        try {
            Assertions.assertEquals(expected, actual, name);
            rows.add(new AtfAssertion(name, String.valueOf(expected), String.valueOf(actual), true));
        } catch (AssertionFailedError failure) {
            rows.add(new AtfAssertion(name, String.valueOf(expected), String.valueOf(actual), false));
            throw failure;
        }
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

    int size() { return rows.size(); }

    boolean failedSince(int first) { return rows.subList(first, rows.size()).stream().anyMatch(row -> !row.passed()); }

    List<AtfAssertion> rows() { return List.copyOf(rows); }
}
