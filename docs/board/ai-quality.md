# AI Evaluation in Chess Lab

## Overview

- **Current policy.** The opponent, hint, and evaluation bar have separate budgets. Hard foreground search starts with 100,000 additional nodes and can use an adaptive reserve up to 200,000. The hint has one 200,000-node ceiling, including the initial horizons, tactical frontier, and up to 150,000 nodes for comparing alternatives. [Search configuration](../../chess-core/src/main/resources/match-ai.yaml)
- **Acceptance.** `MatchAiGovernanceTest` and `HardAdaptivePolicyTest` check the policies and `ChessBenchmarkSuite` measures decisions against a fixed oracle. Historical comparisons below belong to the measured version.
- **Displayed Elo.** The [Game Review contract](game-review.md) defines grades, accuracy, and the displayed estimates. The app reads its rating anchors from the release configuration. [Rating anchors](../../chess-core/src/main/resources/game-review.yaml)

<details>
<summary>Current search policies and bounds</summary>

The opponent levels and hint have separately configured depths and budgets. A search keeps the last depth it evaluated completely. Hard's foreground game search starts with 100,000 additional nodes and can continue to 200,000 with the adaptive policy below. It accepts the final horizon's result only if it keeps the same leading move as the previous depth. If the leader changes, the engine stays with the last complete, stable search unless the bounded [closed-promotion confirmation](#closed-promotion-confirmation) succeeds. The hint attempts up to seven plies, with a bounded two-ply frontier for captures, promotions, or escaping check. A comparison between pawn and piece captures can additionally follow at most two recapture plies when the piece capture permits a checking pawn reply. These visits use the existing comparison allowance; the nominal completed horizon is unchanged. Its completed depth and actual nodes describe the search achieved. In games and lessons, evaluation accounts for piece development, the center, king position, and the bishop pair in addition to material. The weights change according to the remaining material so an active king becomes useful in the endgame. The search checks captures, promotions, and moves that activate pieces first. Tactical tests keep their explicit material or checkmate objective. [Search policy](../../chess-core/src/main/resources/match-ai.yaml) · [Adaptive policy](../../chess-core/src/main/resources/hard-adaptive.yaml)

In rook endgames, Hard uses the full history to evaluate repetitions throughout the tree, including at the root. It can extend the search by one ply beyond the profile depth, up to depth 7, within the configured adaptive allowance. The extension and the 50 cp window for root candidates are set under `rook-endgame` in YAML. Governance rejects extensions or windows outside the validated limits.

In Hard games outside the repertoire and the special rook endgame, checking moves get priority in search order only. The 10,000 bonus is configurable and capped under `hard-ordering`. It does not contribute to position evaluation or change the node allowance. The hint shares this Hard ordering and its exact transposition machinery within its own allowance. Basic, Advanced, and the bar evaluation retain their existing ordering.

Hard's game search also reuses exact lines for transposed positions. Incomplete entries and alpha-beta bounds are excluded from reuse. Capacity is derived from the budget and capped in `hard-transpositions`. [Exact-cache evaluation](#exact-cache-for-hard)

Basic searches two plies in ordinary positions and three in elementary rook or queen endgames against a king. Both paths use zero additional nodes. Configure ordinary depth with `standard-max-plies` in YAML. Hard ends the search at the first complete horizon that proves a winning mate. [Basic calibration](#calibrating-basic-with-selective-depth) · [Mate proofs](#solving-puzzles-with-a-declared-objective)

The hint uses the Hard search kernel independently of the opponent's level. Each completed horizon scores all legal roots through the same tactical frontier. Up to 150,000 nodes from the single 200,000-node allowance verify the incumbent and a challenger from another completed horizon. Both phases share the exact transposition table and ordering. Existing single-root coaching overrides retain a larger seed allocation. If a deeper iteration stops, the last complete result remains available. A changed odd-horizon leader needs confirmation at an equal-turn horizon before replacing the supported result. The list displays at most three distinct first moves within 25 cp of the leader. In a mating position, only variations with the same result and distance remain. Playback uses the scored continuation without searching again or appending an unscored reply. Its static position estimate explains the displayed board and does not rerank the search result. [Reading the difference](README.md)

In Hard, in positions with equal material, the ranking available after four plies also uses the score of the same move at three. The shorter line's contribution is capped. For a quiet move, the credit falls when the provisional line suggests a gain of more than one pawn. Evaluated mates and draws retain their terminal scores. If depth six or confirmation of its changed leader does not complete, the engine keeps the last complete search. Explicit searches and coaching comparisons keep their declared budgets.

Basic, Advanced, and Hard share the documented opening catalogue. A profile-specific verified override takes priority, followed by the first documented continuation of the exact current position. Castling and en passant rights remain part of the identity. A different human reply selects its corresponding branch, and a real exit from theory restores the profile's normal search. Selection is deterministic. Review-only exclusions keep their scope. The live theory fallback is excluded from hint generation; verified coaching overrides and searched continuations retain their scopes. Loading checks legality, duplicate positions, and mates in one. [YAML repertoire](../../chess-core/src/main/resources/ai-repertoire.yaml)

In a cycle, Hard uses history before discarding alternatives. If the current position has already appeared twice, a move that would produce a third or later occurrence receives 0 cp at the search root. The engine compares alternatives within the same budget at the last completed depth. Among lines that remain favorable, Hard may choose a less-visited position if it retains an advantage of at least 100 cp and gives up at most 50 cp relative to the first line.

The third occurrence is a signal for move choice. The game ends automatically on the fifth occurrence, and Hard can choose that draw if it sees no advantage in the cycle. [YAML policy](../../chess-core/src/main/resources/match-ai.yaml) · [Regression game](../../atf-tests/src/test/resources/repetition-regression.yaml) · [FIDE Laws, arts. 9.2 and 9.6](https://handbook.fide.com/chapter/E012023)

The bar and first line of text use a separate estimate for the current position. The engine evaluates two plies, one for each side, and applies a fraction of the difference obtained at three. Both searches check exchanges at the horizon. The correction is capped at 25 cp within the display's own budget. Pressing the lightbulb preserves the displayed estimate and cycles through the lines in hint-search order.

</details>

## Queen recaptures at the hint frontier

A completed verification PV that ends with a queen taking a pawn must include a
legal non-pawn recapture in its score before that exchange can support the hint.
Reconfirmation begins only at the already completed seed horizon or deeper; a
preliminary shallow challenger cannot change that frontier. Every compared root
is recalculated at the same horizon, and all attempts consume the same public
200k allowance. An interrupted comparison retains the last completed ranking.
Earlier equal-turn support and cache entries from another frontier mode cannot
corroborate the new result. Selective verification cannot certify extra equivalent
offers merely because their new provisional scores are close.

Once this frontier is active, close the complete queen exchange, including a
queen capturing another queen and legal king, rook or knight recaptures. A common
prefix does not authorize restoring an older unresolved seed PV. A veto may retain
the incumbent only with its newly scored continuation and actual completed depth;
without a completed safe continuation, no unproved seed is published.

This is a search correction, not an appended display move, sacrifice ban or
position-specific opening exception. Pinned recaptures remain illegal, and
`…Qxg3+ hxg3 …Rh1#` must remain a proved mate. Ordinary opponent decisions and
Game Review keep their separate policies. The [component contracts](../../atf-tests/src/test/java/ro/atf/chess/HintQueenPieceRecaptureTest.java)
cover the actual Caro–Kann prefix, both-color frontier responses, cache modes,
bounded scores and a Sicilian counterexample. [Learning evidence](../testing/learning-archive.md)
keeps native strength and hint quality distinct.

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

## Hint exchange verification

After `1.e4`, the old hint ended at `…Nc6 Bb5 …Nd4 Bc4 …Nxc2+` before `Qxc2`. The preview appended that recapture after ranking, while the public selector retained the losing leader. The initial-board variation had the same defect with `Bxd7+`. Both failures are search regressions with legal moves.

The hint now charges the initial horizons, exchange frontier, and root comparison to one 200,000-node allowance. Capture and recapture use the same frontier when ranking alternatives. Alpha-beta cutoffs are bounds and cannot enter the exact-result cache. Playback preserves the scored line and the source checkpoint. A static replay estimate cannot silently replace that ranking.

Historical study `2026-11-17-j7-hint-repair` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

[Hint contracts](../../atf-tests/src/test/java/ro/atf/chess/MatchHintTest.java) · [Read-only replay](../../atf-tests/src/test/java/ro/atf/chess/HintPreviewTest.java) · [Opening profiles](../../atf-tests/src/test/java/ro/atf/chess/OpeningPlayTest.java)

## Selective hint comparison and move-by-move audit

The hint retains the incumbent and a challenger observed at another completed horizon. Both phases reuse the same exact-cache entries and ordering within one decision budget. A cutoff remains a bound. A full-window value inside its original window can be exact even when discarded child branches produced bounds. Warm, cold, disabled-cache, and interrupted-search controls verify this distinction.

An H6 reversal must use earlier H4 evidence. H5 uses H4 and H7 uses H6. The current even-horizon table cannot corroborate itself. An unsupported reversal retains the actual seed depth and publishes no accepted comparison. Search and comparison depths remain distinct from the number of replayed plies.

| Position | J7 common-horizon comparison | Published result at 200k | Stockfish 19 H19 expected score |
|---|---|---|---|
| `e4 c5 Nf3 d6 d4` | H6, `…cxd4 −48` versus `…Nd7 −82 cp` | Keep `…cxd4`; exclude `…Nd7` from equivalent suggestions | 477‰ versus 268‰ |
| `e4 c6 d4` | H5, `…d5 +1` versus `…d6 −33 cp` | Replace `…d6` with `…d5` | 468‰ versus 383.5‰ |

The Stockfish numbers compare the tested roots with opposing replies free. They are not J7 centipawns or Elo. On the matched 200k controls, 13 of 14 existing leaders stayed the same and the main Caro–Kann leader improved. A first candidate regressed the Catalan and the pre-`Nxc2` control and was rejected. Correcting self-corroboration preserved both incumbents. The initially sealed four-position transfer sample retained its leaders. After inspection it becomes regression evidence, without establishing a general strength gain. A separately added control after `e4 e5` changes `Nf3` to `Nc3`: Stockfish H19 gives +27 versus +4 cp, or 523‰ versus 503‰. This balanced theoretical alternative has a measured 20‰ loss and is reported separately from the 14 matched controls.

[Audit protocol and collection](../testing/hint-audit.md) · [Frontier contracts](../../atf-tests/src/test/java/ro/atf/chess/HintFrontierTest.java)

## First move-by-move collection

Historical study `opening-hints-v1` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

The H17/H19 audit separates the first recommendation from the deliberately played rank. It finds five leader decisions with at least 150‰ expected-score loss at H19. One deliberately selected `Bg5` loses 484.5‰ while that position's leader loses 9‰. Neither number establishes an Elo rating. The first collection remains immutable when later algorithms are evaluated.

| Control | Actual API and allowance | Chosen root, SF H19 | Verified alternative, SF H19 |
|---|---|---|---|
| Italian decision 5 | Hint, 100k in the path; same root at 200k | `…Bxf2+`, −311 cp / 0‰ | `…Qg6`, −49 cp / 444‰ |
| Same full position | Actual Hard, 100k additional nodes | `…Na5`, −120 cp / 155.5‰ | `…Qg6`, −49 cp / 444‰ |
| Italian decision 3 | Hint, 200k | `…Qd6`, −379 cp / 0‰ | `…Qd8`, −197 cp / 10‰ |

The Italian losses also reproduce on the prior engine. After the bishop capture, `Kxf2 …Qc5+ d4` is a real defense and the later knight fork matters. The hint's shallow credit and shortlist are distinct from the actual foreground Hard decision. In the matched White Caro–Kann grid after `…d5`, selected depths are H5/H5/H4 at 50k/100k/200k. A larger allowance therefore does not imply a deeper selected result or a stronger move. These are explicit follow-up cases, not a passed global-strength gate.

The first three Italian probes were rejected before the credit correction documented below; these historical results belong to source `a7e71d9`. The independent foreground Hard check preserved the move and depth on all 24 v1 histories against the prior engine. Five node costs changed, including two reserve uses from 100k to 200k. This sample establishes neither identical cost nor an Elo gain.

<details>
<summary>Rejected Italian probes and the retained contract</summary>

| Probe | Local result | Reason for rejection |
|---|---|---|
| V1 · raw credit filtering and removal of H2 fallback | S5 reaches `…Qg6` | Sicilian S7 changes `N1c3` to `Bg5`, SF H19 499.5‰ to 24‰; the Italian S1 200k control changes `…Nge7` to `…Qf6`, 243.5‰ to 26.5‰ |
| V2 · restore H3 and book fallback | Preserves Sicilian S7 | Still regresses the Italian S1 control |
| V3 · retain the raw eligible domain before credit pruning | Retains the relevant alternative | Cannot complete H4 within the 50k seed; S5 falls back to H3 and `…Bxf2+`; 78/79 component gate fails |

The rejected probes showed that broad filtering can starve the seed or regress another leader. The correction below separates the refuted material offer from a balanced exchange and retains diagnostic challengers. Actual Hard `…Na5` remains a separate P1. Old receipts and sound-sacrifice controls remain evidence, never runtime FEN bans or automatic training labels. [Frozen controls](../../atf-tests/src/test/java/ro/atf/chess/HintTransferRegressionTest.java)

</details>

[Audit protocol](../testing/hint-audit.md) · [Open algorithm feedback](../feedback/README.md)


## Italian credit correction and native Hard calibration

Earlier-horizon credit cannot sustain an immediate material offer contradicted by raw H4 evidence. Search retains the exact values and bounds observed before credit pruning; it does not add another search. Only a credit-backed incumbent offering net material outside the raw window is demoted. The balanced `…Bxb5` exchange and still-uncertain historical challengers remain available. Public suggestions stay distinct from comparison candidates. Fallback scores exclude ordering credit; preference can use evidence from other horizons and does not promise numeric sorting by those raw scores.

At Italian S5 the 200k hint recommends only `…Qg6`. Cold Stockfish 19 with free replies confirms at H19 `…Bxf2+` −311 cp / 0‰, `…Qg6` −49 cp / 444‰ and `…Nge7` −169 cp / 30.5‰. H17 preserves the order. In the distinct Traxler context the same `…Bxf2+` remains Stockfish's free leader at H17/H19 and J7 preserves it; non-mating sacrifices are not banned. The color-inverted Italian position retains the `Qg3` defense.

Among 44 identical-position, identical-budget hint requests, only the Italian S5 leader changes. Sicilian and Caro–Kann leaders stay unchanged; four new histories retain their leaders and alternatives. All 24 compared native Hard decisions keep move, depth and nodes. These samples do not qualify every offered alternative: Sicilian `Bg5` remains open. [Credit regressions](../../atf-tests/src/test/java/ro/atf/chess/HintCreditRegressionTest.java)

The November 18 calibration uses source `772251450f5d8cf337f8f31c18734f5601043e8d`, 50 paired starts and 100 games against Stockfish 19 `UCI_Elo 1500`, one thread and 6,000 requested nodes. Each of the 12,522 played plies has the actual native Hard analysis: 1,600 prefixes, 5,462 Hard turns and 5,460 before Stockfish replies. Independent PGN/JSONL replay and the Extent publisher verify the whole sample.

| Profile | W / D / L / unfinished | Central local Elo | Conservative 95% interval | Unfinished all lost / won |
|---|---|---|---|---|
| Native adaptive Hard 100k–200k | 39 / 3 / 37 / 21 | 1,507 | 1,357–1,656 | 1,433 / 1,581 |

The bootstrap uses openings as units with 20,000 samples. The cap is 220 plies; unfinished games remain distinct from draws. The difference from the configured anchor is +7 Elo, interval −143…+156. This is not a human rating or evidence of improvement over the previous Hard: the correction concerns hints and the native decision algorithm is unchanged. The app's anchors retain their governed evidence.

Historical study `2026-11-17-j7-italian-calibration` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.


## Native frontier study and quest audit

On November 18 source `6d14986` retains `…Na5` at H5 and 100k. Its native line `…Na5 Nb5 …Qc6 Nxe5 …Nxc4` stops before `Nxc6`, winning the queen. Stockfish H19 confirms +867 cp / 1,000‰ for `Nxc6`, but −545 cp / 0‰ for the tempting check `Nxc7+`. Immediately after `…Na5`, the quiet defense `Be2` gives White 912‰ against 478.5‰ for `Nb5`. The first capture or check does not certify the best reply.

Separate H6 root probes score `…Na5 −154 cp` and `…Qg6 −170 cp`, both exact. Each probe has its own 200k ceiling without previous iterations; this is not a complete decision at that cost. After their continuations, Stockfish H19 finds favorable `…Qxe5` and `…Nxe5` recaptures at +306 and +493 cp for Black. The pair therefore retains an evaluation error and unresolved exchange beyond root coverage alone.

| Experimental probe | Italian result | Verification | Verdict |
|---|---|---|---|
| Uniform two-ply tactical frontier | `…Qg6`, completed H4 and 200k | 83/87 components; three H19 losses of at least 150‰ across 32 families | Rejected |
| Same frontier with an even-horizon guard | Same focal H4 result | Local ablation without separate transfer qualification | Experimental |
| Frontier only for a legal queen capture | Reintroduces `…Bxf2+`, H5 and 200k | The oracle still refutes the offer | Rejected at the focus case |

The upstream implementation distinguishes stand-pat evaluation from check evasions and filters captures with static exchange evaluation. Treat these as separate hypotheses without copying numeric thresholds. [Stockfish 19 tactical search](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp#L1484-L1687)

The 32 families come from archived games, not globally unseen games. Positions were fixed before candidate comparison and oracle grading. At H19 the 15 changed choices have six WDL gains, six losses and three ties; aggregate difference is −513‰, or −16.03‰ over all 32 positions. Basic and Advanced retain move, depth and nodes in all 37 requests, including focus controls. The reference passes 87/87 components. No candidate enters the live engine or a duel; candidate ΔElo is not measured. Hard's local 1,507 Elo remains the 100-game estimate, 95% interval 1,357–1,656.

The puzzle benchmark incorrectly used the explicit 44k allowance for every adaptive decision. The new audit retains 44k for exact repertoire and the adaptive YAML ceiling for other positions. It rejects overspending either ceiling, an empty report, foreign accounting and substituted material. Frozen accuracy floors and the `training-048` boundary stay unchanged. Each position calls the actual Hard opponent analysis once without supplying the puzzle key or objective; Extent retains its move, depth, nodes and actual PV.

| Chapter | Accepted first decisions | Retained floor |
|---|---|---|
| Mate in 1 | 500/500 | 500 |
| Mate in 2 | 300/300 | 300 |
| Mate in 3 | 198/200 | 190 |
| Tactics | 93/100 | 86 |
| Training | 94/114; 40/48 on the frozen sample | 39/48 |
| Intermezzo | 24/24 | 24 |

Historical study `2026-11-18-j7-native-italian` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

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
| Hint | Up to 7, plus a bounded two-ply tactical frontier and up to two scoped recapture plies | 200,000 total, including up to 150,000 to compare alternatives |

- Governance requires strictly increasing depth and budget across opponent profiles. The hint uses at least Hard's configured depth and its own bounded allowance. In king-and-rook or king-and-queen endgames, all levels, the hint, and the bar use the same evaluation of the distance between the kings and the defending king's space. Tests verify conversion of the advantage into checkmate with both colors and preservation of White's perspective on the display.

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
    if ($LASTEXITCODE) { throw "Session $index failed" }
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

## Paired native/hint study and recapture rejection

The 18 November producer `e320379212dbb55afb392351da559b1cf949ac4d` records three 20-ply opening studies. At every checkpoint it captures native Hard, public hints and the displayed estimate before play. The native allowance remains at most 200k additional nodes; hints retain their separate 200k total. The production engine is unchanged.

| H19 observation | Measured result |
|---|---:|
| First hints with loss at least 150‰ | 12/60 |
| Offered variants with that loss | 15/83 |
| First opposing PV replies with that loss | 10/83 |
| Native recommendations with that loss, including shadow positions | 3/60 |
| Actual played Hard decisions with that loss | 2/21 |

All 960 raw Stockfish H17/H19 receipts replay successfully. Evidence integrity passes; the separate chess-quality benchmark fails. The first opposing reply and endpoints are covered, not every later hypothetical defense. After Caro–Kann `6.e6`, native `…dxc3` preserves the win while the first hint `…fxe6` loses 576‰. The White bar estimate is +36 cp where Stockfish reports −411 cp for White. Italian `Nd5/Na4` loses 467‰ in the hint and Sicilian `…Be7/…Nc6` loses 358‰. [Protocol and both verdicts](../testing/hard-hint-study.md)

The targeted recapture prototype closes `…Qxe5 / …Nxe5` at the isolated H6 leaves and ranks `…Qg6 −50` above `…Na5 −80 cp`, reversing the reference pair. Warm, cold, disabled and narrow-window tests preserve that local result. Individual isolated roots have separate caps; they do not prove complete-decision accounting. The full candidate chooses the unsound `…Bxf2+` and loses controls 019/021/022 by 478.5‰, 191.5‰ and 516‰ at H19. It is rejected and remains outside production.

Actual Game Review retains all moves and emits no study Elo, but nine classification disagreements remain stable at both oracle depths. The played roots are audited; review alternative lines are replayed legally without claiming independent grading of every alternative. [Review findings](game-review.md#measured-review-limitations)

The three capped games all retain result `*`. They produce no new Elo or candidate ΔElo. The separately qualified native Hard calibration remains 1,507, conservative 95% interval 1,357–1,656, from 100 games. Four full-history lessons are external study data, not runtime FEN overrides. [Current unresolved work](../feedback/README.md)

## Caro–Kann hint and forcing-pawn recapture verification

After `1.e4 c6 2.d4 d5 3.e5 c5 4.Nc3 Nc6 5.dxc5 d4 6.e6`, the hint now selects `…dxc3` instead of `…fxe6`. The former H6 comparison ended after `Qxd8` without the legal knight recapture. Closing only a check was insufficient: a later piece capture could remain unanswered. Broad closure regressed an independent history and was rejected.

The accepted scope compares pawn and piece captures only when taking the piece allows a legal opposing pawn capture with check. At the ordinary frontier it can follow the last capture's square for at most two further plies, including legal check evasions. Stand-pat remains unavailable in check while this extension is active. Its allowance is `hint.verification-recapture-plies` in schema 8; zero disables it. Seed search, ordinary native play and the evaluation bar retain their policies. Cache identity includes the recapture mode, so a seed value cannot impersonate the extended proof. Cutoffs and interrupted work never become exact cache entries. The selective comparison can replace the leader without introducing new equivalent offers.

The corrected public result uses seed H4 and a completed common H5, within exactly 200k total visits. It is not relabeled H6. Both roots remain available diagnostically; `…fxe6` is not offered as equivalent. Stockfish H17/H19 confirms zero expected-score loss for `…dxc3`; the old first hint lost 576‰ at H19. The preceding White `Nce2` hint remains intact.

Replaying the same 60 historical checkpoints changes only this public decision. All native Hard decisions, their depths, nodes and PVs, the displayed estimates, and all Italian/Sicilian hint outputs are identical. The new collection has 954 raw Stockfish queries. Large-loss counts at H19 improve from 12 to 11 first hints and from 15 to 14 offered moves, with 10 first opposing PV replies still deficient. The absolute quality benchmark still fails at the unchanged 150‰ boundary. These capped replays produce no new Elo.

Sixteen development histories exposed the rejected general scope. After freezing sixteen other PGN histories with a potential forcing-pawn context, two leaders improve at both oracle depths: H19 loss falls from 243.5‰ to 8‰ and from 88‰ to zero, without a new leader loss in that set. These are distinct historical game checkpoints, not sixteen statistically independent opening families. The first case retains a deficient secondary offer at 243.5‰; it remains open work. Actual Game Review preserves all sixty turns and its previous nine classification disagreements. A hint correction does not certify review grades.

Historical study `2026-11-18-j7-caro-hint-recapture` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

## Italian queen recapture and hint continuity

After `1.e4 e5 2.Nf3 Nc6 3.Bc4 Bc5 4.Nc3 Qf6`, the public hint selects only `Nd5`, replacing `Na4`. The old line ended after `Nd5 Qd6 O-O Na5 Bb5 Bxf2+ Rxf2 Qxd5`, before White's legal `exd5` wins the queen. Stockfish 19 H17/H19 confirms zero expected-score loss for `Nd5`; the previous `Na4` lost 467‰ at H19. The accepted H6 continuation is `Nd5 Qd8 O-O Nf6 Nxf6 gxf6`, not the old unrecaptured exchange.

The shared hint search rechecks its completed H3 only when a unique non-mating quiet leader creates a new attack on the opposing queen outside the repertoire. It may resolve one legal pawn recapture of a queen at the tactical frontier, within `hint.verification-recapture-plies` and the same total 200k. A geometric attack allocates work; actual recapture legality is checked separately, including pins. Seed and comparison inherit the same mode, whose identity is part of the exact cache key. Interrupted confirmation keeps the already completed horizon; an incomplete deeper search is not advertised as finished. Diagnostic comparisons may activate this mode from the supplied roots. Neither ordinary native play nor the evaluation bar receives the extension.

The public result retains seed H4, comparison/selection H6 and 200k total visits. The explicit cold `Nd5/Na4` comparison completes H5, with +92/+70 cp; this compressed native gap is not calibrated to Stockfish. Selective recapture can replace the leader without creating new equivalent offers. Bulb, playable preview, comparison and destination tap use the calculated line and preserve the original history. [Components](../../atf-tests/src/test/java/ro/atf/chess/HintQueenRecaptureTest.java) · [Policy](../../chess-core/src/main/resources/match-ai.yaml)

Producer `3b1e7ed1a4f2659b544d29732848d8078c5f3abc` replays the same sixty historical checkpoints. Every native report and displayed estimate is identical. Only two first recommendations change: `Na4 → Nd5` and later `Bb5 → Be2`; Sicilian and Caro–Kann leaders stay intact. At H19 the matched counts become 9/60 deficient leaders, 12/83 deficient offers and 9/83 deficient first opposing replies, compared with 11/60, 14/83 and 10/83. Integrity passes; the unchanged absolute 150‰ quality floor still fails. Actual Game Review remains identical on those sixty moves and retains its nine known classification disagreements.

The rejected broad activation regressed an independent leader from 132‰ to 274‰ loss. Its sixteen inspected histories are development controls, not a fresh validation claim. After narrowing and freezing sixteen other opening histories, one leader improves from 339.5‰ to 17‰ and another declines from 0‰ to 13.5‰; no new leader crosses 150‰ there. The development set separately retains a 0‰ to 18.5‰ decrease. These targeted queen-pressure samples do not prove global strength or perfect secondary offers.

Historical study `2026-11-18-j7-italian-nd5-hint` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

The first opposing PV reply at the target improves from 273‰ to 13.5‰ loss at H19. At the later corrected Italian leader, however, reply loss rises from 24.5‰ to 86‰. Both stay below the frozen large-loss boundary; neither proves an optimal full continuation.

## Sicilian exact ties and pin reinforcement

After `1.e4 c5 2.Nf3 d6 3.d4 cxd4 4.Nxd4 Nf6 5.Nc3 a6 6.Bg5 e6 7.f4`, the public hint now offers only `…Nbd7`. Both it and `…Nc6` still score −21 cp at completed H5; seed H3 and the total 200k ceiling are unchanged. The former root order hid a relevant difference: `…Nbd7` adds a defender to Nf6, relatively pinned by Bg5 to Qd8. Stockfish 19 H17/H19 independently grades the selected root; H19 loss falls from 358‰ for `…Nc6` to 33‰ for `…Nbd7`. The free H19 oracle prefers `…Qb6`, so the correction does not prove a globally optimal hint or a calibrated native score.

`HintPinSupport` applies only after score equality. A quiet bishop or knight must safely add a new defense to another minor already pinned against its king or queen. A defender pinned to its king, a directly capturable defender, a missing royal piece behind the ray, or a queen-pin attacker itself pinned to its king invalidates that preference. Unequal centipawn values and mate scores remain primary. An accepted defensive tie yields one public offer; the other root remains in the diagnostic comparison. This does not weaken support requirements for numerical reversals or add a FEN, opening-ID or oracle lookup. [Preference](../../chess-core/src/main/java/ro/atf/chess/HintPinSupport.java) · [Components](../../atf-tests/src/test/java/ro/atf/chess/HintPinSupportTest.java)

Bulb, manual board, destination tap and explicit comparison share the same hint kernel and scored PV. The native −21/−21 equality is preserved rather than replaced by Stockfish values. The H19 cost of the first displayed opposing reply remains 21.5‰; neither it nor the remaining continuation is certified optimal. Native play, the bar, cache semantics and Game Review remain unchanged on all sixty matched checkpoints. Only Sicilian phase13 changes. Large-loss counts become 8/60 leaders, 11/82 offers and 9/82 first opposing replies; absolute quality still fails at the unchanged 150‰ floor.

The earlier frontier prototypes are rejected. Resolving a checking queen capture alone did not change the target. A full pawn-threat extension selected `…Nbd7` while still stopping a different exchange before its legal recapture; closing that exchange removed its apparent advantage. Passing the old component controls did not qualify that chess result. The [Stockfish terminology](https://official-stockfish.github.io/docs/stockfish-wiki/Terminology.html) describes why quiescence matters, but does not establish the correctness of these bounded prototypes. Retain their source snapshots and witnesses instead of treating the selected move alone as proof.

Producer `b90f181b707a3a7dd4c67147a31208534b69f0e2` retains all native reports and the other 59 complete hints. Sixty-four geometrically selected PGN histories have identical old/new outputs; the first sixteen receive H17/H19 root checks. This establishes preservation, not activation or independent gains. Two new twenty-ply thematic studies follow forced `…Nbd7` and inferior `…Nc6` prefixes, then six fresh played plies each. They overlap the inspected Sicilian prefix and do not form a strength league. The audit validates 284 new raw requests and retains original identities for 85 unchanged oracle projections from the earlier 1190-request bundle. All 100 current native receipts are newly captured; old native receipts are never relabeled as current. [Audit contract](../testing/hard-hint-study.md)

Historical study `2026-11-18-j7-sicilian-knight-hint` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

## Sicilian four-root coverage

After the same `7.f4` prefix, the hint now compares `…Nbd7`, `…Nc6`, `…Be7` and `…Qb6`
at completed H5 within one total 200k allowance. The raw scores remain −21/−21/−25/−29 cp.
`HintCandidateCoverage` adds a missing native-theory challenger to a two-root defensive
comparison, then at most one safe quiet queen move creating new pressure on an undefended
pawn and a minor. It adds work, not a score bonus, FEN lookup or automatic theory answer.
All four scored roots remain diagnostic; only `…Nbd7` is publicly offered.

Stockfish 19 H17/H19 checks every compared root, first opposing PV reply and endpoint.
At H19 the four forced-root losses are 33/358/10.5/11‰. Free H19 selects `…Qb6`; the separately
forced queen search differs by 11‰, so it is not reported as an invented exact zero.
First opposing replies cost 21.5/15.5/69.5/37‰ respectively. Their legal lines are audited,
not certified as best play at every later ply. Wider coverage does not repair the native
numerical underranking of the queen and bishop.

The matched sixty-phase replay changes only the target hint comparison/PV. Italian and
Caro–Kann hints, all native decisions, estimates, checkpoints and played moves remain identical.
Sixty-four frozen transfer outputs are also identical. Historical large-loss counts remain
8/60 first hints, 11/82 offers and 9/82 first opposing replies. Two new twenty-ply studies use
`…Qb6` and `…Be7` prefixes; with these forty phases included, counts are 15/100 leaders,
19/142 offers and 15/142 replies. These new cohorts expose residual weaknesses rather than
establish a paired regression or an Elo change.

The source-qualified audit retains 100 fresh native phases, 308 new raw Stockfish requests
and 958 source-preserving exact-query projections. Free/forced scores must share one oracle
producer as well as history, depth and options. Mixing valid raw requests from two runs is
rejected. The 119 component/report checks and scoped qualification pass; absolute hint
quality still fails at the unchanged 150‰ boundary.

Historical study `2026-11-18-j7-sicilian-candidate-coverage` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

## Qualified rook mate readmission

The 18 November closed-mate producer `cc37530be13cf5ade582b3e1d42784fcc4b28e21`
recovers `mate3-04iY6` (`Kc4`, replacing `Kxd4`) and `mate3-019gx` (`Ra4+`, replacing
`Ra8+`). The selected proofs complete H5 at 19,865 and 75,415 additional nodes.
Both forced mates are checked against every legal defense, including the color-reversed
positions. Native analysis receives no quest ID, key, defense or declared mate length.

Ordinary rook search keeps its validated shortlist and completes its configured horizon
first. If it has no winning mate, roots omitted from that shortlist may re-enter for a
mate proof. Checks are ordered first, and deeper odd proof horizons start beyond the
already searched H3. A narrow mate-threshold search only nominates a candidate. It must
then complete a full-window confirmation at the same proof horizon, using the same
remaining allowance, cache and repetition context. Only a confirmed positive mate replaces
the incumbent; a failed or interrupted proof leaves the completed choice intact. The
selected report records H5 when H5 proved the mate, rather than borrowing the prior H7 label.

At a 19,864-node adaptive base, confirmation is interrupted and `Kxd4`/H7 survives.
At 19,865, the exact `Kc4`/H5 proof completes. The maximum remains 200k in both probes,
so cache capacity and the shared ceiling remain fixed. Existing repetition and draw
controls pass. [Implementation](../../chess-core/src/main/java/ro/atf/chess/TacticalAnalysis.java)
· [Targeted component tests](../../atf-tests/src/test/java/ro/atf/chess/RookRootReentryTest.java)

General readmission at every depth was rejected: it improved three catalog stages but
lost wins in `held-rook-08` and `held-rook-12` at both Stockfish depths. Spending the budget
on more roots prevented the old winning H7 response from completing. The qualified mate-only
scope preserves these controls. A second cohort of 24 new legal rook/pawn compositions,
frozen after the algorithm and before its outputs, has no root-WDL regression at H17/H19.
They are verification positions, not new public puzzles or Elo games.

### Rook-mate qualification baseline

| Observation | Qualified result |
|---|---:|
| Canonical native exercises complete | 1,184/1,238, previously 1,182 |
| Canonical stages accepted | 2,223/2,303, previously 2,221 |
| Previously accepted stages lost | 0 |
| Existing mate-in-1 / mate-in-2 / mate-in-3 chapters | 500/500 · 300/300 · 200/200 |
| All legal defending continuations in the mate chapters | 1,500 terminal branches, zero failed |
| Current educational stages still unaccepted | 80 in 54 material exercises |

The exhaustive mate audit retains the 1,000 actually selected initial moves, then records
1,236 fresh native continuation decisions. Its independent receipt replay consumes every
legal defender branch exactly once. The portable `NativeMateDefenseBenchmarkTest` independently
recomputes all 2,236 native choices, with the same 1,500 mating leaves. It shares
`NativeMateAudit` with the targeted tests; no emulator, external data directory or declared-goal
solver is required. [Commands and distinction from objective-aware solvers](../testing/hard-puzzle-study.md)

Stockfish H17/H19 now grades all 80 remaining canonical misses, not a selected sample.
57 stages have a loss of at least 150‰ at both depths; 22 preserve the win at both depths
despite missing the educational goal; one has a smaller or depth-sensitive loss.
Among the 54 first missed educational stages, 43 have a large WDL loss at both depths. Calling these the chronological first WDL losses additionally requires checking the previously accepted prefixes. Later stages
after a reference intervention remain marked conditional evidence, not independent games.
All 80 inspected curriculum keys stay below the existing large-loss boundary at both depths.

`training-051` remains `Ke6` instead of winning `Rc8+`; both oracle depths confirm win→draw.
The mate fix does not claim this non-mating endgame solved. This baseline identified
`training-107:1`: six pieces, `c5` instead of `h8=Q`, H5 and 14,652 additional nodes,
with a 500‰ loss at both depths. Its qualified promotion recovery and the newly exposed
conversion gap are recorded below. The unchanged winning alternatives and mate gate remain controls.

Full-catalog mean additional work rises from 14,371.56 to 15,537.58 nodes; p95 stays at
100k and the maximum at 200k. These are deterministic work counts, not isolated latency or
an Elo measurement. Ordinary native H3 remains separately unbudgeted. The 146 component/report
checks pass; the new portable full-mate benchmark also passes. The all-exercises-solved
quality verdict remains FAIL for the 54 incomplete material exercises.

Historical study `2026-11-18-j7-rook-root-reentry` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

## Closed promotion confirmation

`training-107:1` exposed a stopping defect, not a lack of nodes. After `f6g7 f8d8`, H4
ranks `h8=Q` at +167 cp and gives `c5` an upper bound of −109. H5 prefers the cooperative
`c5 Re8 h8=Q Rxh8 Kxh8` at +190. The completed H6 reverses that order: `c5 Rd7+` is
−115, while `h8=Q Rxh8 Kxh8` is +183. The final stability veto previously kept H5.

Full-window confirmation resolves `c5` at H4 to −279 cp, rather than its preliminary
−109 upper bound. The mechanism never treats that bound as an exact comparison score.

The [promotion policy](../../chess-core/src/main/resources/hard-promotion-exchange.yaml)
allows one structural exception. The actual PV must close a promotion, its capture and a
legal recapture on the promotion square, retaining at least the configured material gain.
Full-window candidate/incumbent comparisons must be exact, positive and strictly better
at both prior-even and final-even horizons. All verification uses the same native allowance;
incomplete confirmation preserves the old leader and completed depth. No curriculum key,
FEN, oracle score or bonus is read by the runtime policy. [Implementation](../../chess-core/src/main/java/ro/atf/chess/PromotionExchangePolicy.java)
· [Components](../../atf-tests/src/test/java/ro/atf/chess/PromotionExchangePolicyTest.java)

| Observation | Qualified result |
|---|---:|
| Actual promotion choice | `h8=Q`, H6, 15,589 additional nodes |
| Canonical native exercises complete | 1,184 → 1,185 / 1,238 |
| Canonical stages accepted | 2,223 → 2,224 / 2,303; zero losses |
| Feature disabled | Identical move, depth, nodes and PV on all 2,303 reference decisions |
| Untouched thematic compositions | 48 positions / 24 geometry families; 12 changed choices, no WDL regression at H17/H19 |
| Extended opposing play | 178 actual plies across six contexts and two arms; 90 native decision checkpoints |
| Independent oracle audit | 463 cold H17/H19 queries, with raw commands, exact scores and legal PVs |

At 15,588 diagnostic base nodes, confirmation is interrupted and `c5`/H5 remains. At 15,589,
the exact comparisons finish and `h8=Q`/H6 replaces it. Both keep the 200k maximum and cache
capacity. A foreign loaded-policy snapshot is rejected by the current catalog publisher.
The runtime ceiling stays adaptive 100k→200k additional nodes; initial H3 remains unbudgeted.
Mean catalog work changes from 15,537.58 to 15,537.99 additional nodes. No latency or Elo gain
is claimed. Basic and Advanced retain their complete analyses at each compared context.

**Limit of the promotion-only baseline.** In free play the reference draws by repetition
after `c5`. The candidate reaches `h8=Q Rxh8 Kxh8 Ke4`, then loses the win with `Kg7` instead
of `a4`; both oracle horizons confirm 500‰ loss. That line finishes as a bare-king draw after
12 plies. That gap is closed by the [bounded conversion proof](#bounded-pawn-ending-conversion) below. `training-051` still chooses
`Ke6` instead of winning `Rc8+`. The 79 remaining canonical misses comprise 56 large WDL losses,
22 win-preserving goal misses and one smaller/depth-sensitive case. Their 53 first educational
misses include 42 large losses; accepted prefixes still need checking before claiming first
chronological WDL loss. The all-exercises-solved verdict remains FAIL for 53 exercises.

Historical study `2026-11-18-j7-hard-training107-promotion` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

## Bounded pawn-ending conversion

After `h8=Q Rxh8 Kxh8 Ke4`, ordinary Hard genuinely completes H6 and prefers `Kg7` at
+104 cp. This time the stability veto is not the cause. The horizon values the remaining
pawn after its partner falls, rather than the tempo that lets `a4` win. The [pawn policy](../../chess-core/src/main/resources/hard-pawn-promotion.yaml)
adds a bounded proof for at most two own pawns against a bare king, without changing weights,
repertoire, Basic/Advanced or the ordinary H6 search.

`PawnPushProof` accepts a winning plan only after every legal defending king reply has a
winning continuation. It may choose pawn moves and king moves that strictly decrease an
escort potential. Pawn progress and captures are irreversible; the king potential prevents
internal cycles. Prior game positions remain forbidden, and a claimable fifty-move draw
rejects the proof. A queen must survive every immediate legal capture and cannot stalemate.
The terminal goal is safe queen material, not an invented mate. Missing or interrupted proof
means unknown and preserves the ordinary decision. [Proof implementation](../../chess-core/src/main/java/ro/atf/chess/PawnPushProof.java)

| Observation | Qualified result |
|---|---:|
| Required position | `a4`; ordinary H6 plus a separate 23-ply proof |
| Shared work | 2,568 ordinary + 14,325 proof = 16,893 additional nodes |
| Reference conversion | Draw in both colors, eight plies each |
| Actual candidate conversion against free Stockfish H19 | Checkmate in 37 plies as White and 31 as Black |
| Native game WDL losses at H17/H19 | Zero on both candidate paths |
| Untouched thematic positions | 24 positions, two changed choices, zero root-WDL regressions |
| Existing canonical catalog | All 2,303 decisions, scores, work and PVs unchanged; 1,185/1,238 complete |
| Existing mate chapters | All 1,000 retain every legal defending branch |

`SearchReport.depth` remains the completed ordinary horizon. Optional `pawnProof` records
the separate plan length, node work, cache limit, quiet counter, history and winning-strategy
digests. It is not a claim that every root was searched to H23. The proof shares the existing
adaptive 100k→200k additional-node allowance and cache capacity; H3 remains separately unbudgeted.
At a diagnostic 5k maximum, incomplete proof preserves `Kg7`/H6 and publishes no certificate.
[Proof and negative components](../../atf-tests/src/test/java/ro/atf/chess/PawnPushProofTest.java)

The source-bound audit records 84 actually played plies across reference/candidate and both
colors, with 313 cold oracle queries. Six fresh H17/H19 queries confirm the unchanged control:
`training-051` still chooses drawing `Ke6` instead of winning `Rc8+`. The 79 educational misses
in 53 exercises remain open. Neither two mirrored conversions nor the transfer roots establish
an Elo gain. Full held-out conversion and broader material scopes remain separate work.

The 128 focused component/report checks, complete native mate benchmark, final receipt-reader
checks and four raw-evidence checks pass. Native JSON decoding is shared by the catalog and
`HardQuestAudit`; legacy receipts without a proof remain readable. Longer native PVs require
their retained certificate, and replay rejects substituted work, history, opposing coverage,
strategy or evaluation. [Repeatable audit contract](../testing/hard-puzzle-study.md#auditing-winning-plan-certificates)

Historical study `2026-11-18-j7-hard-promotion-conversion` has been retired from the active learning archive. The retirement inventory is `C:\work\sah\.studii\metadata\retired-studies.json`; source history remains in pinned Git references. Use the [current admission contract](../testing/learning-archive.md) and newly recorded journals instead of deleted game bodies. Historical scores above are not a new current-source Elo estimate.

## Native secondary-move verification

Native Hard now probes secondary moves with a null window inside its ordinary
alpha-beta search. A result that improves alpha inside the parent window is searched
again at full width before it supplies the principal variation. A fail-high remains
a lower bound; a fail-low remains an upper bound. The existing exact-only cache and
node visits retain their contracts. This mode is enabled only in ordinary Hard
search outside theory; hint, review, Basic, Advanced and specialized rook search
retain their policies. [Primary search reference](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp)

[Independent minimax components](../../atf-tests/src/test/java/ro/atf/chess/HardPrincipalVariationTest.java)
compare five legal positions at H1/H2/H3, with both modes, cold/warm caches and
fail-high/fail-low recovery. The diagnostic full-catalog replay preserves the accepted
result of all 2,303 canonical stages: zero gains and zero losses. It changes one
selected move and 843 complete analysis records. Mean additional work rises from
15,537.99 to 16,798.86 nodes; 19 horizons are shallower and five deeper. This is not
an across-the-board speed or depth gain.

On the 106 observed positions from English, Scandinavian and the sound queen-offer
control, replay changes one selected move. At Scandinavian ply 12, `g4` loses
526.5/620 per mille at Stockfish H17/H19; `Ne4` loses 25.5/110.5. The selected
ordinary horizon is H4, not the old H5, within the same 200k allowance. Six stable
large native losses become five, with no newly confirmed large loss in this cohort.
Basic and Advanced retain all 212 complete comparison analyses. This is a local
quality result, not a measured Elo gain or a proof on unseen opening families.

The [portable new witnesses](../../atf-tests/src/test/resources/hard-continuation-witnesses.yaml)
retain original full histories and raw cold receipts; the new knight alternative is
an explicitly separate replay, never a replacement for the historical pawn move.
[Components](../../atf-tests/src/test/java/ro/atf/chess/HardContinuationWitnessTest.java)
protect that improvement and reject substituted depth or producer. English
`…Qf6` versus `…Nc6` and Queen's Gambit `…Qa5` remain open. The French native
repair is qualified separately below; its hint and review still need repair.

Uniform one-ply tactical closure was rejected: 24 gained and 40 lost canonical
stages, despite repairing the isolated French choice. Continuing every checked
frontier also remains experimental: the small legality goals improve while 13
existing component contracts fail. The [frontier quality goals](../../atf-tests/src/test/java/ro/atf/chess/TacticalFrontierCheckTest.java)
keep mandatory evasions and exhausted comparisons visible in the benchmark suite;
they are not silently counted as a normal component PASS. Preserve sound sacrifices
and all scoped queen-exchange controls before broadening this frontier.


## Closing a pawn recapture at the completed horizon

The French witness is `Bb5+ …c6 Nc4 …Qd7 Nxf5 …exf5`. `Nxf5` captures an existing
pawn; it is not a quiet move. At H5 the isolated bishop root scores +153 cp and
stops at that capture. At the same full-window H6, the bishop root scores −204 cp,
including `…exf5`, while `Nc4` scores −97 cp. Cold Stockfish H17/H19 gives the bishop
475.5/453‰ loss and the forced knight alternative 5.5/5.5‰.

Increasing the unchanged search to a diagnostic fixed 300k–500k does not finish H6.
The first wider ordering/cache prototype does finish H6 but still returns the H5
bishop because of the stability veto; it also regresses on a Queen's Gambit control.
That wider activation was rejected and the control was added before narrowing the
implementation. These are diagnostic budgets, not a new production node policy.

The accepted scope reuses work for a non-mating odd-horizon piece-for-pawn capture
that permits a legal pawn recapture. Early H3 activation additionally needs a checking
first move. Later completed odd horizons can activate the same work. The pending
exchange opens the existing adaptive reserve; base 100k and maximum 200k remain.
Ordering observations are capped at 4,096, never supply a score and are used only
in that mode. Exact and separately typed bound values share the loaded cache limit.
A bound cuts only the matching alpha/beta window and never becomes an exact value.
The horizon key retains position, remaining depth, distance, objective and frontier
modes. Old exact entries cannot match a different ordinary horizon; work reuse keeps
ordering observations while discarding stale values. [Upstream search](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp)

The ordinary stability veto remains. Its narrow exception requires a **completed**
common-horizon root comparison and an exact incumbent line that extends the former
PV by the legal pawn recapture. Material must change from a non-losing gain to a loss,
and the new completed leader must beat that closed incumbent. A further legal
recapture on the same square or a resulting check leaves this proof open. Missing,
foreign-prefix, bounded or interrupted evidence cannot override the guard. This is
a local exchange test, not a claim that every other tactical move is exhausted.

| Focus | Result |
|---|---|
| French original | `Nc4`, full ordinary H6, 175,874 additional nodes |
| French mirrored | `…Nc5`, full ordinary H6, 173,121 additional nodes |
| Independent forced root loss | 5.5/5.5‰ at H17/H19 |
| Scandinavian control | `Ne4` retained; 25.5/110.5‰ |
| Queen's Gambit regression | `Bg3` retained; the rejected wider prototype chose `Bd6` |
| Work-boundary negative | One unpaid node keeps the previous complete horizon |
| Full canonical replay | All 2,303 acceptance outcomes and selected moves unchanged versus the preceding qualified Native source |
| Current/prior game frames | 236 replays, three changed choices, one large French gain and no new large loss |

The frame comparison reuses known opening ancestry and is not an independent league.
Two smaller French changes include an H19 deterioration of 2.5‰; do not describe the
result as uniformly better. The final component suite has 473 passes and four
explicit skips. Both separate French coaching quality goals still fail.

[French components and quality goals](../../atf-tests/src/test/java/ro/atf/chess/FrenchRecaptureTest.java)
retain the original histories, material ledgers, both colors, actual interrupted
searches, bound/foreign-prefix mocks, pins, further recaptures and checking recaptures.
[Cache components](../../atf-tests/src/test/java/ro/atf/chess/FrontierCacheTest.java)
compare both bound windows with independent exhaustive minimax and enforce cache,
ordering, horizon and mate-distance ownership. [Portable cold evidence](../../atf-tests/src/test/resources/french-recapture-witnesses.yaml)
contains 32 raw phases and the regression discovered during this study. It is not a
runtime opening book or a learned FEN override.

The public hint and Game Review keep their independent policies. They still need
their own repair: the hint can use weak `…Nd7`, and review can call `Bb5+` BEST.
Their executable benchmark goals remain separate failures. Dynamic frontier verification
is implemented below. A shorter visible prefix with a separately browsable hidden proof
remains [presentation work](../feedback/README.md#tactical-proof-and-displayed-lines).

## Dynamic coaching frontier

[CoachingProof](../../chess-core/src/main/java/ro/atf/chess/CoachingProof.java) owns a
separate reserve for a visible unresolved frontier in hints and Game Review. It activates
for an unanswered check, a legal recapture after the last capture, or a legally exposed
last important piece. A completed mate or an already closed line retains its existing
contract. Native opponent search and Basic/Advanced policies do not use this reserve.

[coaching-proof.yaml](../../chess-core/src/main/resources/coaching-proof.yaml) configures
the maximum reserve, allocation step and forcing safety limit. The default reserve is
one million nodes, allocated as needed in 25,000-node steps. These are additional to the
ordinary hint allowance of 200k or the configured review allowance. `extraNodes` still
means ordinary work; `proof.nodes`, `proof.ordinaryNodes` and `totalNodes` make the sum
explicit. Do not describe the complete coaching decision as capped at 200k.
Review retains its existing H3 bootstrap outside the extra-node allowance; the summed
counter describes charged ordinary extra work plus reserve, not that inherited bootstrap.

Every compared root uses the same completed ordinary horizon and forcing policy. Hints
retain their established offered/compared root scope; review compares every legal root
and fully evaluates the played move. Noncompetitive roots may receive upper bounds, but
only exact lines enter the returned ranking. The best two values remain exact. At the
frontier, captures and promotions continue without the old one/two-ply cutoff; a checked
position searches every legal evasion and cannot use stand pat. Optional quiet checks
beyond the ordinary horizon are not a claim of this quiescence search. The
[upstream reference](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp)
describes the same distinction between captures and mandatory evasions.

A changed hint leader also needs support from the preceding completed even horizon,
paid from the same reserve. Otherwise the established root retains its newly calculated,
closed line and score; the old unfinished PV is never restored. The receipt records both
root scores, the earlier comparison and the selected root. An unsupported challenger is
not advertised as equivalent. This guard preserves an independently verified witness in
which the unguarded forcing model preferred `Bxe4` over `Nxd7` and lost 335/243.5‰ WDL.

The proof has separate COMPLETE, NODE_LIMIT and FORCING_LIMIT outcomes. Ordinary depth
and additional forcing depth stay distinct. A hard ceiling or unfinished confirmation
returns no scored hint lines; review throws a typed incomplete result before publishing
any grades or graph. Cancellation propagates without a partial value cache. There is no
transposition-value reuse across proof modes or histories. Repetition is checked on the
actual explored path; position-only hints do not invent preexisting match history.

The complete legal PV and work receipt remain available to audit. Review cache v2 retains
them across restart and rejects a receipt for another position. Legacy v1 remains readable
with no fabricated proof. Native adapters distinguish incomplete verification from game
over and never save a partially generated review.

[Components](../../atf-tests/src/test/java/ro/atf/chess/CoachingProofTest.java) cover continuous
exchanges, exact node boundaries, pins, en passant, mandatory evasions, cancellation,
incomplete publication, restart and v1 compatibility. [Cold teacher controls](../../atf-tests/src/test/java/ro/atf/chess/CoachingProofOracleTest.java)
retain Stockfish H17/H19 receipts, sound queen mate and the promotion-stalemate counterexample.
[Stability controls](../../atf-tests/src/test/java/ro/atf/chess/CoachingStabilityTest.java)
retain both horizons and the interrupted-confirmation negative. Completion certifies the
declared search scope, not optimal chess or an Elo improvement. The French quiet-defense
and review-calibration feedback remains open.
