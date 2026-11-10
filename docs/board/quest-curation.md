# Repere pentru compoziția problemelor de mat

- **Mat descoperit și mutare neobișnuită.** Poziția Lichess P9xLh, observată la rating 2184, are cinci mutări de turn care dau mat prin descoperire. Ideea merită studiată. Pentru o compoziție J7, păstrați o singură retragere câștigătoare și verificați fiecare retragere alternativă. [P9xLh](https://lichess.org/training/mateIn1/P9xLh)
- **Șahuri înșelătoare.** În 0jbFz, observată la rating 2370 prin API, există 18 mutări legale, opt șahuri cu apărare și o singură soluție, `Txb6#`. La compoziție, urmăriți ca retragerile naturale să lase o apărare concretă, iar cheia să elimine tocmai acea apărare. [0jbFz](https://lichess.org/training/mateIn1/0jbFz)
- **Mat din poziție de șah.** În 16meG, observată la rating 2075 prin API, albul este în șah și are doar trei mutări legale. Două mutări ale regelui scapă de atac, iar `Nc2#` blochează șahul și deschide simultan linia de mat. În J7, `mate-101` exersează interpunerea, iar `mate-102` compară două capturi posibile ale piesei care dă șah. [16meG](https://lichess.org/training/mateIn1/16meG) · [Contra-maturile J7](README.md#reguli-și-probleme)
- **Construcție.** Porniți de la o idee distinctă, cum ar fi matul descoperit, blocarea ultimei fugi sau promovarea la cal. Dacă există o a doua soluție, schimbați poziția înainte de a o publica. Păstrați câte o apărare concretă pentru încercările cele mai tentante. [Codex WFCC](https://www.wfcc.ch/rules/codex/) · [Teme Lichess](https://lichess.org/training/themes)

Pentru o poziție nouă, validați FEN-ul, enumerați toate mutările în ambele motoare și cereți exact un mat. Dați poziției un ID propriu, independent de rang. Ratingurile Lichess reflectă încercări reale ale jucătorilor, iar scorul local folosește caracteristicile poziției. [Metodologia și formatul Lichess](https://database.lichess.org/)

Din copia TMP, după build, scorurile pot fi regenerate pentru revizie. Verificați apoi catalogul cu `QuestCatalogTest`.

```powershell
java -cp 'atf-tests/target/classes;target/maven-repo/org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar' ro.atf.chess.QuestDifficulty
```

## Mat în două mutări

Cele 100 de poziții provin din exportul CC0 Lichess, prin subsetul `lczerolens/lichess-puzzles-mate-in-2`. ID-ul `mate2-<PuzzleId>` păstrează legătura cu problema `https://lichess.org/training/<PuzzleId>`. Poziția afișată este FEN-ul sursă după prima mutare a adversarului. [Formatul și licența Lichess](https://database.lichess.org/#puzzles) · [Subsetul folosit](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/viewer)

Selecția are câte zece poziții cu fiecare culoare la mutare în fiecare dintre cele cinci intervale de rating. Ratingurile importate merg de la 427 la 2358. Verificarea respinge matul imediat, cheia alternativă și orice apărare fără continuare de mat. Catalogul păstrează linia de referință, iar dovada independentă acoperă toate apărările și toate maturile din fiecare ramură.

Pentru reimport, salvați în TMP paginile `rows-<offset>.json`, cu 100 de rânduri pe pagină. Selecția inițială a folosit offseturile 0, 100, 200, 300, 400, 500, 2000, 3000, 10000, 14000, 20000, 40000, 50000, 80000, 90000, 100000, 200000, 400000 și 600000. [Revizia datelor](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/tree/8c195a0696c30c8371d276db0467e833f1b72468)

```powershell
node scripts/verify-mate-in-two.cjs <cale-catre-chess.js> --import <pagini-TMP> <catalog-TMP.yaml>
```
