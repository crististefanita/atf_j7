# Console — diagnostic pentru execuție

## Pe scurt

`atf-console` proiectează evenimentele ATF în stdout/stderr: ciclul de execuție, durate și detalii configurabile. Este util pentru urmărirea unei rulări Maven sau IntelliJ; artefactul HTML este produs de [Reporting](README.Reporting.md).

## Exemplu minim

Fragment într-o metodă; politica este explicită, iar fluxul de ieșire rămâne în adaptor:

```java
import atf.console.AtfConsoleReporter;
import atf.console.ConsoleSettings;

var console = AtfConsoleReporter.configuredStdout(
        new ConsoleSettings(true, false));
console.info("Catalogul de scenarii a fost încărcat");
```

`configuredStdout()` citește `atf.report.config` → `ATF_REPORT_CONFIG` → `atf.yaml/yml`; opțiunile sunt:

```yaml
reporting:
  console:
    enabled: true
    detailsEnabled: false
```

## Integrare și opțiuni

| Intenție | Alegere |
|---|---|
| Mesaj independent de detalii | `info(...)` |
| Evenimente cu tip explicit, ciclu de execuție și durată | `publish(AtfConsoleEvent)` prin portul Core `AtfConsoleSink` |
| Consolă complet oprită | `enabled: false` sau `ConsoleSettings.disabled()` |
| Calea raportului final în consolă | Constructorul cu `reportPathEnabled: true` și evenimentul `REPORT_PUBLISHED` |
| Politică JUnit per clasă | Adaptorul JUnit Console și adnotările Test Kit; declarați Kit explicit |

- Afișarea căii cere activare explicită, consolă activă și un eveniment cu artefact confirmat. `detailsEnabled` controlează doar detaliile.
- Console depinde de Core. Adaptorul JUnit folosește Kit când acesta este declarat.
- Console afișează diagnosticul. Testul care îl invocă decide comparația și verdictul.

---

Înainte de o versiune nouă, verificați compatibilitatea locală cu `PlatformLibraryProvenanceTest` și randarea diagnosticelor cu `ExtentGovernanceTest`. Păstrați coordonatele, hash-urile și notele de integrare sincronizate cu `library-provenance.json`.

[Verificați integrarea în Chess și exemplele în J0](README.md#verificare)
