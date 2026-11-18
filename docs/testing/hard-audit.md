# Auditul nativ al partidelor Hard

## Pe scurt

- **O singură căutare pe poziție.** `ChessMatch.analyzeBot(HARD)` întoarce mutarea aleasă și raportul aceleiași decizii native. `HardAuditedGame` păstrează recepția până când o mutare legală avansează checkpoint-ul.
- **Fiecare semimutare jucată.** Mutările de deschidere înghețate și replicile Stockfish cer și ele o analiză Hard înainte de joc. Sugestia de control rămâne separată de mutarea efectivă.
- **Calificare.** [Contractele de componentă](../../atf-tests/src/test/java/ro/atf/chess/HardAuditedGameTest.java) resping dovezile lipsă sau străine. [Publicatorul ligii](../../atf-tests/src/test/java/ro/atf/chess/HardLeagueAuditTest.java) rejucă recepțiile brute înainte de publicarea ratingului în Extent.

## Identitatea căutării și interpretarea

Fixează commit-ul, clasele compilate, resursele motorului, lansatorul, binarul oracolului și starturile înghețate înainte de generare. Reverifică sursele de producție și clasele după terminarea tuturor partidelor. Revizia motorului măsurat rămâne distinctă de revizia ulterioară a verificatorului afișată în Extent.

Fiecare recepție conține checkpoint-ul complet și FEN-ul, culoarea și semimutarea, alegerea nativă efectivă și rangul, referința teoretică, adâncimea încheiată, nodurile suplimentare taxate și fiecare PV întors, cu scor nativ, material și final. Scorurile native pot include credit istoric de orizont. Nu sunt evaluări Stockfish sau probabilități calibrate de victorie.

Hard normal pornește cu 100k noduri suplimentare și poate continua până la 200k. Repertoriul și căile explicite își păstrează politica. Distinge acest buget de plafonul total unic de 200k al indiciului. Auditul apelează API-ul efectiv al partidei, păstrând istoricul, alegerea remizei și repetițiile; o a doua căutare de indiciu doar pe poziție nu poate înlocui acea decizie. [Politici](../board/ai-quality.md)

## Validarea recepțiilor

`HardMoveAudit` rejucă fiecare PV și verifică legalitatea, materialul, finalurile, contoarele și drepturile. Un PV cu scor zero oprit înaintea orizontului este acceptat numai când întregul istoric anterior împreună cu traseul căutat dovedește cel puțin trei apariții. Un contraexemplu cu același FEN, dar alt istoric anterior, trebuie respins. [Testele recepției native](../../atf-tests/src/test/java/ro/atf/chess/HardMoveAuditTest.java)

Partida nu poate juca înaintea analizei checkpoint-ului curent. O mutare invalidă păstrează recepția în așteptare; una legală o consumă. Verificatorul ligii respinge recepțiile omise, repetate, reordonate sau străine și cere ca Hard să joace alegerea efectiv auditată.

Amprenta de manifest de încredere, păstrată separat, fixează șase roluri: JSONL combinat al mutărilor, PGN, rezultate, runtime, înghețare și starturi. Verificatorul ignoră căile primite, rejucă fiecare partidă până la rezultatul nativ, verifică ambele culori pentru fiecare start și recalculează ratingul pereche. Lanțul intern de amprente nu autentifică singur o colecție rescrisă coerent. Componentele negative înlocuiesc conținut, rescriu manifestul și substituie identitatea sursei sau fazei.


## Auditul puzzle-urilor fără obiectiv transmis adversarului

`HardQuestTransferBenchmarkTest` măsoară adversarul nativ separat de solverul care primește obiectivul. O singură apelare `analyzeBot(HARD)` furnizează alegerea și căutarea efectivă. `HardQuestAudit` validează legalitatea PV-ului, materialul, finalurile și identitatea contabilizării. Extent păstrează raportul nativ pentru fiecare poziție înaintea scorării alegerii după oracolul catalogului.

Plafonul depinde de calea efectivă: repertoriul exact păstrează alocația explicită din `match-ai.yaml`, iar ieșirea din repertoriu folosește politica activă `hard-adaptive.yaml`. O depășire cu un nod eșuează în ambele cazuri. Nodurile raportate aici sunt suplimentare: căutarea inițială H3 rămâne în afara acelui contor, conform API-ului nativ. Nu eticheta valoarea drept totalul tuturor vizitelor. Corectarea contabilizării nu schimbă pragurile `hard-quest-transfer.yaml` sau granița lotului de antrenament. Raportul măsoară prime decizii; nu dovedește toate replicile ori conversia completă.

[Contractele auditului](../../atf-tests/src/test/java/ro/atf/chess/HardQuestAuditTest.java) · [Benchmarkul nativ](../../atf-tests/src/test/java/ro/atf/chess/HardQuestTransferBenchmarkTest.java)

## Rularea și publicarea calibrării

Construiește într-un spațiu extern din `C:\work\sah`, cu `TEMP`, `TMP` și directorul temporar JVM acolo. Îngheață sursa, configurarea și starturile pereche înainte de observarea rezultatelor. Generează o colecție nouă cu un producător care implementează contractul de recepție. Suita de componente nu pornește automat o ligă.

Rejucă independent PGN-ul SAN și recepțiile brute. Păstrează amprenta manifestului în afara colecției primite, apoi selectează explicit publicatorul benchmark din copia externă:

```powershell
mvn test '-Dtest=HardLeagueAuditTest#publishesEveryNativeMoveAndThePairedHardRating' `
  "-Dchess.hardLeague.output=$bundle" "-Dchess.hardLeague.manifestDigest=$trustedDigest" `
  "-Dchess.hardLeague.revision=$measuredCommit" "-Dchess.extent=$freshExtent" `
  "-Dchess.qualification.commit=$verifierCommit" "-Dchess.qualification.runId=$runId"
```

Publicatorul eșuează pentru colecție absentă, sursă greșită, artefact modificat, analize lipsă, culori nepereche sau rezultat inventat. Emite aserțiuni pentru fiecare decizie nativă și PV, urmate de ratingul legat de sursă. `qualified-rating.json` separă Elo de performanță local de diferența față de ancoră.

## Elo și partidele neterminate

Adversarul măsurat este Stockfish 19, cu putere limitată la `UCI_Elo 1500`, un fir, hash de 16 MiB și 6.000 de noduri solicitate pe mutare. Înregistrează și nodurile întoarse efectiv. Păstrează o singură sesiune Stockfish pe partidă. Estimarea descrie performanța față de acea configurație, nu un rating FIDE sau Chess.com. [Calibrarea Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

Folosește perechile de deschidere ca unități bootstrap, 20.000 de eșantioane și sămânța înghețată. Păstrează separat W/D/L/U. Partidele neterminate la plafonul declarat rămân `*`; valoarea 0,5 se folosește numai pentru estimarea centrală. Publică sensibilitatea cu toate neterminatele pierdute sau câștigate și intervalul conservator de 95% care include acele extreme. Calculele independente Python și Java folosesc implementări PRNG diferite, deci cuantilele pot diferi ușor; estimarea centrală și rezultatele trebuie să coincidă.

O rulare întreruptă rămâne diagnostic. Repornește experimentul înghețat când o corecție a validatorului ar cere altfel amestecarea producătorilor sau sesiunilor. Păstrează fișierele brute vechi și explică excluderea. O corecție numai a indiciului nu dovedește o schimbare cauzală a forței Hard efective, iar o calibrare mai mică nu înlocuiește implicit ancorele guvernate ale aplicației.

[Auditul indiciului](hint-audit.md) · [Strategia testării](README.md) · [Dovezile AI curente](../board/ai-quality.md)
