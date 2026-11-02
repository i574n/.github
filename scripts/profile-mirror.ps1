param(
    [string[]] $Routes = @('laptop', 'laptop-ts', 'laptop-ip'),
    [string] $RemoteDir = 'C:/home/profile-mirror',
    [int] $Keep = 48,
    [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
$profileRoot = $env:USERPROFILE
$claude = Join-Path $profileRoot '.claude'
$sourceExtensions = '.ps1', '.psm1', '.psd1', '.exs', '.ex', '.spi', '.spir', '.md', '.tsv', '.csv', '.txt', '.h', '.c', '.json', '.toml', '.kit', '.do', '.sh', '.py', '.gleam', '.rs', '.fs', '.fsx'
$heavyFolders = '\\(lib\\spiral|mix-build|_build|node_modules|target|deps|\.cache|build|extracted|[^\\]*bundle\\(base|cand))(\\|$)'

$wholeTrees = @(
    (Join-Path $claude 'projects'),
    (Join-Path $claude 'hooks'),
    (Join-Path $claude 'autonomous'),
    (Join-Path $env:LOCALAPPDATA 'kit\backups'),
    (Join-Path $env:LOCALAPPDATA 'kit\jobs'),
    (Join-Path $env:LOCALAPPDATA 'kit\windows'),
    (Join-Path $env:LOCALAPPDATA 'kit\config-baseline'),
    (Join-Path $profileRoot 'scoop\persist\notepadplusplus\backup'),
    # Lane-requested irreproducible stores (added via the resilience lane). Keep small.
    (Join-Path $env:LOCALAPPDATA 'fiscal-undo'),
    (Join-Path $env:LOCALAPPDATA 'tibia-mariadb-dumps')
)
$singleFiles = @(
    (Join-Path $claude 'settings.json'),
    (Join-Path $profileRoot '.gitconfig'),
    (Join-Path $profileRoot '.config\remote-machines.json'),
    (Join-Path $profileRoot '.ssh\config'),
    (Join-Path $profileRoot '.ssh\known_hosts'),
    'C:\home\data\rescue-2026-10-28\loss-map.md',
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
$reached = $null
$makeDir = ConvertTo-EncodedCommand "New-Item -ItemType Directory -Force '$RemoteDir' | Out-Null"
foreach ($route in $Routes) {
    ssh -o ConnectTimeout=8 $route "pwsh -NoProfile -EncodedCommand $makeDir" 2>$null
    if ($LASTEXITCODE -eq 0) { $reached = $route; break }
}
if (-not $reached) { throw "profile-mirror: no route reached the node ($($Routes -join ', ')); the archive stays at $archive" }
scp -q $archive "${reached}:$RemoteDir/"
if ($LASTEXITCODE -ne 0) { throw "profile-mirror: scp failed over $reached; the archive stays at $archive" }
$localSize = (Get-Item $archive).Length
# Node-side (fail-loud): size+entry verify of the landed tgz, previous-newest staleness age, TIERED retention
# (12 hourly + 1/day for 14d + 1/week for 8w: frees most space but keeps history - an old snapshot may be the only
# copy of something deleted since), and a stale_after status file so a monitor can detect a silently frozen mirror.
# A full remote Get-FileHash over ~800 MB was the hot-path wedge point (n19); size+entries catches truncation cheaply.
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
$remote = (ssh -o ConnectTimeout=8 $reached "pwsh -NoProfile -EncodedCommand $(ConvertTo-EncodedCommand $remoteScript)" 2>$null | Select-Object -Last 1) -split '\s+'
if ($remote.Count -lt 2 -or [long]$remote[0] -ne $localSize -or [int]$remote[1] -ne $entries) { throw "profile-mirror: node verify mismatch (local $localSize B/$entries entries, node $($remote[0]) B/$($remote[1]) entries); the archive stays at $archive" }
$prevAgeMin = [int]$remote[2]; $kept = [int]$remote[3]; $prunedCount = [int]$remote[4]
if ($prevAgeMin -gt 120) { "profile-mirror: STALE WARNING - previous newest node tgz was ${prevAgeMin}min old (>120); prior scheduled runs were silently failing" }
. (Join-Path $PSScriptRoot 'safe-delete.ps1')
Remove-TreeSafely $work
"profile-mirror: $reached $RemoteDir/profile-mirror-$stamp.tgz sha256 $hash; tiered retention kept $kept, pruned $prunedCount; stale_after=2h"
