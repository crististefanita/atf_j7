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
| P1 | Hint · independent alternative accuracy | Some alternatives remain overrated after exchange verification. | Verify ranking and every offered alternative against free replies at the same budget. |
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
<summary>P1 · Hint · independent alternative accuracy</summary>

The `…Nxc2+ Qxc2` defect and post-ranking response insertion are closed by the shared exchange frontier and read-only replay. The public Sicilian leader is now `…cxd4`, at 477‰ against Stockfish's free-root 476‰ at H19. The remaining issue is the strength and order of offered alternatives. `…Nd7` still scores 268‰, and `…b5` in the pre-`Nxc2` control scores 329‰ against a free-root 493‰. In the Catalan, `cxd5` gives 485‰ while `Nf3` gives 516.5‰. In the Caro–Kann, the new `…d6` gives 383.5‰ against 215.5‰ for the old `…Nf6`, while the free-root `…d5` still gives 482.5‰. [Completed exchange contract](../board/ai-quality.md#hint-exchange-verification)

Improve the shared ranking within the total 200,000-node hint ceiling. Compare all offered moves at a common completed horizon and check free opposing replies at H17/H19 on new families. Keep the sound queen and knight sacrifices, exact-cache bound negatives, and bulb/comparison/replay identity. A replay-integrity PASS does not establish that the alternatives are equally strong. Preserve completed-depth and actual-node evidence. [AI checks](../testing/README.md#game-and-history)

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

Measure fixed 100,000, fixed 200,000, and the adaptive mode from the same source on held-out games and Stockfish-verified loss positions. Keep the learned-order league's source identity separate and check legal quiet alternatives, mate, and drawn endgames. [Adaptive contract](../board/ai-quality.md#adaptive-foreground-budget)

</details>

<details>
<summary>P1 · Hard learning from PGNs</summary>

The 58-loss ordering model changes 34/36 held-out root lists and keeps all final moves. Corrected chronology identifies one earlier loss in 36 games.

Include preserved good moves, quiet replies, and verified sacrifices in the next training set. Require alternative-rank improvement and a change in complete decisions on excluded families before interpreting the paired Elo. [Offline research](hard-learning.md)

</details>
