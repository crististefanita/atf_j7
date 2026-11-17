# Offline hint audit

## Overview

- **Purpose.** Follow a short game one decision at a time, retain every offered hint and its opposing replies, and compare node budgets from identical positions. This is a diagnostic collection, with no Elo claim.
- **Owner.** The test-side `HintAuditPolicy` and `HintAuditSession` consume the real engine through a single bounded call per decision. They do not train or configure the live opponent.
- **Checks.** `HintAuditSessionTest` covers receipt integrity with mocks. Its separately tagged benchmark method creates an actual collection and an auditable Extent report. [Plan](../../atf-tests/src/test/resources/hint-audit.yaml) · [Contracts](../../atf-tests/src/test/java/ro/atf/chess/HintAuditSessionTest.java)

## Run an actual collection

Use a clean external checkout and a fresh output directory under `C:\work\sah`.
Keep the Extent file beside the raw output directory. The run ID, source commit,
and engine digest must identify the qualified source and compiled runtime.
Retain the source/class manifest before and after generation. A label supplied
by the caller alone does not attest which engine ran.

```powershell
Set-Location $checkout
mvn test '-Dtest=HintAuditSessionTest#collectsShortRankedPathsAndAnIdenticalPositionBudgetGrid' `
  "-Dchess.hintAudit.output=$auditOutput" "-Dchess.extent=$extentPath" `
  "-Dchess.hintAudit.run=$runId" "-Dchess.hintAudit.revision=$sourceCommit" `
  "-Dchess.hintAudit.engineDigest=$engineDigest"
```

Set `TEMP`, `TMP`, and the JVM temporary directory to the external workspace
before building. An explicitly selected collector without its required output
fails. The normal component suite excludes the benchmark method and runs the
mocked contracts without Stockfish or an emulator.

## Plan and comparison

The versioned YAML declares short Sicilian, Caro–Kann, and Italian prefixes,
eight planned decisions per path, budget schedules of 50,000, 100,000, and
200,000 nodes, and requested hint ranks 1, 2, and 3. The hard ceiling stays
200,000. The parser rejects empty schedules, unsupported fields, illegal starts,
duplicate IDs, and values outside the declared bounds.

Each path starts with legal opening moves and then follows its requested hint
rank. This deliberately explores alternatives. The normal Hard opponent's
documented theory move is recorded separately. The collection is a study of hint
policies, not a rated league or a claim that the live opponent played that path.

An unavailable rank is recorded explicitly. The declared fallback selects the
first actual suggestion. It never manufactures a second or third recommendation.
Verified comparison candidates are diagnostic evidence and remain distinct from
the offered recommendations.

Nine additional rows compare all three budgets at the identical full starting
checkpoint of each path. Only these matched rows measure the budget effect.
Paths that have diverged cannot support a direct budget-strength comparison.
A completed game may stop a path before its planned length and keeps its real result.

## Retained evidence

| Record | Meaning | Validation |
|---|---|---|
| Identity | Dataset, run, exact source revision, engine and configuration digests | Foreign identities are rejected |
| Position | Full checkpoint, FEN, side, clocks, castling and en passant rights | Replayed from the entire history |
| Context | Budget, repetition count and cache semantics | Different histories remain different contexts |
| Search | Actual nodes, seed depth, selected depth and comparison depth | One call, bounded at the requested allowance |
| Variants | Offered ranks, scores, material, full PVs and opposing reply facts | Legal moves, material ledger and terminal labels checked |
| Selection | Requested rank, availability, selected move and theory reference | Explicit fallback and legal continuation |
| Integrity | Previous receipt digest and trusted whole-file digest | Resume rejects truncation, substitution and altered scores |

An absent accepted comparison is recorded with comparison depth zero. This does
not mean that the hint searched zero plies. The selected and seed depths record
the achieved horizons separately from PV length.

The search is fresh and position-only. Retaining history for the audit does not
make the hint repetition-aware or create a persistent learned cache. Check,
capture, and reply facts describe observed dangers. They do not certify safety
against every legal defense.

Per-path JSONL and SAN PGN preserve the exact moves. The combined JSONL and
manifest identify the collection. An unfinished study uses PGN result `*`.
Resumption requires a separately trusted JSONL digest and revalidates the whole
history without calling the engine again. An internal hash chain alone cannot
authenticate a consistently rewritten file.

## Independent quality checks

Stockfish 19 evaluates the exact full checkpoint at H17 and H19. Compare a free
root search with the offered or played root, then leave every opposing reply
free. Keep root scores, WDL, principal variations, commands, engine identity,
and raw receipts. Reuse requires identical inputs and explicit provenance.

Separate loss of the first recommendation from loss caused by intentionally
playing rank 2 or 3. Legal replay, preserved history, and a passed integrity
suite do not establish move strength. A change that fixes one opening and
regresses another remains experimental. Document development cases, untouched
transfer cases, and cases later reused as regression controls separately.

Exact-cache tests distinguish full-window values from cutoff bounds and compare
warm, cold, and disabled caches. The upstream reference also classifies bounds
separately. [Stockfish 19 search](https://github.com/official-stockfish/Stockfish/blob/sf_19/src/search.cpp#L1460-L1468)

[AI policy](../board/ai-quality.md) · [Testing strategy](README.md) · [Current feedback](../feedback/README.md)
