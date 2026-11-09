#requires -Version 5.1
param(
    [int] $BoxSaturatedPct = 85,
    [int] $NodeIdlePct = 35,
    [int] $NodeProbeTimeoutMs = 40000,
    [int] $WorkerFullPct = 85,
    [int] $WorkerMemFloorMb = 400,
    [int] $NodeDiskFloorGb = 15,
    [int] $OffloadFreeMb = 3000,
    [switch] $NoteOnWaste
)
$ErrorActionPreference = 'Stop'
$nodeEnv = Join-Path $PSScriptRoot 'node-env.ps1'
if (Test-Path $nodeEnv) { . $nodeEnv }
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
$boxOs = Get-CimInstance Win32_OperatingSystem
$boxCommitPct = [int](100 * (1 - $boxOs.FreeVirtualMemory / $boxOs.TotalVirtualMemorySize))
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
$nodeEnc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("[int]((Get-CimInstance Win32_Processor|Measure-Object LoadPercentage -Average).Average); [int]((Get-CimInstance Win32_LogicalDisk|Where-Object DeviceID -eq 'C:').FreeSpace/1GB); [int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory/1024); `$o=Get-CimInstance Win32_OperatingSystem; [int](100*(1-`$o.FreeVirtualMemory/`$o.TotalVirtualMemorySize)); `$k=0; `$p='C:\ProgramData\erl-guard\killed.log'; if(Test-Path `$p){`$c=(Get-Date).AddMinutes(-30); `$k=@(Get-Content `$p -EA SilentlyContinue|%{`$d=[datetime]::MinValue; if([datetime]::TryParse((`$_ -split ' ')[0],[ref]`$d) -and `$d -gt `$c){1}}).Count}; `$k"))
$nodeR = if ($NodeRoutes) { Invoke-NodeRoute $NodeRoutes "powershell -NoProfile -EncodedCommand $nodeEnc" $NodeProbeTimeoutMs } else { @{ ok = $false; route = 'none'; lines = @(); ms = 0 } }
$digits = @($nodeR.lines | Where-Object { $_ -match '^\d+$' })
$nodePct = if ($nodeR.ok -and $digits.Count -ge 1) { [int]$digits[0] } else { -1 }
$nodeFreeGb = if ($nodeR.ok -and $digits.Count -ge 2) { [int]$digits[1] } else { -1 }
$nodeFreeMb = if ($nodeR.ok -and $digits.Count -ge 3) { [int]$digits[2] } else { -1 }
$nodeCommitPct = if ($nodeR.ok -and $digits.Count -ge 4) { [int]$digits[3] } else { -1 }
$nodeErlKill = if ($nodeR.ok -and $digits.Count -ge 5) { [int]$digits[4] } else { 0 }
$nodeRoute = $nodeR.route; $nodeMs = $nodeR.ms
$workerOut = [IO.Path]::GetTempFileName()
$workerSw = [Diagnostics.Stopwatch]::StartNew()
$workerRc = if ($ComputeNode) { Invoke-Bounded 'ssh' ($sshOpts + @($ComputeNode, "cut -d' ' -f1 /proc/loadavg; nproc; df --output=pcent /tmp 2>/dev/null | tail -1 | tr -dc 0-9; echo; df --output=pcent / 2>/dev/null | tail -1 | tr -dc 0-9; echo; df -BG --output=avail / 2>/dev/null | tail -1 | tr -dc 0-9; echo; timeout 10 journalctl -k --since '30 min ago' 2>/dev/null | grep -i 'killed process' | grep -vic cgroup || true; awk '/MemAvailable/{print int(`$2/1024)}' /proc/meminfo")) $NodeProbeTimeoutMs $workerOut } else { 1 }
$workerSw.Stop()
$wr = if ($workerRc -eq 0) { @(Get-Content -LiteralPath $workerOut -ErrorAction SilentlyContinue | Where-Object { $_ }) } else { @() }
$workerPct = if ($wr.Count -ge 2) { [int]([double]$wr[0] / [int]$wr[1] * 100) } else { -1 }
$workerTmpPct = if ($wr.Count -ge 3) { [int]$wr[2] } else { -1 }
$workerDiskPct = if ($wr.Count -ge 4) { [int]$wr[3] } else { -1 }
$workerFreeGb = if ($wr.Count -ge 5) { [int]$wr[4] } else { -1 }
$workerOom = if ($wr.Count -ge 6) { [int]$wr[5] } else { -1 }
$workerMemMb = if ($wr.Count -ge 7) { [int]$wr[6] } else { -1 }
$workerMs = [int]$workerSw.ElapsedMilliseconds
Remove-Item $workerOut -Force -ErrorAction SilentlyContinue
$diskLow = ($nodeFreeGb -ge 0 -and $nodeFreeGb -lt $NodeDiskFloorGb)
$idleUsable = @()
if ($nodePct -ge 0 -and $nodePct -le $NodeIdlePct -and $nodeFreeMb -ge $OffloadFreeMb) { $idleUsable += "node(cpu ${nodePct}%, free ${nodeFreeMb}MB)" }
$waste = ($box -ge $BoxSaturatedPct -and $idleUsable.Count -gt 0)
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-utilization-monitor.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$priorDiskGb = if ("$prior" -match 'node C: (\d+)GB') { [int]$Matches[1] } else { 9999 }
$diskWorsened = ($diskLow -and $nodeFreeGb -ge 0 -and ($priorDiskGb - $nodeFreeGb) -ge 3)
$oomRecent = ($workerOom -ge 1)
$priorReal = ("$prior" -match 'WASTE|DISK-LOW|WORKER-TMP|WORKER-DISK|COMMIT-HIGH|WORKER-OOM|NODE-ERLKILL|WORKER-MEM') -and -not $diskWorsened
$priorUnknown = "$prior" -match 'UNKNOWN'
$priorDelivered = "$prior" -match 'DELIVERED'
$failed = @()
if ($nodePct -lt 0) { $failed += 'node' }
if ($workerPct -lt 0) { $failed += 'worker' }
if ($nodeFreeGb -lt 0) { $failed += 'node-disk' }
$alarms = @()
if ($waste) { $alarms += "WASTE: box ${box}% saturated while a compatible Windows host is idle: $($idleUsable -join ', ') - offloadable capacity sitting unused (the Linux worker at ${workerPct}% is excluded: it cannot host the heavy Windows jobs)" }
if ($diskLow) { $alarms += "DISK-LOW: node C: ${nodeFreeGb}GB free (<${NodeDiskFloorGb}GB floor) - mirror copies, retention and node sims at risk" }
if ($workerTmpPct -ge $WorkerFullPct) { $alarms += "WORKER-TMP: worker /tmp ${workerTmpPct}% full - Cells/Rust gates there fail as 'zig exit 134 / DWARF NoSpaceLeft', which reads like a compiler bug but is disk; prune stale >48h from /tmp" }
if ($workerDiskPct -ge $WorkerFullPct) { $alarms += "WORKER-DISK: worker root ${workerDiskPct}% full (${workerFreeGb}GB free) - a full disk breaks gates silently (zig NoSpaceLeft); prune /tmp orphans, stale stages and old compiler candidates; owner-owned data dirs are never auto-pruned" }
if ($boxCommitPct -ge 70 -or $nodeCommitPct -ge 70) { $alarms += "COMMIT-HIGH: committed memory box ${boxCommitPct}% node ${nodeCommitPct}% (>=70% of the commit limit incl pagefile) - a runaway can thrash the pagefile while free physical memory still looks fine; check the top committers before placing heavy work" }
if ($oomRecent) { $alarms += "WORKER-OOM: the worker's host OOM-killer fired $workerOom time(s) in the last 30 min (silent 137/143 exits); a job exceeded host RAM, so cap heavy jobs with systemd-run --scope. Container cgroup OOMs are excluded: those go to the pod owner" }
if ($workerMemMb -ge 0 -and $workerMemMb -lt $WorkerMemFloorMb) { $alarms += "WORKER-MEM: worker MemAvailable ${workerMemMb}MB < ${WorkerMemFloorMb}MB - near-OOM; a heavy job is over-committing (early warning before WORKER-OOM)" }
if ($nodeErlKill -ge 1) { $alarms += "NODE-ERLKILL: the node's erl-mem-guard killed $nodeErlKill erl/beam process(es) >12GB in the last 30 min; it was contained, but check the owning lane's mix invocation (unguarded, or needs a heap cap)" }
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$metrics = "box ${box}%, node ${nodePct}%, worker ${workerPct}%, worker-tmp ${workerTmpPct}%, worker-disk ${workerDiskPct}%, commit box ${boxCommitPct}%/node ${nodeCommitPct}%, worker-oom ${workerOom}, worker-mem ${workerMemMb}MB, node-erlkill ${nodeErlKill}, node-free ${nodeFreeGb}GB [route=$nodeRoute node=${nodeMs}ms worker=${workerMs}ms]"
if ($alarms.Count) { $line = "node-utilization-monitor $stamp $($alarms -join ' | ')" }
elseif ($failed.Count) { $line = "node-utilization-monitor $stamp UNKNOWN: $($failed -join ',') probe(s) failed - cannot measure ($metrics)" }
else { $line = "node-utilization-monitor $stamp ok: $metrics" }
$twoUnknown = ($failed.Count -gt 0 -and $alarms.Count -eq 0 -and $priorUnknown)
$noteReal = ($alarms.Count -gt 0 -and -not $priorReal)
$noteUnknown = ($twoUnknown -and -not $priorDelivered)
$doNote = ($NoteOnWaste -and ($noteReal -or $noteUnknown))
$stored = if ($noteUnknown -and $doNote) { "$line [DELIVERED]" } else { $line }
Set-Content -LiteralPath $log -Value $stored
$ts = Join-Path (Split-Path $log) 'node-utilization.timeseries.csv'
$tsHeader = 'iso,box_cpu,box_free_mb,node_cpu,node_free_mb,worker_cpu,node_free_gb,route'
$boxFreeMb = [int]((Get-CimInstance Win32_OperatingSystem).FreePhysicalMemory / 1024)
if (-not (Test-Path $ts)) { Set-Content -LiteralPath $ts -Value $tsHeader }
else {
    $first = Get-Content -LiteralPath $ts -TotalCount 1
    if ($first -ne $tsHeader) {
        $rows = @(Get-Content -LiteralPath $ts | Select-Object -Skip 1)
        $sameShape = (($first -split ',').Count -eq ($tsHeader -split ',').Count)
        Set-Content -LiteralPath $ts -Value (@($tsHeader) + $(if ($sameShape) { $rows } else { @() }))
    }
}
Add-Content -LiteralPath $ts -Value ("{0},{1},{2},{3},{4},{5},{6},{7}" -f (Get-Date).ToString('s'), $box, $boxFreeMb, $nodePct, $nodeFreeMb, $workerPct, $nodeFreeGb, $nodeRoute)
if ((Get-Item $ts).Length -gt 1MB) { $keep = Get-Content -LiteralPath $ts -Tail 4000; Set-Content -LiteralPath $ts -Value (@($tsHeader) + $keep) }
$line
if ($doNote) {
    $kitExe = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
    $backlog = Join-Path $PSScriptRoot '..\docs\backlog'
    $noteText = "node-utilization ALARM: $line"
    $rc = 1; $out = ''
    foreach ($d in @(0, 2, 5, 10)) {
        if ($d -gt 0) { Start-Sleep -Seconds $d }
        $out = (& $kitExe agile note $backlog 'NODE-ALWAYS-FED' $noteText '--as' 'org' 2>&1 | Out-String)
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
