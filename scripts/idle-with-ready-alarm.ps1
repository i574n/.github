#requires -Version 5.1
param(
    [int] $IdleMinutes = 15,
    [switch] $NoteOnIdle
)
$ErrorActionPreference = 'Stop'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
$backlog = Join-Path $PSScriptRoot '..\docs\backlog'
$schedDir = Join-Path $env:LOCALAPPDATA 'kit\schedules'
$log = Join-Path $schedDir 'idle-with-ready-alarm.last.log'
New-Item -ItemType Directory -Force $schedDir | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$now = Get-Date

# Only meaningful during an armed autonomous run: outside one, an idle lane is expected, not a fault.
# Reuse the standing.txt until= contract the heartbeat-staleness-guard reads.
$standing = Join-Path $env:USERPROFILE '.claude\autonomous\standing.txt'
$runActive = $false
if (Test-Path $standing) {
    $m = [regex]::Match(((Get-Content -LiteralPath $standing -Raw -ErrorAction SilentlyContinue) + ''), 'until=(\S+)')
    if ($m.Success) { try { $runActive = ([datetime]::Parse($m.Groups[1].Value) -gt $now) } catch { $runActive = $false } }
}
if (-not $runActive) {
    $line = "idle-with-ready-alarm $stamp ok: no armed run (standing.txt until= absent or past) - idle lanes expected"
    Set-Content -LiteralPath $log -Value $line
    $line
    exit 0
}

# Lane roster DERIVED from the live CC session registry (Get-ActiveLanes in node-env.ps1): the canonical team names
# of sessions active this run, so a CC rename never leaves this stale (LANE-RENAME-RECONCILE). Fail-open to a static
# set if the registry/helper is unreadable. A team that does not resolve just yields a 0 ready-count (no false alarm).
try { . (Join-Path $PSScriptRoot 'node-env.ps1'); $lanes = @(Get-ActiveLanes) } catch { $lanes = @() }
if (-not $lanes.Count) { $lanes = @('i574n', 'management', 'rig', 'fiscal', 'compiler', 'microblog', 'tibia') }

# READY COUNT: the FAST path only. `kit agile list ... --ready` returns in ~3s and prints a `shown=N` trailer that is
# the team-filtered ready count. NEVER use `kit agile wip` - it recomputes lease/claim state over the whole
# state/agile.spi (9.4MB+ and growing) and HANGS >120s, which would hang this watchdog itself (finding, session
# 614162e5). The count is still coarse: the loose --team filter surfaces shared/cross-team items, so every lane reads
# ~>0; AGILE-WAITS-AS-DATA + GATE-ROTATE-PAST-STUCK are the deps that make it a GENUINE own-team-undeclined-ungated
# count. For alert-only v1 that coarseness biases toward alerting (acceptable; main triages), it never over-suppresses.
function Get-ReadyCount([string] $team) {
    $outf = [IO.Path]::GetTempFileName()
    $errf = [IO.Path]::GetTempFileName()
    try {
        $p = Start-Process $kit -ArgumentList @('agile', 'list', $backlog, 'Planned', '--team', $team, '--ready') `
            -NoNewWindow -PassThru -RedirectStandardOutput $outf -RedirectStandardError $errf
        if (-not $p.WaitForExit(30000)) {
            Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
            return -1
        }
        $trailer = @(Get-Content -LiteralPath $outf -ErrorAction SilentlyContinue | Where-Object { $_ -match '(kit|rig) agile list ok' })
        if ($trailer.Count -and ("$($trailer[-1])" -match 'shown=(\d+)')) { return [int]$Matches[1] }
        return -1
    }
    finally {
        Remove-Item $outf, $errf -Force -ErrorAction SilentlyContinue
    }
}

# Lane state: Active (fresh beacon) | IdleWithWork (stale beacon + ready>0) | IdleNoWork (stale beacon + ready==0)
#           | NoBeacon (lane emits no autonomous-<lane>.last.log at all - idle-blind, the hard prerequisite gap)
$idleWithWork = @()
$noBeacon = @()
$active = @()
$idleNoWork = @()
$readyUnknown = @()
foreach ($lane in $lanes) {
    $beacon = Join-Path $schedDir "autonomous-$lane.last.log"
    if (-not (Test-Path $beacon)) { $noBeacon += $lane; continue }
    $age = [int](($now - (Get-Item $beacon).LastWriteTime).TotalMinutes)
    if ($age -le $IdleMinutes) { $active += "${lane}(${age}m)"; continue }
    $ready = Get-ReadyCount $lane
    if ($ready -lt 0) { $readyUnknown += "${lane}(idle ${age}m, ready=?)" }
    elseif ($ready -gt 0) { $idleWithWork += "${lane}(idle ${age}m, ready ${ready})" }
    else { $idleNoWork += "${lane}(idle ${age}m, no ready)" }
}

# BOX-SATURATED (P0 11-08 fold): box pegged while the node sits idle = lanes failed to offload heavy work. Measure box
# CPU and the node's CPU (via the now-cached, retrying Invoke-NodeProbeWin from the sibling node-env.ps1). If the node
# cannot be measured, nodeCpu stays -1 and the condition is false - fail-open (never alarm on an unmeasured node).
$boxCpu = [int]((Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average)
$nodeCpu = -1; $nodeRoute = 'none'
try {
    . (Join-Path $PSScriptRoot 'node-env.ps1')
    $np = Invoke-NodeProbeWin 'node' "[int]((Get-CimInstance Win32_Processor|Measure-Object LoadPercentage -Average).Average)" 40000 1
    if ($np.ok) { $nodeRoute = $np.route; $nodeCpu = [int](@($np.lines | Where-Object { $_ -match '^\d+$' })[0]) }
}
catch {}
$boxHot = ($boxCpu -gt 90) -and ($nodeCpu -ge 0) -and ($nodeCpu -lt 50)

$alarms = @()
if ($idleWithWork.Count) {
    $alarms += "IDLE-WITH-READY: $($idleWithWork -join ', ') - lane(s) idle >${IdleMinutes}min in an armed run while their team has ready, unblocked items; wake them (ready count is coarse pending AGILE-WAITS-AS-DATA, but idle beacon in an armed run is the signal)"
}
# NO-BEACON is the prerequisite gap, not a per-tick alarm: report it in the status line and deliver it ONCE (rising
# edge), because until a lane adopts the autonomous-<lane>.last.log beacon convention it is simply invisible to this
# watch. management's beacon is the reference impl; the other lanes must emit one for the alarm to see their idleness.
$gap = if ($noBeacon.Count) { " | NO-BEACON (idle-blind): $($noBeacon -join ',')" } else { '' }

$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$priorIdle = "$prior" -match 'IDLE-WITH-READY:'
$priorGap = "$prior" -match 'NO-BEACON'
# 2-tick sustain (~15-30min at the 15min cadence) approximates "box >90% for 10min" without cross-run state: hot this
# tick AND the prior tick already hot (pending or fired) => BOX-SATURATED; a first hot tick is only PENDING (no alarm).
$priorBoxHot = "$prior" -match 'BOX-SATURATED|BOX-SAT-PENDING'
$boxSat = $boxHot -and $priorBoxHot
$boxTag = if ($boxHot -and -not $boxSat) { ' | BOX-SAT-PENDING' } else { '' }
if ($boxSat) { $alarms += "BOX-SATURATED: box CPU ${boxCpu}% sustained while node ($nodeRoute) CPU ${nodeCpu}% <50% - lanes are not offloading heavy work; route it to the node (node-env Select-OffloadTarget / Test-BoxHeavyGuard refuse the starved box)" }

$detail = "active=[$($active -join ',')] idleWithWork=$($idleWithWork.Count) idleNoWork=$($idleNoWork.Count) noBeacon=$($noBeacon.Count) readyUnknown=$($readyUnknown.Count) box=${boxCpu}% node=${nodeCpu}%"
if ($alarms.Count) { $line = "idle-with-ready-alarm $stamp $($alarms -join ' | ')$gap$boxTag [$detail]" }
else { $line = "idle-with-ready-alarm $stamp ok: no idle-with-ready lane$gap$boxTag [$detail]" }
Set-Content -LiteralPath $log -Value $line
$line

# Deliver to main (owner of NODE-ALWAYS-FED, the keep-lanes-fed channel main watches) on a RISING edge only, with the
# CAS-retry + real-stderr-capture pattern the node-utilization monitor uses. The run's final report reads this log too.
$deliver = $false
$noteText = $null
if ($idleWithWork.Count -and -not $priorIdle) { $deliver = $true; $noteText = "IDLE-WITH-READY ALARM: $($idleWithWork -join ', ') idle in the armed run with ready work - wake the lane(s)" }
elseif ($boxSat -and "$prior" -notmatch 'BOX-SATURATED') { $deliver = $true; $noteText = "BOX-SATURATED ALARM: box CPU ${boxCpu}% sustained while node ($nodeRoute) CPU ${nodeCpu}% idle (<50%) - lanes are not offloading; heavy runs should route to the node (node-env Test-BoxHeavyGuard refuses the starved box)" }
elseif ($noBeacon.Count -and -not $priorGap -and -not $priorIdle) { $deliver = $true; $noteText = "IDLE-WITH-READY watch: $($noBeacon.Count) lane(s) are NO-BEACON (idle-blind): $($noBeacon -join ','). They must emit autonomous-<lane>.last.log (management is the reference) before their idleness is detectable" }

if ($NoteOnIdle -and $deliver) {
    $rc = 1; $out = ''
    foreach ($d in @(0, 2, 5, 10)) {
        if ($d -gt 0) { Start-Sleep -Seconds $d }
        $out = (& $kit agile note $backlog 'NODE-ALWAYS-FED' $noteText '--as' 'management' 2>&1 | Out-String)
        $rc = $LASTEXITCODE
        if ($rc -eq 0) { break }
    }
    if ($rc -ne 0) {
        $why = ($out -replace '\s+', ' ').Trim()
        if ($why.Length -gt 200) { $why = $why.Substring(0, 200) }
        Add-Content -LiteralPath $log -Value "idle-with-ready-alarm $stamp NOTE FAILED rc=$rc after 4 tries (alarm NOT delivered to NODE-ALWAYS-FED): $why"
    }
}
if ($alarms.Count) { exit 1 }
