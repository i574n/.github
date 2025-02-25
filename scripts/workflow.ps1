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

{ . ../../spiral/workspace/target/release/spiral$(_exe) dib --path $ScriptDir/workflow.dib } | Invoke-Block -Location ../../polyglot/scripts
