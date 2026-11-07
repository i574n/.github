#requires -Version 5.1
$ErrorActionPreference = 'Continue'
$scripts = $PSScriptRoot
try { & (Join-Path $scripts 'orphan-reaper.ps1') } catch { "upkeep-tick: orphan-reaper failed: $($_.Exception.Message)" }
try { & (Join-Path $scripts 'wsl-policy.ps1') -Mode Block } catch { "upkeep-tick: wsl-policy failed: $($_.Exception.Message)" }
$freed = 0L; $pruned = 0
function Remove-Measured([IO.FileSystemInfo] $item) {
    $bytes = (Get-ChildItem -LiteralPath $item.FullName -Recurse -File -Force -ErrorAction Ignore | Measure-Object Length -Sum).Sum
    try { Remove-Item -LiteralPath $item.FullName -Recurse -Force -ErrorAction Stop; $script:freed += [long]$bytes; $script:pruned++ } catch { "upkeep-tick: kept $($item.FullName): $($_.Exception.Message)" }
}
$installs = Join-Path $env:LOCALAPPDATA 'spiral-bin\compiler-installs'
Get-ChildItem -LiteralPath $installs -Directory -ErrorAction Ignore | Sort-Object LastWriteTime -Descending | Select-Object -Skip 4 | ForEach-Object { Remove-Measured $_ }
$cellsTemp = Join-Path ([IO.Path]::GetTempPath()) 'spiral_cells'
$cutoff = (Get-Date).AddHours(-24)
Get-ChildItem -LiteralPath $cellsTemp -Force -ErrorAction Ignore | Where-Object { $_.LastWriteTime -lt $cutoff -and -not @(Get-ChildItem -LiteralPath $_.FullName -Recurse -Force -ErrorAction Ignore | Where-Object LastWriteTime -ge $cutoff).Count } | ForEach-Object { Remove-Measured $_ }
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\upkeep-tick.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Set-Content -LiteralPath $log -Value "upkeep-tick $(Get-Date -Format 'yyyy-MM-dd HH:mm') ran orphan-reaper + wsl-policy Block; pruned $pruned (pack undo beyond newest 4, Cells temp untouched 24h), freed $([math]::Round($freed / 1MB)) MB"
