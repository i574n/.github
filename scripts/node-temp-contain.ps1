param([switch] $Apply, [int] $CellsOlderThanHours = 3)
$ErrorActionPreference = 'Stop'
$temp = Join-Path $env:LOCALAPPDATA 'Temp'
$freeBefore = [math]::Round((Get-PSDrive C).Free / 1GB, 2)
$dumpDir = Join-Path $temp 'wsl-crashes'
$dumps = @(Get-ChildItem $dumpDir -File -Filter '*.dmp' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime)
$toZip = @($dumps | Select-Object -SkipLast 1)
"wsl dumps: $($dumps.Count) ($([math]::Round(($dumps | Measure-Object Length -Sum).Sum / 1MB)) MB); to archive: $($toZip.Count), keeping the newest raw"
$cellsRoot = Join-Path $temp 'spiral_cells'
$cut = (Get-Date).AddHours(-$CellsOlderThanHours)
$oldestLive = @(& C:\home\bin\eoie.exe job list 2>&1 | Select-String 'state=running' | ForEach-Object { if ("$_" -match 'elapsed_ms=(\d+)') { (Get-Date).AddMilliseconds(-[double]$Matches[1]) } }) | Sort-Object | Select-Object -First 1
if ($oldestLive -and $oldestLive.AddMinutes(-30) -lt $cut) { $cut = $oldestLive.AddMinutes(-30) }
"cells cutoff: $($cut.ToString('MM-dd HH:mm')) (the earlier of now-$CellsOlderThanHours h and the oldest live job start minus 30 min$(if ($oldestLive) { ': ' + $oldestLive.ToString('HH:mm') }))"
$cmdlines = @(Get-CimInstance Win32_Process | ForEach-Object { "$($_.CommandLine)" })
$stale = @(Get-ChildItem $cellsRoot -Directory -ErrorAction SilentlyContinue | Where-Object { $_.LastWriteTime -lt $cut } | Where-Object {
    $n = $_.Name
    -not ($cmdlines | Where-Object { $_ -like "*$n*" }) -and
    -not @(Get-ChildItem $_.FullName -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue).Count
})
$staleBytes = ($stale | ForEach-Object { (Get-ChildItem $_.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum } | Measure-Object -Sum).Sum
$all = @(Get-ChildItem $cellsRoot -Directory -ErrorAction SilentlyContinue)
"spiral_cells dirs: $($all.Count); stale (older than $CellsOlderThanHours h, no live process names them, no links inside): $($stale.Count) = $([math]::Round($staleBytes / 1GB, 2)) GB; C: free $freeBefore GB"
if (-not $Apply) { 'dry run: nothing changed; rerun with -Apply'; return }
$toZip = @($toZip | Where-Object { Test-Path -LiteralPath $_.FullName })
if ($toZip.Count) {
    $arch = 'C:\home\remote-archive\wsl-crash-dumps'
    New-Item -ItemType Directory -Force $arch | Out-Null
    $zip = Join-Path $arch ("wsl-crashes-{0}-{1}.zip" -f (Get-Date -Format 'yyyyMMdd-HHmmss'), $PID)
    Compress-Archive -Path ($toZip | ForEach-Object FullName) -DestinationPath $zip -CompressionLevel Optimal -ErrorAction Continue
    $toZip = @($toZip | Where-Object { Test-Path -LiteralPath $_.FullName })
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $z = [IO.Compression.ZipFile]::OpenRead($zip)
    try { $inZip = @{}; foreach ($e in $z.Entries) { $inZip[$e.Name] = $e.Length } } finally { $z.Dispose() }
    $bad = @($toZip | Where-Object { $inZip[$_.Name] -ne $_.Length })
    if ($bad.Count) { throw "zip verify failed for $($bad.Count) dumps; originals kept: $zip" }
    $toZip | ForEach-Object { Remove-Item -LiteralPath $_.FullName -Force }
    "archived $($toZip.Count) dumps into $zip ($([math]::Round((Get-Item $zip).Length / 1MB)) MB, verified by entry size); originals removed"
}
$removed = 0
foreach ($d in $stale) {
    if (@(Get-ChildItem $d.FullName -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue).Count) { continue }
    Remove-Item -LiteralPath $d.FullName -Recurse -Force -Confirm:$false; $removed++
}
"removed $removed stale spiral_cells dirs; C: free $freeBefore -> $([math]::Round((Get-PSDrive C).Free / 1GB, 2)) GB"
