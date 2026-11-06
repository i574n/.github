param([string] $Root = (Join-Path $env:TEMP ('kit-hooks-test-' + $PID)))
$hooks = (Join-Path $HOME '.claude\hooks')
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
    @('allow', "& $Root\kit.exe agile note C:\some\backlog X ""y"" --as tibia 2>&1"),
    @('allow', "$Root/kit.exe agile set C:\some\backlog X 50 Active 2>`$null"),
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
`$began = [DateTime]::UtcNow.Ticks
if (`$env:STUB_DELAY_MS) { Start-Sleep -Milliseconds ([int]`$env:STUB_DELAY_MS) }
foreach (`$try in 1..50) { try { [IO.File]::AppendAllText('$Root\spans.txt', "`$began `$([DateTime]::UtcNow.Ticks) `$(`$args -join ' ')" + [Environment]::NewLine); break } catch { Start-Sleep -Milliseconds 20 } }
foreach (`$try in 1..50) { try { [IO.File]::AppendAllText('$log', (`$args -join ' ') + [Environment]::NewLine); break } catch { Start-Sleep -Milliseconds 20 } }
`$joined = `$args -join ' '
if (`$joined -match '^agile list .* --json') { '{"tasks":[{"id":"USER-1","assignee":"user","status":"Planned","priority":"P1","tags":"kit"}]}'; exit 0 }
if (`$joined -match 'agile (claim|release)|--any') { exit 9 }
if (`$joined -match '^agile next .* --team' -and `$env:STUB_NEXT_MODE -eq 'empty') { exit 3 }
if (`$joined -match '^agile next .* --team') { '  1  KIT-LIGHT   P1 D2  60m  unblocks=0  a light item'; '  2  USER-1   P1 D2  60m  unblocks=0  a user item'; exit 0 }
exit 0
"@
function Gate([hashtable] $vars, [string] $session = 'abcdef1234567890', [switch] $Active) {
    foreach ($k in @('AUTONOMOUS_GATE_DIR', 'AUTONOMOUS_GATE_KIT', 'AUTONOMOUS_GATE_TEST_CPU', 'KIT_LANE', 'STUB_NEXT_MODE', 'STUB_DELAY_MS', 'AUTONOMOUS_GATE_SPIN_SECONDS')) { Remove-Item "env:$k" -ErrorAction Ignore }
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
Check ($r.json.reason -match "top ready light items: KIT-LIGHT" -and $r.json.reason -match 'kit agile claim \S+ <ID> --as kit-abcdef12') 'the lane''s ready light items are listed, to claim explicitly'
Check (@($r.calls | Where-Object { $_ -match '--any|^agile (claim|release)' }).Count -eq 0) 'the gate never claims or releases on the lane''s behalf (no next --any)'
Check (@($r.calls | Where-Object { $_ -match '--team kit --load light,none --open 10' }).Count -eq 1) 'the light list asks for light,none as one argument'
Check ($r.json.reason -match 'KIT-LIGHT' -and $r.json.reason -notmatch 'USER-1') 'user-assigned items stay out of the light list'
Check ((Gate @{}).json.decision -eq 'block') 'a second stop minutes later is blocked again (no cooldown lets a lane idle)'

Reset-Stamps
$r = Gate @{ KIT_LANE = 'Tibia Lane' }
Check ($r.json.reason -match '--as tibia-lane\)') 'KIT_LANE overrides the lane and is normalized to [a-z0-9-]'

Reset-Stamps
$r = Gate @{ STUB_NEXT_MODE = 'empty' }
Check ($r.json.decision -eq 'block' -and $r.json.reason -match 'Pick one from: kit agile next \S+ --team kit' -and $r.json.reason -notmatch 'top ready') 'an empty light list still blocks and names the team query'
Check ($r.json.reason -match 'another team''s READY item from kit agile next \S+ --open 10') 'the block also points at any team''s ready items'
Reset-Stamps
Remove-Item (Join-Path $Root 'spans.txt') -ErrorAction Ignore
$r = Gate @{ STUB_DELAY_MS = '4000' }
Check ($r.json.decision -eq 'block' -and $r.json.reason -match 'KIT-LIGHT') 'with every kit call taking 4 s, the light items are still listed'
$spans = @(Get-Content (Join-Path $Root 'spans.txt') -ErrorAction Ignore | Where-Object { $_ -match ' (agile list|agile next .* --team|window list)' } | ForEach-Object { $f = $_ -split ' '; [pscustomobject]@{ start = [long]$f[0]; end = [long]$f[1] } })
Check ($spans.Count -eq 3 -and ($spans | Measure-Object start -Maximum).Maximum -lt ($spans | Measure-Object end -Minimum).Minimum) ("the gate's 3 kit calls run in parallel: every call starts before any ends ({0} calls)" -f $spans.Count)
Check ($r.seconds -lt 60) ("a slow kit stays inside the hook budget (took {0:N1} s)" -f $r.seconds)
Reset-Stamps
$r = Gate @{ STUB_DELAY_MS = '40000' }
Check ($r.json.decision -eq 'block' -and $r.seconds -lt 75) ("a hung kit still yields a block inside the 90 s hook timeout (took {0:N1} s)" -f $r.seconds)
Reset-Stamps
$null = Gate @{}
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

"== kit style check (lint wiring)"
$styleRoot = Join-Path $Root 'style'
[void][IO.Directory]::CreateDirectory((Join-Path $styleRoot 'src\pkg_a'))
[void][IO.Directory]::CreateDirectory((Join-Path $styleRoot 'standards'))
Set-Content (Join-Path $styleRoot 'src\pkg_a\x.spi') ':= x () : i32 = 1i32'
Set-Content (Join-Path $styleRoot 'standards\census.spi') ''
Set-Content (Join-Path $styleRoot 'standards\lint-macros.spi') 'inl lint_known_1 () = LintKnown ("pkg_a/x.spi", "known", "cast")'
$styleStub = Join-Path $Root 'stub-style-kit.ps1'
Set-Content $styleStub @"
`$joined = `$args -join ' '
if (`$joined -match '^standards --ratchet') { 'kit standards ratchet ok packages=1 regressions=0'; exit 0 }
if (`$joined -match '^lint macros') { "x.spi:3  known  cast   ```$'known_cast as i32'"; "x.spi:7  fresh  cast   ```$'fresh_cast as i32'"; 'failed  files 1  macros 2  violations 2  new 2'; exit 1 }
exit 0
"@
function Style([string] $added, [string] $kitPath = $styleStub) {
    $env:KIT_STYLE_ROOT = $styleRoot; $env:KIT_STYLE_KIT = $kitPath
    $p = @{ tool_name = 'Edit'; tool_input = @{ file_path = (Join-Path $styleRoot 'src\pkg_a\x.spi'); old_string = 'x'; new_string = $added } } | ConvertTo-Json -Compress
    $err = ($p | pwsh -NoProfile -NonInteractive -File (Join-Path $hooks 'kit-style-check.ps1') 2>&1 | Out-String)
    [pscustomobject]@{ code = $LASTEXITCODE; text = $err }
}
$r = Style ":= f () : i32 = `$'fresh_cast as i32' : i32"
Check ($r.code -eq 2 -and $r.text -match 'lint: pkg_a/x\.spi:7 cast fresh') 'a new host macro violation in the added text is reported with its package path'
$r = Style ":= k () : i32 = `$'known_cast as i32' : i32"
Check ($r.text -notmatch 'lint:') 'a violation already in the lint baseline is not reported'
$r = Style ':= plain () : i32 = 2i32'
Check ($r.code -eq 0) 'a violation outside the added text does not block the edit'
$r = Style ":= f () : i32 = `$'fresh_cast as i32' : i32" (Join-Path $Root 'missing-kit.exe')
Check ($r.text -notmatch 'lint:') 'without a kit binary the lint check fails open'
git -C $styleRoot init -q 2>$null; git -C $styleRoot add standards 2>$null; git -C $styleRoot -c user.email=hooks@test -c user.name=hooks commit -qm base 2>$null
function Baseline([string] $content) {
    $file = Join-Path $styleRoot 'standards\lint-macros.spi'
    Set-Content $file $content
    $env:KIT_STYLE_ROOT = $styleRoot; $env:KIT_STYLE_KIT = $styleStub
    $p = @{ tool_name = 'Edit'; tool_input = @{ file_path = $file; old_string = 'x'; new_string = 'y' } } | ConvertTo-Json -Compress
    $err = ($p | pwsh -NoProfile -NonInteractive -File (Join-Path $hooks 'kit-style-check.ps1') 2>&1 | Out-String)
    [pscustomobject]@{ code = $LASTEXITCODE; text = $err }
}
$r = Baseline "inl lint_known_1 () = LintKnown (`"pkg_a/x.spi`", `"known`", `"cast`")`ninl lint_known_2 () = LintKnown (`"pkg_a/x.spi`", `"fresh`", `"cast`")"
Check ($r.code -eq 2 -and $r.text -match 'not in git HEAD' -and $r.text -match '"fresh"') 'a lint baseline row that HEAD lacks is refused at edit time'
$r = Baseline ''
Check ($r.code -eq 0) 'a lint baseline that only shrinks passes'
Remove-Item env:KIT_STYLE_ROOT, env:KIT_STYLE_KIT -ErrorAction Ignore
"== backlog state guard"
function Guard([string] $json) { ($json | pwsh -NoProfile -NonInteractive -File (Join-Path $hooks 'kit-backlog-state-guard.ps1') 2>$null | Out-String).Trim() }
$denied = Guard (@{ tool_name = 'Edit'; tool_input = @{ file_path = 'C:\home\git\i574n.github\docs\backlog\state\agile.spi'; old_string = 'a'; new_string = 'b' } } | ConvertTo-Json -Compress)
Check ((($denied | ConvertFrom-Json).hookSpecificOutput.permissionDecision) -eq 'deny') 'a file-tool edit of backlog state is denied'
Check ($denied -match 'kit agile') 'the denial points at the kit agile verbs'
$forward = Guard (@{ tool_name = 'Write'; tool_input = @{ file_path = 'C:/home/git/i574n.github/docs/backlog/state/claims.spi'; content = 'x' } } | ConvertTo-Json -Compress)
Check ($forward -match '"deny"') 'forward slashes and other state modules are denied too'
Check ((Guard (@{ tool_name = 'Write'; tool_input = @{ file_path = 'C:\home\git\i574n.github\docs\backlog\README.md'; content = 'x' } } | ConvertTo-Json -Compress)) -eq '') 'a sibling path outside state/ is allowed'
Check ((Guard (@{ tool_name = 'Write'; tool_input = @{ file_path = 'C:\home\git\kit\src\agile_claim_model\state.spi'; content = 'x' } } | ConvertTo-Json -Compress)) -eq '') 'a source file named state is allowed'
Check ((Guard 'not json {') -eq '') 'a malformed payload fails open'
foreach ($k in @('AUTONOMOUS_GATE_DIR', 'AUTONOMOUS_GATE_KIT', 'AUTONOMOUS_GATE_TEST_CPU', 'KIT_LANE', 'STUB_NEXT_MODE')) { Remove-Item "env:$k" -ErrorAction Ignore }
Remove-Item -Recurse -Force $Root -ErrorAction Ignore
"hooks-test: $failed failed"
exit $failed
