param(
    [string] $Root = 'C:\home\git',
    [string[]] $Words = @('harness'),
    [string[]] $SkipRepos = @('zed', 'wasmer', 'rustix', 'nearcore', 'leptos', 'The-Spiral-Language', 'forgottenserver', 'vault', 'Trading-Journal', 'azeroth', 'singularity'),
    [string[]] $ExcludePaths = @(':!**/deps/**', ':!**/target/**', ':!**/_build/**', ':!**/node_modules/**', ':!docs/backlog/**', ':!scripts/banned-words-scan.ps1'),
    [string[]] $AllowLine = @('^\s*harness\s*=', '["`]harness\s*=\s*false["`]'),
    [switch] $FailOnHit
)
$ErrorActionPreference = 'Stop'
$pattern = '(?i)\b(' + (($Words | ForEach-Object { [regex]::Escape($_) }) -join '|') + ')'

function Find-Hits([string] $Repo) {
    $lines = @(git -C $Repo grep -P -n -I --untracked --no-color -e $pattern -- . @ExcludePaths 2>$null)
    foreach ($l in $lines) {
        $text = ($l -split ':', 3)[2]
        if ($AllowLine | Where-Object { $text -match $_ }) { continue }
        [pscustomobject]@{ Repo = Split-Path $Repo -Leaf; Hit = $l }
    }
}

$control = Join-Path ([IO.Path]::GetTempPath()) "banned-words-control-$PID"
New-Item -ItemType Directory -Force $control | Out-Null
try {
    git -C $control init -q 2>$null
    Set-Content -LiteralPath (Join-Path $control 'probe.txt') -Value "a $($Words[0]) line"
    Set-Content -LiteralPath (Join-Path $control 'Cargo.toml') -Value 'harness = false'
    $seen = @(Find-Hits $control).Count
} finally {
    Remove-Item -LiteralPath $control -Recurse -Force
}
if ($seen -ne 1) { throw "positive control saw $seen hits, expected 1: the scan cannot be trusted (git grep -P missing, or the allow rules are wrong)" }
"positive control: 1 hit as expected (the probe line found, the Cargo key allowed)"

$repos = @(Get-ChildItem -LiteralPath $Root -Directory | Where-Object { -not $_.LinkType -and (Test-Path (Join-Path $_.FullName '.git')) -and $_.Name -notin $SkipRepos })
$hits = @(foreach ($r in $repos) { Find-Hits $r.FullName })
$hits | Group-Object Repo | Sort-Object Count -Descending | ForEach-Object {
    "== $($_.Name): $($_.Count)"
    $_.Group | ForEach-Object { "   $($_.Hit.Substring(0, [Math]::Min(200, $_.Hit.Length)))" }
}
"scanned $($repos.Count) repos (skipped $($SkipRepos.Count) upstream or user-prose repos) for $($Words -join ', '): $($hits.Count) hit(s)"
if ($FailOnHit -and $hits.Count) { exit 1 }
