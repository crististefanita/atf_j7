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

## Reper măsurat și următorul experiment

Producătorul `711dff3f1bbb4c14aad682e7c319988dd35fce49` măsoară 1.238 de probleme și
2.303 etape canonice ale jucătorului. Corecția ulterioară a cititorului nu schimbă motorul sau datele.

| Capitol | Prima acceptată | Complet canonic | Etape acceptate |
|---|---:|---:|---:|
| Mat în 1 | 500/500 | 500/500 | 500/500 |
| Mat în 2 | 300/300 | 300/300 | 600/600 |
| Mat în 3 | 198/200 | 198/200 | 598/600 |
| Tactică | 93/100 | 85/100 | 201/221 |
| Antrenament | 94/114 | 77/114 | 273/330 |
| Intermezzo | 24/24 | 22/24 | 49/52 |
| Total | 1.209/1.238 | 1.182/1.238 | 2.221/2.303 |

Rămân 82 de etape ratate în 56 de probleme incomplete. Pe exact acele etape, 200k fix/H6 nu
recuperează nimic, 200k/H7 recuperează 20, iar 500k/H7 recuperează 26. Toate cele douăzeci se
păstrează la bugetul mai mare. Aceste brațe nu reevaluează etapele reușite și nu dovedesc non-regresia.

Opt etape ratate alese înaintea oracolului primesc 50 de interogări brute H17/H19 din FEN.
Ambele orizonturi confirmă că `mate3-04iY6` și `mate3-019gx` schimbă matul în trei în înfrângere,
cu toate cele trei politici. `training-051` schimbă victoria în remiză. Celelalte cinci etape
inspectate păstrează victoria deși nu ating obiectivul didactic; nu le declara cinci gafe
confirmate și nu relaxa obiectivul în tăcere.

Căutarea finalurilor de turn păstrează numai lista scurtă calculată ca rădăcini pentru orizontul
următor. Un prototip care readmite toate rădăcinile legale recuperează ambele chei de mat la
40.478 și 139.520 de noduri suplimentare, dar ratează încă `training-051`. Rămâne extern:
trei poziții nu califică transferul, non-regresia pe catalog, conversia cu apărări libere sau Elo.
Folosește ambele maturi, excluderi corecte, `training-051` și familii de turn noi ca să verifici
dacă readmiterea limitată poate păstra corectitudinea fără regresii nemăsurate.

Dovezi: `C:\work\sah\studii\2026-11-18-j7-sicilian-candidate-coverage`. Pornește de la README,
apoi rapoartele de integritate/calitate pe catalog și diagnosticul separat de buget/oracol.
[Criterii curente](../feedback/README.md#pe-scurt)
