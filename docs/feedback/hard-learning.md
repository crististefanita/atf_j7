# Hard learning from archived games

## Overview

This backlog owns the evidence still missing before a learned ordering change can be promoted. The [admission guide](../testing/learning-archive.md#learned-ordering-studies) retains the completed historical experiment and its limits.

| Priority | Gap | Closure criterion |
|---|---|---|
| P1 | Loss-only examples can conflate sound sacrifices and hanging pieces. | Freeze good preserved moves, sacrifices with a verified opposing reply, and close losing alternatives before fitting; split by ancestry. |
| P1 | On 36 excluded families, root order changes in 34 cases but all complete decisions remain identical. | Improve full decisions and opposing continuations on new families at matched horizons and budgets, with no tactical regression. |
| P1 | The historical 44k league does not establish a useful strength gain or measure the adaptive foreground policy. | Qualify transfer first, then use the pinned paired-game and Stockfish-anchor protocol; report confidence, unfinished games and cost. |

## Next contrast

Keep both `learned-0007` (`Rb1`, a gain) and `learned-0015` (`Qxb5 …axb5`, a loss). The inspected candidate/native comparisons reached four and five plies respectively, so they do not isolate an ordering effect. Verify the free opposing reply at a common completed horizon before generalizing.

[AI protocol](../board/ai-quality.md#checks-for-every-change) · [Adaptive profile](../board/ai-quality.md#adaptive-foreground-budget)
