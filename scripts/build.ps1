param(
    $fast,
    $ScriptDir = $PSScriptRoot
)
Set-Location $ScriptDir
$ErrorActionPreference = "Stop"

. ../../polyglot/scripts/core.ps1


{ pwsh ../../spiral/scripts/build.ps1 } | Invoke-Block

if ($env:CI) {
    Remove-Item ../../spiral/workspace/target/release/deps -Recurse -Force -ErrorAction Ignore
    Remove-Item ../../spiral/workspace/target/release/build -Recurse -Force -ErrorAction Ignore
}

{ pwsh ../../polyglot/scripts/build.ps1 } | Invoke-Block

if ($env:CI) {
    Remove-Item ../../polyglot/workspace/target/release/build -Recurse -Force -ErrorAction Ignore
    Remove-Item ../../polyglot/workspace/target/release/deps -Recurse -Force -ErrorAction Ignore

    Remove-Item ../../polyglot/target/spiral/spiral/target/debug/deps -Recurse -Force -ErrorAction Ignore
    Remove-Item ../../polyglot/target/spiral/spiral/target/debug/incremental -Recurse -Force -ErrorAction Ignore
}

{ pwsh ../../dice/scripts/build.ps1 } | Invoke-Block -OnError Continue

if ($env:CI) {
    Remove-Item ../../dice/target/release/deps -Recurse -Force -ErrorAction Ignore
    Remove-Item ../../dice/target/release/build -Recurse -Force -ErrorAction Ignore
}
