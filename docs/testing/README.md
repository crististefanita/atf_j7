# Strategia de testare

## Pe scurt

Alegeți cel mai mic nivel de testare care poate observa schimbarea. Construiți dintr-o copie a sursei în TMP.

- [Suitele JVM](#suite-jvm) verifică regulile, adaptoarele și guvernanța fără emulator.
- [Sanity Android](#sanity-android) și [paritatea Web](#paritatea-web-a-motorului) acoperă, când e nevoie, randarea, ciclul de viață și tehnologiile asistive reale.
- [Dovezile Extent](#raportare-extent) leagă aserțiunile de rulare. [Calificarea](../release/README.md#calificare-și-publicare) guvernează promovarea.

## Alegerea nivelului

- **Componentă** pentru reguli, progres, persistență, ordonare și layout măsurabil. Folosiți store-ul real cu preferințe în memorie și aserțiuni pe valori observabile.
- **Guvernanță** pentru reguli de structură, proveniență, contracte de raportare și publicare. Includeți o probă negativă când un artefact incomplet ar putea primi PASS.
- **Sanity Android** când comportamentul depinde de Activity, preferințele Android, procese, randarea nativă sau accesibilitate. Păstrați doar parcursul care validează integrarea lipsă din componente.
- **Benchmark** numai pentru comparații cu Stockfish, calibrarea boților sau măsurători de timp. Țineți intrările fixe și raportați separat rezultatele opționale.

Alegeți cel mai mic nivel care observă contractul. Verificați regulile în
JVM, iar integrarea nativă și restartul real pe Android. În Extent, urmăriți
aserțiunea, diagnosticul și, unde este necesară, captura checkpointului.

## Motor și proiecție

Verificați în JVM legalitatea mutărilor, proiecția tablei și soluțiile din cataloage. Comparați mutările cu oracolul independent chess.js.

<details>
<summary>Reguli, oracole pentru cataloage și comenzi de regenerare</summary>

`ChessComponentTest` verifică poziția inițială, orientarea și legalitatea, cu oracole independente. Cazurile includ numărul pozițiilor la primele trei mutări individuale, rocada, en passant, promovarea și matul.

`BoardPanelLayoutTest` verifică geometria panoului pentru ferestre înalte, scunde și etichete mai înalte. Dimensiunea tablei se calculează din spațiul rămas după măsurarea ambelor etichete.

- `MainMenuLayoutTest` verifică ordinea celor șase capitole, textele RO/EN și derularea într-o fereastră scundă. `QuestStepTest` verifică piesele și culoarea la mutare înainte și după apărare, reluarea și blocarea navigării până la rezolvare. Acoperă și redarea unui mat alternativ din catalog, cu explicația lui, plus revenirea la linia de referință pentru date vechi sau invalide.

- `QuestCatalogTest` enumeră fiecare mutare legală pentru toate cele 500 de probleme și cere o singură soluție de mat. O probă separă șahul cu apărare de mat.
- O altă probă verifică patul, rocada prin șah și captura en passant care ar expune propriul rege. Amprenta completă a mulțimilor de mutări se compară cu [`quest-oracle.yaml`](../../chess-core/src/main/resources/quest-oracle.yaml), produs de `chess.js` 1.4.0.

`QuestCatalogTest` recalculează din YAML ordinea strict crescătoare a problemelor Mat în 1 și verifică pozițiile repetate în cataloage. Cazurile negative acoperă un mat alternativ, o temă incorectă, un ID retras, o poziție apropiată cu aceeași soluție și copii obținute prin simetrie. `QuestProgressTest` verifică reluarea după reordonare și retragerea unei poziții fără creditarea înlocuitoarei.

`QuestHintTest` verifică pentru toate cele 500 de probleme că prima treaptă expune exclusiv plecarea, iar a doua destinația validată de motor. După apărarea adversarului, indiciul trebuie să folosească următoarea poziție. `QuestProgressTest` verifică pragurile după reluare și reordonare, idempotența rezolvărilor și finalul unui catalog de altă lungime.

- `MateExplanationTest` confruntă pentru toate cele 500 de probleme piesele care dau șah și explicațiile câmpurilor de fugă cu amprenta produsă de chess.js. Probele pentru `mate-101` și `mate-102` folosesc pozițiile din catalog și verifică faptul că pornesc în șah, indiciile și fiecare apărare legală. O probă separată cere o apărare legală după un șah care lasă adversarului mutări.

```powershell
./scripts/update-mate-one-order.ps1 -ChessJs $chessJs
```

Setați `$chessJs` la fișierul CommonJS din pachetul extras chess.js 1.4.0. Verificatorul citește versiunea din `package.json`. Motorul independent rulează în TMP și nu intră în APK. [chess.js](https://github.com/jhlywa/chess.js/tree/v1.4.0) · [Regenerarea ordinii și a dovezii](../board/quest-curation.md#configurarea-capitolelor)

`MateInTwoCatalogTest` verifică exact 300 de poziții, absența matului imediat, o singură cheie, fiecare apărare legală și toate maturile din ramurile rezultate. Amprenta calculată de Chess Lab trebuie să coincidă cu cea produsă independent de chess.js.

```powershell
node scripts/verify-mate-in-two.cjs $chessJs chess-core/src/main/resources/mate-in-two.yaml chess-core/src/main/resources/mate-in-two.sha256
```

`MateInThreeCatalogTest` verifică 200 de poziții cu mat exact în trei, unicitatea primei mutări și toate continuările câștigătoare după prima apărare. Amprenta este comparată cu cea calculată de chess.js 1.4.0.

```powershell
node scripts/verify-mate-in-three.cjs $chessJs chess-core/src/main/resources/mate-in-three.yaml chess-core/src/main/resources/mate-in-three.sha256
```

</details>

### Buget adaptiv Hard

`HardAdaptivePolicyTest` verifică tipurile cheilor YAML, limita de bază și
limita maximă, adâncimea minimă completă, reutilizarea măsurată a cache-ului,
riscul pe coloana regelui și izolarea deciziilor. Verificarea rezervată a schimbului
este inclusă în aceeași alocație. O probă efectivă de verificare imbricată acoperă
ultimele noduri până la bază și până la maxim, cu și fără semnalul de schimb nerezolvat.
`AiOpeningRegressionTest` păstrează controalele capturii de pion expus și ale
pionului apărat după încheierea celei de-a cincea semimutări. `MatchSetupTest`
și `RookEndgameHistoryTest` verifică maximul configurat și rezultatele legale.
Proba de istoric rejucă o secvență înghețată, astfel încât schimbarea alocației
de căutare să nu genereze alt fixture înaintea aserțiunii.

Folosiți `hard-adaptive.yaml` curent când comparați 100.000 fix, 200.000 fix
și căutarea adaptivă. Comparați fiecare mutare pe aceeași sursă și păstrați
istoricul complet. Un buget mai mare poate schimba mutarea preferată, deci
verificați replica adversă și WDL-ul rezultat cu Stockfish. Raportați deciziile
efective, adâncimile complete, nodurile și timpul CPU înainte de interpretarea
estimării Elo. Liga ordonării învățate folosește propria sursă și politică
înghețate. Estimarea ei Elo aparține acelui comparator exact.

### Integritatea indiciului și deschiderile comune

`HintFrontierTest` compară `…Nxc2+` până după `Qxc2`, recaptura nebunului din
debut și două sacrificii de mat corecte, cu mutarea sprijinului ca contraexemplu.
Cache-ul încălzit, rece și dezactivat trebuie să coincidă după o tăiere cu fereastră
îngustă. YAML respinge peste 200.000 de noduri, iar deciziile publice taxează
orizonturile inițiale, frontiera tactică și comparația la adâncime comună din plafon.

`MatchHintTest`, `HintComparisonTest` și `HintPreviewTest` cer ca becul,
comparația, atingerea destinației și rejucarea să păstreze aceleași mutări evaluate.
Rejucarea conservă checkpoint-ul și nu adaugă un răspuns neevaluat. Aserțiunile
comparării disting orizontul nominal complet de frontiera sa limitată.

`OpeningPlayTest` acoperă Basic, Advanced și Hard după deschideri umane diferite,
schimbarea ramurii, transpozițiile exacte, pierderea rocadei și ieșirea reală
din teorie. Replicile pe profil și cele rezervate analizei își păstrează domeniul.
Regulile efective rejucă fiecare cadru și întreaga continuare documentată.

[Frontiera indiciului](../../atf-tests/src/test/java/ro/atf/chess/HintFrontierTest.java) · [Contractele deschiderilor](../../atf-tests/src/test/java/ro/atf/chess/OpeningPlayTest.java) · [Paritatea rejucării Web](../../atf-tests/src/test/java/ro/atf/chess/WebHintReplayComponentTest.java)

`HintRecaptureRegressionTest` protejează comparația forțantă captură de pion/piesă, indiciul corect precedent, identitatea cache-ului încălzit/rece/dezactivat, schimburile întrerupte și YAML plafonat. [Corecția Caro–Kann măsurată](../board/ai-quality.md#indiciul-carokann-și-verificarea-recapturii-după-pionul-cu-șah)

`HintQueenRecaptureTest` protejează atacul italian de dezvoltare asupra damei, recaptura legală de pion, cache-ul separat al seminței/comparației, păstrarea orizontului complet la întrerupere, identitatea tablei manuale/atingerii și ofertele corecte de damă. Dezactivarea limitei trebuie să reproducă vechiul indiciu. [Corecția italiană măsurată](../board/ai-quality.md#recaptura-damei-și-continuitatea-indiciului-italian)

`HintPinSupportTest` guvernează departajările defensive ale scorurilor exacte egale, apărarea sigură a unei piese ușoare legate relativ, contraexemplele, ambele culori, prioritatea numerică, istoricul păstrat și calea comună de indiciu manual/partidă. Ținta rămâne H5 la 200k, cu −21/−21 cp neschimbat. [Audit sicilian](../board/ai-quality.md#egalități-exacte-în-siciliană-și-apărarea-piesei-legate)

### Colecția offline de indicii

`HintAuditSessionTest` validează planul versionat, reluarea întregului istoric,
ancorele de încredere, materialul efectiv, rangurile indisponibile și protecția
ieșirii cu mock-uri. Metoda benchmark cu etichetă separată înregistrează trasee
scurte reale și bugete comparate din aceeași poziție, printr-un apel pe decizie.
Raportul Extent păstrează identitatea sursei, checkpoint-urile, FEN, variantele
oferite și comparate, replicile, adâncimile și nodurile efective. [Rulare și interpretare](hint-audit.md)

`HardHintAuditedGameTest` leagă jocul nativ, indiciile publice și bara la fiecare checkpoint. `StockfishStudyAuditTest` respinge UCI brut, rădăcini, istorii, orizonturi și identități de oracol modificate. `HardHintStudyBenchmarkTest` publică offline colecția înregistrată cu verdicte separate de integritate și calitate șahistă. [Audit pereche și publicare](hard-hint-study.md)

### Auditul nativ al partidelor Hard

Fiecare semimutare, inclusiv replica adversarului, cere analiza efectivă Hard cu istoric. Componentele resping recepții lipsă sau străine; publicatorul benchmark rejucă dovezile înainte de Elo și Extent. [Protocol, comandă și interpretare](hard-audit.md)

## Paritatea Web a motorului

`ChessWebRulesComponentTest` verifică motorul și cataloagele pe JVM. `ChessWebParityPublication` execută aceleași cazuri în workerul TeaVM pentru toate resursele folosite. Rulați proba explicit cu pachetul din TMP. [Build și probe Web](../../web/README.md)

### Intrarea în meniul Web

`ChessWebShellTest` compară titlul și grupurile din Web cu resursele APK. Componentele de navigare simulează istoricul browserului, accesul direct la o secțiune și revenirea la meniu. Guvernanța verifică adresa publică din README, iar sanity-ul pornește din acel link într-un profil nou. [Contractul meniului](../../web/README.md#meniul-principal-și-piesele)

- Pentru culoarea inițială, `MatchSetupTest` și `ChessWebParityPublication#packagedWorkerResolvesAndSavesTheSharedColorChoices` folosesc același fixture cu rezultate explicite pentru Alb, Negru și ambele rezultate aleatorii. Raportul verifică numărul de extrageri, orientarea, tura și păstrarea culorii după redeschidere.
- Adaptorul de test verifică și respingerea unei replici AI cu revizie expirată. `ChessWebShellTest` compară ordinea și textele RO/EN ale opțiunilor cu resursele Android.

`ChessWebStudyShellTest` verifică fără browser că tablele din Probleme și Partide folosesc aceeași regulă CSS și păstrează comenzile în structura de joc. În proba de browser, măsurați dreptunghiurile tablei și ale comenzilor vizibile în ambele ecrane, la 360 × 640 și 1280 × 900 CSS px cu text la 130%. După prima mutare, verificați că tabla rămâne fixă, istoricul încape și documentul nu cere derulare în aceste profiluri. [Regula de dimensionare](../../web/README.md#tabla-din-probleme-și-din-partidă)

Proba MCP a măsurat aceeași latură în ambele ecrane, 324 px pe compact în RO și 469,8 px pe desktop în EN. La 320 × 480 în RO, ambele table păstrează o latură de 249,6 px și pot derula vertical pentru a păstra accesul la comenzi.

### Paritatea meniurilor și indiciilor

- `ChessWebShellTest` compară acțiunile principale și de partidă cu Android și
  verifică recuperarea importurilor pe ruta proprie. Nume și Setări sunt ultimele acțiuni principale.
- `ChessWebOptionsMenuTest` testează controlerul real de meniu cu dimensiuni
  simulate, schimbări de fereastră, tastatură și focus. Includeți dimensiuni
  compacte și desktop, text 130%, origine derulată și liste lungi.
- `HintReplayPresentationTest` verifică afișajul comun, cursoarele independente
  ale liniilor și adaptorul Android. Poziția selectată în istoric se păstrează.
- `WebUiParityGovernanceTest` cere ca RAG să citeze aceste componente și secțiunea
  responsabilă. O suprafață necunoscută rămâne semnalată pentru revizuire.

Folosiți browserul MCP pentru proba Web randată și Android pentru adaptorul
nativ. Consemnați separat aceste observații și verdictul componentelor offline.

### Controlul replicilor AI

- Selectați `ChessWebParityPublication#packagedWorkerKeepsAiIdentityThroughManualControl` în comanda Maven pentru pachetul TeaVM. `MatchControlsTest` și workerul citesc același fixture pentru comutarea fără mutare manuală și pentru mutarea legală jucată de partea AI.
- Proba workerului cere același ID, slot și checkpoint după redeschidere, respinge replica de la o revizie expirată și verifică efectul mutării manuale asupra estimării Elo. `MatchLedgerTest` verifică și clasificarea la încărcarea salvărilor vechi.
- [Comanda Maven](../../web/README.md) · [Datele comune](../../atf-tests/src/test/resources/match-automation-cases.json)

- `MatchControlsTest` verifică disponibilitatea comutatorului la poziția curentă, în istoric și după încheiere. `ChessWebShellTest` verifică etichetele RO și EN cu resursele Android.
- Un ceas și un calcul simulate verifică anularea prin `AbortSignal` și ignorarea unui rezultat livrat după anulare. În sanity-ul de interfață, treceți în modul manual în tura AI, efectuați o mutare legală și redeschideți același slot.
- Reactivați automatizarea în tura AI și observați o singură replică. [Contractul partidei](../../web/README.md#controlul-replicilor-ai)

### Analiza în partida cu AI

Comparați același checkpoint și aceeași semimutare în JVM și în worker. Folosiți probele native și de browser pentru bara randată, bec și tabla fixă.

<details>
<summary>Cazuri comune, ecrane și recepții</summary>

Selectați `MatchAnalysisParityTest` și
`ChessWebParityPublication#packagedWorkerProjectsInlineMatchAnalysis` în comanda
Maven pentru pachetul TeaVM. Cazurile comune fixează checkpointul și semimutarea
afișată. Comparați opțiunea după restaurare, evaluarea albului, linia de mat din
poziția istorică și starea terminală.
[Comanda Maven](../../web/README.md) · [Datele comune](../../atf-tests/src/test/resources/match-analysis-cases.json)

`ChessWebShellTest` exercită în Node schimbarea poziției în timpul calculului
și refuzul indiciului care nu mai este legal în poziția afișată. Un mock al
workerului urmărește starea barei, eliberarea selecției o singură dată și
redarea liniei. Poziția din afara teoriei și cazul de mat compară rezultatul
analizei în partidă și pe Tabla de analiză.

- În proba de interfață, deschideți meniul principal cu o analiză deja importată. Titlul rămâne singur în antet, iar analiza se poate deschide din zona de import.
- Începeți o partidă cu bifa oprită și verificați absența panoului. Porniți una cu analiză și comparați dreptunghiurile barei și tablei de la poziția de start.
- Declanșați becul din rândul istoricului și parcurgeți o semimutare în previzualizare, apoi verificați poziția și revenirea focusului. Comparați laturile tablei de origine și ale redării pentru **Linie calculată**, redarea puzzle-ului și Tabla de analiză la 360 × 640, 1280 × 900 și 740 × 420 CSS px. Verificați 64 de casete pătrate, comenzile vizibile, absența depășirii orizontale și o nouă măsurare a originii la redeschidere. Componentele acoperă capturarea înaintea ascunderii barei, geometria invalidă și parcurgerea fără recapturare. La redeschiderea partidei, verificați bifa salvată, orientarea și încadrarea la text mărit.
- [Contractul Web](../../web/README.md#analiza-în-partida-cu-ai)

- Pentru proba pereche RO/EN, porniți din aceeași partidă după **1.e4 e5**.
  Alegeți poziția 1/2 și verificați bara din stânga, evaluarea +0,05 pentru
  Alb și indiciul **…Nc6**. Redați pașii 0 → 1 → 2 → 1 pe tabla variantei.
  Închideți la 1/2 și redeschideți același slot la 2/2. Bara indică
  +0,19 pentru Alb; pe Web am verificat și noul indiciu **Nf3**.

| Adaptor | Profil compact, text 1,3× | Proba și dovada |
|---|---|---|
| Android | 720 × 1280, 320 dpi | `-AnalysisOnly -CompactMatchAnalysisOnly` verifică APK-ul izolat în dezvoltare. Pentru ediție, adăugați `-Distribution -SourceCommit <sha>` ca să verificați exact APK-ul semnat. Ambele probe fac pregătire și restart real în RO și EN. `AndroidSanityPublication#publishCompactMatchAnalysisSanity` verifică recepțiile și capturile. |
| Web | 360 × 640 CSS px | Browser MCP măsoară tabla și salvează capturi la istoric, redare, întoarcere și reluare. `ChessWebMatchAnalysisPublication` leagă cele două limbi de rulare, revizie și runtime. |

Publicatorul Web primește directorul recepțiilor prin
`-Dchess.web.match.browser`, directorul runtime prin
`-Dchess.web.match.runtime` și valorile execuției prin
`-Dchess.web.match.runId` și `-Dchess.web.match.source`. Alegeți un HTML
nou prin `-Dchess.web.match.extent`. Testele negative verifică recepțiile
substituite, coordonatele deformate și abaterea de la linia comună.

- Comparați tabla înainte și după săgeți, la întoarcerea din previzualizare și
  după redeschiderea slotului. În APK, verificați pătratul maxim după ce măsurați
  zona utilă fără barele de sistem și rezervați loc pentru jucători și comenzi.
  Profilul Android măsurat lasă un dreptunghi de 694 × 665 px pentru tablă,
  după rezervarea barei de evaluare și a intervalului. Latura de 665 px
  (332,5 dp) atinge limita de înălțime. În Web, la 360 × 640 CSS px, tabla
  de 350 px, bara de 8 px și intervalul de
  2 px ocupă toată lățimea utilă. Bara rămâne vizibilă pe toată înălțimea, în stânga
  tablei, iar becul păstrează o țintă vizibilă de cel puțin 48 de unități în
  fiecare adaptor. La 320 × 568 CSS px și text 130%, înălțimea limitează
  tabla Web la 279,6 CSS px. Verificați că săgețile păstrează coordonatele,
  iar becul încape fără derulare. În Web, **Mat în 1** păstrează dreptunghiul
  și după rezolvare, Next și Back.

</details>

### Actualizarea pachetului Web

În componentă, verificați că adresele din `index.html` indică aceeași
versiune pentru toate stilurile și modulul de intrare, inclusiv după
schimbarea unui singur fișier. Urmăriți importurile relative și workerul
până la motorul TeaVM din directorul versiunii. Un pachet cu același manifest
trebuie să aibă aceeași adresă de runtime. [Schema pachetului](../../web/README.md#identitatea-pachetului-web)

- Pentru sanity în browser MCP, păstrați un profil care a deschis ediția anterioară și are o partidă salvată. După publicare, reîncărcați normal din linkul README, cu memoria cache activă.
- Verificați adresele efectiv cerute, reluarea slotului și funcționarea barei și a becului. În Extent, înregistrați revizia, directorul runtime, checkpointul și capturile.
- Identificați publicarea Actions prin commitul static exact și manifestul runtime. O probă HTTP ocolește memoria cache și verifică octeții serviți pentru fiecare fișier. [Publicarea Web](../../web/parity.md#publicarea-linkului)

Proba de migrare în același tab a redeschis slotul pe site-ul public cu
stilurile și modulul din aceeași versiune; becul a arătat e2–e4. Proba locală
cu cache activ a păstrat și poziția după 1.e4 Nc6 înainte de un indiciu nou.

### Orientarea tablei în partidă

- `MatchControlsTest` verifică disponibilitatea acțiunii în partidă și în istoric. `MatchStoreTest` și `ChessWebParityPublication#packagedWorkerPreservesTheMatchWhenFlipping` folosesc același fixture pentru păstrarea poziției, istoricului și orientării la redeschidere.
- Workerul respinge o replică rămasă la revizia veche. `ChessWebShellTest` folosește un ceas simulat și verifică înlocuirea programării vechi cu o singură replică la revizia curentă.
- [Comanda de verificare](../../web/README.md#motorul-chess-lab-în-browser)

- În proba de interfață, deschideți o partidă, reveniți la o semimutare din istoric și alegeți **Întoarce tabla**. Comparați colțurile, tura, semimutarea și dreptunghiul tablei.
- Verificați revenirea focusului la butonul din antet și orientarea după redeschidere. Fixture-ul de arhivă verifică orientarea noului slot la continuare și perspectiva originală la redeschiderea arhivei.
- [Comportamentul partidei](../../web/README.md#partida-cu-ai)

### Partide salvate, arhivă și continuare

Folosiți componentele pentru starea sloturilor și a arhivei, continuare, selecția în grafic și focusul logic. Păstrați probe reale pentru randare și tehnologii asistive.

<details>
<summary>Verificări pentru stocare, navigare și focus</summary>

`MatchStoreTest`, `MatchLedgerTest` și `MatchLedgerPreferencesTest` verifică migrarea, ordinea, retenția și selecția slotului. Cazurile acoperă rescrierea slotului activ, o continuare separată din arhivă și refuzul când toate sloturile sunt ocupate. `ChessWebLedgerPublication` verifică biblioteca după reîncărcare, checkpointurile și capturile din Edge. [Probe Web](../../web/README.md#partide-salvate-și-încheiate)

`MatchLedgerReviewTest` verifică persistența analizei și a poziției de reluare per arhivă, respingerea unui checkpoint sau cache din altă partidă, migrarea unui rezultat unic și evacuarea completă. `ChessWebArchiveReviewsPublication` validează două analize calculate de worker, pozițiile redeschise după reîncărcare și capturile din aceeași rulare. [Reluarea analizei arhivate](../../web/README.md#reluarea-analizei-unei-partide-arhivate)

- `ArchivedReviewContinuationTest` verifică poziția exactă din istoric, slotul separat, biblioteca plină și o singură replică AI după reluare. `ChessWebArchiveContinuationPublication` compară dovada din browser cu checkpointul, analiza și poziția de reluare originale, inclusiv după o mutare în partida nouă.
- Android păstrează proba nativă de continuare din arhivă în `-GameReviewOnly`. [Continuarea din Game Review](../../web/README.md#continuarea-din-analiza-partidei)

- `GameReviewGraphParityTest` folosește partida de 185 de semimutări din APK și poziții de control verificate separat. În JVM verifică punctul 184 la geometria nativă și la două lățimi Web, ambele capete, piesele Staunton originale și dimensiunile capturilor.
- Sanity-ul Android păstrează atingerea și redarea nativă. `ChessWebReviewGraphPublication` cere coordonatele trimise de interfață către worker, poziția de pe tablă, rândul vizibil și marcajul din arhivă după reîncărcare.
- [Graficul Game Review](../../web/README.md#graficul-analizei-partidei)

- `ChessWebReviewAccessibilityTest` verifică în Node destinațiile de focus, limitele istoricului și derularea necesară. `GameReviewSessionTest` verifică revenirea la poziția jucată după **De ce?**.
- Sanity-ul Edge păstrează proba Tab, săgeți și revenirea cu focus vizibil la font 1,3×. Reperul Android compact RO/EN verifică încadrarea și păstrarea poziției.
- [Tastatura în Game Review](../../web/README.md#tastatura-în-analiza-partidei) · [Redarea compactă](#redare-comună-pe-ecran-compact)

Pentru cititoare de ecran, `ChessWebReviewAccessibilityTest` verifică textele RO/EN, limitele graficului și sursa fiecărui anunț. Sanity-ul urmărește valoarea graficului, rândul selectat și revenirea din **De ce?**. Extent indică sursa fiecărei observații, precum arborele Chromium, un eveniment Android sau o cerere către sintetizator. [Cititorul de ecran Web](../../web/README.md#cititorul-de-ecran-în-analiza-partidei) · [Proba TalkBack](#proba-talkback)

Pe Android, `-MatchMenusOnly` verifică listele și deschiderea rândurilor salvate și încheiate. `-GameReviewOnly` verifică o mutare alternativă în continuare și păstrarea intrării arhivate. [Partidă și istoric](#partidă-și-istoric)

</details>

### Studiu Web

Testele de componentă simulează tabla, stocarea și temporizatorul. Ele verifică geometria săgeții în ambele orientări, etichetele RO/EN din resursele APK, accesul la explicațiile lungi, focusul, istoricul, reluarea unei apărări și răspunsurile întârziate după închiderea ecranului.

`ChessWebStudyShellTest` rulează și workerul compilat când primește `-Dchess.study.bundle`. Raportul Extent consemnează deschiderea celor șase capitole, catalogul RO/EN de deschideri, poziția de analiză, izolarea Game Review și refuzul continuării când toate cele zece locuri sunt ocupate. [Comenzile Studiu](../../web/README.md#studiu)

### Reluarea Game Review pe Android și Web

- `GameReviewSessionTest` verifică selecția semimutării, cele două ramuri, bookmarkul și refuzul comenzilor expirate. `ChessGameSessionTest` verifică poziția restaurată și analiza unei linii neterminate.
- Probele Edge folosesc pachetul compilat pentru import, analiză nouă și reîncărcare reală. Publicatorul Extent verifică mutările, cache-ul de analiză, identitatea pachetului și capturile.
- [Comenzile de rulare](../../web/README.md#analiza-partidei)

## Tactică și punctaj

- `QuestRewardsTest` verifică seria între capitole, plafonul bonusului, restaurarea unei încercări asistate, punctajul anterior și totalurile. `TacticsCatalogTest` verifică cheia unică, lipsa matului imediat, continuitatea ID-urilor și câștigul care rezistă răspunsurilor legale la capătul liniei.
- `QuestPayoffTest` verifică reluarea etapelor, capturile, finalul alternativ și restaurarea soluțiilor vechi. YAML-ul indică proveniența pozițiilor.
- Amprenta conține cheia și câștigul de material recalculate de chess.js.

- `QuestDefenseStudyTest` verifică toate studiile din [catalogul explicațiilor](../../chess-core/src/main/resources/quest-defense-studies.yaml). Controlează cheia, replica legală, limba, bilanțul de pe tablă și compatibilitatea cu progresul salvat.
- Proba `measuresHardOnReviewedDefensesWithoutGivingItTheLesson` măsoară cheile și mutările ulterioare separat. La fiecare poziție fixată, motorul primește numai tabla și profilul Hard.
- Concordanța cu linia de studiu rămâne o măsurătoare, iar legalitatea și plafonul sunt aserțiuni.

Pentru explicațiile din Tactică, rulați `run-android-sanity.ps1 -TacticsExplanationOnly` cu parametrizarea ADB din [Sanity Android](#sanity-android). Proba deschide din tablă reluarea normală pentru `tactic-002` și studiul cu replica `…Kg8` pentru `tactic-043`. Verifică navigarea, materialul afișat și păstrarea punctelor după închidere.

Publicați `android-tactics-explanation.html` cu `-Dtest=AndroidSanityPublication#publishTacticsExplanationSanity` și `-Dchess.android.receipt=<dosar>/tactics-explanation.txt`. Extent include capturile și verifică ID-ul de rulare, recepția și amprentele PNG raportate de instrumentare.

- Pentru ultima mutare din `training-069`, rulați `-TrainingFinishOnly`. `QuestPayoffTest` verifică obiectivul imediat, linia legală după `Rd8+` și punctajul unic.
- Sanity verifică accesul la explicație după șah, reluarea pe tablă, păstrarea progresului și redeschiderea explicației după rezolvare. Publicați raportul cu `-Dtest=AndroidSanityPublication#publishTrainingFinishSanity` și recepția `training-finish.txt`.

```powershell
node scripts/verify-tactics.cjs $chessJs chess-core/src/main/resources/tactics.yaml chess-core/src/main/resources/tactics.sha256
```

## Reluarea explicațiilor din puzzle

[QuestReplayTest](../../atf-tests/src/test/java/ro/atf/chess/QuestReplayTest.java)
verifică pe pozițiile din catalog semimutarea salvată în Mat în 2, fiecare
cadru în ambele direcții la Mat în 3 și premiul exact de 10 puncte.

Proba nativă păstrează verificarea comenzilor la font mărit, deschiderea după
apărarea salvată, rotația tablei, pixelii pieselor și progresul după închiderea
explicației. Inclusiv când două mutări se termină pe același pătrat, poziția
pieselor separă apărarea de cadrul final.

- Pe Android 9, la 720 × 1280 logic și 320 dpi, screenshotul UI Automation poate decupa cadrul fizic de 1080 × 1920. Proba verifică în fereastră tabla și comenzile, apoi folosește cadrul complet și verifică pe PNG toate cele patru colțuri ale tablei.
- La 1080 × 1920 nativ folosește UI Automation. Ambele runde folosesc font 1,3×.
- Recepția replayDisplay înregistrează profilul și sursa capturii. [Testul de geometrie](../../atf-tests/src/test/java/ro/atf/chess/ScreenCaptureGeometryTest.java)

## Progresul problemelor

Verificați în JVM punctajul, navigarea, progresul salvat și geometria capitolelor. Păstrați probe native pentru tablă, comenzi și starea salvată la restart.

<details>
<summary>Suitele capitolelor, probe native și verificări de catalog</summary>

`AppLanguagePreferenceTest` verifică selecția implicită, persistența limbii
și scrierea respinsă. `MainMenuLayoutTest` fixează **Setări** în grupul Profil.
Proba `-PuzzleUxOnly` alege ambele limbi din selector, redeschide meniul și
verifică **Deschideri** după schimbare, cu partida, profilul și progresul păstrate.

- `QuestHintTest` acoperă pașii piesă → mutare → explicație, iar
  `PuzzleExplainLayoutTest` verifică structura explicației, comenzile de 48 dp
  și spațiul rezervat pentru **Pericol**. Becul, săgeata și cartea disting cele
  trei acțiuni pe ecran. Explicația pune titlul și ieșirea în antet. Mutarea și
  dovada se derulează sub tabla fixă. Probele `-PuzzleMateReplayOnly` și
  `-PuzzleDangerOnly` păstrează verificările native ale liniei și marcajului.

- Antetul celor șase capitole afișează punctele capitolului și seria globală de la două rezolvări consecutive. Săgețile și becul stau sub tablă.
- Meniul din antet afișează opțiunile în aceeași zonă, păstrând poziția și latura tablei. **Întoarce tabla** schimbă orientarea, iar **Meniu principal** păstrează problema, semimutarea și recompensele înainte de întoarcerea la ecranul principal.
- Comanda de sistem **Înapoi** închide întâi opțiunile. Din ecranul de joc revine la **Probleme**.
- Detaliile se pot derula. Zona rezervată păstrează un rând de stare și locul pentru opțiuni, astfel încât tabla să folosească toată lățimea când încape.

| Probă | Contract urmărit |
|---|---|
| `PuzzleChromeLayoutTest` | Antet pe două rânduri, scor și controale la spațiu restrâns. Săgețile și becul sunt centrate sub tabla pătrată, cu ținte de 48 dp. |
| Sanity Android cu `-PuzzleUxOnly` | Verifică toate cele șase capitole în română și engleză. Deschide opțiunile, întoarce tabla și revine cu progresul păstrat. Controlează geometria tablei și blocarea comenzilor la limite, în timpul unei replici sau înainte de rezolvare. |

- Rulați `-PuzzleUxOnly -PuzzleLanguage ro` pe 720 × 1280 la 320 dpi și `-PuzzleUxOnly -PuzzleLanguage en` pe 1080 × 1920 la 440 dpi, ambele la font 1,3×. Publicați cele șase puncte de control ale fiecărei rulări cu `AndroidSanityPublication#publishPuzzleUxSanity`.
- Extent leagă fiecare comparație și captură încorporată de recepția nativă. Identificatorii `puzzle_options`, `puzzle_flip_board`, `puzzle_main_menu`, `puzzle_points`, `puzzle_streak`, `quest_previous` și `quest_next` permit verificarea comenzilor prin arborele de accesibilitate.

`BoardPanelLayoutTest` verifică lățimea maximă a tablei de joc și de reluare
la patru lățimi, plus o fereastră scundă și text mărit. Proba Android
`-PuzzleDangerOnly` compară partida, puzzle-ul și explicația în aceeași fereastră.

- `QuestDangerTest` verifică matul pe poziția finală, capturile în ambele culori, recaptura care anulează câștigul, patul, piesa legată și en passant. `-PuzzleDangerOnly` verifică pe Android activarea la cerere, stingerea la navigare, motivul accesibil și păstrarea punctelor.
- Publicați recepția `puzzle-danger.txt` prin `AndroidSanityPublication#publishPuzzleDangerSanity`. Extent leagă aserțiunile și capturile de aceeași rulare și același APK.

- `QuestStoreTest` folosește store-ul de producție cu preferințe în memorie. Verifică reluarea cursorului după ID în **Tactică** și **Antrenament tactic**, rezolvarea fără puncte duplicate, seria comună și totalurile separate.
- Include preferințele moștenite și retry-ul după o scriere respinsă. `QuestProgressTest` verifică următoarea problemă nerezolvată după extinderea catalogului.

- `QuestCatalogTest` verifică 1.238 de poziții publice distincte și 24 de poziții independente. `TacticsCatalogTest` verifică dovada materială a celor 114 exerciții de Antrenament tactic și a lotului independent.
- `MainMenuLayoutTest` verifică titlurile și totalurile. `ChessComponentSuite` rulează fără emulator.
- `DocumentationGovernanceTest` și `ReleaseGovernanceTest` verifică linkurile locale, versiunea și secțiunea de descărcare.

La schimbarea catalogului material, rulați verificarea cheii și a amprentei pentru fiecare capitol afectat. Verificatorul reface și liniile publicate în `payoff-line`, inclusiv răspunsurile legale după ultima mutare. Amprenta continuă să urmărească cheia și rezultatul orizontului de trei semimutări.

```powershell
node scripts/verify-tactics.cjs $chessJs chess-core/src/main/resources/training.yaml chess-core/src/main/resources/training.sha256
node scripts/verify-tactics.cjs $chessJs atf-tests/src/test/resources/tactical-transfer-holdout.yaml atf-tests/src/test/resources/tactical-transfer-holdout.sha256
node scripts/verify-tactics.cjs $chessJs chess-core/src/main/resources/intermezzo.yaml chess-core/src/main/resources/intermezzo.sha256
```

</details>

## Suite JVM

| Intrare JUnit | Ce verifică |
|---|---|
| `ChessComponentSuite` | Regulile, pozițiile și problemele, progresul, partida, deschiderile, indiciile și geometria verificată în JVM. Include contractele de accesibilitate Web rulate prin Node.js. Rulează offline. |
| `ChessGovernanceSuite` | Inventarul suitelor, rezultatele și diagnosticul din Extent, legăturile locale din documentație, politica AI și calificarea release-ului. |
| `ChessBenchmarkSuite` | Stockfish, liga offline și ancora Elo. Fiecare probă pornește când primește intrările documentate. |
| `ChessJvmSuite` | Intrarea Maven implicită. Reunește cele trei selecții într-un raport. |

Din copia TMP, cu JDK 21, Maven și Node.js disponibil în PATH, rulați suita necesară.

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dchess.extent=target/jvm.html'
mvn test '-Dtest=ChessComponentSuite' '-Dchess.extent=target/components.html'
mvn test '-Dtest=ChessGovernanceSuite' '-Dchess.extent=target/governance.html'
```

- În IDE, porniți clasa suitei ca test JUnit. Pentru un singur caz, folosiți clasa sau metoda direct, de exemplu `-Dtest=ChessMatchTest#historyPreservesCastlingAndEnPassant`.
- Folosiți un fișier Extent nou la fiecare lansare. Pentru un raport cumulat, porniți `ChessJvmSuite`.
- [Intrările JUnit](../../atf-tests/src/test/java/ro/atf/chess/suites)

JUnit marchează probele de benchmark ca omise când lipsesc intrările lor. [Configurarea Stockfish, a ligii și a ancorei Elo](../board/ai-quality.md#control-la-fiecare-schimbare)

`AiLeagueBenchmarkTest` joacă cele 72 de partide ale ligii într-un pool de cel mult trei workeri și verifică separat replay-ul fiecărui rezultat; folosiți o cale TSV nouă în `-Dchess.league` și un Extent nou. Astfel, testul acoperă căutările simultane și păstrarea checkpoint-urilor fiecărui joc.

- `HardQuestTransferBenchmarkTest` rulează în `ChessBenchmarkSuite`. O probă cere soluția corectă pentru fiecare quest public și independent, folosind poziția și obiectivul.
- Cealaltă măsoară decizia adversarului Hard folosind numai poziția. Extent arată totalurile pe capitol, ratările și respectarea plafonului.
- Pragul de 39 la Antrenament tactic se aplică celor 48 de ID-uri din lotul înghețat. [Pragurile de regresie](../../atf-tests/src/test/resources/hard-quest-transfer.yaml)

- `SuiteGovernanceTest` compară planurile descoperite de JUnit. Fiecare metodă JVM cu audit aparține unei singure suite primare.
- Publicarea recepțiilor Android și TalkBack are intrări explicite. `DocumentationGovernanceTest` verifică fișierele și ancorele Markdown locale, inclusiv cele din feedback.

Auditul puzzle-urilor păstrează căutarea efectivă din aceeași apelare și distinge plafonul repertoriului de cel adaptiv, cu teste negative și praguri de precizie înghețate. [Contract și interpretare](hard-audit.md#auditul-puzzle-urilor-fără-obiectiv-transmis-adversarului)

## Schimbarea testelor față de o bază Git

- Comparați schimbarea cu SHA-ul complet al commitului revizuit. Folosiți un worktree Git din TMP, cu istoricul necesar.
- Verificarea citește separat HEAD, indexul și fișierele de lucru. O mutare apare ca ștergerea căii vechi și adăugarea căii noi.
- Se verifică și editările în interiorul unui test.

| Schimbare | Condiție |
|---|---|
| Adăugare independentă | Intră în inventarul și suita care îi corespund. |
| Editare a testului, resursei sau selectorului | Control cu baza, calea exactă, contractul și metoda ce îl verifică. |
| Ștergere sau mutare | Acord explicit de ștergere și test înlocuitor într-un fișier distinct. |
| Mutare din Android în JVM | Metoda trece în suita de componente și controlul identifică proba nativă păstrată. |

- În [registrul testChanges](test-changes.yaml), completați un control pentru fiecare cale existentă afectată. Indicați ID-ul, baza, calea sursă, calea înlocuitoare, clasa și metoda exactă, contractul păstrat și dreptul de ștergere.
- Când mutați o verificare nativă, adăugați calea și eticheta stabilă a verificării Android păstrate. Revizorul confirmă echivalența aserțiunilor și păstrează dovada Android pentru gesturi, randare, date Android sau restart real.
- Verificarea Git controlează selecția, execută metoda JVM și confirmă referința la controlul nativ.

```powershell
mvn test "-Dtest=TestChangeReview" "-Dchess.testChanges.base=$reviewBase" "-Dchess.extent=$testChangeExtent"
```

- Setați valoarea reviewBase la SHA-ul complet al strămoșului comun ales pentru revizuire și testChangeExtent la o cale HTML nouă în TMP. Testul arhivează decizia și scrie separat Extent pentru metodele executate.
- O bază nouă cere reconcilierea registrului. Scoateți intrarea când revizia de bază include deja schimbarea.
- Preflight-ul de distribuție acceptă recepția pentru un candidat Git curat și o bază distinctă. [Preflight](../release/README.md#calificare-și-publicare)

## Raportare Extent

- Un test JVM auditat care se încheie normal trebuie să evalueze cel puțin o aserțiune în **TEST BODY**. O comparație exclusiv în setup sau teardown produce FAIL în JUnit și în Extent.
- Eșecurile și întreruperile existente își păstrează diagnosticul. `ExtentGovernanceTest` verifică execuția directă și în suită.

În Extent, deschideți scenariul, **TEST BODY** și **Assertions** pentru a vedea valorile așteptate, valorile observate și rezultatul fiecărei comparații. Extensia măsoară execuția de la setup la teardown, apoi publică starea și diagnosticul JUnit. `ExtentGovernanceTest` verifică HTML-ul produs pentru PASS, FAIL, SKIP și pentru un caz fără comparații.

- Pentru cele două meniuri Android, fiecare checkpoint grupează acțiunea, comparația nativă și captura PNG încorporată. La reluarea unei partide, aserțiunea verifică poziția și evidențierea ultimei mutări în imagine.
- Verificați că aserțiunile se pot extinde și că amprentele corespund recepției, APK-ului, commitului și capturilor. O captură absentă, invalidă, substituită sau repetată între cele două ecrane produce FAIL cu diagnostic în HTML.
- `ExtentGovernanceTest` exercită publicarea offline. [Calificarea ediției](../release/README.md#calificare-și-publicare)

Pentru Analiza partidei, păstrați recepția nativă, identitatea APK-ului și raportul Extent. Verificați amprentele raportului și ale capturilor în dovezile ediției. [Calificarea ediției](../release/README.md#calificare-și-publicare)

- Captura UI Automation poate avea dimensiuni raster mai mici decât ecranul real al emulatorului. Comparațiile de pixeli proiectează dreptunghiurile din coordonate de ecran în coordonatele bitmapului folosind dimensiunile reale ale displayului; verificarea vizibilității elementelor rămâne bazată pe limitele native ale ferestrei. Astfel, redimensionarea capturii nu devine un fals eșec vizual.

## Sanity Android

Rezervați emulatorul pentru contractele care cer Android. La calificarea release-ului, corelați recepțiile native cu APK-ul candidat și cu dispozitivul.

<details>
<summary>Pregătire, recepții de fază și verificări negative</summary>

- Chess Lab acceptă API 26 și versiunile ulterioare. Jocul și lecțiile folosesc navigarea de sistem pentru API 26–32 și callback-ul platformei de la API 33.
- Proba de analiză trimite comanda Înapoi prin Android după mutarea Hard și verifică revenirea în meniu cu partida păstrată. Lint verifică API-urile și resursele din APK.

- Verificați separat compatibilitatea surselor împachetate în aplicație:
  rulați `gradlew.bat :app:lintDebug` din `android`, în TMP. Maven verifică
  motorul pe JDK 21; build-ul Gradle și lint răspund la întrebări diferite
  despre compilare și API-ul minim. Proba nativă confirmă apoi comportamentul.

Pentru calificare, folosiți un emulator rezervat și curat. Din copia TMP,
setați `ANDROID_HOME` către SDK 36, încărcați identitatea de semnare și
construiți APK-ul de distribuție și instrumentarea lui.
[Semnare și calificare](../release/README.md#calificare-și-publicare)

```powershell
Push-Location android
.\gradlew.bat -Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest
Pop-Location
./scripts/run-android-sanity.ps1 -Adb adb -Serial <serial> -LeaseFile <registru-rezervare> -Distribution -SourceCommit <sha>
```

Runnerul refuză o instalare existentă a pachetului de distribuție.
Folosiți registrul comun de rezervare pe dispozitivele partajate. Varianta
cu alt ID de aplicație rămâne o probă de dezvoltare și nu califică APK-ul.

1. Prima instrumentare verifică meniul, culoarea, cele două trepte ale becului și încadrarea în română la font 1,3×. Rezolvă două poziții în Tactică și trece la a doua poziție nerezolvată din Antrenament tactic. În Mat în 2 observă mutarea înainte de apărare și pauza de cel puțin 650 ms. În Mat în 3 închide tabla cu a doua apărare în așteptare.
2. Runnerul oprește procesul. A doua instrumentare verifică răspunsul restant, pragul de cinci rezolvări, poziția și punctele restaurate în Tactică și reluarea celei de-a doua probleme nerezolvate din Antrenament tactic.
3. Extent cere un ID comun de rulare și PID-uri distincte pentru cele cinci faze. Verifică cele 58 de perechi ID–etichetă din `prepare`, cele 26 de comparații din `restore` și recepțiile de partidă. Diagnosticul apare lângă comparația nativă.

La modificarea instrumentării, sincronizați inventarul `prepare` și numărul de aserțiuni pe fază cu `AndroidSanityPublication`. Pentru a regenera raportul, copiați dovezile native într-un dosar nou și rulați `-Dtest=AndroidSanityPublication#publishBoardSanity` cu `-Dchess.android.receipt=<dosar>/prepare.txt` și `-Dchess.android.runId=<runId>`. Citiți `runId` din `execution.json`.

`AndroidReceiptTest` verifică offline randarea celor 58 de aserțiuni și respingerea unei aserțiuni lipsă, a unui ID duplicat, a unei etichete substituite și a unei faze din altă rulare. Proba de reluare verifică și respingerea unei etichete străine pentru Antrenament tactic, cu numărul total păstrat.

</details>

## Upgrade al progresului

- Sanity-ul de distribuție pornește de la o instalare curată. Pentru proba separată de upgrade, porniți de la un emulator curat, verificați că cele două APK-uri au același certificat și instalați întâi ediția publicată.
- Creați progres, opriți procesul și instalați candidatul cu `adb install -r`. Verificați `versionCode`, timpul primei instalări, PID-urile și ID-urile problemelor, apoi comparați punctele, seria și apărarea rămasă în curs.

În componentă, `QuestStoreTest` verifică reordonarea și extinderea
capitolului cu același ID și punctaj, inclusiv refuzul acordării duble.
Restartul Android verifică restaurarea aceleiași instalări.
Doar instalarea peste o ediție existentă dovedește migrarea între APK-uri.

- La upgrade-ul de la 0.9.0 la 0.10.0, problema `mate-008` s-a redeschis la `23/500`, cu două rezolvări și 20 de puncte. În vechiul catalog avea rangul 3.
- Faza Android pentru restartul aceleiași versiuni verifică rangul 3 pentru datele pregătite în acea versiune. O probă de upgrade verifică ID-ul memorat și rangul lui din noul YAML.

Pe API 28, un profil de probă salvat de 0.9.0 a păstrat după upgrade 105 ID-uri rezolvate, 1.072 de puncte, seria de trei, încercarea asistată și mutarea finală salvată. La prima deschidere, Mat în 3 a executat a doua apărare restantă și a ținut **Următoarea** blocată până la mat.

- Verificați și un capitol de mat completat anterior. `mate-129`, `mate2-024nn` și `mate3-02YEp` rămân ultimele în capitolele lor, iar toate pozițiile noi din aceste capitole sunt înaintea lor.
- În APK-ul 0.10.0 publicat, Mat în 1 afișa `500/500` cu `102/500` rezolvate și **Următoarea** dezactivată. **Precedentă** deschidea problema de la `499/500`.

## Tabla de analiză dintr-o partidă

- `AnalysisLineTest` și workerul TeaVM citesc `contextual-analysis.json`. Cazurile fixează checkpointul, semimutarea vizibilă și orientarea pentru o partidă cu AI, o arhivă după abandon și o poziție de mat.
- Ele verifică ramificarea, respingerea unui cursor invalid și păstrarea sursei. Componenta cu mock-uri verifică revenirea la partidă și la declanșator, păstrarea marcajului studiului independent și ignorarea răspunsurilor întârziate după ieșire.
- [Contractele APK–Web](../../web/parity.md#contracte-și-probe)

Rulați din copia din TMP, cu `$site` din buildul Web.

```powershell
mvn -B -ntp -pl atf-tests -am test '-Dtest=AnalysisLineTest,ChessWebAnalysisStudyTest,ChessWebStudyShellTest#packagedWorkerSharesTheContextualAnalysisCasesWithTheJvm' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dchess.study.bundle=$site" "-Dchess.extent=$report"
```

În sanity, deschideți o partidă la o mutare din istoric, deschideți tabla de analiză și reveniți. Verificați cursorul, orientarea și focusul. Cazurile de legalitate și persistență rămân în componente. [Tabla de analiză pe Web](../../web/README.md#studiu)

## Deschideri

- `OpeningCatalogTest` verifică parcurgerea pentru ambele culori, pozițiile finale distincte și recunoașterea noilor linii în indexul ECO. `OpeningLessonTest` verifică o singură semimutare la fiecare apăsare, pauza replicii și rejucarea din istoric.
- `MatchAiGovernanceTest` verifică pozițiile de comparație cu Stockfish pentru fiecare lecție. În Engleză și Nimzo-indiană, pozițiile noi măsoară răspunsul negrului înainte de retragerea calului, respectiv de schimbul de pe c3.
- În Catalană și Alapin, comparația pornește cu albul la mutare după ultima replică din lecție.

- Pentru paritatea vizuală APK–Web, `OpeningLessonTest` verifică latura tablei, pragul pentru două coloane și încadrarea după măsurarea etichetelor. `ChessWebOpeningStudyTest` verifică treptele indiciului și contractul de afișare fără browser.
- În sanity, deschideți Italiana C54 cu albele la `0 / 12`, apoi dezvăluiți `e2–e4`. Repetați la font 1,3× pe profilul compact de 360 × 640 unități logice și pe cel orizontal de 1280 × 900.
- Laturile țintă sunt 288 și 592 de unități. Comparați poziția tablei înainte și după indiciu, conturul lui `e2`, textul complet și comenzile de minimum 48 de unități.
- În Extent consemnați densitatea și zona utilă de pe Android, viewportul Web și capturile pereche. [Lecțiile](../board/openings.md) · [Probele Studiu](../../web/README.md#studiu)

Pe Android, rulați sanity cu `-OpeningsOnly`. Proba joacă destinația indicată în Italiană și sugestia AI compatibilă cu lecția. Verifică mesajul pentru varianta spaniolă sugerată de AI, navigarea în ambele sensuri, rejucarea dintr-o poziție anterioară și tabla orientată pentru negru.

La două apăsări **Următoarea**, proba compară coordonatele și dimensiunile tablei și ale celulelor `a1` și `h8`: când apare **Continuă lecția** și când revine **Indiciu lecție**. Folosește fereastra emulatorului și două măsurători native compacte, de până la 320 × 480 dp și 280 × 440 dp. Extent cere ambele comparații chiar dacă numărul total de aserțiuni este corect.

```powershell
mvn test '-Dtest=AndroidSanityPublication#publishOpeningSanity' "-Dchess.android.receipt=$receipt"
```

Setați `$receipt` la `openings.txt` din dosarul TMP tipărit de runner. Raportul include aserțiunile native și cadrele pentru catalog, indiciu, analiză și rejucare.

Pentru APK-ul semnat, folosiți `-Distribution -IncludeOpenings -SourceCommit <sha>`. Proba folosește același APK instalat ca sanity-ul de bază și salvează recepția și capturile în subdirectorul `openings`.

## Partidă și istoric

Verificați în componente partidele, stocarea și analiza. Folosiți sanity-ul Android pentru restarturi de proces, navigarea nativă și dovezi din randare.

<details>
<summary>Responsabili, probe native și publicarea Extent</summary>

`ChessGameBotSessionTest` verifică profilul și culoarea după restaurare, proprietatea turei și respingerea unei replici duplicate. `ChessWebBotPublication` publică proba Edge la `/chess/` și compară mutarea livrată de worker cu JVM. [Sanity Web cu AI](../../web/README.md#partida-cu-ai)

`-ActiveMatchOnly` verifică poziția exactă după restart pe tura jucătorului. Publicați recepția `active-match-prepare.txt` cu `AndroidSanityPublication#publishActiveMatchSanity`.

- `-PendingBotOnly` oprește procesul în tura botului într-o partidă Advanced cu jucătorul la Negru. La relansare, proba verifică o singură replică legală după istoric, profilul și poziția.
- Publicați `pending-bot-prepare.txt` cu `AndroidSanityPublication#publishPendingBotSanity` și `-Dchess.android.receipt=$receipt`. Extent leagă capturile de APK, de rulare și de PID-urile distincte.

- `MatchControlsTest` verifică comenzile din meniu în partida activă, în
  istoric și după rezultat, inclusiv remiza doar la tura jucătorului și sensul
  bifei **Lasă AI-ul să mute**. `MatchToolbarResourcesTest` verifică antetul
  comun, țintele tactile, încadrarea textului și etichetele RO/EN.
  Proba nativă deschide meniul la font 1,3×, întoarce tabla păstrând aceleași
  coordonate și verifică poziția selectată în banda de mutări.

- `MatchStoreTest` rulează persistența de producție cu preferințe în memorie și ceas fix. Verifică listele disjuncte după migrare și restart, salvarea atomică la încheiere, reutilizarea locului arhivat și plafonul celor zece partide active.
- `MatchListEntryTest` respinge construirea unui rând pentru meniul greșit. `MatchArchiveTest` verifică și codec-ul analizei, plafonul și identitatea checkpoint-ului.

- `MatchListEntryTest` verifică datele afișate pentru partide în curs, partide încheiate, remize, renunțări și salvări vechi. `MainMenuLayoutTest` verifică textele în română și engleză, ierarhia dintre titlu, mutări și dată, contrastul etichetei și poziția butonului **Înapoi**.
- `MatchSlotsTest` verifică ordinea de înlocuire, controlul manual și respingerea replicilor AI vechi. Android păstrează un parcurs pe meniu, cu o captură a listei și o verificare a deschiderii partidei.

`ChessMatchTest` verifică reluarea din fiecare poziție a unei partide terminate, salvarea variantei noi, rocada, en passant și repetarea poziției. `GameReviewTest` verifică matul ratat, variantele comparate, transpoziția ECO, schimbarea categoriei la configurarea pragului, anularea și estimarea din perspectiva jucătorului. `GameReviewGovernanceTest` verifică valorile implicite, bugetul, reperele Elo și legalitatea liniilor importate. [Metodă, interacțiune și praguri](../board/game-review.md)

- Pentru geometrie, `GameReviewLayoutTest` verifică pătratul maxim, comenzile fixe și spațiul rămas explicației în profiluri portret compacte și late, inclusiv cu text mai mare. Același calcul poziționează tabla în teoria deschiderilor și în **De ce?**.
- `GameReviewGraphLayoutTest` verifică selecția din centrul fiecărui punct pe grafice înguste și partide lungi, rotunjirea între puncte, capetele și cazurile fără lățime utilă. `GameReviewTimelineTest` verifică alinierea cu partida, dispariția deschiderii după prima navigare, limitele cursorului, saltul la moment-cheie, parcurgerea variantei și revenirea la poziția jucată fără modificarea istoricului.

- Pentru integrarea nativă, rulați `run-android-sanity.ps1 -GameReviewOnly` după construirea variantei `sanity`. La calificarea APK-ului folosiți `-Distribution -GameReviewOnly -SourceCommit <sha>`.
- Prima fază are 23 de aserțiuni și cinci capturi, inclusiv selecția semimutării 184 din partida de 185. Fazele **De ce?** și reluare adaugă șapte aserțiuni și patru capturi.
- Ele verifică fereastra pe tot ecranul, tabla fixă în ambele linii și ambele evaluări cu adâncimea. **Înapoi** păstrează mutarea și zona vizibilă din istoric.
- Proba verifică și anularea, redeschiderea după oprirea procesului și continuarea separată cu arhiva intactă. Publicați `android-game-review.html` din `game-review.txt` cu `-Dtest=AndroidSanityPublication#publishGameReviewSanity` și `-Dchess.android.receipt=$receipt`.
- Păstrați recepțiile și capturile celor trei faze împreună. Extent verifică identificatorul comun de rulare, PID-urile distincte la reluare, identitatea APK-ului și a dispozitivului și amprenta fiecărei capturi.
- [Semnare și calificare](../release/README.md#calificare-și-publicare) · [Comportamentul review-ului](../board/game-review.md#navigarea-în-review-și-variantele-recomandate)

`GameReviewTimelineTest` acoperă matul permis și ratat, promovarea la damă și la turn, captura decisivă, schimbul compensat, orizontul inegal și liniile vechi invalide. `MatchStoreTest` verifică restaurarea marcajului după redeschiderea stocării, continuarea separată și invalidarea când se schimbă identitatea. `AndroidReceiptTest` respinge o captură substituită, o fază preluată din altă rulare și un restart raportat cu același PID.

`GameReviewTest` reconciliază tabelul cu mutările individuale, inclusiv zerourile și rolurile inversate când jucătorul are negrul. `MatchActionsTest` verifică refuzul, redeschiderea rezultatului salvat, mutarea din istoric și respingerea unei salvări care suprascrie un mat. [Fluxul jucătorului](../board/README.md#partidă-cu-calculatorul)

- Pentru analiza AI, rulați `-AnalysisOnly`. Prima fază pornește dintr-un joc salvat și verifică becul din istoric, două semimutări ale variantei, geometria tablei și revenirea la cursor fără schimbarea salvării.
- Faza din `analysis-restore/` verifică poziția și indiciul ascuns după restart. Sunt cinci aserțiuni înainte de restart și două după.
- `-IncludeManualAnalysis` rulează și tabla manuală cu același ID.

- `MatchHintTest` verifică ordinea variantelor, destinația jucabilă, promovarea exactă și prioritatea selecției manuale. Include un caz în care mutarea indicată aparține culorii care a mutat deja.
- `MatchEvaluationTest` verifică perspectiva albului, formatarea și proporțiile barei pentru avantaj, egalitate și final. `MatchSetupTest` verifică alegerile de culoare, adâncimile AI, salvările anterioare și mutarea de deschidere a motorului când omul joacă cu negrul.
- `PlayerProfileTest` verifică validarea numelui, iar `MainMenuLayoutTest` verifică gruparea și opțiunile celor două dropdownuri.

- `HintPreviewTest` verifică pașii motorului pe poziții reale, revenirea exactă, matul, schimbul compensat și respingerea unei linii ilegale. Proba din Siciliană urmărește `…Qxf2+ Kxf2` și verifică diferența față de start din perspectiva negrului.
- `MatchHintTest` acoperă una, două și trei linii și exclude o alternativă pozitivă din afara ferestrei de 25 cp. `HintComparisonTest` verifică ordinea comună a becului, dialogului și mutării la atingere în Siciliană, Caro–Kann și Catalană, pentru ambele culori.
- Extent consemnează mutările, orizontul comun și consumul sub plafonul configurat. Probele redau ambele linii, păstrează varianta la revenire și resping indiciul din alt cadru.
- Réti și Alehina verifică păstrarea mutării când inversarea nu este confirmată. Explicația verifică replica de mat, captura necompensată și diferența de scor.
- `AndroidReceiptTest` reproduce fără emulator PNG-ul substituit și faza străină, inclusiv când metadatele au fost actualizate. Extent păstrează rezultatele native, ID-ul, amprenta APK-ului și capturile confirmate de amprentele din instrumentare.

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dtest=AndroidSanityPublication#publishMatchAnalysisSanity' '-Dchess.android.receipt=<dosar>/analysis-prepare.txt'
```

`ChessMatchTest` verifică reconstrucția înainte și după replica botului, accesul la pozițiile din istoric, matul, rocada, en passant și repetarea. După fazele de probleme, runnerul pornește cu date proaspete și execută `match-prepare`, `match-restore` și `match-settled` în procese distincte, păstrând partida între ele. Verifică poziția inițială, săgețile, selecția directă, revenirea la ultima mutare, blocarea mutărilor în istoric și identitatea partidei salvate.

</details>

## Tablă de analiză

- `AnalysisLineTest` verifică navigarea, ramificarea, mutările speciale și exportul prefixului. Un cursor în istoric poate deschide analiza partidei sursă încheiate dacă linia întreagă îi păstrează mutările.
- O ramură diferită, o partidă în curs și un mat jucat manual folosesc analiza de studiu. `GameReviewTest` verifică analiza tuturor mutărilor și criteriile Elo.
- `MatchSlotsTest` verifică locul zece, refuzul la capacitate și respingerea răspunsurilor AI vechi. `MainMenuLayoutTest` acoperă intrarea localizată din meniu.

- Pentru Android folosiți `-ManualAnalysisOnly` sau `-IncludeManualAnalysis`.
  Proba deschide tabla din meniu, joacă `1.d4` în afara celor trei sugestii inițiale,
  confirmă refacerea listei, apoi schimbă două trasee cu aceeași primă mutare
  și parcurge Italiana până la ultima semimutare pe ecran complet, cu tabla fixă. Verifică explicația ultimei
  mutări, revenirea cu săgeata și închiderea previzualizării la cursorul inițial.
  După ieșirea din repertoriu verifică indiciul motorului. La revenirea în istoric,
  captura arată eticheta de teorie sub tabla fixă, iar ultima aserțiune confirmă
  că mutarea indicată nu creează o partidă salvată. `OpeningTheoryTest` acoperă
  cele trei trasee, toate mutările din YAML, transpozițiile, rocada și trecerea
  de la lecții la indexul ECO și la motor. Verifică Alapin și trecerea la motor, ramificarea în Franceză și sufixul Catalanei după transpoziție. Verifică și ruta `1.d4 Nf6 2.c4 g6`, unde noile poziții aduc alte trei trasee, apoi unul și refac lista la revenirea în istoric. Publicați `manual-analysis.txt`
  cu `-Dtest=AndroidSanityPublication#publishManualAnalysisSanity` și
  `-Dchess.android.receipt=$receipt`. Raportul are cinci aserțiuni și o captură
  legată de recepție. Proba confirmă în PNG toate cele patru colțuri ale tablei.
  Pe API 28, când dimensiunile logice și fizice diferă, captează cadrul fizic
  complet. În rularea combinată, publicatorul Extent confruntă și ID-ul
  fazei de joc din directorul părinte. [Utilizare](../board/README.md#tablă-de-analiză)

- `-MatchMenusOnly` rulează cele două meniuri pe aceeași instalare. Pentru probe separate de dezvoltare, folosiți `-MatchSlotsOnly` sau `-MatchArchiveOnly`.
- Publicați cu `AndroidSanityPublication#publishMatchSlotsSanity` sau `#publishMatchArchiveSanity` și recepția fazei. Extent leagă aserțiunea, captura încorporată și amprenta ei de același pas Android.
- Identitatea execuției include procesul, commitul și amprenta APK-ului pentru distribuție. [Partide salvate și încheiate](../board/README.md#partidă-cu-calculatorul)

## Redare comună pe ecran compact

Rulați `scripts/run-android-sanity.ps1 -Distribution -ReplayCompactOnly -SourceCommit <sha>` după build-ul semnat în TMP. Proba configurează profilul portret 720 × 1280, 320 dpi, font 1,3×, pornește aplicația în română și apoi în engleză, cu date de test independente, și restaurează profilul emulatorului la final.

În fiecare limbă, deschide un traseu din Teoria deschiderilor și ambele variante din **De ce?**, urmărește comenzile și textul, derulează explicația și revine la poziția de pornire. Verifică dreptunghiul tablei și al săgeților la schimbarea variantei și a pasului. Testele JVM păstrează acoperirea geometriei și a cursorului fără emulator.

- Publicați raportul cu `-Dtest=AndroidSanityPublication#publishReplayCompactSanity` și `-Dchess.android.receipt=<recepția RO>`. `android-replay-compact.html` include cele 12 aserțiuni și patru capturi.
- Raportul cere ambele limbi și același APK, commit, profil, dispozitiv și ID de rulare, cu procese distincte. [Calificarea ediției](../release/README.md#calificare-și-publicare)

## Proba TalkBack

1. Activați TalkBack pe emulator. În **Mat în 1**, deschideți **Indiciu**, apoi **Arată mutarea**. Atingeți culoarea la mutare și glisați de două ori spre dreapta prin intrarea tactilă a emulatorului. După fiecare gest, capturați evenimentul Android `TYPE_VIEW_ACCESSIBILITY_FOCUSED` și cadrul cu focusul. Repetați după **Întoarce tabla**. [Intrarea tactilă a emulatorului](https://developer.android.com/studio/run/emulator-console)
2. Pentru prima problemă din 0.3.1, traseul este negrul la mutare → regina neagră de pe d8 → h4, cu oricare dintre culori jos. Proba pe API 33 cu TalkBack 14.2 a confirmat cele patru glisări prin `EmulatorController/sendTouch`. [Extent-ul istoric](https://github.com/crististefanita/chess/blob/v0.4.0/release/0.3.1/reports/talkback-gestures-extent.html) rămâne în tagul Git, în afara retenției scurte a release-urilor.
3. `QuestHintTest` verifică proiecția în ambele orientări. Sanity-ul verifică actualizarea legăturilor native. Proba glisărilor are propriul raport și folosește evenimentele de focus ca dovadă.

Pentru republicare, rulați `TalkBackPublication#publishTalkBackGestures` din copia TMP și indicați logul prin `-Dchess.talkback.gestures`. Logul unei singure probe conține evenimentele `J7FocusProbe` și markerii `J7ProbeMarker`, de la `BLACK_ANCHOR_START` la `WHITE_DESTINATION_END`. În același director puneți `emulator.json`, capturile `BLACK-color.png`, `BLACK-PIECE.png`, `BLACK-DESTINATION.png` și echivalentele `WHITE-*`.

- Pentru vorbire, activați **Display speech output**. La a cincea rezolvare, comparați mesajul unic de mat și prag cu ce se anunță după întoarcere, revenirea din meniu și repornire.
- Proba anterioară compară ediția 0.3.0 cu build-ul corectat și consemnează cererile TalkBack și audio PCM sintetizat. [Extent-ul istoric](https://github.com/crististefanita/chess/blob/v0.3.1/docs/feedback/talkback-extent.html) este păstrat în tagul Git.

### Game Review

- Activați TalkBack și **Explore by touch** pe emulator. Rulați fazele native `talkback-review-ro` și `talkback-review-en` din copia TMP.
- Fiecare folosește aceeași partidă arhivată. Focalizați graficul, apoi mutarea selectată.
- Intrați în **De ce?** și reveniți prin **Înapoi**. Verificați focusul pe butonul de deschidere și semimutarea salvată.

Pe API 28, TalkBack 7.2 a sintetizat o dată pe limbă poziția 3 din 4 și scorul albului de 0 la sută. Proba a capturat audio și la mutarea selectată și la revenirea pe **De ce?**. `GameReviewGraphLayoutTest` verifică offline același punct al graficului și formatarea scorului. [Cititorul de ecran Web](../../web/README.md#cititorul-de-ecran-în-analiza-partidei)

- Pentru auditul vorbirii, rulați `TalkBackPublication#publishGameReviewTalkBack` cu `-Dchess.talkback.review` către directorul `run-<id>`. Pentru fiecare limbă, păstrați în același director recepția nativă, metadatele execuției și cele trei capturi.
- Jurnalul comun `talkback.log` conține limitele acțiunilor `J7ReviewProbe` și recepțiile `J7ReviewSpeech` cu text și audio PCM acceptat de observator pentru procesul TalkBack. Extent verifică identitatea, digesturile și anunțurile în fiecare interval.
- [Proba Web](../../web/README.md#cititorul-de-ecran-în-analiza-partidei)

[Tabla de joc](../board/README.md) · [Procesul de release](../release/README.md)

### Studii de puzzle cu Hard nativ

`HintCandidateCoverageTest` verifică cele patru rădăcini siciliene, scorurile brute, siguranța damei, contraexemplele, fallback-ul și bugetele mici. `StockfishFenAuditTest` verifică originea FEN, contoarele și respingerea proiecțiilor împrumutate. [Procedură, inventar și limite](hard-puzzle-study.md)
