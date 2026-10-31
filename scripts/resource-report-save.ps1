param(
    [int] $Hours = 12,
    [string] $Dir = (Join-Path $env:LOCALAPPDATA 'kit\reports')
)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force $Dir | Out-Null
$out = Join-Path $Dir ("resource-{0:yyyyMMdd-HHmm}.md" -f (Get-Date))
$report = & (Join-Path $PSScriptRoot 'resource-report.ps1') -Hours $Hours -Node 2>&1 | Out-String -Width 200
Set-Content -LiteralPath $out -Value ("# Resource report, last $Hours h, {0:yyyy-MM-dd HH:mm}`n`n``````text`n{1}`n``````" -f (Get-Date), $report.TrimEnd())
$out
