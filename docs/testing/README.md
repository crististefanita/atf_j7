# Strategia de testare

Construiți în TMP dintr-o copie a sursei. Componentele rulează fără emulator. Android păstrează un singur parcurs scurt pentru gesturi, geometrie și randare.

## Motor și proiecție

`ChessComponentTest` verifică poziția inițială, orientarea și legalitatea, cu oracole independente. Cazurile includ numărul pozițiilor la primele trei mutări individuale, rocada, en passant, promovarea și matul.

`QuestCatalogTest` enumeră fiecare mutare legală pentru toate cele 102 probleme și cere o singură soluție de mat. O probă separă șahul cu apărare de mat. O altă probă verifică patul, rocada prin șah și captura en passant care ar expune propriul rege. Amprenta completă a mulțimilor de mutări se compară cu [`quest-oracle.yaml`](../../chess-core/src/main/resources/quest-oracle.yaml), produs de `chess.js` 1.4.0.

`QuestCatalogTest` recalculează ordinea de dificultate pentru întregul catalog. `QuestProgressTest` verifică inclusiv trecerea de la 100 la 102 de probleme cu selecția și rezolvările păstrate după ID, chiar dacă poziția afișată se mută.

`QuestHintTest` verifică pentru toate cele 102 probleme că prima treaptă expune exclusiv plecarea, iar a doua destinația validată de motor. `QuestProgressTest` verifică pragurile după reluare și reordonare, idempotența rezolvărilor și finalul unui catalog de altă lungime.

`MateExplanationTest` confruntă pentru toate cele 102 probleme piesele care dau șah și explicațiile câmpurilor de fugă cu amprenta produsă de chess.js. Probele pentru `mate-101` și `mate-102` folosesc pozițiile din catalog și verifică faptul că pornesc în șah, indiciile și fiecare apărare legală. O probă separată cere o apărare legală după un șah care lasă adversarului mutări.

```powershell
node scripts/verify-quest-oracle.cjs <cale-catre-chess.js> chess-core/src/main/resources/quests.yaml chess-core/src/main/resources/quest-oracle.yaml
```

Motorul independent rulează în TMP și nu intră în APK. [chess.js](https://github.com/jhlywa/chess.js/tree/v1.4.0)

## Raportare Extent

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

1. Prima instrumentare verifică rama fără săgeată, apoi săgeata și explicația. Citește marcajele accesibile și confirmă că dezvăluirea lasă contorul la zero. Rezolvă două probleme și lasă a treia deschisă.
2. Runnerul oprește procesul. A doua instrumentare verifică problema reluată și matul deja salvat. Selectează `mate-101` în datele aplicației izolate, joacă ambele contra-maturi și atinge pragul de cinci cu încă o rezolvare.
3. Extent cere PID-uri distincte, aserțiunile ambelor faze și patru capturi PNG. Rama, săgeata, matul restaurat și bara de progres sunt verificate în pixeli și atașate separat, după modelul capturilor multiple din J0. Diagnosticul unei faze eșuate apare în aserțiunea rezultatului nativ.

[Tabla de joc](../board/README.md) · [Procesul de release](../release/README.md)
