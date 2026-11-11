# Feedback · Chess Lab

| Prioritate | Pas | Criteriu de închidere |
|---|---|---|
| P1 | Conversia avantajului în Alehină după ieșirea din ciclu | Rejucați Hard alb contra Advanced din liga 0.7.0. La semimutarea 72, căutarea Hard păstrează numai `f5c5`, către o poziție vizitată de două ori. Stockfish 19 cu istoric dă 0,00 pentru această mutare și +7,11 pentru `f5f6`. La plafonul 160 albul are −5,19. Introduceți un control care compară alternativele din această poziție cu istoricul complet și verificați conversia în liga cu aceleași praguri. [Retestare](../board/ai-quality.md#retestarea-pentru-070) |
| P2 | Semnul barei la Gambitul damei | După `1.d4 d5 2.c4`, J7 afișează `−0,19`, iar Stockfish 19 la adâncimea 13 indică `+0,22` pentru alb. Urmăriți ce schimbă evaluarea după acceptarea gambitului și în variantele cu `…e6` și `…c6`. Reduceți diferența și păstrați porțile actuale de calibrare. [Măsurători](../board/ai-quality.md#calibrarea-hard-și-a-barei) |
| P2 | Citări RAG pentru schimbările AI | Pe diff-ul 0.6.1 → 0.7.0, schimbările motorului și repertoriului citează capitolul generic al testării. Cereți ca un diff cu `ai-repertoire.yaml`, `match-ai.yaml` sau `TacticalAnalysis.java` să citeze direct protocolul AI, benchmarkul Stockfish și liga offline. Verificați că un diff limitat la README păstrează doar traseul de release. [Protocol AI](../board/ai-quality.md#control-la-fiecare-schimbare) |
| P2 | Calibrare cu jucători | Amestecați poziții din cele trei zone ale capitolului Mat în 1 și comparați medianele timpului și ale încercărilor greșite, fără indicii. Analizați separat problemele pornite din șah și cele cu apărări unice. Ajustați ponderile YAML după inversiuni care se repetă între sesiuni. [Pozițiile de control](../board/quest-curation.md#calibrarea-dificultății) |

## Verificat în surse

Meniul grupat, profilul, culorile și nivelurile de joc, desenul nebunului și bara de evaluare sunt implementate. Proba API 28 a confirmat numele salvat, prima mutare a motorului pentru o partidă cu negrul la Hard și geometria fixă a tablei în istoric. [Contractul tablei și al partidei](../board/README.md) · [Verificările de componentă și Android](../testing/README.md#partidă-și-istoric)
