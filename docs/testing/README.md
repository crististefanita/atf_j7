# Strategia de testare

Construiți în TMP dintr-o copie a sursei. Suitele JVM verifică motorul și
guvernanța fără emulator. Selectați proba Android care acoperă gesturile,
geometria, randarea sau reluarea afectată de schimbare.

## Alegerea nivelului

- **Componentă** pentru reguli, progres, persistență, ordonare și layout măsurabil. Folosiți store-ul real cu preferințe în memorie și aserțiuni pe valori observabile.
- **Guvernanță** pentru reguli de structură, proveniență, contracte de raportare și publicare. Includeți o probă negativă când un artefact incomplet ar putea primi PASS.
- **Sanity Android** când comportamentul depinde de Activity, preferințele Android, procese, randarea nativă sau accesibilitate. Păstrați doar parcursul care validează integrarea lipsă din componente.
- **Benchmark** numai pentru comparații cu Stockfish, calibrarea boților sau măsurători de timp. Țineți intrările fixe și raportați separat rezultatele opționale.

Alegeți cel mai mic nivel care observă contractul. Verificați regulile în
JVM, iar integrarea nativă și restartul real pe Android. În Extent, urmăriți
aserțiunea, diagnosticul și, unde este necesară, captura checkpointului.

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

`QuestRewardsTest` verifică seria între capitole, plafonul bonusului, restaurarea unei încercări asistate, punctajul anterior și totalurile. `TacticsCatalogTest` verifică cheia unică, lipsa matului imediat, continuitatea ID-urilor și câștigul care rezistă răspunsurilor legale la capătul liniei. `QuestPayoffTest` verifică reluarea etapelor, capturile, finalul alternativ și restaurarea soluțiilor vechi. YAML-ul indică proveniența pozițiilor. Amprenta conține cheia și câștigul de material recalculate de chess.js.

`QuestDefenseStudyTest` verifică toate studiile din [catalogul explicațiilor](../../chess-core/src/main/resources/quest-defense-studies.yaml). Controlează cheia, replica legală, limba, bilanțul de pe tablă și compatibilitatea cu progresul salvat. Proba `measuresHardOnReviewedDefensesWithoutGivingItTheLesson` măsoară cheile și mutările ulterioare separat. La fiecare poziție fixată, motorul primește numai tabla și profilul Hard. Concordanța cu linia de studiu rămâne o măsurătoare, iar legalitatea și plafonul sunt aserțiuni.

Pentru explicațiile din Tactică, rulați `run-android-sanity.ps1 -TacticsExplanationOnly` cu parametrizarea ADB din [Sanity Android](#sanity-android). Proba deschide din tablă reluarea normală pentru `tactic-002` și studiul cu replica `…Kg8` pentru `tactic-043`. Verifică navigarea, materialul afișat și păstrarea punctelor după închidere.

Publicați `android-tactics-explanation.html` cu `-Dtest=AndroidSanityPublication#publishTacticsExplanationSanity` și `-Dchess.android.receipt=<dosar>/tactics-explanation.txt`. Extent include capturile și verifică ID-ul de rulare, recepția și amprentele PNG raportate de instrumentare.

Pentru ultima mutare din `training-069`, rulați `-TrainingFinishOnly`. `QuestPayoffTest` verifică obiectivul imediat, linia legală după `Rd8+` și punctajul unic. Sanity verifică accesul la explicație după șah, reluarea pe tablă, păstrarea progresului și redeschiderea explicației după rezolvare. Publicați raportul cu `-Dtest=AndroidSanityPublication#publishTrainingFinishSanity` și recepția `training-finish.txt`.

```powershell
node scripts/verify-tactics.cjs <cale-catre-chess.js> chess-core/src/main/resources/tactics.yaml chess-core/src/main/resources/tactics.sha256
```

## Progresul problemelor

`BoardPanelLayoutTest` verifică lățimea maximă a tablei de joc și de reluare
la patru lățimi, plus o fereastră scundă și text mărit. Proba Android
`-PuzzleDangerOnly` compară partida, puzzle-ul și explicația în aceeași fereastră.

`QuestDangerTest` verifică matul pe poziția finală, capturile în ambele culori,
recaptura care anulează câștigul, patul, piesa legată și en passant.
`-PuzzleDangerOnly` verifică pe Android activarea la cerere, stingerea la navigare,
motivul accesibil și păstrarea punctelor. Publicați recepția `puzzle-danger.txt`
prin `AndroidSanityPublication#publishPuzzleDangerSanity`. Extent leagă aserțiunile
și capturile de aceeași rulare și același APK.

`QuestStoreTest` folosește store-ul de producție cu preferințe în memorie. Verifică reluarea cursorului după ID în **Tactică** și **Antrenament tactic**, rezolvarea fără puncte duplicate, seria comună și totalurile separate. Include preferințele moștenite și retry-ul după o scriere respinsă. `QuestProgressTest` verifică următoarea problemă nerezolvată după extinderea catalogului.

`QuestCatalogTest` verifică 1.238 de poziții publice distincte și 24 de poziții independente. `TacticsCatalogTest` verifică dovada materială a celor 114 exerciții de Antrenament tactic și a lotului independent. `MainMenuLayoutTest` verifică titlurile și totalurile. `ChessComponentSuite` rulează fără emulator. `DocumentationGovernanceTest` și `ReleaseGovernanceTest` verifică linkurile locale, versiunea și secțiunea de descărcare.

La schimbarea catalogului material, rulați verificarea cheii și a amprentei pentru fiecare capitol afectat. Verificatorul reface și liniile publicate în `payoff-line`, inclusiv răspunsurile legale după ultima mutare. Amprenta continuă să urmărească cheia și rezultatul orizontului de trei semimutări.

```powershell
node scripts/verify-tactics.cjs <cale-catre-chess.js> chess-core/src/main/resources/training.yaml chess-core/src/main/resources/training.sha256
node scripts/verify-tactics.cjs <cale-catre-chess.js> atf-tests/src/test/resources/tactical-transfer-holdout.yaml atf-tests/src/test/resources/tactical-transfer-holdout.sha256
node scripts/verify-tactics.cjs <cale-catre-chess.js> chess-core/src/main/resources/intermezzo.yaml chess-core/src/main/resources/intermezzo.sha256
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

`AiLeagueBenchmarkTest` joacă cele 72 de partide ale ligii într-un pool de cel mult trei workeri și verifică separat replay-ul fiecărui rezultat; folosiți o cale TSV nouă în `-Dchess.league` și un Extent nou. Astfel, testul acoperă căutările simultane și păstrarea checkpoint-urilor fiecărui joc.

`HardQuestTransferBenchmarkTest` rulează în `ChessBenchmarkSuite`. O probă cere soluția corectă pentru fiecare quest public și independent, folosind poziția și obiectivul. Cealaltă măsoară decizia adversarului Hard folosind numai poziția. Extent arată totalurile pe capitol, ratările și respectarea plafonului. Pragul de 39 la Antrenament tactic se aplică celor 48 de ID-uri din lotul înghețat. [Pragurile de regresie](../../atf-tests/src/test/resources/hard-quest-transfer.yaml)

`SuiteGovernanceTest` compară planurile descoperite de JUnit. Fiecare metodă JVM cu audit aparține unei singure suite primare. Publicarea recepțiilor Android și TalkBack are intrări explicite. `DocumentationGovernanceTest` verifică fișierele și ancorele Markdown locale, inclusiv cele din feedback.

## Raportare Extent

Un test JVM auditat care se încheie normal trebuie să evalueze cel puțin o
aserțiune în **TEST BODY**. O comparație exclusiv în setup sau teardown
produce FAIL în JUnit și în Extent. Eșecurile și întreruperile existente
își păstrează diagnosticul. `ExtentGovernanceTest` verifică execuția directă
și în suită.

În Extent, deschideți scenariul, **TEST BODY** și **Assertions** pentru a vedea valorile așteptate, valorile observate și rezultatul fiecărei comparații. Extensia măsoară execuția de la setup la teardown, apoi publică starea și diagnosticul JUnit. `ExtentGovernanceTest` verifică HTML-ul produs pentru PASS, FAIL, SKIP și pentru un caz fără comparații.

Pentru cele două meniuri Android, fiecare checkpoint grupează acțiunea, comparația nativă și captura PNG încorporată. La reluarea unei partide, aserțiunea verifică poziția și evidențierea ultimei mutări în imagine. Verificați că aserțiunile se pot extinde și că amprentele corespund recepției, APK-ului, commitului și capturilor. O captură absentă, invalidă, substituită sau repetată între cele două ecrane produce FAIL cu diagnostic în HTML. `ExtentGovernanceTest` exercită publicarea offline. [Calificarea ediției](../release/README.md#calificare-și-publicare)

Pentru Analiza partidei, păstrați recepția nativă, identitatea APK-ului și raportul Extent. Verificați amprentele raportului și ale capturilor în dovezile ediției. [Calificarea ediției](../release/README.md#calificare-și-publicare)

Captura UI Automation poate avea dimensiuni raster mai mici decât ecranul real al emulatorului. Comparațiile de pixeli proiectează dreptunghiurile din coordonate de ecran în coordonatele bitmapului folosind dimensiunile reale ale displayului; verificarea vizibilității elementelor rămâne bazată pe limitele native ale ferestrei. Astfel, redimensionarea capturii nu devine un fals eșec vizual.

## Sanity Android

J7 păstrează API minim 26. Jocul și lecțiile folosesc navigarea de sistem
pentru API 26–32 și callback-ul platformei de la API 33. Proba de analiză
trimite comanda Înapoi prin Android după mutarea Hard și verifică revenirea
în meniu cu partida păstrată. Lint verifică API-urile și resursele din APK.

Verificați separat compatibilitatea surselor împachetate în aplicație:
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
3. Extent cere PID-uri distincte, 46 de comparații la pregătire, 26 la reluare și recepțiile celorlalte faze. Raportul include meniul, indiciile, flacăra, progresul, partida și istoricul. Diagnosticul unei faze eșuate apare lângă comparația nativă.

La modificarea instrumentării, sincronizați numărul de aserțiuni pe fază cu `AndroidSanityPublication`. Pentru a regenera raportul, copiați dovezile native într-un dosar nou și rulați `-Dtest=AndroidSanityPublication#publishBoardSanity` cu `-Dchess.android.receipt=<dosar>/prepare.txt`.

`AndroidReceiptTest` verifică offline importul și randarea HTML a recepției native. Testul negativ înlocuiește comparația obligatorie pentru Antrenament tactic cu una fără legătură, păstrând numărul total corect; Extent trebuie să marcheze faza FAIL și să numească verificarea lipsă.

## Upgrade al progresului

Sanity-ul de distribuție pornește de la o instalare curată. Pentru proba
separată de upgrade, porniți de la un emulator curat, verificați că cele
două APK-uri au același certificat și instalați întâi ediția publicată.
Creați progres, opriți procesul și instalați candidatul cu `adb install -r`.
Verificați `versionCode`, timpul primei instalări, PID-urile și ID-urile
problemelor, apoi comparați punctele, seria și apărarea rămasă în curs.

În componentă, `QuestStoreTest` verifică reordonarea și extinderea
capitolului cu același ID și punctaj, inclusiv refuzul acordării duble.
Restartul Android verifică restaurarea aceleiași instalări.
Doar instalarea peste o ediție existentă dovedește migrarea între APK-uri.

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

`MatchStoreTest` rulează persistența de producție cu preferințe în memorie și ceas fix. Verifică listele disjuncte după migrare și restart, salvarea atomică la încheiere, reutilizarea locului arhivat și plafonul celor zece partide active. `MatchListEntryTest` respinge construirea unui rând pentru meniul greșit. `MatchArchiveTest` verifică și codec-ul analizei, plafonul și identitatea checkpoint-ului.

`MatchListEntryTest` verifică datele afișate pentru partide în curs, partide încheiate, remize, renunțări și salvări vechi. `MainMenuLayoutTest` verifică textele în română și engleză, ierarhia dintre titlu, mutări și dată, contrastul etichetei și poziția butonului **Înapoi**. `MatchSlotsTest` verifică ordinea de înlocuire, controlul manual și respingerea replicilor AI vechi. Android păstrează un parcurs pe meniu, cu o captură a listei și o verificare a deschiderii partidei.

`ChessMatchTest` verifică reluarea din fiecare poziție a unei partide terminate, salvarea variantei noi, rocada, en passant și repetarea poziției. `GameReviewTest` verifică matul ratat, variantele comparate, transpoziția ECO, schimbarea categoriei la configurarea pragului, anularea și estimarea din perspectiva jucătorului. `GameReviewGovernanceTest` verifică valorile implicite, bugetul, reperele Elo și legalitatea liniilor importate. [Metodă, interacțiune și praguri](../board/game-review.md)

Pentru geometrie, `GameReviewLayoutTest` verifică pătratul maxim și spațiul rămas raportului în profiluri portret și late, inclusiv cu controale mai înalte. `GameReviewGraphLayoutTest` verifică selecția din centrul fiecărui punct pe grafice înguste și partide lungi, rotunjirea între puncte, capetele și cazurile fără lățime utilă. `GameReviewTimelineTest` verifică alinierea cu partida, dispariția deschiderii după prima navigare, limitele cursorului, saltul la moment-cheie, parcurgerea variantei și revenirea la poziția jucată fără modificarea istoricului.

Pentru integrarea nativă, rulați `run-android-sanity.ps1 -GameReviewOnly` după construirea variantei `sanity`. La calificarea APK-ului folosiți `-Distribution -GameReviewOnly -SourceCommit <sha>`. Prima fază are 23 de aserțiuni și cinci capturi, inclusiv selecția semimutării 184 din partida de 185. Fazele **De ce?** și reluare adaugă șapte aserțiuni și patru capturi. Ele verifică ambele evaluări și adâncimea, cele două linii, anularea, redeschiderea după oprirea procesului și continuarea separată cu arhiva intactă. Publicați `android-game-review.html` din `game-review.txt` cu `-Dtest=AndroidSanityPublication#publishGameReviewSanity` și `-Dchess.android.receipt=<cale>`. Păstrați recepțiile și capturile celor trei faze împreună. Extent verifică identificatorul comun de rulare, PID-urile distincte la reluare, identitatea APK-ului și a dispozitivului și amprenta fiecărei capturi. [Semnare și calificare](../release/README.md#calificare-și-publicare) · [Comportamentul review-ului](../board/game-review.md#navigarea-în-review-și-variantele-recomandate)

`GameReviewTimelineTest` acoperă matul permis și ratat, promovarea la damă și la turn, captura decisivă, schimbul compensat, orizontul inegal și liniile vechi invalide. `MatchStoreTest` verifică restaurarea marcajului după redeschiderea stocării, continuarea separată și invalidarea când se schimbă identitatea. `AndroidReceiptTest` respinge o captură substituită, o fază preluată din altă rulare și un restart raportat cu același PID.

`GameReviewTest` reconciliază tabelul cu mutările individuale, inclusiv zerourile și rolurile inversate când jucătorul are negrul. `MatchActionsTest` verifică refuzul, redeschiderea rezultatului salvat, mutarea din istoric și respingerea unei salvări care suprascrie un mat. [Fluxul jucătorului](../board/README.md#partidă-cu-calculatorul)

Pentru analiza AI, rulați `-AnalysisOnly`. Prima fază pornește dintr-un joc
salvat și verifică becul din istoric, două semimutări ale variantei, geometria
tablei și revenirea la cursor fără schimbarea salvării. Faza din
`analysis-restore/` verifică poziția și indiciul ascuns după restart. Sunt
cinci aserțiuni înainte de restart și două după. `-IncludeManualAnalysis`
rulează și tabla manuală cu același ID.

`MatchHintTest` verifică ordinea variantelor, destinația jucabilă, promovarea exactă și prioritatea selecției manuale. Include un caz în care mutarea indicată aparține culorii care a mutat deja. `MatchEvaluationTest` verifică perspectiva albului, formatarea și proporțiile barei pentru avantaj, egalitate și final. `MatchSetupTest` verifică alegerile de culoare, adâncimile AI, salvările anterioare și mutarea de deschidere a motorului când omul joacă cu negrul. `PlayerProfileTest` verifică validarea numelui, iar `MainMenuLayoutTest` verifică gruparea și opțiunile celor două dropdownuri.

`HintPreviewTest` verifică pașii motorului pe poziții reale, revenirea exactă,
matul, schimbul compensat și respingerea unei linii ilegale. `HintComparisonTest`
verifică ordinea comună a becului, dialogului și mutării la atingere în
Siciliană, Caro–Kann și Catalană, pentru ambele culori. Extent consemnează
mutările, orizontul comun și consumul sub plafonul configurat. Probele redau
ambele linii, păstrează varianta la revenire și resping indiciul din alt cadru.
Réti și Alehina verifică păstrarea mutării când inversarea nu este confirmată.
Explicația verifică replica de mat, captura necompensată și diferența de scor.
`AndroidReceiptTest`
reproduce fără emulator PNG-ul substituit și faza străină, inclusiv când
metadatele au fost actualizate. Extent păstrează rezultatele native, ID-ul,
amprenta APK-ului și capturile confirmate de amprentele din instrumentare.

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dtest=AndroidSanityPublication#publishMatchAnalysisSanity' '-Dchess.android.receipt=<dosar>/analysis-prepare.txt'
```

`ChessMatchTest` verifică reconstrucția înainte și după replica botului, accesul la pozițiile din istoric, matul, rocada, en passant și repetarea. După fazele de probleme, runnerul pornește cu date proaspete și execută `match-prepare`, `match-restore` și `match-settled` în procese distincte, păstrând partida între ele. Verifică poziția inițială, săgețile, selecția directă, revenirea la ultima mutare, blocarea mutărilor în istoric și identitatea partidei salvate.

## Tablă de analiză

`AnalysisLineTest` verifică navigarea, ramificarea, mutările speciale și exportul prefixului. Un cursor în istoric poate deschide analiza partidei sursă încheiate dacă linia întreagă îi păstrează mutările. O ramură diferită, o partidă în curs și un mat jucat manual folosesc analiza de studiu. `GameReviewTest` verifică analiza tuturor mutărilor și criteriile Elo. `MatchSlotsTest` verifică locul zece, refuzul la capacitate și respingerea răspunsurilor AI vechi. `MainMenuLayoutTest` acoperă intrarea localizată din meniu.

Pentru Android folosiți `-ManualAnalysisOnly` sau `-IncludeManualAnalysis`.
Proba deschide tabla din meniu, navighează în varianta sugerată, revine și joacă
mutarea indicată fără a crea o partidă salvată. Publicați `manual-analysis.txt`
cu `-Dtest=AndroidSanityPublication#publishManualAnalysisSanity` și
`-Dchess.android.receipt=<cale>`. Raportul are cinci aserțiuni și o captură
legată de recepție. În rularea combinată, publicatorul Extent confruntă și ID-ul
fazei de joc din directorul părinte. [Utilizare](../board/README.md#tablă-de-analiză)

`-MatchMenusOnly` rulează cele două meniuri pe aceeași instalare. Pentru probe separate de dezvoltare, folosiți `-MatchSlotsOnly` sau `-MatchArchiveOnly`. Publicați cu `AndroidSanityPublication#publishMatchSlotsSanity` sau `#publishMatchArchiveSanity` și recepția fazei. Extent leagă aserțiunea, captura încorporată și amprenta ei de același pas Android. Identitatea execuției include procesul, commitul și amprenta APK-ului pentru distribuție. [Partide salvate și încheiate](../board/README.md#partidă-cu-calculatorul)

## Proba TalkBack

1. Activați TalkBack pe emulator. În **Mat în 1**, deschideți **Indiciu**, apoi **Arată mutarea**. Atingeți culoarea la mutare și glisați de două ori spre dreapta prin intrarea tactilă a emulatorului. După fiecare gest, capturați evenimentul Android `TYPE_VIEW_ACCESSIBILITY_FOCUSED` și cadrul cu focusul. Repetați după **Întoarce tabla**. [Intrarea tactilă a emulatorului](https://developer.android.com/studio/run/emulator-console)
2. Pentru prima problemă din 0.3.1, traseul este negrul la mutare → regina neagră de pe d8 → h4, cu oricare dintre culori jos. Proba pe API 33 cu TalkBack 14.2 a confirmat cele patru glisări prin `EmulatorController/sendTouch`. [Extent-ul istoric](https://github.com/crististefanita/atf_j7/blob/v0.3.1/release/0.3.1/reports/talkback-gestures-extent.html) rămâne în tagul Git, în afara retenției scurte a release-urilor.
3. `QuestHintTest` verifică proiecția în ambele orientări. Sanity-ul verifică actualizarea legăturilor native. Proba glisărilor are propriul raport și folosește evenimentele de focus ca dovadă.

Pentru republicare, rulați `TalkBackPublication#publishTalkBackGestures` din copia TMP și indicați logul prin `-Dchess.talkback.gestures`. Logul unei singure probe conține evenimentele `J7FocusProbe` și markerii `J7ProbeMarker`, de la `BLACK_ANCHOR_START` la `WHITE_DESTINATION_END`. În același director puneți `emulator.json`, capturile `BLACK-color.png`, `BLACK-PIECE.png`, `BLACK-DESTINATION.png` și echivalentele `WHITE-*`.

Pentru vorbire, activați **Display speech output**. La a cincea rezolvare, comparați mesajul unic de mat și prag cu ce se anunță după întoarcere, revenirea din meniu și repornire. Proba anterioară compară ediția 0.3.0 cu build-ul corectat și consemnează cererile TalkBack și audio PCM sintetizat. [Extent-ul istoric](https://github.com/crististefanita/atf_j7/blob/v0.3.1/release/0.3.1/reports/talkback-extent.html) este păstrat în tagul Git.

[Tabla de joc](../board/README.md) · [Procesul de release](../release/README.md)
