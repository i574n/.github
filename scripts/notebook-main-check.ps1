param([Parameter(Mandatory)] [string] $Src)
$ErrorActionPreference = 'Stop'
$findings = foreach ($file in Get-ChildItem $Src -Recurse -Filter *.spi | Where-Object { $_.FullName -notmatch '\\target\\' }) {
    $lines = [IO.File]::ReadAllLines($file.FullName)
    if (-not ($lines | Where-Object { $_ -match '^///- ' })) { continue }
    $lastTest = -1
    $main = -1
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match '^:# \w+ =' -and $lines[$i] -notmatch ' static =') { $lastTest = $i }
        if ($lines[$i] -match '^:= main \(\) : i32 =') { $main = $i }
    }
    $preludeAt = [Array]::FindIndex($lines, [Predicate[string]] { param($line) $line -match '^:# \w+ static =' })
    $preludeUntargeted = $preludeAt -gt 0 -and $lines[$preludeAt - 1] -eq '///> _'
    if ($main -gt $lastTest -and $lastTest -ge 0 -and $lines[$main - 1] -ne '///> _' -and -not $preludeUntargeted) {
        "trailing-main-untargeted $($file.FullName.Substring($Src.Length + 1)):$($main + 1) (a main after the last test block builds on the default Zig target; put '///> _' directly above it)"
    }
}
$findings
"notebook main check: $(@($findings).Count) findings"
exit [Math]::Min(@($findings).Count, 1)
