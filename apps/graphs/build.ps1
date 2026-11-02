param(
    [string] $Out = (Join-Path $PSScriptRoot 'out'),
    [switch] $Regenerate,
    $ScriptDir = $PSScriptRoot
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path (Split-Path $ScriptDir)
$cache = Join-Path ($env:RUNNER_TOOL_CACHE ?? [IO.Path]::GetTempPath()) 'apps-graphs'
$pins = @{
    yed = @{ version = '3.25.1'; url = 'https://www.yworks.com/resources/yed/demo/yEd-3.25.1.zip'; sha256 = '369258F7704B485445EDE54713042F0B57B4094E7073E4389D12C7015035CD3E'; root = 'yed-3.25.1' }
    kotlin = @{ version = '2.4.21'; url = 'https://github.com/JetBrains/kotlin/releases/download/v2.4.21/kotlin-compiler-2.4.21.zip'; sha256 = '7CC140E76DAF416A0424A5557F99AFD7CA89EBD463EA1101261E3E01C33E75CD'; root = 'kotlinc' }
}

function Get-Pinned([string] $name, [string] $local) {
    if ($local -and (Test-Path $local)) { return $local }
    $pin = $pins[$name]
    $dir = Join-Path $cache "$name-$($pin.version)"
    $root = Join-Path $dir $pin.root
    if (-not (Test-Path $root)) {
        New-Item -ItemType Directory -Force $dir | Out-Null
        $zip = Join-Path $dir "$name.zip"
        Invoke-WebRequest $pin.url -OutFile $zip
        $hash = (Get-FileHash $zip -Algorithm SHA256).Hash
        if ($hash -ne $pin.sha256) { Remove-Item $zip -Force; throw "apps/graphs/build.ps1: $name zip sha256 $hash, expected $($pin.sha256)" }
        Expand-Archive $zip -DestinationPath $dir -Force
        Remove-Item $zip -Force
    }
    $root
}

function Find-JavaHome {
    $homes = @(Get-ChildItem env: | Where-Object Name -match '^JAVA_HOME_(\d+)_(X64|ARM64)$' | Sort-Object { [int]($_.Name -replace '^JAVA_HOME_(\d+)_.*$', '$1') } -Descending | ForEach-Object Value)
    $homes += @($env:JAVA_HOME, (Join-Path $HOME 'scoop/apps/temurin-jdk/current')) | Where-Object { $_ }
    $onPath = Get-Command java -ErrorAction Ignore | Select-Object -First 1
    if ($onPath) {
        $target = (Get-Item $onPath.Source).ResolveLinkTarget($true)
        $homes += Split-Path (Split-Path ($target ? $target.FullName : $onPath.Source))
    }
    foreach ($javaHome in $homes) {
        $java = Get-ChildItem (Join-Path $javaHome 'bin') -Filter 'java*' -ErrorAction Ignore | Where-Object BaseName -eq 'java' | Select-Object -First 1
        if ($java) {
            $major = [int]((& $java.FullName -version 2>&1 | Select-Object -First 1) -replace '^.*version "(\d+).*$', '$1')
            if ($major -ge 17) { return $javaHome }
        }
    }
    throw 'apps/graphs/build.ps1: no JDK 17+ found (JAVA_HOME_<n>_X64, JAVA_HOME or scoop temurin-jdk)'
}

if ($Regenerate) {
    . (Join-Path $repo '../spiral/scripts/core.ps1')
    . (Join-Path $repo '../spiral/lib/spiral/lib.ps1')
    if (-not (BuildSpiral (Join-Path $ScriptDir 'main.spi') (Join-Path $ScriptDir 'graphs.kt') 'i574n.github/apps/graphs' -Backend Kotlin)) { throw 'apps/graphs/build.ps1: Spiral to Kotlin failed' }
}

$javaHome = Find-JavaHome
$env:JAVA_HOME = $javaHome
$yed = Join-Path (Get-Pinned 'yed' (Join-Path $HOME 'scoop/apps/yed/current')) 'yed.jar'
$kotlinHome = Get-Pinned 'kotlin' (Join-Path $HOME 'scoop/apps/kotlin/current')
$kotlinc = Join-Path $kotlinHome ($IsWindows ? 'bin/kotlinc.bat' : 'bin/kotlinc')
$stdlib = Join-Path $kotlinHome 'lib/kotlin-stdlib.jar'
$work = Join-Path ([IO.Path]::GetTempPath()) "apps-graphs-$PID"
$classes = Join-Path $work 'classes'
New-Item -ItemType Directory -Force $classes | Out-Null
try {
    $env:JAVA_OPTS = '-Xmx2g'
    & $kotlinc (Join-Path $ScriptDir 'graphs.kt') -cp $yed -d $classes -nowarn
    if ($LASTEXITCODE -ne 0) { throw "apps/graphs/build.ps1: kotlinc failed (exit code $LASTEXITCODE)" }
    $env:GRAPHS_REPO = $repo
    $env:GRAPHS_OUT = $Out
    & (Join-Path $javaHome 'bin/java') '-Xmx2500m' '-Djava.awt.headless=true' -cp (@($classes, $stdlib, $yed) -join [IO.Path]::PathSeparator) GraphsKt
    if ($LASTEXITCODE -ne 0) { throw "apps/graphs/build.ps1: graphs failed (exit code $LASTEXITCODE)" }
} finally {
    Remove-Item $work -Recurse -Force -ErrorAction Ignore
}
