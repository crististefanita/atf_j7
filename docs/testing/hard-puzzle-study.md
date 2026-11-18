# Native Hard puzzle studies

## Overview

Measure the ordinary opponent before selecting an improvement. A declared-goal puzzle solver,
a correct curriculum key and a successful native game decision are different observations.
Use [the benchmark](../../atf-tests/src/test/java/ro/atf/chess/HardPuzzleStudyBenchmarkTest.java)
to audit every chapter without giving the opponent the objective, key or reference defense.
[AI protocol](../board/ai-quality.md#checks-for-every-change) · [Puzzle catalog](../board/quest-curation.md)

## Freeze and execute

1. Pin the source commit, compiled inputs, catalog, actual loaded policy and run ID before search.
   Keep the build, raw decisions and Extent reports under the external `C:\work\sah` study.
2. For each exercise, create the ordinary Hard match from its FEN. Call the opponent first;
   consult `quest.assess` and the curriculum key only after retaining its actual search report.
3. Preserve every canonical player stage, including misses. After an alternative or wrong move,
   continuing the reference path is a marked intervention, not a completed independent game.
4. Bind each receipt to **initial FEN + full canonical prefix + checkpoint**. A composed-match
   checkpoint alone does not encode its initial board. Preserve FEN clocks and en passant with
   `StudyFen`, rather than the start-position-only history formatter.
5. Freeze the failed-stage list before a budget sweep. Keep diagnostic YAML outside the sources.
   Separate search depth, actual completed depth, additional nodes and the unbudgeted H3 base.
   Rook final positions can use the configured additional endgame ply.

The baseline uses the live adaptive 100k→200k policy, H6 plus the endgame extension and the
separate 44k repertoire allowance. A fixed-budget or depth overlay is a different diagnostic
profile. Do not promote its failed-stage results into a whole-catalog success rate.

## Publish and interpret

From the external checkout, provide the exact native data directory, producer revision and
independently pinned digests for `native.jsonl`, `identity.json` and `catalog.json`:

```powershell
mvn test '-Dtest=HardPuzzleStudyBenchmarkTest' "-Dchess.puzzleStudy.output=$study" "-Dchess.puzzleStudy.revision=$producer" "-Dchess.puzzleStudy.nativeSha256=$nativeHash" "-Dchess.puzzleStudy.identitySha256=$identityHash" "-Dchess.puzzleStudy.catalogSha256=$catalogHash" "-Dchess.extent=$study/puzzles.html"
```

The integrity method must validate the complete catalog, raw digests, order, origin, actual
budget, legal PVs, material ledger and independently recomputed acceptance. Native receipts
cannot inherit hint-only score credits. A second method must fail when any exercise remains
incomplete; the integrity PASS cannot substitute for that chess-quality verdict.

Report first moves, all canonical stages and complete canonical exercises separately. Full
completion requires accepted native choices without a reference-path intervention. It does
not prove conversion against every free defense. Keep the objective-aware solver's results
under its own name and never attribute them to ordinary Hard.

For external verification, use cold Stockfish H17/H19 with identical initial FEN, prefix,
options and producer for each free/forced comparison. Retain raw UCI commands, final exact
score, WDL and legal PV. `StockfishFenAuditTest` rejects a start-position receipt projected
onto a composed position, altered clocks and cross-origin comparisons. A failed material
curriculum stage may still preserve a win; distinguish that from a verified WDL loss before
changing either the engine or the acceptance contract.

## Measured baseline and next experiment

Producer `711dff3f1bbb4c14aad682e7c319988dd35fce49` measures 1,238 exercises and 2,303
canonical player stages. The later receipt-reader correction changes no engine or search data.

| Chapter | First accepted | Canonical complete | Accepted stages |
|---|---:|---:|---:|
| Mate in 1 | 500/500 | 500/500 | 500/500 |
| Mate in 2 | 300/300 | 300/300 | 600/600 |
| Mate in 3 | 198/200 | 198/200 | 598/600 |
| Tactics | 93/100 | 85/100 | 201/221 |
| Training | 94/114 | 77/114 | 273/330 |
| Intermezzo | 24/24 | 22/24 | 49/52 |
| Total | 1,209/1,238 | 1,182/1,238 | 2,221/2,303 |

There are 82 failed stages in 56 incomplete exercises. On exactly those stages, fixed 200k/H6
recovers none, 200k/H7 recovers 20, and 500k/H7 recovers 26. All twenty recoveries survive the
larger profile. These arms do not remeasure successful stages or prove non-regression.

Eight failed stages selected before oracle analysis receive 50 raw H17/H19 FEN-origin queries.
Both depths confirm that `mate3-04iY6` and `mate3-019gx` turn mate in three into a loss under
all three budget overlays. `training-051` turns a win into a draw. The five other inspected
stages preserve a win under the oracle despite failing the curriculum goal; do not label them
five confirmed blunders or relax their goal silently.

The rook-endgame search retains only its scored shortlist as roots for the next horizon.
A diagnostic prototype readmitting all legal roots recovers both mating keys at 40,478 and
139,520 additional nodes, but still fails `training-051`. It remains external: three positions
do not qualify transfer, complete-catalog non-regression, free-defense conversion or Elo.
Use both mates, sound root exclusions, `training-051` and untouched rook families to test
whether a bounded re-entry policy can preserve correctness without unmeasured regressions.

Evidence: `C:\work\sah\studii\2026-11-18-j7-sicilian-candidate-coverage`. Start with its README,
then the full-catalog integrity/quality reports and the separate budget/oracle diagnostics.
[Current closure criteria](../feedback/README.md#overview)
