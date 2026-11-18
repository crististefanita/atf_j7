# Feedback · Chess Lab

## Overview

The register tracks open gaps and closure criteria. The [board guide](../board/README.md)
describes the behavior, and the [release catalog](../../release/README.md) points to the published evidence.

[Reviewing test moves between suites](../testing/README.md#test-changes-against-a-git-baseline) describes the Git baseline and coverage evidence.

| Priority | Area | Observation | Closure Criterion |
|---|---|---|---|
| P1 | Release · governance identity | XML identity is checked by name and verdict. | Reject a PASS receipt from another commit or run. |
| P2 | Release · compact check archive | The compact proof has a separate archive. | Reject missing phases, foreign runs and replaced PNGs. |
| P2 | Game Review · NVDA | RO screen speech repeated position and score. | Confirm one announcement in RO and EN and focus return. |
| P1 | Italian · unresolved native exchange | Uniform extension repairs the example but loses three families; queen-only checking revives the false sacrifice. | Resolve the exchange at a common horizon within 200k, preserving controls and independent transfer before a duel. |
| P1 | Hint · independent alternative accuracy | The correct Sicilian `N1c3` leader still offers the inferior `Bg5`. | Correct the offered alternatives without regressing the leader, with common-horizon and free-reply evidence. |
| P2 | Hint audit · broader transfer | The initial plan covers three short opening paths and three budgets. | Add new history-separated families and measure leader loss separately from deliberately played lower ranks. |
| P2 | Puzzles · TalkBack focus in options | Puzzle option focus still needs speech evidence. | Capture the menu focus path in both languages. |
| P2 | Android · predictive Back gesture | Predictive Back still needs a compatible device. | Exercise cancellation and resumption on API 33+. |
| P1 | AI profile calibration | The Elo intervals still overlap. | Add paired games to narrow the rating uncertainty. |
| P1 | Hard adaptive strength | Foreground search uses 100,000 nodes and a reserve to 200,000. | Compare the three modes on held-out games and verify the opposing reply. |
| P1 | Hard learning from PGNs | The loss-trained ordering model keeps all final moves in 36 new families. | Add preserved moves and sacrifices, then verify ranking and complete decisions. |
| P1 | Hard search · generalization | Selective recapture regresses on independent openings. | Require independent gains at the fixed budget. |
| P1 | Hard · conversion after a pawn capture | The pawn capture can lose its advantage later. | Verify conversion on new positions and distinct replies. |
| P1 | Transfer from tactics to games | Hard misses tactics without a declared objective. | Measure discovery and preserve the solver contract. |
| P2 | Progress · Android upgrade | A signed upgrade still needs a progress probe. | Keep IDs, solutions and points across upgrade and restart. |
| P2 | AI quality · material evaluation | The material score differs from Stockfish. | Reduce the discrepancy with fixed search budgets. |
| P2 | Puzzle calibration | Imported difficulty bands overlap. | Check attempts and time on a separate shuffled set. |
| P2 | Android · separating screen transitions | The Android screen owns several session transitions. | Move transitions into a JVM-tested owner. |

## Evidence and Closure Checks

<details>
<summary>P1 · Release · governance identity</summary>

Preflight ties the test-change receipt to Git and checks the Documentation, Release, and Extent XML files by name and verdict.

Bind each XML file to the candidate commit and qualification run. Replace each XML in turn with a PASS receipt from another commit or run and verify rejection while keeping the current Git receipt. [Preflight](../../release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java) · [Tests](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)

</details>

<details>
<summary>P2 · Release · compact check archive</summary>

The Android publisher verifies the bilingual compact report. The reusable gate qualifies the general Android phases. The procedure also calls for the compact archive when geometry changes.

Add compact verification to the promotion gate for geometry changes. Reject a missing phase, a phase from a different run, and a substituted PNG. Keep validation for archives from previous releases. [Qualification](../release/README.md#qualification-and-publication) · [Compact publisher](../../atf-tests/src/test/java/ro/atf/chess/AndroidSanityPublication.java)

</details>

<details>
<summary>P2 · Game Review · NVDA</summary>

NVDA 2026.3.1 repeated the position and score in RO. Component checks and Edge now confirm one accessible value.

Confirm speech on the corrected build in RO and EN and a return to the same move from **Why?**. [Web feedback](web-parity.md)

</details>

<details>
<summary>P1 · Italian · unresolved native-search exchange</summary>

The S5 hint remains corrected to `…Qg6`; native Hard retains `…Na5` at H5/100k additional nodes. Its PV ends with `…Nxc4`, before `Nxc6` wins the queen. At H6 the separate exact pair still ranks `…Na5 −154` above `…Qg6 −170 cp`; at their leaves Stockfish H19 finds favorable Black recaptures `…Qxe5` (+306) and `…Nxe5` (+493 cp). Root coverage alone does not resolve the pair.

Do not repeat the uniform two-ply extension: it repairs the Italian example but loses three families by at least 150‰ and passes only 83/87 components against the reference's 87/87. Checking only an attacked queen reintroduces `…Bxf2+`. The next probe must handle the unresolved legal exchange without relabeling a shallower search or copying hint selection into game play. Retain `Be2` versus `Nb5`, `Nxc6` versus `Nxc7+`, families 019/021/022, sound sacrifices and Sicilian/Caro–Kann controls. After independent transfer within the 200k additional-node maximum, measure a color-reversed candidate–live duel.

The live engine is unchanged. Applicable local Elo remains 1,507 [1,357–1,656] from 100 games; no new candidate ΔElo exists for the rejected probes. The repaired native benchmark retains every accuracy floor and adds a position-level audit. [Evidence and results](../board/ai-quality.md#native-frontier-study-and-quest-audit) · [Audit contracts](../testing/hard-audit.md#position-only-puzzle-audit)

</details>

<details>
<summary>P1 · Hint · independent alternative accuracy</summary>

The primary Sicilian `…Nd7` alternative and Caro–Kann `…d6` leader are corrected. The remaining transfer gap is visible in the Sicilian neighbor with `Nc3`, where the existing `…Nd7` leader gives 44.5‰ versus 479.5‰ for `…cxd4` at H19. In the Caro–Kann `Nc3` neighbor, `…d6` still gives 400‰ versus 482‰ for `…d5`. The critical pre-`Nxc2` leader remains `…Nf6`, while its offered `…b5` is weaker. Catalan `cxd5` remains inferior to Stockfish's `Nf3`. [Completed comparisons](../board/ai-quality.md#selective-hint-comparison-and-move-by-move-audit)

In v1 Sicilian S7, the leader `N1c3` has −1 cp / 499.5‰ at H19, while the offered `Bg5` has −166 cp / 24‰ after free `…Qa5+`. Relative to the free-root optimum, their losses are 9‰ and 484.5‰; the gap between those forced roots is 475.5‰. The regression guard protects the leader only and does not certify exclusion of the bad alternative.

Improve the shared ranking within the total 200,000-node hint ceiling. Compare all offered moves at a common completed horizon and check free opposing replies at H17/H19 on new families. Keep the sound queen and knight sacrifices, exact-cache bound negatives, and bulb/comparison/replay identity. A replay-integrity PASS does not establish that the alternatives are equally strong. Preserve completed-depth and actual-node evidence. [AI checks](../testing/README.md#game-and-history)

</details>

<details>
<summary>P2 · Hint audit · broader transfer</summary>

The versioned collector preserves source identity, full history, actual search depths, offered and compared candidates, unavailable ranks, and bounded budgets. The mocked contracts cover substitution, rehashed scores, resumption and interrupted output. Actual paths are diagnostic hint self-play, with the normal Hard theory move recorded separately.

Add independent opening and endgame families from archived PGNs. Freeze their histories before tuning, verify the first loss with free Stockfish H17/H19 replies, and retain all failures. Compare budgets only at identical checkpoints. Preserve the source-bound Extent, PGN and trusted JSONL manifest and keep leader loss separate from intentionally selected ranks 2 and 3. [Audit protocol](../testing/hint-audit.md)

</details>

<details>
<summary>P2 · Puzzles · TalkBack focus in options</summary>

Sanity verifies opening the menu, its controls, and progress retention in all six chapters.

With TalkBack active, verify focus on **Flip board**, a swipe to **Main menu**, and a return to the header button after **Back**. Record focus events in RO and EN on API 28 and on a gesture navigation profile. [TalkBack check](../testing/README.md#talkback-probe)

</details>

<details>
<summary>P2 · Android · predictive Back gesture</summary>

Lint validates API protection, and APK 0.13.0 preserves the Hard game on Back on API 28.

Verify predictive Back in the game and Openings on API 33+, including gesture cancellation. [Android sanity](../testing/README.md#android-sanity)

</details>

<details>
<summary>P1 · AI profile calibration</summary>

Basic was estimated at 1067 Elo (638–1225) after 144 games against Stockfish 19. In the same protocol, Advanced at 10,000 nodes was estimated at 1421 (1245–1553), and Hard at 44,000 at 1534 (1396–1672).

Add new pairs with both colors to narrow uncertainty around Basic's 1200 limit and the Advanced range. Recalibrate the app values with pinned sources and evidence covered by governance. [Basic calibration](../board/ai-quality.md#calibrating-basic-with-selective-depth)

</details>

<details>
<summary>P1 · Hard search · generalization</summary>

Selective recapture improves Tactics, but mean loss on independent openings rises from 53.0‰ to 68.0‰. The bounds cache prototype misses a mate-in-three puzzle, and the head-to-head interval includes zero.

In the Hard benchmark, include the Scandinavian after `4.d4` and the Classical Sicilian after `6.Bg5`. Require improvement on an independent set and in matches with reversed colors under the declared fixed or adaptive profile. Preserve mate-in-three correctness and record the actual nodes consumed. [Recapture](../board/ai-quality.md#hard-recapture-at-the-horizon) · [Experimental cache](../board/ai-quality.md#local-alpha-beta-bounds-prototype--not-integrated)

</details>

<details>
<summary>P1 · Hard · conversion after a pawn capture</summary>

Hard now finds `Nxe5` in the Sicilian with `…h6` and neighboring positions at 44,000 nodes. In the continuation with `…Nc6 Nxc6`, pushing `e5` loses 211‰ relative to `d3` at depth 17. In the final thematic set, B finished with 3 wins and A with 5.

Check the choice of `d3` on new positions and after different opposing replies. Track whether the advantage holds in paired games at the same budget. Keep the capture tests and the defended-pawn controls. [Evidence](../board/ai-quality.md#checking-exchanges-after-a-pawn-capture)

</details>

<details>
<summary>P1 · Transfer from tactics to games</summary>

Given a declared objective, the solver completes 1,220/1,220 public puzzles and 24/24 independent puzzles. With the objective unknown, Hard still misses six mates in three and key material-winning moves. Stopping at a proven mate reduced average mate-in-three cost by 33%.

Measure which tactics can be detected from the position and retain 100% for the solver. For the opponent, require improvement on the independent set at the same budgets and no regressions in paired games. [Evidence and head-to-head play](../board/ai-quality.md#solving-puzzles-with-a-declared-objective)

</details>

<details>
<summary>P2 · Progress · Android upgrade</summary>

Release 0.12.0 demonstrates process restart for Tactical Training, and a component check shows that extending the catalog preserves the cursor and score. An upgrade check with signed APKs and existing saved data is still needed across two releases with the same identity.

Install the published release on a clean emulator, create progress, install the compatible candidate over it, and check the ID, solutions, and points after restart without duplicate credits. [Upgrade check](../testing/README.md#upgrading-saved-progress)

</details>

<details>
<summary>P2 · AI quality · material evaluation</summary>

After `2…dxc4`, J7 displays `−0.60` while Stockfish 19 at depth 13 gives `+0.21`. After `3.e3`, the discrepancy reaches 111 cp.

Track recovery of the pawn for both colors and with control branches. Reduce the discrepancy within the evaluation bar's budget and retain the existing gates. [Measurements](../board/ai-quality.md#calibrating-hard-and-the-evaluation-bar)

</details>

<details>
<summary>P2 · Puzzle calibration</summary>

In Mate in 1, median imported ratings rise over the five segments, with some overlap.

Measure time and incorrect attempts before the hint across shuffled positions, tracking positions that start in check separately. Validate YAML weights on a separate set and preserve IDs. [Calibration](../board/quest-curation.md#difficulty-calibration)

</details>

<details>
<summary>P2 · Android · separating screen transitions</summary>

`ChessActivity` owns menus, the cursor, saving, and reply scheduling. A change to game resumption touches the class that also orchestrates exercises and navigation.

Start by extracting resumption, cancellation, and delayed AI results into a JVM-testable owner. Keep component coverage for restoration and rejection of stale replies. On Android, verify one resumption and the visible ordering. [Game and history](../testing/README.md#game-and-history)

</details>

<details>
<summary>P1 · Hard adaptive strength</summary>

Foreground search uses a configurable 100,000-node allowance with a reserve to 200,000. Continuation retains the active stack and cache. Component checks protect the existing exchange proof.

The historical v1 comparison retains native `Bg5` at Sicilian S7 (24‰) against hint `N1c3` (499.5‰). The Italian hint has since been corrected to `…Qg6`; the old `…Bxf2+` recommendation no longer describes live source. Continue comparing the APIs independently rather than automatically copying hint selection into Hard.

Measure fixed 100,000, fixed 200,000, and the adaptive mode from the same source on held-out games and Stockfish-verified loss positions. Keep the learned-order league's source identity separate and check legal quiet alternatives, mate, and drawn endgames. [Adaptive contract](../board/ai-quality.md#adaptive-foreground-budget)

</details>

<details>
<summary>P1 · Hard learning from PGNs</summary>

The 58-loss ordering model changes 34/36 held-out root lists and keeps all final moves. Corrected chronology identifies one earlier loss in 36 games.

Include preserved good moves, quiet replies, and verified sacrifices in the next training set. Require alternative-rank improvement and a change in complete decisions on excluded families before interpreting the paired Elo. [Offline research](hard-learning.md)

</details>
