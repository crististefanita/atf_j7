# ATF J7 · Chess Lab

În versiunea din surse, meniul principal grupează **Partide**, **Probleme**, **Deschideri** și **Profil**. Sub **Probleme** găsești 102 poziții de mat într-o mutare, 100 de mat în două, 100 de mat în trei și 12 poziții de **Tactică**. **Următoarea** se activează după rezolvarea problemei curente.

Marcajul de lângă tablă arată cine mută. În probleme, becul de sub tablă arată întâi piesa, apoi mutarea și explicația. Flacăra apare de la a doua rezolvare consecutivă fără greșeli sau indicii. **Reia partida** cu calculatorul readuce tabla la ultima mutare salvată. Săgețile și lista de sub tablă redau istoricul mutărilor.

În **Profil** poți edita numele afișat. La **Partidă nouă**, alegi Alb, Negru sau Aleatoriu și nivelul Basic, Advanced sau Hard. Bifa **Analiză AI** adaugă bara de evaluare și becul. Becul pornește cu cea mai bine evaluată continuare și parcurge până la cinci variante apropiate ca scor. Sub nume apar capturile și avantajul material.

În **Deschideri** poți exersa zece linii cu ambele culori. Atinge progresul lecției pentru a revedea o poziție și a o analiza cu motorul local. [Lecțiile și sursele](docs/board/openings.md)

## Jocul

- **[Chess Lab 0.7.0 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.7.0)** — ediția Android curentă.
- **[Descarcă APK 0.7.0](https://github.com/crististefanita/atf_j7/releases/download/v0.7.0/ChessLab-0.7.0.apk)** — APK Android semnat.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Configurarea problemelor](docs/board/quest-curation.md#configurarea-capitolelor) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.7.0](release/0.7.0/README.md) · [Feedback](docs/feedback/README.md)
