# remote.ps1 <machine> run|job|status|sync|fetch - drive a remote kit worker node over ssh.
# Machines: ~/.config/remote-machines.json. Transport is ssh (+ Tailscale fallback);
# iggy is parked (unix-only fd hand-off). Internal git on the node is fine (the user
# owns git only on this review box); the bring-back to this box is non-git (fetch).
#
# Offload scope (kit2): only READ-ONLY jobs - compiles, tests, reviews. Pull results
# to scratch/job folders with `fetch`, never back into a repo tree. Jobs that write
# into the live tree (eoie/kit regen+publish, lib land.ps1, matrix accept) stay on
# this box: their outputs race with local edits and the sync digest cannot cover that.
# `sync` proves the node tree matches: per-file sha256 over the changed set, verified
# equal on both sides (Get-TreeDigest), atop `git checkout --detach HEAD` + clean.
param(
    [Parameter(Mandatory, Position = 0)] [string] $Machine,
    [Parameter(Mandatory, Position = 1)] [ValidateSet('run', 'job', 'status', 'sync', 'fetch')] [string] $Verb,
    [Parameter(Position = 2, ValueFromRemainingArguments)] [string[]] $Rest = @(),
    [string] $Config = (Join-Path $HOME '.config/remote-machines.json'),
    [string] $Mirror,
    [switch] $Force
)
$ErrorActionPreference = 'Stop'
$gitRoot = Split-Path (Split-Path $PSScriptRoot)
$machines = Get-Content $Config -Raw | ConvertFrom-Json -AsHashtable
if (-not $machines.ContainsKey($Machine)) { throw "remote.ps1: no machine '$Machine' in $Config (known: $($machines.Keys -join ', '))" }
$target = $machines[$Machine]
$addressCache = Join-Path $HOME ".ssh/$($target.sshAlias)-last-ip.txt"
$script:remoteExit = 0

function Resolve-MachineAddress {
    $byName = try { [Net.Dns]::GetHostAddresses($target.hostName) | Where-Object AddressFamily -eq 'InterNetwork' | Select-Object -First 1 -ExpandProperty IPAddressToString } catch { $null }
    if ($byName) { return $byName }
    if (Test-Path $addressCache) { return (Get-Content $addressCache -TotalCount 1).Trim() }
    $tailscale = 'C:\Program Files\Tailscale\tailscale.exe'
    if (Test-Path $tailscale) { return (& $tailscale ip -4 $target.tailscaleName 2>$null | Select-Object -First 1) }
    throw "remote.ps1: cannot resolve $($target.hostName), no cached address, no tailscale"
}

function ConvertTo-PwshLiteral([string] $text) { "'" + $text.Replace("'", "''") + "'" }

function Invoke-Remote([string] $command) {
    $address = Resolve-MachineAddress
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("`$ProgressPreference = 'SilentlyContinue'`n" + $command))
    ssh -o "HostName=$address" $target.sshAlias "$($target.shell) -NoProfile -NonInteractive -EncodedCommand $encoded"
    $script:remoteExit = $LASTEXITCODE
    if ($script:remoteExit -ne 255) { Set-Content $addressCache $address }
}

function Copy-ToRemote([string] $localPath, [string] $remotePath) {
    $address = Resolve-MachineAddress
    scp -q -o "HostName=$address" $localPath "$($target.sshAlias):$($remotePath.Replace('\', '/'))"
    if ($LASTEXITCODE) { throw "remote.ps1: scp to $remotePath failed (exit $LASTEXITCODE)" }
}

function Get-TreeDigest([string] $root, [string[]] $paths) {
    $lines = foreach ($path in ($paths | Sort-Object -Culture 'en-US')) { "$path $((Get-FileHash -LiteralPath (Join-Path $root $path) -Algorithm SHA256).Hash)" }
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Substring(0, 16)
}

$remoteDigest = @'
function Get-TreeDigest([string] $root, [string[]] $paths) {
    $lines = foreach ($path in ($paths | Sort-Object -Culture 'en-US')) { "$path $((Get-FileHash -LiteralPath (Join-Path $root $path) -Algorithm SHA256).Hash)" }
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Substring(0, 16)
}
'@

switch ($Verb) {
    'run' { Invoke-Remote ($Rest -join ' '); exit $script:remoteExit }
    'job' { Invoke-Remote ("& $(ConvertTo-PwshLiteral $target.eoie) job " + (($Rest | ForEach-Object { ConvertTo-PwshLiteral $_ }) -join ' ')); exit $script:remoteExit }
    'status' {
        Invoke-Remote (@(
            "`$os = Get-CimInstance Win32_OperatingSystem; `$cpu = (Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average"
            "'{0} cpu {1}% free-mem {2:N1} GB free-disk {3:N1} GB' -f `$env:COMPUTERNAME, `$cpu, (`$os.FreePhysicalMemory / 1MB), ((Get-PSDrive C).Free / 1GB)"
            "& $(ConvertTo-PwshLiteral $target.eoie) job list 12 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }"
            "foreach (`$repo in Get-ChildItem $(ConvertTo-PwshLiteral $target.gitRoot) -Directory) { if (Test-Path (Join-Path `$repo.FullName '.git')) { '{0,-14} {1}' -f `$repo.Name, (git -C `$repo.FullName rev-parse --short=8 HEAD) } }"
        ) -join "`n")
        exit $script:remoteExit
    }
    'fetch' {
        if ($Rest.Count -ne 2) { throw 'remote.ps1 <machine> fetch <remote path> <local path>' }
        $address = Resolve-MachineAddress
        scp -q -r -o "HostName=$address" "$($target.sshAlias):$($Rest[0].Replace('\', '/'))" $Rest[1]
        exit $LASTEXITCODE
    }
    'sync' {
        if ($Rest.Count -ne 1) { throw 'remote.ps1 <machine> sync <repo folder under the git root>' }
        $repo = $Rest[0]
        $localRepo = Join-Path $gitRoot $repo
        $remoteSource = ConvertTo-PwshLiteral (Join-Path $target.gitRoot $repo)
        $mirrorRoot = if ($target.mirrorBase) { $target.mirrorBase } else { 'C:\home\remote' }
        $remoteRepo = if ($Mirror) { ConvertTo-PwshLiteral (Join-Path (Join-Path $mirrorRoot $Mirror) $repo) } else { $remoteSource }
        $stage = ConvertTo-PwshLiteral $target.stage
        $head = git -C $localRepo rev-parse HEAD
        $tracked = @((git -C $localRepo diff --name-only -z HEAD) -split "`0")
        $untracked = @((git -C $localRepo ls-files -o --exclude-standard -z) -split "`0")
        $changed = @($tracked + $untracked | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $localRepo $_) -PathType Leaf) } | Sort-Object -Unique)
        $deleted = @($tracked | Where-Object { $_ -and -not (Test-Path -LiteralPath (Join-Path $localRepo $_)) } | Sort-Object -Unique)
        $digest = if ($changed) { Get-TreeDigest $localRepo $changed } else { 'none' }
        "sync $repo -> ${Machine}: head $($head.Substring(0, 8)), $($changed.Count) changed, $($deleted.Count) deleted, digest $digest"
        if (-not $Force) {
            Invoke-Remote "if (& $(ConvertTo-PwshLiteral $target.eoie) job list 50 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }) { exit 4 }"
            if ($script:remoteExit -eq 4) { 'remote.ps1: jobs are running or queued there; pass -Force to sync under them'; exit 4 }
        }
        Invoke-Remote "New-Item -ItemType Directory -Force $stage | Out-Null; git -C $remoteSource cat-file -e '$head^{commit}' 2>`$null; if (`$LASTEXITCODE) { git -C $remoteSource fetch -q origin 2>`$null; git -C $remoteSource cat-file -e '$head^{commit}' 2>`$null }; exit `$LASTEXITCODE"
        if ($script:remoteExit -ne 0) {
            $remoteHead = "$(Invoke-Remote "git -C $remoteSource rev-parse HEAD")".Trim()
            $sharesRemoteHead = $false
            if ($remoteHead -match '^[0-9a-f]{40}$') { git -C $localRepo cat-file -e "$remoteHead^{commit}" 2>$null; $sharesRemoteHead = $LASTEXITCODE -eq 0 }
            $range = if ($sharesRemoteHead) { @('HEAD', '--not', $remoteHead) } else { @('HEAD') }
            $bundle = Join-Path ([IO.Path]::GetTempPath()) "remote-sync-$repo.bundle"
            git -C $localRepo bundle create $bundle @range 2>$null
            if ($LASTEXITCODE) { throw "remote.ps1: git bundle failed for $repo" }
            Copy-ToRemote $bundle (Join-Path $target.stage "$repo.bundle")
            Remove-Item $bundle
            Invoke-Remote "`$bundle = Join-Path $stage '$repo.bundle'; git -C $remoteSource fetch -q `$bundle HEAD; `$code = `$LASTEXITCODE; Remove-Item `$bundle; exit `$code"
            if ($script:remoteExit -ne 0) { throw "remote.ps1: the remote could not fetch the bundle of $repo" }
        }
        if ($Mirror) {
            Invoke-Remote "if (-not (Test-Path $remoteRepo)) { New-Item -ItemType Directory -Force (Split-Path $remoteRepo) | Out-Null; git -C $remoteSource worktree add --detach --force $remoteRepo $head 2>&1 | Out-Null; if (`$LASTEXITCODE) { exit 8 } }"
            if ($script:remoteExit -ne 0) { throw "remote.ps1: could not create worktree mirror $Mirror for $repo" }
        }
        $work = Join-Path ([IO.Path]::GetTempPath()) "remote-sync-$repo"
        New-Item -ItemType Directory -Force $work | Out-Null
        [IO.File]::WriteAllText((Join-Path $work 'changed.txt'), (($changed + '') -join "`n"))
        [IO.File]::WriteAllText((Join-Path $work 'deleted.txt'), (($deleted + '') -join "`n"))
        Copy-ToRemote (Join-Path $work 'changed.txt') (Join-Path $target.stage "$repo.changed.txt")
        Copy-ToRemote (Join-Path $work 'deleted.txt') (Join-Path $target.stage "$repo.deleted.txt")
        if ($changed) {
            $archive = Join-Path $work 'tree.tar'
            tar -cf $archive -C $localRepo -T (Join-Path $work 'changed.txt')
            if ($LASTEXITCODE) { throw "remote.ps1: tar failed for $repo" }
            Copy-ToRemote $archive (Join-Path $target.stage "$repo.tar")
        }
        Remove-Item -Recurse -Force $work
        Invoke-Remote (@(
            $remoteDigest
            "`$repo = $remoteRepo; `$stage = $stage"
            "git -C `$repo -c advice.detachedHead=false checkout -q --detach --force $head; if (`$LASTEXITCODE) { exit 5 }"
            "git -C `$repo clean -fdq"
            "`$changed = @(Get-Content (Join-Path `$stage '$repo.changed.txt') | Where-Object { `$_ })"
            "foreach (`$path in (Get-Content (Join-Path `$stage '$repo.deleted.txt') | Where-Object { `$_ })) { Remove-Item -LiteralPath (Join-Path `$repo `$path) -Force -ErrorAction SilentlyContinue }"
            "`$archive = Join-Path `$stage '$repo.tar'"
            "if (Test-Path `$archive) { tar -xf `$archive -C `$repo; if (`$LASTEXITCODE) { exit 6 }; Remove-Item `$archive }"
            "`$digest = if (`$changed) { Get-TreeDigest `$repo `$changed } else { 'none' }"
            "'remote head {0}, digest {1}' -f (git -C `$repo rev-parse --short=8 HEAD), `$digest"
            "if (`$digest -ne '$digest') { exit 7 }"
        ) -join "`n")
        if ($script:remoteExit -eq 0) { "REMOTE-SYNC-OK $repo on $Machine matches this tree (head $($head.Substring(0, 8)), digest $digest)" }
        exit $script:remoteExit
    }
}
