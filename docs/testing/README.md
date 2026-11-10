# Strategia de testare

Construiți în TMP dintr-o copie a sursei. Componentele rulează fără emulator. Android păstrează un singur parcurs scurt pentru gesturi, geometrie și randare.

## Motor și proiecție

`ChessComponentTest` verifică poziția inițială, orientarea și legalitatea, cu oracole independente. Cazurile includ numărul pozițiilor la primele trei mutări individuale, rocada, en passant, promovarea și matul.

`BoardPanelLayoutTest` verifică geometria panoului pentru ferestre înalte, scunde și etichete mai înalte. Dimensiunea tablei se calculează din spațiul rămas după măsurarea ambelor etichete.

`MainMenuLayoutTest` verifică ordinea celor patru capitole, textele românești și englezești, derularea, totalurile și zona de atingere a becului. `QuestStepTest` verifică piesele și culoarea la mutare înainte și după apărare, reluarea și blocarea navigării până la rezolvare.

`QuestCatalogTest` enumeră fiecare mutare legală pentru toate cele 102 probleme și cere o singură soluție de mat. O probă separă șahul cu apărare de mat. O altă probă verifică patul, rocada prin șah și captura en passant care ar expune propriul rege. Amprenta completă a mulțimilor de mutări se compară cu [`quest-oracle.yaml`](../../chess-core/src/main/resources/quest-oracle.yaml), produs de `chess.js` 1.4.0.

`QuestCatalogTest` recalculează ordinea de dificultate pentru întregul catalog. `QuestProgressTest` verifică inclusiv trecerea de la 100 la 102 de probleme cu selecția și rezolvările păstrate după ID, chiar dacă poziția afișată se mută.

`QuestHintTest` verifică pentru toate cele 102 probleme că prima treaptă expune exclusiv plecarea, iar a doua destinația validată de motor. După apărarea adversarului, indiciul trebuie să folosească următoarea poziție. `QuestProgressTest` verifică pragurile după reluare și reordonare, idempotența rezolvărilor și finalul unui catalog de altă lungime.

`MateExplanationTest` confruntă pentru toate cele 102 probleme piesele care dau șah și explicațiile câmpurilor de fugă cu amprenta produsă de chess.js. Probele pentru `mate-101` și `mate-102` folosesc pozițiile din catalog și verifică faptul că pornesc în șah, indiciile și fiecare apărare legală. O probă separată cere o apărare legală după un șah care lasă adversarului mutări.

```powershell
node scripts/verify-quest-oracle.cjs <cale-catre-chess.js> chess-core/src/main/resources/quests.yaml chess-core/src/main/resources/quest-oracle.yaml
```

Motorul independent rulează în TMP și nu intră în APK. [chess.js](https://github.com/jhlywa/chess.js/tree/v1.4.0)

`MateInTwoCatalogTest` verifică exact 100 de poziții, absența matului imediat, o singură cheie, fiecare apărare legală și toate maturile din ramurile rezultate. Amprenta calculată de J7 trebuie să coincidă cu cea produsă independent de chess.js.

```powershell
node scripts/verify-mate-in-two.cjs <cale-catre-chess.js> chess-core/src/main/resources/mate-in-two.yaml chess-core/src/main/resources/mate-in-two.sha256
```

`MateInThreeCatalogTest` verifică matul exact în trei, unicitatea primei mutări și toate continuările câștigătoare după prima apărare. Amprenta este comparată cu cea calculată de chess.js 1.4.0.

```powershell
node scripts/verify-mate-in-three.cjs <cale-catre-chess.js> chess-core/src/main/resources/mate-in-three.yaml chess-core/src/main/resources/mate-in-three.sha256
```

## Tactică și punctaj

`QuestRewardsTest` verifică seria între capitole, plafonul bonusului, restaurarea unei încercări asistate, punctajul anterior și totalurile. `TacticsCatalogTest` verifică cheia unică a fiecărei poziții, câștigul de material, lipsa matului imediat și blocarea comenzii **Următoarea** la o mutare greșită. Pozițiile provin din partidele locale de calibrare J7, cu partida și semimutarea de origine notate în YAML. Amprenta conține cheia și câștigul de material recalculate de chess.js.

```powershell
node scripts/verify-tactics.cjs <cale-catre-chess.js> chess-core/src/main/resources/tactics.yaml chess-core/src/main/resources/tactics.sha256
```

## Raportare Extent

`TacticalAnalysisTest` compară o captură înainte și după recaptură, două câștiguri egale și un mat care devine vizibil abia la al treilea pas. Pozițiile din partida italiană verifică alegerea unei replici superioare primei mutări generate și evitarea unui mat imediat. Raportul de componente păstrează linia și câștigul așteptat ca aserțiuni.

Din copia TMP, cu JDK 21 și Maven:

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dchess.extent=target/components.html'
```

Aceeași extensie JUnit publică aserțiunile reale într-o secțiune colapsabilă atât la rularea întregii colecții, cât și la rularea unei metode prin `-Dtest`. Rapoartele pot fi inspectate fără XML-urile intermediare. `ReleaseGovernanceTest` verifică refuzurile pentru commit, rapoarte, README și APK nepotrivite.

## Sanity Android

Din aceeași copie TMP, setați `ANDROID_HOME` către SDK 36 și construiți `:app:assembleSanity :app:assembleSanityAndroidTest` din `android` cu `gradlew.bat`. Varianta `sanity` are identitate separată de APK-ul debug folosit pentru joc.

```powershell
./scripts/run-android-sanity.ps1 -Adb adb -Serial <serial> -LeaseFile <registru-rezervare>
```

Folosiți registrul comun de rezervare pe dispozitivele partajate.

1. Prima instrumentare verifică meniul, culoarea, cele două trepte ale becului și încadrarea în română la font 1,3×. În Tactică rezolvă două poziții și verifică afișarea flăcării, a bonusului și a totalurilor. În Mat în 2 observă mutarea înainte de apărare și pauza de cel puțin 650 ms. În Mat în 3 închide tabla cu a doua apărare în așteptare.
2. Runnerul oprește procesul. A doua instrumentare verifică răspunsul restant, deblocarea comenzii **Următoarea**, pragul de cinci rezolvări și restaurarea poziției și punctelor în Tactică.
3. Extent cere PID-uri distincte, aserțiunile celor cinci faze și zece capturi PNG. Raportul include meniul, indiciile, flacăra, progresul, partida și istoricul. Diagnosticul unei faze eșuate apare în aserțiunea rezultatului nativ.

La modificarea instrumentării, sincronizați numărul exact de aserțiuni pe fază cu `AndroidSanityPublication`. Pentru o corecție exclusivă de publicare, copiați dovezile native într-un dosar nou și rulați publicatorul cu `-Dchess.android.receipt=<dosar>/prepare.txt`.

## Partidă și istoric

`ChessMatchTest` verifică reconstrucția înainte și după replica botului, accesul la pozițiile din istoric, matul, rocada, en passant și repetarea. După fazele de probleme, runnerul pornește cu date proaspete și execută `match-prepare`, `match-restore` și `match-settled` în procese distincte, păstrând partida între ele. Verifică poziția inițială, săgețile, selecția directă, revenirea la ultima mutare, blocarea mutărilor în istoric și identitatea partidei salvate.

## Proba TalkBack

1. Activați TalkBack pe emulator. În **Mat în 1**, deschideți **Indiciu**, apoi **Arată mutarea**. Atingeți culoarea la mutare și glisați de două ori spre dreapta prin intrarea tactilă a emulatorului. După fiecare gest, capturați evenimentul Android `TYPE_VIEW_ACCESSIBILITY_FOCUSED` și cadrul cu focusul. Repetați după **Întoarce tabla**. [Intrarea tactilă a emulatorului](https://developer.android.com/studio/run/emulator-console)
2. Pentru prima problemă din 0.3.1, traseul este negrul la mutare → regina neagră de pe d8 → h4, cu oricare dintre culori jos. Proba pe API 33 cu TalkBack 14.2 a confirmat cele patru glisări prin `EmulatorController/sendTouch`. [Extent glisări](../../release/0.3.1/reports/talkback-gestures-extent.html)
3. `QuestHintTest` verifică proiecția în ambele orientări. Sanity-ul verifică actualizarea legăturilor native. Proba glisărilor are propriul raport și folosește evenimentele de focus ca dovadă.

Pentru republicare, rulați `TalkBackPublication#publishTalkBackGestures` din copia TMP și indicați logul prin `-Dchess.talkback.gestures`. Logul unei singure probe conține evenimentele `J7FocusProbe` și markerii `J7ProbeMarker`, de la `BLACK_ANCHOR_START` la `WHITE_DESTINATION_END`. În același director puneți `emulator.json`, capturile `BLACK-color.png`, `BLACK-PIECE.png`, `BLACK-DESTINATION.png` și echivalentele `WHITE-*`.

Pentru vorbire, activați **Display speech output**. La a cincea rezolvare, comparați mesajul unic de mat și prag cu ce se anunță după întoarcere, revenirea din meniu și repornire. Proba anterioară compară ediția 0.3.0 cu build-ul corectat și consemnează cererile TalkBack și audio PCM sintetizat. [Extent vorbire](../feedback/talkback-extent.html)

[Tabla de joc](../board/README.md) · [Procesul de release](../release/README.md)
