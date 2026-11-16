# Motorul Chess Lab în browser

Buildul Web compilează motorul Java 17 cu TeaVM 0.15.0 într-un modul ES. Workerul execută regulile, cataloagele, partida locală și comenzile pentru analiză. [Matricea APK–Web](parity.md) · [Feedback pentru fluxurile Web–Android](../docs/feedback/web-parity.md)

Pe JVM și Android, încărcătorul citește YAML de pe classpath. Buildul Web trece aceleași resurse prin SnakeYAML 2.2 în etapa de generare, cu respingerea cheilor duplicate și a aliasurilor de colecții. Datele generate păstrează tipurile, ordinea și textul. Indexul ECO păstrează octeții originali. Compilatorul Web primește încărcătorul portabil și clasele motorului. SnakeYAML rămâne în etapa de generare. [Încărcătorul Web](src/main/java/ro/atf/chess/ChessResourceLoader.java) · [Generatorul](tools/GenerateChessResources.java)

În codul verificat, folosirea SnakeYAML în browser bloca inițializarea regulilor la `System.getLogger`. Generarea rezolvă dependența la build. Serializarea checkpointului păstrează formatul existent printr-un `StringBuilder`, care funcționează și în runtime-ul TeaVM. Aceleași cazuri verifică poziția inițială, rocada, en passant, promovarea, rezultatele partidei, restaurarea checkpointului, deschiderile și puzzle-urile.

Rulați din rădăcina copiei J7 în TMP cu PowerShell 7, JDK 21, Maven și Node.js. Alegeți `$workRoot` într-un director extern copiei. Buildul lasă aici datele generate, compilarea și pachetul `site`. Pentru cache local complet, adăugați `-Offline` la build și `-o` la Maven. [Locația fișierelor de lucru](../docs/README.md#reguli-de-întreținere)

```powershell
$site = ./web/Build-ChessWeb.ps1 -WorkRoot $workRoot
$report = Join-Path (Split-Path -Parent $site) 'web-parity.html'
mvn -B -ntp -pl atf-tests -am test '-Dtest=ChessWebRulesComponentTest,ChessWebParityPublication,SuiteGovernanceTest' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dchess.web.bundle=$site" "-Dchess.extent=$report"
```

Proba explicită cere pachetul compilat și compară rezultatele efective din JVM și worker pentru aceleași resurse și poziții. Raportul indică fiecare grup de date și de reguli. Verificarea JVM rămâne în suita componentelor. [Paritatea Web](../docs/testing/README.md#paritatea-web-a-motorului)

## Partida cu AI

**Partidă cu AI** deschide alegerea culorii și a profilului Basic, Advanced sau Hard. Workerul folosește aceeași căutare ca motorul Android pentru o replică fără lucru pregătit în fundal. Culoarea și nivelul se păstrează în checkpoint. Browserul salvează tura curentă înainte de a afișa mutarea și programează căutarea după pauza de 650 ms. După reîncărcare, poziția decide dacă mai este rândul AI. Workerul respinge reviziile expirate, iar o replică deja salvată lasă jucătorul la mutare.

Cu `$site`, `$edge` și un dosar `$evidence` nou în TMP:

```powershell
node ./web/tests/browser-game-review.mjs $site --ai $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebBotPublication' "-Dchess.web.bundle=$site" "-Dchess.web.bot.receipt=$receipt"
```

Proba pornește browserul sub `/atf_j7/`, reîncarcă în tura calculatorului și verifică mutarea cu JVM. Raportul încorporează capturile. Pentru verificarea destinației publice, treceți URL-ul din README ca ultim argument al comenzii Node și adăugați `-Dchess.web.bot.public=true` la publicarea Extent. Driverul verifică fișierele servite înaintea probei Edge. [Proba Android](../docs/testing/README.md#partidă-și-istoric)

## Partida locală și o analiză nouă

**Partidă locală** permite mutările ambelor culori și oferă alegerea piesei la promovare. Workerul validează fiecare mutare. Pagina salvează checkpointul canonic înainte de a afișa poziția rezultată. Reîncărcarea pornește un worker nou și reface istoricul, culoarea la mutare și mutările legale din checkpoint. [Matricea funcțiilor](parity.md#contracte-și-probe)

**Analizează partida** capturează checkpointul confirmat și calculează raportul cu `GameReview.analyzeLine`, la bugetul din `game-review.yaml`. Un worker dedicat emite progresul pe semimutare. **Anulează** oprește workerul de calcul, iar partida se poate continua din același checkpoint. Pagina acceptă analiza completă când partida corespunde încă instantaneului analizat. Raportul pentru jocul local rămâne fără estimare Elo. [Politica analizei](../docs/board/game-review.md#evaluarea-unei-semimutări)

Cu `$site` din build, `$edge` la executabilul Edge și un dosar `$evidence` nou în TMP, rulați partida prin interfață și publicați recepția. Extent verifică reluarea, analiza nouă și capturile legate de pachetul testat. [Probe comune și native](../docs/testing/README.md#reluarea-game-review-pe-android-și-web)

```powershell
node ./web/tests/browser-game-review.mjs $site --active $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebGamePublication' "-Dchess.web.bundle=$site" "-Dchess.web.game.receipt=$receipt"
```

## Game Review

Serviți directorul `site` prin HTTPS sau pe `localhost`, unde Web Crypto poate calcula SHA-256. Deschideți Game Review și încărcați o arhivă JSON cu `id`, `checkpoint`, `review` și, opțional, `bookmark`. `review` este cache-ul produs de `GameReviewCodec` pentru aceeași partidă încheiată. Testele generează o arhivă reproductibilă cu `GameReviewArchiveFixture`.

`GameReviewSession` folosește `GameReviewTimeline` pe Android și în worker. Comenzile selectează semimutarea, deschid **De ce?**, schimbă ramura și derulează linia. O comandă cu revizie expirată păstrează starea curentă. Închiderea explicației revine la mutarea selectată. Android salvează bookmarkul prin preferințele arhivei. Browserul salvează arhiva și bookmarkul împreună în `localStorage` și le reîncarcă la deschiderea paginii. Fiecare platformă păstrează progresul local. [Contractul de navigare](../docs/board/game-review.md#navigarea-în-review-și-variantele-recomandate)

Rulați întâi testele de componentă, apoi proba din Edge pe buildul real și publicatorul Extent. Pe Android, `-GameReviewOnly` păstrează gesturile și restartul de proces. [Strategia și dovezile Game Review](../docs/testing/README.md#reluarea-game-review-pe-android-și-web)

După rularea testelor de mai sus, alegeți `$fixture` și un `$evidence` nou în TMP. Setați `$edge` la executabilul Edge.

```powershell
$cpFile = Join-Path $workRoot 'game-review-classpath.txt'
mvn -B -ntp -pl atf-tests dependency:build-classpath "-Dmdep.outputFile=$cpFile"
$classes = (Resolve-Path atf-tests/target/classes, atf-tests/target/test-classes).Path
$runtimeClasspath = ($classes + (Get-Content -LiteralPath $cpFile -Raw).Trim()) -join [IO.Path]::PathSeparator
java -cp $runtimeClasspath ro.atf.chess.GameReviewArchiveFixture $fixture
node ./web/tests/browser-game-review.mjs $site $fixture $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebReviewPublication' "-Dchess.web.bundle=$site" "-Dchess.web.review.fixture=$fixture" "-Dchess.web.review.receipt=$receipt"
```
