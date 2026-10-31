param(
    [string] $ArchiveRoot = 'C:\home\remote-archive',
    [string] $Match = '^20261029-',
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
$buildNames = '^(_build|target|node_modules|\.cache|mix-build.*)$'
$freeBefore = [math]::Round((Get-PSDrive C).Free / 1GB, 2)
$archives = @(Get-ChildItem $ArchiveRoot -Directory | Where-Object Name -match $Match)
$links = @(foreach ($a in $archives) { Get-ChildItem $a.FullName -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue | ForEach-Object { [pscustomobject]@{ Path = $_.FullName; Target = "$($_.Target)"; TargetExists = (Test-Path -LiteralPath "$($_.Target)") } } })
$candidates = @(foreach ($a in $archives) {
    Get-ChildItem $a.FullName -Directory -Recurse -Depth 6 -Force -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -match $buildNames -and -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint) }
})
$outermost = @($candidates | Where-Object { $c = $_.FullName; -not ($candidates | Where-Object { $_.FullName -ne $c -and $c.StartsWith($_.FullName + '\', [StringComparison]::OrdinalIgnoreCase) }) })
$rows = foreach ($d in $outermost) {
    $inner = @(Get-ChildItem $d.FullName -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue)
    $bytes = (Get-ChildItem $d.FullName -Recurse -File -Force -ErrorAction SilentlyContinue | Measure-Object Length -Sum).Sum
    [pscustomobject]@{ Path = $d.FullName; GB = [math]::Round($bytes / 1GB, 2); LinksInside = $inner.Count; Safe = ($inner.Count -eq 0) }
}
"archives=$($archives.Count) junctions/links=$($links.Count) (targets existing: $(@($links | Where-Object TargetExists).Count)); build dirs=$(@($rows).Count), safe=$(@($rows | Where-Object Safe).Count), freeable=$([math]::Round(($rows | Where-Object Safe | Measure-Object GB -Sum).Sum, 2)) GB; C: free $freeBefore GB"
$rows | Where-Object { -not $_.Safe } | ForEach-Object { "  SKIP (has $($_.LinksInside) link(s) inside): $($_.Path)" }
if (-not $Apply) { 'dry run: nothing removed; rerun with -Apply'; return }
$manifest = Join-Path $ArchiveRoot ("cleanup-manifest-{0}.tsv" -f (Get-Date -Format 'yyyyMMdd-HHmm'))
($rows | ForEach-Object { "{0}`t{1}`t{2}" -f $_.Path, $_.GB, $_.Safe }) + ($links | ForEach-Object { "LINK`t{0}`t{1}" -f $_.Path, $_.Target }) | Set-Content -LiteralPath $manifest
$removed = 0
foreach ($r in $rows | Where-Object Safe) {
    $recheck = @(Get-ChildItem $r.Path -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue)
    if ($recheck.Count) { "  SKIP at removal (a link appeared): $($r.Path)"; continue }
    Remove-Item -LiteralPath $r.Path -Recurse -Force -Confirm:$false
    $removed++
}
$after = @($links | ForEach-Object { Test-Path -LiteralPath $_.Target })
"removed $removed build dirs; link targets still existing: $(@($after | Where-Object { $_ }).Count) of $($links.Count); C: free $freeBefore -> $([math]::Round((Get-PSDrive C).Free / 1GB, 2)) GB; manifest $manifest"
if (-not (Test-Path 'C:\home\git\spiral\deps')) { throw 'C:\home\git\spiral\deps is MISSING after cleanup' }
'deps checkout intact: True'
