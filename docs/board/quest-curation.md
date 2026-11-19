# Reference points for composing mate puzzles

## Overview

- **Scope.** The YAML catalogs hold stable puzzle IDs, legal starting positions, and verified solutions. The source rating measures player performance. Local difficulty scores describe the position.
- **Checks.** `QuestCatalogTest` checks puzzle data, `QuestPayoffTest` checks the result of the line, and `QuestProgressTest` checks saved progress. Examples use standard algebraic notation (SAN).

- **Discovered mate and an unusual move.** Lichess position P9xLh, observed at rating 2184, has five rook moves that deliver discovered mate. The idea is worth studying. For a Chess Lab composition, keep a single winning retreat and check every alternative retreat. [P9xLh](https://lichess.org/training/mateIn1/P9xLh)
- **Misleading checks.** In 0jbFz, observed at rating 2370 through the API, there are 18 legal moves, eight checks with a defense, and one solution, `Rxb6#`. When composing, make the natural retreats leave a concrete defense and make the key remove that specific defense. [0jbFz](https://lichess.org/training/mateIn1/0jbFz)
- **Mate from a position in check.** In 16meG, observed at rating 2075 through the API, White is in check and has only three legal moves. Two king moves escape the attack, and `Nc2#` blocks the check while opening the mating line. In Chess Lab, `mate-101` practices interposition, and `mate-102` compares two possible captures of the checking piece. [16meG](https://lichess.org/training/mateIn1/16meG) · [Chess Lab countermates](README.md#rules-and-puzzles)
- **Construction.** Start from a distinct idea, such as discovered mate, blocking the last escape square, or promotion to knight. If there is a second solution, change the position before publishing it. Keep a concrete defense for each of the most tempting tries. [WFCC Codex](https://www.wfcc.ch/rules/codex/) · [Lichess themes](https://lichess.org/training/themes)

- For a new position, validate the FEN, enumerate all moves in both engines, and require exactly one mate. Give the position its own ID, independent of rank. Lichess ratings reflect players' actual attempts, and the local score uses position features. [Lichess methodology and format](https://database.lichess.org/)

## Configuring the chapters

| Chapter | What to edit |
|---|---|
| Mate in 1 | ID, FEN, UCI solution, and theme in [quests.yaml](../../chess-core/src/main/resources/quests.yaml). Configure the introduction, weights, and tiebreakers in [quest-difficulty.yaml](../../chess-core/src/main/resources/quest-difficulty.yaml). |
| Mate in 2 | FEN, UCI line, and rating in [mate-in-two.yaml](../../chess-core/src/main/resources/mate-in-two.yaml). Keep ratings in ascending order. For equal ratings, list order breaks the tie. |
| Mate in 3 | The same format, with five plies, in [mate-in-three.yaml](../../chess-core/src/main/resources/mate-in-three.yaml). Position `mate3-0Ytul` replaces the similar exercise `mate3-02GAF` at the same rating. |
| Tactics | FEN, UCI solution, and `difficulty` in [tactics.yaml](../../chess-core/src/main/resources/tactics.yaml). Use distinct positive integers. The current selection starts with direct captures and progresses to moves without an immediate capture. |
| Tactical training | FEN, key, and order in [training.yaml](../../chess-core/src/main/resources/training.yaml). Preserve IDs and the catalog checksum. |
| In-between move | FEN, key, and rating in [intermezzo.yaml](../../chess-core/src/main/resources/intermezzo.yaml). Ratings define ascending order. |

- For Mate in 1, higher weights place positions with the corresponding feature later. A tiebreaker from 1 to 9 applies only when the base score is the same. Remaining ties follow the ID, and the score in the ordering file increases strictly. The generator rejects a position with multiple solutions. Run from the TMP checkout with JDK 21 and Maven. For independent verification, set `$chessJs` to the CommonJS file from chess.js 1.4.0. Add `-Offline` when dependencies are already cached, or `-MavenRepo` when using another Maven repository.

```powershell
./scripts/update-mate-one-order.ps1 -ChessJs $chessJs
./scripts/update-mate-one-order.ps1 -Write -ChessJs $chessJs
```

- The first command compares the existing order with the calculated order. The second regenerates the order after changing the weights. After a reviewed change to positions or solutions, add `-RefreshOracle` to the second command, then run the catalog suites. Chess.js recomputes the proof from the positions exported by the project's YAML parser.

- Keep the ID when reordering. For a replacement, put the old ID in the retirement registry and assign a new ID to the position. Verification compares all six chapters. It normalizes side and allowed reflections, including rotations of a board without pawns or castling rights. For similar positions, it compares the move type, destination, opposing king's square, and up to ten differing squares. The 31 new Mate in 1 positions come from offline calibration matches. YAML comments identify the match, ply, and any color swap or reflection. [ID registry](../../chess-core/src/main/resources/quest-retired.yaml) · [Verification commands](../testing/README.md#puzzle-progress)

## Difficulty calibration

- Release 0.10.0 adds 786 positions from the Lichess CC0 export of November 9, 2026. Selection split the candidates into five rating bands for each color and chapter. It required popularity of at least 75, at least 180 attempts, and a rating deviation of at most 100. The playable FEN is obtained after the first move in the source. The compressed source has SHA-256 `7b0d235fa40cfd7fdb7df1fec56a96b3b6ee1496b32ed2eb05be90d0ebf94253`. The IDs and solutions of the 314 positions from the previous release were preserved. [Export and license](https://database.lichess.org/#puzzles)

- The chapters contain 500, 300, 200, and 100 positions, with equal proportions of White and Black in each. Mate in 1 follows the structural score. Mate in 2 and Mate in 3 follow the source rating. Tactics retains its 12 introductory positions and puts the new positions in rating order. For tactics, selection verified a unique move that gains at least two pawns after three plies. YAML comments link the new Mate in 1 and Tactics puzzles to their source IDs.

- For calibration with players, alternate the order of a sample from the beginning, middle, and end of the chapter. Include `mate-101` and `mate-102`, which have only six and five legal moves respectively but start in check. Include `mate-129`, which has 14 misleading checks, six with a single defense. Measure time and wrong attempts before the first hint. The local score remains an estimate pending that calibration.

## Mate in two moves

- The first 100 positions came from the `lczerolens/lichess-puzzles-mate-in-2` subset. The 200 additions come directly from the CC0 export. The ID `mate2-<PuzzleId>` links to the puzzle at `https://lichess.org/training/<PuzzleId>`. The displayed position is the source FEN after the opponent's first move. [Lichess format and license](https://database.lichess.org/#puzzles) · [Initial subset](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/viewer)

- The selection includes 150 positions for each color. Verification rejects immediate mate, an alternative key, and any defense without a mating continuation. The catalog stores the reference line, and the independent proof covers all defenses and all mates in each branch.

- For a working selection, save the `rows-<offset>.json` pages in TMP with 100 rows per page. Keep the published IDs when integrating the verified positions. [Initial subset revision](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-2/tree/8c195a0696c30c8371d276db0467e833f1b72468)

```powershell
node scripts/verify-mate-in-two.cjs <cale-catre-chess.js> --import <pagini-TMP> <catalog-TMP.yaml>
```

## Mate in three moves

- The 200 positions come from the Lichess CC0 database. The selection has 100 positions with White to move and 100 with Black to move, ordered by imported rating. The ID `mate3-<PuzzleId>` points to the original Lichess puzzle. [Lichess database](https://database.lichess.org/#puzzles) · [Initial subset revision](https://huggingface.co/datasets/lczerolens/lichess-puzzles-mate-in-3/tree/680d12e5e6447800d5cbe2b2321d4cffd6a024d1)

- Both engines verify a single first move that forces mate in three, with no shorter solution. For each first defense, they verify all moves that force mate in the next two moves. The line played in the app also has a unique second move.

## Tactical Training and verification set

- At stage 6 of `training-069`, `Qxb2` completes the exercise by immediately capturing the rook. The +5 units describe the position immediately after the capture. Black still has the reply `…Qxd1+`. In the study line `Rd8+ Kg7 Qxb2 Qxc4 Rd2`, the check postpones the gain until the next move. Tap the message after `Rd8+` for the explanation and replay the line on the board. The lightbulb also opens the exercise replay. The position and points are retained during study.

- [Final-move studies](../../chess-core/src/main/resources/quest-finish-studies.yaml) define the position, key, and continuation in UCI. The consumer verifies the stage, key, and position before showing the explanation. The study line remains distinct from the credited catalog line.

- The **Tactical training** chapter contains 114 positions without a mating objective, ordered by source rating. The positions cover defense, deflection, quiet moves, sacrifices, forks, and pins. IDs `training-049`–`training-072` cover the 1200–1599 and 1600–1849 bands. IDs `training-073`–`training-096` add one position for each theme and color in the 1800–2199 and 2200–2699 bands. Four rook exercises, two for each side, have source ratings from 1393 to 1794. The next set adds four lessons with source ratings from 1198 to 1766, two positions for each color. The IDs and relative order of existing exercises retain saved progress. [Catalog](../../chess-core/src/main/resources/training.yaml) · [Lichess CC0 export](https://database.lichess.org/#puzzles)

- Stockfish 19 at depth 15 chose the key in all 24 new positions and in 24 separate positions from the same bands and themes. The Chess Lab engine and chess.js 1.4.0 verify the key's uniqueness for a forced material gain in three plies. `QuestCatalogTest` verifies uniqueness across chapters and against the separate set. `TacticsCatalogTest` compares proofs with chess.js fingerprints, and `QuestStoreTest` checks resume after interleaving exercises. The separate set remains in test resources and can be used for engine transfer. [Independent set](../../atf-tests/src/test/resources/tactical-transfer-holdout.yaml) · [Local checks](../testing/README.md#puzzle-progress)

| Exercise | Lesson | Minimum gain after the immediate reply at the end |
|---|---|---:|
| training-101 · [017AB](https://lichess.org/training/017AB) | Sacrifice the rook with check on c5 to promote the pawn on b2. | 300 cp |
| training-102 · [00JYV](https://lichess.org/training/00JYV) | Before recapturing on c3, take the bishop on e7 with check. | 340 cp |
| training-103 · [00IMS](https://lichess.org/training/00IMS) | Open d5 for the bishop's check after the exchange on f5. | 220 cp |
| training-104 · [0047P](https://lichess.org/training/0047P) | Use rook checks to attack the king and the rook on a2. | 500 cp |

- Stockfish 19 confirms the keys at depths 16 and 19. The balance in the table is the material remaining after the playable line and the most costly immediate reply. At depth 19, Stockfish chooses the published lines through to the gain. In 00IMS at depth 16, it prefers another defense with the same key.

- Four new endgames use the reply from Stockfish 19's line at depth 19. The keys match at depths 16 and 19. Existing positions and their relative order were preserved.

| Exercise | Lesson | Minimum gain at the end of the line |
|---|---|---:|
| training-105 · [023eI](https://lichess.org/training/023eI) | The queen captures with check and also attacks the piece on d6. | 430 cp |
| training-106 · [02WXt](https://lichess.org/training/02WXt) | Pawn exchanges open the rook's line to d3. | 400 cp |
| training-107 · [00xxi](https://lichess.org/training/00xxi) | The king's entry enables promotion and wins the rook after the exchange. | 400 cp |
| training-108 · [02oHj](https://lichess.org/training/02oHj) | Pushing the pawn to c2 enables promotion after the king captures on d3. | 300 cp |

- The four endgames below retain the replies from Stockfish 19's depth-19 line through to the gain. The keys match at depths 16 and 19.

| Exercise | Lesson | Minimum gain at the end of the line |
|---|---|---:|
| training-109 · [01Jpm](https://lichess.org/training/01Jpm) | Advance with check and promote before the opposing pawn. | 470 cp |
| training-110 · [00PHg](https://lichess.org/training/00PHg) | The rook's entry with check prepares promotion and recapture. | 200 cp |
| training-111 · [00gS7](https://lichess.org/training/00gS7) | Push the pawn to limit replies and win the bishop. | 230 cp |
| training-112 · [01e8j](https://lichess.org/training/01e8j) | The king captures on e4, then f5 preserves opposition and pawn progress. | 400 cp |

## Intermediate Move

- The **In-between move** chapter has 24 positions in three rating tiers: introduction (1150–1549), consolidation (1550–1849), and advanced (1850–2400), with four positions for each color in each tier. The key checks or captures before recapture and must keep the material gain required by the catalog. [Catalog](../../chess-core/src/main/resources/intermezzo.yaml)

- This chapter's progress and total are independent of **Tactics** and **Tactical training**. Before extending the YAML, check the current ID, idempotent completion, and separate total in the component test.

## Gain and lesson in non-mate puzzles

- A good exercise connects the move to a choice the player can recognize in another match. Start with the relationship between the pieces, identify the reply that tests the idea, and stop the line at the first visible, defended gain. In In-between move, retain the last capture from the source match, the move that delays recapture, and the gain obtained by changing the order.

| Evidence | What to verify |
|---|---|
| Provenance | The ID from the Lichess export, the match, and the move that produces the position. The position and first move must match the source. |
| Line | `payoff-line` lists the UCI plies from the `solution` key to the player's last move. Replay the line on the board and verify every legal reply at the end. |
| Lesson | `lesson` names the idea demonstrated by the line. For a deflection, identify which defender moves and what becomes capturable. For a fork, follow both targets. |
| Outcome | Measure material from the start of the line, including lost and gained pieces. Verify separately whether the opponent can immediately recover the gain. |

- One move can complete an exercise with an immediate gain. For a combination, retain the sequence of key, defense, and gain. The explanation starts at the final board and lets the player return to each move. Difficulty grows when the reply changes the natural move order, when a gain requires a non-capturing move, or when the same idea must be recognized with the other color.

- The unique key verified at three plies remains a local material check. For longer positions, also compare Stockfish's preferred reply. A positive score can indicate attack or initiative even if material decreases. For example, in `training-001`, after `Rxh6+ gxh6 Qxh6+ Kg8 Rh1`, White has an attack and the line's material balance remains negative. Verify a continuation that shows a concrete gain before labeling the line as a material-gain exercise.

- In `intermezzo-020`, after `…Nxe4`, White can recapture the knight immediately. The lesson is to play `Bxe7` first, attacking the queen, then `Nxe4` after `…Qxe7`. In `intermezzo-024`, Black first threatens promotion with `…cxb2`. After `Rb8`, Black recaptures the rook with `…Nxa4`. In both examples, changing the order produces an observable gain.

- In [0Nyzz](https://lichess.org/training/0Nyzz), `…f5` opens the e7–h4 diagonal, and the bishop attacks the queen. If White plays `Qxe7`, the black queen recaptures on e7. The player can see why a move without a capture may gain more than an immediate capture.

- During review, keep the ID when the position and key stay the same. For a replaced position, use a new ID and keep the previous ID in the retirement registry. Compare difficulty, color, and lesson in chapter order. [Catalog verification](../testing/README.md#tactics-and-scoring) · [Lichess CC0 export](https://database.lichess.org/#puzzles)

| Chapter | Verified playable continuation | Verified immediate gain | Defense study in explanation |
|---|---:|---:|---:|
| Tactics | 76 | 17 | 7 |
| Tactical training | 87 | 15 | 10 |
| In-between move | 20 | 4 | 0 |

<details>
<summary>Catalog provenance and replay examples</summary>

Six local introductory exercises were replaced with documented positions. The new ones occupy the same places and preserve the color distribution. For the 17 positions where completion is saved after the key move, **Explanation** opens the defense study. It replays the key, the opponent's reply, and the continuation from the [study catalog](../../chess-core/src/main/resources/quest-defense-studies.yaml).

Of the 183 continuations, 130 come from the published line, 43 from comparison with Stockfish and board verification, four from retained Chess matches, and six from the replacement exercises. For a line obtained with Stockfish, present the reply as a verified line. Proof that a gain survives an immediate reply applies to the position at the end of the line.

| Exercise | Lesson | Gain at the end of the line |
|---|---|---:|
| [training-097](https://lichess.org/training/0Luif) | `Rxb6+` draws the a-pawn. The queen recaptures with check and wins the rook on d8. | +2 |
| [training-098](https://lichess.org/training/0BTVa) | `…Rxg3+` forces the king to retreat. The rook follows with `…Rxg4` and takes the knight. | +4.2 |
| [training-099](https://lichess.org/training/0AZju) | After `…Rxh4+`, the rook draws the queen to g4, where the knight captures it. | +5 |
| [training-100](https://lichess.org/training/02Nl2) | `Rxh6+` clears the queen's way to h7. The rook continues on f6, draws the opposing queen, and the pawn captures it. | +5 |

For each of the 17 studies, the catalog retains the Lichess provenance, UCI line, and specific lesson in Romanian and English. The displayed reply is Stockfish 19's first choice at depth 19 from the position after the key. Running at depth 17 shows where the choice changes. The displayed score is the engine estimate after the key, from the solver's perspective. The displayed material balance is calculated directly from each position using the tactical profile's piece values. At the end, a lower value after a legal reply appears separately.

In `training-001`, `Rxh6+ gxh6 Qxh6+ Kg8 Rh1` opens the h-file for the queen and rook. The line shows −3 pawn units, with a minimum of −4 after a reply, and Stockfish evaluates the position after the key at +7.23. In `tactic-052`, checks push the king to c4 and the rook captures the queen on g4. The material gain from the capture is +9, with a minimum of +4 after a reply. In both cases, use the step where the idea becomes visible when writing the lesson.

</details>
