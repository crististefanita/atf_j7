# Feedback · Chess Lab

| Prioritate | Pas | Criteriu de închidere |
|---|---|---|
| P1 | Conversia avantajului în Alehină după ieșirea din ciclu | Rejucați Hard alb contra Advanced din liga 0.7.0. La semimutarea 72, căutarea Hard păstrează numai `f5c5`, către o poziție vizitată de două ori. Stockfish 19 cu istoric dă 0,00 pentru această mutare și +7,11 pentru `f5f6`. La plafonul 160 albul are −5,19. Introduceți un control care compară alternativele din această poziție cu istoricul complet și verificați conversia în liga cu aceleași praguri. [Retestare](../board/ai-quality.md#retestarea-pentru-070) |
| P2 | Semnul barei la Gambitul damei | După `1.d4 d5 2.c4`, J7 afișează `−0,19`, iar Stockfish 19 la adâncimea 13 indică `+0,22` pentru alb. Urmăriți ce schimbă evaluarea după acceptarea gambitului și în variantele cu `…e6` și `…c6`. Reduceți diferența și păstrați porțile actuale de calibrare. [Măsurători](../board/ai-quality.md#calibrarea-hard-și-a-barei) |
| P2 | Calibrare cu jucători | Amestecați poziții din cele trei zone ale capitolului Mat în 1 și comparați medianele timpului și ale încercărilor greșite, fără indicii. Analizați separat problemele pornite din șah și cele cu apărări unice. Ajustați ponderile YAML după inversiuni care se repetă între sesiuni. [Pozițiile de control](../board/quest-curation.md#calibrarea-dificultății) |
