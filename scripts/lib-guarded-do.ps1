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
$libPattern = [regex]::Escape($Lib) + '|spiral[\\/]lib|spiral\.notebook|lib_gate\.ps1|gate\.ps1|land\.ps1'

function Test-ExemptAncestry($process, $byId) {
    if (-not $ExemptAncestry) { return $false }
    $current = $process
    for ($depth = 0; $current -and $depth -lt 12; $depth++) {
        if ("$($current.CommandLine)" -match $ExemptAncestry) { return $true }
        $current = $byId[[int]$current.ParentProcessId]
    }
    $false
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
        if ($process.Name -match '^(erl|beam\.smp|beam)\.exe$') { $readers.Add("process: $($process.ProcessId) $($process.Name)") }
        elseif ($process.Name -match '^(pwsh|dotnet|powershell)\.exe$' -and $command -match $libPattern -and $command -notmatch 'lib-guarded-do\.ps1') {
            $readers.Add("process: $($process.ProcessId) $($process.Name) $($command.Substring(0, [Math]::Min(120, $command.Length)))")
        }
    }
    $readers
}

$text = Get-Content -Raw -LiteralPath $Program
$applyText = [regex]::Replace($text, '(?m)^(\s*edit\s+\S+)\s+dry-run\b', '$1')
$dryText = [regex]::Replace($applyText, '(?m)^(\s*edit\s+\S+)', '$1 dry-run')
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
