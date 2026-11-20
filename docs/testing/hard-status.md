# Measured Native Hard status

This offline snapshot belongs to source **`85b1c478575d3c31065161c55aa02cffcd0e7eee`**. Engine code, policies, curriculum and displayed app ratings are unchanged. Baseline calibration is independent of candidate-promotion gates: an incomplete puzzle catalog does not prevent measuring the existing player, and a rating estimate does not qualify a candidate.

## Puzzle results

| Chapter | First move accepted | Complete canonical exercise | Accepted stages |
|---|---|---|---|
| mate-in-one | 500/500 | 500/500 | 500/500 |
| mate-in-two | 300/300 | 300/300 | 600/600 |
| mate-in-three | 200/200 | 200/200 | 600/600 |
| tactics | 93/100 | 85/100 | 201/221 |
| training | 94/114 | 78/114 | 274/330 |
| intermezzo | 24/24 | 22/24 | 49/52 |

Totals are **1,211/1,238** first moves, **1,185/1,238** complete canonical exercises and **2,224/2,303** stages. **53 exercises remain incomplete**; the existing all-solved goal remains FAIL. Native search receives only the position and its playing profile; the key is read after its receipt. Reference-path interventions are explicit and cannot become autonomous solutions.

All **1,000 mating exercises** also pass against every legal defense: **2,236 native decisions**, **1,236 defensive branches** and **1,500 terminal mating branches**, within the original goal. The declared-objective solver was not used. Material exercises are weaker: **185/238 complete, 77.7%**, with **524/603** accepted stages. The easy-mate majority cannot conceal that gap behind the 95.7% global score.

Against the identical preserved catalog at `ecf5291a71e49e02b846365b529dc070b4e3057a`, one choice changes with **zero gained or lost stages**. This is not a new catalog-accuracy gain. Cold Stockfish H17/H19 grades every one of the 79 unaccepted stages: **56 stable large losses**, **23 near-oracle alternatives**, and no stable large loss for the curriculum keys. **42** are first reachable large native losses without a reference intervention. Of those, 28 retain H5, nine H6, one H4 and four H7; only five consume the effective maximum allowance. Isolate score, horizon, leader confirmation and root coverage before attributing every miss to a node ceiling.

The primary teacher queries match the actual native initial quiet counter of zero while preserving the board, rights, en passant, complete history and fullmove. A separate untouched original-FEN control changes 11 numeric WDL losses and zero classifications. This assessment alignment does not repair the existing composed-FEN quiet-clock limitation.

## Relative Elo snapshot

The **72 games** use **12 opening pairs / 24 games per profile**, with colors reversed. The anchor is **Stockfish 19, UCI_Elo 1500, 6,000 requested nodes per move, Threads 1, Hash 16 MiB**. These are the project's same 12 frozen starts, not new independent families. A game still active at 220 plies remains unfinished.

| Profile | Local estimate | Paired 95% interval | W/D/L/Unfinished | Unfinished all-loss…all-win sensitivity |
|---|---|---|---|---|
| Basic | 1126 | −∞…1309 | 2/0/21/1 | 1083…1162 |
| Advanced | 1456 | 1126…1711 | 7/1/10/6 | 1363…1544 |
| Hard | 1604 | 1380…1917 | 12/0/5/7 | 1500…1732 |

Only the midpoint gives unfinished games half a point; they are never recorded as draws. The bootstrap uses 10,000 samples of whole opening pairs. Twelve pairs leave substantial uncertainty, and extreme scores may have no finite estimate. This is performance relative to the configured protocol, **not FIDE Elo or a measured candidate ΔElo**. Do not add repeated starts to the old 600-game sample as independent new observations. [Stockfish rating context](https://official-stockfish.github.io/docs/stockfish-wiki/Stockfish-FAQ.html#the-elo-rating-of-stockfish).

Each actual move retains pre-move shadow Hard and a maximum public hint attempt; Basic/Advanced still play their own decisions. The study retains **one unavailable hint** and **one incomplete final review**. Typed failures have no invented final coaching score or line and have a separate quality verdict. The recovered game preserves its original durable prefix; the technical Stockfish session reset retains full history and identical options with both transcripts pinned. Those local review ratings do not supply the measured Elo. The league's full hint and review continuations are not all independently H17/H19-certified, so this rating bundle does not replace the admitted learning corpus.

## First confirmed losses in the retained league

The five actual Hard defeats from the same 72-game calibration were inspected chronologically. Cold unrestricted Stockfish H17/H19 compares every earlier actual Hard move until the first loss reaches 150‰ at both horizons: 30 decisions and 120 raw phases. This is diagnosis of the existing games, not five new independent test families.

| Game | Actual first loss | Better free H19 reply | WDL loss H17/H19 |
|---|---|---|---|
| Alekhine, Black | 11…Bxf2+ | …O-O | 997.5/996.5‰ |
| King's Gambit, Black | 9…Bb4+ | …Bg7 | 300/329‰ |
| Nimzo, White | 11.Qb3 | Na4 | 250.5/244.5‰ |
| Petroff, White | 7.h3 | Nbd2 | 206/236.5‰ |
| Slav, Black | 9…Nxc3 | …Nb4 | 284.5/230.5‰ |

All five retained decisions stop at H5. Four PVs end with a queen capture and an omitted legal recapture, sometimes a check evasion; Petroff ends with a pawn capture and has a different mechanism. Isolated full-window H4/H5/H6 comparisons do not prove a playable full decision: earlier iterations and all root alternatives still consume the allowance. The Alekhine native final review reports 99% Black accuracy despite the independently confirmed first loss; that local evaluator is not an external strength reference.

General root null-window probing preserves exact minimax scores but fails three existing French/Queen's Gambit controls and changes none of the five choices. A scoped H4/H6 confirmation also changes none. Rejecting an exposed-queen H5 frontier improves Alekhine and Slav, but choosing the older H4 line worsens Nimzo to **357.5/383.5‰**. Its full catalog replay gains eight accepted stages and loses sixteen, with 82 changed analyses among 2,303. All 1,700 canonical mating stages remain identical; all-defenses mate transfer was not rerun for this rejected candidate. The engine and policies in live remain unchanged, and no candidate duel or ΔElo is justified.

[HardDefeatWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/HardDefeatWitnessTest.java) retains the five legal histories, original native work and cold teacher receipts, with wrong-history/run/score negatives. It does not require the live player to repeat a bad move. [HardDefeatBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/HardDefeatBenchmarkTest.java) exposes five separate quality goals; all remain FAIL on the reference and are kept out of the ordinary component suite. An unmeasured new move requires a fresh oracle phase, never an assumed improvement. [Portable fixture](../../atf-tests/src/test/resources/hard-defeat-witnesses.yaml).

The auditable external dossier is `C:\work\sah\.studii\experiments\2026-11-20-hard-losses\README.md`. Next require a completed free-reply comparison that removes the exposed-queen optimism without falling back to a worse H4 move; preserve the French/QG controls and every previously accepted stage before excluded-family transfer and a paired duel. More passing plumbing assertions do not establish stronger play.

## Evidence and next study

The external dossier is `C:\work\sah\.studii\experiments\2026-11-19-puzzle-status\README.md`. It links eight Extent reports, exact source/runtime/configuration pins, raw decisions, all PGNs, teacher transcripts, final reviews and 42 contextual YAML lessons. Lessons are study-only: counterexamples and excluded-family transfer are still pending, and no key is imported into runtime. The execution amendment preserves all completed results when increasing independent concurrency; interrupted attempts remain archived and unscored.

Use the [native puzzle protocol](hard-puzzle-study.md), [canonical benchmark](../../atf-tests/src/test/java/ro/atf/chess/HardPuzzleStudyBenchmarkTest.java) and [all-defenses mate benchmark](../../atf-tests/src/test/java/ro/atf/chess/NativeMateDefenseBenchmarkTest.java). [AiCalibrationObservationTest](../../atf-tests/src/test/java/ro/atf/chess/AiCalibrationObservationTest.java) preserves the mocked unavailable-advice contract without freezing an engine defect or changing learning-corpus admission. Next isolate one of the 42 real losses, such as **tactic-040: Qc1 versus dxe6**, H5 at 48,808 additional nodes and 1000/1000‰ loss. Compare free replies at the same horizon, preserve every previously accepted stage and all 1,000 mates, then test counterexamples and untouched families before a direct candidate duel. [Open work](../feedback/README.md).
