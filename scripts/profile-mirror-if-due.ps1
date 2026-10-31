param([int] $EveryMinutes = 50, [switch] $Wait)
$ErrorActionPreference = 'Stop'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
& $kit schedule due profile-mirror $EveryMinutes *> $null
if ($LASTEXITCODE -ne 0) { return }
$mirror = Join-Path $PSScriptRoot 'profile-mirror.ps1'
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\profile-mirror.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$run = "& '$mirror' *> '$log'; if (Select-String -LiteralPath '$log' -Pattern 'sha256 [0-9A-F]{64}' -Quiet) { & '$kit' schedule ran profile-mirror *> `$null }"
$started = Start-Process (Get-Process -Id $PID).Path -ArgumentList '-NoProfile', '-NonInteractive', '-Command', $run -WindowStyle Hidden -PassThru
if ($Wait) { $started.WaitForExit(); Get-Content -LiteralPath $log -Tail 2 }
