param([string] $Root = (Join-Path $env:TEMP 'kit-hooks-test'))
$hooks = 'C:\Users\i574n\.claude\hooks'
$failed = 0
function Check([bool] $ok, [string] $label) {
    if (-not $ok) { $script:failed++ }
    "{0}  {1}" -f $(if ($ok) { 'OK ' } else { 'BAD' }), $label
}
Remove-Item -Recurse -Force $Root -ErrorAction Ignore
[void][IO.Directory]::CreateDirectory($Root)

"== tree-lease hook"
$leaseFile = Join-Path $env:LOCALAPPDATA ('kit\tree-leases\probe-' + $PID + '.lease')
Set-Content $leaseFile "root=$Root`npid=$PID`nholder=hooks-test`nsince=$(Get-Date -Format HH:mm)"
$cases = @(
    @('allow', "& $Root\kit.exe agile note C:\home\git\i574n.github\docs\backlog X ""y"" --as tibia 2>&1"),
    @('allow', "$Root/kit.exe agile set C:\home\git\i574n.github\docs\backlog X 50 Active 2>`$null"),
    @('allow', "Get-Content $Root\src\kit_jobs\host.spi 2>`$null | Select-Object -First 3"),
    @('allow', "& $Root\kit.exe job list *> `$null"),
    @('allow', "`$n=@'`nnote naming $Root and Move-Item and copy`n'@; kit.exe agile note C:\x ID `$n --as kit"),
    @('block', "Set-Content -Path $Root\src\a.spi -Value @'`nx`n'@"),
    @('block', "Set-Content $Root\src\a.spi 'x'"),
    @('block', "echo x > $Root\src\a.spi"),
    @('block', "Copy-Item C:\temp\a.spi $Root\src\a.spi"),
    @('block', "& $Root\kit.exe agile list C:\x 2>&1; Remove-Item $Root\src\b.spi"),
    @('allow', "Get-Content $Root\scripts\lane-copy-swap.do"),
    @('allow', "Get-Content $Root\docs\rm-notes.md, $Root\src\kit_do_move\move.spi"),
    @('block', "cp C:\temp\a.spi $Root\src\a.spi"),
    @('block', "copy C:\temp\a.spi $Root\src\a.spi"),
    @('block', "rm $Root\src\b.spi")
)
foreach ($case in $cases) {
    $payload = @{ tool_name = 'PowerShell'; tool_input = @{ command = $case[1] } } | ConvertTo-Json -Compress
    $null = $payload | pwsh -NoProfile -NonInteractive -File (Join-Path $hooks 'kit-tree-lease.ps1') 2>&1
    $got = if ($LASTEXITCODE -eq 2) { 'block' } else { 'allow' }
    Check ($got -eq $case[0]) ("tree-lease want={0} got={1}  {2}" -f $case[0], $got, $case[1].Substring(0, [Math]::Min(70, $case[1].Length)))
}
Remove-Item $leaseFile -Force

"== autonomous gate"
$dir = Join-Path $Root 'autonomous'
[void][IO.Directory]::CreateDirectory($dir)
Set-Content (Join-Path $dir 'teams.txt') 'abcdef12=kit'
$log = Join-Path $Root 'kit-calls.txt'
$stub = Join-Path $Root 'stub-kit.ps1'
Set-Content $stub @"
if (`$env:STUB_DELAY_MS) { Start-Sleep -Milliseconds ([int]`$env:STUB_DELAY_MS) }
foreach (`$try in 1..50) { try { [IO.File]::AppendAllText('$log', (`$args -join ' ') + [Environment]::NewLine); break } catch { Start-Sleep -Milliseconds 20 } }
`$joined = `$args -join ' '
if (`$joined -match '^agile list .* --json') { '{"tasks":[{"id":"USER-1","assignee":"user","status":"Planned","priority":"P1","tags":"kit"}]}'; exit 0 }
if (`$joined -match '^agile next .* --any') {
    if (`$env:STUB_ANY_MODE -eq 'old') { [Console]::Error.WriteLine('kit error: agile next rejected: unexpected argument --any'); exit 2 }
    if (`$env:STUB_ANY_MODE -eq 'none') { 'kit agile next ok claimed=none reason=all-leased wip=3'; exit 3 }
    'kit agile next ok claimed=TEST-1 owner=x expires=2026-11-02T23:00Z ttl_min=90 wip=1 rank=1'
    'item TEST-1 P0 D2 Planned kit the claimed test item'
    'last-note none'
    'paths none'
    exit 0
}
if (`$joined -match '^agile next .* --team') { '  1  KIT-LIGHT   P1 D2  60m  unblocks=0  a light item'; '  2  USER-1   P1 D2  60m  unblocks=0  a user item'; exit 0 }
exit 0
"@
function Gate([hashtable] $vars, [string] $session = 'abcdef1234567890', [switch] $Active) {
    foreach ($k in @('AUTONOMOUS_GATE_DIR', 'AUTONOMOUS_GATE_KIT', 'AUTONOMOUS_GATE_TEST_CPU', 'KIT_LANE', 'STUB_ANY_MODE', 'STUB_DELAY_MS', 'AUTONOMOUS_GATE_SPIN_SECONDS')) { Remove-Item "env:$k" -ErrorAction Ignore }
    $env:AUTONOMOUS_GATE_DIR = $dir; $env:AUTONOMOUS_GATE_KIT = $stub; $env:AUTONOMOUS_GATE_TEST_CPU = '90'
    foreach ($k in $vars.Keys) { Set-Item "env:$k" $vars[$k] }
    Remove-Item $log -ErrorAction Ignore
    $payload = @{ session_id = $session; stop_hook_active = [bool]$Active } | ConvertTo-Json -Compress
    $clock = [Diagnostics.Stopwatch]::StartNew()
    $out = ($payload | pwsh -NoProfile -NonInteractive -File (Join-Path $hooks 'autonomous-gate.ps1') 2>$null | Out-String).Trim()
    [pscustomobject]@{ seconds = $clock.Elapsed.TotalSeconds; text = $out; json = $(try { $out | ConvertFrom-Json } catch { $null }); calls = $(if (Test-Path $log) { Get-Content $log } else { @() }) }
}
function Reset-Stamps { Remove-Item (Join-Path $dir 'gate-stamps') -Recurse -Force -ErrorAction Ignore }

Set-Content (Join-Path $dir 'standing.txt') 'until=2020-01-01T00:00'
Reset-Stamps
$r = Gate @{}
Check ($r.json.systemMessage -match 'OFF' -and $r.json.systemMessage -match 'ended at 2020-01-01' -and -not $r.json.decision) 'an expired window is reported to the user, not swallowed'
$r = Gate @{}
Check ($r.text -eq '') 'the expiry notice is shown at most once an hour per session'
Set-Content (Join-Path $dir 'standing.txt') 'until=tomorrow-ish'
Reset-Stamps
Check ((Gate @{}).json.systemMessage -match 'does not parse') 'an unparseable until is reported'

Set-Content (Join-Path $dir 'standing.txt') ("until=" + (Get-Date).AddHours(2).ToString('s'))
Reset-Stamps
$r = Gate @{}
Check ($r.json.decision -eq 'block') 'an armed window blocks the stop'
Check ($r.json.reason -match 'CLAIMED FOR YOU' -and $r.json.reason -match 'TEST-1' -and $r.json.reason -match 'the claimed test item') 'the claimed item is handed to the lane'
Check (@($r.calls | Where-Object { $_ -match '^agile next .* --any --as kit-abcdef12 --ttl 90' }).Count -eq 1) 'next --any is called with the lane (team-session8) and a ttl'
Reset-Stamps
$r = Gate @{ STUB_ANY_MODE = 'none' }
Check (@($r.calls | Where-Object { $_ -match '--team kit --load light,none --open 10' }).Count -eq 1) 'the light list asks for light,none as one argument'
Check ($r.json.reason -match 'KIT-LIGHT' -and $r.json.reason -notmatch 'USER-1') 'user-assigned items stay out of the light list'
Check ((Gate @{}).json.decision -eq 'block') 'a second stop minutes later is blocked again (no cooldown lets a lane idle)'

Reset-Stamps
$r = Gate @{ KIT_LANE = 'Tibia Lane' }
Check (@($r.calls | Where-Object { $_ -match '--any --as tibia-lane --ttl 90' }).Count -eq 1) 'KIT_LANE overrides the lane and is normalized to [a-z0-9-]'

Reset-Stamps
$r = Gate @{ STUB_ANY_MODE = 'old' }
Check ($r.json.decision -eq 'block' -and $r.json.reason -notmatch 'CLAIMED' -and $r.json.reason -match 'KIT-LIGHT') 'a kit without --any fails open to the light list'
Reset-Stamps
$r = Gate @{ STUB_ANY_MODE = 'none' }
Check ($r.json.decision -eq 'block' -and $r.json.reason -notmatch 'CLAIMED') 'an empty queue (exit 3) still blocks with the light list'

Reset-Stamps
$r = Gate @{ STUB_DELAY_MS = '4000' }
Check ($r.json.reason -match 'CLAIMED FOR YOU' -and @($r.calls | Where-Object { $_ -match '^agile (list|next .* --team)' }).Count -eq 0) 'a claim takes the fast path: no backlog list, no light list'
Check ($r.seconds -lt 20) ("with every kit call taking 4 s, a claim blocks within 20 s (took {0:N1} s)" -f $r.seconds)
Reset-Stamps
$r = Gate @{ STUB_DELAY_MS = '4000'; STUB_ANY_MODE = 'none' }
Check ($r.json.decision -eq 'block' -and $r.json.reason -match 'KIT-LIGHT') 'the fallback still lists light items when nothing is claimed'
Check ($r.seconds -lt 30) ("the fallback runs its calls in parallel: 4 calls x 4 s finish within 30 s (took {0:N1} s)" -f $r.seconds)
Reset-Stamps
$r = Gate @{ STUB_DELAY_MS = '40000'; STUB_ANY_MODE = 'none' }
Check ($r.json.decision -eq 'block' -and $r.seconds -lt 75) ("a hung kit still yields a block inside the 90 s hook timeout (took {0:N1} s)" -f $r.seconds)
Reset-Stamps
$null = Gate @{ STUB_ANY_MODE = 'none' }
Check ((Gate @{} -Active).text -eq '') 'a stop right after a block (spin guard window) goes through'
$stampFile = Get-ChildItem (Join-Path $dir 'gate-stamps') -Filter 'abcdef1234567890.txt' | Select-Object -First 1
$stampFile.LastWriteTime = (Get-Date).AddMinutes(-5)
Check ((Gate @{} -Active).json.decision -eq 'block') 'a continued turn that worked past the spin window is blocked again (no one-block-then-idle)'
Set-Content (Join-Path $dir 'disarmed') ''
Reset-Stamps
Check ((Gate @{}).text -eq '') 'a disarmed gate is silent'
Remove-Item (Join-Path $dir 'disarmed')
Remove-Item (Join-Path $dir 'standing.txt')
Check ((Gate @{}).text -eq '') 'no standing order is silent (not armed on purpose)'

foreach ($k in @('AUTONOMOUS_GATE_DIR', 'AUTONOMOUS_GATE_KIT', 'AUTONOMOUS_GATE_TEST_CPU', 'KIT_LANE', 'STUB_ANY_MODE')) { Remove-Item "env:$k" -ErrorAction Ignore }
Remove-Item -Recurse -Force $Root -ErrorAction Ignore
"hooks-test: $failed failed"
exit $failed
