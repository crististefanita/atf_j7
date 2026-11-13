# Cum se calculează analiza partidei

**📊 Analiza partidei** este disponibilă după un rezultat final, inclusiv după remiză sau renunțare. Cu **Analiză AI** bifată, meniul poate porni și analiza mutărilor înregistrate până atunci. **Analizează toate mutările** din tabla de studiu aplică aceeași căutare liniei explorate. În studiul manual, tabelul și precizia se afișează pe alb și negru. Raportul provizoriu și studiul folosesc rezultatele poziționale, iar Elo se calculează pentru o partidă încheiată eligibilă.

## De la partidă la raport

```mermaid
flowchart TD
    A["Partidă încheiată"] --> B["📊 Analiza partidei"]
    K["Partidă în curs sau linie de studiu"] --> L["Analiză AI bifată și cel puțin o mutare"]
    L --> E
    B --> C{"Analiză disponibilă pentru această salvare?"}
    C -->|Da| D["Afișează raportul salvat"]
    C -->|Nu| E["Reia mutările în ordine și identifică deschiderea ECO"]
    E --> F["Compară recomandarea și mutarea jucată pentru fiecare poziție"]
    F --> G["Calculează graficul, categoriile și precizia"]
    E -->|"Partidă încheiată eligibilă"| H["Estimează Elo din rezultatul partidei și reperul AI"]
    G --> I["Raport complet"]
    H --> I
    I --> J["Salvează analiza dacă partida este încă în arhivă"]
    I --> D
```

Indexul ECO identifică ultima poziție denumită, inclusiv dacă poziția a fost atinsă prin transpoziție. Separat, marchează prefixul de mutări care urmează efectiv o linie de carte. O mutare din acel prefix primește categoria **De carte** doar dacă pierde sub 100‰ față de recomandare. [Indexul ECO](../../chess-core/src/main/java/ro/atf/chess/OpeningIndex.java)

## Evaluarea unei semimutări

O semimutare este mutarea unei singure culori. Înaintea fiecăreia, motorul recuperează poziția și istoricul. La rădăcina căutării identifică mutările care duc imediat la remiză automată prin material, repetiție sau numărul de semimutări fără pion ori captură. Analiza compară cea mai bună variantă găsită și mutarea jucată la aceeași adâncime finalizată. Căutarea pornește de la trei semimutări și încearcă să ajungă la șase, cu extensie tactică și un buget de 85.000 de noduri suplimentare pe poziție. Dacă o iterație depășește bugetul, rămâne rezultatul iterației precedente. [Căutarea](../../chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java) · [Configurația](../../chess-core/src/main/resources/game-review.yaml)

```mermaid
flowchart LR
    P["Poziție înaintea mutării și istoric"] --> Q["Căutare la adâncime comună"]
    R["Replică verificată la poziția exactă"] --> Q
    Q --> B["Scor recomandat"]
    Q --> M["Scor mutare jucată"]
    B --> L["Pierdere în miimi"]
    M --> L
    L --> C["Categorie"]
    Y["Praguri classification din game-review.yaml"] --> C
    L --> A["Precizie"]
    C -->|"Exclude cartea și mutarea forțată"| A
    M --> W["Conversie la perspectiva albului"]
    W --> G["Punct pe grafic"]
```

Scorul intern se raportează la culoarea care mută. Motorul evaluează pozițiile obișnuite în centipioni, iar maturile demonstrabile primesc scoruri extreme. Analiza transformă scorul pe scara 0–1.000, apoi compară cele două valori.

```text
puncte(cp) = rotunjire(1000 / (1 + exp(-cp / 220)))
pierdere = max(0, puncte(recomandare) - puncte(mutare jucată))
```

De exemplu, dacă recomandarea are +220 cp și mutarea jucată 0 cp, valorile sunt 731‰ și 500‰. Pierderea este **231‰**. Pe grafic, 500‰ înseamnă echilibru. Pentru o mutare a negrului, valoarea desenată este `1000 − puncte(mutare jucată)`, astfel încât partea de sus favorizează mereu albul. Primul punct pornește de la evaluarea recomandării din poziția inițială. [Calcul și grafic](../../chess-core/src/main/java/ro/atf/chess/GameReview.java) · [Afișarea Android](../../android/app/src/main/java/ro/atf/chess/android/GameReviewDialog.java)

În pozițiile exacte din repertoriul verificat, raportul recomandă replica de repertoriu și evaluează ambele variante la aceeași adâncime încheiată. O linie terminală demonstrată sau o remiză automată la rădăcină păstrează prioritatea. Dacă evaluarea locală a mutării jucate depășește scorul recomandării, pierderea rămâne zero. [Repertoriul](../../chess-core/src/main/resources/ai-repertoire.yaml)

## Categorii și precizie

Motorul verifică regulile în ordinea de mai jos. Prima regulă aplicabilă stabilește categoria. Pragurile din tabel sunt valorile implicite ale secțiunii `classification` din [game-review.yaml](../../chess-core/src/main/resources/game-review.yaml). Testele de guvernanță verifică valorile, unitățile întregi și ordinea pragurilor. Analizele deja salvate păstrează rezultatul calculat la momentul respectiv. Toate mutările intră în tabel, iar coloana jucătorului rămâne prima dintre cele numerice chiar dacă joacă cu negrul.

| Ordine | Categorie | Condiție |
|---|---|---|
| 1 | De carte | În prefixul ECO, cu pierdere sub 100‰. |
| 2 | Forțată | Există o singură mutare legală. |
| 3 | Strălucită | Scor cel puțin egal cu al recomandării, adâncime de minimum 4, scor jucat de cel puțin 550‰ și sacrificiu acceptat de minimum 200 cp în replica găsită. Mai cere un avantaj de cel puțin 100‰ față de alternativa estimată sau un singur mat demonstrat în căutare. |
| 4 | Excelentă tactic | Scor cel puțin egal cu al recomandării, adâncime de minimum 4, avantaj de cel puțin 100‰ față de alternativă și trecerea pragului evaluat de motor. Recomandarea ajunge la minimum 750‰ cu alternativa sub 700‰ sau la minimum 450‰ cu alternativa sub 400‰. |
| 5 | Cea mai bună | Scorul intern al mutării atinge cel puțin scorul recomandării. |
| 6 | Ocazie ratată | Adversarul tocmai a pierdut cel puțin 100‰, recomandarea ajunge la minimum 750‰, iar mutarea jucată la maximum 550‰. |
| 7–11 | Greșeală gravă, greșeală, imprecizie, bună, excelentă | Pragurile sunt ≥200‰, ≥100‰, ≥50‰, ≥20‰, respectiv sub 20‰. |

Precizia se calculează separat pentru fiecare culoare, din pierderile mutărilor cu alegere. Mutările de carte și cele forțate sunt excluse.

```text
precizie = rotunjire(100 - suma pierderilor / (10 × numărul de mutări cu alegere))
```

Cu două mutări eligibile care pierd 231‰ și 50‰, precizia este **86%** după rotunjire. Fără mutări eligibile, câmpul rămâne necompletat. [Clasificarea și agregarea](../../chess-core/src/main/java/ro/atf/chess/GameReview.java)

## Cum se estimează Elo

```mermaid
flowchart LR
    A["Reperul profilului AI din game-review.yaml"] --> E["Estimare Elo pentru partidă"]
    B["Rezultatul din perspectiva jucătorului"] --> E
    C["Minimum 10 mutări cu alegere"] --> E
    E --> D["Valoare centrală și interval"]
    H["Jucătorul a mutat pentru AI"] --> F["Afișează doar precizia și categoriile"]
```

Elo estimat folosește rezultatul și reperul profilului AI. Pentru jucător, `s` este 1 la victorie, 0,5 la remiză și 0 la înfrângere. Formula se aplică după minimum zece mutări cu alegere.

```text
p = (1 + s) / 3
diferență Elo = rotunjire(400 × log10(p / (1 - p)))
estimare = Elo de referință + diferență Elo
```

Rezultatul unic actualizează o distribuție uniformă a scorului așteptat la `Beta(1 + s, 2 − s)`. Afișajul adaugă diferențele Elo din cuantilele 5% și 95% la capetele intervalului reperului AI. De exemplu, o înfrângere contra **Advanced**, cu reper 1461 și suficiente decizii, dă **≈1341 (549–1900)**. Intervalul arată cât de puțină informație oferă o singură partidă. Reperul AI provine din protocolul local de calibrare cu Stockfish. [Calibrarea externă](ai-quality.md#rezultatele-ancorei-externe) · [Configurația](../../chess-core/src/main/resources/game-review.yaml)

## Salvare și verificare

- Analiza terminată se păstrează lângă partida din arhivă. Cache-ul are versiune și amprenta SHA-256 a salvării exacte, inclusiv rezultatul și configurația. O analiză absentă, trunchiată sau incompatibilă se recalculează la redeschidere.
- Închiderea ferestrei în timpul calculului oprește căutarea. Arhiva reține ultimele zece rezultate și elimină analiza odată cu intrarea veche. Continuarea din arhivă creează o partidă separată.
- `GameReviewTest` verifică evaluarea, clasificarea, estimarea Elo și reconcilierea tabelului. `GameReviewGovernanceTest` verifică pragurile, reperele și catalogul ECO. `MatchArchiveTest` verifică refacerea raportului complet și respingerea cache-urilor incompatibile. [Strategia de testare](../testing/README.md#partidă-și-istoric)

## Navigarea în review și variantele recomandate

Review-ul măsoară spațiul util al ferestrei după antet și zonele de sistem. În portret, tabla este cel mai mare pătrat care lasă loc barei de comenzi și începutului raportului derulabil. Într-o fereastră lată, raportul trece lateral când rămâne lizibil și tabla poate crește. Cursorul, indiciul și numele deschiderii se pot schimba fără să deplaseze tabla.

Cursorul pornește la finalul partidei. Săgețile discrete schimbă câte o semimutare, iar comanda **Moment-cheie următor** sare la următoarea poziție marcată. Lista și graficul permit selecția directă. Tabla, notația, categoria, scorurile și markerul graficului urmăresc același cursor. Săgețile se dezactivează la capete. Bara are ținte de 48 dp și etichete accesibile în română și engleză. În ferestre foarte înguste, bara derulează orizontal. Detaliile de scor, graficul, precizia, Elo, comparația și lista mutărilor se derulează împreună.

Numele deschiderii identificate apare la intrare. Prima navigare sau activarea variantei îl retrage din raport. **Detalii despre deschidere** din meniul de acțiuni afișează la cerere numele și codul ECO.

```mermaid
flowchart LR
    L["Listă de mutări"] --> C["GameReviewTimeline · cursor"]
    G["Punct selectat pe grafic"] --> C
    N["Săgeți și moment-cheie"] --> C
    C --> B["Poziție pe tablă"]
    C --> S["Scor, notație și clasificare"]
    C --> M["Markerul cursorului pe grafic"]
    C -->|"Bec aprins"| R["Linia recomandată · pas 0"]
    R -->|"Săgeți"| P["Parcurge varianta pe o copie"]
    P -->|"Bec stins"| C
    P --> A["Partida arhivată rămâne neschimbată"]
```

Becul deschide varianta optimă înaintea mutării selectate și marchează prima recomandare pe tablă. Aceleași săgeți parcurg varianta cât timp becul este activ. Bara arată pasul curent, iar raportul oferă notația următoarei recomandări. Stingerea becului revine la mutarea selectată. Alegerea unei poziții din listă sau grafic ori saltul la un moment-cheie închide varianta. Ramura de analiză lucrează pe o copie a poziției și păstrează istoricul partidei.

În meniul de acțiuni, **Vezi pe tablă** închide review-ul și mută cursorul activ la semimutarea aleasă. **Reîncearcă mutarea** cere confirmarea continuării din poziția anterioară pentru mutările jucătorului sau pentru ambele culori în studiu. Pentru o partidă arhivată, continuarea se salvează într-un loc separat. Refuzul confirmării, explorarea variantei și închiderea review-ului păstrează rezultatul original.

`GameReviewTimeline` validează fiecare mutare și culoare față de sursă. `GameReviewLayout` alege geometria din dimensiunile măsurate. Verificările componentelor și parcursul Android urmăresc sincronizarea, navigarea variantei și păstrarea partidei. [Sanity și Extent](../testing/README.md#partidă-și-istoric)
