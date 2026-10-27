param([switch] $Apply)
$ErrorActionPreference = 'Stop'
$planDir = Join-Path $env:TEMP 'pause-requeue'
$cli = @('C:\home\git\kit\kit.exe', 'C:\home\git\kit\kit.exe') | Where-Object { Test-Path $_ } | Select-Object -First 1
if ($Apply -and -not $cli) { throw "kit.exe not found at ${cli}: requeue waits for kit's 'rename finalized'" }
$rows = @(Get-Content (Join-Path $planDir 'plan.json') -Raw | ConvertFrom-Json | Where-Object Safe | Sort-Object Id)
$extrasPath = Join-Path $planDir 'extras.json'
if (Test-Path $extrasPath) { $rows += @(Get-Content $extrasPath -Raw | ConvertFrom-Json) }
foreach ($row in $rows) {
    $label = $row.Label
    if ($row.Remaining -eq '-') {
        $argvLines = @($row.Inner -split "`n" | Where-Object { $_ -ne '' })
        $startArgs = @('job', 'start') + $(if ($row.Heavy) { @('--heavy') } else { @() }) + @($label, $row.Timeout) + $argvLines
        "standalone $label (cwd $($row.Cwd), heavy=$($row.Heavy)): $($argvLines -join ' ')"
        if ($Apply) { Push-Location $row.Cwd; try { & $cli @startArgs } finally { Pop-Location } }
        continue
    }
    if ([int]$row.Remaining -eq 0) { "done $label (nothing left)"; continue }
    $manifest = Join-Path $planDir "$label.json"
    $steps = @(Get-Content $manifest -Raw | ConvertFrom-Json)
    $after = if ($row.PSObject.Properties['After'] -and $row.After) { [string]$row.After } else { '' }
    "serial ${label}: $($steps.Label -join ' -> ')$(if ($after) { " (after $after)" })"
    if ($Apply) {
        $queueArgs = @('-Label', $label, '-Manifest', $manifest, '-TimeoutMs', ([long]$row.Timeout))
        if ($after) { $queueArgs += @('-After', $after) }
        pwsh -NoProfile -File (Join-Path $PSScriptRoot 'queue-serial.ps1') @queueArgs
    }
}
if (-not $Apply) { 'dry run; pass -Apply after rename finalized' }
