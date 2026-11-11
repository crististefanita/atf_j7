package ro.atf.chess;

import atf.testkit.AtfComponentTest;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.discovery.ClassNameFilter;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import ro.atf.chess.suites.ChessBenchmarkSuite;
import ro.atf.chess.suites.ChessComponentSuite;
import ro.atf.chess.suites.ChessGovernanceSuite;
import ro.atf.chess.suites.ChessJvmSuite;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectPackage;

@AtfComponentTest
@Tag("governance")
@ExtendWith(ExtentAudit.class)
class SuiteGovernanceTest {
    @Test void everyJvmTestHasExactlyOnePrimarySuiteAndAnExtentAudit(Checks check) {
        var launcher = LauncherFactory.create();
        var all = launcher.discover(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectPackage("ro.atf.chess"))
                .filters(EngineFilter.includeEngines("junit-jupiter"), ClassNameFilter.includeClassNamePatterns("^[^$]+$"))
                .build());
        var expected = new TreeSet<String>();
        var unaudited = new TreeSet<String>();
        var wronglyTagged = new TreeSet<String>();
        var nativePublishers = new TreeSet<String>();
        for (var method : methods(all)) {
            var source = (MethodSource) method.getSource().orElseThrow();
            var tags = method.getTags().stream().map(tag -> tag.getName()).collect(Collectors.toSet());
            if (tags.contains("native-evidence")) {
                nativePublishers.add(source.getClassName());
                if (tags.contains("component")) wronglyTagged.add(name(source));
                continue;
            }
            expected.add(name(source));
            if (!tags.contains("component") || tags.containsAll(Set.of("governance", "benchmark"))) wronglyTagged.add(name(source));
            var extensions = source.getJavaClass().getAnnotationsByType(ExtendWith.class);
            if (Arrays.stream(extensions).flatMap(annotation -> Arrays.stream(annotation.value())).noneMatch(ExtentAudit.class::equals)) {
                unaudited.add(name(source));
            }
        }
        check.same("Native publishers keep a separate entry point", Set.of(AndroidSanityPublication.class.getName(), TalkBackPublication.class.getName()),
                nativePublishers);
        check.same("The discovered component inventory is nonempty", true, !expected.isEmpty());
        check.same("All JVM methods have a valid test layer", Set.of(), wronglyTagged);
        check.same("All JVM methods attach the Extent audit", Set.of(), unaudited);

        var memberships = new TreeMap<String, Integer>();
        for (Class<?> suite : List.of(ChessComponentSuite.class, ChessGovernanceSuite.class, ChessBenchmarkSuite.class)) {
            var tests = selection(suite);
            check.same(suite.getSimpleName() + " discovers tests", true, !tests.isEmpty());
            tests.forEach(test -> memberships.merge(test, 1, Integer::sum));
        }
        check.same("The primary suites cover the independent discovery inventory", expected, memberships.keySet());
        check.same("Each method belongs to one primary suite", List.of(),
                memberships.entrySet().stream().filter(entry -> entry.getValue() != 1).toList());
        check.same("The Maven suite discovers exactly the same methods once", List.copyOf(expected), selection(ChessJvmSuite.class));
    }

    private static List<String> selection(Class<?> suite) {
        var plan = LauncherFactory.create().discover(LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(suite)).filters(EngineFilter.includeEngines("junit-platform-suite")).build());
        return methods(plan).stream().map(test -> name((MethodSource) test.getSource().orElseThrow())).sorted().toList();
    }

    private static List<TestIdentifier> methods(TestPlan plan) {
        return plan.getRoots().stream().flatMap(root -> plan.getDescendants(root).stream())
                .filter(node -> node.getSource().filter(MethodSource.class::isInstance).isPresent()).toList();
    }

    private static String name(MethodSource source) {
        return source.getClassName() + "#" + source.getMethodName() + "(" + source.getMethodParameterTypes() + ")";
    }
}
