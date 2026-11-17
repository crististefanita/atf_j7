# APK–Web parity · Chess Lab

**Play in browser** sits immediately below **Download APK** in the **Play** section of the README and opens the main menu. Use the menu to choose a match or study activity. Explicit URLs open the requested screen. Each device keeps its own progress locally. [Main README](../README.md#play)

## Contracts and checks

The same component scenario defines the initial state, actions, and observations. The engine and catalog come from J7. The Android adapter sends actions to the contract and persists state in preferences. The Web adapter sends the same intents to the worker and stores the latest confirmed checkpoint locally. Component checks also cover rejection of a stale command, storage write failures, progress retention after upgrades, and saved-game compatibility. Keep a minimal sanity check on each platform for the flow that depends on UI or lifecycle behavior. [Test strategy](../docs/testing/README.md)

| APK feature | Web status | Evidence required for parity |
|---|---|---|
| Main menu | The APK and the Web base URL open **Chess Lab**. The Matches, Study, and Profile groups preserve the order and labels from the APK resources. | Components compare titles, order, and labels in RO and EN. Navigation uses mocked history and storage. [Menu verification](README.md#main-menu-and-pieces) |
| Pieces and board colors | Matches and Game Review use the Staunton outlines from `ChessPieceArtwork` and the same colors as `BoardView`. | Run the rendering component and a sanity check that plays a move on the Web board. [Piece verification](README.md#main-menu-and-pieces) |
| Local match, rules, and promotion | Shared engine. A local match accepts moves for both sides and reopens its checkpoint. | Fixtures for castling, en passant, stalemate, promotion, and draws. Sanity check with a move, restart, resume, and a new move. |
| AI match | Both adapters accept White, Black, or Random and Basic, Advanced, and Hard levels. The **Let AI play its moves** checkbox enables manual control within the same active match. | Shared checks cover switching control, reopening the slot, canceling a calculation in flight, the reply revision, and eligibility for the Elo estimate. [AI reply control](README.md#ai-reply-control) |
| Analysis in an AI match | The setup option enables the vertical bar to the left of the board and the lightbulb beside history from the starting position. The checkpoint stores this choice. | The JVM and worker compare analysis of the same ply. The RO/EN sanity check verifies the largest square in the usable compact area, a fixed board while using arrows, and a visible bar and lightbulb. It replays the same line from 1/2 and reopens the slot at 2/2. [Contract](README.md#analysis-in-an-ai-match) · [Checks](../docs/testing/README.md#analysis-in-the-ai-game) |
| Board orientation in a match | The options menu offers **Flip board**. Orientation is saved for the active slot and temporary in the archive preview. | Compare the position and cursor before and after flipping, then the orientation after reopening. [Component and UI checks](../docs/testing/README.md#board-orientation-in-a-game) |
| Web board size | Puzzles, matches, and Openings use the same limit based on available window space. The lesson also reserves space for the evaluation bar. | The browser measures the board and control visibility before and after a move or hint. [Sizing and scrolling](README.md#study) |
| History and continuation | The board can overwrite the active slot or start a separate match from the archive. Game Review supports continuation from the selected ply in archived history. | Compare continuation of an active match from Game Review on the APK and Web. |
| Saved and finished matches | Two lists use `MatchStore` and `MatchArchive`, with ten entries each. Each result retains its analysis and resume position through an ID and checkpoint. Eviction removes the entry's data. | Verify saved state recovery after a bundle update. |
| Game Review and explanations | The fixed board, graph, and list use the same selected position. Edge verifies the graph value, selected row, and restored focus after **Why?**. | Confirm NVDA speech in RO/EN. [Web check](README.md#screen-readers-in-game-review) |
| Puzzles and training | The six chapters use the catalog, hints, scoring, and resume behavior from the core. Tactical replay uses `QuestPayoff`, including the explanation for a threat marker. | The component and browser resume prior Web progress in Tactics, Tactical Training, and Intermediate Move. [Explanations and upgrade](README.md#explanations-and-upgrade-progress) |
| AI match menu | `MatchControls` projects the same actions as the APK. Draw and resignation use the same result policy and archive contract. | The worker verifies a declined offer and commands from history or a stale revision. The browser verifies analysis, confirmation, and result retention on reload. [Match options](README.md#ai-match-options) |
| Openings | Lessons use `OpeningLesson`. The board and controls have the same geometry on compact and landscape profiles at 1.3× text scale. | Verify the transition to the second hint level and board retention. [Verified profiles](../docs/testing/README.md#openings) |
| Analysis board | From an active or archived match, `AnalysisEntry` records the source, displayed ply, and orientation. Back reopens the same match, and independent study keeps its bookmark. | The JVM and TeaVM worker run the same cases. Mocks verify navigation, storage, and focus restoration. [Contract check](../docs/testing/README.md#analysis-board-from-a-game) |
| Profile, language, and presentation | The local profile retains the name displayed in the AI match and the RO/EN language. | Verify geometry at increased text scale, captures and material advantage, keyboard operation, and semantic equivalents of native accessibility checks. |

Product details, including continuation rules and platform-dependent behavior, are in the game guides. [Matches, puzzles, and analysis](../docs/board/README.md) · [Game Review](../docs/board/game-review.md)

## Publishing the link

1. Build and verify the bundle in TMP. Prepare `gh-pages` in a separate
   worktree from the published revision.
2. Add the qualified build's `runtime/<SHA-256>/` directory and publish it in
   the same commit as the new `index.html` and `.nojekyll`. Keep the already
   published runtime directories byte-for-byte identical. During migration,
   retain the legacy root URLs for pages opened before versioning.
3. Start at **Play in browser**. Verify the versioned files declared by the
   page and test a normal reload with the cache intact. An already open tab
   may continue on its version until reload. Verify that the match resumes
   and works offline after loading, then link the revision and bundle hash
   to the Extent receipts.

[Bundle structure](README.md#web-bundle-identity) ·
[Test strategy](../docs/testing/README.md#updating-the-web-bundle) ·
[Release process](../docs/release/README.md)
