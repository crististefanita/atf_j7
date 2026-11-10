package ro.atf.chess;

import atf.reporting.contract.AtfAssertion;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assertions;

final class Checks {
    private final List<AtfAssertion> rows = new ArrayList<>();

    void same(String name, Object expected, Object actual) {
        rows.add(new AtfAssertion(name, String.valueOf(expected), String.valueOf(actual), java.util.Objects.equals(expected, actual)));
        Assertions.assertEquals(expected, actual, name);
    }

    List<AtfAssertion> rows() { return List.copyOf(rows); }
}
