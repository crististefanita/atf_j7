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
