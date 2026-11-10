package ro.atf.chess;

import atf.reporting.configuration.AtfReportConfigurationLoader;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;

final class ExtentAudit implements InvocationInterceptor, ParameterResolver {
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(ExtentAudit.class);

    @Override public boolean supportsParameter(ParameterContext parameter, ExtensionContext context) {
        return parameter.getParameter().getType() == Checks.class;
    }

    @Override public Object resolveParameter(ParameterContext parameter, ExtensionContext context) {
        return context.getStore(NAMESPACE).getOrComputeIfAbsent(Checks.class);
    }

    @Override public void interceptTestMethod(Invocation<Void> invocation,
            ReflectiveInvocationContext<java.lang.reflect.Method> method, ExtensionContext context) throws Throwable {
        var checks = context.getStore(NAMESPACE).get(Checks.class, Checks.class);
        var report = context.getRoot().getStore(NAMESPACE).getOrComputeIfAbsent(SuiteReport.class);
        Throwable failure = null;
        try {
            invocation.proceed();
            if (checks.rows().isEmpty()) throw new AssertionError("An audited component test requires comparisons");
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            try {
                report.write(context.getRequiredTestClass().getSimpleName(), context.getDisplayName(), checks.rows(), failure);
            } catch (Throwable publication) {
                if (failure == null) throw publication;
                if (failure != publication) failure.addSuppressed(publication);
            }
        }
    }

    static final class SuiteReport implements ExtensionContext.Store.CloseableResource {
        private final ExtentReportSession session;

        SuiteReport() throws IOException {
            String configured = System.getProperty("chess.extent", "");
            Path path = configured.isBlank() ? Files.createTempDirectory("j7-extent-").resolve("components.html") : Path.of(configured).toAbsolutePath();
            if (Files.exists(path)) throw new IOException("Use a fresh Extent path: " + path);
            Files.createDirectories(path.getParent());
            session = new ExtentReportSession(path, AtfReportConfigurationLoader.load().reportingSettings());
            System.out.println("J7 component Extent: " + path);
        }

        synchronized void write(String owner, String label, List<AtfAssertion> rows, Throwable failure) {
            var status = failure == null ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            try (var scenario = session.startScenario(owner, label, List.of("component", "chess"));
                 var phase = scenario.startPhase("TEST BODY")) {
                if (failure != null) phase.detail("Execution failure", failure.toString());
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
