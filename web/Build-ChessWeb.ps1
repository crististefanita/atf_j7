#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$WorkRoot,
    [string]$MavenRepository,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$root = [IO.Path]::GetFullPath($WorkRoot)
$relative = [IO.Path]::GetRelativePath($project, $root)
$parent = '..' + [IO.Path]::DirectorySeparatorChar
if ($relative -ne '..' -and !$relative.StartsWith($parent) -and ![IO.Path]::IsPathRooted($relative)) {
    throw 'Choose a Web build directory outside the checkout.'
}

$run = Join-Path $root ('chess-web-' + [guid]::NewGuid().ToString('N'))
$generatedPackage = Join-Path $run 'generated/ro/atf/chess'
$classes = Join-Path $run 'classes'
$site = Join-Path $run 'site'
$null = New-Item -ItemType Directory -Path $generatedPackage, $classes, $site -Force

$toolchain = Join-Path $PSScriptRoot 'tools'
$classpathFile = Join-Path $run 'toolchain.classpath'
$mavenArgs = @('-B', '-ntp', '-f', (Join-Path $toolchain 'teavm-dependencies.xml'))
if ($Offline) { $mavenArgs += '-o' }
if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$([IO.Path]::GetFullPath($MavenRepository))" }

$log = Join-Path $run 'toolchain.log'
& mvn @mavenArgs 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath' "-Dmdep.outputFile=$classpathFile" *> $log
if ($LASTEXITCODE -ne 0) { throw "Could not resolve the Web compiler dependencies. See $log" }
$classpath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()

$core = Join-Path $project 'chess-core/src/main'
$generator = Join-Path $toolchain 'GenerateChessResources.java'
$log = Join-Path $run 'generate-resources.log'
& java "-Djava.io.tmpdir=$run" -cp $classpath $generator (Join-Path $core 'resources') $generatedPackage *> $log
if ($LASTEXITCODE -ne 0) { throw "Could not generate the portable chess data. See $log" }

$coreLoader = Join-Path $core 'java/ro/atf/chess/ChessResourceLoader.java'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $core 'java') -Recurse -File -Filter '*.java' |
    Where-Object FullName -ne $coreLoader | Sort-Object FullName | ForEach-Object FullName)
$sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/java'), $generatedPackage -Recurse -File -Filter '*.java' |
    Sort-Object FullName | ForEach-Object FullName)
$sources += Join-Path $project 'atf-tests/src/test/java/ro/atf/chess/ChessWebParity.java'

# SnakeYAML runs on the build JVM. Keep it outside the classpath that TeaVM translates.
$webClasspath = ($classpath.Split([IO.Path]::PathSeparator) |
    Where-Object { [IO.Path]::GetFileName($_) -notlike 'snakeyaml-*.jar' }) -join [IO.Path]::PathSeparator
$log = Join-Path $run 'javac.log'
& javac --release 17 -encoding UTF-8 -cp $webClasspath -d $classes @sources *> $log
if ($LASTEXITCODE -ne 0) { throw "Could not compile the chess sources and Web adapter. See $log" }

$compiler = Join-Path $toolchain 'CompileChessWeb.java'
$compilerClasspath = @($classes, $webClasspath) -join [IO.Path]::PathSeparator
$log = Join-Path $run 'teavm.log'
& java "-Djava.io.tmpdir=$run" '-XX:ActiveProcessorCount=3' '-Xmx1g' -cp $compilerClasspath $compiler $site *> $log
if ($LASTEXITCODE -ne 0) { throw "Could not compile the TeaVM chess engine. See $log" }

$webFiles = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/web') -File
foreach ($file in $webFiles) {
    $target = Join-Path $site $file.Name
    if ($file.Extension -in '.html', '.css', '.mjs') {
        $text = [IO.File]::ReadAllText($file.FullName).Replace([string][char]13 + [char]10, [string][char]10)
        [IO.File]::WriteAllText($target, $text)
    } else {
        Copy-Item -LiteralPath $file.FullName -Destination $target
    }
}
$null = New-Item -ItemType File -Path (Join-Path $site '.nojekyll')
$site
