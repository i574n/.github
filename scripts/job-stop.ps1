param([Parameter(Mandatory)] [string] $Ids)
$ErrorActionPreference = 'Stop'
$cli = 'C:\home\git\kit\kit.exe'
foreach ($id in ($Ids -split ',')) {
    if ($id -notmatch '-main-') { throw "$id is not a main job; stop only own jobs" }
    $last = (& $cli job stop $id 2>&1 | Select-Object -Last 1) -as [string]
    "$id -> $last"
    if ($last -match 'unknown job id') { throw "$cli does not know $id; jobs live under kit state" }
}
