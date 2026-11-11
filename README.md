# ATF J7 · Chess Lab

În versiunea din surse, meniul principal grupează **Partide**, **Probleme**, **Deschideri**, **Tablă de analiză** și **Profil**. Sub **Probleme** găsești 500 de poziții de mat într-o mutare, 300 de mat în două, 200 de mat în trei și 100 de poziții de **Tactică**, cu dificultate progresivă în fiecare capitol. **Următoarea** se activează după rezolvarea problemei curente.

Marcajul de lângă tablă arată cine mută. În probleme, becul de sub tablă arată întâi piesa, apoi mutarea și explicația. Flacăra apare de la a doua rezolvare consecutivă fără greșeli sau indicii. **Reia partida** cu calculatorul readuce tabla la ultima mutare salvată. Săgețile și lista de sub tablă redau istoricul mutărilor.

În **Profil** poți edita numele afișat. La **Partidă nouă**, alegi Alb, Negru sau Aleatoriu și nivelul Basic, Advanced sau Hard. Bifa **Analiză AI** adaugă bara de evaluare și becul. Becul parcurge până la cinci variante apropiate ca scor. Pe tabla de analiză și când e rândul tău în partida cu AI, atingerea destinației indicate joacă mutarea sugerată. Sub nume apar capturile și avantajul material.

**Continuă de aici** reia partida din poziția aleasă în istoric. În timpul jocului, **Opțiunile partidei** permite o ofertă de remiză sau renunțarea. După rezultat, **📊 Analiza partidei** deschide pe tot ecranul graficul, estimările Elo și tabelul mutărilor pe categorii pentru jucător și AI. [Partida cu calculatorul](docs/board/README.md#partidă-cu-calculatorul)

**Tabla de analiză** pornește din meniu sau de la poziția afișată în partidă, prin lupa de lângă istoric. Poți juca ambele culori și încerca alte continuări, păstrând partida salvată. [Tablă de analiză](docs/board/README.md#tablă-de-analiză)

În **Deschideri** poți exersa zece linii cu ambele culori. Folosește săgețile pentru a parcurge mutările și **Rejoacă de aici** pentru a exersa din poziția afișată. Indiciul complet îți permite să joci atingând destinația marcată. [Lecțiile și sursele](docs/board/openings.md)

## Jocul

- **[Chess Lab 0.10.1 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.10.1)** — ediția Android curentă.
- **[Descarcă APK 0.10.1](https://github.com/crististefanita/atf_j7/releases/download/v0.10.1/ChessLab-0.10.1.apk)** — APK Android semnat.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Configurarea problemelor](docs/board/quest-curation.md#configurarea-capitolelor) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.10.1](release/0.10.1/README.md) · [Feedback](docs/feedback/README.md)
