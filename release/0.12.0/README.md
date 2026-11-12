# Chess Lab 0.12.0

- **Probleme și Antrenament tactic** păstrează separat progresul și reiau capitolul curent după repornire.
- Hard păstrează istoricul de repetiție în finalurile de turnuri și evită linia de remiză când partida curentă este câștigătoare.
- Ordonarea mutărilor care dau șah a ridicat transferul Hard la Mat în 3 de la 170/200 la 172/200, fără să mărească plafonul de 44.000 de noduri.
- Benchmarkul de transfer verifică toate cele 1.172 de quest-uri; componentele și sanity-ul Android verifică progresul și reluarea după restart.
- Rapoartele Extent leagă aserțiunile de fază de recepțiile și capturile Android.

**Instalare:** 0.12.0 folosește o identitate nouă de semnare; nu se poate instala peste 0.11.2. Dezinstalarea ediției vechi șterge datele ei locale. Noua cheie este protejată în afara repository-ului; release-urile GitHub vechi se elimină numai după verificarea descărcării 0.12.0.

[Ghidul tablei](../../docs/board/README.md) · [Dovezile ediției](reports/README.md) · [Procesul de release](../../docs/release/README.md)
