# Feedback · Chess Lab

## Overview

The register tracks open gaps and closure criteria. The [board guide](../board/README.md)
describes the behavior, and the [release catalog](../../release/README.md) points to the published evidence.

[Reviewing test moves between suites](../testing/README.md#test-changes-against-a-git-baseline) describes the Git baseline and coverage evidence.

| Priority | Area | Observation | Closure Criterion |
|---|---|---|---|
| P1 | Release · governance identity | XML identity is checked by name and verdict. | Reject a PASS receipt from another commit or run. |
| P2 | Release · compact check archive | The compact proof has a separate archive. | Reject missing phases, foreign runs and replaced PNGs. |
| P1 | Game Review · move classification | Nine grade disagreements persist at both oracle depths, including a missed large loss and a false blunder. | Reproduce and correct classification without changing played history, inventing Elo or substituting hint scores for review evidence. |
| P2 | Game Review · NVDA | RO screen speech repeated position and score. | Confirm one announcement in RO and EN and focus return. |
| P1 | Italian · unresolved native exchange | Targeted recapture repairs the isolated H6 pair, but complete play and controls regress. | Resolve the complete decision within 200k, preserving 019/021/022 and independent transfer before a duel. |
| P1 | Hint · leader, alternatives and opposing replies | The corrected replay retains large losses in 11/60 first hints, 14/83 offered variants and 10/83 first opposing replies. | Repair complete public suggestions and their scored replies at common horizons, preserving correct native choices and independent families. |
| P2 | Hint audit · broader transfer | Paired native/hint collection and raw H17/H19 replay now cover three capped games. | Freeze new independent histories and verify whether fixes transfer beyond these inspected paths. |
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
<summary>P1 · Game Review · move classification</summary>

The actual `GameReview.analyzeLine` preserves all 60 moves and emits no rating for the three capped studies. Nevertheless, `6.e6` in Caro–Kann is BEST and White accuracy is 100 while the played root loses 533‰ at H17 and 581.5‰ at H19. Sicilian `5.Nc3` is BLUNDER with 0‰ loss at both horizons. Across the three games, three positive labels accompany a large oracle loss and six severe labels accompany less than 10‰ loss at both depths.

Freeze these full-history witnesses before fixing the shared comparison. Check native played and recommended roots at a common completed horizon, retain legal replay/bookmarks, and measure new families. Local logistic loss and Stockfish WDL are distinct models; do not make production depend on Stockfish or copy its label mechanically. The current audit grades the played root and checks saved review PV legality; it does not independently certify every review alternative. [Review contract and limitation](../board/game-review.md#measured-review-limitations) · [Paired audit](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Game Review · NVDA</summary>

NVDA 2026.3.1 repeated the position and score in RO. Component checks and Edge now confirm one accessible value.

Confirm speech on the corrected build in RO and EN and a return to the same move from **Why?**. [Web feedback](web-parity.md)

</details>

<details>
<summary>P1 · Italian · unresolved native-search exchange</summary>

The targeted last-capture recapture prototype changes the isolated H6 ordering from `…Na5 −154 / …Qg6 −170 cp` to `−80 / −50 cp`. At the leaves it finds `…Qxe5` and `…Nxe5`; both colors and cold, warm, disabled and narrow-window cache probes preserve the local result. Each isolated root has its own allowance; these probes do not certify a complete decision under one combined budget.

The complete candidate instead chooses `…Bxf2+`, evaluated at −311 cp and 0‰ by Stockfish H19. It also loses 478.5‰, 191.5‰ and 516‰ in controls 019/021/022. Sicilian `…cxd4` and Caro–Kann `…d5` remain unchanged. Reject this prototype and the earlier uniform and queen-only extensions. Preserve the last-capture context in cache identity; do not copy the hint into native play or loosen the stability veto.

Next isolate why closing the exchange changes the complete leader and the three controls. Require full-history, same-horizon free opposing replies and a complete native decision within 200k additional nodes before untouched transfer and a paired duel. The corrected S5 public hint remains `…Qg6`; native Hard remains `…Na5`. The live engine is unchanged, so there is no new candidate ΔElo. [Measured study](../board/ai-quality.md#paired-nativehint-study-and-recapture-rejection) · [Audit contract](../testing/hard-hint-study.md)

</details>

<details>
<summary>P1 · Hint · leader, alternatives and opposing replies</summary>

The Caro–Kann hint after `6.e6` is corrected to `…dxc3` within the unchanged total 200k. The preceding `Nce2`, the Italian and Sicilian outputs, and native play are preserved. Completed behavior and the rejected broad probe belong to the [AI guide](../board/ai-quality.md#carokann-hint-and-forcing-pawn-recapture-verification).

Remaining leader gaps include Italian `Na4` instead of native `Nd5` (467‰ H19 loss) and Sicilian `…Nc6` instead of the much better offered `…Nbd7` (358‰). The earlier Sicilian `N1c3` leader still offers inferior `Bg5`. In the independent forcing set, the improved `Nxd7` leader still offers `Bxe4`, which loses 243.5‰ at H19. Do not equate the comparison's narrow native score gap with oracle-equivalent moves.

The replayed absolute quality benchmark still fails: 11 first hints, 14 offered moves and 10 first opposing PV replies breach 150‰. It audits the first opposing reply and PV endpoints, not every future defense. Next isolate the Italian and Sicilian leader/comparison discrepancy at common completed horizons and correct deficient secondary offers. Retain the Caro pair, the new PGN controls, sound sacrifices and exact-cache negatives. Require unchanged ceilings, full histories and raw H17/H19 verification on newly frozen histories before promotion. Native play and Game Review need separate evidence. [Audit protocol and publication](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Hint audit · broader transfer</summary>

Paired capture and raw Stockfish replay are implemented and covered by negative components. The corrected Caro case and two improved first hints transfer to the sixteen newly frozen forcing-pawn histories without a new leader loss there. Sixteen earlier histories exposed the rejected general scope and are now development controls. These are game checkpoints with shared historical sources, not a large independent strength sample.

Expand to untouched opening and endgame families, especially wrong secondary offers and misleading review grades. Freeze inputs before tuning, retain failures, and distinguish native actual turns from shadow analysis. Reuse trusted receipts only with their original source identity and exact query; source substitution must fail. Limited replays do not produce Elo. [Paired audit](../testing/hard-hint-study.md)

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
