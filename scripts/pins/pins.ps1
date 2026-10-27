param(
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"
. ../../../spiral/scripts/core.ps1
. ../../../spiral/lib/spiral/lib.ps1

$orgRepos = 'alphabet', 'blog', 'dice', 'dir-tree-html', 'eoie', 'fiscal', 'i574n.github', 'polyglot', 'spiral', 'tibia', 'x'

New-Item -ItemType Directory -Force -Path dist | Out-Null
$entries = 'pins_check', 'workflows'
foreach ($entry in $entries) {
    if (!(BuildSpiral "$entry.spi" "dist/$entry.rs" "i574n.github/$entry")) {
        throw "RUST-FAILED i574n.github/$entry / compile"
    }
    { rustc +nightly-2026-10-18 -O "dist/$entry.rs" -o "dist/$entry$(_exe)" } | Invoke-Block
}

$workflows = @($orgRepos | ForEach-Object { Get-ChildItem (Join-Path ../../.. $_ .github/workflows) -Filter *.yml -ErrorAction Ignore } | ForEach-Object FullName)
if (!$workflows.Count) { throw "pins: no org workflow found next to i574n.github" }
{ & "dist/pins_check$(_exe)" @workflows } | Invoke-Block
foreach ($entry in $entries | Select-Object -Skip 1) {
    { & "dist/$entry$(_exe)" (Resolve-Path ../../..).Path } | Invoke-Block
}
