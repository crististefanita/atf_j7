#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Apk,
    [ValidateSet('UPGRADE', 'CLEAN_INSTALL')][string]$Mode = 'UPGRADE',
    [string]$BaselineQualification = '',
    [string]$Output
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$apkPath = [IO.Path]::GetFullPath($Apk)
$baselinePath = if ($BaselineQualification) {
    if ([IO.Path]::IsPathRooted($BaselineQualification)) { $BaselineQualification }
    else { Join-Path $projectRoot $BaselineQualification }
} else {
    $catalog = Get-Content -Raw -LiteralPath (Join-Path $projectRoot 'release/index.json') | ConvertFrom-Json
    $reports = Join-Path $projectRoot "release/$($catalog.currentVersion)/reports"
    $qualified = Join-Path $reports 'qualification.json'
    if (Test-Path -LiteralPath $qualified -PathType Leaf) { $qualified }
    else { Join-Path $reports 'build-artifact.json' }
}
if (!(Test-Path -LiteralPath $apkPath -PathType Leaf)) { throw 'Candidate APK is missing.' }
if (!(Test-Path -LiteralPath $baselinePath -PathType Leaf)) { throw 'Previous release qualification is missing.' }
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
if (!$sdk) { throw 'Set ANDROID_HOME or ANDROID_SDK_ROOT to locate apksigner.' }
$buildTools = Join-Path $sdk 'build-tools'
$versions = @(Get-ChildItem -LiteralPath $buildTools -Directory | Sort-Object { [version]$_.Name } -Descending)
if (!$versions.Count) { throw 'No Android build-tools are installed.' }
$apksigner = Join-Path $versions[0].FullName 'apksigner.bat'
$aapt = Join-Path $versions[0].FullName 'aapt.exe'
if (!(Test-Path -LiteralPath $apksigner -PathType Leaf)) { throw 'apksigner is missing from the newest installed build-tools.' }
if (!(Test-Path -LiteralPath $aapt -PathType Leaf)) { throw 'aapt is missing from the newest installed build-tools.' }
$badging = @(& $aapt dump badging $apkPath 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'Cannot read the APK manifest metadata.' }
$packageLine = $badging | Where-Object { $_ -match '^package:' } | Select-Object -First 1
$versionMatch = [regex]::Match([string]$packageLine, "versionName='([^']+)'")
if (!$versionMatch.Success) { throw 'The APK manifest does not contain a version name.' }
$candidateVersion = $versionMatch.Groups[1].Value
$signature = @(& $apksigner verify --verbose --print-certs $apkPath 2>&1)
if ($LASTEXITCODE -ne 0) { throw 'Candidate APK signature verification failed.' }
$certificateLine = $signature | Where-Object { $_ -match 'Signer #1 certificate SHA-256 digest:' } | Select-Object -First 1
if (!$certificateLine) { throw 'apksigner did not report a signer certificate.' }
$candidateFingerprint = (($certificateLine -split ':', 2)[1] -replace ':', '').Trim().ToLowerInvariant()
$baseline = Get-Content -Raw -LiteralPath $baselinePath | ConvertFrom-Json
$previousFingerprint = [string]$baseline.signingCertificateSha256
if (!$previousFingerprint) { $previousFingerprint = [string]$baseline.certificateSha256 }
if (!$previousFingerprint) { throw 'The published baseline has no recorded signing certificate fingerprint.' }
$classes = Join-Path $projectRoot 'atf-tests/target/classes'
$java = (Get-Command java -CommandType Application).Source
$result = @(& $java -cp $classes ro.atf.chess.release.ReleaseGate signing-continuity `
    $previousFingerprint $candidateFingerprint $Mode 2>&1)
if ($LASTEXITCODE -ne 0) { throw ($result -join [Environment]::NewLine) }
$receipt = [ordered]@{
    schemaVersion = 1
    previousVersion = [string]$baseline.version
    previousCertificateSha256 = $previousFingerprint
    candidateVersion = $candidateVersion
    candidateCertificateSha256 = $candidateFingerprint
    installMode = $Mode
    result = (($result | Select-Object -Last 1) -replace '^PASS signing-continuity ', '')
    apkSha256 = (Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant()
    observedUtc = [datetime]::UtcNow.ToString('o')
}
if ($Output) {
    $outputPath = [IO.Path]::GetFullPath($Output)
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $outputPath) | Out-Null
    $receipt | ConvertTo-Json | Set-Content -LiteralPath $outputPath -Encoding utf8NoBOM
}
$receipt | ConvertTo-Json -Depth 4
