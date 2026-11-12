# Documentația Chess Lab

Porniți de la ghidul care răspunde întrebării. Verificați afirmațiile despre comportament în surse și în testele proiectului.

| Întrebare | Ghid |
|---|---|
| Cum se joacă și cum se configurează problemele? | [Tabla și jocul](board/README.md) · [Catalogul](board/quest-curation.md) |
| Ce trebuie verificat înaintea unei schimbări? | [Alegerea nivelului de testare](testing/README.md#alegerea-nivelului) · [Raportare Extent](testing/README.md#raportare-extent) |
| Cum se califică și se publică un APK? | [Procesul de release](release/README.md#calificare-și-publicare) |
| Ce ipoteze mai cer dovezi? | [Feedback](feedback/README.md) |

## Reguli de întreținere

- Începeți cu scopul, publicul și următoarea acțiune. Păstrați la începutul README-ului principal cele două linkuri ale ediției curente.
- Scrieți contractul stabil în ghidul zonei care îl implementează. Trimiteți cititorul direct la secțiunea care deține explicația.
- Folosiți un tabel pentru o matrice de decizie și pași ordonați pentru o procedură. Păstrați comanda lângă prerechizite și explicați cum se verifică rezultatul.
- Notați în feedback observația, prioritatea, zona și criteriul de închidere. Când dovada închide punctul, păstrați regula confirmată în ghidul ei și ștergeți punctul din registru.
- Păstrați dovezile unei ediții lângă ediție. Folosiți TMP pentru build-uri, capturi și rapoarte de lucru. Verificați țintele Markdown înainte de mutarea unui document sau artifact.

## Verificarea documentației

Rulați din copia TMP cu JDK 21 și dependențele Maven în cache.

```powershell
mvn -o test '-Dtest=DocumentationGovernanceTest,ReleaseGovernanceTest'
```

`DocumentationGovernanceTest` verifică navigarea locală, destinațiile ediției curente și registrul de feedback. `ReleaseGovernanceTest` leagă versiunile și linkurile din README de contractul de publicare. La revizie, verificați și proprietarul informației, fidelitatea exemplelor și rezultatul comenzilor din ghidul schimbat. [Calificare](release/README.md#calificare-și-publicare)
