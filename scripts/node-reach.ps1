param([string] $Machine = 'node', [string] $Config = (Join-Path $HOME '.config/remote-machines.json'))
$ErrorActionPreference = 'Continue'
$target = (Get-Content $Config -Raw | ConvertFrom-Json -AsHashtable)[$Machine]
if (-not $target) { throw "node-reach.ps1: no machine '$Machine' in $Config" }
$routes = [ordered]@{}
try { [Net.Dns]::GetHostAddresses($target.hostName) | Where-Object AddressFamily -eq 'InterNetwork' | ForEach-Object { $routes["lan-name $($target.hostName)"] = $_.IPAddressToString } } catch { $routes["lan-name $($target.hostName)"] = $null }
foreach ($a in @($target.lanAddresses)) { if ($a) { $routes["lan-ip"] = $a } }
$tailscale = 'C:\Program Files\Tailscale\tailscale.exe'
$routes['tailscale'] = if (Test-Path $tailscale) { & $tailscale ip -4 $target.tailscaleName 2>$null | Select-Object -First 1 } else { $null }
$keys = @(Get-ChildItem (Join-Path $HOME '.ssh') -File | Where-Object { $_.Name -like 'laptop_ed25519*' -and $_.Extension -eq '' } | ForEach-Object FullName)
$clients = [ordered]@{ windows = (Get-Command ssh -ErrorAction SilentlyContinue).Source; git = (Join-Path $HOME 'scoop\apps\git\current\usr\bin\ssh.exe') }
foreach ($name in $routes.Keys) {
    $address = $routes[$name]
    if (-not $address) { '{0,-28} no address' -f $name; continue }
    $client = [Net.Sockets.TcpClient]::new()
    $tcp = try { $client.ConnectAsync($address, 22).Wait(4000) -and $client.Connected } catch { $false } finally { $client.Dispose() }
    if (-not $tcp) { '{0,-28} {1,-15} port 22 UNREACHABLE (network: the route, the node is off, or sshd is down)' -f $name, $address; continue }
    foreach ($key in $keys) {
        $result = ssh -o "HostName=$address" -o IdentitiesOnly=yes -o "IdentityFile=$key" -o ConnectTimeout=8 $target.sshAlias "hostname" 2>&1 | Select-Object -Last 1
        $ok = $LASTEXITCODE -eq 0
        '{0,-28} {1,-15} {2,-24} {3}' -f $name, $address, (Split-Path $key -Leaf), $(if ($ok) { "OK ($result)" } else { "LOGIN REFUSED: $result (user, key or authorized_keys)" })
    }
}
if (Test-Path $clients.git) {
    $address = @($routes.Values | Where-Object { $_ }) | Select-Object -First 1
    $r = & $clients.git -F (Join-Path $HOME '.ssh\config') -o "HostName=$address" $target.sshAlias "hostname" 2>&1 | Select-Object -Last 1
    '{0,-28} {1,-15} {2,-24} {3}' -f 'git ssh client', $address, 'config keys', $(if ($LASTEXITCODE -eq 0) { "OK ($r)" } else { "FAILED: $r" })
}
$shellCheck = ssh $target.sshAlias "$($target.shell) -NoProfile -NonInteractive -Command `"Get-Date -Format o`"" 2>&1 | Select-Object -Last 1
'{0,-28} {1}' -f "node $($target.shell)", $(if ($LASTEXITCODE -eq 0) { "OK ($shellCheck)" } else { "FAILED: $shellCheck" })
