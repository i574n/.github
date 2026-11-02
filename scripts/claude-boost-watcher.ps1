#requires -Version 5.1

param(
    [int] $SweepMs = 200,
    [int] $SlowEvery = 10
)
$mx = [System.Threading.Mutex]::new($false, 'Global\ClaudeBoostWatcher')
try { $have = $mx.WaitOne(0) } catch { $have = $true }
if (-not $have) { return }
try {
    $self = $PID
    (Get-Process -Id $self).PriorityClass = 'High'
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
            foreach ($p in (Get-Process claude -ErrorAction SilentlyContinue)) { try { if ("$($p.PriorityClass)" -eq 'Normal') { $p.PriorityClass = 'AboveNormal' } } catch {} }
            foreach ($p in (Get-Process -Name $lower -ErrorAction SilentlyContinue)) { try { if ($p.Id -ne $self -and "$($p.PriorityClass)" -eq 'AboveNormal') { $p.PriorityClass = 'Normal' } } catch {} }
        }
        $i++
        Start-Sleep -Milliseconds $SweepMs
    }
} finally { $mx.ReleaseMutex() }
