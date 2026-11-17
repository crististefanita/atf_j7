# Chess Lab Documentation

Start with the guide that answers your question. Verify claims about behavior against the project's sources and tests.

| Question | Guide |
|---|---|
| How do the engine, app, and tests fit together? | [J7 architecture](../README.md#j7-architecture) · [Engine and adapters](../web/README.md#shared-engine-and-platform-adapters) · [ATF libraries](../lib/README.md) |
| How do I play and configure puzzles? | [Board and gameplay](board/README.md) · [Catalog](board/quest-curation.md) |
| How are AI opponents measured and game reviews interpreted? | [AI protocol](board/ai-quality.md#checks-for-every-change) · [Game Review](board/game-review.md) |
| What needs checking before a change? | [Choosing a test level](testing/README.md#choosing-the-test-level) · [Extent reporting](testing/README.md#extent-reporting) |
| How do I verify a feature on Web and APK? | [Web guide](../web/README.md) · [Parity matrix](../web/parity.md) |
| How is an APK qualified and where is the release evidence? | [Release process](release/README.md#qualification-and-publication) · [Published releases](../release/README.md) |
| Which assumptions still need evidence? | [Feedback](feedback/README.md) |

## Maintenance Rules

- **Main README.** Purpose, the two current release links, an architecture map,
  and entry points for testing, releases, and feedback.
- **Area guide.** Ownership, inputs, outcomes, and boundaries. Put commands next to
  their prerequisites and identify the evidence that confirms the result.
- **Format.** One title, semantic Markdown, and language tags on fenced blocks.
  Use tables for comparisons and numbered steps for procedures.
  Each diagram answers one question and has its legend immediately below.
- **Links.** Use relative paths and existing anchors. Verify the destination after
  moving a document. Use parameters for local paths in examples.
- **Feedback.** State the observation, priority, owner, evidence, and closure criteria.
  Move each confirmed rule into its guide and remove the closed item.
- **Evidence.** Keep working builds, screenshots, and reports in TMP.
  Promoted evidence belongs in the release directory and stays tied to the binary and commit.

## Documentation Checks

Run from the TMP copy with JDK 21. If you use `-o`, the selected Maven repository
must already contain the dependencies and plugins.

```powershell
mvn test '-Dtest=DocumentationGovernanceTest,ReleaseGovernanceTest' '-Dchess.extent=target/doc-governance.html'
```

`DocumentationGovernanceTest` checks the README map, local navigation, current
release destinations, feedback structure, and Markdown rules for guides and diagrams.
The same checks cover the Web guide and parity matrix.
`ReleaseGovernanceTest` checks publication rules and RAG routes.

During review, compare claims against sources and build declarations.
Check information ownership, example fidelity, rendering, and command results.
[Qualification](release/README.md#qualification-and-publication)
