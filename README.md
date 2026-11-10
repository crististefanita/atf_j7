# ATF J7 · Chess Lab

Codul curent include 102 probleme de mat într-o mutare, 100 de mat în două și 100 de mat în trei. Alegi capitolul din meniul principal și revii la el când redeschizi jocul. **Următoarea** se activează după ce dai mat. Meniul include și intrările „Șah — ToDo” și „Analiză — ToDo”.

Marcajul de lângă tablă arată cine mută, inclusiv în pauza dinaintea răspunsului adversarului. **Indiciu** marchează piesa, **Arată mutarea** adaugă săgeata, iar **De ce e mat?** explică poziția finală. Bara de progres arată câte probleme ai rezolvat și următorul prag.

## Jocul

- **[Chess Lab 0.3.0 pe GitHub](https://github.com/crististefanita/atf_j7/releases/tag/v0.3.0)** — ediția Android curentă.
- **[Descarcă APK 0.3.0](https://github.com/crististefanita/atf_j7/releases/download/v0.3.0/ChessLab-0.3.0.apk)** — APK Android semnat.

## Proiectul

| Zonă | Responsabilitate |
|---|---|
| `chess-core` | Reguli și probleme YAML, poziții FEN, mutări legale și verificarea matului |
| `android` | Tablă, piese, selecție, promovare, indicii vizuale și accesibilitate |
| `atf-tests` | Componente, guvernanță și publicarea dovezii Android în Extent |
| `release-tools` | Context RAG consultativ și verificarea identității dovezilor |

[Tabla de joc](docs/board/README.md) · [Strategia de testare](docs/testing/README.md) · [Procesul de release](docs/release/README.md) · [Dosarul ediției 0.3.0](release/0.3.0/README.md) · [Feedback](docs/feedback/README.md)
