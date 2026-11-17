# AI Evaluation in Chess Lab

## Overview

- **Current policy.** The opponent, hint, and evaluation bar have separate budgets. Hard foreground search starts with 100,000 additional nodes and can use an adaptive reserve up to 200,000. The hint can use 220,000, including up to 32,000 to compare alternatives. [Search configuration](../../chess-core/src/main/resources/match-ai.yaml)
- **Acceptance.** `MatchAiGovernanceTest` and `HardAdaptivePolicyTest` check the policies and `ChessBenchmarkSuite` measures decisions against a fixed oracle. Historical comparisons below belong to the measured version.
- **Displayed Elo.** The [Game Review contract](game-review.md) defines grades, accuracy, and the displayed estimates. The app reads its rating anchors from the release configuration. [Rating anchors](../../chess-core/src/main/resources/game-review.yaml)

<details>
<summary>Current search policies and bounds</summary>

The opponent levels and hint have separately configured depths and budgets. A search keeps the last depth it evaluated completely. Hard's foreground game search starts with 100,000 additional nodes and can continue to 200,000 with the adaptive policy below. It accepts the final horizon's result only if it keeps the same leading move as the previous depth. If the leader changes, the engine stays with the last complete, stable search. The hint attempts six plies, with one additional ply for captures, promotions, or escaping check at the search horizon. In games and lessons, evaluation accounts for piece development, the center, king position, and the bishop pair in addition to material. The weights change according to the remaining material so an active king becomes useful in the endgame. The search checks captures, promotions, and moves that activate pieces first. Tactical tests keep their explicit material or checkmate objective. [Search policy](../../chess-core/src/main/resources/match-ai.yaml) · [Adaptive policy](../../chess-core/src/main/resources/hard-adaptive.yaml)

In rook endgames, Hard uses the full history to evaluate repetitions throughout the tree, including at the root. It can extend the search by one ply beyond the profile depth, up to depth 7, within the configured adaptive allowance. The extension and the 50 cp window for root candidates are set under `rook-endgame` in YAML. Governance rejects extensions or windows outside the validated limits.

In Hard games outside the repertoire and the special rook endgame, checking moves get priority in search order only. The 10,000 bonus is configurable and capped under `hard-ordering`. It does not contribute to position evaluation or change the node allowance. Basic, Advanced, the hint, and the bar evaluation retain their previous ordering.

Hard's game search also reuses exact lines for transposed positions. Incomplete entries and alpha-beta bounds are excluded from reuse. Capacity is derived from the budget and capped in `hard-transpositions`. [Exact-cache evaluation](#exact-cache-for-hard)

Basic searches two plies in ordinary positions and three in elementary rook or queen endgames against a king. Both paths use zero additional nodes. Configure ordinary depth with `standard-max-plies` in YAML. Hard ends the search at the first complete horizon that proves a winning mate. [Basic calibration](#calibrating-basic-with-selective-depth) · [Mate proofs](#solving-puzzles-with-a-declared-objective)

The hint starts from the Hard search and checks mates and tactical replies separately. If the first moves differ and neither proves mate, the engine compares alternatives at the depth Hard already completed. If verification exhausts its budget, the hint uses the complete Hard result. The list displays at most three distinct first moves. Alternatives stay within 25 cp of the first move after the defense and retain an estimated positive improvement. In a mating position, only variations with the same result and distance remain. On equal scores, move activity and UCI order determine the order. [Reading the difference](README.md)

In Hard, in positions with equal material, the ranking available after four plies also uses the score of the same move at three. The shorter line's contribution is capped. For a quiet move, the credit falls when the provisional line suggests a gain of more than one pawn. Evaluated mates and draws retain their terminal scores. If the budget prevents depth six from completing or the leader changes at that depth, the engine keeps the last complete search. Explicit searches and coaching comparisons keep their declared budgets.

In the Pirc and Benoni, Hard and the hint each use a reply checked with Stockfish 19 at depth 13. The repertoire applies to the same legal position, including through transposition, and the continuation is searched within the profile budget. After the reply or in neighboring positions, the full search is used. Loading checks legality, duplicate positions, and mates in one. [YAML repertoire](../../chess-core/src/main/resources/ai-repertoire.yaml)

In a cycle, Hard uses history before discarding alternatives. If the current position has already appeared twice, a move that would produce a third or later occurrence receives 0 cp at the search root. The engine compares alternatives within the same budget at the last completed depth. Among lines that remain favorable, Hard may choose a less-visited position if it retains an advantage of at least 100 cp and gives up at most 50 cp relative to the first line.

The third occurrence is a signal for move choice. The game ends automatically on the fifth occurrence, and Hard can choose that draw if it sees no advantage in the cycle. [YAML policy](../../chess-core/src/main/resources/match-ai.yaml) · [Regression game](../../atf-tests/src/test/resources/repetition-regression.yaml) · [FIDE Laws, arts. 9.2 and 9.6](https://handbook.fide.com/chapter/E012023)

The bar and first line of text use a separate estimate for the current position. The engine evaluates two plies, one for each side, and applies a fraction of the difference obtained at three. Both searches check exchanges at the horizon. The correction is capped at 25 cp within the display's own budget. Pressing the lightbulb preserves the displayed estimate and cycles through the lines in hint-search order.

</details>

## Adaptive foreground budget

`hard-adaptive.yaml` configures a base allowance of 100,000 and a maximum of
200,000 additional nodes for normal Hard game decisions. At the base boundary,
the same search can use the reserve if it has not completed five plies, the
current king is in check or exposed to a heavy piece on a central file, or exact
cache reuse is below 1% after at least 4,096 observations. An unresolved pawn
exchange identified by the existing candidate check is also a tactical reason
to continue its verification. The current defaults
target six plies. Both bounds, depth, observation count, and cache threshold are
typed configuration values.

Continuation retains the current recursive stack, move ordering, and the
transposition table of that decision. The search does not replay the root after
requesting the reserve. A new game decision starts with its own evidence.
Stopping preserves the last complete result. A known exchange verification can
still spend its reserved nodes. When the fifth ply finishes, both exchange
alternatives are compared at that same depth with the tactical extension. That
verification shares the adaptive controller and a global spent-node offset. It
requests the reserve only when an actual visit crosses the base boundary. The
older exhaustion path keeps its separately reserved allowance.

Disabling the adaptive policy restores the profile declared in `match-ai.yaml`.
The fallback is also used by explicit comparison paths. Hint, evaluation bar,
Basic, and Advanced keep their own budgets and entry points. Elo evidence must
name the exact source and active policy. The experimental learned-ordering
league is a separate source-qualified comparison.

[Component and regression checks](../testing/README.md#adaptive-hard-budget)

On the main-source comparison, fixed 100,000 completed at least five plies on
39/44 positions; fixed 200,000 and adaptive completed them on 43/44. Adaptive
retained all 44 choices of fixed 200,000 while using 124,062 additional nodes per
decision on average, compared with 187,184 for fixed 200,000. The reserve was used
on 12 positions. It recovered `…Ng4` in the early queen sortie and kept the queen
recapture in the king-shield control. All three modes still chose `…Bh3` in
`fresh-030`. This sample measures decision transfer and cost on that source.
The adaptive profile's game-strength estimate requires its own paired league.

## Checks for every change

1. Run the JVM suite before making the change. Compare from the same TMP checkout, with the same Stockfish executable and evaluation YAML. Keep the Extent report from each run.
2. Record the position where the decision was weak, the move chosen, the missed reply, and the reference evaluation. Use these examples to tune the search. Keep the control set fixed during tuning and add new positions separately for verification.
3. Compare Basic, Advanced, Hard, and the first recommendation for each position. Track average expected-score loss, the count of large losses, and exact matches. Check the completed depth, additional nodes, and latency. Compare latency on the same device under similar conditions.
4. When a regression occurs, open the positions with the greatest losses in Extent and check whether the move lost points because of the horizon, a tie, or an exhausted budget. Keep thresholds fixed during the experiment and justify any revision to the set or thresholds separately.

```powershell
mvn test '-Dtest=ChessBenchmarkSuite' "-Dchess.stockfish=$stockfish" '-Dchess.extent=target/match-ai-benchmark.html'
```

- Set `$stockfish` to the Stockfish 19 executable. The check starts one search at the depth fixed in YAML for the full position and another for the J7 move, with one thread, 16 MiB of hash, and memory reset between searches. The Stockfish expected score is `W + D/2` per thousand. The difference between searches is floored at zero when the restricted search finds a better evaluation. The report also shows the UCI score for each move to expose mates or differences when the expected score is already saturated. [Set and thresholds](../../atf-tests/src/test/resources/match-ai-benchmark.yaml) · [Stockfish 19](https://github.com/official-stockfish/Stockfish/releases/tag/sf_19) · [Stockfish WDL scores](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

- These set scores measure move choice in fixed positions. For an Elo estimate, use game pairs from the same openings with reversed colors, fixed time, and a confidence interval. Keep game results separate from this position report.

## Offline league

| Profile | Maximum depth | Additional nodes |
|---|---:|---:|
| Basic | 3 | 0 |
| Advanced | 4 | 10,000 |
| Hard foreground | 6, with a stable leader | 100,000 base, up to 200,000 adaptively |
| Hint | 6, plus one tactical extension | 220,000 total, including up to 32,000 to compare alternatives |

- Governance requires strictly increasing depth and budget across opponents. The hint uses at least Hard's depth and its own larger budget. In king-and-rook or king-and-queen endgames, all levels, the hint, and the bar use the same evaluation of the distance between the kings and the defending king's space. Tests verify conversion of the advantage into checkmate with both colors and preservation of White's perspective on the display.

- The league protocol uses the same openings with both colors and the same
  budgets. An unfinished game contributes an interval, and relative Elo is
  reported with its uncertainty.

<details>
<summary>Historical Hard6 benchmarks and rejected experiments</summary>

### Hard6 benchmark before the exact cache

This section preserves Hard6 results before the exact cache described below.
These figures measure the earlier candidate.

Hard kept the 44,000-node cap, and Basic and Advanced retained their depths and budgets. A check without the stability filter dropped Intermezzo to 22/24. The version kept in the live candidate accepts the depth-6 result only if its leading move matches the one from depth 5. Otherwise it uses the prior complete result.

Hard transfer met the frozen floors: mate in 1 500/500, mate in 2 300/300, mate in 3 190/200, tactics 87/100, training 40/48, and Intermezzo 24/24. The internal league measured Hard–Advanced at 20/1/0/3 and central paired Elo of +417 (95% interval +172…∞), versus the earlier benchmark of 19/1/0/4 and +374. This is one internal league with three unfinished games. The modest difference falls short of a new Elo calibration.

The last external Stockfish 19 calibration before the Hard6 check estimated 1296/1406/1544 for Basic/Advanced/Hard, with wide empirical intervals. The anchors displayed in the app are still the verified, governed ones in `game-review.yaml`: 1340/1461/1549, linked to observations from source `b5c1ef3`. The TMP calibration remains separate from the app. A Hard6 rating requires three external sessions using the same set of 12 openings and both colors. The current check stayed offline. The Extent reports and experiment replays are kept in TMP, outside the release artifacts.

### Rejected offline A/B experiments

PVS applied to all iterations dropped Mate in 3 to 188/200, below the 190 floor, and was rejected before league play. PVS only at the final depth met all floors, but the league remained at 20/1/0/3 and +417, the same as Hard6 without PVS. The added complexity brought no measured strength. LMR for late quiet moves met the transfer criteria, but both the threshold after the fourth move and the threshold after the eighth lowered Hard–Advanced to 19/1/1/3 and +338. Both LMR variants were rejected. The live code retains the previous behavior, with reports in TMP.

A tactical extension at every leaf lowered Training to 36/48, below the
threshold of 39. An extension from check only lowered mate in three to
185/200, and killer ordering dropped it to 169/200. These variants were
rejected before league play.

</details>

- For each of the three matchups, the league starts from 12 openings and reverses the colors. The engine uses the app's decisions and retains history for repetition. Games still active at 160 plies are recorded as unfinished. Each unfinished game can contribute between zero and one point to the calculation. The central estimate uses the midpoint of this interval. The empirical 95% interval comes from 10,000 resamples of entire pairs, using both bounds for unfinished games. Elo differences are `400 log10(p / (1 − p))`, where `p` is the matchup score. [League set](../../atf-tests/src/test/resources/ai-league.yaml) · [Paired method in Fishtest](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html)

- The external anchor uses Stockfish 19, `UCI_Elo 1500`, one thread, and a request for 6,000 nodes per move, reversing colors in the same 12 openings. The result is the performance against that configuration. Stockfish calibrates its Elo option under a different time protocol, and limited-strength selection introduces variation across runs. [Anchor set](../../atf-tests/src/test/resources/ai-anchor.yaml) · [Stockfish option calibration](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

- From a TMP checkout, use new paths for each report and replay file. For parallel Maven runs, use a separate local TMP repository per run so ATF library installation does not write to the same files concurrently.

```powershell
mvn test '-Dtest=ChessBenchmarkSuite' "-Dchess.league=$leagueTsv" "-Dchess.extent=$leagueHtml"
mvn test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" "-Dchess.anchor=$anchorTsv" "-Dchess.extent=$anchorHtml"
```

- Extent records the paired results, while the TSV preserves the complete UCI sequences. To compare style, look at captures and checks per move, castling by seat, and average completed depth. Use the same openings and cap for every comparison.

### Internal league results

- Paired records, Elo intervals, and playing-style observations from the internal league.

<details>
<summary>Pairing, conditions, and uncertainty</summary>

| Matchup | W / D / L / U | Central Elo difference before → after | 95% interval after |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +120 → +280 | +154 … +470 |
| Hard – Advanced | 10 / 11 / 2 / 1 | +232 → +120 | +14 … +211 |
| Hard – Basic | 19 / 3 / 1 / 1 | +470 → +338 | +120 … +669 |

W, D, L, and U denote wins, draws, losses, and unfinished games for the first profile. In this league, Basic completed an average depth of 3.00, Advanced 3.94, and Hard 4.43. Captures accounted for 20.5%, 23.1%, and 24.7% of their moves. Checks accounted for 5.9%, 11.9%, and 15.1%. The 11 draws between Hard and Advanced came from repetition. In the Alekhine, with Hard as White, Stockfish evaluated the position at the start of the cycle on ply 36 at `+8.57` in a search without game history.

</details>

### Retest for 0.7.0

- The Git tag retains the 0.7.0 replay and the paired results.

<details>
<summary>Independent transfer targets and floors</summary>

| Matchup | W / D / L / U | Central Elo difference | Empirical 95% interval |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +280 | +154 … +470 |
| Hard – Advanced | 14 / 7 / 2 / 1 | +191 | +104 … +307 |
| Hard – Basic | 22 / 0 / 1 / 1 | +470 | +191 … ∞ |

Hard won all eight Pirc and Benoni games from both sides against Basic and Advanced. In the Alekhine Hard–Advanced game, it played `Nc3` after ply 44 and left the cycle that had previously led to a draw at 52. Stockfish reports +6.71 for the exit, with game history and a WDL loss of 0‰. The game reached the 160-ply cap. Stockfish shows −5.19 for White at the end of the check. In the reversed-color game, Hard won at ply 108. The [historical 0.7.0 replay](https://github.com/crististefanita/atf_j7/blob/v0.7.0/release/0.7.0/reports/league.tsv) stays in the Git tag, outside the short release catalog.

</details>

### Converting the advantage in the Alekhine

- The historical control compares the repetition exit and the choice after replaying 72 plies.

<details>
<summary>Puzzle results by profile and chapter</summary>

The historical replay stops after 72 plies. Hard played `Qc5`, heading toward the third occurrence of the position. At depth 13 with all preceding moves, Stockfish 19 gives `Qc5` 0.00. With history in the search, Hard chooses `Qxf6+`: +6.62 in the restricted search, versus +7.11 for the same move in the unrestricted search. The WDL loss is 0‰. Hard completes depth 5 in 12,557 additional nodes, below the unchanged 44,000 cap. [Control position and moves](../../atf-tests/src/test/resources/alekhine-conversion.yaml)

From the initial opening, Hard as White plays `Nf4` after ply 44 and wins at 153. From the historical position at 72, after the queen trade, Hard reaches the cap with −0.29 for White. This check retains Advanced's defense and the same 160-ply cap.

| Training league · 72 games | W / D / L / U | Central Elo difference | Empirical 95% interval |
|---|---:|---:|---:|
| Advanced – Basic | 19 / 2 / 3 / 0 | +280 | +154 … +470 |
| Hard – Advanced | 15 / 7 / 2 / 0 | +211 | +137 … +307 |
| Hard – Basic | 22 / 0 / 1 / 1 | +470 | +191 … ∞ |

Compared with 0.7.0, 69 of the 72 games have the same result. Hard turns the unfinished Alekhine game and the King's Gambit draw into wins. Hard's win as White in the Modern Benoni becomes a draw. At the first divergence after 112 plies, Stockfish evaluates the new `Kf4` at 0.00 and the previous `Kf6` at −1.75. The only game still at the cap is Petroff Hard–Basic, with the same sequence as before. Average completed depths for Basic, Advanced, and Hard are 3.00, 3.94, and 4.43.

#### Replaying the rook endgame

`RookEndgameHistoryTest` reconstructs the same full game up to plies 96, 108, and 112. The case at 108 distinguishes board-only evaluation from evaluation with history preserved. Without history, `a7a8` leads to the drawing line. With history, Hard avoids repetition and plays `h2h4`.

For an independent comparison, send Stockfish the full history with `position startpos moves ...` and evaluate the restricted moves at depth 13. Use the same fixture and command for each version.

```text
f5f6 g7f6 g1f1 a7a5 a1b1 b7b5 b1b5 e8d8 e2c3 d8c8 c3e2 a5a4
b5f5 c8c2 f2f4 c2a2 e2c1 a2d2 f1e1 d2g2 c1d3 f8g7 f5a5 h8e8
```

| Ply | Hard with history | Stockfish 19, d13 | Expected loss | Hard nodes |
|---:|---|---|---:|---:|
| 96 | `Kf1` | `Kf1`, +2.86 | 0‰ | 39,504 |
| 108 | `h2h4` | `h1g1`, +5.38 and `h2h4`, +4.08 | 0‰ | 41,376 |
| 112 | `a7a8` | `d3e1`, +2.63 and `a7a8`, +2.40 | 0‰ | 8,045 |

At 108, Stockfish evaluates `a7a8` at 500‰ and `h1g1` at 1,000‰. The repetition key includes the board, the side to move, castling rights, and an en passant capture only when legal. The transposition key also preserves the search path, so it avoids reusing a repetition-dependent value along a different path. All three choices respect Hard's limit. Basic, Advanced, and Hint use their respective search paths.

Verification covers the historical regression and the fixed set of 10 Stockfish positions. Recalibrating Hard's Elo requires the full league comparison beyond these three positions. [Next check](../feedback/README.md)

#### Hard transfer to the puzzle collection

`HardQuestTransferBenchmarkTest` measures the move chosen by the Hard opponent
on the catalog loaded at runtime. Mate cases check the first key move, and
material exercises check the catalog solution. The Tactical Training threshold
uses the fixed `training-001`–`training-048` batch. Extent also shows the total
for the active chapter. [Thresholds](../../atf-tests/src/test/resources/hard-quest-transfer.yaml)

The results below are the historical benchmark for the ordering experiment,
before the current catalog and policy.

| Chapter | Solved by Hard |
|---|---:|
| Mate in 1 | 500 / 500 |
| Mate in 2 | 300 / 300 |
| Mate in 3 | 190 / 200 |
| Tactics | 86 / 100 |
| Training | 39 / 48 |
| Intermezzo | 24 / 24 |

Ordering checks at the Hard search root raises M3 transfer from 172 to 190 under the same node cap. Ten M3 positions, 14 tactics positions, and nine training positions remain to be diagnosed. The test retains the regression floors in the benchmark suite and publishes nodes, depth, and missed examples in Extent.

#### Parallel league and check-order tuning

On November 12, 2026, we compared bonuses of 5,000 and 10,000 on the same 72 games, with up to three workers and the same openings, colors, and budgets. Both runs passed, and every game replayed from its checkpoint.

| Bonus | Hard–Advanced W / D / L / U | Central Elo | 95% interval | Hard–Basic W / D / L / U | Hard nodes / turn |
|---:|---:|---:|---:|---:|---:|
| 5,000 | 19 / 2 / 1 / 2 | +338 | +154 … ∞ | 22 / 1 / 1 / 0 | 29,891 |
| 10,000 | 19 / 2 / 2 / 1 | +307 | +154 … +669 | 22 / 1 / 1 / 0 | 29,518 |

Advanced–Basic stayed at 18 / 3 / 3 / 0 and +255 Elo, with an interval of +137…+417. Only one game's result changed between 5,000 and 10,000: Vienna, with Advanced as White against Hard as Black, ended in a White win after previously remaining unfinished at the cap. At depth 13, Stockfish 19 evaluated the first divergence from `e8g8` to `f5c2` at −915→−926 cp. The WDL loss remained 0‰ for both choices. The league gives insufficient evidence of an Elo increase at 10,000 because the position was already lost for Hard and the intervals are wide.

We chose 10,000 for the measured gain on the puzzle collection: Mate in 3 rose to 172/200 from 170/200 at 5,000, the other chapters held steady, and weighted mean nodes over all 1,172 positions fell by 16.6%, from about 7,554 to 6,299. The fixed set of 10 Stockfish positions stayed at 10/10, with a 0‰ WDL loss for Hard. These results leave Elo unchanged. The global tactical extension and the variant limited to positions in check were rejected. The first lowered Training to 35/48 and breached the Stockfish gates, and the second lowered Mate in 3 to 166/200. Extent reports and TSV files remain in TMP.

We also checked bonuses of 15,000 and 20,000 on the same 1,172 puzzles and the Stockfish gate. Both preserved the same chapter results as 10,000 and 10/10 against Stockfish. The weighted mean dropped by less than 1% relative to 10,000, to approximately 6,255 and 6,254 nodes. With transfer unchanged, the cap and value of 10,000 remain in place. Timing measurements from concurrent runs are excluded from comparison.

We separately tested root move ordering: Hard searches checks first and preserves evaluation order within the checking and non-checking groups. Basic, Advanced, Hint, the repertoire, and special rook endgames follow their own paths. Across all puzzles, M3 increased to 190/200, Training stayed at 39/48, and the other chapters held steady. The weighted mean dropped from 6,299 to about 5,703 nodes. The Stockfish set stayed at 10/10.

The paired 72-game league kept Hard–Basic at 22/1/1/0 and Advanced–Basic at 18/3/3/0. Hard–Advanced moved from 19/2/2/1 and +307 Elo to 19/3/1/1 and +338 Elo. A White win in the Vienna, with Hard as Black, became a repetition draw. The 95% intervals overlap widely (+154…+669 versus +191…+669), so the sample supports the absence of regression and yields insufficient evidence to confirm an Elo increase. Hard's mean dropped from 29,518 to 29,305 nodes per turn.

A more aggressive sort of all root moves by captures and activity also scored 190/200 in M3, but Hard–Advanced fell to 14/6/2/2 (+191 central Elo). We rejected it because the improvement on puzzles came at the cost of game strength. Absolute Elo estimates are updated only after the three Stockfish 19 sessions described below.

</details>

### External anchor results

- The external anchor measures performance under the documented Stockfish configuration.

<details>
<summary>Stockfish conditions, estimates, and study limits</summary>

On the 0.9.0 source, three sessions used the same 12 positions with colors reversed, for 24 games per profile per session. Stockfish 19 stayed at `UCI_Elo 1500`, one thread, and a request for 6,000 nodes per move. The cap stayed at 160 plies, and the J7 profiles retained their budgets.

| Profile | W / D / L / U | Estimate under the protocol | Empirical 95% interval | Session estimates |
|---|---:|---:|---:|---:|
| Basic | 12 / 2 / 43 / 15 | 1340 | 1139–1481 | 1380 / 1328 / 1309 |
| Advanced | 16 / 2 / 24 / 30 | 1461 | 1183–1684 | 1471 / 1411 / 1500 |
| Hard | 31 / 1 / 21 / 19 | 1549 | 1369–1739 | 1573 / 1573 / 1500 |

Of the 216 games, 64 were still active at the cap. Each contributes a score interval from zero to one point. The calculation aggregates the three pairs for each opening before the 10,000 resamples. The opening remains the independent unit. Hard leads by central estimate, and the three profiles' intervals overlap. `GameReviewGovernanceTest` recalculates all three benchmarks used by the app. [Paired results and provenance](../../atf-tests/src/test/resources/ai-anchor-observations.yaml)

In the three historical sessions, Advanced hit the cap in five of the six Modern Benoni games. At depth 13, Stockfish 19 estimated 0‰ for Advanced in all five final positions, including two positions with forced mate. The anchor calculation retains the unfinished-game interval for all five.

Re-evaluation of the full history from these sessions and the session after the Alekhine fix found Black's first major loss at ply 10. `…Ng4` lost 335‰ in all four games relative to `…d6`. As White, the first loss occurred at ply 11 after `…g6`, `…Na6`, or `…h6`, with losses of 379‰, 285‰, and 173‰. The Advanced repertoire uses `…d6`, `d6` after `…g6`, and `e4` after `…Na6` or `…h6`. The rules match the exact position and preserve the Advanced budget. [Replies and levels](../../chess-core/src/main/resources/ai-repertoire.yaml)

To reproduce, use a TMP checkout and three new TSV and Extent paths. Each run validates its game pairs and replays every move through the recorded result.

```powershell
foreach ($index in 1..3) {
    mvn -B -ntp test '-Dtest=AiAnchorBenchmarkTest' "-Dchess.stockfish=$stockfish" `
        "-Dchess.anchor=$scratch/anchor-$index.tsv" "-Dchess.extent=$scratch/anchor-$index.html"
    if ($LASTEXITCODE) { throw "Sesiunea $index a eșuat" }
}
```

The earlier retest after the Alekhine fix covered 72 games.

| Profile | W / D / L / U | Central performance under the protocol | Empirical 95% interval |
|---|---:|---:|---:|
| Basic | 2 / 0 / 17 / 5 | 1245 | −∞ … 1442 |
| Advanced | 11 / 1 / 8 / 4 | 1544 | 1346 … 1755 |
| Hard | 6 / 0 / 14 / 4 | 1380 | 1083 … 1558 |

Thirteen games remained at the cap. Advanced has the highest central estimate against the Stockfish configuration, and Hard leads the head-to-head league. The anchor intervals overlap, so the estimate ordering does not establish the absolute ordering of the profiles.

The first measurement remains the benchmark:

| Profile | W / D / L / U | Central performance under the protocol | Empirical 95% interval |
|---|---:|---:|---:|
| Basic | 6 / 0 / 13 / 5 | 1396 | 1083 … 1589 |
| Advanced | 8 / 0 / 8 / 8 | 1500 | 1162 … 1732 |
| Hard | 5 / 0 / 11 / 8 | 1411 | 955 … 1654 |

In the first measurement, 21 of the 72 games were still active at the cap. Variation across runs also appears for Basic and Advanced, whose search rules remained the same. For a version comparison, keep the budget and cap fixed and repeat the pairs across several sessions.

#### Exploratory check-order calibration · November 12, 2026

We ran three new sessions on the local candidate with stable priority for checks at the Hard search root. Stockfish 19 used `UCI_Elo 1500`, one thread, and a request for 6,000 nodes per move. Each profile played 72 games in the same 12 openings, with both colors. The 160-ply limit and profile budgets held steady. The Stockfish executable hash matched the anchor.

| Profile | W / D / L / U | Estimated Elo | Paired 95% interval |
|---|---:|---:|---:|
| Basic | 7 / 0 / 56 / 9 | 1212 | 1008–1369 |
| Advanced | 23 / 0 / 30 / 19 | 1466 | 1237–1654 |
| Hard | 34 / 0 / 23 / 15 | 1553 | 1411–1718 |

The interval resamples the 12 openings as independent units and treats each unfinished game as a score between zero and one point. The runs generated Extent reports and TSV files in TMP, with the same fixture, thresholds, and budgets. These figures describe the local-checkout candidate and stay out of `game-review.yaml` and `ai-anchor-observations.yaml` while the source lacks a provenance commit for this calibration. The wide, overlapping intervals give insufficient evidence of either an Elo increase or reliable statistical separation between levels. Governed recalibration follows once the source is pinned to a commit and the report hashes are retained.

</details>

## Game review

- The [method for evaluating a ply](game-review.md#evaluating-a-ply),
  the [categories and accuracy](game-review.md#grades-and-accuracy), and
  the [Elo estimate](game-review.md#how-elo-is-estimated) are explained in their
  owning document. Displayed AI benchmarks use the governed external calibration in
  [`game-review.yaml`](../../chess-core/src/main/resources/game-review.yaml).

- The index recognizes 3,865 ECO lines. It looks for the last named position, including when a game reaches it by transposition, and marks book moves for as long as the sequence follows a known prefix. ECO names come from the English CC0 catalog. To regenerate it, download `a.tsv`–`e.tsv` from the specified revision and run the import script with chess.js 1.4.0. Governance replays every UCI line and checks its resulting position against the EPD. [Catalog revision](https://github.com/lichess-org/chess-openings/tree/3ba6b20d31b2e6125cb0e6a2e792f400717d9d39) · [Import script](../../scripts/import-review-openings.cjs)

- We also checked an Elo estimate based exclusively on move score loss. In the sample of 800 Blitz 3+0 games from January 2024 across the 1200–2400 bands, calibration-set averages were 74‰, 86‰, 73‰, 70‰, and 75‰. The separate set gave 103‰, 77‰, 43‰, 73‰, and 54‰. Variation across bands led us to use the result against the AI benchmark for Elo and score loss for accuracy and classification. [Lichess archive](https://database.lichess.org/#standard_games) · [Game Rating on Chess.com](https://support.chess.com/en/articles/10773754-how-is-game-rating-calculated-in-game-review)

### Hints on league openings

- The fixed opening set records hint quality before and after repertoire verification.

<details>
<summary>Game Review positions and checks</summary>

On the same 12 starting positions, the average hint loss against Stockfish at depth 13 fell from 127.3‰ to 73.0‰ and then to 23.4‰ after checking the repertoire. Cases of at least 150‰ fell from five to two and then to zero. Hard has the same result of 23.4‰ and zero large losses. In the Pirc, `…Bg7` replaces `…Bd7` (296‰ loss), and in the Benoni, `…d6` replaces `…Qa5` (299‰). Both replies match Stockfish's first choice in these positions. The test governs total loss, large-loss cases, and the node cap. [League positions](../../atf-tests/src/test/resources/ai-league.yaml)

</details>

## Opening checks

- The fixed suite verifies legal taught moves and compares Hard and the hint against Stockfish in positions from the lessons.

<details>
<summary>Opening coverage, gates, and observed scores</summary>

The Stockfish suite confirms the legality of all taught moves and checks Hard and the hint in one position from each lesson. The UCI sequences are pinned in YAML. Governance checks lesson coverage, both colors to move, and whether every sequence matches the taught line. Extent shows the J7 and Stockfish moves and both scores for each case. Quality gates use average loss and the number of cases with a loss of at least 150‰. [Positions and thresholds](../../atf-tests/src/test/resources/opening-ai-benchmark.yaml)

| Profile | Initial average loss | After the first expansion of the lessons | Cases ≥150‰ before / after |
|---|---:|---:|---:|
| Hard | 117.3‰ | 59.5‰ | 2 / 1 |
| Hint | 70.1‰ | 25.4‰ | 2 / 0 |

In the Italian Game, the hint changed from `Bd6` to `Nf6`, and loss fell from 178‰ to 0‰. In the Scandinavian, it changed from `Nc6` to `Nf6`, with loss falling from 366‰ to 0‰.

</details>

### Calibrating Hard and the evaluation bar

- Move choice and display error are measured separately with distinct metrics.

<details>
<summary>External ratings and confidence bounds</summary>

In the Advance French after `5.Nf3`, Hard switched from `…Qa5` to `…cxd4`. Loss dropped from 277‰ to 21‰ relative to Stockfish 19's `…Nge7`. Across the existing openings set, Hard's average loss fell from 59.5‰ to 19.1‰ and every loss was below 150‰. The six extra French and Scotch positions have an average loss of 39.7‰ and a maximum of 127‰.

After `1.e4 e5`, the first recommendation is `Nf3`, and the bar shows `+0.19`, compared with `+1.40` previously and `+0.43` in Stockfish 19. On seven additional positions, the first calibration lowered mean absolute error from 70.7 cp to 13.1 cp. The benchmark compares move choice in expected score and the bar estimate in centipawns separately, from White's perspective. [Calibration set and gates](../../atf-tests/src/test/resources/opening-calibration.yaml) · [Stockfish scale](https://official-stockfish.github.io/docs/stockfish-wiki/Useful-data.html)

When the three-ply correction reverses the sign of the two-ply evaluation, the bar searches another horizon with the same number of moves for both colors. If it completes within the existing budget, it averages the two even-horizon results. After `1.d4 d5 2.c4`, the bar changes from `−0.19` to `+0.19`, compared with `+0.22` in Stockfish 19. Error falls from 41 to 3 cp, and the average across the seven checks reaches 7.7 cp. The observed error is 33 cp after `2…e6` and 8 cp after `2…c6`. After accepting the gambit with `2…dxc4`, the bar still shows `−0.60` versus `+0.21`, a separate case of pawn compensation.

</details>

## Baseline results for the first expansion

- The first expansion provides historical control values for the four profiles.

<details>
<summary>First expansion by holdout and profile</summary>

| Profile | Same move as Stockfish, before / after | Average loss, before / after | Large losses, before / after |
|---|---:|---:|---:|
| Basic | 5 / 6 out of 10 | 202.7‰ / 202.4‰ | 2 / 2 |
| Advanced | 7 / 7 out of 10 | 2.7‰ / 14.1‰ | 0 / 0 |
| Hard | 7 / 8 out of 10 | 2.7‰ / 11.7‰ | 0 / 0 |
| Hint | 7 / 8 out of 10 | 20.2‰ / 2.4‰ | 1 / 0 |

After calibrating Hard, its average loss on the mixed suite dropped from 11.7‰ to 0‰. The Sicilian tactical regression preserves `Nxe5` for Hard and the hint. This case uses a different position from the Najdorf lesson. Both suites kept the same thresholds.

</details>

## Exact cache for Hard

- Hard reuses completely searched lines within the configured capacity.

<details>
<summary>Exact-cache policy and measurements</summary>

We enabled reuse of completely searched lines only for the Hard opponent. The entry uses the position key, including side to move, castling rights, and legal en passant capture, along with plies remaining, distance from the root, evaluation objective, and tactical extension. Keeping the distance prevents a mate score from being reused with a different value. The cache accepts only exact results and excludes alpha-beta cutoffs and incomplete results as oracles for another branch. Basic, Advanced, hint, bar, and game review searches keep their existing paths.

Configure capacity in `match-ai.yaml` at 500 entries per 1,000 additional Hard nodes, capped at 50,000. The current budget of 44,000 gives up to 22,000 entries. We also compared a cap of 44,000, which left puzzle and league results unimproved, so we kept the lower-memory version.

We ran the same league on 12 new openings with both colors and 24 games for every pair of profiles, in TMP checkouts with identical Hard6 code outside the cache. Advanced–Basic stayed at 16/7/1/0 in both. Hard6 versus Hard6 with the cache produced these results:

| Pair | Baseline Hard6 | Hard6 with exact cache | Central internal estimate |
|---|---:|---:|---:|
| Hard–Basic | 20/3/1/0 | 22/1/1/0 | +374 → +470 |
| Hard–Advanced | 21/1/0/2 | 23/1/0/0 | +470 → +669 |

These conversions report relative scores within the J7 league. External Elo uses the separate anchor protocol. On the existing frozen set, the improvement was smaller: one draw became a win in Hard–Advanced. Transfer floors stayed unchanged: M1 500/500, M2 300/300, M3 194/200, tactics 87/100, training 39/48, and Intermezzo 24/24. The governance test checks the factor, cap, and disabled state and rejects out-of-range values.

Extent reports and TSV replays for the candidate, baseline, and doubled capacity stayed in `C:\Users\Cristi\AppData\Local\Temp\j7-hard-tt-*`, outside the release artifacts. [Official Stockfish terminology for transpositions and search](https://official-stockfish.github.io/docs/stockfish-wiki/Terminology.html) explains how a transposition table reuses positions. Elo improvement for this implementation remains unverified.

The app's configured benchmarks come from the source preceding Hard6 search. Verification of the Advanced ceiling and Hard's progress uses games with the local Stockfish 19 executable identified by the hash in `ai-anchor-observations.yaml`. Estimates depend on the opponent, budget, and protocol. [Stockfish UCI Elo calibration](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

</details>

### Local alpha-beta bounds prototype · not integrated

- The prototype failed the mate-in-three parity requirement and stayed outside the app.

<details>
<summary>Paired games and conversion evidence</summary>

The figures below describe a separate TMP prototype. The app keeps only exact lines and excludes alpha-beta bounds from both storage and reuse. Promotion was rejected because using fewer nodes in Mate in 2 cost one solved position in Mate in 3. Cite these results only when discussing the prototype.

The first prototype keeps depth and distance from the root in the key. A lower bound can be reused if it reaches beta, and an upper bound if it is at most alpha. At the level that returns variations to the root, only exact lines are reused. The differential check verified 60 cases involving narrow windows, reuse, and return to a full window. The final score matched the calculation without caching. [Stockfish 19 reference implementation](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp)

With 44,000 nodes and 22,000 entries, the prototype met the transfer thresholds. Mate in 2 stayed at 300/300, with an average of 12,430 nodes versus 13,377. Mate in 3 dropped from 194 to 193/200. Tactics stayed at 87/100, training at 39/48, and Intermezzo at 24/24.

In the first five losses from the first calibration series, Stockfish's first major loss at depth 12 occurred at plies 10, 13, 34, 17, and 16. Hard had spent all 44,000 nodes in each position. The most severe sample was the King's Gambit as Black at ply 16. Stockfish assessed `d7d5` at +91 cp and Hard's `d7d6` at −339 cp.

Against the live version, the prototype recorded 11 wins, 4 draws, and 9 losses in 24 games, with colors reversed and a 160-ply cap. The estimated difference is +29 Elo, with a 95% interval from −58 to +120. Ordering by the stored move at the same depth produced an identical replay file. Carrying the previous iteration's ordering forward met the transfer thresholds, but raised Mate in 3 mean nodes from 13,705 to 14,573 compared with the bounds prototype.

</details>

### Live-version calibration

- External calibration records the source, result distribution, and uncertainty.

<details>
<summary>Checks at the same root horizon</summary>

Source `0e6a62f` played 216 games in three series against Stockfish 19 at `UCI_Elo 1500`, one thread, a request for 6,000 nodes per move, and at most 160 plies. We used the calibration protocol's 12 openings with colors reversed. The 95% intervals resample openings, and games still active at the cap contribute between zero and one point.

| Profile | Wins / draws / losses / unfinished | Estimated Elo | 95% interval |
|---|---:|---:|---:|
| Basic | 7 / 1 / 49 / 15 | 1268 | 922–1442 |
| Advanced | 23 / 3 / 30 / 16 | 1466 | 1316–1615 |
| Hard | 30 / 2 / 23 / 17 | 1534 | 1396–1672 |

Advanced with 10,000 additional nodes and four plies played another 72 games under the same protocol. It recorded 19 wins, 3 draws, 35 losses, and 15 unfinished games. The central estimate fell from 1466 to 1421, with an interval of 1245–1553. In the ten-position suite, Advanced's average loss stayed at 14.1‰, with zero large losses. The central estimate is below the requested 1450 ceiling, and the interval still crosses it.

</details>

### The capture at Hard's search horizon

- The candidate improved the fixed tactical positions and the paired games measured its broader impact.

<details>
<summary>Hard candidate coverage and checks</summary>

In the Sicilian position from the fixed suite, the four-ply search chooses `Nxe5` and sees the reply. At five plies it searches `Nc3` and moves the same capture to the end of the variation. The prototype retained the even-depth ranking when the odd-depth leader deferred the capture to the horizon.

The prototype improved from 9/10 to 10/10 exact matches with Stockfish. Average loss fell from 45.9‰ to 0‰, and scores on the 1,172 puzzles stayed at 500/500, 300/300, 194/200, 87/100, 39/48, and 24/24. In the head-to-head against the live version, it recorded 8 wins, 6 draws, and 10 losses. The estimated difference is −29 Elo, with a 95% interval from −89 to 0. Against Stockfish it achieved 1519 Elo (1357–1691) after 72 games, compared with 1534 for the baseline. The head-to-head result led to removing the prototype from the live code.

The comparison of the hint and Hard over 12 openings shows cumulative losses of 281‰ and 257‰. In the Catalan, the hint chooses `c4d5` with a 49‰ loss, and Hard chooses `b1c3` with 25‰.

</details>

### Proved mate and position reuse

- The checks measure move equivalence and cost before accepting a search change.

<details>
<summary>Historical gates and controls</summary>

Accepting a proved mate at the final depth regardless of the previous leader preserved every puzzle score. Across 24 games, every color-reversed pair produced the same move sequence, and the head-to-head ended with 9 wins, 6 draws, and 9 losses. The prototype stays in TMP.

Reusing positions generated to order checks preserved move choice, depth, and nodes across 48 positions. Medians were 594 and 639 ms versus 535 and 304 ms for the baseline in two alternating runs. Other checks were running on the same machine, so the measurements reflect varying load. The implementation using position maps was rejected.

</details>

### Experimental 88,000-node budget

- The larger budget remains an experiment with separate strength and latency evidence.

<details>
<summary>Search progress and comparison set</summary>

At six plies and 88,000 additional nodes, Hard recorded 34 wins, one draw, 19 losses, and 18 unfinished games across 72 games against Stockfish 19. The estimate is 1573 Elo (1432–1798), compared with 1534 (1396–1672) at 44,000. Mate in 3 rose from 194 to 198/200. The other five categories kept their scores. Average nodes in Tactics rose from 29,626 to 45,761. Across ten fixed positions, average loss stayed at 45.9‰, with one large loss in the Sicilian.

Resampling pairs by opening, with half a point for each unfinished game, estimates the difference at +39 Elo with a 95% interval from −54 to +132. Across 48 positions, median time rose from 534 to 916 ms. Both measurements ran on the same machine with the JVM head-to-head active in the background.

</details>

## Calibrating Basic with selective depth

- The paired evidence supports a selective Basic depth under its existing node policy.

<details>
<summary>Basic depth policy and controls</summary>

We played 72 games per variant, in three sessions, against Stockfish 19 with `UCI_Elo 1500` and a request for 6,000 nodes per move. We used the same 12 openings with reversed colors and a 160-ply cap. W, D, L, and U denote Basic's wins, draws, losses, and unfinished games.

| Variant | W / D / L / U | Estimated Elo | 95% interval |
|---|---:|---:|---:|
| Three-ply baseline | 7 / 1 / 49 / 15 | 1268 | 922–1442 |
| Two plies | 3 / 0 / 63 / 6 | 1083 | −∞–1322 |
| Two plies, three when in check | 4 / 0 / 59 / 9 | 1151 | −∞–1346 |
| Shipped Basic, baseline set | 2 / 0 / 63 / 7 | 1067 | −∞–1253 |
| Shipped Basic, 12 separate openings | 2 / 1 / 63 / 6 | 1067 | −∞–1245 |
| Shipped Basic, both sets | 4 / 1 / 126 / 13 | 1067 | 638–1225 |

The exception for check estimated 1253 Elo over another 72 games on 12 different openings. Two plies in all positions estimated 1173 Elo on the same new openings, but the conversion test caught a rook endgame that reached the 30-ply cap without mate. The code grants a third ply only in elementary rook or queen endings against a king and passes the conversion tests again. The central estimate is below 1200 in both sets, with intervals extending above 1200. The historical rating in `game-review.yaml` stays bound to its evidence and commit. [Stockfish protocol](#live-version-calibration)

</details>

## Hard recapture at the horizon

- The change is evaluated against fixed moves, chapter floors, and independent positions.

<details>
<summary>Hard recapture search and transfer</summary>

The first check tried a single recapture on the square of the last capture at the search horizon, charging nodes against the 44,000 cap. Positions in check retain the baseline evaluation, and the cache includes the recapture square. The technique applies selective depth to material exchanges. [Variable-depth search](https://ijcai.org/proceedings/1983/183)

Across ten fixed positions, Hard loss fell from 45.9‰ to 2.4‰ and large losses from one to zero. Tactics rose from 87/100 to 92/100. Mate in 3 dropped from 194/200 to 193/200, and `intermezzo-012` dropped the chapter from 24/24 to 23/24. The check breached the Intermezzo threshold and stayed in TMP.

Limiting the recapture to even depths preserved all baseline chapter scores, including 194/200 in Mate in 3 and 24/24 in Intermezzo. In the fixed Stockfish set, loss remained 45.9‰ with the same large loss. The expanded collection yielded 61/72 in Tactical Training with live Hard, including 22/24 on the new exercises. [Public selection](quest-curation.md#tactical-training-and-verification-set)

Recapture only after a piece takes a less valuable one met all fixed thresholds. It scored 95/100 in Tactics, 24/24 in Intermezzo, and 192/200 in Mate in 3, versus 194/200 in the live code. On the fixed Stockfish set it kept 2.4‰ and zero large losses. Across 24 positions from 12 separate openings, with both colors to move and Stockfish 19 at depth 15, average loss increased from 53.0‰ to 68.0‰. The candidate improved six decisions, tied in 14, and worsened four. In the Scandinavian after `4.d4`, `…Nc6` lost 399‰ relative to `…Nf6`. In the Classical Sicilian after `6.Bg5`, `…Nxe4` lost another 244‰ relative to `…Bg4`. The candidate stays in TMP.

In the head-to-head using the same 12 openings with reversed colors, the candidate recorded 10 wins, 4 draws, 5 losses, and 5 unfinished games. Its estimated head-to-head difference is +73 Elo, with a 95% interval from −137 to +307. The independent set and wide interval leave the improvement to Hard unconfirmed.

Restricting the search to recaptures after Hard's last move passed the fixed set, but raised loss to 79.1‰ across the 24 independent positions. The regression persists with a color filter alone.

</details>

## Solving puzzles with a declared objective

- The catalog solver receives the objective and proves mate against all legal defenses.

<details>
<summary>Acceptance conditions and objective results</summary>

`HardQuestSolver` receives the position and the objective. For mate it searches at one, three, and five plies in succession, tries checks first, and retains quiet moves. An attacking move is proved only if every legal defense allows the continuation. The table holds exact results for the position and plies remaining. The cap is 100,000 nodes, and exhausting it produces a distinct result without a solution. For material, the same three-ply minimax evaluation requires a unique key move and a forced gain of at least 200 cp. [Proof search in game trees](https://research.tilburguniversity.edu/en/publications/proof-number-search-2/)

| Check across the 200 mate-in-three positions | Solutions | Average nodes | Maximum |
|---|---:|---:|---:|
| A · Hard in a game | 194/200 | 14,446 additional | 44,000 additional |
| B · Proof, generator order | 200/200 | 21,750 | 344,364 |
| C · Proof, checks before quiet moves | 200/200 | 2,746 | 84,920 |

The B/C comparison uses the same experimental cap of one million nodes. C then passed under the 100,000 cap and solved 1,220/1,220 public puzzles and 24/24 from the independent set. YAML keys enter only the assertion. Solver nodes and the opponent's additional nodes count different searches.

In Hard games, stopping at the first proved mate kept 194/200 in Mate in 3 and reduced the average from 14,446 to 9,657 additional nodes. The 24 independent positions yielded the same choices, and all four game pairs produced identical sequences, with a 3/2/3 record. We integrated stopping on proof to reduce search work. The Stockfish gate remains at 45.9‰ with one large loss. The variant with a null window after the first branch produced the same loss and stayed in TMP. Hard's rating remains at the baseline estimate. [Fishtest protocol](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html)

Reserving 5,000 nodes of the 44,000-node budget for proof led to 199/200 in Mate in 3. Training moved from 80/96 to 81/96, and Tactics from 87/100 to 86/100. Across the 24 independent positions with history it chose the same moves. The check limited proof to positions received without history. It stayed in TMP. The quiet key `…Nb4` in `mate3-005wy` required 26,380 nodes in the separate proof search.

</details>

## Legal-move cost and the Hard repertoire

- The controls verify legal move counts, choice parity, and evaluation changes under the fixed budget.

<details>
<summary>Search cost and repertoire checks</summary>

The engine retains king positions when applying a move. Check detection starts from the king, checks short-range attacks, and stops at the first piece along each ray. Directions and attacks still use the rules loaded from YAML. At four plies, the four control positions produced 197,281, 4,085,603, 43,238, and 2,103,487 continuations, matching Stockfish 19. The component test checks a quick subset of the same positions. [Stockfish perft positions](https://github.com/official-stockfish/Stockfish/blob/sf_19/tests/perft.sh)

Across 48 positions, Hard's move, depth, and nodes matched in all four alternating runs. Local medians were 879 and 934 ms for the previous generator and 281 and 478 ms for the current one. With the current generator, the 95th percentile ranged from 664 to 2,097 ms under the load of parallel runs. The measurement describes the local JVM.

At 44,000 nodes, additional pawn evaluation raised average loss across 24 independent positions from 28.7‰ to 39.8‰. Adding rook activity and king safety raised it to 49.3‰. Experimental budgets of 110,000 and 175,000 retained the major loss in the Sicilian with `…e5`. These variants stay in TMP. [Phase-based evaluation model](https://github.com/official-stockfish/Stockfish/blob/sf_14/src/evaluate.cpp)

In head-to-head games on six openings with reversed colors, the pawn variant recorded 5 wins, 5 losses, and 2 unfinished games. The rook and king-safety variant recorded 4 wins, 6 losses, and 2 unfinished games. The estimated differences were 0 Elo (−280–+280) and −58 Elo (−280–+191). [Fishtest methodology](https://official-stockfish.github.io/docs/fishtest-wiki/Creating-my-first-test.html)

The Hard repertoire has six new replies for exact positions. Stockfish 19 chose the same reply at depths 13, 16, and 19 for each position. In the Sicilian with `…e5`, `Nxe5` scored an expected 993‰ at depth 19 versus 519‰ for `Nc3`. In the Classical Sicilian, `…e6` scored 458‰ versus 277‰ for `…Bg4`. After the repertoire reply, Hard searches the continuation under the 44,000 cap. [YAML repertoire](../../chess-core/src/main/resources/ai-repertoire.yaml)

The fixed Stockfish gate passed with 10/10 exact matches, average loss of 0‰, and zero large losses. The Sicilian position is in the repertoire, so the result also measures repertoire coverage. On the previous set of 24 positions, 23 choices matched and one improved, with average loss dropping from 53.0‰ to 44.5‰. The set includes the Classical Sicilian covered by the repertoire. Across the 12 games started from the six reply positions with reversed colors, the candidate recorded 6 wins, one draw, 3 losses, and 2 unfinished games. The estimated difference is +89 Elo, with a 95% interval from −154 to +338. [Anchor protocol](#live-version-calibration)

</details>

### Recalibration on the 12 standard openings

- The 12-opening comparison reverses colors and reports the interval alongside the estimate.

<details>
<summary>Controlled changes and independent results</summary>

Each row covers 24 games against Stockfish 19 at `UCI_Elo 1500`, one thread, 6,000 requested nodes per move, and both colors. The record gives wins, draws, losses, and unfinished games.

| Profile | Record | Estimated Elo | 95% interval |
|---|---:|---:|---:|
| Basic | 2 / 0 / 22 / 0 | 1083 | −∞–1268 |
| Advanced | 6 / 0 / 11 / 7 | 1427 | 955–1691 |
| Hard before the new repertoire | 12 / 1 / 8 / 3 | 1558 | 1363–1755 |
| Hard with the new repertoire | 8 / 0 / 8 / 8 | 1500 | 1268–1780 |

Replaying the UCI sequences confirms that Hard encountered zero of the six new positions on its turn in either series. The two Hard estimates vary across the limited-strength anchor runs. The head-to-head started from repertoire positions offers a direct measurement of the change, with an interval that includes zero. [Anchor protocol](#live-version-calibration)

The head-to-head on 12 other openings gave the 88,000-node candidate 13 wins, two draws, seven losses, and two unfinished games. The estimated difference is +89 Elo, with a 95% interval from −14 to +232. We keep the live budget at 44,000 and use the 88,000-node check to identify positions where selective search can save nodes.

</details>

## Immediate pawn captures in Hard search

- Hard can retain an immediate pawn capture when a complete odd-depth line only defers the same capture.

<details>
<summary>Pawn-capture policy, league, and score</summary>

When the even depth ranks an immediate pawn capture first, Hard can keep that result if the odd-depth line favors a quiet move and only captures the same pawn on the final ply. The conditions require at least equal material along the immediate-capture line, a complete horizon for both variations, and the pawn remaining on the same square until it is captured. A proved mate takes precedence. The rule applies in ordinary Hard search outside the repertoire and the rook-endgame search. The `hard-horizon.preserve-pawn-capture` key lets you disable it for A/B tests. [YAML policy](../../chess-core/src/main/resources/match-ai.yaml)

| Set | Baseline → candidate | Control |
|---|---|---|
| Sicilian with an exposed pawn, two tuning positions and two validation positions | `Nc3 → Nxe5` in all four positions | Stockfish 19 chose `Nxe5` at depths 14, 17, and 19. |
| Black-to-move positions | `Nc6 → Nxe4` in one position, with two already-correct captures preserved | Stockfish 19 chose `Nxe4` at all three depths. |
| Defended pawn or queen on c7 | Control choices are unchanged | At depth 19, Stockfish favors development. |
| Sicilian tactic with …h6 and Nc3 | Hard keeps `Nd5`, while Stockfish prefers `Nxe5` | The four-ply horizon indicates material loss, and the next horizon does not finish within budget. |

On four thematic positions, each version played both colors against Stockfish 19 at `UCI_Elo 1500`, one thread, and 6,000 requested nodes per move. A and B reused the Stockfish reply for as long as their histories matched. Six game paths stayed identical. In the other two, B won as White at plies 41 and 39. A remained active at the 180-ply cap and won at 135, respectively. The records are 4 / 0 / 3 / 1 for A and 5 / 0 / 3 / 0 for B. The estimate intervals overlap.

The fixed gate kept Hard at 10/10 and the cap at 44,000 additional nodes. On puzzles without an objective passed to the engine, results remained 500/500, 300/300, 194/200, 87/100, 80/96, and 24/24. The declared-objective solver stayed at 1,220/1,220 public puzzles and 24/24 independent puzzles. The hint comparison had the same discrepancy in A and B, 281‰ aggregate for the hint and 257‰ for Hard over 12 positions. The difference comes from the Catalan, where the hint prefers `cxd5` with a 49‰ loss, versus `Nc3` with 25‰ for Hard. [AI feedback](../feedback/README.md)

In the thematic rerun, after restricting the rule to the same pawn and the immediately preceding horizon, A scored 3 / 0 / 2 / 3 and B scored 5 / 0 / 2 / 1. One of the two extra wins later diverged from the initial position. On six neutral openings with shared replies, all 12 game paths matched, and each variant scored 7 / 0 / 3 / 2. Hard's estimate is 1620 Elo with an interval of 1500–1917. The neutral set gives insufficient evidence of a general improvement.

| Profile | Latest neutral set | Estimated Elo | 95% interval |
|---|---:|---:|---:|
| Basic | 0 / 0 / 11 / 1 | 955 | −∞–1309 |
| Advanced | 4 / 0 / 5 / 3 | 1471 | 1083–1917 |
| Hard | 7 / 0 / 3 / 2 | 1620 | 1500–1917 |

Each profile has 12 games against the same anchor. Basic and Advanced use the calibration series, and Hard uses the A/B series with shared replies. Intervals remain wide. The benchmarks displayed in the app still come from the governed calibration. [Anchor protocol](#live-version-calibration)

</details>

## Checking exchanges after a pawn capture

- The exchange check compares independently searched moves and is assessed on fixed positions and paired games.

<details>
<summary>Exchange policy, regressions, and results</summary>

Hard starts with the normal ranking. In a position with equal material and no check at the root, it can check a pawn capture that leads at three plies with an apparent gain of at least two pawns when a different move leads at four plies and the next iteration provides no acceptable result. The comparison searches the two moves separately at the same depth, with up to two plies for captures, promotions, and escapes from check. The capture moves ahead only when both checks complete, its score is better, and the line gains at least one pawn of material. The `hard-horizon.pawn-exchange-nodes` reserve uses up to 7,000 of the 44,000 additional nodes. Set it to zero to disable it. [YAML policy](../../chess-core/src/main/resources/match-ai.yaml)

After `1.e4 c5 2.Nf3 h6 3.Nc3 e5`, the four-ply search reached `Nxe5 Qf6 Nxf7 Qxf7` and evaluated a material loss. The complete check compares other replies too. Stockfish 19, at depth 20 with the move forced, gave +241 cp for `Nxe5` and +37 cp for `Nd5`. Six tuning positions and four of the six validation positions changed from another move to `Nxe5`. The other two validation positions preserved a good capture and rejected a capture of a defended pawn. Controls with the queen on c7, the knight on c6, or the pawn on d6 retained their previous decisions.

In 16 thematic games against Stockfish 19 with colors reversed, A scored 5 / 0 / 2 / 1 and B scored 3 / 0 / 3 / 2. On the neutral set, A scored 4 / 0 / 7 / 1 and B scored 5 / 0 / 6 / 1. In the head-to-head over four thematic openings, B scored 3 / 0 / 2 / 3, with an estimated difference of +44 Elo and a 95% interval from −338 to an open upper bound. The record gives wins, draws, losses, and unfinished games. The Stockfish reply is reused while game histories match. The results confirm the correction to the capture decision, and converting the advantage remains a priority. [Paired method](https://official-stockfish.github.io/docs/fishtest-wiki/Fishtest-Mathematics.html) · [AI feedback](../feedback/README.md)

| Profile | Neutral set | Stockfish 19 `UCI_Elo` | Estimated Elo | 95% interval |
|---|---:|---:|---:|---:|
| Basic | 2 / 0 / 10 / 0 | 1320 | 1040 | −∞–1200 |
| Advanced | 1 / 0 / 6 / 5 | 1500 | 1346 | −∞–1780 |
| Hard B | 5 / 0 / 6 / 1 | 1500 | 1471 | 1309–1691 |

Basic lost all 12 games against the 1500 anchor. The run against 1320 gives a finite estimate. Each profile has six pairs, with one Stockfish thread and 6,000 requested nodes per move. Ratings depend on the set and opponent. [Stockfish calibration](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish)

</details>
