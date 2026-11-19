# Documentația Chess Lab

## Pe scurt

Porniți de la ghidul care răspunde întrebării. Verificați afirmațiile despre comportament în surse și în testele proiectului.

| Întrebare | Ghid |
|---|---|
| Cum se leagă motorul, aplicația și testele? | [Arhitectura Chess](../README.md#arhitectura-chess) · [Motor și adaptoare](../web/README.md#motor-comun-și-adaptoare-de-platformă) · [Bibliotecile ATF](../lib/README.md) |
| Cum se joacă și cum se configurează problemele? | [Tabla și jocul](board/README.md) · [Catalogul](board/quest-curation.md) |
| Cum se măsoară adversarii AI și cum se interpretează analiza unei partide? | [Protocolul AI](board/ai-quality.md#control-la-fiecare-schimbare) · [Analiza partidei](board/game-review.md) |
| Ce trebuie verificat înaintea unei schimbări? | [Alegerea nivelului de testare](testing/README.md#alegerea-nivelului) · [Raportare Extent](testing/README.md#raportare-extent) |
| Cum se verifică o funcție pe Web și APK? | [Ghidul Web](../web/README.md) · [Matricea de paritate](../web/parity.md) |
| Cum se califică un APK și unde sunt dovezile ediției? | [Procesul de release](release/README.md#calificare-și-publicare) · [Edițiile publicate](../release/README.md) |
| Ce ipoteze mai cer dovezi? | [Feedback](feedback/README.md) |

Adversarul Hard și solverele cu obiectiv de puzzle au dovezi distincte: [studiu puzzle nativ](testing/hard-puzzle-study.md).

## Reguli de întreținere

- **README principal:** scop, linkurile APK și browser în Jocul, harta arhitecturii,
  notele ediției separat și rutele către testare, release și feedback.
- **Ghidul zonei:** responsabilitate, intrări, rezultat și limite. Puneți
  comanda lângă prerechizite și indicați dovada care confirmă rezultatul.
- **Format:** un titlu, Markdown semantic și blocuri cu limbaj declarat.
  Folosiți tabele pentru comparații și pași ordonați pentru proceduri.
  Diagrama răspunde unei singure întrebări și are legenda imediat sub ea.
- **Identitate.** Folosiți `chess` pentru rutele actuale ale depozitului și checkout-ului, iar Chess Lab pentru aplicație. Păstrați identificatorii opaci de semnare/protocol și recepțiile istorice imuabile. Fixați dovada istorică la commitul care o conține; tagul buildului poate preceda raportul ulterior.
- **Limbă:** revizuiți proza și etichetele vizibile din ambele ramuri conform
  [contractului documentației bilingve](release/README.md#documentație-bilingvă).
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
Aceleași verificări se aplică ghidului Web și matricei de paritate.
`ReleaseGovernanceTest` verifică regulile de publicare și traseele RAG.

La revizie, comparați afirmațiile cu sursele și declarațiile de build.
Verificați proprietarul informației, fidelitatea exemplelor, randarea și
rezultatul comenzilor. [Calificare](release/README.md#calificare-și-publicare)

<details>
<summary>Ce demonstrează verificările documentației</summary>

- `ProjectDocumentationIdentityTest` verifică ghidurile curente și rezolvă local dovezile Chess fixate în obiectele Git indicate. Păstrați tagurile/commiturile citate; un arbore exportat singur nu demonstrează proveniența istorică.
- `AiDocumentationBudgetTest` compară introducerea indiciului și tabelul bugetului ordinar cu limitele YAML pentru căutarea ordinară și rezerva de dovadă.
- Identificatorii opaci de semnare/protocol și recepțiile imuabile ale edițiilor își păstrează numele originale.
- Sensul prozei, fidelitatea traducerii și linkurile externe străine cer în continuare revizuire; aceste verificări nu le certifică.

</details>
