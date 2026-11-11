# Repere pentru compoziția problemelor de mat

- **Mat descoperit și mutare neobișnuită.** Poziția Lichess P9xLh, observată la rating 2184, are cinci mutări de turn care dau mat prin descoperire. Ideea merită studiată. Pentru o compoziție J7, păstrați o singură retragere câștigătoare și verificați fiecare retragere alternativă. [P9xLh](https://lichess.org/training/mateIn1/P9xLh)
- **Șahuri înșelătoare.** În 0jbFz, observată la rating 2370 prin API, există 18 mutări legale, opt șahuri cu apărare și o singură soluție, `Txb6#`. La compoziție, urmăriți ca retragerile naturale să lase o apărare concretă, iar cheia să elimine tocmai acea apărare. [0jbFz](https://lichess.org/training/mateIn1/0jbFz)
- **Mat din poziție de șah.** În 16meG, observată la rating 2075 prin API, albul este în șah și are doar trei mutări legale. Două mutări ale regelui scapă de atac, iar `Nc2#` blochează șahul și deschide simultan linia de mat. În J7, `mate-101` exersează interpunerea, iar `mate-102` compară două capturi posibile ale piesei care dă șah. [16meG](https://lichess.org/training/mateIn1/16meG) · [Contra-maturile J7](README.md#reguli-și-probleme)
- **Construcție.** Porniți de la o idee distinctă, cum ar fi matul descoperit, blocarea ultimei fugi sau promovarea la cal. Dacă există o a doua soluție, schimbați poziția înainte de a o publica. Păstrați câte o apărare concretă pentru încercările cele mai tentante. [Codex WFCC](https://www.wfcc.ch/rules/codex/) · [Teme Lichess](https://lichess.org/training/themes)

Pentru o poziție nouă, validați FEN-ul, enumerați toate mutările în ambele motoare și cereți exact un mat. Dați poziției un ID propriu, independent de rang. Ratingurile Lichess reflectă încercări reale ale jucătorilor, iar scorul local folosește caracteristicile poziției. [Metodologia și formatul Lichess](https://database.lichess.org/)

## Configurarea capitolelor

| Capitol | Ce se editează |
|---|---|
| Mat în 1 | ID, FEN, soluție UCI și temă în [quests.yaml](../../chess-core/src/main/resources/quests.yaml). Introducerea, ponderile și departajările se configurează în [quest-difficulty.yaml](../../chess-core/src/main/resources/quest-difficulty.yaml). |
| Mat în 2 | FEN, linie UCI și rating în [mate-in-two.yaml](../../chess-core/src/main/resources/mate-in-two.yaml). Păstrați ratingurile în ordine crescătoare. La rating egal contează ordinea din listă. |
| Mat în 3 | Același format, cu cinci semimutări, în [mate-in-three.yaml](../../chess-core/src/main/resources/mate-in-three.yaml). Poziția `mate3-0Ytul` înlocuiește exercițiul apropiat `mate3-02GAF` la același rating. |
| Tactică | FEN, soluție UCI și `difficulty` în [tactics.yaml](../../chess-core/src/main/resources/tactics.yaml). Folosiți valori întregi pozitive distincte. Selecția actuală pornește de la capturi directe și trece la mutări fără captură imediată. |

Pentru Mat în 1, ponderile mai mari mută mai târziu pozițiile care au caracteristica respectivă. O departajare de la 1 la 9 se aplică numai când cel puțin două poziții au același scor de bază. Generatorul raportează ID-urile oricărei egalități rămase și refuză o poziție cu mai multe soluții. Rulați din copia TMP, cu JDK 21 și Maven. Pentru verificarea independentă, setați `$chessJs` la fișierul CommonJS din chess.js 1.4.0. Adăugați `-Offline` dacă dependențele sunt deja în cache sau `-MavenRepo` dacă folosiți alt depozit Maven.

```powershell
./scripts/update-mate-one-order.ps1 -ChessJs $chessJs
./scripts/update-mate-one-order.ps1 -Write -ChessJs $chessJs
```

Prima comandă compară ordinea existentă cu cea calculată. A doua regenerează ordinea după modificarea ponderilor. După o schimbare revizuită de poziții sau soluții, adăugați `-RefreshOracle` la a doua comandă, apoi rulați suitele de catalog. Chess.js recalculează dovada din pozițiile exportate de parserul YAML al proiectului.

Păstrați ID-ul la reordonare. Pentru o înlocuire, puneți ID-ul vechi în registrul de retrageri și atribuiți un ID nou poziției. Verificarea compară toate cele patru capitole. Normalizează culoarea și reflexiile permise, inclusiv rotațiile unei table fără pioni și fără drepturi de rocadă. Pentru poziții apropiate compară tipul mutării, destinația, câmpul regelui advers și maximum zece câmpuri diferite. Cele 31 de poziții noi de Mat în 1 provin din partidele offline de calibrare. Comentariile din YAML indică partida, semimutarea și eventualul schimb de culori și reflexie. [Registrul de ID-uri](../../chess-core/src/main/resources/quest-retired.yaml) · [Comenzi de verificare](../testing/README.md)

## Calibrarea dificultății

Pentru calibrarea cu jucători, alternați ordinea unui eșantion din începutul, mijlocul și finalul capitolului. Includeți `mate-101` și `mate-102`, care au doar șase, respectiv cinci mutări legale, dar încep în șah. Includeți `mate-129`, care are 14 șahuri înșelătoare, dintre care șase cu o singură apărare. Măsurați timpul și încercările greșite înainte de primul indiciu. Scorul local este o estimare până la această calibrare.

## Mat în două mutări

Cele 100 de poziții provin din exportul CC0 Lichess, prin subsetul `lczerolens/lichess-puzzles-mate-in-2`. ID-ul `mate2-<PuzzleId>` păstrează legătura cu problema `https://lichess.org/training/<PuzzleId>`. Poziția afișată este FEN-ul sursă după prima mutare a adversarului. [Formatul și licența Lichess](https://database.lichess.org/#puzzles) · [Subsetul folosit](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/viewer)

Selecția are câte zece poziții cu fiecare culoare la mutare în fiecare dintre cele cinci intervale de rating. Ratingurile importate merg de la 427 la 2358. Verificarea respinge matul imediat, cheia alternativă și orice apărare fără continuare de mat. Catalogul păstrează linia de referință, iar dovada independentă acoperă toate apărările și toate maturile din fiecare ramură.

Pentru reimport, salvați în TMP paginile `rows-<offset>.json`, cu 100 de rânduri pe pagină. Selecția inițială a folosit offseturile 0, 100, 200, 300, 400, 500, 2000, 3000, 10000, 14000, 20000, 40000, 50000, 80000, 90000, 100000, 200000, 400000 și 600000. [Revizia datelor](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/tree/8c195a0696c30c8371d276db0467e833f1b72468)

```powershell
node scripts/verify-mate-in-two.cjs <cale-catre-chess.js> --import <pagini-TMP> <catalog-TMP.yaml>
```

## Mat în trei mutări

Cele 100 de poziții provin din baza Lichess CC0. Selecția are 50 de poziții cu albul la mutare și 50 cu negrul, ordonate după ratingul importat. ID-ul `mate3-<PuzzleId>` trimite la problema originală Lichess. [Baza Lichess](https://database.lichess.org/#puzzles) · [Revizia subsetului](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-3/tree/680d12e5e6447800d5cbe2b2321d4cffd6a024d1)

Ambele motoare verifică o singură primă mutare care forțează matul în trei, fără soluție mai scurtă. Pentru fiecare primă apărare se verifică toate mutările care forțează matul în următoarele două. Linia jucată în aplicație are și a doua mutare unică.
