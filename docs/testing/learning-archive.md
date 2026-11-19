# Learning archive admission

## Required evidence

Learning games live under `C:\work\sah\.studii`, outside the checkout. Every actual
White **and** Black move must have a pre-move native Hard calculation and a separate
public hint requested at the maximum 200,000 ordinary nodes, plus its separately
charged proof reserve when needed. Work may finish below those allowances.
The native allowance and displayed estimate remain separate.
Opening prefixes, theory moves and oracle replies have the same requirement.

[`LearningGameAudit`](../../atf-tests/src/test/java/ro/atf/chess/LearningGameAudit.java)
binds source, runtime, configuration, origin, full history, side, clocks, decisions,
offered lines and requested allowance. A chained journal is forced to disk before
the match advances. A failed write preserves the old board and pending analysis.
[`LearningGameAuditTest`](../../atf-tests/src/test/java/ro/atf/chess/LearningGameAuditTest.java)
rejects missing first moves, another run, substituted phases and smaller requests.

## Admission and quality

Integrity admission is separate from hint quality. Fully recorded mistakes are
useful counterexamples; they are not trusted lessons or a quality PASS. Compare
every offered root and each hypothetical reply with a free opposing search at
Stockfish H17/H19 using the same full context. Inspect the continuation after a
valuable-piece capture, including legal recaptures and stronger quiet defenses.
A legal principal variation or a high native score alone does not prove a sound
sacrifice. Keep independently confirmed sound sacrifices as negative controls.

Historical games without the required pre-move evidence are retired and deleted
from the learning corpus. Recalculating their positions now cannot prove that the
analyses existed before the historical moves. New traces get new identities.
Retain only anti-leakage fingerprints and retirement counts for deleted games.
Source test fixtures and release evidence remain source history, outside learning.

## Reproduction and retention

The external archive README owns current corpus paths, pinned dependencies,
source identities, oracle receipts and replay commands. A zero-clock composed
origin is required until restoration of nonzero initial FEN clocks is qualified.
Record a cap explicitly; an unfinished thematic trace is not an Elo match.

Keep reusable tools, current regression certificates, unresolved problem
definitions and exact tablebases. Historical Stockfish receipts retain their
original producer identity and may serve as references only. Remove redundant
HTML, build copies, rejected experiments and noncompliant game bodies after
retained files, Git source references and active processes have been checked.

Use the [hint protocol](hard-hint-study.md) for oracle interpretation and the
[native league protocol](hard-audit.md#elo-and-unfinished-games) for strength.

## Post-game review and hint endpoints

Attach a complete native review to every saved game as a separate `review.json`.
Use `GameReview.analyze` for an actual result and `analyzeLine` for a capped path;
never turn a cap into a completed game. Retain all moves, colors, grades, score
perspectives, completed depths, played/recommended lines, explanations and the
checkpoint-bound review cache. Capped paths have no rating. Completed-game local
rating estimates are not a measured Elo and must preserve the actual actor roles.

[`LearningReviewAudit`](../../atf-tests/src/test/java/ro/atf/chess/LearningReviewAudit.java)
binds the original journal to a separate reviewer identity, loaded review policy,
final checkpoint, result and uninterrupted chronological progress. It rejects
missing moves, relabeled runs and readable scores that disagree with the cache.
The configured ordinary review search is 85k additional nodes. A proof receipt retains
consumed ordinary and reserve work; legacy turns without a receipt have no invented
per-turn count. Keep these separate from the ordinary 200k public hint allowance.

Compare every played and recommended review branch at each hypothetical ply with
independent H17/H19 evidence. Existing oracle phases may be referenced only with
their original identity and identical full history, origin, root, binary and
options. New phases retain their own identity. Do not relabel inherited output.

For every public hint, audit the exact last move and endpoint. Enumerate **legal**
captures of the final important piece, including the stronger reply after the
displayed endpoint. A pinned pawn is not a legal recapture. Exposure alone is not
an unsound sacrifice: retain `…Qg3 hxg3 …Rh1#` as a sound control. Preserve both
free and constrained scores, and grade the final move before interpreting the
reply. The Caro–Kann `…Qxd4` queen-for-pawn tail is a confirmed counterexample,
not a reason to ban exchanges or all sacrifices.

[`LearningPostgameBenchmarkTest`](../../atf-tests/src/test/java/ro/atf/chess/LearningPostgameBenchmarkTest.java)
requires every game's review and readable analysis, both review branches, all
public tails and the raw oracle phases. Integrity, exposed-tail quality and
recommendation quality are separate Extent verdicts. Native logistic points and
Stockfish WDL are different models; label disagreements are calibration findings.

## Reconfirming a queen capture

Retain the original producer and original bad PV as a counterexample. Record a
new hint from the corrected source with its actual total node use; do not amend
historical journals or relabel the teacher. Compare every offered root on the
frozen regression contexts and fresh contexts before promoting a frontier change.
Do not activate a new tactical mode from a shallow preliminary comparison below
the completed seed horizon. Cache identity and equal-turn support must distinguish
the modes. A selectively searched extra candidate is not automatically equivalent.

Native Hard catalog replay is a separate measurement. Equality of complete native
analyses—including choices, depths, costs and PVs—demonstrates preservation, not
an Elo gain. Two full-strength Stockfish games are diagnostic games, not a rated
league. Preserve every pre-move Hard/hint pair, both colors, and attach the full
native review before the independent oracle completes. Pending oracle phases
remain pending; integrity and absolute chess quality keep separate Extent verdicts.

The component benchmark `LearningArchiveBenchmarkTest.nativeHardChoicesHaveNoStableLargeIndependentOracleLoss`
keeps native choices under a separate oracle quality gate. It binds every observed
move to its original journal and reports the two independent WDL losses in Extent.
A later fix needs a current-source replay or fresh game and renewed oracle evidence;
changing an expected move to accept the defect cannot close it.

The small [permanent witness fixture](../../atf-tests/src/test/resources/hard-learning-witnesses.yaml)
keeps exact full histories and eight raw oracle phases in YAML 1.2 JSON syntax.
`HardLearningWitnessTest` validates it offline and rejects substitutions;
`HardLearningWitnessBenchmarkTest` recalculates the current native choice and
keeps each known loss red until it is repaired. An unseen replacement needs new
teacher evidence. These positions are counterexamples, never runtime book entries.

## Finished-game continuation studies

The November 19 collection records English and Scandinavian from the initial board
to checkmate, plus the sound queen-offer control. All 106 actual plies have their own
pre-move Hard calculation and maximum public hint, including theory and Stockfish
moves. Its 759 public hypothetical plies have 2,692 distinct cold H17/H19 queries.
The complete native reviews use a separately identified reviewer. Every review
branch and exposed important-piece endpoint also needs its own oracle coverage.

Use automatic fivefold repetition and the 75-move rule when the collector does not
implement a player's draw claim. A merely claimable threefold/fifty-move draw must
not truncate an ongoing native game. A capped trace still uses `analyzeLine` and has
no completed-game rating. External tools are frozen with the study; live source
changes require an explicit replay with its own runtime identity, not edited journals.

Keep integrity and absolute quality in separate Extent reports. A successful
component regression or minimax equivalence does not erase the archived mistakes,
qualify a new opening family or establish Elo. Store a small full-context first-loss
witness in the test resources; keep the PGN, all pre-move observations and complete
reviews in the external corpus. Never turn those keys into runtime move overrides.
