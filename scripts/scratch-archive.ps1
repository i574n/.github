param(
    [Parameter(Mandatory)][string] $Root,
    [string] $ListFile = '',
    [string] $Store = (Join-Path $env:LOCALAPPDATA 'kit\archive'),
    [string] $Restore = '',
    [string] $RestoreTo = '',
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression, System.IO.Compression.FileSystem
function Get-Sha256([byte[]] $bytes) { [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($bytes)) }
if ($Restore) {
    $blobs = [IO.Compression.ZipFile]::OpenRead((Join-Path (Split-Path $Restore) 'blobs.zip'))
    try {
        $restored = 0
        foreach ($row in [IO.File]::ReadAllLines($Restore)) {
            $relative, $hash, $null, $null = $row -split "`t"
            $target = Join-Path $RestoreTo $relative
            New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
            $stream = $blobs.GetEntry($hash).Open(); $out = [IO.File]::Create($target)
            try { $stream.CopyTo($out) } finally { $out.Dispose(); $stream.Dispose() }
            $restored++
        }
        "restored $restored files from $Restore into $RestoreTo"
    } finally { $blobs.Dispose() }
    exit 0
}
$names = @(if ($ListFile) { [IO.File]::ReadAllLines($ListFile) | Where-Object { $_ } } else { [IO.Directory]::GetDirectories($Root) | ForEach-Object { Split-Path $_ -Leaf } })
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$run = Join-Path $Store $stamp
New-Item -ItemType Directory -Force $run | Out-Null
$manifestPath = Join-Path $run 'manifest.tsv'
$manifest = [IO.StreamWriter]::new($manifestPath)
$zip = [IO.Compression.ZipFile]::Open((Join-Path $run 'blobs.zip'), 'Create')
$seen = [Collections.Generic.HashSet[string]]::new()
$files = 0; $bytes = [int64]0; $unique = [int64]0; $skipped = [Collections.Generic.List[string]]::new()
try {
    foreach ($name in $names) {
        $folder = Join-Path $Root $name
        if (-not (Test-Path -LiteralPath $folder)) { $skipped.Add("$name (absent)"); continue }
        foreach ($path in [IO.Directory]::EnumerateFiles($folder, '*', 'AllDirectories')) {
            try { $content = [IO.File]::ReadAllBytes($path) } catch { $skipped.Add("$path (unreadable)"); continue }
            $hash = Get-Sha256 $content
            if ($seen.Add($hash)) {
                $entry = $zip.CreateEntry($hash, [IO.Compression.CompressionLevel]::Optimal)
                $stream = $entry.Open(); try { $stream.Write($content, 0, $content.Length) } finally { $stream.Dispose() }
                $unique += $content.Length
            }
            $info = [IO.FileInfo]::new($path)
            $manifest.WriteLine("$($path.Substring($Root.Length + 1))`t$hash`t$($content.Length)`t$($info.LastWriteTimeUtc.ToString('o'))")
            $files++; $bytes += $content.Length
        }
    }
} finally { $manifest.Dispose(); $zip.Dispose() }
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
"archive $stamp -> ${run}: $($names.Count) folders, $files files, $([math]::Round($bytes / 1GB, 2)) GB, unique content $([math]::Round($unique / 1GB, 2)) GB, blobs.zip $zipGB GB; blob verify failures: $bad; skipped: $($skipped.Count)"
$skipped | Select-Object -First 10 | ForEach-Object { "  skipped $_" }
if ($bad -or $skipped.Count) { 'originals kept: verify failed or files were skipped'; exit 1 }
if (-not $Apply) { 'dry: originals kept; rerun with -Apply to remove them after the same verify'; exit 0 }
$removed = 0
foreach ($name in $names) {
    $folder = Join-Path $Root $name
    if (Test-Path -LiteralPath $folder) { Remove-Item -LiteralPath $folder -Recurse -Force -Confirm:$false; $removed++ }
}
"removed $removed archived folders; restore any with -Restore '$manifestPath' -RestoreTo <dir>"
