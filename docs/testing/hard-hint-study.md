# Native hints and Stockfish audit

## Overview

- **Capture.** `HardHintAuditedGame` requires the actual Hard decision, public hint and displayed estimate before every played move, including opening prefixes and the opposing turn. Repeated inspection reuses the same receipt.
- **Oracle.** Stockfish 19 evaluates the full played history at H17 and H19 with opposing replies free. Raw UCI commands and output remain in one source-bound JSONL file.
- **Verdicts.** Evidence integrity and chess quality are separate benchmark methods. A valid audit must still report inaccurate suggestions as a quality failure. [Publisher](../../atf-tests/src/test/java/ro/atf/chess/HardHintStudyBenchmarkTest.java)

## Paired capture

Native Hard keeps its configured 100k–200k additional-node allowance; initial H3 is outside that counter. The hint has its independent 200k total, including seed, tactical frontier and comparison. The bar uses its own estimate. None of these native centipawn scores is a Stockfish score or a calibrated win probability. [Policies](../board/ai-quality.md)

The receipt retains checkpoint, FEN, side, clocks, rights, actual native PVs, every offered hint, diagnostic comparison candidates, selected and seed horizons, nodes and displayed White estimate. Public suggestions need not equal the native choice. Playing a different legal opposing move preserves its identity rather than relabeling the suggestion as played.

[Paired-capture components](../../atf-tests/src/test/java/ro/atf/chess/HardHintAuditedGameTest.java) reject missing, altered or foreign evidence. Invalid play retains the pending receipt; a legal move consumes it. The collector runs each native/hint search once per checkpoint.

## Oracle verification

Use cold Stockfish 19 processes, one thread, 32 MiB hash, full strength and WDL output. Only a tested first move is constrained. Grade every actual choice and public suggestion against a free-root search at the same history and completed horizon. Independently grade the first opposing reply in each PV and inspect its endpoint. Deeper hypothetical replies are not exhaustively graded by this collection.

`StockfishStudyAudit` checks the binary, run and source, exact commands, achieved depth, unbounded UCI score, WDL, actual nodes and legal PV. A cutoff bound cannot impersonate an exact value. Identical FEN and clocks do not authorize a different history. Query projection rejects a missing offered variant, another root or another horizon. [Negative components](../../atf-tests/src/test/java/ro/atf/chess/StockfishStudyAuditTest.java)

Expected score is `W + D/2` in permille for the side to move. Retain raw free and constrained values; diagnostic loss is their difference clamped at zero. The fixed large-loss boundary is 150‰. Small differences between separate cold searches are not Elo gains. [Study policy](../../atf-tests/src/test/resources/hard-hint-study.yaml)

## Offline publication

Generate in a clean external checkout under `C:\work\sah`. Pin source, compiled runtime, producer, protocol, YAML and oracle binary before play. Keep source and classes unchanged during generation. After independent replay, retain the expected file digests outside the received bundle.

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

Publication replays native and hint evidence without Stockfish or an emulator. Every raw row, played phase, planned oracle request and offered root must be present. Missing digests, an empty study or a substituted phase fail. The measured producer commit remains distinct from the later verifier commit.

## Interpreting the three verdicts

- **Integrity:** source, history, raw receipts and projection are complete and reproducible.
- **Hint quality:** first suggestions, all offered variants and first opposing replies stay below the frozen large-loss boundary. An integrity PASS does not imply this PASS.
- **Game Review:** `analyzeLine` preserves the recorded line and produces no rating for a capped study. Compare its labels against played-move H17/H19 evidence; disagreement is a separate finding. Local logistic points and Stockfish WDL are different models.

These studies do not estimate Elo or prove optimal play against every defense. Use the [paired native league](hard-audit.md#elo-and-unfinished-games) for source-qualified strength and confidence intervals. Repairing one fixture does not permit promotion when independent controls regress.

## Reference source-qualified collection

The November 18 collection uses producer `e320379212dbb55afb392351da559b1cf949ac4d`: three study games capped at 20 plies, 60 played positions, 83 offered variations and 960 oracle requests. The positions include both native and shadow decisions. At H19, 12 first suggestions, 15 offered variations and 10 opposing PV replies lose at least 150‰. Native choices have three such gaps across all 60 positions, two on its 21 actual turns. Game Review has nine stable classification disagreements across H17/H19.

The quality benchmark therefore fails; the evidence benchmark passes. The live playing engine is unchanged. The applicable native Elo remains 1,507, conservative 95% interval 1,357–1,656, from the separately qualified 100 games. No Elo is inferred from the three unfinished paths.

Raw data, both Extent verdicts, review observations and study-only YAML lessons are retained under `C:\work\sah\studii\2026-11-18-j7-h6-recapture-hint-games`. The node-limited opponent used to produce moves is separate from the full-depth oracle used to grade them.

## Follow-up

Keep the corrected Caro–Kann pair before and after `6.e6` as a regression control. Sicilian `…Be7` versus `…Nc6` and inaccurate secondary offers remain unresolved source-bound studies. Compare roots and defenses at one horizon and budget. Do not turn observed solutions into runtime FEN exceptions or automatically trusted training labels. Preserve the original corpus when evaluating a later candidate. [Current feedback](../feedback/README.md)

## Comparing the corrected Caro–Kann hint

The forcing-pawn correction is measured on a paired replay of exactly the same historical moves. Its producer is `5858e65e02b96e15ca8238d1d68cc2db70f2b6d8`; historical opponent replies are explicitly marked as replayed, not new Stockfish games. All sixty native reports and displayed estimates match the reference. Only the public hint after `6.e6` changes.

The new plan contains 954 cold H17/H19 requests covering all played and offered roots, first opposing PV replies and endpoints. Integrity passes; absolute quality remains FAIL with 11/60 deficient leaders, 14/83 deficient offered moves and 10/83 deficient first opposing PV replies. Qualify the targeted improvement and independent transfer separately from that absolute floor. Keep rejected runs, source identity and raw receipts intact. [Results and interpretation](../board/ai-quality.md#carokann-hint-and-forcing-pawn-recapture-verification)

`HintRecaptureRegressionTest` verifies the Caro pair, diagnostic comparison, preview and tap identity, interrupted budgets, exact-cache mode separation and bounded YAML. Disabling the new allowance must reproduce `…fxe6` and fail the target test. New PGN transfer histories are frozen before evaluation, then become regression cases after inspection. A positive hint delta is not a native-game Elo delta. [Components](../../atf-tests/src/test/java/ro/atf/chess/HintRecaptureRegressionTest.java)

## Italian comparison and new thematic histories

Producer `3b1e7ed1a4f2659b544d29732848d8078c5f3abc` fixes `Nd5/Na4` after `4…Qf6`. Keep the same sixty historical phases for the matched comparison, with native choices and displayed estimates preserved. Record the two additional sixteen-ply branches as fresh thematic studies, not as a replacement baseline or a rating league. Their opening prefixes overlap deliberately; `Na4` is a labeled inferior-prefix counterexample.

All 92 played phases have paired native/hint evidence. The 1190-query raw plan also covers diagnostic comparison roots, with their actual histories and horizons. Source and compiled runtime remain frozen during generation; an independent chess-rules replay verifies every PGN and checkpoint. The integrity report passes. The absolute-quality report still fails: the matched sixty retain 9 large-loss leaders, 12 deficient offers and 9 deficient first opposing replies. New thematic observations are reported separately, without pooling them into an apparent improvement.

`HintQueenRecaptureTest` covers public and manual-board identity, an explicit cold common-horizon comparison, legal and pinned recaptures in both colors, geometric allocation versus legality, exact cache modes, incomplete budgets and a sound queen sacrifice. Setting the existing recapture allowance to zero reproduces `Na4` and genuinely fails the target assertion. No emulator is needed for these search contracts. Validation preserves the small 13.5‰ leader decline and the 322.5‰ improvement; it must not claim zero regressions or exact score calibration. [Mechanism and measured limits](../board/ai-quality.md#italian-queen-recapture-and-hint-continuity) · [Components](../../atf-tests/src/test/java/ro/atf/chess/HintQueenRecaptureTest.java)
