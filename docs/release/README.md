# Release și RAG

## Calificare și publicare

1. Alegeți commitul candidat și construiți în TMP. Rulați componentele și sanity-ul pe APK-ul semnat ales pentru distribuție. Păstrați hash-ul APK-ului instalat și hash-ul fiecărui HTML Extent în dovezile rulărilor.
2. `ReleaseEvidence.qualification` confruntă commitul, APK-ul, HTML-urile și rezultatul celor două selecții. `ReleaseEvidence.publication` confruntă tagul, README-ul remote, canalul, URL-ul și octeții efectiv descărcați, inclusiv CRC-urile din APK.
3. După aprobare, publicați APK-ul ca asset GitHub. În secțiunea **Jocul** a README-ului principal puneți două linkuri, către release-ul curent și descărcarea directă. Documentați testele în dosarul ediției, separat de pagina de descărcare.

Configurați `J7_SIGNING_STORE` și `J7_SIGNING_PASSWORD` în sesiunea de build și păstrați cheia `chesslab-release` în afara repository-ului. Pentru upgrade, păstrați aceeași cheie. Gradle construiește perechea semnată cu `-Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest`. Runnerul cu `-Distribution -SourceCommit <sha>` cere un emulator fără pachetul jocului și confruntă octeții instalați cu APK-ul calificat înaintea instrumentării.

`ReleaseGate` expune verificările de calificare și publicare din Java. Pentru o ediție privată, citiți README-ul din `main` cu contul autentificat, reconciliați linkul cu `browser_download_url` al asset-ului din tag și descărcați prin API-ul autentificat. Înregistrați ruta, statusul, commitul, hash-ul și rezultatul CRC în dosarul ediției. Linkul din README rămâne ruta din browser pentru utilizatorii conectați. [API-ul GitHub pentru asset-uri](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)

După publicare, rulați verificarea cu autentificarea GitHub existentă în Git Credential Manager. Directorul de ieșire este nou și rămâne în TMP.

```powershell
./scripts/verify-authenticated-download.ps1 -Repository crististefanita/atf_j7 `
  -Version <versiune> -CandidateCommit <sha> -QualifiedApk <apk-calificat> `
  -Classes <copia-TMP>/atf-tests/target/classes -OutputDirectory <director-TMP-nou>
```

## Context consultativ

După compilare, apelați `ReleaseRag` din copia TMP cu rădăcina sursei și căile afectate:

```powershell
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseRag . chess-core/src/main/java/ro/atf/chess/ChessPosition.java
```

Fiecare rezultat indică documentul, ancora, linia, hash-ul și verificarea potrivită. Pentru audit de release, rulați comanda în copia commitului candidat. Rezultatul este consultativ, iar verificările de calificare și publicare rămân `NOT_RUN` în acest pas.
