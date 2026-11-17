# Hard learning from archived games

## Overview

The study `2026-11-17-j7-hard-learned-order` preserves a frozen historical
training set, independently mined losses, and a paired league. Working evidence
stays in its external study directory. The source-qualified game record identifies
the engine, resources, model, start positions, and both color assignments.

## Ordering experiment

The nine-feature linear model was fitted to 58 confirmed alternatives from 30
opening families. Leave-one-family-out validation prefers the labelled alternative
in 28/58 cases. On 36 independent families, the ordering changes 34 root lists,
three initial leaders, and ten node counts. The labelled alternative precedes the
archived move in 12/36 cases, compared with 11/36 for native ordering. Both arms
choose the labelled alternative in six complete decisions. All final moves match.

Chronological rechecking reviewed 140 earlier decisions and corrected one label.
In `loss-005`, `Be2 …dxc4` loses at ply 15, while `exd5` recovers
319.5/402.5 per mille WDL at Stockfish depths 17/19. The original set remains
frozen, and the corrected row is tested separately. Both orderings choose
`cxd5` on that position.

## Next evidence

The positive coefficient for an immediately capturable mover can conflate
correct sacrifices with hanging pieces. Include good preserved moves, sacrifices
with an independently verified reply, and close alternatives alongside the loss
examples. Split by opening ancestry before fitting and preserve both horizons.
Track alternative ranking, complete choice, and resulting opponent continuation.

Among the first 12 divergent positions from the first 100 league starts,
Stockfish confirms five gains and two losses at depths 17 and 19. In
`learned-0007`, `Rb1` gains 528 per mille WDL at depth 19. In
`learned-0015`, `Qxb5 …axb5` loses 541.5. Both compare a candidate result at
four plies with native at five. Use these opposite examples to validate the
actual reply after a selective change.

The adaptive foreground policy is a separate implementation on the current
main source. Its node reserve and any future learned ordering need their own
source-qualified comparisons.

## Completed paired league

The ordering model scored 818 wins, 273 draws, 793 losses, and 116 unfinished
games in 1,000 opening pairs across 996 ancestry groups. Its estimated difference
was +4.34 Elo, with a 95% interval of −2.77 to +11.31. Assigning every unfinished
game a loss or a win changes the central estimate to −15.82 or +24.53. Legal replay
verified 238,591 decision receipts. Complete move sequences matched in 657 pairs.
The model remains an experimental prior with an uncertain game-strength effect.

These games used H5 and 44,000 additional nodes on the pinned research source.
The larger adaptive main profile requires its own game-strength estimate.

[AI evaluation protocol](../board/ai-quality.md#checks-for-every-change) ·
[Adaptive foreground contract](../board/ai-quality.md#adaptive-foreground-budget)
