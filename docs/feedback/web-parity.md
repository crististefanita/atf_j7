# Web · Parity with the Chess Lab APK

## Overview

The register tracks the remaining differences between APK and Web.
[Engine and adapters](../../web/README.md#shared-engine-and-platform-adapters) ·
[APK–Web matrix](../../web/parity.md)

## What We Reuse from J4

Card Table Browser 0.2.0 and its test JAR provide card table contracts.
Extracting a game-independent utility into shared J4/J7 code makes sense when
the evidence identifies a consumer in each project, a stable API, and duplicate
code that can be removed. The candidates are revision-based transport and
browser evidence collection.
[Card Table Browser 0.2.0 packages](https://github.com/crististefanita/atf_j4/tree/3d2b7b11eedd6aadd48a82fd7858ef195e21f12e/atf-ui-browser/lib)

## WEB-01 · Engine and Application Contract

**P1 · Chess owner.** Gradually extract session transitions that require
equivalent decisions in `ChessActivity`, `AnalysisActivity`, and the Web
adapter. Start with resuming, canceling, and receiving a delayed AI reply.
[Engine and projection](../testing/README.md#engine-and-projection)

**P1 · Web compilation · Hard proof digest.** At source `9f4fff3`, the full Web build fails in
[`PawnPushProof.hash`](../../chess-core/src/main/java/ro/atf/chess/PawnPushProof.java): TeaVM cannot compile the reachable JCA provider path behind `MessageDigest.getInstance("SHA-256")`.
The JVM classes compile, and the Android adapter compiles separately. Keep the qualified published engine for UI-only bundles.
Before publishing a new engine, use a deterministic portable digest and verify identical proof IDs on JVM and TeaVM, then rerun the complete Web build and Hard controls. Do not qualify the failed engine from its successful JVM compilation alone.

**Closure checks**

- On the JVM and in the worker, use the same case with a simulated clock and
  storage. Check position, revision, allowed actions, and stale reply rejection.
- In native sanity, verify one resumption and the displayed order.
- On mobile, measure cold worker startup and time to the first analysis ply
  separately at the same budget. Test cancellation and slot changes during calculation.

<details>
<summary>Screen reader speech and focus</summary>

**Game Review · Web.** NVDA 2026.3.1 repeated the position and score in RO in the previous release. The graph now uses one accessible value with position and score. The component covers repeated updates, and Extent rejects a Web receipt with a duplicate description. Confirm speech on the corrected build in RO and EN. Keep focus on the graph between two plies with different scores and verify it returns to the trigger after **Why?**. [Screen reader](../../web/README.md#screen-readers-in-game-review)

</details>

## WEB-02 · Adapters and Shared Layout

**P1 · APK–Web headers and navigation.** APK still uses large returns below lists, a left-side game menu and separate header implementations. Extract one native adapter with Back beside its title, options on the right and centered move navigation. Require the same component cases for both adapters, preserved Back destinations, cursors and contextual commands, then native and MCP captures at font 1.3.

**P2 · Use compact landscape width more effectively.** At 740 × 420 dp and
font 1.3, the mating source and replay both measure 285 native pixels at
320 dpi (142.5 dp). Navigation fits without page scrolling, but the vertical
stack leaves substantial unused width. Explore a shared landscape arrangement
with explanation beside the board, keeping source/replay sizing aligned and
48 dp controls. Require RO/EN component bounds and native captures before
changing the current no-jump contract.
[Puzzle explanation controls](../../atf-tests/src/test/java/ro/atf/chess/PuzzleExplainLayoutTest.java)

**P2 · Legacy analysis history oracles.** `awaitHiddenAnalysis` still checks
one history child per ply. Align `analysis-prepare/restore` and their compact
variants with the shared single-move game cursor. Preserve the Analysis
board's full history list and its separate oracle. Keep completed hint/score
readiness and the displayed position as independent checks; a timeout must
not stand in for a wrong UI oracle.
[Current cursor](../../chess-core/src/main/java/ro/atf/chess/MatchHistorySelection.java)

<details>
<summary>Adapter ownership and input</summary>

**P1 · J7 owns chess and J4 owns shared extractions.** The existing `BoardPanelLayout`, `GameReviewLayout`, `GameReviewTimeline`, and `OpeningReplay` provide starting points. Android applies the bounds and effects in `View` and dialogs. Web applies the same decisions in DOM/CSS, reads the usable area, and translates clicks, touches, and keyboard input into the same intents. [Board guide](../board/README.md) · [Game Review](../board/game-review.md#review-navigation-and-recommended-lines)

</details>

- Define a measurable contract for the square board, orientation, panel order, touch areas, reserved controls, scrollable explanation, focus, and markers. Measure the APK's usable window after bars and insets and its Web counterpart after zoom and browser chrome. Start with J7's 720 × 1280, 320 dpi, and 1.3× font check in RO/EN. Compare logical units, relative position, full text, and access thresholds. Analyze rasterization differences independently. [J7 compact check](../testing/README.md#shared-replay-on-a-compact-screen) · [Measuring the Web viewport](https://www.w3.org/TR/cssom-view-1/)
- On desktop, center a frame sized for the board and chess content. On mobile, resizing, browser chrome, or larger text must preserve the selected square, step, and access to controls. Verify keyboard input, focus order, dialog dismissal, and focus return. On Android, separately verify touch, Back, and labels read by TalkBack.
**Closure criterion.** For the same line on the analysis board and in Game Review, verify a fixed board, legible controls in RO/EN, an accessible explanation, and a return to the same state on APK and Web. [Choosing a test level](../testing/README.md#choosing-the-test-level)

## WEB-03 · Component and E2E Tests for the Shared Contract

<details>
<summary>Shared case data and oracles</summary>

**P1 · J7 and ATF owners.** Use the same case files with an ID, initial FEN position, UCI moves, settings, ordered inputs, and expected outcomes after each step. For repetition, castling, en passant, and resumption, include the required history or checkpoint in the fixture. Use a node budget and reproducible choices for the engine oracle. Measure real latency separately. Compare each Android and Web observation against expectations pinned to the rules and verified outcomes. Reuse `atf-test-kit` and `atf-reporting`, already used by J7, for suites and evidence. [ATF libraries](../../lib/README.md)

</details>

| Level | Check and Verdict |
|---|---|
| JVM component | Use the same contract for positions, legal moves, promotion, outcomes, saving, and rejecting a stale revision. Exercise storage errors, a corrupted checkpoint, a worker reply after navigation, and progress awarded once. |
| Contract on the Web artifact | Run the fixtures through the worker in the generated bundle. Compare result codes and state after each command against the JVM expectations. Run the same check offline after loading. |
| Layout components | Share the matrix of dimensions, rules, and accessible names. Each adapter's local check verifies bounds and behavior. Include touch, keyboard, large fonts, rotation, long explanations, and cases without a line. |
| E2E on APK and browser | Reuse the product journey with platform drivers on the signed APK and the real Web build. The first pair checks opening and closing Theory, then replaying and reopening **Why?**. Extend it with saving and resuming after an app restart or browser reload, and a Web check on a phone. |

<details>
<summary>Reference fixtures and browser verdict</summary>

The first controls are `1.e4 c5 2.c3 h5`, returning from history to the Alapin line, and `1.f3 e5 2.g4 Qh4#`, with both lines starting from the same Game Review position. Record the exact ply, position, explanation, legal actions, and resumed state. The remaining parity matrix covers AI games, puzzles by chapter, lessons, history, imports accepted by the product, preferences, and data recovery. A Node or HTTP result can complete the contract check. The browser verdict requires a click, touch, or keyboard action through the driver and an observable outcome. [Theory](../board/openings.md#opening-theory-hints-on-the-analysis-board) · [Extent reporting](../testing/README.md#extent-reporting) · [Playwright emulation](https://playwright.dev/docs/emulation)

</details>

**Closure criterion.** The APK and Web pair runs the same cases and attaches the case ID, expected result, observations per adapter, commit, artifact identity, language, geometry, and screenshots in Extent. If a platform is missing, its case stays unqualified. Reuse the signed Android check for native thresholds and exercise at least one desktop and one mobile browser profile.

## WEB-04 · Qualification and Publication

**P2 · Release qualification.** In RAG, tie the commit, catalogs, and APK and Web hashes to receipts from the same release. [Update criterion](../testing/README.md#updating-the-web-bundle)

<details>
<summary>RAG routing by affected file</summary>

Add an explicit RAG route for game geometry and analysis when a diff touches
[BoardPanel.java](../../android/app/src/main/java/ro/atf/chess/android/BoardPanel.java),
[study-board.mjs](../../web/src/main/web/study-board.mjs), or [match-inline-analysis.mjs](../../web/src/main/web/match-inline-analysis.mjs).
For each file, a routing test should require a citation to [MatchAnalysisParityTest](../../atf-tests/src/test/java/ro/atf/chess/MatchAnalysisParityTest.java)
and to the UI evidence for compact RO/EN sanity at 130% on the signed APK and the
MCP browser check on the served Web bundle. [Analysis checks](../testing/README.md#analysis-in-the-ai-game)

</details>

**Closure criterion.** Start the check from **Play in your browser**, below
the APK in the README, and confirm the manifest of the served bundle. On
upgrade, verify the game library, an archived review, and the resumed ply,
including after a reload and in an already open tab.
[Web upgrade](../../web/README.md#explanations-and-upgrade-progress) ·
[Release process](../release/README.md#qualification-and-publication)

## WEB-05 · Menus and Anchored Options

**P2 · Rendered verification pending.** Components verify the home ending at
Name and Settings, removal of duplicate AI controls, and archive recovery at
its own route. The shared menu controller covers placement and focus.
[Menu contract](../../web/README.md#main-menu-and-pieces) · [Component checks](../testing/README.md#menu-and-hint-parity)

Use MCP to verify Puzzles, Analysis board and Game Review, including Why, at
360 × 640 and 1280 × 900, with RO/EN text at 130%. Open beside the pressed
trigger, reach the last option, dismiss and check focus. The archived Web asset
checks establish served-byte integrity. The rendered parity verdict stays pending
until this MCP journey has its own receipt and captures.
