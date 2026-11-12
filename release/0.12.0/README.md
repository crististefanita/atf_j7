# Chess Lab 0.12.0

- **Probleme și Antrenament tactic** păstrează separat progresul și reiau capitolul curent după repornire.
- Hard păstrează istoricul de repetiție în finalurile de turnuri și învață ordonarea mutărilor liniștite care produc tăieturi alfa-beta; șahurile și capturile rămân prioritizate separat.
- Benchmarkul Hard măsoară toate capitolele fără să relaxeze pragurile: Mat în 1 `500/500`, Mat în 2 `300/300`, Mat în 3 `190/200`, Tactici `87/100`, Antrenament `40/48`, Intermezzo `24/24`, în plafonul existent de noduri.
- Testele de componentă și guvernanță verifică limitele euristicii, legalitatea mutărilor, pragurile de transfer și reluarea după restart; sanity-ul Android publică acțiunile, aserțiunile și capturile în Extent.
- Rapoartele Extent leagă aserțiunile de fază de recepțiile și capturile Android.

**Instalare:** 0.12.0 folosește o identitate nouă de semnare; nu se poate instala peste 0.11.2. Dezinstalarea ediției vechi șterge datele ei locale. Noua cheie este protejată în afara repository-ului; release-urile GitHub vechi se elimină numai după verificarea descărcării 0.12.0.

[Ghidul tablei](../../docs/board/README.md) · [Dovezile ediției](reports/README.md) · [Procesul de release](../../docs/release/README.md)
