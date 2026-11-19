# Release and RAG

## Overview

This guide ties the commit, signed APK, Android probe, and verified download together.
The [release catalog](../../release/README.md) identifies the available version and its evidence.

## Qualification and Publication

Check the available version in the README from `origin/main`. If the local branch contains parallel work, start the candidate from `origin/main` in TMP and transfer only the files reviewed for the release.

1. Investigate the changes from the latest tag and run `ReleaseRag` with the modified paths. Open the cited documents and record the risks, affected suites, and acceptance criteria.
2. Select the version from the 0.x series. A fix increments the patch, and a new feature or contract change increments the minor version. Align `versionName`, `versionCode`, the Maven version, and the APK name. In `release/index.json`, `currentVersion` is the current release, and `versions` contains the current release followed by one previous release for comparison. The candidate remains separate until publication. [Release catalog](../../release/README.md)
3. Update the notes and affected guides on both branches. Move confirmed rules from feedback into the owning guide. In `main` use **Play** and **Published Release**. In `main_RO` use **Jocul** and **Ediția publicată**. Place the APK and browser links under the play heading in that order. Place the GitHub notes below the architecture. [Bilingual documentation](#bilingual-documentation) · [Web publication](../../web/parity.md#publishing-the-link)
4. Create the candidate commit in a clean Git worktree in TMP. Declare the SHA of the review baseline, a distinct ancestor of the candidate. Run DocumentationGovernanceTest, ReleaseGovernanceTest, ExtentGovernanceTest, TestChangeReview, and ReleaseGate preflight before signing. Keep the reports and Git review receipt. Enable the AI probes when the diff affects the engine or corpus. [JVM suites](../testing/README.md#jvm-suites) · [Test changes](../testing/README.md#test-changes-against-a-git-baseline)
5. Load the identity by dot-sourcing `scripts/Initialize-J7Signing.ps1`; an existing encrypted project record is detected automatically. For a new key after a published release, use `-StartNewIdentity` explicitly. The script rejects implicit rotation and protects the key with ACL and DPAPI. Build the APK and Android tests with Gradle, then verify the signature, `versionCode`, and compatibility through `scripts/verify-signing-continuity.ps1`. Run the flow that covers the change on the emulator and keep the installed APK hash and Extent report. If the signing identity changed and the emulator still has the old release, preserve the installation containing valuable data. A sanity APK with a different `applicationId` supplies supplementary evidence and cannot qualify the distribution binary.
6. Run `ChessComponentSuite` and Game Review sanity under the same qualification ID. Pass the APK, commit, raw receipt directories, and Extent reports through `ReleaseGate qualification`. Check the expandable comparisons and captures for their steps in Extent. For a change to Opening theory or **Why?**, add the compact probe at a 1.3× font scale in RO and EN for the same APK and commit. [Shared replay](../testing/README.md#shared-replay-on-a-compact-screen) [Extent audit criteria](../testing/README.md#extent-reporting)
7. After signing and qualification, push the candidate tag without rewriting history and publish the GitHub asset. Keep `currentVersion`, the comparison list, and the main README on the latest verified release until the new asset exists. Then promote the catalog and README on both branches. Keep only the new and immediately preceding releases on GitHub Releases and locally. Retire the other releases and folders after the new asset and README route pass verification. Keep the Git tags.
8. Read the README from `main` on GitHub and download the APK from the displayed URL. Use `ReleaseGate publication` to verify the tag, channel, HTTP status, byte count, SHA-256, and CRC. Save the evidence, push the reports commit, and repeat the download from the final version of the README.

- Keep the public and authenticated download receipts separately. The GitHub metadata must identify the qualified producer under `targetCommit`; the public receipt records the actual HTTP status, complete bytes, digest and CRC from the canonical README asset route. A successful authenticated download alone does not complete publication governance.

- After publishing a draft, read the release again by tag and identify the asset by ID. GitHub may return an interim route with the “untagged” segment in the upload response. Use only the canonical route from the published release metadata in the README and verify the complete download.

- Send the notes from the UTF-8 file. For REST, send the UTF-8 bytes of the JSON and reread the published release body by tag. Compare the text with the source and check that the evidence link opens the release folder.

- Keep generated Extent HTML byte for byte, including in the Git index, so that the qualification hashes remain valid. Apply the formatting check to manually edited files.

- In the checkout, current-release governance recalculates HTML fingerprints for strict qualification and checks Android receipts against the source, APK, and total assertion count. Report and receipt paths must stay within the release folder.

## Bilingual Documentation

Use `main` for English and `main_RO` for Romanian. Prepare both updates in
detached worktrees in TMP. The default branch owns publication, and version
metadata, the APK link and evidence identify the same release in both branches.

Write all authored Markdown prose in English on `main` and Romanian on
`main_RO`, including headings, summaries, tables, link labels, diagram labels
and legends. Preserve the same meaning, facts, procedure and coverage in both
versions. Code identifiers, commands, SAN and quoted interface labels retain
their required spelling. Generated HTML, JSON and raw execution receipts keep
their original bytes and observed interface language; translate the README
that explains the evidence, rather than rewriting a qualified artifact.

1. Start from the live `origin/main` and `origin/main_RO`. Make the Romanian
   wording precise and translate the same change into English. Keep document
   paths, section hierarchy, command meaning, values and evidence aligned.
2. Review the wording and actual UI labels in both languages. Use standard SAN
   in English chess examples. Record any localization that intentionally differs
   in display while keeping the same move or behavior.
3. Run `DocumentationGovernanceTest` in each candidate worktree and the pair
   gate against the two committed candidate SHAs. Check J0's rules for
   orientation, concise visible prose, diagram labels and adjacent legends.
4. Push both tested commits together with an ordinary atomic fast-forward.
   When the edition changes, verify the complete APK from the English README
   on `main` and confirm the Romanian route points at the same release.

Build the two worktrees sequentially when their Maven local repository is shared: root bootstrap installs the ATF binaries and Windows may lock a JAR already loaded by the other JVM. Independent target directories do not isolate that repository. Parallel builds require independently prepared local repositories and verified library provenance.

```powershell
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate documentation-pair . $enCommit $roCommit
git push --atomic origin "${enCommit}:refs/heads/main" "${roCommit}:refs/heads/main_RO"
```

The pair gate verifies the tracked Markdown inventory, ordered structure,
corresponding links and heading destinations, and byte-identical published
evidence. Review every prose block and visible diagram label for its language
and translated meaning; a passing structural gate alone does not establish
language completeness or translation fidelity. [Testing strategy](../testing/README.md#choosing-the-test-level)

GitHub Actions deploys the qualified Web asset from an immutable Git commit
through the [Pages workflow](../../.github/workflows/chess-pages.yml).
Permanent branches are limited to `main` and `main_RO`. Keep Git tags of
published editions and deployed site bundles. [Web publication](../../web/parity.md#publishing-the-link)

## Strict Exception for Installing the Exact APK

- `ReleaseGate qualification` compares Surefire cases with Extent assertion tables and the three Android receipts, metadata and PNGs.
- Qualification requires one run ID, the exact source and distribution APK hash. Missing results, another run or another APK stop the gate.

The exception requires explicit publication authorization, a confirmed signing change, and old emulator data to preserve.

<details>
<summary>Required evidence and signing steps</summary>

If the signature changed, the only available emulator has the old installation and data that must be kept, and the user explicitly requested publication through the README, the release can only be published with status `PUBLISHED_WITH_INSTALL_EXCEPTION`. The release folder must contain `publication-exception.json`, a verifiable build receipt, the supplementary sanity report and receipt, signing transition, Extent with a verified hash, public and authenticated download receipts, and GitHub release metadata. The exception applies only to the exact installation. Its reason, distribution APK commit and hash, different sanity APK hash, supplementary assertion count, and `CLEAN_INSTALL_REQUIRED` are validated separately. The release omits `qualification.json` and makes no claim that the exact APK was installed. The tests, signature, asset integrity, and full-download requirements still apply. Once a clean emulator is available, run strict qualification and replace the exception with complete evidence.

Dot-source the script in the build session to load `J7_SIGNING_STORE` and `J7_SIGNING_PASSWORD`. DPAPI decrypts the password locally, and the password stays hidden. For an upgrade, the certificate must match exactly. A different signature explicitly requires `CLEAN_INSTALL` mode. Android cannot update the installed version, and old app data does not migrate. Gradle builds the signed pair with `-Pj7Distribution :app:assembleRelease :app:assembleReleaseAndroidTest`. The runner with `-Distribution -SourceCommit <sha>` requires an emulator without the game package and compares the installed bytes with the qualified APK before instrumentation.

</details>

## Signing Identity and Clean Installation

- Identify the current release certificate in the [catalog](../../release/README.md).
  Strict qualification keeps it in the qualification receipt, and a release
  published with an exception keeps it in the build receipt. If the current
  key is missing, recover it or explicitly choose a clean installation with a
  new identity. Android cannot migrate local saves across different signatures.

- `Initialize-J7Signing.ps1` rejects partial records and stores the key,
  certificate, and DPAPI-protected password in `infrastructure/signing` under
  `-DataRoot`, outside the source and TMP. For deliberate rotation, use
  `-StartNewIdentity`. The `signing-continuity` gate rejects a mismatch in
  `UPGRADE` mode. Only `CLEAN_INSTALL` records it as an incompatibility.
  [Negative tests](../../atf-tests/src/test/java/ro/atf/chess/ReleaseGovernanceTest.java)
  verify the gate.

  Chess also keeps the explicitly requested encrypted project record in
  [`.atf-signing`](../../.atf-signing/README.md). An existing directory is loaded
  automatically; `-ProjectIdentity -StartNewIdentity` provisions a missing one
  only after explicit approval of the signing transition. Paths in its manifest
  are relative. The PKCS12 password stays DPAPI-protected for the original
  Windows account; logs and plaintext passwords are excluded from Git.
  A different Windows account requires separate recovery, not silent rotation.
  Both records retain the certificate comparison and `CLEAN_INSTALL` requirement.

- Without an explicit baseline, `verify-signing-continuity.ps1` looks for
  certificate evidence in the current release folder. When recovering a missing
  key, initialization also reads the catalog and stops rotation if current-release
  evidence is missing.

In the TMP copy, before signing:

Run the tests from the candidate Git copy. Choose fresh TMP paths for the
governance and review reports. Preflight checks the baseline and current
commit against the TestChangeReview receipt and requires passing documentation,
release, and Extent controls.


```powershell
mvn test "-Dtest=ChessGovernanceSuite" "-Dchess.extent=$governanceExtent"
mvn test "-Dtest=TestChangeReview" "-Dchess.testChanges.base=$reviewBase" "-Dchess.extent=$testChangeExtent"
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate preflight atf-tests/target/surefire-reports . $reviewBase
```

- Run the suites from the same TMP checkout and choose fresh report paths.
- Save the fresh component Surefire directory before the Android publisher writes other XML files there.
- Build the Web bundle from the candidate commit in TMP and pass it through `chess.study.bundle`. Without it, three worker probes are skipped and `ReleaseGate qualification` rejects the suite. [Web build](../../web/README.md#build-and-verification)

```powershell
$runId = [guid]::NewGuid().ToString('N')
$commit = git rev-parse HEAD
$site = ./web/Build-ChessWeb.ps1 -WorkRoot (Join-Path $env:TEMP "chess-web-qualification-$runId")
mvn -pl atf-tests -am test "-Dtest=ChessComponentSuite" "-Dsurefire.failIfNoSpecifiedTests=false" `
  "-Dchess.study.bundle=$site" `
  "-Dchess.extent=$componentExtent" "-Dchess.qualification.commit=$commit" "-Dchess.qualification.runId=$runId"
  # Move the suite's Surefire receipts to $componentReceipts before Android publication.
  # Run signed sanity with -SourceCommit $commit -QualificationRunId $runId.
java -cp atf-tests/target/classes ro.atf.chess.release.ReleaseGate qualification `
  $version $commit $apk $componentReceipts $componentExtent $nativeRun $nativeExtent
```

`$nativeRun` is the directory containing `game-review.txt`, `execution.json`,
and the `why` and `why-restore` subdirectories. Archive the HTML files and
receipt fingerprints in the release directory. Keep raw XML and the captures
already embedded in reports in TMP.

<details>
<summary>Puzzle board and danger marker</summary>

For changes to the board or danger marker in Puzzles, run
`-Distribution -PuzzleDangerOnly -SourceCommit <sha>` on the signed APK.
For layout changes, also run `-Distribution -PuzzleMateReplayOnly -SourceCommit <sha>`
on 720 × 1280 at 320 dpi and native 1080 × 1920, both at a 1.3× font scale.
Publish Extent from both receipts and compare the commit and APK hash.
[Puzzle probes](../testing/README.md#puzzle-progress)

</details>

<details>
<summary>Puzzle header, scoring and controls</summary>

For changes to the Puzzles header, scoring, or shared controls, run
`-Distribution -PuzzleUxOnly -SourceCommit <sha>` with `-PuzzleLanguage ro` on
the compact profile and `-PuzzleLanguage en` on the physical profile, both at a
1.3× font scale. Publish `puzzle-ux.txt` with
`AndroidSanityPublication#publishPuzzleUxSanity` and keep the result and the
fingerprints for all six captures on each profile.
[Six-chapter contract](../testing/README.md#puzzle-progress)

</details>

<details>
<summary>Openings</summary>

For changes to Openings, add `-IncludeOpenings` to signed sanity. The runner executes the probe on the same APK after checking game resumption. Publish Extent from the `openings/openings.txt` receipt and use the same commit and APK hash to qualify both reports. [Openings probe](../testing/README.md#openings)

</details>

<details>
<summary>Compact AI game geometry</summary>

For geometry changes in the AI game, run `-Distribution -AnalysisOnly -CompactMatchAnalysisOnly -SourceCommit <sha>` on the same signed APK. Publish Extent from `analysis-compact-prepare-ro/analysis-compact-prepare-ro.txt`. Verify a common ID for preparation and resumption in RO and EN, and keep the report, aggregate receipt, and six capture fingerprints under `additionalAndroidChecks.analysis-compact` in the qualification. [Geometry and the compact probe](../testing/README.md#game-and-history)

</details>

<details>
<summary>Game Review</summary>

For a change limited to Game Review, use `-Distribution -GameReviewOnly -SourceCommit <sha> -QualificationRunId <id>`. Publish Extent from `game-review.txt`. If the diff also affects the base flows, run them with `-IncludeGameReview`. The review receipt is then in the `game-review` subdirectory. [Resumption and review probe](../testing/README.md#game-and-history)

</details>

<details>
<summary>Analysis board and Elo references</summary>

For changes to the Analysis board, use `-ManualAnalysisOnly` during development and `-IncludeManualAnalysis` for signed sanity. Publish Extent from `manual-analysis/manual-analysis.txt` and verify APK identity through `manual-analysis/execution.json`. For changes to the Elo references, keep the three report and TSV replay pairs and verify the calculation with `GameReviewGovernanceTest`. [Analysis board](../testing/README.md#analysis-board) · [Anchor protocol](../board/ai-quality.md#external-anchor-results)

</details>

<details>
<summary>Shared hints</summary>

For shared hints, run `-Distribution -AnalysisOnly -IncludeManualAnalysis -SourceCommit <sha>`. Publish both Extent reports while the raw directories retain the structure produced by the runner. The three phases share one ID, and the publishers compare the source, APK, and instrumentation fingerprints. Keep these reports as evidence for the affected flows alongside Game Review qualification. [Game and history](../testing/README.md#game-and-history)

</details>

<details>
<summary>Archived hint evidence</summary>

The hint archive contains the two HTML files and `hint-analysis-execution.json`.
In `qualification.json`, record the final receipt SHA-256 under
`hintAnalysisExecutionSha256`. Governance rereads the three phases, compares the
run and APK identity with the Extent tables, and recalculates fingerprints for
the HTML and embedded PNG files. Releases 0.13.5 and 0.13.6 use the existing
receipt. Releases without this flow retain their format.

</details>

<details>
<summary>Saved and finished game menus</summary>

For changes to game lists, run the signed probe for both menus. The runner keeps the same installation and resets test data between phases. Qualify both Extent reports separately against the common APK identity. Persistence checks run in `MatchStoreTest`.

</details>

```powershell
./scripts/run-android-sanity.ps1 -Adb <adb> -Serial <serial> -LeaseFile <lease-file> `
  -Distribution -MatchMenusOnly -SourceCommit <sha>
```

If the release also changes the board or hints, run the analysis flow with `-AnalysisOnly -IncludeManualAnalysis -IncludeMatchSlots -IncludeMatchArchive`.

- `ReleaseGate` exposes Java qualification and publication checks. For a private release, read the README on `main` with an authenticated account, reconcile the link with the tagged asset's `browser_download_url`, and download through the authenticated API. Record the route, status, commit, hash, and CRC result in the release directory. The README link remains the browser route for signed-in users. [GitHub release assets API](https://docs.github.com/en/rest/releases/assets#get-a-release-asset)

- To audit a historical publication, use the verifier from the release's qualified commit and the README identified in the publication receipt. New releases use the current **Play** section format.

- After publication, run verification with the existing GitHub authentication in Git Credential Manager. The output directory is fresh and stays in TMP.

```powershell
./scripts/verify-authenticated-download.ps1 -Repository crististefanita/chess `
  -Version <version> -CandidateCommit <sha> -QualifiedApk <qualified-apk> `
  -Classes <tmp-checkout>/atf-tests/target/classes -OutputDirectory <new-tmp-directory>
```

## Advisory Context

For new citations, RAG calculates SHA-256 over UTF-8 text after converting CRLF
line endings to LF. The anchor and fingerprint come from the same document read.
Historical citations retain the fingerprints published with their releases.

After compilation, call `ReleaseRag` from the TMP copy with the source root and affected paths:

```powershell
$paths = @(git -C $checkout diff --name-only $previousTag $candidateCommit)
java -cp "$scratch/atf-tests/target/classes" ro.atf.chess.release.ReleaseRag $scratch @paths
```

- Pass the paths from the Git checkout to the command in TMP. RAG reports each as `COVERED` or `NEEDS_REVIEW`, including mixed lists.
- Each citation identifies its document, anchor, line, hash and check. **Qualification and Publication** citations also print the steps in order.
- Keep the output in the release directory. Run qualification and publication checks separately.
