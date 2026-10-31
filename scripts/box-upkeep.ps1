param([switch] $Apply)
$ErrorActionPreference = 'Continue'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
$busy = @(& $kit job list 2>&1 | Select-String 'state=(running|queued)' | Where-Object { $_ -notmatch 'node-watch' })
$held = @(& $kit window list 2>&1 | Select-String '\sheld\s')
if ($busy.Count -or $held.Count) { "upkeep: refused, the box is busy:"; $busy + $held | ForEach-Object { "  $_" }; exit 3 }
$envBefore = [Environment]::GetEnvironmentVariables('User')
$snap = @{}; foreach ($k in $envBefore.Keys) { $snap[$k] = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes("$($envBefore[$k])"))).Substring(0, 12) }
"=== refresh buckets"
scoop update 2>&1 | Select-Object -Last 2
$status = scoop status 2>&1 | Out-String
$outdated = @($status -split "`n" | Where-Object { $_ -match '^(\S+)\s+(\S+)\s+(\S+)\s*$' -and $_ -notmatch '^(Name|----)' } | ForEach-Object { ($_ -split '\s+')[0] })
$broken = @($status -split "`n" | Where-Object { $_ -match 'Install failed' } | ForEach-Object { ($_ -split '\s+')[0] })
"outdated: $($outdated -join ', ')"
"half-installed (left for the user, who must close the apps): $($broken -join ', ')"
$scoopApps = Join-Path $HOME 'scoop\apps'
$results = foreach ($app in $outdated) {
    $running = @(Get-Process -ErrorAction SilentlyContinue | Where-Object { try { $_.Path -and $_.Path.StartsWith((Join-Path $scoopApps $app) + '\', [StringComparison]::OrdinalIgnoreCase) } catch { $false } })
    if ($running.Count) { "{0,-24} SKIPPED: {1} running process(es)" -f $app, $running.Count; continue }
    if (-not $Apply) { "{0,-24} would update (dry run)" -f $app; continue }
    try { $out = scoop update $app 2>&1 | Select-Object -Last 1; "{0,-24} {1}" -f $app, $out } catch { "{0,-24} FAILED: {1}" -f $app, $_.Exception.Message }
}
$results
"=== toolchains, verified by running them"
$checks = [ordered]@{
    'dotnet (bundled)' = { & (Join-Path $env:LOCALAPPDATA 'spiral-bin\toolchains\dotnet\dotnet.exe') --list-sdks | Select-Object -Last 1 }
    'dotnet (system)' = { dotnet --list-runtimes | Select-String 'NETCore.App 11' | Select-Object -Last 1 }
    'erl' = { erl -noshell -eval 'io:format("~s~n",[erlang:system_info(otp_release)]), halt().' }
    'mix' = { mix --version | Select-Object -Last 1 }
    'gleam' = { gleam --version }
    'cargo' = { cargo --version }
    'zig' = { zig version }
    'swiftc' = { swiftc --version 2>&1 | Select-Object -First 1 }
    'fpc' = { fpc -iV }
    'lua' = { lua -v 2>&1 }
    'bun' = { bun --version }
    'python312 numpy' = { & $env:SPIRAL_PYTHON -c "import numpy; print('numpy', numpy.__version__)" }
}
foreach ($name in $checks.Keys) { $o = try { (& $checks[$name] 2>&1 | Out-String).Trim() -replace '\s+', ' ' } catch { "ERROR $($_.Exception.Message)" }; "{0,-18} {1}" -f $name, $o.Substring(0, [Math]::Min(90, $o.Length)) }
"=== User env diff (names only; values compared by hash)"
$envAfter = [Environment]::GetEnvironmentVariables('User')
$added = @($envAfter.Keys | Where-Object { -not $snap.ContainsKey($_) })
$removed = @($snap.Keys | Where-Object { -not $envAfter.Contains($_) })
$changed = @($envAfter.Keys | Where-Object { $snap.ContainsKey($_) -and $snap[$_] -ne [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes("$($envAfter[$_])"))).Substring(0, 12) })
"added: $($added -join ', ')  removed: $($removed -join ', ')  changed: $($changed -join ', ')"
if ($removed.Count) { 'WARNING: an update REMOVED User env vars (the reinstall env_set trap): restore them from C:\home\data\rescue-2026-10-28\profile-snapshot-*' }
