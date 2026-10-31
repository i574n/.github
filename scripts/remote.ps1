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
    [string] $RequireCore,
    [string] $CoreMode = 'single-flight',
    [switch] $Force
)
$ErrorActionPreference = 'Stop'
$gitRoot = Split-Path (Split-Path $PSScriptRoot)
$machines = Get-Content $Config -Raw | ConvertFrom-Json -AsHashtable
if (-not $machines.ContainsKey($Machine)) { throw "remote.ps1: no machine '$Machine' in $Config (known: $($machines.Keys -join ', '))" }
$target = $machines[$Machine]
$addressCache = Join-Path $HOME ".ssh/$($target.sshAlias)-last-ip.txt"
$script:remoteExit = 0

function Test-TcpPort([string] $address, [int] $port = 22) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $client.ConnectAsync($address, $port).Wait(4000) -and $client.Connected } catch { $false } finally { $client.Dispose() }
}

function Resolve-MachineAddress {
    if ($script:machineAddress) { return $script:machineAddress }
    $candidates = [Collections.Generic.List[string]]::new()
    if (Test-Path $addressCache) { $candidates.Add((Get-Content $addressCache -TotalCount 1).Trim()) }
    try { [Net.Dns]::GetHostAddresses($target.hostName) | Where-Object AddressFamily -eq 'InterNetwork' | ForEach-Object { $candidates.Add($_.IPAddressToString) } } catch { }
    foreach ($address in @($target.lanAddresses)) { if ($address) { $candidates.Add($address) } }
    $tailscale = 'C:\Program Files\Tailscale\tailscale.exe'
    if ((Test-Path $tailscale) -and $target.tailscaleName) { & $tailscale ip -4 $target.tailscaleName 2>$null | Select-Object -First 1 | ForEach-Object { $candidates.Add($_.Trim()) } }
    $tried = [Collections.Generic.List[string]]::new()
    foreach ($address in @($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $tcp = Test-TcpPort $address
        if ($tcp) {
            ssh -o "HostName=$address" -o ConnectTimeout=8 $target.sshAlias "exit 0" 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $script:machineAddress = $address; Set-Content $addressCache $address; return $address }
        }
        $tried.Add("$address ($(if ($tcp) { 'port 22 open, login refused: a user or key problem' } else { 'port 22 unreachable: a network problem' }))")
    }
    throw "remote.ps1: no route to $Machine works: $($tried -join '; '). Run i574n.github/scripts/node-reach.ps1 for a per-route, per-key report."
}

function ConvertTo-PwshLiteral([string] $text) { "'" + $text.Replace("'", "''") + "'" }

function Invoke-Remote([string] $command) {
    $address = Resolve-MachineAddress
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("`$ProgressPreference = 'SilentlyContinue'`n" + $command))
    ssh -o "HostName=$address" $target.sshAlias "$($target.shell) -NoProfile -NonInteractive -EncodedCommand $encoded"
    $script:remoteExit = $LASTEXITCODE
    if ($script:remoteExit -ne 255) { Set-Content $addressCache $address }
}

function Assert-NodeCore([string] $expected, [string] $mode) {
    # L6 guard: a verify/compile offload runs only if the node's SpiralCompilerCore.dll
    # matches the expected core (the review box's for the synced HEAD). A mismatch means a
    # stale installed compiler, whose results are invalid even when the source digest matched.
    $newEnv = ConvertTo-PwshLiteral (Join-Path $target.gitRoot 'spiral/apps/compiler/scripts/env.ps1')
    $oldEnv = ConvertTo-PwshLiteral (Join-Path $target.gitRoot 'spiral/apps/compiler/tmp/scripts/env.ps1')
    $out = Invoke-Remote ". (@($newEnv, $oldEnv) | Where-Object { Test-Path `$_ } | Select-Object -First 1); `$dll = Get-SpiralCompilerDll (ConvertTo-SpiralMode '$mode') 'Release'; `$core = Join-Path (Split-Path `$dll) 'SpiralCompilerCore.dll'; if (Test-Path `$core) { (Get-FileHash `$core -Algorithm SHA256).Hash.Substring(0, 16) } else { 'MISSING' }"
    $nodeCore = (("$out" -split "\r?\n") | Where-Object { $_.Trim() } | Select-Object -Last 1).Trim()
    if (-not $nodeCore.ToLower().StartsWith($expected.ToLower())) {
        $fullMode = @{ sf = 'single-flight'; gp = 'gpt5parallel'; hs = 'hopacsota' }[$mode] ?? $mode
        $localIdentityPath = Join-Path ($env:SPIRAL_BIN_CACHE_DIR ?? (Join-Path $env:LOCALAPPDATA 'spiral-bin')) "bin/$fullMode/SpiralCompiler/Release/net11.0/build-identity.json"
        $localIdentity = if (Test-Path $localIdentityPath) { Get-Content $localIdentityPath -Raw | ConvertFrom-Json } else { $null }
        $remoteOut = Invoke-Remote ". (@($newEnv, $oldEnv) | Where-Object { Test-Path `$_ } | Select-Object -First 1); `$file = Join-Path (Split-Path (Get-SpiralCompilerDll (ConvertTo-SpiralMode '$mode') 'Release')) 'build-identity.json'; if (Test-Path `$file) { (Get-Content `$file -Raw).Trim() } else { '{}' }"
        $remoteIdentity = try { (("$remoteOut" -split "\r?\n") | Where-Object { $_.Trim().StartsWith('{') } | Select-Object -Last 1) | ConvertFrom-Json } catch { $null }
        $sameSource = $localIdentity -and $remoteIdentity -and $localIdentity.sourceSha16 -and $localIdentity.sourceSha16 -eq $remoteIdentity.sourceSha16
        if ($sameSource -and $localIdentity.detRoot -ne $remoteIdentity.detRoot) {
            "remote.ps1: node $mode core $nodeCore differs from $expected only by det-root (source $($localIdentity.sourceSha16) on both; box root $($localIdentity.detRoot), node root $($remoteIdentity.detRoot)): VOID by-hash check, offload proceeds"
            return
        }
        throw "remote.ps1: node $mode core $nodeCore does not match required $expected (L6: stale/mismatched compiler, or pass the full 16-hex core $nodeCore; build from the synced core before verifying)"
    }
    "remote.ps1: node $mode core $nodeCore matches required $expected (L6 guard ok)"
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

if ($Verb -ne 'sync' -and ($Mirror -or $Force)) { throw "remote.ps1: -Mirror and -Force belong to sync; on '$Verb' they were taken from the remote command line, which would run without them. Give the remote script a differently named parameter (e.g. -Tree for a mirror path)." }
switch ($Verb) {
    'run' { if ($RequireCore) { Assert-NodeCore $RequireCore $CoreMode } Invoke-Remote ($Rest -join ' '); exit $script:remoteExit }
    'job' { if ($RequireCore) { Assert-NodeCore $RequireCore $CoreMode } Invoke-Remote ("& $(ConvertTo-PwshLiteral $target.eoie) job " + (($Rest | ForEach-Object { ConvertTo-PwshLiteral $_ }) -join ' ')); exit $script:remoteExit }
    'status' {
        Invoke-Remote (@(
            "`$os = Get-CimInstance Win32_OperatingSystem; `$cpu = (Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average"
            "'{0} cpu {1}% free-mem {2:N1} GB free-disk {3:N1} GB' -f `$env:COMPUTERNAME, `$cpu, (`$os.FreePhysicalMemory / 1MB), ((Get-PSDrive C).Free / 1GB)"
            "& $(ConvertTo-PwshLiteral $target.eoie) job list 12 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }"
            "foreach (`$repo in Get-ChildItem $(ConvertTo-PwshLiteral $target.gitRoot) -Directory) { if (Test-Path (Join-Path `$repo.FullName '.git')) { '{0,-14} {1}' -f `$repo.Name, (git -C `$repo.FullName rev-parse --short=8 HEAD) } }"
            "`$runner = [Environment]::GetEnvironmentVariable('SPIRAL_EXE', 'User'); if (`$runner -and (Test-Path `$runner)) { 'node runner   {0} {1:yyyy-MM-dd HH:mm} {2}' -f (Get-FileHash `$runner).Hash.Substring(0, 16), (Get-Item `$runner).LastWriteTime, `$runner } else { 'node runner   (SPIRAL_EXE unset or missing)' }"
            "`$core = Join-Path `$env:LOCALAPPDATA 'spiral-bin\bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompilerCore.dll'; if (Test-Path `$core) { 'node compiler {0} {1:yyyy-MM-dd HH:mm}' -f (Get-FileHash `$core).Hash.Substring(0, 16), (Get-Item `$core).LastWriteTime } else { 'node compiler (missing)' }"
            "`$spiral = Join-Path $(ConvertTo-PwshLiteral $target.gitRoot) 'spiral'; `$lib = @(Get-ChildItem (Join-Path `$spiral 'lib\spiral') -Recurse -File -Include *.spi,*.spir -ErrorAction SilentlyContinue | Sort-Object FullName); 'node checkout {0} lib {1} files {2}' -f (git -C `$spiral rev-parse --short=8 HEAD), `$lib.Count, [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes((`$lib | ForEach-Object { '{0}={1}' -f `$_.FullName.Substring(`$spiral.Length), `$_.Length }) -join '|'))).Substring(0, 16)"
        ) -join "`n")
        $boxRunner = @([Environment]::GetEnvironmentVariable('SPIRAL_EXE', 'User'), (Join-Path $target.gitRoot 'spiral\workspace\target\release\spiral.exe')) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
        $boxCore = Join-Path ($env:SPIRAL_BIN_CACHE_DIR ?? (Join-Path $env:LOCALAPPDATA 'spiral-bin')) 'bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompilerCore.dll'
        if ($boxRunner) { 'box  runner   {0} {1:yyyy-MM-dd HH:mm} {2}' -f (Get-FileHash $boxRunner).Hash.Substring(0, 16), (Get-Item $boxRunner).LastWriteTime, $boxRunner }
        if (Test-Path $boxCore) { 'box  compiler {0} {1:yyyy-MM-dd HH:mm}' -f (Get-FileHash $boxCore).Hash.Substring(0, 16), (Get-Item $boxCore).LastWriteTime }
        $boxSpiral = Join-Path (Split-Path (Split-Path $PSScriptRoot)) 'spiral'
        $boxLib = @(Get-ChildItem (Join-Path $boxSpiral 'lib\spiral') -Recurse -File -Include *.spi, *.spir -ErrorAction SilentlyContinue | Sort-Object FullName)
        'box  checkout {0} lib {1} files {2}' -f (git -C $boxSpiral rev-parse --short=8 HEAD), $boxLib.Count, [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes((($boxLib | ForEach-Object { '{0}={1}' -f $_.FullName.Substring($boxSpiral.Length), $_.Length }) -join '|')))).Substring(0, 16)
        'runner, compiler and checkout lines must match between node and box for comparable gate verdicts (memory node-stale-compiler-dll)'
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
        $syncToken = "$repo-$PID-$([DateTime]::UtcNow.Ticks)"
        $head = git -C $localRepo rev-parse HEAD
        $tracked = @((git -C $localRepo diff --name-only --no-renames -z HEAD) -split "`0")
        $untracked = @((git -C $localRepo ls-files -o --exclude-standard -z) -split "`0")
        $changed = @($tracked + $untracked | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $localRepo $_) -PathType Leaf) } | Sort-Object -Unique)
        $deleted = @((git -C $localRepo diff --name-only --diff-filter=D --no-renames -z HEAD) -split "`0" | Where-Object { $_ } | Sort-Object -Unique)
        $digest = if ($changed) { Get-TreeDigest $localRepo $changed } else { 'none' }
        "sync $repo -> ${Machine}: head $($head.Substring(0, 8)), $($changed.Count) changed, $($deleted.Count) deleted, digest $digest"
        if (-not $Force -and -not $Mirror) {
            Invoke-Remote "if (& $(ConvertTo-PwshLiteral $target.eoie) job list 50 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }) { exit 4 }"
            if ($script:remoteExit -eq 4) { 'remote.ps1: jobs are running or queued there; pass -Force, or use -Mirror <job> for an isolated worktree that is safe under running jobs'; exit 4 }
        }
        Invoke-Remote "New-Item -ItemType Directory -Force $stage | Out-Null; git -C $remoteSource cat-file -e '$head^{commit}' 2>`$null; if (`$LASTEXITCODE) { git -C $remoteSource fetch -q origin 2>`$null; git -C $remoteSource cat-file -e '$head^{commit}' 2>`$null }; exit `$LASTEXITCODE"
        if ($script:remoteExit -ne 0) {
            $remoteHead = "$(Invoke-Remote "git -C $remoteSource rev-parse HEAD")".Trim()
            $sharesRemoteHead = $false
            if ($remoteHead -match '^[0-9a-f]{40}$') { git -C $localRepo cat-file -e "$remoteHead^{commit}" 2>$null; $sharesRemoteHead = $LASTEXITCODE -eq 0 }
            $range = if ($sharesRemoteHead) { @('HEAD', '--not', $remoteHead) } else { @('HEAD') }
            $bundle = Join-Path ([IO.Path]::GetTempPath()) "remote-sync-$syncToken.bundle"
            git -C $localRepo bundle create $bundle @range 2>$null
            if ($LASTEXITCODE) { throw "remote.ps1: git bundle failed for $repo" }
            Copy-ToRemote $bundle (Join-Path $target.stage "$syncToken.bundle")
            Remove-Item $bundle
            Invoke-Remote "`$bundle = Join-Path $stage '$syncToken.bundle'; git -C $remoteSource fetch -q `$bundle HEAD; `$code = `$LASTEXITCODE; Remove-Item `$bundle; exit `$code"
            if ($script:remoteExit -ne 0) { throw "remote.ps1: the remote could not fetch the bundle of $repo" }
        }
        if ($Mirror) {
            Invoke-Remote "if (-not (Test-Path $remoteRepo)) { New-Item -ItemType Directory -Force (Split-Path $remoteRepo) | Out-Null; git -C $remoteSource worktree add --detach --force $remoteRepo $head 2>&1 | Out-Null; if (`$LASTEXITCODE) { exit 8 } }"
            if ($script:remoteExit -ne 0) { throw "remote.ps1: could not create worktree mirror $Mirror for $repo" }
        }
        $work = Join-Path ([IO.Path]::GetTempPath()) "remote-sync-$syncToken"
        New-Item -ItemType Directory -Force $work | Out-Null
        [IO.File]::WriteAllText((Join-Path $work 'changed.txt'), (($changed + '') -join "`n"))
        [IO.File]::WriteAllText((Join-Path $work 'deleted.txt'), (($deleted + '') -join "`n"))
        Copy-ToRemote (Join-Path $work 'changed.txt') (Join-Path $target.stage "$syncToken.changed.txt")
        Copy-ToRemote (Join-Path $work 'deleted.txt') (Join-Path $target.stage "$syncToken.deleted.txt")
        if ($changed) {
            $archive = Join-Path $work 'tree.tar'
            tar -cf $archive -C $localRepo -T (Join-Path $work 'changed.txt')
            if ($LASTEXITCODE) { throw "remote.ps1: tar failed for $repo" }
            Copy-ToRemote $archive (Join-Path $target.stage "$syncToken.tar")
        }
        Remove-Item -Recurse -Force $work
        Invoke-Remote (@(
            $remoteDigest
            "`$repo = $remoteRepo; `$stage = $stage"
            "git -C `$repo -c advice.detachedHead=false checkout -q --detach --force $head; if (`$LASTEXITCODE) { exit 5 }"
            "git -C `$repo clean -fdq"
            "`$changed = @(Get-Content (Join-Path `$stage '$syncToken.changed.txt') | Where-Object { `$_ })"
            "`$deletedLines = @(Get-Content (Join-Path `$stage '$syncToken.deleted.txt') | Where-Object { `$_ })"
            "Remove-Item -LiteralPath (Join-Path `$stage '$syncToken.changed.txt'), (Join-Path `$stage '$syncToken.deleted.txt') -Force -ErrorAction SilentlyContinue"
            "foreach (`$path in `$deletedLines) { Remove-Item -LiteralPath (Join-Path `$repo `$path) -Force -ErrorAction SilentlyContinue }"
            "foreach (`$dir in (`$deletedLines | ForEach-Object { Split-Path (Join-Path `$repo `$_) -Parent } | Sort-Object Length -Descending -Unique)) { `$d = `$dir; while (`$d -and `$d -ne `$repo -and (Test-Path `$d) -and @(Get-ChildItem -LiteralPath `$d -Force -ErrorAction SilentlyContinue).Count -eq 0) { Remove-Item -LiteralPath `$d -Force -ErrorAction SilentlyContinue; `$d = Split-Path `$d -Parent } }"
            "`$archive = Join-Path `$stage '$syncToken.tar'"
            "if (Test-Path `$archive) { tar -xmf `$archive -C `$repo; if (`$LASTEXITCODE) { exit 6 }; Remove-Item `$archive }"
            "`$digest = if (`$changed) { Get-TreeDigest `$repo `$changed } else { 'none' }"
            "'remote head {0}, digest {1}' -f (git -C `$repo rev-parse --short=8 HEAD), `$digest"
            "if (`$digest -ne '$digest') { exit 7 }"
        ) -join "`n")
        $syncExit = $script:remoteExit
        if ($Mirror -and $syncExit -eq 0) {
            # Worktree mirrors share only tracked files; spiral's deps/ holds untracked
            # dependency clones (The-Spiral-Language, polyglot) that |core- resolution needs
            # and `git clean` strips each sync. After the clean, link deps/ read-only from the
            # node's shared source so every concurrent mirror resolves |core- (a link, no copy).
            Invoke-Remote (@(
                "`$sdeps = Join-Path $remoteSource 'deps'; `$mdeps = Join-Path $remoteRepo 'deps'"
                "if ((Test-Path `$sdeps) -and -not @(Get-ChildItem `$mdeps -Force -ErrorAction SilentlyContinue).Count) {"
                "  if (Test-Path `$mdeps) { Remove-Item `$mdeps -Force -Recurse -ErrorAction SilentlyContinue }"
                "  New-Item -ItemType Junction -Path `$mdeps -Target `$sdeps -ErrorAction SilentlyContinue | Out-Null"
                "}"
                "`$expect = if ('$repo' -eq 'spiral') { Join-Path `$mdeps 'The-Spiral-Language' } else { `$mdeps }"
                "`$cellsOk = `$true"
                "if ('$repo' -eq 'spiral') {"
                "  `$scells = Join-Path $remoteSource 'apps\cells\deps'; `$mcells = Join-Path $remoteRepo 'apps\cells\deps'"
                "  if ((Test-Path (Join-Path `$scells 'kino')) -and -not (Test-Path (Join-Path `$mcells 'kino'))) { robocopy `$scells `$mcells /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null }"
                "  `$cellsOk = Test-Path (Join-Path `$mcells 'kino')"
                "}"
                "if (-not `$cellsOk) { 'MIRROR-DEPS-MISSING (apps\cells\deps has no kino: Cells jobs will fail at mix deps.get)' } elseif ((Test-Path `$expect) -and @(Get-ChildItem `$mdeps -Force -ErrorAction SilentlyContinue).Count) { 'MIRROR-DEPS-OK deps -> shared source' + `$(if ('$repo' -eq 'spiral') { ', apps\cells\deps copied' } else { '' }) } elseif (-not (Test-Path `$sdeps)) { 'MIRROR-DEPS-NONE (the shared source has no deps/)' } else { 'MIRROR-DEPS-MISSING (|core- will fail)' }"
            ) -join "`n")
        }
        if ($syncExit -eq 7 -and -not $env:REMOTE_SYNC_RETRIED) {
            "remote.ps1: digest mismatch (exit 7): the tree changed while it was packed; retrying the sync once"
            $env:REMOTE_SYNC_RETRIED = '1'
            & $PSCommandPath @PSBoundParameters
            exit $LASTEXITCODE
        }
        if ($syncExit -eq 0) { "REMOTE-SYNC-OK $repo on $Machine matches this tree (head $($head.Substring(0, 8)), digest $digest)" }
        exit $syncExit
    }
}
