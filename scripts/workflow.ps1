param(
    $fast,
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"


if (!$fast) {
    pwsh init.ps1
}

. ../../polyglot/scripts/core.ps1

$ProgressPreference = "SilentlyContinue"
{ pwsh pins/pins.ps1 } | Invoke-Block
{ pwsh build.ps1 } | Invoke-Block
{ pwsh outdated.ps1 } | Invoke-Block
{ pwsh publish.ps1 } | Invoke-Block
