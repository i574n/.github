param(
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"
. ../../polyglot/scripts/core.ps1


{ pwsh ../../spiral/scripts/publish-tree.ps1 -Root .. } | Invoke-Block
