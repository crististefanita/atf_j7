# Release și RAG

## Calificare și publicare

1. Investigați schimbările față de ultimul tag și rulați `ReleaseRag` cu căile modificate. Deschideți documentele citate și notați riscurile, suitele afectate și criteriile de acceptare.
2. Alegeți versiunea din seria 0.x. O corecție urcă patch-ul, iar o funcție nouă sau o schimbare de contract urcă minorul. Aliniați `versionName`, `versionCode`, versiunea Maven și numele APK-ului.
3. Actualizați notele ediției, ghidul jocului și dovezile de testare. Puneți **Jocul** ca prima secțiune a README-ului principal, imediat după orientare. Folosiți două rânduri de listă formate fiecare dintr-un link, întâi către release, apoi către APK. În restul README-ului puneți ghidurile proiectului. Verificați legăturile locale și rulați `ReleaseGovernanceTest`.
4. Creați commitul candidat local și exportați exact acel commit în TMP. Rulați `mvn test`, care pornește `ChessJvmSuite`, și păstrați raportul Extent al componentelor și guvernanței. Activați probele AI relevante când diff-ul afectează motorul sau corpusul. [Suitele și raportarea](../testing/README.md#suite-jvm)
5. Construiți APK-ul și testele Android cu Gradle și cheia de distribuție. Verificați semnătura și `versionCode`. Rulați sanity-ul semnat pe emulator, inclusiv repornirea, ultima mutare și istoricul partidei. Păstrați hash-ul APK-ului instalat și rapoartele Extent.
6. Treceți APK-ul, commitul și dovezile prin `ReleaseGate qualification`. Rezolvați abaterile și repetați verificările afectate înainte de publicare.
7. După autorizare și calificare, împingeți tagul candidat fără a rescrie istoricul, publicați asset-urile GitHub și împingeți commitul cu linkurile pe `main`. Păstrați identitatea tagului și a asset-urilor în dosarul ediției.
8. Citiți README-ul din `main` pe GitHub și descărcați APK-ul de la URL-ul afișat. Verificați prin `ReleaseGate publication` tagul, canalul, statusul HTTP, octeții, SHA-256 și CRC. Salvați dovezile, împingeți commitul cu rapoartele și repetați descărcarea de pe versiunea finală a README-ului.

Configurați `J7_SIGNING_STORE` și `J7_SIGNING_PASSWORD` în sesiunea de build și păstrați cheia `chesslab-release` în afara repository-ului. Pentru upgrade, păstrați aceeași cheie. Gradle construiește perechea semnată cu `-Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest`. Runnerul cu `-Distribution -SourceCommit <sha>` cere un emulator fără pachetul jocului și confruntă octeții instalați cu APK-ul calificat înaintea instrumentării.

Pentru schimbările din Deschideri, adăugați `-IncludeOpenings` la sanity-ul semnat. Runnerul execută proba pe același APK după verificarea reluării partidei. Publicați Extent din recepția `openings/openings.txt` și folosiți același commit și hash APK la calificarea ambelor rapoarte. [Proba Deschideri](../testing/README.md#deschideri)

Pentru schimbările din Analiza partidei, adăugați `-IncludeGameReview` la sanity-ul semnat. Publicați Extent din `game-review/game-review.txt` și calificați raportul cu identitatea APK-ului instalat din `game-review/execution.json`. [Proba de reluare și analiză](../testing/README.md#partidă-și-istoric)

Pentru schimbările din Tabla de analiză, folosiți `-ManualAnalysisOnly` în dezvoltare și `-IncludeManualAnalysis` pentru sanity-ul semnat. Publicați Extent din `manual-analysis/manual-analysis.txt` și verificați identitatea APK-ului prin `manual-analysis/execution.json`. Pentru schimbarea reperelor Elo, păstrați cele trei perechi de rapoarte și reluări TSV și verificați calculul cu `GameReviewGovernanceTest`. [Tabla de analiză](../testing/README.md#tablă-de-analiză) · [Protocolul ancorei](../board/ai-quality.md#rezultatele-ancorei-externe)

Pentru indiciile comune, rulați `-Distribution -AnalysisOnly -IncludeManualAnalysis -SourceCommit <sha>`. Calificați cele două rapoarte Extent față de același commit și APK semnat. [Partidă și istoric](../testing/README.md#partidă-și-istoric)

Pentru edițiile care schimbă gestionarea partidelor și arhiva, rulați parcursul concentrat pe APK-ul semnat. Runnerul păstrează aceeași instalare și resetează datele de test între faze. Calificați separat raportul de restart și indiciu, tabla de analiză, partidele salvate și arhiva.

```powershell
./scripts/run-android-sanity.ps1 -Adb <adb> -Serial <serial> -LeaseFile <registru> `
  -Distribution -AnalysisOnly -IncludeManualAnalysis -IncludeMatchSlots -IncludeMatchArchive -SourceCommit <sha>
```

`ReleaseGate` expune verificările de calificare și publicare din Java. Pentru o ediție privată, citiți README-ul din `main` cu contul autentificat, reconciliați linkul cu `browser_download_url` al asset-ului din tag și descărcați prin API-ul autentificat. Înregistrați ruta, statusul, commitul, hash-ul și rezultatul CRC în dosarul ediției. Linkul din README rămâne ruta din browser pentru utilizatorii conectați. [API-ul GitHub pentru asset-uri](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)

Pentru auditarea unei publicări istorice, folosiți verificatorul din commitul calificat al ediției și README-ul identificat în recepția publicării. Edițiile noi aplică formatul curent al secțiunii **Jocul**.

După publicare, rulați verificarea cu autentificarea GitHub existentă în Git Credential Manager. Directorul de ieșire este nou și rămâne în TMP.

```powershell
./scripts/verify-authenticated-download.ps1 -Repository crististefanita/atf_j7 `
  -Version <versiune> -CandidateCommit <sha> -QualifiedApk <apk-calificat> `
  -Classes <copia-TMP>/atf-tests/target/classes -OutputDirectory <director-TMP-nou>
```

## Context consultativ

După compilare, apelați `ReleaseRag` din copia TMP cu rădăcina sursei și căile afectate:

```powershell
$paths = @(git -C $checkout diff --name-only $previousTag $candidateCommit)
java -cp "$scratch/atf-tests/target/classes" ro.atf.chess.release.ReleaseRag $scratch @paths
```

Extrageți lista din checkout-ul Git și transmiteți-o comenzii din copia TMP. RAG raportează fiecare cale drept `COVERED` sau `NEEDS_REVIEW`, inclusiv când lista conține ambele tipuri. Fiecare citare indică documentul, ancora, linia, hash-ul și verificarea potrivită. Când este citată secțiunea **Calificare și publicare**, RAG afișează și pașii în ordine. Păstrați ieșirea în dosarul ediției. Verificările de calificare și publicare se execută separat.
