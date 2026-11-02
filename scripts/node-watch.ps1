#requires -Version 7
# Continuous worker-node utilization monitor. Samples the node's CPU and
# running/queued eoie job count on an interval, appends a timestamped line, and
# flags idle / underutilized stretches so the node is never silently idle.
# Light: one ssh status call per sample. Run as a supervised eoie job:
#   eoie job start node-watch 3600000 pwsh -NoProfile -File <this>
# Companion of remote.ps1; pairs with node-idle-wait.ps1 (wake on idle) and
# node-dispatch.ps1 (feed the node from a queue).
param(
  [int] $IntervalSec = 75,
  [string] $Machine = 'node',
  [int] $IdleCpu = 15,
  [int] $UnderCpu = 50,
  [string] $Log = ''
)
# node-jobs counts only eoie jobs; remote.ps1-run work (Cells, builds, verifies)
# is invisible to it, so CPU% is the real utilization signal. IDLE = cpu<IdleCpu,
# UNDERUTIL = cpu<UnderCpu (cores sitting idle) -- the state the user cares about.
if (-not $Log) { $Log = Join-Path 'C:\home\remote-queue' $(if ($Machine -in 'node', 'laptop') { 'node-watch.log' } else { "$Machine-watch.log" }) }
$f = Join-Path $PSScriptRoot 'remote.ps1'
New-Item -ItemType Directory -Force (Split-Path $Log) | Out-Null
$idleStreak = 0
$underStreak = 0
"=== node-watch started $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), interval ${IntervalSec}s, idle<${IdleCpu}% under<${UnderCpu}% ===" | Add-Content $Log
while ($true) {
  $t = Get-Date -Format 'HH:mm:ss'
  try {
    $status = (& $f $Machine status 2>&1) -join "`n"
    $m = [regex]::Match($status, 'cpu (\d+)%')
    $cpu = if ($m.Success) { [int]$m.Groups[1].Value } else { -1 }
    $jobs = ([regex]::Matches($status, 'state=(running|queued)')).Count
    $idle = ($cpu -ge 0 -and $cpu -lt $IdleCpu)
    $under = ($cpu -ge 0 -and $cpu -lt $UnderCpu)
    $idleStreak = if ($idle) { $idleStreak + 1 } else { 0 }
    $underStreak = if ($under) { $underStreak + 1 } else { 0 }
    $flag = if ($cpu -lt 0) { '  (status unreadable)' } elseif ($idleStreak -ge 2) { "  <<< IDLE x$idleStreak (feed the node)" } elseif ($underStreak -ge 6) { "  <<< UNDERUTIL x$underStreak (cpu<$UnderCpu%, cores idle; parallelize)" } else { '' }
    "$t  cpu=$cpu%  node-jobs=$jobs$flag" | Add-Content $Log
    $state = if ($cpu -lt 0) { 'UNKNOWN' } elseif ($cpu -lt $UnderCpu) { 'AVAILABLE' } else { 'BUSY' }
    $staleAfter = (Get-Date).AddSeconds([math]::Max(300, 3 * $IntervalSec)).ToString('o')
    $line = "$(Get-Date -Format o)  machine=$Machine  cpu=$cpu%  jobs=$jobs  $state  stale_after=$staleAfter  (past stale_after this file is UNKNOWN, never BUSY: the watcher died; any lane: self-serve a per-job mirror offload when AVAILABLE)"
    $statusFiles = @("$Machine-status.txt") + $(if ($Machine -in 'node', 'laptop') { @('node-status.txt') } else { @() })
    foreach ($name in $statusFiles) { $line | Set-Content (Join-Path (Split-Path $Log) $name) }
  } catch {
    "$t  status-error: $($_.Exception.Message)" | Add-Content $Log
  }
  Start-Sleep -Seconds $IntervalSec
}
