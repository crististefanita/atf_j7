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

## Partide salvate și încheiate

**Partide salvate** arată numai partidele în desfășurare. **Partide încheiate** arată numai rezultatele arhivate. Ambele liste folosesc `MatchStore` și păstrează cel mult zece intrări fiecare, ordonate de la cea mai nouă. Interfața afișează data, numărul de semimutări și profilul. La o partidă cu AI, rezultatul este din perspectiva jucătorului.

Săgețile tablei schimbă poziția afișată. **Continuă de aici** cere confirmarea și păstrează mutările până la poziția selectată. Pentru o partidă activă rescrie slotul curent. Din arhivă pornește o partidă separată într-un slot liber sau reutilizabil, cu sursa păstrată. Dacă toate sloturile sunt active, utilizatorul rămâne la poziția selectată. Într-o partidă cu AI, confirmarea poate lăsa replica automată activă sau poate trece la control manual.

Browserul păstrează biblioteca într-o singură înregistrare locală versionată. La prima deschidere, salvează împreună partidele locale și AI din previzualizarea veche. La deschiderile ulterioare citește biblioteca salvată. Schimbarea partidei sau a poziției din istoric invalidează comenzile din revizia precedentă. [Matricea APK–Web](parity.md#contracte-și-probe)

Cu `$site`, `$edge` și un dosar `$evidence` nou în TMP:

```powershell
node ./web/tests/browser-game-review.mjs $site --ledger $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebLedgerPublication' "-Dchess.web.bundle=$site" "-Dchess.web.ledger.receipt=$receipt"
```

Pentru destinația publică, transmiteți URL-ul din README ca ultim argument Node și adăugați `-Dchess.web.ledger.public=true` la publicarea Extent.

### Reluarea analizei unei partide arhivate

Din **Partide încheiate**, deschideți partida și alegeți **Analizează partida**. Prima deschidere salvează analiza sub ID-ul partidei și checkpointul ei. **Deschide analiza** păstrează mutarea, ramura, pasul și starea **De ce?**, inclusiv după reîncărcarea paginii. Ștergerea intrării la limita de zece partide elimină și analiza asociată. O analiză din formatul anterior se atașează automat o singură dată, când checkpointul identifică exact o intrare. Analizele independente rămân disponibile din Game Review. [Probe de componentă](../docs/testing/README.md#reluarea-game-review-pe-android-și-web)

Proba Edge calculează două analize distincte cu workerul real și verifică ambele poziții după reîncărcare. Folosiți un director nou pentru dovezi, inclusiv când validați destinația publică.

```powershell
node ./web/tests/browser-game-review.mjs $site --archives $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebArchiveReviewsPublication' "-Dchess.web.bundle=$site" "-Dchess.web.archive.reviews.receipt=$receipt"
```

Pentru destinația publică, transmiteți URL-ul din README ca ultim argument Node și adăugați `-Dchess.web.archive.reviews.public=true` la publicarea Extent.

### Continuarea din Game Review

În analiza unei partide încheiate, selectați o poziție din istoricul jucat și confirmați **Continuă de aici**. Partida pornește exact după semimutarea selectată, într-un slot liber sau reutilizabil, cu analiza și poziția de reluare originale păstrate. Când **De ce?** sau o variantă este deschisă, reveniți în istoric. Poziția finală nu oferă continuarea. Pentru partidele cu AI, confirmarea permite păstrarea răspunsurilor automate. Dacă toate cele zece sloturi sunt active, analiza rămâne deschisă.

Proba verifică în Edge fluxul, salvarea partidei noi și arhiva originală după reîncărcare. Folosiți un director nou pentru dovezi.

```powershell
node ./web/tests/browser-game-review.mjs $site --review-continue $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebArchiveContinuationPublication' "-Dchess.web.bundle=$site" "-Dchess.web.archive.continuation.receipt=$receipt"
```

Pentru destinația publică, transmiteți URL-ul din README ca ultim argument Node și adăugați `-Dchess.web.archive.continuation.public=true` la publicarea Extent. [Probe de componentă și sanity](../docs/testing/README.md#partide-salvate-arhivă-și-continuare)

### Graficul din Game Review

Tabla și comenzile rămân fixe, iar raportul se derulează separat, ca în APK. Graficul folosește scorul albului, o axă neutră, un marcaj pentru poziția selectată și același calcul pentru atingere ca Android. O atingere selectează aceeași semimutare pe tablă, în lista mutărilor și în marcajul arhivei. Web și Android desenează piesele din aceleași contururi Staunton. Analizele noi ale partidelor eligibile cu AI folosesc și estimarea Elo din APK.

Generați în TMP fixture-ul de 185 de semimutări. Driverul verifică atingerea la două lățimi, inclusiv punctul 184, apoi reîncarcă pagina și verifică poziția și analiza păstrate.

```powershell
$cpFile = Join-Path $workRoot 'graph-classpath.txt'
mvn -B -ntp -pl atf-tests test-compile org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath "-Dmdep.outputFile=$cpFile"
$dependencies = (Get-Content -LiteralPath $cpFile -Raw).Trim()
$cp = @('atf-tests/target/classes', 'atf-tests/target/test-classes', $dependencies) -join [IO.Path]::PathSeparator
$fixture = Join-Path $workRoot 'review-graph.json'
java -cp $cp ro.atf.chess.GameReviewGraphFixture $fixture
node ./web/tests/browser-game-review.mjs $site $fixture $evidence $edge
$receipt = Join-Path $evidence 'result.json'
mvn -B -ntp -pl atf-tests test '-Dtest=ChessWebReviewGraphPublication' "-Dchess.web.bundle=$site" "-Dchess.web.graph.fixture=$fixture" "-Dchess.web.graph.receipt=$receipt"
```

Pentru destinația publică, transmiteți URL-ul din README ca ultim argument Node și adăugați `-Dchess.web.graph.public=true` la publicarea Extent. [Probe de componentă și sanity](../docs/testing/README.md#partide-salvate-arhivă-și-continuare)

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

`GameReviewSession` folosește `GameReviewTimeline` pe Android și în worker. Comenzile selectează semimutarea, deschid **De ce?**, schimbă ramura și derulează linia. O comandă cu revizie expirată păstrează starea curentă. Închiderea explicației revine la mutarea selectată. Fiecare platformă păstrează progresul local. Browserul păstrează analizele importate și analizele partidelor active în spațiul Game Review, iar analizele partidelor încheiate în intrările bibliotecii. [Contractul de navigare](../docs/board/game-review.md#navigarea-în-review-și-variantele-recomandate)

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
