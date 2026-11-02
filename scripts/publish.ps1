param(
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"
. ../../polyglot/scripts/core.ps1


{ pwsh ../../spiral/scripts/publish-tree.ps1 -Root .. -Include 'backlog.png,backlog.svg,backlog.mmd,backlog.graphml,spiral-compiler.png,spiral-compiler.svg,spiral-compiler.mmd,spiral-compiler.graphml' } | Invoke-Block
