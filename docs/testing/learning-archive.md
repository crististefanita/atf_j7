# Learning archive admission

## Required evidence

Learning games live under `C:\work\sah\.studii`, outside the checkout. Every actual
White **and** Black move must have a pre-move native Hard calculation and a separate
public hint requested at the maximum 200,000 total nodes. Work may finish below
that allowance. The native allowance and displayed estimate remain separate.
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
