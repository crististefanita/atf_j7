# ATF J7 · Chess Lab

În versiunea din surse, meniul principal oferă 102 probleme de mat într-o mutare, 100 de mat în două, 100 de mat în trei și 12 poziții de **Tactică** pentru câștig de material. **Următoarea** se activează după rezolvarea problemei curente. Meniul arată problemele rezolvate și punctele, pe capitol și în total.

Marcajul de lângă tablă arată cine mută. În probleme, becul de sub tablă arată întâi piesa, apoi mutarea și explicația. Flacăra apare de la a doua rezolvare consecutivă fără greșeli sau indicii. **Reia partida** cu calculatorul readuce tabla la ultima mutare salvată. Săgețile și lista de sub tablă redau istoricul mutărilor.

La pornirea unei partide noi, bifa **Analiză AI** afișează o recomandare calculată local pentru poziția curentă sau cea selectată din istoric.

## Jocul

- **[Chess Lab 0.5.1 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.5.1)** — ediția Android curentă.
- **[Descarcă APK 0.5.1](https://github.com/crististefanita/atf_j7/releases/download/v0.5.1/ChessLab-0.5.1.apk)** — APK Android semnat.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.5.1](release/0.5.1/README.md) · [Feedback](docs/feedback/README.md)
