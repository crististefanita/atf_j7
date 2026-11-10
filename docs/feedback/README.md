# Feedback · Chess Lab

| Prioritate | Pas | Criteriu de închidere |
|---|---|---|
| P2 | Calibrare cu jucători | Măsurați fără indicii timpul până la mat și încercările greșite pentru cele două contra-maturi și pozițiile vecine. Folosiți rezultatele pentru a calibra ponderea de 16 din scorul intern de dificultate. Punctele afișate jucătorului urmează [regula de recompensare](../board/README.md#tactică-și-puncte). [Pozițiile de control](../board/quest-curation.md#calibrarea-contra-maturilor) |
| P2 | Analiză AI pentru partide | La lansarea partidei, oferă o bifă implicit debifată și salvează alegerea cu partida. Când e activă, calculează local și în fundal o continuare legală pentru poziția afișată, inclusiv în istoric. Afișează mutarea recomandată și evaluarea la orizontul motorului. Acoperă activarea, reluarea și actualizarea în istoric prin teste de componentă și un E2E minim pe API 28. |
