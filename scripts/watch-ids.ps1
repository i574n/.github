param([Parameter(Mandatory)] [string] $Ids, [int] $MaxMinutes = 300)
$env:KIT_LEASE_POLICY = 'renewing'
$cli = 'C:\home\git\kit\kit.exe'
$watched = @($Ids -split ',' | Where-Object { $_ })
$deadline = (Get-Date).AddMinutes($MaxMinutes)
$done = @{}
while ($done.Count -lt $watched.Count -and (Get-Date) -lt $deadline) {
    foreach ($id in $watched) {
        if ($done.ContainsKey($id)) { continue }
        $s = (& $cli job status $id 2>&1 | Select-Object -First 1) -as [string]
        if ($s -match 'state=(finished|failed|stopped|timed-out)') {
            $log = Join-Path $env:LOCALAPPDATA "kit\jobs\$id\stdout.log"
            $last = Get-Content $log -ErrorAction Ignore | Select-String 'runner done|VERDICT|verdict' | Select-Object -Last 1
            $done[$id] = "$(Get-Date -Format HH:mm) $($Matches[1]) $(([regex]::Match($s, 'exit=\S+')).Value) | $last"
            "$id -> $($done[$id])"
        }
    }
    if ($done.Count -lt $watched.Count) { Start-Sleep -Seconds 60 }
}
if ($done.Count -lt $watched.Count) { "watcher deadline hit; still open: $(($watched | Where-Object { -not $done.ContainsKey($_) }) -join ', ')" }
