# Native Hard game audit

## Overview

- **One search per position.** `ChessMatch.analyzeBot(HARD)` returns the selected move and the report from that same native decision. `HardAuditedGame` caches its receipt until a legal move advances the checkpoint.
- **Every played ply.** Frozen opening moves and Stockfish replies also require a Hard shadow analysis before play. The shadow suggestion stays separate from the actual move.
- **Qualification.** [Component contracts](../../atf-tests/src/test/java/ro/atf/chess/HardAuditedGameTest.java) reject missing or foreign evidence. The [league publisher](../../atf-tests/src/test/java/ro/atf/chess/HardLeagueAuditTest.java) replays raw receipts before publishing the rating in Extent.

## Search identity and interpretation

Pin the source commit, compiled classes, engine resources, runner, oracle binary and frozen start deck before generation. Recheck production sources and classes after all games finish. The measured engine revision remains distinct from the later verifier revision shown in Extent.

Each receipt contains the full checkpoint and FEN, side and ply, actual native choice and rank, theory reference, completed depth, charged additional nodes, and every returned PV with native score, material change and ending. Native scores can include historical horizon credit. They are not Stockfish evaluations or calibrated win probabilities.

Normal Hard starts with 100k additional nodes and can continue to 200k. Repertoire and explicit paths retain their own policy. This differs from the hint's single 200k total ceiling. The audit calls the actual game API, preserving history, draw selection and repetition; a second position-only hint search cannot impersonate that decision. [Policies](../board/ai-quality.md)

## Receipt validation

`HardMoveAudit` replays every PV and validates legality, material, terminal labels, clocks and rights. A zero-score PV ending before its horizon is accepted only when the full prior game together with the searched path proves at least three occurrences. A same-FEN counterexample with a different earlier history must fail. [Native receipt tests](../../atf-tests/src/test/java/ro/atf/chess/HardMoveAuditTest.java)

A game cannot play before analyzing its current checkpoint. An invalid move retains the pending receipt; a legal move consumes it. The league verifier rejects skipped, repeated, reordered or foreign receipts and requires Hard to play its actual audited choice.

The separately trusted manifest digest pins six fixed roles: combined move JSONL, PGN, results, runtime, freeze and starts. The verifier ignores received path names, replays every game to its native result, checks both colors per start and recomputes the paired rating. An internal hash chain alone cannot authenticate a consistently rewritten bundle. Negative components replace bytes, rewrite the manifest and substitute source or phase identity.


## Position-only puzzle audit

`HardQuestTransferBenchmarkTest` measures the native opponent separately from the objective-aware solver. One `analyzeBot(HARD)` call supplies the move and actual search. `HardQuestAudit` validates PV legality, material, endings and accounting identity. Extent retains the native report for every position before grading the move against the catalog oracle.

The ceiling follows the actual path: exact repertoire retains the explicit allowance from `match-ai.yaml`, while an exit uses active `hard-adaptive.yaml`. One node over either ceiling fails. Reported nodes here are additional: initial H3 search remains outside that counter under the native API. Do not label this value as the total of all visits. Correcting accounting does not change `hard-quest-transfer.yaml` floors or the frozen training boundary. The report measures first decisions, without proving every reply or complete conversion.

[Audit contracts](../../atf-tests/src/test/java/ro/atf/chess/HardQuestAuditTest.java) · [Native benchmark](../../atf-tests/src/test/java/ro/atf/chess/HardQuestTransferBenchmarkTest.java)

## Running and publishing a calibration

Build in an external workspace under `C:\work\sah`, with `TEMP`, `TMP` and the JVM temporary directory there. Freeze source, settings and paired starts before observing results. Generate a fresh bundle with a producer implementing the receipt contract. The component suite does not automatically launch a league.

Independently replay SAN PGN and raw receipts. Record the manifest digest outside the received bundle, then explicitly select the benchmark publisher from the external checkout:

```powershell
mvn test '-Dtest=HardLeagueAuditTest#publishesEveryNativeMoveAndThePairedHardRating' `
  "-Dchess.hardLeague.output=$bundle" "-Dchess.hardLeague.manifestDigest=$trustedDigest" `
  "-Dchess.hardLeague.revision=$measuredCommit" "-Dchess.extent=$freshExtent" `
  "-Dchess.qualification.commit=$verifierCommit" "-Dchess.qualification.runId=$runId"
```

The publisher fails for an absent bundle, wrong source, modified artifact, missing analyses, incomplete color pairs or invented result. It emits assertions for every native decision and PV, then one source-qualified rating. `qualified-rating.json` separates local performance Elo from the difference relative to the anchor.

## Elo and unfinished games

The measured opponent is Stockfish 19, limited strength at `UCI_Elo 1500`, one thread, 16 MiB hash and 6,000 requested nodes per move. Record actual returned nodes too. Keep one Stockfish session per game. The estimate describes performance against that configuration, not a FIDE or Chess.com rating. [Stockfish calibration](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

Use opening pairs as bootstrap units, 20,000 samples and the frozen seed. Keep W/D/L/U separately. Unfinished games at the declared cap remain `*`; 0.5 is used only for the central estimate. Publish sensitivity with all unfinished games lost or won, plus the conservative 95% interval including those extremes. Independent Python and Java calculations use different PRNG implementations, so interval quantiles can differ slightly; the point estimate and outcomes must agree.

An interrupted run remains diagnostic evidence. Restart the frozen experiment when a validator correction would otherwise require pooling different producers or sessions. Preserve old raw files and explain their exclusion. A hint-only correction does not establish a causal change in foreground Hard strength, and a smaller calibration does not silently replace the app's governed anchors.

[Hint audit](hint-audit.md) · [Testing strategy](README.md) · [Current AI evidence](../board/ai-quality.md)
