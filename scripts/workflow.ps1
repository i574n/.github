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

# workflow.livemd's cells are all pwsh: one pwsh session (polyglot core.ps1 Invoke-PwshNotebook), no notebook kernel.
Invoke-PwshNotebook $ScriptDir/workflow.livemd
