param(
    $fast,
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"


$url = git ls-remote --get-url
$owner = ($url -split '/' | Select-Object -Last 2 | Select-Object -First 1) -replace '\.git$', '' ?? $env:GITHUB_REPOSITORY_OWNER
Write-Output "init.ps1 / url: $url / owner: $owner"

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://github.com/$owner/spiral.git # --branch gh-pages
Set-Location spiral
git pull
Set-Location $ScriptDir

pwsh ../../spiral/scripts/init.ps1


. ../../polyglot/scripts/core.ps1

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://github.com/$owner/dice.git # --branch gh-pages
Set-Location dice
git pull
Set-Location $ScriptDir

{ pwsh ../../dice/scripts/init.ps1 } | Invoke-Block

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://github.com/$owner/alphabet.git # --branch gh-pages
Set-Location alphabet
git pull
Set-Location $ScriptDir

{ pwsh ../../alphabet/scripts/init.ps1 } | Invoke-Block
