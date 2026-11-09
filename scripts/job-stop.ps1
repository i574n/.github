param([Parameter(Mandatory)] [string] $Ids)
$ErrorActionPreference = 'Stop'
$cli = (Join-Path $PSScriptRoot '..\..\kit\kit.exe')
foreach ($id in ($Ids -split ',')) {
    if ($id -notmatch '-(org|org2|org3|i574n|main)-') { throw "$id is not an org job; stop only own jobs" }
    $last = (& $cli job stop $id 2>&1 | Select-Object -Last 1) -as [string]
    "$id -> $last"
    if ($last -match 'unknown job id') { throw "$cli does not know $id; jobs live under kit state" }
}
