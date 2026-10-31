param(
    [string] $Kit = (Join-Path $env:LOCALAPPDATA 'kit\bin\kit.exe'),
    [string] $Pwsh = 'C:\Users\i574n\scoop\apps\pwsh\current\pwsh.exe',
    [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
$scripts = $PSScriptRoot
$tasks = @(
    @{
        Name = 'kit-unstaged-backup'
        At = '03:10'
        Description = "KIT-SCRATCH-WATCH M2: daily verified backup of every C:\home\git repo's staged, unstaged and untracked layer to %LOCALAPPDATA%\kit\backups\unstaged (never deletes)"
        Arguments = "--headless `"$Kit`" job start kit-unstaged-backup 1800000 `"$Pwsh`" -NoProfile -NonInteractive -File $(Join-Path $scripts 'unstaged-backup.ps1') -Verify"
    }
    @{
        Name = 'profile-mirror-to-node'
        Every = 15
        Description = 'PROFILE-MIRROR-TO-NODE: profile mirror to the node every 50 min (profile-mirror-if-due.ps1 polls; the kit schedule stamp prevents double runs)'
        Arguments = "--headless `"$Pwsh`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $scripts 'profile-mirror-if-due.ps1')`""
    }
    @{
        Name = 'resource-report-morning'
        At = '06:55'
        Description = 'RESOURCE-REPORTS: the night report (last 12 h of CPU and memory per team, job and dangling process, box and node) saved to %LOCALAPPDATA%\kit\reports'
        Arguments = "--headless `"$Pwsh`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $scripts 'resource-report-save.ps1')`""
    }
    @{
        Name = 'resource-report-evening'
        At = '18:55'
        Description = 'RESOURCE-REPORTS: the day report (last 12 h of CPU and memory per team, job and dangling process, box and node) saved to %LOCALAPPDATA%\kit\reports'
        Arguments = "--headless `"$Pwsh`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $scripts 'resource-report-save.ps1')`""
    }
    @{
        Name = 'tibia-mariadb-dump'
        At = '05:20'
        Description = 'RESILIENCE: daily consistent (--single-transaction) logical dump of the canary DB to %LOCALAPPDATA%\tibia-mariadb-dumps (mirrored); a live-datadir file copy is not a consistent restore point'
        Arguments = "--headless `"$Pwsh`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $scripts 'tibia-mariadb-dump.ps1')`""
    }
    @{
        Name = 'config-baseline-check'
        At = '06:10'
        Description = 'CONFIG-BASELINE: daily drift check of curated config files against the known-good fingerprint baseline; a change becomes a backlog note (restore content from the profile mirror)'
        Arguments = "--headless `"$Pwsh`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $scripts 'config-baseline.ps1')`" -Mode Check -NoteOnDrift"
    }
    @{
        Name = 'profile-restore-drill'
        At = '05:40'
        Description = 'PROFILE-RESTORE-DRILL: daily restore drill of the newest profile-mirror archive on the node (memory hashes vs live, entry count); a failure becomes a backlog note'
        Arguments = "--headless `"$Pwsh`" -NoProfile -NonInteractive -ExecutionPolicy Bypass -File `"$(Join-Path $scripts 'profile-restore-drill.ps1')`" -NoteOnFailure"
    }
)
foreach ($task in $tasks) {
    $action = New-ScheduledTaskAction -Execute 'C:\Windows\System32\conhost.exe' -Argument $task.Arguments
    $existing = Get-ScheduledTask -TaskName $task.Name -ErrorAction SilentlyContinue
    $current = if ($existing) { "$($existing.Actions.Execute) $($existing.Actions.Arguments) priority=$($existing.Settings.Priority)" } else { '' }
    $wanted = "$($action.Execute) $($action.Arguments) priority=5"
    if ($current -eq $wanted) { [Console]::WriteLine("$($task.Name): unchanged"); continue }
    if ($DryRun) { [Console]::WriteLine("$($task.Name): would set $wanted"); continue }
    if ($existing) {
        Set-ScheduledTask -TaskName $task.Name -Action $action | Out-Null
        $updated = Get-ScheduledTask -TaskName $task.Name
        $updated.Settings.Priority = 5
        $updated | Set-ScheduledTask | Out-Null
    } else {
        if ($task.Every) {
            $trigger = New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(2) -RepetitionInterval (New-TimeSpan -Minutes $task.Every)
            $settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -StartWhenAvailable -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Hours 2) -Priority 5
            Register-ScheduledTask -TaskName $task.Name -Action $action -Trigger $trigger -Settings $settings -Description $task.Description | Out-Null
        } else {
            $trigger = New-ScheduledTaskTrigger -Daily -At $task.At
            $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -MultipleInstances IgnoreNew -Priority 5
            Register-ScheduledTask -TaskName $task.Name -Action $action -Trigger $trigger -Settings $settings -Description $task.Description | Out-Null
        }
    }
    [Console]::WriteLine("$($task.Name): set $wanted")
}
