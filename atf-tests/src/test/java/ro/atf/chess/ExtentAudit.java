package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.configuration.ReportEnvironmentField;
import atf.reporting.configuration.ReportEnvironmentSnapshot;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.diagnostic.ReportPhaseDiagnostic.Kind;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.testkit.scope.TestRunScope;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.opentest4j.TestAbortedException;

final class ExtentAudit implements InvocationInterceptor, ParameterResolver, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ExtentAudit.class);

    private enum Stage {
        SETUP("SETUP"), BODY("TEST BODY"), TEARDOWN("TEARDOWN");

        private final String label;

        Stage(String label) { this.label = label; }
    }

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Checks.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return trail(context).checks;
    }

    @Override public void interceptBeforeEachMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
        trail(context).observe(Stage.SETUP, invocation);
    }

    @Override public void interceptAfterEachMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
        trail(context).observe(Stage.TEARDOWN, invocation);
    }

    @Override public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
        trail(context).observe(Stage.BODY, invocation);
    }

    @Override public void afterEach(ExtensionContext context) {
        var report = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(SuiteReport.class, ignored -> openReport(context), SuiteReport.class);
        var tags = Stream.concat(Stream.of("chess"), context.getTags().stream()).distinct().sorted().toList();
        var trail = trail(context);
        var reported = context.getExecutionException().orElse(null);
        var missing = reported == null && trail.checks.size() == 0 ? new AssertionError("An audited component test requires comparisons") : null;
        Throwable failure = missing == null ? reported : missing;
        if (failure != null && trail.stages.values().stream().noneMatch(stage -> stage.failures.contains(failure))) {
            trail.stage(Stage.BODY).failures.add(failure);
        }
        report.write(context.getRequiredTestClass().getSimpleName(), context.getDisplayName(), tags,
                trail.stages, status(failure));
        if (missing != null) throw missing;
    }

    private static AuditTrail trail(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(AuditTrail.class, ignored -> new AuditTrail(), AuditTrail.class);
    }

    private static AtfReportStatus status(Throwable failure) {
        if (failure == null) return AtfReportStatus.PASS;
        return failure instanceof TestAbortedException ? AtfReportStatus.SKIP : AtfReportStatus.FAIL;
    }

    private static SuiteReport openReport(ExtensionContext context) {
        try {
            var settings = AtfReportConfigurationLoader.load().reportingSettings();
            String selection = TestRunScope.selected(context) == TestRunScope.SUITE
                    ? context.getConfigurationParameter("chess.suite").orElse("JUnit suite") : "Direct JUnit selection";
            return new SuiteReport(context.getConfigurationParameter("chess.extent").orElse(""), settings, selection);
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static final class StageEvidence {
        private final List<AtfAssertion> rows = new ArrayList<>();
        private final List<Throwable> failures = new ArrayList<>();
        private long nanos;
        private int invocations;
    }

    private static final class AuditTrail {
        private final Checks checks = new Checks();
        private final EnumMap<Stage, StageEvidence> stages = new EnumMap<>(Stage.class);

        StageEvidence stage(Stage stage) {
            return stages.computeIfAbsent(stage, ignored -> new StageEvidence());
        }

        void observe(Stage stage, Invocation<Void> invocation) throws Throwable {
            var evidence = stage(stage);
            int first = checks.size();
            long start = System.nanoTime();
            try {
                invocation.proceed();
                if (checks.failedSince(first)) throw new AssertionError("An audited comparison failed");
            } catch (Throwable failure) {
                evidence.failures.add(failure);
                throw failure;
            } finally {
                evidence.nanos += System.nanoTime() - start;
                evidence.invocations++;
                var rows = checks.rows();
                evidence.rows.addAll(rows.subList(first, rows.size()));
            }
        }
    }

    private static final class SuiteReport implements ExtensionContext.Store.CloseableResource {
        private final ExtentReportSession session;

        SuiteReport(String configured, ReportingSettings settings, String selection) throws IOException {
            Path path = configured.isBlank() ? Files.createTempDirectory("j7-extent-").resolve("components.html") : Path.of(configured).toAbsolutePath();
            if (Files.exists(path)) throw new IOException("Use a fresh Extent path: " + path);
            Files.createDirectories(path.getParent());
            var environment = ReportEnvironmentSnapshot.builder()
                    .put(ReportEnvironmentField.PROFILE, selection)
                    .put(ReportEnvironmentField.ASSERTION_PREVIEW, settings.assertionMaxDisplayCharacters() + " characters")
                    .build();
            session = new ExtentReportSession(path, settings);
            session.updateEnvironment(environment);
            System.out.println("J7 component Extent: " + path);
        }

        synchronized void write(String owner, String label, List<String> tags, EnumMap<Stage, StageEvidence> stages, AtfReportStatus verdict) {
            try (var scenario = session.startScenario(owner, label, tags)) {
                for (var item : stages.entrySet()) {
                    var stage = item.getKey();
                    var evidence = item.getValue();
                    if (stage != Stage.BODY && evidence.rows.isEmpty() && evidence.failures.isEmpty()) continue;
                    try (var phase = scenario.startPhase(stage.label)) {
                        if (evidence.invocations > 0) phase.executionTiming(new ReportPhaseTiming(Duration.ofNanos(evidence.nanos), evidence.invocations));
                        for (var failure : evidence.failures) {
                            phase.diagnostic(status(failure) == AtfReportStatus.SKIP ? Kind.EXECUTION_ABORTED : Kind.JUNIT_FAILURE, failure.toString());
                        }
                        boolean failedCheck = evidence.rows.stream().anyMatch(row -> !row.passed());
                        if (!evidence.rows.isEmpty()) {
                            try (var step = phase.startAssertionStep("Assertions")) {
                                step.assertions(evidence.rows);
                                step.complete(failedCheck ? AtfReportStatus.FAIL : AtfReportStatus.PASS);
                            }
                        }
                        phase.complete(failedCheck || evidence.failures.stream().anyMatch(error -> status(error) == AtfReportStatus.FAIL)
                                ? AtfReportStatus.FAIL : status(evidence.failures.isEmpty() ? null : evidence.failures.getFirst()));
                    }
                }
                scenario.complete(verdict);
            }
        }

        @Override public void close() { session.close(); }
    }
}
