#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Adb,
    [Parameter(Mandatory)][string]$Serial,
    [Parameter(Mandatory)][string]$LeaseFile,
    [string]$AdbHost = 'localhost',
    [int]$AdbPort = 5037,
    [switch]$Distribution,
    [string]$SourceCommit
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
if (!$root.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) { throw 'Run from a dedicated TMP checkout.' }
$adbExe = (Get-Command $Adb -CommandType Application).Source
$app = if ($Distribution) { 'ro.atf.chess.android' } else { 'ro.atf.chess.android.sanity' }
$variant = if ($Distribution) { 'release' } else { 'sanity' }
if ($Distribution -and $SourceCommit -cnotmatch '^[a-f0-9]{40}$') { throw 'Distribution requires the exact source commit.' }
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

function Receive-Frame([string]$Name) {
    $start = [Diagnostics.ProcessStartInfo]::new($adbExe)
    $start.UseShellExecute = $false
    $start.RedirectStandardOutput = $true
    $source = if ($Distribution) { @('cat',"/sdcard/Android/data/$app/files/j7-$Name.png") }
              else { @('run-as',$app,'cat',"files/j7-$Name.png") }
    foreach ($argument in ($adbArgs + @('exec-out') + $source)) { $start.ArgumentList.Add($argument) }
    $current = Get-Content -Raw -LiteralPath $LeaseFile | ConvertFrom-Json
    if (!$current.active -or $current.leaseId -cne $token) { throw 'Android reservation ownership changed.' }
    $process = [Diagnostics.Process]::Start($start)
    $png = [IO.File]::Create((Join-Path $run "$Name.png"))
    try {
        $process.StandardOutput.BaseStream.CopyTo($png)
        $process.WaitForExit()
        if ($process.ExitCode) { Write-Warning "ADB could not retrieve $Name.png." }
    } finally { $png.Dispose(); $process.Dispose() }
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
} finally {
    try {
        if ($installedTests) { Invoke-Device @('uninstall',"$app.test") }
    } finally {
        try { if ($installed) { Invoke-Device @('uninstall',$app) } } finally { Set-Lease $false }
    }
}

Push-Location $root
try {
    mvn -B -ntp test '-Dmaven.repo.local=target/maven-repo' '-Dtest=AndroidSanityPublication' "-Dchess.android.receipt=$receipt"
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
