#requires -Version 5.1
param(
    [int] $BoxSaturatedPct = 85,
    [int] $NodeIdlePct = 35,
    [switch] $NoteOnWaste
)
$ErrorActionPreference = 'Stop'
$nodeEnv = Join-Path $PSScriptRoot 'node-env.ps1'
if (Test-Path $nodeEnv) { . $nodeEnv }
$MirrorNode = if ($NodeRoutes) { $NodeRoutes[0] } else { $null }
function Invoke-Bounded([string] $exe, [string[]] $a, [int] $timeoutMs, [string] $outFile) {
    $errf = [IO.Path]::GetTempFileName()
    $p = if ($outFile) { Start-Process $exe -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errf }
    else { Start-Process $exe -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardError $errf }
    if (-not $p.WaitForExit($timeoutMs)) {
        Get-CimInstance Win32_Process -Filter "ParentProcessId=$($p.Id)" -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        Remove-Item $errf -Force -ErrorAction SilentlyContinue
        return 124
    }
    $code = $p.ExitCode
    Remove-Item $errf -Force -ErrorAction SilentlyContinue
    return $code
}
$sshOpts = @('-o', 'ConnectTimeout=12', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')
$box = [int]((Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average)
# try each route until one answers (remote.ps1 pattern): the 'laptop' hostname resolution flakes; laptop-ts/laptop-ip fall back
function Invoke-NodeRoute([string[]] $routes, [string] $remoteCmd, [int] $timeoutMs) {
    foreach ($r in $routes) {
        $o = [IO.Path]::GetTempFileName()
        $sw = [Diagnostics.Stopwatch]::StartNew()
        $rc = Invoke-Bounded 'ssh' ($sshOpts + @($r, $remoteCmd)) $timeoutMs $o
        $sw.Stop()
        if ($rc -eq 0) { $c = @(Get-Content -LiteralPath $o -ErrorAction SilentlyContinue); Remove-Item $o -Force -ErrorAction SilentlyContinue; return @{ ok = $true; route = $r; lines = $c; ms = [int]$sw.ElapsedMilliseconds } }
        Remove-Item $o -Force -ErrorAction SilentlyContinue
    }
    return @{ ok = $false; route = 'none'; lines = @(); ms = 0 }
}
# mirror node: CPU% and C: free GB in ONE probe over the route-fallback chain
$mdEnc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("[int]((Get-CimInstance Win32_Processor|Measure-Object LoadPercentage -Average).Average); [int]((Get-CimInstance Win32_LogicalDisk|Where-Object DeviceID -eq 'C:').FreeSpace/1GB); [int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024)"))
# 40s (was 15s): the node probe is ssh + pwsh + 3 CIM queries; when the lap twin is at 100% CPU (common on this fleet) that exceeds 15s and ALL routes "fail", producing a FALSE UNKNOWN/node-down alarm even though the node is up (critical-mirror lands fine). 40s tolerates a loaded lap; Invoke-NodeRoute still returns on the first route that answers, so a healthy node stays fast.
$mdR = if ($NodeRoutes) { Invoke-NodeRoute $NodeRoutes "powershell -NoProfile -EncodedCommand $mdEnc" 40000 } else { @{ ok = $false; route = 'none'; lines = @(); ms = 0 } }
$digits = @($mdR.lines | Where-Object { $_ -match '^\d+$' })
$mirrorPct = if ($mdR.ok -and $digits.Count -ge 1) { [int]$digits[0] } else { -1 }
$nodeFreeGb = if ($mdR.ok -and $digits.Count -ge 2) { [int]$digits[1] } else { -1 }
$lapFreeMb = if ($mdR.ok -and $digits.Count -ge 3) { [int]$digits[2] } else { -1 }
$mirrorRoute = $mdR.route; $mirrorMs = $mdR.ms
# compute node (vultr): loadavg / nproc, timed (the -1 cause is likely the 15s bound under box load, not a route)
$computeOut = [IO.Path]::GetTempFileName()
$computeSw = [Diagnostics.Stopwatch]::StartNew()
$computeRc = if ($ComputeNode) { Invoke-Bounded 'ssh' ($sshOpts + @($ComputeNode, "cut -d' ' -f1 /proc/loadavg; nproc; df --output=pcent /tmp 2>/dev/null | tail -1 | tr -dc 0-9")) 40000 $computeOut } else { 1 }
$computeSw.Stop()
$vr = if ($computeRc -eq 0) { @(Get-Content -LiteralPath $computeOut -ErrorAction SilentlyContinue | Where-Object { $_ }) } else { @() }
$computePct = if ($vr.Count -ge 2) { [int]([double]$vr[0] / [int]$vr[1] * 100) } else { -1 }
# vultr /tmp is a 1.7GB tmpfs (RAM-backed); when it fills, Cells/Rust gates there fail with a MISLEADING "zig exit 134 / DWARF NoSpaceLeft" that looks like a compiler code-red but is disk (main 11-06). Alarm early so it is diagnosed as /tmp, not chased as a compiler bug.
$vultrTmpPct = if ($vr.Count -ge 3) { [int]$vr[2] } else { -1 }
$computeMs = [int]$computeSw.ElapsedMilliseconds
Remove-Item $computeOut -Force -ErrorAction SilentlyContinue
$diskLow = ($nodeFreeGb -ge 0 -and $nodeFreeGb -lt 15)
$idle = @()
if ($mirrorPct -ge 0 -and $mirrorPct -le $NodeIdlePct) { $idle += "mirror(${mirrorPct}%)" }
if ($computePct -ge 0 -and $computePct -le $NodeIdlePct) { $idle += "compute(${computePct}%)" }
# WASTE = USABLE offload only: the lap twin (same OS) idle AND >=3GB free. vultr compute idle is NOT offloadable for box load - it is Linux/3.4GB and cannot host the heavy Windows classes (fiscal 3-5GB peak, trains) that saturate the box (measured, DELEG-WIFI-NODE-HEAVY n5), so counting it was a WASTE false-positive
$idleUsable = @()
if ($mirrorPct -ge 0 -and $mirrorPct -le $NodeIdlePct -and $lapFreeMb -ge 3000) { $idleUsable += "lap(cpu ${mirrorPct}%, free ${lapFreeMb}MB)" }
$waste = ($box -ge $BoxSaturatedPct -and $idleUsable.Count -gt 0)
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-utilization-monitor.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
# DISK-ALARM-REARM: do not dedupe a WORSENING disk - re-alarm DISK-LOW if the node dropped >=3GB below the last-alarmed level
$priorDiskGb = if ("$prior" -match 'node C: (\d+)GB') { [int]$Matches[1] } else { 9999 }
$diskWorsened = ($diskLow -and $nodeFreeGb -ge 0 -and ($priorDiskGb - $nodeFreeGb) -ge 3)
$priorReal = ("$prior" -match 'WASTE|DISK-LOW|VULTR-TMP') -and -not $diskWorsened
$priorUnknown = "$prior" -match 'UNKNOWN'
$priorDelivered = "$prior" -match 'DELIVERED'
$failed = @()
if ($mirrorPct -lt 0) { $failed += 'mirror' }
if ($computePct -lt 0) { $failed += 'compute' }
if ($nodeFreeGb -lt 0) { $failed += 'node-disk' }
$alarms = @()
if ($waste) { $alarms += "WASTE: box ${box}% saturated while a compatible Windows host is idle: $($idleUsable -join ', ') - offloadable capacity sitting unused (vultr compute(${computePct}%) excluded: Linux/3.4GB cannot host heavy Windows jobs)" }
if ($diskLow) { $alarms += "DISK-LOW: mirror node C: ${nodeFreeGb}GB free (<15GB floor) - mirror scp/retention + node sims at risk" }
if ($vultrTmpPct -ge 85) { $alarms += "VULTR-TMP: compute node /tmp ${vultrTmpPct}% full (1.7GB tmpfs) - Cells/Rust gates there will fail as 'zig exit 134 / DWARF NoSpaceLeft' (reads like a compiler code-red, is actually disk); prune stale >48h from /tmp" }
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$metrics = "box ${box}%, mirror ${mirrorPct}%, compute ${computePct}%, vultr-tmp ${vultrTmpPct}%, node-free ${nodeFreeGb}GB [route=$mirrorRoute mirror=${mirrorMs}ms compute=${computeMs}ms]"
if ($alarms.Count) { $line = "node-utilization-monitor $stamp $($alarms -join ' | ')" }
elseif ($failed.Count) { $line = "node-utilization-monitor $stamp UNKNOWN: $($failed -join ',') probe(s) failed - cannot measure ($metrics)" }
else { $line = "node-utilization-monitor $stamp ok: $metrics" }
# fail-closed: a guard that cannot MEASURE must say so (not 'ok'); 2 consecutive UNKNOWN is itself an alarm (node likely down / disk-blind)
$twoUnknown = ($failed.Count -gt 0 -and $alarms.Count -eq 0 -and $priorUnknown)
$noteReal = ($alarms.Count -gt 0 -and -not $priorReal)
$noteUnknown = ($twoUnknown -and -not $priorDelivered)
$doNote = ($NoteOnWaste -and ($noteReal -or $noteUnknown))
$stored = if ($noteUnknown -and $doNote) { "$line [DELIVERED]" } else { $line }
Set-Content -LiteralPath $log -Value $stored
# DELEG-UTIL-TIMESERIES: append a rolling per-machine utilization row (box/lap/vultr CPU %, node disk) so the delegation estimator has history, not just the last line
$ts = Join-Path (Split-Path $log) 'node-utilization.timeseries.csv'
$tsHeader = 'iso,box_cpu,box_free_mb,lap_cpu,lap_free_mb,vultr_cpu,node_free_gb,route'
$boxFreeMb = [int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024)
if (-not (Test-Path $ts) -or (Get-Content -LiteralPath $ts -TotalCount 1) -ne $tsHeader) { Set-Content -LiteralPath $ts -Value $tsHeader }
Add-Content -LiteralPath $ts -Value ("{0},{1},{2},{3},{4},{5},{6},{7}" -f (Get-Date).ToString('s'), $box, $boxFreeMb, $mirrorPct, $lapFreeMb, $computePct, $nodeFreeGb, $mirrorRoute)
if ((Get-Item $ts).Length -gt 1MB) { $keep = Get-Content -LiteralPath $ts -Tail 4000; Set-Content -LiteralPath $ts -Value (@($tsHeader) + $keep) }
$line
if ($doNote) {
    $kitExe = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
    $backlog = Join-Path $PSScriptRoot '..\docs\backlog'
    $noteText = "node-utilization ALARM: $line"
    # use the call operator, NOT Start-Process -ArgumentList: the latter splits a text arg containing spaces and ()<> into many operands (kit then sees 20 operands, rc=2 - the silent-drop bug). retry a genuine transient CAS reject; capture the real stderr so a failure says why.
    $rc = 1; $out = ''
    foreach ($d in @(0, 2, 5, 10)) {
        if ($d -gt 0) { Start-Sleep -Seconds $d }
        $out = (& $kitExe agile note $backlog 'NODE-ALWAYS-FED' $noteText '--as' 'resilience' 2>&1 | Out-String)
        $rc = $LASTEXITCODE
        if ($rc -eq 0) { break }
    }
    if ($rc -ne 0) {
        $why = ($out -replace '\s+', ' ').Trim()
        if ($why.Length -gt 200) { $why = $why.Substring(0, 200) }
        Add-Content -LiteralPath $log -Value "node-utilization-monitor $stamp NOTE FAILED rc=$rc after 4 tries (alarm NOT delivered to NODE-ALWAYS-FED): $why"
    }
}
if ($alarms.Count -or $twoUnknown) { exit 1 }
