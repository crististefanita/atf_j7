# Documentația Chess Lab

Porniți de la ghidul care răspunde întrebării. Verificați afirmațiile despre comportament în surse și în testele proiectului.

| Întrebare | Ghid |
|---|---|
| Cum se leagă motorul, aplicația și testele? | [Arhitectura J7](../README.md#arhitectura-j7) · [Bibliotecile ATF](../lib/README.md) |
| Cum se joacă și cum se configurează problemele? | [Tabla și jocul](board/README.md) · [Catalogul](board/quest-curation.md) |
| Cum se măsoară adversarii AI și cum se interpretează analiza unei partide? | [Protocolul AI](board/ai-quality.md#control-la-fiecare-schimbare) · [Analiza partidei](board/game-review.md) |
| Ce trebuie verificat înaintea unei schimbări? | [Alegerea nivelului de testare](testing/README.md#alegerea-nivelului) · [Raportare Extent](testing/README.md#raportare-extent) |
| Cum se califică un APK și unde sunt dovezile ediției? | [Procesul de release](release/README.md#calificare-și-publicare) · [Edițiile publicate](../release/README.md) |
| Ce ipoteze mai cer dovezi? | [Feedback](feedback/README.md) |

## Reguli de întreținere

- **README principal:** scop, cele două linkuri ale ediției curente, harta
  arhitecturii și intrările pentru testare, release și feedback.
- **Ghidul zonei:** responsabilitate, intrări, rezultat și limite. Puneți
  comanda lângă prerechizite și indicați dovada care confirmă rezultatul.
- **Format:** un titlu, Markdown semantic și blocuri cu limbaj declarat.
  Folosiți tabele pentru comparații și pași ordonați pentru proceduri.
  Diagrama răspunde unei singure întrebări și are legenda imediat sub ea.
- **Linkuri:** căi relative și ancore existente; verificați destinația după
  mutarea documentului. În exemple, folosiți parametri pentru căi locale.
- **Feedback:** observație, prioritate, responsabilitate, probă și criteriu de
  închidere. Promovați regula confirmată în ghidul ei și eliminați punctul închis.
- **Dovezi:** build-urile, capturile și rapoartele de lucru stau în TMP.
  Dovezile promovate aparțin dosarului ediției și rămân legate de binar și commit.

## Verificarea documentației

Rulați din copia TMP, cu JDK 21. Dacă folosiți `-o`, depozitul Maven ales
trebuie să conțină deja dependențele și pluginurile.

```powershell
mvn test '-Dtest=DocumentationGovernanceTest,ReleaseGovernanceTest' '-Dchess.extent=target/doc-governance.html'
```

`DocumentationGovernanceTest` verifică harta README-ului, navigarea locală,
destinațiile ediției curente, structura feedback-ului și regulile Markdown pentru ghiduri și diagrame.
`ReleaseGovernanceTest` verifică regulile de publicare și traseele RAG.

La revizie, comparați afirmațiile cu sursele și declarațiile de build.
Verificați proprietarul informației, fidelitatea exemplelor, randarea și
rezultatul comenzilor. [Calificare](release/README.md#calificare-și-publicare)
