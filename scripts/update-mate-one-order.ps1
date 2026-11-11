[CmdletBinding()]
param(
    [string]$MavenRepo = (Join-Path $env:USERPROFILE '.m2/repository'),
    [string]$ChessJs,
    [switch]$Write,
    [switch]$RefreshOracle,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
if ($RefreshOracle -and (!$Write -or !$ChessJs)) { throw '-RefreshOracle requires -Write and -ChessJs.' }
$project = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$repository = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($MavenRepo)
if ($ChessJs) { $ChessJs = (Resolve-Path -LiteralPath $ChessJs).Path }
Push-Location -LiteralPath $project
try {
    $mavenArgs = @('-B', '-ntp', "-Dmaven.repo.local=$repository", 'compile')
    if ($Offline) { $mavenArgs = @('-o') + $mavenArgs }
    & mvn @mavenArgs
    if ($LASTEXITCODE -ne 0) { throw 'Could not compile the mate-in-one catalog.' }

    $snakeYaml = Join-Path $repository 'org/yaml/snakeyaml/2.2/snakeyaml-2.2.jar'
    $classPath = "atf-tests/target/classes$([IO.Path]::PathSeparator)$snakeYaml"
    $entryPoint = 'ro.atf.chess.QuestDifficulty'
    $order = & java -cp $classPath $entryPoint
    if ($LASTEXITCODE -ne 0) { throw 'Could not generate the mate-in-one order.' }

    $oraclePath = Join-Path $project 'chess-core/src/main/resources/quest-oracle.yaml'
    if ($ChessJs) {
        $catalog = & java -cp $classPath $entryPoint --oracle-input
        if ($LASTEXITCODE -ne 0) { throw 'Could not export the mate-in-one catalog.' }
        $oracleArgs = @('scripts/verify-quest-oracle.cjs', $ChessJs, '-')
        if (!$RefreshOracle) { $oracleArgs += $oraclePath }
        $proof = $catalog | & node @oracleArgs
        if ($LASTEXITCODE -ne 0) { throw 'The independent mate-in-one check failed.' }
        $proof | Write-Output
    }

    $orderPath = Join-Path $project 'chess-core/src/main/resources/quest-order.yaml'
    $generated = ($order -join "`n") + "`n"
    if ($Write) {
        if ($RefreshOracle) {
            $digests = @($proof | Where-Object { $_ -cmatch '^(legal-moves|mate-proof)-sha256: [0-9a-f]{64}$' })
            if ($digests.Count -ne 2 -or $digests[0] -notlike 'legal-moves-*' -or $digests[1] -notlike 'mate-proof-*') {
                throw 'The independent engine did not emit both proof fingerprints.'
            }
            $oracle = (@('oracle: chess.js-1.4.0') + $digests) -join "`n"
            [IO.File]::WriteAllText($oraclePath, $oracle + "`n")
            Write-Output "Updated $oraclePath"
        }
        [IO.File]::WriteAllText($orderPath, $generated)
        Write-Output "Updated $orderPath"
    } elseif ([IO.File]::ReadAllText($orderPath).Replace("`r`n", "`n") -cne $generated) {
        throw 'The mate-in-one order is stale. Review the catalog, then run again with -Write.'
    } else {
        Write-Output 'The checked-in mate-in-one order matches the YAML profile.'
    }
} finally {
    Pop-Location
}
