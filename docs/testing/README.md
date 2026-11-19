# Testing strategy

## Overview

Choose the lowest test level that can observe the change. Build from a copy of the source in TMP.

- [JVM suites](#jvm-suites) check rules, adapters, and governance without an emulator.
- [Android sanity](#android-sanity) and [Web parity](#web-engine-parity) cover rendering, lifecycle, and real assistive technology when needed.
- [Extent evidence](#extent-reporting) ties assertions to the run. [Qualification](../release/README.md#qualification-and-publication) governs promotion.

## Choosing the Test Level

- **Component** for rules, progress, persistence, ordering, and measurable layout. Use the real store with in-memory preferences and assert observable values.
- **Governance** for structural rules, provenance, reporting contracts, and publication. Include a negative probe when an incomplete artifact could pass.
- **Android sanity** when behavior depends on an Activity, Android preferences, processes, native rendering, or accessibility. Keep the flow that verifies the integration missing from component coverage.
- **Benchmark** only for comparisons with Stockfish, bot calibration, or timing measurements. Hold inputs fixed and report optional results separately.

Choose the lowest level that can observe the contract. Verify the rules in the
JVM, and verify native integration and an actual process restart on Android.
In Extent, follow the assertion, diagnostic, and checkpoint capture where needed.

## Engine and projection

Check move legality, board projections, and catalog solutions in the JVM. Compare move sets against the independent chess.js oracle.

<details>
<summary>Rule and catalog checks, oracles, and regeneration commands</summary>

`ChessComponentTest` checks the starting position, orientation, and legality against independent oracles. Cases include position counts for the first three plies, castling, en passant, promotion, and checkmate.

`BoardPanelLayoutTest` verifies panel geometry for tall and short windows and taller labels. It calculates board size from the space left after measuring both labels.

- `MainMenuLayoutTest` checks the order of the six chapter entries, their RO/EN labels, and scrolling in a short window. `QuestStepTest` checks the pieces and side to move before and after the defense, resumption, and navigation locking until solved. It also covers replaying an alternate mate from the catalog with its explanation, and returning to the reference line for old or invalid data.

- `QuestCatalogTest` enumerates every legal move for all 500 puzzles and requires exactly one mating solution. One probe distinguishes check with a legal defense from checkmate.
- Another checks stalemate, castling through check, and an en passant capture that would expose the mover's king. It compares the full move-set fingerprint with [`quest-oracle.yaml`](../../chess-core/src/main/resources/quest-oracle.yaml), produced by `chess.js` 1.4.0.

`QuestCatalogTest` recalculates the strictly increasing Mate in 1 order from YAML and checks catalogs for repeated positions. Negative cases cover an alternative mate, an incorrect theme, a retired ID, a similar position with the same solution, and copies created by symmetry. `QuestProgressTest` verifies resumption after reordering and removal of a position without awarding credit for its replacement.

`QuestHintTest` checks all 500 puzzles to ensure that the first tier reveals only the origin and the second reveals the destination validated by the engine. After the opponent's defense, the hint must use the next position. `QuestProgressTest` checks milestones after resumption and reordering, idempotent solves, and completion of a catalog of a different size.

- `MateExplanationTest` compares the checking pieces and explanations of escape squares for all 500 puzzles with the fingerprint produced by chess.js. The `mate-101` and `mate-102` probes use catalog positions and check that they start in check, along with the hints and every legal defense. A separate probe requires a legal defense after a check that leaves the opponent legal moves.

```powershell
./scripts/update-mate-one-order.ps1 -ChessJs $chessJs
```

Set `$chessJs` to the CommonJS file from the extracted chess.js 1.4.0 package. The verifier reads the version from `package.json`. The independent engine runs in TMP, outside the APK. [chess.js](https://github.com/jhlywa/chess.js/tree/v1.4.0) · [Regenerating the order and evidence](../board/quest-curation.md#configuring-the-chapters)

`MateInTwoCatalogTest` checks exactly 300 positions, the absence of an immediate mate, a single key move, every legal defense, and all mates in the resulting branches. The fingerprint calculated by Chess Lab must match the one chess.js produced independently.

```powershell
node scripts/verify-mate-in-two.cjs $chessJs chess-core/src/main/resources/mate-in-two.yaml chess-core/src/main/resources/mate-in-two.sha256
```

`MateInThreeCatalogTest` checks 200 positions with mate in exactly three, a unique first move, and all winning continuations after the first defense. It compares the fingerprint with the one calculated by chess.js 1.4.0.

```powershell
node scripts/verify-mate-in-three.cjs $chessJs chess-core/src/main/resources/mate-in-three.yaml chess-core/src/main/resources/mate-in-three.sha256
```

</details>

### Adaptive Hard budget

`HardAdaptivePolicyTest` checks typed YAML keys, the base and maximum boundary,
minimum complete depth, measured cache reuse, king-file risk, and isolation between
decisions. A reserved exchange verification counts toward the same allowance.
An actual nested proof test checks the final base nodes and the final maximum
nodes, with and without the unresolved-exchange signal.
`AiOpeningRegressionTest` retains the exposed-pawn capture and defended-pawn
controls after a fifth ply completes. `MatchSetupTest` and
`RookEndgameHistoryTest` check the configured maximum and legal results.
The history probe replays a frozen sequence, so changing the search allowance
does not generate a different fixture before the assertion.

Use the current `hard-adaptive.yaml` when comparing fixed 100,000, fixed 200,000,
and adaptive search. Compare every move from the same source and keep the full
history. A higher budget can change the preferred move, so check the opposing
reply and the resulting WDL with Stockfish. Report actual decisions, completed
depths, nodes, and CPU time before interpreting the Elo estimate.
The learned-ordering league uses its own frozen source and policy. Its Elo
estimate belongs to that exact comparator.

### Hint integrity and shared opening play

`HintFrontierTest` compares `…Nxc2+` through `Qxc2`, the initial bishop recapture,
and two sound mating sacrifices with displaced-support counterexamples. Warm,
cold, and disabled caches must agree after a narrow-window cutoff. YAML rejects
more than 200,000 hint nodes, and actual public decisions count the initial
horizons, tactical frontier, and common-depth comparison within that ceiling.

`MatchHintTest`, `HintComparisonTest`, and `HintPreviewTest` require the bulb,
comparison, destination tap, and replay to retain the same scored moves.
Playback preserves the checkpoint and adds no unscored response. Comparison
assertions distinguish the nominal completed horizon from its bounded frontier.

`OpeningPlayTest` covers Basic, Advanced, and Hard from different human openings,
rebranching, exact transpositions, lost castling rights, and the real exit from
theory. Profile and review-only overrides keep their scopes. Every authored
frame and whole documented continuation is replayed by the actual rules.

[Hint frontier](../../atf-tests/src/test/java/ro/atf/chess/HintFrontierTest.java) · [Opening contracts](../../atf-tests/src/test/java/ro/atf/chess/OpeningPlayTest.java) · [Web replay parity](../../atf-tests/src/test/java/ro/atf/chess/WebHintReplayComponentTest.java)

`HintRecaptureRegressionTest` protects the forcing pawn/piece capture comparison, the preceding good hint, warm/cold/disabled cache identity, interrupted exchanges and bounded YAML. [Measured Caro–Kann correction](../board/ai-quality.md#carokann-hint-and-forcing-pawn-recapture-verification)

`HintQueenRecaptureTest` protects the Italian developing queen attack, the legal pawn recapture, scoped seed/comparison cache, completed-budget fallback, manual-board/tap identity and sound queen offers. Disabling the allowance must reproduce the old first hint. [Measured Italian correction](../board/ai-quality.md#italian-queen-recapture-and-hint-continuity)

`HintPinSupportTest` governs exact-score defensive ties, safe reinforcement of a relatively pinned minor, counterexamples, both colors, numerical precedence, preserved history and the shared manual/game hint path. The target stays H5 at 200k, with −21/−21 cp unchanged. [Sicilian audit](../board/ai-quality.md#sicilian-exact-ties-and-pin-reinforcement)

### Offline hint collection

`HintAuditSessionTest` validates the versioned plan, full-history resumption,
trusted receipt anchors, actual material, unavailable ranks, and protected output
with mocks. Its separately tagged benchmark records real short paths and
same-position budget comparisons through one engine call per decision.
The Extent report preserves source identity, checkpoints, FEN, offered and
compared variants, reply facts, actual depths and nodes. [Run and interpretation](hint-audit.md)

`HardHintAuditedGameTest` binds native play, public hints and the bar at each checkpoint. `StockfishStudyAuditTest` rejects altered raw UCI, roots, histories, horizons and oracle identities. `HardHintStudyBenchmarkTest` publishes the recorded collection offline with separate integrity and chess-quality verdicts. [Paired audit and publication](hard-hint-study.md)

### Native Hard game audit

Every ply, including an opposing reply, requires the actual history-aware Hard analysis. Components reject missing or foreign receipts; the benchmark publisher replays evidence before Elo and Extent. [Protocol, command and interpretation](hard-audit.md)

## Web engine parity

`ChessWebRulesComponentTest` checks the engine and catalogs on the JVM. `ChessWebParityPublication` executes the same cases in the TeaVM worker for all resources used. Run the probe explicitly with the bundle from TMP. [Web build and probes](../../web/README.md)

### Opening the Web menu

`ChessWebShellTest` compares the Web title and groups with the APK resources. Navigation components simulate browser history, direct access to a section, and returning to the menu. Governance checks the public address in the README, and the sanity run starts at that link in a fresh profile. [Menu contract](../../web/README.md#main-menu-and-pieces)

- For the initial color, `MatchSetupTest` and `ChessWebParityPublication#packagedWorkerResolvesAndSavesTheSharedColorChoices` use the same fixture with explicit outcomes for White, Black, and both random results. The report verifies the number of draws, orientation, turn, and color retention after reopening.
- The test adapter also checks rejection of an AI reply from an expired revision. `ChessWebShellTest` compares the order and RO/EN option text with Android resources.

`ChessWebStudyShellTest` checks the common rule and control structure. `ChessWebBoardSizingTest` evaluates the actual CSS expressions against ten independent fixtures for screen width, height, rail and text scale, including rejected arithmetic inputs. `BoardSizingGovernanceTest` checks the single sizing owner and the consumers' scroll policy. These components qualify the contract; they do not replace browser layout measurement.

In Browser MCP, use Analysis board as the reference at the same viewport and text size. Compare Puzzles, AI, reopened Saved games and Openings at 360 × 640, 740 × 420 and 1280 × 900 CSS px. Measure the square, rail and controls before and after navigation. Standard AI and puzzle pages must not require document scrolling; explanations scroll in their own area. Preserve 48 px targets, all eight ranks and no horizontal overflow. [Sizing rule](../../web/README.md#the-board-in-puzzles-and-matches)

### Menu and hint parity

- `ChessWebShellTest` compares the home and game actions with Android and checks
  import recovery on its own route. Name and Settings are the last home actions.
- `ChessWebOptionsMenuTest` tests the real menu controller with simulated bounds,
  viewport changes, keyboard input and focus. Include compact and desktop sizes,
  130% text, a scrolled origin and long lists.
- `HintReplayPresentationTest` checks the shared replay layout, independent line
  cursors and the Android adapter. The selected history position is preserved.
- [`HintArrowTest`](../../atf-tests/src/test/java/ro/atf/chess/HintArrowTest.java) checks the tip's actual position against the destination center for short and long horizontal, vertical, diagonal and knight moves, in both orientations and at different cell sizes. It compares the JVM model, Web SVG and mocked review Canvas on the same measured-cell cases, including odd-sized boards with rounded cell edges. The expected gap is 12% of the cell side; the Android adapter delegates to the tested model.
- `WebUiParityGovernanceTest` requires RAG to cite these component checks and
  the owning section. An unknown surface remains flagged for review.

Use the MCP browser for the rendered Web check and Android for the native
adapter. Keep those observations distinct from the offline component verdict.

### Controlling AI replies

- Select `ChessWebParityPublication#packagedWorkerKeepsAiIdentityThroughManualControl` in the Maven command for the TeaVM bundle. `MatchControlsTest` and the worker read the same fixture for toggling control without a manual move and for a legal move played by the AI side.
- The worker probe requires the same ID, slot, and checkpoint after reopening, rejects replies from expired revisions, and verifies the manual move's effect on the Elo estimate. `MatchLedgerTest` also checks classification when loading old saves.
- [Maven command](../../web/README.md) · [Shared data](../../atf-tests/src/test/resources/match-automation-cases.json)

- `MatchControlsTest` checks switch availability at the current position, in history, and after the game ends. `ChessWebShellTest` checks the RO and EN labels against Android resources.
- A simulated clock and computation verify cancellation through `AbortSignal` and ignoring a result delivered after cancellation. In the UI sanity run, switch to manual mode on the AI's turn, make a legal move, and reopen the same slot.
- Re-enable automation on the AI's turn and observe a single reply. [Game contract](../../web/README.md#ai-reply-control)

### Analysis in the AI game

Compare the same checkpoint and ply in the JVM and worker. Use native and browser probes for the rendered bar, lightbulb, and fixed board.

`MatchHistorySelectionTest` verifies the single game-move cursor against fourteen shared cases. The Web component covers both wire formats, unchanged live announcements, history growth, branching and stale cursors. The compiled worker checks projection parity and orientation. In Browser MCP, step from Start through the first, intermediate and latest move at compact, landscape and desktop sizes; verify one label between the arrows, boundary cues and an unchanged board rectangle. The APK adapter uses the shared cursor in game mode and preserves the Analysis board's history strip.

`NavigationGovernanceTest` registers the shared title, Back and navigation styling, the adapters that consume the line projection and the variant collections that must remain available. Component probes retain the Analysis board's complete history, both opening and calculated-line branches, and puzzle hint/Danger controls. A rendered Web check visits Games, Puzzles, Openings and Analysis board, including their contextual Back path; it must not infer Android rendering from Web captures.

`ReplayBoardLayoutTest` checks the native source-side contract, fixed controls,
long explanations, resizing and missing geometry. `ChessWebStudyShellTest#replayChromeKeepsFixedSlots`
publishes the CSS chrome checks in Extent; mocked owners separately exercise
capture before hiding the source, independent branches, stepping and reopening.
For Browser MCP, open Calculated line, Mating line and an Analysis preview on
each profile above. Compare the source and replay sides, then every board and
arrow rectangle after Next, Previous, a branch change and return. Record the
document height, dialog scroll range and embedded captures. The explanation
may scroll; the board and controls remain fixed. Native compilation and pure
layout components prepare APK parity but do not qualify Android rendering.
[Shared replay contract](../board/README.md#source-sized-replay)

<details>
<summary>Shared cases, screens, and receipts</summary>

Select `MatchAnalysisParityTest` and
`ChessWebParityPublication#packagedWorkerProjectsInlineMatchAnalysis` in the
Maven command for the TeaVM bundle. The shared cases fix the checkpoint and the
displayed ply. Compare the option after restoration, White's evaluation, the
mating line from the historical position, and the terminal state.
[Maven command](../../web/README.md) · [Shared data](../../atf-tests/src/test/resources/match-analysis-cases.json)

`ChessWebShellTest` exercises switching positions during computation and
rejecting a hint that is no longer legal in the displayed position in Node.
A worker mock tracks the state of the bar, clearing the selection once, and
line replay. The off-book position and the mate case compare the analysis
result in the game and on the Analysis board.

- In the UI probe, open the main menu with an analysis already imported. The title remains alone in the header, and analysis can be opened from the import area.
- Start a game with the checkbox cleared and verify that the panel is absent. Start another with analysis enabled and compare the bar and board rectangles at the starting position.
- Trigger the hint button in the history row and step one ply in the preview, then check the position and focus restoration. Compare source and replay sides for **Calculated line**, puzzle replay and Analysis board at 360 × 640, 1280 × 900 and 740 × 420 CSS px. Verify 64 square cells, visible controls, no horizontal overflow and a new source measurement on reopening. Components cover capture before hiding the meter, invalid geometry and stepping without recapture. After reopening the game, verify the saved checkbox, orientation, and layout at larger text size.
- [Web contract](../../web/README.md#analysis-in-an-ai-match)

- For the paired RO/EN probe, start from the same game after **1.e4 e5**.
  Select position 1/2 and verify the left bar, the +0.05 evaluation for
  White, and the hint **…Nc6**. Replay steps 0 → 1 → 2 → 1 on the variation board.
  Close at 1/2 and reopen the same slot at 2/2. The bar reads +0.19 for White.
  On the Web, the probe also verified the new hint **Nf3**.

| Adapter | Compact profile, 1.3× text | Probe and evidence |
|---|---|---|
| Android | 720 × 1280, 320 dpi | `-AnalysisOnly -CompactMatchAnalysisOnly` checks the isolated APK during development. For a release, add `-Distribution -SourceCommit <sha>` to check the exact signed APK. Both probes run setup and an actual restart in RO and EN. `AndroidSanityPublication#publishCompactMatchAnalysisSanity` verifies receipts and captures. |
| Web | 360 × 640 CSS px | The MCP browser measures the board and saves captures in history, replay, return, and resumption. `ChessWebMatchAnalysisPublication` ties both languages to the run, revision, and runtime. |

The Web publisher receives the receipts directory through
`-Dchess.web.match.browser`, the runtime directory through
`-Dchess.web.match.runtime`, and the execution values through
`-Dchess.web.match.runId` and `-Dchess.web.match.source`. Choose a new HTML
file through `-Dchess.web.match.extent`. Negative tests check substituted
receipts, distorted coordinates, and deviations from the common line.

- Compare the board before and after using the arrows, on returning from the
  preview, and after reopening the slot. For the APK, verify the largest square
  after measuring the usable space without the system bars and reserving room
  for players and controls. The measured Android profile leaves a 694 × 665 px
  rectangle for the board after reserving the evaluation bar and gap. The 665 px
  side (332.5 dp) reaches the height limit. The Web follows the shared Analysis
  board reference instead of the native geometry. Its 8 px rail and 5 px gap
  remain alongside the full square. Compare the Web result with Analysis at
  the same viewport and text size. The hint retains a 48 px target; on short
  pages, keep it in the viewport without changing the square. **Mate in 1** keeps its
  rectangle after solving, Next, and Back.

</details>

### Updating the Web bundle

In the component test, verify that the addresses in `index.html` point to the
same version for all styles and the entry module, including after a change to
a single file. Follow the relative imports and worker through to the TeaVM engine
in the version directory. A bundle with the same manifest must have the same
runtime address. [Bundle diagram](../../web/README.md#web-bundle-identity)

- For sanity in the MCP browser, keep a profile that opened the previous release and has a saved game. After publication, reload normally from the README link with the cache enabled.
- Verify the addresses actually requested, slot resumption, and that the bar and hint button work. In Extent, record the revision, runtime directory, checkpoint, and captures.
- Identify the Actions deployment using the exact static commit and runtime manifest. An HTTP probe bypasses cache to verify the bytes served for each file. [Web publication](../../web/parity.md#publishing-the-link)

The same-tab migration probe reopened the slot on the public site with the
styles and module from the same version. The hint button indicated e2–e4.
The local probe with the cache enabled also kept the position after 1.e4 Nc6
before requesting a new hint.

### Board orientation in a game

- `MatchControlsTest` verifies that the action is available in a game and in history. `MatchStoreTest` and `ChessWebParityPublication#packagedWorkerPreservesTheMatchWhenFlipping` use the same fixture for preserving the position, history, and orientation upon reopening.
- The worker rejects a pending reply from the old revision. `ChessWebShellTest` uses a simulated clock to verify that the old schedule is replaced by a single reply at the current revision.
- [Verification command](../../web/README.md#the-chess-lab-engine-in-the-browser)

- In the UI probe, open a game, return to a historical ply, and select **Flip board**. Compare the corners, turn, ply, and board rectangle.
- Verify that focus returns to the header button and check the orientation after reopening. The archive fixture checks the new slot's orientation on continuation and the original perspective when reopening the archive.
- [Game behavior](../../web/README.md#ai-match)

### Saved games, archive, and continuation

Use components for slot and archive state, continuation, graph selection, and logical focus. Keep real UI probes for rendering and assistive technology.

<details>
<summary>Storage, navigation, and focus checks</summary>

`MatchStoreTest`, `MatchLedgerTest`, and `MatchLedgerPreferencesTest` check migration, order, retention, and slot selection. Cases cover overwriting the active slot, a separate continuation from the archive, and refusal when all slots are occupied. `ChessWebLedgerPublication` checks the library after reloading, checkpoints, and Edge captures. [Web probes](../../web/README.md#saved-and-finished-matches)

`MatchLedgerReviewTest` checks persistence of analysis and the resume position per archive entry, rejection of a checkpoint or cache from another game, migration of a single result, and complete eviction. `ChessWebArchiveReviewsPublication` validates two worker-calculated reviews, the positions restored after reload, and captures from the same run. [Resuming archived analysis](../../web/README.md#resuming-an-archived-match-review)

- `ArchivedReviewContinuationTest` checks the exact position in history, the separate slot, a full library, and a single AI reply on resumption. `ChessWebArchiveContinuationPublication` compares browser evidence with the original checkpoint, analysis, and resume position, including after a move in the new game.
- Android keeps the native probe for continuing from the archive under `-GameReviewOnly`. [Continuing from Game Review](../../web/README.md#continuing-from-game-review)

- `GameReviewGraphParityTest` uses the 185-ply game from the APK and separately verified control positions. On the JVM, it checks point 184 at native geometry and at two Web widths, both endpoints, the original Staunton pieces, and capture dimensions.
- Android sanity keeps native touch and rendering checks. `ChessWebReviewGraphPublication` requires coordinates sent from the UI to the worker, the board position, the visible row, and the archive marker after reloading.
- [Game Review graph](../../web/README.md#game-review-graph)

- `ChessWebReviewAccessibilityTest` checks focus destinations, history bounds, and required scrolling in Node. `GameReviewSessionTest` checks returning to the played position after **Why?**.
- The Edge sanity run keeps the Tab, arrow-key, and visible focus return probe at 1.3× font scale. The compact Android RO/EN baseline verifies fit and position retention.
- [Keyboard controls in Game Review](../../web/README.md#keyboard-navigation-in-game-review) · [Compact replay](#shared-replay-on-a-compact-screen)

For screen readers, `ChessWebReviewAccessibilityTest` checks RO/EN text, graph bounds, and the source of each announcement. The sanity run tracks the graph value, selected row, and return from **Why?**. Extent identifies the source of each observation, such as the Chromium tree, an Android event, or a request to the speech synthesizer. [Web screen reader](../../web/README.md#screen-readers-in-game-review) · [TalkBack probe](#talkback-probe)

On Android, `-MatchMenusOnly` verifies the lists and opening saved and finished rows. `-GameReviewOnly` checks an alternative move in the continuation and retention of the archive entry. [Game and history](#game-and-history)

</details>

### Web Study

The component tests simulate the board, storage, and timer. They check arrow geometry in both orientations, RO/EN labels from the APK resources, access to long explanations, focus, history, resumption of a defense, and replies delayed until after the screen closes.

`ChessWebStudyShellTest` also runs the compiled worker when it receives `-Dchess.study.bundle`. The Extent report records opening the six chapters, the RO/EN opening catalog, the analysis position, Game Review isolation, and refusal to continue when all ten slots are occupied. [Study controls](../../web/README.md#study)

### Resuming Game Review on Android and the Web

- `GameReviewSessionTest` checks ply selection, both branches, the bookmark, and rejection of expired commands. `ChessGameSessionTest` checks the restored position and analysis of an unfinished line.
- Edge probes use the compiled bundle for import, new analysis, and a real reload. The Extent publisher verifies moves, the analysis cache, bundle identity, and captures.
- [Run commands](../../web/README.md#game-review)

## Tactics and scoring

- `QuestRewardsTest` checks the streak across chapters, the bonus cap, restoration of an assisted attempt, previous scores, and totals. `TacticsCatalogTest` checks the unique key move, absence of an immediate mate, continuity of IDs, and gains that withstand legal replies at the end of the line.
- `QuestPayoffTest` checks resuming from intermediate stages, captures, the alternative ending, and restoring older solutions. The YAML gives the provenance of the positions.
- The fingerprint contains the key move and material gain recalculated by chess.js.

- `QuestDefenseStudyTest` checks every study in the [explanation catalog](../../chess-core/src/main/resources/quest-defense-studies.yaml). It checks the key move, legal reply, language, board material balance, and compatibility with saved progress.
- The `measuresHardOnReviewedDefensesWithoutGivingItTheLesson` probe measures key moves and subsequent moves separately. For each fixed position, the engine receives only the board and the Hard profile.
- Agreement with the study line remains a measurement, while legality and the cap are assertions.

For Tactics explanations, run `run-android-sanity.ps1 -TacticsExplanationOnly` with the ADB parameters in [Android sanity](#android-sanity). From the board, the probe opens the normal replay for `tactic-002` and the study with the `…Kg8` reply for `tactic-043`. It verifies navigation, displayed material, and retention of points after closing.

Publish `android-tactics-explanation.html` with `-Dtest=AndroidSanityPublication#publishTacticsExplanationSanity` and `-Dchess.android.receipt=<run-directory>/tactics-explanation.txt`. Extent includes the captures and checks the run ID, receipt, and PNG fingerprints reported by instrumentation.

- For the last move in `training-069`, run `-TrainingFinishOnly`. `QuestPayoffTest` checks the immediate objective, the legal line after `Rd8+`, and scoring exactly once.
- Sanity checks access to the explanation after check, replay on the board, progress retention, and reopening the explanation after solving. Publish the report with `-Dtest=AndroidSanityPublication#publishTrainingFinishSanity` and the `training-finish.txt` receipt.

```powershell
node scripts/verify-tactics.cjs $chessJs chess-core/src/main/resources/tactics.yaml chess-core/src/main/resources/tactics.sha256
```

## Resuming puzzle explanations

[QuestReplayTest](../../atf-tests/src/test/java/ro/atf/chess/QuestReplayTest.java)
uses catalog positions to verify the saved ply in Mate in 2, every frame in
both directions in Mate in 3, and the exact award of 10 points.

The native probe keeps checks for the controls at larger font size, opening
after the saved defense, board rotation, piece pixels, and progress after
closing the explanation. Even when two moves end on the same square, the
piece positions distinguish the defense from the final frame.

- On Android 9, at 720 × 1280 logical and 320 dpi, the UI Automation screenshot can crop the physical 1080 × 1920 frame. The probe checks the board and controls in the window, then uses the full frame and checks all four board corners in the PNG.
- At native 1080 × 1920, it uses UI Automation. Both runs use a 1.3× font scale.
- The replayDisplay receipt records the profile and capture source. [Geometry test](../../atf-tests/src/test/java/ro/atf/chess/ScreenCaptureGeometryTest.java)

## Puzzle progress

Check scoring, navigation, saved progress, and chapter geometry in the JVM. Keep native probes for the rendered board, controls, and saved state across process restart.

<details>
<summary>Chapter suites, native probes, and catalog checks</summary>

`AppLanguagePreferenceTest` verifies the default selection, language
persistence, and a rejected write. `MainMenuLayoutTest` pins **Settings** in
the Profile group. The `-PuzzleUxOnly` probe selects both languages in the
selector, reopens the menu, and checks **Openings** after switching, with
the game, profile, and progress preserved.

- `QuestHintTest` covers piece → move → explanation. `PuzzleExplainLayoutTest`
  checks the explanation structure, 48 dp controls, and space reserved for
  **Danger**. The hint bulb, arrow, and book distinguish the three actions
  on screen. The explanation puts the title and exit in the header. The move
  and evidence scroll beneath the fixed board. The `-PuzzleMateReplayOnly` and
  `-PuzzleDangerOnly` probes keep native checks of the line and marker.

- The six chapter headers show chapter points and the global streak once two consecutive puzzles have been solved. The arrows and hint button sit under the board.
- The header menu shows options in the same area, preserving the board position and side length. **Flip board** changes the orientation, and **Main menu** preserves the puzzle, ply, and rewards before returning to the main screen.
- The system **Back** command closes the options first. From the game screen, it returns to **Puzzles**.
- Details can scroll. The reserved area keeps one status row and room for options so that the board uses the full width when it fits.

| Probe | Contract covered |
|---|---|
| `PuzzleChromeLayoutTest` | Two-row header, score, and controls in limited space. The arrows and hint button are centered below the square board, with 48 dp targets. |
| Android sanity with `-PuzzleUxOnly` | Checks all six chapters in Romanian and English. Opens options, flips the board, and returns with progress preserved. Checks board geometry and control locking at bounds, during a reply, or before a solve. |

- Run `-PuzzleUxOnly -PuzzleLanguage ro` at 720 × 1280 and 320 dpi and `-PuzzleUxOnly -PuzzleLanguage en` at 1080 × 1920 and 440 dpi, both at a 1.3× font scale. Publish the six checkpoints from each run with `AndroidSanityPublication#publishPuzzleUxSanity`.
- Extent ties each comparison and embedded capture to the native receipt. The identifiers `puzzle_options`, `puzzle_flip_board`, `puzzle_main_menu`, `puzzle_points`, `puzzle_streak`, `quest_previous`, and `quest_next` let you check the controls through the accessibility tree.

`BoardPanelLayoutTest` checks the maximum width of the game and replay boards
at four widths, plus a short window and larger text. The Android probe
`-PuzzleDangerOnly` compares the game, puzzle, and explanation in the same window.

- `QuestDangerTest` checks mate in the final position, captures for both colors, a recapture that cancels the gain, stalemate, a pinned piece, and en passant. On Android, `-PuzzleDangerOnly` checks activation on request, hiding the marker when navigating, the accessible reason, and points retention.
- Publish the `puzzle-danger.txt` receipt through `AndroidSanityPublication#publishPuzzleDangerSanity`. Extent ties the assertions and captures to the same run and APK.

- `QuestStoreTest` uses the production store with in-memory preferences. It checks resuming the cursor by ID in **Tactics** and **Tactical training**, solving without duplicate points, the shared streak, and separate totals.
- It covers legacy preferences and retry after a rejected write. `QuestProgressTest` checks the next unsolved puzzle after the catalog grows.

- `QuestCatalogTest` checks 1,238 distinct public positions and 24 independent positions. `TacticsCatalogTest` checks the material proof for the 114 Tactical training exercises and the independent set.
- `MainMenuLayoutTest` checks titles and totals. `ChessComponentSuite` runs without an emulator.
- `DocumentationGovernanceTest` and `ReleaseGovernanceTest` verify local links, the version, and the download section.

When the material catalog changes, run the key and fingerprint verification for each affected chapter. The verifier also reconstructs the lines published in `payoff-line`, including legal replies after the last move. The fingerprint continues to track the key move and the result at a three-ply horizon.

```powershell
node scripts/verify-tactics.cjs $chessJs chess-core/src/main/resources/training.yaml chess-core/src/main/resources/training.sha256
node scripts/verify-tactics.cjs $chessJs atf-tests/src/test/resources/tactical-transfer-holdout.yaml atf-tests/src/test/resources/tactical-transfer-holdout.sha256
node scripts/verify-tactics.cjs $chessJs chess-core/src/main/resources/intermezzo.yaml chess-core/src/main/resources/intermezzo.sha256
```

</details>

## JVM suites

| JUnit entry point | What it checks |
|---|---|
| `ChessComponentSuite` | Rules, positions and puzzles, progress, games, openings, hints, and geometry verified in the JVM. Includes Web accessibility contracts run through Node.js. Runs offline. |
| `ChessGovernanceSuite` | Suite inventory, Extent results and diagnostics, local documentation links, AI policy, and release qualification. |
| `ChessBenchmarkSuite` | Stockfish, the offline league, and the Elo anchor. Each probe starts when given its documented inputs. |
| `ChessJvmSuite` | The default Maven entry point. Combines the three selections in one report. |

From the TMP copy, with JDK 21, Maven, and Node.js available on PATH, run the suite you need.

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dchess.extent=target/jvm.html'
mvn test '-Dtest=ChessComponentSuite' '-Dchess.extent=target/components.html'
mvn test '-Dtest=ChessGovernanceSuite' '-Dchess.extent=target/governance.html'
```

- In the IDE, run the suite class as a JUnit test. For a single case, use its class or method directly, for example `-Dtest=ChessMatchTest#historyPreservesCastlingAndEnPassant`.
- Use a fresh Extent file for each run. For a combined report, run `ChessJvmSuite`.
- [JUnit entry points](../../atf-tests/src/test/java/ro/atf/chess/suites)

JUnit marks benchmark probes skipped when their inputs are missing. [Setting up Stockfish, the league, and the Elo anchor](../board/ai-quality.md#checks-for-every-change)

`AiLeagueBenchmarkTest` plays the 72 league games in a pool of at most three workers and independently verifies replay of each result. Use a fresh TSV path in `-Dchess.league` and a fresh Extent report. This covers concurrent searches and retention of each game's checkpoints.

- `HardQuestTransferBenchmarkTest` runs in `ChessBenchmarkSuite`. One probe requires the correct solution for every public and independent puzzle, using the position and objective.
- The other measures the Hard opponent's decision using only the position. Extent shows chapter totals, misses, and compliance with the cap.
- The threshold of 39 in Tactical training applies to the 48 IDs in the frozen set. [Regression thresholds](../../atf-tests/src/test/resources/hard-quest-transfer.yaml)

- `SuiteGovernanceTest` compares the plans discovered by JUnit. Each audited JVM method belongs to one primary suite.
- Android and TalkBack receipt publication have explicit entry points. `DocumentationGovernanceTest` checks local files and Markdown anchors, including those in feedback.

Puzzle auditing retains the actual search from the same call and distinguishes repertoire from adaptive ceilings, with negative tests and frozen accuracy floors. [Contract and interpretation](hard-audit.md#position-only-puzzle-audit)

## Test changes against a Git baseline

- Compare the change against the reviewed commit's full SHA. Use a Git worktree in TMP with the required history.
- The check reads HEAD, the index, and the working files separately. A move appears as deletion of the old path and addition of the new path.
- The check also covers edits within a test.

| Change | Requirement |
|---|---|
| Independent addition | Add it to the appropriate inventory and suite. |
| Edit to a test, resource, or selector | Record a control with the baseline, exact path, contract, and the method that checks it. |
| Deletion or move | Explicit deletion approval and a replacement test in a separate file. |
| Move from Android to the JVM | The method moves to the component suite, and the control identifies the retained native probe. |

- In the [testChanges register](test-changes.yaml), add a control for each affected existing path. Specify the ID, baseline, source path, replacement path, exact class and method, retained contract, and permission to delete.
- When moving a native check, add the path and stable label of the retained Android check. The reviewer confirms assertion equivalence and keeps Android evidence for gestures, rendering, Android data, or an actual restart.
- The Git check verifies selection, executes the JVM method, and confirms the reference to the native control.

```powershell
mvn test "-Dtest=TestChangeReview" "-Dchess.testChanges.base=$reviewBase" "-Dchess.extent=$testChangeExtent"
```

- Set reviewBase to the full SHA of the common ancestor chosen for review and testChangeExtent to a fresh HTML path in TMP. The test archives the decision and writes a separate Extent report for the executed methods.
- A new baseline requires reconciling the register. Remove the entry when the baseline revision already includes the change.
- The distribution preflight accepts the receipt for a clean Git candidate and a distinct baseline. [Preflight](../release/README.md#qualification-and-publication)

## Extent Reporting

- An audited JVM test that completes normally must evaluate at least one assertion in **TEST BODY**. A comparison made only in setup or teardown produces FAIL in JUnit and Extent.
- Existing failures and interruptions retain their diagnostics. `ExtentGovernanceTest` checks direct and suite execution.

In Extent, open the scenario, **TEST BODY**, and **Assertions** to see expected values, observed values, and the outcome of each comparison. The extension measures execution from setup to teardown, then publishes the JUnit status and diagnostic. `ExtentGovernanceTest` verifies the HTML produced for PASS, FAIL, SKIP, and a case without comparisons.

- For the two Android menus, each checkpoint groups the action, native comparison, and embedded PNG capture. When resuming a game, the assertion checks the position and last-move highlight in the image.
- Verify that assertions can be expanded and that fingerprints match the receipt, APK, commit, and captures. A missing, invalid, substituted, or repeated capture across the two screens produces FAIL with a diagnostic in the HTML.
- `ExtentGovernanceTest` exercises publication offline. [Release qualification](../release/README.md#qualification-and-publication)

For Game Review, keep the native receipt, APK identity, and Extent report. Verify the report and capture fingerprints in the release evidence. [Release qualification](../release/README.md#qualification-and-publication)

- The UI Automation capture can have raster dimensions smaller than the emulator's actual screen. Pixel comparisons map screen-coordinate rectangles to bitmap coordinates using the actual display dimensions. Element visibility checks still use the native window bounds. Resizing the capture therefore avoids a false visual failure.

## Android Sanity

Reserve an emulator for contracts that require Android. For release qualification, match the native receipts to the candidate APK and device.

<details>
<summary>Setup, phase receipts, and negative checks</summary>

- Chess Lab supports API 26 and later. The game and lessons use system navigation on API 26–32 and the platform callback from API 33.
- After a Hard move, the analysis probe sends the Android Back command and checks that the menu reopens with the game preserved. Lint checks APK APIs and resources.

- Verify compatibility of the sources packaged in the app separately.
  Run `gradlew.bat :app:lintDebug` from `android` in TMP. Maven verifies the
  engine on JDK 21. The Gradle build and lint answer different questions about
  compilation and the minimum API. The native probe confirms the behavior.

For qualification, use a reserved, clean emulator. In the TMP copy, set
`ANDROID_HOME` to SDK 36, load the signing identity, and build the distribution
APK and its instrumentation.
[Signing and qualification](../release/README.md#qualification-and-publication)

```powershell
Push-Location android
.\gradlew.bat -Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest
Pop-Location
./scripts/run-android-sanity.ps1 -Adb adb -Serial <serial> -LeaseFile <registru-rezervare> -Distribution -SourceCommit <sha>
```

The runner rejects an existing installation of the distribution package.
Use the shared reservation register on shared devices. The variant with a
different application ID remains a development probe and cannot qualify the APK.

1. The first instrumentation phase checks the menu, color, both hint tiers, and layout in Romanian at a 1.3× font scale. It solves two Tactics positions and advances to the second unsolved Tactical training position. In Mate in 2 it observes the move before the defense and the pause of at least 650 ms. In Mate in 3 it closes the board with the second defense pending.
2. The runner stops the process. The second instrumentation phase checks the pending reply, the five-solve milestone, the restored position and points in Tactics, and resumption of the second unsolved Tactical training puzzle.
3. Extent requires a common run ID and distinct PIDs for the five phases. It verifies the 58 ID–label pairs in `prepare`, the 26 comparisons in `restore`, and the game receipts. The diagnostic appears next to the native comparison.

When changing instrumentation, synchronize the `prepare` inventory and per-phase assertion counts with `AndroidSanityPublication`. To regenerate the report, copy the native evidence into a fresh directory and run `-Dtest=AndroidSanityPublication#publishBoardSanity` with `-Dchess.android.receipt=<run-directory>/prepare.txt` and `-Dchess.android.runId=<runId>`. Read `runId` from `execution.json`.

`AndroidReceiptTest` verifies offline that all 58 assertions render and that a missing assertion, duplicate ID, substituted label, and phase from a different run are rejected. The resume probe also checks rejection of a foreign Tactical training label while keeping the total unchanged.

</details>

## Upgrading saved progress

- The distribution sanity run starts from a clean installation. For the separate upgrade probe, start with a clean emulator, confirm that both APKs have the same certificate, and install the published release first.
- Create progress, stop the process, and install the candidate using `adb install -r`. Verify `versionCode`, first-install time, PIDs, and puzzle IDs, then compare the points, streak, and pending defense.

In the component test, `QuestStoreTest` verifies reordering and expanding a
chapter with the same ID and score, including rejection of a duplicate award.
An Android restart verifies restoration in the same installation.
Installing over an existing release is required to prove migration between APKs.

- On the upgrade from 0.9.0 to 0.10.0, `mate-008` reopened at `23/500`, with two solves and 20 points. It was ranked third in the old catalog.
- The Android phase for a restart on the same version checks rank 3 for the data prepared in that version. An upgrade probe verifies the stored ID and its rank in the new YAML.

On API 28, a test profile saved by 0.9.0 kept 105 solved IDs, 1,072 points, the three-solve streak, the assisted attempt, and the saved final move after upgrading. On first opening, Mate in 3 played the second pending defense and kept **Next** disabled until mate.

- Also verify a previously completed mate chapter. `mate-129`, `mate2-024nn`, and `mate3-02YEp` remain last in their chapters, and every new position in these chapters appears before them.
- In the published 0.10.0 APK, Mate in 1 displayed `500/500` with `102/500` solved and **Next** disabled. **Previous** opened the puzzle at `499/500`.

## Analysis board from a game

- `AnalysisLineTest` and the TeaVM worker read `contextual-analysis.json`. The cases fix the checkpoint, visible ply, and orientation for an AI game, an archive entry after resignation, and a checkmate position.
- They check branching, rejection of an invalid cursor, and retention of the source. The component test with mocks checks returning to the game and opening control, keeping the independent study bookmark, and ignoring replies delayed until after exit.
- [APK–Web contracts](../../web/parity.md#contracts-and-checks)

Run from the TMP copy with `$site` from the Web build.

```powershell
mvn -B -ntp -pl atf-tests -am test '-Dtest=AnalysisLineTest,ChessWebAnalysisStudyTest,ChessWebStudyShellTest#packagedWorkerSharesTheContextualAnalysisCasesWithTheJvm' '-Dsurefire.failIfNoSpecifiedTests=false' "-Dchess.study.bundle=$site" "-Dchess.extent=$report"
```

In sanity, open a game at a historical move, open the Analysis board, and return. Check the cursor, orientation, and focus. Legality and persistence cases remain in components. [Analysis board on the Web](../../web/README.md#study)

## Openings

- `OpeningCatalogTest` checks playthrough for both colors, distinct final positions, and recognition of new lines in the ECO index. `OpeningLessonTest` checks a single ply per press, the pause before a reply, and replay from history.
- `MatchAiGovernanceTest` checks Stockfish comparison positions for every lesson. In the English and Nimzo-Indian, the new positions measure Black's reply before the knight retreat and the exchange on c3, respectively.
- In the Catalan and Alapin, comparison starts with White to move after the last reply in the lesson.

- For APK–Web visual parity, `OpeningLessonTest` checks the board side length, the two-column threshold, and fit after measuring labels. `ChessWebOpeningStudyTest` checks hint tiers and the display contract without a browser.
- In sanity, open Italian Game C54 as White at `0 / 12`, then reveal `e2–e4`. Repeat at a 1.3× font scale on a compact 360 × 640 logical-unit profile and a 1280 × 900 landscape profile.
- The target side lengths are 288 and 592 units. Compare board position before and after the hint, the outline on `e2`, complete text, and controls measuring at least 48 units.
- Record Android density and usable area, the Web viewport, and paired captures in Extent. [Lessons](../board/openings.md) · [Study probes](../../web/README.md#study)

On Android, run sanity with `-OpeningsOnly`. The probe plays the indicated destination in the Italian Game and the AI suggestion compatible with the lesson. It checks the message for the AI-suggested Spanish line, navigation in both directions, replay from an earlier position, and a board oriented for Black.

With two presses of **Next**, the probe compares coordinates and dimensions for the board and the `a1` and `h8` squares when **Continue lesson** appears and when **Lesson hint** returns. It uses the emulator window and two compact native measurements, up to 320 × 480 dp and 280 × 440 dp. Extent requires both comparisons, even when the total number of assertions is correct.

```powershell
mvn test '-Dtest=AndroidSanityPublication#publishOpeningSanity' "-Dchess.android.receipt=$receipt"
```

Set `$receipt` to `openings.txt` in the TMP directory printed by the runner. The report includes native assertions and frames for the catalog, hint, analysis, and replay.

For the signed APK, use `-Distribution -IncludeOpenings -SourceCommit <sha>`. The probe uses the same APK installed for the base sanity run and saves the receipt and captures in the `openings` subdirectory.

## Game and History

Verify matches, storage, and review in components. Use Android sanity for process restarts, native navigation, and rendered evidence.

<details>
<summary>Test owners, native probes, and Extent publication</summary>

`ChessGameBotSessionTest` checks the profile and color after restoration, turn ownership, and rejection of a duplicate reply. `ChessWebBotPublication` publishes the Edge probe at `/chess/` and compares the worker's move with the JVM move. [Web sanity with AI](../../web/README.md#ai-match)

`-ActiveMatchOnly` checks the exact position after restarting on the player's turn. Publish the `active-match-prepare.txt` receipt with `AndroidSanityPublication#publishActiveMatchSanity`.

- `-PendingBotOnly` stops the process on the bot's turn in an Advanced game with the player as Black. On relaunch, the probe checks that there is a single legal reply after the history, and checks the profile and position.
- Publish `pending-bot-prepare.txt` with `AndroidSanityPublication#publishPendingBotSanity` and `-Dchess.android.receipt=$receipt`. Extent ties the captures to the APK, run, and distinct PIDs.

- `MatchControlsTest` checks menu controls in the active game, in history, and
  after the result. This includes draws only on the player's turn and the meaning
  of the **Let AI move** checkbox. `MatchToolbarResourcesTest` checks the shared
  header, touch targets, text fit, and RO/EN labels. The native probe opens the
  menu at 1.3× font scale, flips the board while preserving its coordinates,
  and checks the selected position in the move strip.

- `MatchStoreTest` runs production persistence with in-memory preferences and a fixed clock. It checks disjoint lists after migration and restart, atomic saving when the game ends, reuse of the archived slot, and the ten-active-game cap.
- `MatchListEntryTest` rejects constructing a row for the wrong menu. `MatchArchiveTest` also checks the analysis codec, cap, and checkpoint identity.

- `MatchListEntryTest` checks the displayed data for in-progress games, completed games, draws, resignations, and old saves. `MainMenuLayoutTest` checks Romanian and English text, the hierarchy of title, moves, and date, label contrast, and the **Back** button's position.
- `MatchSlotsTest` checks replacement order, manual control, and rejection of stale AI replies. Android keeps one flow per menu, with a list capture and a check that the game opens.

`ChessMatchTest` checks resumption from every position of a finished game, saving the new variation, castling, en passant, and position repetition. `GameReviewTest` checks missed mate, compared lines, ECO transposition, changes in classification with the threshold setting, cancellation, and estimation from the player's perspective. `GameReviewGovernanceTest` checks defaults, budget, Elo references, and legality of imported lines. [Method, interaction, and thresholds](../board/game-review.md)

- For geometry, `GameReviewLayoutTest` checks the largest square, fixed controls, and space left for the explanation on compact and wide portrait profiles, including larger text. The same calculation positions the board in opening theory and **Why?**.
- `GameReviewGraphLayoutTest` checks selection at each point's center on narrow graphs and long games, rounding between points, endpoints, and cases with no usable width. `GameReviewTimelineTest` checks alignment with the game, the opening label disappearing after the first navigation action, cursor bounds, jumping to a key moment, replaying the variation, and returning to the played position with history preserved.

- For native integration, run `run-android-sanity.ps1 -GameReviewOnly` after building the `sanity` variant. To qualify the APK, use `-Distribution -GameReviewOnly -SourceCommit <sha>`.
- The first phase has 23 assertions and five captures, including selection of ply 184 in the 185-ply game. The **Why?** and restore phases add seven assertions and four captures.
- They check the full-screen window, the fixed board in both lines, and both evaluations with their depth. **Back** preserves the move and visible area of history.
- The probe also checks cancellation, reopening after process termination, and a separate continuation with the archive intact. Publish `android-game-review.html` from `game-review.txt` with `-Dtest=AndroidSanityPublication#publishGameReviewSanity` and `-Dchess.android.receipt=$receipt`.
- Keep receipts and captures from all three phases together. Extent checks the common run ID, distinct PIDs on resumption, APK and device identity, and each capture fingerprint.
- [Signing and qualification](../release/README.md#qualification-and-publication) · [Review behavior](../board/game-review.md#review-navigation-and-recommended-lines)

`GameReviewTimelineTest` covers allowed and missed mate, promotion to queen and rook, a decisive capture, a compensated exchange, unequal horizons, and invalid older lines. `MatchStoreTest` verifies bookmark restoration after reopening storage, separate continuation, and invalidation when identity changes. `AndroidReceiptTest` rejects a substituted capture, phase taken from another run, and a reported restart with the same PID.

`GameReviewTest` reconciles the table with individual moves, including zeros and reversed roles when the player has Black. `MatchActionsTest` checks refusal, reopening the saved result, moving from history, and rejecting a save that overwrites checkmate. [Player flow](../board/README.md#game-against-the-computer)

- For AI analysis, run `-AnalysisOnly`. The first phase starts from a saved game and checks the hint button in history, two plies in the variation, board geometry, and returning to the cursor with the save unchanged.
- The phase in `analysis-restore/` checks the position and hidden hint after restart. There are five assertions before the restart and two after it.
- `-IncludeManualAnalysis` also runs the manual board under the same ID.

- `MatchHintTest` checks variation order, the playable destination, exact promotion, and priority of manual selection. It includes a case where the suggested move belongs to the side that has already played.
- `MatchEvaluationTest` checks White's perspective, formatting, and bar proportions for an advantage, equality, and the result. `MatchSetupTest` checks color choices, AI depths, previous saves, and the engine's opening move when the human plays Black.
- `PlayerProfileTest` checks name validation, and `MainMenuLayoutTest` checks grouping and options in both dropdowns.

- `HintPreviewTest` checks engine steps on real positions, exact restoration, mate, compensated exchanges, and rejection of illegal lines. The Sicilian probe follows `…Qxf2+ Kxf2` and checks the difference from the starting position from Black's perspective.
- `MatchHintTest` covers one, two, and three lines and excludes a positive alternative beyond the 25 cp window. `HintComparisonTest` checks the shared ordering of the hint button, dialog, and tap-to-move in the Sicilian, Caro–Kann, and Catalan for both colors.
- Extent records the moves, shared horizon, and usage below the configured cap. The probes replay both lines, preserve the variation on returning, and reject a hint from another frame.
- The Réti and Alekhine check that the move stays in place when a reversal remains unconfirmed. The explanation checks a mating reply, an uncompensated capture, and the score difference.
- `AndroidReceiptTest` reproduces a substituted PNG and a foreign phase without an emulator, even when the metadata has been updated. Extent keeps the native results, ID, APK fingerprint, and captures verified against the instrumentation fingerprints.

```powershell
mvn test '-Dmaven.repo.local=target/maven-repo' '-Dtest=AndroidSanityPublication#publishMatchAnalysisSanity' '-Dchess.android.receipt=<run-directory>/analysis-prepare.txt'
```

`ChessMatchTest` checks reconstruction before and after the bot reply, access to historical positions, checkmate, castling, en passant, and repetition. After the puzzle phases, the runner starts with fresh data and executes `match-prepare`, `match-restore`, and `match-settled` in separate processes, retaining the game between them. It checks the starting position, arrows, direct selection, return to the last move, move locking in history, and the saved game's identity.

</details>

## Analysis Board

- `AnalysisLineTest` checks navigation, branching, special moves, and prefix export. A cursor in history can open review of the finished source game if the whole line preserves its moves.
- A different branch, in-progress game, or manually played mate uses study analysis. `GameReviewTest` checks analysis of all moves and the Elo criteria.
- `MatchSlotsTest` checks the tenth slot, refusal at capacity, and rejection of stale AI replies. `MainMenuLayoutTest` covers the localized menu entry.

- For Android, use `-ManualAnalysisOnly` or `-IncludeManualAnalysis`.
  The probe opens the board from the menu, plays `1.d4` outside the three
  initial suggestions, confirms the refreshed list, then switches between two
  paths sharing their first move and follows the Italian Game to the final ply
  on the full-screen fixed board. It checks the final move explanation, returning
  with the arrow, and closing the preview at the original cursor. After leaving
  the repertoire, it checks the engine hint. When returning to history, the
  capture shows the theory label under the fixed board. The final assertion
  confirms that the suggested move creates no saved game. `OpeningTheoryTest`
  covers the three paths, all YAML moves, transpositions, castling, and the
  transition from lessons to the ECO index and engine. It verifies the Alapin and
  the switch to the engine, branching in the French, and the Catalan suffix
  after transposition. It also checks `1.d4 Nf6 2.c4 g6`, where new positions
  produce three other paths, then one, and returning to history restores the
  list. Publish `manual-analysis.txt` with
  `-Dtest=AndroidSanityPublication#publishManualAnalysisSanity` and
  `-Dchess.android.receipt=$receipt`. The report has five assertions and one
  capture tied to the receipt. The probe confirms all four board corners in the
  PNG. On API 28, when logical and physical dimensions differ, it captures the
  full physical frame. In the combined run, the Extent publisher also checks
  the game phase ID in the parent directory. [Usage](../board/README.md#analysis-board)

- `-MatchMenusOnly` runs both menus on the same installation. For separate development probes, use `-MatchSlotsOnly` or `-MatchArchiveOnly`.
- Publish with `AndroidSanityPublication#publishMatchSlotsSanity` or `#publishMatchArchiveSanity` and the phase receipt. Extent ties the assertion, embedded capture, and fingerprint to the same Android step.
- Execution identity includes the process, commit, and distribution APK fingerprint. [Saved and finished games](../board/README.md#game-against-the-computer)

## Shared replay on a compact screen

Run `scripts/run-android-sanity.ps1 -Distribution -ReplayCompactOnly -SourceCommit <sha>` after the signed build in TMP. The probe sets the portrait profile to 720 × 1280, 320 dpi, and a 1.3× font scale, starts the app in Romanian and then English with independent test data, and restores the emulator profile at the end.

In each language, it opens an Opening theory path and both **Why?** variations, tracks the controls and text, scrolls the explanation, and returns to the starting position. It checks the board and arrow rectangles when switching variations and steps. JVM tests keep geometry and cursor coverage without an emulator.

- Publish the report with `-Dtest=AndroidSanityPublication#publishReplayCompactSanity` and `-Dchess.android.receipt=<RO receipt>`. `android-replay-compact.html` includes the 12 assertions and four captures.
- The report requires both languages and the same APK, commit, profile, device, and run ID, with distinct processes. [Release qualification](../release/README.md#qualification-and-publication)

## TalkBack probe

1. Enable TalkBack on the emulator. In **Mate in 1**, open **Hint**, then **Show move**. Touch the side to move and swipe right twice through emulator touch input. After each gesture, capture the Android `TYPE_VIEW_ACCESSIBILITY_FOCUSED` event and the frame with the focus. Repeat after **Flip board**. [Emulator touch input](https://developer.android.com/studio/run/emulator-console)
2. In the first 0.3.1 puzzle, the route is Black to move → black queen on d8 → h4 with either color at the bottom. The API 33 probe with TalkBack 14.2 confirmed all four swipes through `EmulatorController/sendTouch`. The [historical Extent report](https://github.com/crististefanita/chess/blob/v0.4.0/release/0.3.1/reports/talkback-gestures-extent.html) remains in the Git tag outside the short release-retention window.
3. `QuestHintTest` checks projection in both orientations. The sanity run checks updates to native links. The swipe probe has its own report and uses focus events as evidence.

To republish, run `TalkBackPublication#publishTalkBackGestures` from the TMP copy and specify the log through `-Dchess.talkback.gestures`. The single-probe log contains `J7FocusProbe` events and `J7ProbeMarker` markers from `BLACK_ANCHOR_START` to `WHITE_DESTINATION_END`. Put `emulator.json`, the `BLACK-color.png`, `BLACK-PIECE.png`, and `BLACK-DESTINATION.png` captures, and their `WHITE-*` counterparts in the same directory.

- For speech, enable **Display speech output**. On the fifth solve, compare the single mate and milestone announcement with what is announced after flipping, returning from the menu, and restarting.
- The previous probe compares release 0.3.0 with the corrected build and records TalkBack requests and synthesized PCM audio. The [historical Extent report](https://github.com/crististefanita/chess/blob/v0.3.1/docs/feedback/talkback-extent.html) is retained in the Git tag.

### Game Review

- Enable TalkBack and **Explore by touch** on the emulator. Run the native `talkback-review-ro` and `talkback-review-en` phases from the TMP copy.
- Both use the same archived game. Focus the graph, then the selected move.
- Enter **Why?** and return with **Back**. Check focus on the button that opened the explanation and the saved ply.

On API 28, TalkBack 7.2 synthesized position 3 of 4 and White's score of zero percent once per language. The probe also captured audio for the selected move and on returning to **Why?**. `GameReviewGraphLayoutTest` verifies the same graph point and score formatting offline. [Web screen reader](../../web/README.md#screen-readers-in-game-review)

- To audit speech, run `TalkBackPublication#publishGameReviewTalkBack` with `-Dchess.talkback.review` pointing to the `run-<id>` directory. For each language, keep the native receipt, execution metadata, and three captures together.
- The shared `talkback.log` contains `J7ReviewProbe` action boundaries and `J7ReviewSpeech` receipts with text and PCM audio accepted by the observer for the TalkBack process. Extent verifies identity, digests, and announcements in each interval.
- [Web probe](../../web/README.md#screen-readers-in-game-review)

[Chessboard](../board/README.md) · [Release process](../release/README.md)

### Native Hard puzzle studies

`HintCandidateCoverageTest` checks the four Sicilian roots, raw scores, queen safety, counterexamples, fallback and small budgets. `StockfishFenAuditTest` checks FEN origin, clocks and rejection of borrowed projections. [Procedure, inventory and limits](hard-puzzle-study.md)

`NativeMateDefenseBenchmarkTest` recomputes all mating exercises offline against every legal defense and shares `NativeMateAudit` with the focused components. [Commands and limits](hard-puzzle-study.md#qualification-and-next-steps)
