# Evidence for Release 0.16.1

## Overview

Strict qualification binds 534 component cases and 30 Game Review assertions to the same source, signed APK and run ID. The two Puzzles reports add six verified chapter checks each. Rotation adds 32 checks per language on that same APK.

## Evidence

- [Components — 534 passing cases, zero skipped](components.html)
- [Signed Android Game Review — 30 native assertions](android-game-review.html)
- [Compact Puzzles — six chapters, RO, font 1.3](puzzle-ux.html)
- [Physical-size Puzzles — six chapters, EN, font 1.3](puzzle-ux-1080.html)
- [Open hint, Calculated line and Mating line — RO/EN rotation, 64 assertions](android-replay-rotation.html)
- [Saved games menu — focused native observations](match-slots.html)
- [Finished games menu — focused native observations](match-archive.html)
- [Pre-sign project, documentation and reporting governance](governance.html)
- [Git-baseline review and Android API 28 source guard](test-change-review.html)
- [Executed replacement coverage](test-change-review.html.proof.html)
- [Strict release qualification and source identity](qualification.json)
- [Same-run Game Review and process-restart phases](game-review-execution.json)
- [Rotation receipts and embedded-capture fingerprints](replay-rotation-execution.json)
- [Git-baseline and replacement-proof fingerprints](test-change-review-execution.json)
- [External raw-receipt provenance](raw-receipts.json)
- [Signing transition and clean installation](signing-transition.json)
- [Published APK metadata](github-release.json)
- [Pinned Web workflow and 30 served assets](web-publication.json)
- [Qualified engine reused by the UI-only Web package](web-engine-provenance.json)
- [Advisory RAG context for the candidate diff](rag.txt)
- [Complete authenticated download from the published README](distribution-verification.json)
- [Real browser MCP download — identical complete APK](browser-download.json)
- [Published compact Web geometry observed in MCP](web-rendered-observation.json)
- [Current and previous release retention](release-retention.json)

- [Publication governance — 19 passing checks](publication-governance.html)
- [Romanian documentation governance — 18 passing checks](publication-documentation-ro.html)
- [Governance receipts and tested input identities](publication-governance-execution.json)

## Scope and Remaining Work

- The native menu observations retain their actual receipts without a common qualification run ID. They supplement the strict gate; they do not replace Game Review. Rotation retains both language receipts and all sixteen capture fingerprints. Captures are embedded in Extent, rather than duplicated as PNG files.
- Browser MCP verified the header, contextual Back, right-anchored options and centered navigation on compact and desktop views. The Web package retains the previously qualified engine; the JCA digest compilation failure remains open.
- The legacy compact replay accessibility helper failed at a visible review command. Its failed receipt remains in the external study and its repair remains in feedback. Game Review at font 1.3 and the independent RO/EN open-replay rotation passed.
- Builds, raw XML, screenshots and intermediate probes stay under C:\work\sah. This folder retains the evidence needed to understand this edition.

[Qualification and publication](../../../docs/release/README.md#qualification-and-publication) · [Shared screen chrome](../../../docs/testing/README.md#shared-screen-chrome) · [Remaining Web parity work](../../../docs/feedback/web-parity.md)
