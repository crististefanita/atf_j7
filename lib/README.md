# Bibliotecile ATF

## Pe scurt

Acest director documentează adoptarea bibliotecilor ATF în testele Chess Lab.
Implementarea și regresiile generice aparțin J0; J7 verifică integrarea proprie.

## Proveniență

```mermaid
flowchart LR
    J0["ATF J0"]
    Donor["J4 · pachet fixat"]
    Lib["Chess · lib/"]
    Provenance["library-provenance.json"]
    Maven["Maven"]
    Suite["atf-tests"]
    Extent["Extent"]
    J0 -->|produce| Donor
    Donor -->|cinci perechi| Lib
    Lib -->|fixează| Provenance
    Lib -->|instalează| Maven
    Maven -->|scope test| Suite
    Suite -->|dovezi| Extent
```

<details>
<summary>Legendă · Cum ajung bibliotecile ATF în rapoartele Chess</summary>

- J0 produce bibliotecile. Manifestul de proveniență fixează și commitul J4
  de la care provin cele cinci perechi JAR/POM incluse.
- [POM-ul rădăcină](../pom.xml) instalează perechile. [Manifestul de proveniență](../library-provenance.json)
  fixează identitatea fișierelor, verificată de [testul de proveniență](../atf-tests/src/test/java/ro/atf/chess/PlatformLibraryProvenanceTest.java).
- [POM-ul testelor](../atf-tests/pom.xml) declară Reporting și Test Kit în
  scope `test`. [Guvernanța Extent](../atf-tests/src/test/java/ro/atf/chess/ExtentGovernanceTest.java)
  verifică diagnosticul și probele negative din raportul produs de Chess.
- [Buildurile de produs](../web/README.md#motor-comun-și-adaptoare-de-platformă)
  consumă sursele `chess-core`, cu adaptoare native și Web proprii. JAR-urile
  ATF aparțin suitei JVM care raportează probele de pe aceste platforme.

</details>

## Ghiduri de utilizare

| Bibliotecă | Utilizare |
|---|---|
| [Core](README.Core.md) | Configurație, HTTP și JSON. |
| [Reporting](README.Reporting.md) | Rapoarte Extent/fallback, aserțiuni și dovezi. |
| [Console](README.Console.md) | Evenimente și diagnostic în Maven și IntelliJ. |
| [Mock](README.Mock.md) | Contracte HTTP deterministe, cu un catalog local. |
| [Test Kit](README.TestKit.md) | Lifecycle și audit JUnit. |

## Verificare

Rulați din checkoutul TMP. Testul de proveniență compară perechile instalate cu
manifestul. Guvernanța Extent verifică raportarea folosită de Chess.

```powershell
mvn -pl atf-tests -am test '-Dtest=PlatformLibraryProvenanceTest,ExtentGovernanceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

J0 deține contractele exemplelor de bibliotecă. [Ghidurile executabile J0](https://github.com/crististefanita/atf_j0/blob/master/docs/ATF_Platform_Libraries.md)

## Mentenanță și feedback

Raportați integrarea Chess Lab în [feedback-ul J7](../docs/feedback/README.md).
Pentru un defect al bibliotecii, izolați proba la ownerul J0 și repetați
acceptanța consumatorului după înlocuirea perechii.
