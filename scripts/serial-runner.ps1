param([Parameter(Mandatory)] [string] $Manifest)
$ErrorActionPreference = 'Continue'
$steps = @(Get-Content $Manifest -Raw | ConvertFrom-Json)
. 'C:\home\git\spiral\apps\compiler\tmp\scripts\heavy.ps1'
. (Join-Path $PSScriptRoot 'live-wait.ps1')
$log = [IO.Path]::ChangeExtension($Manifest, '.log')
$failed = [System.Collections.Generic.List[string]]::new()
foreach ($step in $steps) {
    $argv = @($step.Argv)
    "$(Get-Date -Format 'HH:mm:ss') start $($step.Label) heavy=$($step.Heavy)" | Tee-Object -FilePath $log -Append
    $global:LASTEXITCODE = 0
    try {
        if ($step.Heavy) { Wait-NoLiveBlock $step.Label; Invoke-Heavy -Label "$($step.Label) (serial runner)" { & $argv[0] @($argv | Select-Object -Skip 1) } }
        else { & $argv[0] @($argv | Select-Object -Skip 1) }
        $code = $LASTEXITCODE
    }
    catch { $code = "threw: $($_.Exception.Message)" }
    "$(Get-Date -Format 'HH:mm:ss') end $($step.Label) exit $code" | Tee-Object -FilePath $log -Append
    if ("$code" -ne '0') { $failed.Add("$($step.Label) ($code)") }
}
$summary = if ($failed.Count) { "FAILED $($failed.Count) of $($steps.Count): $($failed -join ', ')" } else { "all $($steps.Count) steps exit 0" }
"$(Get-Date -Format 'HH:mm:ss') runner done: $summary" | Tee-Object -FilePath $log -Append
exit $(if ($failed.Count) { 1 } else { 0 })
