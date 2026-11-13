#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Adb,
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$LeaseFile,
    [string]$AdbHost = 'localhost',
    [int]$AdbPort = 5037,
    [switch]$Distribution,
    [switch]$AnalysisOnly,
    [switch]$OpeningsOnly,
    [switch]$GameReviewOnly,
    [switch]$ManualAnalysisOnly,
    [switch]$MatchSlotsOnly,
    [switch]$MatchArchiveOnly,
    [switch]$MatchMenusOnly,
    [switch]$IncludeOpenings,
    [switch]$IncludeGameReview,
    [switch]$IncludeManualAnalysis,
    [switch]$IncludeMatchSlots,
    [switch]$IncludeMatchArchive,
    [string]$SourceCommit,
    [ValidatePattern('^[a-f0-9]{32}$')][string]$QualificationRunId
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (!$root.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Run from a dedicated TMP checkout.' }
$adbExe = (Get-Command $Adb -CommandType Application).Source
$app = if ($Distribution) { 'ro.atf.chess.android' } else { 'ro.atf.chess.android.sanity' }
$variant = if ($Distribution) { 'release' } else { 'sanity' }
if ($Distribution -and $SourceCommit -cnotmatch '^[a-f0-9]{40}$') { throw 'Distribution requires the exact source commit.' }
if ($QualificationRunId -and !($Distribution -and ($GameReviewOnly -or $IncludeGameReview))) {
    throw 'A qualification run ID applies to signed Game Review evidence.'
}
if ($MatchMenusOnly -and ($AnalysisOnly -or $OpeningsOnly -or $GameReviewOnly -or $ManualAnalysisOnly -or $MatchSlotsOnly -or $MatchArchiveOnly -or
        $IncludeOpenings -or $IncludeGameReview -or $IncludeManualAnalysis -or $IncludeMatchSlots -or $IncludeMatchArchive)) {
    throw 'MatchMenusOnly selects both menu phases on its own.'
}
if ($OpeningsOnly -and ($Distribution -or $AnalysisOnly)) { throw 'OpeningsOnly uses its own isolated sanity phase.' }
if ($IncludeOpenings -and ($OpeningsOnly -or $AnalysisOnly)) { throw 'IncludeOpenings extends the board sanity phases.' }
if ($GameReviewOnly -and ($AnalysisOnly -or $OpeningsOnly -or $IncludeOpenings -or $IncludeGameReview)) {
    throw 'GameReviewOnly selects the game review phase on its own.'
}
if ($IncludeGameReview -and ($AnalysisOnly -or $OpeningsOnly)) { throw 'IncludeGameReview extends the board sanity phases.' }
if ($ManualAnalysisOnly -and ($Distribution -or $AnalysisOnly -or $OpeningsOnly -or $GameReviewOnly -or $IncludeOpenings -or $IncludeGameReview -or $IncludeManualAnalysis -or $IncludeMatchSlots)) {
    throw 'ManualAnalysisOnly uses the isolated sanity package and its own phase.'
}
if (($IncludeManualAnalysis -or $IncludeMatchSlots -or $IncludeMatchArchive) -and
        ($OpeningsOnly -or $GameReviewOnly -or $ManualAnalysisOnly -or $MatchSlotsOnly -or $MatchArchiveOnly)) {
    throw 'Included phases require board or match analysis sanity.'
}
if ($MatchSlotsOnly -and ($Distribution -or $AnalysisOnly -or $OpeningsOnly -or $GameReviewOnly -or $ManualAnalysisOnly -or $IncludeOpenings -or $IncludeGameReview)) {
    throw 'MatchSlotsOnly uses the isolated sanity package and its own phase.'
}
if ($MatchArchiveOnly -and ($Distribution -or $AnalysisOnly -or $OpeningsOnly -or $GameReviewOnly -or $ManualAnalysisOnly -or $MatchSlotsOnly -or $IncludeOpenings -or $IncludeGameReview)) {
    throw 'MatchArchiveOnly uses the isolated sanity package and its own phase.'
}
$native = 'android/app/build/outputs/apk'
$apk = Join-Path $root "$native/$variant/app-$variant.apk"
$tests = Join-Path $root "$native/androidTest/$variant/app-$variant-androidTest.apk"
foreach ($file in @($apk, $tests)) { if (!(Test-Path -LiteralPath $file -PathType Leaf)) { throw "Missing $file" } }
$run = Join-Path $root ('target/android-sanity-' + [guid]::NewGuid().ToString('N'))
$token = [guid]::NewGuid().ToString('N')
$lease = [ordered]@{ active=$true; leaseId=$token; serial=$Serial; owner='J7 board sanity'; run=$run; acquiredUtc=[datetime]::UtcNow.ToString('o') }
$adbArgs = @('-H', $AdbHost, '-P', "$AdbPort", '-s', $Serial)

function Set-Lease([bool]$Acquire) {
    $deadline = [datetime]::UtcNow.AddSeconds(3)
    while ($true) {
        try {
            $stream = [IO.File]::Open($LeaseFile, [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
            break
        } catch {
            $code = $_.Exception.GetBaseException().HResult -band 0xffff
            if ($code -notin 32, 33 -or [datetime]::UtcNow -ge $deadline) { throw }
            Start-Sleep -Milliseconds 100
        }
    }
    try {
        $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::UTF8, $true, 1024, $true)
        try { $previous = $reader.ReadToEnd() } finally { $reader.Dispose() }
        $current = if ($previous) { $previous | ConvertFrom-Json }
        if ($Acquire) {
            if ($current.active) { throw "Android reservation is owned by $($current.owner) on $($current.serial)." }
        } else {
            if ($current.leaseId -cne $token) { throw 'Android reservation ownership changed.' }
            $lease.active = $false
            $lease.releasedUtc = [datetime]::UtcNow.ToString('o')
        }
        $stream.SetLength(0)
        $stream.Position = 0
        $bytes = [Text.Encoding]::UTF8.GetBytes(($lease | ConvertTo-Json))
        $stream.Write($bytes)
    } finally { $stream.Dispose() }
}

function Invoke-Device([string[]]$Command) {
    $current = Get-Content -Raw -LiteralPath $LeaseFile | ConvertFrom-Json
    if (!$current.active -or $current.leaseId -cne $token) { throw 'Android reservation ownership changed.' }
    & $adbExe @adbArgs @Command
    if ($LASTEXITCODE) { throw "ADB failed for $($Command[0])" }
}

function Receive-Frame([string]$Name, [string]$Directory = $run) {
    $start = [Diagnostics.ProcessStartInfo]::new($adbExe)
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $source = if ($Distribution) { @('cat',"/sdcard/Android/data/$app/files/j7-$Name.png") }
              else { @('run-as',$app,'cat',"files/j7-$Name.png") }
    foreach ($argument in ($adbArgs + @('exec-out') + $source)) { $start.ArgumentList.Add($argument) }
    $current = Get-Content -Raw -LiteralPath $LeaseFile | ConvertFrom-Json
    if (!$current.active -or $current.leaseId -cne $token) { throw 'Android reservation ownership changed.' }
    $process = [Diagnostics.Process]::Start($start)
    $png = [IO.File]::Create((Join-Path $Directory "$Name.png"))
    try {
        $process.StandardOutput.BaseStream.CopyTo($png)
        $process.WaitForExit()
        if ($process.ExitCode) { Write-Warning "ADB could not retrieve $Name.png." }
    } finally { $png.Dispose(); $process.Dispose() }
}

function Invoke-OpeningSanity([string]$Directory) {
    $openingReceipt = Join-Path $Directory 'openings.txt'
    $lines = @(Invoke-Device ($instrument + @('-e','phase','openings',$runner)))
    $lines | Set-Content -LiteralPath $openingReceipt -Encoding utf8NoBOM
    $passed = @($lines | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: check_\d+=.*\ttrue$' })
    if ($lines -cnotcontains 'INSTRUMENTATION_RESULT: result=PASS' -or $passed.Count -ne 26) {
        Receive-Frame 'failure' $Directory
        throw "Opening lessons did not pass. See $Directory."
    }
    foreach ($frame in 'opening-catalog','opening-catalog-end','opening-hint','opening-ai','opening-replay') {
        Receive-Frame $frame $Directory
    }
    [ordered]@{status='PASS'; scope='openings-only'; device=$Serial; api=$api; apkSha256=$hash;
        sourceCommit=$SourceCommit; variant=$variant; assertions=$passed.Count;
        receiptSha256=(Get-FileHash -LiteralPath $openingReceipt).Hash.ToLowerInvariant()} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Directory 'execution.json') -Encoding utf8NoBOM
}

function Invoke-FocusedSanity([string]$Directory, [string]$Phase, [int]$Expected, [string]$RunId = "") {
    $reviewReceipt = Join-Path $Directory "$Phase.txt"
    $arguments = @('-e','phase',$Phase)
    if ($RunId) { $arguments += @('-e','runId',$RunId) }
    $lines = @(Invoke-Device ($instrument + $arguments + @($runner)))
    $lines | Set-Content -LiteralPath $reviewReceipt -Encoding utf8NoBOM
    $passed = @($lines | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: check_\d+=.*\ttrue$' })
    if ($lines -cnotcontains 'INSTRUMENTATION_RESULT: result=PASS' -or $passed.Count -ne $Expected) {
        Receive-Frame 'failure' $Directory
        throw "Android phase $Phase did not pass. See $Directory."
    }
    $frames = @($Phase)
    switch ($Phase) {
        'match-slots' { $frames += 'saved-games' }
        'match-archive' { $frames += 'finished-games' }
        'game-review' { $frames += 'game-review-navigation', 'game-review-line', 'game-review-comparison', 'game-review-actions' }
        'game-review-why' { $frames += 'game-review-why-recommended', 'game-review-why-score' }
    }
    $captureHashes = [ordered]@{}
    foreach ($frame in $frames) {
        Receive-Frame $frame $Directory
        $file = "$frame.png"
        $captureHashes[$file] = (Get-FileHash -LiteralPath (Join-Path $Directory $file)).Hash.ToLowerInvariant()
    }
    $execution = [ordered]@{status='PASS'; scope=$Phase; device=$Serial; api=$api; apkSha256=$hash;
        sourceCommit=$SourceCommit; variant=$variant; assertions=$passed.Count;
        receiptSha256=(Get-FileHash -LiteralPath $reviewReceipt).Hash.ToLowerInvariant(); captureSha256=$captureHashes}
    if ($RunId) {
        $nativeRunIds = @($lines | Where-Object { $_.StartsWith('INSTRUMENTATION_RESULT: runId=') })
        if ($nativeRunIds.Count -ne 1 -or $nativeRunIds[0] -cne "INSTRUMENTATION_RESULT: runId=$RunId") {
            throw "Unexpected run identity in Android phase $Phase."
        }
        $execution.runId = $RunId
    }
    $execution | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Directory 'execution.json') -Encoding utf8NoBOM
}

function Invoke-IncludedSanity([string]$Directory, [string]$Phase, [int]$Expected) {
    $clear = Invoke-Device @('shell','pm','clear',$app)
    if ($clear -cnotcontains 'Success') { throw "Could not reset for $Phase." }
    $phaseDirectory = Join-Path $Directory $Phase
    $null = New-Item -ItemType Directory $phaseDirectory
    Invoke-FocusedSanity $phaseDirectory $Phase $Expected
}

function Invoke-GameReviewSanity([string]$Directory) {
    $reviewRunId = if ($QualificationRunId) { $QualificationRunId } else { [guid]::NewGuid().ToString('N') }
    Invoke-FocusedSanity $Directory 'game-review' 23 $reviewRunId
    $clear = Invoke-Device @('shell','pm','clear',$app)
    if ($clear -cnotcontains 'Success') { throw 'Could not reset for the archived review fixture.' }
    $why = Join-Path $Directory 'why'
    $restore = Join-Path $Directory 'why-restore'
    $null = New-Item -ItemType Directory $why, $restore
    Invoke-FocusedSanity $why 'game-review-why' 5 $reviewRunId
    Invoke-Device @('shell','am','force-stop',$app)
    Invoke-FocusedSanity $restore 'game-review-why-restore' 2 $reviewRunId
    $pids = @(Get-Content -LiteralPath (Join-Path $why 'game-review-why.txt'), (Join-Path $restore 'game-review-why-restore.txt') |
        Where-Object { $_ -match '^INSTRUMENTATION_RESULT: pid=[1-9][0-9]*$' })
    if ($pids.Count -ne 2 -or $pids[0] -ceq $pids[1]) {
        throw 'Review restore must run in a different Android process.'
    }
}

function Invoke-SelectedMatchSanity([string]$Directory) {
    if ($IncludeManualAnalysis) { Invoke-IncludedSanity $Directory 'manual-analysis' 15 }
    if ($IncludeMatchSlots) { Invoke-IncludedSanity $Directory 'match-slots' 2 }
    if ($IncludeMatchArchive) { Invoke-IncludedSanity $Directory 'match-archive' 2 }
}

Set-Lease $true
$installed = $false
$installedTests = $false
try {
    $null = New-Item -ItemType Directory $run
    Write-Output $run
    $api = (Invoke-Device @('shell','getprop','ro.build.version.sdk') | Out-String).Trim()
    $hash = (Get-FileHash -LiteralPath $apk).Hash.ToLowerInvariant()
    if ($Distribution) {
        $existing = Invoke-Device @('shell','pm','list','packages',$app)
        if ($existing -contains "package:$app" -or $existing -contains "package:$app.test") { throw 'Distribution sanity requires a fresh emulator package.' }
        Invoke-Device @('install',$apk)
        $installed = $true
        Invoke-Device @('install','-t',$tests)
        $installedTests = $true
        $paths = @(Invoke-Device @('shell','pm','path',$app))
        if ($paths.Count -ne 1 -or $paths[0] -notmatch '^package:(/.+/base\.apk)$') { throw 'Expected one installed base APK.' }
        $onDevice = Invoke-Device @('shell','sha256sum',$Matches[1])
        if (($onDevice -split '\s+')[0] -cne $hash) { throw 'Installed APK differs from the signed release candidate.' }
    } else {
        Invoke-Device @('install','-r',$apk)
        Invoke-Device @('install','-r','-t',$tests)
        $clear = Invoke-Device @('shell','pm','clear',$app)
        if ($clear -notcontains 'Success') { throw 'Could not reset the isolated sanity app.' }
    }
    $receipt = Join-Path $run 'prepare.txt'
    $restore = Join-Path $run 'restore.txt'
    $runner = "$app.test/ro.atf.chess.android.BoardSanity"
    $instrument = @('shell','am','instrument','-w','-r')
    if ($Distribution) { $instrument += @('-e','externalFrames','true') }
    if ($GameReviewOnly) {
        Invoke-GameReviewSanity $run
        return
    }
    if ($ManualAnalysisOnly) {
        Invoke-FocusedSanity $run 'manual-analysis' 15
        return
    }
    if ($MatchSlotsOnly) {
        Invoke-FocusedSanity $run 'match-slots' 2
        return
    }
    if ($MatchArchiveOnly) {
        Invoke-FocusedSanity $run 'match-archive' 2
        return
    }
    if ($MatchMenusOnly) {
        Invoke-FocusedSanity $run 'match-slots' 2
        Invoke-IncludedSanity $run 'match-archive' 2
        return
    }
    if ($OpeningsOnly) {
        Invoke-OpeningSanity $run
        return
    }
    if ($AnalysisOnly) {
        $pids = @()
        $checks = [ordered]@{}
        foreach ($phase in 'analysis-prepare','analysis-restore') {
            $lines = @(Invoke-Device ($instrument + @('-e','phase',$phase,$runner)))
            $lines | Set-Content -LiteralPath (Join-Path $run "$phase.txt") -Encoding utf8NoBOM
            $passed = @($lines | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: check_\d+=.*\ttrue$' })
            $expected = if ($phase -eq 'analysis-prepare') { 17 } else { 10 }
            if ($lines -cnotcontains 'INSTRUMENTATION_RESULT: result=PASS' -or $passed.Count -ne $expected) {
                Receive-Frame 'failure'
                throw "Analysis phase $phase did not pass. See $run."
            }
            $checks[$phase] = $passed.Count
            $pidLine = @($lines | Where-Object { $_ -match '^INSTRUMENTATION_RESULT: pid=[0-9]+$' })
            if ($pidLine.Count -ne 1) { throw "Missing process identity for $phase." }
            $pids += $pidLine[0].Split('=')[1]
            if ($phase -eq 'analysis-prepare') { Receive-Frame 'match-analysis'; Receive-Frame 'match-captures' }
            else { Receive-Frame 'match-setup'; Receive-Frame 'match-black' }
            Invoke-Device @('shell','am','force-stop',$app)
        }
        if ($pids[0] -eq $pids[1]) { throw 'The analysis restore phase reused the same process.' }
        Invoke-SelectedMatchSanity $run
        $receiptHashes = [ordered]@{}
        foreach ($phase in $checks.Keys) {
            $receiptHashes[$phase] = (Get-FileHash -LiteralPath (Join-Path $run "$phase.txt")).Hash.ToLowerInvariant()
        }
        [ordered]@{status='PASS'; scope='analysis-only'; device=$Serial; api=$api; apkSha256=$hash;
            sourceCommit=$SourceCommit; variant=$variant; pids=$pids; assertions=$checks; receipts=$receiptHashes} |
            ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'execution.json') -Encoding utf8NoBOM
        return
    }
    Invoke-Device ($instrument + @('-e','phase','prepare',$runner)) |
        Set-Content -LiteralPath $receipt -Encoding utf8NoBOM
    if (Select-String -Quiet -LiteralPath $receipt -SimpleMatch 'INSTRUMENTATION_RESULT: result=PASS') {
        Receive-Frame 'hint-piece'
        Receive-Frame 'hint-solution'
        Receive-Frame 'mate-two'
        Receive-Frame 'menu'
        Receive-Frame 'tactics-streak'
        Invoke-Device @('shell','am','force-stop',$app)
        Invoke-Device ($instrument + @('-e','phase','restore',$runner)) |
            Set-Content -LiteralPath $restore -Encoding utf8NoBOM
    }
    if ((Test-Path -LiteralPath $restore) -and (Select-String -Quiet -LiteralPath $restore -SimpleMatch 'INSTRUMENTATION_RESULT: result=PASS')) {
        Receive-Frame 'board'
        Receive-Frame 'milestone'
        Receive-Frame 'mate-three'
        Invoke-Device @('shell','am','force-stop',$app)
        $clear = Invoke-Device @('shell','pm','clear',$app)
        if ($clear -notcontains 'Success') { throw 'Could not reset for computer-match sanity.' }
        foreach ($phase in 'match-prepare','match-restore','match-settled') {
            $matchReceipt = Join-Path $run "$phase.txt"
            Invoke-Device ($instrument + @('-e','phase',$phase,$runner)) |
                Set-Content -LiteralPath $matchReceipt -Encoding utf8NoBOM
            if (!(Select-String -Quiet -LiteralPath $matchReceipt -SimpleMatch 'INSTRUMENTATION_RESULT: result=PASS')) {
                Receive-Frame 'failure'
                break
            }
            if ($phase -eq 'match-restore') {
                Receive-Frame 'match-resumed'
                Receive-Frame 'match-history'
            }
            Invoke-Device @('shell','am','force-stop',$app)
        }
    } else {
        Receive-Frame 'failure'
    }
    $settled = Join-Path $run 'match-settled.txt'
    if ($IncludeOpenings -and (Test-Path -LiteralPath $settled) -and
            (Select-String -Quiet -LiteralPath $settled -SimpleMatch 'INSTRUMENTATION_RESULT: result=PASS')) {
        $clear = Invoke-Device @('shell','pm','clear',$app)
        if ($clear -notcontains 'Success') { throw 'Could not reset for opening lessons.' }
        $openingDirectory = Join-Path $run 'openings'
        $null = New-Item -ItemType Directory $openingDirectory
        Invoke-OpeningSanity $openingDirectory
    }
    if ($IncludeGameReview -and (Test-Path -LiteralPath $settled) -and
            (Select-String -Quiet -LiteralPath $settled -SimpleMatch 'INSTRUMENTATION_RESULT: result=PASS')) {
        Invoke-Device @('shell','am','force-stop',$app)
        $reviewDirectory = Join-Path $run 'game-review'
        $null = New-Item -ItemType Directory $reviewDirectory
        Invoke-GameReviewSanity $reviewDirectory
    }
    if ((Test-Path -LiteralPath $settled) -and
            (Select-String -Quiet -LiteralPath $settled -SimpleMatch 'INSTRUMENTATION_RESULT: result=PASS')) {
        Invoke-SelectedMatchSanity $run
    }
} finally {
    try {
        if ($installedTests) { Invoke-Device @('uninstall',"$app.test") }
    } finally {
        try { if ($installed) { Invoke-Device @('uninstall',$app) } } finally { Set-Lease $false }
    }
}

Push-Location $root
try {
    mvn -B -ntp test '-Dmaven.repo.local=target/maven-repo' '-Dtest=AndroidSanityPublication#publishBoardSanity' "-Dchess.android.receipt=$receipt"
    $publication = $LASTEXITCODE
    $report = Join-Path $run 'android-board-sanity.html'
    $receiptHashes = [ordered]@{}
    $nativeChecks = 0
    foreach ($file in Get-ChildItem -LiteralPath $run -Filter '*.txt' -File) {
        $nativeChecks += @(Select-String -LiteralPath $file.FullName -Pattern '^INSTRUMENTATION_RESULT: check_').Count
        $receiptHashes[$file.BaseName] = (Get-FileHash -LiteralPath $file.FullName).Hash.ToLowerInvariant()
    }
    [ordered]@{device=$Serial; api=$api; apkSha256=$hash; sourceCommit=$SourceCommit; assertions=$nativeChecks;
        receipts=$receiptHashes; variant=$variant;
        status=$(if ($publication) {'FAIL'} else {'PASS'});
        extentSha256=$(if (Test-Path -LiteralPath $report) {(Get-FileHash -LiteralPath $report).Hash.ToLowerInvariant()})} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $run 'execution.json') -Encoding utf8NoBOM
    if ($publication) { throw "Android sanity did not qualify; see $run" }
} finally { Pop-Location }
