# Release și RAG

## Pe scurt

Acest ghid leagă commitul, APK-ul semnat, proba Android și descărcarea verificată.
[Catalogul edițiilor](../../release/README.md) indică versiunea disponibilă și dovezile ei.

## Calificare și publicare

Versiunea disponibilă se verifică în README-ul din `origin/main`. Dacă ramura locală conține lucrări paralele, porniți candidatul din `origin/main` în TMP și transferați doar fișierele revizuite pentru ediție.

1. Investigați schimbările față de ultimul tag și rulați `ReleaseRag` cu căile modificate. Deschideți documentele citate și notați riscurile, suitele afectate și criteriile de acceptare.
2. Alegeți versiunea din seria 0.x. O corecție urcă patch-ul, iar o funcție nouă sau o schimbare de contract urcă minorul. Aliniați `versionName`, `versionCode`, versiunea Maven și numele APK-ului. În `release/index.json`, `currentVersion` este ediția curentă, iar `versions` conține curentul urmat de o singură ediție anterioară pentru comparație. Candidatul rămâne separat până la publicare. [Catalogul edițiilor](../../release/README.md)
3. Actualizați notele și ghidurile afectate în ambele ramuri. Mutați regulile confirmate din feedback în ghidul ownerului. În `main` folosiți **Play** și **Published Release**. În `main_RO` folosiți **Jocul** și **Ediția publicată**. Așezați linkurile APK și browser în această ordine sub titlul de joc. Puneți notele GitHub sub arhitectură. [Documentație bilingvă](#documentație-bilingvă) · [Publicarea Web](../../web/parity.md#publicarea-linkului)
4. Creați commitul candidat în worktree-ul Git curat din TMP. Declarați SHA-ul bazei de revizuire, strămoș distinct al candidatului. Rulați DocumentationGovernanceTest, ReleaseGovernanceTest, ExtentGovernanceTest, TestChangeReview și ReleaseGate preflight înainte de semnare. Păstrați rapoartele și recepția reviziei Git. Activați probele AI când diff-ul afectează motorul sau corpusul. [Suitele JVM](../testing/README.md#suite-jvm) · [Schimbarea testelor](../testing/README.md#schimbarea-testelor-față-de-o-bază-git)
5. Inițializați identitatea externă prin dot-sourcing `scripts/Initialize-J7Signing.ps1`. Pentru o cheie nouă după o ediție publicată, folosiți explicit `-StartNewIdentity`; scriptul refuză rotația implicită și protejează cheia cu ACL și DPAPI. Construiți APK-ul și testele Android cu Gradle, verificați semnătura, `versionCode` și compatibilitatea prin `scripts/verify-signing-continuity.ps1`. Rulați pe emulator parcursul potrivit schimbării și păstrați hash-ul APK-ului instalat și Extent-ul. Dacă semnarea s-a rotit și emulatorul păstrează ediția veche, nu dezinstalați instalarea cu date valoroase pentru a forța testul. Un APK sanity cu alt `applicationId` este suplimentar și nu califică binarul distribuibil.
6. Rulați `ChessComponentSuite` și sanity Game Review sub același ID de calificare. Treceți APK-ul, commitul, directoarele recepțiilor brute și rapoartele Extent prin `ReleaseGate qualification`. Verificați în Extent comparațiile extensibile și capturile din pașii lor. Pentru o schimbare în Teoria deschiderilor sau **De ce?**, adăugați proba compactă la font 1,3× în RO și EN pe același APK și commit. [Redarea comună](../testing/README.md#redare-comună-pe-ecran-compact) [Criterii de audit Extent](../testing/README.md#raportare-extent)
7. După semnare și calificare, împingeți tagul candidat fără a rescrie istoricul și publicați asset-ul GitHub. Păstrați `currentVersion`, lista de comparație și README-ul principal pe ultima ediție verificată până când asset-ul nou există. Apoi promovați catalogul și README-ul în ambele ramuri. Păstrați în GitHub Release și local doar ediția nouă și ediția imediat anterioară; retrageți celelalte release-uri și dosare după ce noul asset și ruta din README au trecut verificarea. Nu ștergeți tagurile Git.
8. Citiți README-ul din `main` pe GitHub și descărcați APK-ul de la URL-ul afișat. Verificați prin `ReleaseGate publication` tagul, canalul, statusul HTTP, octeții, SHA-256 și CRC. Salvați dovezile, împingeți commitul cu rapoartele și repetați descărcarea de pe versiunea finală a README-ului.

- După publicarea unui draft, recitiți release-ul după tag și identificați asset-ul după ID. GitHub poate returna o rută provizorie cu segmentul „untagged” în răspunsul de upload. Folosiți în README doar ruta canonică din metadatele release-ului publicat și verificați descărcarea completă.

- Trimiteți notele din fișierul UTF-8. Pentru REST, trimiteți octeții UTF-8 ai JSON-ului și recitiți corpul release-ului publicat după tag. Comparați textul cu sursa și verificați că linkul spre dovezi deschide dosarul ediției.

- Păstrați HTML-ul Extent generat la octet, inclusiv în indexul Git, pentru ca hash-urile din calificare să rămână valide. Aplicați verificarea de formatare fișierelor editate manual.

- În checkout, guvernanța ediției curente recalculează amprentele HTML pentru calificarea strictă și compară recepțiile Android cu sursa, APK-ul și totalul de aserțiuni. Căile rapoartelor și ale recepțiilor trebuie să rămână în dosarul ediției.

## Documentație bilingvă

Folosiți `main` pentru engleză și `main_RO` pentru română. Pregătiți ambele
actualizări în worktree-uri detașate în TMP. Ramura implicită deține publicarea,
iar metadatele, linkul APK și dovezile identifică aceeași ediție în ambele ramuri.

1. Porniți din `origin/main` și `origin/main_RO` live. Precizați formularea
   românească și transpuneți aceeași schimbare în engleză. Aliniați căile,
   ierarhia secțiunilor, sensul comenzilor, valorile și dovezile.
2. Revizuiți formulările și etichetele reale ale interfeței în ambele limbi.
   Folosiți SAN standard în exemplele de șah EN. Consemnați localizările care
   diferă intenționat la afișare și păstrează mutarea sau comportamentul.
3. Rulați `DocumentationGovernanceTest` în fiecare worktree candidat și poarta
   perechii pe cele două SHA-uri candidate comise. Verificați regulile J0 pentru
   orientare, proză vizibilă concisă, etichetele diagramelor și legendele alăturate.
4. Împingeți împreună cele două commituri testate prin fast-forward atomic.
   Când se schimbă ediția, verificați APK-ul complet din README-ul EN de pe
   `main` și confirmați că ruta RO indică aceeași ediție.

```powershell
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate documentation-pair . $enCommit $roCommit
git push --atomic origin "${enCommit}:refs/heads/main" "${roCommit}:refs/heads/main_RO"
```

Poarta perechii verifică inventarul Markdown urmărit, ordinea structurii,
linkurile și destinațiile de heading corespondente și identitatea în octeți a
dovezilor publicate. Revizuirea umană verifică fidelitatea traducerii. [Strategia de testare](../testing/README.md#alegerea-nivelului)

GitHub Actions livrează artefactul Web calificat dintr-un commit Git imuabil
prin [workflow-ul Pages](../../.github/workflows/chess-pages.yml).
Ramurile permanente sunt `main` și `main_RO`. Păstrați tagurile Git ale
edițiilor publicate și pachetelor Web servite. [Publicarea Web](../../web/parity.md#publicarea-linkului)

## Excepție strictă pentru instalarea APK-ului exact

- `ReleaseGate qualification` compară cazurile Surefire cu tabelele Extent și cele trei recepții Android, metadatele și PNG-urile.
- Calificarea cere un singur ID de rulare, sursa exactă și hash-ul APK-ului distribuibil. Rezultatele absente, altă rulare sau alt APK opresc poarta.

Excepția cere autorizare explicită de publicare, o schimbare de semnătură confirmată și date vechi de păstrat pe emulator.

<details>
<summary>Dovezi obligatorii și pași de semnare</summary>

Dacă semnătura s-a schimbat, singurul emulator disponibil conține instalarea veche și date care nu trebuie șterse, iar utilizatorul a cerut explicit publicarea prin README, ediția poate fi publicată numai cu starea `PUBLISHED_WITH_INSTALL_EXCEPTION`. Dosarul ediției trebuie să conțină `publication-exception.json`, build receipt verificabil, raportul și recepția sanity suplimentară, tranziția de semnare, Extent-ul cu hash verificat, recepțiile publică și autentificată de download și metadatele release-ului GitHub. Excepția este limitată la instalarea exactă: motivul, commitul și hash-ul APK-ului distribuibil, hash-ul diferit al APK-ului sanity, numărul aserțiunilor suplimentare și `CLEAN_INSTALL_REQUIRED` sunt validate separat. Nu se emite `qualification.json`, nu se afirmă că APK-ul exact a fost instalat și excepția nu relaxează testele, semnătura, integritatea asset-ului ori downloadul complet. După ce există un emulator curat, rulați calificarea strictă și înlocuiți excepția cu dovezile complete.

Dot-source scriptul în sesiunea de build pentru a încărca `J7_SIGNING_STORE` și `J7_SIGNING_PASSWORD`; parola este decriptată local cu DPAPI și nu este afișată. Pentru upgrade, certificatul trebuie să coincidă exact. O semnătură diferită cere explicit modul `CLEAN_INSTALL`; Android nu poate actualiza versiunea instalată, iar datele aplicației vechi nu sunt migrate. Gradle construiește perechea semnată cu `-Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest`. Runnerul cu `-Distribution -SourceCommit <sha>` cere un emulator fără pachetul jocului și confruntă octeții instalați cu APK-ul calificat înaintea instrumentării.

</details>

## Identitatea de semnare și instalare curată

- Identificați certificatul ediției curente din [catalog](../../release/README.md):
  calificarea strictă îl păstrează în recepția de calificare; ediția publicată cu
  excepție îl păstrează în recepția de build. Dacă lipsește cheia existentă,
  recuperați-o sau decideți explicit o instalare curată cu o identitate nouă.
  Android nu migrează salvările locale peste semnături diferite.

- `Initialize-J7Signing.ps1` refuză înregistrări parțiale și păstrează cheia,
  certificatul și parola DPAPI în `infrastructure/signing` sub `-DataRoot`,
  în afara sursei și a TMP. Pentru rotația deliberată folosiți `-StartNewIdentity`.
  Poarta `signing-continuity` respinge diferența în modul `UPGRADE`; numai
  `CLEAN_INSTALL` o consemnează ca incompatibilitate.
  [Testele negative](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)
  verifică poarta.

- Fără un baseline explicit, `verify-signing-continuity.ps1` caută dovada
  certificatului în dosarul ediției curente. La recuperarea unei chei absente,
  inițializarea citește și ea catalogul și oprește rotația dacă dovada ediției curente lipsește.

În copia TMP, înainte de semnare:

Porniți testele din copia Git a candidatului. Alegeți căi noi în TMP pentru
rapoartele de guvernanță și de revizuire. Preflight-ul confruntă baza și
commitul curent cu recepția TestChangeReview și cere verdict favorabil
pentru controalele de documentație, release și Extent.


```powershell
mvn test "-Dtest=ChessGovernanceSuite" "-Dchess.extent=$governanceExtent"
mvn test "-Dtest=TestChangeReview" "-Dchess.testChanges.base=$reviewBase" "-Dchess.extent=$testChangeExtent"
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate preflight atf-tests/target/surefire-reports . $reviewBase
```

- Rulați suitele din același checkout TMP și alegeți căi noi pentru rapoarte.
- Salvați directorul Surefire nou al componentelor înainte ca publicatorul Android să scrie alte XML-uri în el.
- Construiți pachetul Web din commitul candidat în TMP și furnizați-l prin `chess.study.bundle`. Fără el, trei probe ale workerului sunt omise și `ReleaseGate qualification` respinge suita. [Buildul Web](../../web/README.md#build-și-verificare)

```powershell
$runId = [guid]::NewGuid().ToString('N')
$commit = git rev-parse HEAD
$site = ./web/Build-ChessWeb.ps1 -WorkRoot (Join-Path $env:TEMP "chess-web-qualification-$runId")
mvn -pl atf-tests -am test "-Dtest=ChessComponentSuite" "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Dchess.study.bundle=$site" `
  "-Dchess.extent=$componentExtent" "-Dchess.qualification.commit=$commit" "-Dchess.qualification.runId=$runId"
  # Mutați recepțiile Surefire ale suitei în $componentReceipts înainte de publicarea Android.
  # Rulați sanity-ul semnat cu -SourceCommit $commit -QualificationRunId $runId.
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate qualification `
  $version $commit $apk $componentReceipts $componentExtent $nativeRun $nativeExtent
```

`$nativeRun` este dosarul cu `game-review.txt`, `execution.json` și subdirectoarele
`why` și `why-restore`. Arhivați în dosarul ediției HTML-urile și amprentele
recepțiilor; păstrați XML-urile brute și capturile deja încorporate în TMP.

<details>
<summary>Tabla și marcajul de pericol din Probleme</summary>

Pentru modificările la tabla sau marcajul de pericol din Probleme, rulați
`-Distribution -PuzzleDangerOnly -SourceCommit <sha>` pe APK-ul semnat.
La schimbări de layout, rulați și `-Distribution -PuzzleMateReplayOnly -SourceCommit <sha>`
pentru profilurile 720 × 1280 la 320 dpi și 1080 × 1920 nativ, ambele la font 1,3×. Publicați Extent din cele două recepții
și confruntați commitul și hash-ul APK-ului. [Probele Probleme](../testing/README.md#progresul-problemelor)

</details>

<details>
<summary>Antetul, punctajul și comenzile din Probleme</summary>

Pentru schimbări la antetul, punctajul sau comenzile comune din Probleme, rulați
`-Distribution -PuzzleUxOnly -SourceCommit <sha>` cu `-PuzzleLanguage ro` pe
profilul compact și cu `-PuzzleLanguage en` pe profilul fizic, ambele la font
1,3×. Publicați `puzzle-ux.txt` cu `AndroidSanityPublication#publishPuzzleUxSanity`
și păstrați rezultatul și amprentele celor șase capturi ale fiecărui profil.
[Contractul celor șase capitole](../testing/README.md#progresul-problemelor)

</details>

<details>
<summary>Deschideri</summary>

Pentru schimbările din Deschideri, adăugați `-IncludeOpenings` la sanity-ul semnat. Runnerul execută proba pe același APK după verificarea reluării partidei. Publicați Extent din recepția `openings/openings.txt` și folosiți același commit și hash APK la calificarea ambelor rapoarte. [Proba Deschideri](../testing/README.md#deschideri)

</details>

<details>
<summary>Geometria compactă a jocului cu AI</summary>

Pentru schimbări de geometrie în jocul cu AI, rulați `-Distribution -AnalysisOnly -CompactMatchAnalysisOnly -SourceCommit <sha>` pe același APK semnat. Publicați Extent din `analysis-compact-prepare-ro/analysis-compact-prepare-ro.txt`. Verificați același ID pentru pregătirea și reluarea în RO și EN și păstrați raportul, recepția agregată și amprentele celor șase capturi sub `additionalAndroidChecks.analysis-compact` în calificare. [Geometria și proba compactă](../testing/README.md#partidă-și-istoric)

</details>

<details>
<summary>Analiza partidei</summary>

Pentru o schimbare limitată la Analiza partidei, folosiți `-Distribution -GameReviewOnly -SourceCommit <sha> -QualificationRunId <id>`. Publicați Extent din `game-review.txt`. Dacă diff-ul afectează și parcursurile de bază, rulați-le cu `-IncludeGameReview`; recepția analizei se află atunci în subdirectorul `game-review`. [Proba de reluare și analiză](../testing/README.md#partidă-și-istoric)

</details>

<details>
<summary>Tabla de analiză și reperele Elo</summary>

Pentru schimbările din Tabla de analiză, folosiți `-ManualAnalysisOnly` în dezvoltare și `-IncludeManualAnalysis` pentru sanity-ul semnat. Publicați Extent din `manual-analysis/manual-analysis.txt` și verificați identitatea APK-ului prin `manual-analysis/execution.json`. Pentru schimbarea reperelor Elo, păstrați cele trei perechi de rapoarte și reluări TSV și verificați calculul cu `GameReviewGovernanceTest`. [Tabla de analiză](../testing/README.md#tablă-de-analiză) · [Protocolul ancorei](../board/ai-quality.md#rezultatele-ancorei-externe)

</details>

<details>
<summary>Indicii comune</summary>

Pentru indiciile comune, rulați `-Distribution -AnalysisOnly -IncludeManualAnalysis -SourceCommit <sha>`. Publicați ambele rapoarte Extent cât timp directoarele brute păstrează structura produsă de runner. Cele trei faze folosesc același ID, iar publicatoarele compară sursa, APK-ul și amprentele din instrumentare. Păstrați aceste rapoarte ca dovezi ale fluxurilor afectate alături de calificarea Game Review. [Partidă și istoric](../testing/README.md#partidă-și-istoric)

</details>

<details>
<summary>Dovezile arhivate ale indiciilor</summary>

Arhiva indiciilor conține cele două HTML-uri și `hint-analysis-execution.json`.
În `qualification.json`, înregistrați SHA-256 al recepției finale sub
`hintAnalysisExecutionSha256`. Guvernanța recitește cele trei faze, confruntă
identitatea rulării și APK-ului cu tabelele Extent și recalculează amprentele
HTML-urilor și ale PNG-urilor încorporate. Edițiile 0.13.5 și 0.13.6 folosesc
recepția existentă. Edițiile fără acest traseu își păstrează formatul.

</details>

<details>
<summary>Meniurile partidelor salvate și încheiate</summary>

Pentru schimbări la listele de partide, rulați proba semnată a celor două meniuri. Runnerul păstrează aceeași instalare și resetează datele de test între faze. Calificați separat cele două rapoarte Extent cu identitatea comună a APK-ului. Verificările de persistență rulează în `MatchStoreTest`.

</details>

```powershell
./scripts/run-android-sanity.ps1 -Adb <adb> -Serial <serial> -LeaseFile <registru> `
  -Distribution -MatchMenusOnly -SourceCommit <sha>
```

Dacă ediția schimbă și tabla sau indiciile, rulați parcursul de analiză cu `-AnalysisOnly -IncludeManualAnalysis -IncludeMatchSlots -IncludeMatchArchive`.

- `ReleaseGate` expune verificările de calificare și publicare din Java. Pentru o ediție privată, citiți README-ul din `main` cu contul autentificat, reconciliați linkul cu `browser_download_url` al asset-ului din tag și descărcați prin API-ul autentificat. Înregistrați ruta, statusul, commitul, hash-ul și rezultatul CRC în dosarul ediției. Linkul din README rămâne ruta din browser pentru utilizatorii conectați. [API-ul GitHub pentru asset-uri](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)

- Pentru auditarea unei publicări istorice, folosiți verificatorul din commitul calificat al ediției și README-ul identificat în recepția publicării. Pentru edițiile noi, README-ul implicit folosește **Play**, iar ramura RO folosește **Jocul**.

- După publicare, rulați verificarea cu autentificarea GitHub existentă în Git Credential Manager. Directorul de ieșire este nou și rămâne în TMP.

```powershell
./scripts/verify-authenticated-download.ps1 -Repository crististefanita/chess `
  -Version <versiune> -CandidateCommit <sha> -QualifiedApk <apk-calificat> `
  -Classes <copia-TMP>/atf-tests/target/classes -OutputDirectory <director-TMP-nou>
```

## Context consultativ

Pentru citările noi, RAG calculează SHA-256 din textul UTF-8 după conversia
separatorilor CRLF la LF. Ancora și amprenta provin din aceeași lectură a
documentului. Citările istorice păstrează amprentele publicate la ediția lor.

După compilare, apelați `ReleaseRag` din copia TMP cu rădăcina sursei și căile afectate:

```powershell
$paths = @(git -C $checkout diff --name-only $previousTag $candidateCommit)
java -cp "$scratch/atf-tests/target/classes" ro.atf.chess.release.ReleaseRag $scratch @paths
```

- Transmiteți căile din checkout-ul Git comenzii din TMP. RAG raportează fiecare cale drept `COVERED` sau `NEEDS_REVIEW`, inclusiv listele mixte.
- Fiecare citare indică documentul, ancora, linia, hash-ul și verificarea. Citările secțiunii **Calificare și publicare** afișează și pașii în ordine.
- Păstrați ieșirea în dosarul ediției. Rulați separat verificările de calificare și publicare.
