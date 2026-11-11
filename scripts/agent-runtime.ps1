#requires -Version 5.1
# AGENT-RUNTIME PROVIDER (RUNTIME-DECOUPLE): the ONE place the fleet scripts name the
# agent runtime (its state dir, session registry, process image, scratch, transcripts).
# Everything else dot-sources this and asks an accessor, so retargeting a renamed state
# dir or a different agent binary is a single env var / this one file - not a 15-script
# sweep (the rename-sweep-every-consumer trap). Each accessor defaults to the exact path
# the scripts hardcoded before, so behaviour is byte-identical unless an AGENT_* override
# is set. No lane/team vocabulary lives here: that is the fleet's own domain model (see
# node-env.ps1 Resolve-LaneTeam / Get-ActiveLanes), layered on top of Get-AgentSessions.

function Get-AgentStateHome {
    # The runtime's per-user state root (sessions, hooks, settings, transcripts, standing).
    if ($env:AGENT_STATE_HOME) { return $env:AGENT_STATE_HOME }
    Join-Path $env:USERPROFILE '.claude'
}
function Get-AgentProcessName {
    # Process name for Get-Process (NO extension).
    if ($env:AGENT_PROCESS_NAME) { return $env:AGENT_PROCESS_NAME }
    'claude'
}
function Get-AgentProcessImage { "$(Get-AgentProcessName).exe" }   # the .exe form resource-report matches on
function Get-AgentScratchRoot {
    if ($env:AGENT_SCRATCH_ROOT) { return $env:AGENT_SCRATCH_ROOT }
    Join-Path $env:TEMP 'claude'
}
function Get-AgentProjectKey {
    # The mangled-cwd key under <state>/projects that holds this workspace's transcripts + memory.
    if ($env:AGENT_PROJECT_KEY) { return $env:AGENT_PROJECT_KEY }
    'C--home-git'
}
function Get-AgentProjectsDir { Join-Path (Get-AgentStateHome) 'projects' }   # whole transcript tree (all workspaces)
function Get-AgentProjectDir { Join-Path (Get-AgentProjectsDir) (Get-AgentProjectKey) }  # *.jsonl transcripts live here
function Get-AgentMemoryDir { Join-Path (Get-AgentProjectDir) 'memory' }
function Get-AgentHooksDir { Join-Path (Get-AgentStateHome) 'hooks' }
function Get-AgentAutonomousDir { Join-Path (Get-AgentStateHome) 'autonomous' }
function Get-AgentSubagentHoldDir { Join-Path (Get-AgentStateHome) 'subagent-hold' }
function Get-AgentSettingsFile { Join-Path (Get-AgentStateHome) 'settings.json' }
function Get-AgentStandingFile { Join-Path (Get-AgentStateHome) (Join-Path 'autonomous' 'standing.txt') }
function Get-AgentTeamsFile { Join-Path (Get-AgentStateHome) (Join-Path 'autonomous' 'teams.txt') }
function Get-AgentSessionsDir { Join-Path (Get-AgentStateHome) 'sessions' }

function ConvertFrom-AgentTimestamp {
    # The ONE copy of the session-registry timestamp parse (was triplicated across
    # node-env Get-LaneSessions + Get-ActiveLanes + lane-reconcile): the harness writes
    # updatedAt / statusUpdatedAt / startedAt as epoch MILLISECONDS (not ISO, not ticks -
    # the trap that silently stales every row if read as ticks). Returns the first
    # candidate that parses as a local DateTime; a trailing [datetime] (e.g. a file's
    # LastWriteTime) is accepted as the fallback. $null if nothing parses.
    param([Parameter(ValueFromRemainingArguments = $true)] $Candidates)
    foreach ($raw in $Candidates) {
        if ($null -eq $raw -or "$raw" -eq '') { continue }
        if ($raw -is [datetime]) { return $raw }
        try {
            if ("$raw" -match '^\d+$') { return [datetimeoffset]::FromUnixTimeMilliseconds([int64]"$raw").LocalDateTime }
            return [datetime]"$raw"
        }
        catch {}
    }
    return $null
}

function Get-AgentSessions {
    # Normalized records for every live agent session, from the harness registry
    # (<state>/sessions/<pid>.json: sessionId, name, pid, status, updatedAt...). A plain
    # script can read this, so rosters DERIVE their lane list and a rename never leaves
    # them stale. The fleet's team/alias model (node-env Resolve-LaneTeam) is applied by
    # the CALLER on top of these records - this stays runtime-generic.
    #   -WithinMinutes >0  : keep only sessions whose Updated is within the window.
    #   -LivePidOnly       : drop a session whose recorded pid is no longer running.
    param([int] $WithinMinutes = 0, [switch] $LivePidOnly)
    $dir = Get-AgentSessionsDir
    if (-not (Test-Path $dir)) { return @() }
    $now = Get-Date
    @(foreach ($f in (Get-ChildItem -LiteralPath $dir -Filter '*.json' -ErrorAction SilentlyContinue)) {
            try { $j = Get-Content -LiteralPath $f.FullName -Raw -ErrorAction Stop | ConvertFrom-Json } catch { continue }
            if (-not $j.name) { continue }
            $upd = ConvertFrom-AgentTimestamp $j.updatedAt $j.statusUpdatedAt $j.startedAt $f.LastWriteTime
            if ($WithinMinutes -gt 0 -and $upd -and ($now - $upd).TotalMinutes -gt $WithinMinutes) { continue }
            if ($LivePidOnly -and $j.pid -and -not (Get-Process -Id $j.pid -ErrorAction SilentlyContinue)) { continue }
            [pscustomobject]@{
                Id          = "$($j.sessionId)"
                Name        = "$($j.name)"
                Pid         = $j.pid
                Status      = "$($j.status)"
                Updated     = $upd
                StatusSince = ((ConvertFrom-AgentTimestamp $j.statusUpdatedAt) ?? $upd)
            }
        })
}
