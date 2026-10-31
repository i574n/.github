param(
    [Parameter(Mandatory)][string] $Root,
    [string[]] $Repos = @('C:\home\git\spiral', 'C:\home\git\tibia', 'C:\home\git\kit', 'C:\home\git\x', 'C:\home\git\fiscal', 'C:\home\git\i574n.github', 'C:\home\git\dice', 'C:\home\git\alphabet', 'C:\home\git\polyglot'),
    [string[]] $Extensions = @('.spi', '.spir', '.ps1', '.exs', '.fs', '.rs', '.lua', '.py', '.gleam', '.md'),
    [int] $MinLine = 20,
    [switch] $History,
    [string] $Out = ''
)
$ErrorActionPreference = 'Stop'
$live = @{}
foreach ($repo in $Repos) {
    if (-not (Test-Path $repo)) { continue }
    foreach ($rel in @(git -C $repo ls-files -co --exclude-standard)) {
        $name = [IO.Path]::GetFileName($rel)
        if ([IO.Path]::GetExtension($name) -notin $Extensions) { continue }
        if (-not $live.ContainsKey($name)) { $live[$name] = [Collections.Generic.List[string]]::new() }
        $live[$name].Add((Join-Path $repo $rel))
    }
}
$past = @{}
if ($History) {
    foreach ($repo in $Repos) {
        if (-not (Test-Path $repo)) { continue }
        foreach ($rel in @(git -C $repo log --all --format= --name-only | Sort-Object -Unique)) {
            $name = [IO.Path]::GetFileName($rel)
            if (-not $name -or [IO.Path]::GetExtension($name) -notin $Extensions) { continue }
            if (-not $past.ContainsKey($name)) { $past[$name] = [Collections.Generic.List[object]]::new() }
            $past[$name].Add(@($repo, $rel))
        }
    }
}
function Add-Lines($set, $lines) {
    foreach ($line in $lines) { $t = "$line".Trim(); if ($t.Length -ge $MinLine) { [void] $set.Add($t) } }
}
function Get-Distinct([string] $path) {
    $set = [Collections.Generic.HashSet[string]]::new()
    Add-Lines $set ([IO.File]::ReadAllLines($path))
    , $set
}
function Get-Known([string] $name) {
    if ($cache.ContainsKey($name)) { return , $cache[$name] }
    $set = [Collections.Generic.HashSet[string]]::new()
    foreach ($path in @($live[$name])) { if ($path) { Add-Lines $set ([IO.File]::ReadAllLines($path)) } }
    foreach ($pair in @($past[$name])) {
        if (-not $pair) { continue }
        $repo, $rel = $pair
        foreach ($commit in @(git -C $repo log --all --format=%H -- $rel)) { Add-Lines $set @(git -C $repo show "${commit}:$rel" 2>$null) }
    }
    $cache[$name] = $set
    , $set
}
$cache = @{}
$rows = foreach ($folder in Get-ChildItem $Root -Directory) {
    $files = @(Get-ChildItem $folder.FullName -Recurse -File | Where-Object { $_.Extension -in $Extensions })
    $lines = 0; $found = 0; $orphans = [Collections.Generic.List[string]]::new()
    foreach ($file in $files) {
        $mine = Get-Distinct $file.FullName
        if (-not $mine.Count) { continue }
        if (-not $live[$file.Name] -and -not $past[$file.Name]) { $lines += $mine.Count; $orphans.Add($file.Name); continue }
        $union = Get-Known $file.Name
        $lines += $mine.Count
        $found += @($mine | Where-Object { $union.Contains($_) }).Count
    }
    $share = if ($lines) { [math]::Round(100 * $found / $lines) } else { 100 }
    $state = if (-not $lines) { 'empty' } elseif ($share -ge 95) { 'landed' } elseif ($share -ge 50) { 'partial' } else { 'absent' }
    [pscustomobject]@{ Folder = $folder.Name; Files = $files.Count; Lines = $lines; InRepo = "$share%"; State = $state; NoLiveName = (($orphans | Sort-Object -Unique | Select-Object -First 6) -join ',') }
}
if ($Out) { $rows | ConvertTo-Csv -Delimiter "`t" -UseQuotes Never | Set-Content $Out }
$rows | Sort-Object State, Folder | Format-Table -AutoSize | Out-String -Width 260
"recovered-drafts-census: $(@($rows).Count) folders / " + (($rows | Group-Object State | ForEach-Object { "$($_.Name) $($_.Count)" }) -join ' / ')
