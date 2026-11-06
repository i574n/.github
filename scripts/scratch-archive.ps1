param(
    [Parameter(Mandatory)][string] $Root,
    [string] $ListFile = '',
    [string] $Store = (Join-Path $env:LOCALAPPDATA 'kit\archive'),
    [string] $Restore = '',
    [string] $RestoreTo = '',
    [string] $Only = '',
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
function Get-Sha256([byte[]] $bytes) { [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) }
function Get-FileStamp([IO.FileInfo] $info) { "$($info.Length)|$($info.LastWriteTimeUtc.Ticks)" }
function Get-OwnEntries([string] $folder) {
    $files = [Collections.Generic.List[string]]::new()
    $links = [Collections.Generic.List[IO.FileSystemInfo]]::new()
    $stack = [Collections.Generic.Stack[string]]::new(); $stack.Push($folder)
    while ($stack.Count) {
        $dir = $stack.Pop()
        foreach ($entry in [IO.DirectoryInfo]::new($dir).EnumerateFileSystemInfos()) {
            if ($entry.Attributes -band [IO.FileAttributes]::ReparsePoint) { $links.Add($entry) }
            elseif ($entry -is [IO.DirectoryInfo]) { $stack.Push($entry.FullName) }
            else { $files.Add($entry.FullName) }
        }
    }
    [pscustomobject]@{ Files = $files; Links = $links }
}
if ($Restore) {
    $linksPath = Join-Path (Split-Path $Restore) 'links.tsv'
    $relinked = 0
    if (Test-Path -LiteralPath $linksPath) {
        foreach ($row in [IO.File]::ReadAllLines($linksPath)) {
            $relative, $kind, $linkTarget = $row -split "`t"
            if ($Only -and $relative -notlike $Only) { continue }
            $path = Join-Path $RestoreTo $relative
            New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
            New-Item -ItemType $kind -Path $path -Target $linkTarget | Out-Null
            $relinked++
        }
    }
    $blobs = [IO.Compression.ZipFile]::OpenRead((Join-Path (Split-Path $Restore) 'blobs.zip'))
    try {
        $restored = 0
        foreach ($row in [IO.File]::ReadAllLines($Restore)) {
            $relative, $hash, $null, $null = $row -split "`t"
            if ($Only -and $relative -notlike $Only) { continue }
            $target = Join-Path $RestoreTo $relative
            New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
            $stream = $blobs.GetEntry($hash).Open(); $out = [IO.File]::Create($target)
            try { $stream.CopyTo($out) } finally { $out.Dispose(); $stream.Dispose() }
            $restored++
        }
        "restored $restored files and $relinked links from $Restore into $RestoreTo"
    } finally { $blobs.Dispose() }
    exit 0
}
. (Join-Path $PSScriptRoot 'safe-delete.ps1')
$Root = Assert-SafeDeleteRoot $Root
$names = @(if ($ListFile) { [IO.File]::ReadAllLines($ListFile) | Where-Object { $_ } } else { [IO.Directory]::GetDirectories($Root) | ForEach-Object { Split-Path $_ -Leaf } })
if ($Only) {
    $names = @($names | Where-Object { $_ -like $Only })
    if (-not $names) { throw "-Only '$Only' matches no folder under $Root; nothing archived" }
}
foreach ($name in $names) { $null = Assert-SafeDeleteRoot (Join-Path $Root $name) -Under $Root }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $Store $stamp
New-Item -ItemType Directory -Force $run | Out-Null
$manifestPath = Join-Path $run 'manifest.tsv'
$manifest = [IO.StreamWriter]::new($manifestPath)
$zip = [IO.Compression.ZipFile]::Open((Join-Path $run 'blobs.zip'), 'Create')
$seen = [Collections.Generic.HashSet[string]]::new()
$files = 0; $bytes = [int64]0; $unique = [int64]0; $skipped = [Collections.Generic.List[string]]::new()
$linkRows = [Collections.Generic.List[string]]::new()
$ownLinks = @{}
$archivedStamps = @{}
try {
    foreach ($name in $names) {
        $folder = Join-Path $Root $name
        if (-not (Test-Path -LiteralPath $folder)) { $skipped.Add("$name (absent)"); continue }
        $own = Get-OwnEntries $folder
        $ownLinks[$name] = $own.Links
        foreach ($link in $own.Links) {
            $kind = if ($link.LinkType -eq 'Junction') { 'Junction' } else { 'SymbolicLink' }
            $linkRows.Add("$($link.FullName.Substring($Root.Length + 1))`t$kind`t$($link.LinkTarget)")
        }
        foreach ($path in $own.Files) {
            $info = [IO.FileInfo]::new($path)
            try { $content = [IO.File]::ReadAllBytes($path) } catch { $skipped.Add("$path (unreadable)"); continue }
            if ($content.Length -ne $info.Length) { $skipped.Add("$path (changed while reading)"); continue }
            $archivedStamps[$path] = Get-FileStamp $info
            $hash = Get-Sha256 $content
            if ($seen.Add($hash)) {
                $entry = $zip.CreateEntry($hash, [IO.Compression.CompressionLevel]::Optimal)
                $stream = $entry.Open(); try { $stream.Write($content, 0, $content.Length) } finally { $stream.Dispose() }
                $unique += $content.Length
            }
            $manifest.WriteLine("$($path.Substring($Root.Length + 1))`t$hash`t$($content.Length)`t$($info.LastWriteTimeUtc.ToString('o'))")
            $files++; $bytes += $content.Length
        }
    }
} finally { $manifest.Dispose(); $zip.Dispose() }
[IO.File]::WriteAllLines((Join-Path $run 'links.tsv'), $linkRows)
$verifyZip = [IO.Compression.ZipFile]::OpenRead((Join-Path $run 'blobs.zip'))
$bad = 0
try {
    $byName = @{}; foreach ($entry in $verifyZip.Entries) { $byName[$entry.FullName] = $entry }
    foreach ($hash in $seen) {
        $entry = $byName[$hash]
        if (-not $entry) { $bad++; continue }
        $stream = $entry.Open(); $memory = [IO.MemoryStream]::new()
        try { $stream.CopyTo($memory) } finally { $stream.Dispose() }
        if ((Get-Sha256 $memory.ToArray()) -ne $hash) { $bad++ }
    }
} finally { $verifyZip.Dispose() }
$zipGB = [math]::Round((Get-Item (Join-Path $run 'blobs.zip')).Length / 1GB, 3)
"archive $stamp -> ${run}: $($names.Count) folders, $files files, $($linkRows.Count) links recorded (not followed), $([math]::Round($bytes / 1GB, 2)) GB, unique content $([math]::Round($unique / 1GB, 2)) GB, blobs.zip $zipGB GB; blob verify failures: $bad; skipped: $($skipped.Count)"
$skipped | Select-Object -First 10 | ForEach-Object { "  skipped $_" }
if ($bad -or $skipped.Count) { 'originals kept: verify failed or files were skipped'; exit 1 }
if (-not $Apply) { 'dry: originals kept; rerun with -Apply to remove them after the same verify'; exit 0 }
$removed = 0
foreach ($name in $names) {
    $folder = Assert-SafeDeleteRoot (Join-Path $Root $name) -Under $Root
    if (-not (Test-Path -LiteralPath $folder)) { continue }
    $changed = @((Get-OwnEntries $folder).Files | Where-Object { $archivedStamps[$_] -ne (Get-FileStamp ([IO.FileInfo]::new($_))) })
    if ($changed.Count) { throw "files under $folder changed or appeared after the archive; nothing more removed: $(($changed | Select-Object -First 5) -join ', ')" }
    foreach ($link in $ownLinks[$name]) { $link.Delete() }
    $left = @(Get-OwnEntries $folder).Links
    if ($left.Count) { throw "links appeared under $folder after the archive; nothing more removed: $(($left | ForEach-Object FullName) -join ', ')" }
    Remove-Item -LiteralPath $folder -Recurse -Force -Confirm:$false; $removed++
}
"removed $removed archived folders; restore any with -Restore '$manifestPath' -RestoreTo <dir>"
