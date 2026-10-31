$script = 'C:\home\git\i574n.github\scripts\scratch-archive.ps1'
$base = Join-Path $PSScriptRoot "sa-guard-$PID"
$store = Join-Path $base 'store'
$root = Join-Path $base 'root'
New-Item -ItemType Directory -Force (Join-Path $root 'a'), (Join-Path $root 'b'), $store | Out-Null
Set-Content (Join-Path $root 'a\one.txt') 'one'
Set-Content (Join-Path $root 'b\two.txt') 'two'
Set-Content (Join-Path $base 'outside.txt') 'outside'
function Run([string] $label, [string[]] $arguments, [scriptblock] $env = {}) {
    $out = pwsh -NoProfile -Command "& { $env } ; & '$script' $($arguments -join ' ')" 2>&1 | Out-String
    "[$label] exit=$LASTEXITCODE $(($out -split "`n" | Where-Object { $_ -match 'refuse|archive|removed|dry|restored' } | Select-Object -First 2) -join ' | ')"
}
Run 'profile root refused' @("-Root '$env:USERPROFILE'", "-Store '$store'")
Run 'temp root refused, TEMP stripped' @("-Root ([IO.Path]::GetTempPath())", "-Store '$store'") { Remove-Item Env:TEMP, Env:TMP }
Run 'drive root refused' @("-Root 'C:\'", "-Store '$store'")
Set-Content (Join-Path $base 'list.txt') '..'
Run 'dot-dot name refused at delete' @("-Root '$root'", "-ListFile '$(Join-Path $base 'list.txt')'", "-Store '$store'", '-Apply')
"outside.txt still there: $(Test-Path (Join-Path $base 'outside.txt'))"
Run 'normal dry run' @("-Root '$root'", "-Store '$store'")
"after dry: a=$(Test-Path (Join-Path $root 'a')) b=$(Test-Path (Join-Path $root 'b'))"
Run 'normal apply' @("-Root '$root'", "-Store '$store'", '-Apply')
"after apply: a=$(Test-Path (Join-Path $root 'a')) b=$(Test-Path (Join-Path $root 'b'))"
$manifest = Get-ChildItem $store -Recurse -Filter manifest.tsv | Sort-Object LastWriteTime | Select-Object -Last 1
$restoreTo = Join-Path $base 'restored'
Run 'restore' @("-Root '$root'", "-Store '$store'", "-Restore '$($manifest.FullName)'", "-RestoreTo '$restoreTo'")
"restored one.txt equal: $((Get-Content (Join-Path $restoreTo 'a\one.txt') -Raw -ErrorAction SilentlyContinue).Trim() -eq 'one')"
Remove-Item -Recurse -Force $base
