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
$mirrorEnc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes('[int]((Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average)'))
$mirrorOut = [IO.Path]::GetTempFileName()
$mirrorPct = if ($MirrorNode -and (Invoke-Bounded 'ssh' ($sshOpts + @($MirrorNode, "powershell -NoProfile -EncodedCommand $mirrorEnc")) 15000 $mirrorOut) -eq 0) {
    $v = (Get-Content -LiteralPath $mirrorOut -ErrorAction SilentlyContinue | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1); if ($v) { [int]$v } else { -1 }
}
else { -1 }
Remove-Item $mirrorOut -Force -ErrorAction SilentlyContinue
$computeOut = [IO.Path]::GetTempFileName()
$computePct = if ($ComputeNode -and (Invoke-Bounded 'ssh' ($sshOpts + @($ComputeNode, "cut -d' ' -f1 /proc/loadavg; nproc")) 15000 $computeOut) -eq 0) {
    $vr = @(Get-Content -LiteralPath $computeOut -ErrorAction SilentlyContinue | Where-Object { $_ }); if ($vr.Count -ge 2) { [int]([double]$vr[0] / [int]$vr[1] * 100) } else { -1 }
}
else { -1 }
Remove-Item $computeOut -Force -ErrorAction SilentlyContinue
$diskEnc = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("[int]((Get-CimInstance Win32_LogicalDisk | Where-Object DeviceID -eq 'C:').FreeSpace/1GB)"))
$diskOut = [IO.Path]::GetTempFileName()
$nodeFreeGb = if ($MirrorNode -and (Invoke-Bounded 'ssh' ($sshOpts + @($MirrorNode, "powershell -NoProfile -EncodedCommand $diskEnc")) 15000 $diskOut) -eq 0) {
    $v = (Get-Content -LiteralPath $diskOut -ErrorAction SilentlyContinue | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1); if ($v) { [int]$v } else { -1 }
}
else { -1 }
Remove-Item $diskOut -Force -ErrorAction SilentlyContinue
$diskLow = ($nodeFreeGb -ge 0 -and $nodeFreeGb -lt 15)
$idle = @()
if ($mirrorPct -ge 0 -and $mirrorPct -le $NodeIdlePct) { $idle += "mirror(${mirrorPct}%)" }
if ($computePct -ge 0 -and $computePct -le $NodeIdlePct) { $idle += "compute(${computePct}%)" }
$waste = ($box -ge $BoxSaturatedPct -and $idle.Count -gt 0)
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-utilization-monitor.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$wasAlarm = "$prior" -match 'WASTE|DISK-LOW'
$alarms = @()
if ($waste) { $alarms += "WASTE: box ${box}% saturated while idle: $($idle -join ', ') - offloadable capacity sitting unused" }
if ($diskLow) { $alarms += "DISK-LOW: mirror node C: ${nodeFreeGb}GB free (<15GB floor) - mirror scp/retention + node sims at risk" }
if ($alarms.Count) { $line = "node-utilization-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') $($alarms -join ' | ')" }
else { $line = "node-utilization-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') ok: box ${box}%, mirror ${mirrorPct}%, compute ${computePct}%, node-free ${nodeFreeGb}GB" }
Set-Content -LiteralPath $log -Value $line
$line
if ($alarms.Count) {
    if ($NoteOnWaste -and -not $wasAlarm) { & (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe') story note (Join-Path $PSScriptRoot '..\docs\backlog') REMOTE-OFFLOAD-FIRST "node-utilization ALARM: $line" --as resilience *> $null }
    exit 1
}
