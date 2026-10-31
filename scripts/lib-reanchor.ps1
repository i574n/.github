param(
    [Parameter(Mandatory)] [string] $Base,
    [Parameter(Mandatory)] [string] $Draft,
    [Parameter(Mandatory)] [string] $Out,
    [Parameter(Mandatory)] [string] $Program,
    [string] $Lib = 'C:\home\git\spiral\lib\spiral',
    [switch] $DropAddedNameHeadings
)
$ErrorActionPreference = 'Stop'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
New-Item -ItemType Directory -Force $Out | Out-Null
$edits = [Collections.Generic.List[string]]::new()
$unresolved = 0
foreach ($draftFile in Get-ChildItem -LiteralPath $Draft -Filter *.spi -File) {
    $name = $draftFile.Name
    $baseFile = Join-Path $Base $name
    $liveFile = Join-Path $Lib $name
    if (-not (Test-Path -LiteralPath $baseFile)) { "lib-reanchor / $name has no base in $Base, skipped"; continue }
    $merged = Join-Path $Out $name
    Copy-Item -LiteralPath $liveFile $merged -Force
    git merge-file --diff3 -L live -L base -L draft $merged $baseFile $draftFile.FullName
    $conflicts = $LASTEXITCODE
    $liveLines = [Collections.Generic.HashSet[string]]::new([string[]][IO.File]::ReadAllLines($liveFile))
    $lines = [Collections.Generic.List[string]]::new()
    $hunk = @{ live = [Collections.Generic.List[string]]::new(); base = [Collections.Generic.List[string]]::new(); draft = [Collections.Generic.List[string]]::new() }
    $mode = 'normal'
    $liveSide = 0
    $dropped = 0
    $restored = 0
    foreach ($line in [IO.File]::ReadAllLines($merged)) {
        if ($mode -eq 'normal' -and $line -match '^<<<<<<< live$') { $mode = 'live'; $hunk.Values | ForEach-Object { $_.Clear() }; continue }
        if ($mode -eq 'live' -and $line -match '^\|\|\|\|\|\|\| base$') { $mode = 'base'; continue }
        if ($mode -in 'live', 'base' -and $line -eq '=======') { $mode = 'draft'; continue }
        if ($mode -eq 'draft' -and $line -match '^>>>>>>> draft$') {
            $liveSide += $hunk.live.Count
            $hunk.live | ForEach-Object { $lines.Add($_) }
            $deletedByLive = @{}
            foreach ($b in $hunk.base) { $deletedByLive[$b] = 1 + [int]$deletedByLive[$b] }
            foreach ($l in $hunk.live) { if ($deletedByLive[$l]) { $deletedByLive[$l]-- } }
            foreach ($d in $hunk.draft) {
                if ($d.Trim() -ne '' -and $deletedByLive[$d]) { $deletedByLive[$d]--; $restored++; continue }
                if ($DropAddedNameHeadings -and $d -match '^/// ### ' -and -not $liveLines.Contains($d)) { $dropped++; continue }
                $lines.Add($d)
            }
            $mode = 'normal'
            continue
        }
        if ($mode -ne 'normal') { $hunk[$mode].Add($line); continue }
        if ($DropAddedNameHeadings -and $line -match '^/// ### ' -and -not $liveLines.Contains($line)) { $dropped++; continue }
        $lines.Add($line)
    }
    if ($conflicts -and ($liveSide -or -not $DropAddedNameHeadings)) {
        $unresolved++
        "lib-reanchor / $name has $conflicts conflict(s) with live-side lines ($liveSide); left with markers for review"
        continue
    }
    [IO.File]::WriteAllText($merged, ($lines -join "`n") + "`n")
    $added = { param($a, $b) git diff --no-index -U0 $a $b | Select-Object -Skip 4 | Where-Object { $_ -match '^\+' -and $_ -notmatch '^\+\+\+' } | ForEach-Object { $_.Substring(1) } }
    $own = @(& $added $baseFile $draftFile.FullName | Where-Object { -not ($DropAddedNameHeadings -and $_ -match '^/// ### ') }) | Sort-Object
    $now = @(& $added $liveFile $merged) | Sort-Object
    if (@(Compare-Object $own $now).Count) {
        $unresolved++
        "lib-reanchor / ${name}: the merge adds lines the draft does not (or misses some of the draft's); left for review"
        continue
    }
    "lib-reanchor / $name conflicts $conflicts, added name headings dropped $dropped, base lines live deleted kept deleted $restored, live $((Get-FileHash -LiteralPath $liveFile).Hash.Substring(0, 8)) -> $((Get-FileHash -LiteralPath $merged).Hash.Substring(0, 8))"
    $hunks = @(git diff --no-index -U3 $liveFile $merged | Select-Object -Skip 4)
    if ($hunks.Count) { $edits.Add("edit $name"); $edits.Add('patch <<<'); $hunks | ForEach-Object { $edits.Add($_) }; $edits.Add('>>>') }
}
if ($unresolved) { "lib-reanchor / $unresolved file(s) need a manual merge; program not written"; exit 2 }
[IO.File]::WriteAllText($Program, ($edits -join "`n") + "`n")
$rehearse = Join-Path $Out 'rehearse'
if (Test-Path -LiteralPath $rehearse) { Remove-Item -LiteralPath $rehearse -Recurse -Force }
New-Item -ItemType Directory $rehearse | Out-Null
Get-ChildItem -LiteralPath $Out -Filter *.spi -File | ForEach-Object { Copy-Item -LiteralPath (Join-Path $Lib $_.Name) $rehearse }
Get-Content -Raw $Program | & $kit do $rehearse - 2>&1 | Select-Object -Last 1
foreach ($merged in Get-ChildItem -LiteralPath $Out -Filter *.spi -File) {
    $same = (Get-FileHash -LiteralPath $merged.FullName).Hash -eq (Get-FileHash -LiteralPath (Join-Path $rehearse $merged.Name)).Hash
    "lib-reanchor / rehearsed $($merged.Name) equals the merge: $same"
    if (-not $same) { exit 3 }
}
