# Release și RAG

Acest ghid leagă commitul, APK-ul semnat, proba Android și descărcarea verificată.
[Catalogul edițiilor](../../release/README.md) indică versiunea disponibilă și dovezile ei.

## Calificare și publicare

1. Investigați schimbările față de ultimul tag și rulați `ReleaseRag` cu căile modificate. Deschideți documentele citate și notați riscurile, suitele afectate și criteriile de acceptare.
2. Alegeți versiunea din seria 0.x. O corecție urcă patch-ul, iar o funcție nouă sau o schimbare de contract urcă minorul. Aliniați `versionName`, `versionCode`, versiunea Maven și numele APK-ului. În `release/index.json`, `currentVersion` este ediția curentă, iar `versions` conține curentul urmat de o singură ediție anterioară pentru comparație. Candidatul rămâne separat până la publicare. [Catalogul edițiilor](../../release/README.md)
3. Actualizați notele ediției și ghidurile afectate. Mutați regulile confirmate din feedback în ghidul care le deține și păstrați criteriile încă deschise. Puneți **Jocul** ca prima secțiune a README-ului principal, imediat după orientare. Folosiți două rânduri de listă formate fiecare dintr-un link, întâi către release, apoi către APK. În restul README-ului puneți ghidurile proiectului.
4. Creați commitul candidat local și exportați exact acel commit într-un TMP nou. Rulați `mvn test`, care pornește `ChessJvmSuite`. Rulați `ReleaseGate preflight` pe recepțiile acelui checkout pentru `DocumentationGovernanceTest`, `ReleaseGovernanceTest` și `ExtentGovernanceTest` înaintea build-ului de distribuție. Păstrați Extent pentru componente și guvernanță. Activați probele AI relevante când diff-ul afectează motorul sau corpusul. [Suitele JVM](../testing/README.md#suite-jvm)
5. Inițializați identitatea externă prin dot-sourcing `scripts/Initialize-J7Signing.ps1`. Pentru o cheie nouă după o ediție publicată, folosiți explicit `-StartNewIdentity`; scriptul refuză rotația implicită și protejează cheia cu ACL și DPAPI. Construiți APK-ul și testele Android cu Gradle, verificați semnătura, `versionCode` și compatibilitatea prin `scripts/verify-signing-continuity.ps1`. Rulați pe emulator parcursul potrivit schimbării și păstrați hash-ul APK-ului instalat și Extent-ul. Dacă semnarea s-a rotit și emulatorul păstrează ediția veche, nu dezinstalați instalarea cu date valoroase pentru a forța testul. Un APK sanity cu alt `applicationId` este suplimentar și nu califică binarul distribuibil.
6. Verificați în Extent starea, comparațiile extensibile și diagnosticul. Pentru sanity, verificați identitatea execuției și captura în pasul care conține aserțiunea. În fluxul normal, treceți APK-ul, commitul și dovezile prin `ReleaseGate qualification`. Rezolvați abaterile și repetați verificările afectate înainte de publicare. [Criterii de audit Extent](../testing/README.md#raportare-extent)
7. După semnare și calificare, împingeți tagul candidat fără a rescrie istoricul și publicați asset-ul GitHub. Păstrați `currentVersion`, lista de comparație și README-ul principal pe ultima ediție verificată până când asset-ul nou există. Apoi promovați candidatul în catalog și README. Păstrați în GitHub Release și local doar ediția nouă și ediția imediat anterioară; retrageți celelalte release-uri și dosare după ce noul asset și ruta din README au trecut verificarea. Nu ștergeți tagurile Git.
8. Citiți README-ul din `main` pe GitHub și descărcați APK-ul de la URL-ul afișat. Verificați prin `ReleaseGate publication` tagul, canalul, statusul HTTP, octeții, SHA-256 și CRC. Salvați dovezile, împingeți commitul cu rapoartele și repetați descărcarea de pe versiunea finală a README-ului.

## Excepție strictă pentru instalarea APK-ului exact

`ReleaseGate qualification` rămâne poarta normală și cere ca APK-ul distribuibil, commitul, aserțiunile și raportul Extent Android să fie aceeași execuție. Nu transformați o probă cu alt `applicationId`, alt hash sau alt certificat într-o calificare PASS.

Dacă semnătura s-a schimbat, singurul emulator disponibil conține instalarea veche și date care nu trebuie șterse, iar utilizatorul a cerut explicit publicarea prin README, ediția poate fi publicată numai cu starea `PUBLISHED_WITH_INSTALL_EXCEPTION`. Dosarul ediției trebuie să conțină `publication-exception.json`, build receipt verificabil, raportul și recepția sanity suplimentară, tranziția de semnare, Extent-ul cu hash verificat, recepțiile publică și autentificată de download și metadatele release-ului GitHub. Excepția este limitată la instalarea exactă: motivul, commitul și hash-ul APK-ului distribuibil, hash-ul diferit al APK-ului sanity, numărul aserțiunilor suplimentare și `CLEAN_INSTALL_REQUIRED` sunt validate separat. Nu se emite `qualification.json`, nu se afirmă că APK-ul exact a fost instalat și excepția nu relaxează testele, semnătura, integritatea asset-ului ori downloadul complet. După ce există un emulator curat, rulați calificarea strictă și înlocuiți excepția cu dovezile complete.

Dot-source scriptul în sesiunea de build pentru a încărca `J7_SIGNING_STORE` și `J7_SIGNING_PASSWORD`; parola este decriptată local cu DPAPI și nu este afișată. Pentru upgrade, certificatul trebuie să coincidă exact. O semnătură diferită cere explicit modul `CLEAN_INSTALL`; Android nu poate actualiza versiunea instalată, iar datele aplicației vechi nu sunt migrate. Gradle construiește perechea semnată cu `-Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest`. Runnerul cu `-Distribution -SourceCommit <sha>` cere un emulator fără pachetul jocului și confruntă octeții instalați cu APK-ul calificat înaintea instrumentării.

## Identitatea de semnare și instalare curată

Identificați certificatul ediției curente din [catalog](../../release/README.md):
calificarea strictă îl păstrează în recepția de calificare; ediția publicată cu
excepție îl păstrează în recepția de build. Dacă lipsește cheia existentă,
recuperați-o sau decideți explicit o instalare curată cu o identitate nouă.
Android nu migrează salvările locale peste semnături diferite.

`Initialize-J7Signing.ps1` refuză înregistrări parțiale și păstrează cheia,
certificatul și parola DPAPI în `infrastructure/signing` sub `-DataRoot`,
în afara sursei și a TMP. Pentru rotația deliberată folosiți `-StartNewIdentity`.
Poarta `signing-continuity` respinge diferența în modul `UPGRADE`; numai
`CLEAN_INSTALL` o consemnează ca incompatibilitate.
[Testele negative](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)
verifică poarta.

Fără un baseline explicit, `verify-signing-continuity.ps1` caută dovada
certificatului în dosarul ediției curente. La recuperarea unei chei absente,
inițializarea are încă o referință fixă. [Feedback de semnare](../feedback/README.md)

În copia TMP, după execuția Maven și înainte de semnare:

```powershell
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate preflight atf-tests/target/surefire-reports
```

Pentru schimbările din Deschideri, adăugați `-IncludeOpenings` la sanity-ul semnat. Runnerul execută proba pe același APK după verificarea reluării partidei. Publicați Extent din recepția `openings/openings.txt` și folosiți același commit și hash APK la calificarea ambelor rapoarte. [Proba Deschideri](../testing/README.md#deschideri)

Pentru o schimbare limitată la Analiza partidei, folosiți `-Distribution -GameReviewOnly -SourceCommit <sha>`. Publicați Extent din `game-review.txt` și calificați-l cu identitatea APK-ului din `execution.json`. Dacă diff-ul afectează și parcursurile de bază, rulați-le cu `-IncludeGameReview`. În acel caz, recepția analizei se află în subdirectorul `game-review`. [Proba de reluare și analiză](../testing/README.md#partidă-și-istoric)

Pentru schimbările din Tabla de analiză, folosiți `-ManualAnalysisOnly` în dezvoltare și `-IncludeManualAnalysis` pentru sanity-ul semnat. Publicați Extent din `manual-analysis/manual-analysis.txt` și verificați identitatea APK-ului prin `manual-analysis/execution.json`. Pentru schimbarea reperelor Elo, păstrați cele trei perechi de rapoarte și reluări TSV și verificați calculul cu `GameReviewGovernanceTest`. [Tabla de analiză](../testing/README.md#tablă-de-analiză) · [Protocolul ancorei](../board/ai-quality.md#rezultatele-ancorei-externe)

Pentru indiciile comune, rulați `-Distribution -AnalysisOnly -IncludeManualAnalysis -SourceCommit <sha>`. Calificați cele două rapoarte Extent față de același commit și APK semnat. [Partidă și istoric](../testing/README.md#partidă-și-istoric)

Pentru schimbări la listele de partide, rulați proba semnată a celor două meniuri. Runnerul păstrează aceeași instalare și resetează datele de test între faze. Calificați separat cele două rapoarte Extent cu identitatea comună a APK-ului. Verificările de persistență rulează în `MatchStoreTest`.

```powershell
./scripts/run-android-sanity.ps1 -Adb <adb> -Serial <serial> -LeaseFile <registru> `
  -Distribution -MatchMenusOnly -SourceCommit <sha>
```

Dacă ediția schimbă și tabla sau indiciile, rulați parcursul de analiză cu `-AnalysisOnly -IncludeManualAnalysis -IncludeMatchSlots -IncludeMatchArchive`.

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
