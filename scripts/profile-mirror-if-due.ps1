param([int] $EveryMinutes = 50, [switch] $Wait)
$ErrorActionPreference = 'Stop'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
& $kit schedule due profile-mirror $EveryMinutes *> $null
if ($LASTEXITCODE -ne 0) { return }

foreach ($p in @(Get-CimInstance Win32_Process | Where-Object { "$($_.CommandLine)" -match '[\\/]profile-mirror\.ps1' -and $_.ProcessId -ne $PID })) {
    $ageMin = try { ((Get-Date) - $p.CreationDate).TotalMinutes } catch { 999 }
    if ($ageMin -gt 90) {
        Get-CimInstance Win32_Process | Where-Object { $_.ParentProcessId -eq $p.ProcessId } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
    } else { return }
}
$mirror = Join-Path $PSScriptRoot 'profile-mirror.ps1'
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\profile-mirror.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$run = "& '$mirror' *> '$log'; if (Select-String -LiteralPath '$log' -Pattern 'sha256 [0-9A-F]{64}' -Quiet) { & '$kit' schedule ran profile-mirror *> `$null }"
$started = Start-Process (Get-Process -Id $PID).Path -ArgumentList '-NoProfile', '-NonInteractive', '-Command', $run -WindowStyle Hidden -PassThru
if ($Wait) { $started.WaitForExit(); Get-Content -LiteralPath $log -Tail 2 }
