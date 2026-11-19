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

## Qualification and next steps

Use `RookRootReentryTest` for the two formerly omitted mate roots, both colors and all legal
defenses. `NativeMateAudit` owns the shared native traversal. Run the complete portable
benchmark from the external checkout to recompute every existing mating exercise:

```powershell
mvn test '-Dtest=NativeMateDefenseBenchmarkTest' "-Dchess.extent=$study/native-mates.html"
```

It calls the ordinary opponent before consulting the educational acceptance result. Every
legal defender move is explored, every actual native choice retains its depth, nodes and PV,
and every terminal board must be mate within the original goal. It does not use a declared-goal
solver or need an emulator. Keep the full canonical benchmark for material exercises separately.

After a change, compare every previously accepted canonical stage and freeze untouched transfer
families before inspecting outputs. Preserve an interrupted-confirmation negative, exact-cache
and repetition controls, and the current budget. A valid receipt and a legal PV do not substitute
for the quality verdict. Retain rejected variants even when their catalog total improved.

Classify all still-unaccepted stages with both oracle depths. Start with the first actual WDL
loss after checking previously accepted prefixes, not a later state reached only through a reference intervention. Save the initial FEN,
complete prefix, played move, curriculum key, actual search and opposing oracle line. Keep
win-preserving educational misses distinct; never feed a memorized key into ordinary Hard.
Prioritize the current catalog before adding public exercises. New verification compositions
are separate transfer evidence, not additions to the app.

[Current measured inventory and mechanism](../board/ai-quality.md#qualified-rook-mate-readmission)
· [Open work](../feedback/README.md#overview)


## Loaded policy and extended continuations

Bind the native producer to its actual `promotionExchangePolicy` snapshot as well as the
adaptive node policy. Changing diagnostic YAML makes a separate profile, even when source
revision and maximum nodes match. Preserve old receipts with their original producer and
reader; do not rewrite historical headers to claim a policy that was not loaded.

For a promotion exception, require exact full-window dominance at both completed even
horizons, legal capture/recapture and actual material gain in the retained PV. Test interrupted
confirmation at a one-node boundary with fixed maximum and cache. Disabling the feature must
restore the complete reference decision, depth, node count and PV. Reject a receipt declaring
a foreign policy even if its file digests have been recomputed.

Freeze the continuation contexts and ply limit before looking at oracle results. Follow the
actual native move and a freely chosen opposing reply; record both reference and candidate
analyses at each identical position, plus Basic/Advanced isolation controls. Grade every native
root at H17/H19, retain the actual PGN and explain termination or the diagnostic cap. Compare
WDL loss as `max(0, free expected score - forced expected score)` through `StockfishStudyAudit`;
raw exact scores and PVs remain immutable. A solved curriculum stage cannot certify conversion
of that advantage. Keep the newly exposed first loss as contextual YAML with its full origin,
history, adversarial line and counterexample, separate from runtime pattern configuration.


## Auditing winning-plan certificates

Keep ordinary horizon and a forced plan's coverage separate. A long winning continuation
does not establish full-root minimax at that depth. Pin `pawnPushPolicy` in current producer
metadata and retain `pawnProof` with its actual work, plan length, quiet clock, history and
strategy identity. Ordinary work plus proof work must fit the same native allowance.

The native test adapter has one explicit receipt decoder, including move-keyed collections:

```java
var nativeSearch = HardQuestAudit.read(receipt.path("analysis"));
HardQuestAudit.validate(game, nativeSearch);
```

Use the full composed match, not only its current board, when a proof is present. Recompute
the bounded strategy with its declared cache and visits; reject removed certificates on long
PVs, changed opposing coverage, history, strategy, ledger or score. Old non-proof JSON may omit
the optional field. Keep its original producer; do not invent current policy metadata for it.

Include interrupted-budget, unsafe promotion, stalemate, rook-pawn draw, foreign-history and
fifty-move-claim negatives. Retain Basic/Advanced decisions at identical contexts. Verify actual
conversion to checkmate or draw separately from a certificate ending in safe queen material.
Frozen transfer root-WDL checks cannot substitute for full games or paired Elo evidence.


Keep the original FEN draw clock distinct from a newly constructed board-only session.
`ChessMatch.fromPosition` starts at zero; `StudyFen` retains the input clock. A source-bound
prefix cannot alone supply an omitted initial clock. The qualified post-capture conversion
has equal counters after reset; arbitrary composed origins still require the explicit
clock-adapter follow-up recorded in feedback.

Baseline status and candidate promotion are separate operations. Measure the ordinary native catalog before consulting keys, keep all-defense mate proofs separate from canonical material stages, and retain unfinished games as outcome intervals in paired rating estimates. The [measured snapshot](hard-status.md) records actual source and protocol; updating a status must not change runtime budgets, curriculum acceptance or displayed app ratings.

An unavailable hint has a typed failure, no final coaching line or score, and its own completion verdict. It must not replace or cancel the independent playing profile. Preserve the durable prefix and every original failed attempt during an explicit technical recovery; disclose any teacher-session reset. Outcome integrity may pass while coaching completion fails. Such a game is not a complete-hint learning-corpus observation.
