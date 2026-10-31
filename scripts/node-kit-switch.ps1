param(
    [string] $Alias = 'laptop',
    [string] $NodeEoie = 'C:\home\bin\eoie.exe',
    [string] $NodeKit = 'C:\home\bin\kit.exe',
    [string] $Config = (Join-Path $HOME '.config/remote-machines.json'),
    [switch] $Apply
)
$ErrorActionPreference = 'Stop'
function Invoke-Node([string] $script) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($script))
    $out = @(ssh $Alias "pwsh -NoProfile -NonInteractive -EncodedCommand $encoded" 2>&1)
    [pscustomobject]@{ Exit = $LASTEXITCODE; Text = ($out -join "`n") }
}
$busy = Invoke-Node "@(& '$NodeEoie' job list 50 2>&1 | Where-Object { `$_ -match 'state=(running|queued)' }).Count"
if ($busy.Exit -ne 0) { throw "node unreachable or eoie.exe failed: $($busy.Text)" }
$running = [int](($busy.Text -split "`n" | Where-Object { $_ -match '^\d+$' } | Select-Object -Last 1) ?? '0')
if ($running -gt 0) { [Console]::WriteLine("node busy: $running running or queued eoie jobs; switch refused (run again when idle)"); exit 3 }
$plan = Invoke-Node @"
`$eoie = Join-Path `$env:LOCALAPPDATA 'eoie'; `$kit = Join-Path `$env:LOCALAPPDATA 'kit'
foreach (`$part in 'jobs', 'windows') {
    `$from = Join-Path `$eoie `$part; `$to = Join-Path `$kit `$part
    if (-not (Test-Path `$from)) { "skip `$part: no eoie copy"; continue }
    if (Test-Path `$to) { "CONFLICT `$part: `$to already exists"; continue }
    "move `$from -> `$to (`$(@(Get-ChildItem `$from).Count) entries)"
}
`$hosts = @(Get-CimInstance Win32_Process | Where-Object { `$_.CommandLine -match 'job host' -and `$_.Name -match '^eoie' })
"eoie job hosts to stop: `$(`$hosts.Count)"
if (-not (Test-Path '$NodeKit')) { 'MISSING $NodeKit' }
"@
[Console]::WriteLine($plan.Text)
if ($plan.Text -match 'CONFLICT|MISSING') { throw 'switch refused: resolve the CONFLICT/MISSING lines first' }
if (-not $Apply) { [Console]::WriteLine('dry run: nothing changed; rerun with -Apply while the node is idle'); exit 0 }
$moved = Invoke-Node @"
`$eoie = Join-Path `$env:LOCALAPPDATA 'eoie'; `$kit = Join-Path `$env:LOCALAPPDATA 'kit'
Get-CimInstance Win32_Process | Where-Object { `$_.CommandLine -match 'job host' -and `$_.Name -match '^eoie' } | ForEach-Object { Stop-Process -Id `$_.ProcessId -Force }
[void][IO.Directory]::CreateDirectory(`$kit)
foreach (`$part in 'jobs', 'windows') { `$from = Join-Path `$eoie `$part; if (Test-Path `$from) { Move-Item -LiteralPath `$from -Destination (Join-Path `$kit `$part) } }
& '$NodeKit' job list 3 2>&1
"@
[Console]::WriteLine($moved.Text)
if ($moved.Text -notmatch 'kit job list ok') { throw "node kit.exe does not list the moved jobs; remote-machines.json left on eoie.exe" }
$machines = Get-Content $Config -Raw | ConvertFrom-Json -AsHashtable
Copy-Item $Config "$Config.before-node-kit-switch-$(Get-Date -Format yyyyMMdd-HHmm)"
$machines['node']['eoie'] = $NodeKit
[IO.File]::WriteAllText($Config, ($machines | ConvertTo-Json -Depth 6))
[Console]::WriteLine("switched: remote-machines.json node -> $NodeKit (backup beside it); eoie.exe stays on the node until archived")
