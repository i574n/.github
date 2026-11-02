#requires -Version 5.1

param(
    [ValidateSet('Block', 'Enforce')] [string] $Mode = 'Block',
    [string] $Distro = 'Ubuntu',
    [switch] $DryRun
)
$ErrorActionPreference = 'Stop'
$log = Join-Path $env:LOCALAPPDATA 'kit\schedules\wsl-policy.last.log'
New-Item -ItemType Directory -Force (Split-Path $log) | Out-Null
$vm = Get-Process vmmem, vmmemWSL -ErrorAction SilentlyContinue
$resident = [bool]$vm
$memGB = if ($vm) { [Math]::Round((($vm | Measure-Object WorkingSet64 -Sum).Sum) / 1GB, 2) } else { 0 }
$action = 'none'
if ($Mode -eq 'Block') {
    if ($resident) {
        $action = "SHUTDOWN (vmmem held ${memGB}GB)"
        if (-not $DryRun) { & wsl.exe --shutdown 2>&1 | Out-Null }
    } else { $action = 'already down' }
} else {

    if ($resident) { $action = "WSL already up (vmmem ${memGB}GB)" }
    else {
        $action = "START $Distro"
        if (-not $DryRun) { & wsl.exe -d $Distro -e true 2>&1 | Out-Null }
    }
}
$line = "wsl-policy $(Get-Date -Format 'yyyy-MM-dd HH:mm') mode=$Mode distro=$Distro resident=$resident mem=${memGB}GB -> $action$(if($DryRun){' (dry-run)'})"
Set-Content -LiteralPath $log -Value $line
$line
