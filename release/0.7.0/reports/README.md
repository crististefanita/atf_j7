# Dovezile ediției 0.7.0

| Dovadă | Ce verifică |
|---|---|
| [Extent JVM](components.html) | 106 teste trecute, inclusiv componente, guvernanță, Stockfish 19 și liga offline. Ancora Elo externă rămâne o probă opțională separată. |
| [Rejucarea celor 72 de partide](league.tsv) · [Rezultatele AI](../../../docs/board/ai-quality.md#retestarea-pentru-070) | Secvențele UCI complete, culorile, rezultatele și semimutările. Extent verifică rejucarea fiecărei partide și agregarea perechilor. |
| [Extent Android](android.html) | Sanity API 28 pe APK-ul semnat, cu acțiuni, capturi, aserțiuni native și reluare după restart. |
| [RAG](rag.txt) · [Calificarea APK-ului](qualification.json) | Citările din diff-ul față de 0.6.1 și identitatea sursei, a APK-ului instalat și a rapoartelor. |
| [Asset GitHub](github-release.json) · [Descărcare directă](public-download.json) · [Descărcare autentificată](distribution-verification.json) | Verificări pornite din README-ul publicat. Octeții, SHA-256 și CRC-ul coincid cu APK-ul calificat. |

Pe lângă citările generate de RAG, analiza ediției a urmărit explicit [protocolul Stockfish și liga](../../../docs/board/ai-quality.md#control-la-fiecare-schimbare) și [matricea de testare](../../../docs/testing/README.md). Minorul 0.7.0 aduce meniul Deschideri față de 0.6.1.
