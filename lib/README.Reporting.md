# Reporting — dovezi și rapoarte auditabile

## Pe scurt

Reporting publică datele, comparațiile și verdictul apelantului în Extent sau în raportul HTML alternativ, independent de Core și Kit.

| Nevoie | API |
|---|---|
| Rulare individuală/clasă/suită | `AssertionAuditRun` |
| Invocare | `run.open(Identity)` → `AssertionAuditReport` |
| Porturi / raport HTML alternativ | `AtfReportPorts` / `HtmlReportSession` |
| HTTP observat | `request.execution(ReportExecutionEvidence)`, cu `ReportResponse` |
| Capturi | `UiExtentReportSession`; PNG integral |

## Exemplu minim

Pentru o rulare individuală, folosiți `try (var run = open(output)) { publish(run, actual); }`. Suita păstrează rularea și deschide un scenariu pentru fiecare invocare:

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

## Responsabilități și integrare

| Responsabil | Închidere și limită |
|---|---|
| AssertionAuditRun | Deține sesiunea; close publică HTML-ul final. |
| run.open(identity) | Împrumută sesiunea; close închide scenariul invocării. |
| Constructorul AssertionAuditReport(session, ...) | Deține și închide sesiunea proprie. |

- Folosiți aceeași configurație și același redactor în rulare și în componenta de randare; lungimea implicită de previzualizare este **240**, citită prin `assertionMaxDisplayCharacters()`. Valorile complete sunt disponibile înaintea previzualizării.
- Exemplul urmează firul de execuție al sesiunii; `AssertionAuditRun.extent(...)` serializează publicarea pentru apelanți concurenți.
- Kit colectează comparația JUnit evaluată o dată; Reporting o publică. Apelantul păstrează verdictul, cauza și timpii de execuție prin `phase(..., timing)`.
- Fila **Fails** indexează cauza terminală la rândul **ACTION** sau **OBSERVE** care a eșuat. Fila **Tests** păstrează și diagnosticul colapsabil, iar comparațiile independente rămân distincte.
- PNG-ul păstrează întreaga zonă vizibilă a paginii sau întregul ecran; captura și mărirea aparțin adaptorului. Capturile identice din același pas păstrează o singură dovadă și prima legendă.
- După închidere, `ReportArtifactInspector.inspect(path, profile)` califică inventarul și resursele încorporate; diagnosticul localizează dependențele externe. Căile text rămân valide. Inspectorul este static; randarea și interacțiunile se verifică separat în browser.
- Titlurile se publică drept text literal în Extent și în raportul alternativ. Profilul acceptă etichetele originale, inclusiv pe mai multe rânduri. `ReportArtifactProfile.visibleLabel(label)` oferă aceeași proiecție a spațiilor ca inspectorul.
- Browserul aparține apelantului: separați diagnosticul descoperirii testelor JUnit de execuție, închideți contextul înaintea browserului și păstrați cauza inițială la eliberarea resurselor.

Durata HTTP provine din `ReportResponse.duration()`; `detail(...)` păstrează textul apelantului, separat de durata măsurată a transportului.

---

J0: [cod, teste și feedback](README.md#mentenanță-și-feedback).

[Verificați integrarea în Chess și exemplele în J0](README.md#verificare)
