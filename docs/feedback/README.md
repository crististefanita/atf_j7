# Feedback · Chess Lab

## Overview

The register tracks open gaps and closure criteria. The [board guide](../board/README.md)
describes the behavior, and the [release catalog](../../release/README.md) points to the published evidence.

[Reviewing test moves between suites](../testing/README.md#test-changes-against-a-git-baseline) describes the Git baseline and coverage evidence.

| Priority | Area | Observation | Closure Criterion |
|---|---|---|---|
| P1 | Hint · whole opposing continuation | The reported valuable-piece-for-pawn tail has no supplied position; legal PVs alone do not exclude a weak opposing reply. | Mine newly admitted games, grade every offered PV ply at H17/H19, retain a concrete witness and preserve sound sacrifices before changing search. |
| P1 | Learning · per-move admission | Historical games without pre-move Hard and maximum public hint do not meet the current learning contract. | Keep only full White/Black journals with exact source, history, allowance and independent replay; retire noncompliant games. [Admission](../testing/learning-archive.md) |
| P1 | Release · governance identity | XML identity is checked by name and verdict. | Reject a PASS receipt from another commit or run. |
| P2 | Release · compact check archive | The compact proof has a separate archive. | Reject missing phases, foreign runs and replaced PNGs. |
| P2 | Documentation · translation safeguards | The paired gate checks structure and links while prose and examples still need semantic review. | Detect copied prose and changed examples with focused negatives while preserving technical literals and immutable evidence. |
| P1 | Game Review · move classification | Nine grade disagreements persist at both oracle depths, including a missed large loss and a false blunder. | Reproduce and correct classification without changing played history, inventing Elo or substituting hint scores for review evidence. |
| P2 | Game Review · NVDA | RO screen speech repeated position and score. | Confirm one announcement in RO and EN and focus return. |
| P1 | Italian · unresolved native exchange | Targeted recapture repairs the isolated H6 pair, but complete play and controls regress. | Resolve the complete decision within 200k, preserving 019/021/022 and independent transfer before a duel. |
| P1 | Hint · leader, alternatives and opposing replies | The corrected replay retains large losses in 8/60 first hints, 11/82 offered variants and 9/82 first opposing replies. | Repair complete public suggestions and their scored replies at common horizons, preserving correct native choices and independent families. |
| P2 | Hint audit · broader transfer | Paired native/hint collection and raw H17/H19 replay now cover three replays and two fresh thematic branches. | Freeze new independent histories and verify whether fixes transfer beyond these inspected paths. |
| P2 | Puzzles · TalkBack focus in options | Puzzle option focus still needs speech evidence. | Capture the menu focus path in both languages. |
| P2 | Android · predictive Back gesture | Predictive Back still needs a compatible device. | Exercise cancellation and resumption on API 33+. |
| P1 | AI profile calibration | The Elo intervals still overlap. | Add paired games to narrow the rating uncertainty. |
| P1 | Hard adaptive strength | Foreground search uses 100,000 nodes and a reserve to 200,000. | Compare the three modes on held-out games and verify the opposing reply. |
| P1 | Hard learning from PGNs | The loss-trained ordering model keeps all final moves in 36 new families. | Add preserved moves and sacrifices, then verify ranking and complete decisions. |
| P1 | Hard search · generalization | Selective recapture regresses on independent openings. | Require independent gains at the fixed budget. |
| P1 | Hard · conversion after a pawn capture | The pawn capture can lose its advantage later. | Verify conversion on new positions and distinct replies. |
| P1 | Transfer from tactics to games | Hard misses tactics without a declared objective. | Measure discovery and preserve the solver contract. |
| P1 | Native Hard · remaining material goals | 79 canonical stages remain unaccepted in 53 exercises; `training-051` still draws with `Ke6`. | Resolve `Rc8+` versus `Ke6` with free opposing replies, preserving the qualified pawn conversion, defended draws and full catalog. |
| P2 | Native studies · original FEN clock | A board-only new match starts its quiet counter at zero while oracle FEN metadata retains the original clock. | Add explicit native FEN-clock initialization and 0/99/149-clock negatives before claiming draw parity for arbitrary composed origins. |
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

The actual `GameReview.analyzeLine` preserves all 60 moves and emits no rating for the three capped studies. Nevertheless, `6.e6` in Caro–Kann is labelled `BEST` and White accuracy is 100 while the played root loses 533‰ at H17 and 581.5‰ at H19. Sicilian `5.Nc3` is labelled `BLUNDER` with 0‰ loss at both horizons. Across the three games, three positive labels accompany a large oracle loss and six severe labels accompany less than 10‰ loss at both depths.

Freeze these full-history witnesses before fixing the shared comparison. Check native played and recommended roots at a common completed horizon, retain legal replay/bookmarks, and measure new families. Local logistic loss and Stockfish WDL are distinct models; do not make production depend on Stockfish or copy its label mechanically. The current audit grades the played root and checks saved review PV legality; it does not independently certify every review alternative. [Review contract and limitation](../board/game-review.md#measured-review-limitations) · [Paired audit](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Game Review · NVDA</summary>

NVDA 2026.3.1 repeated the position and score in RO. Component checks verify one accessible value.

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

The Italian and Caro–Kann first-hint regressions remain closed. The Sicilian `…Nc6/…Nbd7` leader defect after `7.f4` is also corrected at completed H5 and 200k; the raw −21/−21 tie is retained. Their mechanism and rejected broad scopes belong to the [AI guide](../board/ai-quality.md#italian-queen-recapture-and-hint-continuity), with permanent component checks. Other queen-pressure positions remain deficient: `validation-039`, a distinct full-history position after `…Qb6`, retains its previous 462‰ first-hint loss.

The matched sixty-phase replay still fails absolute quality: 8 first hints, 11 offered moves and 9 first opposing PV replies breach 150‰. The corrected Sicilian hint offers only `…Nbd7` and loses 33‰ at H19, versus 358‰ before; the free oracle prefers `…Qb6` and native theory still supplies sound `…Be7`. The model still cannot distinguish the two knight roots numerically. Candidate coverage is now implemented and governed: all four roots are compared at H5/200k. Improve their numerical distinction and opposing replies without promoting an exchange stopped before recapture. [Four-root measurements](../board/ai-quality.md#sicilian-four-root-coverage) Earlier `N1c3` still offers inferior `Bg5`. [Mechanism and rejected prototypes](../board/ai-quality.md#sicilian-exact-ties-and-pin-reinforcement) The forcing-pawn transfer case still offers `Bxe4` at 243.5‰. The new validation history `validation-057` improves its leader but retains inferior `Bg5` as a secondary offer at 339.5‰. Prioritize the first common-horizon divergence and the free opposing reply; a narrow native score gap is not evidence of equivalent moves.

Preserve the completed Caro and Italian controls, sound sacrifices, exact-cache negatives, 200k total hint ceiling and unmodified historical moves. Address defective secondary offers and PV replies before claiming complete scholastic explanations. Grade first choices, every offered root and scored replies separately at H17/H19. A hint fix does not certify native Hard play, the bar or Game Review grades. [Audit and publication](../testing/hard-hint-study.md)

</details>

<details>
<summary>P2 · Hint audit · broader transfer</summary>

The latest round preserves all sixty-four frozen geometric histories. It captures 100 fresh native phases, including the two new queen/bishop branches, and validates 308 new raw requests plus 958 original exact-query projections. Free/forced scores must share one oracle identity; query integrity alone does not permit combining two producers. Preserve original receipt identities and exact-query joins; do not relabel evidence or count overlapping prefixes as independent games.

The earlier Italian collection and its rejected queen-pressure scope remain historical evidence in the AI guide. Legal replay and receipt integrity do not imply chess-quality PASS.

In that preceding collection, sixteen inspected queen-pressure histories exposed the rejected broad scope and became development controls. Sixteen subsequently frozen opening histories retain a 322.5‰ leader improvement and a 13.5‰ decrease, with no new 150‰ leader loss. The development set separately retains an 18.5‰ decrease. Do not retune on these observations and call them unseen. Expand to untouched families with wrong secondary offers and misleading review grades; retain failures, source identity and exact histories. Capped overlapping studies do not measure Elo. [Paired audit](../testing/hard-hint-study.md)

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

<details>
<summary>P2 · Documentation · translation safeguards</summary>

The paired gate checks document inventory, localized headings, structural blocks, link destinations and archived evidence. It does not inspect paragraph meaning or the contents of fenced examples. A copied English paragraph under Romanian headings or a changed command inside the same fence can pass its structural checks. The reviewed translations still require the explicit semantic check in the [bilingual process](../release/README.md#bilingual-documentation).

Add focused negative cases for copied prose, disclosure captions, link captions and human-readable diagram labels, plus changed commands or measured values. Preserve identifiers, quoted UI labels, SAN/FEN/UCI, command syntax and immutable execution evidence. An identical-sentence check can flag omissions; it cannot independently certify the language or accuracy of a rewritten sentence. [Current pair controls](../../atf-tests/src/test/java/ro/atf/chess/DocumentationPairGovernanceTest.java)

</details>

<details>
<summary>P1 · Native Hard · remaining material goals and first WDL losses</summary>

Keep 79 unaccepted stages in 53 material exercises open: 56 have a large loss at both
Stockfish horizons, 22 preserve the win and one is smaller/depth-sensitive. The 42 first
educational misses with a confirmed large loss precede conditional later stages. Verify
accepted prefixes before claiming a first chronological loss.

**Next compact control: `training-051`.** Ordinary Hard still chooses `Ke6` instead of
`Rc8+`; six fresh H17/H19 queries confirm a 500‰ win-to-draw loss. Compare the rook move and
free opposing replies at common horizons inside the effective allowance. The new pawn proof
does not apply while the adversary has a rook; do not extend its safety lemma to that scope.
Preserve `training-021/065`, the complete native catalog and mate gates before promotion.

The post-promotion `a4` conversion is closed by a source-bound winning-plan certificate and
actual checkmates in both colors. Its mechanism, 37/31-ply games, failure controls and limits
belong in the [AI guide](../board/ai-quality.md#bounded-pawn-ending-conversion).
The 24 new thematic positions establish root-WDL non-regression, not complete conversion
or an Elo gain. Play further excluded families to actual result, including defended draws,
before broadening the material scope or promoting a strength claim. Preserve the complete
PGNs and first verified losses; do not count mirrored examples as independent rating games.

The original 79 contextual lessons retain their oracle producer after complete native
decision equality. New structural YAML describes pawn tempo, king escort, repetition and
safe promotion with counterexamples; runtime contains no curriculum key or FEN lookup.
[Repeatable certificate and receipt procedure](../testing/hard-puzzle-study.md#auditing-winning-plan-certificates)

</details>


<details>
<summary>P2 · Native studies · original FEN draw counter</summary>

`ChessMatch.fromPosition` accepts a board and starts a new zero-clock match; `StudyFen`
separately preserves FEN clocks for the oracle. It therefore cannot establish native/oracle
fifty-move parity for an arbitrary supplied FEN. The qualified post-promotion case is unaffected:
the last capture resets the counter and `…Ke4` makes it one on both paths.

Own this at the composed-session adapter, not in evaluation weights. Preserve an explicit
initial halfmove counter and test identical boards with clocks 0/99/149, captures/pawn resets,
saved-game restoration and rejection of a receipt borrowed from another clock. Keep original
receipts with their real producer; never patch their headers to manufacture parity.
[Native constructor](../../chess-core/src/main/java/ro/atf/chess/ChessMatch.java)
· [FEN receipt helper](../../atf-tests/src/test/java/ro/atf/chess/StudyFen.java)

</details>
