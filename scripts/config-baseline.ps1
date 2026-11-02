param(
    [ValidateSet('Snapshot', 'Check')] [string] $Mode = 'Check',
    [string] $Store = (Join-Path $env:LOCALAPPDATA 'kit\config-baseline'),
    [switch] $NoteOnDrift
)
$ErrorActionPreference = 'Stop'
$p = $env:USERPROFILE

$fileTargets = @(
    "$p\.gitconfig", "$p\.wslconfig", "$p\.npmrc", "$p\.config\scoop\config.json",
    "$p\.config\remote-machines.json", "$p\.ssh\config", "$p\.claude\settings.json",
    "$p\.claude\hooks\subagent-hold.ps1",
    "$p\scoop\shims\shovel.ps1", "$p\scoop\shims\scoop-xelatex.ps1",
    "$p\scoop\persist\notepadplusplus\session.xml", "$p\scoop\persist\notepadplusplus\config.xml",
    "$env:APPDATA\Microsoft\Spelling\neutral\default.dic",
    "$env:APPDATA\Zed\settings.json", "$env:APPDATA\Zed\keymap.json",
    "$env:LOCALAPPDATA\Packages\Microsoft.WindowsTerminal_8wekyb3d8bbwe\LocalState\settings.json"
) + @(Get-ChildItem "$p\scoop\persist\*\data\user-data\User\settings.json", "$p\scoop\persist\*\data\user-data\User\keybindings.json" -ErrorAction SilentlyContinue | ForEach-Object FullName)

$envTargets = @('TIBIA_WORLD', 'TIBIA_MARIADB_BIN', 'MIMALLOC_DISABLE_REDIRECT', 'SPIRAL_PYTHON', 'NO_COLOR')
$dirSetTargets = @("$p\tibia-local")

function Fp($path) { $i = Get-Item -LiteralPath $path -Force; "FILE`t{0}`t{1}`t{2}`t{3:yyyy-MM-ddTHH:mm:ss}" -f $path, (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash, $i.Length, $i.LastWriteTime }
function EnvVal($n) { [Environment]::GetEnvironmentVariable($n) }

function DirNames($d) { (Get-ChildItem -LiteralPath $d -Directory -Force -ErrorAction SilentlyContinue | Sort-Object Name | ForEach-Object Name) -join ';' }
$compilerDir = "$env:LOCALAPPDATA\spiral-bin\bin\single-flight\SpiralCompiler\Release\net11.0"
$installsDir = "$env:LOCALAPPDATA\spiral-bin\compiler-installs"
function Compiler16($dll) { $f = Join-Path $compilerDir $dll; if (Test-Path -LiteralPath $f) { (Get-FileHash -LiteralPath $f -Algorithm SHA256).Hash.Substring(0, 16) } else { 'missing' } }

New-Item -ItemType Directory -Force $Store | Out-Null
if ($Mode -eq 'Snapshot') {
    $rows = @()
    $rows += foreach ($t in $fileTargets | Where-Object { Test-Path -LiteralPath $_ }) { Fp $t }
    $rows += foreach ($n in $envTargets) { "ENV`t$n`t$(EnvVal $n)" }
    $rows += foreach ($d in $dirSetTargets | Where-Object { Test-Path -LiteralPath $_ }) { "DIRSET`t$d`t$(DirNames $d)" }
    $rows += "COMPILER`thost`t$(Compiler16 'SpiralCompiler.dll')"
    $rows += "COMPILER`tcore`t$(Compiler16 'SpiralCompilerCore.dll')"
    $out = Join-Path $Store ("baseline-{0:yyyyMMdd-HHmmss}.tsv" -f (Get-Date))
    Set-Content -LiteralPath $out -Value $rows -Encoding utf8
    "config-baseline: snapshot $($rows.Count) records ($(@($fileTargets|Where-Object{Test-Path -LiteralPath $_}).Count) file, $($envTargets.Count) env, $($dirSetTargets.Count) dirset) -> $out"
    return
}
$latest = Get-ChildItem $Store -Filter 'baseline-*.tsv' -ErrorAction SilentlyContinue | Sort-Object Name | Select-Object -Last 1
if (-not $latest) { "config-baseline: no baseline yet; run -Mode Snapshot first"; exit 2 }
$drift = [Collections.Generic.List[string]]::new()
$sanctioned = [Collections.Generic.List[string]]::new()
$newfiles = 0
foreach ($line in Get-Content -LiteralPath $latest.FullName) {
    $c = $line -split "`t"
    switch ($c[0]) {
        'FILE' { if (Test-Path -LiteralPath $c[1]) { if ((Get-FileHash -LiteralPath $c[1] -Algorithm SHA256).Hash -ne $c[2]) { $drift.Add("CHANGED $($c[1])") } } else { $drift.Add("GONE $($c[1])") } }
        'ENV' { $live = [Environment]::GetEnvironmentVariable($c[1]); if ("$live" -ne "$($c[2])") { $drift.Add("ENV $($c[1]): baseline '$($c[2])' now '$live'") } }
        'DIRSET' { $live = DirNames $c[1]; if ($live -ne $c[2]) { $b = $c[2] -split ';'; $l = $live -split ';'; $added = (@($l | Where-Object { $_ -and $_ -notin $b }) -join ','); $gone = (@($b | Where-Object { $_ -and $_ -notin $l }) -join ','); $drift.Add("DIRSET $($c[1]): +[$added] -[$gone]") } }
        'COMPILER' {
            $cur = if ($c[1] -eq 'host') { Compiler16 'SpiralCompiler.dll' } else { Compiler16 'SpiralCompilerCore.dll' }
            if ($cur -ne $c[2]) {
                $baseTime = [datetime]::ParseExact((($latest.Name -replace '^baseline-', '') -replace '\.tsv$', ''), 'yyyyMMdd-HHmmss', $null)
                $newerInstall = @(Get-ChildItem $installsDir -Directory -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -gt $baseTime } | Sort-Object LastWriteTime -Descending | Select-Object -First 1)
                if ($newerInstall.Count) { $sanctioned.Add("COMPILER $($c[1]) $($c[2])->$cur (sanctioned by $($newerInstall[0].Name); re-baseline)") }
                else { $drift.Add("COMPILER $($c[1]) UNSANCTIONED $($c[2])->$cur (no compiler-installs newer than baseline)") }
            }
        }
    }
}

$baseFiles = (Get-Content -LiteralPath $latest.FullName | Where-Object { $_ -like "FILE`t*" } | ForEach-Object { ($_ -split "`t")[1] })
foreach ($t in $fileTargets | Where-Object { Test-Path -LiteralPath $_ }) { if ($t -notin $baseFiles) { $newfiles++ } }
$verdict = if ($drift.Count) { "DRIFT ($($drift.Count)): $($drift -join '; ')" } else { "clean ($($baseFiles.Count) files, $($envTargets.Count) env, $($dirSetTargets.Count) dirset; $newfiles new not-yet-baselined)" }
$line = "config-baseline $(Get-Date -Format 'yyyy-MM-dd HH:mm') vs $($latest.Name): $verdict$(if ($sanctioned.Count) { " | SANCTIONED (re-baseline): $($sanctioned -join '; ')" })"
$line
$logFile = Join-Path $env:LOCALAPPDATA 'kit\schedules\config-baseline.last.log'
New-Item -ItemType Directory -Force (Split-Path $logFile) | Out-Null
Set-Content -LiteralPath $logFile -Value $line
if ($drift.Count) {
    if ($NoteOnDrift) { & (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe') story note (Join-Path $PSScriptRoot '..\docs\backlog') CONFIG-BASELINE "scheduled check: $line" --as config-baseline *> $null }
    exit 1
}
