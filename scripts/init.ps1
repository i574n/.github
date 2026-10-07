param(
    $fast,
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"


$url = git ls-remote --get-url
$owner = ($url -split '/' | Select-Object -Last 2 | Select-Object -First 1) -replace '\.git$', '' ?? $env:GITHUB_REPOSITORY_OWNER
$domain = ($url -split '/' | Select-Object -Last 3 | Select-Object -First 1) ?? $env:GITHUB_SERVER_URL -replace 'https?://', ''
Write-Output "init.ps1 / url: $url / owner: $owner / domain: $domain"

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://$domain/$owner/spiral.git # --branch gh-pages
Set-Location spiral
git pull
Set-Location $ScriptDir

pwsh ../../spiral/scripts/init.ps1

# polyglot is cloned here too, not only as a side effect of spiral's init (which stopped early once and left no polyglot).
Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
if (!(Test-Path polyglot/.git)) { git clone --recurse-submodules https://$domain/$owner/polyglot.git }
Set-Location $ScriptDir

. ../../polyglot/scripts/core.ps1

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://$domain/$owner/dice.git # --branch gh-pages
Set-Location dice
git pull
Set-Location $ScriptDir

{ pwsh ../../dice/scripts/init.ps1 } | Invoke-Block

Set-Location (New-Item -ItemType Directory -Path "../.." -Force)
git clone --recurse-submodules https://$domain/$owner/alphabet.git # --branch gh-pages
Set-Location alphabet
git pull
Set-Location $ScriptDir

{ pwsh ../../alphabet/scripts/init.ps1 } | Invoke-Block
