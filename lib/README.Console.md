# Console — Execution Diagnostics

## Overview

`atf-console` projects ATF events to stdout and stderr with lifecycle, timing, and configurable details. Use it to follow a Maven or IntelliJ run. [Reporting](README.Reporting.md) produces the HTML artifact.

## Minimal Example

This method fragment sets an explicit policy and leaves the stream in the adapter.

```java
import atf.console.AtfConsoleReporter;
import atf.console.ConsoleSettings;

var console = AtfConsoleReporter.configuredStdout(
        new ConsoleSettings(true, false));
console.info("The scenario catalog has been loaded");
```

`configuredStdout()` reads `atf.report.config` → `ATF_REPORT_CONFIG` → `atf.yaml/yml`. The available options are shown below.

```yaml
reporting:
  console:
    enabled: true
    detailsEnabled: false
```

## Integration and Options

| Intent | Choice |
|---|---|
| Message independent of detail settings | `info(...)` |
| Typed events with lifecycle and duration | `publish(AtfConsoleEvent)` through the Core port `AtfConsoleSink` |
| Disable console output entirely | `enabled: false` or `ConsoleSettings.disabled()` |
| Print the final report path in the console | Constructor with `reportPathEnabled: true` and the `REPORT_PUBLISHED` event |
| JUnit policy per class | The Console JUnit adapter and Test Kit annotations. Declare Kit explicitly. |

- Printing the path requires opt-in, an enabled console, and a confirmed artifact event. `detailsEnabled` controls detail visibility only.
- Console depends on Core. The JUnit adapter uses Kit when declared.
- Console renders diagnostics. The invoking test owns the comparison and verdict.

---

Before adopting a new version, check local compatibility with `PlatformLibraryProvenanceTest` and diagnostic rendering with `ExtentGovernanceTest`. Keep coordinates, hashes, and integration notes aligned with `library-provenance.json`.

[Verify the library integration in Chess and the examples in J0](README.md#verification)
