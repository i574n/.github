param(
    [Parameter(Mandatory)] [string] $LocalPath,
    [Parameter(Mandatory)] [string] $NodePath,
    [Parameter(Mandatory)] [string] $Smoke,
    [string] $Label = (Get-Date -Format 'yyyyMMdd-HHmm')
)
$ErrorActionPreference = 'Stop'
$remote = Join-Path $PSScriptRoot 'remote.ps1'
$item = Get-Item -LiteralPath $LocalPath
$isFolder = $item.PSIsContainer
$work = Join-Path ([IO.Path]::GetTempPath()) "node-ship-$PID"
New-Item -ItemType Directory -Force $work | Out-Null
$zip = Join-Path $work "payload-$Label.zip"
if ($isFolder) { Compress-Archive -Path (Join-Path $item.FullName '*') -DestinationPath $zip } else { Compress-Archive -Path $item.FullName -DestinationPath $zip }
$zipHash = (Get-FileHash $zip).Hash
$localHashes = if ($isFolder) { Get-ChildItem $item.FullName -Recurse -File | Sort-Object FullName | ForEach-Object { '{0}={1}' -f $_.FullName.Substring($item.FullName.Length + 1), (Get-FileHash $_.FullName).Hash } } else { '{0}={1}' -f $item.Name, (Get-FileHash $item.FullName).Hash }
$localDigest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($localHashes -join "`n")))).Substring(0, 16)
"payload {0} ({1}), digest {2}" -f $item.FullName, $(if ($isFolder) { 'folder' } else { 'file' }), $localDigest
$stageZip = "C:/home/remote-stage/node-ship-$Label.zip"
scp -q $zip "laptop:$stageZip"
if ($LASTEXITCODE) { throw "scp failed with exit $LASTEXITCODE" }
Remove-Item $work -Recurse -Force

$script = @"
`$ErrorActionPreference = 'Stop'
`$zip = 'C:\home\remote-stage\node-ship-$Label.zip'
if ((Get-FileHash `$zip).Hash -ne '$zipHash') { 'ABORT: zip hash differs after transfer'; return }
`$staged = 'C:\home\remote-stage\node-ship-$Label'
if (Test-Path `$staged) { Remove-Item `$staged -Recurse -Force }
Expand-Archive `$zip `$staged
`$isFolder = `$$isFolder
`$hashes = if (`$isFolder) { Get-ChildItem `$staged -Recurse -File | Sort-Object FullName | ForEach-Object { '{0}={1}' -f `$_.FullName.Substring(`$staged.Length + 1), (Get-FileHash `$_.FullName).Hash } } else { Get-ChildItem `$staged -File | ForEach-Object { '{0}={1}' -f `$_.Name, (Get-FileHash `$_.FullName).Hash } }
`$digest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes((`$hashes -join "``n")))).Substring(0, 16)
if (`$digest -ne '$localDigest') { "ABORT: staged digest `$digest differs from $localDigest"; return }
`$stagedPath = if (`$isFolder) { `$staged } else { Join-Path `$staged '$($item.Name)' }
`$smokeOut = & { $Smoke } 2>&1 | Out-String
if (`$smokeOut -notmatch 'SMOKE-OK') { "ABORT: smoke did not print SMOKE-OK:``n`$smokeOut"; return }
`$target = [Environment]::ExpandEnvironmentVariables('$NodePath')
`$aside = "`$target.replaced-$Label"
if (Test-Path `$target) {
    try { Rename-Item `$target (Split-Path `$aside -Leaf) -ErrorAction Stop } catch { "ABORT: target in use, nothing changed: `$(`$_.Exception.Message)"; return }
}
New-Item -ItemType Directory -Force (Split-Path `$target) | Out-Null
if (`$isFolder) { Copy-Item `$staged `$target -Recurse } else { Copy-Item `$stagedPath `$target }
`$after = if (`$isFolder) { Get-ChildItem `$target -Recurse -File | Sort-Object FullName | ForEach-Object { '{0}={1}' -f `$_.FullName.Substring(`$target.Length + 1), (Get-FileHash `$_.FullName).Hash } } else { '{0}={1}' -f (Split-Path `$target -Leaf), (Get-FileHash `$target).Hash }
`$afterDigest = [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes((`$after -join "``n")))).Substring(0, 16)
"SHIPPED `$target digest `$afterDigest (previous kept as `$aside)"
"@
$scriptFile = Join-Path ([IO.Path]::GetTempPath()) "node-ship-$Label.ps1"
[IO.File]::WriteAllText($scriptFile, $script)
scp -q $scriptFile "laptop:C:/home/remote-stage/node-ship-$Label.ps1"
if ($LASTEXITCODE) { throw "scp of the ship script failed with exit $LASTEXITCODE" }
Remove-Item $scriptFile
$out = pwsh -NoProfile -File $remote node run "& 'C:\home\remote-stage\node-ship-$Label.ps1'" 2>&1 | Out-String
$out.TrimEnd()
if ($out -notmatch 'SHIPPED') { exit 1 }
