# Studii de puzzle cu Hard nativ

## Pe scurt

Măsoară adversarul obișnuit înainte să alegi o îmbunătățire. Solverul cu obiectiv declarat,
cheia corectă din catalog și decizia nativă reușită sunt observații diferite.
[Benchmarkul](../../atf-tests/src/test/java/ro/atf/chess/HardPuzzleStudyBenchmarkTest.java)
auditează toate capitolele fără să dea adversarului obiectivul, cheia sau apărarea de referință.
[Protocol AI](../board/ai-quality.md#control-la-fiecare-schimbare) · [Catalog](../board/quest-curation.md)

## Înghețare și execuție

1. Fixează commit-ul, intrările compilate, catalogul, politica efectiv încărcată și ID-ul rulării.
   Păstrează build-ul, deciziile brute și rapoartele Extent în studiul extern din `C:\work\sah`.
2. Pentru fiecare problemă, construiește partida Hard obișnuită din FEN. Apelează întâi adversarul;
   consultă `quest.assess` și cheia numai după ce ai păstrat raportul căutării efective.
3. Păstrează fiecare etapă canonică, inclusiv ratările. După o alternativă sau greșeală,
   continuarea pe linia de referință este intervenție marcată, nu partidă rezolvată independent.
4. Leagă recepția de **FEN inițial + prefix canonic complet + checkpoint**. Checkpoint-ul unei
   partide compuse nu codifică singur tabla inițială. Păstrează contoarele și en passant prin
   `StudyFen`, nu prin formatarea istoricului care presupune poziția inițială standard.
5. Îngheață etapele ratate înaintea comparației de bugete. YAML-urile de diagnostic rămân extern.
   Separă adâncimea configurată, cea efectiv încheiată, nodurile suplimentare și baza H3 nebugetată.
   Finalurile de turn pot folosi semimutarea suplimentară configurată pentru ele.

Reperul folosește politica adaptivă live 100k→200k, H6 plus extensia finalurilor și alocarea
separată 44k a repertoriului. Un buget fix sau alt orizont definește alt profil de diagnostic.
Rezultatele pe ratări nu devin procent de reușită pe întreg catalogul.

## Publicare și interpretare

Din copia externă, transmite directorul exact, revizia producătoare și amprentele fixate
independent pentru `native.jsonl`, `identity.json` și `catalog.json`:

```powershell
mvn test '-Dtest=HardPuzzleStudyBenchmarkTest' "-Dchess.puzzleStudy.output=$study" "-Dchess.puzzleStudy.revision=$producer" "-Dchess.puzzleStudy.nativeSha256=$nativeHash" "-Dchess.puzzleStudy.identitySha256=$identityHash" "-Dchess.puzzleStudy.catalogSha256=$catalogHash" "-Dchess.extent=$study/puzzles.html"
```

Metoda de integritate validează catalogul complet, amprentele brute, ordinea, originea,
bugetul efectiv, PV-urile legale, bilanțul material și acceptarea recalculată independent.
Recepțiile adversarului nu pot moșteni credite de scor specifice indiciilor. A doua metodă
trebuie să eșueze dacă rămâne o problemă incompletă; PASS la integritate nu înlocuiește calitatea.

Raportează separat primele mutări, toate etapele și problemele canonice complete. Finalizarea
cere decizii native acceptate fără intervenție pe calea de referință. Nu dovedește conversia
contra tuturor apărărilor libere. Rezultatele solverului cu obiectiv declarat rămân distincte.

Pentru verificarea externă, folosește Stockfish rece H17/H19 cu același FEN inițial, prefix,
opțiuni și producător pentru fiecare pereche liber/forțat. Păstrează comenzile UCI, scorul exact,
WDL și PV-ul legal. `StockfishFenAuditTest` respinge recepții din poziția standard proiectate pe
poziții compuse, contoare modificate și comparații între origini. O etapă materială ratată poate
păstra victoria; separă aceasta de pierderea WDL confirmată înainte să schimbi motorul sau contractul.

## Calificare și pașii următori

Folosește `RookRootReentryTest` pentru cele două rădăcini de mat omise, ambele culori și toate
apărările legale. `NativeMateAudit` deține parcurgerea nativă comună. Rulează benchmarkul
portabil complet din copia externă pentru a recalcula fiecare problemă de mat existentă:

```powershell
mvn test '-Dtest=NativeMateDefenseBenchmarkTest' "-Dchess.extent=$study/native-mates.html"
```

Apelează adversarul obișnuit înainte să consulte acceptarea didactică. Explorează fiecare
apărare legală, păstrează adâncimea, nodurile și PV-ul fiecărei alegeri native, iar fiecare
tablă terminală trebuie să fie mat în obiectivul inițial. Nu folosește solverul cu obiectiv
declarat și nu cere emulator. Benchmarkul canonic complet rămâne separat pentru problemele materiale.

După schimbare, compară fiecare etapă anterior acceptată și îngheață familii de transfer noi
înainte să inspectezi ieșirile. Păstrează negativa de confirmare întreruptă, martorii cache-ului
exact și de repetiție și bugetul curent. Recepția validă și PV-ul legal nu înlocuiesc verdictul
de calitate. Păstrează variantele respinse chiar dacă totalul din catalog a crescut.

Clasifică toate etapele încă neacceptate la ambele adâncimi ale oracolului. Pornește de la
prima pierdere WDL reală, verificând și prefixele acceptate anterior, nu de la o stare ulterioară unei intervenții de referință. Salvează
FEN-ul inițial, prefixul complet, mutarea jucată, cheia, căutarea efectivă și linia adversă a
oracolului. Separă ratările didactice care păstrează victoria; nu da cheia memorată lui Hard.
Prioritizează catalogul curent înaintea problemelor publice noi. Compozițiile noi de verificare
sunt dovezi separate de transfer, nu adăugări în aplicație.

[Inventar măsurat și mecanism curent](../board/ai-quality.md#readmiterea-calificată-a-rădăcinilor-pentru-mat)
· [Lucrări deschise](../feedback/README.md#pe-scurt)


## Politica încărcată și continuările extinse

Leagă producătorul nativ de instantaneul `promotionExchangePolicy` efectiv încărcat, alături
de politica adaptivă a nodurilor. Un YAML diagnostic schimbat este alt profil, chiar dacă
revizia sursă și maximul de noduri coincid. Păstrează recepțiile vechi cu producătorul și
cititorul originale; nu rescrie antetele istorice pentru a inventa o politică neîncărcată.

Pentru excepția de promovare, cere dominanță exactă cu fereastră completă la ambele orizonturi
pare încheiate, captură/recaptură legală și câștig material efectiv în PV-ul păstrat. Testează
confirmarea întreruptă la limita de un nod, cu maxim și cache fixe. Dezactivarea funcționalității
trebuie să refacă decizia completă, adâncimea, nodurile și PV-ul de referință. Respinge recepția
care declară o politică străină chiar dacă amprentele fișierelor au fost recalculate.

Îngheață contextele continuărilor și limita de semimutări înainte de rezultatele oracolului.
Urmează mutarea nativă efectivă și replica adversă aleasă liber; înregistrează analizele ambelor
brațe în fiecare poziție identică, plus martori de izolare Basic/Advanced. Evaluează fiecare
rădăcină nativă la H17/H19, păstrează PGN-ul efectiv și explică terminarea sau limita diagnostică.
Compară pierderea WDL ca `max(0, free expected score - forced expected score)` prin `StockfishStudyAudit`;
scorurile exacte și PV-urile brute rămân imuabile. O etapă didactică rezolvată nu certifică
transformarea avantajului în victorie. Păstrează prima pierdere nouă ca YAML contextual, cu
originea completă, istoric, linie adversă și contraexemplu, separat de configurația tiparelor runtime.


## Auditarea certificatelor planurilor câștigătoare

Separă orizontul obișnuit de acoperirea planului forțat. O continuare câștigătoare lungă nu
dovedește minimax pe toate rădăcinile la acea adâncime. Fixează `pawnPushPolicy` în metadatele
producătorului curent și păstrează `pawnProof` cu nodurile efective, lungimea planului, contorul
fără progres și identitatea istoricului și strategiei. Munca obișnuită și cea a dovezii trebuie
să încapă în aceeași alocație nativă.

Adaptorul nativ de teste are un singur decodor explicit, inclusiv pentru colecții indexate prin mutări:

```java
var nativeSearch = HardQuestAudit.read(receipt.path("analysis"));
HardQuestAudit.validate(game, nativeSearch);
```

Folosește partida compusă completă, nu doar tabla curentă, când există o dovadă. Recalculează
strategia limitată cu cache-ul și vizitele declarate; respinge certificate șterse din PV-uri
lungi, acoperire adversă, istoric, strategie, registru material sau scor modificate. JSON-ul
nativ vechi poate omite câmpul opțional. Păstrează producătorul original; nu-i inventa metadate
ale politicii curente.

Include cazuri negative pentru buget întrerupt, promovare nesigură, pat, remiză cu pion de turn,
istoric străin și revendicarea celor cincizeci de mutări. Păstrează deciziile Basic/Advanced în
contexte identice. Verifică separat conversia efectivă până la mat sau remiză, față de certificatul
care se termină cu dama sigură. Verificarea WDL la rădăcină pe loturi de transfer înghețate nu
înlocuiește partidele complete sau dovezile Elo pereche.


Separă contorul inițial FEN pentru remiză de cel al unei sesiuni noi construite numai din tablă.
`ChessMatch.fromPosition` pornește la zero; `StudyFen` păstrează contorul de intrare. Prefixul
legat de sursă nu poate furniza singur un contor inițial omis. Conversia calificată după captură
are contoare egale după resetare; originile compuse arbitrare cer în continuare adaptorul
explicit al contorului, urmărit în feedback.

Statusul reperului și promovarea candidatului sunt operații separate. Măsoară catalogul nativ obișnuit înainte să consulți cheile, separă dovada matului cu toate apărările de etapele materiale canonice și păstrează neterminatele ca intervale de rezultat în estimarea pereche a ratingului. [Instantaneul măsurat](hard-status.md) înregistrează sursa și protocolul reale; actualizarea statusului nu schimbă bugetele runtime, acceptanța catalogului sau ratingurile afișate.

Indiciul indisponibil are eșec tipizat, fără linie sau scor final de coaching, și propriul verdict de completare. Nu trebuie să înlocuiască sau să anuleze profilul independent de joc. Păstrează prefixul durabil și fiecare tentativă inițială eșuată la recuperarea tehnică explicită; declară resetarea sesiunii profesorului. Integritatea rezultatului poate trece în timp ce completarea coachingului eșuează. O astfel de partidă nu este observație cu hint complet admisă în corpusul de învățare.

O optimizare de căutare trebuie să treacă atât semantica scorurilor exacte, cât și regresia deciziilor efective la buget fix. Probele de rădăcină cu fereastră nulă pot schimba observațiile cache, activarea rezervei și adâncimea completată, chiar dacă minimax exhaustiv coincide. Păstrează egalitățile incluse, replicile adverse libere și recepțiile originale de lucru; un orizont reținut mai mic nu este automat o îmbunătățire. Păstrează integritatea defectului istoric în componente și obiectivele opționale ale adversarului curent în suita de benchmark. [Auditul înfrângerilor](hard-status.md#primele-pierderi-confirmate-din-liga-păstrată).

## Calificarea unei schimbări selective de frontieră

Îngheață activarea din recepțiile reperului, înaintea rezultatelor candidatului ori profesorului. Lotul selectat prin activare măsoară transferul în acel domeniu, nu forța medie de joc. Exclude suprapunerea de partidă sursă, poziție canonică și oglindire de culoare cu reglajul/martorii inspectați. Păstrează versiunea setului sursă, licența, prefixul comprimat și proiecția exactă a primei mutări adverse. Nu transmite restul soluției adversarului obișnuit.

Păstrează variantele largi respinse și contraexemplele lor. După inspecție, transferul ratat devine dovadă de regresie; rafinarea cere o selecție nouă neatinsă. Raportează separat recuperările de pierdere mare, schimbările WDL numerice mici și pozițiile încă pierzătoare. Verifică fiecare fază liberă/forțată H17/H19 față de rularea, sursa, FEN-ul, istoricul, rădăcina și binarul originale; rejucarea ulterioară nu schimbă identitatea.

Păstrează analizele complete ale candidatului ori un reper imuabil fixat și diferențele explicite cu analizele complete schimbate. Recalculează primele mutări, exercițiile complete și fiecare etapă acceptată. Rularea cu politica dezactivată trebuie să restabilească decizia completă, PV-ul, adâncimea și lucrul reperului, nu doar mutarea. [Studiul replicii damei](hard-status.md#verificarea-limitată-a-replicii-damei) este exemplul curent.

Numai după regresiile fixe, toate apărările la mat și transferul neatins, îngheață protocolul partidelor pereche înainte de joc. Păstrează decizia nativă și tentativa maximă de indiciu pentru fiecare mutare efectivă, analiza finală și PGN-ul complet. Partidele noi cer jurnale noi legate de sursă; rejucările de poziții nu se numără ca partide. Estimează ΔElo al candidatului pe perechi de deschidere, cu intervale și sensibilitatea neterminatelor. Nu actualiza ancora reperului dintr-un câștig de puzzle și nu prezenta un duel ipotetic ca executat.
