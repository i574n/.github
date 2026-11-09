#requires -Version 5.1
# LANE-RENAME-RECONCILE: keep lane names consistent across the fleet without hand-editing
# every stale place when the user renames a lane in CC.
#   DETECT  - a session whose sessionId is unchanged but whose name changed (vs the last
#             snapshot) is a rename; a new name is auto-learned as an alias of that team.
#   ENFORCE - backlog items tagged team:<alias> are retagged team:<canonical> (the alias
#             map, schedules\lane-aliases.txt, is the source of truth). team: tags only -
#             never bare topic tags (bare 'kit' is the TOOL, not the rig lane).
# Rosters DERIVE from the same registry (Get-ActiveLanes), so they need no healing here.
param(
    [switch] $Apply,          # perform backlog retags (default: dry-run, report only)
    [switch] $IncludeDone,    # migrate Done items too (default: live only - protects agile.spi)
    [switch] $NoteOnChange,   # deliver detected/applied changes to the LANE-RENAME-RECONCILE item
    [int] $WithinMinutes = 360
)
$ErrorActionPreference = 'Stop'
$kit = Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'
$bl = Join-Path $PSScriptRoot '..\docs\backlog'
$schedDir = Join-Path $env:LOCALAPPDATA 'kit\schedules'
New-Item -ItemType Directory -Force $schedDir | Out-Null
$log = Join-Path $schedDir 'lane-reconcile.last.log'
$snapFile = Join-Path $schedDir 'lane-reconcile.snapshot.json'
$aliasFile = Join-Path $schedDir 'lane-aliases.txt'
. (Join-Path $PSScriptRoot 'node-env.ps1')
$stamp = Get-Date -Format 'yyyy-MM-dd HH:mm'
$now = Get-Date
$map = Get-LaneAliasMap
$changes = @()

# ── read live CC sessions (sessionId -> name), fresh within the window ────────────────
# updatedAt is epoch MILLISECONDS (not ISO) - the trap that silently stales every row if read as ticks.
$dir = Join-Path $env:USERPROFILE '.claude\sessions'
$cur = @{}
if (Test-Path $dir) {
    foreach ($f in (Get-ChildItem -LiteralPath $dir -Filter '*.json' -ErrorAction SilentlyContinue)) {
        try { $j = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction Stop | ConvertFrom-Json } catch { continue }
        if (-not $j.name -or -not $j.sessionId) { continue }
        $upd = $null
        foreach ($k in 'updatedAt', 'statusUpdatedAt', 'startedAt') {
            $r = $j.$k; if ($null -eq $r -or "$r" -eq '') { continue }
            try { if ("$r" -match '^\d+$') { $upd = [datetimeoffset]::FromUnixTimeMilliseconds([int64]"$r").LocalDateTime } else { $upd = [datetime]"$r" }; break } catch {}
        }
        if (-not $upd) { $upd = $f.LastWriteTime }
        if (($now - $upd).TotalMinutes -gt $WithinMinutes) { continue }
        $sid = "$($j.sessionId)"
        if (-not $cur.ContainsKey($sid)) { $cur[$sid] = "$($j.name)" }
    }
}

# ── DETECT: same sessionId, changed name (vs snapshot) => rename; learn unknown new names ──
$snap = @{}
if (Test-Path $snapFile) { try { (Get-Content -LiteralPath $snapFile -Raw | ConvertFrom-Json).PSObject.Properties | ForEach-Object { $snap[$_.Name] = "$($_.Value)" } } catch {} }
$learned = @()
foreach ($id in $cur.Keys) {
    if (-not $snap.ContainsKey($id) -or $snap[$id] -eq $cur[$id]) { continue }
    $old = $snap[$id]; $new = $cur[$id]
    $oldTeam = Resolve-LaneTeam $old $map
    if ((Resolve-LaneTeam $new $map) -eq $new -and -not $map.Contains($new)) {
        $learned += [pscustomobject]@{ team = $oldTeam; alias = $new }
        $changes += "RENAME-DETECTED session=$id '$old'->'$new' : learned as alias of team '$oldTeam'"
    }
    else { $changes += "RENAME-DETECTED session=$id '$old'->'$new' : already mapped (team '$oldTeam'->'$(Resolve-LaneTeam $new $map)')" }
}
# persist learned aliases into the source-of-truth file (idempotent row rewrite)
foreach ($l in $learned) {
    if (-not $map.Contains($l.team) -or ($map[$l.team] -contains $l.alias)) { continue }
    $lines = @(Get-Content -LiteralPath $aliasFile -ErrorAction SilentlyContinue)
    $hit = $false
    $lines = @($lines | ForEach-Object {
            if ($_ -match "^\s*$([regex]::Escape($l.team))\s*=") { $hit = $true; "$($l.team)=$(((@($map[$l.team]) + $l.alias) | Select-Object -Unique) -join ',')" } else { $_ }
        })
    if (-not $hit) { $lines += "$($l.team)=$($l.team),$($l.alias)" }
    Set-Content -LiteralPath $aliasFile -Value $lines
    $map = Get-LaneAliasMap
}

# ── ENFORCE: backlog team:<alias> -> team:<canonical> ────────────────────────────────
$applied = 0; $pending = @()
foreach ($team in $map.Keys) {
    foreach ($alias in $map[$team]) {
        if ($alias -ieq $team) { continue }
        $tasks = @()
        try { $tasks = (& $kit agile list $bl --tag "team:$alias" --json 2>$null | Out-String | ConvertFrom-Json).tasks } catch {}
        foreach ($it in $tasks) {
            if (-not $IncludeDone -and $it.status -eq 'Done') { continue }
            $newtags = (($it.tags -split ',' | ForEach-Object { $t = $_.Trim(); if ($t -ieq "team:$alias") { "team:$team" } else { $t } } | Where-Object { $_ }) | Select-Object -Unique) -join ','
            if ($newtags -eq $it.tags) { continue }
            if ($Apply) {
                $out = (& $kit agile edit $bl $it.id tags $newtags 2>&1 | Out-String)
                if ($LASTEXITCODE -eq 0) { $applied++ } else { $changes += "ENFORCE-FAIL $($it.id) rc=$LASTEXITCODE" }
            }
            else { $pending += "$($it.id) team:$alias->team:$team" }
        }
        if ($Apply -and $applied -gt 0 -and ($map[$team] -contains $alias)) {} # (counter only)
    }
}
if ($applied -gt 0) { $changes += "ENFORCE applied=$applied" }

# ── snapshot + log ───────────────────────────────────────────────────────────────────
($cur | ConvertTo-Json) | Set-Content -LiteralPath $snapFile
$summary = if ($Apply) { "applied=$applied learned=$($learned.Count) detected=$(@($changes | Where-Object { $_ -match 'RENAME-DETECTED' }).Count)" } else { "DRY-RUN pending=$($pending.Count) learned=$($learned.Count)" }
$line = "lane-reconcile $stamp $summary"
Set-Content -LiteralPath $log -Value (@($line) + $changes + @($pending | ForEach-Object { "PENDING $_" }))
$line
$changes | ForEach-Object { "  $_" }
if ($pending.Count) { "  (dry-run) re-run with -Apply to perform $($pending.Count) retag(s); first: $($pending[0])" }

# ── deliver to the tracking item on a real change ────────────────────────────────────
if ($NoteOnChange -and ($applied -gt 0 -or $learned.Count -gt 0)) {
    $noteText = "lane-reconcile ${stamp}: $summary. " + (($changes | Select-Object -First 8) -join '; ')
    $rc = 1
    foreach ($d in @(0, 2, 5, 10)) { if ($d -gt 0) { Start-Sleep -Seconds $d }; $o = (& $kit agile note $bl 'LANE-RENAME-RECONCILE' $noteText '--as' 'management' 2>&1 | Out-String); $rc = $LASTEXITCODE; if ($rc -eq 0) { break } }
    if ($rc -ne 0) { Add-Content -LiteralPath $log -Value "lane-reconcile $stamp NOTE FAILED rc=$rc" }
}
if (@($changes | Where-Object { $_ -match 'FAIL' }).Count) { exit 1 }
