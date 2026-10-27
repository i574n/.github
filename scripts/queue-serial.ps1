param([Parameter(Mandatory)] [string] $Label, [Parameter(Mandatory)] [string] $Manifest, [long] $TimeoutMs = 43200000, [string] $After)
$ErrorActionPreference = 'Stop'
$cli = 'C:\home\git\kit\kit.exe'
$out = Join-Path $env:TEMP "queued-$Label.txt"
$argv = @('job', 'start') + $(if ($After) { @('--after', $After) } else { @() }) + @($Label, "$TimeoutMs", 'pwsh', '-NoProfile', '-File', (Join-Path $PSScriptRoot 'serial-runner.ps1'), '-Manifest', $Manifest)
& $cli @argv > $out 2>&1
Get-Content $out
