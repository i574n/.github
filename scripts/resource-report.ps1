param(
    [int] $Hours = 24,
    [int] $Count = 2000,
    [switch] $Node,
    [string] $NodeHost = 'laptop',
    [string] $NodeKit = 'C:\home\bin\eoie.exe',
    [int] $SampleSec = 5,
    [string[]] $Teams = @('main', 'x', 'kit', 'eoie', 'fiscal', 'compiler', 'tibia', 'heal', 'healing')
)
$ErrorActionPreference = 'Stop'

function Get-JobRows([string[]] $Lines, [string] $Machine) {
    $since = [DateTimeOffset]::UtcNow.AddHours(-$Hours).ToUnixTimeMilliseconds()
    foreach ($l in $Lines) {
        if ($l -notmatch ' id=job-(\d+)-(\S+) state=(\S+) exit=(\S+) elapsed_ms=(\d+)') { continue }
        $started = [int64]$Matches[1]
        if ($started -lt $since) { continue }
        $label = $Matches[2]
        [pscustomobject]@{
            Machine = $Machine
            Team = if (($label -split '-', 2)[0] -in $Teams) { ($label -split '-', 2)[0] } else { '(no team)' }
            Label = $label
            State = $Matches[3]
            Exit = $Matches[4]
            Hours = [double]$Matches[5] / 3.6e6
            PeakMb = if ($l -match 'peak_private_mb=(\d+)') { [int]$Matches[1] } else { $null }
        }
    }
}

$rows = @(Get-JobRows @(& "$env:LOCALAPPDATA\kit\bin\kit.exe" job list $Count 2>&1 | ForEach-Object { "$_" }) 'box')
if ($Node) { $rows += @(Get-JobRows @(ssh -o ConnectTimeout=15 $NodeHost "$NodeKit job list $Count" 2>&1 | ForEach-Object { "$_" }) 'node') }

"=== jobs started in the last $Hours h, by machine and team (wall hours; a job's wall time is not its CPU time)"
$rows | Group-Object Machine, Team | ForEach-Object {
    $g = $_.Group
    [pscustomobject]@{
        Machine = $g[0].Machine
        Team = $g[0].Team
        Jobs = $g.Count
        WallH = [math]::Round(($g | Measure-Object Hours -Sum).Sum, 1)
        Running = @($g | Where-Object State -eq 'running').Count
        Stopped = @($g | Where-Object State -eq 'stopped').Count
        NonZero = @($g | Where-Object { $_.State -notin 'running', 'stopped' -and $_.Exit -notin '0', '-' }).Count
        MaxPeakMb = ($g | Measure-Object PeakMb -Maximum).Maximum
    }
} | Sort-Object Machine, WallH -Descending | Format-Table -AutoSize | Out-String -Width 160

"=== the 10 longest jobs"
$rows | Sort-Object Hours -Descending | Select-Object -First 10 Machine, Label, State, Exit, @{ n = 'WallH'; e = { [math]::Round($_.Hours, 2) } }, PeakMb | Format-Table -AutoSize | Out-String -Width 160

"=== box processes by name, outside any live kit job's tree and any Claude session, at least 200 MB working set (candidates for dangling)"
$all = @(Get-CimInstance Win32_Process | Select-Object ProcessId, ParentProcessId, Name, WorkingSetSize)
$byId = @{}; foreach ($p in $all) { $byId[[int]$p.ProcessId] = $p }
$jobRoots = @(Get-ChildItem "$env:LOCALAPPDATA\kit\jobs" -Directory | Where-Object { -not (Test-Path (Join-Path $_.FullName 'exit')) } | ForEach-Object {
    foreach ($f in 'pid', 'supervisor') { $p = Join-Path $_.FullName $f; if (Test-Path $p) { [int](Get-Content -LiteralPath $p -Raw).Trim() } }
})
$inJob = @{}
foreach ($p in $all) {
    $seen = @{}; $cur = [int]$p.ProcessId
    while ($cur -and -not $seen[$cur]) { $seen[$cur] = 1; if ($cur -in $jobRoots -or $byId[$cur].Name -eq 'claude.exe') { $inJob[[int]$p.ProcessId] = 1; break }; $cur = if ($byId[$cur]) { [int]$byId[$cur].ParentProcessId } else { 0 } }
}
$daemonPorts = @{}
foreach ($c in @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue | Where-Object { $_.LocalPort -ge 13905 -and $_.LocalPort -lt 15000 })) { $daemonPorts[[int]$c.OwningProcess] = [int]$c.LocalPort }
$daemonOwner = @{}
function Get-DaemonOwner([int] $Port) {
    if (-not $daemonOwner.ContainsKey($Port)) {
        $daemonOwner[$Port] = try {
            $client = [Net.Sockets.TcpClient]::new(); if (-not $client.ConnectAsync('127.0.0.1', $Port).Wait(500)) { throw 'no connect' }
            $writer = [IO.StreamWriter]::new($client.GetStream()); $writer.NewLine = "`n"; $writer.WriteLine('status'); $writer.Flush()
            $line = [IO.StreamReader]::new($client.GetStream()).ReadLine(); $client.Dispose()
            $owner = ($line -split "`t")[6]
            if ($owner -match 'argv .*--path (\S+)') { "cells:$Port $(Split-Path $Matches[1] -Leaf)" } elseif ($owner) { "cells:$Port $($owner.Substring(0, [Math]::Min(40, $owner.Length)))" } else { "cells:$Port" }
        } catch { "cells:$Port (no status)" }
    }
    $daemonOwner[$Port]
}
function Get-Owner([int] $Id) {
    $seen = @{}; $cur = $Id
    while ($cur -and -not $seen[$cur]) { $seen[$cur] = 1; if ($daemonPorts.ContainsKey($cur)) { return Get-DaemonOwner $daemonPorts[$cur] }; $cur = if ($byId[$cur]) { [int]$byId[$cur].ParentProcessId } else { 0 } }
    '(none)'
}
$all | Where-Object { -not $inJob[[int]$_.ProcessId] -and $_.WorkingSetSize -ge 200MB } | ForEach-Object { $_ | Add-Member Owner (Get-Owner ([int]$_.ProcessId)) -PassThru } | Group-Object Name, Owner | ForEach-Object {
    [pscustomobject]@{ Name = $_.Group[0].Name; Owner = $_.Group[0].Owner; Count = $_.Count; WorkingSetMb = [math]::Round(($_.Group | Measure-Object WorkingSetSize -Sum).Sum / 1MB) }
} | Sort-Object WorkingSetMb -Descending | Format-Table -AutoSize | Out-String -Width 160

"=== box CPU right now, by process name (TotalProcessorTime delta over $SampleSec s; 100% = all $([Environment]::ProcessorCount) logical cores)"
$before = @{}; foreach ($p in Get-Process) { try { $before[$p.Id] = @($p.Name, $p.TotalProcessorTime.TotalSeconds) } catch { } }
Start-Sleep -Seconds $SampleSec
$deltas = foreach ($p in Get-Process) { try { if ($before.ContainsKey($p.Id)) { [pscustomobject]@{ Name = $p.Name; Cpu = $p.TotalProcessorTime.TotalSeconds - $before[$p.Id][1] } } } catch { } }
$total = [Environment]::ProcessorCount * $SampleSec
$deltas | Group-Object Name | ForEach-Object { [pscustomobject]@{ Name = $_.Name; Count = $_.Count; BoxPct = [math]::Round(100 * ($_.Group | Measure-Object Cpu -Sum).Sum / $total, 1) } } |
    Where-Object BoxPct -ge 0.5 | Sort-Object BoxPct -Descending | Select-Object -First 12 | Format-Table -AutoSize | Out-String -Width 160
"box busy: {0:N1}% of all cores" -f (100 * ($deltas | Measure-Object Cpu -Sum).Sum / $total)
