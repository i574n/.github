param(
    [string] $MirrorName,
    [string] $Label,
    [string] $Command,
    [string[]] $Repos = @('spiral', 'x'),
    [string] $CompilerDll,
    [int] $TimeoutMs = 14400000,
    [switch] $Remove,
    [string] $Summary,
    [string] $Pattern = '^x verify /|^x e2e / (FAILED|\d+ requests|accepted)|DIFFERENT|^Result: |OVER \d+|GLEAM-FAILED|RUST-FAILED|^spiral-tests total|"failed":[1-9]|^XSIM .*result=fail|notebook \S+ FAILED|notebooks (failed|passed)',
    [int] $ErrorLines = 12,
    [string[]] $LandRepos = @(),
    [switch] $WriteManifest,
    [string] $Land,
    [string] $From,
    [string] $Repo,
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
$remote = Join-Path $PSScriptRoot 'remote.ps1'
$gitRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
function Get-ManifestPath([string] $Name) { Join-Path $env:LOCALAPPDATA "node-proof\$Name.json" }
function Get-RepoHashes([string] $RepoRoot) {
    $hashes = @{}
    foreach ($rel in git -C $RepoRoot ls-files -co --exclude-standard) {
        $path = Join-Path $RepoRoot $rel
        if (Test-Path -LiteralPath $path -PathType Leaf) { $hashes[$rel.Replace('\', '/')] = (Get-FileHash -LiteralPath $path).Hash }
    }
    $hashes
}
function Write-SyncManifest([string] $Name, [string[]] $Names) {
    $manifest = @{ label = $Name; at = (Get-Date).ToString('o'); repos = @{} }
    foreach ($r in $Names) { $manifest.repos[$r] = Get-RepoHashes (Join-Path $gitRoot $r) }
    $path = Get-ManifestPath $Name
    New-Item -ItemType Directory -Force (Split-Path $path) | Out-Null
    $manifest | ConvertTo-Json -Depth 4 -Compress | Set-Content -LiteralPath $path
    "node-proof / sync manifest $path ($(($Names | ForEach-Object { "$_ $($manifest.repos[$_].Count) files" }) -join ', '))"
}
if ($WriteManifest) {
    if (!$Label -or !$LandRepos) { throw 'node-proof / -WriteManifest needs -Label and -LandRepos' }
    Write-SyncManifest $Label $LandRepos
    return
}
if ($Land) {
    if (!$From -or !$Repo) { throw 'node-proof / -Land <label> needs -From <remote output folder> and -Repo <repo whose paths it mirrors>' }
    $manifestPath = Get-ManifestPath $Land
    if (!(Test-Path $manifestPath)) { throw "node-proof / no sync manifest for $Land; start the job with -LandRepos $Repo" }
    $base = (Get-Content -Raw $manifestPath | ConvertFrom-Json -AsHashtable).repos[$Repo]
    if ($null -eq $base) { throw "node-proof / the $Land manifest has no $Repo; start the job with -LandRepos $Repo" }
    $fetchRoot = Join-Path ([IO.Path]::GetTempPath()) "node-proof-land\$Land"
    if (Test-Path $fetchRoot) { Remove-Item -Recurse -Force $fetchRoot }
    New-Item -ItemType Directory -Force $fetchRoot | Out-Null
    pwsh -NoProfile -File $remote node fetch $From $fetchRoot
    if ($LASTEXITCODE) { throw "node-proof / fetch of $From failed" }
    $fetched = Join-Path $fetchRoot (Split-Path $From -Leaf)
    $repoRoot = Join-Path $gitRoot $Repo
    $landed, $conflicts, $same = 0, 0, 0
    foreach ($file in Get-ChildItem $fetched -Recurse -File) {
        $rel = [IO.Path]::GetRelativePath($fetched, $file.FullName).Replace('\', '/')
        $target = Join-Path $repoRoot $rel
        $new = (Get-FileHash -LiteralPath $file.FullName).Hash
        $tree = if (Test-Path -LiteralPath $target -PathType Leaf) { (Get-FileHash -LiteralPath $target).Hash } else { $null }
        if ($tree -eq $new) { $same++; continue }
        if ($tree -eq $base[$rel]) {
            $landed++
            "node-proof / land $rel"
            if ($Apply) { New-Item -ItemType Directory -Force (Split-Path $target) | Out-Null; Copy-Item -LiteralPath $file.FullName $target }
        } else {
            $conflicts++
            "node-proof / CONFLICT $rel (changed here since the sync and by the run; review $($file.FullName))"
        }
    }
    "node-proof / $Land into ${Repo}: $landed to land$(if ($Apply) { ' (applied)' } else { ' (report only; -Apply copies them)' }), $conflicts conflict(s), $same unchanged"
    return
}
if ($Summary) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Pattern))
    $read = "`$j = Join-Path `$env:LOCALAPPDATA 'eoie\jobs\$Summary'; if (!(Test-Path `$j)) { 'node-proof / no job $Summary'; exit 1 }; " +
        "`$pattern = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('$encoded')); " +
        "`$exit = (Get-Content (Join-Path `$j 'exit') -ErrorAction Ignore) -split '\t'; " +
        "`$seconds = if (`$exit.Count -ge 2) { ""`$([int](([long]`$exit[1] - [long](Get-Content (Join-Path `$j 'started_ms'))) / 1000)) s"" } else { 'still running' }; " +
        """node-proof / $Summary / exit `$(if (`$exit) { `$exit[0] } else { 'none yet' }) / `$seconds""; " +
        "Get-Content (Join-Path `$j 'stdout.log') | Select-String -Pattern `$pattern | ForEach-Object { `$_.Line.Substring(0, [Math]::Min(220, `$_.Line.Length)) }; " +
        "'node-proof / first error lines:'; Get-Content (Join-Path `$j 'stderr.log') | Select-String -Pattern 'error|Error|Exception|FatalError|ETY-|throw' | Select-Object -First $ErrorLines | ForEach-Object { (`$_.Line -replace '\x1b\[[0-9;]*m', '').Substring(0, [Math]::Min(220, (`$_.Line -replace '\x1b\[[0-9;]*m', '').Length)) }"
    pwsh -NoProfile -File $remote node run $read 2>&1
    return
}
if (!$MirrorName) { throw 'node-proof / -MirrorName is required unless -Summary' }
if ($Remove) {
    $check = "`$live = ((Get-CimInstance Win32_Process | Where-Object CommandLine).CommandLine -join [char]10); `$p = 'C:\home\remote\$MirrorName'; " +
        "if (!(Test-Path `$p)) { 'node-proof / no mirror $MirrorName' } elseif (`$live -match ([regex]::Escape(`$p) + '(\\|""|\s|$)')) { 'node-proof / $MirrorName is in use, kept' } " +
        "else { `$links = @(Get-ChildItem `$p -Recurse -Directory -Force -Attributes ReparsePoint -ErrorAction Ignore | Sort-Object { `$_.FullName.Length } -Descending); " +
        "foreach (`$l in `$links) { [IO.Directory]::Delete(`$l.FullName, `$false) }; Remove-Item -Recurse -Force `$p; " +
        """node-proof / removed $MirrorName (`$(`$links.Count) link(s) unlinked first, targets untouched), free GB `$([math]::Round((Get-PSDrive C).Free / 1GB, 1))"" }"
    pwsh -NoProfile -File $remote node run $check 2>&1 | Select-Object -Last 1
    return
}
if (!$Label -or !$Command) { throw 'node-proof / -Label and -Command are required unless -Remove' }
$machine = (Get-Content ~/.config/remote-machines.json -Raw | ConvertFrom-Json -AsHashtable)['laptop']
$alias = $machine.sshAlias ? $machine.sshAlias : $machine.alias
$address = try { ([Net.Dns]::GetHostAddresses($machine.hostName) | Where-Object AddressFamily -eq 'InterNetwork' | Select-Object -First 1).IPAddressToString } catch { $null }
if (!$address) { $address = (Get-Content "~/.ssh/$alias-last-ip.txt" -Raw).Trim() }
$mirrorRoot = "C:\home\remote\$MirrorName"
$stage = "C:\home\remote-stage\$MirrorName"
$jobScript = Join-Path ([IO.Path]::GetTempPath()) "node-proof-$MirrorName.ps1"
$pin = $CompilerDll ? "`$env:SPIRAL_COMPILER_DLL = '$CompilerDll'`n" : ''
$body = "`$ErrorActionPreference = 'Continue'`n$pin`$env:PATH = `"`$HOME\scoop\apps\erlang\current\bin;`$HOME\scoop\apps\elixir\current\bin;`$env:PATH`"`n$($Command.Replace('{mirror}', $mirrorRoot))`nexit `$LASTEXITCODE`n"
[IO.File]::WriteAllText($jobScript, $body)
$tokens = $null; $errors = $null
[System.Management.Automation.Language.Parser]::ParseFile($jobScript, [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count) { throw "node-proof / the job script does not parse: $($errors[0].Message)" }
pwsh -NoProfile -File $remote node run "New-Item -ItemType Directory -Force '$stage' | Out-Null" | Out-Null
scp -q -o "HostName=$address" $jobScript "${alias}:$($stage.Replace('\', '/'))/run.ps1"
if ($LASTEXITCODE) { throw "node-proof / scp of the job script failed" }
$want = (Get-FileHash $jobScript).Hash.Substring(0, 12)
$have = pwsh -NoProfile -File $remote node run "(Get-FileHash '$stage\run.ps1').Hash.Substring(0, 12)" 2>&1 | Select-Object -Last 1
if ("$have" -ne $want) { throw "node-proof / staged job script hash $have, expected $want" }
foreach ($repo in $Repos) {
    $sync = @(pwsh -NoProfile -File $remote node sync $repo -Mirror $MirrorName 2>&1 | ForEach-Object { "$_" })
    $ok = @($sync -match "^REMOTE-SYNC-OK $repo ")
    if ($ok.Count -ne 1) { throw "node-proof / sync of $repo to $MirrorName did not print REMOTE-SYNC-OK: $($sync | Select-Object -Last 1)" }
    $ok[0]
}
if ($LandRepos) { Write-SyncManifest $Label $LandRepos }
pwsh -NoProfile -File $remote node job start $Label $TimeoutMs pwsh -NoProfile -File "$stage\run.ps1" 2>&1 | Select-Object -Last 1
