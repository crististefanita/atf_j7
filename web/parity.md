# APK–Web parity · Chess Lab

## Overview

**Play in your browser** sits below the APK download link in the main README. The Web base URL opens the main menu; a direct route opens its requested screen. Progress stays local to the device. [Main README](../README.md#play)

## Contracts and checks

Use one component case for the initial state, actions, and observations. Match the same outcomes before comparing rendered interfaces.

- **Shared owner:** `chess-core` supplies the engine and catalog. The Android adapter writes to preferences; the Web adapter sends the same commands to the worker and stores its latest confirmed checkpoint.
- **Component checks:** Reject stale commands and test storage write failures, upgrade progress, and saved-game compatibility with controlled inputs.
- **UI sanity:** Keep one minimal Android or Web flow when the contract needs rendering, accessibility, or a real lifecycle event. [Test strategy](../docs/testing/README.md)

| Feature | Shared contract | Evidence |
|---|---|---|
| Main menu | APK and the Web base URL open **Chess Lab**, with **Games**, **Study**, and **Profile** in the same order. | RO/EN labels and groups; navigation with mocked history and storage. [Menu checks](README.md#main-menu-and-pieces) |
| Pieces and colors | Matches and Game Review share the Staunton outlines from `ChessPieceArtwork` and `BoardView` colors. | Rendering component and one move on the Web board. [Piece checks](README.md#main-menu-and-pieces) |
| Local match | Shared engine; both colors can move, and the saved checkpoint reopens. | Castling, en passant, stalemate, promotion, draws; move, restart, resume, and move again. |
| AI match | White, Black, or Random; Basic, Advanced, or Hard. Clear **Let AI play its moves** to control both sides in the same match. | Shared checks for switching, reopening, cancellation, reply revision, and Elo eligibility. [AI reply control](README.md#ai-reply-control) |
| Analysis in an AI match | Setup enables the left evaluation bar and history lightbulb from the starting position; the checkpoint saves the choice. | JVM and worker compare the same ply. Real RO/EN checks verify the largest usable square, fixed board, visible controls, and replay from 1/2 with reopening at 2/2. [Contract](README.md#analysis-in-an-ai-match) · [Checks](../docs/testing/README.md#analysis-in-the-ai-game) |
| Board orientation | **Flip board** persists in an active slot. In an archive preview the change is temporary. | Compare position and cursor before and after flipping, then orientation after reopening. [Checks](../docs/testing/README.md#board-orientation-in-a-game) |
| Source-sized replay | Calculated and Mating lines capture the source side at opening; steps and branches retain it. Web uses fixed rows and an internal explanation scroller; APK passes measured geometry to `ReplayBoardLayout`. | Components and MCP verify source equality, no overlap and fixed controls at three profiles. Native build and layout components leave the Android visual check separate. [Contract](../docs/board/README.md#source-sized-replay) |
| Web board size | Analysis board supplies one adaptive sizing limit for Puzzles, matches and Openings. Rails share the same width; extra text does not shrink the square again. | Compare each screen with Analysis at the same viewport and text size, then check history, replay and access to controls. [Sizing](README.md#the-board-in-puzzles-and-matches) |
| History and continuation | Active history can replace the slot; an archived ply in Game Review starts a separate match. | Continue from Game Review on Android and Web. |
| Saved and finished matches | `MatchStore` and `MatchArchive` keep ten entries each. ID and checkpoint bind analysis and resume position; eviction removes that entry's data. | Recover both lists after a bundle update. |
| Game Review | A fixed board, graph, and list share the selected position. Browser checks cover graph value, row selection, and focus after **Why?**. | Confirm actual NVDA speech in RO/EN separately. [Web check](README.md#screen-readers-in-game-review) |
| Puzzles and training | Six chapters share the catalog, hints, scoring, and resumption. `QuestPayoff` supplies tactical replay and the verified threat reason. | Components and browser recover Tactics, Tactical training, and In-between move progress. [Upgrade checks](README.md#explanations-and-upgrade-progress) |
| AI match menu | `MatchControls` defines the same actions, draw and resignation outcomes, and archive policy. | Worker checks declined offers and rejects history or stale commands. Browser checks analysis, confirmation, and result retention on reload. [Match options](README.md#ai-match-options) |
| Openings | `OpeningLesson` shares the board and control geometry on compact and landscape profiles at 1.3× text scale. | Reveal the second hint level and verify the board stays fixed. [Profiles](../docs/testing/README.md#openings) |
| Analysis board | `AnalysisEntry` binds source, ply, and orientation for both active and archived matches. Back restores the source; study keeps its own bookmark. | Shared JVM/worker cases; mock navigation, storage, and focus restoration. [Contract check](../docs/testing/README.md#analysis-board-from-a-game) |
| Profile, language, and presentation | Local profile preserves the match name and RO/EN preference. | Verify larger text, captured pieces, material advantage, keyboard, and native accessibility equivalents. |

The game guides own the product rules and platform behavior. [Matches, puzzles, and analysis](../docs/board/README.md) · [Game Review](../docs/board/game-review.md)

## Publishing the link

1. Build and qualify the bundle in TMP. Keep the exact static tree reachable from an immutable commit or tag; use a separate worktree for the staging files.
2. Preserve previously published `runtime/<SHA-256>/` directories byte for byte and the legacy root URLs used by older open tabs. Include the new runtime directory, `index.html`, `.nojekyll` and the generated `.gitattributes` in the same commit. The `* -text` rule prevents Git from changing qualified bytes; compare staged blobs with the measured bundle before publication.
3. Dispatch the [Pages workflow](../.github/workflows/chess-pages.yml) on `main` with the qualified `site_commit` and `manifest_sha256`. Pages deploys through Actions; the documentation branch `main_RO` is never the source of a deployment.
    Use the exact site SHA for `site_commit`. Compute `manifest_sha256` from the raw Git tree listing. In a POSIX shell run:

    ```bash
    git ls-tree -r --full-tree "$SITE_COMMIT" | sha256sum
    ```

4. Open **Play in your browser**. Verify the declared versioned files and reload with the cache intact. Check checkpoint resumption and offline play after the first load; an open tab may keep the previous runtime until it reloads.
5. Record the source revision, deployed static commit, runtime hash, and Extent receipts. Verify the served bytes independently of the browser cache.

[Bundle structure](README.md#web-bundle-identity) ·
[Test strategy](../docs/testing/README.md#updating-the-web-bundle) ·
[Release process](../docs/release/README.md)
