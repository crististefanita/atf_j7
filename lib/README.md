# ATF Libraries

This directory documents how the Chess Lab tests use the ATF libraries.
J0 owns implementation and generic regression tests. J7 verifies its own integration.

## Provenance

```mermaid
flowchart LR
    J0["ATF sources and publication · J0"] -->|provides five JAR/POM pairs| Lib["Chess · lib/"]
    Lib -->|manifest pins file identity| Provenance["library-provenance.json"]
    Lib -->|root POM installs the pairs| Maven["Build Maven repository"]
    Maven -->|resolves Reporting and Test Kit in test scope| Suite["Chess suites · atf-tests"]
    Suite -->|publishes assertions and evidence| Extent["Extent HTML or fallback"]
```

<details>
<summary>Legend · How the ATF libraries reach Chess reports</summary>

- The [root POM](../pom.xml) installs the pairs. The [provenance manifest](../library-provenance.json)
  pins file identity, which the [provenance test](../atf-tests/src/test/java/ro/atf/chess/PlatformLibraryProvenanceTest.java) verifies.
- The [test POM](../atf-tests/pom.xml) declares Reporting and Test Kit in
  `test` scope. [Extent governance](../atf-tests/src/test/java/ro/atf/chess/ExtentGovernanceTest.java)
  verifies diagnostics and negative evidence in the report produced by Chess.
- The [product builds](../web/README.md#shared-engine-and-platform-adapters)
  consume the `chess-core` sources with their own native and Web adapters.
  The ATF JARs belong to the JVM suite that reports evidence from those platforms.

</details>

## Usage Guides

| Library | Use |
|---|---|
| [Core](README.Core.md) | Configuration, HTTP, and JSON. |
| [Reporting](README.Reporting.md) | Extent and fallback reports, assertions, and evidence. |
| [Console](README.Console.md) | Events and diagnostics in Maven and IntelliJ. |
| [Mock](README.Mock.md) | Deterministic HTTP contracts with a local catalog. |
| [Test Kit](README.TestKit.md) | JUnit lifecycle and auditing. |

## Maintenance and Feedback

Report Chess Lab integration issues in [J7 feedback](../docs/feedback/README.md).
For a library defect, isolate the reproduction with the J0 owner and repeat
consumer acceptance after replacing the pair.
