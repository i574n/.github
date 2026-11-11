#requires -Version 5.1
# BOOST-WATCHER: keep the interactive foreground (the agent runtime + git UI) responsive
# under heavy background builds by nudging priorities. Runtime-neutral: the agent process
# name comes from the provider (Get-AgentProcessName), not a hardcoded 'claude'. A native
# build (claude-boost-native.exe, scheduled) supersedes this; the script is the portable
# reference / fallback. Single-instance via a global mutex.

param(
    [int] $SweepMs = 200,
    [int] $SlowEvery = 10
)
. (Join-Path $PSScriptRoot 'agent-runtime.ps1')
$mx = [System.Threading.Mutex]::new($false, 'Global\AgentBoostWatcher')
try { $have = $mx.WaitOne(0) } catch { $have = $true }
if (-not $have) { return }
try {
    $self = $PID
    (Get-Process -Id $self).PriorityClass = 'High'
    $agentProc = Get-AgentProcessName
    $lower = @('dotnet', 'cargo', 'rustc', 'cc1', 'cc1plus', 'ld', 'link', 'erl', 'beam.smp', 'epmd', 'java', 'mix', 'escript', 'gcc', 'clang', 'zig', 'lean', 'node', 'make', 'msbuild', 'pwsh', 'powershell', 'cmd', 'conhost', 'ssh', 'scp')
    $i = 0
    while ($true) {

        foreach ($p in (Get-Process git, Fork -ErrorAction SilentlyContinue)) {
            try {
                if ($p.Name -eq 'Fork') { if ("$($p.PriorityClass)" -ne 'High') { $p.PriorityClass = 'High' } }
                elseif ("$($p.PriorityClass)" -eq 'Normal') { $p.PriorityClass = 'AboveNormal' }
            } catch {}
        }
        if ($i % $SlowEvery -eq 0) {
            foreach ($p in (Get-Process $agentProc -ErrorAction SilentlyContinue)) { try { if ("$($p.PriorityClass)" -eq 'Normal') { $p.PriorityClass = 'AboveNormal' } } catch {} }
            foreach ($p in (Get-Process -Name $lower -ErrorAction SilentlyContinue)) { try { if ($p.Id -ne $self -and "$($p.PriorityClass)" -eq 'AboveNormal') { $p.PriorityClass = 'Normal' } } catch {} }
        }
        $i++
        Start-Sleep -Milliseconds $SweepMs
    }
} finally { $mx.ReleaseMutex() }
