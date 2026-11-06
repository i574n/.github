#requires -Version 5.1
$ErrorActionPreference = 'Continue'
$scripts = $PSScriptRoot
try { & (Join-Path $scripts 'orphan-reaper.ps1') } catch { "upkeep-tick: orphan-reaper failed: $($_.Exception.Message)" }
try { & (Join-Path $scripts 'wsl-policy.ps1') -Mode Block } catch { "upkeep-tick: wsl-policy failed: $($_.Exception.Message)" }
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\upkeep-tick.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
Set-Content -LiteralPath $log -Value "upkeep-tick $(Get-Date -Format 'yyyy-MM-dd HH:mm') ran orphan-reaper + wsl-policy Block"
