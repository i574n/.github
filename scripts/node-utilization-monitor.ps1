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
$mdEnc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("[int]((Get-CimInstance Win32_Processor|Measure-Object LoadPercentage -Average).Average); [int]((Get-CimInstance Win32_LogicalDisk|Where-Object DeviceID -eq 'C:').FreeSpace/1GB)"))
$mdR = if ($NodeRoutes) { Invoke-NodeRoute $NodeRoutes "powershell -NoProfile -EncodedCommand $mdEnc" 15000 } else { @{ ok = $false; route = 'none'; lines = @(); ms = 0 } }
$digits = @($mdR.lines | Where-Object { $_ -match '^\d+$' })
$mirrorPct = if ($mdR.ok -and $digits.Count -ge 1) { [int]$digits[0] } else { -1 }
$nodeFreeGb = if ($mdR.ok -and $digits.Count -ge 2) { [int]$digits[1] } else { -1 }
$mirrorRoute = $mdR.route; $mirrorMs = $mdR.ms
# compute node (vultr): loadavg / nproc, timed (the -1 cause is likely the 15s bound under box load, not a route)
$computeOut = [IO.Path]::GetTempFileName()
$computeSw = [Diagnostics.Stopwatch]::StartNew()
$computeRc = if ($ComputeNode) { Invoke-Bounded 'ssh' ($sshOpts + @($ComputeNode, "cut -d' ' -f1 /proc/loadavg; nproc")) 15000 $computeOut } else { 1 }
$computeSw.Stop()
$computePct = if ($computeRc -eq 0) { $vr = @(Get-Content -LiteralPath $computeOut -ErrorAction SilentlyContinue | Where-Object { $_ }); if ($vr.Count -ge 2) { [int]([double]$vr[0] / [int]$vr[1] * 100) } else { -1 } } else { -1 }
$computeMs = [int]$computeSw.ElapsedMilliseconds
Remove-Item $computeOut -Force -ErrorAction SilentlyContinue
$diskLow = ($nodeFreeGb -ge 0 -and $nodeFreeGb -lt 15)
$idle = @()
if ($mirrorPct -ge 0 -and $mirrorPct -le $NodeIdlePct) { $idle += "mirror(${mirrorPct}%)" }
if ($computePct -ge 0 -and $computePct -le $NodeIdlePct) { $idle += "compute(${computePct}%)" }
$waste = ($box -ge $BoxSaturatedPct -and $idle.Count -gt 0)
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-utilization-monitor.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$priorReal = "$prior" -match 'WASTE|DISK-LOW'
$priorUnknown = "$prior" -match 'UNKNOWN'
$priorDelivered = "$prior" -match 'DELIVERED'
$failed = @()
if ($mirrorPct -lt 0) { $failed += 'mirror' }
if ($computePct -lt 0) { $failed += 'compute' }
if ($nodeFreeGb -lt 0) { $failed += 'node-disk' }
$alarms = @()
if ($waste) { $alarms += "WASTE: box ${box}% saturated while idle: $($idle -join ', ') - offloadable capacity sitting unused" }
if ($diskLow) { $alarms += "DISK-LOW: mirror node C: ${nodeFreeGb}GB free (<15GB floor) - mirror scp/retention + node sims at risk" }
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$metrics = "box ${box}%, mirror ${mirrorPct}%, compute ${computePct}%, node-free ${nodeFreeGb}GB [route=$mirrorRoute mirror=${mirrorMs}ms compute=${computeMs}ms]"
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
$line
if ($doNote) {
    $kitExe = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
    $backlog = Join-Path $PSScriptRoot '..\docs\backlog'
    $noteOut = [IO.Path]::GetTempFileName()
    $rc = Invoke-Bounded $kitExe @('agile', 'note', $backlog, 'NODE-ALWAYS-FED', "node-utilization ALARM: $line", '--as', 'resilience') 30000 $noteOut
    if ($rc -ne 0) { Add-Content -LiteralPath $log -Value "node-utilization-monitor $stamp NOTE FAILED rc=$rc (alarm NOT delivered to backlog NODE-ALWAYS-FED)" }
    Remove-Item $noteOut -Force -ErrorAction SilentlyContinue
}
if ($alarms.Count -or $twoUnknown) { exit 1 }
