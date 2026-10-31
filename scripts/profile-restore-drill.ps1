param(
    [string[]] $Routes = @('laptop', 'laptop-ts', 'laptop-ip'),
    [string] $RemoteDir = 'C:/home/profile-mirror',
    [string] $NodePython = 'C:\Users\alias\scoop\apps\python312\current\python.exe',
    [string] $MemoryDir = (Join-Path $env:USERPROFILE '.claude\projects\C--home-git\memory'),
    [string] $MemberFilter = 'projects/C--home-git/memory/',
    [int] $MinEntries = 1000,
    [int] $MaxArchiveAgeHours = 3,
    [switch] $NoteOnFailure
)
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\profile-restore-drill.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$nodeScript = Join-Path $PSScriptRoot 'profile-restore-drill-node.py'
$reached = $null
foreach ($route in $Routes) {
    scp -q -o ConnectTimeout=8 $nodeScript "${route}:$RemoteDir/profile-restore-drill-node.py" 2>$null
    if ($LASTEXITCODE -eq 0) { $reached = $route; break }
}
$failures = [Collections.Generic.List[string]]::new()
$summary = ''
if (-not $reached) {
    $failures.Add("no route reached the node ($($Routes -join ', '))")
} else {
    $raw = ssh -o ConnectTimeout=8 $reached "`"$NodePython`" -I $RemoteDir/profile-restore-drill-node.py $RemoteDir $MemberFilter" 2>&1
    $result = try { ($raw | Select-Object -Last 1) | ConvertFrom-Json } catch { [pscustomobject]@{ error = "unparsable node output: $(($raw | Select-Object -Last 2) -join ' ')" } }
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
        & $kit story note 'C:\home\git\i574n.github\docs\backlog' PROFILE-RESTORE-DRILL "scheduled drill $line" --as profile-restore-drill *> $null
    }
    exit 1
}
