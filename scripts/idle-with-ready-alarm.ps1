#requires -Version 5.1
param(
    [int] $IdleMinutes = 15,
    [switch] $NoteOnIdle
)
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'agent-runtime.ps1')
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
$backlog = Join-Path $PSScriptRoot '..\docs\backlog'
$schedDir = Join-Path $env:LOCALAPPDATA 'kit\schedules'
$log = Join-Path $schedDir 'idle-with-ready-alarm.last.log'
New-Item -ItemType Directory -Force $schedDir | Out-Null
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$now = Get-Date

$standing = Get-AgentStandingFile
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

try { . (Join-Path $PSScriptRoot 'node-env.ps1'); $lanes = @(Get-ActiveLanes) } catch { $lanes = @() }
if (-not $lanes.Count) { $lanes = @('org', 'rig', 'fiscal', 'compiler', 'microblog', 'tibia') }

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

$idleWithWork = @()
$noBeacon = @()
$active = @()
$idleNoWork = @()
$readyUnknown = @()
$sessions = @()
try { $sessions = @(Get-LaneSessions) } catch { $sessions = @() }
$readyByTeam = @{}
foreach ($s in $sessions) {
    $who = if ($s.Session -ieq $s.Team) { $s.Session } else { "$($s.Session)/$($s.Team)" }
    if ($s.Status -ne 'idle' -or $s.StatusMinutes -le $IdleMinutes) { $active += "${who}($($s.Status) $($s.StatusMinutes)m)"; continue }
    if (-not $readyByTeam.ContainsKey($s.Team)) { $readyByTeam[$s.Team] = Get-ReadyCount $s.Team }
    $ready = $readyByTeam[$s.Team]
    if ($ready -lt 0) { $readyUnknown += "${who}(idle $($s.StatusMinutes)m, ready=?)" }
    elseif ($ready -gt 0) { $idleWithWork += "${who}(idle $($s.StatusMinutes)m, ready ${ready})" }
    else { $idleNoWork += "${who}(idle $($s.StatusMinutes)m, no ready)" }
}
foreach ($lane in $(if ($sessions.Count) { @() } else { $lanes })) {
    $beacon = Join-Path $schedDir "autonomous-$lane.last.log"
    if (-not (Test-Path $beacon)) { $noBeacon += $lane; continue }
    $age = [int](($now - (Get-Item $beacon).LastWriteTime).TotalMinutes)
    if ($age -le $IdleMinutes) { $active += "${lane}(${age}m)"; continue }
    $ready = Get-ReadyCount $lane
    if ($ready -lt 0) { $readyUnknown += "${lane}(idle ${age}m, ready=?)" }
    elseif ($ready -gt 0) { $idleWithWork += "${lane}(idle ${age}m, ready ${ready})" }
    else { $idleNoWork += "${lane}(idle ${age}m, no ready)" }
}

$boxCpu = [int]((Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average)
$nodeCpu = -1; $nodeRoute = 'none'
try {
    . (Join-Path $PSScriptRoot 'node-env.ps1')
    $np = Invoke-NodeProbeWin 'node' "[int]((Get-CimInstance Win32_Processor|Measure-Object LoadPercentage -Average).Average)" 40000 1
    if ($np.ok) { $nodeRoute = $np.route; $nodeCpu = [int](@($np.lines | Where-Object { $_ -match '^\d+$' })[0]) }
}
catch {}
try {
    $la = & ssh -o BatchMode=yes -o ConnectTimeout=12 -o ServerAliveInterval=15 -o ServerAliveCountMax=4 ganymede 'echo $(cut -d" " -f1 /proc/loadavg) $(nproc)' 2>$null
    if ($LASTEXITCODE -eq 0 -and "$la" -match '^([\d.]+) (\d+)$') {
        $gPct = [int][math]::Min(100, [double]$Matches[1] / [int]$Matches[2] * 100)
        if ($nodeCpu -lt 0 -or $gPct -lt $nodeCpu) { $nodeCpu = $gPct; $nodeRoute = 'ganymede' }
    }
}
catch {}
$heavyNames = '^(dotnet|cargo|rustc|cc1|cc1plus|clang|gcc|ld|lld|link|beam\.smp|erl|zig|gleam|lua|luajit|node|bun|SpiralCompiler)$'
$cores = [Environment]::ProcessorCount
$sampleA = @{}; Get-Process | Where-Object { $_.ProcessName -match $heavyNames } | ForEach-Object { $sampleA[$_.Id] = $_.CPU }
Start-Sleep -Seconds 3
$heavy = @(Get-Process | Where-Object { $_.ProcessName -match $heavyNames -and $sampleA.ContainsKey($_.Id) } | ForEach-Object {
    [pscustomobject]@{ Name = $_.ProcessName; Pct = [math]::Round(($_.CPU - $sampleA[$_.Id]) / 3 / $cores * 100, 1) } } | Where-Object Pct -gt 0)
$heavyPct = [int](($heavy | Measure-Object Pct -Sum).Sum)
$heavyTop = ($heavy | Sort-Object Pct -Descending | Select-Object -First 3 | ForEach-Object { "$($_.Name) $($_.Pct)%" }) -join ', '
$boxHot = ($boxCpu -gt 90) -and ($heavyPct -ge 25) -and ($nodeCpu -ge 0) -and ($nodeCpu -lt 50)

$alarms = @()
if ($idleWithWork.Count) {
    $alarms += "IDLE-WITH-READY: $($idleWithWork -join ', ') - lane(s) idle >${IdleMinutes}min in an armed run while their team has ready, unblocked items; wake them (ready count is coarse pending AGILE-WAITS-AS-DATA, but idle beacon in an armed run is the signal)"
}
$gap = if ($noBeacon.Count) { " | NO-BEACON (idle-blind): $($noBeacon -join ',')" } else { '' }

$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$priorIdle = "$prior" -match 'IDLE-WITH-READY:'
$priorGap = "$prior" -match 'NO-BEACON'
$priorBoxHot = "$prior" -match 'BOX-SATURATED|BOX-SAT-PENDING'
$boxSat = $boxHot -and $priorBoxHot
$boxTag = if ($boxHot -and -not $boxSat) { ' | BOX-SAT-PENDING' } else { '' }
if ($boxSat) { $alarms += "BOX-SATURATED: box CPU ${boxCpu}% sustained (heavy ${heavyPct}%: $heavyTop) while node ($nodeRoute) CPU ${nodeCpu}% <50% - lanes are not offloading heavy work; route it to the node (node-env Select-OffloadTarget / Test-BoxHeavyGuard refuse the starved box)" }

$detail = "active=[$($active -join ',')] idleWithWork=$($idleWithWork.Count) idleNoWork=$($idleNoWork.Count) noBeacon=$($noBeacon.Count) readyUnknown=$($readyUnknown.Count) box=${boxCpu}% heavy=${heavyPct}% node=${nodeRoute}:${nodeCpu}%"
if ($alarms.Count) { $line = "idle-with-ready-alarm $stamp $($alarms -join ' | ')$gap$boxTag [$detail]" }
else { $line = "idle-with-ready-alarm $stamp ok: no idle-with-ready lane$gap$boxTag [$detail]" }
Set-Content -LiteralPath $log -Value $line
$line

$deliver = $false
$noteText = $null
if ($idleWithWork.Count -and -not $priorIdle) { $deliver = $true; $noteText = "IDLE-WITH-READY ALARM: $($idleWithWork -join ', ') idle in the armed run with ready work - wake the lane(s)" }
elseif ($boxSat -and "$prior" -notmatch 'BOX-SATURATED') { $deliver = $true; $noteText = "BOX-SATURATED ALARM: box CPU ${boxCpu}% sustained while node ($nodeRoute) CPU ${nodeCpu}% idle (<50%) - lanes are not offloading; heavy runs should route to the node (node-env Test-BoxHeavyGuard refuses the starved box)" }
elseif ($noBeacon.Count -and -not $priorGap -and -not $priorIdle) { $deliver = $true; $noteText = "IDLE-WITH-READY watch: $($noBeacon.Count) lane(s) are NO-BEACON (idle-blind): $($noBeacon -join ','). They must emit autonomous-<lane>.last.log (management is the reference) before their idleness is detectable" }

if ($NoteOnIdle -and $deliver) {
    $rc = 1; $out = ''
    foreach ($d in @(0, 2, 5, 10)) {
        if ($d -gt 0) { Start-Sleep -Seconds $d }
        $out = (& $kit agile note $backlog 'NODE-ALWAYS-FED' $noteText '--as' 'org' 2>&1 | Out-String)
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
