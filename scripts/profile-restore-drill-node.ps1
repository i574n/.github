#requires -Version 7.0
param([string] $Root, [string] $MemberFilter)
$ErrorActionPreference = 'Stop'
$filter = $MemberFilter.ToLower()
$skipped = [Collections.Generic.List[string]]::new()
foreach ($a in (Get-ChildItem -LiteralPath $Root -Filter 'profile-mirror-*.tgz' -ErrorAction SilentlyContinue | Sort-Object Name -Descending)) {
    $hashes = [ordered]@{}; $entries = 0; $fs = $null; $gz = $null; $tar = $null
    try {
        $fs = [IO.File]::OpenRead($a.FullName)
        $gz = [IO.Compression.GZipStream]::new($fs, [IO.Compression.CompressionMode]::Decompress)
        $tar = [System.Formats.Tar.TarReader]::new($gz)
        $sha = [Security.Cryptography.SHA256]::Create()
        while ($null -ne ($e = $tar.GetNextEntry())) {
            $entries++
            $name = $e.Name -replace '\\', '/'
            if ($e.EntryType -eq [System.Formats.Tar.TarEntryType]::RegularFile -and $name.ToLower().Contains($filter)) {
                $hex = ([BitConverter]::ToString($sha.ComputeHash($e.DataStream)) -replace '-', '').ToLower()
                $hashes[($name -split '/')[-1]] = $hex
            }
        }
    }
    catch { $skipped.Add("$($a.Name): $($_.Exception.GetType().Name)") }
    finally { if ($tar) { $tar.Dispose() }; if ($gz) { $gz.Dispose() }; if ($fs) { $fs.Dispose() } }
    if ($skipped.Count -and $skipped[-1].StartsWith($a.Name)) { continue }
    [pscustomobject]@{ archive = $a.Name; entries = $entries; hashes = $hashes; skipped = $skipped } | ConvertTo-Json -Compress -Depth 5
    exit 0
}
[pscustomobject]@{ error = 'no readable archive'; skipped = $skipped } | ConvertTo-Json -Compress
exit 2
