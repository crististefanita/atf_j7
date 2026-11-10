# ATF J7 · Chess Lab

În versiunea din surse, meniul principal grupează **Partide**, **Probleme** și **Profil**. Sub **Probleme** găsești 102 poziții de mat într-o mutare, 100 de mat în două, 100 de mat în trei și 12 poziții de **Tactică**. **Următoarea** se activează după rezolvarea problemei curente.

Marcajul de lângă tablă arată cine mută. În probleme, becul de sub tablă arată întâi piesa, apoi mutarea și explicația. Flacăra apare de la a doua rezolvare consecutivă fără greșeli sau indicii. **Reia partida** cu calculatorul readuce tabla la ultima mutare salvată. Săgețile și lista de sub tablă redau istoricul mutărilor.

În **Profil** poți edita numele afișat. La **Partidă nouă**, alegi Alb, Negru sau Aleatoriu și nivelul Basic, Advanced sau Hard. Bifa **Analiză AI** adaugă bara de evaluare și becul. Apasă becul pentru cea mai bună continuare. Dacă mai multe linii au același scor maxim, le poți parcurge pe rând.

## Jocul

- **[Chess Lab 0.6.1 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.6.1)** — ediția Android curentă.
- **[Descarcă APK 0.6.1](https://github.com/crististefanita/atf_j7/releases/download/v0.6.1/ChessLab-0.6.1.apk)** — APK Android semnat.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.6.1](release/0.6.1/README.md) · [Feedback](docs/feedback/README.md)
