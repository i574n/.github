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
    (Join-Path $env:LOCALAPPDATA 'kit\windows')
)
$singleFiles = @(
    (Join-Path $claude 'settings.json'),
    (Join-Path $profileRoot '.gitconfig'),
    (Join-Path $profileRoot '.config\remote-machines.json'),
    (Join-Path $profileRoot '.ssh\config'),
    (Join-Path $profileRoot '.ssh\known_hosts'),
    'C:\home\data\rescue-2026-10-28\loss-map.md'
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
$check = "`$f = '$RemoteDir/profile-mirror-$stamp.tgz'; (Get-FileHash `$f).Hash; Get-ChildItem '$RemoteDir' -Filter 'profile-mirror-*.tgz' | Sort-Object Name -Descending | Select-Object -Skip $Keep | Remove-Item"
$remoteHash = ssh $reached "pwsh -NoProfile -EncodedCommand $(ConvertTo-EncodedCommand $check)" 2>$null | Select-Object -First 1
if ("$remoteHash".Trim() -ne $hash) { throw "profile-mirror: hash mismatch on the node (local $hash, node $remoteHash); the archive stays at $archive" }
. (Join-Path $PSScriptRoot 'safe-delete.ps1')
Remove-TreeSafely $work
"profile-mirror: $reached $RemoteDir/profile-mirror-$stamp.tgz sha256 $hash, keeping the newest $Keep"
