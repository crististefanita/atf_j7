# Bibliotecile ATF

## Proveniență

J7 consumă perechile versionate JAR/POM Core, Console, Mock, Reporting și Test Kit. [Proveniența](../library-provenance.json) fixează coordonatele și hash-urile fiecărui fișier.
Maven verifică perechile și le instalează în repository-ul de build înaintea testelor. `PlatformLibraryProvenanceTest` validează proveniența; `ExtentGovernanceTest` verifică randarea raportului în proiectul consumator.
Android împachetează motorul și interfața; bibliotecile de testare rămân pe partea JVM.

## Ghiduri de utilizare

| Bibliotecă | Utilizare |
|---|---|
| [Core](README.Core.md) | Configurație, HTTP și JSON. |
| [Reporting](README.Reporting.md) | Rapoarte Extent/fallback, aserțiuni și dovezi. |
| [Console](README.Console.md) | Evenimente și diagnostic în Maven și IntelliJ. |
| [Mock](README.Mock.md) | Contracte HTTP deterministe, cu un catalog local. |
| [Test Kit](README.TestKit.md) | Lifecycle și audit JUnit. |

## Mentenanță și feedback

J7 păstrează acceptanța locală a bibliotecilor și a rapoartelor produse de Chess Lab. Contractul de consum și exemplele sunt în ghidurile locale de mai sus.
