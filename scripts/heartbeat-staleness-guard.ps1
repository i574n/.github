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
$runHeartbeatMax = 40
$standing = Join-Path $env:USERPROFILE '.claude\autonomous\standing.txt'
$runActive = $false
if (Test-Path $standing) {
    $m = [regex]::Match(((Get-Content -LiteralPath $standing -Raw -ErrorAction SilentlyContinue) + ''), 'until=(\S+)')
    if ($m.Success) { try { $runActive = ([datetime]::Parse($m.Groups[1].Value) -gt $now) } catch { $runActive = $false } }
}
$issues = [Collections.Generic.List[string]]::new()
$files = Get-ChildItem $dir -Filter '*.last.log' -ErrorAction SilentlyContinue | Where-Object { $_.Name -ne $self }
foreach ($f in $files) {
    if ($f.Name -like 'autonomous-*') {
        if (-not $runActive) { continue }
        $max = $runHeartbeatMax
    }
    elseif ($override.ContainsKey($f.Name)) { $max = $override[$f.Name] }
    else { $max = $DefaultMaxMinutes }
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
    if ($NoteOnStale -and -not $wasStale) { & (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe') agile note (Join-Path $PSScriptRoot '..\docs\backlog') HEARTBEAT-STALENESS-GUARD "guard ALARM: $line (first: $($issues[0]))" --as resilience 2>&1 | Out-Null; if ($LASTEXITCODE -ne 0) { Add-Content -LiteralPath $log -Value "heartbeat-staleness-guard $(Get-Date -Format 'yyyy-MM-dd HH:mm') NOTE FAILED rc=$LASTEXITCODE (HEARTBEAT-STALENESS-GUARD not delivered)" } }
    exit 1
}
