# Auditul indiciilor native cu Stockfish

## Pe scurt

- **Captură.** `HardHintAuditedGame` cere decizia Hard efectivă, indiciul public și estimarea afișată înaintea fiecărei mutări, inclusiv în prefix și la mutarea adversarului. Inspectarea repetată reutilizează aceeași recepție.
- **Oracol.** Stockfish 19 evaluează istoricul complet la H17 și H19, cu replicile adverse libere. Comenzile și răspunsurile UCI brute rămân într-un singur JSONL legat de sursă.
- **Verdicte.** Integritatea dovezilor și calitatea șahului sunt metode benchmark separate. Un audit valid trebuie să raporteze totuși sugestiile inexacte ca eșec de calitate. [Publicator](../../atf-tests/src/test/java/ro/atf/chess/HardHintStudyBenchmarkTest.java)

## Captura pereche

Hard nativ păstrează alocația configurată de 100k–200k noduri suplimentare; căutarea H3 inițială nu intră în acel contor. Indiciul are totalul său independent de 200k, cu căutarea inițială, frontiera tactică și comparația incluse. Bara folosește estimarea proprie. Niciun scor nativ în centipioni nu este scor Stockfish sau probabilitate calibrată de victorie. [Politici](../board/ai-quality.md)

Recepția păstrează checkpoint-ul, FEN-ul, culoarea, contoarele, drepturile, PV-urile native efective, toate indiciile oferite, candidații diagnostici, orizonturile selectat și inițial, nodurile și estimarea afișată pentru alb. Sugestia publică poate diferi de alegerea nativă. O altă mutare legală a adversarului își păstrează identitatea; nu eticheta sugestia drept mutare jucată.

[Componentele capturii pereche](../../atf-tests/src/test/java/ro/atf/chess/HardHintAuditedGameTest.java) resping dovezi lipsă, schimbate sau străine. Mutarea invalidă păstrează recepția în așteptare; una legală o consumă. Colectorul apelează fiecare căutare nativă și de indiciu o singură dată pe checkpoint.

## Verificarea oracolului

Folosește procese Stockfish 19 reci, un fir, hash de 32 MiB, putere completă și ieșire WDL. Se constrânge numai prima mutare testată. Compară fiecare alegere efectivă și sugestie publică cu o căutare liberă pe același istoric și orizont complet. Evaluează independent prima replică adversă din fiecare PV și inspectează finalul liniei. Colecția nu evaluează exhaustiv toate replicile ipotetice ulterioare.

`StockfishStudyAudit` verifică binarul, rularea și sursa, comenzile exacte, adâncimea atinsă, scorul UCI fără limită de fereastră, WDL, nodurile efective și PV-ul legal. O limită de tăiere nu poate înlocui o valoare exactă. FEN-ul și contoarele identice nu autorizează alt istoric. Proiecția respinge varianta oferită lipsă, altă rădăcină sau alt orizont. [Componente negative](../../atf-tests/src/test/java/ro/atf/chess/StockfishStudyAuditTest.java)

Scorul așteptat este `W + D/2` în miimi, pentru culoarea la mutare. Păstrează valorile brute libere și constrânse; pierderea diagnostică este diferența plafonată la zero. Pragul fix al pierderii mari este 150‰. Diferențele mici dintre căutări reci separate nu sunt câștiguri Elo. [Politica studiului](../../atf-tests/src/test/resources/hard-hint-study.yaml)

## Publicarea offline

Generează dintr-o copie curată externă, sub `C:\work\sah`. Fixează sursa, clasele compilate, producătorul, protocolul, YAML-ul și binarul oracolului înainte de joc. Păstrează sursa și clasele neschimbate în timpul generării. După rejucarea independentă, ține amprentele de încredere în afara colecției primite.

```powershell
$common = @(
  "-Dchess.hardHintStudy.output=$bundle",
  "-Dchess.hardHintStudy.revision=$measuredCommit",
  "-Dchess.hardHintStudy.planSha256=$trustedPlan",
  "-Dchess.hardHintStudy.oracleSha256=$trustedOracle",
  "-Dchess.hardHintStudy.gamesSha256=$trustedGames",
  "-Dchess.hardHintStudy.freezeSha256=$trustedFreeze",
  "-Dchess.qualification.commit=$verifierCommit"
)
mvn test '-Dtest=HardHintStudyBenchmarkTest#publishesEveryPlayedMoveAndEveryOfferedHintAgainstTheOracle' `
  "-Dchess.extent=$freshEvidenceExtent" @common
mvn test '-Dtest=HardHintStudyBenchmarkTest#measuredHintsMeetTheFrozenLargeLossFloor' `
  "-Dchess.extent=$freshQualityExtent" @common
```

Publicarea rejucă dovezile native și de indiciu fără Stockfish sau emulator. Fiecare rând brut, fază jucată, cerere de oracol și rădăcină oferită trebuie să existe. Lipsa amprentelor, studiul gol sau substituirea unei faze eșuează. Commit-ul producătorului măsurat rămâne distinct de commit-ul verificatorului ulterior.

## Interpretarea celor trei verdicte

- **Integritate:** sursa, istoricul, recepțiile brute și proiecția sunt complete și reproductibile.
- **Calitatea indiciului:** primele sugestii, toate variantele oferite și primele replici adverse rămân sub pragul înghețat. PASS la integritate nu implică acest PASS.
- **Game Review:** `analyzeLine` păstrează linia jucată și nu produce rating pentru un studiu limitat. Compară etichetele cu dovezile H17/H19 ale mutării jucate; neconcordanța este o constatare separată. Punctele logistice locale și WDL Stockfish sunt modele diferite.

Studiile nu estimează Elo și nu dovedesc joc optim împotriva oricărei apărări. Folosește [liga nativă pereche](hard-audit.md#elo-și-partidele-neterminate) pentru forță legată de sursă și intervale de încredere. Repararea unui caz nu permite promovarea când martorii independenți regresează.

## Colecția de referință legată de sursă

Colecția din 18 noiembrie folosește producătorul `e320379212dbb55afb392351da559b1cf949ac4d`: trei partide de studiu limitate la 20 de semimutări, 60 de poziții jucate, 83 de variante oferite și 960 de cereri de oracol. Pozițiile includ decizii efective și de control. La H19, 12 prime sugestii, 15 variante oferite și 10 replici adverse din PV pierd cel puțin 150‰. Alegerile native au trei asemenea lacune pe toate cele 60 de poziții, două la cele 21 de mutări efective Hard. Game Review are nouă neconcordanțe stabile de clasificare între H17 și H19.

Benchmarkul de calitate eșuează; cel de integritate trece. Motorul de joc live este neschimbat. Elo nativ aplicabil rămâne 1.507, cu interval conservator 95% 1.357–1.656, din cele 100 de partide calificate separat. Cele trei trasee neterminate nu produc Elo.

Studiul istoric `2026-11-18-j7-h6-recapture-hint-games` a fost retras din arhiva activă de învățare. Inventarul retragerii este `C:\work\sah\.studii\metadata\retired-studies.json`; istoricul sursă rămâne în referințele Git fixate. Folosește [contractul actual de admitere](../testing/learning-archive.md) și jurnalele noi în locul partidelor șterse. Scorurile istorice de mai sus nu reprezintă o estimare Elo nouă pe sursa actuală.

## Continuare

Păstrează perechea Caro–Kann corectată înainte și după `6.e6` ca martor de regresie. Păstrează și cazurile corectate de prim indiciu din Italiană și Siciliană. Scorurile native comprimate, candidații mai buni omiși și alternativele secundare inexacte rămân studii nerezolvate legate de sursă. Compară rădăcinile și apărările la același orizont și buget. Soluțiile observate nu devin interdicții FEN în runtime sau etichete de antrenare acceptate automat. Păstrează colecția originală când evaluezi un candidat ulterior. [Feedback curent](../feedback/README.md)

## Compararea indiciului Caro–Kann corectat

Corecția după pionul forțant este măsurată pe rejucarea pereche a exact acelorași mutări istorice. Producătorul este `5858e65e02b96e15ca8238d1d68cc2db70f2b6d8`; replicile istorice ale adversarului sunt marcate explicit ca rejucări, nu partide Stockfish noi. Toate cele șaizeci de rapoarte native și evaluări afișate coincid cu reperul. Se schimbă numai indiciul public de după `6.e6`.

Noul plan are 954 de cereri reci H17/H19 pentru toate rădăcinile jucate și oferite, primele replici adverse din PV și capetele liniilor. Integritatea trece; calitatea absolută rămâne FAIL cu 11/60 lideri deficitari, 14/83 mutări oferite deficitare și 10/83 prime replici adverse deficitare. Califică îmbunătățirea țintită și transferul independent separat de pragul absolut. Păstrează intacte rulările respinse, identitatea sursei și recepțiile brute. [Rezultate și interpretare](../board/ai-quality.md#indiciul-carokann-și-verificarea-recapturii-după-pionul-cu-șah)

`HintRecaptureRegressionTest` verifică perechea Caro, comparația diagnostică, identitatea redării și atingerii, bugetele întrerupte, separarea modurilor din cache și YAML plafonat. Dezactivarea noii limite trebuie să reproducă `…fxe6` și să eșueze în testul țintit. Istoriile PGN noi de transfer sunt înghețate înainte de evaluare, apoi devin martori de regresie după inspecție. Progresul indiciului nu este ΔElo al jocului nativ. [Componente](../../atf-tests/src/test/java/ro/atf/chess/HintRecaptureRegressionTest.java)

## Comparația italiană și istorii tematice noi

Producătorul `3b1e7ed1a4f2659b544d29732848d8078c5f3abc` corectează `Nd5/Na4` după `4…Qf6`. Păstrează aceleași șaizeci de faze istorice pentru comparația pereche, cu alegerile native și estimările afișate intacte. Înregistrează cele două ramuri suplimentare de șaisprezece semimutări ca studii tematice noi, nu ca reper înlocuit sau ligă de rating. Prefixele lor se suprapun intenționat; `Na4` este contraexemplul etichetat de prefix inferior.

Toate cele 92 de faze jucate au dovezi pereche nativ/indiciu. Planul brut de 1190 cereri acoperă și rădăcinile comparației diagnostice, cu istoricul și orizontul efectiv. Sursa și runtime-ul compilat rămân înghețate în timpul generării; rejucarea independentă prin regulile șahului verifică fiecare PGN și checkpoint. Raportul de integritate trece. Raportul absolut de calitate încă eșuează: cele șaizeci pereche păstrează 9 lideri cu pierdere mare, 12 oferte deficitare și 9 prime replici adverse deficitare. Observațiile tematice noi sunt separate, fără agregare într-un progres aparent.

`HintQueenRecaptureTest` acoperă identitatea indiciului public și a tablei manuale, comparația rece la orizont comun, recapturi legale și pioni legați în ambele culori, alocare geometrică versus legalitate, modurile cache-ului exact, bugete incomplete și un sacrificiu corect de damă. Valoarea zero a limitei existente de recaptură reproduce `Na4` și eșuează real în aserțiunea țintită. Nu este necesar emulator pentru aceste contracte de căutare. Validarea păstrează scăderea mică de 13,5‰ a unui lider și îmbunătățirea de 322,5‰; nu trebuie să pretindă regresii zero sau calibrare exactă a scorului. [Mecanism și limite măsurate](../board/ai-quality.md#recaptura-damei-și-continuitatea-indiciului-italian) · [Componente](../../atf-tests/src/test/java/ro/atf/chess/HintQueenRecaptureTest.java)

## Refolosirea dovezilor de oracol fără reetichetare

Studiul sicilian capturează 100 de faze native curente: șaizeci de checkpoint-uri pereche și două ramuri tematice de douăzeci de semimutări. Producătorul este `b90f181b707a3a7dd4c67147a31208534b69f0e2`. Fiecare înregistrare nativă nouă păstrează actorul real, checkpoint-ul, sursa și digestul înlănțuit. Prefixul forțat nu este prezentat ca alegere efectivă a lui Hard.

Cincisprezece faze primesc cereri reci H17/H19 noi; 85 refolosesc proiecții de oracol doar după egalitatea intrării curente complete și a rădăcinii jucate cu originalul fixat. Păstrează sursa/rularea originală a oracolului, fișierul brut original și digestul de încredere independent. Validează recepțiile brute cu planul lor original, apoi leagă exact istoricul curent, rădăcina și orizontul prin `StockfishStudyAudit.phase`. Nu copia recepții de oracol sau native vechi sub o sursă nouă. O sugestie publică schimbată și un istoric străin sunt cazuri explicite de respingere. Studiul verifică 284 de cereri noi și cele 1190 de recepții originale, fără să le declare eșantioane independente.

Extent-ul dedicat studiului păstrează integritatea, calificarea țintită și calitatea absolută drept rezultate distincte. Toate cele 100 de faze sunt proiectate o dată, inclusiv fiecare rădăcină oferită, prima replică adversă din PV, capătul liniei și comparația diagnostică. Tot nu epuizează fiecare apărare mai adâncă. Numerele de calitate pereche sunt 8/60, 11/82 și 9/82; cele 40 de observații tematice sunt separate. Rejucarea PGN independentă păstrează fiecare mutare, iar Game Review efectiv păstrează cele nouă neconcordanțe pereche anterioare de clasificare. Publicatorul studiului rămâne în dosarul extern; validatoarele brute reutilizabile și componentele funcționale rămân în proiect. [Preferință și limite](../board/ai-quality.md#egalități-exacte-în-siciliană-și-apărarea-piesei-legate)

`HintPinSupportTest` verifică H5 comun neschimbat, selecția nenumerică la egalitate, unica mutare oferită, identitatea comparației/tablei manuale/atingerii, redarea legală, apărători siguri și nesiguri, ambele culori, bugete mici și prioritatea scorului numeric sau de mat. Motorul compilat original trebuie să eșueze la țintă cu `…Nc6`; celelalte aserțiuni nu sunt slăbite pentru raport verde. Șaizeci și patru de ieșiri de transfer păstrate nu dovedesc activarea regulii în familii noi. [Componente](../../atf-tests/src/test/java/ro/atf/chess/HintPinSupportTest.java)
