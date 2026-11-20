# Outside pawn races

## Overview

The shared position model recognizes a narrow pawn-ending structure: an advanced passed pawn
is directly blocked by the opposing king, while the blocking side has a distant passer that
the defending king cannot catch. This corrects an advancement bonus, not material, a search
result or a tablebase outcome. [Current results](../testing/hard-status.md)

## Structural evaluation

The adjustment applies only to kings and pawns. The outside passer must be at least two files
from the blocked pawn, have a clear promotion file and lie beyond the defending king's square.
The calculation includes the defender's initial tempo. A reachable promotion, an adjacent-file
passer, another piece or a pawn that is not directly king-blocked keeps the original evaluation.

The typed [policy](../../chess-core/src/main/resources/pawn-blockade.yaml) removes a configurable
fraction of the existing endgame advancement bonus. Zero or disabled means no adjustment.
The model is shared by Basic, Advanced, Hard, hints and Game Review; unchanged node policies
do not imply unchanged lower-level choices. [Geometry](../../chess-core/src/main/java/ro/atf/chess/PawnRace.java)
contains no puzzle ID, FEN lookup, curriculum answer or teacher score.

## Charged leader confirmation

After rook liquidation, a supported outside-pawn advance can replace the previous odd-horizon
leader only when the even horizon completes, the old leader is no longer tied for first and
both leaders are compared exactly at the earlier even horizon. Both comparisons must finish
and favor the new move. They use the same ordinary allowance; interruption retains the old
leader. A king move cannot borrow this exception. This preserves the existing stability rule
outside the explicitly supported pawn advance.

Geometry is a trigger, not a win certificate. Free opposing replies still determine the result.
The rejected broad discount lost the winning break in `021cc`; the narrower rule keeps it.
The initial `Rc8+` repair also needed `h4` after liquidation: selecting the right first move
without converting the resulting game was insufficient.

## Evidence and boundaries

[PawnBlockadeTest](../../atf-tests/src/test/java/ro/atf/chess/PawnBlockadeTest.java) checks both
colors, initial tempo, reachable kings, neighboring passers, unchanged rook positions,
interrupted confirmation and strict YAML. [PawnRaceEvidenceTest](../../atf-tests/src/test/java/ro/atf/chess/PawnRaceEvidenceTest.java)
retains cold Stockfish H17/H19 free/forced witnesses, rejects altered phases and preserves
the sound supporting knight promotion against every legal reply.

The external study `C:\work\sah\.studii\experiments\2026-11-20-hard-rook-defense\README.md`
retains rejected candidates, the original observed games, every pre-move maximum-hint attempt,
postgame reviews and source-bound teacher phases. Replaying a saved position is regression
evidence, not a new observation. A win in a composed ending is conversion evidence, not Elo.
Keep recommendation quality, score calibration, complete canonical puzzles and game strength
as separate verdicts. [Native study protocol](../testing/hard-puzzle-study.md)
