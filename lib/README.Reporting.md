# Reporting — Evidence and Auditable Reports

## Overview

Reporting publishes the caller's data, comparisons, and verdict in Extent or fallback. It works independently of Core and Kit.

| Need | API |
|---|---|
| Individual, class, or suite run | `AssertionAuditRun` |
| Invocation | `run.open(Identity)` → `AssertionAuditReport` |
| Ports / fallback | `AtfReportPorts` / `HtmlReportSession` |
| Observed HTTP | `request.execution(ReportExecutionEvidence)` with `ReportResponse` |
| Screenshots | `UiExtentReportSession` with the full PNG |

## Minimal Example

For an individual run, use `try (var run = open(output)) { publish(run, actual); }`. The suite retains the run and opens one scenario per invocation.

```java
import atf.reporting.audit.AssertionAuditRun;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.security.SensitiveDataRedactor;
import java.nio.file.Path;
import java.util.List;

public static AssertionAuditRun open(Path output) {
    var settings = ReportingSettings.defaults();
    var redactor = new SensitiveDataRedactor();
    return new AssertionAuditRun(new ExtentReportSession(output, settings, redactor), redactor, settings);
}

public static void publish(AssertionAuditRun run, String actual) {
    boolean passed = "ready".equals(actual);
    var status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
    var identity = new AssertionAuditReport.Identity("HealthContract", "example", "state is ready", "state", List.of("component"));
    try (var report = run.open(identity)) {
        report.phase("TEST BODY", List.of(new AtfAssertion("State", "ready", String.valueOf(actual), passed)), status, "");
        report.complete(status);
    }
    if (!passed) throw new AssertionError("State differs; inspect the report");
}
```

## Scope and Integration

| Owner | Closing Behavior and Boundary |
|---|---|
| AssertionAuditRun | Owns the session. Closing it publishes the final HTML. |
| run.open(identity) | Borrows the session. Closing it ends the invocation's scenario. |
| AssertionAuditReport(session, ...) constructor | Owns and closes its own session. |

- Use the same settings and redactor in the run and renderer. `assertionMaxDisplayCharacters()` reads the default preview length of **240**. Full values are available before previewing.
- The example runs on the session thread. `AssertionAuditRun.extent(...)` serializes publication for concurrent callers.
- Kit collects the JUnit comparison, evaluated once, and Reporting publishes it. The caller retains the verdict, cause, and timing through `phase(..., timing)`.
- Fails indexes the terminal cause on the failing ACTION or OBSERVE row. Tests also retains collapsible diagnostics, with independent comparisons kept separate.
- The PNG retains the full viewport or display. The adapter owns capture and zoom. Identical screenshots in one step retain one piece of evidence and the first caption.
- After close, `ReportArtifactInspector.inspect(path, profile)` qualifies the inventory and embedded resources. Diagnostics locate external dependencies. Text paths remain valid. The inspector is static. Verify rendering and interaction separately in a browser.
- Titles are published as literal text in Extent and fallback. The profile accepts the original labels, including multiline labels. `ReportArtifactProfile.visibleLabel(label)` applies the same whitespace projection as the inspector.
- The caller owns the browser. Separate JUnit discovery diagnostics from execution, close the context before the browser, and preserve the original cause during cleanup.

HTTP duration comes from `ReportResponse.duration()`. `detail(...)` preserves the caller's text as supplied.

---

J0 [code, tests, and feedback](README.md#maintenance-and-feedback).
