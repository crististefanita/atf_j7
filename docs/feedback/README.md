# Feedback · Chess Lab

## Overview

The register tracks open gaps and closure criteria. The [board guide](../board/README.md)
describes the behavior, and the [release catalog](../../release/README.md) points to the published evidence.

[Reviewing test moves between suites](../testing/README.md#test-changes-against-a-git-baseline) describes the Git baseline and coverage evidence.

- [Native strength and hint/review comparisons](#finished-game-native-losses-and-frontier-controls)
- [Learned-ordering transfer still needed](hard-learning.md)
- [APK/Web parity and rendered evidence](web-parity.md)

<details>
<summary>Priority register and closure criteria</summary>

| Priority | Area | Observation | Closure Criterion |
|---|---|---|---|
| P1 | King's Indian · shared Hard/hint loss | In the new 79-ply source-bound game, 8…Bd7 is the first large actual Hard loss and the first hint agrees with it: 231/242‰ versus free e6 at H17/H19. | Compare Bd7/e6 and free replies at the same horizon before changing search or evaluation. Preserve the Nimzo correction, French/Ruy and sound sacrifices; require counterexamples and excluded families. [Witness](#kings-indian-audit-after-the-nimzo-correction) |
| P1 | Nimzo · free quiet replies | The opening-root Be7 defect is qualified, but the original later PV ends with Bxc5 and assumes a weak opposing continuation; free Ne5 loses 504‰ for White, while Qxc5 is a trap. | Compare Rf1/Bxc5 and free Ne5/Qxc5 at the same horizon, preserve the independently sound recapture trap and French/Ruy controls, then audit the whole changed line. [Witness](#nimzo-public-hint-witness) |
| P2 | Web · proof portability | The current TeaVM build reaches JRE security providers through `PawnPushProof.hash`; compilation fails on `Class.getModule` / `ClassLoader.loadClass`. The proof class is unchanged from live baseline `b51e17e`. | Supply a portable identical-digest implementation or supported build substitution, with known-vector negatives and JVM/Web parity; do not alter proof semantics or label Java-only compilation a runnable Web PASS. |
| P1 | Scotch · winning check and opposing mate | At move 20, the hint compares `…dxc3` and `…gxf6`, omits `…Re8+`, and loses 314/348.5‰. Later in its PV, `Bxb2` replaces White's forced mate in three by `Bxh7+`. | Readmit the winning root and search free opposing replies, including quiet follow-up checks; preserve the valid bishop sacrifice, existing root guards and negative work limits. The hint-root, review-root and continuation goals remain FAIL. [Witnesses](#scotch-winning-check-and-opposing-mate) |
| P1 | Hint comparison · whole diagnostic PV | The repaired Ruy public line has no large loss, but the rejected `Ng5 …O-O Nc3` comparison still contains a 346/378.5‰ weak move. | Audit comparison continuations as well as offered hints; repair the full free-reply line and keep the explicit comparison benchmark FAIL until both horizons qualify it. [Mechanism](../board/ai-quality.md#quiet-opposing-offers-inside-a-hint) |
| P1 | Hint · whole opposing continuation | A new second hint after `1.e4` contains weak `Bxe6`; free opposing play prefers `Nc3`, with 382/399‰ loss at H17/H19. | Repair the complete scored continuation with 200k ordinary work and its separately charged finite proof reserve, check every offered PV ply on independent families and preserve sound sacrifices. [Witness](#weak-opposing-replies-inside-public-hints) |
| P1 | Release · governance identity | XML identity is checked by name and verdict. | Reject a PASS receipt from another commit or run. |
| P2 | Release · compact check archive | Declared compact evidence is checked; geometry changes do not yet require it at promotion. | Reject omitted required evidence while preserving phase, run and capture substitution controls. |
| P2 | Documentation · translation safeguards | The paired gate checks structure and links while prose and examples still need semantic review. | Detect copied prose and changed examples with focused negatives while preserving technical literals and immutable evidence. |
| P1 | Game Review · move classification | The preceding 106-ply corpus has 11 native error labels on losses below 50‰ at both oracle depths and one missed large loss. | Isolate score-model and horizon effects without changing played history, inventing Elo or substituting hint scores for review evidence. |
| P1 | Game Review · recommended lines | Ten of the preceding 106 review recommendations lose at least 150‰ at both H17/H19; both review branches are fully audited. | Repair complete same-horizon root/reply comparisons and preserve actual good moves and cached history. [Measured cases](#finished-game-native-losses-and-frontier-controls) |
| P2 | Game Review · NVDA | RO screen speech repeated position and score. | Confirm one announcement in RO and EN and focus return. |
| P1 | Italian · unresolved native exchange | Targeted recapture repairs the isolated H6 pair, but complete play and controls regress. | Resolve the complete decision within 200k, preserving 019/021/022 and independent transfer before a duel. |
| P1 | Hint · leader, alternatives and opposing replies | The preceding source-bound games retain large losses in 6/106 first hints, 11/143 offers and 14/140 first opposing replies. | Repair complete scored continuations at common horizons; all 759 hypothetical plies and legal important-piece recaptures remain independently graded. |
| P2 | Hint audit · broader transfer | Two more games reach checkmate with 106 pre-move native/hint pairs including the sound-sacrifice control. | Add excluded opening ancestry and actual candidate games; replaying the inspected paths is not new-family or Elo proof. |
| P2 | Puzzles · TalkBack focus in options | Puzzle option focus still needs speech evidence. | Capture the menu focus path in both languages. |
| P2 | Android · predictive Back gesture | Predictive Back still needs a compatible device. | Exercise cancellation and resumption on API 33+. |
| P1 | Native Hard · finished-game losses | Scandinavian `g4` is protected by the new `Ne4` regression; French `Nc4` is now protected by complete H6 recapture evidence; English `…Qf6` and Queen's Gambit `…Qa5` remain. | Compare complete free replies at a common horizon; preserve the catalog, sound sacrifices and Basic/Advanced before promotion. [Next work](#finished-game-native-losses-and-frontier-controls) |
| P2 | Tactical frontier · check at the limit | A checked frontier may still stop without a legal evasion; the broad extension fails 13 existing contracts. | Resolve the precise cause within one charged allowance; keep the explicit benchmark failures until quality and transfer pass. |
| P1 | Coaching · hidden recapture proof | Engine-side forcing verification and its work receipt are implemented; the visible preview still shows the complete scored line. | Project a shorter visible prefix while retaining the complete scored proof, shared recommendation and restart identity. Do not hide decisive replies. [Contract](#tactical-proof-and-displayed-lines) |
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

</details>

## Weak opposing replies inside public hints

The new maximum-hint journal `learning-max-hint-20261118`, producer
`8d564655b554d817fea4e3a04723a538ae5f3864`, records every actual White/Black move
before play. After `1.e4`, offer two includes
`…Nf6 Qf3 …Nc6 Bc4 …e6 Bxe6 …dxe6 Qxf6`. At White's `Bxe6` phase, independent
free searches prefer `Nc3`: expected WDL is 382‰/399‰ at H17/H19 versus 0‰/0‰
with `Bxe6` forced. The full hypothetical prefix, raw UCI and both horizons remain
in `C:\work\sah\.studii\corpus\2026-11-18-v1\deep-oracle`.
The repeated opening position in three traces is one context, not three families.

This confirms a weak opposing continuation, not necessarily the user's exact
unprovided position. The public hint was actually requested at 200k. Integrity
still passes; absolute hint quality fails. Do not close this item by appending an
unscored recapture, hiding the secondary offer or banning sacrifices. Compare
complete roots and free opposing replies at common native horizons within the
same total budget. Preserve `…Qg3 hxg3 …Rh1#` and the Italian/Caro/Sicilian
controls. Mark an unproved horizon honestly; never label a legal PV optimal merely
because its material ledger matches.

Use the [admission contract](../testing/learning-archive.md) for future games.
Historical game bodies without current admission are retired. Source fixtures,
release evidence, unresolved problem definitions and original oracle references
are separate from the active learning corpus.

## Full-review calibration on the admitted corpus

All five games now retain `review.json`, readable `analysis.json` and a per-game
README. The added review has its own producer `87ccb91bb169ed95ed7c0dc4b3e58ab67ce7e6df`;
the original Hard/hint journals stay byte-identical. There are 76 reviewed moves,
152 played/recommended branches and 745 hypothetical review plies. The teacher
adds 854 cold H17/H19 queries and references 1,090 identical-context original
queries without relabeling their runs. The native review keeps its actual 85k
configured allowance, separate from the original public hint's 200k.

Nine recommended roots have stable large oracle loss. In Caro–Kann, the review
prefers `…Qa5` over the actually correct `…d5` at move four, losing 299/318‰.
After `Nc3`, it recommends `…e5` instead of the played `…dxe4`, losing
472/448.5‰. In the pawn conversion, the review's alternative loses 500/500‰
while actual `a4` preserves the win. These are whole recorded review results,
not manually selected root scores. Keep this P1 open until the actual review
repairs these recommendations and transfers independently. Do not mark every
review move optimal merely because the original Hard choice was good.

The 118 public hint endpoints contain nine legally exposed important pieces,
but only two combine exposure with stable large last-move loss. In Caro–Kann,
`…Qxd4` takes a pawn, allows `Qxd4`, and loses 518.5/513‰; after the recapture,
the last mover has 0‰ expected score at both horizons. `…Nh6` allows `Bxh6`
and loses 308.5/290‰. Preserve exchanges that do not lose WDL and the sound
`…Qxg3+ hxg3 …Rh1#` control, which retains 1,000‰ after the pawn's capture.
The complete tails and all legal non-pawn recaptures are retained alongside the
review in `C:\work\sah\.studii\corpus\2026-11-18-v1\postgame-audit`.

Integrity, exposed-tail quality and review-recommendation quality are separate
Extent verdicts. The first passes; the latter two fail. Local review ratings are
not measured Elo, and three capped paths have no rating. [Contract and components](../testing/learning-archive.md#post-game-review-and-hint-endpoints)

## Complete-game findings retained as component targets

The two full games (Queen's Gambit and French) and the mating control belong to
producer `242d6fe21d6fbe625f628cb3f677f9618f588674`: 130 pre-move Hard/maximum-hint
pairs and complete native reviews. Their 1,001 hypothetical hint plies have 70
stable large losses (65 distinct forced queries), including 31 opposing replies.
Six offered roots and five recorded native choices lose at least 150‰ at both
horizons. These are diagnostic positions, not a rated league or learned rules.

| Next native target | Current failure | Component closure |
|---|---|---|
| `queens-gambit-21` · `11…Qa5` | 225.5/219.5‰ loss; Stockfish prefers `…a5`; Hard completes H5 using 100k additional nodes. | Compare `…Qa5 Bd3` and `…a5` with free replies at a common native horizon; pass the permanent witness and independent families. |
| `french-20` · `11.Bb5+` | 475.5/453‰ loss; `…c6` refutes the line; Stockfish prefers `Nc4`; Hard completes H5 using 200k additional nodes. | Prioritize this larger loss, isolate `…c6` and the following exchange, then preserve the queen-chain controls and full catalog. |

The IDs use the original zero-based pre-move ply. The small source fixture and
`HardLearningWitnessTest` preserve legal histories, all eight raw oracle phases
and substitution negatives without a Stockfish installation. The two
`HardLearningWitnessBenchmarkTest` goals recalculate the current native choice;
they remain FAIL until repaired, and an unseen replacement requires fresh oracle
support. Never close them by changing an expected bad move or runtime FEN lookup.
The generic native, hint, exposed-endpoint and review quality verdicts stay separate
from integrity and from the normal release regression suite.

The full review additionally measures six bad recommended roots, twelve unsound
exposed public tails and sixteen native error labels with small oracle loss.
Those are results of the original producer, not claims that the later hint fixes
have been replayed historically. Keep those source-bound observations unchanged;
current-source hint comparisons are explicit replays with original teacher
identities, never newly invented historical pre-move journals.
[Reusable admission and reproduction](../testing/learning-archive.md)

## Evidence and Closure Checks

<details>
<summary>P1 · Release · governance identity</summary>

Preflight ties the test-change receipt to Git and checks the Documentation, Release, and Extent XML files by name and verdict.

Bind each XML file to the candidate commit and qualification run. Replace each XML in turn with a PASS receipt from another commit or run and verify rejection while keeping the current Git receipt. [Preflight](../../release-tools/src/main/java/ro/atf/chess/release/ReleaseGate.java) · [Tests](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)

</details>

<details>
<summary>P2 · Release · compact check archive</summary>

The archive verifier already checks declared compact phases, run identity and capture fingerprints. The remaining gap is selecting mandatory evidence from the change: `ReleaseGate qualification` requires components and Game Review, and does not itself require the compact receipt for a geometry change.

Reject omitted required compact evidence before promotion, while preserving the existing missing-phase, foreign-run and substituted-capture checks. Keep historical receipts unchanged. [Promotion procedure](../release/README.md#qualification-and-publication) · [Archive verifier](../../atf-tests/src/test/java/ro/atf/chess/ArchivedReleaseEvidence.java)

</details>

<details>
<summary>P1 · Game Review · move classification</summary>

The actual `GameReview.analyzeLine` preserves all 60 moves and emits no rating for the three capped studies. Nevertheless, `6.e6` in Caro–Kann is labelled `BEST` and White accuracy is 100 while the played root loses 533‰ at H17 and 581.5‰ at H19. Sicilian `5.Nc3` is labelled `BLUNDER` with 0‰ loss at both horizons. Across the three games, three positive labels accompany a large oracle loss and six severe labels accompany less than 10‰ loss at both depths.

Freeze these full-history witnesses before fixing the shared comparison. Check native played and recommended roots at a common completed horizon, retain legal replay/bookmarks, and measure new families. Local logistic loss and Stockfish WDL are distinct models; do not make production depend on Stockfish or copy its label mechanically. The current audit grades the played root and checks saved review PV legality; it does not independently certify every review alternative. [Review contract and limitation](../board/game-review.md#measured-review-limitations) · [Paired audit](../testing/hard-hint-study.md)

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

After `2…dxc4`, Chess displays `−0.60` while Stockfish 19 at depth 13 gives `+0.21`. After `3.e3`, the discrepancy reaches 111 cp.

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

## Finished-game native losses and frontier controls

The current batch uses producer `37c5f65`, not the later Native PVS consumer. English
ends in 51 plies and Scandinavian in 52, both lost by native Hard to full-strength
Stockfish; the sound queen-offer control ends in three. All 106 actual plies retain
both pre-move searches and complete native review. The 4,104 cold oracle queries
cover all 759 public hypothetical plies, every review branch and important-piece
recaptures. Integrity admission does not erase 77 weak PV plies, five unsound exposed
hint tails or ten bad review recommendations.

The Native PVS replay preserves all 2,303 canonical acceptance outcomes and Basic/
Advanced's 212 complete analyses. It fixes Scandinavian `g4` to `Ne4`, with independent
loss 526.5/620 → 25.5/110.5‰. The 236 current/prior frame comparisons have no newly
confirmed large loss; a second gain is a shadow Native choice on a position actually
played by Stockfish. It is not a second improved played game. Two prior French choices
also have small score differences, including a 2.5/4.5‰ deterioration. Mean catalog
work rises by about 8.1%; no speed, depth or Elo gain is claimed. [Method and limits](../board/ai-quality.md#native-secondary-move-verification)

The French native comparison is closed by the [completed recapture contract](../board/ai-quality.md#closing-a-pawn-recapture-at-the-completed-horizon). Its separate hint and review
defects are now covered by the free-counterplay components; retain English `…Qf6` versus free `…Nc6` as transfer. Uniform
one-ply closure gains 24 stages but loses 40; repeating it or merely increasing the
expected answer is not an acceptable repair. Keep common-horizon free defenses,
`training-021/051/065`, all mate chapters, scoped queen recaptures and a sound queen
sacrifice. The unresolved checked-frontier tests remain explicitly tagged benchmark.
The [admission contract](../testing/learning-archive.md#finished-game-continuation-studies)
explains original identities and automatic versus claimable draws. Evidence stays in
`C:\work\sah\.studii\experiments\2026-11-19-hard-continuation`.

## Tactical proof and displayed lines

Requested on November 19: keep the final verification reply out of the normal hint
preview when it is only needed to establish the result of an exchange. This requires
two explicit representations: the visible prefix and the complete scored tactical
proof. Keep origin, full history, legal recapture, score before/after, completed
ordinary horizon, proof length, consumed work and termination reason auditable.
Do not append a decorative reply or replace the score after rendering. Missing or
interrupted evidence must stay unknown; it cannot certify a material gain.

A single automatic recapture is insufficient when the adversary has a stronger
quiet defense, several legal recaptures or a winning sacrifice. Search legal
alternatives; preserve pins, en passant, promotion, mate and the sound queen offer.
The French `Nxf5 …exf5` witness is exactly a piece-for-pawn capture followed by a pawn
recapture. Preserve its SAN and both material ledgers; do not misclassify `Nxf5` as a quiet move.

Engine-side proof ownership and negative components are implemented. Project the
visible prefix without mutating the underlying proof or saved history.
The bulb, played destination and final analysis must refer to that same scored
recommendation. Component mocks cover interrupted work, foreign runs and missing
phases; actual chess decisions retain independent Stockfish H17/H19 evidence.
UI separation remains open until those proof and presentation contracts pass.

The engine-side reserve now resolves a pending frontier with charged continuous recaptures
and mandatory evasions, and rejects incomplete hint/review results. See the
[implementation contract](../board/ai-quality.md#dynamic-coaching-frontier). Keep these
remaining items distinct:

The two French defects are repaired by the charged two-horizon free-counterplay comparison:
`Nc4` replaces `Bb5+` in both coaches, with H17/H19 loss below 50‰ and the original
quality goals moved into the regular component suite. [Counterplay components](../../atf-tests/src/test/java/ro/atf/chess/FrenchCoachingTest.java)
retain both colors, all four scored PVs, one-node-short negatives and cache v1/v2/v3 compatibility.

- The preview still shows the full scored proof; a shorter visible prefix and an explicit
  verification-suffix view need their own presentation and restart tests.
- Preserve the equal-turn guard: the first unguarded prototype switched the independently
  qualified `Nxd7` to `Bxe4`, increasing loss from 43.5/8 to 335/243.5‰ at H17/H19.
  The final guard must retain the actually closed incumbent score, charge its confirmation,
  and publish no score if confirmation is interrupted.
- Legacy v1 review caches remain readable but have no new forcing receipt. Distinguish
  historical analysis from a newly qualified evaluator when revisiting old cached results.

The five-million-node YAML ceiling grows in 25k steps. A normal Italian theory exit
requires 1,037,401 reserve nodes; its smaller-budget negative must remain incomplete,
without restoring the seed score. Keep the 25cp leader-change threshold and the prior
even-horizon check auditable. The Sicilian `…Nc6` reversal was independently worse than
`…Nbd7` at both teacher depths; no fabricated score tie is an acceptable fix. The bulb,
destination and explicit comparison now share that qualified proof decision.
Review also retains its incumbent on an actual exact-score tie. The English replay's
`Nf3`/`e4` tie is +21 after closure, not the old +651. Keep its six teacher phases and
component negative: lexical ordering alone produced an independently worse recommendation.
The preceding qualification's 106-position replay activates reserve in 44 hints and 66 reviews, with no
incomplete result. Its 43 changed root recommendations have 252 H17/H19 phases. No new
loss reaches 150‰; the largest increases are 73.5/54‰. These smaller regressions remain
calibration debt. These historical counts do not qualify a later evaluator. A closed tactical frontier
does not certify globally optimal play or replace a fresh complete-game Elo measurement.

Do not infer Elo or broad independent transfer from the composed forcing controls.
Fresh complete games still need every pre-move maximum hint, post-game review and cold
teacher phases under their actual producer, with the reserve receipt retained.

## New-opening audit after the French repair

The corrected evaluator played a complete Ruy López game, with maximum hints recorded
before all 55 actual plies, including theory and oracle moves. Native Hard supplied
22 actual moves; Stockfish supplied 23 and ten came from the declared theory prefix.
This is one observed game, not an Elo match or a replay promoted to new play.

The 77 hint offers contain 398 hypothetical plies, each independently checked at
H17/H19 with complete history: 40 plies lose at least 150‰ at both depths, including
19 opposing replies. Six offered roots and one first hint exceed that floor. The
55 recorded native choices have no stable large loss on this game. These are
separate quality verdicts; passing receipt integrity does not certify the hints.

Prioritize the weak free reply, not a special opening override: after the recorded
`8.Ng5` branch, the hint uses `…Be6`, losing 509.5/511‰ against free play. Earlier in
another preview `…d5` loses 428.5/443‰ where `…Nxe4` is available. Preserve both
complete hypothetical histories, compare free replies at a common horizon and keep
the French, Italian, Caro–Kann and sound-sacrifice components. The executable
`LearningArchiveBenchmarkTest.maximumHintsHaveNoStableLargeLossInEitherSideOfThePreview`
remains FAIL; never change its zero-error assertion to accept the observed count.
The finished-game review and all its branches retain a separate oracle audit.
Evidence: `C:\work\sah\.studii\corpus\2026-11-19-french-coaching-quiet`; producer
`098848be9612e68e370d3495df4dc679a0f7aedd`, original journal and work receipts intact.

The completed review audits both branches of all 55 moves. It retains one inferior
recommendation (move 26, 267.5/289.5‰ loss) and seven native error labels on moves
losing below 50‰ at both depths. All 77 hint endpoints are independently checked:
none exposes an important final piece to a legal recapture, but 14 last moves are
still inferior at both oracle depths. This does not certify the whole PV: the
finished-review quality benchmark remains FAIL for that recommendation. Repair
free-reply comparisons and classification separately; never hide the internal
weak replies just because the final piece is safe.

The Ruy quiet bishop offer is repaired for the offered hint: full capture closure
removes the optimistic `Ng5` preview and offers `Nc3 …O-O d3 …Be6 Be3`. All five
actual offered plies have cold H17/H19 loss below 50‰. The same `…Be6` is sound in
the Italian control, so a move blacklist is not a repair. Preserve the French and
sound-sacrifice components. The weaker compared continuation remains separate
quality debt, with an executable benchmark and raw teacher phases. A new complete
game must retain every pre-move hint and its full final analysis under the actual
producer; do not relabel the previous 55-ply Ruy game as current play.

## Scotch winning check and opposing mate

The narrowed source `e4bdd31af382297360e825bb1a47729e20c5cbbc` passed the 106-position
historical root gate: three changed recommendations, 18 fresh oracle phases, zero
incomplete results and zero new large root regressions. Its 52-ply Scotch game is
new observed play: ten declared theory moves, 21 actual Hard moves and 21 Stockfish
moves, ending in Black checkmate. Every turn has a pre-move maximum hint. All 52
hint objects match the baseline on these new positions: no full-capture admission
was activated. This is preservation, not independent evidence of a strength gain.

The 66 public offers have 377 hypothetical plies and 1,282 independent H17/H19
queries. Twenty-seven plies have stable loss of at least 150‰, including twelve
opposing replies; three first hints lose substantially. Two of the 52 native choices
have large loss; only one was actually played by Hard, the other is a shadow choice
on the oracle's turn. Full final review retains all 52 turns and both branches:
three recommended roots lose substantially, 39 branch plies are weak, three native
error labels have small oracle loss and one large loss lacks an error label.
All 66 endpoints are checked: zero unsound exposed important pieces, but five last
moves remain inferior. No Elo or globally optimal hints are established by this game.

[ScotchWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/ScotchWitnessTest.java)
keeps ten raw phases, full actual/hypothetical history and original source identity.
At the original move, free Stockfish selects `…Re8+`; the verification shortlist
omits it. Later, free White play proves `Bxh7+ Kxh7 Qh5+ Kg8 Qh8#` at both depths.
`Bxb2` misses that mate but still retains winning chances; do not describe it as a
losing sacrifice. The correct bishop-for-pawn sacrifice must survive future changes.
[ScotchContinuationBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/ScotchContinuationBenchmarkTest.java)
keeps separate FAIL goals for hint-root readmission, review-root selection and the complete opposing continuation.
Do not repair only the displayed suffix, ban captures, force one defense or replace
history. Verify the root and all freely chosen replies at their actual horizon.
Evidence is in `C:\work\sah\.studii\corpus\2026-11-19-ruy-qualified-coaching`.

At that same move the native review selects `…dxc3` and calls the actually played
`…Re8+` BLUNDER with 298 native points lost. Forced H17/H19 checks of Re8+ lose only
7.5/8‰. Repairing shortlist coverage alone cannot close this: review already covers
all legal roots but misranks them. Preserve the separate review-root benchmark.

The next study reconfirmed Re8+ with cold H17/H19 searches, including the color
mirror. No runtime correction qualified. A positional prototype selected Re8+ at
H7, yet its free continuation `Re8+ Be3 dxc3 b3 Rxe3+ fxe3 Qxf6` failed:
`…dxc3` lost 450.5/450‰, and `…Rxe3+` lost 536/668.5‰. White's `b3` was disputed
(32.5/200.5‰), not an independently qualified defense. A correct first move did
not establish understanding of the position. The prototype stays outside main.

Use [ScotchPromotionEvidenceTest](../../atf-tests/src/test/java/ro/atf/chess/ScotchPromotionEvidenceTest.java)
and its source-bound full-line witness to prevent a root-only closure. Negative
controls reject edited scores with recomputed digests, substituted depths, missing
or duplicated phases, a consistently relabeled run, shortened inventory and a
freshly pinned incomplete native body. The separate
[actual Game Review goal](../../atf-tests/src/test/java/ro/atf/chess/ScotchReviewClassificationBenchmarkTest.java)
still fails on the BLUNDER classification. These audit components passing must
never be reported as a repaired chess decision.

Next isolate `…dxe3` versus `…dxc3` after **Re8+ Be3**, with White's **O-O** free.
After the rejected **…dxc3 b3** branch, compare **…Qxf6** against **…Rxe3+** and
include **Qf3**, the later legal castling and free checks. Preserve the sound bishop
and queen sacrifices, French and Ruy controls. Do not give a king shelter from a
castling move that has not actually been searched, or buy a correct root through
an overvalued rook sacrifice. Even an isolated H9 comparison at 20M nodes stopped
without a score; more work alone did not qualify this repair. The runtime keeps
its existing budgets and evaluation. Evidence: `C:\work\sah\.studii\experiments\2026-11-19-scotch-re8`.

## Nimzo public hint witness

The original game `nimzo-indian-hard-black`, producer `3a1b0dbc01da1617d5eca29983f8f86056a7f8e0`,
records 63 actual plies from the initial board, with Hard and the maximum public
hint before every White/Black move, including the six declared theory plies.
Hard plays Black and loses by checkmate to the full-strength offline teacher.
This is diagnostic evidence, not a paired Elo estimate or an opening-strength gate.

The stored second offer after `1.d4 Nf6 2.c4 e6 3.Nc3` is
`…Be7 e4 …d6 Bf4 …Nc6 Nf3`. Free cold Stockfish 19 searches prefer `…Bb4`;
forcing `…Be7` loses 249.5‰ WDL at both H17 and H19. The original oracle
receipts retain their actual identities in
[nimzo-hint-witness.yaml](../../atf-tests/src/test/resources/nimzo-hint-witness.yaml).
[NimzoWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/NimzoWitnessTest.java)
checks legality, both horizons, changed outcomes, foreign phases and missing receipts.
[NimzoHintBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/NimzoHintBenchmarkTest.java)
now passes for the opening root. [NimzoRepertoireTest](../../atf-tests/src/test/java/ro/atf/chess/NimzoRepertoireTest.java) makes the exact position, preserved H6/200k allowance, all six scored PV plies and the French counterexample regular component contracts. The correction is a teacher-qualified repertoire entry, not a new global evaluation term; see the [behavior and limits](../board/ai-quality.md#verified-nimzo-development). The later quiet-reply and review failures below stay open. The original pre-fix audit retains 1,462 original cold oracle queries plus the final-review additions. There are 80 offered hints and 479 hypothetical hint plies: 43 have stable large loss, including 17 opposing replies and six offered roots (one first choice). The native 63-ply final review has four large-loss recommendations, 42 bad hypothetical review plies, three error labels on independently small losses and two missed large losses. Evidence integrity passes; native quality remains FAIL.

At actual ply 34, the first preview ends `…Rxf6 Ne4 …Rf7 dxc5 …b6 Be3 …bxc5 Bxc5`. The final `Bxc5` loses 504‰ at both horizons, but the apparent `…Qxc5` recapture is a trap refuted by `Nxc5`; it preserves White's win. Free opposing play instead uses the quiet `…Ne5`. White could preserve the win with `Rf1` before `Bxc5`. The same witness now retains both teacher identities and tests a foreign reply even when its envelope is rehashed. Do not append `…Qxc5` as a presumed forced continuation or ban bishop sacrifices to hide the actual free-reply defect.

The first large losses in actual Native Hard play are `…Nd7` at ply 18 (259/159‰) and `…Nf6` at 20 (224/202.5‰), with `…Qc7` preferred independently. Both actual decisions consumed 200k additional nodes and retained H5. These are search decisions, not evidence that simply varying a book improves Elo. All original journals, full-review branches, endpoint replies and final Extent reports remain in `C:\work\sah\.studii\corpus\2026-11-19-nimzo-variety` and the companion `experiments\2026-11-19-opening-variety` study.

Opening variety itself is covered by
[OpeningVarietyTest](../../atf-tests/src/test/java/ro/atf/chess/OpeningVarietyTest.java)
and documented in the [AI guide](../board/ai-quality.md). It does not randomize
hint ranking, change search budgets or prove any increase in Hard's Elo.

## King's Indian audit after the Nimzo correction

New game `kings-indian-hard-black`, producer `c8247d386bc922aefbc7d7c46838e32fc00c7e96`, reaches actual checkmate at ply 79. Every White/Black move has its pre-move Native Hard calculation and maximum ordinary hint, and every saved move has complete native final review. All 109 offered hints and 568 hypothetical plies were independently graded at H17/H19. There are 45 stable large PV losses, five offered-root losses (three first choices) and 21 weak opposing replies. Seven exposed important-piece endpoints have no stable large oracle loss; Game Review still has five bad recommendations, 40 bad hypothetical review plies, five error labels on small independent losses and one missed large loss. Integrity passes; global chess quality does not.

The first actual loss is 8…Bd7, also shown by the hint, at H5 after 200k additional native nodes. Free Stockfish replies prefer …e6 with Rc1 among the opposing continuations; the played move loses 231/242‰. [KingsIndianWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/KingsIndianWitnessTest.java) preserves the original actor, full history and both independent horizons. [KingsIndianHintBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/KingsIndianHintBenchmarkTest.java) exposes separate still-failing native goals for the actual decision and public hint. Do not turn the passing witness into an engine PASS or install another lookup without first isolating score, horizon and coverage effects.

This is a different opening family, not a paired comparison with the original Nimzo game and not a new Elo estimate. The corrected Nimzo entry is absent from these actual King’s Indian root positions. The known Nimzo Bxc5/Ne5 and Scotch Re8+ defects remain open. Complete evidence is in `C:\work\sah\.studii\experiments\2026-11-19-nimzo-free-replies` and `corpus\2026-11-19-kings-indian-qualified`.
