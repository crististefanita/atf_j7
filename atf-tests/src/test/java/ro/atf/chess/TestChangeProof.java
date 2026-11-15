package ro.atf.chess;

import atf.testkit.scope.TestRunScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.EngineFilter;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import ro.atf.chess.suites.ChessComponentSuite;
import ro.atf.chess.suites.ChessGovernanceSuite;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/** Runs the exact audited JUnit methods declared as replacement evidence. */
final class TestChangeProof {
    private static final Set<String> REVIEW_CLASSES = Set.of("ro.atf.chess.TestChangeReview",
            "ro.atf.chess.TestChangeProofTest");

    private record Discovered(MethodSource source, boolean component) { }

    private TestChangeProof() { }

    static void verify(Set<String> methods, Set<String> componentOnly, Path extent) throws IOException {
        verify(methods, componentOnly, extent, ChessComponentSuite.class, ChessGovernanceSuite.class);
    }

    static void verify(Set<String> methods, Set<String> componentOnly, Path extent, Class<?> componentSuite,
            Class<?> governanceSuite) throws IOException {
        if (!methods.containsAll(componentOnly)) throw new IOException("Missing declared component proof methods");
        if (methods.isEmpty()) return;
        if (extent == null || Files.exists(extent, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("A fresh Extent path is required for test change proof: " + extent);
        }
        var launcher = LauncherFactory.create();
        var discovered = new HashMap<String, List<Discovered>>();
        for (var method : discover(launcher, componentSuite, true, methods)) {
            discovered.computeIfAbsent(name(method.source()), ignored -> new ArrayList<>()).add(method);
        }
        for (var method : discover(launcher, governanceSuite, false, methods)) {
            discovered.computeIfAbsent(name(method.source()), ignored -> new ArrayList<>()).add(method);
        }
        var selectors = new ArrayList<DiscoverySelector>();
        for (String method : methods.stream().sorted().toList()) {
            if (REVIEW_CLASSES.stream().anyMatch(owner -> method.startsWith(owner + "#"))) {
                throw new IOException("Test change review cannot serve as its own proof: " + method);
            }
            var matches = discovered.getOrDefault(method, List.of());
            if (matches.isEmpty()) throw new IOException("Proof method is not in a J7 component or governance suite: " + method);
            if (matches.size() != 1) throw new IOException("Proof method identifies more than one suite test: " + method);
            var match = matches.getFirst();
            if (componentOnly.contains(method) && !match.component()) {
                throw new IOException("Android replacement proof must belong to ChessComponentSuite: " + method);
            }
            var owner = match.source().getJavaClass();
            if (Arrays.stream(owner.getAnnotationsByType(ExtendWith.class))
                    .flatMap(extension -> Arrays.stream(extension.value())).noneMatch(ExtentAudit.class::equals)) {
                throw new IOException("Proof method is missing ExtentAudit: " + method);
            }
            selectors.add(selectMethod(owner, match.source().getJavaMethod()));
        }
        run(launcher, selectors, methods, extent);
    }

    private static List<Discovered> discover(Launcher launcher, Class<?> suite, boolean component, Set<String> methods) {
        var plan = launcher.discover(LauncherDiscoveryRequestBuilder.request().selectors(selectClass(suite))
                .filters(EngineFilter.includeEngines("junit-platform-suite")).build());
        return plan.getRoots().stream().flatMap(root -> plan.getDescendants(root).stream())
                .filter(TestIdentifier::isTest).flatMap(test -> test.getSource().stream())
                .filter(MethodSource.class::isInstance).map(MethodSource.class::cast)
                .filter(source -> methods.contains(name(source))).map(source -> new Discovered(source, component)).toList();
    }

    private static String name(MethodSource source) {
        return source.getClassName() + "#" + source.getMethodName();
    }

    private static void run(Launcher launcher, List<DiscoverySelector> selectors, Set<String> methods, Path extent) throws IOException {
        var listener = new SummaryGeneratingListener();
        launcher.execute(LauncherDiscoveryRequestBuilder.request().selectors(selectors)
                .filters(EngineFilter.includeEngines("junit-jupiter"))
                .configurationParameter("chess.extent", extent.toAbsolutePath().toString())
                .configurationParameter(TestRunScope.CONFIGURATION_PARAMETER, "suite")
                .configurationParameter("chess.suite", "Test change proof")
                .build(), listener);
        var summary = listener.getSummary();
        if (summary.getTestsFoundCount() != methods.size() || summary.getTestsSucceededCount() != methods.size()
                || summary.getTotalFailureCount() != 0 || summary.getTestsSkippedCount() != 0 || summary.getTestsAbortedCount() != 0) {
            String failures = String.join(", ", summary.getFailures().stream()
                    .map(failure -> failure.getTestIdentifier().getDisplayName() + ": " + failure.getException()).toList());
            throw new IOException("Test change proof did not pass for " + methods.stream().sorted().toList()
                    + " (found " + summary.getTestsFoundCount() + ", passed " + summary.getTestsSucceededCount()
                    + ", skipped " + summary.getTestsSkippedCount() + ", aborted " + summary.getTestsAbortedCount()
                    + ", failed " + summary.getTotalFailureCount() + ")" + (failures.isEmpty() ? "" : ": " + failures));
        }
        if (!Files.isRegularFile(extent, LinkOption.NOFOLLOW_LINKS) || Files.size(extent) == 0) {
            throw new IOException("Proof methods did not publish Extent evidence: " + extent);
        }
    }
}
