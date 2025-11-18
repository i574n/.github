param(
    $fast,
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"

. ../../polyglot/scripts/core.ps1


{ pwsh ../../spiral/scripts/build.ps1 } | Invoke-Block

{ pwsh ../../polyglot/scripts/build.ps1 } | Invoke-Block

{ pwsh ../../dice/scripts/build.ps1 } | Invoke-Block -OnError Continue

{ pwsh ../deps/alphabet/scripts/build.ps1 } | Invoke-Block -OnError Continue
