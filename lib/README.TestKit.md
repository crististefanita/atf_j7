# Test Kit — JUnit Classification and Auditing

## Overview

Kit provides annotations, evaluated comparisons, and a neutral lifecycle. The JAR leaves browser, broker, and ADB startup and Extent publication to its consumers.

| Need | API and Boundary |
|---|---|
| Test level | @AtfUnitTest, @AtfComponentTest, @AtfIntegrationTest, @AtfE2eTest classify tests without launching them automatically. |
| Auditable comparisons | RecordedAssertions. The JUnit verdict precedes the 240-character preview, which keeps Unicode pairs intact. |
| Existing JUnit assertions | AuditedAssertions in the adapter scope. Diagnostics are lazy, and identity and cause are preserved. Direct JUnit calls need explicit auditing. |
| Full values | AssertionValueDisplay can pass redacted text and its expected or actual role to a Projection observer before previewing. The consumer owns retention. |
| Phases and cleanup | ComponentAuditExtension / ComponentAuditAdapter, TestRunInitialization, FailureAwareCleanup. |
| Policies and resources | Atf*Policy resolvers. Use readResourceUtf8 for classpath or JAR resources and requiredPath for a real file. |

## Minimal Example

The adapter owns the recorder and publishes the result. A mismatch preserves the original JUnit exception.

```java
import atf.testkit.assertions.RecordedAssertions;

public static void verifyState(RecordedAssertions audit, String actual) {
    audit.equals("State", "ready", actual);
}
```

## Scope and Integration

- The dependency atf.api:atf-test-kit uses atf.testkit.version from the project POM. Use test scope in the product. Compile scope is appropriate only for the main support code of a test module. Keep Kit outside the APK or application.
- JUnit Jupiter API and Jackson are transitive. The runner declares the junit-jupiter-engine and its selection rules. The level annotation works alongside @Test and the consumer's extension.
- For Extent, the adapter sends checks to AssertionAuditReport and AssertionAuditRun owns the shared session. Rendering stays with Reporting. [Reporting integration](README.Reporting.md#scope-and-integration).
- Inject the sanitizer through AssertionValueDisplay before previewing. The comparison runs once. Stored values are bounded and do not form a complete archive. Pure unit tests keep their own policy.
- Scopes and resources are per invocation. Close them and preserve the original cause. A local lock alone cannot isolate global policies from outside readers.

---

Verify local contracts with `PlatformLibraryProvenanceTest` and `SuiteGovernanceTest`. Keep coordinates, hashes, and integration notes aligned with `library-provenance.json`.

[Verify the library integration in Chess and the examples in J0](README.md#verification)
