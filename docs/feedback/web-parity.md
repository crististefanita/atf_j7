# Web · Paritate cu APK-ul Chess Lab

## Pe scurt

Registrul urmărește diferențele rămase între APK și Web.
[Motor și adaptoare](../../web/README.md#motor-comun-și-adaptoare-de-platformă) ·
[Matricea APK–Web](../../web/parity.md)

## Ce preluăm din J4

Card Table Browser 0.2.0 și JAR-ul său de teste oferă contracte pentru
masa de cărți. O extracție J4/J7 are sens pentru un utilitar independent
de joc, dacă proba identifică un consumator în fiecare proiect, un API
stabil și codul duplicat care poate fi eliminat. Candidații sunt
transportul cu revizii și colectarea dovezilor din browser.
[Pachetele Card Table Browser 0.2.0](https://github.com/crististefanita/atf_j4/tree/3d2b7b11eedd6aadd48a82fd7858ef195e21f12e/atf-ui-browser/lib)

## WEB-01 · Motor și contract de aplicație

**P1 · Responsabil Chess.** Extrageți treptat tranzițiile de sesiune
care necesită decizii echivalente în `ChessActivity`, `AnalysisActivity`
și adaptorul Web. Începeți cu reluarea, anularea și primirea unei replici
AI întârziate. [Motor și proiecție](../testing/README.md#motor-și-proiecție)

**Verificări de închidere**

- Pe JVM și în worker, folosiți același caz cu ceas și stocare simulate.
  Verificați poziția, revizia, acțiunile permise și respingerea replicilor expirate.
- În sanity-ul nativ, verificați o singură reluare și ordinea afișată.
- Pe mobil, măsurați separat pornirea la rece și timpul workerului până la prima
  semimutare de analiză, cu același buget. Verificați anularea și schimbarea slotului în timpul calculului.

<details>
<summary>Anunțuri și focus la cititorul de ecran</summary>

**Game Review · Web.** NVDA 2026.3.1 a reprodus în RO dublarea poziției și a scorului în ediția anterioară. Graficul folosește acum o singură valoare accesibilă, cu poziție și scor. Componenta acoperă actualizările repetate, iar Extent respinge o recepție Web cu descrierea duplicată. Confirmați vorbirea pe build-ul corectat în RO și EN. Păstrați focusul pe grafic între două semimutări cu scoruri diferite și verificați revenirea la declanșator după **De ce?**. [Cititorul de ecran](../../web/README.md#cititorul-de-ecran-în-game-review)

</details>

## WEB-02 · Adaptoare și afișaj comun

<details>
<summary>Responsabilitatea adaptoarelor și intrările</summary>

**P1 · Responsabili J7 pentru șah și J4 pentru extracțiile comune.** Componentele existente `BoardPanelLayout`, `GameReviewLayout`, `GameReviewTimeline` și `OpeningReplay` oferă punctele de plecare. Android aplică dreptunghiurile și efectele în `View` și dialoguri. Web aplică aceleași decizii în DOM/CSS, citește zona utilă și traduce clickul, atingerea și tastatura în aceleași intenții. [Ghidul tablei](../board/README.md) · [Game Review](../board/game-review.md#navigarea-în-review-și-variantele-recomandate)

</details>

- Definiți un contract măsurabil pentru tabla pătrată, orientare, ordinea panourilor, zonele tactile, comenzile rezervate, explicația derulabilă, focalizarea și marcajele. Măsurați fereastra utilă a APK-ului după bare și insets și contrapartea Web după zoom și bara browserului. Porniți de la proba J7 de 720 × 1280, 320 dpi și font 1,3× în RO/EN. Comparați unități logice, poziția relativă, textul complet și pragurile de acces. Analizați separat diferențele de rasterizare. [Proba compactă J7](../testing/README.md#redare-comună-pe-ecran-compact) · [Măsurarea viewportului Web](https://www.w3.org/TR/cssom-view-1/)
- Pe desktop, folosiți un cadru centrat potrivit tablei și conținutului de șah. Pe mobil, redimensionarea, barele browserului sau mărirea textului trebuie să păstreze caseta selectată, pasul și accesul la comenzi. Verificați tastatura, ordinea de focus, închiderea dialogului și revenirea focusului. Pe Android verificați separat atingerea, Înapoi și etichetele citite de TalkBack.
**Criteriu de închidere.** Pentru aceeași linie în tabla de analiză și în Game Review, verificați tabla fixă, comenzi lizibile în RO/EN, explicația accesibilă și revenirea în aceeași stare pe APK și Web. [Alegerea nivelului de testare](../testing/README.md#alegerea-nivelului)

## WEB-03 · Componente și E2E pe contractul comun

<details>
<summary>Datele și oracolele cazurilor comune</summary>

**P1 · Responsabili J7 și ATF.** Folosiți aceleași fișiere de caz cu ID, poziție inițială FEN, mutări UCI, setări, intrări ordonate și rezultate așteptate după fiecare pas. Pentru repetare, rocada, en passant și reluare, fixture-ul include istoricul sau checkpointul necesar. Folosiți un buget în noduri și alegeri reproductibile pentru oracolul motorului. Măsurați separat latența reală. Comparați fiecare observație Android și Web cu așteptarea fixată din reguli și rezultate verificate. Pentru suite și dovezi, refolosiți `atf-test-kit` și `atf-reporting`, deja folosite în J7. [Bibliotecile ATF](../../lib/README.md)

</details>

| Nivel | Probă și verdict |
|---|---|
| Componentă JVM | Folosiți același contract pentru poziții, mutări legale, promovare, rezultate, salvare și refuzul unei revizii expirate. Exercitați erorile de stocare, checkpointul deteriorat, răspunsul workerului după navigare și progresul creditat o singură dată. |
| Contract pe artefactul Web | Rulați fixture-urile și prin workerul din pachetul generat. Comparați codurile de rezultat și starea după fiecare comandă cu așteptările JVM. Rulați aceeași probă fără rețea după încărcare. |
| Componente de afișaj | Partajați matricea de dimensiuni, reguli și nume accesibile. Proba locală a fiecărui adaptor verifică dreptunghiuri și comportament. Includeți atingere, tastatură, font mărit, rotație, explicație lungă și cazuri fără linie disponibilă. |
| E2E pe APK și browser | Refolosiți parcursul de produs cu drivere proprii pe APK-ul semnat și pe buildul Web real. Prima pereche verifică intrarea și ieșirea din Teorie, apoi redarea și redeschiderea lui **De ce?**. Extindeți cu salvare și reluare după repornirea aplicației sau reîncărcarea browserului și cu o probă Web pe telefon. |

<details>
<summary>Cazuri de referință și verdictul din browser</summary>

Primii martori sunt `1.e4 c5 2.c3 h5`, cu revenire din istoric la traseul Alapin, și `1.f3 e5 2.g4 Qh4#`, cu ambele variante pornite din aceeași poziție în Game Review. Înregistrați semimutarea exactă, poziția, motivul, acțiunile legale și starea reluată. Pentru completarea parității, matricea acoperă Jocul cu AI, problemele pe capitole, lecțiile, istoricul, importurile acceptate de produs, preferințele și recuperarea datelor. Un rezultat Node sau HTTP poate închide proba de contract. Verdictul pentru browser cere click, atingere sau tastatură prin driver și un rezultat observabil. [Teoria](../board/openings.md#indicii-de-teorie-pe-tabla-de-analiză) · [Raportare Extent](../testing/README.md#raportare-extent) · [Emularea în Playwright](https://playwright.dev/docs/emulation)

</details>

**Criteriu de închidere.** Perechea APK/Web rulează aceleași cazuri și anexează în Extent ID-ul cazului, așteptarea, observațiile pe fiecare adaptor, commitul, identitatea artefactului, limba, geometria și capturile. Când o platformă lipsește, cazul ei rămâne necalificat. Refolosiți proba Android semnată pentru pragurile native și exercitați cel puțin un profil de desktop și unul mobil în browser.

## WEB-04 · Calificare și publicare

**P2 · Calificarea ediției.** Legați în RAG commitul, cataloagele și amprentele APK și Web de recepțiile aceleiași ediții. [Criteriul de actualizare](../testing/README.md#actualizarea-pachetului-web)

<details>
<summary>Rutarea RAG după fișierul modificat</summary>

Adăugați o rută RAG explicită pentru geometria și analiza partidei când diff-ul atinge
[BoardPanel.java](../../android/app/src/main/java/ro/atf/chess/android/BoardPanel.java),
[study-board.mjs](../../web/src/main/web/study-board.mjs) sau [match-inline-analysis.mjs](../../web/src/main/web/match-inline-analysis.mjs).
Pentru fiecare fișier, un test de rutare trebuie să ceară citarea [MatchAnalysisParityTest](../../atf-tests/src/test/java/ro/atf/chess/MatchAnalysisParityTest.java)
și a dovezilor de interfață: sanity compact RO/EN la 130% pe APK-ul semnat și proba
Web în browser MCP pe pachetul servit. [Probele de analiză](../testing/README.md#analiza-în-partida-cu-ai)

</details>

**Criteriu de închidere.** Porniți proba din **Joacă în browser**, sub
APK în README, și confirmați manifestul pachetului servit. Verificați
la upgrade biblioteca de partide, o analiză arhivată și semimutarea de
reluare, inclusiv după reîncărcare și într-o filă deja deschisă.
[Upgrade Web](../../web/README.md#explicațiile-și-progresul-la-actualizare) ·
[Procesul de release](../release/README.md#calificare-și-publicare)
