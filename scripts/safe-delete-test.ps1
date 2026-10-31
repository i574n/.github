. C:\home\git\i574n.github\scripts\safe-delete.ps1
$results = [Collections.Generic.List[string]]::new()
function Expect-Refused([string] $label, [scriptblock] $action) {
    try { & $action | Out-Null; $results.Add("FAIL $label (allowed)") } catch { $results.Add("PASS $label refused: $($_.Exception.Message)") }
}
Expect-Refused 'empty' { Assert-SafeDeleteRoot '' }
Expect-Refused 'null' { Assert-SafeDeleteRoot $null }
Expect-Refused 'relative' { Assert-SafeDeleteRoot 'x' }
Expect-Refused 'drive root' { Assert-SafeDeleteRoot 'C:\' }
Expect-Refused 'profile' { Assert-SafeDeleteRoot $env:USERPROFILE }
Expect-Refused 'profile with slash' { Assert-SafeDeleteRoot ($env:USERPROFILE + '\') }
Expect-Refused 'temp root' { Assert-SafeDeleteRoot ([IO.Path]::GetTempPath()) }
Expect-Refused 'localappdata' { Assert-SafeDeleteRoot $env:LOCALAPPDATA }
Expect-Refused 'windows' { Assert-SafeDeleteRoot $env:SystemRoot }
Expect-Refused 'dot-dot back to profile' { Assert-SafeDeleteRoot (Join-Path $env:USERPROFILE 'a\..') }
Expect-Refused 'Users folder (contains the profile)' { Assert-SafeDeleteRoot 'C:\Users' }
Expect-Refused 'AppData (contains LOCALAPPDATA)' { Assert-SafeDeleteRoot (Join-Path $env:USERPROFILE 'AppData') }
Expect-Refused 'another profile' { Assert-SafeDeleteRoot 'C:\Users\Public' }
Expect-Refused 'another profile AppData' { Assert-SafeDeleteRoot 'C:\Users\Public\AppData' }
Expect-Refused 'another profile Local\Temp' { Assert-SafeDeleteRoot 'C:\Users\Public\AppData\Local\Temp' }
Expect-Refused 'Under equal to its base' { Assert-SafeDeleteRoot 'C:\home\data' -Under 'C:\home\data' }
Expect-Refused 'outside Under' { Assert-SafeDeleteRoot 'C:\home\git\spiral' -Under 'C:\home\data' }
Expect-Refused 'Under prefix trick' { Assert-SafeDeleteRoot 'C:\home\data-evil\x' -Under 'C:\home\data' }
$stripped = pwsh -NoProfile -Command ". C:\home\git\i574n.github\scripts\safe-delete.ps1; Remove-Item Env:TEMP, Env:TMP; try { Assert-SafeDeleteRoot ([IO.Path]::GetTempPath()); 'ALLOWED' } catch { 'refused: ' + `$_.Exception.Message }"
$results.Add("$(if ($stripped -like 'refused*') { 'PASS' } else { 'FAIL' }) temp root with TEMP and TMP stripped (GetTempPath = profile): $stripped")
$allStripped = pwsh -NoProfile -Command ". C:\home\git\i574n.github\scripts\safe-delete.ps1; Remove-Item Env:TEMP, Env:TMP, Env:USERPROFILE, Env:LOCALAPPDATA, Env:APPDATA; `$r = foreach (`$p in 'C:\Users\i574n', 'C:\Users\i574n\AppData\Local', 'C:\Users') { try { Assert-SafeDeleteRoot `$p | Out-Null; 'ALLOWED ' + `$p } catch { 'refused' } }; `$r -join ','"
$results.Add("$(if ($allStripped -notmatch 'ALLOWED') { 'PASS' } else { 'FAIL' }) profile, LocalAppData and Users refused with USERPROFILE/LOCALAPPDATA/APPDATA/TEMP/TMP all stripped: $allStripped")
$scratch = Join-Path $PSScriptRoot "safe-delete-probe-$PID"
New-Item -ItemType Directory -Force (Join-Path $scratch 'inner') | Out-Null
Set-Content (Join-Path $scratch 'inner\f.txt') 'x'
Remove-TreeSafely (Join-Path $scratch 'inner') -Under $scratch
$results.Add("$(if (-not (Test-Path (Join-Path $scratch 'inner'))) { 'PASS' } else { 'FAIL' }) allowed child under base deleted")
Remove-TreeSafely $scratch
$results.Add("$(if (-not (Test-Path $scratch)) { 'PASS' } else { 'FAIL' }) allowed scratch folder deleted")
$results
"summary: $(($results | Where-Object { $_ -like 'PASS*' }).Count) pass, $(($results | Where-Object { $_ -like 'FAIL*' }).Count) fail"
