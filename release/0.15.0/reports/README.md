# Dovezile ediției 0.15.0

- [Componente · Extent](components.html), [calificare](qualification.json) și [amprentele recepțiilor brute](raw-receipts.json).
- [Game Review · Extent](android-game-review.html) și [recepția Android](game-review-execution.json).
- [Indiciul din istoric · Extent](android-match-analysis.html), [tabla de analiză · Extent](android-manual-analysis.html) și [recepția celor trei faze](hint-analysis-execution.json).
- [Guvernanță JVM · Extent](governance.html), [revizuire Git](test-change-review.html), [testele înlocuitoare](test-change-review.html.proof.html) și [recepția revizuirii](test-change-review-execution.json).
- [Deschideri pe Android · Extent](android-openings.html), [recepția catalogului](openings-execution.json) și [verificarea independentă Stockfish · Extent](stockfish-openings.html).
- [Continuitatea semnăturii](signing-transition.json) și [contextul RAG](rag.txt).
- [Asset GitHub](github-release.json), [descărcare directă](public-download.json) și [descărcare autentificată](distribution-verification.json).

Analiza partidei, proba din istoric și tabla manuală folosesc exact APK-ul semnat.
Recepția indiciilor leagă cele două capturi încorporate și restartul de același ID de rulare.
Instrumentarea generală a trecut toate cele 106 verificări. Extent-ul general are o diferență de inventar la `prepare`, urmărită în [feedback](../../../docs/feedback/README.md). Probele calificate au rapoarte dedicate mai sus.
[Procesul de calificare și publicare](../../../docs/release/README.md#calificare-și-publicare)
