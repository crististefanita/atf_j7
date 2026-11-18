# Test Kit — clasificare și audit JUnit

## Pe scurt

Kit oferă adnotări, comparații evaluate și un ciclu de execuție neutru. JAR-ul lasă consumatorilor pornirea browserului, brokerului și ADB, precum și publicarea Extent.

| Nevoie | API și limită |
|---|---|
| Nivelul testului | @AtfUnitTest, @AtfComponentTest, @AtfIntegrationTest, @AtfE2eTest; clasificare, fără lansare automată. |
| Comparații auditabile | `RecordedAssertions`; verdictul JUnit precede previzualizarea de 240 de caractere, care păstrează perechile Unicode complete. |
| Aserțiuni JUnit existente | `AuditedAssertions` în contextul adaptorului; diagnosticul este calculat la cerere, iar identitatea și cauza originale sunt păstrate. Apelurile JUnit directe necesită auditare explicită. |
| Valori integrale | `AssertionValueDisplay` poate trimite textul redactat și rolul așteptat sau observat unui observator `Projection`, înaintea previzualizării; consumatorul deține retenția. |
| Faze și eliberarea resurselor | `ComponentAuditExtension` / `ComponentAuditAdapter`, `TestRunInitialization`, `FailureAwareCleanup`. |
| Politici și resurse | Rezolvatoarele `Atf*Policy`; `readResourceUtf8` pentru resurse din classpath/JAR, `requiredPath` pentru fișier real. |

## Exemplu minim

Adaptorul deține înregistratorul și publică rezultatul; o nepotrivire păstrează excepția JUnit originală:

```java
import atf.testkit.assertions.RecordedAssertions;

public static void verifyState(RecordedAssertions audit, String actual) {
    audit.equals("State", "ready", actual);
}
```

## Responsabilități și integrare

- Dependența `atf.api:atf-test-kit` folosește versiunea `atf.testkit.version` din POM-ul proiectului. Folosiți domeniul Maven `test` în produs; `compile` este justificat numai pentru suportul din `main` al unui modul de test. Păstrați Kit în afara APK-ului sau aplicației.
- JUnit Jupiter API și Jackson sunt tranzitive. Executorul declară motorul `junit-jupiter-engine` și regulile sale de selecție; adnotarea de nivel lucrează alături de `@Test` și extensia consumatorului.
- Pentru Extent, adaptorul trimite comparațiile către `AssertionAuditReport`, iar `AssertionAuditRun` deține sesiunea comună. Randarea aparține Reporting. [Integrare Reporting](README.Reporting.md#responsabilități-și-integrare).
- Injectați filtrul de redactare prin `AssertionValueDisplay` înaintea previzualizării. Comparația rulează o dată; valorile stocate sunt limitate și nu constituie arhivă integrală. Testele unitare pure își păstrează politica proprie.
- Contextele și resursele sunt per invocare; închideți-le și păstrați cauza inițială. O blocare locală nu poate izola singură politicile globale de cititorii externi.

---

Verificați contractele locale cu `PlatformLibraryProvenanceTest` și `SuiteGovernanceTest`. Păstrați coordonatele, hash-urile și notele de integrare sincronizate cu `library-provenance.json`.

[Verificați integrarea în Chess și exemplele în J0](README.md#verificare)
