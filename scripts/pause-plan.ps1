param([string] $Owner = 'main', [switch] $Stop)
$ErrorActionPreference = 'Stop'
$jobsRoot = Join-Path $env:LOCALAPPDATA 'kit\jobs'
$kit = 'C:\home\git\kit\kit.exe'
$planDir = Join-Path $env:TEMP 'pause-requeue'
New-Item -ItemType Directory -Force $planDir | Out-Null
$live = @(& $kit job list 2>&1 | ForEach-Object { [regex]::Match($_, 'id=(\S+) state=(running|queued)') } | Where-Object Success |
    ForEach-Object { [pscustomobject]@{ Id = $_.Groups[1].Value; State = $_.Groups[2].Value } } | Where-Object { $_.Id -match "-$Owner-" })
$rows = foreach ($job in $live) {
    $dir = Join-Path $jobsRoot $job.Id
    $argvFile = @('argv.inner', 'argv') | ForEach-Object { Join-Path $dir $_ } | Where-Object { Test-Path $_ } | Select-Object -First 1
    $inner = [IO.File]::ReadAllText($argvFile).Trim()
    $label = [IO.File]::ReadAllText((Join-Path $dir 'label')).Trim()
    $timeout = [IO.File]::ReadAllText((Join-Path $dir 'timeout_ms')).Trim()
    $manifestMatch = [regex]::Match($inner, '-Manifest\s+"?([^"]+\.json)')
    $phase = 'standalone'
    $remaining = $null
    $current = ''
    if ($manifestMatch.Success) {
        $manifest = $manifestMatch.Groups[1].Value.Trim()
        $steps = @(Get-Content $manifest -Raw | ConvertFrom-Json)
        $logPath = [IO.Path]::ChangeExtension($manifest, '.log')
        $startedAt = [DateTimeOffset]::FromUnixTimeMilliseconds([long][IO.File]::ReadAllText((Join-Path $dir 'started_ms')).Trim()).LocalDateTime
        $logLines = @(if ((Test-Path $logPath) -and (Get-Item $logPath).LastWriteTime -ge $startedAt -and $job.State -eq 'running') { Get-Content $logPath })
        $runStart = -1
        for ($k = 0; $k -lt $logLines.Count; $k++) { if ($logLines[$k] -match " start $([regex]::Escape($steps[0].Label)) heavy") { $runStart = $k } }
        $logLines = @(if ($runStart -ge 0) { $logLines[$runStart..($logLines.Count - 1)] })
        $ended = @($logLines | ForEach-Object { [regex]::Match($_, ' end (\S+) exit') } | Where-Object Success | ForEach-Object { $_.Groups[1].Value })
        $started = @($logLines | ForEach-Object { [regex]::Match($_, ' start (\S+) heavy') } | Where-Object Success | ForEach-Object { $_.Groups[1].Value })
        $open = @($started | Where-Object { $_ -notin $ended })
        $remaining = @($steps | Where-Object { $_.Label -notin $ended })
        $current = if ($open.Count) { $open[-1] } else { '' }
        $currentStep = $steps | Where-Object Label -eq $current | Select-Object -First 1
        $phase = if (-not $current) { 'between-steps' } elseif ($current -match '-land-|land\.ps1') { 'LANDING' } elseif ($currentStep.Heavy) { 'heavy-step' } else { 'light-step' }
        if ($remaining.Count) { $remaining | ConvertTo-Json -Depth 4 -AsArray | Set-Content -Path (Join-Path $planDir "$label.json") -Encoding utf8NoBOM }
    }
    $safe = $phase -ne 'LANDING'
    $heavy = Test-Path (Join-Path $dir 'argv.inner')
    $cwd = [IO.File]::ReadAllText((Join-Path $dir 'cwd')).Trim()
    [pscustomobject]@{ Id = $job.Id; State = $job.State; Label = $label; Phase = $phase; Current = $current; Remaining = if ($null -ne $remaining) { $remaining.Count } else { '-' }; Safe = $safe; Timeout = $timeout; Heavy = $heavy; Cwd = $cwd; Inner = $inner }
}
$rows | ConvertTo-Json -Depth 4 | Set-Content -Path (Join-Path $planDir 'plan.json') -Encoding utf8NoBOM
$rows | Format-Table Id, State, Phase, Current, Remaining, Safe -AutoSize | Out-String -Width 220
if (-not $Stop) { "dry run: plan in $planDir; pass -Stop to stop the safe ones"; return }
foreach ($row in $rows | Where-Object Safe) {
    pwsh -NoProfile -File (Join-Path $PSScriptRoot 'job-stop.ps1') -Id $row.Id
}
$held = @($rows | Where-Object { -not $_.Safe })
"stopped $(@($rows | Where-Object Safe).Count); held (landing, left to finish): $($held.Count) $($held.Id -join ' ')"
