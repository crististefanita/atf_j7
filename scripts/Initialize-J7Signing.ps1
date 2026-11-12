#requires -Version 7.0
[CmdletBinding()]
param(
    [string]$DataRoot = 'C:\work\chess_back',
    [string]$JavaHome = 'C:\tools\Java21\jdk-21.0.12.1+1',
    [switch]$StartNewIdentity
)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$dataRoot = [IO.Path]::GetFullPath($DataRoot)
$tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/')
if ($dataRoot.Equals($projectRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $dataRoot.StartsWith($projectRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
    $dataRoot.Equals($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
    $dataRoot.StartsWith($tempRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Signing data must stay outside the source checkout and the operating-system temporary directory.'
}

$directory = Join-Path $dataRoot 'infrastructure/signing'
$store = Join-Path $directory 'chesslab-release.p12'
$credential = Join-Path $directory 'password.dpapi'
$identity = Join-Path $directory 'identity.json'
$certificate = Join-Path $directory 'certificate.der'
$files = @($store, $credential, $identity, $certificate)
$present = @($files | Where-Object { Test-Path -LiteralPath $_ })
if ($present.Count -gt 0 -and $present.Count -ne $files.Count) {
    throw 'Incomplete J7 signing identity. Preserve its files and recover explicitly; do not rotate automatically.'
}

if ($present.Count -eq 0) {
    $publishedIdentity = Join-Path $projectRoot 'release/0.11.2/reports/qualification.json'
    $previousFingerprint = $null
    if (Test-Path -LiteralPath $publishedIdentity) {
        $previousFingerprint = (Get-Content -Raw -LiteralPath $publishedIdentity | ConvertFrom-Json).signingCertificateSha256
    }
    if ($previousFingerprint -and !$StartNewIdentity) {
        throw 'The published J7 signer is missing. Recover it, or explicitly use -StartNewIdentity and accept a clean-install transition.'
    }

    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($principal in @($sid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new(
            $principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $directory -AclObject $acl

    $password = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    $securePassword = ConvertTo-SecureString $password -AsPlainText -Force
    $securePassword | ConvertFrom-SecureString | Set-Content -LiteralPath $credential -Encoding ascii
    $previousEnvironmentPassword = $env:J7_SIGNING_PASSWORD
    try {
        $env:J7_SIGNING_PASSWORD = $password
        $keytool = Join-Path $JavaHome 'bin/keytool.exe'
        & $keytool -genkeypair -keystore $store -storetype PKCS12 -alias chesslab-release `
            -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Chess Lab,OU=J7 Android distribution' `
            -storepass:env J7_SIGNING_PASSWORD -keypass:env J7_SIGNING_PASSWORD *> (Join-Path $directory 'provisioning.log')
        if ($LASTEXITCODE -ne 0) { throw 'J7 signing key generation failed; inspect the private provisioning log.' }
        & $keytool -exportcert -alias chesslab-release -keystore $store -storepass:env J7_SIGNING_PASSWORD `
            -file $certificate *> (Join-Path $directory 'certificate-export.log')
        if ($LASTEXITCODE -ne 0) { throw 'J7 public signing certificate export failed.' }
        $record = [ordered]@{
            schemaVersion = 1
            alias = 'chesslab-release'
            store = $store
            credential = $credential
            certificateSha256 = (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()
            previousPublishedCertificateSha256 = $previousFingerprint
            cleanInstallRequired = [bool]$previousFingerprint
            createdUtc = [datetime]::UtcNow.ToString('o')
            protection = 'Windows DPAPI for current user; private directory ACL'
        }
        $record | ConvertTo-Json | Set-Content -LiteralPath $identity -Encoding utf8NoBOM
    } finally {
        if ($null -eq $previousEnvironmentPassword) { Remove-Item Env:J7_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
        else { $env:J7_SIGNING_PASSWORD = $previousEnvironmentPassword }
        $password = $null
        $securePassword = $null
    }
} else {
    $record = Get-Content -Raw -LiteralPath $identity | ConvertFrom-Json
    if ($record.alias -ne 'chesslab-release' -or $record.store -ne $store -or $record.credential -ne $credential -or
        $record.certificateSha256 -ne (Get-FileHash -LiteralPath $certificate -Algorithm SHA256).Hash.ToLowerInvariant()) {
        throw 'Existing J7 signing identity does not match its certificate manifest.'
    }
}

$protectedPassword = (Get-Content -Raw -LiteralPath $credential).Trim()
$securePassword = ConvertTo-SecureString -String $protectedPassword
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$plainPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
$previousStore = $env:J7_SIGNING_STORE
$previousPassword = $env:J7_SIGNING_PASSWORD
$verificationCertificate = Join-Path $directory ('verify-' + [guid]::NewGuid().ToString('N') + '.der')
try {
    $env:J7_SIGNING_PASSWORD = $plainPassword
    $keytool = Join-Path $JavaHome 'bin/keytool.exe'
    & $keytool -exportcert -alias chesslab-release -keystore $store -storepass:env J7_SIGNING_PASSWORD `
        -file $verificationCertificate *> (Join-Path $directory 'identity-verification.log')
    if ($LASTEXITCODE -ne 0) { throw 'The stored keystore cannot export the expected release alias.' }
    $observedFingerprint = (Get-FileHash -LiteralPath $verificationCertificate -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($observedFingerprint -ne $record.certificateSha256) {
        throw 'The stored keystore certificate differs from its protected identity manifest.'
    }
} finally {
    if ($null -eq $previousStore) { Remove-Item Env:J7_SIGNING_STORE -ErrorAction SilentlyContinue }
    else { $env:J7_SIGNING_STORE = $previousStore }
    if ($null -eq $previousPassword) { Remove-Item Env:J7_SIGNING_PASSWORD -ErrorAction SilentlyContinue }
    else { $env:J7_SIGNING_PASSWORD = $previousPassword }
    Remove-Item -LiteralPath $verificationCertificate -Force -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $protectedPassword = $null
    $securePassword = $null
}
$env:J7_SIGNING_STORE = $store
$env:J7_SIGNING_PASSWORD = $plainPassword
$plainPassword = $null

Get-Content -Raw -LiteralPath $identity
