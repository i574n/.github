#requires -Version 5.1
param(
    [int] $MaxAgeMinutes = 120,
    [string[]] $Routes,
    [string] $RemoteDir,
    [switch] $NoteOnStale
)
$ErrorActionPreference = 'Stop'
$nodeEnv = Join-Path $PSScriptRoot 'node-env.ps1'
if (Test-Path $nodeEnv) { . $nodeEnv }
if (-not $Routes) { $Routes = $NodeRoutes }
if (-not $RemoteDir) { $RemoteDir = $NodeMirrorDir }
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
$winDir = $RemoteDir -replace '/', '\'
$sshOpts = @('-o', 'ConnectTimeout=8', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')
$reached = $null; $newest = $null
foreach ($r in $Routes) {
    $out = [IO.Path]::GetTempFileName()
    $rc = Invoke-Bounded 'ssh' ($sshOpts + @($r, "cmd /c dir /b /o-d `"$winDir\profile-mirror-*.tgz`"")) 20000 $out
    if ($rc -eq 0) {
        $first = @(Get-Content -LiteralPath $out -ErrorAction SilentlyContinue | Where-Object { $_ -match 'profile-mirror-\d{8}-\d{6}\.tgz' })
        Remove-Item $out -Force -ErrorAction SilentlyContinue
        $reached = $r; $newest = if ($first.Count) { $first[0].Trim() } else { '' }
        break
    }
    Remove-Item $out -Force -ErrorAction SilentlyContinue
}
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\profile-mirror-monitor.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
$prior = (Get-Content -LiteralPath $log -Tail 1 -ErrorAction SilentlyContinue)
$wasBad = "$prior" -match 'STALE|FAIL|UNREACHABLE'
function Emit($line, $code) {
    Set-Content -LiteralPath $log -Value $line
    $line
    if ($code -ne 0 -and $NoteOnStale -and -not $script:wasBad) { & $kit story note (Join-Path $PSScriptRoot '..\docs\backlog') PROFILE-MIRROR-SILENT-FAIL "monitor ALARM (OK->bad transition): $line" --as resilience *> $null }
    exit $code
}
if (-not $reached) { Emit "profile-mirror-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') UNREACHABLE (node down all routes)" 3 }
if (-not $newest) { Emit "profile-mirror-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') FAIL via ${reached}: NO archive on node ($RemoteDir)" 1 }
if ($newest -notmatch 'profile-mirror-(\d{8})-(\d{6})\.tgz') { Emit "profile-mirror-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') FAIL via ${reached}: unparseable newest '$newest'" 1 }
$stamp = [datetime]::ParseExact($Matches[1] + $Matches[2], 'yyyyMMddHHmmss', $null)
$age = [int]((Get-Date) - $stamp).TotalMinutes
if ($age -gt $MaxAgeMinutes) { Emit "profile-mirror-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') STALE via ${reached}: newest $newest ${age}min old (> ${MaxAgeMinutes}min)" 1 }
Emit "profile-mirror-monitor $(Get-Date -Format 'yyyy-MM-dd HH:mm') OK via ${reached}: newest $newest ${age}min old" 0
