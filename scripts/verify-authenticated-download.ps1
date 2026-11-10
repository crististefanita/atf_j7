#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$')][string]$Repository,
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{40}$')][string]$CandidateCommit,
    [Parameter(Mandatory)][string]$QualifiedApk,
    [Parameter(Mandatory)][string]$Classes,
    [Parameter(Mandatory)][string]$OutputDirectory
)
$ErrorActionPreference = 'Stop'
$tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (!$output.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or (Test-Path -LiteralPath $output)) {
    throw 'Use a fresh output folder in TMP.'
}
$env:GCM_INTERACTIVE = 'never'
$env:GIT_TERMINAL_PROMPT = '0'
$credential = @{}
@('protocol=https', 'host=github.com', '') | git credential fill | ForEach-Object {
    $separator = $_.IndexOf('=')
    if ($separator -gt 0) { $credential[$_.Substring(0, $separator)] = $_.Substring($separator + 1) }
}
if ($LASTEXITCODE -or !$credential.password) { throw 'No authenticated GitHub credential is available.' }
$headers = @{ Authorization = 'Bearer ' + $credential.password; Accept = 'application/vnd.github+json' }
$api = 'https://api.github.com'
$account = Invoke-RestMethod "$api/user" -Headers $headers
$repo = Invoke-RestMethod "$api/repos/$Repository" -Headers $headers
$source = Invoke-RestMethod "$api/repos/$Repository/branches/$($repo.default_branch)" -Headers $headers
$readmeCommit = $source.commit.sha
$readme = Invoke-RestMethod "$api/repos/$Repository/readme?ref=$readmeCommit" -Headers $headers
if ($readme.path -cne 'README.md' -or $readme.encoding -cne 'base64') { throw 'Unexpected default-branch README response.' }
$null = New-Item -ItemType Directory $output
$readmeFile = Join-Path $output 'README.md'
[IO.File]::WriteAllBytes($readmeFile, [Convert]::FromBase64String($readme.content))
$links = @(& java -cp $Classes ro.atf.chess.release.ReleaseGate links $readmeFile)
if ($LASTEXITCODE -or $links.Count -ne 2) { throw 'Expected two canonical links in Jocul.' }

$tag = "v$Version"
$release = Invoke-RestMethod "$api/repos/$Repository/releases/tags/$tag" -Headers $headers
if ($release.draft -or $release.html_url -cne $links[0]) { throw 'README release link does not match the published release.' }
$tagRef = (Invoke-RestMethod "$api/repos/$Repository/git/ref/tags/$tag" -Headers $headers).object
while ($tagRef.type -eq 'tag') { $tagRef = (Invoke-RestMethod "$api/repos/$Repository/git/tags/$($tagRef.sha)" -Headers $headers).object }
if ($tagRef.type -cne 'commit' -or $tagRef.sha -cne $CandidateCommit) { throw 'Published tag does not identify the qualified commit.' }
$assets = @($release.assets | Where-Object { $_.browser_download_url -ceq $links[1] -and $_.state -ceq 'uploaded' })
if ($assets.Count -ne 1 -or $assets[0].name -cne "ChessLab-$Version.apk") { throw 'README download link does not identify the published APK.' }
$asset = $assets[0]
$download = Join-Path $output $asset.name
$headers.Accept = 'application/octet-stream'
$response = Invoke-WebRequest "$api/repos/$Repository/releases/assets/$([long]$asset.id)" -Headers $headers -OutFile $download -PassThru
$size = (Get-Item -LiteralPath $download).Length
if ($size -ne $asset.size) { throw 'Downloaded file size differs from GitHub asset metadata.' }
$sha = (Get-FileHash -LiteralPath $download).Hash.ToLowerInvariant()
if ($asset.digest -and $asset.digest -cne "sha256:$sha") { throw 'Downloaded file hash differs from GitHub asset metadata.' }
& java -cp $Classes ro.atf.chess.release.ReleaseGate publication $Version $CandidateCommit $QualifiedApk $tagRef.sha $readmeCommit `
    $readmeFile $Repository $links[1] 'AUTHENTICATED' ([int]$response.StatusCode) $download
if ($LASTEXITCODE) { throw 'Download verification rejected the response.' }
[ordered]@{
    status = 'PASS'; observedUtc = [datetime]::UtcNow.ToString('o'); account = $account.login; channel = 'GitHub API authenticated'
    repository = $Repository; readmeCommit = $readmeCommit; readmeBlob = $readme.sha; tag = $tag; targetCommit = $tagRef.sha
    releaseUrl = $links[0]; assetUrl = $links[1]; assetId = $asset.id; httpStatus = [int]$response.StatusCode
    bytes = $size; sha256 = $sha; zipCrc = 'PASS'
} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $output 'distribution-verification.json') -Encoding utf8NoBOM
