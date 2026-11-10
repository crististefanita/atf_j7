# Feedback · Chess Lab

| Prioritate | Pas | Criteriu de închidere |
|---|---|---|
| P2 | Parcurgere liniară cu TalkBack pe emulator | Confirmați cu două glisări succesive trecerea de la culoarea activă la piesă și apoi la destinație, cu albul și cu negrul jos. Componentele verifică proiecția pătratelor, iar sanity-ul verifică legăturile de traversare și eliminarea legăturilor vechi după întoarcere. |
| P2 | Calibrare cu jucători | Măsurați fără indicii timpul până la mat și încercările greșite pentru cele două contra-maturi și pozițiile vecine. Comparația motoarelor verifică structura pozițiilor. Ponderea de 16 puncte se decide pe baza rezultatelor jucătorilor. [Pozițiile de control](../board/quest-curation.md#calibrarea-contra-maturilor) |

Pe Android API 33, cu TalkBack 14.2, ediția 0.3.0 anunța matul și omitea pragul. Build-ul local corectat spune culoarea, piesa și destinația când se cere mutarea. La a cincea rezolvare, proba a înregistrat un singur anunț al pragului. Întoarcerea tablei, revenirea din meniu și repornirea procesului au păstrat pragul vizibil fără un nou anunț. [Extent TalkBack](talkback-extent.html)
