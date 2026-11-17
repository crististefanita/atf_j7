# Core — Common Contracts and Mechanisms

## Overview

`atf-core` provides the vocabulary and reusable mechanisms of ATF. Use it to compose adapters and executions while keeping application rules in the consumer.

| What you need | Starting API |
|---|---|
| HTTP requests, responses, and execution | `ApiRequest`, `ApiResponse`, `ApiTransport`, `ApiExecutor` |
| Configuration captured for a run | `AtfConfiguration`, `ApiSettingsSnapshot`, `AtfRuntimeSnapshot` |
| Strict JSON and sensitive data redaction | `JsonDocuments`, `SensitiveDataRedactor` |
| Resource release in reverse order | `CleanupRegistry` |
| Selective synchronization with baseline verification | `AtfPromotionManifest`, `AtfPromotionGuard` |

## Minimal Example

This method fragment can propagate `IOException` and builds the data without sending a request.

```java
import atf.core.http.request.ApiRequest;
import atf.core.http.request.ApiUrlBuilder;
import atf.core.json.JsonDocuments;
import java.net.URI;

var request = ApiRequest.of("GET", "/orders/42");
var url = new ApiUrlBuilder(URI.create("https://example.test/api"))
        .build(request);
var body = JsonDocuments.read("{\"total\":19.90}");
var total = body.path("total").decimalValue();
```

`url` is `https://example.test/api/orders/42`. `JsonDocuments` rejects duplicate keys and trailing content. Decimal numbers bypass `double` conversion.

## Using Core in ATF

- Inject an `ApiTransport` into `ApiExecutor` and choose the real or mock transport explicitly at composition time.
- `HttpClientTransport` and `UrlConnectionTransport` propagate cancellation to the owned operation. The fallback includes the body in the deadline. Cancellation alone provides no rollback or replay authorization.
- Reuse the run's configuration snapshot. The consumer owns the oracle, assertions, and business verdict.
- Keep the original model for comparison. Apply redaction and previews when publishing diagnostics.
- Core exposes neutral ports. [Console](README.Console.md), [Mock](README.Mock.md), and [Reporting](README.Reporting.md) have distinct roles.

---

Before adopting a new version, check local compatibility with `PlatformLibraryProvenanceTest` and verify the contracts used by Chess Lab. Keep coordinates, hashes, and integration notes aligned with `library-provenance.json`.
