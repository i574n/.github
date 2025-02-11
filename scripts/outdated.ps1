param(
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"

. ../../polyglot/scripts/core.ps1


{ pwsh ../../spiral/scripts/outdated.ps1 } | Invoke-Block

{ pwsh ../../polyglot/scripts/outdated.ps1 } | Invoke-Block

{ pwsh ../../dice/scripts/outdated.ps1 } | Invoke-Block
