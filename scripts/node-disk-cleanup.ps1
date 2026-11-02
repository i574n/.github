#requires -Version 5.1
param(
    [int] $MinAgeHours = 6,
    [switch] $Apply
)
$ErrorActionPreference = 'Continue'
$cut = (Get-Date).AddHours(-$MinAgeHours)
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
$c = Get-PSDrive C
$verb = if ($Apply) { 'reclaimed' } else { 'reclaimable' }
$summary = "node-disk-cleanup $(Get-Date -Format 'yyyy-MM-dd HH:mm') $verb ${totalMB}MB (age>${MinAgeHours}h); C: free $([int]($c.Free/1GB))GB"
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-disk-cleanup.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Set-Content -LiteralPath $log -Value (@($summary) + $report)
$summary
$report
