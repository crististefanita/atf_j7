# Strategia de testare

Construiți în TMP dintr-o copie a sursei. Componentele rulează fără emulator. Android păstrează un singur parcurs scurt pentru gesturi, geometrie și randare.

## Motor și proiecție

`ChessComponentTest` verifică poziția inițială, orientarea și legalitatea, cu oracole independente. Cazurile includ numărul pozițiilor la primele trei mutări individuale, rocada, en passant, promovarea și matul.

`BoardPanelLayoutTest` verifică geometria panoului pentru ferestre înalte, scunde și etichete mai înalte. Dimensiunea tablei se calculează din spațiul rămas după măsurarea ambelor etichete.

`MainMenuLayoutTest` verifică ordinea celor patru capitole, textele românești și englezești, derularea, totalurile și zona de atingere a becului. `QuestStepTest` verifică piesele și culoarea la mutare înainte și după apărare, reluarea și blocarea navigării până la rezolvare. Acoperă și redarea unui mat alternativ din catalog, cu explicația lui, plus revenirea la linia de referință pentru date vechi sau invalide.

`QuestCatalogTest` enumeră fiecare mutare legală pentru toate cele 500 de probleme și cere o singură soluție de mat. O probă separă șahul cu apărare de mat. O altă probă verifică patul, rocada prin șah și captura en passant care ar expune propriul rege. Amprenta completă a mulțimilor de mutări se compară cu [`quest-oracle.yaml`](../../chess-core/src/main/resources/quest-oracle.yaml), produs de `chess.js` 1.4.0.

`QuestCatalogTest` recalculează din YAML ordinea strict crescătoare a problemelor Mat în 1 și caută poziții repetate în toate cele patru capitole. Cazurile negative acoperă un mat alternativ, o temă incorectă, un ID retras, o poziție apropiată cu aceeași soluție și copii obținute prin simetrie. `QuestProgressTest` verifică reluarea după reordonare și retragerea unei poziții fără creditarea înlocuitoarei.

`QuestHintTest` verifică pentru toate cele 500 de probleme că prima treaptă expune exclusiv plecarea, iar a doua destinația validată de motor. După apărarea adversarului, indiciul trebuie să folosească următoarea poziție. `QuestProgressTest` verifică pragurile după reluare și reordonare, idempotența rezolvărilor și finalul unui catalog de altă lungime.

`MateExplanationTest` confruntă pentru toate cele 500 de probleme piesele care dau șah și explicațiile câmpurilor de fugă cu amprenta produsă de chess.js. Probele pentru `mate-101` și `mate-102` folosesc pozițiile din catalog și verifică faptul că pornesc în șah, indiciile și fiecare apărare legală. O probă separată cere o apărare legală după un șah care lasă adversarului mutări.

```powershell
./scripts/update-mate-one-order.ps1 -ChessJs $chessJs
```

Setați `$chessJs` la fișierul CommonJS din pachetul extras chess.js 1.4.0. Verificatorul citește versiunea din `package.json`. Motorul independent rulează în TMP și nu intră în APK. [chess.js](https://github.com/jhlywa/chess.js/tree/v1.4.0) · [Regenerarea ordinii și a dovezii](../board/quest-curation.md#configurarea-capitolelor)

`MateInTwoCatalogTest` verifică exact 300 de poziții, absența matului imediat, o singură cheie, fiecare apărare legală și toate maturile din ramurile rezultate. Amprenta calculată de J7 trebuie să coincidă cu cea produsă independent de chess.js.

```powershell
node scripts/verify-mate-in-two.cjs <cale-catre-chess.js> chess-core/src/main/resources/mate-in-two.yaml chess-core/src/main/resources/mate-in-two.sha256
```

`MateInThreeCatalogTest` verifică 200 de poziții cu mat exact în trei, unicitatea primei mutări și toate continuările câștigătoare după prima apărare. Amprenta este comparată cu cea calculată de chess.js 1.4.0.

```powershell
node scripts/verify-mate-in-three.cjs <cale-catre-chess.js> chess-core/src/main/resources/mate-in-three.yaml chess-core/src/main/resources/mate-in-three.sha256
```

## Tactică și punctaj

`QuestRewardsTest` verifică seria între capitole, plafonul bonusului, restaurarea unei încercări asistate, punctajul anterior și totalurile. `TacticsCatalogTest` verifică cheia unică a fiecărei poziții, câștigul de material, lipsa matului imediat și blocarea comenzii **Următoarea** la o mutare greșită. YAML-ul indică proveniența celor 12 poziții introductive din partidele J7 și a celor 88 de importuri Lichess. Amprenta conține cheia și câștigul de material recalculate de chess.js.

```powershell
node scripts/verify-tactics.cjs <cale-catre-chess.js> chess-core/src/main/resources/tactics.yaml chess-core/src/main/resources/tactics.sha256
```

## Suite JVM

| Intrare JUnit | Ce verifică |
|---|---|
| `ChessComponentSuite` | Regulile, pozițiile și problemele, progresul, partida, deschiderile, indiciile și geometria verificată în JVM. Rulează offline. |
| `ChessGovernanceSuite` | Inventarul suitelor, rezultatele și diagnosticul din Extent, legăturile locale din documentație, politica AI și calificarea release-ului. |
| `ChessBenchmarkSuite` | Stockfish, liga offline și ancora Elo. Fiecare probă pornește când primește intrările documentate. |
| `ChessJvmSuite` | Intrarea Maven implicită. Reunește cele trei selecții într-un raport. |

Din copia TMP, cu JDK 21 și Maven:

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dchess.extent=target/jvm.html'
mvn test '-Dtest=ChessComponentSuite' '-Dchess.extent=target/components.html'
mvn test '-Dtest=ChessGovernanceSuite' '-Dchess.extent=target/governance.html'
```

În IDE, porniți clasa suitei ca test JUnit. Pentru un singur caz, folosiți clasa sau metoda direct, de exemplu `-Dtest=ChessMatchTest#historyPreservesCastlingAndEnPassant`. Folosiți un fișier Extent nou la fiecare lansare. Pentru un raport cumulat, porniți `ChessJvmSuite`. [Intrările JUnit](../../atf-tests/src/test/java/ro/atf/chess/suites)

JUnit marchează probele de benchmark ca omise când lipsesc intrările lor. [Configurarea Stockfish, a ligii și a ancorei Elo](../board/ai-quality.md#control-la-fiecare-schimbare)

`SuiteGovernanceTest` compară planurile descoperite de JUnit. Fiecare metodă JVM cu audit aparține unei singure suite primare. Publicarea recepțiilor Android și TalkBack are intrări explicite. `DocumentationGovernanceTest` verifică fișierele și ancorele Markdown locale, inclusiv cele din feedback.

## Raportare Extent

`MatchAiGovernanceTest` verifică limitele YAML, iar `MatchHintTest` verifică pragul, limita de cinci și un mat găsit la adâncime mai mare, inclusiv în partida configurată pe Basic. `MatchMaterialTest` acoperă capturile, en passant, promovarea și reluarea. Pentru comparația cu Stockfish 19, folosiți suita separată și același set de poziții la fiecare revizie. [Protocol și comenzi](../board/ai-quality.md)

`MatchSetupTest` acoperă decizia Hard în Franceză și Scandinavă în bugetul existent. `MatchEvaluationTest` verifică estimarea poziției, ordinea indiciilor și scorurile terminale. Benchmarkul cu Stockfish compară separat pierderea mutărilor Hard și eroarea numerică a barei, pe poziții suplimentare fixate în YAML. [Pozițiile de calibrare](../../atf-tests/src/test/resources/opening-calibration.yaml)

`AiOpeningRegressionTest` verifică repertoriul Pirc și Benoni, transpoziția, ieșirea Hard din ciclul Alehină și remiza disponibilă apărării. La semimutarea 72, compară alegerea Hard cu și fără istoricul aceleiași poziții și verifică bugetul. Proba Stockfish folosește toate mutările precedente ca să poată evalua repetiția. `AiLeagueComponentTest` verifică perechile cu culori inversate, transpozițiile duplicate, intervalul partidelor neterminate și finalurile elementare. Probele opționale scriu rejucarea UCI și raportul Extent. [Comenzile și interpretarea Elo](../board/ai-quality.md#liga-offline)

`TacticalAnalysisTest` compară o captură înainte și după recaptură, două câștiguri egale și un mat care devine vizibil abia la al treilea pas. Pozițiile din partida italiană verifică alegerea unei replici superioare primei mutări generate și evitarea unui mat imediat. Raportul de componente păstrează linia și câștigul așteptat ca aserțiuni.

În Extent, deschideți scenariul, **TEST BODY** și **Assertions** pentru a vedea valorile așteptate, valorile observate și rezultatul fiecărei comparații. Extensia măsoară execuția de la setup la teardown, apoi publică starea și diagnosticul JUnit. `ExtentGovernanceTest` verifică HTML-ul produs pentru PASS, FAIL, SKIP și pentru un caz fără comparații.

Pentru cele două meniuri Android, fiecare checkpoint grupează acțiunea, comparația nativă și captura PNG încorporată. La reluarea unei partide, aserțiunea verifică poziția și evidențierea ultimei mutări în imagine. Verificați că aserțiunile se pot extinde și că amprentele corespund recepției, APK-ului, commitului și capturilor. O captură absentă, invalidă, substituită sau repetată între cele două ecrane produce FAIL cu diagnostic în HTML. `ExtentGovernanceTest` exercită publicarea offline. [Calificarea ediției](../release/README.md#calificare-și-publicare)

## Sanity Android

Din aceeași copie TMP, setați `ANDROID_HOME` către SDK 36 și construiți `:app:assembleSanity :app:assembleSanityAndroidTest` din `android` cu `gradlew.bat`. Varianta `sanity` are identitate separată de APK-ul debug folosit pentru joc.

```powershell
./scripts/run-android-sanity.ps1 -Adb adb -Serial <serial> -LeaseFile <registru-rezervare>
```

Folosiți registrul comun de rezervare pe dispozitivele partajate.

1. Prima instrumentare verifică meniul, culoarea, cele două trepte ale becului și încadrarea în română la font 1,3×. În Tactică rezolvă două poziții și verifică flacăra, bonusul și totalurile. În Mat în 2 observă mutarea înainte de apărare și pauza de cel puțin 650 ms. În Mat în 3 închide tabla cu a doua apărare în așteptare.
2. Runnerul oprește procesul. A doua instrumentare verifică răspunsul restant, deblocarea comenzii **Următoarea**, pragul de cinci rezolvări și restaurarea poziției și punctelor în Tactică.
3. Extent cere PID-uri distincte, aserțiunile celor cinci faze și zece capturi PNG. Raportul include meniul, indiciile, flacăra, progresul, partida și istoricul. Diagnosticul unei faze eșuate apare în aserțiunea rezultatului nativ.

La modificarea instrumentării, sincronizați numărul de aserțiuni pe fază cu `AndroidSanityPublication`. Pentru a regenera raportul, copiați dovezile native într-un dosar nou și rulați `-Dtest=AndroidSanityPublication#publishBoardSanity` cu `-Dchess.android.receipt=<dosar>/prepare.txt`.

`AndroidReceiptTest` verifică offline importul recepției native. O fază trece cu un singur verdict PASS, un cod de succes, un PID valid și toate aserțiunile așteptate. Marcajele contradictorii și valorile neconcordante apar ca FAIL.

## Upgrade al progresului

Sanity-ul de distribuție pornește de la o instalare curată. Pentru a observa upgrade-ul, instalați pe emulator APK-ul anterior semnat, creați progres în capitole, opriți procesul și instalați APK-ul nou cu `adb install -r`. Verificați `versionCode`, timpul primei instalări, PID-urile și identitatea problemelor salvate. Comparați punctele, seria, rezolvările pe capitole și semimutarea unei apărări în curs.

La upgrade-ul de la 0.9.0 la 0.10.0, problema `mate-008` s-a redeschis la `23/500`, cu două rezolvări și 20 de puncte. În vechiul catalog avea rangul 3. Faza Android pentru restartul aceleiași versiuni verifică rangul 3 pentru datele pregătite în acea versiune. O probă de upgrade verifică ID-ul memorat și rangul lui din noul YAML.

Pe API 28, un profil de probă salvat de 0.9.0 a păstrat după upgrade 105 ID-uri rezolvate, 1.072 de puncte, seria de trei, încercarea asistată și mutarea finală salvată. La prima deschidere, Mat în 3 a executat a doua apărare restantă și a ținut **Următoarea** blocată până la mat.

Verificați și un capitol de mat completat anterior. `mate-129`, `mate2-024nn` și `mate3-02YEp` rămân ultimele în capitolele lor, iar toate pozițiile noi din aceste capitole sunt înaintea lor. În APK-ul 0.10.0 publicat, Mat în 1 afișa `500/500` cu `102/500` rezolvate și **Următoarea** dezactivată. **Precedentă** deschidea problema de la `499/500`.

## Deschideri

`OpeningCatalogTest` verifică liniile, alternarea culorilor și validarea catalogului. `OpeningLessonTest` verifică navigarea prin toate liniile cu ambele culori, o singură semimutare la fiecare apăsare, pauza replicii și rejucarea din istoric. `MatchAiGovernanceTest` verifică pozițiile de comparație cu Stockfish pentru fiecare lecție.

Pe Android, rulați sanity cu `-OpeningsOnly`. Proba joacă destinația indicată în Italiană și sugestia AI compatibilă cu lecția. Verifică mesajul pentru varianta spaniolă sugerată de AI, navigarea în ambele sensuri, rejucarea dintr-o poziție anterioară și tabla orientată pentru negru.

La două apăsări **Următoarea**, proba compară coordonatele și dimensiunile tablei și ale celulelor `a1` și `h8`: când apare **Continuă lecția** și când revine **Indiciu lecție**. Folosește fereastra emulatorului și două măsurători native compacte, de până la 320 × 480 dp și 280 × 440 dp. Extent cere ambele comparații chiar dacă numărul total de aserțiuni este corect.

```powershell
mvn test '-Dtest=AndroidSanityPublication#publishOpeningSanity' "-Dchess.android.receipt=$receipt"
```

Setați `$receipt` la `openings.txt` din dosarul TMP tipărit de runner. Raportul include aserțiunile native și cadrele pentru catalog, indiciu, analiză și rejucare.

Pentru APK-ul semnat, folosiți `-Distribution -IncludeOpenings -SourceCommit <sha>`. Proba folosește același APK instalat ca sanity-ul de bază și salvează recepția și capturile în subdirectorul `openings`.

## Partidă și istoric

`MatchStoreTest` rulează persistența de producție cu preferințe în memorie și ceas fix. Verifică migrarea, cele nouă salvări păstrate, ordinea la redeschidere, datele, rezultatul vechi păstrat până la final și eliminarea analizei odată cu rezultatul. `MatchArchiveTest` verifică și codec-ul analizei, plafonul și identitatea checkpoint-ului.

`MatchListEntryTest` verifică datele afișate pentru partide în curs, partide încheiate, remize, renunțări și salvări vechi. `MainMenuLayoutTest` verifică textele în română și engleză, ierarhia dintre titlu, mutări și dată, contrastul etichetei și poziția butonului **Înapoi**. `MatchSlotsTest` verifică ordinea de înlocuire, controlul manual și respingerea replicilor AI vechi. Android păstrează un parcurs pe meniu, cu o captură a listei și o verificare a deschiderii partidei.

`ChessMatchTest` verifică reluarea din fiecare poziție a unei partide terminate, salvarea variantei noi, rocada, en passant și repetarea poziției. `GameReviewTest` verifică matul ratat, variantele comparate, transpoziția ECO, schimbarea categoriei la configurarea pragului, anularea și estimarea din perspectiva jucătorului. `GameReviewGovernanceTest` verifică valorile implicite și respinge politici incomplete sau cu praguri incompatibile. Verifică și bugetul, reperele Elo și legalitatea liniilor importate. [Scheme și praguri](../board/game-review.md)

Pentru un parcurs Android dedicat, rulați `run-android-sanity.ps1 -GameReviewOnly` după construirea variantei `sanity`. Cele 15 verificări deschid analiza din meniul partidei încheiate și acoperă graficul complet, estimările Elo, comparația jucător–AI, reluarea, remiza și renunțarea. Graficul trebuie să apară după calcul. Pe API 28, proba a detectat apelul la `List.getFirst()` din motorul comun prin blocarea progresului la prima mutare. Extent cere recepția nativă și capturile graficului, tabelului și meniului partidei. Publicați raportul din `game-review.txt` cu `-Dtest=AndroidSanityPublication#publishGameReviewSanity` și `-Dchess.android.receipt=<cale>`. `GameReviewTest` reconciliază tabelul cu mutările individuale, inclusiv zerourile și rolurile inversate când jucătorul are negrul. `MatchActionsTest` verifică refuzul, redeschiderea rezultatului salvat, mutarea din istoric și respingerea unei salvări care suprascrie un mat. [Fluxul jucătorului](../board/README.md#partidă-cu-calculatorul)

Pentru analiza AI, rulați runnerul cu `-AnalysisOnly` pe varianta `sanity`. Prima fază are 17 aserțiuni. Verifică bifa, schimbarea variantei, salvarea mutării sugerate prin atingerea destinației și ascunderea indiciului după replică sau renunțare. În istoric verifică săgeata și păstrarea salvării la atingerea destinației. Măsoară tabla, colțurile `a1` și `h8` și zonele de atingere în fereastra reală și două profiluri compacte cu text mărit și interfață română. A doua fază verifică opțiunea și poziția după repornire. Dovezile se scriu în `execution.json` și în cele două rezultate de instrumentare.

`MatchHintTest` verifică ordinea variantelor, destinația jucabilă, promovarea exactă și prioritatea selecției manuale. Include un caz în care mutarea indicată aparține culorii care a mutat deja. `MatchEvaluationTest` verifică perspectiva albului, formatarea și proporțiile barei pentru avantaj, egalitate și final. `MatchSetupTest` verifică alegerile de culoare, adâncimile AI, salvările anterioare și mutarea de deschidere a motorului când omul joacă cu negrul. `PlayerProfileTest` verifică validarea numelui, iar `MainMenuLayoutTest` verifică gruparea și opțiunile celor două dropdownuri.

Proba Android verifică și editorul numelui, jocul cu negrul la Hard, crestătura nebunului și inversarea barei. Publicați aserțiunile și capturile celor două faze în `android-match-analysis.html` prin același adaptor Extent:

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dtest=AndroidSanityPublication#publishMatchAnalysisSanity' '-Dchess.android.receipt=<dosar>/analysis-prepare.txt'
```

`ChessMatchTest` verifică reconstrucția înainte și după replica botului, accesul la pozițiile din istoric, matul, rocada, en passant și repetarea. După fazele de probleme, runnerul pornește cu date proaspete și execută `match-prepare`, `match-restore` și `match-settled` în procese distincte, păstrând partida între ele. Verifică poziția inițială, săgețile, selecția directă, revenirea la ultima mutare, blocarea mutărilor în istoric și identitatea partidei salvate.

## Tablă de analiză

`AnalysisLineTest` verifică navigarea, ramificarea, mutările speciale și exportul prefixului. Un cursor în istoric poate deschide analiza partidei sursă încheiate dacă linia întreagă îi păstrează mutările. O ramură diferită, o partidă în curs și un mat jucat manual folosesc analiza de studiu. `GameReviewTest` verifică analiza tuturor mutărilor și criteriile Elo. `MatchSlotsTest` verifică locul zece, refuzul la capacitate și respingerea răspunsurilor AI vechi. `MainMenuLayoutTest` acoperă intrarea localizată din meniu.

Pentru parcursul Android folosiți `-ManualAnalysisOnly`. Proba joacă ambele culori și aplică indiciul negrului prin atingerea destinației într-o poziție deschisă din istoric. Verifică păstrarea partidei sursă, comutarea analizei, raportul complet și continuarea într-un loc nou. Verifică raportul partidei în curs și o singură replică atunci când se reactivează AI-ul. Publicați `manual-analysis.txt` cu `-Dtest=AndroidSanityPublication#publishManualAnalysisSanity` și `-Dchess.android.receipt=<cale>`. Raportul include 15 aserțiuni și captura integrată. [Utilizare](../board/README.md#tablă-de-analiză)

`-MatchMenusOnly` rulează cele două meniuri pe aceeași instalare. Pentru probe separate de dezvoltare, folosiți `-MatchSlotsOnly` sau `-MatchArchiveOnly`. Publicați cu `AndroidSanityPublication#publishMatchSlotsSanity` sau `#publishMatchArchiveSanity` și recepția fazei. Extent leagă aserțiunea, captura încorporată și amprenta ei de același pas Android. Identitatea execuției include procesul, commitul și amprenta APK-ului pentru distribuție. [Partide salvate și încheiate](../board/README.md#partidă-cu-calculatorul)

## Proba TalkBack

1. Activați TalkBack pe emulator. În **Mat în 1**, deschideți **Indiciu**, apoi **Arată mutarea**. Atingeți culoarea la mutare și glisați de două ori spre dreapta prin intrarea tactilă a emulatorului. După fiecare gest, capturați evenimentul Android `TYPE_VIEW_ACCESSIBILITY_FOCUSED` și cadrul cu focusul. Repetați după **Întoarce tabla**. [Intrarea tactilă a emulatorului](https://developer.android.com/studio/run/emulator-console)
2. Pentru prima problemă din 0.3.1, traseul este negrul la mutare → regina neagră de pe d8 → h4, cu oricare dintre culori jos. Proba pe API 33 cu TalkBack 14.2 a confirmat cele patru glisări prin `EmulatorController/sendTouch`. [Extent glisări](../../release/0.3.1/reports/talkback-gestures-extent.html)
3. `QuestHintTest` verifică proiecția în ambele orientări. Sanity-ul verifică actualizarea legăturilor native. Proba glisărilor are propriul raport și folosește evenimentele de focus ca dovadă.

Pentru republicare, rulați `TalkBackPublication#publishTalkBackGestures` din copia TMP și indicați logul prin `-Dchess.talkback.gestures`. Logul unei singure probe conține evenimentele `J7FocusProbe` și markerii `J7ProbeMarker`, de la `BLACK_ANCHOR_START` la `WHITE_DESTINATION_END`. În același director puneți `emulator.json`, capturile `BLACK-color.png`, `BLACK-PIECE.png`, `BLACK-DESTINATION.png` și echivalentele `WHITE-*`.

Pentru vorbire, activați **Display speech output**. La a cincea rezolvare, comparați mesajul unic de mat și prag cu ce se anunță după întoarcere, revenirea din meniu și repornire. Proba anterioară compară ediția 0.3.0 cu build-ul corectat și consemnează cererile TalkBack și audio PCM sintetizat. [Extent vorbire](../feedback/talkback-extent.html)

[Tabla de joc](../board/README.md) · [Procesul de release](../release/README.md)
