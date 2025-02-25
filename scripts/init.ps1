param(
    $fast,
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"


Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://github.com/i574n/spiral.git # --branch gh-pages
Set-Location spiral
git pull
Set-Location $ScriptDir

pwsh ../../spiral/scripts/init.ps1


. ../../polyglot/scripts/core.ps1

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://github.com/i574n/dice.git # --branch gh-pages
Set-Location dice
git pull
Set-Location $ScriptDir

{ pwsh ../../dice/scripts/init.ps1 } | Invoke-Block
