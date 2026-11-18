# Core — contracte și mecanisme comune

## Pe scurt

`atf-core` oferă vocabularul și mecanismele reutilizabile ale ATF. Folosiți-l pentru a compune adaptoare și execuții, păstrând regulile aplicației în consumator.

| Aveți nevoie de | API de pornire |
|---|---|
| Cereri, răspunsuri și execuție HTTP | `ApiRequest`, `ApiResponse`, `ApiTransport`, `ApiExecutor` |
| Configurație capturată pentru o rulare | `AtfConfiguration`, `ApiSettingsSnapshot`, `AtfRuntimeSnapshot` |
| JSON strict și redactarea datelor sensibile | `JsonDocuments`, `SensitiveDataRedactor` |
| Eliberarea resurselor în ordine inversă | `CleanupRegistry` |
| Sincronizare selectivă cu verificarea bazei de referință | `AtfPromotionManifest`, `AtfPromotionGuard` |

## Exemplu minim

Fragment într-o metodă care poate propaga `IOException`; construiește datele fără a trimite o cerere:

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

`url` este `https://example.test/api/orders/42`. `JsonDocuments` respinge cheile duplicate și conținutul rămas după document; numerele zecimale nu trec prin `double`.

## Utilizare în ATF

- Injectați un `ApiTransport` în `ApiExecutor`; transportul real sau mock se alege explicit la compoziție.
- `HttpClientTransport` și `UrlConnectionTransport` propagă anularea către operația deținută; transportul alternativ include citirea corpului răspunsului în termenul-limită. Anularea nu dovedește revenirea operației și nu autorizează repetarea cererii.
- Reutilizați instantaneul configurației rulării; oracolul, aserțiunile și verdictul aplicației aparțin consumatorului.
- Păstrați modelul original pentru comparație; redactarea și previzualizarea se aplică la publicarea diagnosticului.
- Core expune porturi neutre; [Console](README.Console.md), [Mock](README.Mock.md) și [Reporting](README.Reporting.md) au roluri distincte.

---

Înainte de o versiune nouă, verificați compatibilitatea locală cu `PlatformLibraryProvenanceTest` și contractele folosite de Chess Lab. Păstrați coordonatele, hash-urile și notele de integrare sincronizate cu `library-provenance.json`.

[Verificați integrarea în Chess și exemplele în J0](README.md#verificare)
