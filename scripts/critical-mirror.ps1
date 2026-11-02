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
Copy-Item (Join-Path $home2 '.claude\projects\C--home-git\memory') (Join-Path $stage 'memory') -Recurse -Force
Copy-Item (Join-Path $home2 '.claude\hooks') (Join-Path $stage 'hooks') -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force (Join-Path $stage 'config') | Out-Null
foreach ($f in (Join-Path $home2 '.claude\settings.json'), (Join-Path $home2 '.gitconfig'), (Join-Path $home2 '.ssh\config'), (Join-Path $home2 '.config\remote-machines.json'), (Join-Path $home2 '.wslconfig')) {
    if (Test-Path $f) { Copy-Item $f (Join-Path $stage 'config') -Force }
}
$tar = Join-Path $env:TEMP "critical-$stamp.tar"
Push-Location $stage; try { tar -cf $tar . 2>&1 | Out-Null } finally { Pop-Location }
$hash = (Get-FileHash $tar -Algorithm SHA256).Hash.ToLower()
$sizeMB = [Math]::Round((Get-Item $tar).Length / 1MB, 2)
$sshOpts = @('-o', 'ConnectTimeout=8', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')
$winDir = $RemoteDir -replace '/', '\'
$reached = $null
foreach ($route in $Routes) {
    if ((Invoke-Bounded 'ssh' ($sshOpts + @($route, "cmd /c if not exist `"$winDir`" mkdir `"$winDir`"")) 15000 $null) -eq 0) { $reached = $route; break }
}
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\critical-mirror.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
if (-not $reached) {
    $line = "critical-mirror $(Get-Date -Format 'yyyy-MM-dd HH:mm') FAIL: no route reached (${sizeMB}MB staged, kept local at $tar)"
    Set-Content -LiteralPath $log -Value $line; $line
    Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
    exit 1
}
$scpRc = Invoke-Bounded 'scp' (@('-q') + $sshOpts + @($tar, "${reached}:$RemoteDir/")) 60000 $null
Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c forfiles /p `"$winDir`" /m critical-*.tar /d -2 /c `"cmd /c del @path`"")) 15000 $null | Out-Null
Remove-Item $stage -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item $tar -Force -ErrorAction SilentlyContinue
$line = if ($scpRc -eq 0) { "critical-mirror $(Get-Date -Format 'yyyy-MM-dd HH:mm') ok: $reached $RemoteDir/critical-$stamp.tar ${sizeMB}MB sha256 $hash" }
else { "critical-mirror $(Get-Date -Format 'yyyy-MM-dd HH:mm') FAIL: scp rc $scpRc over $reached (${sizeMB}MB)" }
Set-Content -LiteralPath $log -Value $line
$line
if ($scpRc -ne 0) { exit 1 }
