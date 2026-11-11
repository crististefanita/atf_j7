package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.diagnostic.ReportPhaseDiagnostic.Kind;
import atf.reporting.model.timing.ReportPhaseTiming;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.opentest4j.TestAbortedException;

final class ExtentAudit implements InvocationInterceptor, ParameterResolver, BeforeEachCallback, AfterEachCallback {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ExtentAudit.class);

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Checks.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return checks(context);
    }

    @Override public void beforeEach(ExtensionContext context) {
        context.getStore(NAMESPACE).put("started", System.nanoTime());
    }

    @Override public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
        invocation.proceed();
        var rows = checks(context).rows();
        if (rows.isEmpty()) throw new AssertionError("An audited component test requires comparisons");
        if (rows.stream().anyMatch(row -> !row.passed())) throw new AssertionError("An audited comparison failed");
    }

    @Override public void afterEach(ExtensionContext context) {
        Long start = context.getStore(NAMESPACE).get("started", Long.class);
        var timing = start == null ? null : new ReportPhaseTiming(Duration.ofNanos(System.nanoTime() - start), 1);
        var report = context.getRoot().getStore(NAMESPACE)
                .getOrComputeIfAbsent(SuiteReport.class, ignored -> openReport(context), SuiteReport.class);
        var tags = Stream.concat(Stream.of("chess"), context.getTags().stream()).distinct().sorted().toList();
        report.write(context.getRequiredTestClass().getSimpleName(), context.getDisplayName(), tags,
                checks(context).rows(), timing, context.getExecutionException().orElse(null));
    }

    private static Checks checks(ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(Checks.class);
    }

    private static SuiteReport openReport(ExtensionContext context) {
        try {
            return new SuiteReport(context.getConfigurationParameter("chess.extent").orElse(""));
        } catch (IOException error) {
            throw new UncheckedIOException(error);
        }
    }

    private static final class SuiteReport implements ExtensionContext.Store.CloseableResource {
        private final ExtentReportSession session;

        SuiteReport(String configured) throws IOException {
            Path path = configured.isBlank() ? Files.createTempDirectory("j7-extent-").resolve("components.html") : Path.of(configured).toAbsolutePath();
            if (Files.exists(path)) throw new IOException("Use a fresh Extent path: " + path);
            Files.createDirectories(path.getParent());
            session = new ExtentReportSession(path, AtfReportConfigurationLoader.load().reportingSettings());
            System.out.println("J7 component Extent: " + path);
        }

        synchronized void write(String owner, String label, List<String> tags, List<AtfAssertion> rows, ReportPhaseTiming timing, Throwable failure) {
            var status = failure == null ? AtfReportStatus.PASS
                    : failure instanceof TestAbortedException ? AtfReportStatus.SKIP : AtfReportStatus.FAIL;
            try (var scenario = session.startScenario(owner, label, tags);
                 var phase = scenario.startPhase("TEST BODY")) {
                if (timing != null) phase.executionTiming(timing);
                if (failure != null) phase.diagnostic(status == AtfReportStatus.SKIP ? Kind.EXECUTION_ABORTED : Kind.JUNIT_FAILURE, failure.toString());
                if (!rows.isEmpty()) {
                    try (var step = phase.startAssertionStep("Assertions")) {
                        step.assertions(rows);
                        step.complete(rows.stream().allMatch(AtfAssertion::passed) ? AtfReportStatus.PASS : AtfReportStatus.FAIL);
                    }
                }
                phase.complete(status);
                scenario.complete(status);
            }
        }

        @Override public void close() { session.close(); }
    }
}
