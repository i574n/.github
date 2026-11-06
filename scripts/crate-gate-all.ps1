param([string[]] $Repos = @('kit\src', 'x', 'spiral', 'tibia', 'fiscal'), [int] $Limit = 1000)
$ErrorActionPreference = 'Continue'
$gitRoot = Split-Path (Split-Path $PSScriptRoot)
$kit = Join-Path $gitRoot 'kit\kit.exe'
$env:KIT_LEASE_POLICY = 'renewing'
$failed = 0
foreach ($repo in $Repos) {
    $root = Join-Path $gitRoot $repo
    if (-not (Test-Path $root)) { "skip $repo (no folder)"; continue }
    $out = @(& $kit crate gate $root --enforce --limit $Limit 2>&1 | ForEach-Object { "$_" })
    $code = $LASTEXITCODE
    $out | Where-Object { $_ -match ' OVER ' }
    if ($code -ne 0 -and $out[-1] -match 'no crate with \[package\.metadata\.spiral\]') { "$repo : no Spiral crates (nothing to gate)"; continue }
    "$repo : $($out[-1])"
    if ($code -ne 0) { $failed++ }
}
"crate gate over $($Repos.Count) repos: $(if ($failed) { "$failed FAILED" } else { 'ok' })"
exit $failed
