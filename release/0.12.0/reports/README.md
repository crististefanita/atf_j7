# Dovezile release-ului 0.12.0

Acest director păstrează dovezile concise pentru ediția publicată. Capturile folosite de test sunt încorporate în Extent; nu duplicăm imaginile ca fișiere separate.

- `components.html` și `component-execution.json`: suita JVM, 206 teste, 196 trecute, 10 opționale omise, zero eșecuri; preflight-ul documentației, release-ului și Extent-ului a trecut.
- `android-signed-sanity.html`, `android-signed-sanity.json` și `android-signed-sanity-execution.json`: 94 de aserțiuni Android API 28, inclusiv continuarea partidei după restart. Proba a folosit temporar un application ID separat și nu a produs un APK distribuit; pachetul temporar a fost eliminat după test.
- `build-artifact.json`, `signing-transition.json` și `publication-exception.json`: identitatea APK-ului, certificatul nou și explicația limitată a instalării.
- `rag.txt`: traseul consultativ către documente și suite, generat pentru schimbările candidatului.
- `github-release.json`, `public-download.json` și `distribution-verification.json`: publicarea și verificarea descărcării, completate după ce linkul principal este disponibil.
- Assetul GitHub `ChessLab-0.12.0.apk` este singurul APK de distribuție. Repository-ul păstrează linkul și receptele; APK-ul nu se dublează în Git.

APK-ul de distribuție nu a fost instalat peste 0.11.2, deoarece certificatul s-a schimbat și emulatorul păstrează aplicația și datele vechi. Receptele disting această limitare de testul suplimentar; ele nu declară calificarea instalării exacte drept PASS.
