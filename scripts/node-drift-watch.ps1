param([string[]] $Routes = @('vultr', 'ganymede', 'wifi'))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'node-env.ps1')

$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\node-drift-watch.last.log'
$priorLines = @(Get-Content -LiteralPath $log -ErrorAction SilentlyContinue)
$priorHead = (($priorLines | Select-Object -First 1) + '')
$priorState = ($priorHead -replace '^node-drift-watch \S+ \S+ ', '').Trim()
$priorDetail = (($priorLines | Select-Object -Skip 1 | Where-Object { $_ -like 'drift-watch *' }) -join "`n").Trim()

$r = Invoke-DriftWatch -Routes $Routes
$parts = @()
if ($r.drifted.Count) { $parts += "DRIFT: $($r.drifted -join ',')" }
if ($r.unreachable.Count) { $parts += "UNREACHABLE: $($r.unreachable -join ',')" }
if (-not $parts.Count) { $parts += 'ok' }
$newState = $parts -join ' '
$newDetail = (@($r.report) -join "`n").Trim()

if ($priorState -ne '' -and ($newState -ne $priorState -or $newDetail -ne $priorDetail)) {
    $kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
    $backlog = Join-Path $PSScriptRoot '..\docs\backlog'
    $stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
    $kind = if ($newState -eq $priorState) { 'detail change (same routes)' } else { 'state change' }
    $noteText = "DRIFT-WATCH edge ${stamp} ($kind): [$priorState] -> [$newState] | $($r.report -join ' | ')"
    $rc = 1
    foreach ($d in @(0, 2, 5, 10)) {
        if ($d -gt 0) { Start-Sleep -Seconds $d }
        & $kit agile note $backlog 'DELEGATE-13-DRIFT-WATCH' $noteText '--as' 'org3' 2>&1 | Out-Null
        $rc = $LASTEXITCODE
        if ($rc -eq 0) { break }
    }
    if ($rc -ne 0) { Add-Content -LiteralPath $log -Value "node-drift-watch $stamp NOTE FAILED rc=$rc ($kind edge [$priorState] -> [$newState] not delivered)" }
}
