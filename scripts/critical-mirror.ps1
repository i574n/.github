#requires -Version 5.1
param(
    [string[]] $Routes,
    [string] $RemoteDir
)
$ErrorActionPreference = 'Stop'
$nodeEnv = Join-Path $PSScriptRoot 'node-env.ps1'
if (Test-Path $nodeEnv) { . $nodeEnv }
if (-not $Routes) { $Routes = $NodeRoutes }
if (-not $RemoteDir) { $RemoteDir = $NodeMirrorDir }
function Invoke-Bounded([string] $exe, [string[]] $a, [int] $timeoutMs, [string] $outFile) {
    $errf = [IO.Path]::GetTempFileName()
    $p = if ($outFile) { Start-Process $exe -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardOutput $outFile -RedirectStandardError $errf }
    else { Start-Process $exe -ArgumentList $a -NoNewWindow -PassThru -RedirectStandardError $errf }
    if (-not $p.WaitForExit($timeoutMs)) {
        Get-CimInstance Win32_Process -Filter "ParentProcessId=$($p.Id)" -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
        Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue
        Remove-Item $errf -Force -ErrorAction SilentlyContinue
        return 124
    }
    $code = $p.ExitCode
    Remove-Item $errf -Force -ErrorAction SilentlyContinue
    return $code
}
$home2 = $env:USERPROFILE
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$stage = Join-Path $env:TEMP "critical-mirror-$stamp"
New-Item -ItemType Directory -Force $stage | Out-Null
Copy-Item (Get-AgentMemoryDir) (Join-Path $stage 'memory') -Recurse -Force
Copy-Item (Get-AgentHooksDir) (Join-Path $stage 'hooks') -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $stage 'config') | Out-Null
foreach ($f in (Get-AgentSettingsFile), (Join-Path $home2 '.gitconfig'), (Join-Path $home2 '.ssh\config'), (Join-Path $home2 '.config\remote-machines.json'), (Join-Path $home2 '.wslconfig')) {
    if (Test-Path $f) { Copy-Item $f (Join-Path $stage 'config') -Force }
}
# WIP off-box via the FAST lane: the hot profile-mirror stalls on live transcripts, so route the uncommitted-WIP safety net here too. Include the newest kit-unstaged snapshot only when it differs from the last LANDED one (stamp advanced on scp success), so most 15-min runs stay lean.
$unstagedRoot = Join-Path $env:LOCALAPPDATA 'kit\backups\unstaged'
$unstagedStamp = Join-Path $env:LOCALAPPDATA 'kit\schedules\critical-mirror.unstaged-stamp'
$includedSnap = $null
if (Test-Path $unstagedRoot) {
    $newestSnap = Get-ChildItem $unstagedRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    if ($newestSnap) {
        $lastLanded = if (Test-Path $unstagedStamp) { @(Get-Content -LiteralPath $unstagedStamp -ErrorAction SilentlyContinue)[0] } else { '' }
        if ($newestSnap.Name -ne $lastLanded) {
            Copy-Item $newestSnap.FullName (Join-Path $stage ('unstaged-' + $newestSnap.Name)) -Recurse -Force -ErrorAction SilentlyContinue
            $includedSnap = $newestSnap.Name
        }
    }
}
$tar = Join-Path $env:TEMP "critical-$stamp.tar"
Push-Location $stage; try { tar -cf $tar . 2>&1 | Out-Null } finally { Pop-Location }
$hash = (Get-FileHash $tar -Algorithm SHA256).Hash.ToLower()
$sizeMB = [Math]::Round((Get-Item $tar).Length / 1MB, 2)
$sshOpts = @('-o', 'ConnectTimeout=12', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')
$winDir = $RemoteDir -replace '/', '\'
$reached = $null
foreach ($route in $Routes) {
    if ((Invoke-Bounded 'ssh' ($sshOpts + @($route, "cmd /c if not exist `"$winDir`" mkdir `"$winDir`"")) 40000 $null) -eq 0) { $reached = $route; break }
}
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\critical-mirror.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
if (-not $reached) {
    $line = "critical-mirror $(Get-Date -Format 'yyyy-MM-dd HH:mm') FAIL: no route reached (${sizeMB}MB staged, kept local at $tar)"
    Set-Content -LiteralPath $log -Value $line; $line
    Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}
$arcName = "critical-$stamp.tar"
# 180s (was 60s): the critical FLOOR is the most important durability lane (WIP + core every 15 min); its small (~2-18MB) scp was TIMING OUT at 60s under slow-link contention from OTHER lanes' node transfers (e.g. x/serve scp to remote-stage), failing to land WIP. 180s lets the small transfer get through even when sharing the link; the lane only runs every 15 min so a 3-min bound is safe.
$scpRc = Invoke-Bounded 'scp' (@('-q') + $sshOpts + @($tar, "${reached}:$RemoteDir/$arcName.part")) 180000 $null
if ($scpRc -eq 0) { $scpRc = Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c move /Y `"$winDir\$arcName.part`" `"$winDir\$arcName`"")) 15000 $null }
else { Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c del /q `"$winDir\$arcName.part`" 2>nul")) 15000 $null | Out-Null }
if ($scpRc -eq 0 -and $includedSnap) { Set-Content -LiteralPath $unstagedStamp -Value $includedSnap }
Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c forfiles /p `"$winDir`" /m critical-*.tar /d -2 /c `"cmd /c del @path`"")) 15000 $null | Out-Null
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $tar -Force -ErrorAction SilentlyContinue
$wipTag = if ($includedSnap) { " +wip:$includedSnap" } else { '' }
$line = if ($scpRc -eq 0) { "critical-mirror $(Get-Date -Format 'yyyy-MM-dd HH:mm') ok: $reached $RemoteDir/critical-$stamp.tar ${sizeMB}MB sha256 $hash$wipTag" }
else { "critical-mirror $(Get-Date -Format 'yyyy-MM-dd HH:mm') FAIL: scp rc $scpRc over $reached (${sizeMB}MB)" }
Set-Content -LiteralPath $log -Value $line
$line
if ($scpRc -ne 0) { exit 1 }
