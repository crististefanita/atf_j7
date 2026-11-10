# ATF J7 · Chess Lab

Colecție offline de 102 probleme de mat într-o mutare, ordonate după dificultate. Două poziții încep în șah și cer o apărare care dă mat. După redeschidere revii la problema curentă și vezi toate rezolvările salvate. Tabla se poate întoarce oricând.

**Indiciu** marchează piesa pe tablă. **Arată soluția** trasează mutarea, iar **De ce e mat?** explică poziția finală. Bara de progres arată câte probleme ai rezolvat și următorul prag.

## Jocul

- **[Chess Lab 0.1.0 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.1.0)** — ediția Android curentă.
- **[Descarcă APK 0.1.0](https://github.com/crististefanita/atf_j7/releases/download/v0.1.0/ChessLab-0.1.0.apk)** — pentru utilizatorii autentificați cu acces la repository.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.1.0](release/0.1.0/README.md) · [Feedback](docs/feedback/README.md)
