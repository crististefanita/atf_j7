# Feedback · Chess Lab

| Prioritate | Pas | Criteriu de închidere |
|---|---|---|
| P2 | Parcurgere liniară cu TalkBack pe emulator | Trimiteți două glisări spre dreapta din interfața emulatorului, cu albul și cu negrul jos. Capturați evenimentele de focus în ordinea culoare, piesă, destinație. Pe API 33, glisările injectate prin ADB au lăsat focusul pe culoare în ambele orientări. [Sanity Android 0.3.1](../../release/0.3.1/reports/README.md) |
| P2 | Calibrare cu jucători | Măsurați fără indicii timpul până la mat și încercările greșite pentru cele două contra-maturi și pozițiile vecine. Comparația motoarelor verifică structura pozițiilor. Ponderea de 16 puncte se decide pe baza rezultatelor jucătorilor. [Pozițiile de control](../board/quest-curation.md#calibrarea-contra-maturilor) |

Pe Android API 33, cu TalkBack 14.2, ediția 0.3.0 anunța matul și omitea pragul. Build-ul local corectat spune culoarea, piesa și destinația când se cere mutarea. La a cincea rezolvare, proba a înregistrat un singur anunț al pragului. Întoarcerea tablei, revenirea din meniu și repornirea procesului au păstrat pragul vizibil fără un nou anunț. [Extent TalkBack](talkback-extent.html)
