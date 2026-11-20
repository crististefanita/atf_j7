# Measured Native Hard status

The baseline snapshot belongs to source **`85b1c478575d3c31065161c55aa02cffcd0e7eee`**. The current objective-blind replay includes the bounded queen-reply and outside-pawn corrections below; the curriculum, ordinary 100k→200k allowance and displayed app ratings stay unchanged. The Elo table still belongs to the baseline, not the corrected player. Baseline calibration is independent of candidate-promotion gates: an incomplete puzzle catalog does not prevent measuring the existing player, and a rating estimate does not qualify a candidate.

## Puzzle results

| Chapter | First move accepted | Complete canonical exercise | Accepted stages |
|---|---|---|---|
| mate-in-one | 500/500 | 500/500 | 500/500 |
| mate-in-two | 300/300 | 300/300 | 600/600 |
| mate-in-three | 200/200 | 200/200 | 600/600 |
| tactics | 95/100 | 87/100 | 203/221 |
| training | 95/114 | 79/114 | 275/330 |
| intermezzo | 24/24 | 22/24 | 49/52 |

Totals are **1,214/1,238** first moves, **1,188/1,238** complete canonical exercises and **2,227/2,303** stages, up by three from the original snapshot and one from the qualified queen-reply player, with zero lost stages. **50 exercises remain incomplete**; the existing all-solved goal remains FAIL. Native search receives only the position and its playing profile; the key is read after its receipt. Reference-path interventions are explicit and cannot become autonomous solutions.

All **1,000 mating exercises** also pass against every legal defense: **2,236 native decisions**, **1,236 defensive branches** and **1,500 terminal mating branches**, within the original goal. The declared-objective solver was not used. Material exercises are weaker: **188/238 complete, 79.0%**, with **527/603** accepted stages. The easy-mate majority cannot conceal that gap behind the 96.0% global score.

Before the queen-reply correction, against the identical preserved catalog at `ecf5291a71e49e02b846365b529dc070b4e3057a`, one choice changes with **zero gained or lost stages**. This is not a new catalog-accuracy gain. Cold Stockfish H17/H19 grades every one of the 79 unaccepted stages: **56 stable large losses**, **23 near-oracle alternatives**, and no stable large loss for the curriculum keys. **42** are first reachable large native losses without a reference intervention. Of those, 28 retain H5, nine H6, one H4 and four H7; only five consume the effective maximum allowance. Isolate score, horizon, leader confirmation and root coverage before attributing every miss to a node ceiling.

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

General root null-window probing preserves exact minimax scores but fails three existing French/Queen's Gambit controls and changes none of the five choices. A scoped H4/H6 confirmation also changes none. Rejecting an exposed-queen H5 frontier improves Alekhine and Slav, but choosing the older H4 line worsens Nimzo to **357.5/383.5‰**. Its full catalog replay gains eight accepted stages and loses sixteen, with 82 changed analyses among 2,303. All 1,700 canonical mating stages remain identical; all-defenses mate transfer was not rerun for this rejected candidate. Those candidates were rejected; they do not qualify a duel or a ΔElo claim. The later bounded correction below has its own transfer evidence.

[HardDefeatWitnessTest](../../atf-tests/src/test/java/ro/atf/chess/HardDefeatWitnessTest.java) retains the five legal histories, original native work and cold teacher receipts, with wrong-history/run/score negatives. It does not require the live player to repeat a bad move. [HardDefeatBenchmarkTest](../../atf-tests/src/test/java/ro/atf/chess/HardDefeatBenchmarkTest.java) exposes five separate quality goals; all remain FAIL on the reference and are kept out of the ordinary component suite. An unmeasured new move requires a fresh oracle phase, never an assumed improvement. [Portable fixture](../../atf-tests/src/test/resources/hard-defeat-witnesses.yaml).

The auditable external dossier is `C:\work\sah\.studii\experiments\2026-11-20-hard-losses\README.md`. Next require a completed free-reply comparison that removes the exposed-queen optimism without falling back to a worse H4 move; preserve the French/QG controls and every previously accepted stage before excluded-family transfer and a paired duel. More passing plumbing assertions do not establish stronger play.

## Bounded queen-reply verification

The qualified correction gains `tactic-040:0` (`dxe6`) and `tactic-095:0` (`…Re8`): two complete material exercises, zero lost stages across all 2,303. The all-defense mate traversal was rerun: 1,000 exercises, 2,236 native decisions and 1,500 terminal branches. An interrupted H6 retains H5; it never substitutes an older H4 move.

The [typed YAML policy](../../chess-core/src/main/resources/queen-reply-verification.yaml) admits a capturing root only when its completed odd-horizon PV ends with a queen capture that allows a legal immediate recapture, and remaining phase is at least 16/24. H6 searches every legal root, reuses valid frontier work and verifies its first root inside a ±200 cp aspiration window. A failed or bounded window is searched again. A changed leader needs strict complete H6 superiority plus two exact uncredited H4 root comparisons, all charged to the same 200k maximum. Quiet plans and sparse endings retain their original guard.

The wider phase-only trial was rejected after harming two quiet plans in 21 archived contexts. Those contexts are now regression controls, not untouched transfer. The capturing-root refinement restores both plans, retains one independently equivalent change and leaves all 42 Basic/Advanced analyses identical. A separate [Lichess CC0](https://database.lichess.org/#puzzles) pool of 256 baseline-only positions yielded 17 eligible source-game contexts frozen before candidate and teacher results. Canonical/color equivalents were excluded; only the opponent's first move was supplied to the native player, never the puzzle solution.

| Independent context | Baseline → corrected move | WDL loss H17/H19, before → after |
|---|---|---|
| `06cPH` | `Bxg6` → `h5` | 1000/1000‰ → 0/0‰ |
| `07GQq` | `…Bxe5` → `…Qxe5` | 779/717‰ → 0/0‰ |
| `03z37` | `…Bxc5` → `…Bg3` | 1000/1000‰ → 992/999‰ |

There are no confirmed two-horizon degradations in the 17 cases, and all 34 Basic/Advanced analyses are identical. The last row remains losing and is a small numerical change, not a solved puzzle. The two strong transfer cases are now portable regression witnesses. This trigger-selected sample does not estimate average game strength or Elo.

At the ordinary 200k ceiling, the five retained league defeats still fail their separate goals. In a **500k diagnostic overlay**, Alekhine completes H6 and selects `…Be7` at 476,986 nodes; cold H17/H19 gives 0/0‰ loss versus 997.5/996.5‰ for the old sacrifice. That overlay is not the shipped policy or a measured Elo gain. Keep the original 200k failures and the independent larger-budget result separate.

[HardQueenReplyTest](../../atf-tests/src/test/java/ro/atf/chess/HardQueenReplyTest.java) covers the two catalog repairs, both strong transfer contexts, quiet-plan/sparse-ending counterexamples, pinned recapture, malformed policies, exact aspiration scores/ties and modified teacher/context rejection. The external dossier is `C:\work\sah\.studii\experiments\2026-11-20-hard-even-verification\README.md`; it retains raw receipts, rejected variants and Extent. The measured step is two material exercises and two independent large-loss recoveries. Candidate ΔElo is **not measured**; no rating anchor is changed.

## Evidence and next study

The external dossier is `C:\work\sah\.studii\experiments\2026-11-19-puzzle-status\README.md`. It links eight Extent reports, exact source/runtime/configuration pins, raw decisions, all PGNs, teacher transcripts, final reviews and 42 contextual YAML lessons. Lessons are study-only: counterexamples and excluded-family transfer are still pending, and no key is imported into runtime. The execution amendment preserves all completed results when increasing independent concurrency; interrupted attempts remain archived and unscored.

Use the [native puzzle protocol](hard-puzzle-study.md), [canonical benchmark](../../atf-tests/src/test/java/ro/atf/chess/HardPuzzleStudyBenchmarkTest.java) and [all-defenses mate benchmark](../../atf-tests/src/test/java/ro/atf/chess/NativeMateDefenseBenchmarkTest.java). [AiCalibrationObservationTest](../../atf-tests/src/test/java/ro/atf/chess/AiCalibrationObservationTest.java) preserves the mocked unavailable-advice contract without freezing an engine defect or changing learning-corpus admission. The initial `training-051` loss and its pawn-race handoff are now protected by portable components and actual conversion games. Keep the five retained league defeats and the remaining material catalog separate. Next isolate the first independently confirmed defect in a complete native/hint/review line; preserve every accepted stage and free-defense mate before a new paired strength study. [Open work](../feedback/README.md).

## Qualified outside-pawn race

The shared model removes a misleading advancement bonus only in a king-blockaded outside-pawn
race. A charged H4/H6 confirmation preserves `h4` after rook liquidation without weakening
the general stability rule. Native play, maximum hint and Game Review select `Rc8+` initially.
The current complete catalog adds **training-051:0**, with **zero lost accepted stages**;
all 1,000 mating exercises also retain every legal defense. [Algorithm and counterexamples](../board/pawn-race.md)

The inspected 24 source-game contexts contain two recovered Hard choices, each formerly losing 1,000‰ against the free teacher. In 48 new,
teacher-blind source-game contexts, all Hard and Basic roots remain unchanged; Advanced
recovers three losing choices. No profile degrades at both Stockfish H17/H19 horizons.
This corrects a shared evaluator; lower profiles do not claim bitwise unchanged results.

Two actually played composed endings retain every White/Black pre-move native decision,
maximum public hint and complete final review. Against unrestricted Stockfish 19 H19,
Hard now wins by mate at **39 and 35 plies**; the original orientation lost at ply 30.
Earlier candidates that chose Rc8+ but lost the pawn ending remain rejected evidence.
These are conversion games, not paired opening games or a measured candidate ΔElo.
Displayed evaluation and review classification remain independently open quality goals.

The study is `C:\work\sah\.studii\experiments\2026-11-20-hard-rook-defense\README.md`.
Its source-bound cold oracle grades complete offered hint and review continuations, not only
their first moves. Original receipts retain their actual producers when later code is tested.

The complete cold audit covers **74 actual roots**, **128 offered hints / 787 hint plies**,
**773 review plies** and **401 native PV plies**, plus two rejected-king counterexamples:
2,037 line plies and 2,850 exact free/forced phases. Actual moves, native root choices,
offered hints and both review branches have zero stable losses ≥150‰ at both horizons in
these two games. Native internal continuations retain **13** such losses; hypothetical
`Ke6`/`Ke3` survives inside the shorter scored search even though the actual handoff is fixed.
The classification benchmark also remains FAIL: losing the win with Ke6 is still GOOD,
with native review loss 44. These are open goals, not erased by the two conversions.
