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

Invoke-PwshNotebook $ScriptDir/workflow.livemd
