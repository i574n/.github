param(
    [Parameter(Mandatory)] [string] $Program,
    [string] $Lib = 'C:\home\git\spiral\lib\spiral',
    [switch] $DryRun,
    [string] $ExemptAncestry = '',
    [string[]] $ExemptWindow = @()
)
$ErrorActionPreference = 'Stop'
$env:KIT_LEASE_POLICY = 'renewing'
$kit = Join-Path 'C:\home\git' ('k' + 'it\kit.exe')
$libPattern = [regex]::Escape($Lib) + '|spiral[\\/]lib|spiral\.notebook|lib_gate\.ps1|[\\/]gate\.ps1|[\\/]land\.ps1'

function Test-ExemptAncestry($process, $byId) {
    if (-not $ExemptAncestry) { return $false }
    $current = $process
    for ($depth = 0; $current -and $depth -lt 12; $depth++) {
        if ("$($current.CommandLine)" -match $ExemptAncestry) { return $true }
        $current = $byId[[int]$current.ParentProcessId]
    }
    $false
}

$livePathPattern = [regex]::Escape($Lib) + '|spiral[\\/]lib'
$tempPathPattern = '"?[^\s"]*\\AppData\\Local\\Temp\\[^\s"]*"?'

function Test-RealCopy([string] $LibPath) {
    $dir = $LibPath
    while ($dir -and $dir -match '\\AppData\\Local\\Temp\\') {
        $item = Get-Item -LiteralPath $dir -Force -ErrorAction SilentlyContinue
        if ($item -and ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { return $false }
        $dir = Split-Path $dir -Parent
    }
    $true
}

function Test-CopyDaemon([string] $Command) {
    if ($Command -notmatch 'CompilerDaemon\.serve_app') { return $false }
    if ($Command -notmatch '-pa "?([A-Za-z]:[\\/][^"]*?)[\\/]mix-build-lib-gate[\\/]lib[\\/]') { return $false }
    $root = $Matches[1].Replace('/', '\')
    if ($root -notmatch '\\AppData\\Local\\Temp\\') { return $false }
    $lib = Join-Path $root 'lib\spiral'
    (Test-Path -LiteralPath $lib -PathType Container) -and (Test-RealCopy $lib)
}

function Test-CopyOnlyAncestry($process, $byId) {
    $copy = $false
    $current = $process
    for ($depth = 0; $current -and $depth -lt 12; $depth++) {
        $command = "$($current.CommandLine)"
        if (($command -replace $tempPathPattern, '') -match $livePathPattern) { return $false }
        foreach ($m in [regex]::Matches($command, '[A-Za-z]:\\[^\s"]*\\AppData\\Local\\Temp\\[^\s"]*?lib\\spiral')) {
            if (-not (Test-RealCopy $m.Value)) { return $false }
            $copy = $true
        }
        $current = $byId[[int]$current.ParentProcessId]
    }
    $copy
}

function Get-LibReaders {
    $readers = [Collections.Generic.List[string]]::new()
    foreach ($line in @(& $kit window list 2>&1 | ForEach-Object { "$_" })) {
        if ($line -match '^\s*(win-\S+)\s+(compile|heavy-build|test-suite|compile-run|live-gui)\s+held') {
            if ($Matches[1] -in $ExemptWindow) { "lib-guarded-do / exempt window: $($line.Trim())" | Out-Host; continue }
            $readers.Add("window: $($line.Trim())")
        }
    }
    $processes = @(Get-CimInstance Win32_Process)
    $byId = @{}
    foreach ($process in $processes) { $byId[[int]$process.ProcessId] = $process }
    foreach ($process in $processes) {
        $command = "$($process.CommandLine)"
        if ($process.ProcessId -eq $PID) { continue }
        if (Test-ExemptAncestry $process $byId) {
            if ($process.Name -match '^(erl|beam\.smp|beam|pwsh|dotnet|powershell)\.exe$') { "lib-guarded-do / exempt by ancestry: $($process.ProcessId) $($process.Name)" | Out-Host }
            continue
        }
        if ($process.Name -match '^(erl|beam\.smp|beam)\.exe$') {
            if ((Test-CopyDaemon $command) -or (Test-CopyOnlyAncestry $process $byId)) { "lib-guarded-do / skipped, reads a lib copy under TEMP: $($process.ProcessId) $($process.Name)" | Out-Host; continue }
            $readers.Add("process: $($process.ProcessId) $($process.Name)")
        }
        elseif ($process.Name -match '^(pwsh|dotnet|powershell)\.exe$' -and $command -match $libPattern -and $command -notmatch 'lib-guarded-do\.ps1') {
            if (Test-CopyOnlyAncestry $process $byId) { "lib-guarded-do / skipped, reads a lib copy under TEMP: $($process.ProcessId) $($process.Name)" | Out-Host; continue }
            $readers.Add("process: $($process.ProcessId) $($process.Name) $($command.Substring(0, [Math]::Min(120, $command.Length)))")
        }
    }
    $readers
}

$text = Get-Content -Raw -LiteralPath $Program
$applyText = [regex]::Replace($text, '(?m)^(\s*edit\s+\S+)\s+dry-run\b', '$1')
$writeBlock = '(?ms)^\s*write\s+(\S+)\s*<<<\s*$.*?^>>>\s*$\r?\n?'
$writeTargets = @([regex]::Matches($applyText, $writeBlock) | ForEach-Object { $_.Groups[1].Value })
$existing = @($writeTargets | Where-Object { Test-Path -LiteralPath (Join-Path $Lib $_) })
if ($existing.Count) { "lib-guarded-do / refused: the program writes files that already exist: $($existing -join ', ')"; exit 4 }
$dryText = [regex]::Replace([regex]::Replace($applyText, $writeBlock, ''), '(?m)^(\s*edit\s+\S+)', '$1 dry-run')
$before = Get-LibReaders
if ($before.Count) { "lib-guarded-do / refused: $($before.Count) live lib readers"; $before | ForEach-Object { "  $_" }; exit 3 }
$dry = @($dryText | & $kit do $Lib - 2>&1 | ForEach-Object { "$_" })
if ($LASTEXITCODE) { 'lib-guarded-do / the program does not apply cleanly (dry run):'; $dry | Select-Object -Last 8; exit 4 }
if ($DryRun) { 'lib-guarded-do / dry run only:'; $dry | Select-Object -Last 8; exit 0 }
$again = Get-LibReaders
if ($again.Count) { "lib-guarded-do / refused at apply time: $($again.Count) live lib readers"; $again | ForEach-Object { "  $_" }; exit 3 }
$applied = @($applyText | & $kit do $Lib - 2>&1 | ForEach-Object { "$_" })
$code = $LASTEXITCODE
'lib-guarded-do / applied with no live lib reader:'
$applied | Select-Object -Last 12
exit $code
