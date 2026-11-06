#requires -Version 5.1

param(

    [int] $MinAgeMinutes = 60,
    [string[]] $Classes = @('dotnet', 'node', 'cargo', 'rustc', 'erl', 'beam', 'epmd'),

    [int] $ShortMinAgeMinutes = 3,
    [string[]] $ShortClasses = @('gh', 'ssh', 'scp', 'git'),

    [string[]] $ShellClasses = @('pwsh', 'powershell', 'conhost'),
    [int] $ShellMinAgeMinutes = 720,
    [string[]] $PreserveCmdMatch = @('claude-boost-watcher', 'profile-mirror\.ps1', 'orphan-reaper', 'wsl-policy'),
    [int] $MirrorStuckMinutes = 30,
    [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
$all = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue
$live = @{}; foreach ($p in $all) { $live[[int]$p.ProcessId] = $true }
$preserve = ($PreserveCmdMatch -join '|')
$victims = $all | Where-Object {
    $name = $_.Name -replace '\.exe$', ''
    $grace = if ($name -in $ShellClasses) { $ShellMinAgeMinutes } elseif ($name -in $ShortClasses) { $ShortMinAgeMinutes } elseif ($name -in $Classes) { $MinAgeMinutes } else { return $false }
    -not $live.ContainsKey([int]$_.ParentProcessId) -and
    $_.ProcessId -ne $PID -and
    ((Get-Date) - $_.CreationDate).TotalMinutes -gt $grace -and
    ($preserve -eq '' -or "$($_.CommandLine)" -notmatch $preserve)
}
$report = [Collections.Generic.List[string]]::new()
foreach ($v in $victims) {
    $age = [int]((Get-Date) - $v.CreationDate).TotalMinutes
    $line = "{0} ppid={1}(dead) {2}min {3}MB {4}" -f $v.ProcessId, $v.ParentProcessId, $age, [int]($v.WorkingSetSize / 1MB), ($v.Name -replace '\.exe$', '')
    if (-not $DryRun) {
        $all | Where-Object { [int]$_.ParentProcessId -eq [int]$v.ProcessId } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $v.ProcessId -Force -ErrorAction SilentlyContinue
    }
    $report.Add($line)
}
$stuckMirror = $all | Where-Object { $_.Name -eq 'pwsh.exe' -and "$($_.CommandLine)" -match 'profile-mirror\.ps1' -and [int]$_.ProcessId -ne $PID -and ((Get-Date) - $_.CreationDate).TotalMinutes -gt $MirrorStuckMinutes }
foreach ($m in $stuckMirror) {
    $age = [int]((Get-Date) - $m.CreationDate).TotalMinutes
    if (-not $DryRun) {
        $all | Where-Object { [int]$_.ParentProcessId -eq [int]$m.ProcessId } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $m.ProcessId -Force -ErrorAction SilentlyContinue
    }
    $report.Add("stuck-mirror $($m.ProcessId) ${age}min (>${MirrorStuckMinutes}m) reaped")
}
$verb = if ($DryRun) { 'would reap' } else { 'reaped' }
$summary = "orphan-reaper $(Get-Date -Format 'yyyy-MM-dd HH:mm') $verb $($report.Count) (min-age ${MinAgeMinutes}m)"
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\orphan-reaper.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Set-Content -LiteralPath $log -Value (@($summary) + $report)
$summary
$report
