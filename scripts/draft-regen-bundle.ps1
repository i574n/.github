param(
    [Parameter(Mandatory)] [string] $Src,
    [Parameter(Mandatory)] [string] $Overlay,
    [Parameter(Mandatory)] [string] $LibBase,
    [Parameter(Mandatory)] [string] $LibCandidate,
    [Parameter(Mandatory)] [string] $Dest,
    [string[]] $Changed,
    [string] $Control = 'agile_note_listing'
)
$ErrorActionPreference = 'Stop'
$Changed = @($Changed | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
if (Test-Path $Dest) { Remove-Item $Dest -Recurse -Force }
foreach ($side in 'base', 'cand') {
    robocopy $Src (Join-Path $Dest "$side\src") *.spi *.spiproj Cargo.toml /S /XD target .cache /NFL /NDL /NJH /NJS /NP /MT:8 | Out-Null
    robocopy ($side -eq 'base' ? $LibBase : $LibCandidate) (Join-Path $Dest "$side\src\spiral") *.spi *.spiproj /S /XD target _build .cache node_modules /NFL /NDL /NJH /NJS /NP /MT:8 | Out-Null
}
foreach ($file in Get-ChildItem $Overlay -Recurse -File -Include *.spi, *.spiproj, Cargo.toml) {
    $target = Join-Path $Dest "cand\src\$($file.FullName.Substring($Overlay.Length + 1))"
    New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null
    Copy-Item $file.FullName $target -Force
}
foreach ($fresh in Get-ChildItem (Join-Path $Dest 'cand\src') -Directory | Where-Object { -not (Test-Path (Join-Path $Dest "base\src\$($_.Name)")) }) {
    New-Item -ItemType Directory -Force (Join-Path $Dest "base\src\$($fresh.Name)") | Out-Null
    Copy-Item (Join-Path $fresh.FullName '*') (Join-Path $Dest "base\src\$($fresh.Name)") -Force
}
$refs = @{}
foreach ($proj in Get-ChildItem (Join-Path $Dest 'cand\src') -Recurse -Filter package.spiproj | Where-Object { $_.Directory.Parent.FullName -eq (Join-Path $Dest 'cand\src') }) {
    $block = [regex]::Match([IO.File]::ReadAllText($proj.FullName), '(?s)packages:(.*?)(\n\S|$)').Groups[1].Value
    $refs[$proj.Directory.Name] = @([regex]::Matches($block, '(?m)^\s*\|?\s*([\w'']+)-?\s*$') | ForEach-Object { $_.Groups[1].Value })
}
function Reaches([string] $name, [hashtable] $seen) {
    if ($Changed -contains $name) { return $true }
    if ($seen.ContainsKey($name)) { return $false }
    $seen[$name] = $true
    foreach ($next in @($refs[$name])) { if ($next -and (Reaches $next $seen)) { return $true } }
    $false
}
$crates = @(Get-ChildItem (Join-Path $Dest 'cand\src') -Directory | Where-Object { Test-Path (Join-Path $_.FullName 'Cargo.toml') } | Where-Object { Reaches $_.Name @{} } | ForEach-Object Name)
if ($crates -notcontains $Control) { $crates += $Control }
[IO.File]::WriteAllLines((Join-Path $Dest 'crates.txt'), [string[]]$crates)
"crates reaching the change: $($crates.Count - 1) plus control $Control"
