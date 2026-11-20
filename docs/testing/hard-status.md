# Statusul măsurat al Hard nativ

Instantaneul reperului aparține sursei **`85b1c478575d3c31065161c55aa02cffcd0e7eee`**. Rejucarea curentă fără obiectiv declarat include corecția limitată pentru replica damei descrisă mai jos; catalogul, alocația obișnuită 100k→200k și ratingurile afișate rămân neschimbate. Tabelul Elo aparține în continuare reperului, nu adversarului corectat. Calibrarea reperului este independentă de porțile promovării unui candidat: catalogul incomplet nu împiedică măsurarea adversarului existent, iar estimarea ratingului nu califică un candidat.

## Rezultatele problemelor

| Capitol | Prima mutare acceptată | Exercițiu canonic complet | Etape acceptate |
|---|---|---|---|
| mate-in-one | 500/500 | 500/500 | 500/500 |
| mate-in-two | 300/300 | 300/300 | 600/600 |
| mate-in-three | 200/200 | 200/200 | 600/600 |
| tactics | 95/100 | 87/100 | 203/221 |
| training | 94/114 | 78/114 | 274/330 |
| intermezzo | 24/24 | 22/24 | 49/52 |

Totalurile sunt **1.213/1.238** prime mutări, **1.187/1.238** exerciții canonice complete și **2.226/2.303** etape, cu două reușite în plus în fiecare măsură și zero etape pierdute. **51 de exerciții rămân incomplete**; obiectivul existent «toate rezolvate» rămâne FAIL. Căutarea nativă primește numai poziția și profilul de joc; cheia se citește după recepție. Intervențiile pe traseul de referință sunt explicite și nu devin soluții autonome.

Toate cele **1.000 de probleme de mat** trec și împotriva fiecărei apărări legale: **2.236 de decizii native**, **1.236 de ramuri defensive** și **1.500 de ramuri terminale cu mat**, în limita originală. Solverul cu obiectiv declarat nu a fost folosit. Exercițiile materiale sunt mai slabe: **187/238 complete, 78,6%**, cu **526/603** etape acceptate. Majoritatea de mate scurte nu poate ascunde această lacună în procentul global de 95,9%.

Înaintea corecției pentru replica damei, față de catalogul identic păstrat la `ecf5291a71e49e02b846365b529dc070b4e3057a`, o alegere diferă, cu **zero etape câștigate sau pierdute**. Nu există progres nou în acuratețea catalogului. Stockfish rece H17/H19 verifică fiecare dintre cele 79 de etape neacceptate: **56 de pierderi mari stabile**, **23 de alternative apropiate de oracol** și nicio pierdere mare stabilă pentru cheile pedagogice. **42** sunt prime pierderi native mari accesibile fără intervenție de referință. Dintre ele, 28 rețin H5, nouă H6, una H4 și patru H7; numai cinci consumă plafonul efectiv maxim. Izolează scorul, orizontul, confirmarea liderului și acoperirea rădăcinilor înainte să atribui fiecare ratare plafonului de noduri.

Fazele primare ale profesorului potrivesc contorul nativ inițial fără progres, zero, păstrând tabla, drepturile, en passant, istoricul complet și numărul complet al mutării. Controlul separat, intact, cu FEN-ul original schimbă 11 pierderi WDL numerice și zero clasificări. Această aliniere a măsurării nu repară limitarea existentă a contorului pentru FEN compus.

## Instantaneul Elo relativ

Cele **72 de partide** folosesc **12 perechi de deschidere / 24 de partide per profil**, cu culorile inversate. Ancora este **Stockfish 19, UCI_Elo 1500, 6.000 de noduri solicitate pe mutare, Threads 1, Hash 16 MiB**. Sunt aceleași 12 starturi înghețate ale proiectului, nu familii independente noi. O partidă încă activă la 220 de semimutări rămâne neterminată.

| Profil | Estimare locală | Interval 95% pe perechi | V/R/Î/Neterminate | Sensibilitate neterminate: toate pierdute…toate câștigate |
|---|---|---|---|---|
| Basic | 1126 | −∞…1309 | 2/0/21/1 | 1083…1162 |
| Advanced | 1456 | 1126…1711 | 7/1/10/6 | 1363…1544 |
| Hard | 1604 | 1380…1917 | 12/0/5/7 | 1500…1732 |

Numai punctul central acordă partidelor neterminate jumătate de punct; ele nu sunt înregistrate ca remize. Bootstrapul folosește 10.000 de eșantioane de perechi complete. Douăsprezece perechi păstrează incertitudine mare, iar scorurile extreme pot să nu aibă o estimare finită. Aceasta este performanță relativă la protocolul configurat, **nu Elo FIDE sau ΔElo măsurat al unui candidat**. Nu adăuga starturile repetate vechiului lot de 600 de partide ca observații noi independente. [Contextul ratingului Stockfish](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish).

Fiecare mutare efectivă păstrează Hard shadow și tentativa de indiciu public maxim înainte de joc; Basic/Advanced își joacă în continuare propriile decizii. Există **un indiciu indisponibil** și **o analiză finală incompletă**. Eșecurile tipizate nu au linie sau scor final de coaching inventat și au verdict separat de calitate. Partida recuperată păstrează prefixul original durabil; resetarea tehnică Stockfish păstrează istoricul complet, aceleași opțiuni și ambele transcripturi fixate. Ratingurile locale ale analizei nu furnizează Elo măsurat. Nu toate continuările de indiciu și analiză ale ligii au certificare independentă H17/H19, deci pachetul de rating nu înlocuiește corpusul admis pentru învățare.

## Primele pierderi confirmate din liga păstrată

Cele cinci înfrângeri efective ale Hard din aceeași calibrare de 72 de partide au fost inspectate cronologic. Stockfish rece, nelimitat, H17/H19 compară fiecare mutare Hard anterioară până la prima pierdere de cel puțin 150‰ la ambele orizonturi: 30 de decizii și 120 de faze brute. Este diagnosticul partidelor existente, nu cinci familii noi independente.

| Partidă | Prima pierdere efectivă | Replica liberă mai bună H19 | Pierdere WDL H17/H19 |
|---|---|---|---|
| Alehină, negru | 11…Bxf2+ | …O-O | 997,5/996,5‰ |
| Gambitul Regelui, negru | 9…Bb4+ | …Bg7 | 300/329‰ |
| Nimzo, alb | 11.Qb3 | Na4 | 250,5/244,5‰ |
| Petroff, alb | 7.h3 | Nbd2 | 206/236,5‰ |
| Slavă, negru | 9…Nxc3 | …Nb4 | 284,5/230,5‰ |

Toate cele cinci decizii reținute se opresc la H5. Patru linii se termină cu o captură a damei și o recaptură legală omisă, uneori evadare din șah; Petroff se termină cu o captură de pion și are alt mecanism. Comparațiile izolate la fereastră completă H4/H5/H6 nu dovedesc o decizie completă de joc: iterațiile precedente și toate alternativele de la rădăcină consumă în continuare bugetul. Analiza nativă finală a Alehinei raportează 99% acuratețe pentru negru, în ciuda primei pierderi confirmate independent; evaluatorul local nu este reper extern de forță.

Proba generală cu fereastră nulă la rădăcină păstrează scorurile minimax exacte, dar pierde trei controale existente din Franceză/Gambitul Damei și nu schimbă cele cinci alegeri. Confirmarea restrânsă H4/H6 nu le schimbă nici ea. Respingerea frontierei H5 cu dama expusă îmbunătățește Alehina și Slava, dar alegerea vechii linii H4 agravează Nimzo la **357,5/383,5‰**. Rejucarea completă a catalogului câștigă opt etape acceptate și pierde șaisprezece, cu 82 de analize schimbate din 2.303. Toate cele 1.700 de etape canonice de mat rămân identice; transferul cu toate apărările la mat nu a fost rerulat pentru candidatul respins. Acei candidați au fost respinși; nu califică un duel sau o afirmație ΔElo. Corecția limitată ulterioară, descrisă mai jos, are propriile dovezi de transfer.

[HardDefeatWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/HardDefeatWitnessTest.java) păstrează cele cinci istorii legale, lucrul nativ original și recepțiile profesorului rece, cu negative pentru istoric/rulare/scor străin. Nu cere adversarului live să repete o mutare greșită. [HardDefeatBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/HardDefeatBenchmarkTest.java) expune cinci obiective de calitate separate; toate rămân FAIL pe reper și sunt separate de suita obișnuită de componente. O mutare nouă nemăsurată cere o fază nouă a oracolului, nu presupunerea că este mai bună. [Fixture portabil](../../atf-tests/src/test/resources/hard-defeat-witnesses.yaml).

Dosarul extern auditabil este `C:\work\sah\.studii\experiments\2026-11-20-hard-losses\README.md`. Următorul pas cere o comparație completă cu replici libere care elimină optimismul damei expuse fără revenire la o mutare H4 și mai slabă; păstrează controalele Franceză/Gambitul Damei și fiecare etapă acceptată înainte de transferul pe familii excluse și duelul pereche. Mai multe aserțiuni de infrastructură trecute nu demonstrează un joc mai puternic.

## Verificarea limitată a replicii damei

Corecția calificată câștigă `tactic-040:0` (`dxe6`) și `tactic-095:0` (`…Re8`): două exerciții materiale complete, zero etape pierdute din toate cele 2.303. Traversarea tuturor apărărilor la mat a fost reluată: 1.000 de exerciții, 2.236 de decizii native și 1.500 de ramuri terminale. H6 întrerupt păstrează H5; nu îl înlocuiește niciodată printr-o mutare H4 mai veche.

[Politica YAML tipizată](../../chess-core/src/main/resources/queen-reply-verification.yaml) admite numai o captură de la rădăcină al cărei PV complet de orizont impar se termină cu o captură a damei și permite recaptură legală imediată, la fază materială de cel puțin 16/24. H6 caută fiecare rădăcină legală, reutilizează lucrul valid de frontieră și verifică prima rădăcină în fereastra de aspirație ±200 cp. Fereastra eșuată ori limitată se recaută. Liderul schimbat cere superioritate strictă la H6 complet și două comparații H4 exacte fără credite, taxate din același plafon 200k. Planurile liniștite și finalurile rare păstrează garda originală.

Proba mai largă, bazată numai pe fază, a fost respinsă după ce a stricat două planuri liniștite din 21 de contexte arhivate. Acestea sunt acum martori de regresie, nu transfer neatins. Restrângerea la captură restabilește ambele planuri, păstrează o schimbare echivalentă verificată independent și lasă identice toate cele 42 de analize Basic/Advanced. Un lot separat [Lichess CC0](https://database.lichess.org/#puzzles) de 256 de poziții analizate numai cu reperul a produs 17 contexte eligibile din partide sursă, înghețate înaintea rezultatelor candidatului și profesorului. Echivalentele canonice și de culoare au fost excluse; adversarul nativ primește numai prima mutare adversă, niciodată soluția problemei.

| Context independent | Mutarea reperului → corectată | Pierdere WDL H17/H19, înainte → după |
|---|---|---|
| `06cPH` | `Bxg6` → `h5` | 1000/1000‰ → 0/0‰ |
| `07GQq` | `…Bxe5` → `…Qxe5` | 779/717‰ → 0/0‰ |
| `03z37` | `…Bxc5` → `…Bg3` | 1000/1000‰ → 992/999‰ |

Nu există degradări confirmate la ambele orizonturi în cele 17 cazuri, iar toate cele 34 de analize Basic/Advanced rămân identice. Ultimul rând rămâne pierzător și este o schimbare numerică mică, nu o problemă rezolvată. Cele două reușite clare de transfer devin martori portabili de regresie. Lotul selectat prin activare nu estimează forța medie de joc sau Elo.

La plafonul obișnuit 200k, cele cinci înfrângeri din liga păstrată ratează în continuare obiectivele separate. Într-un **profil diagnostic 500k**, Alehina încheie H6 și alege `…Be7` la 476.986 noduri; H17/H19 rece dă pierdere 0/0‰ față de 997,5/996,5‰ pentru vechiul sacrificiu. Profilul nu este politica livrată sau progres Elo măsurat. Păstrează separat ratările originale 200k și rezultatul independent cu buget mai mare.

[HardQueenReplyTest](../../atf-tests/src/test/java/ro/atf/chess/HardQueenReplyTest.java) acoperă cele două reparații de catalog, ambele reușite clare de transfer, contraexemplele cu planuri liniștite/finaluri rare, recaptura legată, politici invalide, scoruri/egalități exacte la aspirație și respingerea profesorului/contextului modificat. Dosarul extern este `C:\work\sah\.studii\experiments\2026-11-20-hard-even-verification\README.md`; păstrează recepțiile brute, variantele respinse și Extent. Pasul măsurat este de două exerciții materiale și două recuperări independente de pierdere mare. ΔElo al candidatului este **nemăsurat**; niciun reper de rating nu se schimbă.

## Dovezi și studiul următor

Dosarul extern este `C:\work\sah\.studii\experiments\2026-11-19-puzzle-status\README.md`. Acesta leagă opt rapoarte Extent, identitățile exacte de sursă/runtime/configurație, deciziile brute, toate PGN-urile, transcripturile profesorului, analizele finale și 42 de lecții YAML contextuale. Lecțiile sunt numai pentru studiu: contraexemplele și transferul pe familii excluse rămân necesare, iar cheile nu intră în runtime. Amendamentul execuției păstrează toate rezultatele încheiate când crește paralelizarea independentă; încercările întrerupte rămân arhivate și nescorate.

Folosește [protocolul problemelor native](hard-puzzle-study.md), [benchmarkul canonic](../../atf-tests/src/test/java/ro/atf/chess/HardPuzzleStudyBenchmarkTest.java) și [benchmarkul cu toate apărările la mat](../../atf-tests/src/test/java/ro/atf/chess/NativeMateDefenseBenchmarkTest.java). [AiCalibrationObservationTest](../../atf-tests/src/test/java/ro/atf/chess/AiCalibrationObservationTest.java) păstrează contractul indiciului indisponibil cu mock, fără să fixeze un defect de motor sau să schimbe admiterea în corpusul de învățare. Izolează apoi **training-051: Ke6 față de Rc8+**, încă deschis, cu pierderea confirmată independent de 500‰, victorie→remiză; tactic-040 și tactic-095 sunt acum martori de regresie. Păstrează separat cele cinci înfrângeri din liga reținută față de această prioritate materială de catalog. Compară replicile libere la același orizont, păstrează fiecare etapă acceptată și toate cele 1.000 de mate, apoi verifică contraexemple și familii neatinse înaintea duelului direct al candidatului. [Lucrări deschise](../feedback/README.md).
