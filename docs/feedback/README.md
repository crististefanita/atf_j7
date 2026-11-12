# Feedback · Chess Lab

Registrul păstrează ipotezele și criteriile încă deschise. [Ghidul tablei](../board/README.md) descrie comportamentul livrat.

| Prioritate | Zonă | Observație | Criteriu de închidere |
|---|---|---|---|
| P2 | Progres · Antrenament tactic | Componenta verifică cursorul, punctele și totalurile separate; lipsește încă dovada repornirii reale a procesului Android pe acest capitol. | Rulați sanity cu PID-uri distincte și confirmați revenirea la a doua problemă nerezolvată, cu poziția și punctele din Tactică păstrate. [Procesul](../testing/README.md#progresul-problemelor) |
| P1 | Calitatea AI · final de turnuri | După 96 de semimutări din rejucarea istorică, Hard dă 339 cp pentru `Kd1` și 338 cp pentru `Kf1`; Stockfish 19 dă −1,05 și +2,94. La 108 și 112, Hard revine cu turnul la a8 în loc să activeze al doilea turn. | Verificați siguranța regelui și activarea turnului la mutările 96, 108 și 112, apoi pe finaluri independente în același buget. Păstrați liga integrală și comparați continuarea istorică. [Rejucare](../board/ai-quality.md#rejucarea-finalului-de-turnuri) |
| P2 | Calitatea AI · evaluare material | După `2…dxc4`, J7 afișează `−0,60`, iar Stockfish 19 la adâncimea 13 indică `+0,21`; după `3.e3`, abaterea ajunge la 111 cp. | Urmăriți recuperarea pionului pe ambele culori și cu ramuri de control. Reduceți abaterea fără să depășiți bugetul barei sau să slăbiți porțile. [Măsurători](../board/ai-quality.md#calibrarea-hard-și-a-barei) |
| P2 | Calibrarea problemelor | În Mat în 1, medianele ratingului importat urcă pe cele cinci segmente, cu suprapuneri între segmente. | Măsurați timpul și încercările greșite înaintea indiciului pe poziții amestecate, separat pentru pozițiile pornite din șah. Validați ponderile YAML pe un lot separat și păstrați ID-urile. [Calibrare](../board/quest-curation.md#calibrarea-dificultății) |
