# Web · Parity with the Chess Lab APK

## Overview

The register tracks the remaining differences between APK and Web.
[Engine and adapters](../../web/README.md#shared-engine-and-platform-adapters) ·
[APK–Web matrix](../../web/parity.md)

## What We Reuse from J4

Card Table Browser 0.2.0 and its test JAR provide card table contracts.
Extracting a game-independent utility into shared J4/Chess code makes sense when
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

**P2 · Use compact landscape width more effectively.** At 740 × 420 dp and
font 1.3, the mating source and replay both measure 285 native pixels at
320 dpi (142.5 dp). Navigation fits without page scrolling, but the vertical
stack leaves substantial unused width. Explore a shared landscape arrangement
with explanation beside the board, keeping source/replay sizing aligned and
48 dp controls. Require RO/EN component bounds and native captures before
changing the current no-jump contract.
[Puzzle explanation controls](../../atf-tests/src/test/java/ro/atf/chess/PuzzleExplainLayoutTest.java)

**P2 · Native menu run identity.** Propagate one generated run ID through the Saved/Finished menu instrumentation, execution receipts and Extent. The current focused observations passed with the qualified source and APK, but lack this binding and therefore remain outside strict supplemental qualification. Add negative receipt cases for missing and substituted run IDs before admitting them to that gate.

**P2 · Legacy analysis history oracles.** `awaitHiddenAnalysis` still checks
one history child per ply. Align `analysis-prepare/restore` and their compact
variants with the shared single-move game cursor. Preserve the Analysis
board's full history list and its separate oracle. Keep completed hint/score
readiness and the displayed position as independent checks; a timeout must
not stand in for a wrong UI oracle.
[Current cursor](../../chess-core/src/main/java/ro/atf/chess/MatchHistorySelection.java)

**P2 · Native compact accessibility readiness.** The legacy compact replay stops at a visible Game Review command because its accessibility-node snapshot reports it unavailable. Retain that failed observation; refresh and verify the actual node before any click, without increasing the timeout. Game Review at 360 × 640 dp and 1.3× text, and the separate RO/EN rotation probe, passed on the same signed APK. These checks do not close the legacy helper's failure. Web Openings at 740 × 420 still needs about 37 CSS pixels of document scrolling; keep it in the compact-landscape work above.

[Shared policy, callbacks and rendered checks](../testing/README.md#shared-screen-chrome) own the implemented header/navigation contract; keep only unmet geometry, lifecycle and input criteria here.

## WEB-03 · Component and E2E Tests for the Shared Contract

**P2 · Paired adapter evidence.** Shared chrome and the single-move cursor already have common component cases. The gap is a reusable rendered receipt for the same source position, variant, cursor and Back sequence on APK and Web. The retained older Web engine must not be presented as parity with a newer native engine.

- Use the opening-history and mating-review cases from the [shared testing contract](../testing/README.md#shared-screen-chrome).
- Keep source/artifact identities, RO/EN profile, expected state and observed return per adapter. Worker and geometry checks cannot replace missing native or MCP rendering.
- Close only after the paired receipts show the same line and restored state, including reload/restart, on the qualified APK and served Web bundle.

## WEB-04 · Qualification and Publication

**P2 · Release qualification.** In RAG, tie the commit, catalogs, and APK and Web hashes to receipts from the same release. [Update criterion](../testing/README.md#updating-the-web-bundle)

**Closure criterion.** Start the check from **Play in your browser**, below
the APK in the README, and confirm the manifest of the served bundle. On
upgrade, verify the game library, an archived review, and the resumed ply,
including after a reload and in an already open tab.
[Web upgrade](../../web/README.md#explanations-and-upgrade-progress) ·
[Release process](../release/README.md#qualification-and-publication)

## WEB-05 · Menus and Anchored Options

**P2 · Focus, large text and remaining contexts.** The published compact Analysis board has an observed anchored menu and unchanged board without document scrolling. This does not complete the RO/EN 130% matrix for Puzzles, Game Review and Why.

Verify the last option, keyboard dismissal, focus return and the preserved source/cursor in those contexts at compact and desktop sizes. Retain a driver receipt and captures for each missing profile; no full rendered-parity claim follows from the existing component verdict. [Observed compact baseline](https://github.com/crististefanita/chess/blob/b51e17e7752cc0c0a3ec7be9500a362f4d3b20d2/release/0.16.1/reports/web-rendered-observation.json) · [Menu contract](../../web/README.md#main-menu-and-pieces)
