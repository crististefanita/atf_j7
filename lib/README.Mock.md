# Mock — transport HTTP determinist

## Pe scurt

`atf-mock` înlocuiește portul Core `ApiTransport` cu un catalog local de răspunsuri. Folosiți-l pentru componente și contracte fără rețea, cu intrări controlate și un răspuns implicit declarat.

## Exemplu minim

`mock.yaml` în directorul de lucru:

```yaml
mock:
  routes:
    - {method: GET, path: /health, status: 200, body: '{"status":"ready"}'}
  fallback: {status: 500, body: '{}'}
```

Fragment într-o metodă; transportul nu accesează rețeaua:

```java
import atf.core.http.request.ApiRequest;
import atf.mock.AtfMockTransport;
import java.nio.file.Path;

var mock = AtfMockTransport.fromConfiguration(Path.of("mock.yaml"));
var response = mock.send(ApiRequest.of("GET", "/health"),
        "https://offline.test/health", "none").join();
```

`response.status()` este `200`; o cerere fără rută potrivită primește răspunsul implicit `500`. Testul compară independent răspunsul cu rezultatul așteptat.

## Catalog și potrivire

| Necesitate | API sau contract |
|---|---|
| Catalog YAML explicit | `AtfMockTransport.fromConfiguration(Path)` |
| Configurația proiectului | `fromProjectConfiguration()`: `atf.mock.config` > `ATF_MOCK_CONFIG` > `atf-mock.yaml/yml` |
| Condițiile cererii | Metodă, cale, profil de autentificare, parametrii adresei, antetele cerute și corpul așteptat în `MockRoute` |
| Corpul cererii | Mod implicit `json`, probă MIME negativă `wrong-content-type`; potrivire numerică fără conversie prin `double` |
| Parametri repetați | `ApiQueryParameters`; include valorile cerute, cu multiplicitate |

- Fără configurație, `fromProjectConfiguration()` folosește datele de probă `defaults()`; catalogul aplicației rămâne local.
- Durata din rută este metadată a răspunsului, nu o întârziere reală simulată.
- O probă cu răspunsuri simulate verifică integrarea; acceptanța serviciului, brokerului sau dispozitivului real rămâne separată.

---

Înainte de o versiune nouă, verificați compatibilitatea locală cu `PlatformLibraryProvenanceTest` și contractele simulate folosite de Chess Lab. Păstrați coordonatele, hash-urile și notele de integrare sincronizate cu `library-provenance.json`.

[Verificați integrarea în Chess și exemplele în J0](README.md#verificare)
