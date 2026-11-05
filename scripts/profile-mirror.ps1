param(
    [string[]] $Routes,
    [string] $RemoteDir,
    [int] $Keep = 48,
    [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
$nodeEnv = Join-Path $PSScriptRoot 'node-env.ps1'
if (Test-Path $nodeEnv) { . $nodeEnv }
if (-not $Routes) { $Routes = $NodeRoutes }
if (-not $RemoteDir) { $RemoteDir = $NodeMirrorDir }
$profileRoot = $env:USERPROFILE
$claude = Join-Path $profileRoot '.claude'
$sourceExtensions = '.ps1', '.psm1', '.psd1', '.exs', '.ex', '.spi', '.spir', '.md', '.tsv', '.csv', '.txt', '.h', '.c', '.json', '.toml', '.kit', '.do', '.sh', '.py', '.gleam', '.rs', '.fs', '.fsx'
$heavyFolders = '\\(lib\\spiral|mix-build|_build|node_modules|target|deps|\.cache|build|extracted|[^\\]*bundle\\(base|cand))(\\|$)'

$wholeTrees = @(
    (Join-Path $claude 'projects'),
    (Join-Path $claude 'hooks'),
    (Join-Path $claude 'subagent-hold'),
    (Join-Path $claude 'autonomous'),
    (Join-Path $env:LOCALAPPDATA 'kit\backups'),
    (Join-Path $env:LOCALAPPDATA 'kit\jobs'),
    (Join-Path $env:LOCALAPPDATA 'kit\windows'),
    (Join-Path $env:LOCALAPPDATA 'kit\config-baseline'),
    (Join-Path $profileRoot 'scoop\persist\notepadplusplus\backup'),

    (Join-Path $env:LOCALAPPDATA 'fiscal-undo'),
    (Join-Path $env:LOCALAPPDATA 'tibia-mariadb-dumps')
)
$singleFiles = @(
    (Join-Path $claude 'settings.json'),
    (Join-Path $profileRoot '.gitconfig'),
    (Join-Path $profileRoot '.config\remote-machines.json'),
    (Join-Path $profileRoot '.ssh\config'),
    (Join-Path $profileRoot '.ssh\known_hosts'),
    (Join-Path $profileRoot '.wslconfig'),
    (Join-Path $profileRoot '.npmrc'),
    (Join-Path $profileRoot '.config\scoop\config.json'),
    (Join-Path $profileRoot 'scoop\persist\notepadplusplus\session.xml'),
    (Join-Path $profileRoot 'scoop\persist\notepadplusplus\config.xml'),
    (Join-Path $env:LOCALAPPDATA 'Microsoft\Edge\User Data\Default\Bookmarks'),
    (Join-Path $env:LOCALAPPDATA 'Packages\Microsoft.WindowsTerminal_8wekyb3d8bbwe\LocalState\settings.json'),
    (Join-Path $env:APPDATA 'Microsoft\Spelling\neutral\default.dic'),
    (Join-Path $env:APPDATA 'Zed\settings.json'),
    (Join-Path $env:APPDATA 'Zed\keymap.json'),
    (Join-Path $profileRoot 'OneDrive\Documents\PowerShell\profile.ps1'),
    (Join-Path $profileRoot 'OneDrive\Documents\PowerShell\Microsoft.PowerShell_profile.ps1')
)
$globFiles = @(
    (Join-Path $profileRoot 'scoop\shims\scoop-*.ps1'),
    (Join-Path $profileRoot 'scoop\shims\shovel*'),
    (Join-Path $profileRoot 'scoop\persist\*\data\user-data\User\settings.json'),
    (Join-Path $profileRoot 'scoop\persist\*\data\user-data\User\keybindings.json')
)
$scratchRoot = Join-Path $env:TEMP 'claude'

$files = [Collections.Generic.List[string]]::new()
foreach ($tree in $wholeTrees | Where-Object { Test-Path $_ }) {
    Get-ChildItem $tree -Recurse -File -Force -ErrorAction SilentlyContinue | Where-Object { $_.Extension -ne '.lease' } | ForEach-Object { $files.Add($_.FullName) }
}
foreach ($file in $singleFiles | Where-Object { Test-Path $_ }) { $files.Add($file) }
if (Test-Path $scratchRoot) {
    Get-ChildItem $scratchRoot -Recurse -File -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -in $sourceExtensions -and $_.Length -lt 2MB -and $_.FullName -notmatch $heavyFolders -and $_.FullName -notmatch '\\tasks\\' } |
        ForEach-Object { $files.Add($_.FullName) }
}

$files = [Collections.Generic.List[string]]@($files | Where-Object { [IO.File]::Exists($_) })
$bytes = ($files | ForEach-Object { (Get-Item -LiteralPath $_ -Force -ErrorAction SilentlyContinue).Length } | Measure-Object -Sum).Sum
"profile-mirror: $($files.Count) files, $([Math]::Round($bytes / 1MB, 1)) MB"
if ($DryRun) { return }

foreach ($stale in @(Get-ChildItem $env:TEMP -Directory -Filter 'profile-mirror-*' -ErrorAction SilentlyContinue | Where-Object { ((Get-Date) - $_.LastWriteTime).TotalMinutes -gt 60 })) {
    Remove-Item -LiteralPath $stale.FullName -Recurse -Force -ErrorAction SilentlyContinue
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$work = Join-Path $env:TEMP "profile-mirror-$stamp"
New-Item -ItemType Directory -Force $work | Out-Null
$listFile = Join-Path $work 'files.txt'
$archive = Join-Path $work "profile-mirror-$stamp.tgz"
$snapshot = Join-Path $work 'snapshot'
$relative = [Collections.Generic.List[string]]::new()
$unreadable = [Collections.Generic.List[string]]::new()
foreach ($file in $files) {
    $rel = $file.Substring(3)
    $copy = Join-Path $snapshot $rel
    try { [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($copy)) | Out-Null; [IO.File]::Copy($file, $copy, $true); $relative.Add($rel) }
    catch { $unreadable.Add($file) }
}
if ($unreadable.Count) { "profile-mirror: skipped $($unreadable.Count) unreadable files: $(($unreadable | Select-Object -First 3) -join ', ')" }
[IO.File]::WriteAllLines($listFile, [string[]] $relative)
Push-Location $snapshot
try { tar -czf $archive -T $listFile 2>&1 | Select-Object -First 5; $tarExit = $LASTEXITCODE } finally { Pop-Location }
$entries = @(tar -tzf $archive 2>$null).Count
if ($tarExit -ne 0 -or $entries -ne $relative.Count) { throw "profile-mirror: invalid archive (tar exit $tarExit, $entries of $($relative.Count) entries); it stays at $archive" }
$hash = (Get-FileHash $archive).Hash

function ConvertTo-EncodedCommand([string] $script) { [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script)) }
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
$sshOpts = @('-o', 'ConnectTimeout=12', '-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4')
$winDir = $RemoteDir -replace '/', '\'
$reached = $null
foreach ($route in $Routes) {
    if ((Invoke-Bounded 'ssh' ($sshOpts + @($route, "cmd /c if not exist `"$winDir`" mkdir `"$winDir`"")) 15000 $null) -eq 0) { $reached = $route; break }
}
if (-not $reached) { throw "profile-mirror: no route reached the node ($($Routes -join ', ')); the archive stays at $archive" }
$arcName = Split-Path $archive -Leaf
if ((Invoke-Bounded 'scp' (@('-q') + $sshOpts + @($archive, "${reached}:$RemoteDir/$arcName.part")) 1800000 $null) -ne 0) {
    Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c del /q `"$winDir\$arcName.part`" 2>nul")) 15000 $null | Out-Null
    throw "profile-mirror: scp failed/timed out over $reached; the archive stays at $archive"
}
if ((Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c move /Y `"$winDir\$arcName.part`" `"$winDir\$arcName`"")) 15000 $null) -ne 0) { throw "profile-mirror: node move of $arcName.part failed over $reached; partial not promoted" }
$localSize = (Get-Item $archive).Length

$retListOut = [IO.Path]::GetTempFileName()
if ((Invoke-Bounded 'ssh' ($sshOpts + @($reached, "cmd /c dir /b /o-d `"$winDir\profile-mirror-*.tgz`"")) 20000 $retListOut) -eq 0) {
    $arcNames = @(Get-Content -LiteralPath $retListOut -ErrorAction SilentlyContinue) | Where-Object { $_ -match 'profile-mirror-\d{8}-\d{6}\.tgz' }
    $arcs = foreach ($n in $arcNames) { if ($n -match 'profile-mirror-(\d{8})-(\d{6})\.tgz') { [pscustomobject]@{ Name = $n.Trim(); T = [datetime]::ParseExact($matches[1] + $matches[2], 'yyyyMMddHHmmss', $null) } } }
    $arcs = @($arcs | Sort-Object T -Descending)
    $retNow = Get-Date; $keepSet = @{}
    $arcs | Select-Object -First 12 | ForEach-Object { $keepSet[$_.Name] = $true }
    $arcs | Group-Object { $_.T.ToString('yyyyMMdd') } | ForEach-Object { $grp = $_.Group | Sort-Object T -Descending | Select-Object -First 1; if (($retNow - $grp.T).TotalDays -le 14) { $keepSet[$grp.Name] = $true } }
    $arcs | Group-Object { '{0}w{1:00}' -f $_.T.Year, [int]($_.T.DayOfYear / 7) } | ForEach-Object { $grp = $_.Group | Sort-Object T -Descending | Select-Object -First 1; if (($retNow - $grp.T).TotalDays -le 56) { $keepSet[$grp.Name] = $true } }
    $toPrune = @($arcs | Where-Object { -not $keepSet.ContainsKey($_.Name) })
    if ($toPrune.Count -gt 0) {
        $delArgs = ($toPrune | ForEach-Object { '"' + $winDir + '\' + $_.Name + '"' }) -join ' '
        Invoke-Bounded 'ssh' ($sshOpts + @($reached, "del $delArgs")) 30000 $null | Out-Null
    }
    "profile-mirror: box-side retention kept $($keepSet.Count) pruned $($toPrune.Count) (robust under node saturation)"
}
Remove-Item $retListOut -Force -ErrorAction SilentlyContinue

$remoteScript = @"
`$dir = '$RemoteDir'
`$leaf = 'profile-mirror-$stamp.tgz'
`$f = Join-Path `$dir `$leaf
`$size = (Get-Item `$f).Length
`$entries = @(tar -tzf `$f 2>`$null).Count
`$all = Get-ChildItem `$dir -Filter 'profile-mirror-*.tgz' | Sort-Object LastWriteTime -Descending
`$prev = `$all | Where-Object { `$_.Name -ne `$leaf } | Select-Object -First 1
`$prevAgeMin = if (`$prev) { [int]((Get-Date) - `$prev.LastWriteTime).TotalMinutes } else { -1 }
`$keep = @{}
`$all | Select-Object -First 12 | ForEach-Object { `$keep[`$_.Name] = `$true }
`$now = Get-Date
`$all | Group-Object { `$_.LastWriteTime.ToString('yyyyMMdd') } | ForEach-Object { `$n = `$_.Group | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if ((`$now - `$n.LastWriteTime).TotalDays -le 14) { `$keep[`$n.Name] = `$true } }
`$all | Group-Object { '{0}w{1:00}' -f `$_.LastWriteTime.Year, [int](`$_.LastWriteTime.DayOfYear / 7) } | ForEach-Object { `$n = `$_.Group | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if ((`$now - `$n.LastWriteTime).TotalDays -le 56) { `$keep[`$n.Name] = `$true } }
`$pruned = @(`$all | Where-Object { -not `$keep.ContainsKey(`$_.Name) })
`$pruned | Remove-Item -Force -ErrorAction SilentlyContinue
`$kept = (Get-ChildItem `$dir -Filter 'profile-mirror-*.tgz').Count
`$unix = [int][double]::Parse((Get-Date -UFormat %s))
Set-Content (Join-Path `$dir 'profile-mirror-status.txt') "unix=`$unix stale_after_s=7200 newest=`$leaf kept=`$kept sha256=$hash"
"`$size `$entries `$prevAgeMin `$kept `$(`$pruned.Count)"
"@
$vout = [IO.Path]::GetTempFileName()
$vrc = Invoke-Bounded 'ssh' ($sshOpts + @($reached, "pwsh -NoProfile -EncodedCommand $(ConvertTo-EncodedCommand $remoteScript)")) 90000 $vout
$verified = $false; $kept = -1; $prunedCount = -1
if ($vrc -eq 0) {
    $remote = ((Get-Content -LiteralPath $vout -Tail 1 -ErrorAction SilentlyContinue) -split '\s+')
    if ($remote.Count -ge 2 -and [long]$remote[0] -eq $localSize -and [int]$remote[1] -eq $entries) {
        $verified = $true; $prevAgeMin = [int]$remote[2]; $kept = [int]$remote[3]; $prunedCount = [int]$remote[4]
        if ($prevAgeMin -gt 120) { "profile-mirror: STALE WARNING - previous newest node tgz was ${prevAgeMin}min old (>120); prior scheduled runs were silently failing" }
    }
    else { "profile-mirror: node verify MISMATCH (local $localSize/$entries, node $($remote[0])/$($remote[1])); tgz landed, integrity unconfirmed" }
}
else { "profile-mirror: verify+retention SKIPPED (rc $vrc; node likely saturated, remote pwsh could not start); tgz LANDED via scp, integrity unconfirmed this run" }
Remove-Item $vout -Force -ErrorAction SilentlyContinue
. (Join-Path $PSScriptRoot 'safe-delete.ps1')
Remove-TreeSafely $work
"profile-mirror: $reached $RemoteDir/profile-mirror-$stamp.tgz sha256 $hash$(if ($verified) { "; verified, tiered retention kept $kept pruned $prunedCount" } else { '; LANDED (verify/retention skipped this run)' }); stale_after=2h"
