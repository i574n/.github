param(
    [Parameter(Mandatory, Position = 0)] [string] $Machine,
    [Parameter(Mandatory, Position = 1)] [ValidateSet('run', 'job', 'status', 'sync', 'verify', 'fetch', 'refresh')] [string] $Verb,
    [Parameter(Position = 2, ValueFromRemainingArguments)] [string[]] $Rest = @(),
    [string] $Config = (Join-Path $HOME '.config/remote-machines.json'),
    [string] $Mirror,
    [string] $SpiralMirror,
    [switch] $Publish,
    [string] $RequireCore,
    [string] $CoreMode = 'single-flight',
    [string] $Script,
    [string[]] $Include = @(),
    [switch] $Force
)
$ErrorActionPreference = 'Stop'
$gitRoot = Split-Path (Split-Path $PSScriptRoot)
$machines = Get-Content $Config -Raw | ConvertFrom-Json -AsHashtable
if ($Machine -eq 'codespaces' -and $Verb -eq 'refresh') {
    $live = @(gh codespace list --json name,state | ConvertFrom-Json | Where-Object state -eq 'Available')
    $sshConfig = Join-Path $HOME '.ssh/codespaces.config'
    $entries = foreach ($codespace in $live) { gh codespace ssh --config -c $codespace.name 2>$null }
    Set-Content $sshConfig (@($entries) -join "`n") -Encoding utf8NoBOM
    $userConfig = Join-Path $HOME '.ssh/config'
    if ((Get-Content $userConfig -Raw) -notmatch '(?m)^Include codespaces\.config') {
        Set-Content $userConfig ("Include codespaces.config`n`n" + (Get-Content $userConfig -Raw)) -NoNewline -Encoding utf8NoBOM
    }
    foreach ($key in @($machines.Keys | Where-Object { $machines[$_].proxied -and "$($machines[$_].sshAlias)" -like 'cs.*' })) { $machines.Remove($key) }
    $names = foreach ($codespace in $live) {
        $key = "cs-$(($codespace.name -split '-')[0])"
        $machines[$key] = [ordered]@{
            kind = 'linux'; proxied = $true; sshAlias = "cs.$($codespace.name).main"; shell = '/home/codespace/pwsh/pwsh'
            gitRoot = '/tmp/remote/git'; mirrorBase = '/tmp/remote'; stage = '/tmp/remote/stage'; eoie = '/tmp/remote/bin/kit'
        }
        $key
    }
    $machines | ConvertTo-Json -Depth 6 | Set-Content $Config -Encoding utf8NoBOM
    "remote.ps1 codespaces refresh ok: $($live.Count) available -> $(@($names) -join ', ')"
    exit 0
}
if (-not $machines.ContainsKey($Machine)) { throw "remote.ps1: no machine '$Machine' in $Config (known: $($machines.Keys -join ', '))" }
$target = $machines[$Machine]
$addressCache = Join-Path $HOME ".ssh/$($target.sshAlias)-last-ip.txt"
$remoteSeparator = if ($target.kind -eq 'linux') { '/' } else { '\' }

function Join-RemotePath([string] $base, [Parameter(ValueFromRemainingArguments)] [string[]] $parts) {
    (@($base.TrimEnd('\', '/')) + @($parts | ForEach-Object { $_.Trim('\', '/') -replace '[\\/]', $remoteSeparator })) -join $remoteSeparator
}
$script:remoteExit = 0

function Test-TcpPort([string] $address, [int] $port = 22) {
    $client = [Net.Sockets.TcpClient]::new()
    try { $client.ConnectAsync($address, $port).Wait(4000) -and $client.Connected } catch { $false } finally { $client.Dispose() }
}

function Resolve-MachineAddress {
    if ($target.proxied) { return '' }
    if ($script:machineAddress) { return $script:machineAddress }
    $candidates = [Collections.Generic.List[string]]::new()
    if (Test-Path $addressCache) {
        $cached = (Get-Content $addressCache -TotalCount 1).Trim()
        if ($cached) {
            ssh -o "HostName=$cached" -o ConnectTimeout=8 -o BatchMode=yes -o ServerAliveInterval=5 -o ServerAliveCountMax=2 $target.sshAlias "exit 0" 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $script:machineAddress = $cached; return $cached }
        }
        $candidates.Add($cached)
    }
    try { [Net.Dns]::GetHostAddresses($target.hostName) | Where-Object AddressFamily -eq 'InterNetwork' | ForEach-Object { $candidates.Add($_.IPAddressToString) } } catch { }
    foreach ($address in @($target.lanAddresses)) { if ($address) { $candidates.Add($address) } }
    $tailscale = 'C:\Program Files\Tailscale\tailscale.exe'
    if ((Test-Path $tailscale) -and $target.tailscaleName) { & $tailscale ip -4 $target.tailscaleName 2>$null | Select-Object -First 1 | ForEach-Object { $candidates.Add($_.Trim()) } }
    $tried = [Collections.Generic.List[string]]::new()
    foreach ($address in @($candidates | Where-Object { $_ } | Select-Object -Unique)) {
        $tcp = Test-TcpPort $address
        if ($tcp) {
            ssh -o "HostName=$address" -o ConnectTimeout=8 -o BatchMode=yes -o ServerAliveInterval=5 -o ServerAliveCountMax=2 $target.sshAlias "exit 0" 2>$null | Out-Null
            if ($LASTEXITCODE -eq 0) { $script:machineAddress = $address; Set-Content $addressCache $address; return $address }
        }
        $tried.Add("$address ($(if ($tcp) { 'port 22 open, login refused: a user or key problem' } else { 'port 22 unreachable: a network problem' }))")
    }
    throw "remote.ps1: no route to $Machine works: $($tried -join '; '). Run i574n.github/scripts/node-reach.ps1 for a per-route, per-key report."
}

function ConvertTo-PwshLiteral([string] $text) { "'" + $text.Replace("'", "''") + "'" }

function Get-HostOption([string] $address) { @('-o', 'ServerAliveInterval=15', '-o', 'ServerAliveCountMax=4') + $(if ($address) { @('-o', "HostName=$address") } else { @() }) }

function Invoke-Remote([string] $command) {
    $address = Resolve-MachineAddress
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes("`$ProgressPreference = 'SilentlyContinue'`n" + $command))
    ssh @(Get-HostOption $address) $target.sshAlias "$($target.shell) -NoProfile -NonInteractive -EncodedCommand $encoded"
    $script:remoteExit = $LASTEXITCODE
    if ($script:remoteExit -ne 255 -and $address) { Set-Content $addressCache $address }
}

function Assert-NodeCore([string] $expected, [string] $mode) {
    $newEnv = ConvertTo-PwshLiteral (Join-RemotePath $target.gitRoot 'spiral/apps/compiler/scripts/env.ps1')
    $oldEnv = ConvertTo-PwshLiteral (Join-RemotePath $target.gitRoot 'spiral/apps/compiler/tmp/scripts/env.ps1')
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
    scp -q @(Get-HostOption $address) $localPath "$($target.sshAlias):$($remotePath.Replace('\', '/'))"
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

if ($Publish -and ($Verb -ne 'sync' -or $Mirror -or $Rest[0] -ne 'spiral')) { throw 'remote.ps1: -Publish belongs to sync spiral, without -Mirror (the version names its own mirror)' }
if ($Include.Count -and $Verb -ne 'sync') { throw 'remote.ps1: -Include belongs to sync' }
if ($SpiralMirror -and ($Verb -ne 'sync' -or -not $Mirror)) { throw 'remote.ps1: -SpiralMirror belongs to sync -Mirror <name> of a non-spiral repo' }
if ($Verb -ne 'run' -and $Script) { throw "remote.ps1: -Script belongs to run (got '$Verb')" }
if ($Verb -notin 'sync', 'verify' -and ($Mirror -or $Force)) { throw "remote.ps1: -Mirror and -Force belong to sync; on '$Verb' they were taken from the remote command line, which would run without them. Give the remote script a differently named parameter (e.g. -Tree for a mirror path)." }
switch ($Verb) {
    'run' {
        if ($RequireCore) { Assert-NodeCore $RequireCore $CoreMode }
        $body = if ($Script) { if ($Rest.Count) { throw 'remote.ps1: run takes either -Script <local file> or a command, not both' }; [IO.File]::ReadAllText((Resolve-Path -LiteralPath $Script).Path) } else { $Rest -join ' ' }
        Invoke-Remote $body; exit $script:remoteExit
    }
    'job' { if ($RequireCore) { Assert-NodeCore $RequireCore $CoreMode }; $slots = if ($target.slots) { "`$env:KIT_JOB_SLOTS = '$([int]$target.slots)'; " } else { '' }; Invoke-Remote ($slots + "& $(ConvertTo-PwshLiteral $target.eoie) job " + (($Rest | ForEach-Object { ConvertTo-PwshLiteral $_ }) -join ' ')); exit $script:remoteExit }
    'status' {
        Invoke-Remote (@(
            @'
if ($IsWindows) { $os = Get-CimInstance Win32_OperatingSystem; $cpu = '{0}%' -f (Get-CimInstance Win32_Processor | Measure-Object LoadPercentage -Average).Average; $freeGb = $os.FreePhysicalMemory / 1MB }
else { $a = (Get-Content /proc/stat -TotalCount 1).Split(' ', [StringSplitOptions]::RemoveEmptyEntries)[1..8] | ForEach-Object { [double]$_ }; Start-Sleep -Milliseconds 1000; $b = (Get-Content /proc/stat -TotalCount 1).Split(' ', [StringSplitOptions]::RemoveEmptyEntries)[1..8] | ForEach-Object { [double]$_ }; $total = ($b | Measure-Object -Sum).Sum - ($a | Measure-Object -Sum).Sum; $idle = ($b[3] + $b[4]) - ($a[3] + $a[4]); $cpu = '{0}%' -f [math]::Round(100 * (1 - $idle / [math]::Max($total, 1))); $freeGb = [double](Select-String -Path /proc/meminfo -Pattern '^MemAvailable:\s+(\d+)').Matches[0].Groups[1].Value / 1MB }
'{0} cpu {1} free-mem {2:N1} GB free-disk {3:N1} GB' -f [Environment]::MachineName, $cpu, $freeGb, ([IO.DriveInfo]::new([IO.Path]::GetPathRoot((Get-Location).Path)).AvailableFreeSpace / 1GB)
'@
            "& $(ConvertTo-PwshLiteral $target.eoie) job list 12 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }"
            "foreach (`$repo in Get-ChildItem $(ConvertTo-PwshLiteral $target.gitRoot) -Directory) { if (Test-Path (Join-Path `$repo.FullName '.git')) { '{0,-14} {1}' -f `$repo.Name, (git -C `$repo.FullName rev-parse --short=8 HEAD) } }"
            "`$runner = [Environment]::GetEnvironmentVariable('SPIRAL_EXE', 'User') ?? `$env:SPIRAL_EXE; if (`$runner -and (Test-Path `$runner)) { 'node runner   {0} {1:yyyy-MM-dd HH:mm} {2}' -f (Get-FileHash `$runner).Hash.Substring(0, 16), (Get-Item `$runner).LastWriteTime, `$runner } else { 'node runner   (SPIRAL_EXE unset or missing)' }"
            "`$core = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'spiral-bin' 'bin' 'single-flight' 'SpiralCompiler' 'Release' 'net11.0' 'SpiralCompilerCore.dll'; if (Test-Path `$core) { 'node compiler {0} {1:yyyy-MM-dd HH:mm}' -f (Get-FileHash `$core).Hash.Substring(0, 16), (Get-Item `$core).LastWriteTime } else { 'node compiler (missing)' }"
            "`$spiral = Join-Path $(ConvertTo-PwshLiteral $target.gitRoot) 'spiral'; `$lib = @(Get-ChildItem (Join-Path `$spiral 'lib' 'spiral') -Recurse -File -Include *.spi,*.spir -ErrorAction SilentlyContinue | Sort-Object FullName); 'node checkout {0} lib {1} files {2}' -f (git -C `$spiral rev-parse --short=8 HEAD), `$lib.Count, [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes((`$lib | ForEach-Object { '{0}={1}' -f `$_.FullName.Substring(`$spiral.Length).Replace('/', '\'), `$_.Length }) -join '|'))).Substring(0, 16)"
        ) -join "`n")
        $boxRunner = @([Environment]::GetEnvironmentVariable('SPIRAL_EXE', 'User'), (Join-Path $gitRoot 'spiral\workspace\target\release\spiral.exe')) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -First 1
        $boxCore = Join-Path ($env:SPIRAL_BIN_CACHE_DIR ?? (Join-Path $env:LOCALAPPDATA 'spiral-bin')) 'bin\single-flight\SpiralCompiler\Release\net11.0\SpiralCompilerCore.dll'
        if ($boxRunner) { 'box  runner   {0} {1:yyyy-MM-dd HH:mm} {2}' -f (Get-FileHash $boxRunner).Hash.Substring(0, 16), (Get-Item $boxRunner).LastWriteTime, $boxRunner }
        if (Test-Path $boxCore) { 'box  compiler {0} {1:yyyy-MM-dd HH:mm}' -f (Get-FileHash $boxCore).Hash.Substring(0, 16), (Get-Item $boxCore).LastWriteTime }
        $boxSpiral = Join-Path (Split-Path (Split-Path $PSScriptRoot)) 'spiral'
        $boxLib = @(Get-ChildItem (Join-Path $boxSpiral 'lib\spiral') -Recurse -File -Include *.spi, *.spir -ErrorAction SilentlyContinue | Sort-Object FullName)
        'box  checkout {0} lib {1} files {2}' -f (git -C $boxSpiral rev-parse --short=8 HEAD), $boxLib.Count, [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes((($boxLib | ForEach-Object { '{0}={1}' -f $_.FullName.Substring($boxSpiral.Length), $_.Length }) -join '|')))).Substring(0, 16)
        'runner, compiler and checkout lines must match between node and box for comparable gate verdicts (memory node-stale-compiler-dll)'
        $coreDigest = @'
function Get-CoreDigest([string] $core) {
    if (-not (Test-Path -LiteralPath $core)) { return 'missing' }
    $lines = Get-ChildItem -LiteralPath $core -Recurse -File | Sort-Object { $_.FullName.Substring($core.Length).Replace('/', '\') } -Culture 'en-US' | ForEach-Object { '{0} {1}' -f $_.FullName.Substring($core.Length).Replace('/', '\'), (Get-FileHash -LiteralPath $_.FullName).Hash }
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))).Substring(0, 16)
}
'@
        . ([scriptblock]::Create($coreDigest))
        $boxCoreDir = Join-Path $boxSpiral 'deps\The-Spiral-Language\VS Code Plugin\core'
        $nodeCoreDir = Join-RemotePath $target.gitRoot 'spiral' 'deps' 'The-Spiral-Language' 'VS Code Plugin' 'core'
        $boxCoreDigest = Get-CoreDigest $boxCoreDir
        $nodeCoreDigest = "$(Invoke-Remote ($coreDigest + "`nGet-CoreDigest $(ConvertTo-PwshLiteral $nodeCoreDir)"))".Trim()
        if ($nodeCoreDigest -eq $boxCoreDigest) { 'deps core {0} matches the box (content digest)' -f $nodeCoreDigest }
        else { 'DEPS-CORE-STALE deps\The-Spiral-Language core {0} vs box {1}: node compiles resolve |core- there, so they typecheck against another core (NODE-CORE-COPY-DRIFT)' -f $nodeCoreDigest, $boxCoreDigest; exit 6 }
        exit $script:remoteExit
    }
    'verify' {
        if ($Rest.Count -lt 1) { throw 'remote.ps1 <machine> verify <repo> [-Mirror <name>] [path ...]' }
        $repo = $Rest[0]
        $localRepo = Join-Path $gitRoot $repo
        $mirrorRoot = if ($target.mirrorBase) { $target.mirrorBase } else { throw "remote.ps1: machine '$Machine' has no mirrorBase in $Config" }
        $remoteRepo = if ($Mirror) { Join-RemotePath $mirrorRoot $Mirror $repo } else { Join-RemotePath $target.gitRoot $repo }
        $paths = @($Rest | Select-Object -Skip 1)
        if (-not $paths.Count) { $paths = @((git -C $localRepo diff --name-only --no-renames HEAD) + (git -C $localRepo ls-files -o --exclude-standard) | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $localRepo $_) -PathType Leaf) } | Sort-Object -Unique) }
        $local = @{}; foreach ($p in $paths) { $f = Join-Path $localRepo $p; $local[$p] = if (Test-Path -LiteralPath $f -PathType Leaf) { (Get-FileHash -LiteralPath $f).Hash } else { 'MISSING' } }
        $list = ($paths | ForEach-Object { ConvertTo-PwshLiteral $_ }) -join ','
        $remote = @{}
        foreach ($line in @(Invoke-Remote "`$repo = $(ConvertTo-PwshLiteral $remoteRepo); foreach (`$p in @($list)) { `$f = Join-Path `$repo `$p; '{0}`t{1}' -f `$p, `$(if (Test-Path -LiteralPath `$f -PathType Leaf) { (Get-FileHash -LiteralPath `$f).Hash } else { 'MISSING' }) }")) { $k, $v = "$line" -split "`t", 2; if ($v) { $remote[$k] = $v.Trim() } }
        $stale = @($paths | Where-Object { $remote[$_] -ne $local[$_] })
        foreach ($p in $stale) { '{0,-60} box {1} mirror {2}' -f $p, $local[$p].Substring(0, [Math]::Min(12, $local[$p].Length)), "$($remote[$p])".Substring(0, [Math]::Min(12, "$($remote[$p])".Length)) }
        if ($stale.Count) { "MIRROR-STALE $repo on ${Machine}$(if ($Mirror) { " ($Mirror)" }): $($stale.Count) of $($paths.Count) paths differ from the box; re-sync before trusting a proof"; exit 6 }
        "MIRROR-FRESH $repo on ${Machine}$(if ($Mirror) { " ($Mirror)" }): $($paths.Count) paths match the box"
        exit 0
    }
    'fetch' {
        if ($Rest.Count -ne 2) { throw 'remote.ps1 <machine> fetch <remote path> <local path>' }
        $address = Resolve-MachineAddress
        scp -q -r @(Get-HostOption $address) "$($target.sshAlias):$($Rest[0].Replace('\', '/'))" $Rest[1]
        exit $LASTEXITCODE
    }
    'sync' {
        if ($Rest.Count -ne 1) { throw 'remote.ps1 <machine> sync <repo folder under the git root>' }
        $repo = $Rest[0]
        $localRepo = Join-Path $gitRoot $repo
        $remoteSource = ConvertTo-PwshLiteral (Join-RemotePath $target.gitRoot $repo)
        $mirrorRoot = if ($target.mirrorBase) { $target.mirrorBase } else { throw "remote.ps1: machine '$Machine' has no mirrorBase in $Config" }
        $remoteRepo = if ($Mirror) { ConvertTo-PwshLiteral (Join-RemotePath $mirrorRoot $Mirror $repo) } else { $remoteSource }
        $stage = ConvertTo-PwshLiteral $target.stage
        $syncToken = "$repo-$PID-$([DateTime]::UtcNow.Ticks)"
        $head = git -C $localRepo rev-parse HEAD
        $tracked = @((git -C $localRepo diff --name-only --no-renames -z HEAD) -split "`0")
        $untracked = @((git -C $localRepo ls-files -o --exclude-standard -z) -split "`0")
        $changed = @($tracked + $untracked | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $localRepo $_) -PathType Leaf) } | Sort-Object -Unique)
        if ($Include.Count) {
            $rootFull = (Resolve-Path -LiteralPath $localRepo).Path.TrimEnd('\', '/')
            $extra = @(foreach ($inc in ($Include | ForEach-Object { $_ -split ',' } | Where-Object { $_ })) {
                $full = Join-Path $rootFull $inc
                if (-not (Test-Path -LiteralPath $full)) { throw "remote.ps1: -Include $inc does not exist under $repo" }
                if (Test-Path -LiteralPath $full -PathType Leaf) { $inc.Replace('\', '/') }
                else { Get-ChildItem -LiteralPath $full -Recurse -File -Force | ForEach-Object { $_.FullName.Substring($rootFull.Length + 1).Replace('\', '/') } }
            })
            $changed = @($changed + $extra | Sort-Object -Unique)
            "include: $($extra.Count) files from $($Include -join ', ') (gitignored inputs travel with the tree and count in the digest)"
        }
        $deleted =@((git -C $localRepo diff --name-only --diff-filter=D --no-renames -z HEAD) -split "`0" | Where-Object { $_ } | Sort-Object -Unique)
        $digest = if ($changed) { Get-TreeDigest $localRepo $changed } else { 'none' }
        if ($Publish) { $Mirror = 'spiral-' + $head.Substring(0, 8) + '-' + $digest.Substring(0, [Math]::Min(8, $digest.Length)); $remoteRepo = ConvertTo-PwshLiteral (Join-RemotePath $mirrorRoot $Mirror $repo) }
        "sync $repo -> ${Machine}: head $($head.Substring(0, 8)), $($changed.Count) changed, $($deleted.Count) deleted, digest $digest"
        if (-not $Force -and -not $Mirror) {
            Invoke-Remote "if (& $(ConvertTo-PwshLiteral $target.eoie) job list 50 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }) { exit 4 }"
            if ($script:remoteExit -eq 4) { 'remote.ps1: jobs are running or queued there; pass -Force, or use -Mirror <job> for an isolated worktree that is safe under running jobs'; exit 4 }
        }
        Invoke-Remote "New-Item -ItemType Directory -Force $stage | Out-Null; if (-not (Test-Path $remoteSource)) { git init -q $remoteSource }; git -C $remoteSource cat-file -e '$head^{commit}' 2>`$null; if (`$LASTEXITCODE) { git -C $remoteSource fetch -q origin 2>`$null; git -C $remoteSource cat-file -e '$head^{commit}' 2>`$null }; exit `$LASTEXITCODE"
        if ($script:remoteExit -ne 0) {
            $remoteHead = "$(Invoke-Remote "git -C $remoteSource rev-parse HEAD")".Trim()
            $sharesRemoteHead = $false
            if ($remoteHead -match '^[0-9a-f]{40}$') { git -C $localRepo cat-file -e "$remoteHead^{commit}" 2>$null; $sharesRemoteHead = $LASTEXITCODE -eq 0 }
            $range = @(if ($sharesRemoteHead) { 'HEAD', '--not', $remoteHead } else { 'HEAD' })
            $bundle = Join-Path ([IO.Path]::GetTempPath()) "remote-sync-$syncToken.bundle"
            git -C $localRepo bundle create $bundle @range 2>$null
            if ($LASTEXITCODE) { throw "remote.ps1: git bundle failed for $repo" }
            Copy-ToRemote $bundle (Join-RemotePath $target.stage "$syncToken.bundle")
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
        Copy-ToRemote (Join-Path $work 'changed.txt') (Join-RemotePath $target.stage "$syncToken.changed.txt")
        Copy-ToRemote (Join-Path $work 'deleted.txt') (Join-RemotePath $target.stage "$syncToken.deleted.txt")
        if ($changed) {
            $archive = Join-Path $work 'tree.tar'
            tar -cf $archive -C $localRepo -T (Join-Path $work 'changed.txt')
            if ($LASTEXITCODE) { throw "remote.ps1: tar failed for $repo" }
            Copy-ToRemote $archive (Join-RemotePath $target.stage "$syncToken.tar")
        }
        Remove-Item -Recurse -Force $work
        Invoke-Remote (@(
            $remoteDigest
            "`$repo = $remoteRepo; `$stage = $stage"
            "git -C `$repo -c advice.detachedHead=false checkout -q --detach --force $head; if (`$LASTEXITCODE) { exit 5 }"
            "git -C `$repo clean -fd | ForEach-Object { 'remote-sync pruned untracked ' + (`$_ -replace '^Removing ', '') }"
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
            Invoke-Remote (@(
                "`$sdeps = Join-Path $remoteSource 'deps'; `$mdeps = Join-Path $remoteRepo 'deps'"
                "if ('$repo' -ne 'spiral') {"
                "  `$sspiral = Join-Path `$sdeps 'spiral'; `$spiralRoot = Join-Path (Split-Path $remoteSource) 'spiral'"
                "  `$wants = @(Get-ChildItem $remoteRepo -Filter package.spiproj -Recurse -Depth 2 -ErrorAction SilentlyContinue | Select-String -Pattern 'deps/spiral' -SimpleMatch -List).Count"
                "  if (`$wants -and -not (Test-Path `$sspiral) -and (Test-Path `$spiralRoot)) { New-Item -ItemType Directory -Force `$sdeps | Out-Null; New-Item -ItemType `$(if (`$IsWindows) { 'Junction' } else { 'SymbolicLink' }) -Path `$sspiral -Target `$spiralRoot | Out-Null; 'DEPS-SPIRAL-LINKED ' + `$sspiral }"
                "}"
                "if ((Test-Path `$sdeps) -and -not @(Get-ChildItem `$mdeps -Force -ErrorAction SilentlyContinue).Count) {"
                "  if (Test-Path `$mdeps) { Remove-Item `$mdeps -Force -Recurse -ErrorAction SilentlyContinue }"
                "  New-Item -ItemType `$(if (`$IsWindows) { 'Junction' } else { 'SymbolicLink' }) -Path `$mdeps -Target `$sdeps -ErrorAction SilentlyContinue | Out-Null"
                "}"
                $(if ($SpiralMirror -and $repo -ne 'spiral') { "`$smp = $(if ($SpiralMirror -eq 'current') { "Join-Path $(ConvertTo-PwshLiteral $mirrorRoot) (Get-Content -LiteralPath (Join-Path $(ConvertTo-PwshLiteral $mirrorRoot) 'spiral-current.txt') -ErrorAction SilentlyContinue) 'spiral'" } else { ConvertTo-PwshLiteral (Join-RemotePath $mirrorRoot $SpiralMirror 'spiral') }); if (-not (Test-Path `$smp)) { 'SPIRAL-MIRROR-MISSING ' + `$smp; exit 8 }; if (Test-Path `$mdeps) { `$it = Get-Item `$mdeps -Force; if (`$it.LinkType) { `$it.Delete() } else { Get-ChildItem `$mdeps -Force | ForEach-Object { if (`$_.LinkType) { `$_.Delete() } }; `$it.Delete() } }; New-Item -ItemType Directory -Force `$mdeps | Out-Null; New-Item -ItemType `$(if (`$IsWindows) { 'Junction' } else { 'SymbolicLink' }) -Path (Join-Path `$mdeps 'spiral') -Target `$smp | Out-Null; 'MIRROR-SPIRAL-LINKED ' + `$smp" } else { '' })
                "`$expect = if ('$repo' -eq 'spiral') { Join-Path `$mdeps 'The-Spiral-Language' } else { `$mdeps }"
                "`$cellsOk = `$true"
                "if ('$repo' -eq 'spiral') {"
                "  `$scells = Join-Path $remoteSource 'apps' 'cells' 'deps'; `$mcells = Join-Path $remoteRepo 'apps' 'cells' 'deps'"
                "  if ((Test-Path (Join-Path `$scells 'kino')) -and -not (Test-Path (Join-Path `$mcells 'kino'))) { if (`$IsWindows) { robocopy `$scells `$mcells /E /NFL /NDL /NJH /NJS /NP /R:1 /W:1 | Out-Null } else { New-Item -ItemType Directory -Force `$mcells | Out-Null; Copy-Item -Recurse -Force (Join-Path `$scells '*') `$mcells } }"
                "  `$cellsOk = Test-Path (Join-Path `$mcells 'kino')"
                "}"
                "if (-not `$cellsOk) { 'MIRROR-DEPS-MISSING (apps\cells\deps has no kino: Cells jobs will fail at mix deps.get)' } elseif ((Test-Path `$expect) -and @(Get-ChildItem `$mdeps -Force -ErrorAction SilentlyContinue).Count) { 'MIRROR-DEPS-OK deps -> ' + $(if ($SpiralMirror -and $repo -ne 'spiral') { "'spiral mirror $SpiralMirror'" } else { "'shared source'" }) + `$(if ('$repo' -eq 'spiral') { ', apps\cells\deps copied' } else { '' }) } elseif (-not (Test-Path `$sdeps)) { 'MIRROR-DEPS-NONE (the shared source has no deps/)' } else { 'MIRROR-DEPS-MISSING (|core- will fail)' }"
            ) -join "`n")
        }
        $retried = [int]($env:REMOTE_SYNC_RETRIED ?? '0')
        if ($syncExit -ne 0 -and $retried -lt 2) {
            $why = switch ($syncExit) { 7 { 'digest mismatch: the tree changed while it was packed' } 255 { 'ssh dropped' } 5 { 'remote checkout failed' } 6 { 'remote untar failed' } default { "remote step exited $syncExit" } }
            "remote.ps1: no REMOTE-SYNC-OK ($why, exit $syncExit); retry $($retried + 1) of 2"
            $env:REMOTE_SYNC_RETRIED = "$($retried + 1)"
            & $PSCommandPath @PSBoundParameters
            exit $LASTEXITCODE
        }
        if ($syncExit -eq 0 -and $Publish) { Invoke-Remote "Set-Content -LiteralPath (Join-Path $(ConvertTo-PwshLiteral $mirrorRoot) 'spiral-current.txt') -Value $(ConvertTo-PwshLiteral $Mirror) -NoNewline; 'SPIRAL-PUBLISHED $Mirror'; `$root = $(ConvertTo-PwshLiteral $mirrorRoot); `$linked = @(Get-ChildItem `$root -Directory | ForEach-Object { Get-ChildItem `$_.FullName -Directory -ErrorAction SilentlyContinue } | ForEach-Object { `$d = Join-Path `$_.FullName 'deps' 'spiral'; if (Test-Path `$d) { ""`$((Get-Item `$d -Force).Target)"" } }); `$cmds = if (Get-Command Get-CimInstance -ErrorAction SilentlyContinue) { @(Get-CimInstance Win32_Process | ForEach-Object { ""`$(`$_.CommandLine)"" }) } else { @(ps -eo args) }; foreach (`$v in Get-ChildItem `$root -Directory | Where-Object { `$_.Name -match '^spiral-[0-9a-f]{8}-[0-9A-Fa-f]+$' -and `$_.Name -ne $(ConvertTo-PwshLiteral $Mirror) }) { if (-not (`$linked | Where-Object { `$_ -like ""*`$(`$v.Name)*"" }) -and -not (`$cmds | Where-Object { `$_ -like ""*`$(`$v.Name)*"" })) { 'SPIRAL-VERSION-STALE ' + `$v.FullName } }" }
        if ($syncExit -eq 0) { "REMOTE-SYNC-OK $repo on $Machine matches this tree (head $($head.Substring(0, 8)), digest $digest)" }
        exit $syncExit
    }
}
