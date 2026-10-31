param([int] $StaleDays = 3, [string] $Out = '')
$ErrorActionPreference = 'Stop'
$k = "$env:LOCALAPPDATA\kit\bin\kit.exe"
$root = 'C:\home\git\i574n.github\docs\backlog'
$tasks = (& $k agile list $root all --json 2>$null | Out-String | ConvertFrom-Json).tasks
$byId = @{}; $tasks | ForEach-Object { $byId[$_.id] = $_ }
$latest = @{}
$rx = [regex]'^inl note_of_\w+ \(\) = Note \("(?<id>[^"]+)", (?<n>\d+)u32, "(?<at>[^"]+)", "(?<who>[^"]*)", "(?<text>.*)"\)\s*$'
foreach ($line in [IO.File]::ReadLines("$root\state\agile.spi")) {
    if (-not $line.StartsWith('inl note_of_')) { continue }
    $m = $rx.Match($line); if (-not $m.Success) { continue }
    $id = $m.Groups['id'].Value; $n = [int]$m.Groups['n'].Value
    if (-not $latest.ContainsKey($id) -or $latest[$id].N -lt $n) { $latest[$id] = [pscustomobject]@{ N = $n; At = $m.Groups['at'].Value; Who = $m.Groups['who'].Value; Text = $m.Groups['text'].Value -replace '\\n', ' ' } }
}
$userRx = [regex]'(?i)\b(user''?s? (call|decision|decide|push|review|approval|answer|choice|yes|go|order|input)|ask(ed)? the user|needs? the user|waits? (on|for) the user|user to (decide|choose|confirm|push|approve|review)|on the user''s yes|the user decides|pending user|user-only|blocked on (the )?user)'
$waitRx = [regex]'(?i)\b(waits? (on|for)|blocked (on|by)|held (for|until|on)|until (the|a|after)|after the user|parked|on hold|deferred)\b'
$resumeRx = [regex]'(?i)\b(resume (when|on|after|once|if|by)|until\b|unpause|re-?open (when|on)|wakes? (when|on))'
$ruleRx = [regex]'(?i)paused by rule|WIP rule|Resume when picked|no note since'
$now = [DateTime]::UtcNow
$rows = foreach ($t in $tasks | Where-Object status -ne 'Done') {
    $ln = $latest[$t.id]
    $age = if ($ln) { [Math]::Round(($now - [DateTime]::Parse($ln.At).ToUniversalTime()).TotalDays, 1) } else { 999 }
    $deps = @($t.deps | Where-Object { $_ -and $_ -ne '-' })
    $depsDone = $deps.Count -gt 0 -and @($deps | Where-Object { $byId.ContainsKey($_) -and $byId[$_].status -eq 'Done' }).Count -eq $deps.Count
    $text = if ($ln) { $ln.Text } else { '' }
    $flags = @()
    if ($t.status -eq 'Blocked') { $flags += 'BLOCKED' }
    if ($t.status -eq 'Paused') { $flags += 'PAUSED' }
    if ($t.status -eq 'Paused' -and $text -notmatch $resumeRx) { $flags += 'PAUSED-NO-RESUME' }
    if ($t.status -eq 'Paused' -and $text -match $ruleRx) { $flags += 'PAUSED-BY-RULE' }
    if ($t.assignee -in 'user', 'pair') { $flags += "ASSIGNEE-$($t.assignee)" }
    if ($userRx.IsMatch($text)) { $flags += 'WAITS-USER' }
    if ($t.status -eq 'Active' -and $age -ge $StaleDays) { $flags += "STALE-$($age)d" }
    if ($depsDone -and $t.status -in 'Planned', 'Blocked', 'Paused') { $flags += 'DEPS-DONE' }
    [pscustomobject]@{ Id = $t.id; Status = $t.status; P = $t.priority; Pct = $t.progress; Assignee = $t.assignee; Tags = $t.tags; AgeDays = $age; Flags = $flags -join ' '; Last = $(if ($text.Length -gt 400) { $text.Substring(0, 400) } else { $text }) }
}
if ($Out) { $rows | Export-Csv $Out -NoTypeInformation -Encoding utf8 }
"open $(@($rows).Count); flagged $(@($rows | Where-Object Flags).Count)"
foreach ($f in 'BLOCKED', 'PAUSED', 'PAUSED-NO-RESUME', 'PAUSED-BY-RULE', 'ASSIGNEE-user', 'ASSIGNEE-pair', 'WAITS-USER', 'STALE', 'DEPS-DONE') { "{0,-14} {1}" -f $f, @($rows | Where-Object { $_.Flags -match $f }).Count }
