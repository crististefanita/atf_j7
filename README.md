# ATF J7 · Chess Lab

În versiunea din surse, meniul principal oferă 102 probleme de mat într-o mutare, 100 de mat în două și 100 de mat în trei. **Următoarea** se activează după ce dai mat. Poți începe și o partidă cu calculatorul. **Reia partida** readuce tabla la ultima mutare salvată. Meniul include și intrarea „Analiză — ToDo”.

Marcajul de lângă tablă arată cine mută. În partidă, săgețile și lista derulabilă de sub tablă redau toate mutările. În probleme, **Indiciu** marchează piesa, **Arată mutarea** adaugă săgeata, iar **De ce e mat?** explică poziția finală.

## Jocul

- **[Chess Lab 0.4.0 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.4.0)** — ediția Android curentă.
- **[Descarcă APK 0.4.0](https://github.com/crististefanita/atf_j7/releases/download/v0.4.0/ChessLab-0.4.0.apk)** — APK Android semnat.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.4.0](release/0.4.0/README.md) · [Feedback](docs/feedback/README.md)
