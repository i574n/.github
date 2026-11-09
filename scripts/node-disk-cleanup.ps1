#requires -Version 5.1
param(
    [int] $MinAgeHours = 6,
    [int] $StageAgeHours = 24,
    [int] $KeepCandidates = 2,
    [switch] $Apply
)
$ErrorActionPreference = 'Continue'
$cut = (Get-Date).AddHours(-$MinAgeHours)
$stageCut = (Get-Date).AddHours(-$StageAgeHours)
$temp = $env:TEMP
$targets = @(
    @('spiral_cells temp', (Join-Path $temp 'spiral_cells*')),
    @('stale profile-mirror work', (Join-Path $temp 'profile-mirror-*')),
    @('MSBuild temp', (Join-Path $temp 'bg*')),
    @('WSL crash dumps', (Join-Path $temp 'wsl-crashes*')),
    @('Diag output', (Join-Path $temp 'DiagOutputDir*')),
    @('NuGet scratch', (Join-Path $temp 'NuGetScratch*')),
    @('old tmp dirs', (Join-Path $temp 'tmp*'))
)
$totalMB = 0; $report = [Collections.Generic.List[string]]::new()
foreach ($t in $targets) {
    $items = Get-ChildItem -Path $t[1] -Force -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -lt $cut }
    if (-not $items) { continue }
    $mb = 0
    foreach ($it in $items) {
        $sz = if ($it.PSIsContainer) { (Get-ChildItem $it.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum } else { $it.Length }
        $mb += [int]($sz / 1MB)
        if ($Apply) { Remove-Item -LiteralPath $it.FullName -Recurse -Force -ErrorAction SilentlyContinue }
    }
    $totalMB += $mb
    $report.Add(("  {0,-26} {1} item(s) {2} MB" -f $t[0], $items.Count, $mb))
}
# DELEGATE-7 CLEANUP: per-job upload STAGES outlive scratch, so a longer gate (default 24h): a stage dir untouched
# for StageAgeHours has delivered its results (the delegation verb removes a stage on landing; this sweeps the ones
# that never got cleaned - a crashed/abandoned job). Prune by LastWriteTime, which an in-progress job keeps fresh,
# so a long-running job is never pruned mid-flight. Orphaned child process TREES are the orphan-reaper's job, not this.
foreach ($s in @(@('remote-stage (stale job uploads)', 'C:\home\remote-stage'))) {
    if (-not (Test-Path $s[1])) { continue }
    $items = Get-ChildItem -Path $s[1] -Directory -Force -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -lt $stageCut }
    if (-not $items) { continue }
    $mb = 0
    foreach ($it in $items) {
        $sz = (Get-ChildItem $it.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
        $mb += [int]($sz / 1MB)
        if ($Apply) { Remove-Item -LiteralPath $it.FullName -Recurse -Force -ErrorAction SilentlyContinue }
    }
    $totalMB += $mb
    $report.Add(("  {0,-26} {1} item(s) {2} MB (age>${StageAgeHours}h)" -f $s[0], $items.Count, $mb))
}
# DELEGATE-7 (compiler 11-07): build-candidate leaves %LOCALAPPDATA%\spiral-bin-candidates\<name> on the node; these
# are REUSED across packs (not pure scratch), so keep the newest N by LastWriteTime (the active candidate stays) and
# prune older ones, rather than an age cut that could delete a candidate queued for the next pack.
$candRoot = Join-Path $env:LOCALAPPDATA 'spiral-bin-candidates'
if (Test-Path $candRoot) {
    $old = @(Get-ChildItem $candRoot -Directory -Force -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -Skip $KeepCandidates)
    if ($old) {
        $mb = 0
        foreach ($it in $old) {
            $mb += [int]((Get-ChildItem $it.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum / 1MB)
            if ($Apply) { Remove-Item -LiteralPath $it.FullName -Recurse -Force -ErrorAction SilentlyContinue }
        }
        $totalMB += $mb
        $report.Add(("  {0,-26} {1} item(s) {2} MB (keep newest ${KeepCandidates})" -f 'spiral-bin-candidates', $old.Count, $mb))
    }
}
$c = Get-PSDrive C
$verb = if ($Apply) { 'reclaimed' } else { 'reclaimable' }
$summary = "node-disk-cleanup $(Get-Date -Format 'yyyy-MM-dd HH:mm') $verb ${totalMB}MB (age>${MinAgeHours}h); C: free $([int]($c.Free/1GB))GB"
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-disk-cleanup.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Set-Content -LiteralPath $log -Value (@($summary) + $report)
$summary
$report
