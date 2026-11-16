# Web · Paritate cu APK-ul Chess Lab

J7 trebuie să poată juca și analiza în browser cu aceleași reguli, date și comenzi ca pe Android. Folosiți un checkout curat pentru calificarea ediției care va fi accesibilă sub linkul APK din README. Matricea inventariază funcțiile, adaptarea și dovezile necesare. [Matricea APK–Web](../../web/parity.md) · [Arhitectura J7](../../README.md#arhitectura-j7)

## Ce preluăm din J4

| Observație la revizia J4 `f3568e8` | Decizie pentru J7 |
|---|---|
| Același motor Java este folosit printr-un server JVM local și, pe GitHub Pages, printr-un modul compilat cu TeaVM care rulează într-un Web Worker. Workerul servește comenzi fără API de joc după încărcare. | Folosiți aceeași separare între motor, transport și afișaj. Pentru ediția Web autonomă, proba trebuie să cuprindă motorul J7 compilat, datele și stocarea lui, cu rețeaua oprită după încărcare. |
| Card Table Browser 0.2.0 publică serverul Java, contractele și DOM-ul mesei. J5 și J6 folosesc același binar, fixat prin SHA-256. `BrowserServer` servește `BrowserGame`, iar reviziile și starea sunt legate de `BrowserSession` pentru cărți, licitații și levate. | J7 păstrează contractele, regulile și proiecția de șah. Candidatul pentru reutilizare este transportul versionat, cu tipuri independente de masa de cărți. |
| JAR-ul de teste 0.2.0 exercită HTTP, profil mobil, comenzi și revizii. Driverul selectează elemente ale mesei de cărți. | Comparați fixture-urile și dovezile. Evaluați reutilizarea pornirii browserului și a capturilor când acestea au o intrare independentă de DOM-ul Whist. |
| J4 a publicat jocul în browser și are probe separate pentru motorul compilat și pentru browser. Comparația de afișaj și interacțiune cu APK-ul pe același telefon rămâne deschisă în feedback. | Folosiți J4 ca dovadă de fezabilitate și ca reper pentru publicare. Calificați compoziția, accesibilitatea și rezultatele J7 pe ambele suprafețe înainte de a declara paritatea. |

O probă independentă a deschis ediția publică Whist în Edge la 414 × 896 CSS px. După oprirea rețelei, a pornit o partidă românească și a acceptat prima licitație prin atingere. Aceasta acoperă continuitatea după încărcare. Pornirea offline dintr-un profil gol și echivalența cu APK-ul cer probe proprii. [Whist online](https://crististefanita.github.io/atf_j4/)

[Pachetele Card Table Browser 0.2.0](https://github.com/crististefanita/atf_j4/tree/3d2b7b11eedd6aadd48a82fd7858ef195e21f12e/atf-ui-browser/lib) · [Contractul `BrowserGame`](https://github.com/crististefanita/atf_j4/blob/3d2b7b11eedd6aadd48a82fd7858ef195e21f12e/atf-ui-browser/src/main/java/ro/cardtable/browser/BrowserGame.java)

## WEB-01 · Motor și contract de aplicație

**P1 · Responsabil J7.** Web și Android folosesc același `MatchStore`. Analiza și poziția de reluare aparțin fiecărui rezultat arhivat. Pe Web, continuarea dintr-o poziție istorică rămâne disponibilă pe tabla partidei. [Build și probe Web](../../web/README.md)

1. Declarați un modul Java `chess-core` cu publicare reproductibilă, resursele catalogului, dependențele tranzitive și proveniența lor. Nucleul compilează ca Java 17. Calificați și API-urile folosite la execuție pe Android cu `minSdk 26`. Verificați consumul din Gradle și din testele JVM, inventarul de resurse și SHA-ul intrării compilatorului Web.
2. Extrageți gradual deciziile încă orchestrate de `ChessActivity` și `AnalysisActivity`. Același contract trebuie să accepte intenții tipizate precum mutare, promovare, pas prin istoric, alegere de linie și reluare. Returnați poziția, cursorul, acțiunile disponibile, starea marcajelor, rezultatul comenzii și versiunea stării. Proiecția conține doar datele necesare afișării, cu chei de localizare stabile și parametri tipizați.
3. Expuneți **Continuă de aici** în Game Review, la semimutarea selectată în istoricul real. Dintr-o ramură de analiză cereți revenirea la poziția jucată înainte de confirmare. Verificați slotul nou, arhiva și bookmarkul păstrate, refuzul când lista este plină și replica unică atunci când AI este activ.
4. Extindeți refuzul răspunsurilor expirate la continuarea din istoric, schimbarea jocului și eroarea de scriere. Măsurați separat încărcarea workerului și timpul până la prima semimutare. O probă Edge a expirat înaintea afișării primei analize neterminate, iar reluarea pe același pachet a trecut. [Restricțiile TeaVM](https://teavm.org/docs/intro/overview.html)

**Criteriu de închidere.** Același caz J7 pornește din JAR-ul JVM, trece prin intrarea Android și prin motorul compilat pentru browser, apoi produce aceeași succesiune de stări și aceeași restaurare. Măsurați mărimea pachetului, pornirea la rece, timpul căutării la buget identic și întreruperea pe profil mobil înainte de alegerea pragurilor de livrare. [Motor și proiecție](../testing/README.md#motor-și-proiecție)

## WEB-02 · Adaptoare și afișaj comun

**P1 · Responsabili J7 pentru șah și J4 pentru extracțiile comune.** Componentele existente `BoardPanelLayout`, `GameReviewLayout`, `GameReviewTimeline` și `OpeningReplay` oferă punctele de plecare. Android aplică dreptunghiurile și efectele în `View` și dialoguri. Web aplică aceleași decizii în DOM/CSS, citește zona utilă și traduce clickul, atingerea și tastatura în aceleași intenții. [Ghidul tablei](../board/README.md) · [Game Review](../board/game-review.md#navigarea-în-review-și-variantele-recomandate)

- Definiți un contract măsurabil pentru tabla pătrată, orientare, ordinea panourilor, zonele tactile, comenzile rezervate, explicația derulabilă, focalizarea și marcajele. Măsurați fereastra utilă a APK-ului după bare și insets și contrapartea Web după zoom și bara browserului. Porniți de la proba J7 de 720 × 1280, 320 dpi și font 1,3× în RO/EN. Comparați unități logice, poziția relativă, textul complet și pragurile de acces. Analizați separat diferențele de rasterizare. [Proba compactă J7](../testing/README.md#redare-comună-pe-ecran-compact) · [Măsurarea viewportului Web](https://www.w3.org/TR/cssom-view-1/)
- Pe desktop, folosiți un cadru centrat potrivit tablei și conținutului de șah. Pe mobil, redimensionarea, barele browserului sau mărirea textului trebuie să păstreze caseta selectată, pasul și accesul la comenzi. Verificați tastatura, ordinea de focus, închiderea dialogului și revenirea focusului. Pe Android verificați separat atingerea, Înapoi și etichetele citite de TalkBack.
- Faceți proba de extracție J4/J7 cu un singur utilitar generic, de exemplu transportul versionat sau pornirea browserului cu colectarea probelor. Cereți doi consumatori, API explicit, teste de contract și dependențe independente de tipurile jocului și de Android. Pentru un JAR publicat, fixați commitul de build, inventarul API și al resurselor, SHA-256 și un test pe binarul exact.

**Criteriu de închidere.** Primul flux verificabil este aceeași redare pe tabla de analiză și în Game Review. Demonstrați tabla fixă, comenzi lizibile în RO/EN, explicația accesibilă și revenirea în aceeași stare pe APK și Web. Adăugați un JAR comun separat când proba arată ce cod elimină efectiv din ambii consumatori. [Alegerea nivelului de testare](../testing/README.md#alegerea-nivelului)

## WEB-03 · Componente și E2E pe contractul comun

**P1 · Responsabili J7 și ATF.** Folosiți aceleași fișiere de caz cu ID, poziție inițială FEN, mutări UCI, setări, intrări ordonate și rezultate așteptate după fiecare pas. Pentru repetare, rocada, en passant și reluare, fixture-ul include istoricul sau checkpointul necesar. Folosiți un buget în noduri și alegeri reproductibile pentru oracolul motorului. Măsurați separat latența reală. Comparați fiecare observație Android și Web cu așteptarea fixată din reguli și rezultate verificate. Pentru suite și dovezi, refolosiți `atf-test-kit` și `atf-reporting`, deja folosite în J7. [Bibliotecile ATF](../../lib/README.md)

| Nivel | Probă și verdict |
|---|---|
| Componentă JVM | Folosiți același contract pentru poziții, mutări legale, promovare, rezultate, salvare și refuzul unei revizii expirate. Exercitați erorile de stocare, checkpointul deteriorat, răspunsul workerului după navigare și progresul creditat o singură dată. |
| Contract pe artefactul Web | Rulați fixture-urile și prin workerul din pachetul generat. Comparați codurile de rezultat și starea după fiecare comandă cu așteptările JVM. Rulați aceeași probă fără rețea după încărcare. |
| Componente de afișaj | Partajați matricea de dimensiuni, reguli și nume accesibile. Proba locală a fiecărui adaptor verifică dreptunghiuri și comportament. Includeți atingere, tastatură, font mărit, rotație, explicație lungă și cazuri fără linie disponibilă. |
| E2E pe APK și browser | Refolosiți parcursul de produs cu drivere proprii pe APK-ul semnat și pe buildul Web real. Prima pereche verifică intrarea și ieșirea din Teorie, apoi redarea și redeschiderea lui **De ce?**. Extindeți cu salvare și reluare după repornirea aplicației sau reîncărcarea browserului și cu o probă Web pe telefon. |

Primii martori sunt `1.e4 c5 2.c3 h5`, cu revenire din istoric la traseul Alapin, și `1.f3 e5 2.g4 Qh4#`, cu ambele variante pornite din aceeași poziție în Game Review. Înregistrați semimutarea exactă, poziția, motivul, acțiunile legale și starea reluată. Pentru completarea parității, matricea acoperă Jocul cu AI, problemele pe capitole, lecțiile, istoricul, importurile acceptate de produs, preferințele și recuperarea datelor. Un rezultat Node sau HTTP poate închide proba de contract. Verdictul pentru browser cere click, atingere sau tastatură prin driver și un rezultat observabil. [Teoria](../board/openings.md#indicii-de-teorie-pe-tabla-de-analiză) · [Raportare Extent](../testing/README.md#raportare-extent) · [Emularea în Playwright](https://playwright.dev/docs/emulation)

**Criteriu de închidere.** Perechea APK/Web rulează aceleași cazuri și anexează în Extent ID-ul cazului, așteptarea, observațiile pe fiecare adaptor, commitul, identitatea artefactului, limba, geometria și capturile. Când o platformă lipsește, cazul ei rămâne necalificat. Refolosiți proba Android semnată pentru pragurile native și exercitați cel puțin un profil de desktop și unul mobil în browser.

## WEB-04 · Calificare și publicare

**P2 · Responsabili J7 și release.** J7 servește previzualizarea Web statică din `gh-pages`, după modelul J4. Proba la adresa publică verifică fișierele servite și reluarea într-un profil nou. Includeți în RAG actualizarea Web și legați commitul, cataloagele și SHA-urile APK și Web de recepții și revizia publicată. Pentru ediția 1:1, verificați recuperarea stocării la upgrade și aceeași versiune a contractului pe ambele platforme.

**Criteriu de închidere.** După verificarea URL-ului public, linkul **Joacă online · previzualizare** apare sub APK. Pentru a elimina statutul de previzualizare cereți toate funcțiile din matrice, cu dovezi pe Android și Web pentru aceeași ediție. Păstrați dovezile în dosarul ediției. [Procesul de release](../release/README.md#calificare-și-publicare) · [Publicarea GitHub Pages](https://docs.github.com/en/pages/getting-started-with-github-pages/configuring-a-publishing-source-for-your-github-pages-site)
