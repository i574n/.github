param(
    [string[]] $Routes,
    [string] $RemoteDir,
    [string] $MemoryDir = (Join-Path $env:USERPROFILE '.claude\projects\C--home-git\memory'),
    [string] $MemberFilter = 'projects/C--home-git/memory/',
    [int] $MinEntries = 1000,
    [int] $MaxArchiveAgeHours = 3,
    [switch] $NoteOnFailure
)
$ErrorActionPreference = 'Stop'
$nodeEnv = Join-Path $PSScriptRoot 'node-env.ps1'
if (Test-Path $nodeEnv) { . $nodeEnv }
if (-not $Routes) { $Routes = $NodeRoutes }
if (-not $RemoteDir) { $RemoteDir = $NodeMirrorDir }
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\profile-restore-drill.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$nodeScript = Join-Path $PSScriptRoot 'profile-restore-drill-node.ps1'
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
$reached = $null
foreach ($route in $Routes) {
    if ((Invoke-Bounded 'scp' (@('-q') + $sshOpts + @($nodeScript, "${route}:$RemoteDir/profile-restore-drill-node.ps1")) 30000 $null) -eq 0) { $reached = $route; break }
}
$failures = [Collections.Generic.List[string]]::new()
$summary = ''
if (-not $reached) {
    $failures.Add("no route reached the node ($($Routes -join ', '))")
} else {
    $vout = [IO.Path]::GetTempFileName()
    $vrc = Invoke-Bounded 'ssh' ($sshOpts + @($reached, "pwsh -NoProfile -File $RemoteDir/profile-restore-drill-node.ps1 $RemoteDir $MemberFilter")) 180000 $vout
    $raw = Get-Content -LiteralPath $vout -ErrorAction SilentlyContinue
    Remove-Item $vout -Force -ErrorAction SilentlyContinue
    $result = if ($vrc -ne 0) { [pscustomobject]@{ error = "node verify timed out/failed (rc $vrc); node likely saturated" } }
    else { try { ($raw | Select-Object -Last 1) | ConvertFrom-Json } catch { [pscustomobject]@{ error = "unparsable node output: $(($raw | Select-Object -Last 2) -join ' ')" } } }
    if ($result.error) {
        $failures.Add("node: $($result.error)")
    } else {
        $stamp = [regex]::Match($result.archive, '(\d{8})-(\d{6})').Groups
        $archiveTime = [datetime]::ParseExact("$($stamp[1].Value)$($stamp[2].Value)", 'yyyyMMddHHmmss', $null)
        $ageH = [Math]::Round(((Get-Date) - $archiveTime).TotalHours, 1)
        if ($ageH -gt $MaxArchiveAgeHours) { $failures.Add("newest archive is ${ageH}h old (> ${MaxArchiveAgeHours}h): the mirror is stalled") }
        if ($result.entries -lt $MinEntries) { $failures.Add("$($result.archive) lists only $($result.entries) entries (< $MinEntries)") }
        $equal = 0; $newer = 0; $gone = 0
        foreach ($p in $result.hashes.PSObject.Properties) {
            $live = Join-Path $MemoryDir $p.Name
            if (-not (Test-Path -LiteralPath $live)) { $gone++; continue }
            if ((Get-Item -LiteralPath $live).LastWriteTime -gt $archiveTime) { $newer++; continue }
            if ((Get-FileHash -LiteralPath $live -Algorithm SHA256).Hash.ToLower() -eq $p.Value) { $equal++ }
            else { $failures.Add("$($p.Name) differs from the archived copy although unchanged since $archiveTime") }
        }
        if ($equal -eq 0) { $failures.Add("no memory file could be compared ($newer newer, $gone gone)") }
        $summary = "$($result.archive): $($result.entries) entries; memory $equal hash-equal, $newer changed since, $gone gone locally"
        if ($result.skipped) { $summary += "; skipped unreadable: $($result.skipped -join ', ')" }
    }
}
$verdict = if ($failures.Count) { "FAIL: $($failures -join '; ')" } else { 'PASS' }
$line = "profile-restore-drill $(Get-Date -Format 'yyyy-MM-dd HH:mm') $verdict | $summary"
Set-Content -LiteralPath $log -Value $line
$line
if ($failures.Count) {
    if ($NoteOnFailure) {
        $kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
        & $kit story note (Join-Path $PSScriptRoot '..\docs\backlog') PROFILE-RESTORE-DRILL "scheduled drill $line" --as profile-restore-drill *> $null
    }
    exit 1
}
