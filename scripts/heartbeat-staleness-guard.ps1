#requires -Version 5.1
param(
    [int] $DefaultMaxMinutes = 90,
    [switch] $NoteOnStale
)
$ErrorActionPreference = 'Stop'
$dir = Join-Path $env:LOCALAPPDATA 'kit\schedules'
$self = 'heartbeat-staleness-guard.last.log'
$override = @{
    'memory-integrity-sweep.last.log' = 1560
    'config-baseline.last.log'        = 1560
    'profile-restore-drill.last.log'  = 1560
}
$now = Get-Date
$issues = [Collections.Generic.List[string]]::new()
$files = Get-ChildItem $dir -Filter '*.last.log' -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne $self }
foreach ($f in $files) {
    $max = if ($override.ContainsKey($f.Name)) { $override[$f.Name] } else { $DefaultMaxMinutes }
    $age = [int](($now - $f.LastWriteTime).TotalMinutes)
    if ($age -gt $max) { $issues.Add("STALE $($f.Name): ${age}min (> ${max}min) - watcher not writing, likely dead") }
}
$verdict = if ($issues.Count) { "STALE ($($issues.Count))" } else { "all $($files.Count) watchers fresh" }
$line = "heartbeat-staleness-guard $(Get-Date -Format 'yyyy-MM-dd HH:mm') $verdict"
$log = Join-Path $dir $self
$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$wasStale = "$prior" -match 'STALE \('
Set-Content -LiteralPath $log -Value (@($line) + $issues)
$line
$issues
if ($issues.Count) {
    if ($NoteOnStale -and -not $wasStale) { & (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe') story note (Join-Path $PSScriptRoot '..\docs\backlog') HEARTBEAT-STALENESS-GUARD "guard ALARM: $line (first: $($issues[0]))" --as resilience *> $null }
    exit 1
}
