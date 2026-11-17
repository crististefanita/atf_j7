# Mock — Deterministic HTTP Transport

## Overview

`atf-mock` replaces the Core port `ApiTransport` with a local response catalog. Use it for offline components and contracts with controlled input and an explicit fallback.

## Minimal Example

Place `mock.yaml` in the working directory.

```yaml
mock:
  routes:
    - {method: GET, path: /health, status: 200, body: '{"status":"ready"}'}
  fallback: {status: 500, body: '{}'}
```

In this method fragment, the transport stays offline.

```java
import atf.core.http.request.ApiRequest;
import atf.mock.AtfMockTransport;
import java.nio.file.Path;

var mock = AtfMockTransport.fromConfiguration(Path.of("mock.yaml"));
var response = mock.send(ApiRequest.of("GET", "/health"),
        "https://offline.test/health", "none").join();
```

`response.status()` is `200`. A request without a matching route receives the `500` fallback. The test independently compares the response with the expected result.

## Catalog and Matching

| Need | API or Contract |
|---|---|
| Explicit YAML catalog | `AtfMockTransport.fromConfiguration(Path)` |
| Project configuration | `fromProjectConfiguration()`: `atf.mock.config` > `ATF_MOCK_CONFIG` > `atf-mock.yaml/yml` |
| Request conditions | Method, path, auth profile, query, required headers, and expected body in `MockRoute` |
| Request body | Default `json` mode, negative MIME check `wrong-content-type`, and numeric matching without converting through `double` |
| Repeated query parameters | `ApiQueryParameters` includes the required values with their multiplicity |

- Without configuration, `fromProjectConfiguration()` uses the `defaults()` fixture. The application catalog stays local.
- The mock uses route duration as response metadata and returns without a simulated delay.
- A mock check verifies integration. Acceptance of the real service, broker, or device is a separate check.

---

Before adopting a new version, check local compatibility with `PlatformLibraryProvenanceTest` and verify the simulated contracts used by Chess Lab. Keep coordinates, hashes, and integration notes aligned with `library-provenance.json`.
