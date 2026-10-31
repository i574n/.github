param(
    [Parameter(Mandatory)] [string] $Label,
    [string] $MirrorName = 'xqueue1',
    [string] $Core,
    [int] $TimeoutMs = 28800000,
    [ValidateSet('all', 'client', 'sim')] [string] $Part = 'all',
    [switch] $NoAccept
)
$ErrorActionPreference = 'Stop'
$machine = (Get-Content ~/.config/remote-machines.json -Raw | ConvertFrom-Json -AsHashtable)['laptop']
$nodeLocal = "$(pwsh -NoProfile -File (Join-Path $PSScriptRoot 'remote.ps1') node run '$env:LOCALAPPDATA' 2>&1 | Select-Object -Last 1)".Trim()
if ($nodeLocal -notmatch '^[A-Za-z]:\\') { throw "x-node-verify / could not read the node's LOCALAPPDATA, got: $nodeLocal" }
$dll = Join-Path $nodeLocal 'spiral-bin\bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompiler.dll'
$out = Join-Path $machine.stage (($Label -replace '^x-verify', 'xv') + '-out')
$command = @'
$spiralBin = Join-Path $env:LOCALAPPDATA 'spiral-bin'
$dotnet = Join-Path $spiralBin 'toolchains\dotnet\dotnet.exe'
$dll = Join-Path $spiralBin 'bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompiler.dll'
foreach ($tree in '{mirror}\x\apps\client', '{mirror}\x\apps\sim\spi') {
    $found = @(& $dotnet $dll --x-compat $tree 2>&1 | ForEach-Object { "$_" })
    $code = $LASTEXITCODE
    'x-compat ' + $tree + ' exit ' + $code
    $found | Select-Object -Last 4
    if ($code -or -not ($found -match '^x-compat report: 0 finding\(s\) in [1-9]\d* file\(s\)$')) { 'x-compat RED or unattested, verify not started'; exit 1 }
}
pwsh -NoProfile -File {mirror}\x\scripts\verify.ps1 -Part PART ACCEPT -Out OUTDIR
$verified = $LASTEXITCODE
foreach ($d in '{mirror}\spiral\target', '{mirror}\x\target') {
    if ((Test-Path $d) -and -not @(Get-ChildItem $d -Recurse -Directory -Force -ErrorAction SilentlyContinue | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint }).Count) { Remove-Item -LiteralPath $d -Recurse -Force -ErrorAction SilentlyContinue; "freed build output $d" }
}
exit $verified
'@.Replace('OUTDIR', $out).Replace('PART', $Part).Replace('ACCEPT', $(if ($NoAccept) { '' } else { '-Accept' }))
$coreCheck = if ($Core) { @('-RequireCore', $Core) } else { @() }
foreach ($try in 1..4) {
    $o = @(pwsh -NoProfile -File (Join-Path $PSScriptRoot 'node-proof.ps1') -MirrorName $MirrorName -Label $Label @coreCheck -CompilerDll $dll -TimeoutMs $TimeoutMs -LandRepos x,spiral -Command $command 2>&1 | ForEach-Object { "$_" })
    $o | Where-Object { $_ -match 'REMOTE-SYNC-OK|manifest|job start' }
    if (($o -join ' ') -match 'job start ok') { break }
    "try $try : $($o | Select-Object -Last 1)"
}
"x-node-verify / outputs land from $out with: node-proof.ps1 -Land $Label -From '$out' -Repo x [-Apply]"
